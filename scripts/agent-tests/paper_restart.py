"""Fixed two-boot extension. No retries, world resets, or second lease acquisition."""
from pathlib import Path
import copy
import re
import uuid
import zipfile

MODE = 'same-profile-restart-v1'
CONFIG = 'plugins/OnlyDragons/config.yml'


def validate_descriptor(project, descriptor):
    import paper_test as r
    r.require(descriptor.get('catalogMode') == MODE and descriptor.get('testPlayerMode') == 'protocol-actions-v1',
              'Restart requires the explicitly declared catalog/player mode')
    phases = descriptor.get('phases')
    r.require(isinstance(phases, list) and len(phases) == 2, 'Restart requires exactly two ordered phases')
    roster = None
    for index, phase in enumerate(phases, 1):
        r.require(isinstance(phase, dict) and type(phase.get('index')) is int and phase['index'] == index
                  and phase.get('expectation') in (('positive',) if index == 1 else ('positive', 'cleanup-abort')),
                  'Invalid restart phase order/expectation')
        r.require(set(phase) == {'index', 'expectation', 'mechanicRevision', 'requiredAssertions',
                               'playerActionPlan', 'requiredActorMessages'}, 'Unknown/missing restart phase fields')
        assertions = phase['requiredAssertions']
        r.require(isinstance(assertions, list) and 1 <= len(assertions) <= 256
                  and all(isinstance(a, str) and re.fullmatch('[a-z][a-z0-9_]{0,95}', a) for a in assertions)
                  and len(set(assertions)) == len(assertions),
                  'Missing/duplicate phase assertions')
        r.require(set(descriptor['requiredAssertions']) <= set(assertions), 'Parent assertions missing from phase')
        r.require(isinstance(phase['mechanicRevision'], str) and re.fullmatch('[A-Za-z0-9._-]{1,64}', phase['mechanicRevision']),
                  'Invalid phase mechanic revision')
        plan = r.player_actions.load_plan(project, phase)[2]
        validate_message_bindings(r, phase, plan)
        actors = [actor['id'] for actor in plan['actors']]
        r.require(roster is None or actors == roster, 'Restart actor roster changed')
        roster = actors
    return phases


def artifact_hashes(r, directory):
    paths = ['server.jar', 'plugins/OnlyDragons.jar', 'plugins/OnlyDragonsGameTests.jar']
    paths += [path.relative_to(directory).as_posix() for path in sorted((directory / 'player-client').glob('*.jar'))]
    paths += [path.relative_to(directory).as_posix() for path in sorted((directory / 'cache').glob('mojang_*.jar'))]
    return {name: r.sha256(directory / name) for name in paths}


def snapshot(r, source, destination):
    r.require(source.is_file() and not source.is_symlink() and source.stat().st_size <= 65536,
              'Missing, symlink or oversized production config')
    destination.write_bytes(source.read_bytes())
    return r.sha256(destination)


