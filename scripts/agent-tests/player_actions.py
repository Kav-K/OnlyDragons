"""Strict, bounded contracts for real protocol-player action fixtures."""
from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import uuid


class ActionValidationError(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise ActionValidationError(message)


def _object(value, *keys):
    require(isinstance(value, dict) and set(value) == set(keys),
            'Unknown or missing object fields; expected: ' + ', '.join(keys))
    return value


def _array(value, low, high):
    require(isinstance(value, list) and low <= len(value) <= high, 'Array size outside bounds')
    return value


def _id(value):
    require(isinstance(value, str) and re.fullmatch(r'[a-z][a-z0-9-]{0,31}', value), 'Invalid identifier')
    return value


def _integer(value, low, high):
    require(type(value) is int and low <= value <= high, 'Integer outside bounds or wrong type')
    return value


def _number(value, low, high):
    try:
        finite = type(value) in (int, float) and math.isfinite(value)
    except OverflowError:
        finite = False
    require(finite and low <= value <= high,
            'Number outside bounds, nonfinite, or wrong type')
    return value


def _boolean(value):
    require(type(value) is bool, 'Expected boolean')
    return value


def _choice(value, choices):
    require(isinstance(value, str) and value in choices, 'Invalid enum value')
    return value


def _bounded_tree(value, depth=0, count=None):
    count = [0] if count is None else count
    count[0] += 1
    require(depth <= 12 and count[0] <= 4096, 'JSON nesting/node limit')
    require(value is not None, 'Null plan value')
    if isinstance(value, dict):
        for child in value.values():
            _bounded_tree(child, depth + 1, count)
    elif isinstance(value, list):
        for child in value:
            _bounded_tree(child, depth + 1, count)
    elif type(value) is float:
        require(math.isfinite(value), 'Nonfinite JSON number')
    else:
        require(type(value) in (str, int, bool), 'Invalid JSON value')


def _pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate JSON key: ' + key)
        result[key] = value
    return result


def parse_plan(raw):
    require(isinstance(raw, bytes) and 0 < len(raw) <= 65536, 'Plan exceeds byte limit')
    try:
        value = json.loads(raw.decode('utf-8'), object_pairs_hook=_pairs,
                           parse_constant=lambda value: (_ for _ in ()).throw(
                               ActionValidationError('Nonfinite JSON number')))
    except (ValueError, UnicodeError, RecursionError) as error:
        raise ActionValidationError('Malformed plan JSON') from error
    return validate_plan(value)


def validate_arguments(action, args):
    if action == 'selectSlot':
        _object(args, 'slot'); _integer(args['slot'], 0, 8)
    elif action == 'look':
        _object(args, 'yaw', 'pitch')
        _number(args['yaw'], -180, 180); _number(args['pitch'], -90, 90)
    elif action == 'move':
        _object(args, 'x', 'y', 'z', 'onGround')
        _number(args['x'], -1024, 1024); _number(args['y'], -64, 320)
        _number(args['z'], -1024, 1024); _boolean(args['onGround'])
    elif action == 'command':
        _object(args, 'command')
        value = args['command']
        require(isinstance(value, str) and len(value) <= 256
                and re.fullmatch(r'onlydragons(?: [ -~]+)?', value),
                'Command must be a bounded OnlyDragons command without slash/control characters')
    elif action in ('useItem', 'swing'):
        _object(args, 'hand'); _choice(args['hand'], ('main', 'off'))
    elif action == 'attackEntity':
        _object(args, 'targetRef'); _id(args['targetRef'])
    elif action == 'dropItem':
        _object(args, 'all'); _boolean(args['all'])
    elif action == 'inventoryClick':
        _object(args, 'slot', 'button')
        _integer(args['slot'], 5, 45); _choice(args['button'], ('left', 'right'))
    elif action == 'reconnect':
        _object(args, 'delayMillis'); _integer(args['delayMillis'], 100, 5000)
    elif action == 'interactBlock':
        _object(args, 'x', 'y', 'z', 'face', 'hand', 'cursorX', 'cursorY', 'cursorZ', 'insideBlock')
        _integer(args['x'], -1024, 1024); _integer(args['y'], -64, 319); _integer(args['z'], -1024, 1024)
        _choice(args['face'], ('down', 'up', 'north', 'south', 'west', 'east'))
        _choice(args['hand'], ('main', 'off'))
        for key in ('cursorX', 'cursorY', 'cursorZ'):
            _number(args[key], 0, 1)
        _boolean(args['insideBlock'])
    elif action in ('releaseUse', 'respawn', 'swapHands', 'disconnect'):
        _object(args)
    else:
        raise ActionValidationError('Unknown action')
    return args


def validate_plan(plan):
    _bounded_tree(plan)
    _object(plan, 'schemaVersion', 'planId', 'targets', 'actors')
    _integer(plan['schemaVersion'], 1, 1); _id(plan['planId'])
    targets = [_id(value) for value in _array(plan['targets'], 0, 16)]
    require(len(set(targets)) == len(targets), 'Duplicate target')
    actors = _array(plan['actors'], 1, 4)
    actor_ids = set(); total = 0
    for actor in actors:
        _object(actor, 'id', 'sessions'); _id(actor['id'])
        require(actor['id'] not in actor_ids, 'Duplicate actor'); actor_ids.add(actor['id'])
        sessions = _array(actor['sessions'], 1, 4); session_ids = set()
        for i, session in enumerate(sessions):
            _object(session, 'id', 'steps'); _id(session['id'])
            require(session['id'] not in session_ids, 'Duplicate session'); session_ids.add(session['id'])
            steps = _array(session['steps'], 1, 64); step_ids = set()
            for j, step in enumerate(steps):
                _object(step, 'id', 'action', 'args'); _id(step['id'])
                require(step['id'] not in step_ids, 'Duplicate step'); step_ids.add(step['id'])
                validate_arguments(step['action'], step['args'])
                if step['action'] == 'attackEntity':
                    require(step['args']['targetRef'] in targets, 'Undeclared target')
                terminal = step['action'] in ('disconnect', 'reconnect')
                require(terminal == (j == len(steps) - 1), 'Session must end with exactly one terminal action')
                if terminal:
                    expected = 'disconnect' if i == len(sessions) - 1 else 'reconnect'
                    require(step['action'] == expected, 'Wrong session terminal action')
                total += 1
                require(total <= 64, 'Too many total steps')
    return plan


def load_plan(project, descriptor):
    require(isinstance(descriptor, dict), 'Missing scenario descriptor')
    value = descriptor.get('playerActionPlan')
    require(isinstance(value, str), 'Scenario requires an explicit playerActionPlan')
    relative = PurePosixPath(value)
    require(not relative.is_absolute() and relative.as_posix() == value
            and '..' not in relative.parts and '\\' not in value
            and value.startswith('dev/game-tests/player-plans/') and relative.suffix == '.json',
            'Player plan path must stay under dev/game-tests/player-plans')
    root = Path(project).resolve()
    current = root
    for component in relative.parts:
        current = current / component
        require(not current.is_symlink(), 'Player plan path cannot follow a symlink')
    require(current.is_file() and current.resolve().is_relative_to(root), 'Missing or unscoped player plan file')
    require(current.stat().st_size <= 65536, 'Plan exceeds byte limit')
    with current.open('rb') as handle:
        raw = handle.read(65537)
    return current, hashlib.sha256(raw).hexdigest(), parse_plan(raw)


def identities(run_id, plan):
    require(isinstance(run_id, str) and re.fullmatch(r'[a-f0-9]{32}', run_id), 'Invalid action run ID')
    validate_plan(plan)
    result = []
    for index, actor in enumerate(plan['actors']):
        name = 'od_' + run_id[:10] + '_' + str(index)
        raw = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode('utf-8')).digest())
        raw[6] = (raw[6] & 0x0f) | 0x30
        raw[8] = (raw[8] & 0x3f) | 0x80
        result.append({'name': name, 'uuid': str(uuid.UUID(bytes=bytes(raw)))})
    return result


