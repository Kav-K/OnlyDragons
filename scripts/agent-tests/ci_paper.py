#!/usr/bin/env python3
"""Manual hosted-Paper orchestration; reuse consent and export unchanged replay inputs."""
from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tarfile
import time
import urllib.request

import paper_bootstrap
import paper_suite
import paper_test


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def canonical_root(run_id, attempt):
    require(isinstance(run_id, str) and re.fullmatch(r'[1-9][0-9]{0,19}', run_id), 'Invalid Actions run ID')
    require(isinstance(attempt, str) and re.fullmatch(r'[1-9][0-9]{0,5}', attempt), 'Invalid Actions attempt')
    return Path('/tmp/onlydragons-paper-ci') / (run_id + '-' + attempt)


def java_version(checkout):
    """Use the existing exact Linux runtime pin, checked against the project's major."""
    major = paper_test.properties(Path(checkout) / 'versions.properties')['javaVersion']
    script = (Path(checkout) / 'scripts/symphony/install-runtime.sh').read_text(encoding='utf-8')
    matches = re.findall(r'^JAVA_VERSION="([0-9]+(?:\.[0-9]+){0,3})"\s*$', script, re.MULTILINE)
    require(len(matches) == 1 and matches[0].split('.')[0] == major,
            'Exact Linux JDK pin is missing or disagrees with versions.properties')
    return matches[0]


def materialize_consent(encoded, destination):
    require(isinstance(encoded, str) and 0 < len(encoded) <= 32768,
            'Set ONLYDRAGONS_PAPER_EULA_BASE64 from the existing accepted file; no consent is generated')
    try:
        raw = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error) as error:
        raise RuntimeError('Existing EULA variable must contain valid base64 bytes') from error
    require(0 < len(raw) <= 16384 and base64.b64encode(raw).decode('ascii') == encoded,
            'Existing EULA variable is empty, oversized or noncanonical')
    with Path(destination).open('xb') as output:
        output.write(raw)
    Path(destination).chmod(0o600)
    paper_test.accepted_eula(Path(destination))
    return hashlib.sha256(raw).hexdigest()


def download_verified(url, destination, expected):
    require(isinstance(url, str) and url.startswith('https://')
            and re.fullmatch(r'[a-f0-9]{64}', expected), 'Download requires HTTPS and a pinned SHA256')
    request = urllib.request.Request(url, headers={'User-Agent': 'OnlyDragons-Paper-CI (https://github.com/Kav-K/OnlyDragons)'})
    size = 0
    with urllib.request.urlopen(request, timeout=90) as response, Path(destination).open('xb') as output:
        require(response.geturl().startswith('https://'), 'Artifact redirect left HTTPS')
        while data := response.read(1024 * 1024):
            size += len(data)
            require(size <= 256 * 1024 * 1024, 'Public bootstrap artifact exceeds its size bound')
            output.write(data)
    require(paper_test.sha256(Path(destination)) == expected, 'Public bootstrap artifact SHA256 mismatch')
    return Path(destination)


def clone_exact(checkout, project, revision):
    require(isinstance(revision, str) and re.fullmatch(r'[a-f0-9]{40,64}', revision), 'Use an exact source commit SHA')
    actual = paper_suite.git(checkout, 'rev-parse', 'HEAD').decode().strip()
    require(actual == revision, 'Actions checkout differs from the requested commit')
    paper_suite.source_identity(checkout)
    subprocess.run(['git', 'clone', '--no-hardlinks', '--no-checkout', '--', str(checkout), str(project)],
                   check=True, timeout=120)
    subprocess.run(['git', '-C', str(project), '-c', 'core.autocrlf=false', 'checkout', '--detach', revision],
                   check=True, timeout=120)
    source = paper_suite.source_identity(project)
    require(source['revision'] == revision, 'Run checkout is not the requested source commit')
    return source


