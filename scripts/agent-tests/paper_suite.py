#!/usr/bin/env python3
"""Plan or run shared Paper suites; completed receipts revalidate their raw evidence."""
from __future__ import annotations

import argparse
from fnmatch import fnmatchcase
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

import paper_test as runner

ValidationError = runner.ValidationError
require = runner.require
CLEANUP = ('owned_entities_removed', 'owned_tasks_cancelled',
           'owned_chunk_tickets_removed', 'owned_listeners_removed')
NEGATIVES = {'deliberate-failure', 'cleanup-failure', 'cleanup-abort', 'player-early-exit', 'player-idle'}
ABORT = 'java.lang.IllegalStateException: Companion disabled before scenario completion'
PLAYER_SETUP = ('server_thread', 'production_enabled', 'disposable_protocol_mode', 'offline_fixture',
                'real_player_join', 'player_uuid', 'player_loopback', 'player_not_op')


def git(project, *args):
    return subprocess.check_output(['git', *args], cwd=project)


def digest_json(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'),
                                     ensure_ascii=True, allow_nan=False).encode('utf-8')).hexdigest()


def documentation(path):
    if path.startswith('src/') or '/src/' in path:
        return False  # Markdown resources are packaged into JARs too.
    return path.endswith('.md') or path.startswith('docs/')


def tree_inputs(project, revision):
    entries = []
    for row in git(project, 'ls-tree', '-rz', '--full-tree', revision).split(b'\0'):
        if not row:
            continue
        metadata, raw_path = row.split(b'\t', 1)
        mode, kind, oid = metadata.decode('ascii').split()
        path = raw_path.decode('utf-8')
        if documentation(path):
            continue
        require(kind == 'blob' and mode in ('100644', '100755'),
                'Runtime inputs must be regular tracked files: ' + path)
        entries.append({'path': path, 'mode': mode, 'gitBlob': oid})
    return sorted(entries, key=lambda entry: entry['path'])


def source_identity(project):
    """Exact actual-byte cohort; Markdown/docs may change only between complete runs.

    Clean means no tracked changes or untracked files (ignored build/run caches are
    expected). Hidden index flags are rejected, rather than trusting a clean status
    that can conceal changed inputs. Byte hashes deliberately distinguish CRLF/LF.
    """
    project = Path(project).resolve()
    require(not git(project, 'status', '--porcelain=v1', '-z', '--untracked-files=all'),
            'A clean committed checkout is required, including untracked files')
    require(not git(project, 'ls-files', '--others', '--ignored', '--exclude-standard', '-z', '--',
                    'src', 'dev/game-tests/src', 'dev/player-client/src', 'gradle',
                    'dev/game-tests/gradle', 'dev/player-client/gradle'),
            'Ignored untracked source/build inputs prevent exact cohort verification')
    for row in git(project, 'ls-files', '-v', '-z').split(b'\0'):
        if row:
            require(row[:1] == b'H', 'Hidden/unsupported Git index flags prevent input verification')
    revision = git(project, 'rev-parse', 'HEAD').decode().strip()
    tree = tree_inputs(project, revision)
    files = []
    for entry in tree:
        path = safe_path(project, entry['path'])
        require(path.is_file(), 'Missing tracked input: ' + entry['path'])
        files.append({'path': entry['path'], 'mode': entry['mode'], 'sha256': runner.sha256(path)})
    return {'revision': revision, 'sourceInputSha256': digest_json(files),
            'gitTreeSha256': digest_json(tree), 'inputFiles': files}


def safe_path(project, relative):
    require(isinstance(relative, str) and relative and '\\' not in relative,
            'Evidence paths must be repository-relative POSIX paths')
    path = PurePosixPath(relative)
    require(not path.is_absolute() and '..' not in path.parts and ':' not in relative,
            'Evidence path escapes checkout')
    candidate = Path(project) / relative
    require(candidate.resolve().is_relative_to(Path(project).resolve()), 'Evidence symlink escapes checkout')
    require(not any(part.is_symlink() for part in (candidate, *candidate.parents)
                    if part != Path(project).parent), 'Symlink evidence/input is not supported')
    return candidate


def resolved_evidence_path(project, value):
    """Normalize filesystem aliases only after rejecting original symlink/traversal paths."""
    project = Path(project).resolve()
    require(isinstance(value, (str, os.PathLike)), 'Missing evidence path')
    candidate = Path(value)
    require('..' not in candidate.parts, 'Evidence path escapes checkout')
    if not candidate.is_absolute():
        candidate = safe_path(project, candidate.as_posix())
    require(not any(part.is_symlink() for part in (candidate, *candidate.parents)),
            'Symlink evidence/input is not supported')
    resolved = candidate.resolve()
    require(resolved.is_relative_to(project), 'Evidence path escapes checkout')
    return safe_path(project, resolved.relative_to(project).as_posix())


def names(value, description):
    require(isinstance(value, list) and all(isinstance(item, str) and item for item in value)
            and len(value) == len(set(value)), 'Invalid/duplicate ' + description)
    return value


