import json
import os
import re
import yaml
import logging
import tempfile
import shutil
import subprocess
import gitlab
from utils import scm
from pocketflow import Node
from utils.call_llm import call_llm_agentic
from utils.config import get_harness_label, get_harness_name
from utils.logger import get_logger
from utils.review_comment_metadata import (
    REVIEW_COMMENT_CATEGORIES,
    parse_review_comment_metadata,
)
from flows.code_reviews.common import comment_filters
from flows.code_reviews.impact.prompts import (
    get_impact_system_message,
    build_impact_analysis_prompt,
)

# Set up logger
logger = get_logger("code_review_impact_nodes_logger")

# Valid comment categories shared with the improve workflow.
COMMENT_CATEGORIES = REVIEW_COMMENT_CATEGORIES

# Extract the bold label prefix from bot-generated comments. The formatter
# emits ``**Security:**``; the alternate ``**Security**:`` form is accepted
# for older or manually-authored comments.
_LABEL_PREFIX_RE = re.compile(
    r"^\*\*([^*]+?)(?::\*\*|\*\*:)",
    re.MULTILINE,
)

# Regex to extract code from GitLab suggestion blocks:
#   ```suggestion:-0+N   or   ```suggestion
_SUGGESTION_BLOCK_RE = re.compile(
    r"```suggestion[^\n]*\n(.*?)```",
    re.DOTALL,
)


