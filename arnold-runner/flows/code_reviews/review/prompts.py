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

"""Prompts used by the /review (summary) flow."""

PREVIOUS_COMMENT_ANALYSIS_SYSTEM_MESSAGE = (
    "You are an expert code review analyst evaluating whether previous review "
    "feedback has been incorporated into the current code.\n\n"
    "You have access to the FULL repository on disk via your built-in "
    "filesystem tools (view, grep, list, etc.). The repository is checked out "
    "at the source branch HEAD — the code you see is the current state of the MR.\n\n"
    "Rules:\n"
    "- For each review comment, determine if the feedback has been addressed "
    "in the current source branch code.\n"
    "- Read the actual file contents at the relevant lines to verify.\n"
    "- Be fair and accurate — don't assume a comment was ignored just because "
    "the exact wording differs.\n"
    "- Consider semantic equivalence: the developer may have addressed the "
    "concern in a different way than suggested.\n"
    "- Comments that are questions, acknowledgments, or purely informational "
    "should be scored as 'not_applicable'.\n"
    "- Do NOT modify any files in the repository.\n"
    "- Call write_comment_status_analysis exactly ONCE with all your findings."
)


def build_previous_comment_analysis_prompt(review_comments, local_repo_path, diffs_dir="", changed_files=None):
    """Build the prompt for analyzing whether previous review comments were addressed.

    Parameters
    ----------
    review_comments : list[dict]
        All previous review comments collected from the MR.
    local_repo_path : str
        Path to the checked-out repository.
    diffs_dir : str
        Path to the pre-staged .diffs/ directory (may be empty string if unavailable).
    changed_files : list[dict] | None
        All MR changed files (``{"path": ...}``). Only the commented files' diffs are
        read up front; the rest are listed in case a fix landed in another file.
    """
    # Build per-comment summary with ABSOLUTE paths so the model never needs
    # to guess the base directory.
    comments_summary = []
    for idx, c in enumerate(review_comments):
        comment_id = f"comment-{idx}"
        file_path = c.get("file_path", "")
        abs_file = f"{local_repo_path}/{file_path}" if file_path else "(no file)"
        line_number = c.get("line_number", "")
        line_type = c.get("line_type", "new")
        author = c.get("author", "unknown")
        entry = f"- {comment_id}: {abs_file}"
        if line_number:
            location = f"old line {line_number}" if line_type == "old" else f"line {line_number}"
            entry += f" ({location})"
        entry += f" by {author}"
        comments_summary.append(entry)
    comments_summary_text = "\n".join(comments_summary) if comments_summary else "(none)"

    diffs_section = ""
    if diffs_dir:
        commented = list(dict.fromkeys(c["file_path"] for c in review_comments if c.get("file_path")))
        others = [f["path"] for f in changed_files or [] if f.get("path") not in commented]
        first_read = " ".join(
            [f"{local_repo_path}/.comments/prev_comments.yaml"]
            + [f"{diffs_dir}/{path}.diff" for path in commented]
        )
        others_text = "\n".join(f"- `{diffs_dir}/{path}.diff`" for path in others) or "(none)"
        diffs_section = f"""
### MR Diffs

Per-file diffs are pre-staged as **absolute paths**: `{diffs_dir}/<file_path>.diff`
(a missing diff means that file is unchanged in this MR).

**Start with this single command** — it reads all comments plus the diffs of every
file that has a comment:
```
cat {first_read}
```

Other changed files (no comments on them). Read one of these diffs **only** when a
comment's fix is not visible in its own file — e.g. the logic moved to a helper:
{others_text}

"""

    prompt = f"""# Previous Review Comment Status Analysis

## Repository Access

The repository is checked out at: `{local_repo_path}`

**CRITICAL — always use absolute paths:**
- Source files: `{local_repo_path}/<file_path>`
- Diff files:   `{diffs_dir}/<file_path>.diff`

Never use relative paths. Never resolve paths from any other directory.
If a comment's `file_path` is `foo/bar/Baz.java`, read it as:
`{local_repo_path}/foo/bar/Baz.java`

---

## Pre-Staged Data (read from disk)

### Previous Review Comments ({len(review_comments)} total)

Read the full comment details from the absolute path{" (included in the single command under MR Diffs below)" if diffs_dir else ""}:
```
cat {local_repo_path}/.comments/prev_comments.yaml{" ..." if diffs_dir else ""}
```

Each entry contains: file_path, line_number, line_type, body, author, and thread_replies.
The `file_path` values in the YAML are **relative** — always prefix with `{local_repo_path}/` when reading.
`line_type` is `"new"` when `line_number` refers to a line in the current (post-diff) file, or
`"old"` when the comment was anchored to a removed line that no longer exists in the current file —
in that case, consult the diff (`{diffs_dir}/<file_path>.diff`) rather than the current file at that
line number.

Comment summary (absolute paths for quick reference):
{comments_summary_text}
{diffs_section}
---

## Task

These comments were posted on this open merge request by previous reviewers (human or AI).
Your task is to determine whether each comment's feedback has been addressed
in the current source branch code.

---

## Scoring Criteria

For each comment, assign one of these statuses:

### applied (score: 0.8 – 1.0)
The feedback was directly implemented in the current code:
- The exact suggestion was applied (score ~1.0)
- An equivalent fix addresses the same concern (score ~0.8–0.9)
- The thread replies show an agreed alternative solution that was implemented

### partially_applied (score: 0.4 – 0.79)
Some aspect of the feedback was addressed but not completely:
- Part of a multi-point suggestion was applied
- The concern was partially addressed but not fully resolved

### ignored (score: 0.0 – 0.39)
The feedback was not addressed at all:
- The code at the relevant location is unchanged
- The issue raised still exists in the current code

### not_applicable (score: 0.0)
The comment does not require a code change:
- Purely a question, acknowledgment, or informational note
- The relevant code was removed entirely

---

## How to Evaluate Each Comment

Every turn resends the whole conversation, so batch: issue all lookups you already
know about in ONE response (one `bash` command chaining `cat`/`sed -n` with
`echo ----` separators), never one file per turn.

1. **Read comments YAML and the commented files' diffs** — the single `cat` command above (or `cat {local_repo_path}/.comments/prev_comments.yaml` if no diffs are listed)
2. **Check thread replies** — the last agreed-upon resolution in the thread takes precedence
3. **Read the current code around each comment** — `sed -n <line-30>,<line+30>p {local_repo_path}/<file_path>`
   for all comments in one command; widen the range only if the change spans further
4. **Compare** — does the current code reflect the feedback? If not visible there, check the
   other changed files' diffs listed above before marking a comment `ignored`
5. **Score** — assign status and confidence score

---

## Output

Call the `write_comment_status_analysis` tool **EXACTLY ONCE** with:
- A result entry for **every** comment (comment-0 through comment-{len(review_comments) - 1})
- An overall summary of findings
- The overall adoption rate (fraction of actionable comments that were applied or partially applied)
"""
    return prompt


