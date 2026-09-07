#!/usr/bin/env bash
# Git-only credential helper: stdin/stdout are the credential protocol, not a log.
# Answer get for HTTPS Kav-K/OnlyDragons only; ignore store/erase and other hosts or
# paths. Read the token file at request time so rotation needs no embedded secret.
# The caller sets credential.useHttpPath; never run this helper with shell tracing
# or capture its successful stdout in public task evidence.
set -euo pipefail
# Git credential protocol: only answer get requests for this repository over HTTPS.
[[ "${1:-}" == get ]] || exit 0
protocol= host= path=
while IFS='=' read -r key value && [[ -n "$key" ]]; do
  case "$key" in protocol) protocol=$value;; host) host=$value;; path) path=$value;; esac
done
[[ "$protocol" == https && "$host" == github.com ]] || exit 0
case "${path,,}" in kav-k/onlydragons|kav-k/onlydragons.git) ;; *) exit 0;; esac
[[ -r "${ONLYDRAGONS_TOKEN_FILE:-}" ]] || exit 0
printf 'username=x-access-token\npassword=%s\n\n' "$(tr -d '\r\n' < "$ONLYDRAGONS_TOKEN_FILE")"
