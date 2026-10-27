#!/usr/bin/env python3
"""Audit native X11 in the installed APK and an already-visible Android desktop.

Works with release and debug apps. Does not restart sessions, send input, change
preferences or require run-as. Saves a screenshot for separate visual inspection;
process/layer checks alone do not prove correct pixels or input behavior.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shlex
import subprocess
import tempfile
import zipfile

PROJECT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("artifacts", PROJECT / "tools/verify/verify-artifacts.py")
artifacts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(artifacts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial")
    parser.add_argument("--package", default="org.debiannoroot")
    parser.add_argument("--output", type=Path, default=PROJECT / "build/device-x11.json")
    args = parser.parse_args()
    adb = ["adb"] + (["-s", args.serial] if args.serial else [])

    def shell(*words):
        return subprocess.check_output(adb + ["shell", shlex.join(words)], text=True)

    # Require a visible, unlocked desktop instead of mistaking a bound service
    # or a background SurfaceView for a functioning foreground display.
    policy = shell("dumpsys", "window", "policy")
    if "mIsShowing=true" in policy or "screenState=SCREEN_STATE_OFF" in policy:
        raise RuntimeError("Unlock the device and open the desktop before testing")
    windows = shell("dumpsys", "window")
    focus = next((line.strip() for line in windows.splitlines() if "mCurrentFocus=" in line), "")
    if args.package + "/org.debiannoroot.ui.DesktopActivity" not in focus:
        raise RuntimeError("Open Debian Noroot's desktop before testing")
    apk_path = shell("pm", "path", args.package).splitlines()[0].removeprefix("package:")
    with tempfile.TemporaryDirectory(prefix="noroot-apk-audit-") as temporary:
        local = Path(temporary) / "installed.apk"
        subprocess.run(adb + ["pull", apk_path, str(local)], check=True, stdout=subprocess.DEVNULL)
        with zipfile.ZipFile(local) as apk:
            artifacts.verify_x11_apk(apk)
            engine = hashlib.sha256(apk.read("lib/arm64-v8a/libXlorie.so")).hexdigest()
            expected = json.loads((PROJECT / "dist/x11.manifest.json").read_text())["binaries"]["libXlorie.so"]
            if engine != expected:
                raise RuntimeError("Installed APK does not contain the pinned Termux:X11 engine")
        apk_sha = artifacts.digest(local)
    processes = [line.split(None, 3) for line in shell("ps", "-A", "-o", "USER,PID,NAME,ARGS").splitlines()[1:]]
    server = next((p for p in processes if len(p) >= 3 and p[2] == args.package + ":x11"), None)
    if server is None:
        raise RuntimeError("Embedded X11 process is not running")
    owned = [p for p in processes if len(p) >= 3 and p[0] == server[0]]
    if any("xtigervnc" in " ".join(p).lower() or "xvnc" in " ".join(p).lower() for p in owned):
        raise RuntimeError("App UID is running a VNC server")
    if not any(p[2] == "xfce4-session" for p in owned):
        raise RuntimeError("XFCE is not running under the app UID")
    layers = shell("dumpsys", "SurfaceFlinger", "--list").splitlines()
    surface = [line.strip() for line in layers if "SurfaceView[" + args.package + "/org.debiannoroot.ui.DesktopActivity]" in line]
    if not surface:
        raise RuntimeError("Native X11 SurfaceView is missing")
    result = {"package": args.package, "apkSha256": apk_sha, "x11LibrarySha256": engine,
              "x11Pid": int(server[1]), "pageSize": int(shell("getconf", "PAGE_SIZE")),
              "nativeX11Packaged": True, "vncServerRunning": False,
              "xfceRunning": True, "nativeSurfacePresent": True,
              "pixelsAndInput": "require visual inspection and an input probe"}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix(".png").write_bytes(subprocess.check_output(adb + ["exec-out", "screencap", "-p"]))
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
