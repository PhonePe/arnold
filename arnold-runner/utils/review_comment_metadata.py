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

"""Build and parse invisible Clio metadata stored in GitLab comments."""

from __future__ import annotations

import json
import re

from utils.config import VALID_HARNESS_LABELS

REVIEW_COMMENT_CATEGORIES = {
    "security",
    "critical bug",
    "possible bug",
    "possible issue",
    "performance",
    "best practice",
    "maintainability",
    "enhancement",
    "typo",
    "rfc_violation",
    "general",
}

_METADATA_MARKER_RE = re.compile(
    r"<!--\s*clio-meta:(.*?)-->",
    re.DOTALL,
)
_LEGACY_HARNESS_MARKER_RE = re.compile(
    r"<!--\s*clio:source-harness=([a-zA-Z0-9._-]+)\s*-->",
)
_MAX_MODEL_LENGTH = 200


def normalize_source_harness(value) -> str | None:
    """Return a supported lowercase harness label, or ``None``."""
    normalized = str(value or "").strip().lower()
    return normalized if normalized in VALID_HARNESS_LABELS else None


def normalize_source_model(value) -> str | None:
    """Return a safe model identifier suitable for an HTML comment."""
    normalized = str(value or "").strip()
    if (
        not normalized
        or len(normalized) > _MAX_MODEL_LENGTH
        or "\n" in normalized
        or "\r" in normalized
        or "--" in normalized
    ):
        return None
    return normalized


def build_review_comment_marker(
    source_harness,
    source_model,
) -> str:
    """Build a compact metadata marker for a GitLab note body."""
    harness = normalize_source_harness(source_harness)
    if not harness:
        return ""

    payload = {
        "source_harness": harness,
        "source_model": normalize_source_model(source_model),
    }
    encoded = json.dumps(
        payload,
        ensure_ascii=True,
        separators=(",", ":"),
    )
    return f"<!-- clio-meta:{encoded} -->"


def parse_review_comment_metadata(body) -> tuple[str, dict]:
    """Return the visible body and parsed Clio provenance.

    JSON metadata markers take precedence. Legacy harness-only markers remain
    supported so impact analysis can attribute existing comments.
    """
    original_body = str(body or "")
    provenance = {
        "source_harness": None,
        "source_model": None,
    }

    marker_match = _METADATA_MARKER_RE.search(original_body)
    if marker_match:
        try:
            payload = json.loads(marker_match.group(1).strip())
        except (json.JSONDecodeError, TypeError, ValueError):
            payload = {}
        if isinstance(payload, dict):
            provenance["source_harness"] = normalize_source_harness(
                payload.get("source_harness")
            )
            provenance["source_model"] = normalize_source_model(
                payload.get("source_model")
            )

    if not provenance["source_harness"]:
        legacy_match = _LEGACY_HARNESS_MARKER_RE.search(original_body)
        if legacy_match:
            provenance["source_harness"] = normalize_source_harness(
                legacy_match.group(1)
            )

    visible_body = _METADATA_MARKER_RE.sub("", original_body)
    visible_body = _LEGACY_HARNESS_MARKER_RE.sub("", visible_body)
    return visible_body.rstrip(), provenance
