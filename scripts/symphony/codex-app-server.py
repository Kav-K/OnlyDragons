#!/usr/bin/env python3
"""Bridge Symphony's line-delimited app-server protocol into one issue-local Codex child.

Reviewed MCP configuration comes from the operator checkout; project/tool paths bind
to the issue clone. Only workspaceWrite turn roots are extended, and only for that
clone's Git/skill metadata and the shared test-coordination directory. Other policy
messages pass through unchanged. This is not a general permission-escalation proxy.

Linux signals/EOF stop the owned process group; stdout remains protocol-only and
diagnostics avoid request bodies, response bodies and credential values.
"""
import copy
import json
import os
from pathlib import Path
import select
import signal
import subprocess
import sys
import threading
import tomllib


def toml_value(value):
    """Encode the small JSON-compatible config subset for Codex -c arguments."""
    if isinstance(value, dict):
        return '{' + ', '.join(json.dumps(key) + ' = ' + toml_value(item) for key, item in value.items()) + '}'
    if isinstance(value, list):
        return '[' + ', '.join(toml_value(item) for item in value) + ']'
    if isinstance(value, (str, bool, int, float)):
        return json.dumps(value)
    raise ValueError('Unsupported MCP configuration value.')


def codex_command(workspace, source):
    # Use the operator's reviewed tool configuration, not an issue branch's
    # arbitrary MCP commands or its local trust state. Paths bind to this clone.
    """Construct the child command from operator-reviewed MCP settings with issue-local paths.

    Bind Serena navigation to this clone and explicitly pass its shared runtime path.
    Disable remote-plugin syncing only for this child to avoid shared mutable-cache
    races. Do not read arbitrary MCP commands or trust settings from the issue branch.
    """
    source = Path(source).resolve(strict=True)
    with (source / '.codex/config.toml').open('rb') as config_file:
        servers = tomllib.load(config_file)['mcp_servers']
    servers['serena']['args'] = [
        str(source / 'scripts/agent-tools/serena-launch.mjs'),
        '--project', str(workspace),
    ]
    servers['serena']['cwd'] = str(workspace)
    # Codex forwards a small environment to stdio MCP servers. Explicitly carry
    # the shared installation path, without forwarding tracker credentials.
    servers['serena']['env'] = dict(
        servers['serena'].get('env', {}),
        ONLYDRAGONS_AGENT_RUNTIME=str(source / '.symphony/runtime'),
    )
    return [
        # Concurrent workers share CODEX_HOME. Optional remote-plugin syncing
        # races over its mutable cache; the reviewed MCP servers and repository
        # skills are supplied independently. Apply this only to this child.
        'codex', '--disable', 'remote_plugin',
        '--config', 'shell_environment_policy.inherit=all',
        '--config', 'mcp_servers=' + toml_value(servers), 'app-server',
    ]


def validate_workspace(workspace, source):
    """Resolve and require a descendant issue checkout with real local Git and skill directories.

    The workspace root itself is not a worker. Return its canonical path; subsequent
    turn validation rechecks metadata anchors against replacement/redirection.
    """
    workspace = Path(workspace).resolve(strict=True)
    expected = (Path(source).resolve(strict=True) / '.symphony/workspaces').resolve(strict=True)
    if workspace == expected or not workspace.is_relative_to(expected):
        raise ValueError('Worker is outside the issue workspace root.')
    git_dir = workspace / '.git'
    if git_dir.is_symlink() or not git_dir.is_dir() or git_dir.resolve(strict=True).parent != workspace:
        raise ValueError('Worker requires a real local .git directory.')
    validate_agents(workspace)
    return workspace


def validate_agents(workspace):
    # Codex protects .agents even inside workspaceWrite. Grant the directory
    # itself: a nested .agents/skills root leaves a read-only ancestor in bwrap.
    # Keep the original anchor so replacing the checkout between turns cannot
    # redirect this grant to another clone. Missing skills are an invalid worker
    # preparation, not permission to create or follow a replacement directory.
    """Require the original absolute checkout's real .agents directory and return its path.

    The sandbox grant must cover .agents itself because a nested-only skill grant leaves
    a protected read-only ancestor. Missing or symlinked skill metadata is an error, not
    an instruction to create or follow a replacement.
    """
    workspace = Path(workspace)
    agents_dir = workspace / '.agents'
    if (not workspace.is_absolute() or workspace.resolve(strict=True) != workspace
            or agents_dir.is_symlink() or not agents_dir.is_dir()
            or agents_dir.resolve(strict=True) != agents_dir):
        raise ValueError('Worker requires a real local .agents directory.')
    return agents_dir