def load_catalog(project):
    catalog = runner.strict_json(Path(project) / 'dev/game-tests/suites.json')
    scenarios = runner.strict_json(Path(project) / 'dev/game-tests/scenarios.json')
    require(type(catalog.get('schemaVersion')) is int and catalog['schemaVersion'] == 1,
            'Unsupported suites schema')
    cases = catalog.get('cases')
    require(isinstance(cases, dict) and cases, 'Suite cases are missing')
    covered = set()
    for case_id, case in cases.items():
        require(re.fullmatch(r'[a-z0-9-]+', case_id) and isinstance(case, dict), 'Invalid suite case')
        scenario_id = case.get('scenarioId')
        require(scenario_id in scenarios, 'Unknown case scenario: ' + str(scenario_id))
        covered.add(scenario_id)
        expectation = case.get('expectation')
        require(expectation == 'positive' or expectation in NEGATIVES, 'Unknown expectation: ' + str(expectation))
        descriptor = scenarios[scenario_id]
        names(descriptor.get('requiredAssertions'), 'scenario required assertions')
        mode = case.get('testPlayer')
        declared = descriptor.get('testPlayerMode')
        # Legacy catalog compatibility is restricted to the already reviewed actor.
        if declared is None and scenario_id == 'protocol-player-calibration':
            declared = 'protocol-calibration'
        require(mode == declared and mode in (None, 'protocol-calibration'), 'Case player mode differs from scenario admission')
        control = case.get('playerControl', 'calibrate')
        require(control in ('calibrate', 'early-exit', 'idle') and (mode or control == 'calibrate'), 'Invalid player control')
        require((control == 'early-exit') == (expectation == 'player-early-exit')
                and (control == 'idle') == (expectation == 'player-idle'), 'Control/expectation mismatch')
        if expectation in ('player-early-exit', 'player-idle'):
            require(scenario_id == 'protocol-player-calibration', 'Player negative policy is calibrated for one scenario')
        timeout = case.get('scenarioTimeout', 60)
        require(type(timeout) is int and 1 <= timeout <= 300, 'Invalid scenario timeout')
    require(covered == set(scenarios), 'Every registered scenario needs explicit positive/negative suite coverage: '
            + ', '.join(sorted(set(scenarios) - covered)))
    suites = catalog.get('suites', {})
    require(set(suites) == {'regression', 'harness-controls', 'all'}, 'Expected the three shared suites')
    for suite_id, selected in suites.items():
        require(set(names(selected, 'suite cases')).issubset(cases), 'Unknown suite case: ' + suite_id)
    positives = {key for key, value in cases.items() if value['expectation'] == 'positive'}
    require(set(suites['regression']) == positives and set(suites['harness-controls']) == set(cases) - positives
            and set(suites['all']) == set(cases), 'Shared suites must contain every classified case')
    areas = catalog.get('areas', {})
    require(isinstance(areas, dict) and areas, 'Changed-area coverage is missing')
    for area in areas.values():
        names(area.get('paths'), 'area paths')
        require(set(names(area.get('cases'), 'area cases')).issubset(cases), 'Unknown area case')
        require(not area['paths'] or area['cases'], 'Mapped runtime paths require nonempty scenario coverage')
        require(set(names(area.get('affects'), 'affected areas')).issubset(areas), 'Unknown affected area')
    for full_area in ('harness-and-build', 'shared-contracts'):
        if full_area in areas:
            require(set(areas[full_area]['cases']) == set(cases),
                    full_area + ' changes require every catalog case')
    names(catalog.get('ignoredChanges'), 'ignored changes')
    return catalog, scenarios


def required_cases(project, changed_paths):
    """Return deterministic transitive scenario cases, failing closed on unmapped inputs."""
    catalog, _ = load_catalog(project)
    areas = catalog['areas']
    selected = set()
    for path in names(list(changed_paths), 'changed paths'):
        safe_path(project, path)
        if documentation(path) or any(fnmatchcase(path, pattern) for pattern in catalog['ignoredChanges']):
            continue
        matched = {name for name, area in areas.items()
                   if any(fnmatchcase(path, pattern) for pattern in area['paths'])}
        require(matched, 'Changed input has no scenario coverage mapping: ' + path)
        selected.update(matched)
    pending = list(selected)
    while pending:
        for affected in areas[pending.pop()]['affects']:
            if affected not in selected:
                selected.add(affected)
                pending.append(affected)
    result = {case for area in selected for case in areas[area]['cases']}
    return [case for case in catalog['cases'] if case in result]


def changed_paths(project, base, head='HEAD'):
    require(isinstance(base, str) and base and not base.startswith('-'), 'Invalid changed base')
    revision = git(project, 'rev-parse', '--verify', base + '^{commit}').decode().strip()
    paths = git(project, 'diff', '--no-renames', '--name-only', '-z', revision, head).decode().split('\0')
    return revision, sorted(path for path in paths if path)


