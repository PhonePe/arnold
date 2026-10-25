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

"""Shared comment-classification helpers for MR discussion processing.

Previously duplicated (with drifting behavior) across
``flows/code_reviews/common/nodes.py::FetchMRReviewStatus`` and
``flows/code_reviews/impact/nodes.py::FetchAllReviewComments``. Consolidated
here so bot/non-actionable detection stays consistent and any future fix only
needs to be made in one place.
"""

import re

# Short replies and acknowledgments that are not actionable review feedback.
NON_ACTIONABLE_PATTERNS = frozenset({
    "done", "fixed", "ok", "okay", "sure", "thanks", "thank you",
    "will do", "noted", "ack", "acknowledged", "agreed", "yes",
    "lgtm", "looks good", "looks good to me", "lg", "ship it",
    "nit", "+1", "-1", "resolved", "addressed", "updated",
    "makes sense", "got it", "right", "correct", "good point",
    "good catch", "nice", "great", "awesome", "perfect",
})

# Bot / workflow status headers to skip when collecting review comments.
# Union of the patterns previously tracked separately in each node.
BOT_SKIP_PATTERNS = (
    "## Agentic PR Suggestions Triggered",
    "## ✅ Agentic PR Suggestions Completed",
    "## Agentic PR Review Triggered",
    "## Agentic PR Description Triggered",
    "## Agentic SonarQube Fix Triggered",
    "## Impact Analysis Triggered",
    "## PR Reviewer Guide",
    "## Title\n",
    "## Code Review Impact Analysis",
    "Impact Analysis Failed",
    "Agentic PR Suggestions Failed",
    "Agentic PR Review Failed",
    "Agentic PR Description Failed",
)

# Full modern Unicode emoji ranges used to detect emoji-only comments.
_EMOJI_PATTERN = re.compile(
    r"[\U0001F600-\U0001F64F\U0001F300-\U0001F5FF"
    r"\U0001F680-\U0001F6FF\U0001F1E0-\U0001F1FF"
    r"\U00002702-\U000027B0\U000024C2-\U0001F251"
    r"\U0001F900-\U0001F9FF\U0001FA00-\U0001FA6F"
    r"\U0001FA70-\U0001FAFF\U00002600-\U000026FF"
    r"\U0000FE00-\U0000FE0F\U0000200D\U00002640\U00002642]+"
)


def is_bot_comment(body: str) -> bool:
    """Check if a comment is a bot acknowledgment/status message to skip.

    Matches only at the start of the comment or on a standalone line so that
    human comments quoting/referencing bot output aren't falsely skipped.
    """
    stripped = body.lstrip()
    return any(
        stripped.startswith(p) or f"\n{p}" in body
        for p in BOT_SKIP_PATTERNS
    )


def is_non_actionable(body: str) -> bool:
    """Check if a comment is a short non-actionable reply.

    Filters out acknowledgments, one-word replies, emoji-only messages,
    and other responses that don't contain code review feedback.
    """
    cleaned = re.sub(r"[*_~`#>]", "", body).strip()

    # Emoji-only comments (e.g. "👍", "✅", "🎉")
    stripped_emoji = _EMOJI_PATTERN.sub("", cleaned).strip()
    if not stripped_emoji:
        return True

    lower = cleaned.lower().rstrip(".!?")
    if lower in NON_ACTIONABLE_PATTERNS:
        return True

    if cleaned in ("+1", "-1", ":+1:", ":-1:", ":thumbsup:", ":thumbsdown:"):
        return True

    return False