def execute_phases(r, args, project, java_home, pins, descriptor, parent, directory, root, outcome):
    phases = validate_descriptor(project, descriptor)
    outcome.update(catalogMode=MODE, phases=[])
    # The production default is staged once from the exact built JAR. No inter-boot config writes.
    config = directory / CONFIG
    config.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(directory / 'plugins/OnlyDragons.jar') as jar:
        config.write_bytes(jar.read('config.yml'))
    outcome['initialConfigSha256'] = snapshot(r, config, root / 'config-initial.yml')
    artifacts = artifact_hashes(r, directory)
    outcome['stagedArtifacts'] = artifacts
    previous_nonce = None
    for index, phase in enumerate(phases, 1):
        nonce = parent[:10] + uuid.uuid4().hex[10:]
        r.require(nonce not in (parent, previous_nonce), 'Repeated restart nonce')
        phase_root = root / f'phase-{index}'
        phase_root.mkdir()
        plan = r.player_actions.load_plan(project, phase)
        plan_file = phase_root / 'player-plan.json'
        r.stage_artifact(plan[0], plan_file, plan[1])
        context = {'schemaVersion': 1, 'mode': MODE, 'parentRunId': parent, 'index': index,
                   'nonce': nonce, 'previousNonce': previous_nonce,
                   'initialConfigPath': str(root / 'config-initial.yml'),
                   'previousReportPath': str(root / 'phase-1/scenario.json') if index == 2 else None}
        r.atomic_json(phase_root / 'context.json', context)
        record = {key: copy.deepcopy(outcome[key]) for key in
                  ('schemaVersion', 'scenarioId', 'revision', 'worktreeDirty', 'pins', 'javaVersion',
                   'build', 'artifacts', 'playerBuild', 'bootstrap', 'profile')}
        record.update(runId=nonce, index=index, parentRunId=parent, startedAtEpochMs=r.epoch_ms(),
                      passed=False, error=None, cleanup=None, context=context,
                      playerPlan={'sha256': plan[1], 'planId': plan[2]['planId']})
        outcome['phases'].append(record)
        try:
            record['memory'] = r.wait_for_memory(args.memory_mib, args.resource_timeout, 256)
            record['artifactsBefore'] = artifact_hashes(r, directory)
            r.require(record['artifactsBefore'] == artifacts, 'Restart staged artifacts changed')
            record['configBeforeSha256'] = snapshot(r, config, phase_root / 'config-before.yml')
            if index == 2:
                r.require(record['configBeforeSha256'] == outcome['phases'][0]['configAfterSha256'],
                          'Configuration changed between boots')
            # Remove only the companion's previous output, never world/configuration state.
            (directory / 'plugins/OnlyDragonsGameTests/report.json').unlink(missing_ok=True)
            r.boot(args, project, java_home, pins, args.scenario, phase, plan, nonce, directory,
                   phase_root, record, plan_file, phase_root / 'context.json')
        except (Exception, KeyboardInterrupt) as error:
            record['error'] = str(error) or type(error).__name__
            raise
        finally:
            record['completedAtEpochMs'] = r.epoch_ms()
            record['artifactsAfter'] = artifact_hashes(r, directory)
            record['configAfterSha256'] = snapshot(r, config, phase_root / 'config-after.yml')
            r.atomic_json(phase_root / 'result.json', record)
        r.require(record['artifactsAfter'] == artifacts, 'Restart staged artifacts changed')
        previous_nonce = nonce
    outcome['passed'] = True


def capture(s, project, reports, record):
    result = reports / 'result.json'
    record.update(resultPath=result.relative_to(project).as_posix(), resultSha256=s.runner.sha256(result))
    parent = s.runner.strict_json(result)
    s.reject_unstarted_run(parent, record, result)
    record['phases'] = []
    for phase in parent.get('phases', []):
        root = reports / f"phase-{phase['index']}"
        item = {'caseId': record['caseId'], 'scenarioId': record['scenarioId'], 'runId': phase['runId'],
                'startedAtEpochMs': phase['startedAtEpochMs'], 'completedAtEpochMs': phase['completedAtEpochMs'],
                'exitCode': 0 if phase['passed'] else 1}
        s.capture_run_files(project, root, item, True, reports)
        record['phases'].append(item)


