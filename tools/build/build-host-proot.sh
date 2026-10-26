#!/bin/sh
# Build a recent PRoot for the non-root Debian image builder, without installing it globally.
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
python3 - "$PROJECT_DIR" <<'PY'
import hashlib
from pathlib import Path
import shutil
import sys
import urllib.request
import zipfile

project = Path(sys.argv[1])
build = project / "build/host-proot"
build.mkdir(parents=True, exist_ok=True)
archive = build / "proot.zip"
if not archive.is_file():
    urllib.request.urlretrieve("https://codeload.github.com/termux/proot/zip/refs/tags/v5.1.107.100", archive)
if hashlib.sha256(archive.read_bytes()).hexdigest() != "2621da26f348d9e8b3f85a1680d568bee6eb959618917dc34279747861e20728":
    raise SystemExit("Host PRoot source checksum mismatch")
source = build / "proot-5.1.107.100"
if source.exists():
    shutil.rmtree(source)
with zipfile.ZipFile(archive) as package:
    package.extractall(build)
patch = source / "src/extension/ashmem_memfd/ashmem_memfd.c"
patch.write_text("#include <string.h>\n" + patch.read_text())
patch = source / "src/cli/proot.h"
patch.write_text(patch.read_text().replace('#define VERSION "5.1.0"', '#define VERSION "5.1.107.100"'))
PY
make -C "$PROJECT_DIR/build/host-proot/proot-5.1.107.100/src" -j4 GIT=true
mkdir -p "$PROJECT_DIR/build/host-proot/bin"
cp "$PROJECT_DIR/build/host-proot/proot-5.1.107.100/src/proot" "$PROJECT_DIR/build/host-proot/bin/proot"
printf 'Host PRoot: %s\n' "$PROJECT_DIR/build/host-proot/bin/proot"
