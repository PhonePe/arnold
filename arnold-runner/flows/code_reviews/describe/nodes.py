import logging
import gitlab
from utils import scm
from pocketflow import Node
from pydantic import BaseModel, Field
from typing import List, Optional
from utils.call_llm import call_llm_agentic
from utils.config import get_harness_label, get_harness_name, get_review_model
from utils.logger import get_logger
from flows.code_reviews.describe.prompts import (
    DESCRIBE_SYSTEM_MESSAGE,
    build_describe_prompt,
)

# Set up logger
logger = get_logger("code_review_describe_nodes_logger")


class ExecuteDescribe(Node):
    """Execute the PR description generation in agent mode.

    The LLM agent gets full repo access via *working_directory*,
    explores the codebase, then calls the ``write_pr_description`` tool
    exactly once with structured description data.
    """

    def prep(self, shared):
        changed_files = shared.get("changed_files", [])
        source_branch = shared.get("source_branch", "")
        target_branch = shared.get("target_branch", "main")
        mr_title = shared.get("mr_title", "")
        mr_description = shared.get("mr_description", "")
        local_repo_path = shared.get("local_repo_path", ".")
        project_name = shared.get("project_name", "Unknown Project")
        impact_context = shared.get("impact_context", "")

        graph_tools_available = shared.get("code_graph_built", False)

        return {
            "changed_files": changed_files,
            "source_branch": source_branch,
            "target_branch": target_branch,
            "mr_title": mr_title,
            "mr_description": mr_description,
            "local_repo_path": local_repo_path,
            "project_name": project_name,
            "impact_context": impact_context,
            "graph_tools_available": graph_tools_available,
        }

    def exec(self, prep_res):
        from utils.tool_adapter import define_tool

        changed_files = prep_res["changed_files"]
        source_branch = prep_res["source_branch"]
        target_branch = prep_res["target_branch"]
        mr_title = prep_res["mr_title"]
        mr_description = prep_res["mr_description"]
        local_repo_path = prep_res["local_repo_path"]
        impact_context = prep_res.get("impact_context", "")
        graph_tools_available = prep_res.get("graph_tools_available", False)

        if not changed_files:
            logger.info("No changed files to describe")
            return {"describe_data": {}, "raw_output": ""}

        # --- Captured state for tool closure ---
        captured_description: dict = {}

        # ---- Pydantic models for the tool ----
        class FileDescription(BaseModel):
            model_config = {"extra": "allow"}

            filename: str = Field(
                default="",
                description="The full file path of the relevant file"
            )
            changes_summary: str = Field(
                default="",
                description="Concise summary of the changes in the relevant file, in bullet points (1-4 bullet points)"
            )
            changes_title: str = Field(
                default="",
                description="One-line summary (5-10 words) capturing the main theme of changes in the file"
            )
            label: str = Field(
                default="enhancement",
                description="A single semantic label for the type of change: 'bug fix', 'tests', 'enhancement', 'documentation', 'error handling', 'configuration changes', 'dependencies', 'formatting', 'miscellaneous'"
            )

        class WritePRDescriptionParams(BaseModel):
            model_config = {"extra": "allow"}

            title: str = Field(
                description="A concise and descriptive title that captures the PR's main theme"
            )
            pr_type: List[str] = Field(
                description="One or more types: Bug fix, Tests, Enhancement, Documentation, Configuration, Dependencies, Other"
            )
            description: str = Field(
                description="Summarise the PR changes in up to four bullet points, each up to 8 words. Order bullets by importance."
            )
            pr_files: List[FileDescription] = Field(
                default_factory=list,
                description="A list of all the files that were changed in the PR with summary of their changes"
            )

        @define_tool(description=(
            "Write the final PR description. Call this ONCE after you have "
            "reviewed all changed files. Provide the title, PR type, "
            "bullet-point description, and a per-file walkthrough."
        ))
        def write_pr_description(params: WritePRDescriptionParams) -> str:
            logger.info(
                f"[tool] write_pr_description called — "
                f"title='{params.title}', types={params.pr_type}, "
                f"{len(params.pr_files)} files"
            )

            pr_files = []
            for item in params.pr_files:
                entry = item.model_dump() if hasattr(item, "model_dump") else dict(item)
                # Handle field name aliases from LLM
                if not entry.get("filename") and entry.get("file_path"):
                    entry["filename"] = entry.pop("file_path")
                if not entry.get("changes_title") and entry.get("change_summary"):
                    entry["changes_title"] = entry.pop("change_summary")
                if not entry.get("label") and entry.get("category"):
                    entry["label"] = entry.pop("category")
                if not entry.get("changes_summary") and entry.get("changes"):
                    changes = entry.pop("changes")
                    if isinstance(changes, list):
                        entry["changes_summary"] = "\n".join(f"- {c}" for c in changes)
                    else:
                        entry["changes_summary"] = str(changes)
                pr_files.append(entry)

            # Normalize description to string
            desc = params.description
            if isinstance(desc, list):
                desc = "\n".join(f"- {d}" for d in desc)

            captured_description.update({
                "title": params.title,
                "pr_type": params.pr_type,
                "description": desc,
                "pr_files": pr_files,
            })

            return (
                f"OK — PR description saved. Title: '{params.title}', "
                f"Types: {params.pr_type}, {len(pr_files)} files described."
            )

        tools = [write_pr_description]

        # Build prompt
        describe_prompt = build_describe_prompt(
            mr_title=mr_title,
            mr_description=mr_description,
            source_branch=source_branch,
            target_branch=target_branch,
            changed_files=changed_files,
            local_repo_path=local_repo_path,
            impact_context=impact_context,
            graph_tools_available=graph_tools_available,
        )

        _harness_name = get_harness_name()
        logger.info(f"Invoking {_harness_name} harness for PR description generation...")

        try:
            _final_response = call_llm_agentic(
                describe_prompt,
                tools=tools,
                system_message=DESCRIBE_SYSTEM_MESSAGE,
                model=get_review_model(),
                working_directory=local_repo_path,
            )

            logger.info(f"{_harness_name} PR description completed")

            if not captured_description:
                logger.warning("write_pr_description tool was not called by the agent")
                captured_description.update({
                    "title": mr_title or "Untitled PR",
                    "pr_type": ["Enhancement"],
                    "description": "No description generated.",
                    "pr_files": [],
                })

            return {
                "describe_data": captured_description,
                "raw_output": _final_response,
            }

        except Exception as e:
            logger.error(f"{_harness_name} PR description failed: {e}")
            return {"describe_data": {}, "raw_output": ""}

    def post(self, shared, prep_res, exec_res):
        shared["describe_data"] = exec_res["describe_data"]
        shared["describe_raw_output"] = exec_res.get("raw_output", "")


