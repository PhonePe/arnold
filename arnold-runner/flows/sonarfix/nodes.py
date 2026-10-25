"""Nodes for the sonarfix flow.

Reuses CloneRepository, CheckoutBranch, Cleanup from flows.code_reviews.common.nodes.
Defines sonar-specific nodes: DetectSonarConfig, FetchQualityGateStatus,
FetchSonarQubeIssues, FixIssues, FixCoverage, FixDuplication,
CreateFixBranch, CreateMergeRequest.
"""

import os
import json
import logging
import subprocess
import re
import xml.etree.ElementTree as ET
from pocketflow import Node
from utils.call_llm import call_llm_agentic
from utils.logger import get_logger
from flows.sonarfix.prompts import (
    SYSTEM_MESSAGE,
    COVERAGE_SYSTEM_MESSAGE,
    DUPLICATION_SYSTEM_MESSAGE,
    build_sonarfix_prompt,
    build_coverage_fix_prompt,
    build_duplication_fix_prompt,
    load_skills,
)

logger = get_logger("sonarfix_nodes_logger")


# ---------------------------------------------------------------------------
# Helpers shared across Fix* nodes
# ---------------------------------------------------------------------------

def _get_changed_files(local_repo_path):
    """Get files changed in the current branch vs default branch."""
    try:
        for base in ("origin/master", "origin/main", "origin/develop"):
            result = subprocess.run(
                ["git", "-C", local_repo_path, "merge-base", "HEAD", base],
                capture_output=True, text=True, timeout=30,
            )
            if result.returncode == 0:
                merge_base = result.stdout.strip()
                diff_result = subprocess.run(
                    ["git", "-C", local_repo_path, "diff", "--name-only", merge_base, "HEAD"],
                    capture_output=True, text=True, timeout=30,
                )
                if diff_result.returncode == 0:
                    return [f for f in diff_result.stdout.strip().split("\n") if f]
    except Exception as e:
        logger.warning(f"Failed to get changed files: {e}")
    return None


def _build_write_fix_results_tool(captured_results):
    """Build the write_fix_results tool for agent sessions."""
    from pydantic import BaseModel, Field
    from utils.tool_adapter import define_tool

    class WriteFixResultsParams(BaseModel):
        fixes: list = Field(
            description=(
                "List of fix result objects. Each must have: "
                "issue_key, file_path, status (fixed/skipped), reason"
            )
        )
        summary: str = Field(
            default="Issues processed",
            description="Brief summary of what was fixed",
        )

    @define_tool(
        description=(
            "Write the results of fixing issues. Call this ONCE "
            "after you have attempted to fix all issues in this batch. "
            "Each fix must include: issue_key, file_path, status "
            "(fixed or skipped), and reason."
        )
    )
    def write_fix_results(params: WriteFixResultsParams) -> str:
        logger.info(
            f"[tool] write_fix_results -- {len(params.fixes)} results"
        )
        captured_results["fixes"] = params.fixes
        captured_results["summary"] = params.summary
        fixed_count = sum(
            1
            for f in params.fixes
            if (f.get("status") if isinstance(f, dict) else getattr(f, "status", "")) == "fixed"
        )
        return f"OK -- {fixed_count} fixed, {len(params.fixes) - fixed_count} skipped"

    return write_fix_results


def _process_fix_results(captured_results):
    """Extract fix results from the captured tool output."""
    from pydantic import BaseModel

    all_fix_results = []
    total_fixed = 0
    total_skipped = 0

    fixes = captured_results.get("fixes", [])
    for fix in fixes:
        if isinstance(fix, BaseModel):
            fix = fix.model_dump() if hasattr(fix, "model_dump") else dict(fix)
        if not isinstance(fix, dict):
            continue
        status = fix.get("status", "skipped")
        all_fix_results.append(fix)
        if status == "fixed":
            total_fixed += 1
        else:
            total_skipped += 1

    return all_fix_results, total_fixed, total_skipped


# ---------------------------------------------------------------------------
# Node: DetectSonarConfig
# ---------------------------------------------------------------------------

