#!/usr/bin/env python3
"""Stage only verified Paperclip bootstrap input into disposable test profiles.

The launcher pin authenticates its embedded Mojang URL/checksum. This module
never starts Java, accesses a network, or copies an existing server profile.
Provision the one missing public artifact separately if no valid local copy is
available. Paperclip extracts its libraries and applies its own embedded patches.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import shutil
from urllib.parse import urlsplit
import zipfile


class BootstrapError(RuntimeError):
    """Missing, malformed, changed, or unverified bootstrap evidence."""


def _require(condition, message):
    """Raise BootstrapError when a bootstrap trust or path condition is not satisfied."""
    if not condition:
        raise BootstrapError(message)


def _digest(path):
    """Hash the current file bytes with SHA-256; propagate unreadable/missing-file errors."""
    with Path(path).open('rb') as handle:
        return hashlib.file_digest(handle, 'sha256').hexdigest()


def _verified_file(path, expected, label):
    """Return a regular nonsymlink file only when its bytes match the lowercase SHA-256.

    label supplies diagnostics. This verifies the named file now, not an immutable
    filesystem handle or every ancestor; sensitive consumers recheck after reading.
    """
    path = Path(path)
    _require(isinstance(expected, str) and re.fullmatch(r'[a-f0-9]{64}', expected),
             f'Invalid {label} SHA256')
    _require(not path.is_symlink() and path.is_file(), f'{label} must be a regular local file: {path}')
    _require(_digest(path) == expected, f'{label} checksum mismatch: {path}')
    return path


def inspect_launcher(paper_jar, pinned_paper_sha256):
    """Read bounded Mojang bootstrap metadata authenticated by the exact Paper launcher hash.

    Require one small download-context entry, a safe cache filename and the expected
    public Mojang HTTPS URL shape. Rehash the launcher after reading to reject detected
    changes. Return metadata/digests only; never access the URL or execute the archive.
    """
    paper_jar = _verified_file(paper_jar, pinned_paper_sha256, 'Paper launcher')
    try:
        with zipfile.ZipFile(paper_jar) as archive:
            entries = [entry for entry in archive.infolist()
                       if entry.filename == 'META-INF/download-context']
            _require(len(entries) == 1, 'Expected exactly one Paper download-context entry')
            _require(0 < entries[0].file_size <= 4096, 'Invalid Paper download-context size')
            raw = archive.read(entries[0])
        # Reject a launcher changed between its initial hash and ZIP metadata read.
        _verified_file(paper_jar, pinned_paper_sha256, 'Paper launcher')
        text = raw.decode('utf-8')
        _require('\r' not in text and '\x00' not in text,
                 'Malformed Paper download-context')
        lines = text.splitlines()
        _require(len(lines) == 1, 'Expected one Paper download-context record')
        fields = lines[0].split('\t')
        _require(len(fields) == 3, 'Malformed Paper download-context fields')
        digest, url, name = fields
        _require(re.fullmatch(r'[a-f0-9]{64}', digest), 'Invalid Mojang SHA256 in Paper metadata')
        _require(re.fullmatch(r'mojang_[A-Za-z0-9][A-Za-z0-9_.-]*\.jar', name)
                 and '..' not in name, 'Unsafe Mojang cache filename')
        parsed = urlsplit(url)
        _require(parsed.scheme == 'https' and parsed.hostname == 'piston-data.mojang.com'
                 and parsed.username is None and parsed.password is None and parsed.port is None
                 and not parsed.query and not parsed.fragment
                 and re.fullmatch(r'/v1/objects/[a-f0-9]{40}/server\.jar', parsed.path),
                 'Expected a public pinned Mojang HTTPS artifact URL')
    except (OSError, ValueError, UnicodeError, zipfile.BadZipFile, RuntimeError) as error:
        if isinstance(error, BootstrapError):
            raise
        raise BootstrapError('Cannot read verified Paper download-context') from error
    return {'paperSha256': pinned_paper_sha256,
            'downloadContextSha256': hashlib.sha256(raw).hexdigest(),
            'mojangSha256': digest, 'mojangUrl': url, 'mojangFileName': name}


def _project_candidates(root, metadata):
    """Yield only named verified-artifact caches beneath a project root.

    These candidates are launcher/Mojang caches, never worlds or plugin directories;
    existence and digest validation belong to resolve_mojang.
    """
    root = Path(root)
    name = metadata['mojangFileName']
    yield root / 'run/agent-cache' / f"mojang-{metadata['mojangSha256']}.jar"
    yield root / 'run/agent-cache' / name
    # These are named development caches only; never inspect worlds or plugins.
    version = name[len('mojang_'):-len('.jar')]
    for suffix in ('dev', 'smoke'):
        yield root / 'run/servers' / f'{version}-{suffix}' / 'cache' / name


def resolve_mojang(project, paper_jar, metadata, supplied=None, environ=None):
    """Find a valid local input without downloading or silently ignoring an override.

    Search the issue cache, the explicitly named operator source, the launcher's
    sibling cache, and repository ancestors of the launcher. A valid candidate
    may replace an invalid automatic cache; an invalid explicit override fails.
    """
    _require(isinstance(metadata, dict), 'Missing Paper bootstrap metadata')
    actual = inspect_launcher(paper_jar, metadata.get('paperSha256'))
    _require(metadata == actual, 'Paper bootstrap metadata differs from its launcher')
    environment = os.environ if environ is None else environ
    explicit = supplied if supplied is not None else environment.get('ONLYDRAGONS_TEST_MOJANG_JAR')
    if explicit is not None:
        _require(str(explicit).strip(), 'Explicit Mojang cache path is empty')
        return _verified_file(Path(explicit), metadata['mojangSha256'], 'Mojang bootstrap input')

    candidates = list(_project_candidates(project, metadata))
    source = environment.get('ONLYDRAGONS_SOURCE')
    if source:
        candidates.extend(_project_candidates(source, metadata))
    launcher_parent = Path(paper_jar).absolute().parent
    candidates.append(launcher_parent / 'cache' / metadata['mojangFileName'])
    for parent in (launcher_parent, *launcher_parent.parents):
        if (parent / 'AGENTS.md').is_file() and (parent / 'versions.properties').is_file():
            candidates.extend(_project_candidates(parent, metadata))
    seen = set()
    rejected = 0
    for path in candidates:
        key = os.path.normcase(os.path.abspath(path))
        if key in seen:
            continue
        seen.add(key)
        if not path.exists() and not path.is_symlink():
            continue
        try:
            return _verified_file(path, metadata['mojangSha256'], 'Mojang bootstrap input')
        except (BootstrapError, OSError):
            rejected += 1
    raise BootstrapError(
        'No verified local Mojang bootstrap input. Provision the artifact named by '
        'the pinned Paper download-context and set ONLYDRAGONS_TEST_MOJANG_JAR '
        f'or --mojang-jar; rejected automatic cache candidates: {rejected}.')


def _manifest(metadata):
    """Describe the exact bootstrap input and its fixed relative cache path without I/O."""
    return {'schemaVersion': 1, **metadata,
            'stagedRelativePath': 'cache/' + metadata['mojangFileName']}


def _copy_verified(source, destination, digest):
    """Exclusively create and rehash a staged bootstrap copy; remove only that copy on failure.

    A preexisting destination is never overwritten or deleted by the failure cleanup.
    """
    _verified_file(source, digest, 'Mojang bootstrap input')
    created = False
    try:
        with Path(source).open('rb') as reader, destination.open('xb') as writer:
            created = True
            shutil.copyfileobj(reader, writer)
        _verified_file(destination, digest, 'Staged Mojang bootstrap input')
    except BaseException:
        # This path was exclusively created inside this run's fresh cache directory.
        if created:
            destination.unlink(missing_ok=True)
        raise


def stage_bootstrap(profile_directory, mojang_jar, metadata):
    """Stage one verified Mojang input beside the caller's pinned server.jar.

    Require a real profile and a previously absent cache directory; rederive metadata
    from the staged launcher, copy exclusively and validate the resulting manifest.
    Return the manifest for the receipt. No network, Java launch, EULA or world copying
    is performed; a failed staging attempt may leave its newly created cache directory.
    """
    profile = Path(profile_directory)
    _require(not profile.is_symlink() and profile.is_dir(), 'Expected a real disposable profile directory')
    _require(isinstance(metadata, dict), 'Missing Paper bootstrap metadata')
    actual = inspect_launcher(profile / 'server.jar', metadata.get('paperSha256'))
    _require(actual == metadata, 'Staged Paper bootstrap metadata changed')
    _verified_file(mojang_jar, actual['mojangSha256'], 'Mojang bootstrap input')
    cache = profile / 'cache'
    _require(not cache.exists() and not cache.is_symlink(), 'Bootstrap cache must be fresh for this run')
    cache.mkdir()
    _copy_verified(mojang_jar, cache / actual['mojangFileName'], actual['mojangSha256'])
    manifest = _manifest(actual)
    validate_bootstrap(profile, manifest, actual['paperSha256'])
    return manifest


def validate_bootstrap(profile_directory, manifest, pinned_paper_sha256):
    """Replay launcher metadata and the staged Mojang digest against the supplied manifest.

    Reject symlinked profile/cache/input, stale fields and bool-as-version confusion.
    Return the rederived manifest without modifying files or trusting reported hashes
    alone; the caller still owns overall run/source and process-window validation.
    """
    profile = Path(profile_directory)
    _require(not profile.is_symlink() and profile.is_dir(), 'Expected a real disposable profile directory')
    actual = inspect_launcher(profile / 'server.jar', pinned_paper_sha256)
    expected = _manifest(actual)
    # Explicit type check prevents JSON true from impersonating schemaVersion 1.
    _require(isinstance(manifest, dict) and type(manifest.get('schemaVersion')) is int
             and manifest == expected, 'Missing, malformed or stale bootstrap manifest')
    cache = profile / 'cache'
    _require(not cache.is_symlink() and cache.is_dir(), 'Expected a real staged bootstrap cache')
    _verified_file(cache / actual['mojangFileName'], actual['mojangSha256'],
                   'Staged Mojang bootstrap input')
    return expected
