"""Prompts used by the sonarfix flow.

Each agent specialization has its own system message and prompt builder.
The flow runs agents sequentially: issues -> coverage -> duplication.

Skill files from the target repository's ``.github/skills/`` directory are
automatically loaded and appended to the agent's system message, so the
agent receives project-specific conventions (e.g. Dropwizard testing rules)
without hardcoding them here.
"""

import glob
import logging
import os
from utils.logger import get_logger

logger = get_logger("sonarfix_prompts_logger")

# ---------------------------------------------------------------------------
# System messages -- one per agent specialization
# ---------------------------------------------------------------------------

SYSTEM_MESSAGE = (
    "You are an expert Senior Software Engineer specializing in fixing "
    "SonarQube code quality issues.\n\n"
    "You have access to the FULL repository on disk via your built-in "
    "filesystem tools (view, grep, list, etc.).\n\n"
    "Rules:\n"
    "- Fix ONLY the SonarQube issues provided to you.\n"
    "- Do NOT introduce new issues or refactor unrelated code.\n"
    "- Make minimal, surgical changes -- change as few lines as possible.\n"
    "- Ensure fixes are syntactically valid and won't break compilation.\n"
    "- Preserve existing code style and conventions.\n"
    "- If a fix requires understanding context, read the full file first.\n"
    "- Call write_fix_results exactly ONCE with all fixes applied."
)

COVERAGE_SYSTEM_MESSAGE = (
    "You are an expert Senior Software Engineer specializing in writing "
    "unit tests to improve code coverage.\n\n"
    "You have access to the FULL repository on disk via your built-in "
    "filesystem tools (view, grep, list, etc.) **and a bash tool** to "
    "run shell commands.\n\n"
    "Rules:\n"
    "- Write tests ONLY for the uncovered new code identified below.\n"
    "- Follow the project's existing test patterns, frameworks, and conventions.\n"
    "- Place tests in the appropriate test directory (e.g. src/test/java for Maven).\n"
    "- Ensure tests are valid, compilable, and actually cover the identified lines.\n"
    "- Do NOT modify production code -- only add/edit test files.\n"
    "- Import necessary test framework classes (JUnit, Mockito, pytest, etc.).\n\n"
    "CRITICAL -- You MUST compile and validate every test you create:\n"
    "1. After writing all test files, compile ONLY the newly created test class(es):\n"
    "   - Maven:  `mvn test-compile -B -pl <module> -Dtest=<YourNewTestClass> "
    "-Dsurefire.failIfNoSpecifiedTests=false` (for multi-module; omit -pl for single-module)\n"
    "   - Gradle: `./gradlew testClasses`\n"
    "   - npm/TypeScript: `npx tsc --noEmit`\n"
    "   - Python: skip (no compilation step)\n"
    "2. If compilation fails, read the errors, fix the test files, and re-compile.\n"
    "   Repeat until compilation succeeds.\n"
    "3. Run ONLY the specific new test class(es) you created -- never the entire module:\n"
    "   - Maven:  `mvn test -B -pl <module> -Dtest=<YourNewTestClass> "
    "-Dsurefire.failIfNoSpecifiedTests=false`\n"
    "   - Gradle: `./gradlew test --tests com.example.YourNewTestClass`\n"
    "   - npm:    `npx jest -- YourNewTestFile`\n"
    "   - Python: `python -m pytest path/to/your_new_test.py -v --tb=short`\n"
    "4. If any test fails, read the failure output, fix the test, and re-run.\n"
    "   Repeat until all tests pass (max 3 retries per test class).\n"
    "5. If tests still fail after 3 fix attempts, mark the coverage item as "
    "'skipped' with a clear reason explaining what went wrong.\n"
    "6. Call write_fix_results exactly ONCE **only after all tests compile and pass**.\n\n"
    "IMPORTANT -- Do NOT run the entire project's test suite or the full module's "
    "tests. Only compile and run the specific new test class(es) you wrote."
)

