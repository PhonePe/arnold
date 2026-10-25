import os
import logging
import gitlab
from pocketflow import Node
from pydantic import BaseModel, Field
from typing import List, Optional
from utils.call_llm import call_llm_agentic
from utils.config import get_harness_label, get_harness_name, get_review_model, get_analysis_model
from utils.logger import get_logger
from flows.code_reviews.review.prompts import (
    REVIEW_SYSTEM_MESSAGE,
    PREVIOUS_COMMENT_ANALYSIS_SYSTEM_MESSAGE,
    build_review_summary_prompt,
    build_previous_comment_analysis_prompt,
)
from flows.code_reviews.review.markdown_formatter import format_review_as_markdown

# Set up logger
logger = get_logger("code_review_review_nodes_logger")


class ExecuteReviewSummary(Node):
    """Execute the high-level review summary in agent mode.

    Mirrors the agentic pattern of ExecuteCodeReview (the /improve flow):
    the LLM agent gets full repo access via *working_directory*,
    explores the codebase, then calls the ``write_review_summary`` tool
    exactly once with structured review data.
    """

    def prep(self, shared):
        changed_files = shared.get("changed_files", [])
        source_branch = shared.get("source_branch", "")
        target_branch = shared.get("target_branch", "main")
        mr_title = shared.get("mr_title", "")
        mr_description = shared.get("mr_description", "")
        local_repo_path = shared.get("local_repo_path", ".")
        project_name = shared.get("project_name", "Unknown Project")
        related_tickets = shared.get("related_tickets", [])
        impact_context = shared.get("impact_context", "")
        graph_tools_available = shared.get("code_graph_built", False)
        jira_file_path = shared.get("jira_file_path", "")
        rfc_file_path = shared.get("rfc_file_path", "")

        return {
            "changed_files": changed_files,
            "source_branch": source_branch,
            "target_branch": target_branch,
            "mr_title": mr_title,
            "mr_description": mr_description,
            "local_repo_path": local_repo_path,
            "project_name": project_name,
            "related_tickets": related_tickets,
            "impact_context": impact_context,
            "graph_tools_available": graph_tools_available,
            "jira_file_path": jira_file_path,
            "rfc_file_path": rfc_file_path,
        }

    def exec(self, prep_res):
        from utils.tool_adapter import define_tool
        from utils.config import get_harness_name

        changed_files = prep_res["changed_files"]
        source_branch = prep_res["source_branch"]
        target_branch = prep_res["target_branch"]
        mr_title = prep_res["mr_title"]
        mr_description = prep_res["mr_description"]
        local_repo_path = prep_res["local_repo_path"]
        related_tickets = prep_res["related_tickets"]
        impact_context = prep_res.get("impact_context", "")
        graph_tools_available = prep_res.get("graph_tools_available", False)
        jira_file_path = prep_res.get("jira_file_path", "")
        rfc_file_path = prep_res.get("rfc_file_path", "")

        if not changed_files:
            logger.info("No changed files to review")
            return {"review_data": {}, "raw_output": ""}

        # --- Captured state for tool closure ---
        captured_review: dict = {}

        # ---- Pydantic models for the tool ----
        class KeyIssue(BaseModel):
            model_config = {"extra": "allow"}

            relevant_file: str = Field(
                default="",
                description="Full file path from the diff (e.g., src/main.py)"
            )
            issue_header: str = Field(
                default="",
                description="One or two word title (e.g., 'Possible Bug', 'Race Condition')"
            )
            issue_content: str = Field(
                default="",
                description="Concise description of what should be inspected and validated. This is always visible to the reviewer."
            )
            start_line: int = Field(
                default=0,
                description="Start line number in the new file"
            )
            end_line: int = Field(
                default=0,
                description="End line number in the new file"
            )
            code_snippet: str = Field(
                default="",
                description="The relevant code snippet from the file (the actual changed code that the issue refers to). Include enough context lines. This will be shown in a collapsible section."
            )

        class SubPR(BaseModel):
            model_config = {"extra": "allow"}

            relevant_files: List[str] = Field(
                default_factory=list,
                description="List of file paths belonging to this sub-PR"
            )
            title: str = Field(
                default="",
                description="Short title for the independent sub-PR"
            )

        class TicketCompliance(BaseModel):
            model_config = {"extra": "allow"}

            ticket_url: str = Field(
                default="",
                description="Jira ticket URL or ID"
            )
            ticket_requirements: str = Field(
                default="",
                description="Repeat, in your own words, all the requirements, sub-tasks, DoD, and acceptance criteria from the ticket"
            )
            fully_compliant_requirements: str = Field(
                default="",
                description="Bullet-point list of ticket requirements that ARE fulfilled by this PR"
            )
            not_compliant_requirements: str = Field(
                default="",
                description="Bullet-point list of ticket requirements that are NOT fulfilled by this PR"
            )
            requires_further_human_verification: str = Field(
                default="",
                description="Bullet-point list of ticket requirements that cannot be assessed through code review alone"
            )

        class RFCCompliance(BaseModel):
            model_config = {"extra": "allow"}

            rfc_section: str = Field(
                default="",
                description="The RFC section title being assessed (e.g., 'Security Considerations', 'API Contract')"
            )
            implemented: str = Field(
                default="",
                description="Bullet-point list of RFC requirements that ARE implemented in this PR"
            )
            missing: str = Field(
                default="",
                description="Bullet-point list of RFC requirements that are NOT implemented in this PR"
            )
            deviated: str = Field(
                default="",
                description="Bullet-point list of requirements implemented differently than the RFC specifies"
            )

        class WriteReviewSummaryParams(BaseModel):
            model_config = {"extra": "allow"}

            estimated_effort: int = Field(
                ge=1, le=5,
                description="Effort to review on a 1-5 scale (1=trivial, 5=very large)"
            )
            estimated_effort_rationale: str = Field(
                min_length=1, pattern=r"\S",
                description="Explain the rating in 1-2 sentences using specific changed files, logic, and review or testing complexity."
            )
            relevant_tests: str = Field(
                default="Unknown",
                description="'Yes' or 'No' with brief explanation of whether relevant tests are included"
            )
            key_issues: List[KeyIssue] = Field(
                default_factory=list,
                description="List of 0-5 key issues a reviewer should focus on"
            )
            security_concerns: str = Field(
                default="No",
                description="Description of security concerns, or 'No' if none"
            )
            can_be_split: Optional[List[SubPR]] = Field(
                default_factory=list,
                description="Optional list of 0-3 independent sub-PRs this MR could be split into"
            )
            ticket_compliance_check: Optional[List[TicketCompliance]] = Field(
                default_factory=list,
                description="Compliance check for each related Jira ticket (only if tickets are linked)"
            )
            rfc_compliance_check: Optional[List[RFCCompliance]] = Field(
                default_factory=list,
                description="RFC compliance check per section (only if an RFC document is available)"
            )

        @define_tool(description=(
            "Write the final high-level review summary. Call this ONCE after "
            "you have reviewed all changed files. Provide the estimated effort, "
            "test assessment, key issues to review, and security concerns."
        ))
        def write_review_summary(params: WriteReviewSummaryParams) -> str:
            logger.info(
                f"[tool] write_review_summary called — "
                f"effort={params.estimated_effort}, "
                f"{len(params.key_issues)} key issues"
            )

            # Normalise nested Pydantic models to dicts and handle field aliases
            key_issues = []
            for issue in params.key_issues:
                entry = issue.model_dump() if hasattr(issue, "model_dump") else dict(issue)
                # Handle common field name mismatches from LLM output
                if not entry.get("issue_header") and entry.get("header"):
                    entry["issue_header"] = entry.pop("header")
                if not entry.get("issue_content") and entry.get("description"):
                    entry["issue_content"] = entry.pop("description")
                key_issues.append(entry)

            can_be_split = []
            if params.can_be_split:
                for sub in params.can_be_split:
                    entry = sub.model_dump() if hasattr(sub, "model_dump") else dict(sub)
                    # Handle field name mismatches
                    if not entry.get("relevant_files") and entry.get("files"):
                        entry["relevant_files"] = entry.pop("files")
                    can_be_split.append(entry)

            captured_review.update({
                "estimated_effort": params.estimated_effort,
                "estimated_effort_rationale": params.estimated_effort_rationale.strip(),
                "relevant_tests": params.relevant_tests,
                "key_issues": key_issues,
                "security_concerns": params.security_concerns,
                "can_be_split": can_be_split,
                "ticket_compliance_check": [
                    tc.model_dump() if hasattr(tc, "model_dump") else dict(tc)
                    for tc in (params.ticket_compliance_check or [])
                ],
                "rfc_compliance_check": [
                    rc.model_dump() if hasattr(rc, "model_dump") else dict(rc)
                    for rc in (params.rfc_compliance_check or [])
                ],
            })

            return (
                f"OK — Review summary saved. "
                f"Effort: {params.estimated_effort}/5, "
                f"{len(key_issues)} key issues."
            )

        tools = [write_review_summary]

        # Build prompt
        review_prompt = build_review_summary_prompt(
            mr_title=mr_title,
            mr_description=mr_description,
            source_branch=source_branch,
            target_branch=target_branch,
            changed_files=changed_files,
            local_repo_path=local_repo_path,
            impact_context=impact_context,
            graph_tools_available=graph_tools_available,
            jira_file_path=jira_file_path,
            rfc_file_path=rfc_file_path,
        )

        _harness_name = get_harness_name()
        logger.info(f"Invoking {_harness_name} harness for high-level review summary...")

        try:
            _final_response = call_llm_agentic(
                review_prompt,
                tools=tools,
                system_message=REVIEW_SYSTEM_MESSAGE,
                model=get_review_model(),
                working_directory=local_repo_path,
            )

            logger.info(f"{_harness_name} review summary completed")

            if not captured_review:
                logger.warning("write_review_summary tool was not called by the agent")
                captured_review.update({
                    "estimated_effort": None,
                    "relevant_tests": "Unknown",
                    "key_issues": [],
                    "security_concerns": "No",
                    "can_be_split": [],
                    "ticket_compliance_check": [],
                    "rfc_compliance_check": [],
                })

            return {
                "review_data": captured_review,
                "raw_output": _final_response,
            }

        except Exception as e:
            logger.error(f"{_harness_name} review summary failed: {e}")
            return {"review_data": {}, "raw_output": ""}

    def post(self, shared, prep_res, exec_res):
        shared["review_data"] = exec_res["review_data"]
        shared["review_raw_output"] = exec_res.get("raw_output", "")


