#!/usr/bin/env python3
"""Run one catalog-bound real-Paper scenario in an isolated Linux/WSL profile.

The lifecycle is build -> verify bootstrap -> acquire shared lease -> admit combined
JVM memory -> stage a fresh profile -> boot and observe -> stop owned processes ->
write result.json. Only explicit catalog-matching protocol modes disable online
authentication, and only in the disposable loopback profile. No EULA is accepted.

Scenario success, client success, clean shutdown and absence of Paper errors are
separate requirements. A boot or a submitted player packet alone proves neither
damage registration nor feature acceptance. See paper_suite for cohort replay and
checkpoint for requirement mapping. CLI exit 75 means retryable resource contention;
exit 1 means validation/execution failure, and exit 0 means this scenario passed.
"""
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
# Support the repository's isolated importlib lifecycle tests as well as CLI use.
if str(Path(__file__).resolve().parent) not in sys.path:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
import paper_bootstrap
import player_actions


class ValidationError(RuntimeError):
    """An input, artifact or observed result violates the runner's required contract."""
    pass


class ResourceBusy(ValidationError):
    """Admission could not acquire a lease or verify sufficient memory before its deadline.

    This is a retryable scheduling outcome, never evidence that a scenario passed.
    """
    pass


def require(condition, message):
    """Raise ValidationError with the supplied diagnostic when a required condition fails."""
    if not condition:
        raise ValidationError(message)


def paper_errors(log):
    """Paper console and file appenders use different prefixes; both must fail closed."""
    return [line for line in log.splitlines() if re.search(
        r'(?:\bERROR\]|\bSEVERE\]|OD_GAME_TEST_REPORT_ERROR|Error occurred while (?:enabling|disabling)|Could not load|Failed to start the minecraft server)', line)]


def sha256(path):
    """Return a lowercase SHA-256 hex digest of a file using bounded one-MiB reads.

    The file is read now; a previously reported digest is not substituted. I/O errors
    propagate so callers cannot certify missing evidence.
    """
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def atomic_json(path, value):
    """Replace a JSON record through its sibling .tmp file after finite-value serialization.

    The caller owns the directory and path. This prevents readers seeing partial JSON;
    it is not a durable fsync transaction or a lock between concurrent writers.
    """
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    temporary.replace(path)


MAX_JSON_BYTES = 1024 * 1024


def strict_json(path, *, max_bytes=MAX_JSON_BYTES, artifact='scenario report'):
    """Read a bounded UTF-8 JSON artifact, rejecting duplicate keys and non-finite numbers.

    path must be a file no larger than max_bytes (default one MiB). artifact names the
    diagnostic context. Returns decoded data without imposing a feature schema; callers
    must validate types, identity, timestamps and required fields separately. Missing or
    malformed artifacts raise ValidationError; filesystem failures may propagate.
    """
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
    require(path.is_file(), f'Missing {artifact}: {path}')
    require(path.stat().st_size <= max_bytes,
            f'{artifact.capitalize()} exceeds {max_bytes / (1024 * 1024):g} MiB: {path}')
    try:
        return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique, parse_float=finite_number,
                          parse_constant=lambda value: (_ for _ in ()).throw(ValidationError(f'Non-finite JSON: {value}')))
    except (ValueError, UnicodeError) as error:
        raise ValidationError(f'Malformed {artifact}: {path}: {error}') from error


def json_values_equal(expected, observed):
    """Compare JSON values recursively; numbers may agree across int/float, booleans may not."""
    if type(expected) is not type(observed):
        return type(expected) in (int, float) and type(observed) in (int, float) and expected == observed
    if isinstance(expected, dict):
        return expected.keys() == observed.keys() and all(
            json_values_equal(value, observed[key]) for key, value in expected.items())
    if isinstance(expected, list):
        return len(expected) == len(observed) and all(
            json_values_equal(left, right) for left, right in zip(expected, observed))
    return expected == observed


def validate_report(path, expected, issued_ms, deadline_ms, now_ms=None):
    """Validate a completed companion report against one issued scenario invocation.

    issued_ms/deadline_ms and optional now_ms are epoch milliseconds; the latter is
    injectable for deterministic validation tests. The file's modification time,
    reported execution window, run/scenario/mechanic identity and actual Paper pin
    must agree. Each unique assertion must be true and its expected/observed JSON must
    match; all catalog-required IDs must exist. Returns the decoded report or raises
    ValidationError. Intentional failure controls are interpreted by paper_suite, not
    made successful here.
    """
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
        if assertion.get('passed') is not True or not json_values_equal(assertion['expected'], assertion['observed']):
            failures.append(name)
    require(set(expected['requiredAssertions']).issubset(seen), 'Required scenario assertions are missing')
    require(not failures, 'Failed scenario assertions: ' + ', '.join(failures))
    require(report.get('passed') is True, 'Scenario reports failure')
    return report


