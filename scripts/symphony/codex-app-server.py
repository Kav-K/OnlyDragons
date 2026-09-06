#!/usr/bin/env python3
"""Preserve Symphony's sandbox while permitting Git metadata in its issue clone."""
import copy
import json
import os
from pathlib import Path
import select
import signal
import subprocess
import sys
import threading


def validate_workspace(workspace, source):
    workspace = Path(workspace).resolve(strict=True)
    expected = (Path(source).resolve(strict=True) / '.symphony/workspaces').resolve(strict=True)
    if workspace == expected or not workspace.is_relative_to(expected):
        raise ValueError('Worker is outside the issue workspace root.')
    git_dir = workspace / '.git'
    if git_dir.is_symlink() or not git_dir.is_dir() or git_dir.resolve(strict=True).parent != workspace:
        raise ValueError('Worker requires a real local .git directory.')
    return workspace


def transform_message(message, workspace):
    """Change only a workspaceWrite turn's roots; leave every other message intact."""
    if not isinstance(message, dict) or message.get('method') != 'turn/start':
        return message
    params = message.get('params')
    if not isinstance(params, dict):
        return message
    policy = params.get('sandboxPolicy')
    if not isinstance(policy, dict) or policy.get('type') != 'workspaceWrite':
        return message
    workspace = Path(workspace).resolve(strict=True)
    requested_cwd = params.get('cwd', str(workspace))
    if not isinstance(requested_cwd, str) or not Path(requested_cwd).is_absolute() or Path(requested_cwd).resolve(strict=True) != workspace:
        raise ValueError('Turn cwd does not match the issue workspace.')
    git_dir = workspace / '.git'
    if git_dir.is_symlink() or not git_dir.is_dir() or git_dir.resolve(strict=True).parent != workspace:
        raise ValueError('Worker Git metadata escaped its issue workspace.')
    roots = policy.get('writableRoots', [])
    if not isinstance(roots, list) or not all(isinstance(root, str) for root in roots):
        raise ValueError('Invalid writable roots in turn policy.')
    if str(git_dir) in roots:
        return message
    updated = copy.deepcopy(message)
    updated['params']['sandboxPolicy']['writableRoots'] = [*roots, str(git_dir)]
    return updated


def main():
    source = os.environ.get('ONLYDRAGONS_SOURCE')
    if not source:
        raise ValueError('Start workers through Symphony.cmd.')
    workspace = validate_workspace(Path.cwd(), source)
    stopping = threading.Event()
    for signum in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(signum, lambda *_: stopping.set())
    child = subprocess.Popen(
        ['codex', '--config', 'shell_environment_policy.inherit=all', 'app-server'],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=None,
        cwd=workspace, start_new_session=True,
    )

    def copy_stdout():
        try:
            while chunk := child.stdout.read1(65536):
                sys.stdout.buffer.write(chunk)
                sys.stdout.buffer.flush()
        except (BrokenPipeError, OSError):
            stopping.set()

    def send_line(line):
        try:
            original = json.loads(line)
        except (ValueError, UnicodeDecodeError):
            child.stdin.write(line)
        else:
            transformed = transform_message(original, workspace)
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