class FormatDescribeMarkdown(Node):
    """Convert captured description data into GitLab-compatible markdown."""

    def prep(self, shared):
        gitlab_url = shared.get("gitlab_url", "")
        project_name = shared.get("project_name", "")
        mr_iid = shared.get("merge_request_iid")
        mr_diff_url = ""
        if gitlab_url and project_name and mr_iid:
            mr_diff_url = f"{gitlab_url}/{project_name}/-/merge_requests/{mr_iid}/diffs"

        return {
            "describe_data": shared.get("describe_data", {}),
            "project_name": project_name,
            "source_branch": shared.get("source_branch", ""),
            "target_branch": shared.get("target_branch", ""),
            "merge_request_iid": mr_iid,
            "mr_diff_url": mr_diff_url,
            "gitlab_url": gitlab_url,
            "changed_files": shared.get("changed_files", []),
            "local_repo_path": shared.get("local_repo_path", ""),
            "merge_base": shared.get("merge_base", ""),
        }

    def exec(self, prep_res):
        describe_data = prep_res["describe_data"]

        if not describe_data:
            logger.info("No description data to format")
            return {"markdown": ""}

        markdown = format_describe_as_markdown(
            describe_data,
            mr_diff_url=prep_res["mr_diff_url"],
            gitlab_url=prep_res["gitlab_url"],
            project_name=prep_res["project_name"],
            source_branch=prep_res["source_branch"],
            changed_files=prep_res["changed_files"],
            local_repo_path=prep_res["local_repo_path"],
            merge_base=prep_res["merge_base"],
        )

        logger.info(f"Formatted description markdown ({len(markdown)} chars)")
        return {"markdown": markdown}

    def post(self, shared, prep_res, exec_res):
        shared["describe_markdown"] = exec_res["markdown"]