PACKETS = {
    'selectSlot': ['ServerboundSetCarriedItemPacket'],
    'look': ['ServerboundMovePlayerRotPacket'],
    'move': ['ServerboundMovePlayerPosPacket'],
    'command': ['ServerboundChatCommandPacket'],
    'useItem': ['ServerboundUseItemPacket'],
    'releaseUse': ['ServerboundPlayerActionPacket'],
    'swapHands': ['ServerboundPlayerActionPacket'],
    'dropItem': ['ServerboundPlayerActionPacket'],
    'inventoryClick': ['ServerboundContainerClickPacket'],
    'swing': ['ServerboundSwingPacket'],
    'attackEntity': ['ServerboundAttackPacket'],
    'interactBlock': ['ServerboundUseItemOnPacket'],
    'respawn': ['ServerboundClientCommandPacket'],
    'reconnect': [], 'disconnect': [],
}


def _uuid(value):
    require(isinstance(value, str)
            and re.fullmatch(r'[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}', value),
            'Invalid canonical UUID')
    return value


def _times(record, start, end):
    begin = _integer(record.get('startedAtEpochMs'), start, end)
    finish = _integer(record.get('completedAtEpochMs'), begin, end)
    return begin, finish


def _messages(messages):
    for message in _array(messages, 0, 128):
        require(isinstance(message, str) and message, 'Missing/malformed received player message')
        try:
            length = len(message.encode('utf-16-le')) // 2
        except UnicodeError as error:
            raise ActionValidationError('Invalid received message Unicode') from error
        require(length <= 2048 and not message.startswith(('OD_BIND:', 'OD_ACTION:', 'OD_PLAYER:')),
                'Unbounded message or leaked action marker')