def selection(project, suite_ids=None, base=None, revision='HEAD'):
    catalog, _ = load_catalog(project)
    require(not (suite_ids and base), 'Choose suites or --changed-since')
    if base:
        base_revision, paths = changed_paths(project, base, revision)
        return {'mode': 'changed', 'suiteIds': [], 'baseRevision': base_revision,
                'changedPaths': paths, 'caseIds': required_cases(project, paths)}
    suite_ids = suite_ids or ['all']
    require(set(names(suite_ids, 'selected suites')).issubset(catalog['suites']), 'Unknown suite')
    wanted = {case for suite in suite_ids for case in catalog['suites'][suite]}
    return {'mode': 'suites', 'suiteIds': suite_ids, 'changedPaths': [],
            'caseIds': [case for case in catalog['cases'] if case in wanted]}


def validate_selection(project, selected, revision='HEAD'):
    require(isinstance(selected, dict), 'Missing suite selection')
    if selected.get('mode') == 'changed':
        expected = selection(project, base=selected.get('baseRevision'), revision=revision)
    else:
        require(selected.get('mode') == 'suites', 'Unknown selection mode')
        expected = selection(project, suite_ids=selected.get('suiteIds'))
    require(runner.json_values_equal(selected, expected), 'Suite selection is incomplete or stale')
    require(expected['caseIds'], 'Zero-case plans are not completed gameplay evidence')


def timestamps(report, start, end, label):
    first, last = report.get('startedAtEpochMs'), report.get('completedAtEpochMs')
    require(type(first) is int and type(last) is int and start <= first <= last <= end,
            'Missing/stale timestamps: ' + label)
    require(last <= int(time.time() * 1000) + 1000, 'Future timestamps: ' + label)
    return first, last


def verify_scenario(report, case, descriptor, run_id, pins, start, end):
    expected = {'schemaVersion': 1, 'runId': run_id, 'scenarioId': case['scenarioId'],
                'mechanicRevision': descriptor['mechanicRevision'], 'state': 'complete', 'syntheticActors': True}
    for key, value in expected.items():
        require(runner.json_values_equal(report.get(key), value), 'Wrong scenario ' + key)
    first, last = timestamps(report, start, end, 'scenario')
    allowance = 80 if case['expectation'] == 'player-idle' else 0
    require(last - first <= (case.get('scenarioTimeout', 60) + allowance) * 1000, 'Scenario exceeded declared deadline')
    server = report.get('server', {})
    require(server.get('minecraftVersion') == pins['minecraftVersion'], 'Wrong Minecraft version')
    require(isinstance(server.get('paperVersion'), str) and re.search(
        r'(?:^|[- ])' + re.escape(pins['paperBuild']) + r'(?:[- ]|$)', server['paperVersion']), 'Wrong Paper build')
    assertions = report.get('assertions')
    require(isinstance(assertions, list) and assertions, 'Missing scenario assertions')
    rows, failures = {}, {}
    for row in assertions:
        require(isinstance(row, dict) and isinstance(row.get('id'), str) and row['id'] not in rows
                and 'expected' in row and 'observed' in row, 'Malformed or duplicate assertion')
        name = row['id']
        equal = runner.json_values_equal(row['expected'], row['observed'])
        require(type(row.get('passed')) is bool and row['passed'] == equal, 'Forged assertion flag: ' + name)
        rows[name] = row
        if not equal:
            failures[name] = {'expected': row['expected'], 'observed': row['observed']}
    expectation = case['expectation']
    allowed = {}
    required = set(descriptor['requiredAssertions'])
    if expectation == 'deliberate-failure':
        allowed = {'deliberate_failure': {'expected': 1, 'observed': 0}}
    elif expectation in ('cleanup-failure', 'cleanup-abort'):
        observed = ABORT if expectation == 'cleanup-abort' else 'java.lang.IllegalStateException: Deliberate projectile cleanup failure'
        allowed = {'scenario_exception': {'expected': 'no exception', 'observed': observed}}
    elif expectation in ('player-early-exit', 'player-idle'):
        required = set(PLAYER_SETUP) | set(CLEANUP)
        allowed = {'real_player_quit': {'expected': True, 'observed': False}}
        if expectation == 'player-early-exit' or 'scenario_exception' not in rows:
            # Client cleanup can finish the quit callback before Paper disables the
            # companion. That ordering must prove logout instead of an abort.
            required.add('player_removed_after_quit')
        else:
            allowed['scenario_exception'] = {'expected': 'no exception', 'observed': ABORT}
        if 'player_removed_after_quit' in rows:
            removed = rows['player_removed_after_quit']
            require(removed['expected'] is True and removed['observed'] is True and removed['passed'] is True,
                    'Negative player logout evidence is missing or failed')
        require(not any(name in rows for name in ('real_selected_slot_event', 'real_bow_use_event',
                    'real_bow_release_event', 'native_projectile_shooter')), 'Negative player unexpectedly performed actions')
    require(required.issubset(rows), 'Required scenario assertions are missing')
    require(runner.json_values_equal(failures, allowed), 'Scenario failure differs from intended negative control')
    require(report.get('passed') is (not bool(allowed)), 'Scenario overall flag contradicts assertion evidence')
    for name in CLEANUP:
        require(name in rows and runner.json_values_equal(rows[name]['expected'], 0)
                and runner.json_values_equal(rows[name]['observed'], 0) and rows[name]['passed'] is True,
                'Scenario resource cleanup failed: ' + name)
    return len(rows)


