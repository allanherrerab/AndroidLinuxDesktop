#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
python3 -m unittest discover -s "$PROJECT_DIR/tests/python" -p 'test_*.py' -v
mkdir -p "$PROJECT_DIR/build/host-tests"
JDK_DIR=${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v javac)")")")}
export JDK_DIR PROJECT_DIR
javac -h "$PROJECT_DIR/build/host-tests" -d "$PROJECT_DIR/build/host-tests" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/runtime/NativeCore.java" \
    "$PROJECT_DIR/tests/java/NativeCoreTest.java" \
    "$PROJECT_DIR/tests/java/GuestFilesTest.java" \
    "$PROJECT_DIR/tests/support/org/debiannoroot/RfbConnection.java" \
    "$PROJECT_DIR/tests/java/RfbConnectionTest.java" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/runtime/DisplayGeometry.java" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/runtime/ImageIntegrity.java" \
    "$PROJECT_DIR/tests/java/DisplayAndImageTest.java" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/settings/RegionalConfig.java" \
    "$PROJECT_DIR/tests/java/RegionalConfigTest.java" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/session/SessionLogs.java" \
    "$PROJECT_DIR/tests/java/SessionLogsTest.java" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/ui/HeldKeys.java" \
    "$PROJECT_DIR/tests/java/HeldKeysTest.java"
python3 - <<'PY_CORE'
import os, shlex, subprocess, sysconfig
from pathlib import Path
project=Path(os.environ['PROJECT_DIR']);jdk=Path(os.environ['JDK_DIR'])
compiler=shlex.split(os.environ.get('CC','cc'))
# The bundled sysroot has libm development files but no runtime .so; its linker
# script refers to absent /lib paths. Use the running host's matching runtime
# for this host-only test library, without altering the toolchain installation.
host_math=Path('/usr/lib')/(sysconfig.get_config_var('MULTIARCH') or '')/'libm.so.6'
math_flags=[str(host_math)] if any(a.startswith('--sysroot') for a in compiler) and host_math.is_file() else ['-lm']
subprocess.run(compiler+[
    '-shared','-fPIC','-std=c11','-D_POSIX_C_SOURCE=200809L',
    '-Wall','-Wextra','-Werror','-Wformat=2','-ffp-contract=off',
    '-I'+str(jdk/'include'),'-I'+str(jdk/'include/linux'),
    '-include',str(project/'build/host-tests/org_debiannoroot_runtime_NativeCore.h'),
    *[str(project/'app/src/main/cpp/core'/name) for name in ('core.c','sha256.c','guest_files.c','jni.c')],
    *math_flags,
    '-o',str(project/'build/host-tests/libdebian_core.so')],check=True)
sanitizers=os.environ.get('NOROOT_CORE_SANITIZERS')=='1'
flags=['-fsanitize=address,undefined','-fno-omit-frame-pointer'] if sanitizers else []
if sanitizers and any(a.startswith('--sysroot') for a in compiler) and host_math.is_file():
    flags+=['-Wl,-rpath-link,'+str(host_math.parent)]
target=project/'build/host-tests/core-test'
subprocess.run(compiler+['-std=c11','-Wall','-Wextra','-Werror','-O1','-g','-ffp-contract=off',
    *flags,'-I'+str(project/'app/src/main/cpp/core'),str(project/'tests/native/core_test.c'),
    str(project/'app/src/main/cpp/core/core.c'),str(project/'app/src/main/cpp/core/sha256.c'),
    *math_flags,'-o',str(target)],check=True)
subprocess.run([str(target)],check=True,env={**os.environ,
    'ASAN_OPTIONS':'detect_leaks=1:halt_on_error=1','UBSAN_OPTIONS':'halt_on_error=1'})
print('C core vectors and malformed values passed'+(' (ASan/UBSan)' if sanitizers else ''))
PY_CORE
java -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.ui.HeldKeysTest
java -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.RfbConnectionTest
java -Xcheck:jni -Djava.library.path="$PROJECT_DIR/build/host-tests" -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.DisplayAndImageTest
java -Xcheck:jni -Djava.library.path="$PROJECT_DIR/build/host-tests" -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.NativeCoreTest
java -Xcheck:jni -Djava.library.path="$PROJECT_DIR/build/host-tests" -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.GuestFilesTest
java -Xcheck:jni -Djava.library.path="$PROJECT_DIR/build/host-tests" -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.RegionalConfigTest
java -Xcheck:jni -Djava.library.path="$PROJECT_DIR/build/host-tests" -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.session.SessionLogsTest
python3 - <<'PY'
import os
from pathlib import Path
import shlex
import subprocess
project = Path(os.environ["PROJECT_DIR"])
jdk = Path(os.environ["JDK_DIR"])
target = project / "build/host-tests/native-launcher-test"
subprocess.run(shlex.split(os.environ.get("CC", "cc")) + [
    "-std=c11", "-Wall", "-Wextra", "-Werror", "-Wformat=2", "-pthread",
    f"-I{jdk / 'include'}", f"-I{jdk / 'include/linux'}",
    str(project / "tests/native/native_launcher_test.c"),
    str(project / "app/src/main/cpp/rootfs_extract.c"),
    "-llzma", "-o", str(target)
], check=True)
subprocess.run([str(target)], check=True, timeout=15)
PY
