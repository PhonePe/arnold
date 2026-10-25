#!/usr/bin/env python3
# Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Shared CLI utilities for the improve and review flows."""

import os
import re
import json
import logging
import gitlab
from utils import scm
from utils.logger import get_logger
from utils.env import get_env_optional

logger = get_logger("clio_cli_logger")


def _review_personas_from_env():
    raw = os.environ.get("REVIEW_PERSONAS_JSON", "").strip()
    if not raw:
        return []
    personas = json.loads(raw)
    if not isinstance(personas, list) or len(personas) > 3:
        raise ValueError("REVIEW_PERSONAS_JSON must contain at most three personas")
    required = {"id", "name", "instructions", "version"}
    if any(not isinstance(persona, dict) or not required.issubset(persona) for persona in personas):
        raise ValueError("REVIEW_PERSONAS_JSON contains an invalid persona")
    return personas


_FAILURE_MESSAGES = {
    "improve": "## ❌ Agentic PR Suggestions Failed\n\n",
    "review":  "## ❌ Agentic PR Review Failed\n\n",
    "describe": "## ❌ Agentic PR Description Failed\n\n",
    "sonarfix": "## ❌ Agentic SonarQube Fix Failed\n\n",
    "impact":  "## ❌ Impact Analysis Failed\n\n",
}

# Error patterns that indicate an LLM agent timeout or transient failure
_SDK_TIMEOUT_PATTERNS = (
    "timed out",
    "timeout",
    "TimeoutError",
    "deadline exceeded",
    "agent session",
    "session returned no content",
    "send_and_wait",
)


def _classify_error(error: Exception) -> str:
    """Return a human-friendly error category string."""
    error_str = str(error)
    error_type = type(error).__name__

    if any(p.lower() in error_str.lower() or p.lower() in error_type.lower()
           for p in _SDK_TIMEOUT_PATTERNS):
        return "sdk_timeout"
    return "generic"


def post_failure_notification(shared, error: Exception):
    """Delete the ACK comment and post a failure notification to the MR.

    Called from each flow's except block so users are always informed when
    the pipeline fails for any reason.
    """
    gitlab_token = shared.get("gitlab_token")
    gitlab_url = shared.get("gitlab_url", "https://gitlab.example.com")
    project_id = shared.get("project_id")
    merge_request_iid = shared.get("merge_request_iid")
    ack_note_id = shared.get("ack_note_id")
    flow_type = shared.get("flow_type", "improve")
    dry_run = shared.get("dry_run", False)
    pipeline_url = shared.get("pipeline_url", "")

    if dry_run or not gitlab_token or not project_id or not merge_request_iid:
        return

    try:
        gl = scm.connect(gitlab_url, private_token=gitlab_token)
        project = gl.projects.get(project_id)
        mr = project.mergerequests.get(merge_request_iid)

        if ack_note_id:
            try:
                mr.notes.get(ack_note_id).delete()
                logger.info(f"Deleted acknowledgment comment (note_id={ack_note_id})")
            except Exception as del_err:
                logger.warning(f"Failed to delete acknowledgment comment: {del_err}")

        header = _FAILURE_MESSAGES.get(flow_type, "## ❌ Pipeline Failed\n\n")
        error_category = _classify_error(error)

        if error_category == "sdk_timeout":
            body = (
                header
                + "The LLM agent call **timed out** or failed to respond.\n\n"
                + "This is usually a transient issue. Please **re-trigger** the pipeline to retry.\n\n"
            )
        else:
            body = header + "The pipeline encountered an error and could not complete.\n\n"

        if pipeline_url:
            body += f"**Pipeline:** [View pipeline logs]({pipeline_url})\n\n"
        body += f"**Error:** `{error}`"

        mr.notes.create({"body": body})
        logger.info("Posted failure notification to MR")
    except Exception as notify_err:
        logger.warning(f"Failed to post failure notification: {notify_err}")


