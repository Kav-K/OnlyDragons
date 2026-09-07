#!/usr/bin/env python3
"""Check declared direction and real suite evidence; never grant review/CI/human approval.

plan: structural consistency only (exit 0), never gameplay acceptance.
acceptance --automated: verified changed-area/task machine evidence (exit 0).
acceptance: same evidence, exit 2 while independent external gates remain.
No network, credentials, server launch, issue mutation, or progress-file mutation.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import time


class CheckpointError(RuntimeError):
    """A plan, requirement, dependency or acceptance-evidence contract is invalid."""
    pass


def require(condition, message):
    """Raise CheckpointError with a precise diagnostic when a checkpoint condition fails."""
    if not condition:
        raise CheckpointError(message)


def sibling_module(name):
    """Load a repository sibling by explicit path under a private module name for test isolation."""
    path = Path(__file__).with_name(name + '.py')
    require(path.is_file(), 'Required checkpoint dependency is missing: ' + name)
    directory = str(path.parent)
    if directory not in sys.path:
        sys.path.insert(0, directory)
    spec = importlib.util.spec_from_file_location('_checkpoint_' + name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def load_json(path):
    """Read bounded duplicate-safe finite JSON and translate validation errors to checkpoint errors."""
    try:
        return sibling_module('paper_test').strict_json(Path(path))
    except (ValueError, RuntimeError, OSError) as error:
        raise CheckpointError(str(error)) from error


def names(value, label, nonempty=False):
    """Validate unique string identifiers and return a set, optionally rejecting an empty list."""
    require(isinstance(value, list) and all(isinstance(x, str) and x for x in value), label + ' must be a string list')
    require(len(set(value)) == len(value), 'Duplicate ' + label)
    require(not nonempty or value, 'Missing ' + label)
    return set(value)


def versioned(value, label):
    """Require an object with integer schemaVersion 1; booleans do not count as versions."""
    require(isinstance(value, dict) and type(value.get('schemaVersion')) is int
            and value['schemaVersion'] == 1, 'Unsupported ' + label + ' schema')


def acyclic(graph, label):
    """Reject unknown dependency IDs and cycles using a read-only depth-first traversal."""
    done = set()
    def visit(node, path):
        require(node not in path, label + ' dependency cycle: ' + ' -> '.join(path + [node]))
        if node in done:
            return
        for dependency in graph[node]:
            require(dependency in graph, 'Unknown ' + label + ' dependency: ' + dependency)
            visit(dependency, path + [node])
        done.add(node)
    for node in graph:
        visit(node, [])


def evidence_reference(value, label):
    """Validate the shape of an HTTPS evidence URL and Git revision, without fetching proof."""
    require(isinstance(value, dict), 'Missing evidence reference: ' + label)
    require(isinstance(value.get('url'), str) and value['url'].startswith('https://'),
            'Evidence must have a durable HTTPS reference: ' + label)
    require(isinstance(value.get('revision'), str) and re.fullmatch(r'[a-f0-9]{7,40}', value['revision']),
            'Evidence revision missing: ' + label)


def validate_task_gates(key, tasks, progress, dependencies, milestone_gates):
    """Require a task's dependencies, named manual gate and prerequisite milestones to be complete."""
    missing = sorted(dep for dep in dependencies[key] if progress['tasks'][dep]['status'] != 'complete')
    require(not missing, 'Unsatisfied task prerequisites for ' + key + ': ' + ', '.join(missing))
    if tasks[key].get('manualGate'):
        evidence_reference(progress['tasks'][key].get('manualGateEvidence'), key + ' manual gate')
    missing = sorted(gate for gate in milestone_gates[key] if progress['milestones'][gate]['status'] != 'accepted')
    require(not missing, 'Task requires accepted milestones for ' + key + ': ' + ', '.join(missing))