REVIEW_SYSTEM_MESSAGE = (
    "You are an expert Senior Software Engineer performing a high-level "
    "code review of a merge request.\n\n"
    "You have access to the FULL repository on disk via your built-in "
    "filesystem tools (view, grep, list, etc.).\n\n"
    "Rules:\n"
    "- Focus ONLY on ACTUAL code changes shown in the diff.\n"
    "- CRITICAL: Assess changes on lines that start with '+' (added) in the git diff. "
    "Defects caused by deleted lines, or by a change that alters behavior for unchanged "
    "callers, ARE in scope — point to the added line that introduces the change.\n"
    "- Do NOT hallucinate issues in code that wasn't changed.\n"
    "- Do NOT run tests or modify any files in the repository.\n"
    "- Do NOT run git commands. Read diffs from `.diffs/` directory only.\n"
    "- Use graph query tools (via MCP) to understand code relationships instead of reading full files blindly.\n"
    "- Your goal is a HIGH-LEVEL review summary, NOT inline code suggestions.\n"
    "- Identify key issues, estimate effort, and flag security concerns.\n"
    "- Verify before flagging — if below 60% confident, investigate further or skip.\n"
    "- Do not drop a verified concern just because it seems minor or the MR/Jira calls it "
    "intentional — stated intent explains why, it does not remove a concrete cost or risk.\n"
    "- Call write_review_summary exactly ONCE with your complete review."
)