DUPLICATION_SYSTEM_MESSAGE = (
    "You are an expert Senior Software Engineer specializing in refactoring "
    "duplicated code.\n\n"
    "You have access to the FULL repository on disk via your built-in "
    "filesystem tools (view, grep, list, etc.).\n\n"
    "Rules:\n"
    "- Refactor ONLY the duplicated code blocks identified below.\n"
    "- Extract common logic into shared methods/functions/utilities.\n"
    "- Do NOT change behavior -- refactoring must be behavior-preserving.\n"
    "- Make minimal changes -- only consolidate the identified duplications.\n"
    "- Ensure the code still compiles and existing tests pass.\n"
    "- Preserve existing code style and conventions.\n"
    "- Call write_fix_results exactly ONCE with all refactoring applied."
)


# ---------------------------------------------------------------------------
# Issue fix prompt
# ---------------------------------------------------------------------------

def build_sonarfix_prompt(issues, local_repo_path, changed_files=None):
    """Build the prompt that asks the agent to fix SonarQube issues.

    Parameters
    ----------
    issues : list[dict]
        SonarQube issues to fix (from the API response).
    local_repo_path : str
        Path to the local repository clone.
    changed_files : list[str] | None
        Files changed in this MR.
    """
    issues_block = _format_issues(issues)

    files_section = ""
    if changed_files:
        files_list = "\n".join(f"- `{f}`" for f in changed_files[:80])
        files_section = f"\n## Changed Files in MR\n\n{files_list}\n"

    return f"""# SonarQube Issue Fix Request

## Repository Access

**IMPORTANT:** You have full access to the local repository at: `{local_repo_path}`

Before making fixes, use tools to read the **full file** for each affected file to understand the surrounding context.

---

## Issues to Fix ({len(issues)})

{issues_block}

---
{files_section}
## How to Fix

For each issue:

1. **Read the full file** where the issue is located using filesystem tools.
2. **Understand the context** — look at imports, class hierarchy, how the code is used.
3. **Apply the minimal fix** that resolves the SonarQube issue.
4. **Verify** the fix doesn't break surrounding logic.

---

## Fix Guidelines by Issue Type

### Code Smells
- Remove unused variables, imports, and parameters
- Replace deprecated API calls with modern equivalents
- Reduce cognitive complexity by extracting methods or using early returns
- Fix naming convention violations
- Remove dead code and commented-out code
- Add missing `@Override` annotations (Java)
- Use diamond operator `<>` where applicable (Java)
- Replace raw types with parameterized types (Java)

### Bugs
- Fix null pointer dereferences with proper null checks
- Fix resource leaks by adding try-with-resources or proper close() calls
- Fix incorrect equality comparisons (== vs .equals())
- Fix array index out of bounds risks
- Fix concurrent modification issues

### Vulnerabilities
- Fix SQL injection by using parameterized queries
- Fix XSS by properly escaping output
- Fix path traversal by validating/sanitizing paths
- Remove hardcoded credentials
- Fix insecure random number generation

### Security Hotspots
- Review and fix flagged security-sensitive code
- Add input validation where needed
- Use secure defaults

---

## What NOT to Do

- Do NOT refactor or improve code beyond what's needed to fix the issue
- Do NOT change formatting or style unless it's the issue itself
- Do NOT add comments explaining the fix (the code should be self-explanatory)
- Do NOT modify tests unless the issue is in a test file
- Do NOT introduce new dependencies

---

## Output

After fixing all issues, call the `write_fix_results` tool ONCE with:
- A list of fix objects, each containing:
  - **issue_key**: The SonarQube issue key
  - **file_path**: Path to the fixed file
  - **status**: "fixed" or "skipped"
  - **reason**: Brief explanation (why fixed or why skipped)

If an issue cannot be fixed without significant refactoring or risk of breakage, mark it as "skipped" with a clear reason.

---

## Task

Fix the {len(issues)} SonarQube issues listed above in the repository at `{local_repo_path}`.
"""


# ---------------------------------------------------------------------------
# Coverage fix prompt
# ---------------------------------------------------------------------------