def verify_player(report, case, run_id, pins, start, end):
    pinned = runner.player_pins(pins)
    negative = case['expectation'] != 'positive'
    expected = {'schemaVersion': 1, 'runId': run_id, 'username': 'od_' + run_id[:13],
                'authentication': 'offline-disposable-loopback', 'artifact': pinned['artifact'],
                'minecraftVersion': pins['minecraftVersion'], 'protocolVersion': pinned['protocolVersion'],
                'loginReceived': True, 'disconnected': True,
                'actions': [] if negative else ['select', 'draw', 'release', 'quit'], 'passed': not negative}
    for key, value in expected.items():
        require(runner.json_values_equal(report.get(key), value), 'Wrong player evidence: ' + key)
    timestamps(report, start, end, 'player')
    early = case['expectation'] == 'player-early-exit'
    require(type(report.get('playerLoadedSent')) is bool and (early or report['playerLoadedSent']), 'Player loaded evidence is missing')
    require(type(report.get('teleportsAcknowledged')) is int and report['teleportsAcknowledged'] >= (0 if early else 1),
            'Player teleport acknowledgement is missing')
    error = report.get('error')
    if case['expectation'] == 'player-early-exit':
        require(error == 'Deliberate early client exit', 'Wrong early-exit reason')
    elif case['expectation'] == 'player-idle':
        require(error == 'Timed out waiting for calibration' or (isinstance(error, str)
                and error.startswith('Unexpected disconnect: ') and "content='Runner cleanup'" in error),
                'Wrong idle disconnect reason')
    else:
        require(error == '', 'Player unexpectedly failed')


def junit_counts(files):
    counts = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
    identities = set()
    require(files, 'Missing JUnit XML evidence')
    for path in files:
        data = path.read_bytes()
        require(len(data) <= 16 * 1024 * 1024 and b'<!DOCTYPE' not in data and b'<!ENTITY' not in data,
                'Unsafe or oversized JUnit evidence')
        try:
            root = ET.fromstring(data)
        except ET.ParseError as error:
            raise ValidationError('Malformed JUnit XML') from error
        require(root.tag == 'testsuite', 'Expected one JUnit testsuite per evidence file')
        tests = root.findall('testcase')
        actual = {'tests': len(tests), 'failures': sum(len(test.findall('failure')) for test in tests),
                  'errors': sum(len(test.findall('error')) for test in tests),
                  'skipped': sum(len(test.findall('skipped')) for test in tests)}
        for key, value in actual.items():
            require(root.get(key, '').isdigit() and int(root.get(key)) == value, 'JUnit summary contradicts cases: ' + key)
            counts[key] += value
        for test in tests:
            identity = (test.get('classname'), test.get('name'))
            require(all(identity) and identity not in identities, 'Missing/duplicate JUnit test identity')
            identities.add(identity)
    require(counts['tests'] > 0 and not any(counts[key] for key in ('failures', 'errors', 'skipped')),
            'JUnit failures/errors/skips or no executed tests')
    return counts


def process_cleanup(profile, port):
    require(sys.platform == 'linux' and Path('/proc').is_dir(), 'Acceptance cleanup verification requires Linux/WSL')
    for entry in Path('/proc').iterdir():
        if entry.name.isdecimal():
            try:
                require((entry / 'cwd').resolve() != profile.resolve(), 'Owned run process is still alive: ' + entry.name)
            except (PermissionError, FileNotFoundError, ProcessLookupError):
                continue
    with socket.socket() as sock:
        sock.settimeout(0.2)
        require(sock.connect_ex(('127.0.0.1', port)) != 0, 'Run loopback port is still open')


def checked_file(project, record, path_key, hash_key, exact=None):
    path = safe_path(project, record.get(path_key))
    if exact is not None:
        require(path == exact, 'Evidence path does not belong to this run: ' + path_key)
    require(path.is_file() and runner.sha256(path) == record.get(hash_key), 'Missing/mutated evidence: ' + str(path))
    return path


def reject_unstarted_run(result, record, result_path):
    """Preserve a resource/preflight failure before demanding nonexistent runtime reports."""
    require(isinstance(result, dict), 'Malformed runner result: ' + str(result_path))
    reason = result.get('error') or 'runner supplied no failure reason'
    if result.get('busy') is True or record.get('exitCode') == 75:
        raise ValidationError(f"Runner resources unavailable for {record['caseId']}: {reason}; see {result_path}")
    if result.get('passed') is False and not result.get('profile') and result.get('error'):
        raise ValidationError(f"Runner stopped before Paper started for {record['caseId']}: {reason}; see {result_path}")


