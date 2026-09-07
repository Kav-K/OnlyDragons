"""Bounded received ANVIL identity, full-state and action evidence; no window-0 relaxation."""
import re

ACTION_FIELDS = ('anvilOpenSequence', 'containerId', 'stateId', 'anvilSnapshotSequence', 'anvilSnapshotSha256')


def validate(session, expected, start, end):
    # Import lazily to retain one shared validation exception and primitive boundary.
    from player_actions import require, _object, _array, _integer, _number
    actions = [s for s in expected['steps'] if s['action'].startswith('anvil')]
    data = session.get('anvil')
    if data is None:
        require(not actions, 'Missing received anvil evidence')
        return
    _object(data, 'opens', 'snapshots', 'costs', 'xp', 'closes')
    opens = _array(data['opens'], 1, 256)
    snapshots = _array(data['snapshots'], 0, 256)
    costs = _array(data['costs'], 0, 256)
    xp = _array(data['xp'], 0, 256)
    closes = _array(data['closes'], 0, 256)

    def header(row, keys, earliest=start):
        _object(row, *keys, 'receivedAtEpochMs', 'sha256')
        _integer(row['receivedAtEpochMs'], earliest, end)
        require(isinstance(row['sha256'], str) and re.fullmatch('[a-f0-9]{64}', row['sha256']), 'Invalid anvil packet hash')

    previous = start
    for index, row in enumerate(opens, 1):
        header(row, ('openSequence', 'containerId', 'menuType'), previous)
        _integer(row['openSequence'], 1, len(opens))
        require(row['openSequence'] == index and row['menuType'] == 'ANVIL', 'Wrong anvil menu/open identity')
        _integer(row['containerId'], 1, 2**31-1)
        previous = row['receivedAtEpochMs']

    def bound(row):
        sequence = _integer(row['openSequence'], 1, len(opens))
        opened = opens[sequence-1]
        _integer(row['containerId'], 1, 2**31-1)
        require(row['containerId'] == opened['containerId'] and row['receivedAtEpochMs'] >= opened['receivedAtEpochMs'], 'Wrong anvil window binding')
        require(sequence == len(opens) or row['receivedAtEpochMs'] <= opens[sequence]['receivedAtEpochMs'], 'Anvil observation after newer open')

    for row in snapshots:
        header(row, ('openSequence', 'containerId', 'stateId', 'slotCount', 'slotSha256', 'cursorSha256', 'amounts', 'cursorAmount'))
        bound(row); _integer(row['stateId'], 0, 32767)
        _integer(row['slotCount'], 39, 39)
        require(row['slotCount'] == 39, 'Anvil snapshot requires 39 slots')
        for digest in _array(row['slotSha256'], 39, 39) + [row['cursorSha256']]:
            require(isinstance(digest, str) and re.fullmatch('[a-f0-9]{64}', digest), 'Invalid anvil item digest')
        for count in _array(row['amounts'], 39, 39) + [row['cursorAmount']]:
            _integer(count, 0, 99)
    for row in costs:
        header(row, ('openSequence', 'containerId', 'cost')); bound(row); _integer(row['cost'], -1, 32767)
    for row in xp:
        header(row, ('level', 'fraction', 'total')); _integer(row['level'], 0, 2**31-1); _number(row['fraction'], 0, 1); _integer(row['total'], 0, 2**31-1)
    for row in closes:
        header(row, ('openSequence', 'containerId', 'source')); bound(row)
        require(row['source'] in ('client', 'server'), 'Invalid anvil close source')
    previous_sequence = 0
    for step in session['steps']:
        if not step['action'].startswith('anvil'):
            continue
        seq = _integer(step['anvilSnapshotSequence'], previous_sequence+1, len(snapshots))
        snapshot = snapshots[seq-1]
        _integer(step['anvilOpenSequence'], 1, len(opens))
        _integer(step['containerId'], 1, 2**31-1)
        _integer(step['stateId'], 0, 32767)
        time = step['submittedAtEpochMs']
        require(snapshot['receivedAtEpochMs'] <= time, 'Anvil action before snapshot')
        require(step['anvilOpenSequence'] == snapshot['openSequence'] and step['containerId'] == snapshot['containerId']
                and step['stateId'] == snapshot['stateId'] and step['anvilSnapshotSha256'] == snapshot['sha256'], 'Anvil action uses stale or wrong input identity')
        require(not any(s['receivedAtEpochMs'] < time for s in snapshots[seq:]), 'Anvil action ignored newer full state')
        require(not any(o['openSequence'] > snapshot['openSequence'] and o['receivedAtEpochMs'] < time for o in opens), 'Anvil action from an old session')
        require(not any(c['openSequence'] == snapshot['openSequence'] and c['receivedAtEpochMs'] < time and not (step['action'] == 'anvilClose' and c['source'] == 'client') for c in closes), 'Anvil action after close')
        if step['action'] != 'anvilClose':
            terminal = min((s['submittedAtEpochMs'] for s in session['steps'] if s['action'] in ('disconnect', 'reconnect') and s['submittedAtEpochMs'] >= time), default=end)
            require(any(s['openSequence'] == snapshot['openSequence'] and time <= s['receivedAtEpochMs'] <= terminal for s in snapshots[seq:]), 'Unsettled anvil action')
        else:
            require(any(c['source'] == 'client' and c['openSequence'] == snapshot['openSequence'] and c['receivedAtEpochMs'] <= time for c in closes), 'Missing submitted anvil close')
        previous_sequence = seq


