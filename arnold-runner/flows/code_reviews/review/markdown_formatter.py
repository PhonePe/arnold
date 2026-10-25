"""Markdown formatting utilities for the /review summary flow."""

import logging
from html import escape

logger = logging.getLogger("code_review_markdown_logger")

# GitLab comment body limit
MAX_COMMENT_LENGTH = 65000


def format_review_as_markdown(review_data, project_name="", source_branch="",
                               target_branch="", mr_iid=None, mr_diff_url="",
                               gitlab_url="", ai_improve_done=False,
                               ai_improve_comment_count=0,
                               comment_analysis_results=None,
                               comment_adoption_rate=None,
                               comment_analysis_summary="",
                               previous_review_comments=None):
    """Convert structured review data into a GitLab-compatible markdown comment.

    Uses an HTML ``<table>`` layout matching GitLab's native rendering for
    a clean, compact review guide.

    Parameters
    ----------
    review_data : dict
        Captured output from the ``write_review_summary`` tool.
    project_name, source_branch, target_branch, mr_iid :
        Optional context for the header.
    mr_diff_url : str
        Base URL for the MR diffs page.
    gitlab_url : str
        GitLab instance URL (e.g. ``https://gitlab.example.com``).
    ai_improve_done : bool
        Whether the AI bot has posted any inline suggestions on the MR.
    ai_improve_comment_count : int
        Number of inline suggestions the bot posted.
    comment_analysis_results : list or None
        Per-comment LLM analysis results (status, score, reasoning).
    comment_adoption_rate : float or None
        Overall fraction of addressed comments (0.0–1.0).
    comment_analysis_summary : str
        Overall summary from the LLM analysis.
    previous_review_comments : list or None
        Full comment objects used to correlate results with file paths.
    """
    if comment_analysis_results is None:
        comment_analysis_results = []
    if previous_review_comments is None:
        previous_review_comments = []

    lines: list[str] = []

    # --- Header ---
    lines.append("## PR Reviewer Guide 🔍\n")
    lines.append("Here are some key observations to aid the review process:\n")
    lines.append("<table>")

    # --- AI /improve Status row ---
    lines.extend(_format_ai_improve_status(ai_improve_done, ai_improve_comment_count))

    # --- Previous Comment Analysis row ---
    if comment_analysis_results:
        lines.extend(_format_comment_analysis(
            comment_analysis_results,
            comment_adoption_rate,
            comment_analysis_summary,
            previous_review_comments,
        ))

    # --- Effort row ---
    effort = format_review_effort(review_data)
    if effort:
        lines.append(f"<tr><td>\n\n{effort}\n\n</td></tr>")

    # --- Tests row ---
    relevant_tests = review_data.get("relevant_tests", "")
    if relevant_tests:
        test_emoji = "✅" if relevant_tests.lower().startswith("yes") else "⚠️"
        lines.append(f"<tr><td>🧪&nbsp;<strong>PR contains tests</strong>&nbsp;{test_emoji}&nbsp;{relevant_tests}</td></tr>")

    # --- Security concerns row ---
    security = review_data.get("security_concerns", "No")
    if security:
        sec_lower = str(security).lower().strip()
        if sec_lower not in ("no", "no.", "none", "n/a", ""):
            lines.append(
                f"<tr><td>🔒&nbsp;<strong>Security concerns</strong><br><br>\n\n"
                f"{security}</td></tr>"
            )
        else:
            lines.append(
                "<tr><td>🔒&nbsp;<strong>No security concerns identified</strong></td></tr>"
            )

    # --- Ticket compliance row ---
    ticket_checks = review_data.get("ticket_compliance_check", [])
    if ticket_checks:
        lines.extend(_format_ticket_compliance(ticket_checks))

    # --- RFC compliance row ---
    rfc_checks = review_data.get("rfc_compliance_check", [])
    if rfc_checks:
        lines.extend(_format_rfc_compliance(rfc_checks))

    # --- Can be split / Multiple PR themes row ---
    can_be_split = review_data.get("can_be_split", [])
    if can_be_split:
        row_lines = []
        row_lines.append("<tr><td>🔀 <strong>Multiple PR themes</strong><br><br>\n")
        for idx, sub_pr in enumerate(can_be_split, 1):
            if isinstance(sub_pr, dict):
                title = sub_pr.get("title", f"Sub-PR {idx}")
                files = sub_pr.get("relevant_files", [])
                row_lines.append(f"<details><summary>")
                row_lines.append(f"Sub-PR theme: <b>{title}</b></summary>\n")
                row_lines.append("___\n")
                row_lines.append("Relevant files:\n")
                for f in files:
                    row_lines.append(f"- {f}")
                row_lines.append("\n___\n")
                row_lines.append("</details>\n")
        row_lines.append("</td></tr>")
        lines.extend(row_lines)

    # --- Key issues / Recommended focus areas row ---
    key_issues = review_data.get("key_issues", [])
    if key_issues:
        row_lines = []
        row_lines.append("<tr><td>⚡&nbsp;<strong>Recommended focus areas for review</strong><br><br>\n")
        for issue in key_issues:
            if isinstance(issue, dict):
                _format_issue_html(row_lines, issue, gitlab_url, project_name, source_branch)
        row_lines.append("\n</td></tr>")
        lines.extend(row_lines)

    lines.append("</table>")

    markdown = "\n".join(lines)

    # Truncate if over GitLab limit
    if len(markdown) > MAX_COMMENT_LENGTH:
        truncation_msg = "\n\n---\n⚠️ *Review output was truncated due to length limits.*"
        markdown = markdown[: MAX_COMMENT_LENGTH - len(truncation_msg)] + truncation_msg
        logger.warning(f"Review markdown truncated from {len(markdown)} to {MAX_COMMENT_LENGTH} chars")

    return markdown


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _format_ai_improve_status(ai_improve_done: bool, comment_count: int) -> list:
    """Render the AI /improve suggestions status table row."""
    if ai_improve_done:
        noun = "suggestion" if comment_count == 1 else "suggestions"
        return [
            f"<tr><td>🤖&nbsp;<strong>AI code suggestions</strong>&nbsp;✅&nbsp;"
            f"{comment_count} inline {noun} posted — run <code>/improve</code> to refresh</td></tr>"
        ]
    else:
        return [
            "<tr><td>🤖&nbsp;<strong>AI code suggestions</strong>&nbsp;⚠️&nbsp;"
            "No AI suggestions yet — run <code>/improve</code> to get inline code suggestions</td></tr>"
        ]


