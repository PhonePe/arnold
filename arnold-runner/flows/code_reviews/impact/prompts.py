"""Prompts used by the /impact (code review comment scoring) flow."""


def get_impact_system_message(is_merged=True):
    """Return the system message adapted for merged or open MR analysis."""
    if is_merged:
        state_context = (
            "The repository is checked out at the merged state — "
            "the code you see IS the final merged code."
        )
        task = "determine if the feedback was applied in the final merged code"
    else:
        state_context = (
            "The repository is checked out at the source branch HEAD — "
            "the code you see is the current state of the MR before merge."
        )
        task = (
            "determine if the feedback has been addressed so far in the "
            "current source branch code"
        )

    return (
        "You are an expert code review analyst evaluating whether code review "
        f"feedback was incorporated into the code.\n\n"
        "You have access to the FULL repository on disk via your built-in "
        f"filesystem tools (view, grep, list, etc.). {state_context}\n\n"
        "Rules:\n"
        f"- For each review comment, {task}.\n"
        "- Read the actual file contents at the relevant lines to verify.\n"
        "- Be fair and accurate — don't assume a comment was ignored just "
        "because the exact wording differs.\n"
        "- Consider semantic equivalence: the developer may have addressed the "
        "concern in a different way than suggested.\n"
        "- Comments that are questions, acknowledgments, or purely informational "
        "should be scored as 'not_applicable'.\n"
        "- Do NOT modify any files in the repository.\n"
        "- Call write_impact_analysis exactly ONCE with all your findings."
    )


