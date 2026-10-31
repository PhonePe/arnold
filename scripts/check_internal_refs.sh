#!/usr/bin/env bash
# Fails when tracked files reference internal PhonePe systems or hosts.
set -euo pipefail
pattern='olympus|cibot|godric|rosey|artifactory|tinyproxy|docker\.phonepe|phonepe\.(com|nb[0-9]|nm[0-9]|mh[0-9]|nmg|mhg|mhz)|\.(nb6|nb1|nm5|mh1)\b|devx/|DEVX-[0-9]'
if git grep -nIiE "$pattern" -- . ':!scripts/check_internal_refs.sh' ':!scripts/oss-history/sync-internal.sh' ':!**/package-lock.json' ':!**/pnpm-lock.yaml' \
    | grep -viE 'com\.phonepe\.commons\.columbus'; then
  echo "Internal references found (see above)." >&2
  exit 1
fi
echo "No internal references found."