def wait_for_report(path, expected, issued_ms, timeout, process, client_process=None):
    """Wait at most timeout seconds for a fresh companion file, then validate it.

    Paper must remain alive while waiting; an optional client may remain running or
    exit zero, but a failed client aborts the wait. Monotonic time bounds waiting and
    epoch milliseconds bind evidence. An existing invalid file fails immediately; it
    is not retried until a different report happens to appear.
    """
    deadline = time.monotonic() + timeout
    deadline_ms = issued_ms + int(timeout * 1000)
    while not path.exists():
        require(process.poll() is None, 'Paper exited before writing a scenario report')
        if client_process is not None:
            require(client_process.poll() in (None, 0), 'Protocol player exited unsuccessfully before scenario completion')
        require(time.monotonic() < deadline, 'Timed out waiting for a scenario report')
        time.sleep(0.1)
    require(time.monotonic() <= deadline, 'Scenario report arrived after its deadline')
    return validate_report(path, expected, issued_ms, deadline_ms)


def properties(path):
    """Read the repository's simple UTF-8 key=value format into a last-value-wins mapping.

    This helper trims keys/values and ignores blank/comment lines; it is not the Java
    Properties escape/continuation grammar or a strict duplicate-key validator.
    """
    result = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            result[key.strip()] = value.strip()
    return result


def accepted_eula(path):
    """Require an existing operator-provided file containing eula=true; never create consent."""
    require(path is not None and path.is_file(), 'Supply an existing approved EULA file with --eula-file or ONLYDRAGONS_EULA_FILE')
    require(properties(path).get('eula') == 'true', 'Operator EULA acceptance is absent; this runner never accepts it')


@contextmanager
def server_lease(directory, timeout, evidence=None):
    """Hold the operator's Linux flock across both owned JVMs and their cleanup.

    directory must already exist. Acquisition polls nonblocking until timeout seconds,
    then raises ResourceBusy. Optional evidence receives epoch-millisecond acquire and
    release times. The lock file rejects a symlink; descriptor release runs even when
    the scenario or shutdown raises. This serializes cooperating test runners only.
    """
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
                if evidence is not None:
                    evidence.update(directory=str(directory), acquiredAtEpochMs=int(time.time() * 1000))
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise ResourceBusy('Busy: timed out waiting for the shared Paper test lease')
                time.sleep(0.25)
        yield
    finally:
        if acquired:
            fcntl.flock(descriptor, fcntl.LOCK_UN)
            if evidence is not None:
                evidence['releasedAtEpochMs'] = int(time.time() * 1000)
        os.close(descriptor)


def assess_memory(memory_mib, linux, windows_available=None, client_memory_mib=0):
    """Validate memory admission for Paper heap, optional client heap and a 1024-MiB reserve.

    All inputs/returned counts are MiB. Linux availability must cover the total. On WSL,
    Windows availability plus half of bounded reclaimable Linux cache must also cover
    it; resident caches are not counted as fully reusable. Returns the recorded
    calculation or raises ResourceBusy. This is a prelaunch observation, not a memory
    reservation enforced by the operating system.
    """
    required = memory_mib + client_memory_mib + 1024
    result = {'linuxMemoryMiB': linux, 'requiredMiB': required, 'paperHeapMiB': memory_mib,
              'playerClientHeapMiB': client_memory_mib, 'combinedReserveMiB': 1024}
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


def windows_available_memory(timeout=15):
    """Read Windows host free memory through a bounded, read-only PowerShell probe.

    Returns nonnegative MiB. timeout is seconds. Missing, malformed, failed or late
    probe output raises ResourceBusy rather than assuming enough memory or zero usage.
    """
    powershell = shutil.which('powershell.exe') or '/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe'
    command = ("$ErrorActionPreference='Stop'; "
               "$memoryProbe = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop; "
               "if ($null -eq $memoryProbe.FreePhysicalMemory) { throw 'Missing FreePhysicalMemory' }; "
               '[int64]($memoryProbe.FreePhysicalMemory / 1024)')
    try:
        probe = subprocess.run([powershell, '-NoProfile', '-NonInteractive', '-Command', command], capture_output=True, text=True, check=True, timeout=timeout)
        output = probe.stdout.strip()
        if not re.fullmatch(r'[0-9]+', output):
            raise ValueError('invalid nonnegative MiB response: ' + repr(output[:200]))
        return int(output)
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        if isinstance(error, subprocess.TimeoutExpired):
            detail = f'probe exceeded {timeout:g}s'
        elif isinstance(error, subprocess.CalledProcessError):
            detail = f'probe exit {error.returncode}: ' + str(error.stderr or '').strip()[:300]
        else:
            detail = type(error).__name__ + ': ' + str(error)[:300]
        raise ResourceBusy('Busy: Cannot verify Windows host memory before starting WSL Paper: ' + detail) from error


