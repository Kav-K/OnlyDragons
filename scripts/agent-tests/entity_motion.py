"""Validate bounded client-received entity-motion summaries and catalog-required motion.

These checks constrain observed position segments; they do not render a Minecraft
client or independently reconstruct the underlying packet stream.
"""
import math
import uuid


def require(condition, message):
    """Raise ValueError for a malformed or missing motion-evidence condition."""
    if not condition:
        raise ValueError(message)


def number(value, low, high):
    """Require a finite real number within inclusive bounds, excluding booleans, and return it."""
    require(type(value) in (int, float) and math.isfinite(value) and low <= value <= high,
            'Invalid entity motion number')
    return value


def validate(rows, start, end):
    """Validate at most 128 ordered entity segments within an epoch-millisecond session window.

    Require canonical UUIDs, finite bounded positions/distances, nonnegative packet
    counts and consistent chord/path/maximum-step relationships. Zero packets cannot
    claim path motion. Return the validated rows without changing them.
    """
    require(isinstance(rows, list) and len(rows) <= 128, 'Invalid entity motion segments')
    for index, row in enumerate(rows):
        require(isinstance(row, dict) and set(row) == {'segment', 'uuid', 'startedAtEpochMs', 'endedAtEpochMs',
                'packets', 'first', 'last', 'path', 'maxStep'}, 'Invalid entity motion fields')
        require(type(row['segment']) is int and row['segment'] == index, 'Reordered motion segment')
        require(isinstance(row['uuid'], str) and str(uuid.UUID(row['uuid'])) == row['uuid'], 'Invalid motion UUID')
        require(type(row['packets']) is int and 0 <= row['packets'] <= 10000, 'Invalid motion packet count')
        first = row['startedAtEpochMs']; last = row['endedAtEpochMs']
        require(type(first) is int and type(last) is int and start <= first <= last <= end, 'Stale motion segment')
        for position in (row['first'], row['last']):
            require(isinstance(position, list) and len(position) == 3, 'Invalid motion position')
            for value in position:
                number(value, -3.1e7, 3.1e7)
        path = number(row['path'], 0, 1e9); step = number(row['maxStep'], 0, 1e8)
        require(step <= path + 1e-8 and math.dist(row['first'], row['last']) <= path + 1e-8,
                'Inconsistent received motion distance')
        require(row['packets'] != 0 or (path == step == 0 and row['first'] == row['last']), 'Motion without packets')
    return rows


def validate_required(report, descriptor):
    """Match catalog motion expectations to exact actor/session/target bindings.

    Require each requested packet/path minimum and step maximum in the bound entity's
    validated segments. This demonstrates received movement, not visual smoothness.
    """
    requirements = descriptor.get('requiredEntityMotion', [])
    require(isinstance(requirements, list) and len(requirements) <= 16, 'Invalid required motion matchers')
    seen = set()
    for matcher in requirements:
        require(isinstance(matcher, dict) and set(matcher) == {'id','actor','session','targetRef','minPackets','minPath','maxStep'},
                'Invalid required entity motion matcher')
        identity=matcher['id']; require(isinstance(identity,str) and identity and identity not in seen, 'Duplicate motion matcher'); seen.add(identity)
        require(type(matcher['minPackets']) is int and 1 <= matcher['minPackets'] <= 10000, 'Invalid minimum motion packets')
        number(matcher['minPath'], .001, 1e9); number(matcher['maxStep'], .001, 1e8)
        sessions=[s for a in report.get('actors',[]) if a['id']==matcher['actor'] for s in a['sessions'] if s['id']==matcher['session']]
        require(len(sessions)==1, 'Missing motion actor/session'); session=sessions[0]
        bindings=[b['uuid'] for b in session['bindings'] if b['targetRef']==matcher['targetRef']]
        require(len(bindings)==1, 'Missing motion target binding')
        rows=validate(session.get('entityMotion',[]),session['startedAtEpochMs'],session['completedAtEpochMs'])
        require(any(r['uuid']==bindings[0] and r['packets']>=matcher['minPackets'] and r['path']>=matcher['minPath']
                    and r['maxStep']<=matcher['maxStep'] for r in rows),'Missing/incorrect received entity motion: '+identity)
