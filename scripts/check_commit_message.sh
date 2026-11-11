#!/usr/bin/env bash
# Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Rejects commit messages that credit AI assistants or tools (Co-authored-by trailers, "Generated with" footers).
# The human committing the change is its author.
#
# Usage:
#   scripts/check_commit_message.sh <message-file>        (commit-msg hook)
#   scripts/check_commit_message.sh --range <rev-range>   (CI, e.g. origin/main..HEAD)
set -euo pipefail

tools='copilot|claude|anthropic|openai|chatgpt|codex|cursor|gemini|devin|aider|opencode|windsurf|codeium|cody|tabnine|amazon q'
pattern="^[[:space:]]*co-authored-by:.*(${tools}|\\[bot\\])|generated (with|by) \\[?(${tools})|🤖"

check() {
  grep -v '^#' | grep -inE "$pattern" || return 0
  return 1
}

if [[ ${1:-} == --range ]]; then
  status=0
  for commit in $(git rev-list --no-merges "$2"); do
    if ! output=$(git log -1 --format=%B "$commit" | check); then
      echo "$(git log -1 --format='%h %s' "$commit"):"
      echo "$output"
      status=1
    fi
  done
else
  output=$(check < "$1") && status=0 || { echo "$output"; status=1; }
fi

if [[ $status -ne 0 ]]; then
  echo
  echo "Commit messages must not credit AI assistants or tools (Co-authored-by / 'Generated with' lines)." >&2
  echo "Remove those lines (git commit --amend, or git rebase -i for older commits)." >&2
fi
exit $status
