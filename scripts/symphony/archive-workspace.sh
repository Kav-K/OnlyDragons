#!/usr/bin/env bash
set -euo pipefail
: "${ONLYDRAGONS_SOURCE:?Start through Symphony.cmd}"
# Preserve ignored runtime evidence and avoid recursive Windows-filesystem I/O.
exec python3 "$ONLYDRAGONS_SOURCE/scripts/symphony/retain-workspace.py" \
  --source "$ONLYDRAGONS_SOURCE" --workspace "$PWD"
