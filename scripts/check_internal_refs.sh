#!/usr/bin/env bash
# Fails when tracked files reference internal PhonePe systems or hosts.
# --history also scans every commit's message and patch reachable from HEAD.
set -euo pipefail
pattern='olympus|cibot|godric|rosey|artifactory|tinyproxy|docker\.phonepe|phonepe\.(com|nb[0-9]|nm[0-9]|mh[0-9]|nmg|mhg|mhz)\b|\.(nb6|nb1|nm5|mh1)\b|devx/|DEVX-[0-9]'
excludes=(':!scripts/check_internal_refs.sh' ':!**/package-lock.json' ':!**/pnpm-lock.yaml')
allowed='https://www\.phonepe\.com'

if [[ "${1:-}" == "--history" ]]; then
  if git log -p --format='commit %H%n%B' HEAD -- . "${excludes[@]}" \
      | grep -IiE "$pattern" | grep -vE "$allowed"; then
    echo "Internal references found in git history (see above)." >&2
    exit 1
  fi
  echo "No internal references found in history."
  exit 0
fi

if git grep -nIiE "$pattern" -- . "${excludes[@]}" | grep -vE "$allowed"; then
  echo "Internal references found (see above)." >&2
  exit 1
fi
echo "No internal references found."
