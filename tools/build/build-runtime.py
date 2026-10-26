#!/usr/bin/env python3
"""Build the pinned Android PRoot and loader; requires NDK r27c and GNU make."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

PROJECT = Path(__file__).resolve().parents[2]
BUILD = PROJECT / "build/runtime"
PROOT_VERSION = "5.1.107.100"
TALLOC_VERSION = "2.4.3"
SOURCES = {
    "proot.zip": (f"https://codeload.github.com/termux/proot/zip/refs/tags/v{PROOT_VERSION}",
                  "2621da26f348d9e8b3f85a1680d568bee6eb959618917dc34279747861e20728"),
    "talloc.tar.gz": (f"https://www.samba.org/ftp/talloc/talloc-{TALLOC_VERSION}.tar.gz",
                      "dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd"),
}

CROSS_ANSWERS = '''Checking uname sysname type: "Linux"
Checking uname machine type: "aarch64"
Checking uname release type: "dontcare"
Checking uname version type: "dontcare"
Checking simple C program: OK
building library support: OK
Checking for large file support: OK
Checking for -D_FILE_OFFSET_BITS=64: OK
Checking for WORDS_BIGENDIAN: FAIL
Checking for C99 vsnprintf: OK
Checking for HAVE_SECURE_MKSTEMP: OK
rpath library support: OK
-Wl,--version-script support: FAIL
Checking correct behavior of strtoll: OK
Checking correct behavior of strptime: OK
Checking for HAVE_IFACE_GETIFADDRS: OK
Checking for HAVE_IFACE_IFCONF: OK
Checking for HAVE_IFACE_IFREQ: OK
Checking getconf LFS_CFLAGS: OK
Checking for large file support without additional flags: OK
Checking for working strptime: OK
Checking for HAVE_SHARED_MMAP: OK
Checking for HAVE_MREMAP: OK
Checking for HAVE_INCOHERENT_MMAP: OK
Checking getconf large file support flags work: OK
'''


def main() -> None:
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk:
        raise SystemExit("Set ANDROID_SDK_ROOT to the Android SDK containing NDK 27.2.12479018")
    host = "darwin-x86_64" if platform.system() == "Darwin" else "linux-x86_64"
    toolchain = Path(sdk).resolve() / f"ndk/27.2.12479018/toolchains/llvm/prebuilt/{host}/bin"
    compiler = toolchain / "aarch64-linux-android26-clang"
    if not compiler.is_file() or not shutil.which("make"):
        raise SystemExit("NDK r27c and GNU make are required to rebuild the runtime")
    BUILD.mkdir(parents=True, exist_ok=True)
    for name, (url, digest) in SOURCES.items():
        archive = BUILD / name
        if not archive.is_file():
            print(f"Downloading {name}", flush=True)
            with urllib.request.urlopen(url, timeout=90) as response, archive.open("wb") as output:
                shutil.copyfileobj(response, output)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
            raise SystemExit(f"Source checksum mismatch: {archive}")
    proot = BUILD / f"proot-{PROOT_VERSION}"
    talloc = BUILD / f"talloc-{TALLOC_VERSION}"
    for source in (proot, talloc):
        if source.exists():
            shutil.rmtree(source)
    with zipfile.ZipFile(BUILD / "proot.zip") as archive:
        archive.extractall(BUILD)
    with tarfile.open(BUILD / "talloc.tar.gz") as archive:
        archive.extractall(BUILD, filter="data")
    environment = os.environ.copy()
    environment.update(CC=str(compiler), LD=str(compiler), AR=str(toolchain / "llvm-ar"),
                       CFLAGS="-O2 -fPIC", LDFLAGS="-Wl,-z,max-page-size=16384")
    (talloc / "cross-answers.txt").write_text(CROSS_ANSWERS)
    subprocess.run(["./configure", f"--prefix={talloc / 'prefix'}", "--disable-rpath",
                    "--disable-python", "--cross-compile", "--cross-answers=cross-answers.txt"],
                   cwd=talloc, env=environment, check=True)
    subprocess.run(["make", "-j4"], cwd=talloc, env=environment, check=True)
    libraries = BUILD / "lib"
    libraries.mkdir(exist_ok=True)
    subprocess.run([str(toolchain / "llvm-ar"), "rcs", str(libraries / "libtalloc.a"),
                    str(talloc / "bin/default/talloc.c.5.o")], check=True)
    source = proot / "src"
    # NDK r27's Clang rejects implicit declarations in this upstream source file.
    patch = source / "extension/ashmem_memfd/ashmem_memfd.c"
    patch.write_text("#include <string.h>\n" + patch.read_text())
    patch = source / "GNUmakefile"
    patch.write_text(patch.read_text().replace("LOADER_LDFLAGS$1 += -static",
                     "LOADER_LDFLAGS$1 += -Wl,-z,max-page-size=16384 -static"))
    patch = source / "cli/proot.h"
    patch.write_text(patch.read_text().replace('#define VERSION "5.1.0"',
                                              f'#define VERSION "{PROOT_VERSION}"'))
    environment.update(CFLAGS=f"-O2 -fPIE -DARG_MAX=131072 -I{talloc}",
                       LDFLAGS=f"-L{libraries} -Wl,-z,max-page-size=16384",
                       # GNUmakefile invokes readelf directly for the loader workaround.
                       PATH=str(toolchain) + os.pathsep + environment["PATH"])
    # Provide the LLVM readelf under the name required by upstream make.
    host_bin = BUILD / "host-bin"
    host_bin.mkdir(exist_ok=True)
    readelf = host_bin / "readelf"
    readelf.unlink(missing_ok=True)
    readelf.symlink_to(toolchain / "llvm-readelf")
    environment["PATH"] = str(host_bin) + os.pathsep + environment["PATH"]
    subprocess.run(["make", "-j4", "GIT=true", f"CC={compiler}",
                    f"STRIP={toolchain / 'llvm-strip'}", f"OBJCOPY={toolchain / 'llvm-objcopy'}",
                    f"OBJDUMP={toolchain / 'llvm-objdump'}", "PROOT_UNBUNDLE_LOADER=libproot-loader.so"],
                   cwd=source, env=environment, check=True)
    target = PROJECT / "dist/runtime/arm64-v8a"
    target.mkdir(parents=True, exist_ok=True)
    binaries = {}
    for origin, name in ((source / "proot", "libproot.so"),
                         (source / "loader/loader", "libproot-loader.so")):
        destination = target / name
        shutil.copyfile(origin, destination)
        subprocess.run([str(toolchain / "llvm-strip"), str(destination)], check=True)
        destination.chmod(0o755)
        binaries[name] = hashlib.sha256(destination.read_bytes()).hexdigest()
    (PROJECT / "dist/runtime.manifest.json").write_text(json.dumps({
        "proot": PROOT_VERSION, "talloc": TALLOC_VERSION,
        "ndk": "27.2.12479018", "page_size": 16384, "binaries": binaries,
        "sources": {name: {"url": url, "sha256": digest} for name, (url, digest) in SOURCES.items()},
    }, indent=2) + "\n")
    print(f"Built Android ARM64 runtime: {target}")


if __name__ == "__main__":
    main()