def validate_feature(scenario, report):
    """Bind feature transactions to received previews, destinations, cost and XP packets."""
    from player_actions import require
    name = scenario.get('scenarioId')
    if name not in ('enchant-anvil', 'anvil-boundaries', 'anvil-lifecycle'):
        return
    session = report['actors'][0]['sessions'][0]
    data = session.get('anvil')
    require(data is not None, 'Feature lacks received ANVIL observations')
    rows = scenario['observations'].get('anvilTransactions' if name == 'enchant-anvil' else 'anvilBoundaryTransactions')
    require(isinstance(rows, list) and rows, 'Missing feature anvil transactions')
    if name == 'enchant-anvil':
        expected = {'collect-snipe':9,'collect-dragon-tracer':10,'collect-vicious':10,'collect-overload':10,'collect-gravity':12,
                    'collect-infinite-quiver':20,'collect-flame':4,'collect-duplex':20,'collect-fatal-tempo':20,'collect-power':14}
        require({r['step']:r['cost'] for r in rows} == expected and len(rows) == 10, 'Missing/incorrect ten-enchant received cost coverage')
    steps = session['steps']
    for row in rows:
        matching = [(i,s) for i,s in enumerate(steps) if s['id'] == row['step']]
        require(len(matching) == 1, 'Missing exact native extraction action')
        index, step = matching[0]
        require(step['action'] == 'anvilClick' and step['args']['slot'] == 2, 'Transaction was not actual result extraction')
        before = data['snapshots'][step['anvilSnapshotSequence']-1]
        start = step['submittedAtEpochMs']
        # End before the next fixture trial's action; collecting/store actions have their own received full snapshot.
        end = steps[index+1]['submittedAtEpochMs'] if index+1 < len(steps) else session['completedAtEpochMs']
        after = [s for s in data['snapshots'][step['anvilSnapshotSequence']:]
                 if s['openSequence'] == step['anvilOpenSequence'] and start <= s['receivedAtEpochMs'] <= end]
        require(after, 'No fresh received post-extraction inventory state')
        prior_costs = [c for c in data['costs'] if c['openSequence'] == step['anvilOpenSequence'] and c['receivedAtEpochMs'] <= start]
        success = row.get('success', True)
        if success:
            require(prior_costs and prior_costs[-1]['cost'] == row['cost'], 'Received XP cost differs from accepted native recipe')
            require(before['amounts'][0] == before['amounts'][2] == 1 and before['cursorAmount'] == 0, 'Missing received intact inputs/output preview')
            digest = before['slotSha256'][2]
            right_after = max(0, before['amounts'][1]-1)
            shift = step['args']['button'].startswith('shift-')
            require(any(s['amounts'][0] == 0 and s['amounts'][1] == right_after and
                        (digest in s['slotSha256'][3:] if shift else s['cursorSha256'] == digest and s['cursorAmount'] == 1)
                        for s in after), 'Received output/input conservation does not match preview')
            require(row['afterLevel'] == row['beforeLevel'] - (0 if row['step'] == 'collect-creative' else row['cost']), 'Incorrect native XP level debit')
            xp = [x for x in data['xp'] if start <= x['receivedAtEpochMs'] <= end]
            if row['step'] == 'collect-creative':
                xp += [x for x in data['xp'] if x['receivedAtEpochMs'] <= start][-1:]
            require(any(x['level'] == row['afterLevel'] and x['fraction'] == row['fraction'] for x in xp), 'Missing received native XP/fraction result')
        else:
            require(row['afterLevel'] == row['beforeLevel'], 'Rejected extraction charged XP')
            require(any(s['amounts'][:2] == before['amounts'][:2] and (row['step'] in ('collect-stale', 'collect-stale-ordinary') or s['slotSha256'][0] == before['slotSha256'][0])
                        and (row['step'] == 'collect-stale-ordinary' or s['slotSha256'][1] == before['slotSha256'][1])
                        and s['cursorAmount'] == before['cursorAmount'] for s in after), 'Rejected extraction lost input/cursor items')
            require(not any(x['level'] < row['beforeLevel'] for x in data['xp'] if start <= x['receivedAtEpochMs'] < end), 'Rejected extraction received an XP debit')
