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

    def test_default_raw_json_ceiling_is_inclusive_and_remains_one_mib(self):
        for filename in ('scenario.json', 'player.json', 'result.json', 'scenarios.json'):
            with self.subTest(filename=filename):
                path = self.path.with_name(filename)
                path.write_bytes(b'{}' + b' ' * (1024 * 1024 - 2))
                self.assertEqual(runner.strict_json(path), {})
                with path.open('ab') as stream:
                    stream.write(b' ')
                with self.assertRaisesRegex(runner.ValidationError, 'exceeds 1 MiB'):
                    runner.strict_json(path)


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
            with self.assertRaisesRegex(runner.ResourceBusy, 'Cannot verify Windows.*OSError: probe unavailable'):
                runner.windows_available_memory()

    def test_windows_probe_timeout_exit_and_invalid_output_keep_precise_busy_reason(self):
        for error, reason in ((subprocess.TimeoutExpired('powershell', 4), 'probe exceeded 4s'),
                              (subprocess.CalledProcessError(7, 'powershell', stderr='CIM unavailable'), 'probe exit 7: CIM unavailable')):
            with self.subTest(reason=reason), patch.object(runner.subprocess, 'run', side_effect=error):
                with self.assertRaisesRegex(runner.ResourceBusy, reason):
                    runner.windows_available_memory(timeout=4)
        for output in ('', '-1', 'not a memory reading'):
            with self.subTest(output=output), patch.object(runner.subprocess, 'run', return_value=Mock(stdout=output)):
                with self.assertRaisesRegex(runner.ResourceBusy, 'invalid nonnegative MiB response'):
                    runner.windows_available_memory()

    def wsl_memory(self, path):
        if path == Path('/proc/sys/kernel/osrelease'):
            return '6.6.87.2-microsoft-standard-WSL2'
        self.assertEqual(path, Path('/proc/meminfo'))
        return '\n'.join(f'{key}: {value * 1024} kB' for key, value in
                         dict(MemAvailable=8000, MemFree=4000, Buffers=0, Cached=4000, SReclaimable=0, Shmem=0).items())

    def test_transient_windows_failure_retries_and_admits_only_fresh_successful_probe(self):
        elapsed = [0.0]
        def sleep(seconds):
            elapsed[0] += seconds
        with patch.object(runner.time, 'monotonic', side_effect=lambda: elapsed[0]), \
                patch.object(runner.time, 'sleep', side_effect=sleep), \
                patch.object(Path, 'read_text', autospec=True, side_effect=self.wsl_memory), \
                patch.object(runner.subprocess, 'run', side_effect=[OSError('transient CIM failure'), Mock(stdout='6000')]) as probe, \
                patch.object(runner, 'assess_memory', wraps=runner.assess_memory) as assess:
            admitted = runner.wait_for_memory(1536, 5, 256)
        self.assertEqual(6000, admitted['windowsAvailableMiB'])
        self.assertEqual(2816, admitted['requiredMiB'])
        self.assertEqual(2, probe.call_count)
        self.assertEqual([5, 4], [call.kwargs['timeout'] for call in probe.call_args_list])
        self.assertEqual(1, assess.call_count, 'Failed host probe must never reach memory admission')

    def test_failed_windows_probe_consumes_only_remaining_resource_budget_and_never_admits(self):
        elapsed = [0.0]
        def timeout(*args, **kwargs):
            elapsed[0] += kwargs['timeout']
            raise subprocess.TimeoutExpired('powershell', kwargs['timeout'])
        with patch.object(runner.time, 'monotonic', side_effect=lambda: elapsed[0]), \
                patch.object(Path, 'read_text', autospec=True, side_effect=self.wsl_memory), \
                patch.object(runner.subprocess, 'run', side_effect=timeout) as probe, \
                patch.object(runner, 'assess_memory') as assess:
            with self.assertRaisesRegex(runner.ResourceBusy, 'probe exceeded 2s.*resource wait expired'):
                runner.wait_for_memory(1536, 2)
        probe.assert_called_once()
        self.assertEqual(2, probe.call_args.kwargs['timeout'])
        assess.assert_not_called()

    def test_successful_probe_that_finishes_after_resource_deadline_is_not_admission(self):
        elapsed = [0.0]
        def late_probe(*args, **kwargs):
            elapsed[0] = 6
            return {'windowsAvailableMiB': 9000}
        with patch.object(runner.time, 'monotonic', side_effect=lambda: elapsed[0]), \
                patch.object(runner, 'available_memory', side_effect=late_probe):
            with self.assertRaisesRegex(runner.ResourceBusy, 'probe completed after the resource deadline.*resource wait expired'):
                runner.wait_for_memory(1536, 5)

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


class BuildEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.project = Path(self.temp.name).resolve()
        self.reports = self.project / 'build/reports/fixture'
        self.reports.mkdir(parents=True)
        self.main = self.project / 'build/libs/OnlyDragons.jar'
        self.companion = self.project / 'dev/game-tests/build/libs/OnlyDragonsGameTests.jar'
        for path in (self.main, self.companion):
            path.parent.mkdir(parents=True)
            path.write_bytes(b'fixture artifact')
        (self.project / 'build/plugin-artifact.txt').write_text(str(self.main))
        for directory, count in [('build/test-results/test', 1), ('dev/game-tests/build/test-results/test', 2)]:
            path = self.project / directory / 'TEST-Sample.xml'
            path.parent.mkdir(parents=True)
            path.write_text(self.xml(count))
        self.companion_xml = self.project / 'dev/game-tests/build/test-results/test/TEST-Sample.xml'

    @staticmethod
    def xml(count, failure=None):
        counts = {'failures': 0, 'errors': 0, 'skipped': 0}
        if failure:
            counts[failure] = 1
        tag = {'failures': 'failure', 'errors': 'error', 'skipped': 'skipped'}.get(failure)
        cases = ''.join('<testcase classname="Sample" name="case' + str(i) + '">'
                        + ('<' + tag + '/>' if tag and i == 0 else '') + '</testcase>' for i in range(count))
        return '<testsuite tests="' + str(count) + '" ' + ' '.join(key + '="' + str(value) + '"' for key, value in counts.items()) + '>' + cases + '</testsuite>'

    def build(self):
        # No JVM: emulate successful wrapper exits to exercise the evidence gate itself.
        with patch.object(runner.subprocess, 'run', return_value=Mock(returncode=0)):
            return runner.build_artifacts(self.project, self.project / 'java', self.reports)

    def test_successful_build_records_distinct_production_and_companion_counts(self):
        main, companion, evidence = self.build()
        self.assertEqual((main, companion), (self.main, self.companion))
        self.assertEqual(evidence, {'wrapperInvoked': True,
                                  'unitTests': {'tests': 1, 'failures': 0, 'errors': 0, 'skipped': 0},
                                  'companionUnitTests': {'tests': 2, 'failures': 0, 'errors': 0, 'skipped': 0}})

    def test_successful_gradle_exit_cannot_hide_missing_companion_xml(self):
        self.companion_xml.unlink()
        with self.assertRaisesRegex(runner.ValidationError, 'Companion JUnit evidence is missing'):
            self.build()

    def test_successful_production_tests_cannot_hide_failed_skipped_or_empty_companion_tests(self):
        for failure in ('failures', 'errors', 'skipped', None):
            with self.subTest(failure=failure):
                self.companion_xml.write_text(self.xml(2 if failure else 0, failure))
                with self.assertRaisesRegex(runner.ValidationError, 'Companion tests failed, aborted, or skipped'):
                    self.build()


class PaperLogPolicyTests(unittest.TestCase):
    def test_console_and_file_error_formats_are_both_rejected(self):
        for line in ('[01:24:14 ERROR]: Failed to request yggdrasil public key',
                     '[01:24:14] [Server thread/ERROR]: Event registration failed',
                     '[01:24:14 SEVERE]: Plugin initialization failed',
                     'OD_GAME_TEST_REPORT_ERROR Unsupported report value'):
            with self.subTest(line=line):
                self.assertEqual(runner.paper_errors('normal startup\n' + line + '\nStopping server'), [line])

    def test_ordinary_console_information_is_not_an_error(self):
        self.assertEqual(runner.paper_errors('[01:24:14 INFO]: OnlyDragons enabled\n[01:24:14 WARN]: Offline fixture\nStopping server'), [])


if __name__ == '__main__':
    unittest.main()
