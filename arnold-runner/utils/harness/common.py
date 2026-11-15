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

"""Shared helpers across harness modules.

These utilities are used by multiple harness backends (copilot, opencode)
and should stay lightweight to avoid circular imports.
"""

from __future__ import annotations

import os
import shutil
import threading


def crg_available() -> bool:
    """Check if the code-review-graph CLI is installed on this machine."""
    return shutil.which("code-review-graph") is not None


# One CI job runs one analysis, so a process-wide tally is the analysis total.
_usage_lock = threading.Lock()
_usage_by_model: dict[str, list[int]] = {}


def record_usage(model: str | None, input_tokens, output_tokens, cache_read_tokens=0) -> None:
    """Add one LLM session's token usage to this process's analysis total.

    ``cache_read_tokens`` is the prompt-cache hit subset of ``input_tokens``.
    """
    input_tokens, output_tokens = int(input_tokens or 0), int(output_tokens or 0)
    if not input_tokens and not output_tokens:
        return
    cache_read_tokens = min(max(int(cache_read_tokens or 0), 0), input_tokens)
    with _usage_lock:
        totals = _usage_by_model.setdefault(model or "unknown", [0, 0, 0])
        totals[0] += input_tokens
        totals[1] += output_tokens
        totals[2] += cache_read_tokens


def usage_summary() -> dict | None:
    """Arnold callback payload for the tokens recorded so far, or ``None`` when nothing ran."""
    with _usage_lock:
        models = [
            {"model": model, "inputTokens": totals[0], "outputTokens": totals[1], "cacheReadTokens": totals[2]}
            for model, totals in sorted(_usage_by_model.items())
        ]
    if not models:
        return None
    summary = {
        "inputTokens": sum(item["inputTokens"] for item in models),
        "outputTokens": sum(item["outputTokens"] for item in models),
        "cacheReadTokens": sum(item["cacheReadTokens"] for item in models),
        "models": models,
    }
    if job_url := _job_url():
        summary["jobUrl"] = job_url
    return summary


def _job_url() -> str | None:
    """CI page of the job running this process: GitLab CI, GitHub Actions, or none for local workers."""
    if os.environ.get("CI_JOB_URL"):
        return os.environ["CI_JOB_URL"]
    server, repo, run = (os.environ.get(name) for name in ("GITHUB_SERVER_URL", "GITHUB_REPOSITORY", "GITHUB_RUN_ID"))
    return f"{server}/{repo}/actions/runs/{run}" if server and repo and run else None