def evidence_files(project):
    """Exact allowlist for current strict replay; never include worlds, auth or caches."""
    project = Path(project).resolve()
    selected = set()

    def include(path):
        relative = path.relative_to(project).as_posix()
        checked = paper_suite.safe_path(project, relative)
        require(checked.is_file(), 'Evidence entry is not a regular file: ' + relative)
        selected.add(relative)

    for relative in ('build/reports/agent-paper', 'build/reports/agent-paper-suites'):
        root = paper_suite.safe_path(project, relative)
        if root.exists():
            for path in root.rglob('*'):
                require(not path.is_symlink(), 'Symlink report evidence is not supported')
                if path.is_file():
                    include(path)
    profiles = paper_suite.safe_path(project, 'run/agent-tests')
    if profiles.exists():
        for profile in profiles.iterdir():
            require(re.fullmatch(r'[a-f0-9]{32}', profile.name) and profile.is_dir() and not profile.is_symlink(),
                    'Unexpected disposable profile directory')
            for relative in ('server.jar', 'server.properties', 'whitelist.json', 'player-plan.json',
                             'plugins/OnlyDragons.jar', 'plugins/OnlyDragonsGameTests.jar'):
                path = profile / relative
                if path.exists() or path.is_symlink():
                    include(path)
            for directory, pattern in (('cache', 'mojang_*.jar'), ('player-client', '*.jar')):
                root = profile / directory
                if root.exists():
                    require(not root.is_symlink(), 'Symlink artifact directory is not supported')
                    for path in root.glob(pattern):
                        include(path)
    return sorted(selected)


def export_evidence(project, output, manifest):
    output = Path(output)
    files = evidence_files(project) if Path(project).is_dir() else []
    archive_path = output / 'evidence.tar.gz'
    # Every raw path survives. Identical bytes use only backward, archive-internal
    # hardlinks (never filesystem symlinks); report contents are never rewritten.
    contents = {}
    logical_bytes = stored_bytes = 0
    with tarfile.open(archive_path, 'w:gz', compresslevel=1) as archive:
        for relative in files:
            path = paper_suite.safe_path(project, relative)
            entry = archive.gettarinfo(str(path), arcname=relative)
            require(entry.isfile(), 'Evidence archive accepts regular source files only')
            digest = paper_test.sha256(path)
            key = (entry.size, digest)
            logical_bytes += entry.size
            if key in contents:
                entry.type = tarfile.LNKTYPE
                entry.linkname = contents[key]
                entry.size = 0
                archive.addfile(entry)
            else:
                with path.open('rb') as data:
                    archive.addfile(entry, data)
                require(paper_test.sha256(path) == digest, 'Evidence changed during archive export')
                contents[key] = relative
                stored_bytes += entry.size
    manifest.update({'archive': archive_path.name, 'archiveSha256': paper_test.sha256(archive_path),
                     'evidenceFileCount': len(files), 'evidenceStoredFileCount': len(contents),
                     'evidenceLogicalBytes': logical_bytes, 'evidenceStoredBytes': stored_bytes,
                     'completedAtEpochMs': int(time.time() * 1000)})
    paper_test.atomic_json(output / 'manifest.json', manifest)