def build_coverage_fix_prompt(
    condition,
    measures,
    local_repo_path,
    changed_files=None,
    coverage_components=None,
):
    """Build a prompt that asks the agent to write tests to improve coverage.

    Parameters
    ----------
    condition : dict
        The failing quality gate condition (metricKey, actualValue, errorThreshold, etc.)
    measures : dict
        All fetched measures (new_coverage, new_lines_to_cover, etc.)
    local_repo_path : str
        Path to the cloned repository.
    changed_files : list[str] | None
        List of files changed in the MR (for targeted test writing).
    coverage_components : list[dict] | None
        File-level coverage data from ``api/measures/component_tree``.
    """
    actual = condition.get("actualValue", "?")
    required = condition.get("errorThreshold", "?")
    new_lines_to_cover = measures.get("new_lines_to_cover", "?")
    new_uncovered = measures.get("new_uncovered_lines", "?")

    files_section = ""
    if changed_files:
        files_list = "\n".join(f"- `{f}`" for f in changed_files[:50])
        files_section = f"\n## Changed Files (write tests for these)\n\n{files_list}\n"

    # File-level coverage breakdown
    coverage_section = ""
    if coverage_components:
        cov_lines = [
            "\n## File-Level Coverage Breakdown\n",
            "**Files with uncovered new code** (sorted by uncovered lines, descending):\n",
            "| File | Uncovered Lines | Lines to Cover | Line Coverage | Branch Coverage |",
            "|------|-----------------|----------------|---------------|-----------------|",
        ]
        for comp in coverage_components[:60]:
            path = comp.get("path", "?")
            uncovered = comp.get("new_uncovered_lines", "---")
            to_cover = comp.get("new_lines_to_cover", "---")
            line_cov = comp.get("new_line_coverage", "---")
            branch_cov = comp.get("new_branch_coverage", "---")
            if line_cov not in ("---", None):
                line_cov = f"{line_cov}%"
            if branch_cov not in ("---", None):
                branch_cov = f"{branch_cov}%"
            cov_lines.append(
                f"| `{path}` | {uncovered} | {to_cover} | {line_cov} | {branch_cov} |"
            )
        coverage_section = "\n".join(cov_lines)

    return f"""# Coverage Improvement Request

## Repository Access

**IMPORTANT:** You have full access to the local repository at: `{local_repo_path}`

## Quality Gate Failure

- **Current coverage on new code:** {actual}%
- **Required coverage:** >= {required}%
- **New lines to cover:** {new_lines_to_cover}
- **Uncovered new lines:** {new_uncovered}
{files_section}{coverage_section}
---

## How to Fix

1. **Explore the project** --- identify the test framework (JUnit, TestNG, pytest, Jest, etc.)
   and test directory structure.
2. **Identify uncovered new code** --- look at the changed/new files listed above (or use
   `git diff` to find new code added in this branch).
3. **Write unit tests** for the uncovered code paths:
   - Cover branches, edge cases, and error handling paths.
   - Mock external dependencies (HTTP clients, databases, etc.).
   - Follow existing test naming conventions.

## MANDATORY --- Compile and Run ONLY Your New Tests

After writing all test files you **MUST** verify they work. **Do NOT run the
entire module's test suite** — only compile and run the specific new test
class(es) you created.

4. **Compile** only your new test class(es) (use `bash`):
   - Maven (multi-module): `mvn test-compile -B -pl <module-name>`
   - Maven (single-module): `mvn test-compile -B`
   - Gradle: `./gradlew testClasses`
   - npm (TypeScript): `npx tsc --noEmit`
   - Python: skip this step
   If compilation fails -> read the errors -> fix your test files -> re-compile.
   **Repeat until compilation succeeds.**

5. **Run** ONLY the specific new test class(es) you created (use `bash`):
   - Maven (multi-module): `mvn test -B -pl <module-name> -Dtest=YourNewTestClass -Dsurefire.failIfNoSpecifiedTests=false`
   - Maven (single-module): `mvn test -B -Dtest=YourNewTestClass -Dsurefire.failIfNoSpecifiedTests=false`
   - Gradle: `./gradlew test --tests com.example.YourNewTestClass`
   - npm: `npx jest -- YourNewTestFile`
   - Python: `python -m pytest path/to/your_new_test.py -v --tb=short`
   If any tests fail -> read the failure output -> fix the test -> re-run.
   **Repeat until all tests pass (max 3 retries per test class).**

   **IMPORTANT:** Never use `mvn test -B` without `-Dtest=<ClassName>`. Always
   specify the exact test class name(s) you wrote to avoid running unrelated tests.

6. **Only after all tests compile and pass**, call `write_fix_results`.

## Guidelines

- Place test files in the correct test directory matching the project structure.
- Use the same test framework already used in the project.
- Do NOT modify production/source code --- only add test files.
- Focus on the NEW code --- do not write tests for pre-existing uncovered code.
- Prioritize branches and conditions that maximize line coverage.
- If a class has complex dependencies, use mocking (Mockito, unittest.mock, etc.).

## Output

After all tests compile and pass, call `write_fix_results` ONCE with:
- A list of fix objects, each containing:
  - **issue_key**: "coverage-<filename>" (e.g., "coverage-UserService.java")
  - **file_path**: Path to the test file you created/modified
  - **status**: "fixed" or "skipped"
  - **reason**: Brief explanation (e.g., "Added 3 test cases covering lines 45-67, all pass")

---

## Task

Write unit tests to bring coverage on new code from {actual}% to >= {required}% in the
repository at `{local_repo_path}`. **Do NOT call write_fix_results until every test
compiles and passes.**
"""


