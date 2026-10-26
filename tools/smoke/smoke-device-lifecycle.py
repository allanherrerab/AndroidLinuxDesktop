#!/usr/bin/env python3
"""Exercise a ready debug installation on an unlocked ADB device.

Stops/restarts the Debian session; save guest work before running. Never clears
app data or replaces the rootfs. Restores Android rotation preferences and
removes its own guest marker. Does not establish 16 KB support on a 4 KB device.
"""
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial')
    parser.add_argument('--package', default='org.debiannoroot')
    parser.add_argument('--cycles', type=int, default=5)
    parser.add_argument('--restarts', type=int, default=3)
    parser.add_argument('--output', type=Path, default=PROJECT / 'build/device-lifecycle.json')
    args = parser.parse_args()
    if args.cycles < 1 or args.restarts < 1:
        parser.error('cycles and restarts must be positive')
    adb = ['adb'] + (['-s', args.serial] if args.serial else [])

    def shell(*words):
        return subprocess.check_output(adb + ['shell', shlex.join(map(str, words))], text=True)

    def app(*words):
        return shell('run-as', args.package, *words)

    def wait_for(check, seconds=60):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if check():
                return
            time.sleep(.5)
        raise RuntimeError('Device did not reach the expected session state')

    def phase():
        root = ET.fromstring(app('cat', 'shared_prefs/session.xml'))
        return next((n.text for n in root if n.get('name') == 'phase'), '')

    labels = {'start': set(), 'stop': set()}
    for resource in (PROJECT / 'app/src/main/res').glob('values*/strings.xml'):
        for entry in ET.parse(resource).getroot():
            if entry.get('name') in labels and entry.text:
                labels[entry.get('name')].add(entry.text.casefold())

    dump_path = '/sdcard/noroot-lifecycle-' + uuid.uuid4().hex + '.xml'

    def click(action):
        shell('am', 'start', '--activity-clear-top', '-n',
              args.package + '/org.debiannoroot.MainActivity')
        shell('uiautomator', 'dump', dump_path)
        root = ET.fromstring(shell('cat', dump_path))
        node = next(n for n in root.iter('node') if n.get('text', '').casefold() in labels[action])
        x, y, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
        shell('input', 'tap', (x + right) // 2, (y + bottom) // 2)

    if 'mDreamingLockscreen=true' in shell('dumpsys', 'window'):
        raise RuntimeError('Unlock the device before testing')
    ready = 'files/rootfs/.debian-noroot-ready'
    identity = app('stat', '-c', '%i:%s:%Y', ready).strip()
    rotation = {key: shell('settings', 'get', 'system', key).strip()
                for key in ('accelerometer_rotation', 'user_rotation')}
    token = 'noroot-lifecycle-' + uuid.uuid4().hex
    marker = 'files/rootfs/home/debian/.' + token
    result = {'package': args.package, 'pageSize': int(shell('getconf', 'PAGE_SIZE')),
              'backgroundRotationCycles': 0, 'sessionRestarts': 0}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        app('sh', '-c', 'printf %s ' + shlex.quote(token) + ' > ' + shlex.quote(marker))
        click('start')
        wait_for(lambda: phase() == 'running')
        pid = shell('pidof', args.package + ':x11').strip()
        for index in range(args.cycles):
            shell('input', 'keyevent', 'KEYCODE_HOME')
            time.sleep(.5)
            click('start')
            shell('cmd', 'window', 'user-rotation', 'lock', index % 2)
            time.sleep(1)
            assert phase() == 'running', 'Reopening desktop changed the persisted session phase'
            assert shell('pidof', args.package + ':x11').strip() == pid, 'X11 restarted during rotation'
            result['backgroundRotationCycles'] += 1
        for _ in range(args.restarts):
            click('stop')
            wait_for(lambda: phase() == 'ended')
            for log in ('desktop.log', 'x11.log'):
                app('sh', '-c', 'printf %s ' + shlex.quote(token) + ' >> files/' + log)
            click('start')
            wait_for(lambda: phase() == 'running')
            for log in ('desktop.log', 'x11.log'):
                assert token not in app('cat', 'files/' + log), 'Old session log survived restart'
            assert app('cat', marker) == token, 'Guest home marker was changed'
            assert app('stat', '-c', '%i:%s:%Y', ready).strip() == identity, 'Ready rootfs was replaced'
            result['sessionRestarts'] += 1
        result.update(logReplacement=True, guestFilesPreserved=True, rootfsReused=True)
        args.output.with_suffix('.png').write_bytes(subprocess.check_output(adb + ['exec-out', 'screencap', '-p']))
    finally:
        for key, value in rotation.items():
            if value == 'null':
                shell('settings', 'delete', 'system', key)
            else:
                shell('settings', 'put', 'system', key, value)
        app('rm', '-f', marker)
        shell('rm', '-f', dump_path)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