def validate_plan(project, snapshot=None, now_ms=None):
    """Cross-check the backlog, requirements, progress, fixture catalogs and Java registrations.

    Enforce consistent IDs, acyclic combined dependencies, honest deferred/external
    requirements, scenario bindings and recorded completion evidence. Optional sanitized
    live issue state must also agree. Return validated data plus planValid metadata;
    structural consistency never implies automatedReady or human acceptance.
    """
    project = Path(project)
    backlog = load_json(project / 'docs/planning/backlog.json')
    mapping = load_json(project / 'docs/planning/github-issues.json')
    plan = load_json(project / 'dev/game-tests/acceptance.json')
    progress = load_json(project / 'docs/planning/progress.json')
    scenarios = load_json(project / 'dev/game-tests/scenarios.json')
    suites = load_json(project / 'dev/game-tests/suites.json')
    versioned(suites, 'suite catalog')
    require(isinstance(suites.get('cases'), dict) and suites['cases'], 'Suite cases missing')
    for value, label in ((backlog, 'backlog'), (plan, 'acceptance'), (progress, 'progress')):
        versioned(value, label)
    require(backlog.get('repository') == plan.get('repository') == progress.get('repository') == 'Kav-K/OnlyDragons',
            'Repository identity drift')
    require(isinstance(backlog.get('tasks'), list), 'Backlog tasks missing')
    tasks = {}
    for task in backlog['tasks']:
        require(isinstance(task, dict) and isinstance(task.get('id'), str), 'Invalid backlog task')
        require(task['id'] not in tasks, 'Duplicate backlog task: ' + task['id'])
        if 'manualGate' in task:
            require(isinstance(task['manualGate'], str) and task['manualGate'].strip(), 'Invalid manual gate: ' + task['id'])
        tasks[task['id']] = task
    require(isinstance(mapping, dict) and set(tasks) == set(mapping), 'Backlog/issue mapping drift')
    require(isinstance(plan.get('tasks'), dict) and set(tasks) == set(plan['tasks']), 'Backlog/acceptance task drift')
    require(isinstance(progress.get('tasks'), dict) and set(tasks) == set(progress['tasks']), 'Backlog/progress task drift')
    numbers = []
    for key, entry in mapping.items():
        require(isinstance(entry, dict) and type(entry.get('number')) is int and entry['number'] > 0,
                'Invalid issue mapping: ' + key)
        require(entry.get('url') == f'https://github.com/Kav-K/OnlyDragons/issues/{entry["number"]}',
                'Issue URL drift: ' + key)
        numbers.append(entry['number'])
    require(len(numbers) == len(set(numbers)), 'Multiple tasks mapped to one issue')
    dependencies = {key: names(task.get('dependsOn'), key + ' dependencies') for key, task in tasks.items()}
    milestone_gates = {key: names(task.get('requiredMilestones', []), key + ' required milestones')
                       for key, task in tasks.items()}
    acyclic(dependencies, 'task')

    requirements = plan.get('requirements')
    fixtures = plan.get('fixtures')
    require(isinstance(requirements, dict) and requirements, 'Acceptance requirements missing')
    require(isinstance(fixtures, dict) and set(fixtures) == set(scenarios), 'Scenario/fixture coverage drift')
    source = (project / 'dev/game-tests/src/main/java/com/kaveenk/onlydragons/gametests/GameTestsPlugin.java').read_text(encoding='utf-8')
    declaration = re.search(r'private final Map<String, Scenario> scenarios\s*=\s*(.*?);', source, re.S)
    require(declaration is not None, 'Cannot find explicit companion scenario registration')
    registered = re.findall(r'"([a-z0-9-]+)"\s*,\s*new\s+', declaration.group(1))
    require(len(registered) == len(set(registered)) and set(registered) == set(scenarios),
            'Java registration/scenario catalog drift')
    for key, fixture in fixtures.items():
        require(isinstance(fixture, dict), 'Invalid fixture: ' + key)
        required = names(scenarios[key].get('requiredAssertions'), key + ' catalog assertions', True)
        contract = names(fixture.get('assertions'), key + ' acceptance assertions', True)
        require(contract <= required, 'Missing registered acceptance assertions: ' + key)
        cases = names(fixture.get('caseIds'), key + ' suite cases', True)
        require(cases <= set(suites['cases']), 'Unknown fixture suite case: ' + key)
        require(all(isinstance(suites['cases'][case], dict) and suites['cases'][case].get('scenarioId') == key
                    for case in cases), 'Fixture/suite scenario binding drift: ' + key)
        positives = {case for case, definition in suites['cases'].items()
                     if definition.get('scenarioId') == key and definition.get('expectation') == 'positive'}
        require(positives <= cases, 'Positive fixture coverage cannot be replaced by negative controls: ' + key)
        require(isinstance(fixture.get('scope'), str) and fixture['scope'], 'Fixture scope is missing: ' + key)
    referenced_fixtures = set()
    for key, requirement in requirements.items():
        require(isinstance(requirement, dict), 'Invalid requirement: ' + key)
        kind = requirement.get('kind')
        require(kind in ('automated', 'ci', 'human', 'design', 'external-review'), 'Unknown requirement kind: ' + key)
        require(requirement.get('availability') in ('implemented', 'deferred', 'external'), 'Unknown requirement availability: ' + key)
        bindings = names(requirement.get('fixtures'), key + ' fixture bindings')
        require(bindings <= set(fixtures), 'Unknown fixture binding: ' + key)
        require(isinstance(requirement.get('description'), str) and requirement['description'], 'Requirement description missing: ' + key)
        if kind == 'automated':
            require(requirement['availability'] in ('implemented', 'deferred'), 'Automated requirement cannot be external: ' + key)
            require(bool(bindings) == (requirement['availability'] == 'implemented'), 'Automated requirement lacks an honest fixture binding: ' + key)
        elif kind == 'ci':
            require(requirement['availability'] == 'implemented' and not bindings, 'CI requirement cannot claim Paper fixture coverage: ' + key)
        else:
            require(requirement['availability'] == 'external' and not bindings, 'External gate cannot be passed by a fixture: ' + key)
        referenced_fixtures.update(bindings)
    require(referenced_fixtures == set(fixtures), 'Fixture has no acceptance requirement')
    task_requirements = {}
    for key, refs in plan['tasks'].items():
        task_requirements[key] = names(refs, key + ' requirements', True)
        require(task_requirements[key] <= set(requirements), 'Unknown task requirement: ' + key)
        state = progress['tasks'][key]
        require(isinstance(state, dict) and state.get('status') in ('complete', 'active', 'partial', 'planned', 'blocked', 'in-review'),
                'Invalid progress status: ' + key)
        completed = names(state.get('completedRequirements'), key + ' completed requirements')
        require(completed <= task_requirements[key], 'Unassigned completed component: ' + key)
        for component in completed:
            require(requirements[component]['availability'] != 'deferred', 'Deferred component declared complete: ' + component)
            evidence_reference(state.get('evidence', {}).get(component), key + '/' + component)
        if state['status'] == 'complete':
            require(completed == task_requirements[key], 'Task complete with missing requirements: ' + key)
            require(isinstance(state.get('mergedRevision'), str) and re.fullmatch(r'[a-f0-9]{7,40}', state['mergedRevision']),
                    'Task complete without integrated revision: ' + key)
        if state['status'] == 'partial':
            require(completed and completed < task_requirements[key], 'Partial task must retain unfinished components: ' + key)

    paper_cases = plan.get('paperCases')
    require(isinstance(paper_cases, dict) and set(paper_cases) == {f'P{i:02d}' for i in range(1, 15)}, 'P01-P14 coverage mapping drift')
    for key, refs in paper_cases.items():
        require(names(refs, key + ' requirements', True) <= set(requirements), 'Unknown Paper-case requirement: ' + key)
        require(all(requirements[ref]['kind'] == 'automated' for ref in refs), 'Functional Paper case cannot be delegated to a human gate: ' + key)
    milestones = plan.get('milestones')
    require(isinstance(milestones, dict) and set(milestones) == {f'M{i}' for i in range(6)}, 'M0-M5 requirement mapping drift')
    require(isinstance(progress.get('milestones'), dict) and set(progress['milestones']) == set(milestones), 'Milestone progress drift')
    acyclic({key: names(value.get('dependsOn'), key + ' milestone dependencies') for key, value in milestones.items()}, 'milestone')
    for key, milestone in milestones.items():
        refs = names(milestone.get('requirements'), key + ' requirements', True)
        task_refs = names(milestone.get('tasks'), key + ' tasks', True)
        require(refs <= set(requirements) and task_refs <= set(tasks), 'Unknown milestone requirement/task: ' + key)
        state = progress['milestones'][key]
        require(isinstance(state, dict) and state.get('status') in ('not-accepted', 'accepted'), 'Invalid milestone status: ' + key)
        if state['status'] == 'accepted':
            require(all(progress['tasks'][ref]['status'] == 'complete' for ref in task_refs), 'Milestone accepted with incomplete task: ' + key)
            require(all(progress['milestones'][dep]['status'] == 'accepted' for dep in milestone['dependsOn']), 'Milestone accepted before prerequisite: ' + key)
            for ref in refs:
                require(requirements[ref]['availability'] != 'deferred', 'Milestone accepted with deferred evidence: ' + key + '/' + ref)
                evidence_reference(state.get('evidence', {}).get(ref), key + '/' + ref)
    combined = {}
    for key, task in tasks.items():
        require(milestone_gates[key] <= set(milestones), 'Unknown required milestone: ' + key)
        combined['task:' + key] = {'task:' + dep for dep in dependencies[key]} | {
            'milestone:' + gate for gate in milestone_gates[key]}
    for key, milestone in milestones.items():
        combined['milestone:' + key] = {'task:' + task for task in milestone['tasks']} | {
            'milestone:' + dep for dep in milestone['dependsOn']}
    acyclic(combined, 'task/milestone')
    for key in tasks:
        if progress['tasks'][key]['status'] in ('active', 'partial', 'in-review', 'complete'):
            validate_task_gates(key, tasks, progress, dependencies, milestone_gates)
    if snapshot is not None:
        validate_live_snapshot(snapshot, mapping, progress, dependencies, now_ms)
    return {'plan': plan, 'progress': progress, 'dependencies': dependencies,
            'backlogTasks': tasks, 'milestoneGates': milestone_gates,
            'summary': {'status': 'plan-valid', 'planValid': True, 'automatedReady': False, 'acceptanceApproved': False,
                        'taskStates': {key: value['status'] for key, value in progress['tasks'].items()},
                        'milestoneStates': {key: value['status'] for key, value in progress['milestones'].items()},
                        'pendingAutomatedRequirements': [key for key, value in requirements.items() if value['availability'] == 'deferred'],
                        'externalGates': ['independent-review', 'current-ci'],
                        'externalRequirementKinds': {key: value['kind'] for key, value in requirements.items() if value['kind'] != 'automated'}}}


