#!/usr/bin/env bash
set -euo pipefail
: "${ONLYDRAGONS_SOURCE:?Start through Symphony.cmd}"
workspace=$(pwd -P)
expected_root=$(realpath -m "$ONLYDRAGONS_SOURCE/.symphony/workspaces")
case "$workspace" in "$expected_root"/*) ;; *) echo 'Refusing to archive outside the issue workspace root.' >&2; exit 1;; esac
[[ -d .git ]] || exit 0
result_root="$ONLYDRAGONS_SOURCE/.symphony/results/$(basename "$workspace")/$(date -u +%Y%m%dT%H%M%S)-$$"
mkdir -p "$result_root"
git bundle create "$result_root/repository.bundle" --all
git diff --binary HEAD > "$result_root/uncommitted.patch"
git status --short > "$result_root/status.txt"
git ls-files --others --exclude-standard -z | tar --null --verbatim-files-from -T - -czf "$result_root/untracked.tar.gz"
printf 'Workspace archived at %s\n' "$result_root"