def available_memory(memory_mib, client_memory_mib=0, probe_timeout=15):
    """Sample Linux memory and, under WSL, Windows memory before calling assess_memory.

    Heap arguments are MiB and probe_timeout is seconds. No process is stopped to
    create headroom; unverifiable host availability remains a ResourceBusy result.
    """
    is_wsl = 'microsoft' in Path('/proc/sys/kernel/osrelease').read_text().lower()
    windows = windows_available_memory(probe_timeout) if is_wsl else None
    raw = dict(re.findall(r'^(\w+):\s+(\d+)\s+kB', Path('/proc/meminfo').read_text(), re.M))
    linux = {key: int(raw[key]) // 1024 for key in ('MemAvailable', 'MemFree', 'Buffers', 'Cached', 'SReclaimable', 'Shmem')}
    return assess_memory(memory_mib, linux, windows, client_memory_mib)


def wait_for_memory(memory_mib, timeout, client_memory_mib=0):
    """Retry admission until timeout seconds elapse, returning the successful memory snapshot.

    Both heap sizes are MiB. Each host probe is bounded by the remaining deadline; a
    probe finishing after it cannot admit a run. Busy messages are rate-limited. This
    wait does not evict caches, terminate other processes or weaken the reserve.
    """
    deadline = time.monotonic() + timeout
    announced = 0.0
    last_busy = ResourceBusy('Busy: memory admission has no successful probe')
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise ResourceBusy(str(last_busy) + '; resource wait expired') from last_busy
        try:
            admitted = available_memory(memory_mib, client_memory_mib, probe_timeout=min(15, remaining))
            if time.monotonic() >= deadline:
                raise ResourceBusy('Busy: memory probe completed after the resource deadline')
            return admitted
        except ResourceBusy as busy:
            last_busy = busy
            if time.monotonic() >= deadline:
                raise ResourceBusy(str(busy) + '; resource wait expired') from busy
            if time.monotonic() >= announced:
                print(str(busy) + '; waiting', flush=True)
                announced = time.monotonic() + 30
            time.sleep(min(1, max(0, deadline - time.monotonic())))


def varint(value):
    """Encode a caller-validated nonnegative protocol integer as continuation bytes.

    This small status-client encoder does not accept arbitrary signed inputs; callers
    supply bounded lengths, packet IDs and the positive handshake protocol number.
    """
    output = bytearray()
    while True:
        byte = value & 127
        value >>= 7
        output.append(byte | (128 if value else 0))
        if not value:
            return bytes(output)


def read_varint(stream):
    """Decode at most five bytes from a binary status stream; reject truncation/overlong input."""
    value = 0
    for index in range(5):
        raw = stream.read(1)
        require(raw, 'Status stream ended unexpectedly')
        value |= (raw[0] & 127) << (7 * index)
        if not raw[0] & 128:
            return value
    raise ValidationError('Invalid status VarInt')


def query_status(port):
    """Query one loopback Minecraft status endpoint with a five-second socket timeout.

    Returns decoded status JSON after bounded framing checks. It neither logs in a
    player nor proves plugins or gameplay work; boot separately checks the version and
    companion/client evidence. Socket and JSON errors propagate.
    """
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
    """Return an ephemeral loopback port observed free at bind time.

    The temporary socket closes before return, so this is not an exclusive reservation
    against unrelated processes; a later bind failure must remain a failed boot.
    """
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


class OwnedServer:
    """Own exactly one subprocess group, its pipes/log and an idempotent shutdown receipt.

    Linux parent-death signaling is installed before Java starts, including the parent
    exit race check. No PID search or global JVM shutdown is used. The caller must call
    stop even after scenario failure; boot provides that finally path.
    """
    def __init__(self, command, directory, log_path):
        """Start command in directory with binary combined stdout/stderr at log_path.

        The process gets its own group and stdin console pipe. Spawn failure closes the
        opened log; the caller retains ownership of a successfully created instance.
        """
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
        """Read UTF-8 log output for observations, stripping ANSI codes and replacing invalid bytes."""
        return re.sub(r'\x1b\[[0-9;]*[A-Za-z]', '', self.log_path.read_text(encoding='utf-8', errors='replace'))

    def send(self, command):
        """Write and flush one newline-free console command to this still-running child.

        This is delivery intent, not completion; follow with fresh log/report observation.
        """
        require(command and not re.search(r'[\r\n]', command), 'Expected one console command')
        require(self.process.poll() is None, 'Paper is no longer running')
        self.process.stdin.write((command + '\n').encode())
        self.process.stdin.flush()

    def wait_text(self, pattern, timeout, offset=0):
        """Require a regex in log text after the character offset before timeout seconds.

        An exited child or expired monotonic deadline raises ValidationError. Use an offset
        captured before sending a command to avoid matching an older command's response.
        """
        deadline = time.monotonic() + timeout
        while True:
            if re.search(pattern, self.text()[offset:]):
                return
            require(self.process.poll() is None, f'Paper exited while waiting for {pattern}')
            require(time.monotonic() < deadline, f'Timed out waiting for {pattern}')
            time.sleep(0.1)

    def stop(self, timeout=45):
        """Stop this owned process group and cache its shutdown receipt for repeated callers.

        Try the console stop and wait timeout seconds, then TERM and KILL only this group
        with bounded ten-second waits. Close the owned streams. Clean Paper shutdown means
        exit zero, no forced signal and a stopping-server log marker; forced exit remains
        explicit evidence even if the process eventually returns zero.
        """
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


class OwnedPlayer(OwnedServer):
    """Reuse owned-group cleanup while keeping client success separate from clean termination.

    A deliberate negative-control client may exit nonzero without forced termination.
    The result therefore records clean cleanup and successfulExit independently; suite
    control policy decides which exit outcome was required.
    """
    def stop(self, timeout=5):
        """Return cached group cleanup with client-specific clean and successfulExit fields."""
        result = super().stop(timeout)
        result['clean'] = not result['forced'] and result['exitCode'] is not None
        result['successfulExit'] = result['exitCode'] == 0
        return result


def player_pins(pins):
    """Validate the exact timestamped MCProtocolLib coordinate, digest and protocol number.

    Return normalized pin metadata, including its expected distribution JAR filename.
    A floating snapshot/version or malformed hash cannot establish client provenance.
    """
    coordinate = pins.get('testPlayerProtocolLib', '')
    require(re.fullmatch(r'org\.geysermc\.mcprotocollib:protocol:[0-9.]+-\d{8}\.\d{6}-\d+', coordinate),
            'Player protocol pin must name an exact timestamped MCProtocolLib publication')
    digest = pins.get('testPlayerProtocolLibSha256', '')
    require(re.fullmatch(r'[a-f0-9]{64}', digest), 'Invalid player protocol SHA256')
    protocol = pins.get('testPlayerProtocolVersion', '')
    require(protocol.isdecimal() and int(protocol) > 0, 'Invalid player protocol version')
    return {'artifact': coordinate, 'sha256': digest, 'protocolVersion': int(protocol),
            'jarName': 'protocol-' + coordinate.rsplit(':', 1)[1] + '.jar',
            'minecraftVersion': pins['minecraftVersion']}


def player_mode(mode, scenario, control, catalog):
    """Check explicit runner actor mode against the selected scenario's catalog declaration.

    Return whether a client is required. Unknown modes/controls, an implicit offline
    mode, or a player failure control without a client raise ValidationError.
    """
    require(mode in (None, 'protocol-calibration', 'protocol-actions-v1'), 'Unknown isolated player mode')
    require(control in ('calibrate', 'early-exit', 'idle'), 'Unknown player failure control')
    require(isinstance(catalog, dict) and scenario in catalog and isinstance(catalog[scenario], dict),
            'Unknown or malformed scenario descriptor')
    descriptor = catalog[scenario]
    require('testPlayerMode' not in descriptor or descriptor['testPlayerMode'] in ('protocol-calibration', 'protocol-actions-v1'),
            'Unknown or malformed catalog player mode')
    require(mode == descriptor.get('testPlayerMode'),
            'Explicit player mode must match the selected scenario catalog declaration')
    require(mode is not None or control == 'calibrate', 'Player failure controls require protocol player mode')
    return mode is not None


def test_settings(base, run_id, port, with_player=False, actor_count=1):
    """Copy base settings into a fresh disposable run configuration.

    Bind loopback, disable remote management and use a run-specific world. Only an
    explicit with_player mode disables authentication and limits admission to the
    catalog actor count/whitelist. No human profile or input dictionary is mutated.
    """
    settings = dict(base)
    settings.update({'server-ip': '127.0.0.1', 'server-port': str(port),
                     'online-mode': 'false' if with_player else 'true',
                     'enable-rcon': 'false', 'enable-query': 'false', 'enable-jmx-monitoring': 'false',
                     'level-name': 'agent-world-' + run_id, 'pause-when-empty-seconds': '-1'})
    if with_player:
        settings.update({'enforce-secure-profile': 'false', 'white-list': 'true', 'enforce-whitelist': 'true',
                         'max-players': str(actor_count), 'allow-flight': 'true'})
    return settings


def validate_player_messages(report, descriptor):
    """Replay bounded received text and catalog matchers, not sent command intent."""
    require(isinstance(report, dict) and isinstance(descriptor, dict), 'Malformed player message evidence/catalog')
    messages = report.get('messages')
    require(isinstance(messages, list) and len(messages) <= 128
            and all(isinstance(message, str) and 0 < len(message) <= 2048
                    and not message.startswith('OD_PLAYER:') for message in messages),
            'Missing/malformed bounded player messages')
    expected = descriptor.get('requiredPlayerMessages', [])
    require(isinstance(expected, list) and len(expected) <= 32, 'Malformed required player messages')
    seen = set()
    for requirement in expected:
        require(isinstance(requirement, dict) and set(requirement) in ({'id', 'exact'}, {'id', 'containsAll'}),
                'Malformed player message matcher')
        identity = requirement['id']
        require(isinstance(identity, str) and re.fullmatch(r'[a-z][a-z0-9-]{0,63}', identity)
                and identity not in seen, 'Missing/duplicate player message matcher identity')
        seen.add(identity)
        if 'exact' in requirement:
            value = requirement['exact']
            require(isinstance(value, str) and 0 < len(value) <= 2048, 'Malformed exact player message')
            matched = value in messages
        else:
            fragments = requirement['containsAll']
            require(isinstance(fragments, list) and 0 < len(fragments) <= 8
                    and all(isinstance(value, str) and 0 < len(value) <= 256 for value in fragments),
                    'Malformed player message fragments')
            matched = any(all(fragment in message for fragment in fragments) for message in messages)
        require(matched, 'Missing/incorrect required player message: ' + identity)


def validate_player_report(path, run_id, issued_ms, timeout, pins, descriptor=None):
    """Validate the legacy single-actor calibration report and its received messages.

    Bind identity, protocol artifact, action sequence, disconnect and successful result
    to run_id and the issued epoch-millisecond window (timeout in seconds). This does
    not accept the multi-actor action schema; boot routes that mode to player_actions.
    """
    report = strict_json(path)
    require(isinstance(report, dict), 'Player report must be an object')
    pinned = player_pins(pins)
    expected = {'schemaVersion': 1, 'runId': run_id, 'username': 'od_' + run_id[:13],
                'authentication': 'offline-disposable-loopback', 'artifact': pinned['artifact'],
                'minecraftVersion': pinned['minecraftVersion'], 'protocolVersion': pinned['protocolVersion'], 'loginReceived': True,
                'playerLoadedSent': True, 'actions': ['select', 'draw', 'release', 'quit'],
                'disconnected': True, 'passed': True, 'error': ''}
    for key, value in expected.items():
        require(json_values_equal(value, report.get(key)), 'Missing/failed player evidence: ' + key)
    start, end = report.get('startedAtEpochMs'), report.get('completedAtEpochMs')
    require(type(start) is int and type(end) is int and issued_ms <= start <= end <= issued_ms + timeout * 1000,
            'Stale or timed-out player timestamps')
    require(end <= int(time.time() * 1000) + 1000 and path.stat().st_mtime_ns // 1_000_000 >= issued_ms - 1000,
            'Stale/future player report')
    require(type(report.get('teleportsAcknowledged')) is int and report['teleportsAcknowledged'] > 0,
            'Player did not acknowledge teleportation')
    validate_player_messages(report, descriptor if descriptor is not None else {})
    return report


def build_player_client(project, java_home, report_root, pins):
    """Build/test the isolated client and verify its installed distribution and locked pin.

    Use java_home and an issue-local Gradle cache; append build output to report_root.
    Reject failed/skipped/missing JUnit or mismatched MCProtocolLib bytes. Return staged
    JAR paths plus dependency/JUnit/hash evidence; never add these dependencies to the
    production plugin classpath.
    """
    pinned = player_pins(pins)
    env = dict(os.environ, JAVA_HOME=str(java_home), GRADLE_USER_HOME=str(project / '.gradle/agent-home'))
    env['PATH'] = str(java_home / 'bin') + os.pathsep + env.get('PATH', '')
    with (report_root / 'build.log').open('a', encoding='utf-8') as log:
        result = subprocess.run(['bash', str(project / 'gradlew'), '-p', 'dev/player-client', 'build', 'installDist',
                                 '--dependency-verification', 'strict', '--console=plain'], cwd=project, env=env,
                                stdout=log, stderr=subprocess.STDOUT, timeout=900)
    require(result.returncode == 0, 'Player Gradle build or dependency verification failed; see build.log')
    root = project / 'dev/player-client'
    tests = list((root / 'build/test-results/test').glob('TEST-*.xml'))
    require(tests, 'Player client JUnit evidence is missing')
    counts = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
    for file in tests:
        suite = ET.parse(file).getroot()
        for key in counts:
            counts[key] += int(suite.attrib.get(key, '0'))
    require(counts['tests'] > 0 and not any(counts[key] for key in ('failures', 'errors', 'skipped')),
            'Player client tests failed, aborted, or skipped')
    jars = sorted((root / 'build/install/OnlyDragonsPlayerClient/lib').glob('*.jar'))
    hashes = {jar.name: sha256(jar) for jar in jars}
    require('OnlyDragonsPlayerClient.jar' in hashes, 'Player client artifact is missing')
    require(hashes.get(pinned['jarName']) == pinned['sha256'],
            'MCProtocolLib artifact differs from the verified exact 26.2 publication')
    return jars, {'artifact': pinned['artifact'], 'jars': hashes, 'unitTests': counts,
                  'lockSha256': sha256(root / 'gradle.lockfile'),
                  'verificationMetadataSha256': sha256(root / 'gradle/verification-metadata.xml')}


def fetch_paper(project, pins, supplied):
    """Return the supplied or cached Paper launcher only after verifying its project digest.

    An explicit wrong artifact fails. Otherwise download the pinned HTTPS URL into a
    temporary cache file and publish it only after hashing. This selects no newer
    Paper build and does not supply the separate Mojang bootstrap JAR.
    """
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
    """Sequentially build the plugin and real-Paper companion with the selected JDK.

    Read the production artifact receipt, require it inside this checkout's build
    directory, and require both nonempty successful JUnit cohorts without skips. Return
    the two JAR paths and build counts; no server is launched or evidence reused merely
    because an older artifact exists.
    """
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
    evidence = {'wrapperInvoked': True}
    for label, directory, key in [('Production', 'build/test-results/test', 'unitTests'),
                                  ('Companion', 'dev/game-tests/build/test-results/test', 'companionUnitTests')]:
        tests = list((project / directory).glob('TEST-*.xml'))
        require(tests, label + ' JUnit evidence is missing')
        counts = {name: 0 for name in ('tests', 'failures', 'errors', 'skipped')}
        for file in tests:
            suite = ET.parse(file).getroot()
            for name in counts:
                counts[name] += int(suite.attrib.get(name, '0'))
        require(counts['tests'] > 0 and not any(counts[name] for name in ('failures', 'errors', 'skipped')),
                label + ' tests failed, aborted, or skipped')
        evidence[key] = counts
    return main, companion, evidence


def stage_artifact(source, destination, expected_sha256):
    """Copy one artifact, then require its destination bytes to match the recorded SHA-256."""
    shutil.copyfile(source, destination)
    require(sha256(destination) == expected_sha256, 'Artifact changed after build validation: ' + destination.name)


def epoch_ms():
    """Return wall-clock epoch milliseconds for evidence windows, not monotonic deadlines."""
    return int(time.time() * 1000)


def process_identity(owned):
    # Kernel start ticks disambiguate recycled PIDs without searching for other JVMs.
    """Record this owned live child's PID and Linux kernel start tick to detect PID reuse."""
    stat = Path(f'/proc/{owned.process.pid}/stat').read_text().rsplit(')', 1)[1].split()
    return {'pid': owned.process.pid, 'startTicks': int(stat[19]), 'startedAtEpochMs': epoch_ms()}


def boot(args, project, java_home, pins, scenario_id, scenario, action_plan,
         run_id, directory, report_root, outcome, plan_file=None, phase_context=None):
    """Execute and observe one Paper boot while the caller holds lease and memory admission.

    Inputs bind a fresh profile, run ID, catalog scenario, optional exact action plan
    and restart phase. Stage creation/building is the caller's responsibility. This
    method launches Paper, verifies status, issues the companion scenario, optionally
    launches one client JVM, and cross-checks companion/client/journal evidence.

    It mutates outcome with process windows and observations. A finally path stops
    client then Paper, restores signal handlers and preserves the raw companion report
    even on failure. passed becomes true only after validation, clean cleanup and the
    Paper error scan; an exception never rolls back an earlier observed game action.
    """
    server = client = None
    plugins = directory / 'plugins'
    client_dir = directory / 'player-client'
    port = outcome['profile']['port']
    with_player = bool(args.test_player)
    plan_file = plan_file or directory / 'player-plan.json'
    command = [str(java_home / 'bin/java'), f'-Xmx{args.memory_mib}m', '-Xms256m', '-XX:ActiveProcessorCount=2',
               '-Dfile.encoding=UTF-8', '-Dterminal.jline=false', '-Dterminal.ansi=false',
               f'-Donlydragons.test.runId={run_id}', f'-Donlydragons.test.playerMode={args.test_player or ""}',
               '-jar', 'server.jar', '--nogui']
    if action_plan:
        command[1:1] = ['-Donlydragons.test.playerPlan=' + str(plan_file),
                        '-Donlydragons.test.playerPlanSha256=' + action_plan[1]]
    if phase_context is not None:
        command[1:1] = ['-Donlydragons.test.phaseContext=' + str(phase_context)]
    try:
        print(f'Starting isolated Paper at 127.0.0.1:{port}', flush=True)
        blocked = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
        try:
            server = OwnedServer(command, directory, report_root / 'server.log')
            outcome['serverProcess'] = process_identity(server)
        finally:
            signal.pthread_sigmask(signal.SIG_SETMASK, blocked)
        server.wait_text(r'Done \([0-9.,]+s\)!', args.startup_timeout)
        status = query_status(port)
        require(pins['minecraftVersion'] in status['version']['name'], 'Status protocol returned the wrong Minecraft version')
        outcome['status'] = status
        issued_ms = int(time.time() * 1000)
        outcome['issuedAtEpochMs'] = issued_ms
        outcome['deadlineAtEpochMs'] = issued_ms + args.scenario_timeout * 1000
        server.send(f'odgametest {run_id} {scenario_id}')
        expected = dict(scenario, runId=run_id, scenarioId=scenario_id,
                        minecraftVersion=pins['minecraftVersion'], paperBuild=pins['paperBuild'])
        scenario_path = plugins / 'OnlyDragonsGameTests/report.json'
        if with_player:
            server.wait_text('OD_PLAYER_READY ' + run_id, min(args.scenario_timeout, 10))
            client_command = [str(java_home / 'bin/java'), '-Xmx256m', '-Xms32m', '-XX:ActiveProcessorCount=2',
                              '-Dfile.encoding=UTF-8', '-cp', str(client_dir / '*'),
                              'com.kaveenk.onlydragons.playerclient.ProtocolPlayer', run_id, str(port),
                              str(report_root / 'player.json'), str(args.scenario_timeout), args.player_control]
            if action_plan:
                client_command[client_command.index('com.kaveenk.onlydragons.playerclient.ProtocolPlayer')] = 'com.kaveenk.onlydragons.playerclient.ActionPlayer'
                client_command += [str(plan_file), action_plan[1]]
            blocked = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
            try:
                client = OwnedPlayer(client_command, directory, report_root / 'player.log')
                outcome['clientProcess'] = process_identity(client)
            finally:
                signal.pthread_sigmask(signal.SIG_SETMASK, blocked)
        outcome['scenario'] = wait_for_report(scenario_path, expected, issued_ms, args.scenario_timeout,
                                              server.process, client.process if client else None)
        if client:
            require(client.process.wait(timeout=5) == 0, 'Protocol player exited unsuccessfully')
            if action_plan:
                report = strict_json(report_root / 'player.json')
                player_actions.validate_report(report, action_plan[2], action_plan[1], run_id, pins,
                                               issued_ms, int(time.time() * 1000))
                player_actions.validate_messages(report, scenario)
                player_actions.validate_server_journal(outcome['scenario'], report, action_plan[2])
                outcome['player'] = report
            else:
                outcome['player'] = validate_player_report(report_root / 'player.json', run_id, issued_ms, args.scenario_timeout, pins, scenario)
    finally:
        if server is not None:
            # Ignore a repeated termination while saving this runner's own disposable world.
            previous = {item: signal.signal(item, signal.SIG_IGN) for item in (signal.SIGTERM, signal.SIGINT)}
            try:
                try:
                    if client is not None:
                        outcome['playerStopStartedAtEpochMs'] = epoch_ms()
                        outcome['playerCleanup'] = client.stop()
                        outcome['playerStopCompletedAtEpochMs'] = epoch_ms()
                finally:
                    outcome['stopStartedAtEpochMs'] = epoch_ms()
                    outcome['cleanup'] = server.stop()
                    outcome['stopCompletedAtEpochMs'] = epoch_ms()
            finally:
                for item, handler in previous.items():
                    signal.signal(item, handler)
        raw_report = plugins / 'OnlyDragonsGameTests/report.json'
        if raw_report.is_file():
            shutil.copyfile(raw_report, report_root / 'scenario.json')
    require(outcome['cleanup'] and outcome['cleanup']['clean'], 'Paper did not stop cleanly')
    if with_player:
        require(outcome.get('playerCleanup', {}).get('clean'), 'Protocol player did not stop cleanly')
    errors = paper_errors(server.text())
    require(not errors, 'Paper logged errors: ' + '\n'.join(errors[:10]))
    outcome['passed'] = True


def execute(args):
    """Own a single scenario's complete filesystem/build/lease/staging/run/report lifecycle.

    Create a unique report directory, validate Linux/JDK/EULA/catalog inputs, and build
    before acquiring the scarce server lease. Restart descriptors delegate two boots
    to paper_restart under the same ownership. Always attempt the final result receipt;
    return 0 on pass, 75 on ResourceBusy or 1 on other failure. The receipt describes
    this exact source/worktree state and does not itself accept a project milestone.
    """
    require(sys.platform == 'linux', 'Use this runner on Linux/WSL; Windows human play keeps using mcdev.cmd')
    project = args.project.resolve()
    require((project / 'versions.properties').is_file() and (project / 'AGENTS.md').is_file(), 'Expected an OnlyDragons checkout')
    run_id = uuid.uuid4().hex
    report_root = project / 'build/reports/agent-paper' / run_id
    report_root.mkdir(parents=True)
    outcome = {'schemaVersion': 1, 'runId': run_id, 'scenarioId': args.scenario, 'passed': False,
               'startedAtEpochMs': int(time.time() * 1000), 'error': None, 'cleanup': None}
    server = None
    client = None
    try:
        plans = strict_json(project / 'dev/game-tests/scenarios.json')
        with_player = player_mode(args.test_player, args.scenario, args.player_control, plans)
        accepted_eula(args.eula_file)
        require(args.lease_directory is not None, 'Supply the operator-provisioned shared lease directory')
        require(args.java_home is not None, 'Supply JDK via --java-home or JAVA_HOME')
        java_home = args.java_home.resolve()
        pins = properties(project / 'versions.properties')
        release = properties(java_home / 'release')
        require(re.match(r'"?' + re.escape(pins['javaVersion']) + r'(?:\.|\")', release.get('JAVA_VERSION', '')), 'JDK major does not match versions.properties')
        scenario = plans[args.scenario]
        restart = scenario.get('catalogMode') == 'same-profile-restart-v1'
        if restart:
            import paper_restart
            paper_restart.validate_descriptor(project, scenario)
        action_plan = player_actions.load_plan(project, scenario['phases'][0] if restart else scenario) if args.test_player == 'protocol-actions-v1' else None
        outcome.update({'revision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=project, text=True).strip(),
                        'worktreeDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=project, text=True).strip()),
                        'pins': pins, 'javaVersion': release['JAVA_VERSION'].strip('"')})
        print(f'RUN {run_id}: {args.scenario}; reports: {report_root}', flush=True)
        # Build before acquiring the scarce server slot. Caches remain in this issue checkout.
        main, companion, build = build_artifacts(project, java_home, report_root)
        outcome['build'] = build
        outcome['artifacts'] = {'productionSha256': sha256(main), 'gameTestsSha256': sha256(companion)}
        if with_player:
            client_jars, outcome['playerBuild'] = build_player_client(project, java_home, report_root, pins)
        paper = fetch_paper(project, pins, args.paper_jar)
        bootstrap_metadata = paper_bootstrap.inspect_launcher(paper, pins['paperSha256'])
        mojang = paper_bootstrap.resolve_mojang(project, paper, bootstrap_metadata, supplied=args.mojang_jar)
        lease_evidence = {} if restart else None
        if restart:
            outcome['lease'] = lease_evidence
        with server_lease(args.lease_directory.resolve(), args.lease_timeout, lease_evidence):
            outcome['memory'] = wait_for_memory(args.memory_mib, args.resource_timeout, 256 if with_player else 0)
            directory = project / 'run/agent-tests' / run_id
            directory.mkdir(parents=True, exist_ok=False)
            plugins = directory / 'plugins'
            plugins.mkdir()
            stage_artifact(main, plugins / 'OnlyDragons.jar', outcome['artifacts']['productionSha256'])
            stage_artifact(companion, plugins / 'OnlyDragonsGameTests.jar', outcome['artifacts']['gameTestsSha256'])
            stage_artifact(paper, directory / 'server.jar', pins['paperSha256'])
            outcome['bootstrap'] = paper_bootstrap.stage_bootstrap(directory, mojang, bootstrap_metadata)
            shutil.copyfile(args.eula_file, directory / 'eula.txt')
            port = free_port()
            settings = test_settings(properties(project / 'dev/server.properties'), run_id, port, with_player,
                                     len(action_plan[2]['actors']) if action_plan else 1)
            if with_player:
                client_dir = directory / 'player-client'
                client_dir.mkdir()
                for jar in client_jars:
                    stage_artifact(jar, client_dir / jar.name, outcome['playerBuild']['jars'][jar.name])
                player_name = 'od_' + run_id[:13]
                offline_id = bytearray(hashlib.md5(('OfflinePlayer:' + player_name).encode()).digest())
                offline_id[6] = (offline_id[6] & 0x0f) | 0x30
                offline_id[8] = (offline_id[8] & 0x3f) | 0x80
                atomic_json(directory / 'whitelist.json', [{'uuid': str(uuid.UUID(bytes=bytes(offline_id))), 'name': player_name}])
                if action_plan:
                    plan_path, plan_hash, plan = action_plan
                    stage_artifact(plan_path, directory / 'player-plan.json', plan_hash)
                    atomic_json(directory / 'whitelist.json', player_actions.identities(run_id, plan))
                    outcome['playerPlan'] = {'sha256': plan_hash, 'planId': plan['planId']}
            (directory / 'server.properties').write_text(''.join(f'{key}={value}\n' for key, value in settings.items()), encoding='utf-8')
            outcome['profile'] = {'directory': str(directory), 'port': port, 'world': settings['level-name'],
                                  'authentication': 'offline-disposable-loopback' if with_player else 'authenticated',
                                  'testPlayerMode': args.test_player}
            if restart:
                import paper_restart
                paper_restart.execute_phases(sys.modules[__name__], args, project, java_home, pins,
                                             scenario, run_id, directory, report_root, outcome)
            else:
                boot(args, project, java_home, pins, args.scenario, scenario, action_plan,
                     run_id, directory, report_root, outcome)

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
    """Parse the bounded CLI, convert termination into owned cleanup, and return execute's code."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=Path.cwd())
    parser.add_argument('--scenario', default='lifecycle-calibration')
    parser.add_argument('--eula-file', type=Path, default=os.environ.get('ONLYDRAGONS_EULA_FILE'))
    parser.add_argument('--lease-directory', type=Path, default=os.environ.get('ONLYDRAGONS_TEST_COORDINATION'))
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--paper-jar', type=Path, default=os.environ.get('ONLYDRAGONS_TEST_PAPER_JAR'))
    parser.add_argument('--mojang-jar', type=Path, default=os.environ.get('ONLYDRAGONS_TEST_MOJANG_JAR'))
    parser.add_argument('--test-player', choices=['protocol-calibration', 'protocol-actions-v1'], help='Explicit disposable loopback offline actor mode')
    parser.add_argument('--player-control', choices=['calibrate', 'early-exit', 'idle'], default='calibrate',
                        help='Negative client controls always fail validation; default performs the calibration')
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
