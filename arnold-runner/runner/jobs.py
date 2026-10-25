"""Translate Arnold job variables into workflow commands and execute them.

A job is a flat map of string variables (``WORKFLOW``, ``ARNOLD_RUN_ID``, ``MR_LINK``, ...). Every runner mode — the
local worker, GitLab CI and GitHub Actions — passes the same variables, so this module is the single place that knows
how a workflow is started.
"""
from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

RUNNER_HOME = Path(__file__).resolve().parents[1]
DEFAULT_TIMEOUT_SECONDS = int(os.environ.get("ARNOLD_RUNNER_JOB_TIMEOUT", str(165 * 60)))

_NAME = re.compile(r"^[A-Z][A-Z0-9_]{0,63}$")

# Runner-side credentials and process settings that a job must never override.
_PROTECTED = re.compile(
    r"^(ARNOLD_URL|ARNOLD_RUNNER_API_KEY|GITLAB_ACCESS_TOKEN|GITLAB_TOKEN|GITHUB_TOKEN|GH_TOKEN"
    r"|CLIO_LLM_API_KEY|CLIO_LLM_BASE_URL|CLIO_OPENCODE_API_KEY|CLIO_OPENCODE_BASE_URL|OPENAI_API_KEY|OPENAI_BASE_URL"
    r"|RFC_API_TOKEN|IMPACT_SERVICE_TOKEN|SONAR_TOKEN|JIRA_TOKEN"
    r"|PATH|HOME|SHELL|USER|PWD|TMPDIR|SSL_CERT_FILE|REQUESTS_CA_BUNDLE|NODE_OPTIONS"
    r"|PYTHON[A-Z0-9_]*|LD_[A-Z0-9_]*|DYLD_[A-Z0-9_]*|GIT_[A-Z0-9_]*|HTTPS?_PROXY|NO_PROXY|ALL_PROXY)$"
)


class JobError(ValueError):
    """Raised when a job cannot be translated into a command."""


def sanitize(variables: dict[str, str]) -> dict[str, str]:
    """Return job variables that are safe to export, rejecting invalid or protected names."""
    clean = {}
    for name, value in variables.items():
        if not _NAME.match(name):
            raise JobError(f"Invalid job variable name: {name!r}")
        if _PROTECTED.match(name):
            raise JobError(f"Job may not override runner variable {name}")
        if value is None:
            continue
        clean[name] = str(value)
    return clean


def _require(variables: dict[str, str], *names: str) -> None:
    missing = [name for name in names if not variables.get(name)]
    if missing:
        raise JobError(f"Job is missing required variables: {', '.join(missing)}")


def _mr_source(command: str, variables: dict[str, str]) -> list[str]:
    if variables.get("MR_LINK"):
        return [command, "--mr-link", variables["MR_LINK"]]
    if variables.get("REPO_URL") and variables.get("SOURCE_BRANCH"):
        return [command, "--repo", variables["REPO_URL"], "--source-branch", variables["SOURCE_BRANCH"],
                "--target-branch", variables.get("TARGET_BRANCH", "main")]
    raise JobError(f"{command} requires MR_LINK or REPO_URL and SOURCE_BRANCH")


def command_for(variables: dict[str, str]) -> list[str]:
    """Return the ``main.py`` arguments for a job."""
    workflow = variables.get("WORKFLOW", "")
    if workflow in ("improve", "review", "describe", "fix_sonar", "review_analysis"):
        command = {"fix_sonar": "sonarfix", "review_analysis": "impact"}.get(workflow, workflow)
        args = _mr_source(command, variables)
        if workflow == "improve" and variables.get("MIN_CONFIDENCE"):
            args += ["--min-confidence", variables["MIN_CONFIDENCE"]]
        if variables.get("DRY_RUN") == "true":
            args.append("--dry-run")
        return args
    if workflow == "smart_diff":
        _require(variables, "MR_URL", "PROJECT_ID", "MR_IID", "EXPECTED_HEAD_SHA", "EXECUTION_ID")
        return ["smart_diff", "--mr-link", variables["MR_URL"], "--project-id", variables["PROJECT_ID"],
                "--mr-iid", variables["MR_IID"], "--expected-head-sha", variables["EXPECTED_HEAD_SHA"],
                "--execution-id", variables["EXECUTION_ID"]]
    if workflow == "group_review":
        _require(variables, "ARNOLD_RUN_ID", "EXECUTION_ID")
        return ["group_review", "--run-id", variables["ARNOLD_RUN_ID"], "--execution-id", variables["EXECUTION_ID"]]
    raise JobError(f"Unsupported workflow: {workflow!r}")


def run(variables: dict[str, str], timeout: int = DEFAULT_TIMEOUT_SECONDS, workdir: str | None = None) -> int:
    """Run a job to completion in a child process and return its exit code."""
    job = sanitize(variables)
    argv = [sys.executable, str(RUNNER_HOME / "main.py"), *command_for(job)]
    environment = {**os.environ, **job}
    if workdir:
        environment["LOG_DIR"] = os.path.join(workdir, "logs")
        environment["TMPDIR"] = workdir
    try:
        return subprocess.run(argv, env=environment, cwd=str(RUNNER_HOME), timeout=timeout, check=False).returncode
    except subprocess.TimeoutExpired:
        return 124
