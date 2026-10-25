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

"""Shared helpers across harness modules.

These utilities are used by multiple harness backends (copilot, opencode)
and should stay lightweight to avoid circular imports.
"""

from __future__ import annotations

import shutil


def crg_available() -> bool:
    """Check if the code-review-graph CLI is installed on this machine."""
    return shutil.which("code-review-graph") is not None
