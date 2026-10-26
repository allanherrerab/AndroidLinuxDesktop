# Debian Noroot

[Leer en español](readme.es.md)

> Put a Debian desktop on your Android phone. No root required. Patience sold separately.

Run Debian on top of Android with a single click. Should work on almost any device. Unleash a full unrestricted desktop environment onto your mobile device. Instant frustration guaranteed (unless you're using a mouse or stylus).

XFCE, Firefox, a terminal, a file manager, and actual sound, all crammed into an Android app. Open windows. Install packages. Spend ten minutes trying to click something that was clearly designed for a mouse. It's desktop Linux in your pocket, with most of the questionable decisions that implies.

I rebuilt this around Debian Trixie, inspired by [pelya's Debian Noroot](https://github.com/pelya/debian-noroot) and using the original Android 4-era APK as a reference. The king is back.

Expect some jank. Things may be slow, awkward, broken on your particular phone, or simply annoying to operate with a finger. Sometimes you'll get a useful little Linux workstation. Sometimes you'll get a troubleshooting hobby. Both can be fun, provided you weren't counting on this to save your deadline.
This is an independent project, not an official Debian.org release. It is **not** a full Debian OS. It is a compatibility layer that allows you to run Debian applications. It shares Android's kernel, does not root your phone, and does not turn it into a normally booted Debian machine.  
  
## Contents

- [What you get](#what-you-get)
- [Before you invite Debian into your phone](#before-you-invite-debian-into-your-phone)
- [Install and start](#install-and-start)
  - [Install with ADB](#install-with-adb)
  - [Build and install an update](#build-and-install-an-update)
- [Operating a desktop with your thumbs](#operating-a-desktop-with-your-thumbs)
- [How this contraption works](#how-this-contraption-works)
- [Build from source](#build-from-source)
  - [First build](#first-build)
  - [The Make commands](#the-make-commands)
- [Where things live](#where-things-live)
- [Check that it actually works](#check-that-it-actually-works)
- [It broke. Now what?](#it-broke-now-what)
- [Thanks, and the paperwork](#thanks-and-the-paperwork)

## What you get
- **A real Linux desktop.** The X11 Graphics engine is built in. No separate X server app to install and persuade to cooperate.  
- **Sound.** PulseAudio feeds Android AudioTrack's API. Phone volume keys, app volume and XFCE's volume controls work. Yes, the extra volume sliders are intentional. Feel free to mute your microphone, as this app can make use of it as well.  
- **Display controls.** Resolution presets and custom sizes, fit/stretch/integer/custom scaling, filtering, orientation, fullscreen and 100–400% zoom. Tiny desktop buttons remain tiny desktop buttons, but you have options.  
- **Mouse and keyboard controls.** Relative touchpad or absolute touch input, drag lock, right/middle click, two-finger scrolling, modifiers, F1–F12, hardware key remapping and clipboard paste. Physical mouse/stylus and gyro controls are included; hardware testing coverage is still limited.  
- **Regional settings.** Choose app and Debian languages separately, plus the guest keyboard layout and timezone. English, Spanish, Brazilian Portuguese, French, German, Japanese and Polish are included, along with UTF-8 locales, XFCE/Firefox translations and Japanese fonts.  
- **A small APK and a much less small Debian image.** The APK is a few MB; the external image is several hundred MiB. Desktop Linux did not suddenly become pocket lint.
See [PARITY.md](PARITY.md) for what matches the old app, what changed, and which old options haven't made the cut.
  
## Before you invite Debian into your phone
As it stands you will need a 64-bit ARM **Android device running Android 8.0 or newer**. The current build targets Android 16/API 36. Other CPU architectures aren't included, but should work provided you create a rootfs tarball for them. You'll also need enough internal storage for the imported archive, extracted system and temporary extraction files. The installed desktop takes considerably more space than the download (around 1.5 GB total).  
**There are several limitations**: some carried over from the original app, some fixed as improvements:  
- **Android permissions still apply.** No root access is required, but granting shared-file access lets guest programs modify those files. Back up important data and run trusted guest software.
- **No Android root access.** `sudo` inside Debian uses PRoot's emulated root identity. It can install packages, but will not enable monitor mode on your Android Wi-Fi card or root your device.  
- **Audio support improved.** The original app had no working audio; the current build routes PulseAudio into Android's AudioTrack API. Phone volume keys, app volume and XFCE's volume controls all work. See [What you get](#what-you-get).
- **Experimental OpenGL acceleration.** Select **Guest graphics → VirGL (Android GPU)** in Settings and restart the desktop. Mesa Virpipe forwards guest OpenGL to a bundled Android EGL/GLES renderer. Software rendering remains the default and automatic fallback. The S24 FE gears test reports VirGL on its Xclipse 940; this is not universal GPU/application compatibility, Vulkan passthrough or a performance guarantee. Firefox retains its software-rendering defaults.
- **No privileged hardware access.** Debian shares the phone's kernel. You cannot re-partition the SD card, burn CD-ROMs, run raw `ping` or sniff your network, etc.  
- **No ability to move the app to the SD card**, so you'll need a lot of internal storage.  
- **Servers that requiere ports lower than 1024 won't start**, because they all need root features. Feel free to start Apache in port 8080 instead.
- **No microphone capture.** Might not work sometimes.  
- **Android chooses the Android keyboard.** The app's layout setting changes the guest X11 keyboard. Japanese Unicode input works, but a separate Japanese input method inside Debian isn't configured.  
I've tested on a Galaxy S24 FE (SM-S721B), Android 16. The native binaries pass 16 KB alignment checks, but testing on a 16 KB-page device is still outstanding. One working phone is encouraging; it isn't universal Android compatibility. [Process.md](Process.md) has the test results and limits.
  
## Install and start
You'll need these two files from the same build:  
  
| File | What it does |
| --- | --- |
| `dist/debian-noroot-debug.apk` |  The development app |  
| `dist/main.2.org.debiannoroot.obb` |  The Debian filesystem image |  
  
   
These are local build outputs, not links to a hosted release. If you're building it yourself, skip to [Build from source](#build-from-source).
1. Install the APK and copy the matching OBB into the phone's **Downloads** folder.  
2. Open Debian Noroot, choose your languages, guest keyboard layout and timezone, then select **Save and continue**.  
3. Select **Import Debian image** and choose the OBB. The matching `debian-rootfs.tar.xz` is also accepted.  
4. Select **Start Debian**. The first start verifies and extracts the image. Give it time. Subsequent starts reuse the installed desktop.  
The file picker grants access to the selected image, so there's no broad "please let me read everything" storage permission to hunt for. The app can also detect the matching image in its Android OBB directory. A missing or mismatched image gets a recovery message; it doesn't get a free pass into the extractor.  
   
### Install with ADB
You will need to get Android development tools for this, which is out of scope on this guide. On your phone, enable **Developer options → USB debugging**, connect the phone, and accept its debugging authorization prompt. From the repository root, after building:  
```  
adb devices -l  
 # If several devices are connected, select yours:  
 # export ANDROID_SERIAL=YOUR_DEVICE_SERIAL  
   
 adb install -r dist/debian-noroot-debug.apk  
 adb push dist/main.2.org.debiannoroot.obb /sdcard/Download/  
 adb shell am start -n org.debiannoroot/.MainActivity  
   
```  
Then select the transferred image in the app and follow the setup steps above. ADB gets the file onto the phone; you still choose it in the importer.  
   
### Build and install an update
Once the build dependencies and initial preparation are in place, this is the usual update routine:  
```  
. tools/build/env.sh  # Optional: use this workspace's cached tools.  
 make app DEBUG=1  # Or: make debug.  
   
 adb devices -l  
 # export ANDROID_SERIAL=YOUR_DEVICE_SERIAL  # If needed.  
   
 # Save guest work and choose Stop session in the app first.  
 adb install -r dist/debian-noroot-debug.apk  
 adb shell am start -n org.debiannoroot/.MainActivity  
   
```  
Select **Start Debian** to use your existing installation. An ordinary APK update does not need another OBB transfer or rootfs import. Debian files, your home directory and saved settings stay in place.  
**Keep the same signing key across updates.** Don't uninstall the app or clear its data to update it; that is a very efficient way to turn "keep my files" into "where did my files go?"  
Older installations automatically add missing audio and language packages when needed. That needs Internet access; fresh images already contain them. Check **View session log** if setup stalls or a download fails, then restart the session to retry.  

Older installations also install missing Mesa utilities when VirGL is first
selected; the new image includes them for offline startup. VirGL startup runs
an eight-second, small mapped-window rendering check and falls back to software
if the server, GPU or workload fails. No OpenGL version is artificially forced.

To access phone files from Debian, save guest work and stop the session, then
open **Settings → Android files → Manage device file access**. Grant Android's
all-files access (Android 11+) or read-storage permission (older Android) and
start Debian again. Shared files appear at `/storage/emulated/0`, including
`/storage/emulated/0/Download`; removable volumes use `/storage/<volume-id>`.
The existing `/sdcard` path remains the app's shared folder. This grant is
optional and is not needed to import the image. All-files access also permits
modifying shared files; guest applications inherit that authority. Android can
restart the app when the grant changes. Other apps' private `Android/data`
directories remain restricted; PRoot does not bypass Android permissions. See
[Android's all-files access rules](https://developer.android.com/training/data-storage/manage-all-files).
  
## Operating a desktop with your thumbs
Slide a finger to move the pointer and tap to click. Double-tap and drag holds the left mouse button; **Drag** gives you an explicit latch. Toolbar buttons handle right and middle clicks. Two fingers scroll.  
The toolbar scrolls horizontally. If a button seems missing, try scrolling before deciding I've hidden it out of spite.  
- **Zoom:** 100–400% magnification.  
- **F1–F12:** opens a separate scrollable function-key row.  
- **Fullscreen:** hides the toolbars and Android system bars. The ☰ button brings the controls back. You are allowed to escape fullscreen.  
- **Drag / Ctrl / Alt:** show a checkmark while held.  
- **Volume:** adjusts phone media volume and app playback gain. Inside Debian, use the PulseAudio panel plugin or `pavucontrol` for guest/application volume and mute. Hardware volume keys control phone media volume.  
**Display, mouse & audio settings** contains the input modes, click timing, speed/acceleration, hardware input options, gyro controls and shortcut appearance. The audio buffer defaults to 200 ms and can be set from 20–500 ms.  
You can reopen regional settings from the main screen or **Settings**. App language changes immediately; Debian language, keyboard and timezone apply next session. Most display/input changes apply when you return from Settings. Compatibility drawing needs a session restart.  
Leaving the desktop screen keeps the session running in the background. **View session log** shows the current or most recent session. Starting a new session clears the Debian, native X11 and VirGL logs, so save anything useful before restarting. Reopening a running desktop keeps that session's logs.
  
## How this contraption works
The Debian graphical shell is launched using PRoot, the ultimate Linux virtualization solution: [http://proot.me/. It then renders through the app's built-in X11 engine; An improvement over the original, which launched the separate XSDL X server app and required you to install and configure it separately.](http://proot.me/ "http://proot.me/")  
```  
Android application  
    ├─ PRoot → Debian Trixie → XFCE / Firefox / other Linux applications  
    ├─ Embedded X11 → native Android surface  
    ├─ Optional Mesa Virpipe → private Unix socket → VirGL → Android EGL/GLES
    └─ PulseAudio monitor PCM → Unix socket → Android AudioTrack API  
   
```  
The app verifies the root filesystem image and extracts it into private storage. PRoot handles filesystem translation and the emulated guest identity. X11 runs in a separate Android process with per-session authentication and TCP disabled; audio sockets also stay in private storage. Startup refreshes the app's guest helpers without replacing your installed rootfs or home directory.  
Android launches the embedded Termux:X11 server in its `:x11` process and displays
it directly through a native `SurfaceView`. The guest launcher accepts only an
external X11 server; it cannot start VNC. The optional host VNC launcher lives in
`tests/support/host-vnc-startx.sh` and is copied only into temporary host test
installations. TigerVNC remains installed in the image for those tests.
Firefox's nested sandbox is disabled for PRoot compatibility; Firefox still runs within the Android app's sandbox and privileges. PRoot itself is not a separate VM or kernel security boundary.  
  
## Build from source
So you'd like the troubleshooting hobby *and* the build system. Welcome.  
Use Linux with **Python 3.12+**, **JDK 17 or 21**, **Gradle 8.11.1**, **GNU Make**, a **C compiler**, **binutils**, **libtalloc/liblzma development headers** and **QEMU user-mode ARM64 emulation**. The Android SDK needs **Platform 36**, **Build Tools 36.0.0**, **NDK 27.2.12479018** and **CMake 3.22.1**. Set `ANDROID_SDK_ROOT` accordingly. Initial tools, sources and packages need Internet access.  
In this workspace, `. tools/build/env.sh` loads cached tools when available. It doesn't install missing dependencies. On a standard setup, configure the equivalent paths yourself.  
To install the pinned command-line toolchain inside this checkout, run `make tools` (or `make install-tools`). This places the JDK, Android SDK/NDK, CMake, Gradle and host build libraries under `build/` without writing to system directories. Use `make tools TOOLS_FLAGS=--skip-host` or `make tools TOOLS_FLAGS=--skip-android` to install only one side of the toolchain. Android Studio is not required for this project; the command-line SDK is sufficient.  
**An improvement over the original build.** The original app was built with debootstrap scripts under `img/` (`img-debian-buster-arm64-v8a.sh`, `img-debian-buster-x86_64.sh`), a chroot-based `build.sh` dance (`mount -o bind`, `chroot`, install gcc, rerun `build.sh`, `prepare-img-overlay.sh`), and PRoot pulled from Termux's Bintray packages. The current build replaces all of that with a Make-driven system: the Debian base is pinned in `rootfs.lock.json`, PRoot is built from source by `tools/build/build-host-proot.sh`, and the runtime/X11 stack is prepared by `tools/build/build-runtime.py` and `tools/build/prepare-x11.py`. The old dependency list (`autoconf automake debhelper build-essential libtool qemu-user-static debootstrap pxz schroot apt-cacher-ng`) is no longer needed, use the list above.  
### First build
From the repository root:  
```  
. tools/build/env.sh  # Optional cached workspace tools.  
 ./tools/build/build-host-proot.sh  
 python3 tools/build/build-runtime.py  
 python3 tools/build/prepare-x11.py  
 python3 tools/build/prepare-virgl.py
 make rootfs  
 make test  
 make app DEBUG=1  
   
```  
The app build runs assembly, lint, native JNI compatibility and artifact checks, then puts the APK and matching OBB in `dist/`. Direct Gradle builds need the prepared runtime, image metadata and compile-only JNI annotations first.  
### The Make commands
```  
make app            # Default: build and verify the debug APK and matching OBB.  
 make app DEBUG=1    # Explicit debug build; shortcut: make debug.  
 make app RELEASE=1  # Unsigned release build; shortcut: make release.  
 make rootfs         # Build the Debian desktop tarball and image metadata.  
 make test           # Run host regression checks.  
 make clean          # Remove generated outputs and deliverables.  
 make help           # Show targets and options.  
   
```  
`BUILD_TYPE=debug` and `BUILD_TYPE=release` also select the build mode. Don't set both `DEBUG=1` and `RELEASE=1`; the Makefile will object, quite reasonably.  
Debug produces `dist/debian-noroot-debug.apk`, signed with the development key. Release runs the same kinds of checks for the release variant and produces `dist/debian-noroot-release-unsigned.apk`. Sign the release APK with your release key before installing or distributing it. "Release" is a build mode, not a signature fairy. Both variants use the same Debian image bytes.  
Use the [update instructions](#build-and-install-an-update) to install a debug build. The shell entry point, `tools/build/build-apk.sh`, also accepts `--debug` or `--release`, optionally with `--rebuild-rootfs`.
To update packages in a previously built image, or refresh only its guest assets:  
```  
make rootfs ROOTFS_FLAGS="--reuse-rootfs"  
 # For guest script/template changes only, use this instead:  
 make rootfs ROOTFS_FLAGS="--reuse-rootfs --overlay-only"  
   
 # Publish the APK and OBB matching the changed image:  
 make app DEBUG=1  
   
```  
`make app APP_FLAGS="--rebuild-rootfs"` rebuilds the image as part of the app build. Avoid rebuilding the whole desktop for an app-only change unless you're particularly fond of watching progress output.  
`make clean` removes app/native/generated/test/report outputs and the APK, image, manifests and staged runtime libraries. It keeps `.gradle`, cached toolchains, image layers, pinned runtime/X11 sources, relinking material, licenses, references and local test evidence. It doesn't touch an installation on your phone.  
The Debian base is pinned in `rootfs.lock.json`. Later Debian packages come from signed repositories but aren't snapshot-pinned. The X11 artifact and matching source are checksum-pinned. Its upstream nightly URL can change; different bytes are deliberately rejected. Keep the pinned cache or update and validate both pins together. "It downloaded something" is a fairly low bar for a build system.  
  
## Where things live
  
| Location | Contents |
| --- | --- |
| `app/src/main/java/org/debiannoroot/` |  Java packages: `ui`, `session`, `settings`, `audio`, `runtime` |  
| `app/src/main/java/com/termux/x11/` |  Native X11 compatibility adapter |  
| `app/src/main/cpp/`, `app/src/main/res/` |  JNI/extractor, Android resources |  
| `app/src/main/cpp/core/` |  First-party C geometry, input maps, regional/image validation and guest filesystem helpers |
| `assets/rootfs/bin/`, `install/`, `defaults/` |  Guest helpers, installer and desktop defaults |  
| `tools/build/`, `bootstrap/`, `localization/`, `verify/`, `smoke/`, `device/` |  Tools grouped by purpose |  
| `tests/java/`, `native/`, `python/`, `support/` |  Host regressions and shared test support |  
| `references/` |  Original APK and its provenance |  
| `licenses/`, `THIRD_PARTY.md`, `licenses.md` |  Component licenses and source obligations |  
| `build/` |  Ignored caches, intermediates and local test evidence |  
| `dist/` |  Ignored APKs, rootfs/OBB, runtimes and manifests |  
| `Makefile` |  Build, test and cleanup entry points |  
| `rootfs.lock.json` |  Pinned Debian base image metadata |  
| `Process.md` |  Development history, fixes and test results |  
| `PARITY.md` |  Differences from the original app |  
  
   
  
## Check that it actually works
```  
make test  
 python3 tools/verify/verify-artifacts.py --full-image \  
    --apk dist/debian-noroot-debug.apk --obb dist/main.2.org.debiannoroot.obb  
 python3 tools/verify/verify-x11-abi.py --build-type debug  
 python3 tools/smoke/smoke-desktop.py --proot "$PWD/build/host-proot/bin/proot"  
 python3 tools/smoke/smoke-audio.py --proot "$PWD/build/host-proot/bin/proot"  
   
```  
Host smoke tests run ARM64 guest applications and check PCM audio transport under emulation. They don't prove that Android's surface, AudioTrack, lifecycle or physical input works. Those need a phone, preferably one somebody is willing to test on.  

To audit the display in an installed debug or release APK, unlock the phone and
open its Debian desktop, then run:

```sh
python3 tools/smoke/smoke-device-x11.py --serial DEVICE_SERIAL
```

This checks the installed APK's X11 classes/library, the running `:x11` process,
XFCE under the same app UID, the native display surface and absence of an app-owned
VNC server. It saves `build/device-x11.json` and a screenshot. Inspect the screenshot
and exercise mouse/keyboard input separately; process checks alone do not validate
pixels or input. APK builds also reject a packaged VNC client or guest launcher.
For slower emulation, `smoke-desktop.py --timeout-scale 2` doubles readiness deadlines without skipping assertions. Failed runs save a `-failure.png` frame when available. A disk-backed `TMPDIR` avoids extracting the test rootfs into a small RAM-backed temporary filesystem.  
For lifecycle checks on an unlocked device with an initialized debug app, save guest work first:  
```  
python3 tools/smoke/smoke-device-lifecycle.py --serial DEVICE_SERIAL  
   
```  
This stops/restarts sessions, checks rotation/return, rootfs/home preservation and log replacement, then restores Android rotation preferences. Results go to `build/device-lifecycle.json`. To run an isolated guest command without typing into somebody's terminal, use `tools/device/device-guest.py --serial DEVICE_SERIAL -- PROGRAM ARGS...`.  

With VirGL enabled in a running debug/acceptance installation,
`python3 tools/smoke/smoke-device-graphics.py --package org.debiannoroot.acceptance`
checks the session's renderer process, inherited XFCE environment and a bounded
mapped GL workload with positive completed frames. It saves the renderer/frame
log and JSON evidence. Optional `--storage-fixture /storage/emulated/0/Download/TEST_FILE`
reads and hashes only that caller-owned fixture after the system grant; it does
not browse personal files. This is a compatibility smoke, not a GPU benchmark.
For first-run import/extraction tests:  
```  
gradle :app:assembleAcceptance :app:lintAcceptance  
   
```  
This builds a separate debug app, `org.debiannoroot.acceptance`, under `build/app/outputs/apk/acceptance/`, with its own rootfs and preferences. Its `org.debiannoroot.FocusProbeActivity` requests temporary media focus for audio checks. These test components aren't included in the normal APK.  
  
## It broke. Now what?
Start with **View session log**. It contains Debian startup output, the native X11 log and `virgl.log`. A warning about who owns `/tmp/.X11-unix` isn't, by itself, evidence that the sky has fallen. VirGL's guest renderer/frame probe is saved in `/tmp/debian-noroot-graphics-probe.log`; `glxinfo -B` can report X_GetImage BadMatch on this path even when mapped applications render correctly.
- **Image won't extract:** check that the APK and image match, and that internal storage isn't full.  
- **No sound:** check phone media volume, app gain, guest mute and package-setup progress. Three volume controls means three opportunities for silence.  
- **Black screen or miserable performance:** [Process.md records previously diagnosed failures and fixes. Include logs when reporting a new one.](Process.md "Process.md")  
- **A server (SSH, Apache, etc.) won't start:** expected. They need root features. Use `tightvncserver` instead of SSH and `wbox` for a small file-sharing web server, as the original app also advised.  
For bug reports, include the device model, Android version, build/image identifiers, reproduction steps and relevant logs. Remove personal information first. "It doesn't work" is a perfectly understandable feeling, but a difficult bug report to debug.  
  
## Thanks, and the paperwork
Thanks to [pelya and contributors](https://github.com/pelya), [Debian](https://www.debian.org/), [XFCE](https://xfce.org/), [Termux:X11](https://github.com/termux/termux-x11), [PRoot](https://proot-me.github.io/), [PulseAudio](https://www.freedesktop.org/wiki/Software/PulseAudio/) and the other upstream projects. The original app made this wonderfully impractical idea worth revisiting.

Components keep their own licenses. See [THIRD_PARTY.md](THIRD_PARTY.md), [licenses.md](licenses.md) and the preserved license texts. Binary redistribution must include the corresponding source/build and relinking material those licenses require. This README doesn't relicense the project, even if you've read all the way to the bottom. Especially then.