def _format_comment_analysis(results: list, adoption_rate, summary: str,
                              review_comments: list) -> list:
    """Render the previous comment analysis table row.

    Shows overall adoption rate + per-status counts, then a collapsible
    breakdown of each comment with its LLM-assessed status.
    """
    if not results:
        return []

    # Count per-status
    status_counts: dict[str, int] = {}
    for r in results:
        s = r.get("status", "unknown")
        status_counts[s] = status_counts.get(s, 0) + 1

    applied = status_counts.get("applied", 0)
    partial = status_counts.get("partially_applied", 0)
    ignored = status_counts.get("ignored", 0)
    not_applicable = status_counts.get("not_applicable", 0)
    actionable = applied + partial + ignored

    # Overall adoption emoji + label
    if adoption_rate is None:
        if actionable == 0:
            adoption_pct_str = "N/A"
            overall_emoji = "⭕"
        else:
            rate = (applied + partial) / actionable
            adoption_pct_str = f"{rate:.0%}"
            overall_emoji = "✅" if rate >= 0.8 else ("🔶" if rate >= 0.4 else "❌")
    else:
        adoption_pct_str = f"{adoption_rate:.0%}"
        overall_emoji = "✅" if adoption_rate >= 0.8 else ("🔶" if adoption_rate >= 0.4 else "❌")

    row_lines = []
    row_lines.append(
        f"<tr><td>💬&nbsp;<strong>Previous review comments</strong>&nbsp;"
        f"{overall_emoji}&nbsp;Adoption rate: <strong>{adoption_pct_str}</strong>"
        f"&nbsp;({len(results)} comment{'s' if len(results) != 1 else ''})<br><br>\n"
    )

    # Status summary bar
    status_parts = []
    if applied:
        status_parts.append(f"✅&nbsp;{applied} applied")
    if partial:
        status_parts.append(f"🔶&nbsp;{partial} partially applied")
    if ignored:
        status_parts.append(f"❌&nbsp;{ignored} ignored")
    if not_applicable:
        status_parts.append(f"⭕&nbsp;{not_applicable} not applicable")
    if status_parts:
        row_lines.append(" &nbsp;·&nbsp; ".join(status_parts) + "\n")

    # Overall summary (if available)
    if summary and summary.strip():
        row_lines.append(f"\n{summary.strip()}\n")

    # Per-comment breakdown (collapsible)
    _STATUS_EMOJI = {
        "applied": "✅",
        "partially_applied": "🔶",
        "ignored": "❌",
        "not_applicable": "⭕",
    }
    _STATUS_LABEL = {
        "applied": "Applied",
        "partially_applied": "Partially applied",
        "ignored": "Ignored",
        "not_applicable": "Not applicable",
    }

    # Build a comment index for quick lookup
    comment_by_idx = {i: c for i, c in enumerate(review_comments)}

    for result in results:
        comment_id = result.get("comment_id", "")
        status = result.get("status", "not_applicable")
        reasoning = result.get("reasoning", "")
        emoji = _STATUS_EMOJI.get(status, "⭕")
        label = _STATUS_LABEL.get(status, status.replace("_", " ").title())

        # Extract index from "comment-N"
        try:
            idx = int(comment_id.split("-")[-1])
            comment = comment_by_idx.get(idx, {})
        except (ValueError, IndexError):
            comment = {}

        file_path = comment.get("file_path", "")
        line_number = comment.get("line_number") or ""
        body_preview = (comment.get("body") or "")[:120].replace("\n", " ").strip()
        if len(comment.get("body") or "") > 120:
            body_preview += "…"

        summary_line = f"{emoji} <strong>{label}</strong>"
        if file_path:
            loc = f"`{file_path}`"
            if line_number:
                loc += f" line {line_number}"
            summary_line += f" — {loc}"

        row_lines.append(f"<details><summary>{summary_line}</summary>\n")
        if body_preview:
            row_lines.append(f"\n> {body_preview}\n")
        if reasoning:
            row_lines.append(f"\n{reasoning}\n")
        row_lines.append("</details>\n")

    row_lines.append("</td></tr>")
    return row_lines