# ---------------------------------------------------------------------------
# Duplication fix prompt
# ---------------------------------------------------------------------------

def build_duplication_fix_prompt(
    condition,
    measures,
    local_repo_path,
    changed_files=None,
    duplication_components=None,
):
    """Build a prompt that asks the agent to refactor duplicated code.

    Parameters
    ----------
    condition : dict
        The failing quality gate condition for duplications.
    measures : dict
        All fetched measures.
    local_repo_path : str
        Path to the cloned repository.
    changed_files : list[str] | None
        Files changed in the MR.
    duplication_components : list[dict] | None
        File-level duplication data from ``api/measures/component_tree``.
    """
    actual = condition.get("actualValue", "?")
    required = condition.get("errorThreshold", "?")
    new_dup_lines = measures.get("new_duplicated_lines", "?")
    new_dup_blocks = measures.get("new_duplicated_blocks", "?")
    new_lines = measures.get("new_lines", "?")

    files_section = ""
    if changed_files:
        files_list = "\n".join(f"- `{f}`" for f in changed_files[:50])
        files_section = f"\n## Changed Files (check for duplications)\n\n{files_list}\n"

    # File-level duplication breakdown
    duplication_section = ""
    if duplication_components:
        dup_lines = [
            "\n## File-Level Duplication Breakdown\n",
            "**Files with duplicated new code** (sorted by duplicated lines, descending):\n",
            "| File | Duplicated Lines | Duplicated Blocks | Density |",
            "|------|-----------------|-------------------|---------|",
        ]
        for comp in duplication_components[:60]:
            path = comp.get("path", "?")
            dup_l = comp.get("new_duplicated_lines", "---")
            dup_b = comp.get("new_duplicated_blocks", "---")
            density = comp.get("new_duplicated_lines_density", "---")
            if density not in ("---", None):
                density = f"{density}%"
            dup_lines.append(f"| `{path}` | {dup_l} | {dup_b} | {density} |")
        duplication_section = "\n".join(dup_lines)

    return f"""# Duplication Reduction Request

## Repository Access

**IMPORTANT:** You have full access to the local repository at: `{local_repo_path}`

## Quality Gate Failure

- **Current duplication density on new code:** {actual}%
- **Required:** <= {required}%
- **New duplicated lines:** {new_dup_lines}
- **New duplicated blocks:** {new_dup_blocks}
- **Total new lines:** {new_lines}
{files_section}{duplication_section}
---

## How to Fix

1. **Find duplicated blocks** in the new/changed files --- look for repeated patterns of
   3+ lines that are identical or near-identical.
2. **Extract common logic** into shared helper methods, utility functions, or base classes.
3. **Replace** all occurrences with calls to the shared implementation.
4. **Verify** the refactoring preserves behavior (no logic changes).

## Guidelines

- Focus ONLY on duplications in NEW code (changed files in this MR).
- Extract methods/functions when >=2 places share the same logic.
- Use appropriate design patterns: Template Method, Strategy, utility class, etc.
- Preserve method signatures and public API contracts.
- Do NOT refactor pre-existing duplications outside the MR scope.
- Ensure existing tests still pass after refactoring.

## Output

After refactoring, call `write_fix_results` ONCE with:
- A list of fix objects, each containing:
  - **issue_key**: "duplication-<filename>" (e.g., "duplication-OrderService.java")
  - **file_path**: Path to the refactored file
  - **status**: "fixed" or "skipped"
  - **reason**: Brief explanation (e.g., "Extracted common validation to BaseService")

---

## Task

Reduce code duplication from {actual}% to <= {required}% in new code at `{local_repo_path}`.
"""