def _inventory_snapshots(snapshots, start, end):
    values = _array(snapshots, 0, 128)
    previous_time = start
    for sequence, snapshot in enumerate(values, 1):
        _object(snapshot, 'sequence', 'containerId', 'stateId', 'slotCount', 'receivedAtEpochMs', 'sha256', 'slotSha256')
        _integer(snapshot['sequence'], sequence, sequence)
        _integer(snapshot['containerId'], 0, 0); _integer(snapshot['stateId'], 0, 32767)
        _integer(snapshot['slotCount'], 46, 46)
        previous_time = _integer(snapshot['receivedAtEpochMs'], previous_time, end)
        require(isinstance(snapshot['sha256'], str) and re.fullmatch(r'[a-f0-9]{64}', snapshot['sha256']),
                'Invalid full inventory packet SHA256')
        for digest in _array(snapshot['slotSha256'], 46, 46):
            require(isinstance(digest, str) and re.fullmatch(r'[a-f0-9]{64}', digest),
                    'Invalid full inventory slot SHA256')
    return values


def _inventory_confirmations(confirmations, snapshots, start, end):
    values = _array(confirmations, 0, 512)
    previous_time = start
    previous_snapshot = 1
    effective_states = {}
    for sequence, confirmation in enumerate(values, 1):
        _object(confirmation, 'sequence', 'inventorySnapshotSequence', 'containerId',
                'stateId', 'slot', 'receivedAtEpochMs', 'sha256', 'itemSha256')
        _integer(confirmation['sequence'], sequence, sequence)
        snapshot_sequence = _integer(confirmation['inventorySnapshotSequence'], previous_snapshot, len(snapshots))
        snapshot = snapshots[snapshot_sequence - 1]
        _integer(confirmation['containerId'], 0, 0)
        state = _integer(confirmation['stateId'], 0, 32767)
        slot = _integer(confirmation['slot'], 0, 45)
        previous_time = _integer(confirmation['receivedAtEpochMs'],
                                 max(previous_time, snapshot['receivedAtEpochMs']), end)
        require(not any(newer['sequence'] > snapshot_sequence and newer['receivedAtEpochMs'] < previous_time
                        for newer in snapshots), 'Confirmation belongs to an obsolete full inventory snapshot')
        for key in ('sha256', 'itemSha256'):
            require(isinstance(confirmation[key], str) and re.fullmatch(r'[a-f0-9]{64}', confirmation[key]),
                    'Invalid inventory confirmation SHA256')
        require(confirmation['itemSha256'] == snapshot['slotSha256'][slot],
                'Inventory confirmation changed the full snapshot item bytes')
        previous_state = effective_states.get(snapshot_sequence, snapshot['stateId'])
        require(state in (previous_state, (previous_state + 1) % 32768),
                'Inventory confirmation skipped or reversed the effective state ID')
        effective_states[snapshot_sequence] = state
        previous_snapshot = snapshot_sequence
    return values


