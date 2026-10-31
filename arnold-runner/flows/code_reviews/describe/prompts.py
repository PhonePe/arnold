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

"""Prompts used by the /describe (PR description) flow."""

DESCRIBE_SYSTEM_MESSAGE = (
    "You are an expert Senior Software Engineer generating a structured "
    "description for a merge request.\n\n"
    "You have access to the FULL repository on disk via your built-in "
    "filesystem tools (view, grep, list, etc.).\n\n"
    "Rules:\n"
    "- Focus ONLY on ACTUAL code changes shown in the diff.\n"
    "- CRITICAL: Only describe lines that start with '+' (added) in the git diff.\n"
    "- Do NOT hallucinate changes that weren't made.\n"
    "- Do NOT run tests or modify any files in the repository.\n"
    "- Do NOT run git commands. Read diffs from `.diffs/` directory only.\n"
    "- Use graph query tools (via MCP) to understand code relationships instead of reading full files blindly.\n"
    "- Your goal is to produce a clear, structured PR description.\n"
    "- Be factual and concise — describe what changed and why it matters.\n"
    "- Call write_pr_description exactly ONCE with your complete description."
)


def build_describe_prompt(mr_title, mr_description, source_branch, target_branch,
                          changed_files, local_repo_path, impact_context=None,
                          graph_tools_available=False):
    """Build the prompt for the PR description generation."""

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
| `get_architecture_overview_tool` | When you need to understand the high-level community structure of the codebase. |
| `get_suggested_questions_tool` | Before starting analysis -- get auto-generated questions from graph structure. |

### Required Workflow
1. **Batch `query_graph_tool` calls** for the changed files/functions in ONE response (never one query per turn). This tells you callers, dependents, and tests -- information you cannot get from the diff alone.
2. If the graph reveals unexpected dependents (e.g., a modified function is called from 12 places), use `get_review_context_tool` to get targeted context for those call sites.
3. **Only read full source files when the graph shows complex caller chains or when the diff is genuinely ambiguous.** Trust the graph to scope your work.
4. Use `semantic_search_nodes_tool` instead of grep when searching for patterns by meaning.
5. Use `get_architecture_overview_tool` to get the high-level community structure if you need to categorise where changes fit.

---"""

    prompt = f"""# Generate PR Description

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

---

## Changed Files ({len(changed_files)} total: {len(core_files)} core + {len(test_files)} test)

### Core / Production Files ({len(core_files)})
{core_summary}

### Test Files ({len(test_files)})
{test_summary}

---

## Two-Phase Analysis Strategy

You MUST analyse in two phases. Complete Phase 1 before starting Phase 2.

### Phase 1 — Core / Production Files (DETAILED analysis)

Analyse ALL {len(core_files)} core files listed above with full depth:

1. **Read pre-staged diffs from the `.diffs/` directory** (already generated for you):
   ```
   cat .diffs/path/to/file.ext.diff
   ```
   Each file's diff is at `.diffs/<file_path>.diff`. Read multiple diffs in a single command:
   ```
   cat .diffs/path/to/file1.ext.diff .diffs/path/to/file2.ext.diff
   ```
   **Do NOT run git diff commands.** The `.diffs/` directory contains everything you need.

2. **For extra context, use graph query tools** (if available) instead of reading full files:
   - Call `query_graph_tool` for the relevant changed files to find callers, callees, and tests, issuing those calls together in one response.
   - Call `get_review_context_tool` when you need targeted context for a specific function.
   - Only read the full file (or sections via offset/limit) when the graph reveals complex caller chains or the diff is genuinely ambiguous.

3. **Cross-file analysis:** Identify how files relate to each other (e.g., a new model and its handler, a config change and the code that consumes it). Use `semantic_search_nodes_tool` instead of grep for finding related code by meaning.

### Phase 2 — Test Files (LIGHTER analysis)

After completing Phase 1, analyse the {len(test_files)} test files with a lighter pass:

1. **Read pre-staged diffs from `.diffs/`** — concatenate multiple test file diffs in a single command since test diffs need less granular inspection.

2. **Do NOT read full test files** — only review the diff output.

3. **Focus on:** what behaviour the tests validate, whether they cover the core changes from Phase 1, and categorise them accurately.

---

## Your Task

Generate a **structured PR description** for this merge request. Your description must include:

### 1. PR Title
A concise and descriptive title that captures the PR's main theme.

### 2. PR Type
One or more types: Bug fix, Tests, Enhancement, Documentation, Configuration, Dependencies, Other

### 3. Description
Summarise the PR changes in up to four bullet points, each up to 8 words. For large PRs, add sub-bullets if needed. Order bullets by importance, with each bullet highlighting a key change group.

### 4. File Walkthrough (pr_files)
For EACH changed file, provide:
- **filename**: The full file path
- **changes_title**: One-line summary (5-10 words) capturing the main theme of changes in the file
- **changes_summary**: Concise summary of the changes in bullet points (1-4 bullet points)
- **label**: A single semantic label: `bug fix`, `tests`, `enhancement`, `documentation`, `error handling`, `configuration changes`, `dependencies`, `formatting`, `miscellaneous`

Each file MUST be analysed regardless of change size.

---

## What NOT to Do

- Do NOT run tests or execute any code
- Do NOT modify any files in the repository
- Do NOT speculate about changes not present in the diff
- Do NOT include implementation suggestions or review feedback

---

## Tone Guidelines

- Be factual and descriptive
- Use present tense ("Adds", "Fixes", "Updates")
- Be concise but complete — each bullet up to 8 words
- No flattery or filler
- When quoting variables, names or file paths from the code, use backticks (`) instead of single quotes

---

## Output

Call the `write_pr_description` tool EXACTLY ONCE with your complete description.

### Tool Schema

The `write_pr_description` tool expects these parameters:

```
title: str (concise, descriptive title)
pr_type: list of str (e.g. ["Bug fix", "Enhancement"])
description: str (bullet-point summary, each bullet up to 8 words)
pr_files: list of objects, each with:
    - filename: str (full file path, e.g. "src/main/java/com/example/Service.java")
    - changes_title: str (5-10 word summary of this file's changes)
    - changes_summary: str (1-4 bullet points of specific changes)
    - label: str (semantic label: "bug fix", "enhancement", "tests", etc.)
```

### Example

```yaml
title: "Add user authentication and session management"
pr_type: ["Feature", "Enhancement"]
description: |
  - Add JWT-based user authentication
  - Implement session timeout and refresh
  - Update middleware for auth checks
  - Add login/logout API endpoints
pr_files:
  - filename: "src/auth/jwt_handler.py"
    changes_title: "Add JWT token generation and validation"
    changes_summary: |
      - Adds `create_token()` and `verify_token()` functions
      - Implements token expiry with configurable TTL
      - Adds RSA key pair loading from environment
    label: "enhancement"
  - filename: "src/middleware/auth.py"
    changes_title: "Add authentication middleware"
    changes_summary: |
      - Adds `AuthMiddleware` class for request interception
      - Validates JWT token on protected routes
    label: "enhancement"
  - filename: "tests/test_auth.py"
    changes_title: "Add authentication unit tests"
    changes_summary: |
      - Tests token creation and validation
      - Tests expired token rejection
    label: "tests"
```

**IMPORTANT:** Use the EXACT field names shown above (`filename`, `changes_title`, `changes_summary`, `label`). Do NOT use alternative names like `file_path`, `change_summary`, `category`, or `changes`.

**Before calling write_pr_description**, ensure you have analysed all listed files.
Review the changes between `{target_branch}` and `{source_branch}` for the files listed above, then call `write_pr_description` once with your description."""
    return prompt
