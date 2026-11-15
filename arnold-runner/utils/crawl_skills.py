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

"""
Skills Crawler for Code Review

Discovers and collects agent skills from:
1. The central skills repo — a dedicated GitLab repository that mirrors the
   namespace hierarchy (group/subgroup/project), with skills stored under
   ``.github/skills/`` at each level.
2. The target project's own ``.github/skills/`` directory.

Skills are Markdown files (`SKILL.md`) inside recognised skill directories
that contain review guidelines, conventions, and domain knowledge.
"""

import os
import re
import logging
import tempfile
import shutil
import subprocess
import fnmatch
from urllib.parse import urlparse, urlunparse
from utils.logger import get_logger

logger = get_logger("crawl_skills_logger")

CENTRAL_SKILLS_REPO_URL = os.environ.get(
    "CLIO_CENTRAL_SKILLS_REPO_URL",
    "https://gitlab.example.com/acme/code-review-skills",
)

CENTRAL_SKILLS_REPO_BRANCH = os.environ.get(
    "SKILLS_REPO_BRANCH",
    "main",
)

# The only recognised skill file name
SKILL_FILE_PATTERNS = ["SKILL.md"]


def _read_file_safe(path: str, max_size: int = 512 * 1024) -> str | None:
    """Read a file's text content, returning None on failure or if too large."""
    try:
        size = os.path.getsize(path)
        if size > max_size:
            logger.debug(f"Skipping large file ({size} bytes): {path}")
            return None
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            return fh.read()
    except Exception as exc:
        logger.warning(f"Could not read {path}: {exc}")
        return None


def _is_skill_file(filename: str) -> bool:
    """Return True if *filename* matches any recognised skill pattern."""
    return any(fnmatch.fnmatch(filename, pat) for pat in SKILL_FILE_PATTERNS)


# ---------------------------------------------------------------------------
# Central skills repo discovery
# ---------------------------------------------------------------------------

def _clone_central_skills_repo(
    gitlab_token: str | None = None,
    repo_url: str = CENTRAL_SKILLS_REPO_URL,
    branch: str = CENTRAL_SKILLS_REPO_BRANCH,
) -> str | None:
    """Shallow-clone the central skills repo into a temp directory.

    Returns the path to the cloned directory, or None on failure.
    """
    clone_url = repo_url
    if gitlab_token:
        parsed = urlparse(repo_url)
        netloc = f"oauth2:{gitlab_token}@{parsed.netloc}"
        clone_url = urlunparse((parsed.scheme, netloc, parsed.path, "", "", ""))
    clone_url = clone_url.rstrip("/")
    if not clone_url.endswith(".git"):
        clone_url += ".git"

    temp_dir = tempfile.mkdtemp(prefix="skills_repo_")
    cmd = ["git", "clone", "--depth", "1", "--branch", branch, clone_url, temp_dir]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        if result.returncode != 0:
            stderr = result.stderr.strip()
            if gitlab_token:
                stderr = stderr.replace(gitlab_token, "***")
            logger.warning(f"Failed to clone central skills repo: {stderr}")
            shutil.rmtree(temp_dir, ignore_errors=True)
            return None
        logger.info(f"Cloned central skills repo to {temp_dir}")
        return temp_dir
    except Exception as exc:
        logger.warning(f"Exception cloning central skills repo: {exc}")
        shutil.rmtree(temp_dir, ignore_errors=True)
        return None


def _extract_repo_path_parts(repo_url: str) -> list[str]:
    """Extract the GitLab project path segments from a repo URL.

    Example:
        ``https://gitlab.example.com/group/subgroup/my-repo``  →  ``["group", "subgroup", "my-repo"]``
    """
    parsed = urlparse(repo_url)
    path = parsed.path.strip("/")
    if path.endswith(".git"):
        path = path[:-4]
    parts = [p for p in path.split("/") if p]
    return parts


def _find_skill_folders(skills_dir: str) -> list[str]:
    """Return paths of direct child directories of *skills_dir* that contain at
    least one recognised skill file (recursively).

    Expects *skills_dir* to be a ``.github/skills/`` directory where every
    direct child is a skill folder (no hierarchy ambiguity).
    """
    skill_folders: list[str] = []
    if not os.path.isdir(skills_dir):
        return skill_folders
    for entry in os.scandir(skills_dir):
        if not entry.is_dir():
            continue
        contains_skill = any(
            _is_skill_file(fname)
            for _, _dirs, files in os.walk(entry.path)
            for fname in files
        )
        if contains_skill:
            skill_folders.append(entry.path)
    return skill_folders


