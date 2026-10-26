#!/usr/bin/env python3
"""Boot the packaged ARM64 desktop on a Linux host through PRoot/QEMU.

This validates Debian, startup, the real RFB decoder, and typed shell input.
Android lifecycle, SELinux, and physical input still require device testing.
"""
import argparse
import ctypes
import os
from pathlib import Path
import shlex
import shutil
import signal
import socket
import math
import struct
import threading
import subprocess
import tempfile
import time

PROJECT = Path(__file__).resolve().parents[1]


def check_pulse_audio(root,command,build):
    endpoint=root/'tmp/debian-noroot-audio.sock'
    deadline=time.monotonic()+30
    while not endpoint.exists():
        if time.monotonic()>deadline:raise RuntimeError('PulseAudio PCM socket did not appear')
        time.sleep(0.2)
    pcm=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);pcm.settimeout(1);pcm.connect(str(endpoint))
    capture=bytearray();finished=threading.Event()
    def read():
        while not finished.is_set():
            try:
                data=pcm.recv(16384)
                if not data:return
                capture.extend(data)
            except socket.timeout:pass
            except OSError:return
    reader=threading.Thread(target=read,daemon=True);reader.start()
    tone=b''.join(struct.pack('<hh',sample,sample) for sample in
                  (round(14000*math.sin(2*math.pi*1000*i/48000)) for i in range(96000)))
    (root/'tmp/audio-test.pcm').write_bytes(tone)
    try:
        playback=command[:-1]+['/usr/bin/env','PULSE_SERVER=unix:/tmp/debian-noroot-pulse.sock',
                    '/usr/bin/paplay','--raw','--format=s16le','--rate=48000','--channels=2','/tmp/audio-test.pcm']
        subprocess.run(playback,check=True,timeout=30,stdout=subprocess.DEVNULL)
        time.sleep(0.5)
    finally:
        finished.set();pcm.close();reader.join(2)
    data=bytes(capture[:len(capture)//4*4]);samples=struct.unpack('<'+'h'*(len(data)//2),data)
    audible=[i for i in range(0,len(samples)-1,2) if abs(samples[i])>1000]
    if len(audible)<4800:raise RuntimeError('PulseAudio produced no sustained audible PCM')
    if any(samples[i]!=samples[i+1] for i in audible):raise RuntimeError('PulseAudio stereo format mismatch')
    (build/'pulse-audio.pcm').write_bytes(data)
    print(f'ARM64 PulseAudio native client -> monitor socket passed ({len(data)} bytes, 48 kHz stereo s16le)',flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--proot", required=True)
    parser.add_argument("--qemu", default=shutil.which("qemu-aarch64"))
    parser.add_argument("--output", type=Path, default=PROJECT / "build/desktop-smoke.png")
    parser.add_argument("--timeout-scale", type=int, default=1,
                        help="Multiply readiness deadlines on slower emulation hosts (default: 1)")
    args = parser.parse_args()
    if args.timeout_scale < 1:
        parser.error("--timeout-scale must be positive")
    if not args.qemu:
        parser.error("Install qemu-user or pass --qemu")
    build = PROJECT / "build/smoke"
    build.mkdir(parents=True, exist_ok=True)
    library = build / "extract.so"
    subprocess.run(shlex.split(os.environ.get("CC", "cc")) + ["-shared", "-fPIC", "-std=c11",
                    str(PROJECT / "app/src/main/cpp/rootfs_extract.c"), "-llzma", "-o", str(library)], check=True)
    native = ctypes.CDLL(str(library))
    native.rootfs_extract_xz.argtypes = [ctypes.c_char_p, ctypes.c_char_p, ctypes.c_char_p, ctypes.c_size_t]
    subprocess.run(["javac", "-d", str(build), str(PROJECT / "tests/support/org/debiannoroot/RfbConnection.java"),
                    str(PROJECT / "tests/DesktopSmoke.java")], check=True)
    with tempfile.TemporaryDirectory(prefix="noroot-smoke-") as temporary:
        root = Path(temporary) / "rootfs"
        error = ctypes.create_string_buffer(256)
        result = native.rootfs_extract_xz(os.fsencode(PROJECT / "dist/debian-rootfs.tar.xz"),
                                         os.fsencode(root), error, len(error))
        if result:
            raise RuntimeError(error.value.decode())
        # Mirror the service's refresh of app-owned launch code and defaults.
        assets = PROJECT / "assets/rootfs"
        for name in ("debian-noroot-startx", "debian-noroot-setup-desktop", "debian-noroot-ensure-audio", "debian-noroot-setup-audio-panel", "debian-noroot-ensure-locale"):
            shutil.copyfile(assets / name, root / "usr/local/bin" / name)
            (root / "usr/local/bin" / name).chmod(0o755)
        shutil.copytree(assets / "desktop-defaults", root / "usr/share/debian-noroot/desktop-defaults",
                        dirs_exist_ok=True)
        shutil.copyfile(assets / "debian-noroot-firefox.js",
                        root / "usr/lib/firefox-esr/defaults/pref/debian-noroot.js")
        uid, gid = os.getuid(), os.getgid()
        for filename, entry in (("passwd", f"debian:x:{uid}:{gid}:Debian:/home/debian:/bin/bash\n"),
                                ("group", f"debian:x:{gid}:\n"),
                                ("shadow", "debian:!:19000:0:99999:7:::\n"),
                                ("gshadow", "debian:!::\n")):
            with (root / "etc" / filename).open("a") as output:
                output.write(entry)
        home = root / "home/debian"
        shutil.copytree(root / "etc/skel", home, symlinks=True, dirs_exist_ok=True)
        (home / "browser-check.html").write_text(
            "<!doctype html><html><head><title>Debian Noroot browser check</title></head>"
            "<body style='font:24px sans-serif;padding:48px;background:#edf4ff;color:#173865'>"
            "<h1>Firefox is running in Debian Noroot</h1>"
            "<p>This local page checks the browser without a network connection.</p></body></html>")
        # Keep this test offline and avoid first-run web pages competing with
        # its local-page navigation. These test-only prefs are not shipped.
        profile = home / ".mozilla/firefox/smoke.default"
        profile.mkdir(parents=True)
        (profile.parent / "profiles.ini").write_text(
            "[General]\nStartWithLastProfile=1\nVersion=2\n\n"
            "[Profile0]\nName=smoke\nIsRelative=1\nPath=smoke.default\nDefault=1\n")
        (profile / "user.js").write_text(
            'user_pref("browser.startup.homepage_override.mstone", "ignore");\n'
            'user_pref("browser.aboutwelcome.enabled", false);\n'
            'user_pref("browser.shell.checkDefaultBrowser", false);\n'
            'user_pref("browser.startup.page", 1);\n'
            'user_pref("browser.startup.homepage", "file:///home/debian/browser-check.html");\n')
        for folder in ("Desktop", "Documents", "Downloads"):
            (home / folder).mkdir(exist_ok=True)
        with (root / "etc/hosts").open("a") as hosts:
            hosts.write(f"\n127.0.1.1 {os.uname().nodename}\n")
        sudoers = root / "etc/sudoers.d/debian-noroot"
        sudoers.write_text("debian ALL=(ALL) NOPASSWD: ALL\n")
        sudoers.chmod(0o440)
        localtime = root / "etc/localtime"
        localtime.unlink(missing_ok=True)
        shutil.copyfile(root / "usr/share/zoneinfo/America/El_Salvador", localtime)
        shared = build / "shared"
        shared.mkdir(exist_ok=True)
        shm = Path(temporary) / "shared-memory"
        shm.mkdir(mode=0o700)
        (shared / "desktop-shared-ok").unlink(missing_ok=True)
        autostart = home / ".config/autostart/noroot-smoke.desktop"
        autostart.parent.mkdir(parents=True, exist_ok=True)
        autostart.write_text("[Desktop Entry]\nType=Application\nName=Desktop smoke terminal\n"
                             "Exec=xfce4-terminal --disable-server --title=Debian-Noroot "
                             "--command=/usr/local/bin/debian-noroot-smoke-terminal\n")
        shell = root / "usr/local/bin/debian-noroot-smoke-terminal"
        shell.write_text("#!/bin/sh\ncd /home/debian\n"
                         "/usr/local/bin/debian-noroot-smoke-windows &\n"
                         "export PROMPT_COMMAND='touch /home/debian/.noroot-terminal-ready'\n"
                         "exec /bin/bash --noprofile --norc -i\n")
        shell.chmod(0o755)
        watcher = root / "usr/local/bin/debian-noroot-smoke-windows"
        watcher.write_text("#!/bin/sh\n"
                           "while :; do\n"
                           "  xwininfo -root -tree > /home/debian/desktop-windows.txt 2>/dev/null\n"
                           "  grep -Eqi '\"Thunar\".* [1-9][0-9]{2,}x[1-9][0-9]{2,}[+-]' /home/debian/desktop-windows.txt && touch /home/debian/.noroot-thunar-ready\n"
                           "  grep -Eqi '\"firefox-esr\".* [1-9][0-9]{2,}x[1-9][0-9]{2,}[+-]' /home/debian/desktop-windows.txt && touch /home/debian/.noroot-firefox-ready\n"
                           "  grep -q 'Debian Noroot browser check' /home/debian/desktop-windows.txt && touch /home/debian/.noroot-browser-page-ready\n"
                           "  count=$(grep -Eic '\"Xfce4-terminal\".* [1-9][0-9]{2,}x[1-9][0-9]{2,}[+-]' /home/debian/desktop-windows.txt)\n"
                           "  [ \"$count\" -lt 2 ] || touch /home/debian/.noroot-terminal-launcher-ready\n"
                           "  sleep 2\n"
                           "done\n")
        watcher.chmod(0o755)
        # QEMU translates guest SysV IPC on Linux itself. Applying PRoot's Android
        # IPC emulation to QEMU's host calls would virtualize the addresses twice.
        command = [str(Path(args.proot).resolve()), "--kill-on-exit", "--link2symlink",
                   "-i", f"{uid}:{gid}", "-q", str(Path(args.qemu).resolve()), "-r", str(root), "-w", "/home/debian",
                   "-b", "/dev", "-b", f"{shm}:/dev/shm", "-b", "/proc", "-b", "/sys", "-b", f"{shared}:/sdcard",
                   "/usr/bin/env", "-i", "USER=debian",
                   "LOGNAME=debian", "HOME=/home/debian", "NOROOT_DISPLAY_BACKEND=vnc", "LANG=C.UTF-8", "TZ=:/etc/localtime", "SHELL=/bin/bash",
                   "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                   "/usr/local/bin/debian-noroot-startx"]
        log_path = build / "session.log"
        preflight = subprocess.run(command[:-1] + ["/usr/bin/sudo", "-n", "id", "-u"],
                                   text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
        if preflight.returncode != 0 or preflight.stdout.strip().splitlines()[-1] != "0":
            raise RuntimeError(f"Emulated sudo preflight failed: {preflight.stdout}")
        print("Emulated sudo preflight passed", flush=True)
        panel = home / ".config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml"
        panel.write_text("<!-- previous panel configuration -->\n")
        setup = command[:-1] + ["/usr/local/bin/debian-noroot-setup-desktop"]
        subprocess.run(setup, check=True, timeout=30)
        backup = home / ".local/share/debian-noroot/before-bottom-panel-v1/xfce4-panel.xml"
        if backup.read_text() != "<!-- previous panel configuration -->\n":
            raise RuntimeError("Panel migration failed to back up the old configuration")
        with panel.open("a") as config:
            config.write("<!-- user customization survives setup -->\n")
        configured = panel.read_bytes()
        subprocess.run(setup, check=True, timeout=30)
        if panel.read_bytes() != configured:
            raise RuntimeError("Panel setup overwrote an existing customization")
        # Select the isolated offline profile explicitly. Firefox otherwise
        # creates a dedicated per-install profile instead of using Default=1.
        launcher = home / ".config/xfce4/panel/launcher-4/firefox.desktop"
        launcher.write_text(launcher.read_text().replace(
            "Exec=firefox-esr\n", "Exec=firefox-esr --profile /home/debian/.mozilla/firefox/smoke.default\n"))
        print("Panel migration backup and customization persistence passed", flush=True)
        with log_path.open("w") as log:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                endpoint = root / "tmp/debian-noroot-vnc.sock"
                deadline = time.monotonic() + 120
                while not endpoint.exists():
                    if process.poll() is not None or time.monotonic() > deadline:
                        raise RuntimeError(f"Desktop startup failed; see {log_path}")
                    time.sleep(0.5)
                args.output.parent.mkdir(parents=True, exist_ok=True)
                subprocess.run(["java", "-Djava.awt.headless=true", f"-Dnoroot.smoke.timeoutScale={args.timeout_scale}",
                                "-cp", str(build), "org.debiannoroot.DesktopSmoke",
                                str(endpoint), str(args.output), str(home / ".noroot-terminal-ready")],
                               check=True, timeout=600 * args.timeout_scale)
                sentinel = home / "desktop-input-ok"
                if not sentinel.is_file() or sentinel.read_text().strip() != "desktop-input-ok":
                    raise RuntimeError(f"Desktop keyboard/terminal smoke failed; see {args.output} and {log_path}")
                if not (home / "desktop-sudo-uid").is_file() or (home / "desktop-sudo-uid").read_text().strip() != "0":
                    raise RuntimeError(f"Guest sudo failed; see {args.output} and {log_path}")
                if (shared / "desktop-shared-ok").read_text() != "shared-ok":
                    raise RuntimeError("Guest shared-folder write failed")
                if not (home / ".local/share/debian-noroot/bottom-panel-v1").is_file():
                    raise RuntimeError("Requested bottom-panel profile was not applied")
                for app in ("thunar", "firefox"):
                    if not (home / f".noroot-{app}-ready").is_file():
                        raise RuntimeError(f"Panel {app} launcher did not open its application; see screenshots and {log_path}")
                if not (home / ".noroot-browser-page-ready").is_file():
                    raise RuntimeError(f"Firefox failed to load the local test page; see screenshots and {log_path}")
                if not (home / ".noroot-terminal-launcher-ready").is_file():
                    raise RuntimeError("The terminal panel launcher did not open a second terminal")
                if not (home / "desktop-notification-ok").is_file():
                    raise RuntimeError("The desktop notification service did not accept a notification")
                check_pulse_audio(root,command,build)
                print(f"ARM64 XFCE rendering, keyboard, emulated sudo, shared-folder writes, and Thunar/Firefox panel launches passed: {args.output}")
            finally:
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGTERM)
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
                windows = home / "desktop-windows.txt"
                if windows.is_file():
                    shutil.copyfile(windows, build / "windows.txt")


if __name__ == "__main__":
    main()