def verify_case(project, record, case, descriptor, source, suite_root):
    run_id = record.get('runId')
    require(isinstance(run_id, str) and re.fullmatch('[0-9a-f]{32}', run_id), 'Invalid run ID')
    report_root = project / 'build/reports/agent-paper' / run_id
    result_path = checked_file(project, record, 'resultPath', 'resultSha256', report_root / 'result.json')
    result = runner.strict_json(result_path)
    reject_unstarted_run(result, record, result_path)
    scenario_path = checked_file(project, record, 'scenarioPath', 'scenarioSha256', report_root / 'scenario.json')
    start, end = record.get('startedAtEpochMs'), record.get('completedAtEpochMs')
    require(type(start) is int and type(end) is int and start <= end, 'Missing case invocation window')
    timestamps(result, start, end, 'runner')
    pins = runner.properties(project / 'versions.properties')
    require(runner.json_values_equal(result.get('schemaVersion'), 1) and result.get('runId') == run_id
            and result.get('scenarioId') == case['scenarioId'] and record.get('scenarioId') == case['scenarioId'], 'Wrong runner identity')
    require(result.get('revision') == source['revision'] and result.get('worktreeDirty') is False,
            'Runner did not test the clean source revision')
    require(result.get('pins') == pins and re.match(re.escape(pins['javaVersion']) + r'(?:\.|$)', result.get('javaVersion', '')),
            'Runner pin/JDK identity mismatch')
    require(result.get('busy', False) is False, 'Busy is not completed evidence')
    positive = case['expectation'] == 'positive'
    require(type(record.get('exitCode')) is int and record['exitCode'] == (0 if positive else 1)
            and result.get('passed') is positive, 'Runner result/exit contradicts expected outcome')
    errors = {'deliberate-failure': ['Failed scenario assertions: deliberate_failure'],
              'cleanup-failure': ['Failed scenario assertions: scenario_exception'],
              'cleanup-abort': ['Failed scenario assertions: scenario_exception'],
              'player-early-exit': ['Required scenario assertions are missing',
                                    'Protocol player exited unsuccessfully before scenario completion'],
              'player-idle': ['Timed out waiting for a scenario report']}
    require(result.get('error') in ([None] if positive else errors[case['expectation']]), 'Unexpected runner failure reason')
    cleanup = result.get('cleanup', {})
    require(cleanup.get('clean') is True and cleanup.get('forced') is False
            and runner.json_values_equal(cleanup.get('exitCode'), 0), 'Paper was not reaped cleanly')
    scenario = runner.strict_json(scenario_path)
    assertion_count = verify_scenario(scenario, case, descriptor, run_id, pins, start, end)
    if positive:
        require(runner.json_values_equal(result.get('scenario'), scenario), 'Raw scenario differs from accepted runner scenario')
    profile = safe_path(project, 'run/agent-tests/' + run_id)
    settings = runner.properties(profile / 'server.properties')
    actor = bool(case.get('testPlayer'))
    memory = result.get('memory', {})
    heap = memory.get('paperHeapMiB')
    require(type(heap) is int and 1024 <= heap <= 2048 and isinstance(memory.get('linuxMemoryMiB'), dict),
            'Paper memory admission evidence is missing')
    assessed = runner.assess_memory(heap, memory['linuxMemoryMiB'], memory.get('windowsAvailableMiB'), 256 if actor else 0)
    require(runner.json_values_equal(assessed, memory), 'Memory admission evidence contradicts the shared gate')
    expected_settings = {'server-ip': '127.0.0.1', 'online-mode': 'false' if actor else 'true',
                         'enable-rcon': 'false', 'enable-query': 'false', 'enable-jmx-monitoring': 'false',
                         'level-name': 'agent-world-' + run_id}
    for key, value in expected_settings.items():
        require(settings.get(key) == value, 'Disposable profile policy mismatch: ' + key)
    profile_info = result.get('profile', {})
    require(isinstance(profile_info.get('directory'), str) and Path(profile_info['directory']).is_absolute(),
            'Disposable profile path must be absolute')
    require(resolved_evidence_path(project, profile_info.get('directory')) == profile and profile_info.get('world') == expected_settings['level-name']
            and profile_info.get('testPlayerMode') == case.get('testPlayer')
            and profile_info.get('authentication') == ('offline-disposable-loopback' if actor else 'authenticated'), 'Wrong disposable profile identity')
    port = profile_info.get('port')
    require(type(port) is int and 1 <= port <= 65535 and settings.get('server-port') == str(port), 'Wrong loopback port')
    process_cleanup(profile, port)
    server_log = checked_file(project, record, 'serverLogPath', 'serverLogSha256', report_root / 'server.log').read_text(encoding='utf-8', errors='replace')
    checked_file(project, record, 'buildLogPath', 'buildLogSha256', report_root / 'build.log')
    require('Stopping server' in server_log and not re.search(
        r'(?:/ERROR\]|\bSEVERE\]|OD_GAME_TEST_REPORT_ERROR|Error occurred while (?:enabling|disabling)|Could not load|Failed to start the minecraft server)', server_log),
        'Paper error log or missing shutdown evidence')
    artifacts = result.get('artifacts', {})
    for relative, digest in [('server.jar', pins['paperSha256']),
                             ('plugins/OnlyDragons.jar', artifacts.get('productionSha256')),
                             ('plugins/OnlyDragonsGameTests.jar', artifacts.get('gameTestsSha256'))]:
        require(runner.sha256(safe_path(project, 'run/agent-tests/' + run_id + '/' + relative)) == digest,
                'Staged artifact differs from exact run build: ' + relative)
    build = result.get('build', {})
    require(build.get('wrapperInvoked') is True, 'Wrapper build evidence is missing')
    evidence = record.get('testEvidence')
    require(isinstance(evidence, list), 'JUnit evidence list is missing')
    grouped, seen = {'production': [], 'player': []}, set()
    for item in evidence:
        require(isinstance(item, dict) and item.get('kind') in grouped, 'Unknown JUnit evidence kind')
        path = checked_file(project, item, 'path', 'sha256')
        require(path.is_relative_to(suite_root / record['caseId'] / item['kind']) and path.name.startswith('TEST-')
                and path.suffix == '.xml' and path not in seen, 'Duplicate/misplaced JUnit evidence')
        seen.add(path)
        grouped[item['kind']].append(path)
    counts = junit_counts(grouped['production'])
    require(runner.json_values_equal(counts, build.get('unitTests')), 'Production JUnit totals differ from actual cases')
    client_identity = None
    if actor:
        for key, value in {'white-list': 'true', 'enforce-whitelist': 'true', 'max-players': '1',
                           'enforce-secure-profile': 'false'}.items():
            require(settings.get(key) == value, 'Wrong synthetic actor admission: ' + key)
        name = 'od_' + run_id[:13]
        raw_uuid = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode()).digest())
        raw_uuid[6] = (raw_uuid[6] & 0x0f) | 0x30
        raw_uuid[8] = (raw_uuid[8] & 0x3f) | 0x80
        require(runner.strict_json(profile / 'whitelist.json') == [{'name': name, 'uuid': str(uuid.UUID(bytes=bytes(raw_uuid)))}],
                'Actor whitelist differs from the unique run identity')
        client = result.get('playerBuild', {})
        pinned = runner.player_pins(pins)
        require(client.get('artifact') == pinned['artifact'], 'Wrong player build artifact')
        require(client.get('lockSha256') == runner.sha256(project / 'dev/player-client/gradle.lockfile')
                and client.get('verificationMetadataSha256') == runner.sha256(project / 'dev/player-client/gradle/verification-metadata.xml'),
                'Player dependency inputs changed')
        client_dir = profile / 'player-client'
        jars = {path.name: runner.sha256(path) for path in client_dir.glob('*.jar')}
        require(jars and jars == client.get('jars') and jars.get(pinned['jarName']) == pinned['sha256']
                and 'OnlyDragonsPlayerClient.jar' in jars, 'Missing/mutated player dependency artifacts')
        require(runner.json_values_equal(junit_counts(grouped['player']), client.get('unitTests')), 'Player JUnit totals differ')
        player_path = checked_file(project, record, 'playerPath', 'playerSha256', report_root / 'player.json')
        player = runner.strict_json(player_path)
        verify_player(player, case, run_id, pins, start, end)
        if positive:
            runner.validate_player_messages(player, descriptor)
            require(runner.json_values_equal(result.get('player'), player), 'Raw player differs from accepted player report')
        cleanup = result.get('playerCleanup', {})
        require(cleanup.get('clean') is True and cleanup.get('forced') is False
                and runner.json_values_equal(cleanup.get('exitCode'), 0 if positive else 1)
                and cleanup.get('successfulExit') is positive, 'Player process was not reaped with expected exit')
        memory = result.get('memory', {})
        require(memory.get('playerClientHeapMiB') == 256, 'Player memory reservation is missing')
        client_identity = {'jars': jars, 'lockSha256': client['lockSha256'],
                           'verificationMetadataSha256': client['verificationMetadataSha256']}
    else:
        require(not grouped['player'] and 'playerBuild' not in result and 'playerPath' not in record,
                'Unexpected player evidence in authenticated scenario')
    return {'assertions': assertion_count, 'unitTests': counts, 'artifacts': artifacts,
            'client': client_identity, 'expectedOutcome': case['expectation']}


