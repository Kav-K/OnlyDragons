#!/usr/bin/env bash
# Install the pinned Linux x86_64 tools without changing system tools or auth.
set -euo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNTIME_ROOT="$PROJECT_ROOT/.symphony/runtime"
SYMPHONY_VERSION="0.0.2"
SYMPHONY_SHA256="bace377e92b7c244a29da15a88f517c7722ca69e249068395f77ce6912ef39eb"
SYMPHONY_FILE="symphony-v${SYMPHONY_VERSION}-linux_x86_64"
CODEX_VERSION="0.153.4"
CODEX_INTEGRITY="sha512-wbHDmit7S/YvBGVX1DQmk13xtWblZ2cApeJ/pB7xDZ10Cna+DZc5ij7f0F4OxdsXN4FW1oLT48OpogUI1+8Y2w=="
JAVA_VERSION="25.0.4.1"
JAVA_SHA256="dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e"
JAVA_URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
  printf '%s\n' 'Run this installer in Linux x86_64, such as Ubuntu-24.04 in WSL.' >&2
  exit 1
fi
for required_tool in curl sha256sum tar node npm git timeout python3 flock; do
  command -v "$required_tool" >/dev/null || {
    printf 'Required tool is missing: %s\n' "$required_tool" >&2
    exit 1
  }
done
if [[ "$(node -p process.platform)" != linux ]]; then
  printf '%s\n' 'Install Linux Node.js in WSL; the Windows Node.js executable cannot install Linux Codex.' >&2
  exit 1
fi

mkdir -p "$RUNTIME_ROOT" "$RUNTIME_ROOT/burrito"
export XDG_DATA_HOME="$RUNTIME_ROOT/burrito"

download_verified() {
  local source_url="$1" target_file="$2" expected_sha="$3"
  if [[ -f "$target_file" ]] && printf '%s  %s\n' "$expected_sha" "$target_file" | sha256sum --check --status; then
    printf 'Verified cached download: %s\n' "$(basename -- "$target_file")"
    return
  fi
  curl --fail --location --retry 3 --proto '=https' --tlsv1.2 "$source_url" --output "$target_file.download"
  printf '%s  %s\n' "$expected_sha" "$target_file.download" | sha256sum --check
  mv -- "$target_file.download" "$target_file"
}

download_verified \
  "https://github.com/openai/symphony/releases/download/v${SYMPHONY_VERSION}/${SYMPHONY_FILE}" \
  "$RUNTIME_ROOT/$SYMPHONY_FILE" "$SYMPHONY_SHA256"
chmod +x "$RUNTIME_ROOT/$SYMPHONY_FILE"

download_verified "$JAVA_URL" "$RUNTIME_ROOT/temurin25-linux-x64.tar.gz" "$JAVA_SHA256"
if [[ ! -x "$RUNTIME_ROOT/java/bin/java" ]] || ! "$RUNTIME_ROOT/java/bin/java" -version 2>&1 | grep -Fq "\"$JAVA_VERSION\""; then
  if [[ -e "$RUNTIME_ROOT/java" ]]; then
    printf 'An unexpected Java installation already exists at %s. Move it aside before reinstalling.\n' "$RUNTIME_ROOT/java" >&2
    exit 1
  fi
  mkdir -p "$RUNTIME_ROOT/java"
  tar -xzf "$RUNTIME_ROOT/temurin25-linux-x64.tar.gz" --strip-components=1 -C "$RUNTIME_ROOT/java"
fi

# npm checks package integrity while installing. Check the registry metadata
# against the integrity value recorded when this pinned release was verified.
actual_codex_integrity="$(npm --cache "$RUNTIME_ROOT/npm-cache" view "@openai/codex@$CODEX_VERSION" dist.integrity)"
if [[ "$actual_codex_integrity" != "$CODEX_INTEGRITY" ]]; then
  printf '%s\n' 'The pinned Codex package integrity does not match; installation stopped.' >&2
  exit 1
fi
npm install --prefix "$RUNTIME_ROOT" --cache "$RUNTIME_ROOT/npm-cache" \
  --save-exact --no-audit --no-fund --ignore-scripts "@openai/codex@$CODEX_VERSION"

bash "$PROJECT_ROOT/scripts/agent-tools/install-serena.sh" "$RUNTIME_ROOT"

"$RUNTIME_ROOT/java/bin/java" -version
"$RUNTIME_ROOT/node_modules/.bin/codex" --version

# --help deliberately returns usage with exit 1 in Symphony 0.0.2. It does not
# start the tracker, create an agent, or require credentials.
smoke_status=0
timeout 60 "$RUNTIME_ROOT/$SYMPHONY_FILE" --help >"$RUNTIME_ROOT/symphony-smoke.log" 2>&1 || smoke_status=$?
if [[ "$smoke_status" != 1 ]] || ! grep -Fq 'Usage: symphony ' "$RUNTIME_ROOT/symphony-smoke.log"; then
  cat "$RUNTIME_ROOT/symphony-smoke.log" >&2
  printf 'Symphony smoke check failed (exit %s).\n' "$smoke_status" >&2
  exit 1
fi
printf 'Symphony %s verified. Runtime installed at %s\n' "$SYMPHONY_VERSION" "$RUNTIME_ROOT"
