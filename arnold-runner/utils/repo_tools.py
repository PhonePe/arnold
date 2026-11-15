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

"""Repository-scoped write and test tools for agents that change code.

Agents never get shell or built-in file-write tools. Changes go through these
tools, which keep every path inside the repository, refuse CI and VCS metadata,
and record what was written so only those files are committed. Tests run from a
fixed command template with an environment that carries no credentials.
"""


import os
import re
import subprocess
from dataclasses import dataclass, field
from typing import Callable

from pydantic import BaseModel, Field

from utils.logger import get_logger

logger = get_logger("repo_tools")

MAX_FILE_BYTES = 1_000_000
TEST_TIMEOUT_SECONDS = int(os.getenv("CLIO_TEST_TIMEOUT", "900"))
MAX_TEST_OUTPUT_CHARS = 12_000

# Paths whose modification could change how CI, the build host or reviewers behave.
_PROTECTED_DIRS = (".git", ".github", ".gitlab", ".circleci", ".husky", ".diffs", ".comments")
_PROTECTED_FILES = frozenset({
    ".gitlab-ci.yml", "Jenkinsfile", ".gitmodules", ".gitattributes", "AGENTS.md",
    "azure-pipelines.yml", "bitbucket-pipelines.yml", ".travis.yml",
})

_TEST_DIR_NAMES = frozenset({"test", "tests", "__tests__", "spec", "specs", "testFixtures"})
_TEST_FILE_RE = re.compile(
    r"(^test_.*\.py$|_test\.py$|_test\.go$|Tests?\.(java|kt|scala|groovy)$|IT\.(java|kt)$"
    r"|\.(test|spec)\.(js|jsx|ts|tsx|mjs|cjs)$)"
)

_ENV_ALLOWLIST = frozenset({
    "PATH", "HOME", "USER", "LOGNAME", "LANG", "LANGUAGE", "TZ", "TMPDIR",
    "JAVA_HOME", "MAVEN_HOME", "M2_HOME", "GRADLE_USER_HOME", "MAVEN_OPTS", "GRADLE_OPTS",
    "NODE_PATH", "VIRTUAL_ENV", "PYTHONPATH",
    "SSL_CERT_FILE", "SSL_CERT_DIR", "REQUESTS_CA_BUNDLE", "NODE_EXTRA_CA_CERTS",
    "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "no_proxy",
})

_MODULE_RE = re.compile(r"^[A-Za-z0-9_.:/-]{1,200}$")
_JVM_TEST_RE = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$.*#]{0,200}$")


class RepoPathError(ValueError):
    pass


def is_test_path(rel_path: str) -> bool:
    parts = rel_path.replace("\\", "/").split("/")
    return any(p in _TEST_DIR_NAMES for p in parts[:-1]) or bool(_TEST_FILE_RE.search(parts[-1]))


def resolve_repo_path(repo_root: str, rel_path: str) -> tuple[str, str]:
    """Return ``(absolute_path, normalized_relative_path)`` or raise RepoPathError."""
    if not rel_path or "\x00" in rel_path:
        raise RepoPathError("path is empty or invalid")
    root = os.path.realpath(repo_root)
    candidate = rel_path if os.path.isabs(rel_path) else os.path.join(root, rel_path)
    absolute = os.path.realpath(candidate)
    if absolute == root or not absolute.startswith(root + os.sep):
        raise RepoPathError(f"{rel_path} is outside the repository")
    rel = os.path.relpath(absolute, root).replace(os.sep, "/")
    parts = rel.split("/")
    if any(p in _PROTECTED_DIRS for p in parts) or parts[-1] in _PROTECTED_FILES:
        raise RepoPathError(f"{rel} is a protected path")
    if os.path.islink(candidate):
        raise RepoPathError(f"{rel} is a symlink")
    return absolute, rel


def _scrubbed_env() -> dict[str, str]:
    env = {k: v for k, v in os.environ.items() if k in _ENV_ALLOWLIST or k.startswith("LC_")}
    env["CI"] = "true"
    return env


def build_test_command(build_tool: str, module: str | None, tests: list[str]) -> list[str]:
    """Fixed command templates; every argument is validated so nothing reaches a shell."""
    if module is not None and not _MODULE_RE.match(module):
        raise ValueError("invalid module name")
    if not tests:
        raise ValueError("at least one test is required; running whole suites is not allowed")
    if build_tool in ("maven", "gradle"):
        if not all(_JVM_TEST_RE.match(t) for t in tests):
            raise ValueError("invalid test class name")
        if build_tool == "maven":
            cmd = ["mvn", "-B", "-q", "test", f"-Dtest={','.join(tests)}",
                   "-Dsurefire.failIfNoSpecifiedTests=false"]
            return cmd + (["-pl", module, "-am"] if module else [])
        task = f":{module.strip(':')}:test" if module else "test"
        cmd = ["./gradlew", "--no-daemon", "-q", task]
        for t in tests:
            cmd += ["--tests", t]
        return cmd
    if build_tool in ("pytest", "jest"):
        for t in tests:
            if t.startswith("-") or not _MODULE_RE.match(t.split("::")[0]):
                raise ValueError("invalid test path")
        if build_tool == "pytest":
            return ["python", "-m", "pytest", "-q", "--tb=short", "--", *tests]
        return ["npx", "--no-install", "jest", "--", *tests]
    raise ValueError(f"unsupported build tool: {build_tool}")


