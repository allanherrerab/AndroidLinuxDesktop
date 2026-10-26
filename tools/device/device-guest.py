#!/usr/bin/env python3
"""Run an isolated command in an installed debug app's guest via ADB.

Does not type into a terminal, alter preferences, or disable PRoot seccomp.
The caller owns any processes/files created by the supplied command.
"""
import argparse
import os
from pathlib import Path
import shlex
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'))
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--package', default='org.debiannoroot')
    parser.add_argument('--host-storage', action='store_true', help='Mirror the /storage bind; Android must have granted access')
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command
    if command[:1] == ['--']:
        command = command[1:]
    if not command:
        parser.error('supply a guest program and its arguments after --')
    adb = [args.adb] + (['-s', args.serial] if args.serial else [])

    def read(*words):
        return subprocess.check_output(adb + ['shell', shlex.join(words)], text=True).strip()

    package = args.package
    apk = read('pm', 'path', package).splitlines()[0].removeprefix('package:')
    if not apk.startswith('/'):
        parser.error('debug app is not installed on the selected device')
    runtime = str(Path(apk).parent / 'lib/arm64')
    uid = read('run-as', package, 'id', '-u')
    files = '/data/user/0/' + package + '/files'
    words = [
        'run-as', package, '/system/bin/env', 'LD_LIBRARY_PATH=' + runtime,
        'PROOT_TMP_DIR=' + files + '/proot-tmp',
        'PROOT_LOADER=' + runtime + '/libproot-loader.so',
        'PROOT_EXEC=' + runtime + '/libproot.so',
        '/system/bin/sh', '-c', 'exec "$PROOT_EXEC" "$@"', 'proot',
        '--link2symlink', '--kill-on-exit', '--sysvipc',
        '-i', uid + ':' + uid, '-r', files + '/rootfs', '-w', '/home/debian',
        '-b', '/dev', '-b', '/proc', '-b', '/sys', '-b', '/system',
        '-b', files + '/shared-memory:/dev/shm',
        '-b', files + '/proc-version:/proc/version',
        *(['-b', '/storage:/storage'] if args.host_storage else []),
        '/usr/bin/env', '-i', 'HOME=/home/debian', 'USER=debian', 'LOGNAME=debian',
        'LANG=C.UTF-8', 'SHELL=/bin/bash',
        'PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',
        'DISPLAY=:0', 'XAUTHORITY=/tmp/debian-noroot.Xauthority',
        'PULSE_SERVER=unix:/tmp/debian-noroot-pulse.sock',
        'XDG_RUNTIME_DIR=/tmp/runtime-debian', *command,
    ]
    return subprocess.call(adb + ['shell', '-T', shlex.join(words)])


if __name__ == '__main__':
    raise SystemExit(main())
