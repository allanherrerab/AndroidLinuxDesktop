#!/bin/sh
# Host PRoot/QEMU regression backend. Never packaged in the Android APK/image.
set -eu
server=
session=
cleanup() {
    trap - EXIT HUP INT TERM
    [ -z "$session" ] || kill "$session" 2>/dev/null || true
    [ -z "$server" ] || kill "$server" 2>/dev/null || true
    [ -z "$session" ] || wait "$session" 2>/dev/null || true
    [ -z "$server" ] || wait "$server" 2>/dev/null || true
    rm -f /tmp/debian-noroot-vnc.sock /tmp/.X1-lock /tmp/.X11-unix/X1
}
trap cleanup EXIT
trap 'exit 0' HUP INT TERM
mkdir -p /tmp/.X11-unix
chmod 1777 /tmp/.X11-unix
rm -f /tmp/debian-noroot-vnc.sock /tmp/.X1-lock /tmp/.X11-unix/X1
Xtigervnc :1 -geometry "${NOROOT_RESOLUTION:-1280x800}" -depth 24 -SecurityTypes None \
    -rfbport -1 -rfbunixpath /tmp/debian-noroot-vnc.sock -rfbunixmode 0600 \
    -nolisten tcp -localhost -AlwaysShared -AcceptSetDesktopSize=0 &
server=$!
NOROOT_DISPLAY_BACKEND=x11 DISPLAY=:1 /usr/local/bin/debian-noroot-startx &
session=$!
wait "$session"
