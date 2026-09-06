"""Run in WSL: python3 -m unittest discover -s scripts/symphony/tests -v."""
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
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='symphony-bridge-')
        self.addCleanup(self.temp.cleanup)
        self.source = Path(self.temp.name)
        self.workspace = self.source / '.symphony/workspaces/issue'
        (self.workspace / '.git').mkdir(parents=True)
        self.turn = {
            'id': 7, 'method': 'turn/start',
            'params': {
                'cwd': str(self.workspace),
                'sandboxPolicy': {'type': 'workspaceWrite', 'networkAccess': True},
                'input': [{'type': 'text', 'text': 'Retain this prompt exactly.'}],
            },
        }

    def test_only_git_root_is_added_without_changing_input(self):
        changed = bridge.transform_message(self.turn, self.workspace)
        expected = json.loads(json.dumps(self.turn))
        expected['params']['sandboxPolicy']['writableRoots'] = [str(self.workspace / '.git')]
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

    def test_git_symlink_is_rejected_on_launch_and_later_turn(self):
        (self.workspace / '.git').rmdir()
        external_git = self.source / 'other-git'
        external_git.mkdir()
        (self.workspace / '.git').symlink_to(external_git, target_is_directory=True)
        with self.assertRaises(ValueError):
            bridge.validate_workspace(self.workspace, self.source)
        with self.assertRaises(ValueError):
            bridge.transform_message(self.turn, self.workspace)

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
                    proc.stdin.write(json.dumps(self.turn) + '\n')
                    proc.stdin.flush()
                    self.assertEqual(bridge.transform_message(self.turn, self.workspace), json.loads(proc.stdout.readline()))
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
