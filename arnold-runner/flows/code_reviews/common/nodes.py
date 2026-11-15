# Copyright (c) 2026 Original Author(s), PhonePe Limited
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

import os
import os
import re
import logging
import tempfile
import shutil
import subprocess
import gitlab
from utils import scm
from pocketflow import Node
from utils.config import get_ai_bot_user_id
from flows.code_reviews.common import comment_filters
from utils.logger import get_logger
# Set up logger
logger = get_logger("code_review_common_nodes_logger")

def _safe_int(value):
    """Safely convert a value to int, returning None on failure."""
    if value is None:
        return None
    try:
        return int(value)
    except (ValueError, TypeError):
        return None

class PostAcknowledgment(Node):
    """Post acknowledgment comment to MR at start of review."""

    # Flow-specific ACK messages
    _ACK_MESSAGES = {
        "improve": {
            "header": "## Agentic PR Suggestions Triggered\n\n",
            "body": "An agentic code review has been triggered.\n\n",
        },
        "review": {
            "header": "## Agentic PR Review Triggered\n\n",
            "body": "A high-level review summary has been triggered.\n\n",
        },
        "describe": {
            "header": "## Agentic PR Description Triggered\n\n",
            "body": "A PR description generation has been triggered.\n\n",
        },
        "sonarfix": {
            "header": "## Agentic SonarQube Fix Triggered\n\n",
            "body": "An automated SonarQube issue fix has been triggered.\n\n",
        },
        "impact": {
            "header": "## 📊 Impact Analysis Triggered\n\n",
            "body": "A post-merge code review impact analysis has been triggered.\n\n",
        },
    }

    def prep(self, shared):
        gitlab_token = shared.get("gitlab_token")
        gitlab_url = shared.get("gitlab_url", "https://gitlab.example.com")
        project_id = shared.get("project_id")
        merge_request_iid = shared.get("merge_request_iid")
        dry_run = shared.get("dry_run", False)
        mr_title = shared.get("mr_title", "")
        pipeline_url = shared.get("pipeline_url", "")
        flow_type = shared.get("flow_type", "improve")
        
        return {
            "gitlab_token": gitlab_token,
            "gitlab_url": gitlab_url,
            "project_id": project_id,
            "merge_request_iid": merge_request_iid,
            "dry_run": dry_run,
            "mr_title": mr_title,
            "pipeline_url": pipeline_url,
            "flow_type": flow_type,
        }
    
    def exec(self, prep_res):
        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        project_id = prep_res["project_id"]
        merge_request_iid = prep_res["merge_request_iid"]
        dry_run = prep_res["dry_run"]
        pipeline_url = prep_res["pipeline_url"]
        flow_type = prep_res["flow_type"]
        
        if dry_run:
            logger.info("Dry run mode - skipping acknowledgment comment")
            return {"ack_posted": False, "ack_note_id": None}
        
        if not gitlab_token or not project_id or not merge_request_iid:
            logger.warning("Missing GitLab credentials - skipping acknowledgment comment")
            return {"ack_posted": False, "ack_note_id": None}

        msg = self._ACK_MESSAGES.get(flow_type, self._ACK_MESSAGES["improve"])
        ack_body = msg["header"] + msg["body"]

        if pipeline_url:
            ack_body += f"**Pipeline:** [View pipeline]({pipeline_url})\n\n"

        ack_body += (
            "This may take a few minutes. "
            "Please wait for the process to complete."
        )
        
        try:
            gl = scm.connect(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr = project.mergerequests.get(merge_request_iid)
            note = mr.notes.create({'body': ack_body})
            logger.info(f"Posted acknowledgment comment to MR (note_id={note.id})")
            return {"ack_posted": True, "ack_note_id": note.id}
        except Exception as e:
            logger.warning(f"Failed to post acknowledgment: {e}")
            return {"ack_posted": False, "ack_note_id": None}
    
    def post(self, shared, prep_res, exec_res):
        shared["ack_posted"] = exec_res["ack_posted"]
        shared["ack_note_id"] = exec_res["ack_note_id"]


class CloneRepository(Node):
    """Clone the repository on which review is triggered."""
    
    def prep(self, shared):
        repo_url = shared.get("repo_url")
        gitlab_token = shared.get("gitlab_token")
        source_branch = shared.get("source_branch")
        target_branch = shared.get("target_branch", "main")
        
        if not repo_url:
            raise ValueError("repo_url is required for cloning the repository")
        if not source_branch:
            raise ValueError("source_branch is required for cloning")
        
        return {
            "repo_url": repo_url,
            "gitlab_token": gitlab_token,
            "source_branch": source_branch,
            "target_branch": target_branch,
            "clone_parent_dir": shared.get("clone_parent_dir"),
        }
    
    def _build_clone_url(self, repo_url, gitlab_token):
        """Construct authenticated clone URL."""
        clone_url = scm.authenticated_clone_url(repo_url, gitlab_token)
        if not clone_url.endswith('.git'):
            clone_url += '.git'
        return clone_url

    def exec(self, prep_res):
        repo_url = prep_res["repo_url"]
        gitlab_token = prep_res["gitlab_token"]
        source_branch = prep_res["source_branch"]
        target_branch = prep_res["target_branch"]
        
        temp_dir = tempfile.mkdtemp(prefix="code_review_", dir=prep_res.get("clone_parent_dir"))
        clone_url = self._build_clone_url(repo_url, gitlab_token)
        
        logger.info(f"Cloning repository from {repo_url} (partial clone)...")
        
        # Partial (treeless) clone: downloads only commit graph + trees,
        # blobs are fetched on demand during checkout/diff.
        # This is typically 10-50x faster than a full clone for large repos.
        cmd = [
            'git', 'clone',
            '--no-checkout',
            '--filter=blob:none',
            clone_url, temp_dir,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
        
        if result.returncode != 0:
            # Fallback: the server may not support partial clone (older GitLab).
            # Use a shallow clone with enough depth for typical MR histories.
            logger.warning(
                "Partial clone (--filter=blob:none) failed, "
                "falling back to shallow clone (--depth=50)"
            )
            # Clean up the failed attempt
            shutil.rmtree(temp_dir, ignore_errors=True)
            temp_dir = tempfile.mkdtemp(prefix="code_review_", dir=prep_res.get("clone_parent_dir"))
            
            cmd = ['git', 'clone', '--depth=50', clone_url, temp_dir]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
            if result.returncode != 0:
                shutil.rmtree(temp_dir, ignore_errors=True)
                raise Exception(f"Failed to clone repository: {result.stderr}")
        
        # Fetch only the two branches we need instead of all refs.
        branches_to_fetch = [b for b in {source_branch, target_branch} if b]
        logger.info(f"Fetching branches: {branches_to_fetch}")
        if not branches_to_fetch:
            raise ValueError("source_branch and target_branch must be set before cloning")
        cmd = [
            'git', '-C', temp_dir,
            'fetch', 'origin',
        ] + branches_to_fetch
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        if result.returncode != 0:
            # Some branch names may not exist on remote yet; fetch individually
            for branch in branches_to_fetch:
                cmd = ['git', '-C', temp_dir, 'fetch', 'origin', branch]
                r = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
                if r.returncode != 0:
                    logger.warning(f"Failed to fetch branch '{branch}' from origin: {r.stderr.strip()}")
        
        # Create local tracking branches so that bare branch names work in
        # git diff / merge-base commands (the LLM prompt uses the bare name).
        for branch in branches_to_fetch:
            cmd = ['git', '-C', temp_dir, 'branch', '--track', branch, f'origin/{branch}']
            subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        
        logger.info(f"Repository cloned successfully to {temp_dir}")
        
        return {
            "local_repo_path": temp_dir
        }
    
    def post(self, shared, prep_res, exec_res):
        shared["local_repo_path"] = exec_res["local_repo_path"]


class CheckoutBranch(Node):
    """Checkout the branch which needs to be reviewed."""
    
    def prep(self, shared):
        local_repo_path = shared.get("local_repo_path")
        source_branch = shared.get("source_branch")
        target_branch = shared.get("target_branch", "main")
        
        if not local_repo_path:
            raise ValueError("local_repo_path is required")
        if not source_branch:
            raise ValueError("source_branch is required for code review")
        
        return {
            "local_repo_path": local_repo_path,
            "source_branch": source_branch,
            "target_branch": target_branch
        }
    
    def _resolve_target_branch(self, local_repo_path, target_branch):
        """Verify target branch exists on remote."""
        cmd = ['git', '-C', local_repo_path, 'rev-parse', '--verify', f'origin/{target_branch}']
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode == 0:
            return target_branch

        raise ValueError(f"Target branch '{target_branch}' not found on remote")

    def exec(self, prep_res):
        local_repo_path = prep_res["local_repo_path"]
        source_branch = prep_res["source_branch"]
        target_branch = prep_res["target_branch"]
        
        # CloneRepository already fetches source + target branches, so we
        # skip the expensive `git fetch --all`.  Only fetch the specific
        # target branch if it wasn't already fetched (e.g. local-dir mode).
        cmd = ['git', '-C', local_repo_path, 'rev-parse', '--verify', f'origin/{target_branch}']
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode != 0:
            logger.info(f"Target branch origin/{target_branch} not found locally, fetching...")
            cmd = ['git', '-C', local_repo_path, 'fetch', 'origin', target_branch]
            subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        
        # Resolve the target branch (validate it exists, auto-detect if needed)
        target_branch = self._resolve_target_branch(local_repo_path, target_branch)
        logger.info(f"Resolved target branch: {target_branch}")

        # Checkout the source branch for review
        logger.info(f"Checking out source branch: {source_branch}")
        cmd = ['git', '-C', local_repo_path, 'checkout', source_branch]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        
        if result.returncode != 0:
            # Try with origin prefix
            cmd = ['git', '-C', local_repo_path, 'checkout', '-b', source_branch, f'origin/{source_branch}']
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            
            if result.returncode != 0:
                raise Exception(f"Failed to checkout branch {source_branch}: {result.stderr}")
        
        logger.info(f"Checked out branch: {source_branch}")
        
        return {
            "source_branch": source_branch,
            "target_branch": target_branch
        }
    
    def post(self, shared, prep_res, exec_res):
        shared["source_branch"] = exec_res["source_branch"]
        shared["target_branch"] = exec_res["target_branch"]


class FetchBestPractices(Node):
    """Get the best practices for the service or group from GitLab wiki and add it in the local repo for reference."""
    
    def prep(self, shared):
        local_repo_path = shared.get("local_repo_path")
        gitlab_token = shared.get("gitlab_token")
        gitlab_url = shared.get("gitlab_url")
        repo_url = shared.get("repo_url")
        project_id = shared.get("project_id")
        
        return {
            "local_repo_path": local_repo_path,
            "gitlab_token": gitlab_token,
            "gitlab_url": gitlab_url,
            "repo_url": repo_url,
            "project_id": project_id
        }
    
    def exec(self, prep_res):
        local_repo_path = prep_res["local_repo_path"]
        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        repo_url = prep_res["repo_url"]
        project_id = prep_res["project_id"]
        
        best_practices_content = ""
        best_practices_file_path = os.path.join(local_repo_path, ".code_review_best_practices.md")
        
        if not gitlab_token:
            logger.warning("No GitLab token provided - skipping best practices")
            best_practices_content = "# Best Practices\n\nNo best practices available."
        else:
            try:
                gl = scm.connect(gitlab_url, private_token=gitlab_token)
                gl.auth()
                logger.info(f"Connected to GitLab: {gitlab_url}")
                
                # Try project wiki first
                logger.info("Fetching best practices from project wiki...")
                best_practices_content = self._fetch_from_project_wiki(gl, repo_url, project_id)
                
                # Try group wiki if project wiki not found
                if not best_practices_content and repo_url:
                    logger.info("Fetching best practices from group wiki...")
                    best_practices_content = self._fetch_from_group_wiki(gl, repo_url)
                
                if not best_practices_content:
                    logger.warning("No 'best_practices' wiki page found in project or group")
                    best_practices_content = "# Best Practices\n\nNo best practices wiki page found."
            except Exception as e:
                logger.warning(f"Failed to fetch best practices: {e}")
                best_practices_content = "# Best Practices\n\nFailed to fetch best practices."
        
        # Write best practices to local repo
        with open(best_practices_file_path, 'w', encoding='utf-8') as f:
            f.write(best_practices_content)
        
        logger.info(f"Best practices saved to: {best_practices_file_path}")
        
        return {
            "best_practices_content": best_practices_content,
            "best_practices_file_path": best_practices_file_path
        }
    
    def _fetch_from_project_wiki(self, gl, repo_url, project_id):
        """Fetch best practices from the project's wiki page named 'best_practices'."""
        try:
            project = None
            if project_id:
                project = gl.projects.get(project_id)
            elif repo_url:
                from urllib.parse import urlparse
                project_path = urlparse(repo_url).path.strip('/').replace('.git', '')
                project = gl.projects.get(project_path)
            
            if not project:
                return ""
            
            try:
                wiki_page = project.wikis.get("best_practices")
                logger.info("Found best practices in project wiki")
                return wiki_page.content
            except gitlab.exceptions.GitlabGetError:
                return ""
        except Exception as e:
            logger.warning(f"Failed to fetch from project wiki: {e}")
        return ""
    
    def _fetch_from_group_wiki(self, gl, repo_url):
        """Fetch best practices from the group's wiki page named 'best_practices'."""
        try:
            from urllib.parse import urlparse
            project_path = urlparse(repo_url).path.strip('/').replace('.git', '')
            path_parts = project_path.split('/')
            
            if len(path_parts) >= 2:
                group_path = '/'.join(path_parts[:-1])
                try:
                    group = gl.groups.get(group_path)
                    try:
                        wiki_page = group.wikis.get("best_practices")
                        logger.info("Found best practices in group wiki")
                        return wiki_page.content
                    except (gitlab.exceptions.GitlabGetError, AttributeError):
                        return ""
                except gitlab.exceptions.GitlabGetError:
                    pass
        except Exception as e:
            logger.warning(f"Failed to fetch from group wiki: {e}")
        return ""
    
    def post(self, shared, prep_res, exec_res):
        shared["best_practices_content"] = exec_res["best_practices_content"]
        shared["best_practices_file_path"] = exec_res["best_practices_file_path"]


# Pattern to auto-detect Google Docs links in MR descriptions
_GOOGLE_DOC_PATTERN = re.compile(
    r'https://docs\.google\.com/document/d/[a-zA-Z0-9_-]+(?:/edit[^\s)]*)?'
)


_HEADING_HASHES = {
    "HEADING_1": "#",
    "HEADING_2": "##",
    "HEADING_3": "###",
    "HEADING_4": "####",
    "HEADING_5": "#####",
    "HEADING_6": "######",
}


def _table_to_markdown(table_data: list) -> list[str]:
    """Render a list-of-rows (list-of-cell-strings) as a Markdown table.

    Skips rows where every cell is empty (artefact of merged/blank Google Doc rows).
    """
    non_empty_rows = [row for row in table_data if any(str(c).strip() for c in row)]
    if not non_empty_rows:
        return []

    lines = []
    for i, row in enumerate(non_empty_rows):
        lines.append("| " + " | ".join(str(c).replace("\n", " ").strip() for c in row) + " |")
        if i == 0:
            lines.append("| " + " | ".join("---" for _ in row) + " |")
    return lines


def _parse_rfc_response(response: dict) -> str:
    """Convert a structured RFC Central ``DocumentContentResponse`` into Markdown.

    Schema (from RFCServiceHttpImpl / DocumentContentResponse):
      response:
        documentId, rfcId, title
        tabs[]:
          tabId, tabTitle
          elements[]:
            type: PARAGRAPH | HEADING | LIST_ITEM | TABLE
            text: str          (PARAGRAPH, HEADING, LIST_ITEM)
            headingLevel: str  (HEADING only — e.g. "HEADING_1" .. "HEADING_6")
            tableData: [[str]] (TABLE only)
    """
    parts: list[str] = []

    title = response.get("title", "").strip()
    if title:
        parts.append(f"# {title}")

    tabs = response.get("tabs", [])
    for tab in tabs:
        tab_title = (tab.get("tabTitle") or "").strip()
        # Only emit a tab heading when there are multiple tabs or it has a real name
        if tab_title and (len(tabs) > 1 or tab_title.lower() not in ("tab 1", "")):
            parts.append(f"\n---\n## {tab_title}")

        prev_type = None
        for element in tab.get("elements", []):
            etype = element.get("type", "")
            text = (element.get("text") or "").strip()

            if etype == "HEADING":
                level = element.get("headingLevel", "HEADING_2")
                hashes = _HEADING_HASHES.get(level, "##")
                parts.append(f"\n{hashes} {text}")

            elif etype == "PARAGRAPH":
                if text:
                    parts.append(text)

            elif etype == "LIST_ITEM":
                if text:
                    # Add blank line before first bullet in a new list block
                    if prev_type != "LIST_ITEM":
                        parts.append("")
                    parts.append(f"- {text}")

            elif etype == "TABLE":
                table_lines = _table_to_markdown(element.get("tableData", []))
                if table_lines:
                    parts.append("")
                    parts.extend(table_lines)
                    parts.append("")

            prev_type = etype

    return "\n".join(parts).strip()


class FetchRFCContent(Node):
    """Fetch RFC/design document content from RFC Central and save it for review context."""

    def prep(self, shared):
        return {
            "rfc_url": shared.get("rfc_url", ""),
            "rfc_api_url": shared.get("rfc_api_url", ""),
            "mr_description": shared.get("mr_description", ""),
            "local_repo_path": shared.get("local_repo_path", "."),
        }

    def exec(self, prep_res):
        import requests

        rfc_url = prep_res["rfc_url"]
        rfc_api_url = prep_res["rfc_api_url"]
        mr_description = prep_res["mr_description"]
        local_repo_path = prep_res["local_repo_path"]

        # If no explicit URL, try to auto-detect from MR description
        if not rfc_url and mr_description:
            match = _GOOGLE_DOC_PATTERN.search(mr_description)
            if match:
                rfc_url = match.group(0)
                logger.info(f"Auto-detected RFC document URL from MR description: {rfc_url}")

        if not rfc_url:
            logger.info("No RFC document URL provided — skipping RFC context")
            return {"rfc_content": "", "rfc_file_path": ""}

        if not rfc_api_url:
            logger.warning("RFC_CENTRAL_API_URL is not configured — skipping RFC fetch")
            return {"rfc_content": "", "rfc_file_path": ""}

        token = os.environ.get("RFC_API_TOKEN", "").strip()

        # Call RFC Central API
        api_endpoint = f"{rfc_api_url.rstrip('/')}/rfcs/v1/content"
        logger.info(f"Fetching RFC content from: {api_endpoint}")

        try:
            resp = requests.post(
                api_endpoint,
                json={"documentUrl": rfc_url},
                headers={
                    "Accept": "application/json",
                    "Content-Type": "application/json",
                    **({"Authorization": f"Bearer {token}"} if token else {}),
                },
                timeout=30,
            )
            resp.raise_for_status()

            data = resp.json()
            rfc_content = data.get("data", data.get("content", ""))
            if isinstance(rfc_content, dict):
                rfc_content = rfc_content.get("content", "")

            # Handle structured response: {"response": {"tabs": [{"elements": [...]}]}}
            if not rfc_content and isinstance(data.get("response"), dict):
                rfc_content = _parse_rfc_response(data["response"])

            if not rfc_content:
                logger.warning("RFC Central returned empty content")
                return {"rfc_content": "", "rfc_file_path": ""}

            logger.info(f"RFC content fetched successfully ({len(rfc_content)} chars)")

        except (requests.RequestException, ValueError) as e:
            logger.warning(f"Failed to fetch RFC content: {e}")
            return {"rfc_content": "", "rfc_file_path": ""}

        # Write to local repo for agent filesystem access
        rfc_file_path = os.path.join(local_repo_path, ".rfc_context.md")
        try:
            markdown_content = f"# RFC / Design Document\n\nSource: {rfc_url}\n\n{rfc_content}"
            with open(rfc_file_path, "w", encoding="utf-8") as f:
                f.write(markdown_content)
            logger.info(f"RFC context saved to: {rfc_file_path}")
        except OSError as e:
            logger.warning(f"Failed to write RFC file: {e}")
            rfc_file_path = ""

        return {"rfc_content": rfc_content, "rfc_file_path": rfc_file_path}

    def post(self, shared, prep_res, exec_res):
        shared["rfc_content"] = exec_res.get("rfc_content", "")
        shared["rfc_file_path"] = exec_res.get("rfc_file_path", "")


class GetChangedFiles(Node):
    """Get the changed files via git command."""
    
    def prep(self, shared):
        local_repo_path = shared.get("local_repo_path")
        source_branch = shared.get("source_branch")
        target_branch = shared.get("target_branch", "develop")
        
        return {
            "local_repo_path": local_repo_path,
            "source_branch": source_branch,
            "target_branch": target_branch
        }
    
    def exec(self, prep_res):
        local_repo_path = prep_res["local_repo_path"]
        source_branch = prep_res["source_branch"]
        target_branch = prep_res["target_branch"]
        
        # Get the merge base between source and target branches
        logger.info(f"Getting diff between {target_branch} and {source_branch}")
        
        # Determine the correct target ref to use
        merge_base = self._find_merge_base(local_repo_path, target_branch, source_branch)
        
        if not merge_base:
            # List available remote branches for debugging
            cmd = ['git', '-C', local_repo_path, 'branch', '-r', '--format=%(refname:short)']
            branch_result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            available = branch_result.stdout.strip() if branch_result.returncode == 0 else "(unable to list)"
            raise Exception(
                f"Failed to determine merge base: could not find target branch '{target_branch}'. "
                f"Available remote branches: {available}"
            )
        
        logger.info(f"Using merge base: {merge_base}")
        
        # Get list of changed files with their status
        cmd = ['git', '-C', local_repo_path, 'diff', '--name-status', merge_base, 'HEAD']
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        
        if result.returncode != 0:
            raise Exception(f"Failed to get changed files: {result.stderr}")
        
        changed_files = []
        for line in result.stdout.strip().split('\n'):
            if not line:
                continue
            parts = line.split('\t')
            if len(parts) >= 2:
                status = parts[0][0]  # A (added), M (modified), D (deleted), R (renamed)
                file_path = parts[-1]  # Use last part for renamed files
                
                changed_files.append({
                    "path": file_path,
                    "status": status
                })
        
        logger.info(f"Found {len(changed_files)} changed files")

        # Reject very large MRs to control LLM cost
        MAX_CHANGED_FILES = int(os.environ.get("MAX_CHANGED_FILES", "200"))
        if len(changed_files) > MAX_CHANGED_FILES:
            raise ValueError(
                f"This MR changes {len(changed_files)} files, which exceeds the "
                f"limit of {MAX_CHANGED_FILES}. Agentic code review support is "
                f"currently limited for very large MRs due to LLM token costing. "
                f"Please split the MR into smaller pieces; support will resume "
                f"after optimizations."
            )

        # Pre-stage per-file diffs so the agent can read them directly
        # instead of running git diff commands (saves many agentic turns).
        diffs_dir = self._stage_diffs(local_repo_path, merge_base, changed_files)
        
        # Get commit SHAs for inline comment positioning
        base_sha, head_sha, start_sha = self._get_commit_shas(
            local_repo_path, target_branch, source_branch
        )
        
        return {
            "changed_files": changed_files,
            "merge_base": merge_base,
            "base_sha": base_sha,
            "head_sha": head_sha,
            "start_sha": start_sha,
            "diffs_dir": diffs_dir,
        }
    
    def _stage_diffs(self, local_repo_path, merge_base, changed_files):
        """Generate per-file diffs and save them to a .diffs/ directory.

        This avoids the agent having to run ``git diff`` itself, which on large
        MRs causes many pagination turns (head/sed) due to output size limits.
        The agent can instead ``cat .diffs/<file>.diff`` in a single turn.
        """
        diffs_dir = os.path.join(local_repo_path, ".diffs")
        os.makedirs(diffs_dir, exist_ok=True)

        staged_count = 0
        for f in changed_files:
            file_path = f["path"]
            status = f["status"]

            # Skip deleted files — there's no new content to review
            if status == "D":
                continue

            cmd = [
                'git', '-C', local_repo_path,
                'diff', merge_base, 'HEAD', '--', file_path,
            ]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            if result.returncode != 0 or not result.stdout.strip():
                continue

            # Mirror the directory structure so paths stay intuitive
            diff_file = os.path.join(diffs_dir, file_path + ".diff")
            os.makedirs(os.path.dirname(diff_file), exist_ok=True)

            with open(diff_file, 'w', encoding='utf-8') as fh:
                fh.write(result.stdout)
            staged_count += 1

        logger.info(f"Pre-staged {staged_count} diff files in {diffs_dir}")
        return diffs_dir

    def _get_commit_shas(self, local_repo_path, target_branch, source_branch):
        """Get commit SHAs for positioning inline comments on GitLab."""
        base_sha = None
        head_sha = None
        start_sha = None
        
        try:
            # Get HEAD SHA (source branch)
            cmd = ['git', '-C', local_repo_path, 'rev-parse', f'origin/{source_branch}']
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            if result.returncode == 0:
                head_sha = result.stdout.strip()
            
            # Get BASE SHA (target branch)
            cmd = ['git', '-C', local_repo_path, 'rev-parse', f'origin/{target_branch}']
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            if result.returncode == 0:
                base_sha = result.stdout.strip()
            
            # Get START SHA (merge-base)
            if base_sha and head_sha:
                cmd = ['git', '-C', local_repo_path, 'merge-base', base_sha, head_sha]
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
                if result.returncode == 0:
                    start_sha = result.stdout.strip()
            
            logger.info(f"Commit SHAs: base={base_sha[:8]}, head={head_sha[:8]}, start={start_sha[:8] if start_sha else 'N/A'}")
            
        except Exception as e:
            logger.warning(f"Could not get commit SHAs: {e}")
        
        return base_sha, head_sha, start_sha
    
    def _find_merge_base(self, local_repo_path, target_branch, source_branch):
        """Find the merge base between source and target branches.
        
        Uses HEAD as the source ref (source branch is already checked out)
        and tries origin/<target> then local <target>.
        """
        # Try origin/<target_branch>
        ref = f'origin/{target_branch}'
        cmd = ['git', '-C', local_repo_path, 'merge-base', ref, 'HEAD']
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode == 0:
            return result.stdout.strip()

        # Try local <target_branch>
        cmd = ['git', '-C', local_repo_path, 'merge-base', target_branch, 'HEAD']
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode == 0:
            return result.stdout.strip()

        logger.warning(f"Could not determine merge base for target branch '{target_branch}'")
        return None
    
    def post(self, shared, prep_res, exec_res):
        shared["changed_files"] = exec_res["changed_files"]
        shared["merge_base"] = exec_res["merge_base"]
        shared["base_sha"] = exec_res["base_sha"]
        shared["head_sha"] = exec_res["head_sha"]
        shared["start_sha"] = exec_res["start_sha"]
        shared["diffs_dir"] = exec_res.get("diffs_dir")


class GetImpactContext(Node):
    """Build code graph and get blast-radius context for changed files.

    Uses code-review-graph to build a structural map of the codebase and
    compute the minimal set of files the agent needs to read for review.
    This reduces token consumption by 38x-528x according to benchmarks.
    """

    # Adjust based on CI timeout budget and repo size
    BUILD_TIMEOUT = int(os.getenv("CRG_BUILD_TIMEOUT", "600"))
    DETECT_TIMEOUT = int(os.getenv("CRG_DETECT_TIMEOUT", "60"))
    MAX_AFFECTED_FILES = int(os.getenv("CRG_MAX_AFFECTED_FILES", "100"))

    # Extensions that are structurally low-risk and can be deprioritised
    LOW_RISK_EXTENSIONS = (
        ".xml", ".json", ".png", ".jpg", ".jpeg", ".gif",
        ".svg", ".webp", ".ttf", ".otf", ".woff", ".woff2",
        ".md", ".txt", ".properties", ".gradle", ".pro",
    )

    # Path substrings that indicate generated / boilerplate / asset content
    LOW_RISK_PATTERNS = (
        "/res/drawable", "/res/mipmap", "/res/font", "/res/raw",
        "/res/values", "/res/color", "/res/anim", "/assets/",
        "/manifests/", "/generated/", "schemas/", "baseline-prof",
        "lint-baseline",
    )

    # Minimum number of non-test code files we must keep after filtering
    MIN_CORE_FILES = 3

    # Only engage smart-filter when MR changes more than this many files
    FILTER_FILE_THRESHOLD = 10

    def prep(self, shared):
        return {
            "local_repo_path": shared.get("local_repo_path"),
            "changed_files": shared.get("changed_files", []),
            "enable_code_graph": shared.get("enable_code_graph", True),
            "merge_base": shared.get("merge_base"),
            "source_branch": shared.get("source_branch"),
            "target_branch": shared.get("target_branch"),
        }

    def exec(self, prep_res):
        repo_path = prep_res["local_repo_path"]
        changed_files = prep_res["changed_files"]
        enabled = prep_res["enable_code_graph"]

        # Bail out gracefully when disabled or no files changed
        if not enabled:
            logger.info("Code graph analysis disabled (enable_code_graph=False)")
            return self._empty_result()

        if not changed_files:
            logger.info("No changed files — skipping impact radius analysis")
            return self._empty_result()

        # Verify code-review-graph CLI is available
        crg_bin = shutil.which("code-review-graph")
        if crg_bin is None:
            logger.warning(
                "code-review-graph CLI not found. Install with: "
                "pip install code-review-graph"
            )
            return self._empty_result()

        try:
            merge_base = prep_res.get("merge_base")

            # 1. Build (or update) the graph for this repo
            graph_dir = self._build_graph(repo_path)
            if not graph_dir:
                return self._empty_result()

            # 2. Run detect-changes to get blast radius + token savings
            impact_data = self._detect_changes(repo_path, merge_base, changed_files)

            # 3. Collect affected file paths from the blast radius
            affected_files = self._collect_affected_files(
                repo_path, merge_base, changed_files, impact_data
            )

            # 4. Smart-filter: deprioritise structurally low-risk files
            #    so downstream prompts are smaller but no less safe
            filtered_changed_files, skipped_files = self._filter_changed_files(
                changed_files
            )

            # 5. Summarise for downstream prompts
            summary = self._build_summary(
                filtered_changed_files, skipped_files, affected_files, impact_data
            )

            return {
                "impact_context": summary,
                "graph_built": True,
                "affected_files": affected_files,
                "token_savings": impact_data.get("token_savings", {}),
                "filtered_changed_files": filtered_changed_files,
                "skipped_files": skipped_files,
            }

        except Exception as exc:
            logger.warning(f"Impact radius analysis failed: {exc}")
            return self._empty_result()

    def post(self, shared, prep_res, exec_res):
        shared["impact_context"] = exec_res.get("impact_context", "")
        shared["code_graph_built"] = exec_res.get("graph_built", False)
        shared["affected_files"] = exec_res.get("affected_files", [])
        shared["token_savings"] = exec_res.get("token_savings", {})

        # Propagate filtered file list to downstream nodes so review
        # prompts only reference files that matter structurally.
        filtered = exec_res.get("filtered_changed_files")
        if filtered is not None:
            shared["changed_files"] = filtered
            skipped = exec_res.get("skipped_files", [])
            if skipped:
                logger.info(
                    f"Downstream review scoped to {len(filtered)} files "
                    f"({len(skipped)} low-risk files deprioritised)"
                )

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    @classmethod
    def _empty_result(cls):
        return {
            "impact_context": "",
            "graph_built": False,
            "affected_files": [],
            "token_savings": {},
            "filtered_changed_files": None,
            "skipped_files": [],
        }

    @classmethod
    def _is_low_risk_file(cls, file_dict: dict) -> bool:
        """Return True if the file can be safely deprioritised for deep review.

        Criteria (all must match):
          1. Not a test file.
          2. Has a low-risk extension OR lives in a low-risk directory.
        """
        path = file_dict.get("path", "")
        if not path:
            return False

        # Never skip test files — they still need review
        lower = path.lower()
        test_indicators = ("/test/", "/tests/", "_test", "test_", ".spec.")
        if any(t in lower for t in test_indicators):
            return False

        # Check extension
        if any(lower.endswith(ext) for ext in cls.LOW_RISK_EXTENSIONS):
            return True

        # Check directory pattern
        if any(pattern in lower for pattern in cls.LOW_RISK_PATTERNS):
            return True

        return False

    @classmethod
    def _filter_changed_files(cls, changed_files: list[dict]) -> tuple[list[dict], list[dict]]:
        """Split changed_files into (kept_for_deep_review, deprioritised/skipped).

        We only skip low-risk files when the MR is large enough that the filter
        provides meaningful savings (above FILTER_FILE_THRESHOLD). We also
        enforce a minimum core file floor so tiny MRs still get full review.
        """
        if len(changed_files) <= cls.FILTER_FILE_THRESHOLD:
            return changed_files, []

        # Partition into low-risk vs kept
        kept: list[dict] = []
        skipped: list[dict] = []
        for f in changed_files:
            if cls._is_low_risk_file(f):
                skipped.append(f)
            else:
                kept.append(f)

        # Count non-test code files after filtering
        test_indicators = ("/test/", "/tests/", "_test", "test_", ".spec.")
        core_kept = [
            f for f in kept
            if not any(t in f.get("path", "").lower() for t in test_indicators)
        ]

        # Safety valve: if filtering would leave us with fewer than MIN_CORE_FILES,
        # return everything (don't filter this MR)
        if len(core_kept) < cls.MIN_CORE_FILES and len(skipped) > 0:
            logger.info(
                f"Smart-filter would drop too many core files "
                f"({len(core_kept)} left < {cls.MIN_CORE_FILES}); reverting to full set"
            )
            return changed_files, []

        logger.info(
            f"Smart-filter: kept {len(kept)} files for deep review, "
            f"skipped {len(skipped)} low-risk files"
        )
        return kept, skipped

    def _build_graph(self, repo_path: str) -> str | None:
        """Run `code-review-graph build` in the repo. Returns graph dir or None."""
        if not repo_path or not os.path.isdir(repo_path):
            return None

        cmd = ["code-review-graph", "build", "--repo", repo_path]
        logger.info(f"Building code graph for {repo_path} …")
        result = subprocess.run(
            cmd, capture_output=True, text=True, timeout=self.BUILD_TIMEOUT
        )

        if result.returncode != 0:
            logger.warning(
                f"code-review-graph build failed (exit={result.returncode}): "
                f"{result.stderr[:250]}"
            )
            return None

        graph_dir = os.path.join(repo_path, ".code-review-graph")
        logger.info(f"Code graph built → {graph_dir}")
        return graph_dir

    def _detect_changes(self, repo_path: str, merge_base: str | None, changed_files: list[dict]) -> dict:
        """Run `code-review-graph detect-changes --base <merge-base> --brief`.

        The --base flag tells detect-changes to compare HEAD against the merge
        base (the commit where the branch diverged). This produces the actual
        blast radius of the MR changes.
        """
        cmd = [
            "code-review-graph",
            "detect-changes",
            "--repo", repo_path,
            "--brief",
        ]
        # Pass merge base so detect-changes compares against the original code
        if merge_base:
            cmd.extend(["--base", merge_base])

        logger.info(f"Running detect-changes with base={merge_base or 'HEAD~1'} ...")
        result = subprocess.run(
            cmd, capture_output=True, text=True, timeout=self.DETECT_TIMEOUT
        )

        if result.returncode != 0:
            logger.warning(
                f"detect-changes failed (exit={result.returncode}): "
                f"{result.stderr[:500]}"
            )
            return {}

        if not result.stdout.strip():
            logger.warning("detect-changes returned empty output")
            return {}

        logger.info(f"detect-changes output:\n{result.stdout[:800]}")
        return self._parse_detect_output(result.stdout)

    def _parse_detect_output(self, stdout: str) -> dict:
        """Extract blast radius and token-savings from detect-changes output."""
        import re

        savings = {}

        lines = stdout.splitlines()
        for line in lines:
            stripped = line.strip("│ ┌─┘└┐ ")
            if "Saved:" in stripped and "tokens" in stripped:
                try:
                    savings["saved_tokens_str"] = stripped.split("Saved:")[1].strip()
                except IndexError:
                    pass
            elif "Full context would be:" in stripped:
                savings["full_context"] = stripped.split(":")[-1].strip()
            elif "Graph context used:" in stripped:
                savings["graph_context"] = stripped.split(":")[-1].strip()
            elif "Analyzed" in stripped and "changed file" in stripped:
                # Parse header: "Analyzed 30 changed file(s):"
                try:
                    m = re.search(r'Analyzed\s+(\d+)', stripped)
                    if m:
                        savings["analyzed_files"] = int(m.group(1))
                except Exception:
                    pass
            elif "changed function" in stripped.lower():
                # "  - 30 changed function(s)/class(es)"
                try:
                    m = re.search(r'(\d+)\s+changed function', stripped)
                    if m:
                        savings["changed_functions"] = int(m.group(1))
                except Exception:
                    pass
            elif "test gap" in stripped.lower():
                try:
                    m = re.search(r'(\d+)\s+test gap', stripped)
                    if m:
                        savings["test_gaps"] = int(m.group(1))
                except Exception:
                    pass
            elif "risk score" in stripped.lower():
                try:
                    m = re.search(r'risk score[:\s]+([\d.]+)', stripped, re.I)
                    if m:
                        savings["risk_score"] = float(m.group(1))
                except Exception:
                    pass

        return {
            "token_savings": savings,
            "raw_output": stdout,
        }

    def _collect_affected_files(
        self, repo_path: str, merge_base: str | None, changed_files: list[dict], impact_data: dict
    ) -> list[str]:
        """Collect affected files from changed files + blast radius data.

        We rely on detect-changes output plus git diff to determine the blast
        radius. The graph query tools (like query-graph) are not available in
        all versions of code-review-graph.
        """
        affected: set[str] = {cf.get("path", "") for cf in changed_files if cf.get("path")}

        # Use git to find which files changed (as a sanity check)
        if merge_base and repo_path and os.path.isdir(repo_path):
            try:
                cmd = ["git", "-C", repo_path, "diff", "--name-only", merge_base, "HEAD"]
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
                if result.returncode == 0:
                    for line in result.stdout.strip().split("\n"):
                        if line:
                            affected.add(line)
            except Exception:
                pass

        # Cap to avoid overwhelming the agent
        if len(affected) > self.MAX_AFFECTED_FILES:
            logger.warning(
                f"Blast radius produced {len(affected)} files; "
                f"capping to {self.MAX_AFFECTED_FILES} most-relevant"
            )
            changed_paths = {cf["path"] for cf in changed_files if cf.get("path")}
            ordered = sorted(
                affected,
                key=lambda p: (p not in changed_paths, p),
            )
            affected = set(ordered[: self.MAX_AFFECTED_FILES])

        return sorted(affected)

    def _build_summary(
        self,
        filtered_changed_files: list[dict],
        skipped_files: list[dict],
        affected_files: list[str],
        impact_data: dict,
    ) -> str:
        """Build a CONCISE summary (< 500 chars) for downstream prompts.

        The full file listing adds bloat. We provide a compact summary so the
        agent knows what to focus on without reading a wall of text.
        """
        savings = impact_data.get("token_savings", {})
        lines = [
            "## Code Graph Impact Analysis",
            "",
            f"- Files selected for deep review: {len(filtered_changed_files)}",
        ]

        if skipped_files:
            lines.append(
                f"- Deprioritised (low-risk assets): {len(skipped_files)} "
                f"({', '.join(f['path'] for f in skipped_files[:3])}"
                f"{', ...' if len(skipped_files) > 3 else ''})"
            )

        # Add blast radius stats if available
        if savings.get("analyzed_files"):
            lines.append(f"- Analyzed by graph: {savings['analyzed_files']} files")
        if savings.get("changed_functions"):
            lines.append(f"- Changed functions/classes: {savings['changed_functions']}")
        if savings.get("test_gaps"):
            lines.append(f"- Test gaps detected: {savings['test_gaps']}")
        if savings.get("risk_score"):
            lines.append(f"- Risk score: {savings['risk_score']}/1.0")

        # Token savings (CRG + our smart-filter)
        if savings.get("saved_tokens_str"):
            lines.append(f"- Token savings: {savings['saved_tokens_str']}")

        # Shorten file list to first 15 of affected (non-changed) dependencies
        changed_set = {cf["path"] for cf in filtered_changed_files}
        extra_files = [f for f in affected_files if f not in changed_set]
        if extra_files:
            shown = extra_files[:15]
            remainder = len(extra_files) - len(shown)
            lines += ["", f"### Affected dependencies ({len(extra_files)} total):"]
            for f in shown:
                lines.append(f"  - {f}")
            if remainder > 0:
                lines.append(f"  - ... and {remainder} more")

        # Instructions for agent
        lines += [
            "",
            "**Instructions:** Prioritize review of CHANGED FILES listed below. "
            "When reviewing, check if changes to these files break any of the listed dependencies. "
            "Deprioritised files are low-risk assets (images, fonts, XML) and do NOT need review. "
            "Pay special attention to test gaps and high-risk areas.",
        ]

        summary = "\n".join(lines)
        # Hard cap at 2,000 chars to prevent prompt bloat
        if len(summary) > 2000:
            summary = summary[:1900] + "\n\n... (truncated)"
        return summary


class FetchJiraTickets(Node):
    """Extract Jira ticket IDs from MR metadata and fetch their details via Jira Cloud REST API v3.

    This node is a **no-op** when Jira is not configured (no ``jira_url``,
    ``jira_token``, or ``jira_cloud_id`` in shared).  It populates
    ``shared["related_tickets"]`` with a list of ticket dicts that downstream
    prompts can use for context and compliance checking.
    """

    # Standard Jira ticket pattern: 2-10 uppercase letters, hyphen, digits
    _TICKET_RE = re.compile(r"\b[A-Z]{2,10}-\d+\b")

    # Sections in a Jira description that typically hold requirements
    _REQ_PATTERNS = [
        re.compile(p, re.DOTALL | re.MULTILINE) for p in (
            r"(?i)acceptance criteria[:\s]*\n(.*?)(?=\n\n|\n[A-Z][a-z]|\Z)",
            r"(?i)requirements[:\s]*\n(.*?)(?=\n\n|\n[A-Z][a-z]|\Z)",
            r"(?i)definition of done[:\s]*\n(.*?)(?=\n\n|\n[A-Z][a-z]|\Z)",
            r"(?i)dod[:\s]*\n(.*?)(?=\n\n|\n[A-Z][a-z]|\Z)",
            r"(?i)user stor(?:y|ies)[:\s]*\n(.*?)(?=\n\n|\n[A-Z][a-z]|\Z)",
            r"(?i)tasks?[:\s]*\n(.*?)(?=\n\n|\n[A-Z][a-z]|\Z)",
        )
    ]

    # ------------------------------------------------------------------ prep
    def prep(self, shared):
        return {
            "mr_title": shared.get("mr_title", ""),
            "mr_description": shared.get("mr_description", ""),
            "source_branch": shared.get("source_branch", ""),
            "jira_url": shared.get("jira_url", ""),
            "jira_token": shared.get("jira_token", ""),
            "jira_cloud_id": shared.get("jira_cloud_id", ""),
            "jira_browse_url": shared.get("jira_browse_url", ""),
            "jira_tickets": shared.get("jira_tickets", ""),
            "local_repo_path": shared.get("local_repo_path", "."),
        }

    # ------------------------------------------------------------------ exec
    def exec(self, prep_res):
        jira_url = prep_res["jira_url"]
        jira_token = prep_res["jira_token"]
        jira_cloud_id = prep_res["jira_cloud_id"]
        jira_browse_url = prep_res["jira_browse_url"]
        local_repo_path = prep_res["local_repo_path"]

        # --- Extract ticket IDs from MR metadata ---
        ticket_ids = self._extract_ticket_ids(
            prep_res["mr_title"],
            prep_res["mr_description"],
            prep_res["source_branch"],
            prep_res["jira_tickets"],
        )

        if not ticket_ids:
            logger.info("No Jira ticket IDs found in MR metadata")
            return {"related_tickets": [], "jira_file_path": ""}

        logger.info(f"Found Jira ticket IDs: {ticket_ids}")

        if not jira_url or not jira_token or not jira_cloud_id:
            logger.info("Jira Cloud not configured (missing JIRA_URL / JIRA_TOKEN / JIRA_CLOUD_ID) — skipping fetch")
            browse_base = jira_browse_url or jira_url
            return {
                "related_tickets": self._minimal_entries(ticket_ids, browse_base),
                "jira_file_path": "",
            }

        # --- Fetch details from Jira REST API ---
        tickets = self._fetch_tickets(ticket_ids, jira_url, jira_token, jira_cloud_id, jira_browse_url)
        logger.info(f"Fetched details for {len(tickets)} Jira ticket(s)")

        # --- Write Jira context to local repo ---
        jira_file_path = os.path.join(local_repo_path, ".jira_context.md")
        try:
            md_lines = ["# Related Jira Tickets\n"]
            for t in tickets:
                md_lines.append(f"## {t.get('ticket_id', '')} — {t.get('title', '')}")
                md_lines.append(f"**URL:** {t.get('ticket_url', '')}")
                md_lines.append(f"**Status:** {t.get('status', 'Unknown')}")
                md_lines.append(f"**Priority:** {t.get('priority', 'Unknown')}")
                md_lines.append(f"**Type:** {t.get('issue_type', 'Unknown')}")
                if t.get("labels"):
                    md_lines.append(f"**Labels:** {', '.join(t['labels'])}")
                if t.get("requirements"):
                    md_lines.append("\n**Extracted Requirements:**")
                    for req in t["requirements"]:
                        md_lines.append(f"- {req}")
                if t.get("description"):
                    desc = t["description"].strip()
                    if len(desc) > 2000:
                        desc = desc[:2000] + "\n... (truncated)"
                    md_lines.append(f"\n**Description:**\n{desc}")
                md_lines.append("")

            with open(jira_file_path, "w", encoding="utf-8") as f:
                f.write("\n".join(md_lines))
            logger.info(f"Jira context saved to: {jira_file_path}")
        except OSError as e:
            logger.warning(f"Failed to write Jira file: {e}")
            jira_file_path = ""

        return {"related_tickets": tickets, "jira_file_path": jira_file_path}

    # ------------------------------------------------------------------ post
    def post(self, shared, prep_res, exec_res):
        shared["related_tickets"] = exec_res.get("related_tickets", [])
        shared["jira_file_path"] = exec_res.get("jira_file_path", "")

    # ====================================================================
    # Private helpers
    # ====================================================================

    def _extract_ticket_ids(self, *texts):
        """Return de-duplicated Jira ticket IDs found in MR metadata and explicit context."""
        from collections import OrderedDict

        ids = []
        for text in texts:
            ids.extend(self._TICKET_RE.findall(text or ""))
        return list(OrderedDict.fromkeys(ids))

    def _fetch_tickets(self, ticket_ids, jira_url, jira_token, jira_cloud_id, jira_browse_url):
        """Fetch ticket details via Jira Cloud REST API v3."""
        import requests

        jira_token = jira_token.strip()

        # Build auth header — support three formats:
        #   1. "Basic ZGV2eC1..." — pre-formatted, use as-is
        #   2. "user:password"    — plain credentials, encode to base64
        #   3. "ZGV2eC1..."      — raw base64, prepend "Basic "
        if jira_token.lower().startswith("basic"):
            raw_token = jira_token[5:].lstrip()
            auth_header = f"Basic {raw_token}"
        elif ":" in jira_token:
            import base64
            encoded = base64.b64encode(jira_token.encode("ascii")).decode("ascii")
            auth_header = f"Basic {encoded}"
        else:
            auth_header = f"Basic {jira_token}"

        headers = {
            "Authorization": auth_header,
            "Accept": "application/json",
            "Content-Type": "application/json",
        }

        # Determine browse base URL
        browse_base = jira_browse_url or jira_url

        tickets = []
        for tid in ticket_ids:
            try:
                url = f"{jira_url}/ex/jira/{jira_cloud_id}/rest/api/3/issue/{tid}"

                params = {
                    "fields": "summary,description,status,issuetype,assignee,labels,priority,created,updated"
                }
                resp = requests.get(url, headers=headers, params=params, timeout=30)

                if resp.status_code == 200:
                    fields = resp.json().get("fields", {})
                    raw_desc = fields.get("description", "") or ""

                    # In Cloud v3, description is ADF (dict)
                    if isinstance(raw_desc, dict):
                        desc_text = self._adf_to_plain_text(raw_desc)
                    else:
                        desc_text = raw_desc if isinstance(raw_desc, str) else ""

                    ticket = {
                        "ticket_url": f"{browse_base}/browse/{tid}",
                        "ticket_id": tid,
                        "title": fields.get("summary", tid),
                        "description": desc_text,
                        "status": (fields.get("status") or {}).get("name", "Unknown"),
                        "issue_type": (fields.get("issuetype") or {}).get("name", "Unknown"),
                        "priority": (fields.get("priority") or {}).get("name", "Unknown"),
                        "labels": fields.get("labels", []),
                        "assignee": (
                            (fields.get("assignee") or {}).get("displayName", "Unassigned")
                        ),
                        "requirements": self._extract_requirements(desc_text),
                    }
                    tickets.append(ticket)
                else:
                    logger.warning(f"Jira API returned {resp.status_code} for {tid}")
                    tickets.append(self._error_entry(tid, browse_base, resp.status_code))
            except Exception as e:
                logger.warning(f"Failed to fetch Jira ticket {tid}: {e}")
                tickets.append(self._error_entry(tid, browse_base, str(e)))

        return tickets

    @staticmethod
    def _adf_to_plain_text(adf_doc):
        """Recursively extract plain text from an Atlassian Document Format (ADF) object.

        ADF is the structured JSON format used by Jira Cloud API v3 for rich-text
        fields like description and comments.

        Heading nodes are emitted as ``\\n\\n{text}:\\n`` so that the section-based
        regex patterns in ``_extract_requirements`` can reliably match them.
        """
        if not adf_doc or not isinstance(adf_doc, dict):
            return ""

        parts = []

        def _walk(node):
            if isinstance(node, str):
                parts.append(node)
                return
            if not isinstance(node, dict):
                return

            # Text leaf nodes
            if node.get("type") == "text":
                parts.append(node.get("text", ""))
                return

            # Mention → display text
            if node.get("type") == "mention":
                parts.append(node.get("attrs", {}).get("text") or "")
                return

            # Hard break → newline
            if node.get("type") == "hardBreak":
                parts.append("\n")
                return

            node_type = node.get("type", "")

            # Heading nodes: emit double-newline prefix + text + colon + newline
            # so that _extract_requirements regex patterns can match reliably.
            if node_type == "heading":
                parts.append("\n\n")
                for child in node.get("content", []):
                    _walk(child)
                parts.append(":\n")
                return

            # Block-level nodes that should end with a newline
            block_types = {"paragraph", "bulletList", "orderedList",
                           "listItem", "blockquote", "codeBlock", "table",
                           "tableRow", "tableCell", "tableHeader", "rule",
                           "panel", "decisionList", "decisionItem",
                           "taskList", "taskItem", "mediaSingle"}

            # Recurse into child content
            for child in node.get("content", []):
                _walk(child)

            # Add newline after block-level elements
            if node_type in block_types:
                parts.append("\n")

        _walk(adf_doc)
        return "".join(parts).strip()

    def _extract_requirements(self, description):
        """Mine acceptance criteria / requirements from a Jira description."""
        if not description:
            return []

        reqs = []
        for pattern in self._REQ_PATTERNS:
            for match in pattern.findall(description):
                for line in match.strip().split("\n"):
                    line = re.sub(r"^[-*•]\s*", "", line.strip())
                    line = re.sub(r"^\d+\.\s*", "", line)
                    if line and len(line) > 10:
                        reqs.append(line)

        # Fallback: extract sentences with requirement-like keywords
        if not reqs:
            for sentence in description.split("."):
                sentence = sentence.strip()
                if len(sentence) > 20 and any(
                    kw in sentence.lower()
                    for kw in ("should", "must", "need", "require", "implement", "add", "create", "update")
                ):
                    reqs.append(sentence)

        return reqs[:10]

    @staticmethod
    def _error_entry(ticket_id, browse_base, status):
        return {
            "ticket_url": f"{browse_base}/browse/{ticket_id}" if browse_base else ticket_id,
            "ticket_id": ticket_id,
            "title": f"Could not fetch {ticket_id} ({status})",
            "description": "",
            "status": "Error",
            "labels": [],
            "requirements": [],
        }

    @staticmethod
    def _minimal_entries(ticket_ids, browse_base):
        """Return stub entries when Jira creds are absent."""
        return [
            {
                "ticket_url": f"{browse_base}/browse/{tid}" if browse_base else tid,
                "ticket_id": tid,
                "title": f"Jira Ticket {tid}",
                "description": "",
                "status": "Unknown",
                "labels": [],
                "requirements": [],
            }
            for tid in ticket_ids
        ]


class FetchMRReviewStatus(Node):
    """Fetch AI review status and previous inline review comments from GitLab MR.

    Checks whether the AI bot has posted any inline suggestions on the MR.
    If no inline comments from the bot exist, sets a flag so the reviewer
    guide can prompt the author to run /improve.

    Also collects the full body of all previous inline comment threads so that
    ``AnalyzePreviousCommentStatus`` can assess whether each was addressed.
    """


    def prep(self, shared):
        return {
            "gitlab_token": shared.get("gitlab_token"),
            "gitlab_url": shared.get("gitlab_url", ""),
            "project_id": shared.get("project_id"),
            "merge_request_iid": shared.get("merge_request_iid"),
        }

    def exec(self, prep_res):
        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        project_id = prep_res["project_id"]
        merge_request_iid = prep_res["merge_request_iid"]

        default = {
            "ai_improve_done": False,
            "ai_improve_comment_count": 0,
            "previous_review_comments": [],
            "comment_fetch_succeeded": False,
        }

        if not gitlab_token or not project_id or not merge_request_iid:
            logger.warning("Missing GitLab credentials — skipping MR review status fetch")
            return default

        try:
            gl = scm.connect(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr = project.mergerequests.get(merge_request_iid)

            # ---- 1. Check for inline comments from the AI bot user ----
            discussions = mr.discussions.list(all=True)
            ai_improve_done = False
            ai_improve_comment_count = 0
            review_comments = []

            skipped_system = skipped_general = skipped_bot = skipped_non_actionable = 0

            for discussion in discussions:
                raw_notes = discussion.attributes.get("notes", [])

                # Find the first non-system root note
                root_note = None
                for note in raw_notes:
                    if note.get("system"):
                        skipped_system += 1
                        continue
                    root_note = note
                    break

                if root_note is None:
                    continue

                body = root_note.get("body", "").strip()
                if not body:
                    continue

                # Extract position — inline comments only
                position = root_note.get("position") or {}
                file_path = (
                    position.get("new_path")
                    or position.get("old_path")
                    or ""
                )

                if not file_path:
                    skipped_general += 1
                    continue

                author_data = root_note.get("author", {})
                author_id = author_data.get("id") if author_data else None
                author = author_data.get("username", "unknown") if author_data else "unknown"

                # Check if this inline comment is from the AI bot
                if author_id == get_ai_bot_user_id():
                    ai_improve_done = True
                    ai_improve_comment_count += 1

                # Skip bot / workflow status comments from the review analysis list
                if self._is_bot_comment(body):
                    skipped_bot += 1
                    continue

                # Skip non-actionable acknowledgment comments
                if self._is_non_actionable(body):
                    skipped_non_actionable += 1
                    continue

                new_line = position.get("new_line")
                old_line = position.get("old_line")
                line_number = new_line if new_line is not None else old_line
                # Track whether the comment anchors to the current (new) side of
                # the diff or a removed (old) line, so downstream consumers know
                # whether line_number refers to current file content or to code
                # that no longer exists.
                line_type = "new" if new_line is not None else "old"

                # Collect thread replies for context
                thread_replies = []
                is_past_root = False
                for note in raw_notes:
                    if note.get("system"):
                        continue
                    if not is_past_root:
                        is_past_root = True
                        continue
                    reply_body = note.get("body", "").strip()
                    if not reply_body or self._is_bot_comment(reply_body):
                        continue
                    reply_author = (note.get("author") or {}).get("username", "unknown")
                    thread_replies.append({"author": reply_author, "body": reply_body})

                review_comments.append({
                    "discussion_id": discussion.id,
                    "note_id": root_note.get("id"),
                    "file_path": file_path,
                    "line_number": _safe_int(line_number),
                    "line_type": line_type,
                    "body": body,
                    "author": author,
                    "author_id": author_id,
                    "thread_replies": thread_replies,
                })

            logger.info(
                f"MR review status — AI improve done: {ai_improve_done} "
                f"({ai_improve_comment_count} bot inline comments), "
                f"actionable inline comments: {len(review_comments)} "
                f"(skipped: {skipped_system} system, {skipped_general} general, "
                f"{skipped_bot} bot, {skipped_non_actionable} non-actionable)"
            )

            return {
                "ai_improve_done": ai_improve_done,
                "ai_improve_comment_count": ai_improve_comment_count,
                "previous_review_comments": review_comments,
                "comment_fetch_succeeded": True,
            }

        except gitlab.exceptions.GitlabAuthenticationError as e:
            # Auth failures indicate misconfiguration (bad/expired token), not
            # "no review history" — surface loudly so it isn't mistaken for the
            # latter and silently masked.
            logger.error(f"GitLab authentication failed while fetching MR review status: {e}")
            return default
        except gitlab.exceptions.GitlabHttpError as e:
            status_code = getattr(e, "response_code", None)
            if status_code in (401, 403, 429):
                logger.error(
                    f"GitLab API error (status {status_code}) while fetching MR review status — "
                    f"check token permissions/rate limits: {e}"
                )
            else:
                logger.warning(f"Failed to fetch MR review status: {e}")
            return default
        except Exception as e:
            logger.warning(f"Failed to fetch MR review status: {e}")
            return default

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _is_bot_comment(body: str) -> bool:
        return comment_filters.is_bot_comment(body)

    @staticmethod
    def _is_non_actionable(body: str) -> bool:
        return comment_filters.is_non_actionable(body)

    def post(self, shared, prep_res, exec_res):
        shared["ai_improve_done"] = exec_res["ai_improve_done"]
        shared["ai_improve_comment_count"] = exec_res["ai_improve_comment_count"]
        shared["previous_review_comments"] = exec_res["previous_review_comments"]
        shared["comment_fetch_succeeded"] = exec_res["comment_fetch_succeeded"]


class Cleanup(Node):
    """Clean up temporary files and finalize the acknowledgment comment."""
    
    def prep(self, shared):
        local_repo_path = shared.get("local_repo_path")
        cleanup_enabled = shared.get("cleanup_enabled", True)
        ack_note_id = shared.get("ack_note_id")
        gitlab_token = shared.get("gitlab_token")
        gitlab_url = shared.get("gitlab_url", "https://gitlab.example.com")
        project_id = shared.get("project_id")
        merge_request_iid = shared.get("merge_request_iid")
        dry_run = shared.get("dry_run", False)
        no_suggestions = (
            shared.get("flow_type") == "improve"
            and not (shared.get("review_output_data") or {}).get("comments")
        )
        pipeline_url = shared.get("pipeline_url", "")
        
        return {
            "local_repo_path": local_repo_path,
            "cleanup_enabled": cleanup_enabled,
            "ack_note_id": ack_note_id,
            "gitlab_token": gitlab_token,
            "gitlab_url": gitlab_url,
            "project_id": project_id,
            "merge_request_iid": merge_request_iid,
            "dry_run": dry_run,
            "no_suggestions": no_suggestions,
            "pipeline_url": pipeline_url,
        }
    
    def exec(self, prep_res):
        local_repo_path = prep_res["local_repo_path"]
        cleanup_enabled = prep_res["cleanup_enabled"]
        
        # Keep a visible completion message when /improve found nothing to post.
        ack_deleted = self._finalize_ack_comment(prep_res)
        
        if not cleanup_enabled:
            logger.info("Cleanup disabled - keeping temporary files")
            return {"cleaned_up": False, "ack_deleted": ack_deleted}
        
        if local_repo_path and os.path.exists(local_repo_path):
            logger.info(f"Cleaning up temporary directory: {local_repo_path}")
            try:
                shutil.rmtree(local_repo_path)
                logger.info("Cleanup completed successfully")
                return {"cleaned_up": True, "ack_deleted": ack_deleted}
            except Exception as e:
                logger.error(f"Failed to cleanup: {e}")
                return {"cleaned_up": False, "error": str(e), "ack_deleted": ack_deleted}
        
        return {"cleaned_up": False, "ack_deleted": ack_deleted}
    
    def _finalize_ack_comment(self, prep_res):
        """Complete or delete the acknowledgment posted at the start of the flow."""
        ack_note_id = prep_res.get("ack_note_id")
        gitlab_token = prep_res.get("gitlab_token")
        gitlab_url = prep_res.get("gitlab_url")
        project_id = prep_res.get("project_id")
        merge_request_iid = prep_res.get("merge_request_iid")
        dry_run = prep_res.get("dry_run", False)
        
        if not ack_note_id:
            return False
        
        if dry_run:
            logger.info("Dry run mode - skipping acknowledgment comment finalization")
            return False
        
        if not gitlab_token or not project_id or not merge_request_iid:
            logger.warning("Missing GitLab credentials - cannot delete acknowledgment comment")
            return False
        
        try:
            gl = scm.connect(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr = project.mergerequests.get(merge_request_iid)
            note = mr.notes.get(ack_note_id)
            if prep_res.get("no_suggestions"):
                note.body = (
                    "## ✅ Agentic PR Suggestions Completed\n\n"
                    "Review completed successfully. No actionable suggestions were found."
                )
                if prep_res.get("pipeline_url"):
                    note.body += f"\n\n**Pipeline:** [View pipeline]({prep_res['pipeline_url']})"
                note.save()
                logger.info(f"Updated acknowledgment with completion status (note_id={ack_note_id})")
                return False
            note.delete()
            logger.info(f"Deleted acknowledgment comment (note_id={ack_note_id})")
            return True
        except Exception as e:
            logger.warning(f"Failed to finalize acknowledgment comment: {e}")
            return False
    
    def post(self, shared, prep_res, exec_res):
        shared["cleanup_completed"] = exec_res.get("cleaned_up", False)
        shared["ack_deleted"] = exec_res.get("ack_deleted", False)