def validate_suite_receipt(project, receipt_path):
    """Replay raw evidence; return the verified receipt, never trusting its pass flags."""
    project = Path(project).resolve()
    path = resolved_evidence_path(project, receipt_path)
    receipt = runner.strict_json(path)
    require(receipt.get('kind') == 'paper-suite-receipt' and receipt.get('state') == 'complete'
            and receipt.get('passed') is True and runner.json_values_equal(receipt.get('schemaVersion'), 1),
            'Only complete successful suite receipts are acceptance evidence')
    suite_id = receipt.get('suiteRunId')
    require(isinstance(suite_id, str) and re.fullmatch('[0-9a-f]{32}', suite_id), 'Invalid suite ID')
    suite_root = project / 'build/reports/agent-paper-suites' / suite_id
    require(path == suite_root / 'receipt.json', 'Receipt is outside its unique suite directory')
    current = source_identity(project)
    source = receipt.get('source', {})
    require(source.get('sourceInputSha256') == current['sourceInputSha256']
            and source.get('inputFiles') == current['inputFiles'] and source.get('gitTreeSha256') == current['gitTreeSha256'],
            'Current source inputs differ from the tested clean cohort')
    require(re.fullmatch('[0-9a-f]{40,64}', source.get('revision', ''))
            and digest_json(tree_inputs(project, source['revision'])) == source['gitTreeSha256'], 'Tested revision does not identify this input tree')
    start, end = timestamps(receipt, 0, int(time.time() * 1000) + 1000, 'suite')
    validate_selection(project, receipt.get('selection'), source['revision'])
    catalog, scenarios = load_catalog(project)
    cases = receipt.get('cases')
    require(isinstance(cases, list) and [record.get('caseId') for record in cases] == receipt['selection']['caseIds'],
            'Missing, duplicate or unexpected suite cases')
    seen, previous_end, artifacts, client = set(), start, None, None
    for record in cases:
        require(record.get('runId') not in seen, 'Reused scenario run ID')
        seen.add(record.get('runId'))
        first, last = timestamps(record, previous_end, end, 'case')
        previous_end = last
        case = catalog['cases'][record['caseId']]
        verified = verify_case(project, record, case, scenarios[case['scenarioId']], source, suite_root)
        require(runner.json_values_equal(record.get('verified'), verified), 'Suite copied evidence differs from independent verification')
        require(artifacts is None or artifacts == verified['artifacts'], 'Production/companion artifacts changed within cohort')
        artifacts = verified['artifacts']
        if verified['client']:
            require(client is None or client == verified['client'], 'Player artifacts changed within cohort')
            client = verified['client']
    require(source_identity(project) == current, 'Checkout changed during receipt verification')
    return receipt