class DetectSonarConfig(Node):
    """Auto-detect SonarQube component key from the cloned repository's build files.

    Detection order:
    1. sonar-project.properties at repo root (sonar.projectKey)
    1b. sonar-project.properties in subdirectories (recursive search)
    2. pom.xml (groupId:artifactId)
    3. build.gradle / build.gradle.kts:
       a. Explicit sonar.projectKey property
       b. rootProject.name from settings.gradle(.kts)

    Also auto-sets the pull request number from the MR IID if not provided.
    """

    def prep(self, shared):
        return {
            "local_repo_path": shared.get("local_repo_path"),
            "sonar_component_key": shared.get("sonar_component_key"),
            "sonar_pr_number": shared.get("sonar_pr_number"),
            "mr_iid": shared.get("merge_request_iid"),
        }

    def exec(self, prep_res):
        local_repo_path = prep_res["local_repo_path"]
        component_key = prep_res["sonar_component_key"]
        pr_number = prep_res["sonar_pr_number"]
        mr_iid = prep_res["mr_iid"]

        # Auto-detect PR number from MR IID if not explicitly set
        if not pr_number and mr_iid:
            pr_number = str(mr_iid)
            logger.info(f"Auto-detected SonarQube PR number from MR IID: {pr_number}")

        # Skip detection if component key is already provided
        if component_key:
            logger.info(f"Using provided SonarQube component key: {component_key}")
            return {"sonar_component_key": component_key, "sonar_pr_number": pr_number}

        if not local_repo_path:
            raise ValueError("local_repo_path is required to auto-detect SonarQube config")

        # 1. Try sonar-project.properties (root first, then recursive search)
        sonar_props = os.path.join(local_repo_path, "sonar-project.properties")
        if os.path.exists(sonar_props):
            component_key = self._parse_sonar_properties(sonar_props)
            if component_key:
                logger.info(f"Detected component key from sonar-project.properties: {component_key}")
                return {"sonar_component_key": component_key, "sonar_pr_number": pr_number}

        # 1b. Search for sonar-project.properties in subdirectories
        found_props = self._find_sonar_properties(local_repo_path)
        if found_props:
            component_key = self._parse_sonar_properties(found_props)
            if component_key:
                rel_path = os.path.relpath(found_props, local_repo_path)
                logger.info(f"Detected component key from {rel_path}: {component_key}")
                return {"sonar_component_key": component_key, "sonar_pr_number": pr_number}

        # 2. Try pom.xml (Maven)
        pom_file = os.path.join(local_repo_path, "pom.xml")
        if os.path.exists(pom_file):
            component_key = self._parse_pom_xml(pom_file)
            if component_key:
                logger.info(f"Detected component key from pom.xml: {component_key}")
                return {"sonar_component_key": component_key, "sonar_pr_number": pr_number}

        # 3. Try build.gradle / build.gradle.kts (Gradle)
        for gradle_file in ["build.gradle", "build.gradle.kts"]:
            gradle_path = os.path.join(local_repo_path, gradle_file)
            if os.path.exists(gradle_path):
                component_key = self._parse_gradle(gradle_path, local_repo_path)
                if component_key:
                    logger.info(f"Detected component key from {gradle_file}: {component_key}")
                    return {"sonar_component_key": component_key, "sonar_pr_number": pr_number}

        raise ValueError(
            "Could not auto-detect SonarQube component key. "
            "No sonar-project.properties, pom.xml, or build.gradle found. "
            "Please provide --sonar-component-key explicitly."
        )

    def _parse_sonar_properties(self, path):
        """Extract sonar.projectKey from sonar-project.properties."""
        with open(path, "r") as f:
            for line in f:
                line = line.strip()
                if line.startswith("sonar.projectKey"):
                    _, _, value = line.partition("=")
                    return value.strip()
        return None

    def _find_sonar_properties(self, repo_path):
        """Recursively search for sonar-project.properties in subdirectories.

        Skips hidden directories, build output, and node_modules.
        Returns the first match found, or None.
        """
        skip_dirs = {".git", "build", "node_modules", ".gradle", "__pycache__", ".idea"}
        for dirpath, dirnames, filenames in os.walk(repo_path):
            # Prune directories we don't want to descend into
            dirnames[:] = [d for d in dirnames if d not in skip_dirs and not d.startswith(".")]
            if "sonar-project.properties" in filenames:
                candidate = os.path.join(dirpath, "sonar-project.properties")
                # Skip root — already checked above
                if os.path.normpath(dirpath) == os.path.normpath(repo_path):
                    continue
                return candidate
        return None

    def _parse_pom_xml(self, path):
        """Extract groupId:artifactId from pom.xml."""
        try:
            tree = ET.parse(path)
            root = tree.getroot()
            # Handle Maven namespace
            ns = ""
            if root.tag.startswith("{"):
                ns = root.tag.split("}")[0] + "}"

            group_id = None
            artifact_id = None

            # Direct children
            group_el = root.find(f"{ns}groupId")
            artifact_el = root.find(f"{ns}artifactId")

            if group_el is not None:
                group_id = group_el.text.strip()
            else:
                # Fall back to parent groupId
                parent = root.find(f"{ns}parent")
                if parent is not None:
                    parent_group = parent.find(f"{ns}groupId")
                    if parent_group is not None:
                        group_id = parent_group.text.strip()

            if artifact_el is not None:
                artifact_id = artifact_el.text.strip()

            if group_id and artifact_id:
                return f"{group_id}:{artifact_id}"
        except ET.ParseError as e:
            logger.warning(f"Failed to parse pom.xml: {e}")
        return None

    def _parse_gradle(self, gradle_path, repo_path):
        """Extract SonarQube project key from Gradle build files.

        Detection order:
        1. Explicit sonar.projectKey in the Gradle build file
        2. rootProject.name from settings.gradle(.kts)
        3. Directory name as fallback
        """
        with open(gradle_path, "r") as f:
            content = f.read()

        # 1. Check for explicit sonar.projectKey in the Gradle build file
        for pattern in [
            r"""property\s*\(\s*["']sonar\.projectKey["']\s*,\s*["']([^"']+)["']\s*\)""",
            r"""sonar\.projectKey["']\s*,\s*["']([^"']+)["']""",
        ]:
            match = re.search(pattern, content)
            if match:
                return match.group(1)

        # 2. Extract project name from settings.gradle(.kts)
        project_name = None
        for settings_file in ["settings.gradle", "settings.gradle.kts"]:
            settings_path = os.path.join(repo_path, settings_file)
            if os.path.exists(settings_path):
                with open(settings_path, "r") as f:
                    settings_content = f.read()
                match = re.search(
                    r"""rootProject\.name\s*[=:]\s*['"]([^'"]+)['"]""",
                    settings_content,
                )
                if match:
                    project_name = match.group(1)
                    break

        if not project_name:
            project_name = os.path.basename(repo_path)

        return project_name

    def post(self, shared, _prep_res, exec_res):
        shared["sonar_component_key"] = exec_res["sonar_component_key"]
        shared["sonar_pr_number"] = exec_res["sonar_pr_number"]


# ---------------------------------------------------------------------------
# Node: FetchQualityGateStatus
# ---------------------------------------------------------------------------

