#!/usr/bin/env python3
"""Isolated Linux real-Paper tests. Requires existing operator EULA consent and a shared lease."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import ctypes
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import struct
import subprocess
import sys
import time
import urllib.request
import uuid
import xml.etree.ElementTree as ET


class ValidationError(RuntimeError):
    pass


class ResourceBusy(ValidationError):
    pass


def require(condition, message):
    if not condition:
        raise ValidationError(message)


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def atomic_json(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    temporary.replace(path)


def strict_json(path):
    def finite_number(value):
        number = float(value)
        require(math.isfinite(number), f'Non-finite JSON number: {value}')
        return number
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, f'Duplicate JSON key: {key}')
            result[key] = value
        return result
    require(path.is_file(), f'Missing scenario report: {path}')
    require(path.stat().st_size <= 1024 * 1024, 'Scenario report exceeds 1 MiB')
    try:
        return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique, parse_float=finite_number,
                          parse_constant=lambda value: (_ for _ in ()).throw(ValidationError(f'Non-finite JSON: {value}')))
    except (ValueError, UnicodeError) as error:
        raise ValidationError(f'Malformed scenario report: {error}') from error


def validate_report(path, expected, issued_ms, deadline_ms, now_ms=None):
    """Reject missing, stale, truncated, failed, or incomplete evidence, even after a clean boot."""
    report = strict_json(Path(path))
    require(isinstance(report, dict), 'Scenario report must be an object')
    for key in ('runId', 'scenarioId', 'mechanicRevision'):
        require(report.get(key) == expected[key], f'Wrong/stale {key}')
    require(type(report.get('schemaVersion')) is int and report['schemaVersion'] == 1, 'Unsupported scenario schema')
    require(report.get('state') == 'complete', 'Scenario is incomplete')
    require(report.get('syntheticActors') is True, 'Synthetic actor disclosure is missing')
    start, end = report.get('startedAtEpochMs'), report.get('completedAtEpochMs')
    require(type(start) is int and type(end) is int, 'Scenario timestamps are missing')
    require(issued_ms <= start <= end <= deadline_ms, 'Stale or timed-out scenario timestamps')
    require(end <= (now_ms if now_ms is not None else int(time.time() * 1000)) + 1000, 'Scenario timestamp is in the future')
    require(Path(path).stat().st_mtime_ns // 1_000_000 >= issued_ms - 1000, 'Stale scenario file timestamp')
    server = report.get('server', {})
    require(isinstance(server, dict) and server.get('minecraftVersion') == expected['minecraftVersion'], 'Wrong Minecraft version')
    require(isinstance(server.get('paperVersion'), str) and server['paperVersion'], 'Actual Paper version is missing')
    require(re.search(r'(?:^|[- ])' + re.escape(expected['paperBuild']) + r'(?:[- ]|$)', server['paperVersion']), 'Wrong Paper build')
    assertions = report.get('assertions')
    require(isinstance(assertions, list) and assertions, 'No scenario assertions')
    seen = set()
    failures = []
    for assertion in assertions:
        require(isinstance(assertion, dict), 'Invalid assertion object')
        name = assertion.get('id')
        require(isinstance(name, str) and name and name not in seen, 'Missing or duplicate assertion ID')
        seen.add(name)
        require('expected' in assertion and 'observed' in assertion, f'Assertion has no expected/observed values: {name}')
        if assertion.get('passed') is not True or assertion['expected'] != assertion['observed']:
            failures.append(name)
    require(set(expected['requiredAssertions']).issubset(seen), 'Required scenario assertions are missing')
    require(not failures, 'Failed scenario assertions: ' + ', '.join(failures))
    require(report.get('passed') is True, 'Scenario reports failure')
    return report


def wait_for_report(path, expected, issued_ms, timeout, process):
    deadline = time.monotonic() + timeout
    deadline_ms = issued_ms + int(timeout * 1000)
    while not path.exists():
        require(process.poll() is None, 'Paper exited before writing a scenario report')
        require(time.monotonic() < deadline, 'Timed out waiting for a scenario report')
        time.sleep(0.1)
    require(time.monotonic() <= deadline, 'Scenario report arrived after its deadline')
    return validate_report(path, expected, issued_ms, deadline_ms)


def properties(path):
    result = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            result[key.strip()] = value.strip()
    return result


def accepted_eula(path):
    require(path is not None and path.is_file(), 'Supply an existing approved EULA file with --eula-file or ONLYDRAGONS_EULA_FILE')
    require(properties(path).get('eula') == 'true', 'Operator EULA acceptance is absent; this runner never accepts it')


@contextmanager
def server_lease(directory, timeout):
    import fcntl
    require(directory.is_dir(), 'The operator must provision the shared lease directory')
    descriptor = os.open(directory / 'paper-tests.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    acquired = False
    try:
        deadline = time.monotonic() + timeout
        while True:
            try:
                fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
                acquired = True
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise ResourceBusy('Busy: timed out waiting for the shared Paper test lease')
                time.sleep(0.25)
        yield
    finally:
        if acquired:
            fcntl.flock(descriptor, fcntl.LOCK_UN)
        os.close(descriptor)


def assess_memory(memory_mib, linux, windows_available=None):
    """Account for already resident, reclaimable WSL cache without assuming it is all reusable."""
    required = memory_mib + 1024
    result = {'linuxMemoryMiB': linux, 'requiredMiB': required}
    enough = linux['MemAvailable'] >= required
    if windows_available is not None:
        reclaimable = min(max(0, linux['MemAvailable'] - linux['MemFree']),
                          max(0, linux['Buffers'] + linux['Cached'] + linux['SReclaimable'] - linux['Shmem']))
        allowance = reclaimable // 2
        result.update({'windowsAvailableMiB': windows_available, 'combinedReserveMiB': 1024,
                       'wslReclaimableEstimateMiB': reclaimable, 'wslDiscountedAllowanceMiB': allowance,
                       'effectiveHostAvailableMiB': windows_available + allowance})
        enough = enough and windows_available + allowance >= required
    if not enough:
        raise ResourceBusy('Busy: not enough available memory for isolated Paper: ' + json.dumps(result))
    return result


def windows_available_memory():
    powershell = shutil.which('powershell.exe') or '/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe'
    command = '[int64]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1024)'
    try:
        probe = subprocess.run([powershell, '-NoProfile', '-NonInteractive', '-Command', command], capture_output=True, text=True, check=True, timeout=15)
        return int(probe.stdout.strip())
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        raise ValidationError('Cannot verify Windows host memory before starting WSL Paper') from error


def available_memory(memory_mib):
    raw = dict(re.findall(r'^(\w+):\s+(\d+)\s+kB', Path('/proc/meminfo').read_text(), re.M))
    linux = {key: int(raw[key]) // 1024 for key in ('MemAvailable', 'MemFree', 'Buffers', 'Cached', 'SReclaimable', 'Shmem')}
    is_wsl = 'microsoft' in Path('/proc/sys/kernel/osrelease').read_text().lower()
    return assess_memory(memory_mib, linux, windows_available_memory() if is_wsl else None)


def wait_for_memory(memory_mib, timeout):
    deadline = time.monotonic() + timeout
    announced = 0.0
    while True:
        try:
            return available_memory(memory_mib)
        except ResourceBusy as busy:
            if time.monotonic() >= deadline:
                raise ResourceBusy(str(busy) + '; resource wait expired') from busy
            if time.monotonic() >= announced:
                print(str(busy) + '; waiting', flush=True)
                announced = time.monotonic() + 30
            time.sleep(1)


def varint(value):
    output = bytearray()
    while True:
        byte = value & 127
        value >>= 7
        output.append(byte | (128 if value else 0))
        if not value:
            return bytes(output)


def read_varint(stream):
    value = 0
    for index in range(5):
        raw = stream.read(1)
        require(raw, 'Status stream ended unexpectedly')
        value |= (raw[0] & 127) << (7 * index)
        if not raw[0] & 128:
            return value
    raise ValidationError('Invalid status VarInt')


def query_status(port):
    with socket.create_connection(('127.0.0.1', port), timeout=5) as connection:
        connection.settimeout(5)
        host = b'127.0.0.1'
        handshake = varint(0) + varint(47) + varint(len(host)) + host + struct.pack('>H', port) + varint(1)
        connection.sendall(varint(len(handshake)) + handshake + b'\x01\x00')
        with connection.makefile('rb') as stream:
            size = read_varint(stream)
            require(2 <= size <= 1048576 and read_varint(stream) == 0, 'Invalid status packet')
            length = read_varint(stream)
            require(1 <= length < size, 'Invalid status JSON length')
            payload = stream.read(length)
            require(len(payload) == length, 'Incomplete status packet')
            return json.loads(payload)


def free_port():
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


class OwnedServer:
    """Controls only the child it created; never searches for or stops other JVMs."""
    def __init__(self, command, directory, log_path):
        self.log_path = log_path
        self.log_file = log_path.open('wb')
        self.process = None
        self.shutdown = None
        try:
            parent_pid = os.getpid()
            def death_signal():
                if ctypes.CDLL(None).prctl(1, signal.SIGTERM) != 0:
                    os._exit(1)
                signal.pthread_sigmask(signal.SIG_SETMASK, set())
                if os.getppid() != parent_pid:
                    os._exit(1)
            self.process = subprocess.Popen(command, cwd=directory, stdin=subprocess.PIPE,
                                            stdout=self.log_file, stderr=subprocess.STDOUT,
                                            start_new_session=True, preexec_fn=death_signal)
        except BaseException:
            self.log_file.close()
            raise

    def text(self):
        return re.sub(r'\x1b\[[0-9;]*[A-Za-z]', '', self.log_path.read_text(encoding='utf-8', errors='replace'))

    def send(self, command):
        require(command and not re.search(r'[\r\n]', command), 'Expected one console command')
        require(self.process.poll() is None, 'Paper is no longer running')
        self.process.stdin.write((command + '\n').encode())
        self.process.stdin.flush()

    def wait_text(self, pattern, timeout, offset=0):
        deadline = time.monotonic() + timeout
        while True:
            if re.search(pattern, self.text()[offset:]):
                return
            require(self.process.poll() is None, f'Paper exited while waiting for {pattern}')
            require(time.monotonic() < deadline, f'Timed out waiting for {pattern}')
            time.sleep(0.1)

    def stop(self, timeout=45):
        if self.shutdown is not None:
            return self.shutdown
        forced = False
        try:
            if self.process.poll() is None:
                try:
                    self.send('stop')
                except (BrokenPipeError, OSError):
                    pass
                try:
                    self.process.wait(timeout=timeout)
                except subprocess.TimeoutExpired:
                    forced = True
                    os.killpg(self.process.pid, signal.SIGTERM)
                    try:
                        self.process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(self.process.pid, signal.SIGKILL)
                        self.process.wait(timeout=10)
        finally:
            self.log_file.close()
            if self.process.stdin:
                self.process.stdin.close()
        self.shutdown = {'exitCode': self.process.returncode, 'forced': forced,
                         'clean': self.process.returncode == 0 and not forced and 'Stopping server' in self.text()}
        return self.shutdown


def fetch_paper(project, pins, supplied):
    expected = pins['paperSha256']
    require(re.fullmatch(r'[a-f0-9]{64}', expected), 'Invalid pinned Paper SHA256')
    if supplied:
        require(supplied.is_file() and sha256(supplied) == expected, 'Supplied Paper JAR differs from the project pin')
        return supplied
    cache = project / 'run/agent-cache'
    cache.mkdir(parents=True, exist_ok=True)
    artifact = cache / f'paper-{expected}.jar'
    if not artifact.is_file() or sha256(artifact) != expected:
        require(pins['paperUrl'].startswith('https://'), 'Paper download must use HTTPS')
        request = urllib.request.Request(pins['paperUrl'], headers={'User-Agent': 'OnlyDragons/agent-integration-tests (https://github.com/Kav-K/OnlyDragons)'})
        temporary = artifact.with_suffix('.download')
        with urllib.request.urlopen(request, timeout=90) as response, temporary.open('wb') as destination:
            shutil.copyfileobj(response, destination)
        require(sha256(temporary) == expected, 'Paper download checksum mismatch')
        temporary.replace(artifact)
    return artifact


def build_artifacts(project, java_home, report_root):
    env = dict(os.environ, JAVA_HOME=str(java_home), GRADLE_USER_HOME=str(project / '.gradle/agent-home'))
    env['PATH'] = str(java_home / 'bin') + os.pathsep + env.get('PATH', '')
    with (report_root / 'build.log').open('w', encoding='utf-8') as log:
        for args in (['build'], ['-p', 'dev/game-tests', 'build']):
            result = subprocess.run(['bash', str(project / 'gradlew'), *args, '--console=plain'], cwd=project, env=env,
                                    stdout=log, stderr=subprocess.STDOUT, timeout=900)
            require(result.returncode == 0, 'Gradle failed; see build.log')
    receipt = project / 'build/plugin-artifact.txt'
    require(receipt.is_file(), 'Production artifact receipt is missing')
    main = Path(receipt.read_text().strip()).resolve()
    require(main.is_relative_to(project / 'build') and main.is_file(), 'Production artifact is missing or outside this checkout build directory')
    companion = project / 'dev/game-tests/build/libs/OnlyDragonsGameTests.jar'
    require(companion.is_file(), 'Game-test companion is missing')
    tests = list((project / 'build/test-results/test').glob('TEST-*.xml'))
    require(tests, 'Production JUnit evidence is missing')
    counts = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
    for file in tests:
        suite = ET.parse(file).getroot()
        for key in counts:
            counts[key] += int(suite.attrib.get(key, '0'))
    require(counts['tests'] > 0 and not any(counts[key] for key in ('failures', 'errors', 'skipped')), 'Production tests failed, aborted, or skipped')
    return main, companion, {'wrapperInvoked': True, 'unitTests': counts}


def stage_artifact(source, destination, expected_sha256):
    shutil.copyfile(source, destination)
    require(sha256(destination) == expected_sha256, 'Artifact changed after build validation: ' + destination.name)


def execute(args):
    require(sys.platform == 'linux', 'Use this runner on Linux/WSL; Windows human play keeps using mcdev.cmd')
    project = args.project.resolve()
    require((project / 'versions.properties').is_file() and (project / 'AGENTS.md').is_file(), 'Expected an OnlyDragons checkout')
    run_id = uuid.uuid4().hex
    report_root = project / 'build/reports/agent-paper' / run_id
    report_root.mkdir(parents=True)
    outcome = {'schemaVersion': 1, 'runId': run_id, 'scenarioId': args.scenario, 'passed': False,
               'startedAtEpochMs': int(time.time() * 1000), 'error': None, 'cleanup': None}
    server = None
    try:
        accepted_eula(args.eula_file)
        require(args.lease_directory is not None, 'Supply the operator-provisioned shared lease directory')
        require(args.java_home is not None, 'Supply JDK via --java-home or JAVA_HOME')
        java_home = args.java_home.resolve()
        pins = properties(project / 'versions.properties')
        release = properties(java_home / 'release')
        require(re.match(r'"?' + re.escape(pins['javaVersion']) + r'(?:\.|\")', release.get('JAVA_VERSION', '')), 'JDK major does not match versions.properties')
        plans = strict_json(project / 'dev/game-tests/scenarios.json')
        require(args.scenario in plans, 'Unknown scenario; register it in dev/game-tests/scenarios.json')
        scenario = plans[args.scenario]
        outcome.update({'revision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=project, text=True).strip(),
                        'worktreeDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=project, text=True).strip()),
                        'pins': pins, 'javaVersion': release['JAVA_VERSION'].strip('"')})
        print(f'RUN {run_id}: {args.scenario}; reports: {report_root}', flush=True)
        # Build before acquiring the scarce server slot. Caches remain in this issue checkout.
        main, companion, build = build_artifacts(project, java_home, report_root)
        outcome['build'] = build
        outcome['artifacts'] = {'productionSha256': sha256(main), 'gameTestsSha256': sha256(companion)}
        paper = fetch_paper(project, pins, args.paper_jar)
        with server_lease(args.lease_directory.resolve(), args.lease_timeout):
            outcome['memory'] = wait_for_memory(args.memory_mib, args.resource_timeout)
            directory = project / 'run/agent-tests' / run_id
            directory.mkdir(parents=True, exist_ok=False)
            plugins = directory / 'plugins'
            plugins.mkdir()
            stage_artifact(main, plugins / 'OnlyDragons.jar', outcome['artifacts']['productionSha256'])
            stage_artifact(companion, plugins / 'OnlyDragonsGameTests.jar', outcome['artifacts']['gameTestsSha256'])
            stage_artifact(paper, directory / 'server.jar', pins['paperSha256'])
            shutil.copyfile(args.eula_file, directory / 'eula.txt')
            port = free_port()
            settings = properties(project / 'dev/server.properties')
            settings.update({'server-ip': '127.0.0.1', 'server-port': str(port), 'online-mode': 'true',
                             'enable-rcon': 'false', 'enable-query': 'false', 'enable-jmx-monitoring': 'false',
                             'level-name': 'agent-world-' + run_id, 'pause-when-empty-seconds': '-1'})
            (directory / 'server.properties').write_text(''.join(f'{key}={value}\n' for key, value in settings.items()), encoding='utf-8')
            outcome['profile'] = {'directory': str(directory), 'port': port, 'world': settings['level-name']}
            command = [str(java_home / 'bin/java'), f'-Xmx{args.memory_mib}m', '-Xms256m', '-XX:ActiveProcessorCount=2',
                       '-Dfile.encoding=UTF-8', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                       f'-Donlydragons.test.runId={run_id}', '-jar', 'server.jar', '--nogui']
            try:
                print(f'Starting isolated Paper at 127.0.0.1:{port}', flush=True)
                blocked = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
                try:
                    server = OwnedServer(command, directory, report_root / 'server.log')
                finally:
                    signal.pthread_sigmask(signal.SIG_SETMASK, blocked)
                server.wait_text(r'Done \([0-9.,]+s\)!', args.startup_timeout)
                status = query_status(port)
                require(pins['minecraftVersion'] in status['version']['name'], 'Status protocol returned the wrong Minecraft version')
                outcome['status'] = status
                issued_ms = int(time.time() * 1000)
                server.send(f'odgametest {run_id} {args.scenario}')
                expected = dict(scenario, runId=run_id, scenarioId=args.scenario,
                                minecraftVersion=pins['minecraftVersion'], paperBuild=pins['paperBuild'])
                scenario_path = plugins / 'OnlyDragonsGameTests/report.json'
                outcome['scenario'] = wait_for_report(scenario_path, expected, issued_ms, args.scenario_timeout, server.process)
            finally:
                if server is not None:
                    # Ignore a repeated termination while saving this runner's own disposable world.
                    previous = {item: signal.signal(item, signal.SIG_IGN) for item in (signal.SIGTERM, signal.SIGINT)}
                    try:
                        outcome['cleanup'] = server.stop()
                    finally:
                        for item, handler in previous.items():
                            signal.signal(item, handler)
                raw_report = plugins / 'OnlyDragonsGameTests/report.json'
                if raw_report.is_file():
                    shutil.copyfile(raw_report, report_root / 'scenario.json')
            require(outcome['cleanup'] and outcome['cleanup']['clean'], 'Paper did not stop cleanly')
            errors = [line for line in server.text().splitlines() if re.search(r'(?:/ERROR\]|\bSEVERE\]|OD_GAME_TEST_REPORT_ERROR|Error occurred while (?:enabling|disabling)|Could not load|Failed to start the minecraft server)', line)]
            require(not errors, 'Paper logged errors: ' + '\n'.join(errors[:10]))
            outcome['passed'] = True
    except (Exception, KeyboardInterrupt) as error:
        outcome['error'] = str(error) or type(error).__name__
        outcome['busy'] = isinstance(error, ResourceBusy)
        print('FAIL: ' + outcome['error'], file=sys.stderr, flush=True)
    finally:
        outcome['completedAtEpochMs'] = int(time.time() * 1000)
        atomic_json(report_root / 'result.json', outcome)
        print(f"{'PASS' if outcome['passed'] else 'FAIL'}: {report_root / 'result.json'}", flush=True)
    return 0 if outcome['passed'] else (75 if outcome.get('busy') else 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=Path.cwd())
    parser.add_argument('--scenario', default='lifecycle-calibration')
    parser.add_argument('--eula-file', type=Path, default=os.environ.get('ONLYDRAGONS_EULA_FILE'))
    parser.add_argument('--lease-directory', type=Path, default=os.environ.get('ONLYDRAGONS_TEST_COORDINATION'))
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--paper-jar', type=Path, default=os.environ.get('ONLYDRAGONS_TEST_PAPER_JAR'))
    def bounded(minimum, maximum):
        def parse(value):
            number = int(value)
            if not minimum <= number <= maximum:
                raise argparse.ArgumentTypeError(f'Expected {minimum}..{maximum}')
            return number
        return parse
    parser.add_argument('--memory-mib', type=bounded(1024, 2048), default=1536)
    parser.add_argument('--startup-timeout', type=bounded(10, 600), default=240)
    parser.add_argument('--scenario-timeout', type=bounded(1, 300), default=60)
    parser.add_argument('--lease-timeout', type=bounded(1, 1800), default=600)
    parser.add_argument('--resource-timeout', type=bounded(1, 1800), default=600)
    args = parser.parse_args()
    def interrupted(signum, frame):
        raise KeyboardInterrupt(f'Interrupted by signal {signum}')
    signal.signal(signal.SIGTERM, interrupted)
    return execute(args)


if __name__ == '__main__':
    sys.exit(main())
