"""Hosted orchestration boundaries; all downloads and Paper launches are mocked."""
from argparse import Namespace
import base64
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import ci_paper as ci


SHA = 'a' * 40
RUN = 'b' * 32
SUITE = 'c' * 32
CONSENT = b'# Already accepted by the operator\r\neula=true\r\n'
JDK_VERSION = '25.0.4.1'
JDK_RUNTIME = JDK_VERSION + '+1'
JDK_DIRECTORY = 'jdk-' + JDK_RUNTIME
JDK_URL = ('https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/'
           'OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz')
JDK_RELEASE = {'JAVA_VERSION': JDK_VERSION, 'JAVA_RUNTIME_VERSION': JDK_RUNTIME + '-LTS',
               'SEMANTIC_VERSION': JDK_RUNTIME, 'IMPLEMENTOR': 'Eclipse Adoptium',
               'IMPLEMENTOR_VERSION': 'Temurin-' + JDK_RUNTIME, 'OS_NAME': 'Linux',
               'OS_ARCH': 'x86_64', 'IMAGE_TYPE': 'JDK'}


class Response(io.BytesIO):
    def __init__(self, data, url='https://piston-data.mojang.com/file'):
        super().__init__(data)
        self.url = url

    def geturl(self):
        return self.url


class HostedPaperTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.project = self.root / 'project'
        self.project.mkdir()

    def put(self, relative, data=b'evidence', root=None):
        path = (root or self.project) / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def test_canonical_path_has_only_bounded_actions_identifiers(self):
        self.assertEqual(ci.canonical_root('1234', '2').as_posix(), '/tmp/onlydragons-paper-ci/1234-2')
        for run, attempt in (('../outside', '1'), ('1', '../2'), ('0', '1'), ('1', '0'),
                             ('1', '1/2'), ('1' * 21, '1'), ('1', True)):
            with self.subTest(run=run, attempt=attempt), self.assertRaises(RuntimeError):
                ci.canonical_root(run, attempt)

    def test_exact_java_pin_must_match_project_major(self):
        self.put('versions.properties', b'javaVersion=25\n')
        script = self.put('scripts/symphony/install-runtime.sh', b'JAVA_VERSION="25.0.4.1"\n')
        self.assertEqual(ci.java_version(self.project), '25.0.4.1')
        for value in (b'JAVA_VERSION="26.0.1"\n', b'JAVA_VERSION="25;bad"\n', b'',
                      b'JAVA_VERSION="25.0.4.1"\nJAVA_VERSION="25.0.4.1"\n'):
            script.write_bytes(value)
            with self.assertRaises(RuntimeError):
                ci.java_version(self.project)

    def jdk_archive(self, identity=None, extra=None, tool_mode=0o755):
        release = '\n'.join(f'{key}="{value}"' for key, value in (identity or JDK_RELEASE).items()) + '\n'
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode='w:gz') as archive:
            for name, data, mode in [('release', release.encode(), 0o644),
                                     ('bin/java', b'test-only executable bytes', tool_mode),
                                     ('bin/javac', b'test-only compiler bytes', tool_mode)]:
                entry = tarfile.TarInfo(JDK_DIRECTORY + '/' + name)
                entry.mode, entry.size = mode, len(data)
                archive.addfile(entry, io.BytesIO(data))
            if extra is not None:
                archive.addfile(extra, io.BytesIO(b'') if extra.isfile() else None)
        return stream.getvalue()

    def pin_jdk(self, data):
        self.put('versions.properties', b'javaVersion=25\n')
        return self.put('scripts/symphony/install-runtime.sh',
                        f'JAVA_VERSION="{JDK_VERSION}"\nJAVA_URL="{JDK_URL}"\n'
                        f'JAVA_SHA256="{hashlib.sha256(data).hexdigest()}"\n'.encode())

    def test_archive_pin_requires_matching_literal_url_build_and_checksum(self):
        data = self.jdk_archive()
        script = self.pin_jdk(data)
        original = script.read_text()
        pin = ci.java_archive_pin(self.project)
        self.assertEqual(pin['runtimeVersion'], '25.0.4.1+1')
        self.assertEqual(pin['url'], JDK_URL)
        invalid = [original.replace('%2B1/', '%2B2/'), original.replace('_1.tar.gz', '_2.tar.gz'),
                   original.replace('x64_linux', 'aarch64_linux'), original.replace('https://', 'http://'),
                   original.replace('25.0.4.1%2B1/', '${JAVA_VERSION}%2B1/'),
                   original.replace('JAVA_SHA256="', 'JAVA_SHA256="invalid'),
                   original + f'JAVA_URL="{JDK_URL}"\n', original.replace('JAVA_SHA256=', '#JAVA_SHA256=')]
        for content in invalid:
            with self.subTest(content=content), self.assertRaises(RuntimeError):
                script.write_text(content)
                ci.java_archive_pin(self.project)

    def test_provision_extracts_exact_archive_and_records_release_identity(self):
        data = self.jdk_archive()
        self.pin_jdk(data)
        runtime = self.root / 'runtime'
        runtime.mkdir()
        with patch.object(ci.urllib.request, 'urlopen', return_value=Response(data, JDK_URL)) as opened, \
                patch.object(ci.subprocess, 'run') as launched:
            home, identity = ci.provision_java(self.project, runtime)
        self.assertEqual(home, runtime / 'jdk' / JDK_DIRECTORY)
        self.assertEqual((home / 'bin/javac').read_bytes(), b'test-only compiler bytes')
        self.assertEqual(opened.call_args.args[0].full_url, JDK_URL)
        self.assertEqual(identity['sha256'], hashlib.sha256(data).hexdigest())
        self.assertEqual(identity['releaseSha256'], ci.paper_test.sha256(home / 'release'))
        self.assertEqual(identity['javaRuntimeVersion'], '25.0.4.1+1-LTS')
        self.assertEqual(identity['releaseIdentity']['OS_ARCH'], 'x86_64')
        launched.assert_not_called()

    def test_provision_hash_failure_never_extracts_or_uses_ambient_java(self):
        self.pin_jdk(self.jdk_archive())
        runtime = self.root / 'runtime'
        runtime.mkdir()
        with patch.object(ci.urllib.request, 'urlopen', return_value=Response(b'wrong archive', JDK_URL)), \
                patch.object(ci.tarfile, 'open') as extracted, \
                patch.dict(ci.os.environ, {'JAVA_HOME': str(self.project)}):
            with self.assertRaisesRegex(RuntimeError, 'SHA256 mismatch'):
                ci.provision_java(self.project, runtime)
        extracted.assert_not_called()
        self.assertFalse((runtime / 'jdk').exists())

    def test_provision_rejects_wrong_version_build_vendor_platform_or_jre(self):
        invalid = {'JAVA_VERSION': '25.0.4', 'JAVA_RUNTIME_VERSION': '25.0.4.1+2-LTS',
                   'SEMANTIC_VERSION': '25.0.4.1+2', 'IMPLEMENTOR': 'Other vendor',
                   'IMPLEMENTOR_VERSION': 'Temurin-25.0.4.1+2', 'OS_NAME': 'Windows',
                   'OS_ARCH': 'aarch64', 'IMAGE_TYPE': 'JRE'}
        for key, value in invalid.items():
            data = self.jdk_archive({**JDK_RELEASE, key: value})
            self.pin_jdk(data)
            runtime = self.root / key
            runtime.mkdir()
            with self.subTest(key=key), patch.object(ci.urllib.request, 'urlopen', return_value=Response(data, JDK_URL)), \
                    self.assertRaisesRegex(RuntimeError, 'Extracted JDK identity differs'):
                ci.provision_java(self.project, runtime)

    def test_provision_rejects_paths_links_duplicates_and_nonexecutable_tools(self):
        extras = [tarfile.TarInfo('../outside'), tarfile.TarInfo('/absolute'),
                  tarfile.TarInfo('other-root/entry'), tarfile.TarInfo(JDK_DIRECTORY + '/release')]
        link = tarfile.TarInfo(JDK_DIRECTORY + '/escape')
        link.type, link.linkname = tarfile.SYMTYPE, '../../outside'
        extras.append(link)
        fifo = tarfile.TarInfo(JDK_DIRECTORY + '/fifo')
        fifo.type = tarfile.FIFOTYPE
        extras.append(fifo)
        for index, extra in enumerate(extras):
            data = self.jdk_archive(extra=extra)
            self.pin_jdk(data)
            runtime = self.root / f'unsafe-{index}'
            runtime.mkdir()
            with self.subTest(index=index), patch.object(ci.urllib.request, 'urlopen', return_value=Response(data, JDK_URL)), \
                    self.assertRaises((RuntimeError, tarfile.FilterError)):
                ci.provision_java(self.project, runtime)
        data = self.jdk_archive(tool_mode=0o644)
        self.pin_jdk(data)
        runtime = self.root / 'not-executable'
        runtime.mkdir()
        with patch.object(ci.urllib.request, 'urlopen', return_value=Response(data, JDK_URL)), \
                self.assertRaisesRegex(RuntimeError, 'not executable'):
            ci.provision_java(self.project, runtime)
        self.assertFalse((self.root / 'outside').exists())

    def test_provision_never_overwrites_existing_destination(self):
        self.pin_jdk(self.jdk_archive())
        runtime = self.root / 'runtime'
        self.put('jdk/sentinel', b'preserve', runtime)
        with patch.object(ci.urllib.request, 'urlopen') as opened, self.assertRaisesRegex(RuntimeError, 'already exists'):
            ci.provision_java(self.project, runtime)
        opened.assert_not_called()
        self.assertEqual((runtime / 'jdk/sentinel').read_bytes(), b'preserve')

    def test_consent_preserves_exact_bytes_and_does_not_overwrite(self):
        destination = self.root / 'eula.txt'
        digest = ci.materialize_consent(base64.b64encode(CONSENT).decode(), destination)
        self.assertEqual(destination.read_bytes(), CONSENT)
        self.assertEqual(digest, hashlib.sha256(CONSENT).hexdigest())
        with self.assertRaises(FileExistsError):
            ci.materialize_consent(base64.b64encode(CONSENT).decode(), destination)

    def test_missing_malformed_or_unaccepted_consent_never_becomes_acceptance(self):
        values = [None, '', 'not-base64!', base64.b64encode(b'eula=false\n').decode(),
                  base64.b64encode(b'# no acceptance\n').decode(), base64.b64encode(CONSENT).decode() + '\n']
        for index, value in enumerate(values):
            with self.subTest(index=index), self.assertRaises(RuntimeError):
                ci.materialize_consent(value, self.root / f'eula-{index}')

    def test_download_requires_https_and_exact_hash(self):
        data = b'pinned public artifact'
        destination = self.root / 'artifact.jar'
        with patch.object(ci.urllib.request, 'urlopen', return_value=Response(data)) as opened:
            ci.download_verified('https://piston-data.mojang.com/file', destination, hashlib.sha256(data).hexdigest())
            self.assertEqual(destination.read_bytes(), data)
            self.assertEqual(opened.call_args.kwargs['timeout'], 90)
        with patch.object(ci.urllib.request, 'urlopen', return_value=Response(b'changed')):
            with self.assertRaisesRegex(RuntimeError, 'SHA256 mismatch'):
                ci.download_verified('https://piston-data.mojang.com/file', self.root / 'bad.jar', hashlib.sha256(data).hexdigest())
        with patch.object(ci.urllib.request, 'urlopen') as opened:
            with self.assertRaises(RuntimeError):
                ci.download_verified('http://piston-data.mojang.com/file', self.root / 'http.jar', 'd' * 64)
            opened.assert_not_called()

    def test_download_rejects_https_to_http_redirect(self):
        with patch.object(ci.urllib.request, 'urlopen', return_value=Response(b'body', 'http://unexpected/file')):
            with self.assertRaisesRegex(RuntimeError, 'left HTTPS'):
                ci.download_verified('https://piston-data.mojang.com/file', self.root / 'redirect.jar', 'd' * 64)

    def test_clone_is_exact_clean_source_and_does_not_copy_origin_credentials(self):
        self.put('input.txt', b'committed bytes\n')
        def git(*args):
            return subprocess.check_output(['git', '-c', 'maintenance.auto=false', '-C', str(self.project), *args], stderr=subprocess.STDOUT)
        git('init', '-q')
        git('config', 'user.name', 'Fixture')
        git('config', 'user.email', 'fixture@example.invalid')
        git('config', 'http.https://github.com/.extraheader', 'test-only-do-not-copy')
        git('add', 'input.txt')
        git('commit', '-qm', 'fixture')
        revision = git('rev-parse', 'HEAD').decode().strip()
        git('checkout', '--detach', revision)
        destination = self.root / 'exact'
        identity = ci.clone_exact(self.project, destination, revision)
        self.assertEqual(identity['revision'], revision)
        self.assertEqual((destination / 'input.txt').read_bytes(), b'committed bytes\n')
        config = subprocess.check_output(['git', '-C', str(destination), 'config', '--local', '--list']).decode()
        self.assertNotIn('extraheader', config)
        self.put('input.txt', b'uncommitted')
        with self.assertRaisesRegex(RuntimeError, 'clean committed'):
            ci.clone_exact(self.project, self.root / 'dirty', revision)
        with self.assertRaisesRegex(RuntimeError, 'differs'):
            ci.clone_exact(self.project, self.root / 'wrong', SHA)

    def test_archive_preserves_required_profile_and_report_inputs_without_worlds_or_consent(self):
        required = [f'build/reports/agent-paper/{RUN}/result.json',
                    f'build/reports/agent-paper/{RUN}/scenario.json',
                    f'build/reports/agent-paper/{RUN}/player.json',
                    f'build/reports/agent-paper/{RUN}/server.log',
                    f'build/reports/agent-paper/{RUN}/build.log',
                    f'build/reports/agent-paper-suites/{SUITE}/receipt.json',
                    f'build/reports/agent-paper-suites/{SUITE}/positive/production/TEST-Core.xml']
        profile = f'run/agent-tests/{RUN}/'
        required += [profile + value for value in ('server.jar', 'server.properties', 'whitelist.json',
                     'player-plan.json', 'plugins/OnlyDragons.jar', 'plugins/OnlyDragonsGameTests.jar',
                     'cache/mojang_26.2.jar', 'player-client/OnlyDragonsPlayerClient.jar', 'player-client/dependency.jar')]
        for relative in required:
            self.put(relative)
        for relative in (profile + 'eula.txt', profile + 'agent-world-x/level.dat', profile + 'libraries/unused.jar',
                         '.git/config', '.symphony/github-token', '.gradle/cache.bin'):
            self.put(relative, b'not exported')
        self.assertEqual(ci.evidence_files(self.project), sorted(required))
        output = self.root / 'out'
        output.mkdir()
        ci.export_evidence(self.project, output, {'passed': False})
        with tarfile.open(output / 'evidence.tar.gz') as archive:
            self.assertEqual(sorted(archive.getnames()), sorted(required))
            regular = set()
            for member in archive:
                self.assertFalse(PurePosixPath(member.name).is_absolute())
                self.assertNotIn('..', PurePosixPath(member.name).parts)
                self.assertTrue(member.isfile() or member.islnk())
                self.assertFalse(member.issym())
                if member.islnk():
                    self.assertIn(member.linkname, regular)
                    self.assertFalse(PurePosixPath(member.linkname).is_absolute())
                    self.assertNotIn('..', PurePosixPath(member.linkname).parts)
                else:
                    regular.add(member.name)
                self.assertEqual(archive.extractfile(member).read(), b'evidence')
            extracted = self.root / 'extracted'
            extracted.mkdir()
            archive.extractall(extracted, filter='data')
            for relative in required:
                self.assertEqual((extracted / relative).read_bytes(), b'evidence')
                self.assertFalse((extracted / relative).is_symlink())
        manifest = json.loads((output / 'manifest.json').read_text())
        self.assertFalse(manifest['passed'])
        self.assertEqual(manifest['archiveSha256'], ci.paper_test.sha256(output / 'evidence.tar.gz'))
        self.assertEqual(manifest['evidenceStoredFileCount'], 1)
        self.assertEqual(manifest['evidenceStoredBytes'], len(b'evidence'))
        self.assertEqual(manifest['evidenceLogicalBytes'], len(required) * len(b'evidence'))

    def test_archive_rejects_unexpected_profile_names(self):
        self.put('run/agent-tests/human-profile/server.jar')
        with self.assertRaisesRegex(RuntimeError, 'Unexpected disposable profile'):
            ci.evidence_files(self.project)

    def test_archive_rejects_symlink_reports_portably(self):
        self.put(f'build/reports/agent-paper/{RUN}/server.log')
        original = Path.is_symlink
        with patch.object(Path, 'is_symlink', lambda path: path.name == 'server.log' or original(path)):
            with self.assertRaisesRegex(RuntimeError, 'Symlink report'):
                ci.evidence_files(self.project)

    def run_mocked(self, suite_exit=0, replay_exit=0, consent=True, existing=False, interrupt=False,
                   provision_error=None):
        runtime = self.root / 'runtime'
        if existing:
            runtime.mkdir()
            (runtime / 'sentinel').write_bytes(b'preserved')
        output = self.root / 'output'
        args = Namespace(checkout=self.project, revision=SHA, run_id='123', attempt='1', output=output)
        java = self.root / 'jdk'
        self.put('bin/java', root=java)
        self.put('release', b'JAVA_VERSION="25.0.4.1"\n', root=java)
        source = {'revision': SHA, 'sourceInputSha256': 'd' * 64}
        calls = []
        def clone(checkout, project, revision):
            project.mkdir()
            self.put('versions.properties', b'javaVersion=25\npaperUrl=https://example.invalid/paper.jar\npaperSha256=' + b'e' * 64 + b'\n', project)
            return source
        def download(url, path, expected):
            path.write_bytes(b'public artifact')
            return path
        def child(command, project, log):
            calls.append(command)
            log.write_text('child diagnostics', encoding='utf-8')
            if '--suite' in command:
                self.put(f'build/reports/agent-paper-suites/{SUITE}/receipt.json', b'{"state":"original"}', project)
                if interrupt:
                    raise KeyboardInterrupt('intentional test cancellation')
                return suite_exit
            return replay_exit
        environment = {'JAVA_HOME': str(java), 'GITHUB_ACTIONS': 'true', 'RUNNER_ENVIRONMENT': 'github-hosted',
                       'GITHUB_REPOSITORY': 'Kav-K/OnlyDragons', 'GITHUB_EVENT_NAME': 'workflow_dispatch',
                       'GITHUB_RUN_ID': '123', 'GITHUB_RUN_ATTEMPT': '1', 'GITHUB_SHA': SHA,
                       'GITHUB_WORKSPACE': str(self.project)}
        if consent:
            environment['ONLYDRAGONS_PAPER_EULA_BASE64'] = base64.b64encode(CONSENT).decode()
        with patch.object(ci.sys, 'platform', 'linux'), patch.object(ci, 'canonical_root', return_value=runtime), \
                patch.dict(ci.os.environ, environment, clear=True), patch.object(ci, 'clone_exact', side_effect=clone) as cloned, \
                patch.object(ci, 'provision_java', return_value=(java, {'version': JDK_VERSION}),
                             side_effect=provision_error), \
                patch.object(ci, 'download_verified', side_effect=download) as downloaded, \
                patch.object(ci.paper_bootstrap, 'inspect_launcher', return_value={
                    'mojangUrl': 'https://piston-data.mojang.com/file', 'mojangFileName': 'mojang_26.2.jar', 'mojangSha256': 'f' * 64}), \
                patch.object(ci.paper_suite, 'source_identity', return_value=source), \
                patch.object(ci.paper_suite, 'run_child', side_effect=child):
            status = ci.execute(args)
        manifest = json.loads((output / 'manifest.json').read_text())
        return status, manifest, calls, cloned.call_count, downloaded.call_count

    def test_success_requires_full_suite_then_separate_replay(self):
        status, manifest, calls, _, _ = self.run_mocked()
        self.assertEqual(status, 0)
        self.assertTrue(manifest['passed'])
        self.assertEqual(len(calls), 2)
        self.assertIn('--suite', calls[0])
        self.assertIn('all', calls[0])
        self.assertIn('--validate', calls[1])
        self.assertNotIn('--skip-build', calls[0])
        self.assertEqual(manifest['acceptedEulaSha256'], hashlib.sha256(CONSENT).hexdigest())
        self.assertEqual(manifest['receiptPath'], f'build/reports/agent-paper-suites/{SUITE}/receipt.json')

    def test_failed_suite_exports_failure_and_never_promotes_it(self):
        status, manifest, calls, _, _ = self.run_mocked(suite_exit=1)
        self.assertEqual(status, 1)
        self.assertFalse(manifest['passed'])
        self.assertEqual(len(calls), 1)
        self.assertIsNone(manifest['replayExitCode'])
        self.assertGreater(manifest['evidenceFileCount'], 0)

    def test_replay_failure_prevents_pass(self):
        status, manifest, calls, _, _ = self.run_mocked(replay_exit=1)
        self.assertEqual(status, 1)
        self.assertFalse(manifest['passed'])
        self.assertEqual(len(calls), 2)
        self.assertEqual(manifest['replayExitCode'], 1)

    def test_jdk_provision_failure_exports_reason_without_any_paper_download_or_launch(self):
        status, manifest, calls, clones, downloads = self.run_mocked(
            provision_error=RuntimeError('JDK archive SHA256 mismatch'))
        self.assertEqual(status, 1)
        self.assertFalse(manifest['passed'])
        self.assertIn('JDK archive SHA256 mismatch', manifest['error'])
        self.assertEqual((clones, downloads, calls), (1, 0, []))
        self.assertIsNone(manifest['suiteExitCode'])
        self.assertIsNone(manifest['replayExitCode'])

    def test_cancellation_exports_partial_evidence_as_failure(self):
        status, manifest, calls, _, _ = self.run_mocked(interrupt=True)
        self.assertEqual(status, 1)
        self.assertFalse(manifest['passed'])
        self.assertIn('cancellation', manifest['error'])
        self.assertGreater(manifest['evidenceFileCount'], 0)

    def test_missing_consent_does_not_clone_download_or_launch(self):
        status, manifest, calls, clones, downloads = self.run_mocked(consent=False)
        self.assertEqual(status, 1)
        self.assertFalse(manifest['passed'])
        self.assertEqual((clones, downloads, calls), (0, 0, []))

    def test_existing_run_is_preserved_and_not_exported_as_new_evidence(self):
        status, manifest, calls, clones, downloads = self.run_mocked(existing=True)
        self.assertEqual(status, 1)
        self.assertFalse(manifest['passed'])
        self.assertEqual(manifest['evidenceFileCount'], 0)
        self.assertEqual((self.root / 'runtime/sentinel').read_bytes(), b'preserved')
        self.assertEqual((clones, downloads, calls), (0, 0, []))

    def test_windows_actual_execution_fails_before_any_workspace_mutation(self):
        args = Namespace(checkout=self.project, revision=SHA, run_id='123', attempt='1', output=self.root / 'output')
        with patch.object(ci.sys, 'platform', 'win32'), self.assertRaisesRegex(RuntimeError, 'requires Linux'):
            ci.execute(args)
        self.assertFalse(args.output.exists())

    def test_non_hosted_or_wrong_job_context_cannot_create_an_independent_local_lease(self):
        args = Namespace(checkout=self.project, revision=SHA, run_id='123', attempt='1', output=self.root / 'output')
        correct = {'GITHUB_ACTIONS': 'true', 'RUNNER_ENVIRONMENT': 'github-hosted',
                   'GITHUB_REPOSITORY': 'Kav-K/OnlyDragons', 'GITHUB_EVENT_NAME': 'workflow_dispatch',
                   'GITHUB_RUN_ID': '123', 'GITHUB_RUN_ATTEMPT': '1', 'GITHUB_SHA': SHA,
                   'GITHUB_WORKSPACE': str(self.project)}
        for key in correct:
            invalid = {**correct, key: 'different'}
            with self.subTest(key=key), patch.object(ci.sys, 'platform', 'linux'), \
                    patch.dict(ci.os.environ, invalid, clear=True), self.assertRaisesRegex(RuntimeError, 'manual GitHub-hosted'):
                ci.execute(args)
            self.assertFalse(args.output.exists())


if __name__ == '__main__':
    unittest.main()
