# How this compares with the original Debian Noroot

I used the original 2020 Debian Noroot APK as the reference for this rebuild.
The idea is still the same: open an Android app and use a Debian desktop without
rooting your phone. This version runs Debian Trixie with XFCE, an embedded
Termux:X11 display server and PulseAudio.

If you used the old app, many of the controls should feel familiar. Some have
moved or work differently, and a few of the old SDL-specific options are missing.
Here's what you can expect.

## The desktop and display

You can change the desktop resolution, scaling, image filtering, aspect ratio,
orientation and screen borders. There are also zoom and fullscreen controls.
The X11 server is included in the app, so you don't need to install a separate
display-server app.

The display now uses an Android surface instead of the old SDL renderer. That
means the old color-depth and separate-rendering-thread switches don't have
matching settings here. Compatibility drawing is available for devices that
need it. Linux applications still use software 3D rendering; displaying the
desktop through Android's GPU does not give them hardware-accelerated OpenGL.

Debian still runs through PRoot and shares Android's kernel. Using `sudo` inside
Debian gives you an emulated root identity, not root access to your phone.

## Mouse and keyboard

The app supports relative touchpad movement and absolute touch input, along with
pointer speed, acceleration, click modes, dragging and scrolling. Gyroscope,
D-pad, joystick, trackball and stylus/hover settings are also present.

I've checked touch input and gyro movement on the Galaxy S24 FE. Physical mice,
styluses, joysticks and trackballs still need testing with actual hardware.
I also can't promise that acceleration or pressure sensitivity feels exactly
like the old app: I haven't established its original native calibration curves.
The old gyro-calibration screen and separate subframe touch-event option haven't
been recreated.

The biggest change to the on-screen controls is the toolbar. You can choose its
shortcut keys, size, opacity and theme, use Ctrl/Alt/Shift and drag toggles, and
show a separate F1–F12 row. You can't freely position buttons around the screen,
create floating or multiple screen joysticks, or assign arbitrary gestures to
keys as you could with the old SDL controls.

Hardware key remapping covers common modifiers, navigation keys, F1–F12 and
mouse buttons. It doesn't include the entire old SDL key catalog.

Your Android keyboard handles text input, including Unicode, composing text and
deletion. Samsung Keyboard has been checked with lowercase and uppercase letters,
ñ, Backspace and Enter. You still choose the Android keyboard in Android's
settings; the app's keyboard-layout selector controls the Debian X11 layout.
Japanese Unicode input is supported, but a separate Japanese input method inside
Debian isn't configured.

## Sound

Debian audio plays through PulseAudio and Android AudioTrack. You can adjust the
phone's media volume, the app's playback gain and individual volumes inside
Debian. The audio buffer can be set from 20 to 500 ms.

Playback and temporary audio-focus loss/recovery have been checked on the S24 FE.
Microphone recording isn't implemented.

## Installation, languages and sessions

The Debian image is a separate OBB or tar.xz file. You select it with Android's
file picker, and the app checks that it matches the APK before extracting it into
private storage. Later launches reuse that installation.

The old download/mirror choices, custom data-directory setting and startup-command
editor aren't available. There isn't a hosted image-download service at present.
See the [README](README.md) for building, installing and updating the app.

You can choose the app and Debian languages independently, along with Debian's
keyboard layout and timezone. English, Spanish, Brazilian Portuguese, French,
German, Japanese and Polish are included.

Leaving the desktop screen keeps the session running in the background. Starting
a new session replaces the previous desktop and X11 logs; returning to a running
session keeps its logs.

## What still needs checking

Device testing so far has used a Galaxy S24 FE running Android 16 with 4 KB memory
pages. The native binaries pass the 16 KB alignment checks, but I still need to
run the app on a device with 16 KB pages. Those build checks alone don't prove
that it works there.

The [development log](Process.md) has the detailed test results, fixes and known
limits. The differences described here are part of the current rebuild; they
shouldn't be read as a claim that every detail of the original app is identical.

## The original app and old source files

I've kept the [original APK](references/debian-noroot-2020.apk) as a reference,
and the launcher uses its original icon. After reviewing the differences on
2026-10-25, I removed the obsolete legacy sources, panel excerpts and prototype
files from this repository. The sources and license material needed by the
current build are still retained; see [THIRD_PARTY.md](THIRD_PARTY.md).
