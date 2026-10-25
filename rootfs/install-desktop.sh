#!/bin/sh
set -eu
export DEBIAN_FRONTEND=noninteractive
apt-get -o APT::Sandbox::User=root update
apt-get -o APT::Sandbox::User=root install -y --no-install-recommends \
    ca-certificates xfce4 xfce4-terminal thunar firefox-esr \
    xfce4-whiskermenu-plugin xfce4-notifyd libnotify-bin dbus-x11 dbus-daemon \
    tigervnc-standalone-server tigervnc-common x11-utils xfonts-base \
    fonts-dejavu-core librsvg2-common xterm procps sudo fakechroot fakeroot \
    pulseaudio pulseaudio-utils libasound2-plugins x11-xserver-utils
apt-get clean
rm -rf /var/lib/apt/lists/*
# An application has no Android system bus or systemd. User dbus is started per session.
rm -f /var/lib/dbus/machine-id /etc/machine-id