def validate_live_snapshot(snapshot, mapping, progress, dependencies, now_ms=None):
    """Consume sanitized facts for drift detection only; this is not approval provenance."""
    versioned(snapshot, 'GitHub snapshot')
    require(snapshot.get('repository') == 'Kav-K/OnlyDragons', 'Wrong snapshot repository')
    now_ms = int(time.time() * 1000) if now_ms is None else now_ms
    captured = snapshot.get('capturedAtEpochMs')
    require(type(captured) is int and now_ms - 900_000 <= captured <= now_ms + 1000, 'Stale/future GitHub snapshot')
    require(isinstance(snapshot.get('issues'), list), 'Snapshot issues missing')
    issues = {}
    for issue in snapshot['issues']:
        require(isinstance(issue, dict) and type(issue.get('number')) is int, 'Invalid snapshot issue')
        require(issue['number'] not in issues and issue.get('state') in ('open', 'closed'), 'Duplicate/invalid snapshot issue')
        names(issue.get('labels'), 'issue labels')
        issues[issue['number']] = issue
    require(set(issues) == {item['number'] for item in mapping.values()}, 'Snapshot does not cover exactly mapped task issues')
    for key, ref in mapping.items():
        issue, state = issues[ref['number']], progress['tasks'][key]['status']
        require((issue['state'] == 'closed') == (state == 'complete'), 'Issue closure/progress drift: ' + key)
        if 'symphony' in issue['labels']:
            require(issue['state'] == 'open' and state in ('active', 'partial', 'in-review'), 'Dispatch/progress drift: ' + key)
            require(all(progress['tasks'][dep]['status'] == 'complete' for dep in dependencies[key]), 'Dispatched task has unmet prerequisites: ' + key)