def format_review_effort(review_data: dict) -> str:
    """Render only effort estimates supported by a rationale."""
    effort = review_data.get("estimated_effort")
    rationale = review_data.get("estimated_effort_rationale")
    if type(effort) is not int or not 1 <= effort <= 5 or not isinstance(rationale, str) or not rationale.strip():
        return ""
    return f"**Review effort: {effort}/5**\n\nBasis: {escape(rationale.strip())}"


def _detect_lang(file_path: str) -> str:
    """Guess the code-fence language hint from a file extension."""
    ext_map = {
        ".java": "java", ".py": "python", ".js": "javascript", ".ts": "typescript",
        ".go": "go", ".rb": "ruby", ".rs": "rust", ".kt": "kotlin", ".scala": "scala",
        ".yml": "yaml", ".yaml": "yaml", ".xml": "xml", ".json": "json",
        ".sh": "bash", ".sql": "sql", ".html": "html", ".css": "css",
        ".c": "c", ".cpp": "cpp", ".h": "c", ".cs": "csharp", ".swift": "swift",
        ".gradle": "groovy", ".groovy": "groovy", ".tf": "hcl",
    }
    if not file_path:
        return ""
    for ext, lang in ext_map.items():
        if file_path.endswith(ext):
            return lang
    return ""


