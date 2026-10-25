"""Shared helpers across harness modules.

These utilities are used by multiple harness backends (copilot, opencode)
and should stay lightweight to avoid circular imports.
"""

from __future__ import annotations

import shutil


def crg_available() -> bool:
    """Check if the code-review-graph CLI is installed on this machine."""
    return shutil.which("code-review-graph") is not None