def verify_continuity(r, parent, descriptor, root):
    """Independent envelope checks in addition to each ordinary phase's strict replay."""
    phases = parent.get('phases')
    r.require(parent.get('catalogMode') == MODE and isinstance(phases, list) and len(phases) == 2,
              'Missing/duplicate restart phases')
    parent_id = parent['runId']
    nonces = {parent_id}
    previous_end = parent['startedAtEpochMs']
    process_ids = set()
    initial = root / 'config-initial.yml'
    r.require(initial.is_file() and r.sha256(initial) == parent.get('initialConfigSha256'), 'Initial config changed')
    for index, phase in enumerate(phases, 1):
        nonce = phase.get('runId', '')
        r.require(type(phase.get('index')) is int and phase['index'] == index and phase.get('parentRunId') == parent_id
                  and re.fullmatch('[a-f0-9]{32}', nonce) and nonce[:10] == parent_id[:10]
                  and nonce not in nonces, 'Reordered phase or stale/wrong nonce')
        nonces.add(nonce)
        r.require(phase['profile'] == parent['profile'] and phase['artifacts'] == parent['artifacts']
                  and phase['playerBuild'] == parent['playerBuild'], 'Changed restart profile/artifacts')
        r.require(phase.get('artifactsBefore') == parent['stagedArtifacts'] == phase.get('artifactsAfter'),
                  'Changed artifacts between boots')
        root_phase = root / f'phase-{index}'
        context = r.strict_json(root_phase / 'context.json')
        expected_context = {'schemaVersion': 1, 'mode': MODE, 'parentRunId': parent_id, 'index': index,
                            'nonce': nonce, 'previousNonce': phases[0]['runId'] if index == 2 else None,
                            'initialConfigPath': str(initial),
                            'previousReportPath': str(root / 'phase-1/scenario.json') if index == 2 else None}
        r.require(context == phase.get('context') == expected_context, 'Wrong restart phase metadata')
        for name in ('before', 'after'):
            r.require(r.sha256(root_phase / f'config-{name}.yml') == phase.get(f'config{name.title()}Sha256'),
                      'Changed phase configuration evidence')
        r.require(type(phase.get('startedAtEpochMs')) is int and previous_end <= phase['startedAtEpochMs'],
                  'Overlapping or reordered boot windows')
        for role, stop_start, stop_end in (('server', 'stopStartedAtEpochMs', 'stopCompletedAtEpochMs'),
                                           ('client', 'playerStopStartedAtEpochMs', 'playerStopCompletedAtEpochMs')):
            process = phase.get(role + 'Process', {})
            identity = (process.get('pid'), process.get('startTicks'))
            r.require(all(type(value) is int and value > 0 for value in identity) and identity not in process_ids,
                      'Missing/reused process identity')
            process_ids.add(identity)
            window = [phase['startedAtEpochMs'], process.get('startedAtEpochMs'), phase.get(stop_start),
                      phase.get(stop_end), phase.get('completedAtEpochMs'), parent['completedAtEpochMs']]
            r.require(all(type(value) is int for value in window) and window == sorted(window),
                      'Missing/overlapping process lifetime')
        r.require(phase['playerStopCompletedAtEpochMs'] <= phase['stopStartedAtEpochMs'], 'Client not reaped before Paper stop')
        r.require(phase.get('cleanup') == {'exitCode': 0, 'forced': False, 'clean': True}, 'Failed phase shutdown')
        r.require('status' in phase and parent['pins']['minecraftVersion'] in phase['status'].get('version', {}).get('name', ''),
                  'Failed phase startup')
        previous_end = phase['completedAtEpochMs']
    worlds = [r.strict_json(root / f'phase-{i}/scenario.json').get('observations', {}).get('restartWorld') for i in (1, 2)]
    r.require(all(isinstance(world, dict) and set(world) == {'uuid', 'name'} for world in worlds)
              and worlds[0] == worlds[1] and worlds[0]['name'] == parent['profile']['world']
              and re.fullmatch('[a-f0-9-]{36}', worlds[0]['uuid']), 'World UUID changed across restart')
    r.require(phases[0]['configBeforeSha256'] == parent['initialConfigSha256']
              and phases[0]['configAfterSha256'] == phases[1]['configBeforeSha256'], 'Configuration changed between boots')
    r.require(phases[0].get('passed') is True and phases[0].get('error') is None, 'Failed first phase')
    positive = descriptor['phases'][1]['expectation'] == 'positive'
    r.require(parent.get('passed') is positive and phases[1].get('passed') is positive
              and parent.get('error') == phases[1].get('error') == (None if positive else 'Failed scenario assertions: scenario_exception'),
              'Arbitrary restart error or wrong outcome')