def base_document(project, revision, path):
    """Read a JSON document from a Git revision, returning None only when that path is absent."""
    exists = subprocess.run(['git', 'cat-file', '-e', revision + ':' + path], cwd=project,
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if exists.returncode:
        return None
    raw = subprocess.check_output(['git', 'show', revision + ':' + path], cwd=project)
    return json.loads(raw)


def validate_no_weakening(project, base, validated):
    """Additions are allowed; removal of existing invariants needs a reviewed plan change.

    There is deliberately no self-attested override flag. An explicit plan revision
    must first land through independent review, becoming the new comparison base.
    """
    revision = subprocess.check_output(['git', 'rev-parse', '--verify', '--end-of-options', base + '^{commit}'],
                                       cwd=project).decode().strip()
    previous = base_document(project, revision, 'docs/planning/backlog.json')
    if previous is not None:
        current_tasks = {task['id']: task for task in load_json(Path(project) / 'docs/planning/backlog.json')['tasks']}
        for task in previous['tasks']:
            require(task['id'] in validated['dependencies'], 'Previously declared task removed: ' + task['id'])
            require(set(task['dependsOn']) <= validated['dependencies'][task['id']], 'Task dependency removed: ' + task['id'])
            updated = current_tasks[task['id']]
            require(set(task.get('requiredMilestones', [])) <= set(updated.get('requiredMilestones', [])),
                    'Task milestone gate removed: ' + task['id'])
            require(not task.get('manualGate') or updated.get('manualGate') == task['manualGate'],
                    'Task manual gate removed or changed: ' + task['id'])
    current_scenarios = load_json(Path(project) / 'dev/game-tests/scenarios.json')
    previous = base_document(project, revision, 'dev/game-tests/scenarios.json')
    if previous is not None:
        require(set(previous) <= set(current_scenarios), 'Previously registered scenario removed')
        for key, value in previous.items():
            if 'phases' in value or 'catalogMode' in value:
                updated = current_scenarios[key]
                label = 'Previously bound restart phases changed: ' + key
                require(('catalogMode' in value) == ('catalogMode' in updated)
                        and value.get('catalogMode') == updated.get('catalogMode'), label)
                old_phases, new_phases = value.get('phases'), updated.get('phases')
                require(isinstance(old_phases, list) and isinstance(new_phases, list)
                        and len(old_phases) == len(new_phases), label)
                for position, (old_phase, new_phase) in enumerate(zip(old_phases, new_phases)):
                    phase_label = label + '/' + str(position + 1)
                    require(isinstance(old_phase, dict) and isinstance(new_phase, dict)
                            and set(old_phase) == set(new_phase)
                            and 'requiredAssertions' in old_phase, phase_label)
                    # Preserve every other field, list order and JSON scalar type.
                    metadata = lambda phase: json.dumps(
                        {field: item for field, item in phase.items() if field != 'requiredAssertions'},
                        sort_keys=True, allow_nan=False)
                    require(metadata(old_phase) == metadata(new_phase), phase_label)
                    require(names(old_phase['requiredAssertions'], phase_label, nonempty=True)
                            <= names(new_phase['requiredAssertions'], phase_label, nonempty=True),
                            phase_label + '/requiredAssertions')
            require(set(value['requiredAssertions']) <= set(current_scenarios[key]['requiredAssertions']),
                    'Previously required scenario assertion removed: ' + key)
            messages = {item['id']: item for item in current_scenarios[key].get('requiredPlayerMessages', [])}
            for message in value.get('requiredPlayerMessages', []):
                updated = messages.get(message['id'], {})
                if 'exact' in message:
                    preserved = updated.get('exact') == message['exact']
                else:
                    preserved = 'containsAll' in updated and set(message['containsAll']) <= set(updated['containsAll'])
                require(preserved, 'Previously required player message weakened: ' + key + '/' + message['id'])
    previous = base_document(project, revision, 'dev/game-tests/suites.json')
    if previous is not None:
        current_suites = load_json(Path(project) / 'dev/game-tests/suites.json')
        require(set(previous['cases']) <= set(current_suites['cases']), 'Previously declared suite case removed')
        for key, value in previous['cases'].items():
            for field in ('scenarioId', 'expectation', 'testPlayer', 'playerControl'):
                require(value.get(field) == current_suites['cases'][key].get(field), 'Suite case meaning changed: ' + key + '/' + field)
        for key, value in previous['suites'].items():
            require(set(value) <= set(current_suites['suites'].get(key, [])), 'Named suite coverage weakened: ' + key)
        for key, value in previous['areas'].items():
            require(key in current_suites['areas'], 'Changed-area coverage removed: ' + key)
            for field in ('paths', 'cases', 'affects'):
                require(set(value[field]) <= set(current_suites['areas'][key][field]), 'Changed-area coverage weakened: ' + key + '/' + field)
        require(set(current_suites['ignoredChanges']) <= set(previous['ignoredChanges']), 'New changes excluded from suite selection')
    previous = base_document(project, revision, 'dev/game-tests/acceptance.json')
    current = validated['plan']
    if previous is not None:
        for group in ('fixtures', 'requirements', 'tasks', 'paperCases', 'milestones'):
            require(set(previous[group]) <= set(current[group]), 'Previously declared acceptance entry removed: ' + group)
        for key, value in previous['fixtures'].items():
            for field in ('assertions', 'caseIds'):
                require(set(value[field]) <= set(current['fixtures'][key][field]), 'Fixture coverage weakened: ' + key + '/' + field)
        for key, value in previous['requirements'].items():
            updated = current['requirements'][key]
            require(value['kind'] == updated['kind'], 'Requirement kind changed: ' + key)
            require(value['availability'] != 'implemented' or updated['availability'] == 'implemented',
                    'Implemented requirement deferred: ' + key)
            require(set(value['fixtures']) <= set(updated['fixtures']), 'Requirement fixture binding removed: ' + key)
        for group in ('tasks', 'paperCases'):
            for key, value in previous[group].items():
                require(set(value) <= set(current[group][key]), 'Acceptance requirement mapping weakened: ' + group + '/' + key)
        for key, value in previous['milestones'].items():
            for field in ('requirements', 'tasks', 'dependsOn'):
                require(set(value[field]) <= set(current['milestones'][key][field]), 'Milestone gate weakened: ' + key + '/' + field)
    previous = base_document(project, revision, 'docs/planning/progress.json')
    if previous is not None:
        for key, value in previous['tasks'].items():
            require(key in validated['progress']['tasks'], 'Previously tracked progress task removed: ' + key)
            require(set(value['completedRequirements']) <= set(validated['progress']['tasks'][key]['completedRequirements']),
                    'Previously completed component removed: ' + key)
    return revision


def validate_acceptance(project, receipt_path, base, task_ids=(), suite_module=None):
    """Replay current clean-source evidence and derive the selected tasks' automated readiness.

    Recompute affected cases from the real Git diff, enforce additive/no-weakening
    contracts and all task bindings, and reject deferred implementations. External gates
    remain pending even after machine checks pass. Recheck source at the end; this
    function never promotes a milestone or edits the plan.
    """
    validated = validate_plan(project)
    selected_tasks = names(list(task_ids), 'requested tasks')
    require(selected_tasks <= set(validated['plan']['tasks']), 'Unknown requested task')
    for key in selected_tasks:
        validate_task_gates(key, validated['backlogTasks'], validated['progress'],
                            validated['dependencies'], validated['milestoneGates'])
    suite = suite_module or sibling_module('paper_suite')
    project = Path(project).resolve()
    current = suite.source_identity(project)  # Rejects dirty/untracked inputs, even with a forged receipt flag.
    receipt_path = Path(receipt_path)
    if not receipt_path.is_absolute():
        receipt_path = project / receipt_path
    receipt = suite.validate_suite_receipt(project, receipt_path)
    require(receipt['source']['sourceInputSha256'] == current['sourceInputSha256'], 'Stale suite source input hash')
    revision = validate_no_weakening(project, base, validated)
    changed = subprocess.check_output(['git', 'diff', '--no-renames', '--name-only', '-z', revision, 'HEAD', '--'], cwd=project).decode().split('\0')
    changed = [path for path in changed if path]
    required_cases = set(suite.required_cases(project, changed))
    pending, external = [], ['independent-review-of-current-inputs', 'current-head-ci']
    for key in selected_tasks:
        for requirement_id in validated['plan']['tasks'][key]:
            requirement = validated['plan']['requirements'][requirement_id]
            if requirement['availability'] == 'deferred':
                pending.append(requirement_id)
            if requirement['kind'] != 'automated':
                external.append(requirement_id)
            for fixture in requirement['fixtures']:
                required_cases.update(validated['plan']['fixtures'][fixture]['caseIds'])
    case_ids = [case['caseId'] for case in receipt['cases']]
    require(len(case_ids) == len(set(case_ids)), 'Duplicate receipt case IDs')
    require(required_cases <= set(case_ids), 'Receipt missing required changed-area/task cases: ' + ', '.join(sorted(required_cases - set(case_ids))))
    require(not pending, 'Task still has deferred automated requirements: ' + ', '.join(sorted(set(pending))))
    require(suite.source_identity(project) == current, 'Source inputs changed during checkpoint verification')
    result = dict(validated['summary'])
    result.update(status='automated-ready', automatedReady=True, acceptanceApproved=False,
                  source=current, comparisonBase=revision, verifiedReceipt=str(receipt_path), changedPaths=changed,
                  requiredCases=sorted(required_cases), requestedTasks=sorted(selected_tasks),
                  externalGates=sorted(set(external)),
                  notice='Exit 0 with --automated means machine evidence only. Review/CI and human/design gates are not approved by this command.')
    return result


def main(argv=None):
    """Return 0 for valid plans or explicitly automated readiness, 2 for pending external gates.

    Invalid input/evidence returns 1. These exit meanings distinguish machine validation
    from full acceptance; none of the modes launches Minecraft or writes project state.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['plan', 'acceptance'])
    parser.add_argument('--project', type=Path, default=Path.cwd())
    parser.add_argument('--snapshot', type=Path, help='Sanitized complete current task-issue snapshot; drift detection only')
    parser.add_argument('--receipt', type=Path)
    parser.add_argument('--base', help='Git comparison base; changed paths are derived locally, not trusted from receipt')
    parser.add_argument('--task', action='append', default=[])
    parser.add_argument('--automated', action='store_true', help='Exit 0 for machine evidence only; external gates remain pending')
    args = parser.parse_args(argv)
    try:
        require(args.mode == 'acceptance' or not (args.receipt or args.base or args.task or args.automated), 'Acceptance options require acceptance mode')
        snapshot = load_json(args.snapshot) if args.snapshot else None
        planned = validate_plan(args.project, snapshot)
        if args.mode == 'plan':
            result, code = planned['summary'], 0
        else:
            require(args.receipt and args.base, 'Acceptance requires --receipt and --base')
            result = validate_acceptance(args.project, args.receipt, args.base, args.task)
            code = 0 if args.automated else 2
            if code == 2:
                result['status'] = 'external-gates-pending'
        print(json.dumps(result, indent=2, allow_nan=False))
        return code
    except (CheckpointError, RuntimeError, ValueError, OSError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(json.dumps({'status': 'checkpoint-failed', 'automatedReady': False, 'acceptanceApproved': False, 'error': str(error)}, indent=2))
        return 1


if __name__ == '__main__':
    sys.exit(main())
