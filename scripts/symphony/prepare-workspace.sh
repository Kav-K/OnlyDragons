#!/usr/bin/env bash
set -euo pipefail
: "${ONLYDRAGONS_SOURCE:?Start through Symphony.cmd}"
workspace=$(pwd -P)
expected_root=$(realpath -m "$ONLYDRAGONS_SOURCE/.symphony/workspaces")
case "$workspace" in "$expected_root"/*) ;; *) echo 'Refusing to prepare outside the issue workspace root.' >&2; exit 1;; esac
git clone --no-tags https://github.com/Kav-K/OnlyDragons.git .
git config user.name 'Kav-K'
git config user.email 'Kav-K@users.noreply.github.com'
git config credential.helper ''
git config --add credential.helper '!bash "$ONLYDRAGONS_SOURCE/scripts/symphony/git-credential.sh"'
git config credential.useHttpPath true
git config push.default current
# Never start the interactive Minecraft lab from unattended workspaces.
test -f gradlew
