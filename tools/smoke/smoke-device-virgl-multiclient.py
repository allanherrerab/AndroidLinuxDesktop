#!/usr/bin/env python3
"""Exercise overlapping Virpipe clients in an already-running debug desktop.

Uses isolated guest commands, never an existing terminal. Leaves failure logs
in a unique guest /tmp directory and copies them to the host evidence folder.
Does not restart the desktop or change the user's graphics preferences.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

PROJECT = Path(__file__).resolve().parents[2]
SCRIPT = r'''
set -u
work=$(mktemp -d /tmp/noroot-virgl-multi.XXXXXX)
echo "EVIDENCE=$work"
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=virpipe LIBGL_DRI3_DISABLE=1
export VTEST_SOCKET_NAME=/tmp/.virgl_test
timeout 32 stdbuf -oL -eL glxgears -info -geometry 160x160+20+80 >"$work/survivor.log" 2>&1 &
survivor=$!
trap 'kill "$survivor" 2>/dev/null || true' EXIT
sleep 3
timeout -s "$1" 9 stdbuf -oL -eL glxgears -info -geometry 160x160+220+80 >"$work/peer.log" 2>&1
echo "PEER_EXIT=$?"
sleep 2
timeout 9 stdbuf -oL -eL glxgears -info -geometry 160x160+420+80 >"$work/reconnect.log" 2>&1
echo "RECONNECT_EXIT=$?"
wait "$survivor"
echo "SURVIVOR_EXIT=$?"
trap - EXIT
for name in survivor peer reconnect; do
    echo "BEGIN_LOG=$name"
    cat "$work/$name.log"
    echo "END_LOG=$name"
done
'''


def assess_clients(output, kill_peer=False):
    checks = {}
    for name, minimum in [('survivor', 5), ('peer', 1), ('reconnect', 1)]:
        match = re.search(r'BEGIN_LOG=' + name + r'\n(.*?)END_LOG=' + name,
                          output, re.S)
        log = match.group(1) if match else ''
        frames = re.findall(r'([1-9][0-9]*) frames in ', log)
        expected = 137 if name == 'peer' and kill_peer else 124
        checks[name] = {'samples': len(frames),
                        'passed': len(frames) >= minimum
                        and bool(re.search(r'GL_RENDERER\s*=\s*virgl', log))
                        and not re.search(r'llvmpipe|softpipe|swiftshader|lost connection', log, re.I)
                        and bool(re.search(r'^' + name.upper() + '_EXIT=' + str(expected) + r'$',
                                           output, re.M))}
    return checks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial')
    parser.add_argument('--package', default='org.debiannoroot')
    parser.add_argument('--kill-peer', action='store_true', help='Kill the peer with SIGKILL instead of SIGTERM')
    parser.add_argument('--output', type=Path, default=PROJECT / 'build/virgl-multiclient')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    command = [sys.executable, str(PROJECT / 'tools/device/device-guest.py'),
               '--package', args.package]
    if args.serial:
        command += ['--serial', args.serial]
    result = subprocess.run(command + ['--', 'bash', '-c', SCRIPT, 'virgl-test',
                                       'KILL' if args.kill_peer else 'TERM'],
                            capture_output=True, text=True, timeout=60)
    (args.output / 'clients.log').write_text(result.stdout + result.stderr)
    checks = assess_clients(result.stdout, args.kill_peer)
    checks['passed'] = result.returncode == 0 and all(c['passed'] for c in checks.values())
    (args.output / 'result.json').write_text(json.dumps(checks, indent=2) + '\n')
    print(json.dumps(checks, indent=2))
    return 0 if checks['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
