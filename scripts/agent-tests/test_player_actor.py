"""Protocol-player admission and failure tests; real login is a separate Paper gate."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock

spec = importlib.util.spec_from_file_location('paper_test', Path(__file__).with_name('paper_test.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class PlayerActorContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.now = int(time.time() * 1000)
        self.run_id = 'a' * 32
        self.pins = runner.properties(Path(__file__).resolve().parents[2] / 'versions.properties')
        pinned = runner.player_pins(self.pins)
        self.report = {'schemaVersion': 1, 'runId': self.run_id, 'username': 'od_' + 'a' * 13,
                       'authentication': 'offline-disposable-loopback', 'artifact': pinned['artifact'],
                       'minecraftVersion': pinned['minecraftVersion'], 'protocolVersion': pinned['protocolVersion'],
                       'startedAtEpochMs': self.now, 'completedAtEpochMs': self.now,
                       'loginReceived': True, 'playerLoadedSent': True, 'teleportsAcknowledged': 1,
                       'actions': ['select', 'draw', 'release', 'quit'], 'messages': [], 'disconnected': True,
                       'passed': True, 'error': ''}

    def validate(self, report):
        path = self.root / 'player.json'
        runner.atomic_json(path, report)
        return runner.validate_player_report(path, self.run_id, self.now, 60, self.pins)

    def test_floating_protocol_publication_and_malformed_hash_are_rejected(self):
        for mutation in ({'testPlayerProtocolLib': 'org.geysermc.mcprotocollib:protocol:26.2-SNAPSHOT'},
                         {'testPlayerProtocolLibSha256': 'bad'}, {'testPlayerProtocolVersion': 'true'}):
            with self.subTest(mutation=mutation), self.assertRaises(runner.ValidationError):
                runner.player_pins(dict(self.pins, **mutation))

    def test_only_explicit_catalog_declared_scenarios_can_enable_player_mode(self):
        catalog = {'plain': {}, 'calibration': {'testPlayerMode': 'protocol-calibration'},
                   'equipment': {'testPlayerMode': 'protocol-calibration'}}
        self.assertFalse(runner.player_mode(None, 'plain', 'calibrate', catalog))
        for scenario in ('calibration', 'equipment'):
            self.assertTrue(runner.player_mode('protocol-calibration', scenario, 'calibrate', catalog))
            with self.assertRaises(runner.ValidationError):
                runner.player_mode(None, scenario, 'calibrate', catalog)
        for args in (('protocol-calibration', 'plain', 'calibrate'),
                     (None, 'plain', 'early-exit'),
                     ('unrestricted', 'calibration', 'calibrate'),
                     (None, 'missing', 'calibrate')):
            with self.subTest(args=args), self.assertRaises(runner.ValidationError):
                runner.player_mode(*args, catalog)
        for malformed in (None, False, '', 'unknown', [], {}):
            with self.subTest(declaration=malformed), self.assertRaises(runner.ValidationError):
                runner.player_mode(None, 'bad', 'calibrate', {'bad': {'testPlayerMode': malformed}})
        with self.assertRaises(runner.ValidationError):
            runner.player_mode(None, 'bad', 'calibrate', {'bad': []})
        actual = runner.strict_json(Path(__file__).resolve().parents[2] / 'dev/game-tests/scenarios.json')
        for scenario in ('protocol-player-calibration', 'equipment-player'):
            self.assertTrue(runner.player_mode('protocol-calibration', scenario, 'calibrate', actual))

    def test_projectile_catalog_admission_retains_failure_controls(self):
        catalog = runner.strict_json(Path(__file__).resolve().parents[2] / 'dev/game-tests/scenarios.json')
        scenario = 'projectile-player-feasibility'
        for control in ('calibrate', 'early-exit', 'idle'):
            with self.subTest(control=control):
                self.assertTrue(runner.player_mode('protocol-calibration', scenario, control, catalog))
                with self.assertRaises(runner.ValidationError):
                    runner.player_mode(None, scenario, control, catalog)
        for unknown in ('projectile-feasibility', scenario + '-extra', scenario.upper(), ''):
            with self.subTest(scenario=unknown), self.assertRaises(runner.ValidationError):
                runner.player_mode('protocol-calibration', unknown, 'calibrate', catalog)
        undeclared = {scenario: {}}
        with self.assertRaises(runner.ValidationError):
            runner.player_mode('protocol-calibration', scenario, 'calibrate', undeclared)
        with self.assertRaises(runner.ValidationError):
            runner.player_mode('protocol-calibration', scenario, 'repeat-shots', catalog)

    def test_default_authenticated_settings_and_input_are_preserved(self):
        source = {'online-mode': 'true', 'server-ip': '0.0.0.0', 'level-name': 'human-world'}
        original = dict(source)
        ordinary = runner.test_settings(source, self.run_id, 30123)
        player = runner.test_settings(source, self.run_id, 30124, True)
        self.assertEqual(source, original)
        self.assertEqual(ordinary['online-mode'], 'true')
        self.assertEqual(player['online-mode'], 'false')
        self.assertEqual(player['white-list'], 'true')
        for settings in (ordinary, player):
            self.assertEqual(settings['server-ip'], '127.0.0.1')
            self.assertEqual(settings['level-name'], 'agent-world-' + self.run_id)
            self.assertEqual(settings['enable-rcon'], 'false')

    def test_client_heap_is_included_before_both_jvms_start(self):
        memory = {'MemAvailable': 2700}
        self.assertEqual(runner.assess_memory(1536, memory)['requiredMiB'], 2560)
        with self.assertRaises(runner.ResourceBusy):
            runner.assess_memory(1536, memory, client_memory_mib=256)
        result = runner.assess_memory(1536, {'MemAvailable': 3000}, client_memory_mib=256)
        self.assertEqual(result['requiredMiB'], 2816)
        self.assertEqual(result['playerClientHeapMiB'], 256)

    def test_complete_player_evidence_passes(self):
        self.assertTrue(self.validate(self.report)['passed'])

    def test_missing_malformed_or_unbounded_received_messages_fail(self):
        absent = dict(self.report)
        del absent['messages']
        with self.assertRaises(runner.ValidationError):
            self.validate(absent)
        for messages in (None, {}, 'text', [True], [''], ['x' * 2049], ['x'] * 129, ['OD_PLAYER:forged']):
            with self.subTest(messages=messages), self.assertRaises(runner.ValidationError):
                self.validate(dict(self.report, messages=messages))

    def test_real_equipment_command_requirements_reject_missing_or_wrong_output(self):
        catalog = runner.strict_json(Path(__file__).resolve().parents[2] / 'dev/game-tests/scenarios.json')
        descriptor = catalog['equipment-player']
        expected = descriptor['requiredPlayerMessages']
        messages = [item.get('exact', ' '.join(item.get('containsAll', []))) for item in expected]
        runner.validate_player_messages({'messages': messages}, descriptor)
        for index, item in enumerate(expected):
            with self.subTest(missing=item['id']), self.assertRaises(runner.ValidationError):
                runner.validate_player_messages({'messages': messages[:index] + messages[index + 1:]}, descriptor)
        for old, new in (('raw=3.0', 'raw=500.0'), ('Granted ordinary;', 'Granted wrong;'),
                         ('do not have permission', 'have permission')):
            with self.subTest(wrong=new), self.assertRaises(runner.ValidationError):
                runner.validate_player_messages({'messages': [message.replace(old, new) for message in messages]}, descriptor)
        # Fragments in unrelated messages cannot be assembled into a false match.
        with self.assertRaises(runner.ValidationError):
            runner.validate_player_messages({'messages': ['Main hand: ordinary ', 'Offhand inactive']},
                                            {'requiredPlayerMessages': [{'id': 'hands', 'containsAll': ['Main hand:', 'Offhand']}]})

    def test_message_catalog_rejects_malformed_or_duplicate_matchers(self):
        valid = {'id': 'message', 'exact': 'text'}
        for requirements in (None, {}, [None], [{'id': 'message'}], [dict(valid, containsAll=['text'])],
                             [dict(valid, exact=True)], [dict(valid, id='')], [valid, valid],
                             [{'id': 'message', 'containsAll': []}], [{'id': 'message', 'containsAll': [False]}],
                             [{'id': 'message', 'containsAll': ['x' * 257]}], [valid] * 33):
            with self.subTest(requirements=requirements), self.assertRaises(runner.ValidationError):
                runner.validate_player_messages({'messages': ['text']}, {'requiredPlayerMessages': requirements})

    def test_sent_actions_alone_or_forged_types_cannot_pass(self):
        mutations = ({'loginReceived': False}, {'disconnected': False}, {'disconnected': 1},
                     {'teleportsAcknowledged': True}, {'protocolVersion': 775},
                     {'actions': ['select', 'draw', 'release']}, {'error': 'timeout'},
                     {'runId': 'b' * 32}, {'startedAtEpochMs': self.now - 1},
                     {'completedAtEpochMs': self.now + 61000}, {'passed': False})
        for mutation in mutations:
            with self.subTest(mutation=mutation), self.assertRaises(runner.ValidationError):
                self.validate(dict(self.report, **mutation))

    def test_early_client_failure_and_idle_client_deadline_fail(self):
        paper, client = Mock(), Mock()
        paper.poll.return_value = None
        client.poll.return_value = 1
        with self.assertRaisesRegex(runner.ValidationError, 'Protocol player exited unsuccessfully'):
            runner.wait_for_report(self.root / 'absent.json', {}, self.now, 10, paper, client)
        client.poll.return_value = None
        with self.assertRaisesRegex(runner.ValidationError, 'Timed out'):
            runner.wait_for_report(self.root / 'absent.json', {}, self.now, 0, paper, client)

    @unittest.skipUnless(sys.platform == 'linux', 'Linux owned process/lease semantics')
    def test_lease_covers_two_owned_processes_through_failure_cleanup(self):
        unrelated = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])
        self.addCleanup(lambda: (unrelated.terminate(), unrelated.wait()) if unrelated.poll() is None else None)
        fixture = 'import sys; print("ready",flush=True); sys.stdin.readline(); print("Stopping server",flush=True)'
        with runner.server_lease(self.root, 0):
            paper = runner.OwnedServer([sys.executable, '-u', '-c', fixture], self.root, self.root / 'paper.log')
            client = runner.OwnedPlayer([sys.executable, '-u', '-c', fixture], self.root, self.root / 'player.log')
            try:
                paper.wait_text('ready', 5)
                client.wait_text('ready', 5)
                with self.assertRaises(runner.ResourceBusy), runner.server_lease(self.root, 0):
                    pass
            finally:
                self.assertTrue(client.stop(2)['clean'])
                self.assertIsNone(paper.process.poll())
                with self.assertRaises(runner.ResourceBusy), runner.server_lease(self.root, 0):
                    pass
                self.assertTrue(paper.stop(2)['clean'])
        self.assertIsNotNone(client.process.poll())
        self.assertIsNotNone(paper.process.poll())
        self.assertIsNone(unrelated.poll())
        with runner.server_lease(self.root, 0):
            pass

    @unittest.skipUnless(sys.platform == 'linux', 'Linux owned process semantics')
    def test_failed_client_exit_is_cleaned_but_forced_exit_is_not_clean(self):
        failed = runner.OwnedPlayer([sys.executable, '-c', 'raise SystemExit(1)'], self.root, self.root / 'failed.log')
        failed.process.wait(timeout=5)
        cleanup = failed.stop(1)
        self.assertTrue(cleanup['clean'])
        self.assertFalse(cleanup['successfulExit'])
        stuck = runner.OwnedPlayer([sys.executable, '-c', 'import time; time.sleep(30)'], self.root, self.root / 'stuck.log')
        cleanup = stuck.stop(0.05)
        self.assertTrue(cleanup['forced'])
        self.assertFalse(cleanup['clean'])
        self.assertIsNotNone(stuck.process.poll())


if __name__ == '__main__':
    unittest.main()
