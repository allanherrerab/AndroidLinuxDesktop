import importlib.util
from pathlib import Path
import unittest

PROJECT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('multi', PROJECT / 'tools/smoke/smoke-device-virgl-multiclient.py')
multi = importlib.util.module_from_spec(spec)
spec.loader.exec_module(multi)


class MultiClientEvidenceTest(unittest.TestCase):
    def evidence(self, kill=False):
        return ''.join(
            f'{name.upper()}_EXIT={137 if kill and name == "peer" else 124}\n'
            f'BEGIN_LOG={name}\nGL_RENDERER = virgl (device GPU)\n'
            + '100 frames in 5.0 seconds\n' * samples + f'END_LOG={name}\n'
            for name, samples in [('survivor', 6), ('peer', 1), ('reconnect', 1)])

    def test_term_and_kill_expect_different_peer_exit(self):
        for kill in (False, True):
            checks = multi.assess_clients(self.evidence(kill), kill)
            self.assertTrue(all(c['passed'] for c in checks.values()))
            self.assertFalse(multi.assess_clients(self.evidence(kill), not kill)['peer']['passed'])

    def test_frames_before_crash_do_not_pass(self):
        output = self.evidence().replace('SURVIVOR_EXIT=124', 'SURVIVOR_EXIT=134')
        self.assertFalse(multi.assess_clients(output)['survivor']['passed'])
        output = self.evidence().replace('END_LOG=survivor', 'lost connection to rendering server\nEND_LOG=survivor')
        self.assertFalse(multi.assess_clients(output)['survivor']['passed'])

    def test_missing_or_software_frames_do_not_pass(self):
        for output in ('', self.evidence().replace('100 frames', '0 frames'),
                       self.evidence().replace('device GPU', 'SwiftShader')):
            self.assertFalse(any(c['passed'] for c in multi.assess_clients(output).values()))
