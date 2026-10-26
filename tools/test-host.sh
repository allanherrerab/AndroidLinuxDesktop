#!/bin/sh
set -eu
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
python3 -m unittest discover -s "$PROJECT_DIR/tests" -v
mkdir -p "$PROJECT_DIR/build/host-tests"
javac -d "$PROJECT_DIR/build/host-tests" \
    "$PROJECT_DIR/tests/support/org/debiannoroot/RfbConnection.java" \
    "$PROJECT_DIR/tests/RfbConnectionTest.java" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/DisplayGeometry.java" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/ImageIntegrity.java" \
    "$PROJECT_DIR/tests/DisplayAndImageTest.java" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/RegionalConfig.java" \
    "$PROJECT_DIR/tests/RegionalConfigTest.java" \
    "$PROJECT_DIR/app/src/main/java/org/debiannoroot/SessionLogs.java" \
    "$PROJECT_DIR/tests/SessionLogsTest.java"
java -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.RfbConnectionTest
java -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.DisplayAndImageTest
java -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.RegionalConfigTest
java -cp "$PROJECT_DIR/build/host-tests" org.debiannoroot.SessionLogsTest
JDK_DIR=${JAVA_HOME:-$(dirname -- "$(dirname -- "$(readlink -f "$(command -v javac)")")")}
export JDK_DIR PROJECT_DIR
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
    str(project / "tests/native_launcher_test.c"),
    str(project / "app/src/main/cpp/rootfs_extract.c"),
    "-llzma", "-o", str(target)
], check=True)
subprocess.run([str(target)], check=True, timeout=15)
PY
