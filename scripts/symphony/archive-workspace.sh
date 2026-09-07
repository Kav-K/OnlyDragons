#!/usr/bin/env bash
# Symphony before_remove hook. The caller must stop the issue worker first.
# Retention renames the complete clone, including ignored evidence, on the same
# filesystem. Hook failure is not proof of preservation: the pinned upstream
# cleanup behavior is documented in retain-workspace.py and must be considered.
set -euo pipefail
: "${ONLYDRAGONS_SOURCE:?Start through Symphony.cmd}"
# Preserve ignored runtime evidence and avoid recursive Windows-filesystem I/O.
exec python3 "$ONLYDRAGONS_SOURCE/scripts/symphony/retain-workspace.py" \
  --source "$ONLYDRAGONS_SOURCE" --workspace "$PWD"