def _report_header(report, plan, plan_sha256, run_id, pins, start, end, passed, error):
    validate_plan(plan)
    require(isinstance(plan_sha256, str) and re.fullmatch(r'[a-f0-9]{64}', plan_sha256), 'Invalid plan digest')
    _integer(start, 1, 2**63 - 1); _integer(end, start, 2**63 - 1)
    _object(report, 'schemaVersion', 'runId', 'planId', 'planSha256', 'authentication',
            'artifact', 'minecraftVersion', 'protocolVersion', 'startedAtEpochMs',
            'completedAtEpochMs', 'actors', 'passed', 'error')
    _integer(report['schemaVersion'], 2, 2)
    require(report['runId'] == run_id and report['planId'] == plan['planId']
            and report['planSha256'] == plan_sha256, 'Wrong/stale player run or plan')
    require(report['authentication'] == 'offline-disposable-loopback', 'Wrong action-player authentication mode')
    require(report['artifact'] == pins['testPlayerProtocolLib']
            and report['minecraftVersion'] == pins['minecraftVersion'], 'Wrong player artifact or Minecraft version')
    _integer(report['protocolVersion'], int(pins['testPlayerProtocolVersion']), int(pins['testPlayerProtocolVersion']))
    require(report['passed'] is passed and report['error'] == error, 'Unexpected player action outcome')
    return _times(report, start, end)


def _ui_session_fields(session, plan):
    require(isinstance(session, dict), 'Missing client session')
    fields = {'bossBars', 'styledMessages'}
    enabled = plan['planId'].startswith(('dragon-presentation-', 'dragon-restart-'))
    require((fields <= set(session)) if enabled else not (fields & set(session)),
            'Missing UI receipt or UI fields outside bounded presentation plan')
    return {k: v for k, v in session.items() if k not in fields}


