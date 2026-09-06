"""Failure-boundary tests for suite selection and independently replayed evidence."""
import copy
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

import paper_suite as suite


class ReceiptFixture:
    def __init__(self, root):
        self.root = root
        self.run_id, self.suite_id = 'a' * 32, 'b' * 32
        self.write('.gitignore', 'build/\nrun/\n*.ignored\n')
        self.write('src/main/Example.java', 'class Example {}\n')
        self.write('src/main/resources/help.md', 'Packaged documentation\n')
        self.write('README.md', 'Human instructions\n')
        self.write('versions.properties', 'javaVersion=25\nminecraftVersion=26.2\npaperBuild=121\npaperSha256=' + suite.hashlib.sha256(b'paper').hexdigest() + '\n')
        self.descriptor = {'mechanicRevision': 'fixture-v1', 'requiredAssertions': ['feature', *suite.CLEANUP]}
        self.json('dev/game-tests/scenarios.json', {'fixture': self.descriptor})
        self.catalog = {'schemaVersion': 1, 'cases': {'fixture': {'scenarioId': 'fixture', 'expectation': 'positive'}},
                        'suites': {'all': ['fixture'], 'regression': ['fixture'], 'harness-controls': []},
                        'areas': {'feature': {'paths': ['src/*'], 'cases': ['fixture'], 'affects': []}}, 'ignoredChanges': []}
        self.json('dev/game-tests/suites.json', self.catalog)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'core.autocrlf', 'false')
        self.commit()
        self.source = suite.source_identity(root)
        self.profile = root / 'run/agent-tests' / self.run_id
        self.reports = root / 'build/reports/agent-paper' / self.run_id
        self.suite_root = root / 'build/reports/agent-paper-suites' / self.suite_id
        self.path = self.suite_root / 'receipt.json'
        now = int(time.time() * 1000)
        self.start, self.end = now - 10000, now - 1000
        self.write(self.profile / 'server.jar', b'paper')
        self.write(self.profile / 'plugins/OnlyDragons.jar', b'production')
        self.write(self.profile / 'plugins/OnlyDragonsGameTests.jar', b'companion')
        self.write(self.profile / 'server.properties', 'server-ip=127.0.0.1\nserver-port=45678\nonline-mode=true\nenable-rcon=false\nenable-query=false\nenable-jmx-monitoring=false\nlevel-name=agent-world-' + self.run_id + '\n')
        self.write(self.reports / 'server.log', 'Done (1.0s)!\nStopping server\n')
        self.write(self.reports / 'build.log', 'BUILD SUCCESSFUL\n')
        test_path = self.suite_root / 'fixture/production/TEST-Sample.xml'
        self.write(test_path, '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="Sample" name="boundary"/></testsuite>')
        self.scenario = {'schemaVersion': 1, 'runId': self.run_id, 'scenarioId': 'fixture', 'mechanicRevision': 'fixture-v1',
                         'state': 'complete', 'syntheticActors': True, 'passed': True,
                         'startedAtEpochMs': self.start + 2000, 'completedAtEpochMs': self.end - 2000,
                         'server': {'minecraftVersion': '26.2', 'paperVersion': '26.2-121-a2a42c5 (MC: 26.2)'},
                         'assertions': [{'id': 'feature', 'expected': True, 'observed': True, 'passed': True}]
                                       + [{'id': key, 'expected': 0, 'observed': 0, 'passed': True} for key in suite.CLEANUP]}
        self.result = {'schemaVersion': 1, 'runId': self.run_id, 'scenarioId': 'fixture', 'revision': self.source['revision'],
                       'worktreeDirty': False, 'pins': suite.runner.properties(root / 'versions.properties'), 'javaVersion': '25.0.4.1',
                       'startedAtEpochMs': self.start + 1000, 'completedAtEpochMs': self.end - 1000, 'passed': True, 'error': None,
                       'cleanup': {'clean': True, 'forced': False, 'exitCode': 0}, 'scenario': self.scenario,
                       'memory': suite.runner.assess_memory(1536, {'MemAvailable': 5000}),
                       'profile': {'directory': str(self.profile), 'port': 45678, 'world': 'agent-world-' + self.run_id,
                                   'authentication': 'authenticated', 'testPlayerMode': None},
                       'artifacts': {'productionSha256': suite.runner.sha256(self.profile / 'plugins/OnlyDragons.jar'),
                                     'gameTestsSha256': suite.runner.sha256(self.profile / 'plugins/OnlyDragonsGameTests.jar')},
                       'build': {'wrapperInvoked': True, 'unitTests': {'tests': 1, 'failures': 0, 'errors': 0, 'skipped': 0}}}
        self.record = {'caseId': 'fixture', 'scenarioId': 'fixture', 'runId': self.run_id, 'exitCode': 0,
                       'startedAtEpochMs': self.start, 'completedAtEpochMs': self.end,
                       'testEvidence': [{'kind': 'production', 'path': test_path.relative_to(root).as_posix(), 'sha256': suite.runner.sha256(test_path)}]}
        self.receipt = {'schemaVersion': 1, 'kind': 'paper-suite-receipt', 'suiteRunId': self.suite_id, 'state': 'complete', 'passed': True,
                        'startedAtEpochMs': self.start, 'completedAtEpochMs': self.end, 'source': self.source,
                        'selection': suite.selection(root), 'cases': [self.record]}
        self.refresh()

    def write(self, path, data):
        path = self.root / path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data if isinstance(data, bytes) else data.encode())

    def json(self, path, value):
        self.write(path, json.dumps(value))

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, stderr=subprocess.DEVNULL)

    def commit(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'Fixture inputs')

    def refresh(self):
        self.json(self.reports / 'scenario.json', self.scenario)
        self.json(self.reports / 'result.json', self.result)
        for key, filename in [('scenario', 'scenario.json'), ('result', 'result.json'), ('serverLog', 'server.log'), ('buildLog', 'build.log')]:
            path = self.reports / filename
            self.record[key + 'Path'] = path.relative_to(self.root).as_posix()
            self.record[key + 'Sha256'] = suite.runner.sha256(path)
        self.record['verified'] = {'assertions': len(self.scenario['assertions']), 'unitTests': self.result['build']['unitTests'],
                                   'artifacts': self.result['artifacts'], 'client': None, 'expectedOutcome': 'positive'}
        self.json(self.path, self.receipt)

    def validate(self):
        with patch.object(suite, 'process_cleanup'):
            return suite.validate_suite_receipt(self.root, self.path)


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture = ReceiptFixture(Path(self.temp.name))

    def rejected(self, mutation, refresh=True):
        mutation()
        if refresh:
            self.fixture.refresh()
        with self.assertRaises(suite.ValidationError):
            self.fixture.validate()

    def test_complete_evidence_replays_and_documentation_commit_remains_valid(self):
        f = self.fixture
        self.assertTrue(f.validate()['passed'])
        f.write('README.md', 'Changed human instructions\n')
        f.commit()
        self.assertTrue(f.validate()['passed'])

    def test_native_path_alias_replays_receipt_and_profile_with_canonical_project(self):
        f = self.fixture
        path = f.path
        if sys.platform == 'win32':
            import ctypes
            kernel = ctypes.WinDLL('kernel32', use_last_error=True)
            short_name = kernel.GetShortPathNameW
            short_name.argtypes = [ctypes.c_wchar_p, ctypes.c_wchar_p, ctypes.c_uint32]
            short_name.restype = ctypes.c_uint32
            def alias(value):
                buffer = ctypes.create_unicode_buffer(32768)
                length = short_name(str(value), buffer, len(buffer))
                self.assertTrue(0 < length < len(buffer), 'Windows could not resolve an existing path alias')
                return Path(buffer.value)
            path = alias(f.path)
            f.result['profile']['directory'] = str(alias(f.profile))
            f.refresh()
        with patch.object(suite, 'process_cleanup'):
            self.assertTrue(suite.validate_suite_receipt(f.root.resolve(), path)['passed'])

    def test_path_normalization_preserves_containment_and_linux_symlink_rejection(self):
        f = self.fixture
        with self.assertRaisesRegex(suite.ValidationError, 'escapes checkout'):
            suite.resolved_evidence_path(f.root, f.root.parent / 'outside-receipt.json')
        if sys.platform == 'linux':
            link = f.root / 'build/alias'
            link.symlink_to(f.suite_root, target_is_directory=True)
            with self.assertRaisesRegex(suite.ValidationError, 'Symlink'):
                suite.validate_suite_receipt(f.root, link / 'receipt.json')
    def test_missing_duplicate_or_planned_cases_cannot_pass(self):
        f = self.fixture
        for cases in ([], [f.record, f.record]):
            with self.subTest(cases=len(cases)):
                self.rejected(lambda: f.receipt.update(cases=cases))
        f.receipt['cases'] = [f.record]
        self.rejected(lambda: f.receipt.update(kind='paper-suite-plan'))

    def test_artifact_mutation_is_rejected(self):
        self.rejected(lambda: self.fixture.write(self.fixture.profile / 'plugins/OnlyDragons.jar', 'different bytecode'))

    def test_raw_report_or_log_mutation_is_rejected_even_if_receipt_passed(self):
        f = self.fixture
        self.rejected(lambda: f.write(f.reports / 'scenario.json', '{}'), refresh=False)
        f.refresh()
        self.rejected(lambda: f.write(f.reports / 'server.log', 'tampered'), refresh=False)

    def test_forged_boolean_numeric_assertion_is_rejected(self):
        self.rejected(lambda: self.fixture.scenario['assertions'][0].update(observed=1))

    def test_clean_shutdown_flag_cannot_hide_forcing_or_nonzero_exit(self):
        f = self.fixture
        self.rejected(lambda: f.result['cleanup'].update(forced=True))
        f.result['cleanup']['forced'] = False
        self.rejected(lambda: f.result['cleanup'].update(exitCode=1))

    def test_busy_and_unexpected_runner_failure_are_not_expected_negatives(self):
        self.rejected(lambda: self.fixture.result.update(busy=True))

    def test_missing_or_insufficient_memory_gate_evidence_fails(self):
        self.rejected(lambda: self.fixture.result['memory'].update(requiredMiB=1))

    def test_copied_summary_boolean_does_not_equal_numeric_test_count(self):
        f = self.fixture
        f.record['verified'] = copy.deepcopy(f.record['verified'])
        f.record['verified']['unitTests']['tests'] = True
        f.json(f.path, f.receipt)
        with self.assertRaises(suite.ValidationError):
            f.validate()

    def test_junit_skip_and_forged_counts_fail(self):
        f = self.fixture
        path = f.root / f.record['testEvidence'][0]['path']
        f.write(path, '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="Sample" name="boundary"><skipped/></testcase></testsuite>')
        f.record['testEvidence'][0]['sha256'] = suite.runner.sha256(path)
        self.rejected(lambda: None)

    def test_duplicate_test_evidence_cannot_inflate_test_counts(self):
        self.rejected(lambda: self.fixture.record['testEvidence'].append(self.fixture.record['testEvidence'][0]))

    def test_runtime_resource_markdown_invalidates_receipt(self):
        f = self.fixture
        f.write('src/main/resources/help.md', 'Changed packaged bytes\n')
        f.commit()
        self.rejected(lambda: None)

    def test_dirty_untracked_and_ignored_sources_rejected(self):
        f = self.fixture
        for path in ('src/main/Example.java', 'src/main/New.java', 'src/main/Extra.ignored'):
            with self.subTest(path=path):
                f.write(path, 'runtime input')
                with self.assertRaises(suite.ValidationError):
                    suite.source_identity(f.root)
                if path.endswith('Example.java'):
                    f.git('checkout', '--', path)
                else:
                    (f.root / path).unlink()

    def test_hidden_index_flag_rejected(self):
        f = self.fixture
        f.git('update-index', '--assume-unchanged', 'src/main/Example.java')
        f.write('src/main/Example.java', 'Hidden mutation')
        with self.assertRaises(suite.ValidationError):
            suite.source_identity(f.root)

    def test_wrong_revision_or_stale_window_fails(self):
        f = self.fixture
        self.rejected(lambda: f.result.update(revision='0' * 40))
        f.result['revision'] = f.source['revision']
        self.rejected(lambda: f.scenario.update(startedAtEpochMs=f.start - 1000))

    def test_outside_checkout_path_rejected(self):
        f = self.fixture
        f.record['resultPath'] = '../elsewhere/result.json'
        f.json(f.path, f.receipt)
        with self.assertRaises(suite.ValidationError):
            f.validate()


class SelectionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'dev/game-tests').mkdir(parents=True)
        source = Path(__file__).resolve().parents[2]
        for name in ('suites.json', 'scenarios.json'):
            shutil.copyfile(source / 'dev/game-tests' / name, self.root / 'dev/game-tests' / name)

    def test_stats_changes_require_transitive_item_and_combat(self):
        selected = suite.required_cases(self.root, ['src/main/java/com/kaveenk/onlydragons/domain/stats/StatResolver.java'])
        self.assertTrue({'foundation-contracts', 'stats-resolution', 'item-identity', 'combat-accounting'}.issubset(selected))

    def test_unknown_gameplay_fails_instead_of_selecting_zero(self):
        with self.assertRaisesRegex(suite.ValidationError, 'no scenario coverage'):
            suite.required_cases(self.root, ['src/main/java/com/kaveenk/onlydragons/paper/newfeature/Missing.java'])

    def test_docs_only_and_deleted_files_are_explicitly_distinguished(self):
        self.assertEqual([], suite.required_cases(self.root, ['docs/planning/02-foundation-plan.md']))
        self.assertIn('stats-resolution', suite.required_cases(self.root, ['src/main/resources/stats/deleted.properties']))

    def test_harness_changes_require_every_positive_and_negative_case(self):
        catalog, _ = suite.load_catalog(self.root)
        self.assertEqual(set(catalog['cases']), set(suite.required_cases(self.root, ['scripts/agent-tests/paper_suite.py'])))
        self.assertEqual(set(catalog['cases']), set(suite.required_cases(self.root, ['dev/game-tests/acceptance.json'])))

    def test_unclassified_new_scenario_fails_catalog(self):
        path = self.root / 'dev/game-tests/scenarios.json'
        data = json.loads(path.read_text())
        data['unclassified'] = {'mechanicRevision': 'new-v1', 'requiredAssertions': ['proof']}
        path.write_text(json.dumps(data))
        with self.assertRaisesRegex(suite.ValidationError, 'explicit positive/negative'):
            suite.load_catalog(self.root)

    def test_regression_cannot_silently_omit_a_classified_positive(self):
        path = self.root / 'dev/game-tests/suites.json'
        data = json.loads(path.read_text())
        data['suites']['regression'].pop()
        path.write_text(json.dumps(data))
        with self.assertRaisesRegex(suite.ValidationError, 'every classified case'):
            suite.load_catalog(self.root)

    def test_mapped_runtime_area_cannot_claim_empty_coverage(self):
        path = self.root / 'dev/game-tests/suites.json'
        data = json.loads(path.read_text())
        data['areas']['stats']['cases'] = []
        path.write_text(json.dumps(data))
        with self.assertRaisesRegex(suite.ValidationError, 'nonempty scenario coverage'):
            suite.load_catalog(self.root)

    def test_harness_mapping_cannot_omit_new_or_existing_cases(self):
        path = self.root / 'dev/game-tests/suites.json'
        data = json.loads(path.read_text())
        data['areas']['harness-and-build']['cases'].pop()
        path.write_text(json.dumps(data))
        with self.assertRaisesRegex(suite.ValidationError, 'every catalog case'):
            suite.load_catalog(self.root)

    def test_shared_contract_mapping_cannot_omit_any_catalog_case(self):
        path = self.root / 'dev/game-tests/suites.json'
        data = json.loads(path.read_text())
        data['areas']['shared-contracts']['cases'].remove('protocol-player-idle')
        path.write_text(json.dumps(data))
        with self.assertRaisesRegex(suite.ValidationError, 'shared-contracts changes require every catalog case'):
            suite.load_catalog(self.root)

    def test_shared_contract_types_require_full_baseline_across_feature_packages(self):
        catalog, _ = suite.load_catalog(self.root)
        paths = ['application/TickClock.java', 'domain/DomainChecks.java', 'domain/stats/StatSnapshot.java',
                 'domain/item/WeaponDefinition.java', 'domain/projectile/ShotContext.java',
                 'domain/combat/DamageResult.java', 'domain/encounter/TargetState.java']
        for path in paths:
            with self.subTest(path=path):
                required = suite.required_cases(self.root, ['src/main/java/com/kaveenk/onlydragons/' + path])
                self.assertEqual(set(catalog['cases']), set(required))