class FetchQualityGateStatus(Node):
    """Fetch quality gate status, coverage, duplications, and other metrics from SonarQube."""

    # Metrics to fetch from api/measures/component
    _METRIC_KEYS = [
        # Coverage
        "new_coverage", "coverage",
        "new_lines_to_cover", "lines_to_cover",
        "new_uncovered_lines", "uncovered_lines",
        "new_line_coverage", "line_coverage",
        "new_branch_coverage", "branch_coverage",
        # Duplications
        "new_duplicated_lines_density", "duplicated_lines_density",
        "new_duplicated_lines", "duplicated_lines",
        "new_duplicated_blocks", "duplicated_blocks",
        "new_lines",
        # Issues by type
        "new_bugs", "bugs",
        "new_vulnerabilities", "vulnerabilities",
        "new_code_smells", "code_smells",
        "new_security_hotspots", "security_hotspots",
        # Ratings
        "new_reliability_rating", "reliability_rating",
        "new_security_rating", "security_rating",
        "new_sqale_debt_ratio", "sqale_debt_ratio",
        # Complexity
        "complexity", "cognitive_complexity",
    ]

    def prep(self, shared):
        return {
            "sonar_url": shared.get("sonar_url"),
            "sonar_component_key": shared.get("sonar_component_key"),
            "sonar_pr_number": shared.get("sonar_pr_number"),
            "sonar_cookies": shared.get("sonar_cookies"),
            "sonar_token": shared.get("sonar_token"),
        }

    def _make_request(self, url, headers):
        """Make a GET request to SonarQube API."""
        import requests
        resp = requests.get(url, headers=headers, allow_redirects=True, verify=False)
        resp.raise_for_status()
        return resp.json()

    def exec(self, prep_res):
        sonar_url = prep_res["sonar_url"]
        component_key = prep_res["sonar_component_key"]
        pr_number = prep_res["sonar_pr_number"]
        cookies = prep_res["sonar_cookies"]
        token = prep_res.get("sonar_token")

        if not sonar_url or not component_key:
            logger.warning("SonarQube URL or component key not set -- skipping quality gate fetch")
            return {"quality_gate": None, "measures": {}}

        from urllib.parse import quote

        headers = {"Accept": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        elif cookies:
            headers["Cookie"] = cookies

        encoded_key = quote(component_key, safe="")

        # --- 1. Quality Gate Status ---
        qg_url = (
            f"{sonar_url.rstrip('/')}/api/qualitygates/project_status"
            f"?projectKey={encoded_key}"
        )
        if pr_number:
            qg_url += f"&pullRequest={pr_number}"

        quality_gate = None
        try:
            logger.info(f"Fetching quality gate status: {qg_url}")
            qg_data = self._make_request(qg_url, headers)
            quality_gate = qg_data.get("projectStatus", {})
            qg_status = quality_gate.get("status", "UNKNOWN")
            logger.info(f"Quality Gate: {qg_status}")

            # Log each condition
            for cond in quality_gate.get("conditions", []):
                metric = cond.get("metricKey", "")
                status = cond.get("status", "")
                actual = cond.get("actualValue", "")
                threshold = cond.get("errorThreshold", "")
                comparator = cond.get("comparator", "")
                # SonarQube comparator = "error when actual {op} threshold"
                # Flip to show the *requirement*: LT error means require >=
                req_symbol = {"GT": "<=", "LT": ">=", "GE": "<", "LE": ">"}.get(comparator, comparator)
                pass_fail = "PASS" if status == "OK" else "FAIL"
                logger.info(
                    f"  {pass_fail} {metric}: {actual} (required {req_symbol} {threshold})"
                )
        except Exception as e:
            logger.warning(f"Failed to fetch quality gate status: {e}")

        # --- 2. Measures ---
        metric_keys = ",".join(self._METRIC_KEYS)
        measures_url = (
            f"{sonar_url.rstrip('/')}/api/measures/component"
            f"?component={encoded_key}"
            f"&metricKeys={metric_keys}"
        )
        if pr_number:
            measures_url += f"&pullRequest={pr_number}"

        measures = {}
        try:
            logger.info("Fetching project measures...")
            measures_data = self._make_request(measures_url, headers)
            component = measures_data.get("component", {})
            for m in component.get("measures", []):
                metric = m.get("metric", "")
                value = m.get("value", m.get("period", {}).get("value"))
                if value is not None:
                    measures[metric] = value

            logger.info(f"Fetched {len(measures)} measures")
        except Exception as e:
            logger.warning(f"Failed to fetch measures: {e}")

        # --- 3. Print summary dashboard ---
        self._print_dashboard(quality_gate, measures)

        return {"quality_gate": quality_gate, "measures": measures}

    def _print_dashboard(self, quality_gate, measures):
        """Print a human-readable quality gate dashboard."""
        logger.info("")
        logger.info("=" * 60)
        if quality_gate:
            status = quality_gate.get("status", "UNKNOWN")
            icon = "PASSED" if status == "OK" else "FAILED"
            logger.info(f"  Quality Gate: {icon}")
        else:
            logger.info("  Quality Gate: UNKNOWN")
        logger.info("-" * 60)

        # Coverage
        new_cov = measures.get("new_coverage", "N/A")
        overall_cov = measures.get("coverage", "N/A")
        new_lines_to_cover = measures.get("new_lines_to_cover", "N/A")
        logger.info(f"  Coverage (new code):      {new_cov}%")
        logger.info(f"    Lines to cover:         {new_lines_to_cover}")
        logger.info(f"    Estimated after merge:  {overall_cov}%")

        # Duplications
        new_dup = measures.get("new_duplicated_lines_density", "N/A")
        overall_dup = measures.get("duplicated_lines_density", "N/A")
        new_lines = measures.get("new_lines", "N/A")
        logger.info(f"  Duplications (new code):  {new_dup}%")
        logger.info(f"    New lines:              {new_lines}")
        logger.info(f"    Estimated after merge:  {overall_dup}%")

        # Issues
        new_bugs = measures.get("new_bugs", "0")
        new_vulns = measures.get("new_vulnerabilities", "0")
        new_smells = measures.get("new_code_smells", "0")
        new_hotspots = measures.get("new_security_hotspots", "0")
        logger.info(f"  New Bugs:                 {new_bugs}")
        logger.info(f"  New Vulnerabilities:      {new_vulns}")
        logger.info(f"  New Code Smells:          {new_smells}")
        logger.info(f"  New Security Hotspots:    {new_hotspots}")

        # Quality gate conditions detail
        if quality_gate:
            failed = [
                c for c in quality_gate.get("conditions", [])
                if c.get("status") != "OK"
            ]
            if failed:
                logger.info("-" * 60)
                logger.info("  FAILING CONDITIONS:")
                for c in failed:
                    metric = c.get("metricKey", "")
                    actual = c.get("actualValue", "")
                    threshold = c.get("errorThreshold", "")
                    req_symbol = {"GT": "<=", "LT": ">=", "GE": "<", "LE": ">"}.get(
                        c.get("comparator", ""), c.get("comparator", "")
                    )
                    logger.info(f"    FAIL {metric}: {actual} (required {req_symbol} {threshold})")

        logger.info("=" * 60)
        logger.info("")

    def post(self, shared, _prep_res, exec_res):
        shared["quality_gate"] = exec_res["quality_gate"]
        shared["measures"] = exec_res["measures"]

        quality_gate = exec_res["quality_gate"]
        if quality_gate is None:
            # Could not fetch quality gate -- proceed cautiously and attempt fixes
            logger.warning("Quality gate status unknown -- proceeding with issue fetch")
            return "gate_failed"

        gate_status = quality_gate.get("status", "UNKNOWN")
        if gate_status == "OK":
            logger.info("Quality gate PASSED -- no fixes required, skipping issue fetch")
            return "gate_passed"

        logger.info(f"Quality gate {gate_status} -- proceeding to fetch issues and apply fixes")
        return "gate_failed"


# ---------------------------------------------------------------------------
# Node: FetchSonarQubeIssues
# ---------------------------------------------------------------------------

class FetchSonarQubeIssues(Node):
    """Fetch targeted SonarQube data based on failing quality gate conditions.

    Analyses the quality gate result to determine which metrics are failing,
    then fetches only the relevant detailed data:
    - Issue-based failures (bugs, vulns, etc.) -> issues filtered by type
    - Coverage failures -> file-level coverage via component_tree API
    - Duplication failures -> file-level duplication via component_tree API
    """

    # Maps quality gate metric keys to SonarQube issue types.
    # Includes both legacy (SonarQube <10.x) and new software-quality metrics
    # introduced in SonarQube 10.x to avoid silently skipping issues.
    _METRIC_TO_ISSUE_TYPE = {
        # Legacy metric keys
        "new_bugs": "BUG",
        "new_vulnerabilities": "VULNERABILITY",
        "new_code_smells": "CODE_SMELL",
        "new_security_hotspots": "SECURITY_HOTSPOT",
        "new_reliability_rating": "BUG",
        "new_security_rating": "VULNERABILITY",
        # SonarQube 10.x software-quality severity metrics (map to all types)
        "new_software_quality_high_issues": "__ALL__",
        "new_software_quality_medium_issues": "__ALL__",
        "new_software_quality_low_issues": "__ALL__",
        "new_software_quality_info_issues": "__ALL__",
        # SonarQube 10.x software-quality rating metrics
        "new_software_quality_maintainability_rating": "CODE_SMELL",
        "new_software_quality_reliability_rating": "BUG",
        "new_software_quality_security_rating": "VULNERABILITY",
        # Catch-all violation count (includes all issue types)
        "new_violations": "__ALL__",
        "new_accepted_issues": "__ALL__",
    }
    _ALL_ISSUE_TYPES = {"BUG", "VULNERABILITY", "CODE_SMELL", "SECURITY_HOTSPOT"}
    _COVERAGE_METRICS = {"new_coverage", "new_line_coverage", "new_branch_coverage"}
    _DUPLICATION_METRICS = {"new_duplicated_lines_density"}

    def prep(self, shared):
        quality_gate = shared.get("quality_gate")
        failing_conditions = []
        if quality_gate:
            failing_conditions = [
                c for c in quality_gate.get("conditions", [])
                if c.get("status") != "OK"
            ]

        return {
            "sonar_url": shared.get("sonar_url"),
            "sonar_component_key": shared.get("sonar_component_key"),
            "sonar_pr_number": shared.get("sonar_pr_number"),
            "sonar_cookies": shared.get("sonar_cookies"),
            "sonar_token": shared.get("sonar_token"),
            "sonar_issues_file": shared.get("sonar_issues_file"),
            "failing_conditions": failing_conditions,
        }

    # ------------------------------------------------------------------
    # helpers
    # ------------------------------------------------------------------

    def _build_headers(self, cookies, token=None):
        headers = {"Accept": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        elif cookies:
            headers["Cookie"] = cookies
        return headers

    # Maps MQR softwareQuality values to legacy issue types for client-side filtering
    _QUALITY_TO_LEGACY_TYPE = {
        "RELIABILITY": "BUG",
        "SECURITY": "VULNERABILITY",
        "MAINTAINABILITY": "CODE_SMELL",
    }

    def _issue_matches_type_filter(self, issue, type_filter):
        """Check if an issue matches the type filter, supporting both legacy and MQR formats."""
        # Legacy format: issue has a "type" field
        legacy_type = issue.get("type", "")
        if legacy_type and legacy_type in type_filter:
            return True
        # MQR format: issue has "impacts" array with softwareQuality entries
        for impact in issue.get("impacts", []):
            quality = impact.get("softwareQuality", "")
            mapped = self._QUALITY_TO_LEGACY_TYPE.get(quality)
            if mapped and mapped in type_filter:
                return True
        # If issue has neither type nor impacts, include it (don't silently drop)
        if not legacy_type and not issue.get("impacts"):
            return True
        return False

    def _fetch_issues(self, sonar_url, encoded_key, pr_number, headers, type_filter=None):
        """Fetch issues from api/issues/search, optionally filtered by type.

        Builds a chain of parameter combinations to handle different SonarQube
        versions.  SonarQube 2025.x MQR mode removed ``types``,
        ``sinceLeakPeriod`` and the ``CONFIRMED`` status entirely.
        Tries each combination in order until one succeeds.
        """
        import requests

        base_url = f"{sonar_url.rstrip('/')}/api/issues/search?componentKeys={encoded_key}"
        pr_param = f"&pullRequest={pr_number}" if pr_number else ""
        types_param = f"&types={','.join(sorted(type_filter))}" if type_filter else ""

        # Ordered list of (label, url) combinations to try.
        # SonarQube 2025.x MQR: CONFIRMED is gone, sinceLeakPeriod is gone
        attempts = [
            ("MQR-mode (2025.x)",
             f"{base_url}&statuses=OPEN,ACCEPTED&inNewCodePeriod=true{pr_param}"),
            ("no-types + sinceLeakPeriod",
             f"{base_url}&statuses=OPEN,CONFIRMED&sinceLeakPeriod=true{pr_param}"),
            ("no-types + inNewCodePeriod",
             f"{base_url}&statuses=OPEN,CONFIRMED,ACCEPTED&inNewCodePeriod=true{pr_param}"),
            ("with-types + sinceLeakPeriod (legacy)",
             f"{base_url}&statuses=OPEN,CONFIRMED&sinceLeakPeriod=true{types_param}{pr_param}"),
        ]

        for label, api_url in attempts:
            logger.info(f"Fetching SonarQube issues ({label}): {api_url}")
            all_issues = []
            page = 1
            page_size = 100
            try:
                while True:
                    paginated_url = f"{api_url}&p={page}&ps={page_size}"
                    resp = requests.get(paginated_url, headers=headers,
                                        allow_redirects=True, verify=False)
                    resp.raise_for_status()
                    data = resp.json()
                    issues = data.get("issues", [])
                    all_issues.extend(issues)
                    total = data.get("total", len(issues))
                    logger.info(f"  Page {page}: fetched {len(issues)} issues (total: {total})")
                    if len(all_issues) >= total or not issues:
                        break
                    page += 1

                # Client-side type filtering (handles both legacy type and MQR impacts)
                if type_filter and all_issues and types_param not in api_url:
                    before = len(all_issues)
                    all_issues = [i for i in all_issues
                                  if self._issue_matches_type_filter(i, type_filter)]
                    if before != len(all_issues):
                        logger.info(f"Filtered by type: {before} -> {len(all_issues)} issues")

                logger.info(f"Total issues fetched: {len(all_issues)}")
                return all_issues

            except requests.exceptions.HTTPError as e:
                if e.response is not None and e.response.status_code == 400:
                    logger.warning(f"Attempt '{label}' failed (400) -- trying next")
                    continue
                raise

        # All attempts exhausted
        raise RuntimeError(
            "All SonarQube issues/search API parameter combinations failed with 400. "
            "Please verify your SonarQube version and API compatibility."
        )

    def _fetch_component_tree(self, sonar_url, encoded_key, pr_number,
                              headers, metric_keys, label):
        """Fetch file-level metrics via api/measures/component_tree.

        Returns a list of dicts with ``path`` and metric values for every
        file that has non-zero values for the requested metrics.
        """
        import requests

        keys_csv = ",".join(metric_keys)
        api_url = (
            f"{sonar_url.rstrip('/')}/api/measures/component_tree"
            f"?component={encoded_key}"
            f"&metricKeys={keys_csv}"
            f"&qualifiers=FIL"
            f"&ps=100"
            f"&s=metric"
            f"&metricSort={metric_keys[0]}"
            f"&metricSortFilter=withMeasuresOnly"
            f"&asc=false"
        )
        if pr_number:
            api_url += f"&pullRequest={pr_number}"

        logger.info(f"Fetching {label} component tree: {api_url}")

        components = []
        page = 1
        try:
            while True:
                paginated_url = f"{api_url}&p={page}"
                resp = requests.get(paginated_url, headers=headers,
                                    allow_redirects=True, verify=False)
                resp.raise_for_status()
                data = resp.json()
                raw = data.get("components", [])
                if not raw:
                    break
                for comp in raw:
                    entry = {"path": comp.get("path", comp.get("key", ""))}
                    for m in comp.get("measures", []):
                        val = m.get("value", m.get("period", {}).get("value"))
                        if val is not None:
                            entry[m["metric"]] = val
                    # Only include files that actually have metric values
                    if len(entry) > 1:
                        components.append(entry)
                total = data.get("paging", {}).get("total", len(raw))
                if len(components) >= total or not raw:
                    break
                page += 1
        except Exception as e:
            logger.warning(f"Failed to fetch {label} component tree: {e}")

        logger.info(f"Fetched {label} data for {len(components)} files")
        return components

    # ------------------------------------------------------------------
    # main execution
    # ------------------------------------------------------------------

    def exec(self, prep_res):
        sonar_issues_file = prep_res["sonar_issues_file"]

        # If a pre-saved JSON file is provided, load from that
        if sonar_issues_file and os.path.exists(sonar_issues_file):
            logger.info(f"Loading SonarQube issues from file: {sonar_issues_file}")
            with open(sonar_issues_file, "r") as f:
                data = json.load(f)
            issues = data.get("issues", data if isinstance(data, list) else [])
            logger.info(f"Loaded {len(issues)} issues from file")
            return {
                "issues": issues,
                "total": len(issues),
                "coverage_components": [],
                "duplication_components": [],
                "failing_conditions": prep_res["failing_conditions"],
            }

        sonar_url = prep_res["sonar_url"]
        sonar_component_key = prep_res["sonar_component_key"]
        sonar_pr_number = prep_res["sonar_pr_number"]
        sonar_cookies = prep_res["sonar_cookies"]
        sonar_token = prep_res.get("sonar_token")
        failing_conditions = prep_res["failing_conditions"]

        if not sonar_url or not sonar_component_key:
            raise ValueError(
                "SonarQube URL and component key are required. "
                "Provide --sonar-url and --sonar-component-key, or use --sonar-issues-file."
            )

        from urllib.parse import quote
        encoded_key = quote(sonar_component_key, safe="")
        headers = self._build_headers(sonar_cookies, token=sonar_token)

        # --- Categorize failing conditions ---
        issue_types_needed = set()
        need_coverage = False
        need_duplication = False

        for cond in failing_conditions:
            metric = cond.get("metricKey", "")
            if metric in self._METRIC_TO_ISSUE_TYPE:
                mapped = self._METRIC_TO_ISSUE_TYPE[metric]
                if mapped == "__ALL__":
                    issue_types_needed.update(self._ALL_ISSUE_TYPES)
                else:
                    issue_types_needed.add(mapped)
            if metric in self._COVERAGE_METRICS:
                need_coverage = True
            if metric in self._DUPLICATION_METRICS:
                need_duplication = True

        # Fallback: if no conditions were categorized, fetch all issues
        if not issue_types_needed and not need_coverage and not need_duplication:
            logger.info("No failing conditions categorized -- fetching all issues as fallback")
            issue_types_needed = set(self._ALL_ISSUE_TYPES)

        logger.info(
            f"Failing condition analysis: "
            f"issue_types={sorted(issue_types_needed) or 'none'}, "
            f"coverage={'yes' if need_coverage else 'no'}, "
            f"duplication={'yes' if need_duplication else 'no'}"
        )

        # --- 1. Fetch issues (filtered by failing types) ---
        all_issues = []
        if issue_types_needed:
            all_issues = self._fetch_issues(
                sonar_url, encoded_key, sonar_pr_number,
                headers, type_filter=issue_types_needed,
            )

        # --- 2. Fetch file-level coverage data ---
        coverage_components = []
        if need_coverage:
            coverage_components = self._fetch_component_tree(
                sonar_url, encoded_key, sonar_pr_number, headers,
                metric_keys=[
                    "new_uncovered_lines", "new_lines_to_cover",
                    "new_line_coverage", "new_branch_coverage",
                    "new_uncovered_conditions",
                ],
                label="coverage",
            )

        # --- 3. Fetch file-level duplication data ---
        duplication_components = []
        if need_duplication:
            duplication_components = self._fetch_component_tree(
                sonar_url, encoded_key, sonar_pr_number, headers,
                metric_keys=[
                    "new_duplicated_lines", "new_duplicated_blocks",
                    "new_duplicated_lines_density",
                ],
                label="duplication",
            )

        return {
            "issues": all_issues,
            "total": len(all_issues),
            "coverage_components": coverage_components,
            "duplication_components": duplication_components,
            "failing_conditions": failing_conditions,
        }

    def post(self, shared, _prep_res, exec_res):
        shared["sonar_issues"] = exec_res["issues"]
        shared["sonar_issues_total"] = exec_res["total"]
        shared["coverage_components"] = exec_res.get("coverage_components", [])
        shared["duplication_components"] = exec_res.get("duplication_components", [])
        shared["failing_conditions"] = exec_res.get("failing_conditions", [])


# ---------------------------------------------------------------------------
# Node: FixIssues — specialized agent for code issues
# ---------------------------------------------------------------------------

class FixIssues(Node):
    """Fix code issues (bugs, vulnerabilities, code smells, security hotspots).

    Only runs if there are issue-based failing conditions. Uses a specialized
    issues-fixing agent via call_llm_agentic().
    """

    # Metric keys that indicate issue-based failures
    _ISSUE_METRICS = {
        # Legacy
        "new_bugs", "new_vulnerabilities", "new_code_smells",
        "new_security_hotspots", "new_reliability_rating", "new_security_rating",
        # SonarQube 10.x software-quality metrics
        "new_software_quality_high_issues", "new_software_quality_medium_issues",
        "new_software_quality_low_issues", "new_software_quality_info_issues",
        "new_software_quality_maintainability_rating",
        "new_software_quality_reliability_rating",
        "new_software_quality_security_rating",
        # Catch-all
        "new_violations", "new_accepted_issues",
    }

    def prep(self, shared):
        issues = shared.get("sonar_issues", [])
        failing_conditions = shared.get("failing_conditions", [])
        tracking_file = shared.get("tracking_file")

        # Check if any failing condition is issue-related
        has_issue_conditions = any(
            c.get("metricKey", "") in self._ISSUE_METRICS
            for c in failing_conditions
        )

        # Also check if we loaded issues from file (fallback)
        if not has_issue_conditions and not issues:
            return {"skip": True, "reason": "No issue-based conditions failing"}

        # Resumability: filter out already-resolved issues via tracking file
        if tracking_file and os.path.exists(tracking_file):
            logger.info(f"Loading tracking file for resumability: {tracking_file}")
            with open(tracking_file, "r") as f:
                existing = json.load(f)
            resolved_keys = {
                item["key"] for item in existing
                if item.get("status") in ("fixed", "resolved", "done")
            }
            before = len(issues)
            issues = [i for i in issues if i.get("key", "") not in resolved_keys]
            logger.info(f"Filtered {before - len(issues)} already-resolved issues "
                        f"({len(issues)} remaining)")

        if not issues:
            return {"skip": True, "reason": "No issues to fix (all resolved or empty)"}

        return {
            "skip": False,
            "sonar_issues": issues,
            "local_repo_path": shared.get("local_repo_path"),
            "changed_files": shared.get("changed_files"),
            "tracking_file": tracking_file,
        }

    def exec(self, prep_res):
        if prep_res.get("skip"):
            logger.info(f"FixIssues: skipping -- {prep_res.get('reason')}")
            return {"fix_results": [], "total_fixed": 0, "total_skipped": 0}

        issues = prep_res["sonar_issues"]
        local_repo_path = prep_res["local_repo_path"]
        changed_files = prep_res.get("changed_files")
        tracking_file = prep_res.get("tracking_file")

        # Auto-detect changed files if not provided
        if changed_files is None and local_repo_path:
            changed_files = _get_changed_files(local_repo_path)
            if changed_files:
                logger.info(f"Detected {len(changed_files)} changed files in MR")

        # Build prompt
        changed_file_paths = None
        if changed_files:
            changed_file_paths = [
                f["path"] if isinstance(f, dict) else f for f in changed_files
            ]

        prompt = build_sonarfix_prompt(
            issues=issues,
            local_repo_path=local_repo_path,
            changed_files=changed_file_paths,
        )

        # Build results tool
        captured_results = {}
        tool = _build_write_fix_results_tool(captured_results)

        # Save initial tracking file
        if tracking_file:
            tracked = [{"key": i.get("key", ""), "status": "open"} for i in issues]
            with open(tracking_file, "w") as f:
                json.dump(tracked, f, indent=2)
            logger.info(f"Tracking file initialized: {tracking_file} ({len(tracked)} issues)")

        # Run agent
        logger.info(f"Running issues-fix agent for {len(issues)} issues...")
        system_message = SYSTEM_MESSAGE + load_skills(local_repo_path)
        try:
            call_llm_agentic(
                prompt,
                tools=[tool],
                system_message=system_message,
                model="gpt-5.6-sol",
                working_directory=local_repo_path,
                disable_fallback=True,
            )
        except Exception as e:
            logger.error(f"Issues-fix agent failed: {e}")

        # Process results
        all_fix_results, total_fixed, total_skipped = _process_fix_results(captured_results)
        logger.info(f"Issues-fix agent: {total_fixed} fixed, {total_skipped} skipped")

        # Update tracking file
        if tracking_file and issues:
            fix_status_map = {
                r.get("issue_key", ""): r.get("status", "skipped")
                for r in all_fix_results
                if isinstance(r, dict)
            }
            tracked_issues = [{"key": i.get("key", ""), "status": "open"} for i in issues]
            for item in tracked_issues:
                if item["key"] in fix_status_map:
                    item["status"] = fix_status_map[item["key"]]
            with open(tracking_file, "w") as f:
                json.dump(tracked_issues, f, indent=2)
            logger.info(f"Updated tracking file: {tracking_file}")

        return {
            "fix_results": all_fix_results,
            "total_fixed": total_fixed,
            "total_skipped": total_skipped,
        }

    def post(self, shared, _prep_res, exec_res):
        # Accumulate fix results
        existing_results = shared.get("fix_results", [])
        existing_results.extend(exec_res.get("fix_results", []))
        shared["fix_results"] = existing_results
        shared["total_fixed"] = shared.get("total_fixed", 0) + exec_res.get("total_fixed", 0)
        shared["total_skipped"] = shared.get("total_skipped", 0) + exec_res.get("total_skipped", 0)


# ---------------------------------------------------------------------------
# Node: FixCoverage — specialized agent for test coverage
# ---------------------------------------------------------------------------

class FixCoverage(Node):
    """Write unit tests to improve code coverage.

    Only runs if coverage-based quality gate conditions are failing.
    """

    _COVERAGE_METRICS = {"new_coverage", "new_line_coverage", "new_branch_coverage"}

    def prep(self, shared):
        failing_conditions = shared.get("failing_conditions", [])

        # Find failing coverage condition
        coverage_condition = None
        for c in failing_conditions:
            if c.get("metricKey", "") in self._COVERAGE_METRICS:
                coverage_condition = c
                break

        if not coverage_condition:
            return {"skip": True, "reason": "No coverage conditions failing"}

        return {
            "skip": False,
            "coverage_condition": coverage_condition,
            "measures": shared.get("measures", {}),
            "local_repo_path": shared.get("local_repo_path"),
            "changed_files": shared.get("changed_files"),
            "coverage_components": shared.get("coverage_components", []),
        }

    def exec(self, prep_res):
        if prep_res.get("skip"):
            logger.info(f"FixCoverage: skipping -- {prep_res.get('reason')}")
            return {"fix_results": [], "total_fixed": 0, "total_skipped": 0}

        condition = prep_res["coverage_condition"]
        measures = prep_res["measures"]
        local_repo_path = prep_res["local_repo_path"]
        changed_files = prep_res.get("changed_files")
        coverage_components = prep_res.get("coverage_components", [])

        # Auto-detect changed files if needed
        if changed_files is None and local_repo_path:
            changed_files = _get_changed_files(local_repo_path)

        changed_file_paths = None
        if changed_files:
            changed_file_paths = [
                f["path"] if isinstance(f, dict) else f for f in changed_files
            ]

        # Build prompt
        prompt = build_coverage_fix_prompt(
            condition=condition,
            measures=measures,
            local_repo_path=local_repo_path,
            changed_files=changed_file_paths,
            coverage_components=coverage_components,
        )

        # Build results tool
        captured_results = {}
        tool = _build_write_fix_results_tool(captured_results)

        # Run agent
        actual = condition.get("actualValue", "?")
        required = condition.get("errorThreshold", "?")
        logger.info(f"Running coverage-fix agent (current: {actual}%, required: >= {required}%)...")
        system_message = COVERAGE_SYSTEM_MESSAGE + load_skills(local_repo_path)
        try:
            call_llm_agentic(
                prompt,
                tools=[tool],
                system_message=system_message,
                model="gpt-5.6-sol",
                working_directory=local_repo_path,
                disable_fallback=True,
            )
        except Exception as e:
            logger.error(f"Coverage-fix agent failed: {e}")

        # Process results
        all_fix_results, total_fixed, total_skipped = _process_fix_results(captured_results)
        logger.info(f"Coverage-fix agent: {total_fixed} fixed, {total_skipped} skipped")

        return {
            "fix_results": all_fix_results,
            "total_fixed": total_fixed,
            "total_skipped": total_skipped,
        }

    def post(self, shared, _prep_res, exec_res):
        existing_results = shared.get("fix_results", [])
        existing_results.extend(exec_res.get("fix_results", []))
        shared["fix_results"] = existing_results
        shared["total_fixed"] = shared.get("total_fixed", 0) + exec_res.get("total_fixed", 0)
        shared["total_skipped"] = shared.get("total_skipped", 0) + exec_res.get("total_skipped", 0)


# ---------------------------------------------------------------------------
# Node: FixDuplication — specialized agent for code deduplication
# ---------------------------------------------------------------------------

class FixDuplication(Node):
    """Refactor duplicated code blocks to reduce duplication density.

    Only runs if duplication-based quality gate conditions are failing.
    """

    _DUPLICATION_METRICS = {"new_duplicated_lines_density"}

    def prep(self, shared):
        failing_conditions = shared.get("failing_conditions", [])

        # Find failing duplication condition
        duplication_condition = None
        for c in failing_conditions:
            if c.get("metricKey", "") in self._DUPLICATION_METRICS:
                duplication_condition = c
                break

        if not duplication_condition:
            return {"skip": True, "reason": "No duplication conditions failing"}

        return {
            "skip": False,
            "duplication_condition": duplication_condition,
            "measures": shared.get("measures", {}),
            "local_repo_path": shared.get("local_repo_path"),
            "changed_files": shared.get("changed_files"),
            "duplication_components": shared.get("duplication_components", []),
        }

    def exec(self, prep_res):
        if prep_res.get("skip"):
            logger.info(f"FixDuplication: skipping -- {prep_res.get('reason')}")
            return {"fix_results": [], "total_fixed": 0, "total_skipped": 0}

        condition = prep_res["duplication_condition"]
        measures = prep_res["measures"]
        local_repo_path = prep_res["local_repo_path"]
        changed_files = prep_res.get("changed_files")
        duplication_components = prep_res.get("duplication_components", [])

        # Auto-detect changed files if needed
        if changed_files is None and local_repo_path:
            changed_files = _get_changed_files(local_repo_path)

        changed_file_paths = None
        if changed_files:
            changed_file_paths = [
                f["path"] if isinstance(f, dict) else f for f in changed_files
            ]

        # Build prompt
        prompt = build_duplication_fix_prompt(
            condition=condition,
            measures=measures,
            local_repo_path=local_repo_path,
            changed_files=changed_file_paths,
            duplication_components=duplication_components,
        )

        # Build results tool
        captured_results = {}
        tool = _build_write_fix_results_tool(captured_results)

        # Run agent
        actual = condition.get("actualValue", "?")
        required = condition.get("errorThreshold", "?")
        logger.info(f"Running duplication-fix agent (current: {actual}%, required: <= {required}%)...")
        system_message = DUPLICATION_SYSTEM_MESSAGE + load_skills(local_repo_path)
        try:
            call_llm_agentic(
                prompt,
                tools=[tool],
                system_message=system_message,
                model="gpt-5.6-sol",
                working_directory=local_repo_path,
                disable_fallback=True,
            )
        except Exception as e:
            logger.error(f"Duplication-fix agent failed: {e}")

        # Process results
        all_fix_results, total_fixed, total_skipped = _process_fix_results(captured_results)
        logger.info(f"Duplication-fix agent: {total_fixed} fixed, {total_skipped} skipped")

        return {
            "fix_results": all_fix_results,
            "total_fixed": total_fixed,
            "total_skipped": total_skipped,
        }

    def post(self, shared, _prep_res, exec_res):
        existing_results = shared.get("fix_results", [])
        existing_results.extend(exec_res.get("fix_results", []))
        shared["fix_results"] = existing_results
        shared["total_fixed"] = shared.get("total_fixed", 0) + exec_res.get("total_fixed", 0)
        shared["total_skipped"] = shared.get("total_skipped", 0) + exec_res.get("total_skipped", 0)


# ---------------------------------------------------------------------------
# Node: CreateFixBranch
# ---------------------------------------------------------------------------

class CreateFixBranch(Node):
    """Create a new branch with the fixes and commit changes."""

    def prep(self, shared):
        local_repo_path = shared.get("local_repo_path")
        source_branch = shared.get("source_branch")
        fix_branch_name = shared.get("fix_branch_name")
        total_fixed = shared.get("total_fixed", 0)
        push_branch = shared.get("push_branch", False)
        gitlab_token = shared.get("gitlab_token")
        gitlab_url = shared.get("gitlab_url", "https://gitlab.example.com")
        project_id = shared.get("project_id")

        if not fix_branch_name:
            fix_branch_name = f"sonarfix/{source_branch}"

        return {
            "local_repo_path": local_repo_path,
            "source_branch": source_branch,
            "fix_branch_name": fix_branch_name,
            "total_fixed": total_fixed,
            "push_branch": push_branch,
            "gitlab_token": gitlab_token,
            "gitlab_url": gitlab_url,
            "project_id": project_id,
        }

    def exec(self, prep_res):
        local_repo_path = prep_res["local_repo_path"]
        fix_branch_name = prep_res["fix_branch_name"]
        source_branch = prep_res["source_branch"]
        total_fixed = prep_res["total_fixed"]
        push_branch = prep_res["push_branch"]
        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        project_id = prep_res["project_id"]

        if total_fixed == 0:
            logger.info("No fixes applied -- skipping branch creation")
            return {"branch_created": False, "branch_pushed": False}

        if gitlab_token and project_id:
            return self._commit_via_api(
                local_repo_path, fix_branch_name, source_branch,
                total_fixed, gitlab_token, gitlab_url, project_id,
            )

        # Fallback: local git commit + push (requires git identity configured)
        return self._commit_via_git(
            local_repo_path, fix_branch_name, total_fixed, push_branch,
        )

    def _commit_via_api(self, local_repo_path, fix_branch_name, source_branch,
                        total_fixed, gitlab_token, gitlab_url, project_id):
        """Commit changes via GitLab API (no local git identity required)."""
        import gitlab as gitlab_lib

        # Stage all changes so we can diff against HEAD
        cmd = ["git", "-C", local_repo_path, "add", "-A"]
        subprocess.run(cmd, capture_output=True, text=True, timeout=60)

        # Detect changed files
        cmd = ["git", "-C", local_repo_path, "diff", "--cached", "--name-status"]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if not result.stdout.strip():
            logger.info("No changes to commit")
            return {"branch_created": True, "branch_pushed": False}

        commit_actions = []
        for line in result.stdout.strip().splitlines():
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            status = parts[0].strip()
            file_path = parts[-1].strip()

            if status.startswith("D"):
                commit_actions.append({"action": "delete", "file_path": file_path})
            elif status.startswith(("A", "M", "T", "R")):
                abs_path = os.path.join(local_repo_path, file_path)
                try:
                    with open(abs_path, "r", encoding="utf-8") as f:
                        content = f.read()
                    action = "create" if status.startswith("A") else "update"
                    commit_actions.append({
                        "action": action,
                        "file_path": file_path,
                        "content": content,
                    })
                except (UnicodeDecodeError, FileNotFoundError) as exc:
                    logger.warning(f"Skipping file {file_path}: {exc}")

        if not commit_actions:
            logger.info("No applicable changes to commit via API")
            return {"branch_created": True, "branch_pushed": False}

        commit_msg = (
            f"fix: resolve {total_fixed} SonarQube quality gate issue(s)\n\n"
            "Automated fixes by Clio sonarfix agent."
        )

        gl = gitlab_lib.Gitlab(gitlab_url, private_token=gitlab_token)
        project = gl.projects.get(project_id)
        commit_data = {
            "branch": fix_branch_name,
            "start_branch": source_branch,
            "commit_message": commit_msg,
            "actions": commit_actions,
        }
        commit = project.commits.create(commit_data)
        logger.info(f"Committed fixes via GitLab API: {commit.id}")
        return {"branch_created": True, "branch_pushed": True}

    def _commit_via_git(self, local_repo_path, fix_branch_name, total_fixed, push_branch):
        """Commit changes via local git (fallback for local-dir mode)."""
        # Create and checkout the fix branch
        logger.info(f"Creating fix branch: {fix_branch_name}")
        cmd = ["git", "-C", local_repo_path, "checkout", "-b", fix_branch_name]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode != 0:
            logger.warning(f"Branch may already exist, trying checkout: {result.stderr}")
            cmd = ["git", "-C", local_repo_path, "checkout", fix_branch_name]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            if result.returncode != 0:
                raise Exception(f"Failed to create/checkout fix branch: {result.stderr}")

        # Stage all changes
        cmd = ["git", "-C", local_repo_path, "add", "-A"]
        subprocess.run(cmd, capture_output=True, text=True, timeout=60)

        # Check if there are changes to commit
        cmd = ["git", "-C", local_repo_path, "diff", "--cached", "--quiet"]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode == 0:
            logger.info("No changes to commit")
            return {"branch_created": True, "branch_pushed": False}

        # Commit
        commit_msg = (
            f"fix: resolve {total_fixed} SonarQube quality gate issue(s)\n\n"
            "Automated fixes by Clio sonarfix agent."
        )
        cmd = ["git", "-C", local_repo_path, "commit", "-m", commit_msg]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if result.returncode != 0:
            raise Exception(f"Failed to commit: {result.stderr}")
        logger.info(f"Committed fixes: {commit_msg.splitlines()[0]}")

        # Optionally push
        pushed = False
        if push_branch:
            logger.info(f"Pushing branch: {fix_branch_name}")
            cmd = ["git", "-C", local_repo_path, "push", "-u", "origin", fix_branch_name]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
            if result.returncode != 0:
                logger.error(f"Failed to push: {result.stderr}")
            else:
                pushed = True
                logger.info("Branch pushed successfully")

        return {"branch_created": True, "branch_pushed": pushed}

    def post(self, shared, prep_res, exec_res):
        shared["fix_branch_created"] = exec_res["branch_created"]
        shared["fix_branch_pushed"] = exec_res["branch_pushed"]
        shared["fix_branch_name"] = prep_res["fix_branch_name"]


# ---------------------------------------------------------------------------
# Node: CreateMergeRequest
# ---------------------------------------------------------------------------

class CreateMergeRequest(Node):
    """Push the fix branch and create a GitLab merge request."""

    def prep(self, shared):
        return {
            "gitlab_token": shared.get("gitlab_token"),
            "gitlab_url": shared.get("gitlab_url", "https://gitlab.example.com"),
            "project_id": shared.get("project_id"),
            "source_branch": shared.get("source_branch"),
            "fix_branch_name": shared.get("fix_branch_name"),
            "fix_branch_created": shared.get("fix_branch_created", False),
            "fix_branch_pushed": shared.get("fix_branch_pushed", False),
            "local_repo_path": shared.get("local_repo_path"),
            "total_fixed": shared.get("total_fixed", 0),
            "total_skipped": shared.get("total_skipped", 0),
            "sonar_issues_total": shared.get("sonar_issues_total", 0),
            "fix_results": shared.get("fix_results", []),
            "dry_run": shared.get("dry_run", False),
            "create_mr": shared.get("create_mr", False),
            "original_mr_url": shared.get("mr_web_url", ""),
            "original_mr_title": shared.get("mr_title", ""),
            "original_mr_author_id": shared.get("mr_author_id"),
            "original_mr_author_username": shared.get("mr_author_username", ""),
        }

    def exec(self, prep_res):
        dry_run = prep_res["dry_run"]
        create_mr = prep_res["create_mr"]
        fix_branch_created = prep_res["fix_branch_created"]
        total_fixed = prep_res["total_fixed"]

        if not create_mr:
            logger.info("MR creation not requested (use --create-mr to enable)")
            return {"mr_created": False, "mr_url": None}

        if not fix_branch_created or total_fixed == 0:
            logger.info("No fixes to submit -- skipping MR creation")
            return {"mr_created": False, "mr_url": None}

        if dry_run:
            logger.info("Dry run -- skipping MR creation")
            return {"mr_created": False, "mr_url": None}

        gitlab_token = prep_res["gitlab_token"]
        gitlab_url = prep_res["gitlab_url"]
        project_id = prep_res["project_id"]
        source_branch = prep_res["source_branch"]
        fix_branch_name = prep_res["fix_branch_name"]
        local_repo_path = prep_res["local_repo_path"]
        total_skipped = prep_res["total_skipped"]
        sonar_issues_total = prep_res["sonar_issues_total"]
        fix_results = prep_res["fix_results"]
        original_mr_url = prep_res["original_mr_url"]
        original_mr_title = prep_res["original_mr_title"]
        original_mr_author_id = prep_res["original_mr_author_id"]

        if not gitlab_token or not project_id:
            logger.warning("Missing GitLab credentials -- skipping MR creation")
            return {"mr_created": False, "mr_url": None}

        # Push the branch if not already pushed
        if not prep_res["fix_branch_pushed"]:
            logger.info(f"Pushing branch: {fix_branch_name}")
            cmd = ["git", "-C", local_repo_path, "push", "-u", "origin", fix_branch_name]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
            if result.returncode != 0:
                logger.error(f"Failed to push branch: {result.stderr}")
                return {"mr_created": False, "mr_url": None}
            logger.info("Branch pushed successfully")

        # Build MR description
        description = self._build_mr_description(
            total_fixed, total_skipped, sonar_issues_total, fix_results,
            original_mr_url, original_mr_title,
        )

        # Create the merge request via GitLab API
        try:
            import gitlab as gitlab_lib
            gl = gitlab_lib.Gitlab(gitlab_url, private_token=gitlab_token)
            project = gl.projects.get(project_id)
            mr_data = {
                "source_branch": fix_branch_name,
                "target_branch": source_branch,
                "title": f"fix: resolve {total_fixed} SonarQube quality gate issue(s)",
                "description": description,
                "remove_source_branch": True,
                "labels": ["automated_sonar_fix"],
            }
            if original_mr_author_id:
                mr_data["assignee_id"] = original_mr_author_id
                mr_data["reviewer_ids"] = [original_mr_author_id]
            mr = project.mergerequests.create(mr_data)
            mr_url = mr.web_url
            logger.info(f"Merge request created: {mr_url}")
            return {"mr_created": True, "mr_url": mr_url}
        except Exception as e:
            logger.error(f"Failed to create merge request: {e}")
            return {"mr_created": False, "mr_url": None}

    @staticmethod
    def _build_mr_description(total_fixed, total_skipped, total_issues, fix_results,
                              original_mr_url="", original_mr_title=""):
        """Build a markdown description for the merge request."""
        lines = [
            "## 🛠️ Automated SonarQube Fixes",
            "",
            "> *This MR contains automated fixes for SonarQube quality gate issues "
            "detected and resolved by the **sonarfix agent**.*",
            "",
        ]

        if original_mr_url:
            display = original_mr_title or original_mr_url
            lines.extend([
                f"🔗 **Original Merge Request:** [{display}]({original_mr_url})",
                "",
            ])

        total_analysed = total_fixed + total_skipped
        lines.extend([
            "---",
            "",
            "### 📊 Execution Summary",
            "",
            f"* 🔍 **Issues Analysed:** {total_analysed}",
            f"* ✅ **Fixed:** {total_fixed}",
            f"* ⏭️ **Skipped:** {total_skipped}",
            "",
            "---",
            "",
            "### 📝 Fix Details",
            "",
        ])

        for idx, r in enumerate(fix_results, start=1):
            if not isinstance(r, dict):
                continue
            key = r.get("issue_key", "")
            file_path = r.get("file_path", "")
            status = r.get("status", "")
            reason = r.get("reason", "")
            status_icon = "✅ Fixed" if status == "fixed" else "⏭️ Skipped"
            lines.extend([
                f"**{idx}. Issue:** `{key}`",
                f"* **File:** `{file_path}`",
                f"* **Status:** {status_icon}",
                f"* **Resolution:** {reason}",
                "",
            ])

        lines.extend([
            "---",
            "",
            "⚠️ *Please review all changes carefully before merging.*",
            "🤖 *Automated by **sonarfix agent***",
        ])
        return "\n".join(lines)

    def post(self, shared, _prep_res, exec_res):
        shared["mr_created"] = exec_res["mr_created"]
        shared["mr_url"] = exec_res["mr_url"]