def validate_coordination(source):
    """Require the operator's real shared Paper lease directory without resolving a symlink grant."""
    expected = Path(source).resolve(strict=True) / '.symphony/test-coordination'
    if expected.is_symlink() or not expected.is_dir() or expected.resolve(strict=True) != expected:
        raise ValueError('Invalid shared Paper-test coordination directory.')
    return expected


def transform_message(message, workspace, coordination=None):
    """Copy only eligible workspaceWrite turn messages that need the three reviewed writable roots.

    Revalidate cwd/Git/skill/coordination anchors. Preserve all existing roots and other
    policy fields; non-turn, other-policy and already-complete messages retain object
    identity and original wire bytes. Invalid eligible inputs raise ValueError.
    """
    if not isinstance(message, dict) or message.get('method') != 'turn/start':
        return message
    params = message.get('params')
    if not isinstance(params, dict):
        return message
    policy = params.get('sandboxPolicy')
    if not isinstance(policy, dict) or policy.get('type') != 'workspaceWrite':
        return message
    workspace = Path(workspace)
    agents_dir = validate_agents(workspace)
    requested_cwd = params.get('cwd', str(workspace))
    if not isinstance(requested_cwd, str) or not Path(requested_cwd).is_absolute() or Path(requested_cwd).resolve(strict=True) != workspace:
        raise ValueError('Turn cwd does not match the issue workspace.')
    git_dir = workspace / '.git'
    if git_dir.is_symlink() or not git_dir.is_dir() or git_dir.resolve(strict=True).parent != workspace:
        raise ValueError('Worker Git metadata escaped its issue workspace.')
    roots = policy.get('writableRoots', [])
    if not isinstance(roots, list) or not all(isinstance(root, str) for root in roots):
        raise ValueError('Invalid writable roots in turn policy.')
    additions = [str(git_dir), str(agents_dir)]
    if coordination is not None:
        coordination = Path(coordination)
        if coordination.is_symlink() or coordination.resolve(strict=True) != coordination:
            raise ValueError('Shared Paper-test coordination directory changed.')
        additions.append(str(coordination))
    missing = [root for root in additions if root not in roots]
    if not missing:
        return message
    updated = copy.deepcopy(message)
    updated['params']['sandboxPolicy']['writableRoots'] = [*roots, *missing]
    return updated


def main():
    """Validate ownership, proxy protocol lines and reap the single child process group on exit.

    A reader thread forwards stdout while the main thread transforms incoming lines.
    EOF and INT/TERM/HUP request shutdown; the finally path sends group TERM, waits five
    seconds, then KILL if needed. Return the child failure unless stopping was requested.
    """
    source = os.environ.get('ONLYDRAGONS_SOURCE')
    if not source:
        raise ValueError('Start workers through Symphony.cmd.')
    workspace = validate_workspace(Path.cwd(), source)
    coordination = validate_coordination(source)
    stopping = threading.Event()
    for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(signum, lambda *_: stopping.set())
    child = subprocess.Popen(
        codex_command(workspace, source),
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=None,
        cwd=workspace, start_new_session=True,
    )

    def copy_stdout():
        """Forward child protocol bytes unchanged; broken output requests coordinated shutdown."""
        try:
            while chunk := child.stdout.read1(65536):
                sys.stdout.buffer.write(chunk)
                sys.stdout.buffer.flush()
        except (BrokenPipeError, OSError):
            stopping.set()

    def send_line(line):
        """Forward malformed JSON unchanged or narrowly transform a parsed turn, then flush."""
        try:
            original = json.loads(line)
        except (ValueError, UnicodeDecodeError):
            child.stdin.write(line)
        else:
            transformed = transform_message(original, workspace, coordination)
            child.stdin.write(line if transformed is original else json.dumps(transformed).encode() + b'\n')
        child.stdin.flush()

    output = threading.Thread(target=copy_stdout, daemon=True)
    output.start()
    pending = b''
    try:
        while child.poll() is None and not stopping.is_set():
            if not select.select([sys.stdin.buffer], [], [], 0.2)[0]:
                continue
            chunk = os.read(sys.stdin.fileno(), 65536)
            if not chunk:
                if pending:
                    send_line(pending + b'\n')
                stopping.set()
                break
            pending += chunk
            while b'\n' in pending:
                line, pending = pending.split(b'\n', 1)
                send_line(line + b'\n')
    finally:
        if child.poll() is None:
            try:
                os.killpg(child.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait()
        output.join(timeout=1)
    return child.returncode if child.returncode and not stopping.is_set() else 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError) as error:
        # Do not include protocol messages, response bodies, or environment values.
        print(f'Codex bridge stopped ({type(error).__name__}).', file=sys.stderr)
        sys.exit(1)
