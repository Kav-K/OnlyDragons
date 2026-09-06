#!/usr/bin/env python3
"""Move a terminal issue checkout out of Symphony's recursive cleanup path.

The caller must have stopped the issue worker. This helper never copies or
deletes workspace contents. Upstream v0.0.2 still ignores before_remove failures;
an error here is not a guarantee that the caller will preserve the original.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import sys
import tempfile


def real_directory(path):
    path = Path(path)
    if (not path.is_absolute() or '..' in path.parts or path.is_symlink()
            or not path.is_dir() or path.resolve(strict=True) != path):
        raise ValueError('Retention requires real absolute directories without redirection.')
    return path


def child_directory(parent, name):
    path = parent / name
    # lexists also detects broken symlinks; never replace or follow them.
    if not os.path.lexists(path):
        path.mkdir()
    return real_directory(path)


def retain_workspace(source, workspace):
    source = real_directory(source)
    state = real_directory(source / '.symphony')
    workspace_root = real_directory(state / 'workspaces')
    workspace = real_directory(workspace)
    if (workspace.parent != workspace_root
            or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', workspace.name)):
        raise ValueError('Retention requires a direct issue workspace, not the root or another checkout.')

    results = child_directory(state, 'results')
    issue_results = child_directory(results, workspace.name)
    if workspace.stat().st_dev != issue_results.stat().st_dev:
        raise ValueError('Retention requires the same filesystem; copying is not permitted.')
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    archive = Path(tempfile.mkdtemp(prefix=stamp + '-', dir=issue_results))
    retained = archive / 'workspace'
    # Recheck the anchors after creating the destination. Retention is an
    # operator hook, not an adversarial concurrent filesystem service.
    for path in (source, state, workspace_root, workspace, results, issue_results, archive):
        real_directory(path)
    if os.path.lexists(retained):
        raise FileExistsError('Reserved retention destination is not empty.')
    if workspace.stat().st_dev != archive.stat().st_dev:
        raise ValueError('Retention destination changed filesystem.')
    os.rename(workspace, retained)

    # Leave the original path absent: File.rm_rf on that path is then trivial.
    # If metadata writing fails, the complete checkout still lives at retained.
    manifest = {
        'schemaVersion': 1,
        'kind': 'symphony-workspace-retention',
        'originalWorkspace': str(workspace),
        'retainedWorkspace': str(retained),
        'retainedAtUtc': datetime.now(timezone.utc).isoformat(),
        'method': 'same-filesystem-rename',
        'includesIgnoredFiles': True,
    }
    try:
        with (archive / 'retention.json').open('x', encoding='utf-8') as stream:
            json.dump(manifest, stream, indent=2)
            stream.write('\n')
    except OSError as error:
        raise RuntimeError('Workspace retained at ' + str(retained)
                           + ' but retention metadata could not be written.') from error
    return retained


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', default=os.environ.get('ONLYDRAGONS_SOURCE'))
    parser.add_argument('--workspace', default=str(Path.cwd()))
    args = parser.parse_args()
    if not args.source:
        parser.error('ONLYDRAGONS_SOURCE or --source is required.')
    try:
        retained = retain_workspace(args.source, args.workspace)
    except (OSError, ValueError, RuntimeError) as error:
        print('Workspace retention failed: ' + str(error), file=sys.stderr)
        return 1
    print('Workspace retained at ' + str(retained))
    return 0


if __name__ == '__main__':
    sys.exit(main())
