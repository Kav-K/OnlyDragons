"""Preflight must distinguish missing access and temporary resource contention."""
from pathlib import Path
from contextlib import contextmanager
import tempfile
import unittest
from unittest.mock import patch

import doctor


class DoctorTests(unittest.TestCase):
    def test_missing_context_and_consent_are_unavailable_not_test_passes(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(doctor.runner, 'available_memory', return_value={}):
            result = doctor.inspect(Path(directory), None, None, None)
        self.assertEqual(result['state'], 'unavailable')
        self.assertTrue(any('sharedContextAndFixtures' in message for message in result['errors']))
        self.assertTrue(any('eula' in message for message in result['errors']))
        self.assertNotIn('passed', result)

    def test_contended_lease_is_waiting_and_probe_is_removed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            # Keep other checks independent; assert the actual lease and probe lifecycle.
            with (patch.object(doctor.runner, 'server_lease', side_effect=doctor.runner.ResourceBusy('Busy: lease')),
                  patch.object(doctor.runner, 'available_memory', return_value={})):
                result = doctor.inspect(root, None, None, root)
            self.assertEqual(result['checks']['sharedLease'], 'waiting')
            self.assertTrue(result['waiting'])
            self.assertEqual(list(root.iterdir()), [])

    def test_preflight_releases_its_lease_and_does_not_start_paper(self):
        events = []
        @contextmanager
        def lease(*args):
            events.append('acquired')
            try:
                yield
            finally:
                events.append('released')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(doctor.runner, 'available_memory', return_value={}), patch.object(doctor.subprocess, 'run') as process, patch.object(doctor.runner, 'server_lease', lease):
                result = doctor.inspect(root, None, None, root)
                process.assert_not_called()
            self.assertEqual(result['checks']['sharedLease'], 'writable; lease available')
            self.assertEqual(events, ['acquired', 'released'])
            self.assertEqual(list(root.iterdir()), [])


if __name__ == '__main__':
    unittest.main()
