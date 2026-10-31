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

"""Centralized configuration for Clio.

Provides a single source of truth for environment-based configuration
that is reused across flows and utilities.
"""

from __future__ import annotations

import os

VALID_HARNESS_LABELS = ("copilot", "opencode", "codex")


def get_harness_label() -> str:
    """Return the active harness label (e.g. 'copilot' or 'opencode')."""
    return os.getenv("CLIO_HARNESS", "copilot").lower()


def get_harness_name() -> str:
    """Return the human-friendly active harness name (e.g. 'Copilot SDK' or 'OpenCode')."""
    harness = get_harness_label()
    if harness == "opencode":
        return "OpenCode"
    return "Copilot SDK"


def get_ai_bot_user_id() -> int:
    """Return the SCM user ID of the bot account that posts inline suggestions (0 when not configured)."""
    raw = os.getenv("CLIO_AI_BOT_USER_ID", "0")
    try:
        return int(raw)
    except (ValueError, TypeError):
        return 0


def _selected_model() -> str | None:
    """Return the harness-appropriate model injected by Arnold, if any."""
    if get_harness_label() == "opencode":
        return os.getenv("CLIO_OPENCODE_MODEL")
    return os.getenv("CLIO_LLM_MODEL")


def get_review_model() -> str:
    """Return the model name used for high-level review/description generation."""
    return os.getenv("CLIO_REVIEW_MODEL", "") or _selected_model() or "gpt-6.1-sol"


def get_analysis_model() -> str:
    """Return the model name used for previous-comment analysis."""
    return os.getenv("CLIO_ANALYSIS_MODEL", "") or _selected_model() or "gpt-6.1-sol"