def validate_report(report, plan, plan_sha256, run_id, pins, start, end):
    """Verify completed client submission evidence; server behavior is checked separately."""
    begin, finish = _report_header(report, plan, plan_sha256, run_id, pins, start, end, True, '')
    expected_identities = identities(run_id, plan)
    actors = _array(report['actors'], len(plan['actors']), len(plan['actors']))
    for expected_actor, actor, identity in zip(plan['actors'], actors, expected_identities):
        _object(actor, 'id', 'username', 'uuid', 'sessions')
        require(actor['id'] == expected_actor['id'] and actor['username'] == identity['name']
                and actor['uuid'] == identity['uuid'], 'Wrong, reordered or stale actor identity')
        sessions = _array(actor['sessions'], len(expected_actor['sessions']), len(expected_actor['sessions']))
        previous_finish = begin
        reconnect_delay = 0
        for expected_session, session in zip(expected_actor['sessions'], sessions):
            _object(_ui_session_fields(session, plan), 'id', 'startedAtEpochMs', 'completedAtEpochMs', 'loginReceived',
                    'playerLoadedSent', 'teleportsAcknowledged', 'steps', 'messages', 'bindings', 'inventorySnapshots',
                    'inventoryConfirmations',
                    'disconnected', 'passed', 'error')
            require(session['id'] == expected_session['id'], 'Wrong or reordered actor session')
            session_start, session_end = _times(session, previous_finish + reconnect_delay, finish)
            require(session['loginReceived'] is True and session['playerLoadedSent'] is True
                    and session['disconnected'] is True and session['passed'] is True
                    and session['error'] == '', 'Incomplete/failed actor session or disconnect')
            _integer(session['teleportsAcknowledged'], 1, 4096)
            _messages(session['messages'])
            inventory_snapshots = _inventory_snapshots(session['inventorySnapshots'], session_start, session_end)
            inventory_confirmations = _inventory_confirmations(session['inventoryConfirmations'], inventory_snapshots,
                                                                 session_start, session_end)
            bindings = _array(session['bindings'], 0, len(plan['targets']))
            last_binding = session_start
            target_refs = set()
            for binding in bindings:
                _object(binding, 'targetRef', 'uuid', 'receivedAtEpochMs')
                require(binding['targetRef'] in plan['targets'], 'Undeclared target binding')
                require(binding['targetRef'] not in target_refs, 'Duplicate target binding within a session')
                target_refs.add(binding['targetRef'])
                _uuid(binding['uuid'])
                last_binding = _integer(binding['receivedAtEpochMs'], last_binding, session_end)
            steps = _array(session['steps'], len(expected_session['steps']), len(expected_session['steps']))
            last_step = session_start
            previous_click_sequence = 0
            previous_click_time = session_start
            for expected_step, step in zip(expected_session['steps'], steps):
                keys = ['id', 'action', 'args', 'submittedAtEpochMs', 'packetTypes']
                if expected_step['action'] == 'attackEntity':
                    keys.extend(('targetUuid', 'networkEntityId'))
                elif expected_step['action'] == 'inventoryClick':
                    keys.extend(('containerId', 'stateId', 'inventorySnapshotSequence',
                                 'inventorySnapshotReceivedAtEpochMs', 'inventorySnapshotSha256',
                                 'inventoryConfirmationSequence'))
                _object(step, *keys)
                require(step['id'] == expected_step['id'] and step['action'] == expected_step['action'],
                        'Wrong, duplicate or reordered action step')
                validate_arguments(step['action'], step['args'])
                require(step['args'] == expected_step['args'], 'Action arguments differ from the pinned plan')
                require(step['packetTypes'] == PACKETS[step['action']], 'Wrong or incomplete action packet submission')
                last_step = _integer(step['submittedAtEpochMs'], last_step, session_end)
                if step['action'] == 'attackEntity':
                    _uuid(step['targetUuid']); _integer(step['networkEntityId'], 0, 2**31 - 1)
                    matches = [binding for binding in bindings
                               if binding['targetRef'] == step['args']['targetRef']
                               and binding['receivedAtEpochMs'] <= last_step]
                    require(matches and matches[-1]['uuid'] == step['targetUuid'],
                            'Attack does not match a previously received target binding')
                elif step['action'] == 'inventoryClick':
                    _integer(step['containerId'], 0, 0); _integer(step['stateId'], 0, 32767)
                    sequence = _integer(step['inventorySnapshotSequence'], previous_click_sequence + 1,
                                        len(inventory_snapshots))
                    snapshot = inventory_snapshots[sequence - 1]
                    received = _integer(step['inventorySnapshotReceivedAtEpochMs'], previous_click_time, last_step)
                    require(received == snapshot['receivedAtEpochMs']
                            and step['inventorySnapshotSha256'] == snapshot['sha256'],
                            'Click does not match its prior full server inventory snapshot')
                    confirmations = [row for row in inventory_confirmations
                                     if row['inventorySnapshotSequence'] == sequence]
                    # No confirmation can be accepted after clicking this snapshot:
                    # the client requires a new full snapshot before becoming ready.
                    confirmation = confirmations[-1] if confirmations else None
                    expected_confirmation = confirmation['sequence'] if confirmation else 0
                    _integer(step['inventoryConfirmationSequence'], expected_confirmation, expected_confirmation)
                    require(step['stateId'] == (confirmation['stateId'] if confirmation else snapshot['stateId'])
                            and (confirmation is None or confirmation['receivedAtEpochMs'] <= last_step),
                            'Click does not use its latest prior unchanged-item confirmation')
                    require(not any(newer['sequence'] > sequence and newer['receivedAtEpochMs'] < last_step
                                    for newer in inventory_snapshots),
                            'Click used a snapshot older than a known newer server snapshot')
                    # Another full snapshot may arrive with the same/wrapped state ID.
                    # Freshness is tied to the observed packet sequence, never prediction.
                    previous_click_sequence = sequence
                    previous_click_time = last_step
                elif step['action'] in ('disconnect', 'reconnect') and previous_click_sequence:
                    require(any(snapshot['sequence'] > previous_click_sequence
                                and previous_click_time <= snapshot['receivedAtEpochMs'] <= last_step
                                for snapshot in inventory_snapshots),
                            'Actor terminated before a full inventory resynchronization after its last click')
            previous_finish = session_end
            terminal = expected_session['steps'][-1]
            reconnect_delay = terminal['args']['delayMillis'] if terminal['action'] == 'reconnect' else 0
    return report


