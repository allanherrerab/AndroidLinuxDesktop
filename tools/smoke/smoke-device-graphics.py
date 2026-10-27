#!/usr/bin/env python3
"""Check a running debug/acceptance VirGL desktop without terminal injection.

Select VirGL and restart the desktop first. Runs the app's bounded mapped GL
probe in an isolated guest process; does not change settings or user files.
Optional --storage-fixture must name a caller-owned test file, not personal data.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shlex
import subprocess
import sys

PROJECT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial')
    parser.add_argument('--package', default='org.debiannoroot.acceptance')
    parser.add_argument('--storage-fixture')
    parser.add_argument('--output', type=Path, default=PROJECT / 'build/device-graphics.json')
    args = parser.parse_args()
    adb = ['adb'] + (['-s', args.serial] if args.serial else [])

    def shell(*words):
        return subprocess.check_output(adb + ['shell', shlex.join(words)], text=True, timeout=30)

    def app(*words):
        return shell('run-as', args.package, *words)

    guest = [sys.executable, str(PROJECT / 'tools/device/device-guest.py'), '--package', args.package]
    if args.serial:
        guest += ['--serial', args.serial]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    result = {'package': args.package, 'pageSize': int(shell('getconf', 'PAGE_SIZE'))}
    try:
        status = app('cat', 'files/rootfs/tmp/debian-noroot-graphics-status').strip()
        assert status == 'virpipe', 'Desktop fell back to ' + status
        processes = shell('ps', '-A', '-o', 'USER,PID,ARGS').splitlines()
        uid = app('id', '-un').strip()
        owned = [line for line in processes if line.split() and line.split()[0] == uid]
        server = [line for line in owned if 'libvirgl_server.so' in line]
        assert len(server) == 1, 'Expected exactly one session-owned VirGL server'
        desktops = [line.split()[1] for line in owned if line.rstrip().endswith('xfce4-session')]
        assert len(desktops) == 1, 'Expected one XFCE desktop'
        environment = app('cat', '/proc/' + desktops[0] + '/environ').split('\0')
        assert 'GALLIUM_DRIVER=virpipe' in environment, 'XFCE did not inherit Virpipe'
        assert 'VTEST_SOCKET_NAME=/tmp/.virgl_test' in environment
        probe = subprocess.run(guest + ['--', 'env', 'LIBGL_ALWAYS_SOFTWARE=1',
                               'GALLIUM_DRIVER=virpipe', 'LIBGL_DRI3_DISABLE=1',
                               'VTEST_SOCKET_NAME=/tmp/.virgl_test',
                               '/usr/local/bin/debian-noroot-probe-graphics'], timeout=30)
        log = app('cat', 'files/rootfs/tmp/debian-noroot-graphics-probe.log')
        args.output.with_suffix('.log').write_text(log)
        assert probe.returncode == 0, 'Mapped GL workload failed; see saved probe log'
        result['renderer'] = next(line.split('=', 1)[1].strip() for line in log.splitlines()
                                  if line.startswith('GL_RENDERER'))
        result['probe'] = 'renderer query or private socket readiness'
        if args.storage_fixture:
            assert args.storage_fixture.startswith('/storage/'), 'Use a fixture under /storage/'
            data = subprocess.check_output(guest + ['--host-storage', '--', 'cat', args.storage_fixture], timeout=30)
            result['storageFixture'] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
        args.output.with_suffix('.png').write_bytes(subprocess.check_output(adb + ['exec-out', 'screencap', '-p'], timeout=30))
        result['passed'] = True
    finally:
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