def build_review_summary_prompt(mr_title, mr_description, source_branch, target_branch,
                                changed_files, local_repo_path,
                                impact_context=None, graph_tools_available=False,
                                jira_file_path="", rfc_file_path=""):
    """Build the prompt for the high-level review summary."""

    # Classify files into core (production) and test categories
    test_patterns = ("/test/", "/tests/", "/test_", "_test.", "Test.", ".test.",
                     "/spec/", ".spec.", "/fixtures/", "/testutil/",
                     "/testing/", "/__tests__/")
    core_files = []
    test_files = []
    for f in changed_files:
        if any(p in f["path"] for p in test_patterns):
            test_files.append(f)
        else:
            core_files.append(f)

    core_summary = "\n".join(
        [f"- {f['path']} ({f['status']})" for f in core_files]
    ) or "(none)"
    test_summary = "\n".join(
        [f"- {f['path']} ({f['status']})" for f in test_files]
    ) or "(none)"

    from datetime import datetime
    today_date = datetime.now().strftime("%Y-%m-%d")

    graph_tools_section = ""
    if graph_tools_available:
        graph_tools_section = """\

## Code Intelligence Tools (via MCP)

You have access to a **code-review-graph** MCP server that queries the structural graph of this codebase. **Use these tools to work smarter.** They are faster and more precise than manual grep/cat exploration.

| Tool | When to Call |
|------|-------------|
| `query_graph_tool` | For changed files whose callers, callees, tests, or imports matter -- who calls a modified function? What tests cover it? |
| `semantic_search_nodes_tool` | When you need to find related code by meaning (better than grep). |
| `get_impact_radius_tool` | After reading diffs -- verify the blast radius matches your mental model. |
| `get_affected_flows_tool` | When changes touch entry points (activities, fragments, API handlers). |
| `get_review_context_tool` | When you need compact, targeted context for a specific function. |
| `get_knowledge_gaps_tool` | After reviewing all files -- flag untested hotspots, isolated code, structural weaknesses as review findings. |
| `get_surprising_connections_tool` | When you suspect cross-module coupling or architecture boundary violations. |
| `get_hub_nodes_tool` | When a changed file is highly connected -- this signals extra risk. |
| `get_bridge_nodes_tool` | When changes affect code that couples multiple domains -- high blast radius. |
| `find_large_functions_tool` | After reviewing files -- suggest splitting oversized functions as maintainability findings. |
| `get_suggested_questions_tool` | Before starting your review -- get auto-generated questions from graph structure. |

### Required Workflow
1. **Batch `query_graph_tool` calls** for the changed files/functions in ONE response (never one query per turn). This tells you callers, dependents, and tests -- information you cannot get from the diff alone.
2. If the graph reveals unexpected dependents (e.g., a modified function is called from 12 places), use `get_review_context_tool` to get targeted context for those call sites.
3. **Only read full source files when the graph shows complex caller chains or when the diff is genuinely ambiguous.** Trust the graph to scope your work.
4. Use `semantic_search_nodes_tool` instead of grep when searching for patterns by meaning.
5. **After reviewing all files**, use `get_knowledge_gaps_tool` to catch structural issues (untested hotspots, thin communities) you might have missed.
6. Use `get_suggested_questions_tool` at any time to sanity-check your review coverage.

---"""

    prompt = f"""# High-Level Code Review

## Repository Access

**IMPORTANT:** You have full access to the local repository at: `{local_repo_path}`

The **pre-staged diffs** in `.diffs/` contain all the changes. Use those as your primary source. For extra context about how changed code is used in the codebase, use the graph query tools below -- do NOT read full files blindly.
{graph_tools_section}

---

## Merge Request Details
- **Source Branch:** {source_branch}
- **Target Branch:** {target_branch}
- **Title:** {mr_title}
- **Description:** {mr_description}
- **Date:** {today_date}

Use this context to understand the intent and scope of the changes. The description may contain:
- Business requirements or acceptance criteria
- Links to related issues or documentation
- Design decisions or trade-offs
- Areas the author wants special attention on

Factor this context into your review — flag issues that conflict with stated requirements or miss described edge cases.

---

## Impact Analysis (blast radius)

Below is a structural analysis of the codebase showing which files are affected by this MR, including callers, dependents, and tests that could be impacted.

Use this to prioritise your review focus and to check if changes have unanticipated effects on dependent code.

{impact_context or "(No impact analysis available — review all changed files.)"}

---
"""

    # --- Conditional: Jira Ticket context (filesystem-based) ---
    if jira_file_path:
        prompt += """
## Jira Ticket Context

Jira ticket details have been fetched and saved to `.jira_context.md` in the repo root.

**Before reviewing any code**, read the full Jira context by running:
```
cat .jira_context.md
```

Use the ticket context to validate that the code changes align with the stated requirements, acceptance criteria, and expected behavior.

---
"""

    # --- Conditional: RFC / Design document context (filesystem-based) ---
    if rfc_file_path:
        prompt += """
## RFC / Design Document Context

An RFC / design document has been fetched and saved to `.rfc_context.md` in the repo root.

**Before reviewing any code**, read the full RFC by running:
```
cat .rfc_context.md
```

Use the RFC to:
- Validate that the implementation matches the stated design decisions and approach
- Identify RFC requirements or edge cases not addressed in the code
- Flag API contracts, data models, or flows that differ from the RFC specification
- Catch security or error-handling requirements from the RFC that were overlooked
- Flag code that implements features explicitly listed as **out of scope** in the RFC

---
"""

    prompt += f"""
## Changed Files ({len(changed_files)} total: {len(core_files)} core + {len(test_files)} test)

### Core / Production Files ({len(core_files)})
{core_summary}

### Test Files ({len(test_files)})
{test_summary}

---

## Two-Phase Review Strategy

You MUST review in two phases. Complete Phase 1 before starting Phase 2.

**Turn efficiency:** every turn resends the whole conversation, so the number of turns drives cost. Plan each step's lookups together and issue them in ONE response — several tool calls at once, or one `bash` command chaining `cat`/`grep`/`sed -n` with `echo ----` separators. Never spend a turn on a single grep or a single file when other checks are already known. Read source as line ranges around the change (`sed -n 120,160p file`), not whole files.

### Phase 1 — Core / Production Files (DEEP review)

Review ALL {len(core_files)} core files listed above with full depth:

1. **Read pre-staged diffs from the `.diffs/` directory** (already generated for you):
   ```
   cat .diffs/path/to/file.ext.diff
   ```
   Each file's diff is at `.diffs/<file_path>.diff`. Read multiple diffs in a single command:
   ```
   cat .diffs/path/to/file1.ext.diff .diffs/path/to/file2.ext.diff
   ```
   **Do NOT run git diff commands.** The `.diffs/` directory contains everything you need.

2. **For context on how changed code is used, use graph query tools FIRST** (if available) before reading full files:
   - Call `query_graph_tool` for the relevant changed files to find callers, callees, and tests, issuing those calls together in one response.
   - Call `get_review_context_tool` when you need targeted context for a specific function.
   - **Read the full file (or large chunks via offset/limit) ONLY when the graph reveals complex caller chains or when the diff involves:**
     - **Conditional branching** (`when`, `if-else`, `switch`) — scan nearby branches for duplicates, dead code, or missing fallbacks
     - **Thread/concurrency context changes** (`withContext`, `Dispatchers`, `launch`) — verify I/O happens on the correct thread
     - **Layout or modifier changes** — removed modifiers often break surrounding elements not shown in the diff
     - **Function signature changes** — find all callers to check for breakage
     - **Security-sensitive operations** (permissions, auth, network, PII) — verify surrounding validation logic
   - For all other files, trust the diff and the graph context. Do NOT read full files blindly.

3. **Check conventions:** Read `.code_review_best_practices.md` in the repo root (placed there for you). Also check for CONVENTIONS.md, CONTRIBUTING.md, .editorconfig, or linter configs if present.

4. **Cross-file analysis:** Look for inconsistencies between related files (e.g., a model changed but its handler wasn't updated, or a new method added without proper error propagation). Use `semantic_search_nodes_tool` instead of grep for finding related code by meaning.

### Phase 2 — Test Files (LIGHTER review)

After completing Phase 1, review the {len(test_files)} test files with a focused, lighter pass:

1. **Read pre-staged diffs from `.diffs/`** — concatenate multiple test file diffs in a single command since test diffs need less granular inspection.

2. **Do NOT read full test files** — only review the diff output.

3. **Only assess these categories in test files:**
   - Whether tests cover the key changes from Phase 1, especially new fallback/error paths
   - Tests that don't actually test what they claim (mocking away the behavior under test)
   - Security: hardcoded credentials or tokens in test data

---

## Your Task

Produce a **high-level review summary** (not inline code suggestions). Evaluate the MR across these dimensions:

### 1. Estimated Effort to Review (1-5 scale)
Rate how much time and effort an experienced developer needs to review this MR:
- **1** — Trivial: config changes, typos, small fixes
- **2** — Small: a few straightforward changes
- **3** — Medium: multiple files, moderate logic
- **4** — Large: significant changes, complex logic
- **5** — Very large: architectural changes, many files, deep domain knowledge needed

Consider: number of files, complexity of changes, domain knowledge required.
Provide estimated_effort_rationale in 1-2 sentences explaining why this MR fits the rating.
Cite concrete changed files or behavior and the logic, cross-component, or testing complexity
the reviewer must inspect. Use only observed evidence; do not invent file counts or review times.
Effort measures review work, independently of whether you found any defects.

### 2. Relevant Tests
Does this MR include relevant test additions or updates? Answer "Yes" or "No", with a brief explanation.

### 3. Key Issues to Review
Identify EVERY issue a reviewer should focus on — there is no limit on the number of issues. Do not drop verified issues to keep the list short. Each issue should include:
- The specific file and line range
- A short header (e.g., "Possible Bug", "Error Handling Gap", "Race Condition")
- A concise description of what should be inspected (this will always be visible)
- The actual code snippet from the changed file that the issue refers to (this will be shown in a collapsible/expandable section). Include enough surrounding context so the reviewer can understand the code without opening the file.

**Priority order:** bugs > security > error handling > behavior changes for existing callers > performance > design

Performance counts when the cost is concrete: new per-request remote calls (LLM, HTTP, DB) on a request path, prompts/payloads that grow with data size, tight timeouts, O(n²) on unbounded data, or N+1 queries. Report these even when the MR/Jira describes the trade-off as intentional, citing the stated rationale.

### 4. Security Concerns
Are there security vulnerabilities introduced by these changes?
- Input validation gaps, injection risks, sensitive data exposure, auth issues
- Answer "No" if there are no concerns
- If yes, describe the specific concern with file references
"""

    # Conditional: Ticket Compliance task
    if jira_file_path:
        prompt += """
### 5. Ticket Compliance Check
For EACH related Jira ticket listed above, assess whether this PR fulfills the ticket's requirements:
- **ticket_url**: The Jira ticket URL
- **ticket_requirements**: Repeat, in your own words (bullet points), all requirements, sub-tasks, DoD, and acceptance criteria from the ticket
- **fully_compliant_requirements**: Items from above that ARE fulfilled by the PR code
- **not_compliant_requirements**: Items from above that are NOT fulfilled by the PR code
- **requires_further_human_verification**: Items that cannot be assessed through code review alone (e.g., UI checks, browser testing, integration testing)

"""
        next_section_num = 6
    else:
        next_section_num = 5

    # Conditional: RFC Compliance task
    if rfc_file_path:
        prompt += f"""
### {next_section_num}. RFC Compliance Check
The RFC / design document is available at `.rfc_context.md`. For each meaningful RFC section (e.g., Requirements, API Contract, Security Considerations, Out-of-Scope), assess whether this PR aligns with the RFC:
- **rfc_section**: The section title from the RFC (e.g., "Security Considerations")
- **implemented**: Requirements from this section that ARE correctly implemented in the PR
- **missing**: Requirements from this section that are NOT implemented in the PR
- **deviated**: Requirements implemented differently than the RFC specifies (include what the RFC says vs what the code does)

Only include sections where you found implemented, missing, or deviated items. Skip sections with no findings.

"""
        next_section_num += 1

    prompt += f"""
### {next_section_num}. Can Be Split (optional)
If this MR contains {len(changed_files)} changed files, could it be meaningfully split into smaller independent MRs? Only suggest splits if the sub-MRs are truly independent (no code dependencies between them). List 0-3 potential sub-MRs with their relevant files and titles.

---

## What NOT to Flag

- Pre-existing issues in unchanged code
- Minor missing tests — cover test gaps in Relevant Tests; raise a key issue only when new non-trivial logic (fallback, error path, new branch) has no test exercising it
- TODO/FIXME comments — these are intentional
- Stylistic preferences not in project conventions
- Import ordering, formatting — leave to linters
- Minor refactoring suggestions

---

## Output

Call the `write_review_summary` tool EXACTLY ONCE with your complete review.
If the code is clean with no issues, still call the tool with an evidence-based effort rating and an empty key_issues list.

### Tool Schema

The `write_review_summary` tool expects these parameters:

```
estimated_effort: int (1-5)
estimated_effort_rationale: str (required MR-specific explanation of the rating)
relevant_tests: str ("Yes ..." or "No ...")
security_concerns: str ("No" or description of concerns)
key_issues: list of objects, each with:
    - relevant_file: str (full file path, e.g. "src/main/java/com/example/Service.java")
    - issue_header: str (1-2 word title, e.g. "Possible Bug", "Race Condition")
    - issue_content: str (concise description of what to inspect — do NOT reference line numbers here)
    - start_line: int (start line number in the NEW file)
    - end_line: int (end line number in the NEW file)
    - code_snippet: str (the relevant code from the file — include enough context so
      a reviewer can understand without opening the file)
can_be_split: list of objects (optional, 0-3), each with:
    - relevant_files: list of str (file paths belonging to this sub-PR)
    - title: str (short title for the independent sub-PR)
ticket_compliance_check: list of objects (only if Jira tickets are linked), each with:
    - ticket_url: str (Jira ticket URL)
    - ticket_requirements: str (bullet-point list of all ticket requirements)
    - fully_compliant_requirements: str (bullet-point list of requirements fulfilled by this PR)
    - not_compliant_requirements: str (bullet-point list of requirements NOT fulfilled)
    - requires_further_human_verification: str (bullet-point list of items needing manual review)
rfc_compliance_check: list of objects (only if RFC document is available), each with:
    - rfc_section: str (RFC section title, e.g. "Security Considerations")
    - implemented: str (bullet-point list of RFC requirements fulfilled by this PR)
    - missing: str (bullet-point list of RFC requirements NOT implemented)
    - deviated: str (bullet-point list of requirements implemented differently than RFC specifies)
```

### Example

```yaml
estimated_effort: 3
estimated_effort_rationale: "The service and repository changes require tracing the new query through both layers and checking the pagination tests."
relevant_tests: "Yes - unit tests added for new service class"
security_concerns: "No"
key_issues:
  - relevant_file: "src/main/java/com/example/UserService.java"
    issue_header: "Possible Bug"
    issue_content: "The `findUser` method returns null when the user is not found, but callers do not check for null. This could cause NullPointerException in `processOrder`."
    start_line: 45
    end_line: 52
    code_snippet: |
      public User findUser(String id) {{
          return userRepository.findById(id).orElse(null);
      }}
can_be_split:
  - relevant_files:
      - "src/main/java/com/example/UserService.java"
    title: "Refactor user lookup logic"
ticket_compliance_check:
  - ticket_url: "https://jira.example.com/browse/PROJ-123"
    ticket_requirements: |
      - Implement user authentication
      - Add password validation rules
    fully_compliant_requirements: |
      - Implement user authentication
    not_compliant_requirements: |
      - Add password validation rules
    requires_further_human_verification: ""
rfc_compliance_check:
  - rfc_section: "Security Considerations"
    implemented: |
      - Input validation on all API endpoints
    missing: |
      - Rate limiting on login endpoint (RFC section 4.2 specifies max 5 req/min)
    deviated: |
      - Token expiry set to 24h in code; RFC specifies 1h (section 3.1)
```

**IMPORTANT:** Use the EXACT field names shown above. Do NOT use alternative names like `header`, `description`, `file`, or `files`.

**Before calling write_review_summary**, ensure you have reviewed all listed files.
Review the changes between `{target_branch}` and `{source_branch}` for the files listed above, then call `write_review_summary` once with your findings."""
    return prompt
