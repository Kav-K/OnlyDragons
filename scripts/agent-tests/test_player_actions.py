"""Plan, client evidence, and independent server-journal rejection regressions."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('player_actions', Path(__file__).with_name('player_actions.py'))
actions = importlib.util.module_from_spec(spec)
spec.loader.exec_module(actions)


def step(identifier, action, **args):
    return {'id': identifier, 'action': action, 'args': args}


def fixture_plan():
    return {'schemaVersion': 1, 'planId': 'primitives-v1', 'targets': ['dummy'], 'actors': [
        {'id': 'alpha', 'sessions': [
            {'id': 'first', 'steps': [
                step('slot', 'selectSlot', slot=2), step('look', 'look', yaw=-30.5, pitch=10),
                step('move', 'move', x=0.5, y=65, z=0.5, onGround=True),
                step('command', 'command', command='onlydragons stats explain'),
                step('use', 'useItem', hand='main'), step('release', 'releaseUse'),
                step('swap', 'swapHands'), step('drop', 'dropItem', all=False),
                step('swing', 'swing', hand='off'), step('attack', 'attackEntity', targetRef='dummy'),
                step('block', 'interactBlock', x=0, y=64, z=0, face='up', hand='main',
                     cursorX=0.5, cursorY=1, cursorZ=0.5, insideBlock=False),
                step('respawn', 'respawn'), step('again', 'reconnect', delayMillis=100)]},
            {'id': 'second', 'steps': [step('done', 'disconnect')]}]},
        {'id': 'beta', 'sessions': [{'id': 'only', 'steps': [
            step('command', 'command', command='onlydragons status'), step('done', 'disconnect')]}]}]}


class ActionContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.project = Path(self.temp.name)
        self.plan = fixture_plan()
        self.run = 'b' * 32
        self.raw = json.dumps(self.plan).encode()
        self.sha = hashlib.sha256(self.raw).hexdigest()
        self.pins = {'minecraftVersion': '26.2', 'testPlayerProtocolVersion': '776',
                     'testPlayerProtocolLib': 'org.geysermc.mcprotocollib:protocol:26.2-pinned'}
        self.target = '12345678-1234-4234-8234-123456789abc'
        self.report = self.make_report()
        self.scenario = self.make_journal()

    def make_report(self):
        actors = []
        for actor, identity in zip(self.plan['actors'], actions.identities(self.run, self.plan)):
            sessions = []; begin = 1100
            for definition in actor['sessions']:
                bindings = [{'targetRef': 'dummy', 'uuid': self.target, 'receivedAtEpochMs': begin + 1}]
                steps = []
                for index, value in enumerate(definition['steps']):
                    row = {**copy.deepcopy(value), 'submittedAtEpochMs': begin + 20 + index * 10,
                           'packetTypes': actions.PACKETS[value['action']].copy()}
                    if value['action'] == 'attackEntity':
                        row.update(targetUuid=self.target, networkEntityId=17)
                    steps.append(row)
                sessions.append({'id': definition['id'], 'startedAtEpochMs': begin,
                                 'completedAtEpochMs': begin + 500, 'loginReceived': True,
                                 'playerLoadedSent': True, 'teleportsAcknowledged': 2,
                                 'steps': steps, 'messages': ['Received ordinary message'],
                                 'bindings': bindings, 'inventorySnapshots': [], 'inventoryConfirmations': [],
                                 'disconnected': True, 'passed': True, 'error': ''})
                terminal = definition['steps'][-1]
                begin += 500 + terminal['args'].get('delayMillis', 0)
            actors.append({'id': actor['id'], 'username': identity['name'], 'uuid': identity['uuid'], 'sessions': sessions})
        return {'schemaVersion': 2, 'runId': self.run, 'planId': self.plan['planId'], 'planSha256': self.sha,
                'authentication': 'offline-disposable-loopback', 'artifact': self.pins['testPlayerProtocolLib'],
                'minecraftVersion': '26.2', 'protocolVersion': 776, 'startedAtEpochMs': 1000,
                'completedAtEpochMs': 3000, 'actors': actors, 'passed': True, 'error': ''}

    def make_journal(self):
        journal = []
        for actor, client in zip(self.plan['actors'], self.report['actors']):
            for ordinal, session in enumerate(actor['sessions'], 1):
                def event(kind, detail):
                    journal.append({'kind': kind, 'actor': actor['id'], 'tick': len(journal) + 100, 'detail': detail})
                event('join', {'uuid': client['uuid'], 'sessionOrdinal': ordinal})
                event('server-setup-position', {'x': 0, 'y': 65, 'z': 0})
                event('bind', {'targetRef': 'dummy', 'uuid': self.target})
                for value in session['steps']:
                    event('request', {'session': session['id'], 'step': value['id']})
                event('quit', {'uuid': client['uuid']})
        return {'runId': self.run, 'observations': {'playerFixture': journal}}

    def verify(self, report=None):
        return actions.validate_report(self.report if report is None else report, self.plan, self.sha,
                                       self.run, self.pins, 900, 3100)

    def journal(self, scenario=None):
        return actions.validate_server_journal(self.scenario if scenario is None else scenario, self.report, self.plan)

    def test_all_declared_operations_parse_and_complete_evidence_replays(self):
        self.assertEqual(actions.parse_plan(self.raw), self.plan)
        self.assertEqual(self.verify(), self.report)
        self.assertEqual(self.journal(), self.scenario['observations']['playerFixture'])

    def test_raw_plan_duplicate_keys_nonfinite_overflow_and_trailing_data_fail(self):
        bad = [b'{"schemaVersion":1,"schemaVersion":1}', self.raw + b'{}', b'\xff', b'x' * 65537,
               self.raw.replace(b'-30.5', b'NaN'), self.raw.replace(b'-30.5', b'1e999'),
               self.raw.replace(b'-30.5', b'9' * 400)]
        for raw in bad:
            with self.subTest(rawLength=len(raw)):
                with self.assertRaises(actions.ActionValidationError):
                    actions.parse_plan(raw)

    def test_strict_schema_unknown_keys_null_and_integer_types(self):
        for value in (True, 1.0, '1', 2, None):
            bad = copy.deepcopy(self.plan); bad['schemaVersion'] = value
            with self.subTest(value=value), self.assertRaises(actions.ActionValidationError):
                actions.validate_plan(bad)
        bad = copy.deepcopy(self.plan); bad['actors'][0]['sessions'][0]['extra'] = 'ignored?'
        with self.assertRaises(actions.ActionValidationError):
            actions.validate_plan(bad)

    def test_plan_rejects_duplicate_actor_session_step_and_targets(self):
        for kind in ('actor', 'session', 'step', 'target'):
            bad = copy.deepcopy(self.plan)
            values = {'actor': bad['actors'], 'session': bad['actors'][0]['sessions'],
                      'step': bad['actors'][0]['sessions'][0]['steps'], 'target': bad['targets']}[kind]
            values.insert(0, copy.deepcopy(values[0]))
            with self.subTest(kind=kind), self.assertRaises(actions.ActionValidationError):
                actions.validate_plan(bad)

    def test_terminal_actions_and_session_order_are_mandatory(self):
        for action in ('disconnect', 'selectSlot', 'reconnect'):
            bad = copy.deepcopy(self.plan)
            if action == 'disconnect':
                bad['actors'][0]['sessions'][0]['steps'][-1] = step('done', action)
            elif action == 'selectSlot':
                bad['actors'][0]['sessions'][1]['steps'][-1] = step('done', action, slot=1)
            else:
                bad['actors'][0]['sessions'][1]['steps'][-1] = step('done', action, delayMillis=100)
            with self.subTest(action=action), self.assertRaises(actions.ActionValidationError):
                actions.validate_plan(bad)

    def test_total_step_and_actor_bounds(self):
        bad = copy.deepcopy(self.plan)
        bad['actors'][0]['sessions'][0]['steps'] = [step('n' + str(i), 'swing', hand='main') for i in range(63)] + [step('again', 'reconnect', delayMillis=100)]
        with self.assertRaisesRegex(actions.ActionValidationError, 'Too many total'):
            actions.validate_plan(bad)
        for values in ([], self.plan['actors'] * 3):
            with self.assertRaises(actions.ActionValidationError):
                actions.validate_plan(dict(self.plan, actors=values))

    def test_argument_bounds_types_enums_and_command_scope(self):
        bad = [('selectSlot', {'slot': True}), ('selectSlot', {'slot': 2.0}), ('selectSlot', {'slot': 9}),
               ('look', {'yaw': 181, 'pitch': 0}), ('look', {'yaw': 0, 'pitch': float('inf')}),
               ('move', {'x': 0, 'y': 321, 'z': 0, 'onGround': True}),
               ('useItem', {'hand': 'invalid'}), ('dropItem', {'all': 1}),
               ('reconnect', {'delayMillis': 99}), ('reconnect', {'delayMillis': 5001}),
               ('disconnect', {'extra': 1}), ('arbitraryPacket', {}),
               ('command', {'command': '/onlydragons status'}), ('command', {'command': 'stop'}),
               ('command', {'command': 'onlydragons status\nstop'}),
               ('command', {'command': 'onlydragons ' + 'x' * 256})]
        for action, args in bad:
            with self.subTest(action=action, args=args), self.assertRaises(actions.ActionValidationError):
                actions.validate_arguments(action, args)

    def test_attack_requires_a_declared_symbolic_target(self):
        bad = copy.deepcopy(self.plan); bad['targets'] = []
        with self.assertRaisesRegex(actions.ActionValidationError, 'Undeclared target'):
            actions.validate_plan(bad)

    def test_inventory_click_is_limited_to_real_player_slots_and_two_buttons(self):
        for slot in (5, 37, 40, 45):
            for button in ('left', 'right'):
                args = {'slot': slot, 'button': button}
                self.assertEqual(actions.validate_arguments('inventoryClick', args), args)
        for args in ({'slot': 4, 'button': 'left'}, {'slot': 46, 'button': 'left'},
                     {'slot': True, 'button': 'left'}, {'slot': 37.0, 'button': 'left'},
                     {'slot': '37', 'button': 'left'}, {'slot': 37, 'button': 'middle'},
                     {'slot': 37}, {'slot': 37, 'button': 'left', 'containerId': 1}):
            with self.subTest(args=args), self.assertRaises(actions.ActionValidationError):
                actions.validate_arguments('inventoryClick', args)

    def setup_inventory(self):
        self.plan['actors'][0]['sessions'][0]['steps'][0:0] = [
            step('click-' + str(i), 'inventoryClick', slot=slot, button='left')
            for i, slot in enumerate((37, 40, 40, 37))]
        self.sha = hashlib.sha256(json.dumps(self.plan).encode()).hexdigest()
        self.report = self.make_report()
        session = self.report['actors'][0]['sessions'][0]
        states = (32766, 32767, 0, 0, 1)
        snapshots = []
        for index, state in enumerate(states):
            received = 1110 if index == 0 else 1115 + index * 10
            snapshots.append({'sequence': index + 1, 'containerId': 0, 'stateId': state,
                              'slotCount': 46, 'receivedAtEpochMs': received,
                              'slotSha256': [hashlib.sha256(('slot-' + str(index) + '-' + str(slot)).encode()).hexdigest()
                                             for slot in range(46)],
                              'sha256': hashlib.sha256(('packet-' + str(index)).encode()).hexdigest()})
        session['inventorySnapshots'] = snapshots
        for row, snapshot in zip(session['steps'][:4], snapshots):
            row.update(containerId=0, stateId=snapshot['stateId'], inventorySnapshotSequence=snapshot['sequence'],
                       inventorySnapshotReceivedAtEpochMs=snapshot['receivedAtEpochMs'],
                       inventorySnapshotSha256=snapshot['sha256'], inventoryConfirmationSequence=0)
        self.scenario = self.make_journal()

    def test_inventory_clicks_require_fresh_full_snapshots_and_allow_state_id_wrap_or_repeat(self):
        self.setup_inventory()
        self.assertEqual(self.verify(), self.report)
        self.journal()

    def test_inventory_snapshot_capture_rejects_wrong_shape_hash_window_and_sequence(self):
        self.setup_inventory()
        for field, value in (('sequence', 2), ('containerId', 1), ('containerId', False),
                             ('stateId', 32768), ('stateId', True), ('slotCount', 45),
                             ('slotCount', 46.0), ('sha256', 'unverified'), ('receivedAtEpochMs', 900)):
            bad = copy.deepcopy(self.report)
            bad['actors'][0]['sessions'][0]['inventorySnapshots'][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(actions.ActionValidationError): self.verify(bad)
        for snapshots in (None, [], self.report['actors'][0]['sessions'][0]['inventorySnapshots'] * 26):
            bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0]['inventorySnapshots'] = snapshots
            with self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_inventory_click_receipt_must_match_exact_prior_snapshot_and_packet(self):
        self.setup_inventory()
        for field, value in (('containerId', 1), ('stateId', 2), ('stateId', False),
                             ('inventorySnapshotSequence', 2), ('inventorySnapshotSequence', True),
                             ('inventorySnapshotSha256', 'f' * 64), ('inventorySnapshotReceivedAtEpochMs', 999999),
                             ('packetTypes', [])):
            bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0]['steps'][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(actions.ActionValidationError): self.verify(bad)
        bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0]['steps'][0].pop('inventorySnapshotSha256')
        with self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_inventory_snapshot_cannot_be_reused_for_a_second_click(self):
        self.setup_inventory()
        bad = copy.deepcopy(self.report); steps = bad['actors'][0]['sessions'][0]['steps']
        for key in ('containerId', 'stateId', 'inventorySnapshotSequence',
                    'inventorySnapshotReceivedAtEpochMs', 'inventorySnapshotSha256', 'inventoryConfirmationSequence'):
            steps[1][key] = steps[0][key]
        with self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_known_newer_snapshot_and_missing_final_resync_are_rejected(self):
        self.setup_inventory()
        bad = copy.deepcopy(self.report)
        bad['actors'][0]['sessions'][0]['inventorySnapshots'][1]['receivedAtEpochMs'] = 1111
        with self.assertRaisesRegex(actions.ActionValidationError, 'older than'):
            self.verify(bad)
        bad = copy.deepcopy(self.report)
        bad['actors'][0]['sessions'][0]['inventorySnapshots'].pop()
        with self.assertRaisesRegex(actions.ActionValidationError, 'resynchronization'):
            self.verify(bad)

    def setup_confirmations(self):
        self.setup_inventory()
        session = self.report['actors'][0]['sessions'][0]
        for index, (snapshot, click) in enumerate(zip(session['inventorySnapshots'], session['steps'][:4]), 1):
            confirmation = {'sequence': index, 'inventorySnapshotSequence': snapshot['sequence'],
                            'containerId': 0, 'stateId': (snapshot['stateId'] + 1) % 32768, 'slot': 45,
                            'receivedAtEpochMs': snapshot['receivedAtEpochMs'] + 1,
                            'sha256': hashlib.sha256(('confirmation-' + str(index)).encode()).hexdigest(),
                            'itemSha256': snapshot['slotSha256'][45]}
            session['inventoryConfirmations'].append(confirmation)
            click.update(stateId=confirmation['stateId'], inventoryConfirmationSequence=index)

    def test_unchanged_slot_confirmations_advance_click_state_and_allow_wrap(self):
        self.setup_confirmations()
        self.assertEqual(self.verify(), self.report)
        self.assertEqual(self.report['actors'][0]['sessions'][0]['steps'][1]['stateId'], 0)

    def test_confirmation_requires_exact_original_slot_digest(self):
        self.setup_confirmations()
        for replacement in ('a' * 64, self.report['actors'][0]['sessions'][0]['inventorySnapshots'][0]['slotSha256'][37]):
            bad = copy.deepcopy(self.report)
            bad['actors'][0]['sessions'][0]['inventoryConfirmations'][0]['itemSha256'] = replacement
            with self.assertRaisesRegex(actions.ActionValidationError, 'item bytes'): self.verify(bad)
        bad = copy.deepcopy(self.report)
        bad['actors'][0]['sessions'][0]['inventorySnapshots'][0]['slotSha256'][45] = 'c' * 64
        with self.assertRaisesRegex(actions.ActionValidationError, 'item bytes'): self.verify(bad)

    def test_snapshot_requires_all_46_valid_slot_digests(self):
        self.setup_inventory()
        for digests in (None, [], ['a' * 64] * 45, ['a' * 64] * 47, ['invalid'] * 46):
            bad = copy.deepcopy(self.report)
            bad['actors'][0]['sessions'][0]['inventorySnapshots'][0]['slotSha256'] = digests
            with self.subTest(digests=digests), self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_confirmation_shape_sequence_bounds_and_packet_digest_fail_closed(self):
        self.setup_confirmations()
        for field, value in (('sequence', 2), ('sequence', True), ('inventorySnapshotSequence', 0),
                             ('inventorySnapshotSequence', 6), ('containerId', 1), ('slot', -1), ('slot', 46),
                             ('slot', True), ('stateId', True), ('stateId', 32768),
                             ('receivedAtEpochMs', 1100), ('sha256', 'invalid'), ('itemSha256', 'invalid')):
            bad = copy.deepcopy(self.report)
            bad['actors'][0]['sessions'][0]['inventoryConfirmations'][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(actions.ActionValidationError): self.verify(bad)
        for values in (None, self.report['actors'][0]['sessions'][0]['inventoryConfirmations'] * 129):
            bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0]['inventoryConfirmations'] = values
            with self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_confirmation_state_cannot_skip_or_reverse_but_may_repeat(self):
        self.setup_confirmations()
        for state in (32765, 0):
            bad = copy.deepcopy(self.report)
            bad['actors'][0]['sessions'][0]['inventoryConfirmations'][0]['stateId'] = state
            bad['actors'][0]['sessions'][0]['steps'][0]['stateId'] = state
            with self.assertRaisesRegex(actions.ActionValidationError, 'skipped or reversed'): self.verify(bad)
        session = self.report['actors'][0]['sessions'][0]
        session['inventoryConfirmations'][0]['stateId'] = session['inventorySnapshots'][0]['stateId']
        session['steps'][0]['stateId'] = session['inventorySnapshots'][0]['stateId']
        self.verify()

    def test_click_must_use_final_confirmation_attached_to_its_snapshot(self):
        self.setup_confirmations()
        session = self.report['actors'][0]['sessions'][0]
        repeated = dict(session['inventoryConfirmations'][0], receivedAtEpochMs=1112)
        session['inventoryConfirmations'].insert(1, repeated)
        for index, confirmation in enumerate(session['inventoryConfirmations'], 1): confirmation['sequence'] = index
        for click in session['steps'][:4]: click['inventoryConfirmationSequence'] += 1
        self.verify()
        for sequence in (0, 1, True):
            bad = copy.deepcopy(self.report)
            bad['actors'][0]['sessions'][0]['steps'][0]['inventoryConfirmationSequence'] = sequence
            with self.subTest(sequence=sequence), self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_confirmations_cannot_arrive_after_click_or_attach_to_obsolete_snapshot(self):
        self.setup_confirmations()
        for received in (1121, 1126):
            bad = copy.deepcopy(self.report)
            bad['actors'][0]['sessions'][0]['inventoryConfirmations'][0]['receivedAtEpochMs'] = received
            with self.subTest(received=received), self.assertRaises(actions.ActionValidationError): self.verify(bad)
        bad = copy.deepcopy(self.report)
        bad['actors'][0]['sessions'][0]['inventoryConfirmations'][2]['inventorySnapshotSequence'] = 1
        with self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_confirmation_never_replaces_a_fresh_full_snapshot_after_click(self):
        self.setup_confirmations()
        bad = copy.deepcopy(self.report); session = bad['actors'][0]['sessions'][0]
        session['inventorySnapshots'].pop()
        with self.assertRaisesRegex(actions.ActionValidationError, 'resynchronization'): self.verify(bad)
        bad = copy.deepcopy(self.report); session = bad['actors'][0]['sessions'][0]
        for field in ('containerId', 'stateId', 'inventorySnapshotSequence', 'inventorySnapshotReceivedAtEpochMs',
                      'inventorySnapshotSha256', 'inventoryConfirmationSequence'):
            session['steps'][1][field] = session['steps'][0][field]
        with self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_confirmation_capture_is_required_in_positive_and_abort_session_schemas(self):
        bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0].pop('inventoryConfirmations')
        with self.assertRaises(actions.ActionValidationError): self.verify(bad)
        self.setup_abort()
        bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0].pop('inventoryConfirmations')
        with self.assertRaises(actions.ActionValidationError): self.abort_verify(bad)
        bad = copy.deepcopy(self.report)
        bad['actors'][0]['sessions'][0]['inventoryConfirmations'] = [{'sequence': 1}]
        with self.assertRaises(actions.ActionValidationError): self.abort_verify(bad)

    def test_load_plan_binds_raw_file_bytes_and_scoped_path(self):
        relative = 'dev/game-tests/player-plans/primitives-v1.json'
        path = self.project / relative; path.parent.mkdir(parents=True); path.write_bytes(self.raw)
        actual, sha, plan = actions.load_plan(self.project, {'playerActionPlan': relative})
        self.assertEqual((actual, sha, plan), (path, self.sha, self.plan))
        for value in ('../outside.json', '/tmp/plan.json', 'dev/game-tests/player-plans/../secret.json',
                      r'dev\game-tests\player-plans\plan.json', 'dev/game-tests/scenarios.json', None):
            with self.subTest(value=value), self.assertRaises(actions.ActionValidationError):
                actions.load_plan(self.project, {'playerActionPlan': value})

    def test_plan_path_cannot_redirect_to_another_file(self):
        outside = self.project / 'outside.json'; outside.write_bytes(self.raw)
        path = self.project / 'dev/game-tests/player-plans/alias.json'; path.parent.mkdir(parents=True)
        try:
            path.symlink_to(outside)
        except (OSError, NotImplementedError) as error:
            self.skipTest('Host cannot create test symlinks: ' + type(error).__name__)
        with self.assertRaisesRegex(actions.ActionValidationError, 'symlink'):
            actions.load_plan(self.project, {'playerActionPlan': path.relative_to(self.project).as_posix()})

    def test_deterministic_distinct_whitelist_identities(self):
        expected = actions.identities(self.run, self.plan)
        self.assertEqual(expected[0]['name'], 'od_bbbbbbbbbb_0')
        self.assertEqual(expected[1]['name'], 'od_bbbbbbbbbb_1')
        self.assertNotEqual(expected[0]['uuid'], expected[1]['uuid'])
        self.assertEqual(expected, actions.identities(self.run, copy.deepcopy(self.plan)))
        with self.assertRaises(actions.ActionValidationError):
            actions.identities('stale-run', self.plan)

    def test_report_rejects_stale_run_plan_artifact_protocol_and_false_success(self):
        for key, value in (('runId', 'a' * 32), ('planId', 'other'), ('planSha256', 'a' * 64),
                           ('artifact', 'unpinned'), ('protocolVersion', 775), ('protocolVersion', True),
                           ('minecraftVersion', 'old'), ('schemaVersion', 2.0), ('passed', 1),
                           ('passed', False), ('error', 'failure'), ('authentication', 'online')):
            bad = copy.deepcopy(self.report); bad[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(actions.ActionValidationError):
                self.verify(bad)

    def test_report_rejects_actor_session_and_step_removal_duplication_or_reordering(self):
        for kind in ('actor', 'session', 'step'):
            for mutation in ('remove', 'duplicate', 'reverse'):
                bad = copy.deepcopy(self.report)
                target = {'actor': bad['actors'], 'session': bad['actors'][0]['sessions'],
                          'step': bad['actors'][0]['sessions'][0]['steps']}[kind]
                if mutation == 'remove': target.pop()
                elif mutation == 'duplicate': target.append(copy.deepcopy(target[-1]))
                else: target.reverse()
                with self.subTest(kind=kind, mutation=mutation), self.assertRaises(actions.ActionValidationError):
                    self.verify(bad)

    def test_report_rejects_identity_and_lifecycle_substitution(self):
        for key, value in (('id', 'wrong'), ('username', 'wrong'), ('uuid', self.target)):
            bad = copy.deepcopy(self.report); bad['actors'][0][key] = value
            with self.assertRaises(actions.ActionValidationError): self.verify(bad)
        for key, value in (('loginReceived', False), ('playerLoadedSent', 1), ('disconnected', False),
                           ('teleportsAcknowledged', 0), ('teleportsAcknowledged', True),
                           ('passed', False), ('error', 'partial')):
            bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0][key] = value
            with self.subTest(key=key), self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_report_rejects_wrong_arguments_packet_counts_and_submission_time(self):
        for field, value in (('args', {'slot': 3}), ('args', {'slot': True}), ('packetTypes', []),
                             ('packetTypes', ['ServerboundSetCarriedItemPacket'] * 2),
                             ('submittedAtEpochMs', 999999), ('submittedAtEpochMs', True)):
            bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0]['steps'][0][field] = value
            with self.subTest(field=field, value=value), self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_report_rejects_out_of_window_and_early_reconnect_timestamps(self):
        for key, value in (('startedAtEpochMs', 899), ('completedAtEpochMs', 3101)):
            bad = copy.deepcopy(self.report); bad[key] = value
            with self.assertRaises(actions.ActionValidationError): self.verify(bad)
        bad = copy.deepcopy(self.report)
        bad['actors'][0]['sessions'][1]['startedAtEpochMs'] -= 1
        with self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_report_rejects_missing_late_or_changed_attack_binding(self):
        for mutation in ('missing', 'late', 'changed', 'unobserved'):
            bad = copy.deepcopy(self.report); session = bad['actors'][0]['sessions'][0]
            attack = next(row for row in session['steps'] if row['action'] == 'attackEntity')
            if mutation == 'missing': session['bindings'] = []
            elif mutation == 'late': session['bindings'][0]['receivedAtEpochMs'] = attack['submittedAtEpochMs'] + 1
            elif mutation == 'changed': attack['targetUuid'] = '00000000-0000-0000-0000-000000000001'
            else: attack.pop('networkEntityId')
            with self.subTest(mutation=mutation), self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_report_rejects_unbounded_messages_and_control_markers(self):
        for messages in (['message'] * 129, ['x' * 2049], ['OD_ACTION:run:fake'], [''], [None]):
            bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0]['messages'] = messages
            with self.subTest(messagesCount=len(messages)), self.assertRaises(actions.ActionValidationError): self.verify(bad)

    def test_server_journal_requires_matching_actor_join_and_quit_counts(self):
        for kind in ('join', 'quit', 'request', 'bind'):
            bad = copy.deepcopy(self.scenario)
            rows = bad['observations']['playerFixture']
            rows.pop(next(i for i, row in enumerate(rows) if row['kind'] == kind))
            with self.subTest(kind=kind), self.assertRaises(actions.ActionValidationError): self.journal(bad)

    def test_server_journal_rejects_forged_session_step_identity_and_binding(self):
        for kind, field, value in (('join', 'uuid', self.target), ('join', 'sessionOrdinal', True),
                                   ('request', 'session', 'wrong'), ('request', 'step', 'wrong'),
                                   ('bind', 'uuid', '00000000-0000-0000-0000-000000000001')):
            bad = copy.deepcopy(self.scenario)
            event = next(row for row in bad['observations']['playerFixture'] if row['kind'] == kind)
            event['detail'][field] = value
            with self.subTest(kind=kind, field=field), self.assertRaises(actions.ActionValidationError): self.journal(bad)

    def test_server_journal_rejects_tick_reordering_duplicate_events_and_unknown_actors(self):
        for mutation in ('tick', 'duplicate', 'actor', 'kind'):
            bad = copy.deepcopy(self.scenario); rows = bad['observations']['playerFixture']
            if mutation == 'tick': rows[1]['tick'] = 99
            elif mutation == 'duplicate': rows.insert(1, copy.deepcopy(rows[0]))
            elif mutation == 'actor': rows[1]['actor'] = 'unplanned'
            else: rows[1]['kind'] = 'client-success'
            with self.subTest(mutation=mutation), self.assertRaises(actions.ActionValidationError): self.journal(bad)

    def test_server_journal_cannot_bind_a_target_after_requesting_the_attack(self):
        bad = copy.deepcopy(self.scenario); rows = bad['observations']['playerFixture']
        binding = rows.pop(next(i for i, row in enumerate(rows) if row['kind'] == 'bind'))
        index = next(i for i, row in enumerate(rows) if row['kind'] == 'request' and row['detail']['step'] == 'attack')
        rows.insert(index + 1, binding)
        for index, row in enumerate(rows): row['tick'] = index + 100
        with self.assertRaisesRegex(actions.ActionValidationError, 'before attack'):
            self.journal(bad)

    def test_server_journal_cannot_come_from_a_different_run(self):
        bad = copy.deepcopy(self.scenario); bad['runId'] = 'f' * 32
        with self.assertRaisesRegex(actions.ActionValidationError, 'run IDs'):
            self.journal(bad)

    def test_exact_and_fragment_messages_are_received_in_the_declared_actor_session(self):
        self.report['actors'][0]['sessions'][0]['messages'] = [
            'Permission denied.', 'Granted ordinary bow.', 'ferocity raw=3.0 effective=3.0 source=item']
        expected = {'requiredActorMessages': [
            {'actor': 'alpha', 'session': 'first', 'id': 'permission-denied', 'exact': 'Permission denied.'},
            {'actor': 'alpha', 'session': 'first', 'id': 'grant', 'exact': 'Granted ordinary bow.'},
            {'actor': 'alpha', 'session': 'first', 'id': 'numeric-stats',
             'containsAll': ['ferocity', 'raw=3.0', 'effective=3.0', 'source=item']}]}
        self.assertEqual(actions.validate_messages(self.report, expected), expected['requiredActorMessages'])
        self.report['actors'][0]['sessions'][0]['messages'][-1] = 'ferocity raw=0.0 effective=0.0 source=item'
        with self.assertRaisesRegex(actions.ActionValidationError, 'numeric-stats'):
            actions.validate_messages(self.report, expected)

    def test_messages_from_another_actor_or_session_do_not_satisfy_requirement(self):
        requirement = {'requiredActorMessages': [
            {'actor': 'alpha', 'session': 'first', 'id': 'denial', 'exact': 'Permission denied.'}]}
        for destination in ((0, 1), (1, 0)):
            bad = copy.deepcopy(self.report)
            bad['actors'][destination[0]]['sessions'][destination[1]]['messages'] = ['Permission denied.']
            with self.subTest(destination=destination), self.assertRaises(actions.ActionValidationError):
                actions.validate_messages(bad, requirement)

    def test_message_matchers_reject_unknown_fields_duplicates_unbounded_and_empty_patterns(self):
        base = {'actor': 'alpha', 'session': 'first', 'id': 'text', 'exact': 'Received ordinary message'}
        values = [dict(base, ignored=True), dict(base, containsAll=['message']), dict(base, exact=''),
                  dict(base, exact='x' * 2049), dict(base, actor='missing'), dict(base, session='missing'),
                  {'actor': 'alpha', 'session': 'first', 'id': 'text', 'containsAll': []},
                  {'actor': 'alpha', 'session': 'first', 'id': 'text', 'containsAll': ['x'] * 9}]
        for value in values:
            with self.subTest(value=value), self.assertRaises(actions.ActionValidationError):
                actions.validate_messages(self.report, {'requiredActorMessages': [value]})
        for values in ([base, base], [base] * 33):
            with self.assertRaises(actions.ActionValidationError):
                actions.validate_messages(self.report, {'requiredActorMessages': values})

    def test_all_fragments_must_appear_in_one_received_message(self):
        bad = copy.deepcopy(self.report)
        bad['actors'][0]['sessions'][0]['messages'] = ['raw=3.0', 'effective=3.0']
        with self.assertRaises(actions.ActionValidationError):
            actions.validate_messages(bad, {'requiredActorMessages': [
                {'actor': 'alpha', 'session': 'first', 'id': 'stats', 'containsAll': ['raw=3.0', 'effective=3.0']}]})

    def test_missing_or_malformed_actor_message_capture_fails(self):
        for value in (None, [None], ['OD_BIND:fake'], ['x'] * 129):
            bad = copy.deepcopy(self.report); bad['actors'][0]['sessions'][0]['messages'] = value
            with self.subTest(value=value), self.assertRaises(actions.ActionValidationError):
                actions.validate_messages(bad, {})

    def setup_abort(self):
        self.plan = {'schemaVersion': 1, 'planId': 'cleanup-abort-v1', 'targets': [], 'actors': [
            {'id': actor, 'sessions': [{'id': 's1', 'steps': [
                step('status', 'command', command='onlydragons status'), step('quit', 'disconnect')]}]}
            for actor in ('alpha', 'beta')]}
        self.sha = hashlib.sha256(json.dumps(self.plan).encode()).hexdigest()
        self.report = self.make_report()
        self.report.update(passed=False, error=actions.ABORT_ERROR)
        for actor in self.report['actors']:
            session = actor['sessions'][0]
            session.update(passed=False, error=actions.ABORT_ERROR, bindings=[],
                           steps=session['steps'][:1], messages=['OnlyDragons ready | version 0.1.0'])
        self.scenario = self.make_journal()
        self.scenario['observations']['playerFixture'] = [event for event in self.scenario['observations']['playerFixture']
            if event['kind'] in ('join', 'server-setup-position')
            or (event['kind'] == 'request' and event['detail']['step'] == 'status')]

    def abort_verify(self, report=None):
        return actions.validate_abort_report(self.report if report is None else report, self.plan, self.sha,
                                             self.run, self.pins, 900, 3100)

    def test_exact_orderly_abort_retains_two_real_command_prefixes_and_joined_server_sessions(self):
        self.setup_abort()
        self.assertEqual(self.abort_verify(), self.report)
        self.assertEqual(actions.validate_abort_journal(self.scenario, self.report, self.plan),
                         self.scenario['observations']['playerFixture'])
        with self.assertRaises(actions.ActionValidationError): self.verify()
        with self.assertRaises(actions.ActionValidationError): self.journal()

    def test_abort_never_accepts_timeout_crash_early_exit_or_forged_success(self):
        self.setup_abort()
        for error in ('Timed out waiting for actions', 'Deliberate early client exit', 'crashed', ''):
            bad = copy.deepcopy(self.report); bad['error'] = error
            with self.subTest(error=error), self.assertRaises(actions.ActionValidationError): self.abort_verify(bad)
        bad = copy.deepcopy(self.report); bad['passed'] = True
        with self.assertRaises(actions.ActionValidationError): self.abort_verify(bad)

    def test_abort_requires_both_joined_loaded_acked_and_disconnected_sessions(self):
        self.setup_abort()
        for actor in range(2):
            for field, value in (('loginReceived', False), ('playerLoadedSent', False),
                                 ('teleportsAcknowledged', 0), ('disconnected', False),
                                 ('error', 'Unexpected disconnect'), ('passed', True)):
                bad = copy.deepcopy(self.report); bad['actors'][actor]['sessions'][0][field] = value
                with self.subTest(actor=actor, field=field), self.assertRaises(actions.ActionValidationError): self.abort_verify(bad)

    def test_abort_rejects_missing_extra_wrong_or_synthetic_terminal_steps(self):
        self.setup_abort()
        for mutation in ('missing', 'extra', 'different-command', 'terminal'):
            bad = copy.deepcopy(self.report); rows = bad['actors'][0]['sessions'][0]['steps']
            if mutation == 'missing': rows.clear()
            elif mutation == 'extra': rows.append(copy.deepcopy(rows[0]))
            elif mutation == 'different-command': rows[0]['args']['command'] = 'onlydragons reload'
            else: rows[0].update(id='quit', action='disconnect', args={}, packetTypes=[])
            with self.subTest(mutation=mutation), self.assertRaises(actions.ActionValidationError): self.abort_verify(bad)

    def test_abort_rejects_wrong_identity_hash_timing_and_missing_received_status(self):
        self.setup_abort()
        for mutation in ('identity', 'hash', 'time', 'message'):
            bad = copy.deepcopy(self.report)
            if mutation == 'identity': bad['actors'][1]['uuid'] = self.target
            elif mutation == 'hash': bad['planSha256'] = 'f' * 64
            elif mutation == 'time': bad['actors'][0]['sessions'][0]['completedAtEpochMs'] = 1
            else: bad['actors'][1]['sessions'][0]['messages'] = []
            with self.subTest(mutation=mutation), self.assertRaises(actions.ActionValidationError): self.abort_verify(bad)

    def test_abort_is_not_a_general_prefix_acceptance_for_other_plans(self):
        self.setup_abort()
        for mutation in ('one-actor', 'other-command', 'target'):
            bad = copy.deepcopy(self.plan)
            if mutation == 'one-actor': bad['actors'].pop()
            elif mutation == 'other-command': bad['actors'][0]['sessions'][0]['steps'][0]['args']['command'] = 'onlydragons reload'
            else: bad['targets'] = ['dummy']
            with self.subTest(mutation=mutation), self.assertRaises(actions.ActionValidationError):
                actions.validate_abort_report(self.report, bad, self.sha, self.run, self.pins, 900, 3100)

    def test_abort_server_journal_rejects_missing_command_or_join_and_any_terminal_request_or_quit(self):
        self.setup_abort()
        for mutation in ('missing-command', 'missing-join', 'terminal-request', 'quit'):
            bad = copy.deepcopy(self.scenario); events = bad['observations']['playerFixture']
            if mutation in ('missing-command', 'missing-join'):
                kind = 'request' if mutation == 'missing-command' else 'join'
                events.pop(next(i for i, event in enumerate(events) if event['kind'] == kind))
            elif mutation == 'terminal-request':
                events.append({'kind': 'request', 'actor': 'alpha', 'tick': 1000,
                               'detail': {'session': 's1', 'step': 'quit'}})
            else:
                events.append({'kind': 'quit', 'actor': 'alpha', 'tick': 1000,
                               'detail': {'uuid': self.report['actors'][0]['uuid']}})
            with self.subTest(mutation=mutation), self.assertRaises(actions.ActionValidationError):
                actions.validate_abort_journal(bad, self.report, self.plan)


if __name__ == '__main__':
    unittest.main()
