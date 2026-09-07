#!/usr/bin/env python3
"""No-model MCP and skill smoke check. Run under WSL in an issue clone.

Set ONLYDRAGONS_SOURCE to the operator checkout and use the runtime Codex on PATH.
This starts an ephemeral app-server session, not a model turn or game server.
"""
import importlib.util
import json
import os
from pathlib import Path
import queue
import re
import signal
import subprocess
import sys
import threading
import time


def main():
    """Probe repository skills and reviewed MCP navigation tools through an isolated app-server.

    Require the prepared issue clone/operator runtime, create unauthenticated scratch
    CODEX_HOME and save local tool receipts. Verify actual Context7 documentation and
    Serena lifecycle-symbol responses. No model turn, GitHub write or Minecraft launch
    is requested. Always close the owned app-server, with bounded TERM fallback.
    """
    root = Path(os.environ['ONLYDRAGONS_SOURCE']).resolve(strict=True)
    workspace = Path.cwd().resolve(strict=True)
    spec = importlib.util.spec_from_file_location('bridge', root / 'scripts/symphony/codex-app-server.py')
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    bridge.validate_workspace(workspace, root)
    scratch = workspace / '.serena/runtime' / f'codex-check-{os.getpid()}'
    scratch.mkdir(parents=True)
    # Isolated, unauthenticated config proves this does not depend on Windows
    # global tools, trust settings, or skill installation.
    env = dict(os.environ, CODEX_HOME=str(scratch / 'codex'))
    Path(env['CODEX_HOME']).mkdir()
    messages = queue.Queue()
    log = (scratch / 'app-server.log').open('w')
    proc = subprocess.Popen(
        [sys.executable, str(root / 'scripts/symphony/codex-app-server.py')],
        cwd=workspace, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=log, text=True, start_new_session=True,
    )

    def receive():
        """Queue decoded protocol responses/notifications and an EOF sentinel from the owned child."""
        try:
            for line in proc.stdout:
                messages.put(json.loads(line))
        finally:
            messages.put(None)

    threading.Thread(target=receive, daemon=True).start()
    counter = 0

    def request(method, params, timeout=210):
        """Send one numbered RPC and wait up to timeout seconds for its matching response.

        Persist notifications separately, reject unexpected approval requests and surface
        server errors. This helper does not approve or answer interactive server requests.
        """
        nonlocal counter
        counter += 1
        proc.stdin.write(json.dumps({'id': counter, 'method': method, 'params': params}) + '\n')
        proc.stdin.flush()
        deadline = time.monotonic() + timeout
        while True:
            msg = messages.get(timeout=max(0.1, deadline - time.monotonic()))
            if msg is None:
                raise RuntimeError('App server exited before replying. Inspect local app-server.log.')
            if 'id' in msg and 'method' in msg:
                raise RuntimeError('Unexpected server approval/request during read-only smoke check.')
            if 'method' in msg:
                with (scratch / 'notifications.jsonl').open('a') as events:
                    events.write(json.dumps(msg) + '\n')
            if msg.get('id') == counter:
                if 'error' in msg:
                    raise RuntimeError(method + ': ' + str(msg['error']))
                return msg['result']
            if time.monotonic() >= deadline:
                raise TimeoutError(method)

    try:
        request('initialize', {'clientInfo': {'name': 'onlydragons-tools-check', 'version': '1'}, 'capabilities': {'experimentalApi': True}})
        proc.stdin.write('{"method":"initialized","params":{}}\n')
        proc.stdin.flush()
        skills = request('skills/list', {'cwds': [str(workspace)], 'forceReload': True})
        (scratch / 'skills.json').write_text(json.dumps(skills, indent=2))
        expected = {'minecraft-plugin-development', 'paper-runtime-validation', 'paper-threading-review'}
        found = {
            skill['name'] for group in skills['data'] for skill in group['skills']
            if skill.get('enabled', True) and str(workspace / '.agents/skills') in skill.get('path', '')
        }
        if not expected <= found:
            raise RuntimeError('Missing repository skills: ' + ', '.join(sorted(expected - found)))
        print('PASS: all 3 repository Minecraft skills discovered with isolated CODEX_HOME.', flush=True)
        thread = request('thread/start', {
            'cwd': str(workspace), 'sandbox': 'workspace-write', 'ephemeral': True,
            'approvalPolicy': {'granular': dict.fromkeys(
                ['sandbox_approval', 'rules', 'mcp_elicitations', 'request_permissions', 'skill_approval'], False)},
        })
        thread_id = thread['thread']['id']
        statuses = request('mcpServerStatus/list', {'threadId': thread_id})
        (scratch / 'mcp-status.json').write_text(json.dumps(statuses, indent=2))
        for name, needed in [('context7', {'resolve-library-id', 'query-docs'}), ('serena', {'get_symbols_overview', 'find_symbol', 'find_referencing_symbols'})]:
            server = next((server for server in statuses['data'] if server['name'] == name), None)
            if not server or not needed <= set(server.get('tools', {})):
                raise RuntimeError('MCP tools unavailable for ' + name + '. Inspect mcp-status.json.')
            print(f'PASS: {name} connected ({len(server["tools"])} tools).', flush=True)
        for server, tool, args in [
            ('context7', 'resolve-library-id', {'libraryName': 'PaperMC', 'query': 'Official Paper Minecraft server plugin development documentation for Java, events and scheduling.'}),
            ('serena', 'get_symbols_overview', {'relative_path': str(next(workspace.glob('src/main/java/**/*Plugin.java')).relative_to(workspace)), 'depth': 1}),
        ]:
            result = request('mcpServer/tool/call', {'threadId': thread_id, 'server': server, 'tool': tool, 'arguments': args})
            (scratch / f'{server}-query.json').write_text(json.dumps(result, indent=2))
            # RPC wraps the actual MCP CallToolResult on some Codex releases.
            payload = result.get('result', result)
            if payload.get('isError') or not payload.get('content'):
                raise RuntimeError(f'{server} query failed; inspect {server}-query.json.')
            text = '\n'.join(item.get('text', '') for item in payload['content'])
            if server == 'serena' and ('onEnable' not in text or 'onDisable' not in text):
                raise RuntimeError('Serena returned no expected plugin lifecycle symbols.')
            if server == 'context7':
                match = re.search(r'library ID:\s*(/\S+)', text, flags=re.IGNORECASE)
                if not match:
                    raise RuntimeError('Context7 returned no library match; check rate limits/network.')
                docs = request('mcpServer/tool/call', {
                    'threadId': thread_id, 'server': server, 'tool': 'query-docs',
                    'arguments': {'libraryId': match[1], 'query': 'Paper 26.2 JavaPlugin onEnable and onDisable lifecycle method documentation.'},
                })
                (scratch / 'context7-docs.json').write_text(json.dumps(docs, indent=2))
                payload = docs.get('result', docs)
                docs_text = '\n'.join(item.get('text', '') for item in payload.get('content', []))
                if payload.get('isError') or 'onEnable' not in docs_text:
                    raise RuntimeError('Context7 documentation query returned no lifecycle documentation.')
                print('PASS: context7/query-docs returned plugin lifecycle documentation.', flush=True)
            print(f'PASS: {server}/{tool} returned expected content.', flush=True)
        print('No model turn, GitHub write, or Minecraft server was started.', flush=True)
    finally:
        if proc.poll() is None:
            proc.stdin.close()
            try:
                proc.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGTERM)
                proc.wait(timeout=10)
        proc.stdout.close()
        log.close()


if __name__ == '__main__':
    main()
