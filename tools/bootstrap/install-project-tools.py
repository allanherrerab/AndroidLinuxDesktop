#!/usr/bin/env python3
"""Install the build dependencies into this checkout without system writes.

The Android command-line tools and Gradle are downloaded by the existing
bootstrap script. Host packages are downloaded with apt and unpacked into
build/host, where tools/build/env.sh exposes them to the build.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path


PROJECT = Path(__file__).resolve().parents[2]
BUILD = PROJECT / "build"
HOST = BUILD / "host"
DEBS = BUILD / "host-debs"

HOST_PACKAGES = (
    "openjdk-21-jdk-headless",
    "gcc-14-x86-64-linux-gnu",
    "binutils-x86-64-linux-gnu",
    "make",
    "libtalloc-dev",
    "liblzma-dev",
    "qemu-user",
    "unzip",
    "ca-certificates",
)


def run(command: list[str], *, cwd: Path | None = None) -> None:
    print("+", " ".join(command), flush=True)
    subprocess.run(command, cwd=cwd, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--skip-host",
        action="store_true",
        help="only download Android SDK/NDK/CMake and Gradle tools",
    )
    parser.add_argument(
        "--skip-android",
        action="store_true",
        help="only install host libraries and the local JDK",
    )
    args = parser.parse_args()

    BUILD.mkdir(exist_ok=True)
    if not args.skip_android:
        run([sys.executable, str(PROJECT / "tools/bootstrap/download-android-tools.py")])

    if not args.skip_host:
        if shutil.which("apt-get") is None or shutil.which("dpkg-deb") is None:
            raise SystemExit("apt-get and dpkg-deb are required for local host packages")
        DEBS.mkdir(parents=True, exist_ok=True)
        HOST.mkdir(parents=True, exist_ok=True)
        env = os.environ.copy()
        env["DEBIAN_FRONTEND"] = "noninteractive"
        run(["apt-get", "download", *HOST_PACKAGES], cwd=DEBS)
        for package in sorted(DEBS.glob("*.deb")):
            run(["dpkg-deb", "-x", str(package), str(HOST)])

    print(f"Project-local tools are installed under {BUILD}")
    print("Run: . tools/build/env.sh")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
