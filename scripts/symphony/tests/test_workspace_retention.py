"""Terminal retention tests use disposable directories, never real workspaces."""
import errno
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / 'retain-workspace.py'
spec = importlib.util.spec_from_file_location('workspace_retention', SCRIPT)
retention = importlib.util.module_from_spec(spec)
spec.loader.exec_module(retention)


class RetentionTests(unittest.TestCase):
    """Prove whole-clone retention preserves bytes/history/evidence and rejects redirected ownership.

    Only disposable directories are moved. Tests prevent copy/delete fallbacks and
    check post-rename metadata failure without touching actual Symphony workspaces.
    """
    def setUp(self):
        """Create a temporary issue tree containing Git, dirty/untracked source and ignored evidence."""
        self.temp = tempfile.TemporaryDirectory(prefix='symphony-retention-')
        self.addCleanup(self.temp.cleanup)
        self.source = Path(self.temp.name).resolve()
        self.workspace = self.source / '.symphony/workspaces/GH-17'
        self.workspace.mkdir(parents=True)
        self.files = {
            '.git/index': b'git metadata',
            'src/source.java': b'dirty tracked source',
            'notes/untracked.txt': b'untracked source',
            'run/agent-tests/run/world/region/r.0.0.mca': b'ignored world',
            'build/reports/agent-paper/run/result.json': b'ignored raw evidence',
            '.symphony/gradle/cache.bin': b'ignored dependency cache',
            '.gitignore': b'run/\nbuild/\n.symphony/\n',
        }
        for name, contents in self.files.items():
            file = self.workspace / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(contents)

    def retained(self):
        """Invoke the actual retention rename with the test-owned source and issue directory."""
        return retention.retain_workspace(self.source, self.workspace)

    def assert_preserved(self, path):
        """Compare the retained tree's entire file set and bytes against the original fixture."""
        self.assertEqual({p.relative_to(path).as_posix(): p.read_bytes()
                          for p in path.rglob('*') if p.is_file()}, self.files)

    def test_whole_tree_and_ignored_evidence_move_without_copy_or_recursive_delete(self):
        inode = (self.workspace / '.git/index').stat().st_ino
        with patch.object(shutil, 'copytree', side_effect=AssertionError('Must not copy')), \
                patch.object(shutil, 'rmtree', side_effect=AssertionError('Must not recursively delete')):
            destination = self.retained()
        self.assertFalse(self.workspace.exists())
        self.assert_preserved(destination)
        self.assertEqual((destination / '.git/index').stat().st_ino, inode)
        self.assertEqual(destination.parent.parent, self.source / '.symphony/results/GH-17')
        manifest = json.loads((destination.parent / 'retention.json').read_text())
        self.assertEqual(manifest['originalWorkspace'], str(self.workspace))
        self.assertEqual(manifest['retainedWorkspace'], str(destination))
        self.assertEqual(manifest['method'], 'same-filesystem-rename')
        self.assertIs(manifest['includesIgnoredFiles'], True)

    def test_partial_workspace_without_git_is_still_retained(self):
        (self.workspace / '.git/index').unlink()
        (self.workspace / '.git').rmdir()
        self.files.pop('.git/index')
        self.assert_preserved(self.retained())

    def test_repeated_issue_archivals_get_distinct_destinations_without_overwriting(self):
        first = self.retained()
        self.workspace.mkdir()
        (self.workspace / 'new.txt').write_text('later attempt')
        second = self.retained()
        self.assertNotEqual(first, second)
        self.assert_preserved(first)
        self.assertEqual((second / 'new.txt').read_text(), 'later attempt')
        with self.assertRaises((ValueError, OSError)):
            self.retained()
        self.assertTrue(first.is_dir())
        self.assertTrue(second.is_dir())

    def test_source_workspace_root_nested_and_outside_paths_are_rejected(self):
        outside = self.source / 'other-project'
        outside.mkdir()
        nested = self.workspace / 'nested'
        nested.mkdir()
        for workspace in (self.source, self.workspace.parent, outside, nested, Path('relative')):
            with self.subTest(workspace=workspace), self.assertRaises((ValueError, OSError)):
                retention.retain_workspace(self.source, workspace)
        nested.rmdir()
        self.assert_preserved(self.workspace)

    def test_missing_file_or_relative_source_cannot_redirect_retention(self):
        file = self.source / 'file'
        file.write_text('unchanged')
        for source in (Path('.'), self.source / 'missing', file):
            with self.subTest(source=source), self.assertRaises((ValueError, OSError)):
                retention.retain_workspace(source, self.workspace)
        self.assert_preserved(self.workspace)
        self.assertEqual(file.read_text(), 'unchanged')

    def test_file_destination_roots_are_rejected(self):
        results = self.source / '.symphony/results'
        results.write_text('existing file')
        with self.assertRaises(ValueError):
            self.retained()
        self.assertEqual(results.read_text(), 'existing file')
        results.unlink()
        results.mkdir()
        (results / 'GH-17').write_text('existing issue file')
        with self.assertRaises(ValueError):
            self.retained()
        self.assert_preserved(self.workspace)

    def test_reserved_destination_collision_never_replaces_existing_content(self):
        archive = self.source / '.symphony/results/GH-17/reserved'
        (archive / 'workspace').mkdir(parents=True)
        (archive / 'workspace/sentinel').write_text('prior archive')
        with patch.object(retention.tempfile, 'mkdtemp', return_value=str(archive)):
            with self.assertRaises(FileExistsError):
                self.retained()
        self.assertEqual((archive / 'workspace/sentinel').read_text(), 'prior archive')
        self.assert_preserved(self.workspace)

    def test_different_filesystem_is_rejected_before_rename(self):
        original_stat = Path.stat
        def stat(path, *args, **kwargs):
            result = original_stat(path, *args, **kwargs)
            if path == self.workspace:
                fields = list(result)
                fields[2] += 1
                return os.stat_result(fields)
            return result
        with patch.object(Path, 'stat', stat), patch.object(retention.os, 'rename') as rename:
            with self.assertRaisesRegex(ValueError, 'same filesystem'):
                self.retained()
            rename.assert_not_called()
        self.assert_preserved(self.workspace)

    def test_atomic_rename_failure_preserves_source_without_copy_fallback(self):
        for code in (errno.EXDEV, errno.EACCES):
            with self.subTest(errno=code), patch.object(retention.os, 'rename', side_effect=OSError(code, 'fixture failure')):
                with self.assertRaises(OSError):
                    self.retained()
            self.assert_preserved(self.workspace)
        self.assertEqual(list((self.source / '.symphony/results').rglob('workspace')), [])

    def test_metadata_failure_after_rename_keeps_the_complete_retained_tree(self):
        original_open = Path.open
        def open_file(path, *args, **kwargs):
            if path.name == 'retention.json':
                raise OSError(errno.ENOSPC, 'fixture metadata failure')
            return original_open(path, *args, **kwargs)
        with patch.object(Path, 'open', open_file):
            with self.assertRaisesRegex(RuntimeError, 'Workspace retained at'):
                self.retained()
        self.assertFalse(self.workspace.exists())
        destinations = list((self.source / '.symphony/results').rglob('workspace'))
        self.assertEqual(len(destinations), 1)
        self.assert_preserved(destinations[0])

    @unittest.skipUnless(os.name == 'posix', 'Linux/WSL symlink containment regression.')
    def test_symlinked_source_workspace_and_destination_ancestors_are_rejected(self):
        alias = self.source / 'source-alias'
        alias.symlink_to(self.source, target_is_directory=True)
        with self.assertRaises(ValueError):
            retention.retain_workspace(alias, self.workspace)
        alias.unlink()
        alias = self.workspace.parent / 'GH-alias'
        alias.symlink_to(self.workspace, target_is_directory=True)
        with self.assertRaises(ValueError):
            retention.retain_workspace(self.source, alias)
        alias.unlink()
        outside = self.source / 'outside'
        outside.mkdir()
        for target in (outside, self.source / 'missing-target'):
            results = self.source / '.symphony/results'
            results.symlink_to(target, target_is_directory=True)
            with self.assertRaises(ValueError):
                self.retained()
            results.unlink()
        results.mkdir()
        (results / 'GH-17').symlink_to(outside, target_is_directory=True)
        with self.assertRaises(ValueError):
            self.retained()
        self.assertEqual(list(outside.iterdir()), [])
        self.assert_preserved(self.workspace)

    @unittest.skipUnless(os.name == 'posix', 'Linux/WSL parent symlink regression.')
    def test_symlinked_state_or_dispatch_root_is_rejected(self):
        state = self.source / '.symphony'
        moved = self.source / 'state-real'
        state.rename(moved)
        state.symlink_to(moved, target_is_directory=True)
        with self.assertRaises(ValueError):
            self.retained()
        state.unlink()
        moved.rename(state)
        workspaces = state / 'workspaces'
        moved = state / 'workspaces-real'
        workspaces.rename(moved)
        workspaces.symlink_to(moved, target_is_directory=True)
        with self.assertRaises(ValueError):
            self.retained()
        self.assertTrue((moved / 'GH-17/.git/index').is_file())

    @unittest.skipUnless(os.name == 'posix', 'Linux/WSL symlink preservation regression.')
    def test_links_inside_workspace_are_moved_without_following_their_targets(self):
        outside = self.source / 'external.txt'
        outside.write_text('unrelated')
        (self.workspace / 'external-link').symlink_to(outside)
        destination = self.retained()
        self.assertTrue((destination / 'external-link').is_symlink())
        self.assertEqual(outside.read_text(), 'unrelated')

    @unittest.skipUnless(os.name == 'posix', 'The actual Symphony hook runs under Linux/WSL.')
    def test_actual_shell_hook_moves_its_cwd_and_leaves_upstream_cleanup_path_absent(self):
        scripts = self.source / 'scripts/symphony'
        scripts.mkdir(parents=True)
        shutil.copyfile(SCRIPT, scripts / SCRIPT.name)
        hook = SCRIPT.with_name('archive-workspace.sh')
        result = subprocess.run(['bash', str(hook)], cwd=self.workspace,
                                env=dict(os.environ, ONLYDRAGONS_SOURCE=str(self.source)),
                                capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('Workspace retained at ', result.stdout)
        self.assertFalse(self.workspace.exists())
        destinations = list((self.source / '.symphony/results').rglob('workspace'))
        self.assertEqual(len(destinations), 1)
        self.assert_preserved(destinations[0])


if __name__ == '__main__':
    unittest.main()