class AnalyzePreviousCommentStatus(Node):
    """LLM-powered analysis of whether previous inline review comments were addressed.

    Uses the already-cloned repository to evaluate each comment from a prior
    review run against the current source-branch code, assigning a status of
    ``applied``, ``partially_applied``, ``ignored``, or ``not_applicable``.

    This is a no-op when there are no previous comments.
    """

    def prep(self, shared):
        return {
            "previous_review_comments": shared.get("previous_review_comments", []),
            "local_repo_path": shared.get("local_repo_path", "."),
            "diffs_dir": shared.get("diffs_dir", ""),
        }

    def exec(self, prep_res):
        import os
        import yaml
        from pydantic import BaseModel, Field
        from utils.tool_adapter import define_tool

        review_comments = prep_res["previous_review_comments"]
        local_repo_path = prep_res["local_repo_path"]
        # Ensure diffs_dir is always an absolute path so prompts are unambiguous
        diffs_dir = prep_res.get("diffs_dir", "") or os.path.join(local_repo_path, ".diffs")

        if not review_comments:
            logger.info("No previous review comments to analyze")
            return {
                "comment_analysis_results": [],
                "comment_adoption_rate": None,
                "comment_analysis_summary": "",
                "comment_analysis_succeeded": True,
            }

        # --- Stage comments to disk for the agent ---
        comments_dir = os.path.join(local_repo_path, ".comments")
        os.makedirs(comments_dir, exist_ok=True)
        comments_file = os.path.join(comments_dir, "prev_comments.yaml")
        with open(comments_file, "w", encoding="utf-8") as fh:
            yaml.dump(
                review_comments, fh,
                default_flow_style=False, allow_unicode=True, width=200,
            )
        logger.info(f"Staged {len(review_comments)} previous comments to {comments_file}")

        # --- Captured state for tool closure ---
        captured: dict = {}

        # ---- Pydantic models ----
        class CommentStatusResult(BaseModel):
            comment_id: str = Field(description='ID of the comment (e.g. "comment-0")')
            status: str = Field(
                description='One of: "applied", "partially_applied", "ignored", "not_applicable"'
            )
            score: float = Field(description="Confidence score 0.0–1.0", ge=0.0, le=1.0)
            reasoning: str = Field(description="Brief 1–2 sentence explanation")

        class WriteCommentStatusAnalysisParams(BaseModel):
            results: list[CommentStatusResult] = Field(
                description="Status result for each previous review comment"
            )
            overall_summary: str = Field(
                description="Overall summary of how well previous feedback was addressed"
            )
            overall_adoption_rate: float = Field(
                description="Fraction (0.0–1.0) of actionable comments that were applied or partially applied"
            )

        @define_tool(description=(
            "Write the analysis results for all previous review comments. Call this ONCE "
            "after evaluating every comment against the current code. Provide a result for "
            "every comment, an overall summary, and the adoption rate."
        ))
        def write_comment_status_analysis(params: WriteCommentStatusAnalysisParams) -> str:
            expected = len(review_comments)
            actual = len(params.results)
            if actual != expected:
                logger.warning(
                    f"LLM returned {actual} results but {expected} were expected"
                )
            logger.info(
                f"[tool] write_comment_status_analysis called — "
                f"{actual} results, "
                f"adoption rate: {params.overall_adoption_rate:.1%}"
            )
            results_list = []
            valid_statuses = {"applied", "partially_applied", "ignored", "not_applicable"}
            for r in params.results:
                entry = r.model_dump() if hasattr(r, "model_dump") else dict(r)
                if entry.get("status") not in valid_statuses:
                    entry["status"] = "not_applicable"
                try:
                    entry["score"] = max(0.0, min(1.0, float(entry.get("score", 0.0))))
                except (ValueError, TypeError):
                    entry["score"] = 0.0
                results_list.append(entry)

            captured["results"] = results_list
            captured["overall_summary"] = params.overall_summary
            captured["overall_adoption_rate"] = max(0.0, min(1.0, params.overall_adoption_rate))

            return (
                f"OK — {len(results_list)} comment status results saved. "
                f"Adoption rate: {params.overall_adoption_rate:.1%}"
            )

        tools = [write_comment_status_analysis]

        analysis_prompt = build_previous_comment_analysis_prompt(
            review_comments=review_comments,
            local_repo_path=local_repo_path,
            diffs_dir=diffs_dir,
        )

        _harness_name = get_harness_name()
        logger.info(
            f"Invoking {_harness_name} harness to analyze "
            f"{len(review_comments)} previous review comments..."
        )

        try:
            call_llm_agentic(
                analysis_prompt,
                tools=tools,
                system_message=PREVIOUS_COMMENT_ANALYSIS_SYSTEM_MESSAGE,
                model=get_analysis_model(),
                working_directory=local_repo_path,
            )

            logger.info(f"{_harness_name} previous comment analysis completed")

            if not captured:
                logger.warning("write_comment_status_analysis was not called by the agent")
                return {
                    "comment_analysis_results": [],
                    "comment_adoption_rate": None,
                    "comment_analysis_summary": "",
                    "comment_analysis_succeeded": False,
                }

            # Compute status counts for logging
            status_counts: dict[str, int] = {}
            for r in captured.get("results", []):
                s = r.get("status", "unknown")
                status_counts[s] = status_counts.get(s, 0) + 1
            logger.info(f"Previous comment analysis: {status_counts}")

            return {
                "comment_analysis_results": captured.get("results", []),
                "comment_adoption_rate": captured.get("overall_adoption_rate"),
                "comment_analysis_summary": captured.get("overall_summary", ""),
                "comment_analysis_succeeded": True,
            }

        except Exception as e:
            logger.error(f"Previous comment analysis failed: {e}")
            return {
                "comment_analysis_results": [],
                "comment_adoption_rate": None,
                "comment_analysis_summary": "",
                "comment_analysis_succeeded": False,
            }

    def post(self, shared, prep_res, exec_res):
        shared["comment_analysis_results"] = exec_res["comment_analysis_results"]
        shared["comment_adoption_rate"] = exec_res["comment_adoption_rate"]
        shared["comment_analysis_summary"] = exec_res["comment_analysis_summary"]
        shared["comment_analysis_succeeded"] = exec_res["comment_analysis_succeeded"]


