#!/usr/bin/env python3
"""Check actual Virpipe texture pixels on an already-running debug desktop.

Runs an isolated guest EGL pbuffer with generated textures. No window input,
preferences, package installation, or desktop restart. Requires guest Python 3,
libEGL and libGLESv2 (included in the image). Saves output even on failure.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys

PROJECT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial')
    parser.add_argument('--package', default='org.debiannoroot')
    parser.add_argument('--output', type=Path, default=PROJECT / 'build/virgl-textures')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    command = [sys.executable, str(PROJECT / 'tools/device/device-guest.py'),
               '--package', args.package]
    if args.serial:
        command += ['--serial', args.serial]
    source = (PROJECT / 'tests/support/virgl-textures.py').read_text()
    try:
        result = subprocess.run(command + ['--', 'env', 'LIBGL_ALWAYS_SOFTWARE=1',
            'GALLIUM_DRIVER=virpipe', 'LIBGL_DRI3_DISABLE=1',
            'VTEST_SOCKET_NAME=/tmp/.virgl_test', 'EGL_PLATFORM=surfaceless',
            'python3', '-c', source], capture_output=True, text=True, timeout=60)
        output = result.stdout + result.stderr
        passed = result.returncode == 0
    except subprocess.TimeoutExpired as error:
        output = (error.stdout or b'').decode() + (error.stderr or b'').decode()
        output += '\nTexture test timed out\n'
        passed = False
    (args.output / 'textures.log').write_text(output)
    print(output, end='')
    records = []
    for line in output.splitlines():
        if line.startswith('{'):
            records.append(json.loads(line))
    checks = {'passed': passed and any(r.get('passed') and 'cases' in r for r in records),
              'results': records}
    (args.output / 'result.json').write_text(json.dumps(checks, indent=2) + '\n')
    return 0 if checks['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
