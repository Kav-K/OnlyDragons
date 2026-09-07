"""Linux/WSL bridge transformation and owned-child regressions using disposable fixtures.

Run with python3 -m unittest discover -s scripts/symphony/tests -v. The actual Codex
sandbox smoke is separate in verify-skill-sandbox.py; these tests do not invoke a
model or turn a mocked protocol response into sandbox/runtime acceptance.
"""
import importlib.util
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import unittest

BRIDGE_PATH = Path(__file__).resolve().parents[1] / 'codex-app-server.py'
spec = importlib.util.spec_from_file_location('codex_bridge', BRIDGE_PATH)
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


@unittest.skipUnless(os.name == 'posix', 'The Symphony worker runs under Linux/WSL.')
class BridgeTests(unittest.TestCase):
    """Require narrow clone-local grants, unchanged protocol fields and owned bridge cleanup.

    Temporary operator/worker roots model metadata redirection and scope boundaries.
    POSIX-only behavior is explicitly skipped on other platforms.
    """
    def setUp(self):
        """Create test-owned reviewed config, issue metadata, shared lease and a workspaceWrite turn."""
        self.temp = tempfile.TemporaryDirectory(prefix='symphony-bridge-')
        self.addCleanup(self.temp.cleanup)
        self.source = Path(self.temp.name)
        (self.source / '.codex').mkdir()
        (self.source / '.codex/config.toml').write_text(
            (BRIDGE_PATH.parents[2] / '.codex/config.toml').read_text()
        )
        self.workspace = self.source / '.symphony/workspaces/issue'
        (self.workspace / '.git').mkdir(parents=True)
        (self.workspace / '.agents').mkdir()
        self.coordination = self.source / '.symphony/test-coordination'
        self.coordination.mkdir()
        self.turn = {
            'id': 7, 'method': 'turn/start',
            'params': {
                'cwd': str(self.workspace),
                'sandboxPolicy': {'type': 'workspaceWrite', 'networkAccess': True},
                'input': [{'type': 'text', 'text': 'Retain this prompt exactly.'}],
            },
        }

    def test_only_git_and_agents_roots_are_added_without_changing_input(self):
        changed = bridge.transform_message(self.turn, self.workspace)
        expected = json.loads(json.dumps(self.turn))
        expected['params']['sandboxPolicy']['writableRoots'] = [str(self.workspace / '.git'), str(self.workspace / '.agents')]
        self.assertEqual(expected, changed)
        self.assertNotIn('writableRoots', self.turn['params']['sandboxPolicy'])
        self.assertIs(bridge.transform_message(changed, self.workspace), changed)
        for message in ({'method': 'initialize', 'params': {}}, {'id': 7, 'result': {}}, {'method': 'turn/completed'}):
            self.assertIs(bridge.transform_message(message, self.workspace), message)

    def test_workspace_and_turn_cwd_cannot_escape(self):
        self.assertEqual(self.workspace, bridge.validate_workspace(self.workspace, self.source))
        with self.assertRaises(ValueError):
            bridge.validate_workspace(self.source, self.source)
        for cwd in (str(self.source), '../issue', str(self.workspace.parent)):
            with self.subTest(cwd=cwd), self.assertRaises(ValueError):
                changed = json.loads(json.dumps(self.turn))
                changed['params']['cwd'] = cwd
                bridge.transform_message(changed, self.workspace)

    def test_shared_lease_is_narrow_and_rejects_symlink_redirection(self):
        self.assertEqual(self.coordination, bridge.validate_coordination(self.source))
        changed = bridge.transform_message(self.turn, self.workspace, self.coordination)
        self.assertEqual([str(self.workspace / '.git'), str(self.workspace / '.agents'), str(self.coordination)], changed['params']['sandboxPolicy']['writableRoots'])
        self.coordination.rmdir()
        self.coordination.symlink_to(self.source, target_is_directory=True)
        with self.assertRaises(ValueError):
            bridge.validate_coordination(self.source)
        with self.assertRaises(ValueError):
            bridge.transform_message(self.turn, self.workspace, self.coordination)

    def test_git_symlink_is_rejected_on_launch_and_later_turn(self):
        (self.workspace / '.git').rmdir()
        external_git = self.source / 'other-git'
        external_git.mkdir()
        (self.workspace / '.git').symlink_to(external_git, target_is_directory=True)
        with self.assertRaises(ValueError):
            bridge.validate_workspace(self.workspace, self.source)
        with self.assertRaises(ValueError):
            bridge.transform_message(self.turn, self.workspace)

    def test_missing_or_file_agents_is_rejected_on_launch_and_each_turn(self):
        # A previously valid/idempotent policy must not skip path revalidation.
        granted = bridge.transform_message(self.turn, self.workspace, self.coordination)
        agents = self.workspace / '.agents'
        agents.rmdir()
        for replacement in ('missing', 'file'):
            with self.subTest(replacement=replacement):
                if replacement == 'file':
                    agents.write_text('not a directory')
                with self.assertRaises(ValueError):
                    bridge.validate_workspace(self.workspace, self.source)
                with self.assertRaises(ValueError):
                    bridge.transform_message(granted, self.workspace, self.coordination)

    def test_agents_symlink_cannot_redirect_to_source_sibling_or_local_target(self):
        agents = self.workspace / '.agents'
        granted = bridge.transform_message(self.turn, self.workspace)
        agents.rmdir()
        for target in (self.source, self.workspace.parent / 'other-issue',
                       self.workspace / 'different-directory', self.workspace / 'missing'):
            with self.subTest(target=target):
                if target.name != 'missing':
                    target.mkdir(exist_ok=True)
                agents.symlink_to(target, target_is_directory=True)
                try:
                    with self.assertRaises(ValueError):
                        bridge.validate_workspace(self.workspace, self.source)
                    with self.assertRaises(ValueError):
                        bridge.transform_message(granted, self.workspace)
                finally:
                    agents.unlink()

    def test_replaced_workspace_cannot_redirect_later_turn_grant(self):
        granted = bridge.transform_message(self.turn, self.workspace)
        sibling = self.workspace.parent / 'different-issue'
        self.workspace.rename(sibling)
        self.workspace.symlink_to(sibling, target_is_directory=True)
        with self.assertRaises(ValueError):
            bridge.transform_message(granted, self.workspace)

    def test_scoped_agents_root_preserves_existing_policy_and_is_idempotent(self):
        turn = json.loads(json.dumps(self.turn))
        policy = turn['params']['sandboxPolicy']
        original_roots = [str(self.workspace / 'build'), str(self.workspace / '.git')]
        policy.update(writableRoots=original_roots, excludeTmpdirEnvVar=True,
                      excludeSlashTmp=True, networkAccess=False)
        changed = bridge.transform_message(turn, self.workspace, self.coordination)
        expected = json.loads(json.dumps(turn))
        expected['params']['sandboxPolicy']['writableRoots'] += [str(self.workspace / '.agents'), str(self.coordination)]
        self.assertEqual(changed, expected)
        self.assertEqual(turn['params']['sandboxPolicy']['writableRoots'], original_roots)
        self.assertIs(bridge.transform_message(changed, self.workspace, self.coordination), changed)
        added = set(changed['params']['sandboxPolicy']['writableRoots']) - set(original_roots)
        self.assertEqual(added, {str(self.workspace / '.agents'), str(self.coordination)})
        self.assertNotIn(str(self.workspace / '.codex'), added)
        self.assertNotIn(str(self.source / '.agents'), added)
        self.assertNotIn(str(self.workspace.parent), added)

    def test_non_workspace_write_policy_is_preserved_even_without_agents(self):
        (self.workspace / '.agents').rmdir()
        for policy in ({'type': 'readOnly'}, {'type': 'dangerFullAccess'}, None):
            changed = json.loads(json.dumps(self.turn))
            changed['params']['sandboxPolicy'] = policy
            self.assertIs(bridge.transform_message(changed, self.workspace), changed)

    def test_mcp_command_uses_operator_config_and_anchors_issue_checkout(self):
        import tomllib
        # An issue branch must not redirect tool startup to an arbitrary command.
        (self.workspace / '.codex').mkdir()
        (self.workspace / '.codex/config.toml').write_text('[mcp_servers.serena]\ncommand="unreviewed"\n')
        command = bridge.codex_command(self.workspace, self.source)
        self.assertEqual(['codex', '--disable', 'remote_plugin'], command[:3])
        servers = tomllib.loads(next(arg for arg in command if arg.startswith('mcp_servers=')))['mcp_servers']
        self.assertEqual('node', servers['serena']['command'])
        self.assertEqual(str(self.workspace), servers['serena']['cwd'])
        self.assertEqual(str(self.workspace), servers['serena']['args'][-1])
        self.assertTrue(servers['serena']['args'][0].startswith(str(self.source)))
        self.assertNotIn('execute_shell_command', servers['serena']['enabled_tools'])
        self.assertEqual('https://mcp.context7.com/mcp', servers['context7']['url'])

    def test_protocol_transparency_and_child_shutdown(self):
        fake_bin = self.source / 'bin'
        fake_bin.mkdir()
        fake_codex = fake_bin / 'codex'
        fake_codex.write_text(
            '#!/usr/bin/env python3\n'
            'import json,os,sys\n'
            'print(json.dumps({"pid":os.getpid()}),flush=True)\n'
            'for line in sys.stdin:\n'
            ' sys.stdout.write(line);sys.stdout.flush()\n'
        )
        fake_codex.chmod(0o755)
        env = dict(os.environ, ONLYDRAGONS_SOURCE=str(self.source), PATH=str(fake_bin) + os.pathsep + os.environ['PATH'])
        for shutdown in ('eof', 'signal', 'invalid-cwd'):
            with self.subTest(shutdown=shutdown):
                proc = subprocess.Popen(
                    [sys.executable, str(BRIDGE_PATH)], cwd=self.workspace, env=env,
                    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                )
                try:
                    child_pid = json.loads(proc.stdout.readline())['pid']
                    untouched = '{"method": "initialized", "params": {}}\n'
                    proc.stdin.write(untouched)
                    proc.stdin.flush()
                    self.assertEqual(untouched, proc.stdout.readline())
                    # Host-provided tools, model selection and approval
                    # policy remain protocol data; remote-plugin sync is separate.
                    thread = {'method': 'thread/start', 'params': {
                        'cwd': str(self.workspace), 'model': 'operator-model',
                        'sandbox': 'workspace-write', 'approvalPolicy': 'never',
                        'dynamicTools': [{'name': 'github_api', 'description': 'Host-owned GitHub tool',
                                          'inputSchema': {'type': 'object', 'properties': {}}}],
                    }}
                    proc.stdin.write(json.dumps(thread) + '\n')
                    proc.stdin.flush()
                    self.assertEqual(thread, json.loads(proc.stdout.readline()))
                    proc.stdin.write(json.dumps(self.turn) + '\n')
                    proc.stdin.flush()
                    self.assertEqual(bridge.transform_message(self.turn, self.workspace, self.coordination), json.loads(proc.stdout.readline()))
                    if shutdown == 'eof':
                        proc.stdin.close()
                    elif shutdown == 'signal':
                        proc.send_signal(signal.SIGTERM)
                    else:
                        invalid = json.loads(json.dumps(self.turn))
                        invalid['params']['cwd'] = str(self.source)
                        proc.stdin.write(json.dumps(invalid) + '\n')
                        proc.stdin.flush()
                    proc.wait(timeout=10)
                    self.assertEqual(1 if shutdown == 'invalid-cwd' else 0, proc.returncode)
                    with self.assertRaises(ProcessLookupError):
                        os.kill(child_pid, 0)
                finally:
                    if proc.poll() is None:
                        proc.terminate()
                        proc.wait(timeout=10)
                    for stream in (proc.stdin, proc.stdout, proc.stderr):
                        stream.close()


if __name__ == '__main__':
    unittest.main()