def capture_tests(project, suite_root, case_id, actor):
    result = []
    roots = {'production': project / 'build/test-results/test'}
    if actor:
        roots['player'] = project / 'dev/player-client/build/test-results/test'
    for kind, root in roots.items():
        destination = suite_root / case_id / kind
        destination.mkdir(parents=True)
        for path in sorted(root.glob('TEST-*.xml')):
            target = destination / path.name
            shutil.copyfile(path, target)
            result.append({'kind': kind, 'path': target.relative_to(project).as_posix(), 'sha256': runner.sha256(target)})
    return result


def run_child(command, project, log_path):
    """Forward cancellation to the owned runner; it retains its lease until JVM cleanup."""
    require(sys.platform == 'linux', 'Owned Paper runner processes require Linux/WSL')
    process = None
    with log_path.open('w', encoding='utf-8') as log:
        try:
            blocked = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
            try:
                process = subprocess.Popen(command, cwd=project, stdout=log, stderr=subprocess.STDOUT,
                                           start_new_session=True,
                                           preexec_fn=lambda: signal.pthread_sigmask(signal.SIG_SETMASK, set()))
            finally:
                signal.pthread_sigmask(signal.SIG_SETMASK, blocked)
            return process.wait()
        except KeyboardInterrupt:
            if process is not None and process.poll() is None:
                # Signal only the runner PID: its own finally stops the client then Paper.
                process.send_signal(signal.SIGTERM)
                previous = {item: signal.signal(item, signal.SIG_IGN) for item in (signal.SIGTERM, signal.SIGINT)}
                try:
                    process.wait()
                finally:
                    for item, handler in previous.items():
                        signal.signal(item, handler)
            raise


