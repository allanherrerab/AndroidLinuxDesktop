#!/bin/sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_DIR=$(dirname -- "$(dirname -- "$SCRIPT_DIR")")
BUILD_TYPE=debug
EXPLICIT_TYPE=
REBUILD_ROOTFS=0
for argument in "$@"; do
    case "$argument" in
        --debug|--release)
            selected=${argument#--}
            if [ -n "$EXPLICIT_TYPE" ] && [ "$EXPLICIT_TYPE" != "$selected" ]; then
                echo "Choose either --debug or --release." >&2; exit 2
            fi
            BUILD_TYPE=$selected;EXPLICIT_TYPE=$selected ;;
        --rebuild-rootfs) REBUILD_ROOTFS=1 ;;
        --help|-h) echo "Usage: $0 [--debug|--release] [--rebuild-rootfs]"; exit 0 ;;
        *) echo "Usage: $0 [--debug|--release] [--rebuild-rootfs]" >&2; exit 2 ;;
    esac
done
if [ "$BUILD_TYPE" = release ]; then
    TASK_TYPE=Release
    BUILT_APK="$PROJECT_DIR/build/app/outputs/apk/release/app-release-unsigned.apk"
    PUBLISHED_APK="$PROJECT_DIR/dist/debian-noroot-release-unsigned.apk"
else
    TASK_TYPE=Debug
    BUILT_APK="$PROJECT_DIR/build/app/outputs/apk/debug/app-debug.apk"
    PUBLISHED_APK="$PROJECT_DIR/dist/debian-noroot-debug.apk"
fi

if ! command -v gradle >/dev/null 2>&1; then
    echo "Gradle is required. Install Gradle 8.11.1 and a JDK 17, then rerun." >&2
    exit 2
fi
if [ -z "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}" ]; then
    echo "Set ANDROID_SDK_ROOT to an Android SDK with Platform 36, NDK 27.2.12479018, and CMake 3.22.1." >&2
    exit 2
fi
if [ ! -f "$PROJECT_DIR/dist/runtime.manifest.json" ]; then
    python3 "$SCRIPT_DIR/build-runtime.py"
fi
if [ ! -f "$PROJECT_DIR/dist/rootfs.manifest.json" ] || [ "$REBUILD_ROOTFS" = 1 ]; then
    "$SCRIPT_DIR/build-rootfs.sh" --desktop
fi
python3 "$SCRIPT_DIR/prepare-x11.py"
python3 "$SCRIPT_DIR/prepare-virgl.py"
python3 "$SCRIPT_DIR/../verify/verify-artifacts.py"
cd "$PROJECT_DIR"
gradle --no-daemon ":app:assemble$TASK_TYPE" ":app:lint$TASK_TYPE"
python3 "$SCRIPT_DIR/../verify/verify-x11-abi.py" --build-type "$BUILD_TYPE"
python3 "$SCRIPT_DIR/../verify/verify-artifacts.py" --apk "$BUILT_APK"
cp "$BUILT_APK" "$PUBLISHED_APK.installing"
mv "$PUBLISHED_APK.installing" "$PUBLISHED_APK"
python3 - "$PROJECT_DIR" <<'PY_OBB'
from pathlib import Path
import os
import sys
root=Path(sys.argv[1]);image=root/'dist/debian-rootfs.tar.xz'
obb=root/'dist/main.2.org.debiannoroot.obb'
pending=obb.with_suffix('.obb.installing');pending.unlink(missing_ok=True)
if not obb.exists() or not os.path.samefile(image,obb):
    os.link(image,pending);os.replace(pending,obb)
PY_OBB
python3 "$SCRIPT_DIR/../verify/verify-artifacts.py" --apk "$PUBLISHED_APK" --obb "$PROJECT_DIR/dist/main.2.org.debiannoroot.obb"
printf 'APK: %s\n' "$PUBLISHED_APK"
printf 'Rootfs: %s\n' "$PROJECT_DIR/dist/debian-rootfs.tar.xz"
printf 'Expansion image: %s\n' "$PROJECT_DIR/dist/main.2.org.debiannoroot.obb"