def _server_journal(scenario_report, player_report, plan, aborted=False):
    """Bind exact server requests/joins/quits and targets to the client plan/receipt.

    Requests establish fixture coordination, not that a gameplay packet took
    effect. Scenario-specific assertions must independently test that behavior.
    """
    validate_plan(plan)
    if aborted:
        _abort_plan(plan)
    require(isinstance(scenario_report, dict) and isinstance(player_report, dict), 'Missing fixture evidence')
    if scenario_report.get("scenarioId") == "dragon-presentation" or scenario_report.get("scenarioId", "").startswith("dragon-restart-"):
        import bossbar_observation
        bossbar_observation.validate(scenario_report, player_report)
    require(isinstance(scenario_report.get('runId'), str)
            and scenario_report['runId'] == player_report.get('runId'), 'Server/client fixture run IDs differ')
    observations = scenario_report.get('observations')
    require(isinstance(observations, dict), 'Missing server fixture observations')
    journal = _array(observations.get('playerFixture'), 1, 512)
    require(isinstance(player_report.get('actors'), list)
            and len(player_report['actors']) == len(plan['actors']), 'Missing client actor evidence')
    states = {}
    for actor, client in zip(plan['actors'], player_report['actors']):
        require(client.get('id') == actor['id'] and isinstance(client.get('sessions'), list)
                and len(client['sessions']) == len(actor['sessions']), 'Server/client actor plan mismatch')
        states[actor['id']] = {'plan': actor, 'client': client, 'ordinal': 0,
                               'online': False, 'requests': [], 'bindings': []}
    last_tick = 0
    for event in journal:
        _object(event, 'kind', 'actor', 'tick', 'detail')
        last_tick = _integer(event['tick'], last_tick, 2**31 - 1)
        require(isinstance(event['actor'], str) and event['actor'] in states, 'Unknown server journal actor')
        state = states[event['actor']]
        detail = event['detail']; kind = event['kind']
        require(isinstance(kind, str) and isinstance(detail, dict), 'Malformed server journal event')
        if kind == 'join':
            _object(detail, 'uuid', 'sessionOrdinal')
            require(not state['online'] and state['ordinal'] < len(state['plan']['sessions']),
                    'Duplicate or extra server join')
            state['ordinal'] += 1
            _integer(detail['sessionOrdinal'], state['ordinal'], state['ordinal'])
            require(detail['uuid'] == state['client']['uuid'], 'Wrong server join UUID')
            state['online'] = True; state['requests'] = []; state['bindings'] = []
        elif kind == 'quit':
            require(not aborted, 'Aborted context journal cannot contain completed actor quits')
            _object(detail, 'uuid')
            require(state['online'] and detail['uuid'] == state['client']['uuid'], 'Unexpected server quit')
            expected_session = state['plan']['sessions'][state['ordinal'] - 1]
            expected = [(expected_session['id'], step['id']) for step in expected_session['steps']]
            require(state['requests'] == expected, 'Missing, duplicate or reordered server action requests')
            client_session = state['client']['sessions'][state['ordinal'] - 1]
            require(client_session.get('id') == expected_session['id'], 'Wrong client session for server lifecycle')
            bindings = client_session.get('bindings')
            require(isinstance(bindings, list), 'Missing client target bindings')
            require(state['bindings'] == [(row.get('targetRef'), row.get('uuid')) for row in bindings],
                    'Server/client target bindings differ')
            state['online'] = False
        elif kind == 'request':
            _object(detail, 'session', 'step')
            require(state['online'], 'Server requested an action outside its session')
            expected_session = state['plan']['sessions'][state['ordinal'] - 1]
            index = len(state['requests'])
            limit = 1 if aborted else len(expected_session['steps'])
            require(index < limit
                    and detail == {'session': expected_session['id'], 'step': expected_session['steps'][index]['id']},
                    'Unexpected server action request')
            expected_step = expected_session['steps'][index]
            if expected_step['action'] == 'attackEntity':
                client_steps = state['client']['sessions'][state['ordinal'] - 1].get('steps')
                require(isinstance(client_steps, list) and index < len(client_steps), 'Missing client attack evidence')
                matches = [binding for binding in state['bindings'] if binding[0] == expected_step['args']['targetRef']]
                require(matches and matches[-1][1] == client_steps[index].get('targetUuid'),
                        'Server target binding was missing or different before attack request')
            state['requests'].append((detail['session'], detail['step']))
        elif kind == 'bind':
            require(not aborted, 'Cleanup-abort plan must not bind targets')
            _object(detail, 'targetRef', 'uuid')
            require(state['online'] and detail['targetRef'] in plan['targets'], 'Invalid server target binding')
            _uuid(detail['uuid'])
            state['bindings'].append((detail['targetRef'], detail['uuid']))
        elif kind.startswith('server-setup-'):
            require(state['online'] and re.fullmatch(r'server-setup-[a-z][a-z0-9-]{0,63}', kind),
                    'Invalid/out-of-session server setup event')
            _bounded_tree(detail)
            # Preserve explicit setup classification; it never substitutes for a request.
        else:
            raise ActionValidationError('Unknown server fixture journal event')
    if aborted:
        for state in states.values():
            session = state['plan']['sessions'][0]
            require(state['online'] and state['ordinal'] == 1
                    and state['requests'] == [(session['id'], session['steps'][0]['id'])],
                    'Abort journal requires exactly each joined actor and its status request')
            client_session = state['client']['sessions'][0]
            require(client_session.get('id') == session['id'] and client_session.get('bindings') == [],
                    'Abort server/client session evidence differs')
            steps = client_session.get('steps')
            require(isinstance(steps, list) and len(steps) == 1
                    and {key: steps[0].get(key) for key in ('id', 'action', 'args')} == session['steps'][0],
                    'Abort server/client command prefixes differ')
    else:
        require(all(not state['online'] and state['ordinal'] == len(state['plan']['sessions'])
                    for state in states.values()), 'Missing final actor sessions or server quit events')
    return journal


