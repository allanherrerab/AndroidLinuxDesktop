#!/usr/bin/env python3
"""Exercise the production guest PulseAudio block and validate its PCM transport."""
import argparse
import ctypes
import os
from pathlib import Path
import shlex
import shutil
import signal
import subprocess
import tempfile
import time
import importlib.util

PROJECT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('desktop_smoke',PROJECT/'tools/smoke-desktop.py')
smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
parser=argparse.ArgumentParser();parser.add_argument('--proot',default=str(PROJECT/'build/host-proot/bin/proot'));args=parser.parse_args()
build=PROJECT/'build/audio-smoke';build.mkdir(exist_ok=True)
lib=build/'extract.so'
subprocess.run(shlex.split(os.environ.get('CC','cc'))+['-shared','-fPIC','-std=c11',str(PROJECT/'app/src/main/cpp/rootfs_extract.c'),'-llzma','-o',str(lib)],check=True)
native=ctypes.CDLL(str(lib));native.rootfs_extract_xz.argtypes=[ctypes.c_char_p,ctypes.c_char_p,ctypes.c_char_p,ctypes.c_size_t]
with tempfile.TemporaryDirectory(prefix='noroot-audio-') as temporary:
    root=Path(temporary)/'rootfs';error=ctypes.create_string_buffer(512)
    result=native.rootfs_extract_xz(os.fsencode(PROJECT/'dist/debian-rootfs.tar.xz'),os.fsencode(root),error,len(error))
    if result:raise RuntimeError(error.value.decode())
    uid,gid=os.getuid(),os.getgid()
    (root/'home/debian').mkdir(parents=True,exist_ok=True)
    with (root/'etc/passwd').open('a') as out:out.write(f'debian:x:{uid}:{gid}:Debian:/home/debian:/bin/bash\n')
    with (root/'etc/group').open('a') as out:out.write(f'debian:x:{gid}:\n')
    runtime=root/'tmp/runtime-debian';runtime.mkdir(mode=0o700,exist_ok=True)
    shutil.copyfile(PROJECT/'assets/rootfs/debian-noroot-ensure-audio',root/'usr/local/bin/debian-noroot-ensure-audio')
    (root/'usr/local/bin/debian-noroot-ensure-audio').chmod(0o755)
    shutil.copyfile(PROJECT/'assets/rootfs/debian-noroot-setup-audio-panel',root/'usr/local/bin/debian-noroot-setup-audio-panel')
    (root/'usr/local/bin/debian-noroot-setup-audio-panel').chmod(0o755)
    script=(PROJECT/'assets/rootfs/debian-noroot-startx').read_text()
    pulse=script[script.index('if [ "${NOROOT_AUDIO:-1}"'):script.index('# PRoot emulates SysV IPC.')]
    (root/'tmp/audio-start.sh').write_text('#!/bin/sh\nset -eu\n'+pulse+'\ntrap \'kill "$pulse" 2>/dev/null || true\' EXIT\nwait "$pulse"\n')
    command=[args.proot,'--kill-on-exit','--link2symlink','-i',f'{uid}:{gid}','-q',shutil.which('qemu-aarch64'),'-r',str(root),'-w','/home/debian','-b','/dev','-b','/proc','-b','/sys',
             '/usr/bin/env','-i','HOME=/home/debian','USER=debian','LOGNAME=debian','XDG_RUNTIME_DIR=/tmp/runtime-debian','PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin','/bin/sh','/tmp/audio-start.sh']
    with (build/'pulse.log').open('w') as log:
        process=subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        try:
            # check_pulse_audio expects a command whose final entry is the guest script.
            smoke.check_pulse_audio(root,command[:-2]+['/tmp/audio-start.sh'],build)
        finally:
            if process.poll() is None:
                os.killpg(process.pid,signal.SIGTERM)
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
