"""Regression tests for false progress/evidence greens; no server or network required."""
from contextlib import redirect_stdout
from copy import deepcopy
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import checkpoint


ROOT = Path(__file__).resolve().parents[2]
BACKLOG = 'docs/planning/backlog.json'
MAPPING = 'docs/planning/github-issues.json'
PLAN = 'dev/game-tests/acceptance.json'
PROGRESS = 'docs/planning/progress.json'
SCENARIOS = 'dev/game-tests/scenarios.json'
SUITES = 'dev/game-tests/suites.json'
JAVA = 'dev/game-tests/src/main/java/com/kaveenk/onlydragons/gametests/GameTestsPlugin.java'
SHA = 'a' * 40


class VerifiedSuite:
    """Checkpoint seam: the actual suite validator has its own raw-report tests."""
    def __init__(self, case_ids):
        self.source = {'revision': SHA, 'sourceInputSha256': 'b' * 64, 'gitTreeSha256': 'c' * 64}
        self.receipt = {'source': deepcopy(self.source), 'cases': [{'caseId': key} for key in case_ids]}
        self.required = ['stats-resolution']
        self.error = None
        self.calls = []

    def source_identity(self, project):
        self.calls.append('source')
        return deepcopy(self.source)

    def validate_suite_receipt(self, project, receipt_path):
        self.calls.append(('receipt', receipt_path))
        if self.error:
            raise RuntimeError(self.error)
        return deepcopy(self.receipt)

    def required_cases(self, project, changed):
        self.calls.append(('changed', changed))
        return self.required


class CheckpointTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.project = Path(self.temporary.name)
        for path in (BACKLOG, MAPPING, PLAN, PROGRESS, SCENARIOS, JAVA):
            destination = self.project / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes((ROOT / path).read_bytes())
        # The component branch precedes the lead's backlog/mapping integration.
        backlog, mapping = self.read(BACKLOG), self.read(MAPPING)
        if 'T09c' not in mapping:
            backlog['tasks'].append({'id': 'T09c', 'dependsOn': ['T00', 'T09a', 'T09b']})
            mapping['T09c'] = {'number': 28, 'url': 'https://github.com/Kav-K/OnlyDragons/issues/28'}
            self.write(BACKLOG, backlog)
            self.write(MAPPING, mapping)
        cases = {case: {'scenarioId': key} for key, fixture in self.read(PLAN)['fixtures'].items()
                 for case in fixture['caseIds']}
        self.write(SUITES, {'schemaVersion': 1, 'cases': cases})
        self.suite = VerifiedSuite(list(cases))

    def read(self, path):
        return json.loads((self.project / path).read_text(encoding='utf-8'))

    def write(self, path, value):
        (self.project / path).write_text(json.dumps(value), encoding='utf-8')

    def change(self, path, mutation):
        value = self.read(path)
        mutation(value)
        self.write(path, value)

    def plan(self):
        return checkpoint.validate_plan(self.project)

    def reject_plan(self, text):
        with self.assertRaisesRegex(checkpoint.CheckpointError, text):
            self.plan()

    def acceptance(self, tasks=()):
        with patch.object(checkpoint, 'validate_no_weakening', return_value=SHA), \
                patch.object(checkpoint.subprocess, 'check_output', return_value=b'src/main/java/stats/Changed.java\0'):
            return checkpoint.validate_acceptance(self.project, 'build/receipt.json', 'origin/main', tasks, self.suite)

    def baseline(self, previous):
        with patch.object(checkpoint.subprocess, 'check_output', return_value=(SHA + '\n').encode()), \
                patch.object(checkpoint, 'base_document', side_effect=lambda project, revision, path: previous.get(path)):
            return checkpoint.validate_no_weakening(self.project, 'origin/main', self.plan())

    def test_honest_plan_keeps_partial_feature_and_milestones_unaccepted(self):
        result = self.plan()['summary']
        self.assertEqual('partial', result['taskStates']['T04'])
        self.assertEqual({'not-accepted'}, set(result['milestoneStates'].values()))
        self.assertFalse(result['automatedReady'])
        self.assertFalse(result['acceptanceApproved'])
        self.assertIn('P02', result['pendingAutomatedRequirements'])

    def test_duplicate_json_cannot_hide_gate(self):
        (self.project / PLAN).write_text('{"schemaVersion":1,"schemaVersion":1}')
        self.reject_plan('Duplicate JSON key')

    def test_task_dependency_cycle_rejected(self):
        self.change(BACKLOG, lambda value: value['tasks'][0]['dependsOn'].append('T01a'))
        self.reject_plan('dependency cycle')

    def test_issue_mapping_drift_rejected(self):
        self.change(MAPPING, lambda value: value.pop('T04'))
        self.reject_plan('issue mapping drift')

    def test_complete_label_does_not_replace_unfinished_components(self):
        self.change(PROGRESS, lambda value: value['tasks']['T04'].update(status='complete', mergedRevision=SHA))
        self.reject_plan('complete with missing requirements')

    def test_deferred_component_cannot_be_declared_complete(self):
        self.change(PROGRESS, lambda value: value['tasks']['T04']['completedRequirements'].append('P02'))
        self.reject_plan('Deferred component declared complete')

    def test_active_dispatch_requires_integrated_dependencies(self):
        self.change(PROGRESS, lambda value: value['tasks']['T06'].update(status='active'))
        self.reject_plan('Unsatisfied task prerequisites')

    def test_milestone_cannot_accept_partial_foundation(self):
        self.change(PROGRESS, lambda value: value['milestones']['M0'].update(status='accepted'))
        self.reject_plan('Milestone accepted with incomplete task')

    def test_paper_case_mapping_cannot_drop_p14(self):
        self.change(PLAN, lambda value: value['paperCases'].pop('P14'))
        self.reject_plan('P01-P14 coverage mapping drift')

    def test_functional_case_cannot_be_replaced_by_human_gate(self):
        self.change(PLAN, lambda value: value['paperCases'].update(P02=['human-input-feel']))
        self.reject_plan('Functional Paper case cannot')

    def test_fixture_requires_registered_scenario(self):
        self.change(SCENARIOS, lambda value: value.pop('stats-resolution'))
        self.reject_plan('Scenario/fixture coverage drift')

    def test_catalog_requires_actual_java_registration(self):
        path = self.project / JAVA
        path.write_text(path.read_text().replace('"stats-resolution"', '"renamed-resolution"'))
        self.reject_plan('Java registration/scenario catalog drift')

    def test_fixture_assertion_cannot_reference_missing_check(self):
        self.change(PLAN, lambda value: value['fixtures']['stats-resolution']['assertions'].append('invented_assertion'))
        self.reject_plan('Missing registered acceptance assertions')

    def test_fixture_case_must_exist(self):
        self.change(PLAN, lambda value: value['fixtures']['stats-resolution']['caseIds'].append('invented-case'))
        self.reject_plan('Unknown fixture suite case')

    def test_unrelated_passing_case_cannot_satisfy_fixture(self):
        self.change(PLAN, lambda value: value['fixtures']['stats-resolution'].update(caseIds=['item-identity']))
        self.reject_plan('Fixture/suite scenario binding drift')

    def test_receipt_replayed_and_changed_paths_derived_locally(self):
        result = self.acceptance()
        self.assertTrue(result['automatedReady'])
        self.assertFalse(result['acceptanceApproved'])
        self.assertEqual(['stats-resolution'], result['requiredCases'])
        self.assertEqual(['src/main/java/stats/Changed.java'], result['changedPaths'])
        self.assertIn(('receipt', self.project / 'build/receipt.json'), self.suite.calls)
        self.assertEqual(2, self.suite.calls.count('source'))
        self.assertIn('current-head-ci', result['externalGates'])

    def test_matching_revision_cannot_hide_stale_source(self):
        self.suite.receipt['source']['sourceInputSha256'] = 'd' * 64
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Stale suite source'):
            self.acceptance()

    def test_receipt_omitting_changed_area_case_rejected(self):
        self.suite.receipt['cases'] = [{'caseId': 'item-identity'}]
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'missing required changed-area'):
            self.acceptance()

    def test_duplicate_case_does_not_increase_coverage(self):
        self.suite.receipt['cases'].append({'caseId': 'stats-resolution'})
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Duplicate receipt case'):
            self.acceptance()

    def test_deferred_task_gate_cannot_be_satisfied_by_all_current_fixtures(self):
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'deferred automated requirements.*P02'):
            self.acceptance(['T04'])

    def test_named_task_requires_all_of_its_bound_cases(self):
        self.suite.receipt['cases'] = [{'caseId': 'stats-resolution'}]
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'protocol-player'):
            self.acceptance(['T09b'])

    def test_self_attested_pass_cannot_override_raw_evidence_failure(self):
        self.suite.receipt.update(passed=True, verified=True)
        for failure in ('skipped JUnit case', 'resource busy', 'report not run', 'raw artifact hash mismatch'):
            with self.subTest(failure=failure):
                self.suite.error = failure
                with self.assertRaisesRegex(RuntimeError, failure):
                    self.acceptance()

    def test_dirty_source_failure_propagates(self):
        with patch.object(self.suite, 'source_identity', side_effect=RuntimeError('dirty checkout')):
            with self.assertRaisesRegex(RuntimeError, 'dirty checkout'):
                self.acceptance()

    def test_input_change_during_verification_rejected(self):
        updated = dict(self.suite.source, revision='e' * 40)
        with patch.object(self.suite, 'source_identity', side_effect=[self.suite.source, updated]):
            with self.assertRaisesRegex(checkpoint.CheckpointError, 'changed during checkpoint'):
                self.acceptance()

    def test_ci_requirement_remains_external_after_machine_gate(self):
        result = self.acceptance(['MAINT-17'])
        self.assertTrue(result['automatedReady'])
        self.assertIn('runner-unit-checks', result['externalGates'])
        self.assertFalse(result['acceptanceApproved'])

    def test_initial_manifest_addition_accepts_older_base_without_manifest(self):
        self.assertEqual(SHA, self.baseline({SCENARIOS: self.read(SCENARIOS)}))

    def test_coordinated_scenario_fixture_and_requirement_removal_rejected(self):
        previous = {SCENARIOS: self.read(SCENARIOS), PLAN: self.read(PLAN)}
        self.change(SCENARIOS, lambda value: value.pop('deliberate-failure'))
        def drop(value):
            value['fixtures'].pop('deliberate-failure')
            value['requirements'].pop('runner-negative')
            value['tasks']['T09a'].remove('runner-negative')
            value['requirements']['suite-baseline']['fixtures'].remove('deliberate-failure')
        self.change(PLAN, drop)
        self.change(PROGRESS, lambda value: value['tasks']['T09a']['completedRequirements'].remove('runner-negative'))
        path = self.project / JAVA
        path.write_text(''.join(line for line in path.read_text().splitlines(keepends=True) if '"deliberate-failure"' not in line))
        self.change(SUITES, lambda value: value['cases'].pop('deliberate-failure'))
        self.assertTrue(self.plan()['summary']['planValid'])
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Previously registered scenario removed'):
            self.baseline(previous)

    def test_coordinated_assertion_removal_rejected(self):
        previous = {SCENARIOS: self.read(SCENARIOS)}
        removed = previous[SCENARIOS]['stats-resolution']['requiredAssertions'][0]
        self.change(SCENARIOS, lambda value: value['stats-resolution']['requiredAssertions'].remove(removed))
        self.change(PLAN, lambda value: value['fixtures']['stats-resolution']['assertions'].remove(removed))
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Previously required scenario assertion removed'):
            self.baseline(previous)

    def test_required_player_message_text_and_fragments_cannot_weaken(self):
        requirements = [{'id': 'ferocity-values', 'exact': 'ferocity: raw=3.0 effective=3.0'},
                        {'id': 'hand-labels', 'containsAll': ['Main hand: ordinary ', 'enchants={vicious=3}']}]
        self.change(SCENARIOS, lambda value: value['protocol-player-calibration'].update(requiredPlayerMessages=requirements))
        previous = {SCENARIOS: self.read(SCENARIOS)}
        self.assertEqual(SHA, self.baseline(previous))
        for replacement in (requirements[1:],
                            [dict(requirements[0], exact='anything'), requirements[1]],
                            [requirements[0], {'id': 'hand-labels', 'containsAll': ['Main hand: ordinary ']}]):
            with self.subTest(replacement=replacement):
                self.change(SCENARIOS, lambda value: value['protocol-player-calibration'].update(requiredPlayerMessages=replacement))
                with self.assertRaisesRegex(checkpoint.CheckpointError, 'Previously required player message weakened'):
                    self.baseline(previous)

    def test_requirement_fixture_and_completed_component_removal_rejected(self):
        previous_plan = self.read(PLAN)
        previous_plan['requirements']['stats-resolver']['fixtures'].append('item-identity')
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Requirement fixture binding removed'):
            self.baseline({PLAN: previous_plan})
        previous_progress = self.read(PROGRESS)
        previous_progress['tasks']['T04']['completedRequirements'].append('historically-completed')
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Previously completed component removed'):
            self.baseline({PROGRESS: previous_progress})

    def test_dependency_and_changed_area_coverage_cannot_silently_shrink(self):
        previous_backlog = self.read(BACKLOG)
        previous_backlog['tasks'][0]['dependsOn'].append('T09a')
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Task dependency removed'):
            self.baseline({BACKLOG: previous_backlog})
        current = self.read(SUITES)
        current.update(suites={'all': list(current['cases'])},
                       areas={'stats': {'paths': ['src/**'], 'cases': ['stats-resolution'], 'affects': []}},
                       ignoredChanges=[])
        self.write(SUITES, current)
        previous = deepcopy(current)
        previous['areas']['stats']['cases'].append('item-identity')
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Changed-area coverage weakened'):
            self.baseline({SUITES: previous})

    def test_positive_case_cannot_be_relabeled_as_expected_failure(self):
        current = self.read(SUITES)
        current.update(suites={}, areas={}, ignoredChanges=[])
        current['cases']['stats-resolution']['expectation'] = 'positive'
        previous = deepcopy(current)
        current['cases']['stats-resolution']['expectation'] = 'deliberate-failure'
        self.write(SUITES, current)
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Suite case meaning changed'):
            self.baseline({SUITES: previous})

    def snapshot(self):
        progress = self.read(PROGRESS)
        return {'schemaVersion': 1, 'repository': 'Kav-K/OnlyDragons', 'capturedAtEpochMs': 1_000_000,
                'issues': [{'number': value['number'], 'state': 'closed' if progress['tasks'][key]['status'] == 'complete' else 'open',
                            'labels': []} for key, value in self.read(MAPPING).items()]}

    def test_current_issue_snapshot_is_drift_check_only(self):
        result = checkpoint.validate_plan(self.project, self.snapshot(), now_ms=1_000_000)['summary']
        self.assertFalse(result['acceptanceApproved'])

    def test_closed_issue_does_not_complete_partial_feature(self):
        snapshot = self.snapshot()
        number = self.read(MAPPING)['T04']['number']
        next(issue for issue in snapshot['issues'] if issue['number'] == number)['state'] = 'closed'
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Issue closure/progress drift: T04'):
            checkpoint.validate_plan(self.project, snapshot, now_ms=1_000_000)

    def test_dispatch_and_stale_snapshot_rejected(self):
        snapshot = self.snapshot()
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Stale/future'):
            checkpoint.validate_plan(self.project, snapshot, now_ms=2_000_000)
        number = self.read(MAPPING)['T06']['number']
        next(issue for issue in snapshot['issues'] if issue['number'] == number)['labels'] = ['symphony']
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Dispatch/progress drift: T06'):
            checkpoint.validate_plan(self.project, snapshot, now_ms=1_000_000)

    def test_cli_exit_zero_is_explicitly_machine_only(self):
        for automated, expected in ((False, 2), (True, 0)):
            with self.subTest(automated=automated), redirect_stdout(io.StringIO()) as output, \
                    patch.object(checkpoint, 'validate_plan', return_value={'summary': {}}), \
                    patch.object(checkpoint, 'validate_acceptance', return_value={'automatedReady': True, 'acceptanceApproved': False}):
                arguments = ['acceptance', '--project', str(self.project), '--base', 'main', '--receipt', 'r.json']
                code = checkpoint.main(arguments + (['--automated'] if automated else []))
                self.assertEqual(expected, code)
                self.assertFalse(json.loads(output.getvalue())['acceptanceApproved'])


if __name__ == '__main__':
    unittest.main()