def _copy_central_skills_to_repo(
    cloned_skills_dir: str,
    path_parts: list[str],
    local_repo_path: str,
) -> list[dict]:
    """Copy central skill folders into ``<local_repo_path>/.github/skills/``.

    The central skills repo uses the ``.github/skills/`` convention at every
    hierarchy level, with the namespace hierarchy starting at the repo root::

        <group>/.github/skills/<skill-folder>/SKILL.md      ← group-level
        <group>/<subgroup>/.github/skills/<skill-folder>/    ← subgroup-level
        <group>/<subgroup>/<repo>/.github/skills/<skill>/    ← project-level

    For each level of the hierarchy (group → group/subgroup → …/repo) that
    has a ``.github/skills/`` directory, its skill folders are copied to the
    destination, prefixed with the cumulative path so names are unique:

        ``clio-<group>-<subgroup>-<skill-folder-name>/``

    Returns a list of skill dicts read from the copies inside the local repo.
    """
    skills_root = cloned_skills_dir

    dest_base = os.path.join(local_repo_path, ".github", "skills")
    os.makedirs(dest_base, exist_ok=True)

    skills: list[dict] = []
    cumulative_parts: list[str] = []

    for part in path_parts:
        cumulative_parts.append(part)
        cumulative = os.path.join(*cumulative_parts)
        skills_dir = os.path.join(skills_root, cumulative, ".github", "skills")

        if not os.path.isdir(skills_dir):
            continue

        prefix = "-".join(cumulative_parts)

        for skill_folder in _find_skill_folders(skills_dir):
            folder_name = os.path.basename(skill_folder)
            dest_name = f"{prefix}-{folder_name}"
            dest_path = os.path.join(dest_base, dest_name)

            if os.path.exists(dest_path):
                shutil.rmtree(dest_path)
            shutil.copytree(skill_folder, dest_path)
            logger.info(
                f"Copied central skill folder '{cumulative}/.github/skills/{folder_name}'"
                f" → '.github/skills/{dest_name}'"
            )

            for root, _dirs, files in os.walk(dest_path):
                for fname in files:
                    if not _is_skill_file(fname):
                        continue
                    full_path = os.path.join(root, fname)
                    content = _read_file_safe(full_path)
                    if content:
                        rel = os.path.relpath(full_path, local_repo_path)
                        skills.append({
                            "source": f"central/{cumulative}",
                            "path": rel,
                            "content": content,
                        })
                        logger.info(f"Found central skill: {rel}")

    return skills


def discover_central_skills(
    repo_url: str,
    local_repo_path: str,
    gitlab_token: str | None = None,
    central_repo_url: str = CENTRAL_SKILLS_REPO_URL,
    central_repo_branch: str = CENTRAL_SKILLS_REPO_BRANCH,
) -> list[dict]:
    """Fetch skills from the central skills repo that apply to *repo_url*.

    The central repo mirrors the GitLab namespace hierarchy, with skills stored
    under ``.github/skills/`` at each level::

        <group>/.github/skills/<skill>/SKILL.md          ← group-level
        <group>/<subgroup>/.github/skills/<skill>/        ← subgroup-level
        <group>/<subgroup>/<repo>/.github/skills/<skill>/ ← project-level

    Matching skill folders are copied into ``<local_repo_path>/.github/skills/``
    with the cumulative hierarchy path prepended to their name so that names are
    unique and the local repo's own ``.github/`` content is never overwritten.

    Returns a list of dicts: ``{"source": ..., "path": ..., "content": ...}``
    """
    if not repo_url:
        return []

    path_parts = _extract_repo_path_parts(repo_url)
    if not path_parts:
        logger.warning("Could not extract path parts from repo URL")
        return []

    cloned_dir = _clone_central_skills_repo(gitlab_token, central_repo_url, central_repo_branch)
    if not cloned_dir:
        logger.warning("Central skills repo not available — skipping central skills")
        return []

    skills: list[dict] = []
    try:
        skills = _copy_central_skills_to_repo(cloned_dir, path_parts, local_repo_path)
    finally:
        shutil.rmtree(cloned_dir, ignore_errors=True)

    return skills


# ---------------------------------------------------------------------------
# Keyword-based filtering
# ---------------------------------------------------------------------------

# Keywords that indicate a skill is code-review related.
# Matched case-insensitively against skill content and path.
# Strong patterns — any single match means the skill is about code review.
_CODE_REVIEW_STRONG = re.compile(
    r"code[_\s-]?review"
    r"|review\s*(guideline|rule|checklist|convention|instruction)"
    r"|(pr|mr)\s*review"
    r"|review\s*(pull|merge)\s*request"
    r"|(pull|merge)\s*request\s*review"
    r"|coding\s*(standard|convention|guideline)"
    r"|code\s*(quality|style|standard)\s*(guideline|rule|checklist)"
    r"|security\s*review",
    re.IGNORECASE,
)