class FormatReviewMarkdown(Node):
    """Convert captured review data into GitLab-compatible markdown."""

    def prep(self, shared):
        # Build the MR diff base URL for linking to individual files
        gitlab_url = shared.get("gitlab_url", "")
        project_name = shared.get("project_name", "")
        mr_iid = shared.get("merge_request_iid")
        mr_diff_url = ""
        if gitlab_url and project_name and mr_iid:
            mr_diff_url = f"{gitlab_url}/{project_name}/-/merge_requests/{mr_iid}/diffs"

        return {
            "review_data": shared.get("review_data", {}),
            "project_name": project_name,
            "source_branch": shared.get("source_branch", ""),
            "target_branch": shared.get("target_branch", ""),
            "merge_request_iid": mr_iid,
            "mr_diff_url": mr_diff_url,
            "gitlab_url": gitlab_url,
            "ai_improve_done": shared.get("ai_improve_done", False),
            "ai_improve_comment_count": shared.get("ai_improve_comment_count", 0),
            "comment_analysis_results": shared.get("comment_analysis_results", []),
            "comment_adoption_rate": shared.get("comment_adoption_rate"),
            "comment_analysis_summary": shared.get("comment_analysis_summary", ""),
            "previous_review_comments": shared.get("previous_review_comments", []),
        }

    def exec(self, prep_res):
        review_data = prep_res["review_data"]

        if not review_data:
            logger.info("No review data to format")
            return {"markdown": ""}

        markdown = format_review_as_markdown(
            review_data,
            project_name=prep_res["project_name"],
            source_branch=prep_res["source_branch"],
            target_branch=prep_res["target_branch"],
            mr_iid=prep_res["merge_request_iid"],
            mr_diff_url=prep_res["mr_diff_url"],
            gitlab_url=prep_res["gitlab_url"],
            ai_improve_done=prep_res.get("ai_improve_done", False),
            ai_improve_comment_count=prep_res.get("ai_improve_comment_count", 0),
            comment_analysis_results=prep_res.get("comment_analysis_results", []),
            comment_adoption_rate=prep_res.get("comment_adoption_rate"),
            comment_analysis_summary=prep_res.get("comment_analysis_summary", ""),
            previous_review_comments=prep_res.get("previous_review_comments", []),
        )

        logger.info(f"Formatted review markdown ({len(markdown)} chars)")
        return {"markdown": markdown}

    def post(self, shared, prep_res, exec_res):
        shared["review_markdown"] = exec_res["markdown"]