class FetchMergeRequestDetails(Node):
    """Fetch merge request details. Supports both merged and open MRs.

    For merged MRs: extracts merge commit SHA for checkout.
    For open MRs: extracts source branch for checkout (pre-merge analysis).
    """

    def prep(self, shared):
        gitlab_token = shared.get("gitlab_token")
        gitlab_url = shared.get("gitlab_url", "https://gitlab.example.com")
        project_id = shared.get("project_id")
        merge_request_iid = shared.get("merge_request_iid")

        return {
            "gitlab_token": gitlab_token,
            "gitlab_url": gitlab_url,
            "project_id": project_id,
            "merge_request_iid": merge_request_iid,
        }

    def exec(self, prep_res):
        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        project_id = prep_res["project_id"]
        merge_request_iid = prep_res["merge_request_iid"]

        if not gitlab_token or not project_id or not merge_request_iid:
            raise ValueError(
                "GitLab token, project ID, and MR IID are required for impact analysis"
            )

        try:
            gl = scm.connect(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr = project.mergerequests.get(merge_request_iid)
        except Exception as e:
            raise ValueError(f"Failed to fetch MR details: {e}")

        mr_state = mr.state
        if mr_state not in ("merged", "opened"):
            raise ValueError(
                f"Impact analysis requires a merged or open MR. "
                f"Current state: '{mr_state}'."
            )

        is_merged = mr_state == "merged"

        # Extract metadata based on MR state
        merge_commit_sha = getattr(mr, "merge_commit_sha", None) if is_merged else None
        source_branch = mr.source_branch
        merged_at = getattr(mr, "merged_at", None) if is_merged else None
        merged_by = None
        if is_merged and hasattr(mr, "merged_by") and mr.merged_by:
            merged_by = mr.merged_by.get("username", "unknown")

        if is_merged:
            logger.info(f"MR !{merge_request_iid} is merged (commit: {merge_commit_sha})")
            logger.info(f"Merged at: {merged_at}, by: {merged_by}")
        else:
            logger.info(f"MR !{merge_request_iid} is open (source: {source_branch})")
            logger.info("Running pre-merge impact analysis")

        # Fetch the diff per file
        merged_diff_map = {}
        try:
            mr_changes = mr.changes(access_raw_diffs=True)
            for change in mr_changes.get("changes", []):
                file_path = change.get("new_path") or change.get("old_path")
                diff_content = change.get("diff", "")
                if file_path and diff_content:
                    merged_diff_map[file_path] = diff_content
            logger.info(f"Fetched diffs for {len(merged_diff_map)} files")
        except Exception as e:
            logger.warning(f"Failed to fetch MR changes: {e}")

        return {
            "mr_state": mr_state,
            "merge_request_id": mr.id,
            "merge_commit_sha": merge_commit_sha,
            "source_branch": source_branch,
            "merged_at": merged_at,
            "merged_by": merged_by,
            "merged_diff_map": merged_diff_map,
        }

    def post(self, shared, prep_res, exec_res):
        shared["mr_state"] = exec_res["mr_state"]
        shared["merge_request_id"] = exec_res["merge_request_id"]
        shared["merge_commit_sha"] = exec_res["merge_commit_sha"]
        shared["source_branch"] = exec_res["source_branch"]
        shared["merged_at"] = exec_res["merged_at"]
        shared["merged_by"] = exec_res["merged_by"]
        shared["merged_diff_map"] = exec_res["merged_diff_map"]


class CloneAndCheckoutCode(Node):
    """Clone the repository and checkout the appropriate ref.

    Auto-detects based on MR state:
    - Merged MR: checks out the merge commit SHA (or falls back to target branch)
    - Open MR: checks out the source branch (latest code under review)
    """

    def prep(self, shared):
        repo_url = shared.get("repo_url")
        gitlab_token = shared.get("gitlab_token")
        target_branch = shared.get("target_branch", "main")
        source_branch = shared.get("source_branch")
        merge_commit_sha = shared.get("merge_commit_sha")
        mr_state = shared.get("mr_state", "merged")

        if not repo_url:
            raise ValueError("repo_url is required for cloning the repository")

        return {
            "repo_url": repo_url,
            "gitlab_token": gitlab_token,
            "target_branch": target_branch,
            "source_branch": source_branch,
            "merge_commit_sha": merge_commit_sha,
            "mr_state": mr_state,
        }

    @staticmethod
    def _build_clone_url(repo_url, gitlab_token):
        """Construct authenticated clone URL."""
        clone_url = repo_url
        if gitlab_token:
            from urllib.parse import urlparse, urlunparse
            parsed = urlparse(repo_url)
            netloc = f"oauth2:{gitlab_token}@{parsed.netloc}"
            clone_url = urlunparse((parsed.scheme, netloc, parsed.path, "", "", ""))
        if not clone_url.endswith(".git"):
            clone_url += ".git"
        return clone_url

    def exec(self, prep_res):
        repo_url = prep_res["repo_url"]
        gitlab_token = prep_res["gitlab_token"]
        target_branch = prep_res["target_branch"]
        source_branch = prep_res["source_branch"]
        merge_commit_sha = prep_res["merge_commit_sha"]
        mr_state = prep_res["mr_state"]
        is_merged = mr_state == "merged"

        temp_dir = tempfile.mkdtemp(prefix="impact_analysis_")
        clone_url = self._build_clone_url(repo_url, gitlab_token)

        logger.info(f"Cloning repository from {repo_url} (partial clone)...")

        # Partial (treeless) clone
        cmd = [
            "git", "clone",
            "--no-checkout",
            "--filter=blob:none",
            clone_url, temp_dir,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)

        if result.returncode != 0:
            logger.warning(
                "Partial clone failed, falling back to shallow clone (--depth=50)"
            )
            shutil.rmtree(temp_dir, ignore_errors=True)
            temp_dir = tempfile.mkdtemp(prefix="impact_analysis_")

            cmd = ["git", "clone", "--depth=50", clone_url, temp_dir]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
            if result.returncode != 0:
                shutil.rmtree(temp_dir, ignore_errors=True)
                raise Exception(f"Failed to clone repository: {result.stderr}")

        checked_out_ref = None

        if is_merged:
            # Post-merge: checkout merge commit or fall back to target branch
            logger.info(f"Fetching target branch: {target_branch}")
            cmd = ["git", "-C", temp_dir, "fetch", "origin", target_branch]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            if result.returncode != 0:
                logger.warning(f"Failed to fetch branch '{target_branch}': {result.stderr.strip()}")

            if merge_commit_sha:
                logger.info(f"Checking out merge commit: {merge_commit_sha}")
                cmd = ["git", "-C", temp_dir, "checkout", merge_commit_sha]
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
                if result.returncode == 0:
                    checked_out_ref = merge_commit_sha
                    logger.info(f"Checked out merge commit: {merge_commit_sha[:12]}")
                else:
                    logger.warning(
                        f"Could not checkout merge commit {merge_commit_sha[:12]}, "
                        f"falling back to target branch"
                    )

            if not checked_out_ref:
                checked_out_ref = self._checkout_branch(temp_dir, target_branch)
        else:
            # Pre-merge: checkout source branch (the code under review)
            logger.info(f"Fetching source branch: {source_branch}")
            cmd = ["git", "-C", temp_dir, "fetch", "origin", source_branch]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            if result.returncode != 0:
                logger.warning(f"Failed to fetch branch '{source_branch}': {result.stderr.strip()}")

            checked_out_ref = self._checkout_branch(temp_dir, source_branch)

        logger.info(f"Repository ready at {temp_dir} (ref: {checked_out_ref})")

        return {
            "local_repo_path": temp_dir,
            "checked_out_ref": checked_out_ref,
        }

    @staticmethod
    def _checkout_branch(repo_path, branch):
        """Try checking out a branch with origin/ prefix fallback."""
        for ref in (f"origin/{branch}", branch):
            cmd = ["git", "-C", repo_path, "checkout", ref]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            if result.returncode == 0:
                logger.info(f"Checked out branch: {branch}")
                return ref

        raise Exception(
            f"Failed to checkout branch '{branch}'. "
            f"Error: {result.stderr}"
        )

    def post(self, shared, prep_res, exec_res):
        shared["local_repo_path"] = exec_res["local_repo_path"]
        shared["checked_out_ref"] = exec_res["checked_out_ref"]


class CheckoutCodeLocal(Node):
    """Checkout the appropriate ref in an existing local repo.

    Auto-detects based on MR state:
    - Merged MR: checks out merge commit or target branch
    - Open MR: checks out source branch
    """

    def prep(self, shared):
        local_repo_path = shared.get("local_repo_path")
        target_branch = shared.get("target_branch", "main")
        source_branch = shared.get("source_branch")
        merge_commit_sha = shared.get("merge_commit_sha")
        mr_state = shared.get("mr_state", "merged")

        if not local_repo_path:
            raise ValueError("local_repo_path is required")

        return {
            "local_repo_path": local_repo_path,
            "target_branch": target_branch,
            "source_branch": source_branch,
            "merge_commit_sha": merge_commit_sha,
            "mr_state": mr_state,
        }

    def exec(self, prep_res):
        local_repo_path = prep_res["local_repo_path"]
        target_branch = prep_res["target_branch"]
        source_branch = prep_res["source_branch"]
        merge_commit_sha = prep_res["merge_commit_sha"]
        mr_state = prep_res["mr_state"]
        is_merged = mr_state == "merged"

        checked_out_ref = None

        if is_merged:
            # Post-merge: try merge commit, fall back to target branch
            cmd = ["git", "-C", local_repo_path, "fetch", "origin", target_branch]
            subprocess.run(cmd, capture_output=True, text=True, timeout=300)

            if merge_commit_sha:
                logger.info(f"Checking out merge commit: {merge_commit_sha}")
                cmd = ["git", "-C", local_repo_path, "checkout", merge_commit_sha]
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
                if result.returncode == 0:
                    checked_out_ref = merge_commit_sha
                    logger.info(f"Checked out merge commit: {merge_commit_sha[:12]}")

            if not checked_out_ref:
                checked_out_ref = self._checkout_branch(local_repo_path, target_branch)
        else:
            # Pre-merge: checkout source branch
            cmd = ["git", "-C", local_repo_path, "fetch", "origin", source_branch]
            subprocess.run(cmd, capture_output=True, text=True, timeout=300)

            checked_out_ref = self._checkout_branch(local_repo_path, source_branch)

        logger.info(f"Local repo ready (ref: {checked_out_ref})")

        return {"checked_out_ref": checked_out_ref}

    @staticmethod
    def _checkout_branch(repo_path, branch):
        """Try checking out a branch with fallback order."""
        for ref in (branch, f"origin/{branch}"):
            cmd = ["git", "-C", repo_path, "checkout", ref]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            if result.returncode == 0:
                logger.info(f"Checked out branch: {branch}")
                return ref

        raise Exception(
            f"Failed to checkout branch '{branch}' in local repo. "
            f"Error: {result.stderr}"
        )

    def post(self, shared, prep_res, exec_res):
        shared["checked_out_ref"] = exec_res["checked_out_ref"]


class FetchAllReviewComments(Node):
    """Fetch the root review comment from each discussion on the merge request.

    Collects only the first actionable inline note per discussion — this is
    the original review feedback. Replies within the thread are ignored
    because impact is measured per review point, not per message.

    Skips: system notes, general MR-level comments, bot status messages,
    and non-actionable discussions (e.g. emoji-only, "LGTM").
    """


    def prep(self, shared):
        gitlab_token = shared.get("gitlab_token")
        gitlab_url = shared.get("gitlab_url", "https://gitlab.example.com")
        project_id = shared.get("project_id")
        merge_request_iid = shared.get("merge_request_iid")
        mr_author_username = shared.get("mr_author_username", "")

        return {
            "gitlab_token": gitlab_token,
            "gitlab_url": gitlab_url,
            "project_id": project_id,
            "merge_request_iid": merge_request_iid,
            "mr_author_username": mr_author_username,
        }

    def exec(self, prep_res):
        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        project_id = prep_res["project_id"]
        merge_request_iid = prep_res["merge_request_iid"]
        mr_author_username = prep_res["mr_author_username"]

        review_comments = []

        if not gitlab_token or not project_id or not merge_request_iid:
            logger.warning("Missing GitLab credentials — cannot fetch comments")
            return {"review_comments": review_comments, "total_comments_count": 0}

        try:
            gl = scm.connect(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr = project.mergerequests.get(merge_request_iid)

            # If mr_author_username wasn't provided, fetch it from the MR
            if not mr_author_username:
                mr_author = getattr(mr, "author", None)
                if mr_author and isinstance(mr_author, dict):
                    mr_author_username = mr_author.get("username", "")
                logger.info(f"MR author resolved to: {mr_author_username or '(unknown)'}")

            discussions = mr.discussions.list(all=True)
            logger.info(f"Fetched {len(discussions)} discussions from MR {merge_request_iid}")

            skipped_system = 0
            skipped_general = 0
            skipped_bot = 0
            skipped_non_actionable = 0

            for discussion in discussions:
                notes = discussion.attributes.get("notes", [])

                # Find the root note: first non-system note in the discussion
                root_note = None
                for note in notes:
                    if note.get("system"):
                        skipped_system += 1
                        continue
                    root_note = note
                    break

                if root_note is None:
                    continue

                body, provenance = parse_review_comment_metadata(
                    root_note.get("body", "")
                )
                body = body.strip()
                if not body:
                    continue

                # Skip bot status / summary comments
                if comment_filters.is_bot_comment(body):
                    skipped_bot += 1
                    continue

                # Extract position info for inline comments
                position = root_note.get("position") or {}
                file_path = (
                    position.get("new_path")
                    or position.get("old_path")
                    or ""
                )
                line_number = position.get("new_line") or position.get("old_line")

                # Skip general (non-inline) comments — not tied to code
                if not file_path:
                    skipped_general += 1
                    continue

                # Skip non-actionable discussions
                if comment_filters.is_non_actionable(body):
                    skipped_non_actionable += 1
                    continue

                # Extract author info
                author_data = root_note.get("author", {})
                author = author_data.get("username", "unknown") if author_data else "unknown"
                author_id = author_data.get("id") if author_data else None

                # Check for GitLab suggestion blocks
                has_suggestion = bool(_SUGGESTION_BLOCK_RE.search(body))
                suggested_code = ""
                if has_suggestion:
                    matches = _SUGGESTION_BLOCK_RE.findall(body)
                    suggested_code = "\n".join(m.strip() for m in matches)

                # Collect reply notes as thread context (non-system, non-bot)
                thread_replies = []
                is_past_root = False
                for note in notes:
                    if note.get("system"):
                        continue
                    if not is_past_root:
                        is_past_root = True
                        continue  # skip the root note itself
                    reply_body, _ = parse_review_comment_metadata(
                        note.get("body", "")
                    )
                    reply_body = reply_body.strip()
                    if not reply_body or comment_filters.is_bot_comment(reply_body):
                        continue
                    reply_author_data = note.get("author", {})
                    reply_author = reply_author_data.get("username", "unknown") if reply_author_data else "unknown"
                    thread_replies.append({
                        "author": reply_author,
                        "body": reply_body,
                    })

                # Extract label from bot comments (bold prefix like "**Security:** ...")
                extracted_label = ""
                label_match = _LABEL_PREFIX_RE.search(body)
                if label_match:
                    candidate = label_match.group(1).strip().lower()
                    if candidate in COMMENT_CATEGORIES:
                        extracted_label = candidate

                review_comments.append({
                    "file_path": file_path,
                    "line_number": int(line_number) if line_number else None,
                    "body": body,
                    "author": author,
                    "author_id": author_id,
                    "discussion_id": discussion.id,
                    "note_id": root_note.get("id"),
                    "comment_type": "inline",
                    "has_suggestion": has_suggestion,
                    "suggested_code": suggested_code,
                    "thread_replies": thread_replies,
                    "label": extracted_label,
                    "source_harness": provenance.get("source_harness"),
                    "source_model": provenance.get("source_model"),
                })

            suggestion_count = sum(1 for c in review_comments if c["has_suggestion"])

            logger.info(
                f"Collected {len(review_comments)} actionable discussions "
                f"({suggestion_count} with suggestions)"
            )
            logger.info(
                f"Skipped: {skipped_system} system, {skipped_general} general, "
                f"{skipped_bot} bot, {skipped_non_actionable} non-actionable"
            )

        except Exception as e:
            logger.error(f"Failed to fetch MR discussions: {e}")
            raise

        return {
            "review_comments": review_comments,
            "total_comments_count": len(review_comments),
        }

    def post(self, shared, prep_res, exec_res):
        shared["review_comments"] = exec_res["review_comments"]
        shared["total_comments_count"] = exec_res["total_comments_count"]

        if not exec_res["review_comments"]:
            logger.info("No actionable comments found — skipping clone and analysis")
            return "no_comments"
        return "has_comments"


class AnalyzeCommentImpact(Node):
    """Use an LLM agent to analyze whether each review comment was applied.

    The LLM reads the merged repository and compares each comment's feedback
    against the final code state, scoring each as applied / partially_applied /
    ignored / not_applicable.
    """

    def prep(self, shared):
        review_comments = shared.get("review_comments", [])
        merged_diff_map = shared.get("merged_diff_map", {})
        local_repo_path = shared.get("local_repo_path", ".")
        mr_state = shared.get("mr_state", "merged")

        return {
            "review_comments": review_comments,
            "merged_diff_map": merged_diff_map,
            "local_repo_path": local_repo_path,
            "mr_state": mr_state,
        }

    def exec(self, prep_res):
        from pydantic import BaseModel, Field
        from utils.tool_adapter import define_tool

        review_comments = prep_res["review_comments"]
        merged_diff_map = prep_res["merged_diff_map"]
        local_repo_path = prep_res["local_repo_path"]
        is_merged = prep_res["mr_state"] == "merged"

        if not review_comments:
            logger.info("No review comments to analyze")
            return {
                "impact_results": [],
                "overall_adoption_rate": 0.0,
                "overall_summary": "No review comments found to analyze.",
            }

        # --- Captured state for tool closure ---
        captured_analysis: dict = {}

        # ---- Pydantic models for the tool ----
        class CommentImpactResult(BaseModel):
            comment_id: str = Field(
                description='ID of the comment (e.g. "comment-0")'
            )
            status: str = Field(
                description='One of: "applied", "partially_applied", "ignored", "not_applicable"'
            )
            score: float = Field(
                description="Confidence score 0.0-1.0"
            )
            reasoning: str = Field(
                description="Brief 1-2 sentence explanation of the scoring decision"
            )
            final_code_snippet: str = Field(
                default="",
                description="The actual code at the relevant location in the merged version"
            )
            priority: str = Field(
                default="medium",
                description='Importance of the comment: "critical", "high", "medium", or "low"'
            )
            comment_category: str = Field(
                default="general",
                description=(
                    'Category of the review comment. Must be one of: "security", '
                    '"critical bug", "possible bug", "possible issue", "performance", '
                    '"best practice", "maintainability", "enhancement", "typo", "general". '
                    'If the comment already has a label field in the YAML, use that value directly.'
                )
            )

        class WriteImpactAnalysisParams(BaseModel):
            results: list[CommentImpactResult] = Field(
                description="Impact analysis result for each review comment"
            )
            overall_summary: str = Field(
                description="Overall summary of findings across all comments"
            )
            overall_adoption_rate: float = Field(
                description=(
                    "Fraction (0.0-1.0) of actionable comments that were "
                    "applied or partially applied"
                )
            )

        @define_tool(description=(
            "Write the impact analysis results. Call this ONCE after you have "
            "analyzed all review comments against the merged code. Provide a "
            "result for every comment, an overall summary, and the overall "
            "adoption rate."
        ))
        def write_impact_analysis(params: WriteImpactAnalysisParams) -> str:
            logger.info(
                f"[tool] write_impact_analysis called - "
                f"{len(params.results)} results, "
                f"adoption rate: {params.overall_adoption_rate:.1%}"
            )
            results_list = []
            for r in params.results:
                entry = r.model_dump() if hasattr(r, "model_dump") else dict(r)
                results_list.append(entry)

            captured_analysis["results"] = results_list
            captured_analysis["overall_summary"] = params.overall_summary
            captured_analysis["overall_adoption_rate"] = params.overall_adoption_rate

            return (
                f"OK - {len(results_list)} comment impact results saved. "
                f"Overall adoption rate: {params.overall_adoption_rate:.1%}"
            )

        tools = [write_impact_analysis]

        # Pre-stage diffs and comments to disk so the prompt stays small
        # (avoids "Argument list too long" when passed as CLI arg to opencode).
        self._stage_diffs(local_repo_path, merged_diff_map)
        self._stage_comments(local_repo_path, review_comments)

        # Build the analysis prompt (lightweight — references staged files)
        analysis_prompt = build_impact_analysis_prompt(
            review_comments=review_comments,
            merged_diff_map=merged_diff_map,
            local_repo_path=local_repo_path,
            is_merged=is_merged,
        )

        _harness_name = get_harness_name()
        logger.info(
            f"Invoking {_harness_name} harness for impact analysis of "
            f"{len(review_comments)} comments..."
        )

        try:
            _final_response = call_llm_agentic(
                analysis_prompt,
                tools=tools,
                system_message=get_impact_system_message(is_merged=is_merged),
                model="gpt-5.6-sol",
                working_directory=local_repo_path,
            )

            logger.info(f"{_harness_name} impact analysis completed")

            if not captured_analysis:
                logger.warning("write_impact_analysis tool was not called by the agent")
                return {
                    "impact_results": [],
                    "overall_adoption_rate": 0.0,
                    "overall_summary": "Analysis failed: tool was not called.",
                    "raw_output": _final_response,
                }

            # Process and validate results
            impact_results = captured_analysis.get("results", [])
            valid_statuses = {"applied", "partially_applied", "ignored", "not_applicable"}

            for result in impact_results:
                # Validate status
                if result.get("status") not in valid_statuses:
                    result["status"] = "not_applicable"
                # Clamp score
                try:
                    result["score"] = max(0.0, min(1.0, float(result.get("score", 0.0))))
                except (ValueError, TypeError):
                    result["score"] = 0.0
                # Validate priority
                if result.get("priority") not in {"critical", "high", "medium", "low"}:
                    result["priority"] = "medium"
                # Validate comment_category against the canonical label set
                cat = result.get("comment_category", "").strip().lower()
                if cat not in COMMENT_CATEGORIES:
                    result["comment_category"] = "general"
                else:
                    result["comment_category"] = cat

            # Log summary
            status_counts = {}
            for r in impact_results:
                s = r.get("status", "unknown")
                status_counts[s] = status_counts.get(s, 0) + 1
            logger.info(f"Impact analysis results: {status_counts}")

            return {
                "impact_results": impact_results,
                "overall_adoption_rate": captured_analysis.get("overall_adoption_rate", 0.0),
                "overall_summary": captured_analysis.get("overall_summary", ""),
                "raw_output": _final_response,
            }

        except Exception as e:
            logger.error(f"{_harness_name} impact analysis failed: {e}")
            return {
                "impact_results": [],
                "overall_adoption_rate": 0.0,
                "overall_summary": f"Analysis failed: {e}",
                "raw_output": "",
            }

    @staticmethod
    def _stage_diffs(local_repo_path, merged_diff_map):
        """Write per-file diffs to a `.diffs/` directory in the repo.

        This mirrors the approach used by the improve flow — the agent reads
        diffs from disk instead of receiving them inline in the prompt, keeping
        the prompt size well under OS arg-length limits.
        """
        diffs_dir = os.path.join(local_repo_path, ".diffs")
        os.makedirs(diffs_dir, exist_ok=True)

        staged_count = 0
        for file_path, diff_content in merged_diff_map.items():
            if not diff_content:
                continue
            diff_file = os.path.join(diffs_dir, file_path + ".diff")
            os.makedirs(os.path.dirname(diff_file), exist_ok=True)
            with open(diff_file, "w", encoding="utf-8") as fh:
                fh.write(diff_content)
            staged_count += 1

        logger.info(f"Pre-staged {staged_count} diff files in {diffs_dir}")
        return diffs_dir

    @staticmethod
    def _stage_comments(local_repo_path, review_comments):
        """Write review comments to a `.comments/comments.yaml` file in the repo.

        This keeps the prompt lightweight — the agent reads comments from disk.
        """
        comments_dir = os.path.join(local_repo_path, ".comments")
        os.makedirs(comments_dir, exist_ok=True)

        comments_file = os.path.join(comments_dir, "comments.yaml")
        # Provenance is joined mechanically after impact scoring. Excluding it
        # from the LLM-facing input prevents harness/model attribution from
        # biasing the evaluator.
        analysis_comments = [
            {
                key: value
                for key, value in comment.items()
                if key
                not in {
                    "source_harness",
                    "source_model",
                }
            }
            for comment in review_comments
        ]

        with open(comments_file, "w", encoding="utf-8") as fh:
            yaml.dump(
                analysis_comments,
                fh,
                default_flow_style=False,
                allow_unicode=True,
                width=200,
            )

        logger.info(
            f"Pre-staged {len(review_comments)} comments to {comments_file}"
        )
        return comments_file

    def post(self, shared, prep_res, exec_res):
        shared["impact_results"] = exec_res["impact_results"]
        shared["overall_adoption_rate"] = exec_res["overall_adoption_rate"]
        shared["overall_summary"] = exec_res["overall_summary"]
        shared["impact_raw_output"] = exec_res.get("raw_output", "")


class WriteImpactOutput(Node):
    """Write the impact analysis results to a local YAML file."""

    def prep(self, shared):
        return {
            "impact_results": shared.get("impact_results", []),
            "review_comments": shared.get("review_comments", []),
            "project_id": shared.get("project_id"),
            "merge_request_id": shared.get("merge_request_id"),
            "merge_request_iid": shared.get("merge_request_iid"),
            "mr_author_id": shared.get("mr_author_id"),
            "local_repo_path": shared.get("local_repo_path"),
            "output_file": shared.get("output_file", "impact_analysis_output.yaml"),
        }

    def exec(self, prep_res):
        impact_results = prep_res["impact_results"]
        review_comments = prep_res["review_comments"]
        local_repo_path = prep_res["local_repo_path"]
        output_file = prep_res["output_file"]

        # Build merged comment+result entries
        comment_entries = []
        result_map = {r.get("comment_id", ""): r for r in impact_results}

        for idx, comment in enumerate(review_comments):
            comment_id = f"comment-{idx}"
            result = result_map.get(comment_id, {})

            entry = {
                "note_id": comment.get("note_id"),
                "discussion_id": comment.get("discussion_id"),
                "file": comment.get("file_path", ""),
                "line": comment.get("line_number"),
                "author": comment.get("author", "unknown"),
                "author_id": comment.get("author_id"),
                "comment_type": comment.get("comment_type", "general"),
                "has_suggestion": comment.get("has_suggestion", False),
                "status": result.get("status", "not_applicable"),
                "score": result.get("score", 0.0),
                "reasoning": result.get("reasoning", ""),
                "priority": result.get("priority", "medium"),
                "comment_category": (
                    comment.get("label")
                    or result.get("comment_category")
                    or "general"
                ),
                "source_harness": comment.get("source_harness"),
                "source_model": comment.get("source_model"),
            }
            comment_entries.append(entry)

        # Build output structure — keys match the Arnold ReviewFeedbackRequest contract
        output_data = {
            "metadata": {
                "project_id": prep_res["project_id"],
                "mr_id": prep_res["merge_request_id"],
                "mr_iid": prep_res["merge_request_iid"],
                "mr_author_id": prep_res["mr_author_id"],
            },
            "comments": comment_entries,
        }

        # Write YAML to cwd so it survives temp directory cleanup
        output_path = (
            os.path.join(os.getcwd(), output_file)
            if local_repo_path
            else output_file
        )

        with open(output_path, "w", encoding="utf-8") as f:
            yaml.dump(
                output_data, f,
                default_flow_style=False, allow_unicode=True, sort_keys=False,
            )

        logger.info(f"Impact analysis output written to: {output_path}")

        return {
            "output_path": output_path,
            "output_data": output_data,
        }

    def post(self, shared, prep_res, exec_res):
        shared["impact_output_path"] = exec_res["output_path"]
        shared["impact_output_data"] = exec_res["output_data"]


class SendImpactToService(Node):
    """Send impact analysis data to a configured external service via HTTP POST."""

    def prep(self, shared):
        return {
            "impact_output_data": shared.get("impact_output_data", {}),
            "impact_service_url": shared.get("impact_service_url", ""),
            "dry_run": shared.get("dry_run", False),
            "output_file": shared.get("output_file", "impact_analysis_output.yaml"),
        }

    def exec(self, prep_res):
        import requests

        impact_data = prep_res["impact_output_data"]
        service_url = prep_res["impact_service_url"]
        dry_run = prep_res["dry_run"]

        if not impact_data:
            logger.info("No impact data to send")
            return {"impact_sent": False, "dry_run_json_path": None}

        if dry_run:
            # Save JSON payload to a file and print it for inspection
            base_name = os.path.splitext(prep_res["output_file"])[0]
            dry_run_path = f"{base_name}_payload.json"
            with open(dry_run_path, "w", encoding="utf-8") as f:
                json.dump(impact_data, f, indent=2, default=str)
            logger.info("Dry run mode — impact data will not be sent to service")
            logger.info(f"Would send to: {service_url or '(not configured)'}")
            logger.info(f"Payload saved to: {dry_run_path}")
            logger.info("Payload JSON:\n%s", json.dumps(impact_data, indent=2, default=str))
            return {"impact_sent": False, "dry_run_json_path": dry_run_path}

        if not service_url:
            logger.warning(
                "IMPACT_SERVICE_URL not configured — skipping impact data dispatch. "
                "Set the IMPACT_SERVICE_URL environment variable to enable."
            )
            return {"impact_sent": False}

        headers = {"Content-Type": "application/json"}
        impact_token = os.environ.get("IMPACT_SERVICE_TOKEN", "").strip()
        if impact_token:
            headers["Authorization"] = f"Bearer {impact_token}"

        try:
            response = requests.post(
                service_url,
                json=impact_data,
                headers=headers,
                timeout=30,
            )
            response.raise_for_status()
            logger.info(
                f"Impact data sent to service ({service_url}) — "
                f"status {response.status_code}"
            )
            return {"impact_sent": True}
        except requests.RequestException as e:
            logger.error(f"Failed to send impact data to service: {e}")
            return {"impact_sent": False}

    def post(self, shared, prep_res, exec_res):
        shared["impact_summary_posted"] = exec_res["impact_sent"]
        if exec_res.get("dry_run_json_path"):
            shared["dry_run_json_path"] = exec_res["dry_run_json_path"]
