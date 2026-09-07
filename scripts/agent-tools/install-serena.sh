#!/usr/bin/env bash
# Install only the Linux Serena/JDTLS runtime. Does not start Minecraft or alter global tools.
# Runtime root precedence: positional argument, ONLYDRAGONS_AGENT_RUNTIME, then
# this checkout's .symphony/runtime. Requires existing supported system Python;
# UV_PYTHON_DOWNLOADS=never prevents an implicit interpreter installation. Direct
# artifacts are hash-pinned; uv resolves the Serena wheel's dependencies separately.
# Extract only the language-server/lombok VSIX subtrees and leave project settings
# to serena-launch.mjs. This installer does not authenticate or start an MCP client.
set -euo pipefail
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
runtime_root=$(realpath -m "${1:-${ONLYDRAGONS_AGENT_RUNTIME:-$project_root/.symphony/runtime}}")
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || { echo 'This pinned installer supports Linux x86_64.' >&2; exit 1; }
for tool in curl tar sha256sum python3; do command -v "$tool" >/dev/null || { echo "Missing prerequisite: $tool" >&2; exit 1; }; done
python3 -c 'import sys; assert (3,11) <= sys.version_info[:2] < (3,15), "Python 3.11-3.14 required"'
mkdir -p "$runtime_root/agent-downloads" "$runtime_root/uv-bin"
downloads="$runtime_root/agent-downloads"
export UV_CACHE_DIR="$runtime_root/uv-cache"
export UV_PYTHON_DOWNLOADS=never

# Arguments: source URL, cache destination, SHA-256. Reuse verified cache bytes or
# download to .part and rename after validation; no unverified file is published.
fetch_verified() {
  local url=$1 output=$2 digest=$3
  if [[ -f "$output" ]] && printf '%s  %s\n' "$digest" "$output" | sha256sum -c --status; then return; fi
  curl --fail --location --retry 3 --connect-timeout 20 --output "$output.part" "$url"
  printf '%s  %s\n' "$digest" "$output.part" | sha256sum -c --status || { echo "Checksum mismatch: $output" >&2; exit 1; }
  mv -- "$output.part" "$output"
}

# SHA256 digests come from the named GitHub release assets and PyPI 1.7.0 metadata.
fetch_verified 'https://github.com/astral-sh/uv/releases/download/0.12.10/uv-x86_64-unknown-linux-gnu.tar.gz' \
  "$downloads/uv-0.12.10-linux-x64.tar.gz" '173d95a0c32d18c896c46ba6fafbf3cf9c14ab74b033f81b76c883ef492a976b'
tar -xzf "$downloads/uv-0.12.10-linux-x64.tar.gz" -C "$runtime_root/uv-bin" --strip-components=1 uv-x86_64-unknown-linux-gnu/uv
uv="$runtime_root/uv-bin/uv"
fetch_verified 'https://files.pythonhosted.org/packages/95/c1/edd38220ce54fe37999d5b4a0790e6cfcc3557f71a8f47cf850fc079769b/serena_agent-1.7.0-py3-none-any.whl' \
  "$downloads/serena_agent-1.7.0-py3-none-any.whl" '6dbf1459670d96fb0595f84932adef34260a6fe14ba5135b901fdb3c8c76e891'
if [[ ! -x "$runtime_root/serena-venv/bin/python" ]]; then
  "$uv" venv --python "$(command -v python3)" "$runtime_root/serena-venv"
fi
"$uv" pip install --python "$runtime_root/serena-venv/bin/python" "$downloads/serena_agent-1.7.0-py3-none-any.whl"

fetch_verified 'https://github.com/redhat-developer/vscode-java/releases/download/v1.56.0/java-linux-x64-1.56.0-1066.vsix' \
  "$downloads/java-linux-x64-1.56.0-1066.vsix" '3365249637a81705690ea7e4dc4c8533bfad36b65e754ebb1c839524e208ad41'
# The authenticated VSIX is data: filter the two required subtrees and reject
# traversal before writing into the designated runtime language-server directory.
python3 - "$downloads/java-linux-x64-1.56.0-1066.vsix" "$runtime_root/jdtls-vscode-java-1.56.0" <<'PY'
import pathlib, shutil, sys, zipfile
target = pathlib.Path(sys.argv[2])
with zipfile.ZipFile(sys.argv[1]) as archive:
    for item in archive.infolist():
        relative = pathlib.PurePosixPath(item.filename)
        if not (item.filename.startswith('extension/server/') or item.filename.startswith('extension/lombok/')):
            continue
        if '..' in relative.parts or relative.is_absolute():
            raise ValueError('Invalid archive member')
        output = target.joinpath(*relative.parts[1:])
        if item.is_dir():
            output.mkdir(parents=True, exist_ok=True)
        else:
            output.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(item) as source, output.open('wb') as destination:
                shutil.copyfileobj(source, destination)
PY
"$runtime_root/serena-venv/bin/python" -c 'import importlib.metadata; assert importlib.metadata.version("serena-agent") == "1.7.0"; print("Installed Serena 1.7.0 and vscode-java 1.56.0 (Eclipse JDT LS core 1.61.0; verified downloads).")'