# ---------------------------------------------------------------------------
# Skills loader -- reads .github/skills/*.md from the target repository
# ---------------------------------------------------------------------------

def load_skills(local_repo_path):
    """Load all skill files from the target repository's ``.github/skills/`` directory.

    Each ``.md`` file in that directory is treated as a skill document that
    provides project-specific conventions (e.g. Dropwizard testing rules,
    coding standards, etc.).

    Parameters
    ----------
    local_repo_path : str
        Path to the cloned repository.

    Returns
    -------
    str
        Concatenated skill content ready to append to a system message.
        Returns an empty string if no skill files are found.
    """
    if not local_repo_path:
        return ""

    skills_dir = os.path.join(local_repo_path, ".github", "skills")
    if not os.path.isdir(skills_dir):
        return ""

    skill_files = sorted(glob.glob(os.path.join(skills_dir, "*.md")))
    if not skill_files:
        return ""

    parts = []
    for skill_file in skill_files:
        try:
            with open(skill_file, "r") as f:
                content = f.read().strip()
            if content:
                skill_name = os.path.splitext(os.path.basename(skill_file))[0]
                parts.append(f"\n\n## Skill: {skill_name}\n\n{content}")
                logger.info(f"Loaded skill: {skill_name} ({len(content)} chars)")
        except Exception as e:
            logger.warning(f"Failed to read skill file {skill_file}: {e}")

    if not parts:
        return ""

    return "\n\n---\n# Project-Specific Skills & Conventions" + "".join(parts)


# ---------------------------------------------------------------------------
# Helper
# ---------------------------------------------------------------------------

def _format_issues(issues):
    """Format SonarQube issues into a readable block for the prompt."""
    sections = []
    for i, issue in enumerate(issues, 1):
        component = issue.get("component", "")
        # Extract file path from component (format: project:path/to/file.java)
        file_path = component.split(":")[-1] if ":" in component else component

        line = issue.get("line", "N/A")
        message = issue.get("message", "")
        rule = issue.get("rule", "")
        severity = issue.get("severity", "")
        issue_type = issue.get("type", "")

        # MQR mode: derive type/severity from impacts when legacy fields are absent
        if not issue_type and issue.get("impacts"):
            quality_to_type = {
                "RELIABILITY": "BUG",
                "SECURITY": "VULNERABILITY",
                "MAINTAINABILITY": "CODE_SMELL",
            }
            impact_parts = []
            for impact in issue.get("impacts", []):
                q = impact.get("softwareQuality", "")
                s = impact.get("severity", "")
                if not issue_type:
                    issue_type = quality_to_type.get(q, q)
                if not severity:
                    severity = s
                impact_parts.append(f"{q}:{s}")
            impact_str = ", ".join(impact_parts)
        else:
            impact_str = ""
        key = issue.get("key", f"issue-{i}")
        effort = issue.get("effort", "")
        tags = ", ".join(issue.get("tags", []))

        section = (
            f"### Issue {i}: {key}\n"
            f"- **File:** `{file_path}`\n"
            f"- **Line:** {line}\n"
            f"- **Rule:** {rule}\n"
            f"- **Severity:** {severity}\n"
            f"- **Type:** {issue_type}\n"
            f"- **Message:** {message}\n"
        )
        if impact_str:
            section += f"- **Impacts:** {impact_str}\n"
        if effort:
            section += f"- **Effort:** {effort}\n"
        if tags:
            section += f"- **Tags:** {tags}\n"

        sections.append(section)

    return "\n".join(sections)