def execute(args):
    require(sys.platform == 'linux', 'Hosted Paper execution requires Linux; replay also uses Linux/WSL')
    checkout = args.checkout.resolve()
    hosted = {'GITHUB_ACTIONS': 'true', 'RUNNER_ENVIRONMENT': 'github-hosted',
              'GITHUB_REPOSITORY': 'Kav-K/OnlyDragons', 'GITHUB_EVENT_NAME': 'workflow_dispatch',
              'GITHUB_RUN_ID': args.run_id, 'GITHUB_RUN_ATTEMPT': args.attempt, 'GITHUB_SHA': args.revision}
    require(all(os.environ.get(key) == value for key, value in hosted.items())
            and os.environ.get('GITHUB_WORKSPACE') is not None
            and Path(os.environ['GITHUB_WORKSPACE']).resolve() == checkout,
            'Use this helper only in the matching manual GitHub-hosted job; local agents must use their shared runner lease')
    root = canonical_root(args.run_id, args.attempt)
    project = root / 'OnlyDragons'
    output = args.output.resolve()
    require(not output.is_relative_to(checkout) and not output.is_relative_to(root),
            'Evidence export must be outside source and runtime checkouts')
    output.mkdir(parents=True, exist_ok=False)
    manifest = {'schemaVersion': 1, 'kind': 'onlydragons-hosted-paper-evidence', 'passed': False,
                'sourceRevision': args.revision, 'projectDirectory': str(project),
                'actionsRunId': args.run_id, 'actionsAttempt': args.attempt,
                'startedAtEpochMs': int(time.time() * 1000), 'suiteExitCode': None,
                'replayExitCode': None, 'receiptPath': None, 'error': None}
    owned_root = False
    exit_code = 1
    try:
        require(not root.exists() and not root.is_symlink(), 'Actions run directory already exists; refusing reuse')
        root.parent.mkdir(parents=True, exist_ok=True)
        require(root.parent.resolve() == root.parent, 'Actions runtime parent must not be a symlink')
        root.mkdir(mode=0o700)
        owned_root = True
        eula = root / 'accepted-eula.txt'
        manifest['acceptedEulaSha256'] = materialize_consent(os.environ.get('ONLYDRAGONS_PAPER_EULA_BASE64'), eula)
        source = clone_exact(checkout, project, args.revision)
        manifest['sourceInputSha256'] = source['sourceInputSha256']
        pins = paper_test.properties(project / 'versions.properties')
        java_home = Path(os.environ.get('JAVA_HOME', '')).resolve()
        require((java_home / 'bin/java').is_file(), 'CI must supply the pinned JDK through JAVA_HOME')
        actual_java = paper_test.properties(java_home / 'release').get('JAVA_VERSION', '').strip('"')
        require(actual_java == java_version(project), 'Hosted JDK does not match the exact existing Linux pin')
        manifest['javaVersion'] = actual_java
        paper = download_verified(pins['paperUrl'], root / 'paper.jar', pins['paperSha256'])
        metadata = paper_bootstrap.inspect_launcher(paper, pins['paperSha256'])
        mojang = download_verified(metadata['mojangUrl'], root / metadata['mojangFileName'], metadata['mojangSha256'])
        lease = root / 'test-coordination'
        lease.mkdir(mode=0o700)
        # The existing suite owns fresh builds, server/client processes, memory and its shared lease.
        command = [sys.executable, str(project / 'scripts/agent-tests/paper_suite.py'), '--project', str(project),
                   '--suite', 'all', '--eula-file', str(eula), '--lease-directory', str(lease),
                   '--java-home', str(java_home), '--paper-jar', str(paper), '--mojang-jar', str(mojang)]
        manifest['suiteExitCode'] = paper_suite.run_child(command, project, output / 'suite.log')
        receipts = list((project / 'build/reports/agent-paper-suites').glob('*/receipt.json'))
        require(len(receipts) == 1, 'Expected exactly one fresh full-suite receipt')
        receipt = receipts[0]
        manifest['receiptPath'] = receipt.relative_to(project).as_posix()
        require(manifest['suiteExitCode'] == 0, 'Full Paper suite failed; inspect the original receipt and logs')
        replay = [sys.executable, str(project / 'scripts/agent-tests/paper_suite.py'), '--project', str(project),
                  '--validate', str(receipt)]
        manifest['replayExitCode'] = paper_suite.run_child(replay, project, output / 'replay.log')
        require(manifest['replayExitCode'] == 0, 'Independent raw-evidence replay failed')
        require(paper_suite.source_identity(project) == source, 'Source changed during hosted validation')
        manifest['passed'] = True
        exit_code = 0
    except (Exception, KeyboardInterrupt) as error:
        manifest['error'] = str(error) or type(error).__name__
    finally:
        try:
            export_evidence(project if owned_root else output / 'absent-checkout', output, manifest)
        except (Exception, KeyboardInterrupt) as error:
            manifest.update(passed=False, error='Evidence export failed: ' + (str(error) or type(error).__name__))
            paper_test.atomic_json(output / 'manifest.json', manifest)
            exit_code = 1
    print(('PASS' if manifest['passed'] else 'FAIL') + ': ' + str(output / 'manifest.json'), flush=True)
    if manifest.get('error'):
        print(manifest['error'], file=sys.stderr, flush=True)
    return exit_code


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    version = commands.add_parser('java-version')
    version.add_argument('--checkout', type=Path, default=Path.cwd())
    run = commands.add_parser('run')
    run.add_argument('--checkout', type=Path, required=True)
    run.add_argument('--revision', required=True)
    run.add_argument('--run-id', required=True)
    run.add_argument('--attempt', required=True)
    run.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(argv)
    def interrupted(signum, frame):
        raise KeyboardInterrupt('Interrupted by signal ' + str(signum))
    signal.signal(signal.SIGTERM, interrupted)
    try:
        if args.command == 'java-version':
            print(java_version(args.checkout))
            return 0
        return execute(args)
    except (Exception, KeyboardInterrupt) as error:
        print('FAIL: ' + (str(error) or type(error).__name__), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