class NegativePolicyTests(unittest.TestCase):
    def setUp(self):
        self.now = int(time.time() * 1000)
        self.pins = {'minecraftVersion': '26.2', 'paperBuild': '121',
                     'testPlayerProtocolLib': 'org.geysermc.mcprotocollib:protocol:26.2-20260824.124638-17',
                     'testPlayerProtocolLibSha256': 'c' * 64, 'testPlayerProtocolVersion': '776'}
        self.run_id = 'a' * 32

    def scenario(self, expectation):
        rows = [{'id': key, 'expected': 0, 'observed': 0, 'passed': True} for key in suite.CLEANUP]
        rows.append({'id': 'deliberate_failure', 'expected': 1, 'observed': 0, 'passed': False})
        return {'schemaVersion': 1, 'runId': self.run_id, 'scenarioId': 'deliberate-failure',
                'mechanicRevision': 'harness-v1', 'state': 'complete', 'syntheticActors': True, 'passed': False,
                'startedAtEpochMs': self.now - 1000, 'completedAtEpochMs': self.now,
                'server': {'minecraftVersion': '26.2', 'paperVersion': '26.2-121-a2a42c5'}, 'assertions': rows}

    def test_only_exact_expected_failure_is_accepted(self):
        report = self.scenario('deliberate-failure')
        case = {'scenarioId': 'deliberate-failure', 'expectation': 'deliberate-failure'}
        descriptor = {'mechanicRevision': 'harness-v1', 'requiredAssertions': [row['id'] for row in report['assertions']]}
        suite.verify_scenario(report, case, descriptor, self.run_id, self.pins, self.now - 2000, self.now)
        for mutation in ({'expected': 1, 'observed': 1, 'passed': True}, {'expected': 1, 'observed': 2, 'passed': False}):
            with self.subTest(mutation=mutation):
                candidate = copy.deepcopy(report)
                candidate['assertions'][-1].update(mutation)
                with self.assertRaises(suite.ValidationError):
                    suite.verify_scenario(candidate, case, descriptor, self.run_id, self.pins, self.now - 2000, self.now)

    def player(self):
        return {'schemaVersion': 1, 'runId': self.run_id, 'username': 'od_' + self.run_id[:13],
                'authentication': 'offline-disposable-loopback', 'artifact': self.pins['testPlayerProtocolLib'],
                'minecraftVersion': '26.2', 'protocolVersion': 776, 'loginReceived': True,
                'playerLoadedSent': False, 'teleportsAcknowledged': 0, 'disconnected': True,
                'actions': [], 'passed': False, 'error': 'Deliberate early client exit',
                'startedAtEpochMs': self.now - 1000, 'completedAtEpochMs': self.now}

    def test_early_exit_can_precede_loading_but_cannot_hide_wrong_failure(self):
        report = self.player()
        case = {'expectation': 'player-early-exit'}
        suite.verify_player(report, case, self.run_id, self.pins, self.now - 2000, self.now)
        report['error'] = 'Authentication failed'
        with self.assertRaises(suite.ValidationError):
            suite.verify_player(report, case, self.run_id, self.pins, self.now - 2000, self.now)


    def test_idle_requires_real_loaded_player_and_exact_timeout_or_cleanup(self):
        report = self.player()
        report.update(playerLoadedSent=True, teleportsAcknowledged=1, error='Timed out waiting for calibration')
        case = {'expectation': 'player-idle'}
        suite.verify_player(report, case, self.run_id, self.pins, self.now - 2000, self.now)
        report['actions'] = ['draw']
        with self.assertRaises(suite.ValidationError):
            suite.verify_player(report, case, self.run_id, self.pins, self.now - 2000, self.now)

    def idle_scenario(self, abort):
        report = self.scenario('player-idle')
        report.update(scenarioId='protocol-player-calibration', mechanicRevision='protocol-player-v1')
        setup = dict.fromkeys(suite.PLAYER_SETUP, True)
        setup.update(disposable_protocol_mode='protocol-calibration', offline_fixture=False,
                     player_uuid='a9a0f02c-aa22-4c00-8d88-630f222ca009', player_not_op=False)
        rows = [{'id': key, 'expected': value, 'observed': value, 'passed': True} for key, value in setup.items()]
        rows.append({'id': 'real_player_quit', 'expected': True, 'observed': False, 'passed': False})
        if abort:
            rows.append({'id': 'scenario_exception', 'expected': 'no exception', 'observed': suite.ABORT, 'passed': False})
        else:
            rows.append({'id': 'player_removed_after_quit', 'expected': True, 'observed': True, 'passed': True})
        rows.extend({'id': key, 'expected': 0, 'observed': 0, 'passed': True} for key in suite.CLEANUP)
        report['assertions'] = rows
        return report

    def verify_idle_scenario(self, report):
        case = {'scenarioId': 'protocol-player-calibration', 'expectation': 'player-idle', 'scenarioTimeout': 15}
        descriptor = {'mechanicRevision': 'protocol-player-v1',
                      'requiredAssertions': [*suite.PLAYER_SETUP, *suite.CLEANUP, 'native_projectile_shooter']}
        return suite.verify_scenario(report, case, descriptor, self.run_id, self.pins, self.now - 2000, self.now)

    def test_idle_accepts_server_abort_after_client_disconnect(self):
        self.assertEqual(14, self.verify_idle_scenario(self.idle_scenario(abort=True)))

    def test_idle_accepts_quit_completion_before_server_abort(self):
        self.assertEqual(14, self.verify_idle_scenario(self.idle_scenario(abort=False)))

    def test_idle_rejects_unrelated_failure_in_either_cleanup_order(self):
        for abort in (False, True):
            with self.subTest(abort=abort):
                report = self.idle_scenario(abort)
                report['assertions'].append({'id': 'other_failure', 'expected': 1, 'observed': 2, 'passed': False})
                with self.assertRaisesRegex(suite.ValidationError, 'differs from intended negative'):
                    self.verify_idle_scenario(report)

    def test_idle_without_abort_requires_successful_logout_evidence(self):
        for removed in (None, False):
            with self.subTest(removed=removed):
                report = self.idle_scenario(abort=False)
                row = next(row for row in report['assertions'] if row['id'] == 'player_removed_after_quit')
                if removed is None:
                    report['assertions'].remove(row)
                else:
                    row.update(expected=False, observed=False)
                with self.assertRaises(suite.ValidationError):
                    self.verify_idle_scenario(report)


