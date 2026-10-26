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

"""Generate Arnold's context-first merge-request walkthrough."""

from utils import scm
import argparse
import os
import re

from flows.code_reviews.common.cli import parse_mr_link
from flows.code_reviews.common.nodes import Cleanup
from flows.code_reviews.smart_diff.flow import create_smart_diff_flow
from flows.code_reviews.smart_diff.nodes import send_callback
from utils.env import get_env_optional

SHA_RE = re.compile(r"^[0-9a-fA-F]{40,64}$")
UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")


def parser():
    result = argparse.ArgumentParser(description="Generate a Smart Diff walkthrough for Arnold")
    result.add_argument("--mr-link", required=True)
    result.add_argument("--project-id", required=True, type=int)
    result.add_argument("--mr-iid", required=True, type=int)
    result.add_argument("--expected-head-sha", required=True)
    result.add_argument("--execution-id", required=True)
    result.add_argument("--token", default=None)
    result.add_argument("--arnold-url", default=get_env_optional("ARNOLD_URL"))
    result.add_argument("--dry-run", action="store_true", help="Generate locally without calling Arnold")
    return result


def main():
    args = parser().parse_args()
    args.token = scm.token_for(args.mr_link, args.token)
    if not args.token or (not args.dry_run and not args.arnold_url) or not SHA_RE.fullmatch(args.expected_head_sha) \
            or not UUID_RE.fullmatch(args.execution_id):
        parser().error("valid GitLab credentials, Arnold URL, head SHA, and execution ID are required")

    shared = {
        "arnold_url": args.arnold_url, "dry_run": args.dry_run,
        "project_id": args.project_id, "merge_request_iid": args.mr_iid,
        "expected_head_sha": args.expected_head_sha, "execution_id": args.execution_id,
        "gitlab_token": args.token,
    }
    try:
        details = parse_mr_link(args.mr_link, args.token)
        shared.update({
            "repo_url": details["repo_url"], "gitlab_url": details["gitlab_url"],
            "resolved_project_id": details["project_id"],
            "resolved_merge_request_iid": details["mr_iid"],
            "merge_request_id": details["mr_id"],
            "mr_author_id": details["mr_author_id"],
            "source_branch": details["source_branch"], "target_branch": details["target_branch"],
            "mr_title": details["mr_title"], "mr_description": details["mr_description"],
            "project_name": details["project_path"], "cleanup_enabled": True,
            "enable_code_graph": True, "jira_url": os.environ.get("JIRA_URL", ""),
            "jira_token": os.environ.get("JIRA_TOKEN", ""),
            "jira_cloud_id": os.environ.get("JIRA_CLOUD_ID", ""),
            "jira_browse_url": os.environ.get("JIRA_BROWSE_URL", ""),
            "rfc_url": "", "rfc_api_url": get_env_optional("RFC_CENTRAL_API_URL", ""),
        })
        create_smart_diff_flow().run(shared)
    except Exception:
        try:
            send_callback(shared, "failed", error_message="Smart Diff generation failed. Please retry.",
                          comment_assessment_status="failed")
        finally:
            if shared.get("local_repo_path"):
                Cleanup().run(shared)
        raise


if __name__ == "__main__":
    main()