def build_impact_analysis_prompt(review_comments, merged_diff_map, local_repo_path,
                                is_merged=True):
    """Build the prompt for analyzing the impact of review comments.

    The prompt references pre-staged files on disk (`.diffs/` and
    `.comments/comments.yaml`) instead of embedding content inline, keeping
    the prompt small enough to pass as a CLI argument to opencode.

    Parameters
    ----------
    review_comments : list[dict]
        All review comments collected from the MR.
    merged_diff_map : dict
        Mapping of file_path -> diff_content for the MR.
    local_repo_path : str
        Path to the cloned repository.
    is_merged : bool
        True for post-merge analysis, False for pre-merge analysis.
    """

    # --- Build a lightweight comment summary (IDs + file references only) ---
    comments_summary = []
    for idx, c in enumerate(review_comments):
        comment_id = f"comment-{idx}"
        file_path = c.get("file_path", "")
        line_number = c.get("line_number", "")
        author = c.get("author", "unknown")
        entry = f"- {comment_id}: {file_path}"
        if line_number:
            entry += f" (line {line_number})"
        entry += f" by {author}"
        comments_summary.append(entry)
    comments_summary_text = "\n".join(comments_summary) if comments_summary else "(none)"

    # --- List available diff files ---
    diff_files_list = "\n".join(
        f"- `.diffs/{fp}.diff`" for fp in merged_diff_map.keys()
    ) if merged_diff_map else "(no diffs staged)"

    # --- State-aware text ---
    if is_merged:
        title = "Post-Merge Code Review Impact Analysis"
        repo_state = (
            "The repository is checked out at the **merged state** — the files you read represent\n"
            "the final code that was merged."
        )
        task_description = (
            "The review comments were posted on this merge request BEFORE it was merged.\n"
            "Your task is to determine whether each comment's feedback was incorporated into the\n"
            "final merged code."
        )
        code_label = "merged code"
    else:
        title = "Pre-Merge Code Review Impact Analysis"
        repo_state = (
            "The repository is checked out at the **source branch HEAD** — the files you read\n"
            "represent the current state of the code in this MR before merge."
        )
        task_description = (
            "The review comments were posted on this open merge request.\n"
            "Your task is to determine whether each comment's feedback has been addressed\n"
            "in the current source branch code."
        )
        code_label = "current code"

    prompt = f"""# {title}

## Repository Access

**IMPORTANT:** You have full access to the local repository at: `{local_repo_path}`

{repo_state}
Use the filesystem tools (view, grep, list) to read
the actual file contents when evaluating whether a comment's feedback was applied.

---

## Pre-Staged Data (read from disk)

All review comments and diffs have been pre-staged on disk for you:

### Review Comments ({len(review_comments)} total)

Read the full comment details from: `.comments/comments.yaml`
```
cat .comments/comments.yaml
```

Each entry in the YAML file contains: file_path, line_number, body, author,
comment_type, has_suggestion, suggested_code, thread_replies, and label.

Comment summary (for quick reference — read the YAML for full details):
{comments_summary_text}

### MR Diffs ({len(merged_diff_map)} files)

Per-file diffs are staged at `.diffs/<file_path>.diff`. Read them with:
```
cat .diffs/path/to/file.ext.diff
```

Available diff files:
{diff_files_list}

---

## Task

{task_description}

---

## Scoring Criteria

For each comment, assign one of these statuses:

### applied (score: 0.8 - 1.0)
The suggestion or feedback was directly implemented in the {code_label}. This includes:
- The exact suggestion was applied (score ~1.0)
- An equivalent fix was applied that addresses the same concern (score ~0.8-0.9)
- A GitLab suggestion block was accepted and applied
- The discussion thread shows an alternative solution was agreed upon, and
  that alternative was implemented in the {code_label} (score ~0.8-0.9)

### partially_applied (score: 0.4 - 0.79)
Some aspect of the feedback was addressed, but not completely:
- Part of a multi-point suggestion was applied
- The concern was partially addressed but not fully resolved
- A related but incomplete fix was made

### ignored (score: 0.0 - 0.39)
The feedback was not addressed at all in the {code_label}:
- The code at the relevant location is unchanged from what was reviewed
- The issue raised in the comment still exists in the {code_label}
- No evidence that the feedback influenced the {code_label}

### not_applicable (score: 0.0)
The comment does not require a code change:
- The comment is a question (e.g., "Why was this done?")
- The comment is an acknowledgment or approval (e.g., "Looks good", "LGTM")
- The comment is purely informational or educational
- The relevant code was removed entirely (file deleted, section removed)
- The comment is a general MR-level note not tied to specific code

---

## How to Evaluate Each Comment

1. **Read the comments file** — `cat .comments/comments.yaml` to get all comment details.
2. **For each comment**, read the discussion thread (included in the YAML) — check whether an
   alternative solution was discussed and agreed upon. The final agreement
   in the thread takes precedence over the original suggestion.
3. **Read the diff** — `cat .diffs/<file_path>.diff` for the relevant file.
4. **Read the actual file** — use filesystem tools to read the file at the
   relevant lines in the {code_label}.
5. **Compare** — does the {code_label} reflect the feedback?
   - For suggestion blocks: check if the suggested code matches the {code_label}.
   - For descriptive comments: check if the concern/issue raised has been addressed.
6. **Score** — assign status and confidence score.

---

## Output

Call the `write_impact_analysis` tool **EXACTLY ONCE** with:
- A result entry for **every** comment (comment-0 through comment-{len(review_comments) - 1})
- An overall summary of findings
- The overall adoption rate (fraction of actionable comments that were applied or
  partially applied)

For each result entry, provide:
- `comment_id` — the ID from the comment list (e.g., "comment-0")
- `status` — one of: "applied", "partially_applied", "ignored", "not_applicable"
- `score` — float between 0.0 and 1.0
- `reasoning` — brief 1-2 sentence explanation of why you assigned this score
- `final_code_snippet` — the actual code at the relevant location in the merged
  version (empty string if not applicable)
- `comment_category` — category label for the comment. If the comment already has a
  `label` field in the YAML, use that value directly. For human comments without a
  pre-existing label, classify into one of: "security", "critical bug", "possible bug",
  "possible issue", "performance", "best practice", "maintainability", "enhancement",
  "typo", or "general".
"""
    return prompt