def execute(args):
    project = args.project.resolve()
    selected = selection(project, args.suite, args.changed_since)
    if args.plan:
        print(json.dumps({'schemaVersion': 1, 'kind': 'paper-suite-plan', 'state': 'planned',
                          'selection': selected}, indent=2))
        return 0
    require(sys.platform == 'linux', 'Run actual suites on Linux/WSL')
    require(selected['caseIds'], 'No runtime cases selected; this is a plan, not completed test evidence')
    source = source_identity(project)
    catalog, scenarios = load_catalog(project)
    suite_id = uuid.uuid4().hex
    root = project / 'build/reports/agent-paper-suites' / suite_id
    root.mkdir(parents=True)
    path = root / 'receipt.json'
    receipt = {'schemaVersion': 1, 'kind': 'paper-suite-receipt', 'suiteRunId': suite_id,
               'state': 'running', 'passed': False, 'startedAtEpochMs': int(time.time() * 1000),
               'source': source, 'selection': selected, 'cases': []}
    print('SUITE ' + suite_id + ': ' + str(path), flush=True)
    runner.atomic_json(path, receipt)
    record = None
    try:
        for case_id in selected['caseIds']:
            require(source_identity(project) == source, 'Checkout changed before suite case')
            case = catalog['cases'][case_id]
            record = {'caseId': case_id, 'scenarioId': case['scenarioId'], 'startedAtEpochMs': int(time.time() * 1000)}
            log_path = root / (case_id + '.log')
            command = [sys.executable, str(project / 'scripts/agent-tests/paper_test.py'), '--project', str(project),
                       '--scenario', case['scenarioId'], '--scenario-timeout', str(case.get('scenarioTimeout', 60))]
            if case.get('testPlayer'):
                command += ['--test-player', case['testPlayer'], '--player-control', case.get('playerControl', 'calibrate')]
            for option in ('eula_file', 'lease_directory', 'java_home', 'paper_jar', 'memory_mib',
                           'startup_timeout', 'lease_timeout', 'resource_timeout'):
                value = getattr(args, option)
                if value is not None:
                    command += ['--' + option.replace('_', '-'), str(value)]
            print('Running ' + case_id, flush=True)
            record['exitCode'] = run_child(command, project, log_path)
            record['completedAtEpochMs'] = int(time.time() * 1000)
            require(source_identity(project) == source, 'Checkout changed during suite case')
            matches = re.findall(r'^RUN ([0-9a-f]{32}): ([a-z0-9-]+); reports: ', log_path.read_text(), re.MULTILINE)
            require(len(matches) == 1 and matches[0][1] == case['scenarioId'], 'Runner did not identify one fresh scenario run; see ' + str(log_path))
            record['runId'] = matches[0][0]
            reports = project / 'build/reports/agent-paper' / record['runId']
            for kind in ('result', 'scenario', *(['player'] if case.get('testPlayer') else [])):
                report = reports / (kind + '.json')
                require(report.is_file(), 'Runner evidence is missing: ' + str(report))
                record[kind + 'Path'] = report.relative_to(project).as_posix()
                record[kind + 'Sha256'] = runner.sha256(report)
                if kind == 'result':
                    reject_unstarted_run(runner.strict_json(report), record, report)
            for kind in ('server', 'build'):
                log = reports / (kind + '.log')
                require(log.is_file(), 'Runner log is missing: ' + str(log))
                record[kind + 'LogPath'] = log.relative_to(project).as_posix()
                record[kind + 'LogSha256'] = runner.sha256(log)
            record['testEvidence'] = capture_tests(project, root, case_id, bool(case.get('testPlayer')))
            record['verified'] = verify_case(project, record, case, scenarios[case['scenarioId']], source, root)
            receipt['cases'].append(record)
            runner.atomic_json(path, receipt)
        require(source_identity(project) == source, 'Checkout changed after suite execution')
        receipt.update(state='complete', passed=True, completedAtEpochMs=int(time.time() * 1000))
        runner.atomic_json(path, receipt)
        validate_suite_receipt(project, path)
        print('PASS: ' + str(path), flush=True)
        return 0
    except (Exception, KeyboardInterrupt) as error:
        if record is not None:
            receipt['failedCase'] = record
        receipt.update(state='failed', passed=False, completedAtEpochMs=int(time.time() * 1000),
                       error=str(error) or type(error).__name__)
        runner.atomic_json(path, receipt)
        print('FAIL: ' + receipt['error'] + '\nReceipt: ' + str(path), file=sys.stderr, flush=True)
        return 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=Path.cwd())
    parser.add_argument('--suite', action='append', choices=['all', 'regression', 'harness-controls'])
    parser.add_argument('--changed-since')
    parser.add_argument('--plan', action='store_true', help='Print selection only; never produces acceptance evidence')
    parser.add_argument('--validate', type=Path, help='Independently revalidate a completed receipt')
    for option in ('eula-file', 'lease-directory', 'java-home', 'paper-jar'):
        parser.add_argument('--' + option, type=Path)
    for option in ('memory-mib', 'startup-timeout', 'lease-timeout', 'resource-timeout'):
        parser.add_argument('--' + option, type=int)
    args = parser.parse_args()
    def interrupted(signum, frame):
        raise KeyboardInterrupt('Interrupted by signal ' + str(signum))
    signal.signal(signal.SIGTERM, interrupted)
    try:
        if args.validate:
            require(not args.plan and not args.suite and not args.changed_since, 'Receipt verification is separate from selection')
            receipt = validate_suite_receipt(args.project, args.validate)
            print('PASS: verified suite ' + receipt['suiteRunId'])
            return 0
        return execute(args)
    except (ValidationError, ValueError, OSError, subprocess.SubprocessError) as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
