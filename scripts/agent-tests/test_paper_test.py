"""Failure-contract tests: no Paper instance, EULA mutation, or external services required."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('paper_test', Path(__file__).with_name('paper_test.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class ReportContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'report.json'
        self.now = int(time.time() * 1000)
        self.expected = {'runId': 'a' * 32, 'scenarioId': 'calibration', 'mechanicRevision': 'harness-v1',
                         'minecraftVersion': '26.2', 'paperBuild': '121', 'requiredAssertions': ['one', 'cleanup']}
        self.report = {**{key: self.expected[key] for key in ('runId', 'scenarioId', 'mechanicRevision')},
                       'schemaVersion': 1, 'state': 'complete', 'passed': True, 'syntheticActors': True,
                       'startedAtEpochMs': self.now, 'completedAtEpochMs': self.now,
                       'server': {'minecraftVersion': '26.2', 'paperVersion': '26.2-121-main@abcd'},
                       'assertions': [{'id': 'one', 'expected': 1, 'observed': 1, 'passed': True},
                                      {'id': 'cleanup', 'expected': 0, 'observed': 0, 'passed': True}]}

    def write(self, report=None):
        self.path.write_text(json.dumps(self.report if report is None else report))

    def validate(self):
        return runner.validate_report(self.path, self.expected, self.now, self.now + 10000, self.now)

    def test_complete_actual_run_passes(self):
        self.write()
        self.assertTrue(self.validate()['passed'])

    def test_missing_report_fails(self):
        with self.assertRaisesRegex(runner.ValidationError, 'Missing scenario'):
            self.validate()

    def test_old_run_or_scenario_cannot_be_reused(self):
        for field in ('runId', 'scenarioId', 'mechanicRevision'):
            with self.subTest(field=field):
                bad = dict(self.report, **{field: 'stale'})
                self.write(bad)
                with self.assertRaisesRegex(runner.ValidationError, 'Wrong/stale'):
                    self.validate()

    def test_incomplete_and_empty_assertions_fail(self):
        for bad in (dict(self.report, state='running'), dict(self.report, assertions=[]),
                    dict(self.report, assertions=self.report['assertions'][:1])):
            with self.subTest(report=bad):
                self.write(bad)
                with self.assertRaises(runner.ValidationError):
                    self.validate()

    def test_explicit_failure_and_forged_pass_flag_fail(self):
        for passed in (True, False):
            bad = copy.deepcopy(self.report)
            bad['assertions'][0].update(observed=0, passed=passed)
            self.write(bad)
            with self.assertRaisesRegex(runner.ValidationError, 'Failed scenario assertions: one'):
                self.validate()

    def test_boolean_and_numeric_assertions_never_match(self):
        for boolean, number in ((True, 1), (True, 1.0), (False, 0), (False, 0.0)):
            for expected, observed in ((boolean, number), (number, boolean)):
                with self.subTest(expected=expected, observed=observed):
                    report = copy.deepcopy(self.report)
                    report['assertions'][0].update(expected=expected, observed=observed, passed=True)
                    self.write(report)
                    with self.assertRaisesRegex(runner.ValidationError, 'Failed scenario assertions: one'):
                        self.validate()

    def test_nested_boolean_numeric_mismatches_fail_despite_forged_flags(self):
        for expected, observed in (([True], [1]), ({'value': False}, {'value': 0.0}),
                                   ({'values': [1, {'active': True}]}, {'values': [1.0, {'active': 1}]})):
            for left, right in ((expected, observed), (observed, expected)):
                with self.subTest(expected=left, observed=right):
                    report = copy.deepcopy(self.report)
                    report['assertions'][0].update(expected=left, observed=right, passed=True)
                    self.write(report)
                    with self.assertRaisesRegex(runner.ValidationError, 'Failed scenario assertions: one'):
                        self.validate()

    def test_matching_json_types_and_equivalent_numeric_values_pass(self):
        for expected, observed in ((True, True), (False, False), (None, None), ('1', '1'),
                                   (1, 1.0), (0.0, 0), ([], []), ({}, {}),
                                   ({'active': True, 'values': [None, 2, {'fraction': 0.5}]},
                                    {'values': [None, 2.0, {'fraction': 0.5}], 'active': True})):
            with self.subTest(expected=expected, observed=observed):
                report = copy.deepcopy(self.report)
                report['assertions'][0].update(expected=expected, observed=observed, passed=True)
                self.write(report)
                self.assertTrue(self.validate()['passed'])

    def test_recursive_comparison_preserves_structure_order_and_value_checks(self):
        for expected, observed in (('1', 1), (None, False), ([1], 1), ([1], [1, 2]),
                                   ([1, 2], [2, 1]), ({'one': 1}, {'two': 1}),
                                   ({'values': [1]}, {'values': [2]}), (True, False)):
            with self.subTest(expected=expected, observed=observed):
                report = copy.deepcopy(self.report)
                report['assertions'][0].update(expected=expected, observed=observed, passed=True)
                self.write(report)
                with self.assertRaisesRegex(runner.ValidationError, 'Failed scenario assertions: one'):
                    self.validate()

    def test_false_overall_result_fails_even_if_assertions_pass(self):
        self.write(dict(self.report, passed=False))
        with self.assertRaisesRegex(runner.ValidationError, 'reports failure'):
            self.validate()

    def test_duplicate_assertion_and_duplicate_json_key_fail(self):
        self.write(dict(self.report, assertions=self.report['assertions'] * 2))
        with self.assertRaisesRegex(runner.ValidationError, 'duplicate assertion'):
            self.validate()
        self.path.write_text('{"runId": "old", "runId": "new"}')
        with self.assertRaisesRegex(runner.ValidationError, 'Duplicate JSON key'):
            self.validate()

    def test_stale_timestamps_wrong_server_and_malformed_json_fail(self):
        for changes in ({'startedAtEpochMs': self.now - 1}, {'completedAtEpochMs': self.now + 20000},
                        {'server': {'minecraftVersion': '1.21', 'paperVersion': '1.21-121'}},
                        {'server': {'minecraftVersion': '26.2', 'paperVersion': '26.2-120-main'}}):
            self.write(dict(self.report, **changes))
            with self.assertRaises(runner.ValidationError):
                self.validate()
        self.path.write_text('{"state":')
        with self.assertRaisesRegex(runner.ValidationError, 'Malformed'):
            self.validate()

    def test_absent_result_times_out_and_early_exit_fails(self):
        process = Mock()
        process.poll.return_value = None
        with self.assertRaisesRegex(runner.ValidationError, 'Timed out'):
            runner.wait_for_report(self.path, self.expected, self.now, 0.01, process)
        process.poll.return_value = 1
        with self.assertRaisesRegex(runner.ValidationError, 'exited before'):
            runner.wait_for_report(self.path, self.expected, self.now, 5, process)

    def test_stale_file_mtime_fails(self):
        self.write()
        os.utime(self.path, (0, 0))
        with self.assertRaisesRegex(runner.ValidationError, 'Stale scenario file'):
            self.validate()

    def test_report_arriving_after_deadline_fails_even_with_early_timestamps(self):
        self.write()
        with patch.object(runner.time, 'monotonic', side_effect=[0, 11]):
            with self.assertRaisesRegex(runner.ValidationError, 'after its deadline'):
                runner.wait_for_report(self.path, self.expected, self.now, 10, Mock())

    def test_exponent_overflow_is_rejected_in_assertions_and_nested_observations(self):
        self.write()
        for text in ('{"observations":{"nested":[1e999]}}', '{"expected":1e999,"observed":1e999}', '{"number":NaN}'):
            self.path.write_text(text)
            with self.assertRaisesRegex(runner.ValidationError, 'Non-finite'):
                runner.strict_json(self.path)


class LifecycleContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_absent_or_false_eula_is_never_created_or_accepted(self):
        eula = self.root / 'eula.txt'
        with self.assertRaises(runner.ValidationError):
            runner.accepted_eula(eula)
        self.assertFalse(eula.exists())
        eula.write_text('eula=false\n')
        with self.assertRaises(runner.ValidationError):
            runner.accepted_eula(eula)
        self.assertEqual(eula.read_text(), 'eula=false\n')
        eula.write_text('eula=true\n')
        runner.accepted_eula(eula)

    @unittest.skipUnless(sys.platform == 'linux', 'Linux lease and process semantics')
    def test_shared_lease_blocks_second_runner_and_releases_after_failure(self):
        with runner.server_lease(self.root, 0.01):
            with self.assertRaises(runner.ResourceBusy):
                with runner.server_lease(self.root, 0.01):
                    self.fail('Second runner acquired a held lease')
        with runner.server_lease(self.root, 0.01):
            pass

    def test_memory_wait_is_bounded_and_can_recover(self):
        with patch.object(runner, 'available_memory', side_effect=runner.ResourceBusy('busy')):
            with self.assertRaises(runner.ResourceBusy):
                runner.wait_for_memory(1536, 0)
        with patch.object(runner, 'available_memory', return_value={'linuxAvailableMiB': 9000}):
            self.assertEqual(runner.wait_for_memory(1536, 1)['linuxAvailableMiB'], 9000)

    def test_wsl_resident_cache_can_supply_guest_without_host_expansion(self):
        linux = {'MemAvailable': 7400, 'MemFree': 1100, 'Buffers': 170, 'Cached': 6200, 'SReclaimable': 570, 'Shmem': 450}
        result = runner.assess_memory(1536, linux, 1400)
        self.assertEqual(result['wslDiscountedAllowanceMiB'], 3150)
        self.assertEqual(result['effectiveHostAvailableMiB'], 4550)
        self.assertLess(result['windowsAvailableMiB'], result['requiredMiB'])

    def test_wsl_real_combined_shortage_or_guest_shortage_waits(self):
        linux = {'MemAvailable': 3000, 'MemFree': 2800, 'Buffers': 10, 'Cached': 200, 'SReclaimable': 20, 'Shmem': 30}
        with self.assertRaises(runner.ResourceBusy):
            runner.assess_memory(1536, linux, 1400)
        cached = dict(linux, MemAvailable=8000, Cached=7000)
        self.assertGreaterEqual(runner.assess_memory(1536, cached, 900)['effectiveHostAvailableMiB'], 2560)
        with self.assertRaises(runner.ResourceBusy):
            runner.assess_memory(1536, dict(linux, MemAvailable=2000), 10000)

    def test_native_linux_uses_guest_available_without_windows_assumptions(self):
        result = runner.assess_memory(1536, {'MemAvailable': 3000})
        self.assertNotIn('windowsAvailableMiB', result)
        with self.assertRaises(runner.ResourceBusy):
            runner.assess_memory(1536, {'MemAvailable': 2000})

    def test_windows_probe_failure_does_not_silently_bypass_gate(self):
        with patch.object(runner.subprocess, 'run', side_effect=OSError('probe unavailable')):
            with self.assertRaisesRegex(runner.ValidationError, 'Cannot verify Windows'):
                runner.windows_available_memory()

    def test_changed_build_bytes_cannot_be_deployed_with_old_hash(self):
        source, destination = self.root / 'build.jar', self.root / 'deployed.jar'
        source.write_bytes(b'verified build')
        expected = runner.sha256(source)
        runner.stage_artifact(source, destination, expected)
        self.assertEqual(runner.sha256(destination), expected)
        source.write_bytes(b'rebuilt while waiting')
        with self.assertRaisesRegex(runner.ValidationError, 'Artifact changed after build validation'):
            runner.stage_artifact(source, destination, expected)

    @unittest.skipUnless(sys.platform == 'linux', 'Linux owned process lifecycle')
    def test_failure_cleanup_stops_owned_child_preserves_unrelated_process(self):
        unrelated = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])
        self.addCleanup(lambda: (unrelated.terminate(), unrelated.wait()) if unrelated.poll() is None else None)
        program = 'import sys; print("ready",flush=True); command=sys.stdin.readline(); print("Stopping server",flush=True); sys.exit(0 if command.strip()=="stop" else 2)'
        server = runner.OwnedServer([sys.executable, '-u', '-c', program], self.root, self.root / 'server.log')
        try:
            server.wait_text('ready', 5)
            with self.assertRaises(runner.ValidationError):
                server.wait_text('impossible assertion', 0.01)
        finally:
            cleanup = server.stop(timeout=2)
        self.assertTrue(cleanup['clean'])
        self.assertIsNone(unrelated.poll())
        self.assertEqual(server.stop(), cleanup)

    @unittest.skipUnless(sys.platform == 'linux', 'Linux owned process lifecycle')
    def test_unresponsive_owned_child_is_forced_and_not_reported_clean(self):
        server = runner.OwnedServer([sys.executable, '-c', 'import time; time.sleep(30)'], self.root, self.root / 'unresponsive.log')
        cleanup = server.stop(timeout=0.05)
        self.assertTrue(cleanup['forced'])
        self.assertFalse(cleanup['clean'])
        self.assertIsNotNone(server.process.poll())

    @unittest.skipUnless(sys.platform == 'linux', 'Linux signal lifecycle')
    def test_sigterm_handler_can_finish_owned_child_cleanup(self):
        supervisor = self.root / 'supervisor.py'
        supervisor.write_text('''import importlib.util,json,signal,sys,time
from pathlib import Path
spec=importlib.util.spec_from_file_location("runner",sys.argv[1])
runner=importlib.util.module_from_spec(spec);spec.loader.exec_module(runner)
root=Path(sys.argv[2])
fixture='import sys;print("ready",flush=True);sys.stdin.readline();print("Stopping server",flush=True)'
server=runner.OwnedServer([sys.executable,"-u","-c",fixture],root,root/"signal-server.log")
def interrupted(signum,frame): raise KeyboardInterrupt()
signal.signal(signal.SIGTERM,interrupted)
try:
 server.wait_text("ready",5)
 print("ready",flush=True)
 while True: time.sleep(0.1)
except KeyboardInterrupt: pass
finally:
 (root/"cleanup.json").write_text(json.dumps(server.stop(timeout=2)))
''')
        process = subprocess.Popen([sys.executable, str(supervisor), str(Path(runner.__file__)), str(self.root)], stdout=subprocess.PIPE, text=True)
        try:
            self.assertEqual(process.stdout.readline().strip(), 'ready')
            process.terminate()
            self.assertEqual(process.wait(timeout=10), 0)
            self.assertTrue(json.loads((self.root / 'cleanup.json').read_text())['clean'])
        finally:
            process.stdout.close()
            if process.poll() is None:
                process.kill()
                process.wait()


if __name__ == '__main__':
    unittest.main()