class PostDescription(Node):
    """Post the PR description as a single MR note."""

    def prep(self, shared):
        return {
            "markdown": shared.get("describe_markdown", ""),
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
            return {"description_posted": False}

        if dry_run:
            logger.info("Dry run mode — printing PR description to console")
            logger.info("\n" + markdown)
            return {"description_posted": False}

        if not gitlab_token or not project_id or not merge_request_iid:
            logger.warning("Missing GitLab credentials — skipping description post")
            return {"description_posted": False}

        try:
            gl = scm.connect(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr = project.mergerequests.get(merge_request_iid)

            self._delete_and_create_note(mr, markdown)

            logger.info(f"Posted PR description to MR {merge_request_iid}")
            return {"description_posted": True}
        except Exception as e:
            logger.error(f"Failed to post PR description: {e}")
            return {"description_posted": False}

    def _delete_and_create_note(self, mr, markdown):
        """Delete any existing description notes, then post a fresh one."""
        header_marker = "## Title"
        existing_notes = []
        try:
            notes = mr.notes.list(all=True)
            for note in notes:
                if note.body and header_marker in note.body:
                    existing_notes.append(note)
        except Exception as e:
            logger.warning(f"Could not search for existing description notes: {e}")

        try:
            new_note = mr.notes.create({"body": markdown})
            logger.info(
                f"Created new description note (id={new_note.id})"
            )
        except Exception as e:
            logger.error(f"Failed to post new description note: {e}")
            raise

        for note in existing_notes:
            try:
                note.delete()
                logger.info(f"Deleted existing description note (id={note.id})")
            except Exception as e:
                logger.warning(f"Could not delete old description note (id={note.id}): {e}")

    def post(self, shared, prep_res, exec_res):
        shared["description_posted"] = exec_res["description_posted"]


# ---------------------------------------------------------------------------
# Markdown formatting
# ---------------------------------------------------------------------------

MAX_COMMENT_LENGTH = 65000


def _get_diff_stats(local_repo_path, merge_base, changed_files):
    """Get +N/-N line counts per file using git diff --numstat."""
    import subprocess
    stats = {}
    if not local_repo_path or not merge_base:
        return stats
    try:
        cmd = ['git', '-C', local_repo_path, 'diff', '--numstat', merge_base, 'HEAD']
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode == 0:
            for line in result.stdout.strip().split('\n'):
                if not line:
                    continue
                parts = line.split('\t')
                if len(parts) >= 3:
                    adds = parts[0] if parts[0] != '-' else '0'
                    dels = parts[1] if parts[1] != '-' else '0'
                    file_path = parts[2]
                    stats[file_path] = (int(adds), int(dels))
    except Exception as e:
        logger.warning(f"Could not get diff stats: {e}")
    return stats


def format_describe_as_markdown(describe_data, mr_diff_url="", gitlab_url="",
                                 project_name="", source_branch="", changed_files=None,
                                 local_repo_path="", merge_base=""):
    """Convert structured description data into a GitLab-compatible markdown comment.

    Format matches the code-reviews (PR-Agent) describe output.
    """
    changed_files = changed_files or []

    # Get +N/-N stats per file via git
    diff_stats = _get_diff_stats(local_repo_path, merge_base, changed_files)

    # --- Build pr_body section by section ---
    pr_body = ""

    # --- PR Type ---
    pr_type = describe_data.get("pr_type", [])
    pr_body += "### **PR Type**\n"
    if pr_type:
        pr_body += ", ".join(pr_type) + "\n"
    pr_body += "\n\n___\n\n"

    # --- Description ---
    description = describe_data.get("description", "")
    pr_body += "### **Description**\n"
    if description:
        if isinstance(description, list):
            description = "\n".join(f"- {d}" for d in description)
        pr_body += f"{description}\n"
    pr_body += "\n\n___\n\n"

    # --- Changes walkthrough ---
    pr_files = describe_data.get("pr_files", [])
    if pr_files:
        changes_walkthrough = _build_walkthrough_html(
            pr_files, diff_stats, gitlab_url, project_name, source_branch
        )
        pr_body += "\n\n### **Changes walkthrough** \U0001f4dd\n" + changes_walkthrough

    # --- Assemble full markdown ---
    title = describe_data.get("title", "")
    markdown = f"## Title\n\n{title}\n\n___\n{pr_body}"

    # Truncate if over GitLab limit
    if len(markdown) > MAX_COMMENT_LENGTH:
        truncation_msg = "\n\n---\n*Description output was truncated due to length limits.*"
        markdown = markdown[: MAX_COMMENT_LENGTH - len(truncation_msg)] + truncation_msg
        logger.warning(f"Description markdown truncated to {MAX_COMMENT_LENGTH} chars")

    return markdown


def _build_walkthrough_html(pr_files, diff_stats, gitlab_url, project_name, source_branch):
    """Build the changes walkthrough HTML table matching PR-Agent format."""
    from collections import OrderedDict

    COLLAPSIBLE_FILE_LIST_THRESHOLD = 4

    # Group files by label
    groups: OrderedDict[str, list] = OrderedDict()
    for item in pr_files:
        if not isinstance(item, dict):
            continue
        label = item.get("label", "enhancement")
        groups.setdefault(label, []).append(item)

    # Collapse file lists when total file count exceeds threshold
    num_files = sum(len(v) for v in groups.values())
    use_collapsible = num_files > COLLAPSIBLE_FILE_LIST_THRESHOLD

    blob_base = ""
    if gitlab_url and project_name and source_branch:
        blob_base = f"{gitlab_url}/{project_name}/-/blob/{source_branch}"

    pr_body = ""
    pr_body += "<table>"
    pr_body += '<thead><tr><th></th><th align="left">Relevant files</th></tr></thead>'
    pr_body += "<tbody>"

    for label, items in groups.items():
        pr_body += f'<tr><td><strong>{label.capitalize()}</strong></td>'
        if use_collapsible:
            pr_body += f"<td><details><summary>{len(items)} files</summary><table>"
        else:
            pr_body += "<td><table>"

        for item in items:
            file_path = item.get("filename", "")
            changes_title = item.get("changes_title", "")
            changes_summary = item.get("changes_summary", "")

            short_name = file_path.rsplit("/", 1)[-1] if "/" in file_path else file_path

            # Build filename display with changes_title
            if changes_title and changes_title.strip() != "...":
                filename_publish = f"<strong>{short_name}</strong><dd><code>{changes_title}</code></dd>"
            else:
                filename_publish = f"<strong>{short_name}</strong>"

            # Compute +N/-N from diff stats
            adds, dels = diff_stats.get(file_path, (0, 0))
            diff_plus_minus = f"+{adds}/-{dels}"

            # File link
            link = ""
            if blob_base and file_path:
                link = f"{blob_base}/{file_path}?ref_type=heads"

            # Format changes_summary as HTML list
            file_change_description_br = ""
            if changes_summary:
                # changes_summary can be bullet text or plain text
                summary_lines = changes_summary.strip().split('\n')
                parts = []
                for line in summary_lines:
                    line = line.strip().lstrip('- ')
                    if line:
                        parts.append(f"<li>{line}")
                file_change_description_br = "\n".join(parts)

            # Add file data (matches code-reviews add_file_data format)
            if not file_change_description_br:
                pr_body += f"""
<tr>
  <td>{filename_publish}</td>
  <td><a href="{link}">{diff_plus_minus}</a></td>

</tr>
"""
            else:
                pr_body += f"""
<tr>
  <td>
    <details>
      <summary>{filename_publish}</summary>
<hr>

{file_path}

{file_change_description_br}


</details>


  </td>
  <td><a href="{link}">{diff_plus_minus}</a></td>

</tr>
"""

        if use_collapsible:
            pr_body += "</table></details></td></tr>"
        else:
            pr_body += "</table></td></tr>"

    pr_body += "</tbody></table>"

    return pr_body