class ChildOwnershipTests(unittest.TestCase):
    def test_unsupported_platform_rejects_execution_before_opening_log_or_spawning(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            with patch.object(suite.sys, 'platform', 'win32'), patch.object(suite.subprocess, 'Popen') as spawn:
                with self.assertRaisesRegex(suite.ValidationError, 'require Linux/WSL'):
                    suite.run_child([sys.executable, '-c', 'raise SystemExit(0)'], root, root / 'runner.log')
                spawn.assert_not_called()
                self.assertFalse((root / 'runner.log').exists())

    def test_runner_platform_boundary_and_linux_owned_process_cleanup(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            if sys.platform != 'linux':
                # This platform verifies rejection only; actual signal/cleanup evidence is Linux-only.
                with self.assertRaisesRegex(suite.ValidationError, 'require Linux/WSL'):
                    suite.run_child([sys.executable, '-c', 'raise SystemExit(0)'], root, root / 'runner.log')
                self.assertFalse((root / 'runner.log').exists())
                return
            child = root / 'runner.py'
            child.write_text('import signal,time\nfrom pathlib import Path\n'
                             'def stop(signum,frame):\n Path("cleaned").write_text("done")\n raise SystemExit(0)\n'
                             'signal.signal(signal.SIGTERM,stop)\nPath("ready").touch()\ntime.sleep(30)\n')
            unrelated = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])
            def interrupt_when_ready():
                deadline = time.monotonic() + 5
                while not (root / 'ready').exists() and time.monotonic() < deadline:
                    time.sleep(0.01)
                os.kill(os.getpid(), signal.SIGINT)
            thread = threading.Thread(target=interrupt_when_ready)
            thread.start()
            try:
                with self.assertRaises(KeyboardInterrupt):
                    suite.run_child([sys.executable, str(child)], root, root / 'runner.log')
                self.assertEqual('done', (root / 'cleaned').read_text())
                self.assertIsNone(unrelated.poll())
            finally:
                thread.join()
                unrelated.terminate()
                unrelated.wait(timeout=5)

if __name__ == '__main__':
    unittest.main()
