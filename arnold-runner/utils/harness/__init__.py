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

"""Harness abstraction package for Clio.

Provides a factory to instantiate the correct agentic harness based on
the ``CLIO_HARNESS`` environment variable.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from utils.harness.base import AgenticHarness


def get_harness() -> AgenticHarness:
    """Return the harness instance for the active backend.

    The backend is selected via the ``CLIO_HARNESS`` environment variable:
    - ``copilot`` (default) — GitHub Copilot SDK
    - ``opencode`` — OpenCode CLI via subprocess + MCP tool bridge
    """
    from utils.config import get_harness_label

    harness_name = get_harness_label()

    if harness_name == "copilot":
        from utils.harness.copilot_harness import CopilotHarness
        return CopilotHarness()
    elif harness_name == "opencode":
        from utils.harness.opencode_harness import OpenCodeHarness
        return OpenCodeHarness()
    else:
        raise ValueError(
            f"Unknown harness: {harness_name!r}. "
            f"Set CLIO_HARNESS to 'copilot' or 'opencode'."
        )
