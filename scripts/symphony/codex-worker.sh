#!/usr/bin/env bash
set -euo pipefail
# Keep Gradle's mutable cache inside the current issue's writable sandbox.
export GRADLE_USER_HOME="$PWD/.symphony/gradle"
exec python3 "$ONLYDRAGONS_SOURCE/scripts/symphony/codex-app-server.py"