def parse_mr_link(mr_link, gitlab_token):
    """Parse a GitLab merge request or GitHub pull request link and extract all necessary information."""
    gitlab_match = re.match(r'(https?://[^/]+)/(.+?)/-/merge_requests/(\d+)', mr_link)
    github_match = re.match(r'(https?://[^/]+)/([\w.-]+/[\w.-]+)/pull/(\d+)', mr_link)
    match = gitlab_match or (github_match if github_match and scm.is_github(mr_link) else None)
    if not match:
        raise ValueError(f"Invalid merge request link: {mr_link}")

    gitlab_url, project_path, mr_iid = match.group(1), match.group(2), int(match.group(3))
    logger.info(f"Parsed: {project_path}, MR {mr_iid}")

    try:
        gl = scm.connect(gitlab_url, private_token=gitlab_token)
        gl.auth()
        project = gl.projects.get(project_path)
        mr = project.mergerequests.get(mr_iid)

        return {
            "gitlab_url": gitlab_url,
            "project_id": project.id,
            "project_path": project_path,
            "repo_url": project.http_url_to_repo,
            "source_branch": mr.source_branch,
            "target_branch": mr.target_branch,
            "mr_iid": mr_iid,
            "mr_id": mr.id,
            "mr_title": mr.title,
            "mr_description": mr.description,
            "mr_web_url": mr.web_url,
            "mr_author_id": mr.author.get("id") if mr.author else None,
            "mr_author_username": mr.author.get("username", "") if mr.author else "",
        }
    except Exception as e:
        raise ValueError(f"Failed to fetch MR details: {e}")


def add_common_args(parser):
    """Add CLI arguments shared by both improve and review flows."""
    # Source options (mutually exclusive)
    source_group = parser.add_mutually_exclusive_group(required=True)
    source_group.add_argument(
        "--mr-link",
        help="GitLab merge request link (e.g., https://gitlab.com/group/project/-/merge_requests/123)",
    )
    source_group.add_argument("--repo", help="URL of the GitLab repository")
    source_group.add_argument("--local-dir", help="Path to local repository (skip cloning)")

    # Branch options
    parser.add_argument(
        "--source-branch", "-s",
        help="Source branch to review (auto-detected from MR link)",
    )
    parser.add_argument(
        "--target-branch", "-t", default="main",
        help="Target branch to compare against (default: main)",
    )

    # GitLab options
    parser.add_argument("--token", help="GitLab personal access token (default: GITLAB_ACCESS_TOKEN env var)")
    parser.add_argument("--gitlab-url", default="https://gitlab.com", help="GitLab instance URL")
    parser.add_argument("--project-id", help="GitLab project ID")
    parser.add_argument("--mr-iid", type=int, help="Merge Request IID")

    # Jira options
    parser.add_argument(
        "--jira-url",
        help="Jira base URL (default: JIRA_URL env var). For Cloud via proxy: https://jira.example.com/api",
    )
    parser.add_argument(
        "--jira-token",
        help="Jira API token — plain 'user:password' or pre-encoded 'Basic ...' (default: JIRA_TOKEN env var)",
    )
    parser.add_argument(
        "--jira-cloud-id",
        help="Jira Cloud ID for cloud API routing (default: JIRA_CLOUD_ID env var)",
    )
    parser.add_argument(
        "--jira-browse-url",
        help="Base URL for Jira browse links (default: JIRA_BROWSE_URL env var or https://example.atlassian.net)",
    )
    parser.add_argument(
        "--jira-tickets",
        help="Comma-separated Jira ticket keys or URLs (default: JIRA_TICKETS env var)",
    )

    # RFC options
    parser.add_argument(
        "--rfc-url",
        help="Google Doc URL of the RFC/design document to use as review context",
    )
    parser.add_argument(
        "--rfc-api-url",
        default=None,
        help="RFC Central API base URL (default: RFC_CENTRAL_API_URL env var or staging)",
    )

    # Common options
    parser.add_argument("--dry-run", action="store_true", help="Don't post to GitLab, just generate output")
    parser.add_argument("--no-cleanup", action="store_true", help="Don't clean up temporary files")
    parser.add_argument("-n", "--name", help="Project name (derived from repo URL if omitted)")


