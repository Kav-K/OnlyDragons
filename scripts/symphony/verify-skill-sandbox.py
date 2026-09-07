#!/usr/bin/env python3
"""Explicit no-model Codex 0.153.4 smoke: scoped skill writes and an ordinary merge.

Requires an operator-selected installed Codex binary and scratch root. Creates
only disposable local repositories and CODEX_HOME; uses no auth, MCP or model.
This is deliberately separate from unit discovery: CI without Codex did not run it.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import select
import signal
import subprocess
import sys
import tempfile
import time


def require(condition, message):
    """Raise RuntimeError when a scoped sandbox observation differs from the reviewed contract."""
    if not condition:
        raise RuntimeError(message)


def git(cwd, *args):
    """Run bounded Git against the explicitly owned fixture repository and return trimmed text."""
    return subprocess.check_output(['git', *args], cwd=cwd, stderr=subprocess.PIPE, timeout=15).decode().strip()


def configure_author(repo):
    """Set deterministic local-only author, line-ending and signing settings in a scratch clone."""
    git(repo, 'config', 'user.name', 'OnlyDragons Sandbox Fixture')
    git(repo, 'config', 'user.email', 'sandbox-fixture@example.invalid')
    git(repo, 'config', 'core.autocrlf', 'false')
    git(repo, 'config', 'commit.gpgsign', 'false')


class AppServer:
    """Own a no-model app-server subprocess and a sequential line-delimited RPC stream.

    The caller supplies disposable workspace/CODEX_HOME and must call close. Remote
    plugins and MCP servers are disabled so this probe tests sandbox behavior directly.
    """
    def __init__(self, binary, workspace, codex_home):
        """Start the supplied binary in an owned session with isolated config and piped protocol I/O."""
        self.process = subprocess.Popen(
            [str(binary), '--disable', 'remote_plugin', '--config', 'mcp_servers={}', 'app-server'],
            cwd=workspace, env=dict(os.environ, CODEX_HOME=str(codex_home)),
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            start_new_session=True,
        )
        self.pending = b''
        self.identifier = 0

    def notify(self, method, params):
        """Send and flush a protocol notification that does not expect a response ID."""
        self.process.stdin.write(json.dumps({'method': method, 'params': params}).encode() + b'\n')
        self.process.stdin.flush()

    def rpc(self, method, params):
        """Send one incrementing request and wait at most 30 seconds for its exact response ID.

        Retain partial lines between reads; unrelated notifications do not satisfy the call.
        """
        self.identifier += 1
        self.process.stdin.write(json.dumps({'id': self.identifier, 'method': method, 'params': params}).encode() + b'\n')
        self.process.stdin.flush()
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            while b'\n' in self.pending:
                line, self.pending = self.pending.split(b'\n', 1)
                item = json.loads(line)
                if item.get('id') == self.identifier:
                    return item
            if not select.select([self.process.stdout], [], [], max(0, deadline - time.monotonic()))[0]:
                break
            chunk = os.read(self.process.stdout.fileno(), 65536)
            require(chunk, 'App server exited before responding to ' + method)
            self.pending += chunk
        raise TimeoutError(method)

    def close(self):
        """Close input and reap only this app-server group, escalating after bounded waits."""
        self.process.stdin.close()
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(self.process.pid, signal.SIGTERM)
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(self.process.pid, signal.SIGKILL)
                self.process.wait(timeout=5)
        finally:
            self.process.stdout.close()


def run(binary, base):
    """Exercise the pinned Linux sandbox with disposable Git/skill/sibling control files.

    Require scratch outside default writable temporary roots so denied sibling writes
    remain meaningful. Compare baseline denials, narrow reviewed grants, an ordinary
    two-parent skill merge and the known nested-only grant failure. Verify protected
    files survive, cleanly reap the child and remove owned scratch. Return observations;
    this explicit smoke is not run by ordinary unit discovery or a model turn.
    """
    require(sys.platform == 'linux', 'This smoke requires Linux/WSL sandbox facilities')
    temporary_roots = [Path('/tmp').resolve()]
    if os.environ.get('TMPDIR'):
        temporary_roots.append(Path(os.environ['TMPDIR']).resolve())
    require(not any(base.is_relative_to(path) for path in temporary_roots),
            'Choose a scratch root outside /tmp and TMPDIR: default temporary writes invalidate the sibling control')
    version = subprocess.check_output([str(binary), '--version'], timeout=10).decode().strip()
    require(version == 'codex-cli 0.153.4', 'Re-review the sandbox contract before using ' + version)
    spec = importlib.util.spec_from_file_location('codex_bridge', Path(__file__).with_name('codex-app-server.py'))
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    with tempfile.TemporaryDirectory(prefix='onlydragons-skill-sandbox-', dir=base) as temporary:
        root = Path(temporary)
        upstream = root / 'upstream'
        upstream.mkdir()
        git(upstream, 'init', '-b', 'main')
        configure_author(upstream)
        skill_relative = '.agents/skills/sandbox-fixture/SKILL.md'
        (upstream / skill_relative).parent.mkdir(parents=True)
        (upstream / skill_relative).write_text('original skill\n')
        (upstream / '.codex').mkdir()
        (upstream / '.codex/config.toml').write_text('# protected operator/tool configuration\n')
        git(upstream, 'add', '.')
        git(upstream, 'commit', '-m', 'Create disposable skill fixture')
        workspace = root / 'worker'
        git(root, 'clone', '--no-hardlinks', str(upstream), str(workspace))
        configure_author(workspace)
        git(workspace, 'switch', '-c', 'worker')
        (workspace / 'worker.txt').write_text('retained worker change\n')
        git(workspace, 'add', 'worker.txt')
        git(workspace, 'commit', '-m', 'Retain unrelated worker change')
        worker_head = git(workspace, 'rev-parse', 'HEAD')
        (upstream / skill_relative).write_text('upstream main skill update\n')
        git(upstream, 'add', skill_relative)
        git(upstream, 'commit', '-m', 'Update shared skill on main')
        incoming = git(upstream, 'rev-parse', 'HEAD')
        git(workspace, 'fetch', '--no-tags', 'origin', 'main')
        sibling = root / 'unrelated'
        sibling.mkdir()
        sibling_marker = sibling / 'marker'
        sibling_marker.write_text('protected sibling\n')
        home = root / 'codex-home'
        home.mkdir()
        coordination = root / 'test-coordination'
        coordination.mkdir()
        turn = {'method': 'turn/start', 'params': {
            'cwd': str(workspace), 'sandboxPolicy': {'type': 'workspaceWrite', 'networkAccess': False},
        }}
        policy = bridge.transform_message(turn, workspace, coordination)['params']['sandboxPolicy']
        require(policy['writableRoots'] == [str(workspace / '.git'), str(workspace / '.agents'), str(coordination)],
                'Bridge added an unexpected root')
        controls = {
            'skill': str(workspace / skill_relative),
            'codex': str(workspace / '.codex/config.toml'),
            'sibling': str(sibling_marker),
        }
        code = (
            'import json,sys\nfrom pathlib import Path\nout={}\n'
            'for label,target in json.loads(sys.argv[1]).items():\n'
            ' try:\n  p=Path(target);p.write_bytes(p.read_bytes());out[label]="writable"\n'
            ' except OSError as error: out[label]=error.errno\n'
            'print(json.dumps(out))\n'
        )
        command = [sys.executable, '-c', code, json.dumps(controls)]
        app = AppServer(binary, workspace, home)
        try:
            response = app.rpc('initialize', {'clientInfo': {'name': 'onlydragons-skill-sandbox', 'version': '1.0'},
                                              'capabilities': {'experimentalApi': True}})
            require('result' in response, 'App-server initialization failed')
            app.notify('initialized', {})

            def execute(command, requested_policy):
                """Request a ten-second command execution under the exact supplied sandbox policy."""
                return app.rpc('command/exec', {'command': command, 'cwd': str(workspace),
                                                'sandboxPolicy': requested_policy, 'timeoutMs': 10000})

            before = execute(command, dict(policy, writableRoots=[str(workspace / '.git')]))
            require(before.get('result', {}).get('exitCode') == 0, 'Baseline command did not execute')
            require(json.loads(before['result']['stdout']) == {'skill': 30, 'codex': 30, 'sibling': 30},
                    'Baseline must establish all three writes are denied with EROFS')
            after = execute(command, policy)
            require(after.get('result', {}).get('exitCode') == 0, 'Scoped command did not execute')
            require(json.loads(after['result']['stdout']) == {'skill': 'writable', 'codex': 30, 'sibling': 30},
                    'Scoped grant widened beyond worker skills or did not allow the skill')
            merged = execute(['git', '-c', 'core.hooksPath=/dev/null', 'merge', '--no-ff', '--no-edit', incoming], policy)
            require(merged.get('result', {}).get('exitCode') == 0, 'Ordinary shared-skill merge failed')
            require(git(workspace, 'show', '-s', '--format=%P', 'HEAD').split() == [worker_head, incoming],
                    'Expected an ordinary two-parent merge preserving the worker commit')
            require((workspace / skill_relative).read_text() == 'upstream main skill update\n'
                    and (workspace / 'worker.txt').read_text() == 'retained worker change\n',
                    'Merged skill or unrelated worker change was lost')
            require(not git(workspace, 'status', '--porcelain'), 'Merge left an unclean fixture checkout')
            require((workspace / '.codex/config.toml').read_text() == '# protected operator/tool configuration\n'
                    and sibling_marker.read_text() == 'protected sibling\n', 'Protected control changed')
            narrow = execute(command, dict(policy, writableRoots=[str(workspace / '.git'), str(workspace / '.agents/skills')]))
            detail = json.dumps(narrow)
            require(('error' in narrow or narrow.get('result', {}).get('exitCode') != 0)
                    and 'bwrap' in detail and 'Read-only file system' in detail,
                    'Nested-only grant no longer has the reviewed read-only ancestor failure; re-review it')
            result = {'status': 'passed', 'codexVersion': version, 'modelCalls': 0,
                      'baseline': {'skill': 'EROFS', 'codex': 'EROFS', 'sibling': 'EROFS'},
                      'scoped': {'skill': 'writable', 'codex': 'EROFS', 'sibling': 'EROFS'},
                      'ordinarySkillMerge': 'passed, both parents and worker content preserved',
                      'nestedOnlyGrant': 'expected bwrap read-only ancestor failure'}
        finally:
            app.close()
        require(app.process.returncode == 0, 'App server did not exit cleanly')
        result['ownedAppServerExitCode'] = app.process.returncode
    result['scratchRemoved'] = not root.exists()
    return result


def main():
    """Parse explicit Codex/scratch paths and print the no-model sandbox observation JSON."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--codex', required=True, type=Path)
    parser.add_argument('--scratch-root', required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.codex.resolve(strict=True), args.scratch_root.resolve(strict=True)), indent=2))


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.SubprocessError) as error:
        print(json.dumps({'status': 'failed', 'error': str(error)}), file=sys.stderr)
        sys.exit(1)