def _blob_url(file_path: str, gitlab_url: str, project_name: str,
              source_branch: str, start_line: int = None,
              end_line: int = None) -> str:
    """Build a GitLab blob URL pointing to specific lines on the source branch."""
    if not (gitlab_url and project_name and source_branch and file_path):
        return ""
    url = f"{gitlab_url}/{project_name}/-/blob/{source_branch}/{file_path}?ref_type=heads"
    if start_line:
        url += f"#L{start_line}"
        if end_line and end_line != start_line:
            url += f"-{end_line}"
    return url


def _format_issue_html(lines: list, issue: dict, gitlab_url: str,
                       project_name: str, source_branch: str):
    """Append a collapsible issue block (HTML) to *lines*.

    Format matches GitLab's native rendering:
    - <summary> contains the clickable link header + description (always visible)
    - After </summary> contains the code snippet (expandable)
    """
    header = issue.get("issue_header", "Issue")
    content = issue.get("issue_content", "")
    file_path = issue.get("relevant_file", "")
    start_line = issue.get("start_line")
    end_line = issue.get("end_line")
    code_snippet = issue.get("code_snippet", "")

    # Build the link to the file in the source branch
    blob = _blob_url(file_path, gitlab_url, project_name, source_branch,
                     start_line, end_line)

    if blob:
        header_html = f"<a href='{blob}'><strong>{header}</strong></a>"
    else:
        header_html = f"<strong>{header}</strong>"

    # Description text goes inside <summary> so it's always visible
    lines.append(f"<details><summary>{header_html}\n")
    lines.append(f"{content}")
    lines.append(f"</summary>\n")

    # Code snippet goes after </summary> — shown only when expanded
    if code_snippet:
        # Detect language from file extension
        lang = _detect_lang(file_path)
        lines.append(f"```{lang}")
        lines.append(code_snippet)
        lines.append("```\n")

    lines.append("</details>\n")


def _ticket_compliance_level(compliant: str, not_compliant: str, needs_human: str) -> tuple:
    """Determine compliance level and emoji for a single ticket.

    Returns (level_str, emoji_str) following the PR-Agent decision tree:
      - Fully compliant ✅  — all requirements met, no human verification needed
      - PR Code Verified ✅  — all requirements met, but some need human verification
      - Partially compliant 🔶 — mix of compliant and non-compliant
      - Not compliant ❌     — requirements not met
    """
    has_compliant = bool(compliant and compliant.strip() and
                         compliant.strip().lower() not in ("none", "n/a", ""))
    has_not_compliant = bool(not_compliant and not_compliant.strip() and
                             not_compliant.strip().lower() not in ("none", "n/a", ""))
    has_human = bool(needs_human and needs_human.strip() and
                     needs_human.strip().lower() not in ("none", "n/a", ""))

    if has_compliant and not has_not_compliant:
        if has_human:
            return "PR Code Verified", "✅"
        return "Fully compliant", "✅"
    if has_compliant and has_not_compliant:
        return "Partially compliant", "🔶"
    if has_not_compliant:
        return "Not compliant", "❌"
    # Fallback: no data parsed
    return "Unknown", "❔"