@dataclass
class RepoWriteSession:
    """Tracks files written by an agent and builds the tools that write them."""

    repo_root: str
    allow: Callable[[str], bool] = lambda _rel: True
    restriction: str = ""
    written: set[str] = field(default_factory=set)

    def _check(self, rel_path: str) -> tuple[str, str]:
        absolute, rel = resolve_repo_path(self.repo_root, rel_path)
        if not self.allow(rel):
            raise RepoPathError(f"{rel} is not allowed here. {self.restriction}".strip())
        return absolute, rel

    def write_file(self, rel_path: str, content: str) -> str:
        if len(content.encode("utf-8")) > MAX_FILE_BYTES:
            raise RepoPathError("content exceeds the size limit")
        absolute, rel = self._check(rel_path)
        os.makedirs(os.path.dirname(absolute), exist_ok=True)
        with open(absolute, "w", encoding="utf-8") as f:
            f.write(content)
        self.written.add(rel)
        logger.info("[tool] write_file %s (%d chars)", rel, len(content))
        return f"OK -- wrote {rel}"

    def replace_in_file(self, rel_path: str, old: str, new: str, replace_all: bool = False) -> str:
        absolute, rel = self._check(rel_path)
        if not os.path.isfile(absolute):
            raise RepoPathError(f"{rel} does not exist")
        with open(absolute, encoding="utf-8") as f:
            text = f.read()
        count = text.count(old) if old else 0
        if count == 0:
            raise RepoPathError("old text not found; read the file and copy the exact text")
        if count > 1 and not replace_all:
            raise RepoPathError(f"old text occurs {count} times; add surrounding context or set replace_all")
        updated = text.replace(old, new) if replace_all else text.replace(old, new, 1)
        if len(updated.encode("utf-8")) > MAX_FILE_BYTES:
            raise RepoPathError("result exceeds the size limit")
        with open(absolute, "w", encoding="utf-8") as f:
            f.write(updated)
        self.written.add(rel)
        logger.info("[tool] replace_in_file %s (%d replacement(s))", rel, count if replace_all else 1)
        return f"OK -- updated {rel}"

    def run_tests(self, build_tool: str, tests: list[str], module: str | None = None) -> str:
        cmd = build_test_command(build_tool, module, tests)
        logger.info("[tool] run_tests %s", cmd)
        try:
            proc = subprocess.run(
                cmd, cwd=os.path.realpath(self.repo_root), env=_scrubbed_env(),
                capture_output=True, text=True, timeout=TEST_TIMEOUT_SECONDS,
            )
        except subprocess.TimeoutExpired:
            return f"TIMEOUT after {TEST_TIMEOUT_SECONDS}s"
        except FileNotFoundError as e:
            return f"ERROR -- {e}"
        output = (proc.stdout or "") + (proc.stderr or "")
        if len(output) > MAX_TEST_OUTPUT_CHARS:
            output = "...(truncated)...\n" + output[-MAX_TEST_OUTPUT_CHARS:]
        return f"exit code {proc.returncode}\n{output}"

    def tools(self, with_tests: bool = False) -> list:
        from utils.tool_adapter import define_tool

        session = self

        class WriteFileParams(BaseModel):
            path: str = Field(description="File path relative to the repository root")
            content: str = Field(description="Complete new file content")

        class ReplaceInFileParams(BaseModel):
            path: str = Field(description="File path relative to the repository root")
            old: str = Field(description="Exact existing text to replace, including enough context to be unique")
            new: str = Field(description="Replacement text")
            replace_all: bool = Field(default=False, description="Replace every occurrence instead of exactly one")

        class RunTestsParams(BaseModel):
            build_tool: str = Field(description="One of: maven, gradle, pytest, jest")
            tests: list[str] = Field(description="Test classes (maven/gradle) or test file paths (pytest/jest) to run")
            module: str | None = Field(default=None, description="Maven module or Gradle project path, if multi-module")

        def _safe(fn, *args):
            try:
                return fn(*args)
            except (RepoPathError, ValueError, OSError) as e:
                return f"ERROR -- {e}"

        restriction = f" {self.restriction}" if self.restriction else ""

        @define_tool(description="Create or overwrite a file in the repository." + restriction)
        def write_file(params: WriteFileParams) -> str:
            return _safe(session.write_file, params.path, params.content)

        @define_tool(description="Replace exact text in an existing repository file." + restriction)
        def replace_in_file(params: ReplaceInFileParams) -> str:
            return _safe(session.replace_in_file, params.path, params.old, params.new, params.replace_all)

        tools = [write_file, replace_in_file]
        if with_tests:
            @define_tool(description=(
                "Compile and run specific test classes or files. Runs a fixed build command "
                "(no shell); returns the exit code and the tail of the output."
            ))
            def run_tests(params: RunTestsParams) -> str:
                return _safe(session.run_tests, params.build_tool, params.tests, params.module)

            tools.append(run_tests)
        return tools