def validate_server_journal(scenario_report, player_report, plan):
    return _server_journal(scenario_report, player_report, plan)


ABORT_ERROR = 'Runner cleanup before action completion'


def _abort_plan(plan):
    validate_plan(plan)
    require(plan['targets'] == [] and len(plan['actors']) == 2,
            'Cleanup-abort requires exactly two actors and no targets')
    for actor in plan['actors']:
        require(len(actor['sessions']) == 1, 'Cleanup-abort requires one session per actor')
        steps = actor['sessions'][0]['steps']
        require(len(steps) == 2 and steps[0]['action'] == 'command'
                and steps[0]['args'] == {'command': 'onlydragons status'}
                and steps[1]['action'] == 'disconnect' and steps[1]['args'] == {},
                'Cleanup-abort plan must contain only status then disconnect')


def validate_abort_report(report, plan, plan_sha256, run_id, pins, start, end):
    """Accept only the named orderly-abort control after both real status commands."""
    _abort_plan(plan)
    begin, finish = _report_header(report, plan, plan_sha256, run_id, pins, start, end, False, ABORT_ERROR)
    identities_expected = identities(run_id, plan)
    actors = _array(report['actors'], 2, 2)
    requirements = []
    for expected_actor, actor, identity in zip(plan['actors'], actors, identities_expected):
        _object(actor, 'id', 'username', 'uuid', 'sessions')
        require(actor['id'] == expected_actor['id'] and actor['username'] == identity['name']
                and actor['uuid'] == identity['uuid'], 'Wrong cleanup-abort actor identity')
        session = _array(actor['sessions'], 1, 1)[0]
        expected_session = expected_actor['sessions'][0]
        _object(_ui_session_fields(session, plan), 'id', 'startedAtEpochMs', 'completedAtEpochMs', 'loginReceived',
                'playerLoadedSent', 'teleportsAcknowledged', 'steps', 'messages', 'bindings', 'inventorySnapshots',
                'inventoryConfirmations',
                'disconnected', 'passed', 'error')
        require(session['id'] == expected_session['id'] and session['loginReceived'] is True
                and session['playerLoadedSent'] is True and session['disconnected'] is True
                and session['passed'] is False and session['error'] == ABORT_ERROR,
                'Cleanup-abort actor did not join and disconnect through named orderly cleanup')
        session_start, session_end = _times(session, begin, finish)
        _integer(session['teleportsAcknowledged'], 1, 4096)
        snapshots = _inventory_snapshots(session['inventorySnapshots'], session_start, session_end)
        _inventory_confirmations(session['inventoryConfirmations'], snapshots, session_start, session_end)
        require(session['bindings'] == [], 'Cleanup-abort must have no target bindings')
        submitted = _array(session['steps'], 1, 1)[0]
        _object(submitted, 'id', 'action', 'args', 'submittedAtEpochMs', 'packetTypes')
        expected_step = expected_session['steps'][0]
        require({key: submitted[key] for key in ('id', 'action', 'args')} == expected_step,
                'Cleanup-abort requires exactly the planned status command prefix')
        validate_arguments(submitted['action'], submitted['args'])
        require(submitted['packetTypes'] == PACKETS['command'], 'Missing cleanup-abort command submission')
        _integer(submitted['submittedAtEpochMs'], session_start, session_end)
        requirements.append({'actor': actor['id'], 'session': session['id'], 'id': actor['id'] + '-status',
                             'containsAll': ['OnlyDragons ready | version ']})
    validate_messages(report, {'requiredActorMessages': requirements})
    return report


