"""Bootstrap provenance and isolated staging regressions; no Java or network."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

spec = importlib.util.spec_from_file_location('paper_bootstrap', Path(__file__).with_name('paper_bootstrap.py'))
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)


def digest(value):
    return hashlib.sha256(value).hexdigest()


class BootstrapTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.project = self.root / 'issue'
        self.project.mkdir()
        self.paper = self.root / 'operator/run/servers/26.2-dev/server.jar'
        self.paper.parent.mkdir(parents=True)
        self.mojang = self.root / 'mojang.jar'
        self.mojang.write_bytes(b'verified upstream server bundle')
        self.name = 'mojang_26.2.jar'
        self.url = 'https://piston-data.mojang.com/v1/objects/' + 'a' * 40 + '/server.jar'
        self.raw = f'{digest(self.mojang.read_bytes())}\t{self.url}\t{self.name}'.encode()
        self.make_launcher(self.raw)

    def make_launcher(self, raw, duplicate=False):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.paper, 'w') as archive:
                archive.writestr('META-INF/download-context', raw)
                if duplicate:
                    archive.writestr('META-INF/download-context', raw)
        self.pin = digest(self.paper.read_bytes())
        return self.pin

    def metadata(self):
        return bootstrap.inspect_launcher(self.paper, self.pin)

    def profile(self):
        result = self.project / 'run/agent-tests/fresh'
        result.mkdir(parents=True)
        shutil.copyfile(self.paper, result / 'server.jar')
        return result

    def copy_cache(self, path, data=None):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(self.mojang.read_bytes() if data is None else data)
        return path

    def link(self, target, path, directory=False):
        try:
            path.symlink_to(target, target_is_directory=directory)
        except (OSError, NotImplementedError) as error:
            self.skipTest(f'Host cannot create test symlinks: {type(error).__name__}')

    def test_pinned_metadata_has_exact_provenance(self):
        value = self.metadata()
        self.assertEqual(value['paperSha256'], self.pin)
        self.assertEqual(value['downloadContextSha256'], digest(self.raw))
        self.assertEqual(value['mojangSha256'], digest(self.mojang.read_bytes()))
        self.assertEqual(value['mojangFileName'], self.name)
        self.assertEqual(value['mojangUrl'], self.url)

    def test_wrong_launcher_pin_fails_before_metadata_is_used(self):
        with self.assertRaisesRegex(bootstrap.BootstrapError, 'checksum'):
            bootstrap.inspect_launcher(self.paper, 'f' * 64)

    def test_metadata_rejects_duplicates_extra_records_and_oversize(self):
        for raw, duplicate in ((self.raw, True), (self.raw + b'\n' + self.raw, False),
                               (b'x' * 4097, False), (b'', False)):
            with self.subTest(rawLength=len(raw), duplicate=duplicate):
                self.make_launcher(raw, duplicate)
                with self.assertRaises(bootstrap.BootstrapError):
                    self.metadata()

    def test_metadata_rejects_non_utf8_and_missing_fields(self):
        for raw in (b'\xff', b'one\ttwo', self.raw + b'\x00', self.raw + b'\r\n'):
            with self.subTest(raw=raw):
                self.make_launcher(raw)
                with self.assertRaises(bootstrap.BootstrapError):
                    self.metadata()

    def test_metadata_rejects_unsafe_filename_and_url(self):
        for name in ('../mojang.jar', '/mojang.jar', 'mojang_../outside.jar',
                     'mojang_..jar', r'mojang_26.2\outside.jar', 'world.jar'):
            with self.subTest(name=name):
                self.make_launcher(f'{digest(self.mojang.read_bytes())}\t{self.url}\t{name}'.encode())
                with self.assertRaises(bootstrap.BootstrapError):
                    self.metadata()
        for url in ('http://piston-data.mojang.com/a', 'https://untrusted.invalid/server.jar',
                    self.url + '?secret=value', self.url + '#fragment',
                    self.url.replace('https://', 'https://user:pass@'),
                    self.url.replace('mojang.com/', 'mojang.com:443/')):
            with self.subTest(url=url):
                self.make_launcher(f'{digest(self.mojang.read_bytes())}\t{url}\t{self.name}'.encode())
                with self.assertRaises(bootstrap.BootstrapError):
                    self.metadata()

    def test_launcher_change_between_hash_and_metadata_read_fails(self):
        original = bootstrap._verified_file
        count = 0

        def changing(path, expected, label):
            nonlocal count
            count += 1
            result = original(path, expected, label)
            if count == 1:
                self.make_launcher(self.raw + b'\n')
            return result

        pin = self.pin
        with patch.object(bootstrap, '_verified_file', side_effect=changing):
            with self.assertRaisesRegex(bootstrap.BootstrapError, 'checksum'):
                bootstrap.inspect_launcher(self.paper, pin)

    def test_explicit_file_and_environment_are_verified_and_take_precedence(self):
        meta = self.metadata()
        selected = bootstrap.resolve_mojang(self.project, self.paper, meta,
                                            supplied=self.mojang,
                                            environ={'ONLYDRAGONS_TEST_MOJANG_JAR': str(self.root / 'missing')})
        self.assertEqual(selected, self.mojang)
        self.assertEqual(bootstrap.resolve_mojang(self.project, self.paper, meta,
                         environ={'ONLYDRAGONS_TEST_MOJANG_JAR': str(self.mojang)}), self.mojang)

    def test_invalid_explicit_override_never_falls_back(self):
        self.copy_cache(self.paper.parent / 'cache' / self.name)
        for value in ('', str(self.root / 'missing'), str(self.root)):
            with self.subTest(value=value):
                with self.assertRaises(bootstrap.BootstrapError):
                    bootstrap.resolve_mojang(self.project, self.paper, self.metadata(),
                                             environ={'ONLYDRAGONS_TEST_MOJANG_JAR': value})

    def test_issue_hash_cache_is_used_before_operator_cache(self):
        meta = self.metadata()
        expected = self.copy_cache(self.project / 'run/agent-cache' / f"mojang-{meta['mojangSha256']}.jar")
        self.copy_cache(self.paper.parent / 'cache' / self.name)
        self.assertEqual(bootstrap.resolve_mojang(self.project, self.paper, meta, environ={}), expected)

    def test_invalid_automatic_cache_can_fall_back_to_valid_launcher_sibling(self):
        self.copy_cache(self.project / 'run/agent-cache' / self.name, b'corrupt')
        expected = self.copy_cache(self.paper.parent / 'cache' / self.name)
        self.assertEqual(bootstrap.resolve_mojang(self.project, self.paper, self.metadata(), environ={}), expected)

    def test_named_source_cache_is_discovered_without_user_path_constants(self):
        source = self.root / 'different-operator'
        expected = self.copy_cache(source / 'run/servers/26.2-smoke/cache' / self.name)
        self.assertEqual(bootstrap.resolve_mojang(self.project, self.paper, self.metadata(),
                         environ={'ONLYDRAGONS_SOURCE': str(source)}), expected)

    def test_launcher_repository_ancestor_is_discovered(self):
        source = self.root / 'operator'
        (source / 'AGENTS.md').write_text('fixture')
        (source / 'versions.properties').write_text('fixture')
        expected = self.copy_cache(source / 'run/servers/26.2-smoke/cache' / self.name)
        self.assertEqual(bootstrap.resolve_mojang(self.project, self.paper, self.metadata(), environ={}), expected)

    def test_missing_cache_fails_without_network_or_profile_mutation(self):
        before = sorted(str(p.relative_to(self.project)) for p in self.project.rglob('*'))
        with self.assertRaisesRegex(bootstrap.BootstrapError, 'No verified local'):
            bootstrap.resolve_mojang(self.project, self.paper, self.metadata(), environ={})
        self.assertEqual(before, sorted(str(p.relative_to(self.project)) for p in self.project.rglob('*')))

    def test_stage_and_replay_copy_only_the_verified_bootstrap_file(self):
        profile = self.profile()
        marker = profile / 'world-marker'
        marker.write_text('untouched')
        manifest = bootstrap.stage_bootstrap(profile, self.mojang, self.metadata())
        self.assertEqual(manifest, bootstrap.validate_bootstrap(profile, json.loads(json.dumps(manifest)), self.pin))
        self.assertEqual(manifest['stagedRelativePath'], 'cache/' + self.name)
        self.assertEqual(sorted(p.name for p in (profile / 'cache').iterdir()), [self.name])
        self.assertEqual(marker.read_text(), 'untouched')
        self.assertEqual(self.mojang.read_bytes(), (profile / 'cache' / self.name).read_bytes())

    def test_stage_rejects_stale_metadata_and_changed_source(self):
        meta = self.metadata()
        profile = self.profile()
        with self.assertRaisesRegex(bootstrap.BootstrapError, 'metadata changed'):
            bootstrap.stage_bootstrap(profile, self.mojang, dict(meta, mojangFileName='mojang_other.jar'))
        self.mojang.write_bytes(b'changed after resolution')
        with self.assertRaisesRegex(bootstrap.BootstrapError, 'checksum'):
            bootstrap.stage_bootstrap(profile, self.mojang, meta)
        self.assertFalse((profile / 'cache').exists())

    def test_corrupted_copy_is_rejected_and_not_left_as_valid_evidence(self):
        profile = self.profile()

        def corrupt(reader, writer):
            writer.write(b'changed bytes during copy')

        with patch.object(bootstrap.shutil, 'copyfileobj', side_effect=corrupt):
            with self.assertRaisesRegex(bootstrap.BootstrapError, 'checksum'):
                bootstrap.stage_bootstrap(profile, self.mojang, self.metadata())
        self.assertFalse((profile / 'cache' / self.name).exists())

    def test_existing_cache_cannot_be_reused_or_overwritten(self):
        profile = self.profile()
        expected = self.copy_cache(profile / 'cache' / self.name, b'existing-run')
        with self.assertRaisesRegex(bootstrap.BootstrapError, 'fresh'):
            bootstrap.stage_bootstrap(profile, self.mojang, self.metadata())
        self.assertEqual(expected.read_bytes(), b'existing-run')

    def test_exclusive_copy_never_removes_a_preexisting_destination(self):
        destination = self.root / 'someone-elses-file.jar'
        destination.write_bytes(b'preserved')
        with self.assertRaises(FileExistsError):
            bootstrap._copy_verified(self.mojang, destination, digest(self.mojang.read_bytes()))
        self.assertEqual(destination.read_bytes(), b'preserved')

    def test_replay_rejects_missing_forged_stale_and_unsafe_manifests(self):
        profile = self.profile()
        manifest = bootstrap.stage_bootstrap(profile, self.mojang, self.metadata())
        for value in (None, {}, dict(manifest, schemaVersion=True), dict(manifest, schemaVersion=2),
                      dict(manifest, stagedRelativePath='../outside'), dict(manifest, mojangSha256='f' * 64),
                      dict(manifest, downloadContextSha256='f' * 64), dict(manifest, extra='unreviewed')):
            with self.subTest(manifest=value):
                with self.assertRaises(bootstrap.BootstrapError):
                    bootstrap.validate_bootstrap(profile, value, self.pin)

    def test_replay_rejects_changed_or_missing_staged_bytes(self):
        profile = self.profile()
        manifest = bootstrap.stage_bootstrap(profile, self.mojang, self.metadata())
        staged = profile / 'cache' / self.name
        staged.write_bytes(b'changed staged jar')
        with self.assertRaisesRegex(bootstrap.BootstrapError, 'checksum'):
            bootstrap.validate_bootstrap(profile, manifest, self.pin)
        staged.unlink()
        with self.assertRaises(bootstrap.BootstrapError):
            bootstrap.validate_bootstrap(profile, manifest, self.pin)

    def test_replay_rejects_changed_launcher(self):
        profile = self.profile()
        manifest = bootstrap.stage_bootstrap(profile, self.mojang, self.metadata())
        (profile / 'server.jar').write_bytes(b'wrong launcher')
        with self.assertRaisesRegex(bootstrap.BootstrapError, 'checksum'):
            bootstrap.validate_bootstrap(profile, manifest, self.pin)

    def test_explicit_symlink_is_rejected(self):
        alias = self.root / 'alias.jar'
        self.link(self.mojang, alias)
        with self.assertRaises(bootstrap.BootstrapError):
            bootstrap.resolve_mojang(self.project, self.paper, self.metadata(), supplied=alias, environ={})

    def test_staging_and_replay_reject_cache_redirection(self):
        profile = self.profile()
        outside = self.root / 'outside'
        outside.mkdir()
        self.link(outside, profile / 'cache', directory=True)
        with self.assertRaises(bootstrap.BootstrapError):
            bootstrap.stage_bootstrap(profile, self.mojang, self.metadata())
        with self.assertRaises(bootstrap.BootstrapError):
            bootstrap.validate_bootstrap(profile, bootstrap._manifest(self.metadata()), self.pin)
        self.assertEqual(list(outside.iterdir()), [])


if __name__ == '__main__':
    unittest.main()