def verify_case(s, project, record, case, descriptor, source, suite_root):
    r = s.runner
    validate_descriptor(project, descriptor)
    parent_id = record.get('runId', '')
    r.require(re.fullmatch('[a-f0-9]{32}', parent_id), 'Invalid parent run ID')
    root = project / 'build/reports/agent-paper' / parent_id
    path = s.checked_file(project, record, 'resultPath', 'resultSha256', root / 'result.json')
    parent = r.strict_json(path)
    s.reject_unstarted_run(parent, record, path)
    s.timestamps(parent, record['startedAtEpochMs'], record['completedAtEpochMs'], 'restart parent')
    r.require(parent.get('runId') == parent_id and parent.get('scenarioId') == case['scenarioId']
              and parent.get('revision') == source['revision'] and parent.get('worktreeDirty') is False,
              'Wrong restart parent identity')
    r.require(type(record.get('exitCode')) is int and record['exitCode'] == (0 if case['expectation'] == 'positive' else 1),
              'Wrong restart exit code')
    verify_continuity(r, parent, descriptor, root)
    lease = parent.get('lease', {})
    window = [parent['startedAtEpochMs'], lease.get('acquiredAtEpochMs'), parent['phases'][0]['startedAtEpochMs'],
              parent['phases'][1]['completedAtEpochMs'], lease.get('releasedAtEpochMs'), parent['completedAtEpochMs']]
    r.require(all(type(value) is int for value in window) and window == sorted(window), 'Lease did not span both boots and cleanup')
    profile = s.safe_path(project, 'run/agent-tests/' + parent_id)
    with zipfile.ZipFile(profile / 'plugins/OnlyDragons.jar') as jar:
        r.require((root / 'config-initial.yml').read_bytes() == jar.read('config.yml'), 'Initial config differs from staged production defaults')
    r.require(artifact_hashes(r, profile) == parent['stagedArtifacts'], 'Restart artifacts changed on disk')
    r.require(r.sha256(profile / CONFIG) == parent['phases'][1]['configAfterSha256'], 'Final persisted config changed')
    records = record.get('phases')
    r.require(isinstance(records, list) and len(records) == 2, 'Missing/duplicate phase evidence records')
    verified = []
    for index, (phase, item, definition) in enumerate(zip(parent['phases'], records, descriptor['phases']), 1):
        phase_root = root / f'phase-{index}'
        r.require(item.get('runId') == phase['runId'], 'Reordered/wrong phase evidence')
        r.require(r.strict_json(phase_root / 'result.json') == phase, 'Phase report differs from parent')
        observations = r.strict_json(phase_root / 'scenario.json').get('observations', {})
        r.require(observations.get('restart') == {'parentRunId': parent_id, 'index': index, 'nonce': phase['runId']}
                  and observations.get('configOnBootSha256') == phase['configBeforeSha256'], 'Production phase/config observation mismatch')
        log = (phase_root / 'server.log').read_text(encoding='utf-8', errors='replace')
        r.require('OnlyDragons enabled' in log and 'OnlyDragons disabled' in log, 'Production enable/disable evidence missing')
        local = dict(item, testEvidence=record['testEvidence'])
        phase_case = dict(case, expectation=definition['expectation'])
        verified.append(s.verify_case(project, local, phase_case, definition, source, suite_root,
                                      {'parentRunId': parent_id, 'reportRoot': phase_root}))
    return dict(verified[-1], assertions=sum(item['assertions'] for item in verified), phases=verified)


def validate_message_bindings(r, phase, plan):
    requirements = phase['requiredActorMessages']
    r.require(isinstance(requirements, list) and len(requirements) <= 32, 'Invalid phase message matchers')
    actors = []
    for actor in plan['actors']:
        sessions = []
        for session in actor['sessions']:
            messages = []
            for matcher in requirements:
                r.require(isinstance(matcher, dict), 'Invalid phase message matcher')
                if matcher.get('actor') == actor['id'] and matcher.get('session') == session['id']:
                    if 'exact' in matcher:
                        messages.append(matcher['exact'])
                    else:
                        fragments = matcher.get('containsAll')
                        r.require(isinstance(fragments, list) and all(isinstance(v, str) for v in fragments),
                                  'Invalid phase message fragments')
                        messages.append(' '.join(fragments))
            sessions.append({'id': session['id'], 'messages': messages})
        actors.append({'id': actor['id'], 'sessions': sessions})
    r.player_actions.validate_messages({'actors': actors}, phase)
