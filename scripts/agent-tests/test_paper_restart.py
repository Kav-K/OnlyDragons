"""Two-boot failure boundaries; synthetic records here are never runtime acceptance."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import paper_restart as restart
import paper_test as runner
import player_actions
import ci_paper

ROOT = Path(__file__).resolve().parents[2]


class RestartContracts(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.descriptor = runner.strict_json(ROOT / 'dev/game-tests/scenarios.json')['same-profile-restart']
        self.parent_id = 'a' * 32
        self.parent = {'catalogMode': restart.MODE, 'runId': self.parent_id, 'passed': True, 'error': None,
                       'startedAtEpochMs': 100, 'completedAtEpochMs': 1000,
                       'pins': {'minecraftVersion': '26.2'}, 'profile': {'world': 'agent-world-' + self.parent_id},
                       'artifacts': {'productionSha256': 'c' * 64}, 'playerBuild': {'jars': {'client.jar': 'd' * 64}},
                       'stagedArtifacts': {'server.jar': 'e' * 64}, 'phases': []}
        (self.root / 'config-initial.yml').write_text('initial')
        self.parent['initialConfigSha256'] = runner.sha256(self.root / 'config-initial.yml')
        for index in (1, 2):
            root = self.root / f'phase-{index}'; root.mkdir()
            nonce = self.parent_id[:10] + str(index) * 22
            context = {'schemaVersion': 1, 'mode': restart.MODE, 'parentRunId': self.parent_id, 'index': index,
                       'nonce': nonce, 'previousNonce': self.parent_id[:10] + '1' * 22 if index == 2 else None,
                       'initialConfigPath': str(self.root / 'config-initial.yml'),
                       'previousReportPath': str(self.root / 'phase-1/scenario.json') if index == 2 else None}
            runner.atomic_json(root / 'context.json', context)
            runner.atomic_json(root / 'scenario.json', {'observations': {'restartWorld':
                {'uuid': '11111111-1111-1111-1111-111111111111', 'name': self.parent['profile']['world']}}})
            first = index * 200
            phase = {'index': index, 'parentRunId': self.parent_id, 'runId': nonce, 'context': context,
                     'startedAtEpochMs': first, 'completedAtEpochMs': first + 100,
                     'serverProcess': {'pid': index, 'startTicks': first, 'startedAtEpochMs': first + 1},
                     'clientProcess': {'pid': index + 10, 'startTicks': first + 1, 'startedAtEpochMs': first + 2},
                     'stopStartedAtEpochMs': first + 80, 'stopCompletedAtEpochMs': first + 90,
                     'playerStopStartedAtEpochMs': first + 60, 'playerStopCompletedAtEpochMs': first + 70,
                     'cleanup': {'exitCode': 0, 'forced': False, 'clean': True},
                     'passed': True, 'error': None, 'status': {'version': {'name': '26.2'}}}
            for key in ('profile', 'artifacts', 'playerBuild'):
                phase[key] = copy.deepcopy(self.parent[key])
            phase['artifactsBefore'] = phase['artifactsAfter'] = copy.deepcopy(self.parent['stagedArtifacts'])
            for side, text in [('before', 'initial' if index == 1 else 'saved'), ('after', 'saved' if index == 1 else 'initial')]:
                path = root / f'config-{side}.yml'; path.write_text(text)
                phase['config' + side.title() + 'Sha256'] = runner.sha256(path)
            self.parent['phases'].append(phase)

    def verify(self):
        restart.verify_continuity(runner, self.parent, self.descriptor, self.root)

    def test_two_sequential_boots_preserve_config_and_world(self):
        self.verify()
        restart.validate_descriptor(ROOT, self.descriptor)
        plans = [player_actions.load_plan(ROOT, d)[2] for d in self.descriptor['phases']]
        self.assertNotEqual(plans[0]['planId'], plans[1]['planId'])
        self.assertEqual(player_actions.identities(self.parent['phases'][0]['runId'], plans[0]),
                         player_actions.identities(self.parent['phases'][1]['runId'], plans[1]))

    def test_missing_duplicate_reordered_phases_reject(self):
        original = copy.deepcopy(self.parent['phases'])
        for phases in [[], original[:1], original + original[:1], original[::-1], [original[0], original[0]]]:
            with self.subTest(phases=len(phases)), self.assertRaises(runner.ValidationError):
                self.parent['phases'] = phases; self.verify()

    def test_stale_wrong_nonce_reject(self):
        for nonce in [self.parent_id, self.parent['phases'][0]['runId'], 'f' * 32, 'invalid']:
            with self.subTest(nonce=nonce), self.assertRaises(runner.ValidationError):
                self.parent['phases'][1]['runId'] = nonce; self.verify()

    def test_failed_shutdown_startup_process_reuse_and_arbitrary_errors_reject(self):
        original = copy.deepcopy(self.parent)
        mutations = [lambda p: p['phases'][0]['cleanup'].update(clean=False),
                     lambda p: p['phases'][0]['cleanup'].update(forced=True),
                     lambda p: p['phases'][0]['cleanup'].update(exitCode=1),
                     lambda p: p['phases'][1].pop('status'),
                     lambda p: p['phases'][1].update(serverProcess=p['phases'][0]['serverProcess']),
                     lambda p: p['phases'][1].update(startedAtEpochMs=250),
                     lambda p: p['phases'][0].update(error='first boot failed'),
                     lambda p: p.update(error='arbitrary', passed=False),
                     lambda p: p['phases'][1].update(index=True)]
        for mutation in mutations:
            self.parent = copy.deepcopy(original); mutation(self.parent)
            with self.subTest(mutation=mutation), self.assertRaises(runner.ValidationError): self.verify()

    def test_changed_saved_config_artifacts_and_world_reject(self):
        original = copy.deepcopy(self.parent)
        for key in ['configBeforeSha256', 'configAfterSha256', 'artifactsBefore', 'artifactsAfter', 'profile']:
            self.parent = copy.deepcopy(original); self.parent['phases'][1][key] = 'mutated'
            with self.subTest(key=key), self.assertRaises(runner.ValidationError): self.verify()
        self.parent = original
        (self.root / 'phase-2/config-before.yml').write_text('changed on disk')
        with self.assertRaises(runner.ValidationError): self.verify()

    def test_wrong_world_uuid_even_with_same_world_name_rejects(self):
        path = self.root / 'phase-2/scenario.json'; d=runner.strict_json(path)
        d['observations']['restartWorld']['uuid'] = '22222222-2222-2222-2222-222222222222'
        runner.atomic_json(path, d)
        with self.assertRaisesRegex(runner.ValidationError, 'World UUID'): self.verify()

    def test_abort_accepts_only_exact_second_phase_reason(self):
        self.descriptor['phases'][1]['expectation'] = 'cleanup-abort'
        self.parent.update(passed=False, error='Failed scenario assertions: scenario_exception')
        self.parent['phases'][1].update(passed=False, error=self.parent['error'])
        self.verify()
        self.parent['phases'][1]['error'] = self.parent['error'] = 'Timed out waiting for a scenario report'
        with self.assertRaises(runner.ValidationError): self.verify()

    def test_typed_bounded_descriptor_and_wrong_actors_reject(self):
        original = copy.deepcopy(self.descriptor)
        for index in [True, 1.0, '1', 2]:
            d = copy.deepcopy(original); d['phases'][0]['index'] = index
            with self.subTest(index=index), self.assertRaises(runner.ValidationError): restart.validate_descriptor(ROOT, d)
        for value in [[], [1], ['same', 'same'], ['bad assertion']]:
            d = copy.deepcopy(original); d['phases'][1]['requiredAssertions'] = value
            with self.subTest(value=value), self.assertRaises(runner.ValidationError): restart.validate_descriptor(ROOT, d)
        d = copy.deepcopy(original); d['phases'][1]['requiredActorMessages'][0]['actor'] = 'intruder'
        with self.assertRaises(player_actions.ActionValidationError): restart.validate_descriptor(ROOT, d)
        real_loader = player_actions.load_plan
        def changed(project, descriptor):
            path, digest, plan = real_loader(project, descriptor)
            if descriptor['index'] == 2: plan['actors'].reverse()
            return path, digest, plan
        with patch.object(player_actions, 'load_plan', side_effect=changed), self.assertRaisesRegex(runner.ValidationError, 'roster'):
            restart.validate_descriptor(ROOT, original)

    def test_second_boot_missing_wrong_or_wrong_actor_received_text_rejects(self):
        phase = self.descriptor['phases'][1]
        messages = ['OnlyDragons ready | version 1', 'Restart calibration parent hello player']
        valid = {'actors': [{'id': actor, 'sessions': [{'id': 's1', 'messages': messages.copy()}]} for actor in ['alpha', 'beta']]}
        player_actions.validate_messages(valid, phase)
        for text in [[], ['wrong'], ['OnlyDragons ready | version 1']]:
            report = copy.deepcopy(valid); report['actors'][1]['sessions'][0]['messages'] = text
            with self.subTest(text=text), self.assertRaises(player_actions.ActionValidationError):
                player_actions.validate_messages(report, phase)

    def test_export_only_declared_restart_final_config(self):
        profile = self.root / 'run/agent-tests' / self.parent_id
        config = profile / restart.CONFIG; config.parent.mkdir(parents=True); config.write_text('initial')
        (config.parent / 'secret.yml').write_text('excluded')
        (profile / 'world').mkdir(); (profile / 'world/level.dat').write_text('excluded')
        self.assertNotIn(config.relative_to(self.root).as_posix(), ci_paper.evidence_files(self.root))
        result = self.root / 'build/reports/agent-paper' / self.parent_id / 'result.json'
        result.parent.mkdir(parents=True); runner.atomic_json(result, {'catalogMode': restart.MODE})
        exported = ci_paper.evidence_files(self.root)
        self.assertIn(config.relative_to(self.root).as_posix(), exported)
        self.assertFalse(any('secret.yml' in p or 'level.dat' in p for p in exported))


if __name__ == '__main__': unittest.main()