def resolve_args(args):
    """Resolve common CLI arguments into repo/branch/gitlab variables.

    Returns (repo_url, source_branch, target_branch, project_id, mr_iid,
             gitlab_url, gitlab_token, project_name, mr_details).
    """
    gitlab_token = args.token or os.environ.get("GITLAB_ACCESS_TOKEN")

    mr_details = None
    if args.mr_link:
        logger.info(f"Parsing merge request link: {args.mr_link}")
        if not gitlab_token:
            raise ValueError(
                "GitLab token required for MR link parsing. "
                "Set GITLAB_ACCESS_TOKEN env var or use --token"
            )
        mr_details = parse_mr_link(args.mr_link, gitlab_token)

        repo_url = mr_details["repo_url"]
        source_branch = mr_details["source_branch"]
        target_branch = mr_details["target_branch"]
        project_id = mr_details["project_id"]
        mr_iid = mr_details["mr_iid"]
        gitlab_url = mr_details["gitlab_url"]
        project_name = mr_details["project_path"]

        logger.info(f"MR Title: {mr_details['mr_title']}")
        logger.info(f"Source: {source_branch} → Target: {target_branch}")
    else:
        if not args.source_branch:
            raise ValueError("--source-branch is required (unless using --mr-link)")

        repo_url = args.repo
        source_branch = args.source_branch
        target_branch = args.target_branch
        project_id = args.project_id
        mr_iid = args.mr_iid
        gitlab_url = args.gitlab_url
        project_name = args.name

        if not gitlab_token and repo_url:
            logger.warning("No GitLab token provided. Private repositories won't be accessible.")

        if not project_name:
            if repo_url:
                project_name = repo_url.rstrip("/").split("/")[-1].replace(".git", "")
            elif args.local_dir:
                project_name = os.path.basename(os.path.abspath(args.local_dir))

        if repo_url and gitlab_url == "https://gitlab.com":
            from urllib.parse import urlparse
            parsed = urlparse(repo_url)
            gitlab_url = f"{parsed.scheme}://{parsed.netloc}"

    return (
        repo_url, source_branch, target_branch, project_id, mr_iid,
        gitlab_url, gitlab_token, project_name, mr_details,
    )


def build_shared_dict(args, repo_url, source_branch, target_branch, project_id,
                      mr_iid, gitlab_url, gitlab_token, project_name, mr_details):
    """Build the base shared dictionary used by all flows."""
    mr_description = (mr_details.get("mr_description") or "") if mr_details else ""
    review_context = os.environ.get("REVIEW_CONTEXT", "").strip()
    if review_context:
        mr_description = f"{mr_description}\n\nAdditional review context:\n{review_context}".strip()
    jira_url = getattr(args, "jira_url", None) or os.environ.get("JIRA_URL", "")
    jira_token = getattr(args, "jira_token", None) or os.environ.get("JIRA_TOKEN", "")
    jira_cloud_id = getattr(args, "jira_cloud_id", None) or os.environ.get("JIRA_CLOUD_ID", "")
    jira_browse_url = (
        getattr(args, "jira_browse_url", None)
        or os.environ.get("JIRA_BROWSE_URL", "")
        or "https://example.atlassian.net"
    )
    jira_tickets = getattr(args, "jira_tickets", None) or os.environ.get("JIRA_TICKETS", "")

    rfc_url = getattr(args, "rfc_url", None) or os.environ.get("RFC_URL", "")
    rfc_api_url = (getattr(args, "rfc_api_url", None)
                   or get_env_optional("RFC_CENTRAL_API_URL", ""))

    return {
        # Repository info
        "repo_url": repo_url,
        "local_repo_path": args.local_dir,
        "project_name": project_name,
        # Branch info
        "source_branch": source_branch,
        "target_branch": target_branch,
        # GitLab info
        "gitlab_token": gitlab_token,
        "gitlab_url": gitlab_url,
        "project_id": project_id,
        "merge_request_iid": mr_iid,
        # MR details
        "mr_title": mr_details.get("mr_title", "") if mr_details else "",
        "mr_description": mr_description,
        "mr_web_url": mr_details.get("mr_web_url", "") if mr_details else "",
        "mr_author_id": mr_details.get("mr_author_id") if mr_details else None,
        "mr_author_username": mr_details.get("mr_author_username", "") if mr_details else "",
        # Pipeline URL
        "pipeline_url": os.environ.get("CI_PIPELINE_URL", ""),
        # Jira integration
        "jira_url": jira_url.rstrip("/") if jira_url else "",
        "jira_token": jira_token,
        "jira_cloud_id": jira_cloud_id.strip() if jira_cloud_id else "",
        "jira_browse_url": jira_browse_url.rstrip("/") if jira_browse_url else "",
        "jira_tickets": jira_tickets,
        "related_tickets": [],
        # RFC integration
        "rfc_url": rfc_url,
        "rfc_api_url": rfc_api_url,
        "rfc_content": "",
        "review_personas": _review_personas_from_env(),
        # Output settings
        "dry_run": args.dry_run,
        "cleanup_enabled": not args.no_cleanup,
        "enable_code_graph": getattr(args, "enable_code_graph", True),
        # Outputs (populated by nodes)
        "changed_files": [],
    }
