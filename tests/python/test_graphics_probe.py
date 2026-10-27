import os
from pathlib import Path
import subprocess
import tempfile
import unittest

PROJECT = Path(__file__).resolve().parents[2]
PROBE = PROJECT / "assets/rootfs/bin/debian-noroot-probe-graphics"


class GraphicsProbeTest(unittest.TestCase):
    def probe(self, output, status):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            # Fake the bounded renderer query, not its validation logic.
            timeout = root / "timeout"
            timeout.write_text('#!/bin/sh\nprintf "%s\\n" "$PROBE_OUTPUT"\nexit "$PROBE_STATUS"\n')
            timeout.chmod(0o700)
            result = subprocess.run(['sh', str(PROBE), str(root / 'probe.log')],
                                    env={**os.environ, 'PATH': directory + ':' + os.environ['PATH'],
                                         'PROBE_OUTPUT': output, 'PROBE_STATUS': str(status)}, timeout=5)
            self.assertEqual((root / 'probe.log').read_text().strip(), output)
            return result.returncode

    def test_hardware_renderer(self):
        for status in (0, 124):
            self.assertEqual(self.probe('GL_RENDERER = virgl (device GPU)', status), 0)

    def test_software_renderer_rejected(self):
        for output in ('GL_RENDERER = llvmpipe',
                       'GL_RENDERER = virgl (SwiftShader)',
                       'GL_RENDERER = software rasterizer'):
            self.assertNotEqual(self.probe(output, 0), 0)

    def test_crash_rejected_even_after_frames(self):
        self.assertNotEqual(self.probe('GL_RENDERER = virgl (device GPU)', 139), 0)