class PostReviewSummary(Node):
    """Post the review summary as a single MR note (not inline comments)."""

    def prep(self, shared):
        return {
            "markdown": shared.get("review_markdown", ""),
            "gitlab_token": shared.get("gitlab_token"),
            "gitlab_url": shared.get("gitlab_url", "https://gitlab.example.com"),
            "project_id": shared.get("project_id"),
            "merge_request_iid": shared.get("merge_request_iid"),
            "dry_run": shared.get("dry_run", False),
        }

    def exec(self, prep_res):
        markdown = prep_res["markdown"]
        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        project_id = prep_res["project_id"]
        merge_request_iid = prep_res["merge_request_iid"]
        dry_run = prep_res["dry_run"]

        if not markdown:
            logger.info("No markdown to post")
            return {"summary_posted": False}

        if dry_run:
            logger.info("Dry run mode — printing review summary to console")
            logger.info("\n" + markdown)
            return {"summary_posted": False}

        if not gitlab_token or not project_id or not merge_request_iid:
            logger.warning("Missing GitLab credentials — skipping review summary post")
            return {"summary_posted": False}

        try:
            gl = gitlab.Gitlab(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr = project.mergerequests.get(merge_request_iid)

            self._delete_and_create_note(mr, markdown)

            logger.info(f"Posted review summary to MR {merge_request_iid}")
            summary_posted = True
        except Exception as e:
            logger.error(f"Failed to post review summary: {e}")
            summary_posted = False

        return {"summary_posted": summary_posted}

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _delete_and_create_note(self, mr, markdown):
        """Delete any existing PR Reviewer Guide notes, then post a fresh one."""
        header_marker = "## PR Reviewer Guide 🔍"
        existing_notes = []
        try:
            notes = mr.notes.list(all=True)
            for note in notes:
                if note.body and header_marker in note.body:
                    existing_notes.append(note)
        except Exception as e:
            logger.warning(f"Could not search for existing review notes: {e}")

        try:
            new_note = mr.notes.create({"body": markdown})
            logger.info(
                f"Created new review summary note (id={new_note.id})"
            )
        except Exception as e:
            logger.error(f"Failed to post new review summary note: {e}")
            raise

        for note in existing_notes:
            try:
                note.delete()
                logger.info(f"Deleted existing review summary note (id={note.id})")
            except Exception as e:
                logger.warning(f"Could not delete old review note (id={note.id}): {e}")

    def post(self, shared, prep_res, exec_res):
        shared["summary_posted"] = exec_res["summary_posted"]