def validate_abort_journal(scenario_report, player_report, plan):
    return _server_journal(scenario_report, player_report, plan, aborted=True)


def validate_messages(player_report, descriptor):
    """Require catalog text in the exact actor/session's received ordinary chat."""
    require(isinstance(player_report, dict) and isinstance(descriptor, dict), 'Malformed actor message evidence/catalog')
    messages_by_session = {}
    actor_ids = set()
    for actor in _array(player_report.get('actors'), 1, 4):
        require(isinstance(actor, dict), 'Malformed actor message evidence')
        actor_id = _id(actor.get('id'))
        require(actor_id not in actor_ids, 'Duplicate message actor identity')
        actor_ids.add(actor_id)
        for session in _array(actor.get('sessions'), 1, 4):
            require(isinstance(session, dict), 'Malformed session message evidence')
            session_id = _id(session.get('id'))
            key = (actor_id, session_id)
            require(key not in messages_by_session, 'Duplicate message session identity')
            _messages(session.get('messages'))
            messages_by_session[key] = session['messages']
    requirements = _array(descriptor.get('requiredActorMessages', []), 0, 32)
    seen = set()
    for requirement in requirements:
        require(isinstance(requirement, dict) and set(requirement) in (
            {'actor', 'session', 'id', 'exact'}, {'actor', 'session', 'id', 'containsAll'}),
            'Malformed required actor message matcher')
        key = (_id(requirement['actor']), _id(requirement['session']))
        require(key in messages_by_session, 'Required message actor/session is missing')
        identity = requirement['id']
        require(isinstance(identity, str) and re.fullmatch(r'[a-z][a-z0-9-]{0,63}', identity)
                and identity not in seen, 'Missing/duplicate actor message matcher identity')
        seen.add(identity)
        if 'exact' in requirement:
            value = requirement['exact']
            require(isinstance(value, str) and 0 < len(value) <= 2048, 'Malformed exact actor message')
            matched = value in messages_by_session[key]
        else:
            fragments = requirement['containsAll']
            require(isinstance(fragments, list) and 0 < len(fragments) <= 8
                    and all(isinstance(value, str) and 0 < len(value) <= 256 for value in fragments),
                    'Malformed actor message fragments')
            matched = any(all(fragment in message for fragment in fragments)
                          for message in messages_by_session[key])
        require(matched, 'Missing/incorrect required actor message: ' + identity)
    return requirements
