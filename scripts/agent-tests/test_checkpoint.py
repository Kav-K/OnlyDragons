"""Regression tests for false progress/evidence greens; no server or network required."""
from contextlib import redirect_stdout
from copy import deepcopy
import io
import json
from pathlib import Path
import tempfile
import subprocess
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

    def block_dependent_fixture_tasks(self, task_id):
        # A scenario that uncompletes a prerequisite must also put its downstream
        # fixture tasks on hold. Keep production progress and gate validation intact.
        affected = {task_id}
        tasks = self.read(BACKLOG)['tasks']
        while True:
            expanded = affected | {task['id'] for task in tasks
                                   if affected.intersection(task.get('dependsOn', []))}
            if expanded == affected:
                break
            affected = expanded
        progress = self.read(PROGRESS)
        for dependent in affected - {task_id}:
            progress['tasks'][dependent].update(status='blocked', completedRequirements=[], evidence={})
        self.write(PROGRESS, progress)

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
        self.change(BACKLOG, lambda value: next(task for task in value['tasks'] if task['id'] == 'T00')['dependsOn'].append('T01a'))
        self.reject_plan('dependency cycle')

    def test_restart_phase_bindings_cannot_be_removed_or_weakened(self):
        previous = {SCENARIOS: self.read(SCENARIOS)}
        for mutation in (lambda phase: phase['requiredAssertions'].pop(),
                         lambda phase: phase['requiredActorMessages'].pop(),
                         lambda phase: phase.update(playerActionPlan='dev/game-tests/player-plans/cleanup-abort-v1.json')):
            self.write(SCENARIOS, previous[SCENARIOS])
            self.change(SCENARIOS, lambda value: mutation(value['same-profile-restart']['phases'][1]))
            with self.assertRaisesRegex(checkpoint.CheckpointError, 'restart phases changed'):
                self.baseline(previous)

    def test_issue_mapping_drift_rejected(self):
        self.change(MAPPING, lambda value: value.pop('T04'))
        self.reject_plan('issue mapping drift')

    def test_complete_label_does_not_replace_unfinished_components(self):
        self.change(PROGRESS, lambda value: value['tasks']['T04'].update(status='complete', mergedRevision=SHA))
        self.reject_plan('complete with missing requirements')

    def test_deferred_component_cannot_be_declared_complete(self):
        self.change(PROGRESS, lambda value: value['tasks']['T06']['completedRequirements'].append('P02'))
        self.reject_plan('Deferred component declared complete')

    def test_active_dispatch_requires_integrated_dependencies(self):
        self.change(PROGRESS, lambda value: value['tasks']['T06'].update(status='active'))
        self.reject_plan('Unsatisfied task prerequisites')

    def prepare_milestone_dispatch(self):
        # Isolate the milestone gate from ordinary task dependencies: this fixture's
        # code prerequisite is already integrated, and its durable manual reference exists.
        self.change(BACKLOG, lambda value: next(task for task in value['tasks'] if task['id'] == 'T11').update(dependsOn=['T00']))
        self.change(PROGRESS, lambda value: value['tasks']['T11'].update(
            status='active', manualGateEvidence={'url': 'https://github.com/Kav-K/OnlyDragons/pull/15', 'revision': SHA}))

    def test_manual_reference_cannot_replace_required_milestone_acceptance(self):
        task = next(task for task in self.read(BACKLOG)['tasks'] if task['id'] == 'T11')
        self.assertEqual(['M3'], task['requiredMilestones'])
        self.prepare_milestone_dispatch()
        self.reject_plan('Task requires accepted milestones for T11: M3')

    def test_accepted_milestone_and_manual_reference_release_declared_gate(self):
        self.prepare_milestone_dispatch()
        # A small completed milestone keeps this positive gate test independent of
        # the future gameplay implementations still intentionally deferred in M3.
        self.change(PLAN, lambda value: value['milestones']['M3'].update(
            dependsOn=[], tasks=['T00'], requirements=['contract-consumers']))
        self.change(PROGRESS, lambda value: value['milestones']['M3'].update(status='accepted', evidence={
            'contract-consumers': {'url': 'https://github.com/Kav-K/OnlyDragons/pull/15', 'revision': SHA}}))
        self.assertTrue(self.plan()['summary']['planValid'])

    def test_unknown_and_circular_task_milestone_dependencies_rejected(self):
        original = self.read(BACKLOG)
        self.change(BACKLOG, lambda value: next(task for task in value['tasks'] if task['id'] == 'T11').update(requiredMilestones=['missing']))
        self.reject_plan('Unknown required milestone: T11')
        self.write(BACKLOG, original)
        self.change(BACKLOG, lambda value: next(task for task in value['tasks'] if task['id'] == 'T00').update(requiredMilestones=['M0']))
        self.reject_plan('task/milestone dependency cycle')

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

    def test_negative_controls_cannot_replace_positive_case_for_same_scenario(self):
        self.change(SUITES, lambda value: value['cases']['protocol-player-calibration'].update(expectation='positive'))
        self.change(PLAN, lambda value: value['fixtures']['protocol-player-calibration'].update(caseIds=['protocol-player-idle']))
        self.reject_plan('Positive fixture coverage cannot be replaced')

    def test_receipt_replayed_and_changed_paths_derived_locally(self):
        result = self.acceptance()
        self.assertTrue(result['automatedReady'])
        self.assertFalse(result['acceptanceApproved'])
        self.assertEqual(['stats-resolution'], result['requiredCases'])
        self.assertEqual(['src/main/java/stats/Changed.java'], result['changedPaths'])
        self.assertIn(('receipt', (self.project / 'build/receipt.json').resolve()), self.suite.calls)
        self.assertEqual(2, self.suite.calls.count('source'))
        self.assertIn('current-head-ci', result['externalGates'])

    def test_matching_revision_cannot_hide_stale_source(self):
        self.suite.receipt['source']['sourceInputSha256'] = 'd' * 64
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Stale suite source'):
            self.acceptance()

    def test_renamed_file_selects_both_removed_and_added_areas(self):
        def git(*arguments):
            return subprocess.check_output(['git', *arguments], cwd=self.project, stderr=subprocess.DEVNULL)
        git('init', '-q')
        git('config', 'user.name', 'Checkpoint test')
        git('config', 'user.email', 'checkpoint@example.invalid')
        git('config', 'core.autocrlf', 'false')
        original = self.project / 'src/old-area/Feature.java'
        original.parent.mkdir(parents=True)
        original.write_text('class Feature {}\n')
        git('add', '.')
        git('commit', '-qm', 'Initial inputs')
        base = git('rev-parse', 'HEAD').decode().strip()
        destination = self.project / 'src/new-area/Feature.java'
        destination.parent.mkdir(parents=True)
        original.rename(destination)
        git('add', '.')
        git('commit', '-qm', 'Move feature across areas')
        with patch.object(checkpoint, 'validate_no_weakening', return_value=base):
            result = checkpoint.validate_acceptance(self.project, 'build/receipt.json', base, suite_module=self.suite)
        self.assertEqual({'src/old-area/Feature.java', 'src/new-area/Feature.java'}, set(result['changedPaths']))

    def test_receipt_omitting_changed_area_case_rejected(self):
        self.suite.receipt['cases'] = [{'caseId': 'item-identity'}]
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'missing required changed-area'):
            self.acceptance()

    def test_duplicate_case_does_not_increase_coverage(self):
        self.suite.receipt['cases'].append({'caseId': 'stats-resolution'})
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Duplicate receipt case'):
            self.acceptance()

    def test_deferred_player_observations_cannot_be_satisfied_by_all_current_fixtures(self):
        # Retain this negative control after the real player fixture later lands.
        self.change(PLAN, lambda value: value['requirements']['projectile-player-observations'].update(
            availability='deferred', fixtures=[]))
        with self.assertRaisesRegex(checkpoint.CheckpointError,
                                    'deferred automated requirements: projectile-player-observations'):
            self.acceptance(['T04'])

    def implement_player_observation_fixture(self):
        """Simulate a future distinct registration at the verified-receipt seam."""
        fixture = 'projectile-player-feasibility'
        plan = self.read(PLAN)
        if fixture not in plan['fixtures']:
            assertions = ['native_release_damage_positive', 'part_parent_mapping']
            plan['fixtures'][fixture] = {'assertions': assertions, 'caseIds': [fixture],
                                         'scope': 'Synthetic checkpoint fixture registration; no runtime claim.'}
            self.change(SCENARIOS, lambda value: value.update({fixture: {'requiredAssertions': assertions}}))
            self.change(SUITES, lambda value: value['cases'].update({fixture: {
                'scenarioId': fixture, 'expectation': 'positive'}}))
            path = self.project / JAVA
            path.write_text(path.read_text().replace('scenarios = Map.ofEntries(',
                'scenarios = Map.ofEntries(\nMap.entry("' + fixture + '", new SyntheticScenario()),'))
        plan['requirements']['projectile-player-observations'].update(
            availability='implemented', fixtures=[fixture])
        self.write(PLAN, plan)
        present = {case['caseId'] for case in self.suite.receipt['cases']}
        for case in plan['fixtures'][fixture]['caseIds']:
            if case not in present:
                self.suite.receipt['cases'].append({'caseId': case})
        return fixture

    def record_bounded_t04_completion(self):
        # These are synthetic ledger references, not acceptance of the real branch.
        refs = self.read(PLAN)['tasks']['T04']
        evidence = {ref: {'url': 'https://github.com/Kav-K/OnlyDragons/pull/31', 'revision': SHA}
                    for ref in refs}
        self.change(PROGRESS, lambda value: value['tasks']['T04'].update(
            status='complete', mergedRevision=SHA, completedRequirements=refs, evidence=evidence))

    def test_feasibility_prerequisite_does_not_require_its_dependent_adapter(self):
        plan = self.read(PLAN)
        bounded = {'projectile-observations', 'projectile-player-observations',
                   'listener-cleanup', 'projectile-impact-policy'}
        self.assertEqual(bounded, set(plan['tasks']['T04']))
        self.assertEqual(bounded | {'contract-consumers'}, set(plan['milestones']['M0']['requirements']))
        adapter = next(task for task in self.read(BACKLOG)['tasks'] if task['id'] == 'T06')
        self.assertIn('T04', adapter['dependsOn'])
        for requirement in ('P02', 'P04'):
            self.assertIn(requirement, plan['tasks']['T06'])
            self.assertIn(requirement, plan['milestones']['M1']['requirements'])
            self.assertEqual([requirement], plan['paperCases'][requirement])
            self.assertEqual('automated', plan['requirements'][requirement]['kind'])

    def test_bounded_player_evidence_allows_machine_readiness_but_keeps_policy_external(self):
        fixture = self.implement_player_observation_fixture()
        result = self.acceptance(['T04'])
        self.assertTrue(result['automatedReady'])
        self.assertFalse(result['acceptanceApproved'])
        self.assertIn('projectile-impact-policy', result['externalGates'])
        self.assertEqual('external-review', self.read(PLAN)['requirements']['projectile-impact-policy']['kind'])
        self.assertTrue({fixture, 'projectile-feasibility', 'projectile-cleanup-failure',
                         'projectile-cleanup-abort'} <= set(result['requiredCases']))
        self.assertTrue({'P02', 'P04'} <= set(result['pendingAutomatedRequirements']))
        self.suite.receipt['cases'] = [case for case in self.suite.receipt['cases'] if case['caseId'] != fixture]
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'missing required.*projectile-player-feasibility'):
            self.acceptance(['T04'])

    def test_feasibility_completion_cannot_release_deferred_adapter_requirements(self):
        self.implement_player_observation_fixture()
        self.record_bounded_t04_completion()
        self.assertTrue(self.plan()['summary']['planValid'])
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'deferred automated requirements: P02, P04'):
            self.acceptance(['T06'])

    def test_t04_and_m0_cannot_record_acceptance_without_policy_review_reference(self):
        self.implement_player_observation_fixture()
        self.record_bounded_t04_completion()
        completed = self.read(PROGRESS)
        self.change(PROGRESS, lambda value: value['tasks']['T04']['evidence'].pop('projectile-impact-policy'))
        self.reject_plan('Missing evidence reference: T04/projectile-impact-policy')
        self.write(PROGRESS, completed)
        refs = self.read(PLAN)['milestones']['M0']['requirements']
        evidence = {ref: {'url': 'https://github.com/Kav-K/OnlyDragons/pull/31', 'revision': SHA}
                    for ref in refs if ref != 'projectile-impact-policy'}
        self.change(PROGRESS, lambda value: value['milestones']['M0'].update(status='accepted', evidence=evidence))
        self.reject_plan('Missing evidence reference: M0/projectile-impact-policy')
        self.change(PROGRESS, lambda value: value['milestones']['M0']['evidence'].update({
            'projectile-impact-policy': {'url': 'https://github.com/Kav-K/OnlyDragons/pull/31', 'revision': SHA}}))
        self.assertTrue(self.plan()['summary']['planValid'])

    def test_gate_relocation_still_requires_a_reviewed_new_comparison_base(self):
        previous = self.read(PLAN)
        previous['tasks']['T04'] = ['projectile-observations', 'listener-cleanup', 'P02', 'P04']
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Acceptance requirement mapping weakened: tasks/T04'):
            self.baseline({PLAN: previous})
        # Once the reviewed relocation lands, neither downstream owner can drop it.
        current = self.read(PLAN)
        for group, owner in (('tasks', 'T06'), ('milestones', 'M1')):
            with self.subTest(group=group, owner=owner):
                self.write(PLAN, current)
                def drop(value):
                    refs = value[group][owner] if group == 'tasks' else value[group][owner]['requirements']
                    refs.remove('P02')
                self.change(PLAN, drop)
                with self.assertRaisesRegex(checkpoint.CheckpointError,
                                            'Acceptance requirement mapping weakened|Milestone gate weakened'):
                    self.baseline({PLAN: current})

    def test_named_task_requires_all_of_its_bound_cases(self):
        self.suite.receipt['cases'] = [{'caseId': 'stats-resolution'}]
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'protocol-player'):
            self.acceptance(['T09b'])

    def test_blocked_selected_task_requires_completed_prerequisites(self):
        self.block_dependent_fixture_tasks('T09b')
        self.change(PROGRESS, lambda value: value['tasks']['T09c'].update(status='blocked'))
        self.change(PROGRESS, lambda value: value['tasks']['T09b'].update(status='blocked'))
        self.assertTrue(self.plan()['summary']['planValid'])
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Unsatisfied task prerequisites for T09c: T09b'):
            self.acceptance(['T09c'])
        self.assertEqual([], self.suite.calls)

    def test_planned_selected_task_requires_milestone_and_manual_gates(self):
        self.prepare_milestone_dispatch()
        self.change(PROGRESS, lambda value: value['tasks']['T11'].update(status='planned'))
        self.assertTrue(self.plan()['summary']['planValid'])
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Task requires accepted milestones for T11: M3'):
            self.acceptance(['T11'])
        self.change(BACKLOG, lambda value: next(task for task in value['tasks'] if task['id'] == 'T12').update(dependsOn=['T00']))
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Missing evidence reference: T12 manual gate'):
            self.acceptance(['T12'])
        self.assertEqual([], self.suite.calls)

    def test_planned_task_with_satisfied_gates_can_verify_automated_components(self):
        self.block_dependent_fixture_tasks('T09c')
        self.change(PROGRESS, lambda value: value['tasks']['T09c'].update(status='planned'))
        result = self.acceptance(['T09c'])
        self.assertTrue(result['automatedReady'])
        self.assertFalse(result['acceptanceApproved'])
        self.assertIn('checkpoint-unit-checks', result['externalGates'])

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
        next(task for task in previous_backlog['tasks'] if task['id'] == 'T00')['dependsOn'].append('T09a')
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

    def test_manual_and_milestone_dispatch_gates_cannot_silently_disappear(self):
        original = self.read(BACKLOG)
        for task_id in ('T11', 'T12'):
            with self.subTest(task=task_id):
                self.write(BACKLOG, original)
                self.change(BACKLOG, lambda value: next(task for task in value['tasks'] if task['id'] == task_id).pop('manualGate'))
                self.assertTrue(self.plan()['summary']['planValid'])
                with self.assertRaisesRegex(checkpoint.CheckpointError, 'Task manual gate removed or changed: ' + task_id):
                    self.baseline({BACKLOG: original})
        self.write(BACKLOG, original)
        self.change(BACKLOG, lambda value: next(task for task in value['tasks'] if task['id'] == 'T11').pop('requiredMilestones'))
        with self.assertRaisesRegex(checkpoint.CheckpointError, 'Task milestone gate removed: T11'):
            self.baseline({BACKLOG: original})

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
