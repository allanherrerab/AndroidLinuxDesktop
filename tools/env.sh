# Source from Bash: . tools/env.sh
# Optional workspace-local tools; standard system installations also work.
NOROOT_PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
NOROOT_LOCAL_HOST="$NOROOT_PROJECT_DIR/build/host"
if [ -d "$NOROOT_LOCAL_HOST/usr/lib/jvm/java-21-openjdk-amd64" ]; then
    export JAVA_HOME="$NOROOT_LOCAL_HOST/usr/lib/jvm/java-21-openjdk-amd64"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -d "$NOROOT_PROJECT_DIR/build/sdk" ]; then
    export ANDROID_SDK_ROOT="$NOROOT_PROJECT_DIR/build/sdk"
fi
export PATH="$NOROOT_PROJECT_DIR/build/tools/gradle-8.11.1/bin:$NOROOT_LOCAL_HOST/usr/bin:${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/nonexistent}}/platform-tools:$PATH"
if [ -d "$NOROOT_LOCAL_HOST/usr/lib/x86_64-linux-gnu" ]; then
    export LD_LIBRARY_PATH="$NOROOT_LOCAL_HOST/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
    export CC="x86_64-linux-gnu-gcc-14 --sysroot=$NOROOT_LOCAL_HOST"
    export LDFLAGS="-L$NOROOT_LOCAL_HOST/usr/lib/x86_64-linux-gnu"
    export OBJCOPY=x86_64-linux-gnu-objcopy
    export OBJDUMP=x86_64-linux-gnu-objdump
fi
