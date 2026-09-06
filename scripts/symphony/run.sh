#!/usr/bin/env bash
set -euo pipefail
ONLYDRAGONS_SOURCE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
export ONLYDRAGONS_SOURCE
state_root="$ONLYDRAGONS_SOURCE/.symphony"
runtime="$state_root/runtime"
mkdir -p "$state_root"
export XDG_DATA_HOME="$runtime/burrito"
export XDG_CACHE_HOME="$state_root/cache"
export ERL_CRASH_DUMP="$state_root/erl_crash.dump"
export CODEX_HOME="$state_root/codex"
export JAVA_HOME="$runtime/java"
export PATH="$runtime/node_modules/.bin:$JAVA_HOME/bin:$PATH"
export ONLYDRAGONS_TOKEN_FILE="$state_root/github-token"
export GIT_TERMINAL_PROMPT=0
binary="$runtime/symphony-v0.0.2-linux_x86_64"
# v0.0.2 precedes the upstream alias-scrubbing fix. Do not pass aliases into it.
unset GITHUB_TOKEN GH_TOKEN GITHUB_API_TOKEN GITHUB_PERSONAL_ACCESS_TOKEN GITHUB_ACCESS_TOKEN GH_ENTERPRISE_TOKEN GITHUB_ENTERPRISE_TOKEN
unset SYMPHONY_GITHUB_TOKEN
if [[ -r "$ONLYDRAGONS_TOKEN_FILE" ]]; then
  SYMPHONY_GITHUB_TOKEN=$(tr -d '\r\n' < "$ONLYDRAGONS_TOKEN_FILE")
  export SYMPHONY_GITHUB_TOKEN
fi
action="${1:-check}"
case "$action" in
  install) exec bash "$ONLYDRAGONS_SOURCE/scripts/symphony/install-runtime.sh";;
  login)
    [[ -x "$runtime/node_modules/.bin/codex" ]] || { echo 'Run Symphony.cmd install first.' >&2; exit 1; }
    if codex login status; then exit 0; fi
    exec codex login --device-auth;;
  status)
    if curl -fsS --max-time 5 http://127.0.0.1:4000/api/v1/state; then printf '\n'; else echo 'Symphony dashboard is not running on localhost:4000.'; exit 1; fi
    exit 0;;
  stop)
    if [[ ! -f "$state_root/symphony.pid" ]]; then echo 'No recorded Symphony process.'; exit 0; fi
    read -r pid < "$state_root/symphony.pid"
    if [[ "$pid" =~ ^[0-9]+$ ]] && [[ -r "/proc/$pid/environ" ]] && grep -zFxq "ONLYDRAGONS_SOURCE=$ONLYDRAGONS_SOURCE" "/proc/$pid/environ" && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -Fq 'symphony'; then
      kill -TERM "$pid"
      echo 'Requested Symphony shutdown.'
    else echo 'Recorded process is absent or is not this Symphony instance; no process was stopped.'; fi
    exit 0;;
  check|start) ;;
  *) echo "Unknown action: $action" >&2; exit 2;;
esac
missing=0
for executable in "$binary" "$runtime/node_modules/.bin/codex" "$JAVA_HOME/bin/java"; do
  if [[ -x "$executable" ]]; then printf 'OK: %s\n' "$(basename "$executable")"; else printf 'MISSING: %s (run Symphony.cmd install)\n' "$executable"; missing=1; fi
done
[[ "$missing" == 0 ]] || exit 1
codex --version
java -version 2>&1 | head -n 1
if node "$ONLYDRAGONS_SOURCE/scripts/agent-tools/serena-launch.mjs" --project "$ONLYDRAGONS_SOURCE" --check >"$state_root/agent-tools-check.json"; then
  echo 'OK: Serena and Java language-server dependencies'
else
  echo 'Run Symphony.cmd install to provision agent tools.'
  missing=1
fi
codex login status || { echo 'Run Symphony.cmd login.'; missing=1; }
python3 "$ONLYDRAGONS_SOURCE/scripts/symphony/check-github.py" || missing=1
[[ "$missing" == 0 ]] || exit 1
[[ "$action" == start ]] || exit 0
# Hold the lock through the entire process lifetime; prevents duplicate dispatchers.
exec 9>"$state_root/symphony.lock"
flock -n 9 || { echo 'OnlyDragons Symphony is already running.' >&2; exit 1; }
printf '%s\n' "$$" > "$state_root/symphony.pid"
cd "$ONLYDRAGONS_SOURCE"
echo 'Dashboard: http://localhost:4000 — stop with Ctrl+C or Symphony.cmd stop.'
exec "$binary" "$ONLYDRAGONS_SOURCE/WORKFLOW.md" --logs-root "$state_root/log" --port 4000 --i-understand-that-this-will-be-running-without-the-usual-guardrails
