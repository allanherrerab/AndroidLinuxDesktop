#!/bin/sh
set -eu
export DEBIAN_FRONTEND=noninteractive
for config in /etc/dpkg/dpkg.cfg.d/*; do
    [ -f "$config" ] || continue
    sed -i '\|^path-exclude[ =].*/usr/share/locale/|d' "$config"
done
apt-get -o APT::Sandbox::User=root update
apt-get -o APT::Sandbox::User=root install -y --no-install-recommends \
    ca-certificates xfce4 xfce4-terminal thunar firefox-esr \
    xfce4-whiskermenu-plugin xfce4-notifyd libnotify-bin dbus-x11 dbus-daemon \
    tigervnc-standalone-server tigervnc-common x11-utils xfonts-base \
    locales fonts-noto-cjk firefox-esr-l10n-es-es firefox-esr-l10n-pt-br \
    firefox-esr-l10n-fr firefox-esr-l10n-de firefox-esr-l10n-ja firefox-esr-l10n-pl \
    fonts-dejavu-core librsvg2-common xterm procps sudo fakechroot fakeroot \
    pulseaudio pulseaudio-utils libasound2-plugins xfce4-pulseaudio-plugin pavucontrol x11-xserver-utils \
    libgl1-mesa-dri libglx-mesa0 mesa-utils
# Restore translations when upgrading an older slim-based image.
if [ ! -f /usr/share/locale/es/LC_MESSAGES/xfce4-panel.mo ]; then
    apt-get -o APT::Sandbox::User=root install -y --no-install-recommends --reinstall \
        xfce4-session xfce4-panel xfce4-settings xfce4-terminal xfdesktop4-data \
        xfwm4 thunar-data libxfce4ui-common libxfce4util-common libgtk-3-common
fi
for language in en_US es_ES pt_BR fr_FR de_DE ja_JP pl_PL; do
    localedef --no-archive -i "$language" -f UTF-8 "$language.UTF-8"
done
apt-get clean
rm -rf /var/lib/apt/lists/*
# An application has no Android system bus or systemd. User dbus is started per session.
rm -f /var/lib/dbus/machine-id /etc/machine-id