# Weak patterns — individually not enough to classify as code-review.
# Require multiple matches (threshold: 3+) to confirm the skill is
# primarily about code review quality rather than just mentioning a term.
_CODE_REVIEW_WEAK = re.compile(
    r"code\s*(quality|smell)"
    r"|style\s*guide"
    r"|lint(ing|er|\s*rule)"
    r"|static\s*analysis"
    r"|best[_\s-]?practice"
    r"|anti[_\s-]?pattern"
    r"|vulnerabilit(y|ies)"
    r"|error[_\s-]?handling"
    r"|naming\s*convention"
    r"|refactor(ing)?"
    r"|code\s*convention",
    re.IGNORECASE,
)

_WEAK_MATCH_THRESHOLD = 3


def _is_code_review_skill(skill: dict) -> bool:
    """Return True if the skill is related to code review.

    Uses a two-tier approach:
    - Strong patterns: a single match is sufficient (the skill explicitly
      mentions code review, PR/MR review, or coding standards).
    - Weak patterns: require 3+ distinct matches, indicating the skill's
      primary purpose is code quality rather than just mentioning a term
      in passing (e.g., a migration guide saying "best practice").
    """
    path_lower = skill.get("path", "").lower()
    if "review" in path_lower:
        return True

    content = skill.get("content", "")

    # Strong match — single hit is definitive
    if _CODE_REVIEW_STRONG.search(content):
        return True

    # Weak match — require multiple distinct pattern hits
    weak_hits = len(_CODE_REVIEW_WEAK.findall(content))
    return weak_hits >= _WEAK_MATCH_THRESHOLD


# ---------------------------------------------------------------------------
# Local (project-level) skill discovery
# ---------------------------------------------------------------------------

def discover_local_skills(
    local_repo_path: str,
    central_skill_paths: set[str] | None = None,
) -> list[dict]:
    """Discover skills already present in the project's ``.github/skills/`` directory.

    Skills that were copied from the central repo (identified by *central_skill_paths*)
    are excluded to avoid double-counting.

    Returns a list of dicts: ``{"source": "local", "path": ..., "content": ...}``
    """
    skills_dir = os.path.join(local_repo_path, ".github", "skills")
    if not os.path.isdir(skills_dir):
        return []

    exclude_paths = central_skill_paths or set()
    skills: list[dict] = []

    for root, _dirs, files in os.walk(skills_dir):
        for fname in files:
            if not _is_skill_file(fname):
                continue
            full_path = os.path.join(root, fname)
            rel = os.path.relpath(full_path, local_repo_path)
            if rel in exclude_paths:
                continue
            content = _read_file_safe(full_path)
            if content:
                skills.append({
                    "source": "local",
                    "path": rel,
                    "content": content,
                })
                logger.info(f"Found local skill: {rel}")

    return skills


# ---------------------------------------------------------------------------
# Public convenience function
# ---------------------------------------------------------------------------

def fetch_all_skills(
    local_repo_path: str,
    repo_url: str = "",
    gitlab_token: str | None = None,
    central_repo_url: str = CENTRAL_SKILLS_REPO_URL,
    central_repo_branch: str = CENTRAL_SKILLS_REPO_BRANCH,
) -> list[dict]:
    """Discover and return all code-review-related skills for a review.

    Collects skills from:
    1. The central skills repo (copied into ``.github/skills/``).
    2. The project's own ``.github/skills/`` directory (pre-existing skills).

    Duplicates are avoided by tracking paths written by the central copy step.
    Central skills are all kept; local skills are filtered by code-review relevance.
    """
    central = discover_central_skills(
        repo_url=repo_url,
        local_repo_path=local_repo_path,
        gitlab_token=gitlab_token,
        central_repo_url=central_repo_url,
        central_repo_branch=central_repo_branch,
    )
    logger.info(f"Discovered {len(central)} central skill(s)")

    central_paths = {s["path"] for s in central}

    local = discover_local_skills(local_repo_path, central_skill_paths=central_paths)
    if local:
        logger.info(f"Discovered {len(local)} local (project-level) skill(s)")

    logger.info(f"Discovered {len(central) + len(local)} skill(s) total")

    # Central skills are curated for review, so only local ones are filtered.
    local_kept = [s for s in local if _is_code_review_skill(s)]
    discarded = len(local) - len(local_kept)
    if discarded:
        logger.info(f"Discarded {discarded} non-code-review local skill(s)")

    return central + local_kept