def _format_ticket_compliance(ticket_checks: list) -> list:
    """Render the ticket compliance HTML table row.

    Matches the PR-Agent output format: an overall compliance emoji in the
    header, followed by per-ticket details with compliance level, compliant
    requirements, non-compliant requirements, and items needing human review.
    """
    row_lines: list[str] = []
    all_levels: list[str] = []

    # First pass — compute per-ticket compliance levels
    ticket_entries = []
    for tc in ticket_checks:
        if not isinstance(tc, dict):
            continue
        compliant = tc.get("fully_compliant_requirements", "")
        not_compliant = tc.get("not_compliant_requirements", "")
        needs_human = tc.get("requires_further_human_verification", "")
        level, emoji = _ticket_compliance_level(compliant, not_compliant, needs_human)
        all_levels.append(level)
        ticket_entries.append((tc, level, emoji))

    # Overall compliance emoji
    if all(lv in ("Fully compliant", "PR Code Verified") for lv in all_levels):
        overall_emoji = "✅"
    elif any(lv == "Not compliant" for lv in all_levels):
        overall_emoji = "❌" if all(lv == "Not compliant" for lv in all_levels) else "🔶"
    else:
        overall_emoji = "🔶"

    row_lines.append(
        f"<tr><td>🎫&nbsp;<strong>Ticket compliance analysis {overall_emoji}</strong><br><br>\n"
    )

    for tc, level, emoji in ticket_entries:
        ticket_url = tc.get("ticket_url", "")
        ticket_reqs = tc.get("ticket_requirements", "")
        compliant = tc.get("fully_compliant_requirements", "")
        not_compliant = tc.get("not_compliant_requirements", "")
        needs_human = tc.get("requires_further_human_verification", "")

        # Build ticket link
        ticket_label = ticket_url.rsplit("/", 1)[-1] if "/" in ticket_url else ticket_url
        if ticket_url.startswith("http"):
            link = f"[{ticket_label}]({ticket_url})"
        else:
            link = f"**{ticket_label}**"

        row_lines.append(f"**{link} - {level} {emoji}**\n")

        if compliant and compliant.strip().lower() not in ("none", "n/a", ""):
            row_lines.append(f"\nCompliant requirements:\n\n{_ensure_bullets(compliant)}\n")
        if not_compliant and not_compliant.strip().lower() not in ("none", "n/a", ""):
            row_lines.append(f"\nNon-compliant requirements:\n\n{_ensure_bullets(not_compliant)}\n")
        if needs_human and needs_human.strip().lower() not in ("none", "n/a", ""):
            row_lines.append(f"\nRequires further human verification:\n\n{_ensure_bullets(needs_human)}\n")

        row_lines.append("\n")

    row_lines.append("</td></tr>")
    return row_lines


def _format_rfc_compliance(rfc_checks: list) -> list:
    """Render the RFC compliance HTML table row.

    Shows per-section RFC analysis: implemented, missing, and deviated items.
    """
    row_lines: list[str] = []

    # Compute overall status
    has_missing = any(
        bool(
            rc.get("missing", "").strip()
            and rc.get("missing", "").strip().lower() not in ("none", "n/a", "")
        )
        for rc in rfc_checks
    )
    has_deviated = any(
        bool(
            rc.get("deviated", "").strip()
            and rc.get("deviated", "").strip().lower() not in ("none", "n/a", "")
        )
        for rc in rfc_checks
    )

    if has_missing or has_deviated:
        overall_emoji = "🔶"
    else:
        overall_emoji = "✅"

    row_lines.append(
        f"<tr><td>📋&nbsp;<strong>RFC / Design compliance {overall_emoji}</strong><br><br>\n"
    )

    for rc in rfc_checks:
        if not isinstance(rc, dict):
            continue
        section = rc.get("rfc_section", "Unknown Section")
        implemented = rc.get("implemented", "")
        missing = rc.get("missing", "")
        deviated = rc.get("deviated", "")

        row_lines.append(f"**{section}**\n")

        if (
            implemented
            and implemented.strip()
            and implemented.strip().lower() not in ("none", "n/a", "")
        ):
            row_lines.append(
                f"\n✅ Implemented:\n\n{_ensure_bullets(implemented)}\n"
            )
        if (
            missing
            and missing.strip()
            and missing.strip().lower() not in ("none", "n/a", "")
        ):
            row_lines.append(
                f"\n❌ Missing:\n\n{_ensure_bullets(missing)}\n"
            )
        if (
            deviated
            and deviated.strip()
            and deviated.strip().lower() not in ("none", "n/a", "")
        ):
            row_lines.append(
                f"\n⚠️ Deviated:\n\n{_ensure_bullets(deviated)}\n"
            )

        row_lines.append("\n")

    row_lines.append("</td></tr>")
    return row_lines


def _ensure_bullets(text: str) -> str:
    """Ensure each non-empty line in *text* starts with '- '."""
    result = []
    for line in text.strip().split("\n"):
        line = line.strip()
        if not line:
            continue
        if not line.startswith("- "):
            line = f"- {line}"
        result.append(line)
    return "\n".join(result)



