#!/usr/bin/env python3
"""Check this worker's fixture access without building or starting Minecraft."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

import paper_test as runner


def inspect(project, java_home, eula_file, lease_directory, paper_jar=None, memory_mib=1536):
    checks, errors, waiting = {}, [], []

    def check(name, action):
        try:
            checks[name] = action()
        except runner.ResourceBusy as error:
            checks[name] = 'waiting'
            waiting.append(str(error))
        except (OSError, ValueError, ImportError, subprocess.SubprocessError, runner.ValidationError) as error:
            checks[name] = 'unavailable'
            errors.append(f'{name}: {error}')

    def files():
        required = ['AGENTS.md', 'versions.properties', 'gradlew',
                    'docs/planning/01-research.md', 'docs/planning/02-foundation-plan.md',
                    'docs/planning/03-agent-tasks-and-validation.md',
                    'dev/game-tests/scenarios.json', 'dev/game-tests/suites.json',
                    'dev/game-tests/acceptance.json', 'dev/player-client/build.gradle.kts',
                    'scripts/agent-tests/paper_test.py', 'scripts/agent-tests/paper_suite.py',
                    'scripts/agent-tests/checkpoint.py']
        required += [f'.agents/skills/{name}/SKILL.md' for name in (
            'minecraft-plugin-development', 'paper-runtime-validation', 'paper-threading-review')]
        for name in required:
            runner.require((project / name).is_file(), 'Missing shared fixture/context: ' + name)
            with (project / name).open('rb') as stream:
                runner.require(bool(stream.read(1)), 'Empty shared fixture/context: ' + name)
        return {'readableFiles': len(required)}

    def java():
        runner.require(java_home is not None, 'JAVA_HOME is not provisioned')
        pins = runner.properties(project / 'versions.properties')
        release = runner.properties(java_home / 'release')
        runner.require(re.match(r'"?' + re.escape(pins['javaVersion']) + r'(?:\.|\")',
                                release.get('JAVA_VERSION', '')), 'JDK does not match the project pin')
        result = subprocess.run([str(java_home / 'bin/java'), '-version'], capture_output=True,
                                text=True, timeout=15, check=True)
        return (result.stderr or result.stdout).splitlines()[0]

    def lease():
        runner.require(lease_directory is not None and lease_directory.is_dir(),
                       'ONLYDRAGONS_TEST_COORDINATION is not provisioned')
        # A unique owned file proves the actual sandbox can write here. Never remove the shared lock.
        with tempfile.TemporaryFile(dir=lease_directory) as probe:
            probe.write(b'OnlyDragons fixture access probe\n')
            probe.flush()
        with runner.server_lease(lease_directory.resolve(), 0):
            return 'writable; lease available'

    def paper():
        if paper_jar is None:
            return 'runner will download and verify the pinned artifact'
        pins = runner.properties(project / 'versions.properties')
        runner.require(runner.sha256(paper_jar) == pins['paperSha256'], 'Cached Paper hash does not match pin')
        return 'readable; SHA256 matches pin'

    check('platform', lambda: runner.require(sys.platform == 'linux', 'Run doctor in Linux/WSL') or 'Linux/WSL')
    check('sharedContextAndFixtures', files)
    check('java', java)
    check('eula', lambda: runner.accepted_eula(eula_file) or 'existing acceptance readable')
    check('sharedLease', lease)
    check('paper', paper)
    if sys.platform == 'linux':
        check('memoryWithProtocolPlayer', lambda: runner.available_memory(memory_mib, 256))
    return {'schemaVersion': 1, 'kind': 'fixture-access-check',
            'state': 'unavailable' if errors else ('waiting' if waiting else 'ready'),
            'checks': checks, 'errors': errors, 'waiting': waiting,
            'scope': 'Preflight only; no build, Paper scenario or acceptance evidence.'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=Path.cwd())
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--eula-file', type=Path, default=os.environ.get('ONLYDRAGONS_EULA_FILE'))
    parser.add_argument('--lease-directory', type=Path, default=os.environ.get('ONLYDRAGONS_TEST_COORDINATION'))
    parser.add_argument('--paper-jar', type=Path, default=os.environ.get('ONLYDRAGONS_TEST_PAPER_JAR'))
    args = parser.parse_args()
    result = inspect(args.project.resolve(), args.java_home, args.eula_file, args.lease_directory, args.paper_jar)
    print(json.dumps(result, indent=2))
    return {'ready': 0, 'waiting': 75, 'unavailable': 1}[result['state']]


if __name__ == '__main__':
    sys.exit(main())
