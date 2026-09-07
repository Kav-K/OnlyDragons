#!/usr/bin/env bash
# Replace this wrapper with the reviewed app-server bridge in the current issue
# clone. Symphony/run.sh supplies ONLYDRAGONS_SOURCE and the pinned runtime PATH.
# The bridge owns child cleanup; this wrapper does not start a second dispatcher.
set -euo pipefail
# Keep Gradle's mutable cache inside the current issue's writable sandbox.
export GRADLE_USER_HOME="$PWD/.symphony/gradle"
exec python3 "$ONLYDRAGONS_SOURCE/scripts/symphony/codex-app-server.py"
