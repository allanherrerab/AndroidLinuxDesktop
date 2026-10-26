# Development and repair log

## 2026-10-26: experimental VirGL and Android shared files

Added an opt-in Guest graphics setting (software remains the default). A pinned
virglrenderer 1.3.0/libepoxy 1.5.10 Android PIE server uses native EGL/GLES and a
session-private mode-0600 vtest socket. The APK bundles it as
libvirgl_server.so; the service owns startup, cancellation, logging and shutdown.
Mesa Virpipe in Debian forwards OpenGL commands over that socket. There is no
direct DRM access, Vulkan passthrough or forced OpenGL-version override.
Firefox's existing software-rendering defaults are preserved. Selected pinned
Termux Android patches, source archives, static libraries, licenses and the
build recipe/manifest are retained; the pinned Termux:X11 engine is unchanged.

The initial glxinfo probe failed with X_GetImage BadMatch on the S24 FE. A mapped
glxgears workload nevertheless rendered visible gears and identified
`virgl (ANGLE ((Samsung Xclipse 940) on Vulkan 1.3.279))`, Mesa 25.0.7, OpenGL 2.1.
Startup now runs an eight-second bounded small mapped workload, accepting only
a non-software VirGL renderer AND a completed positive frame sample. A timeout
without frames, a software renderer or a crashed workload is not success.
Probe failure falls back to llvmpipe. Host regressions cover those decisions;
the device smoke checks the session-owned server, XFCE's inherited Virpipe
environment, a real frame sample and an optional caller-owned storage fixture.
Frame counts here establish rendering, not a speed comparison or gaming claim.

Added optional Android all-files access (API 30+) / read-storage permission
(API 26–29) through system Settings/runtime permission UI. Only granted sessions
bind host /storage at guest /storage. The existing app-owned /sdcard mapping is
unchanged. Settings explains Android restrictions and the all-files grant's
write authority; guests inherit the app's permissions. Android can terminate
the app on a permission change, so the UI warns to save work and stop first.
Other apps' protected data remains restricted. Removable storage support is the
same /storage binding, not a separately measured physical-volume test.

The new image includes Mesa GLX/DRI and mesa-utils for offline startup. Startup
refreshes app-owned graphics helpers in older initialized roots and installs
missing packages only when VirGL is selected, without replacing rootfs/home.
Settings/help has all seven translations. New sessions truncate virgl.log along
with desktop.log and x11.log; View session log includes it.

Android checks used only the disposable acceptance package on the Galaxy S24 FE,
Android 16, 4096-byte pages, with the normal desktop stopped. The older image
installed missing Mesa utilities automatically. adb install -r preserved the
ready-rootfs inode/size/timestamp and a guest-home marker. VirGL startup and a
second mapped workload passed, XFCE inherited GALLIUM_DRIVER=virpipe, and the
socket mode was 0600. Unlinking only the test session's socket before guest
startup exercised fallback: XFCE ran with GALLIUM_DRIVER=llvmpipe. Normal stop
left no acceptance-owned renderer or guest process. Shared-file tests read only
a 61-byte caller-created Downloads fixture: denied before the grant, readable
after the Settings grant. Production PRoot arguments contained /storage:/storage
and retained the original app-folder /sdcard binding. The normal release-signed
app, its grants and its installed rootfs/home were not changed.

Host checks passed: make test (22 Python checks, C/JNI/Java and native launcher
tests), full-image asset/package verification, and release assembly/lint, all
34 pinned X11 signatures, APK/image pairing and native 16 KB ELF alignment.
Final acceptance assembly/lint, ABI and full-image/APK/OBB checks passed too;
adb install -r of that final APK again preserved the ready marker and home file.
The ARM64 host PulseAudio smoke passed 521,448 bytes of 48 kHz stereo s16le PCM.
Two earlier runs hit the socket-readiness deadline with temporary paths longer
than the AF_UNIX pathname limit; using a short disk-backed temporary directory
passed without changing the deadline or assertions. Those failed outputs remain
available. This is transport validation, not Android audio-quality acceptance.
The final full host desktop smoke passed 349 decoded frames, keyboard/terminal
input, emulated sudo, shared-folder writes, panel customization preservation,
Thunar/terminal/Firefox launchers, Firefox's offline page and 787,364 bytes of
stereo PCM. These are QEMU/VNC regressions, not substitutes for the separate
Android VirGL checks above. The phone relocked during final host builds, so a
further run after the last metadata/translation rebuild was not performed.
The disposable acceptance app/data and verified test Downloads fixture were
removed afterward; APK/image and evidence remain available for recreation.
Release output remains unsigned. Logs/screenshots and intermediate failures are
retained under build/graphics/. Other GPUs, Android 8–10 permissions, physical
removable volumes, broader GL applications and 16 KB-page runtime acceptance
remain untested. No release was published or code pushed for this work.

## 2026-10-26: enforce and audit Android's native X11 path

Audited the signed daily release and the installed APK before changing the
display path. Their bytes matched. Both contained DesktopActivity, LorieView,
X11Service and the pinned libXlorie.so, with no RfbConnection client class.
On the S24 FE, the normal installation rendered XFCE through its :x11 process
and native SurfaceView with no app-owned Xtigervnc process. A long press opened
the XFCE context menu. The native implementation was already present; this
change does not claim to introduce an absent server or replace a measured VNC
transport on that installation.

Removed the dormant VNC server branch from the packaged guest launcher. The
Android native launcher now explicitly supplies NOROOT_DISPLAY_BACKEND=x11,
DISPLAY=:0 and its private XAUTHORITY. Other backend values fail instead of
starting another server. Host VNC startup now lives in
tests/support/host-vnc-startx.sh, copied only into temporary host test roots;
the existing TigerVNC package is retained for those tests. The pinned native
engine, separate process, main-Looper startup, authentication, disabled TCP and
native input ABI are unchanged. X11 startup logs identify the actual engine.

Added DEX class-definition checks to artifact verification, requiring the X11
adapters and rejecting a host RFB client or VNC startup branch in an APK.
smoke-device-x11.py audits an installed release/debug APK, its pinned engine,
same-UID XFCE/X11 processes and native surface, and saves a screenshot for
visual inspection. It does not claim that a process/layer check proves correct
pixels or input. Application-specific internal permission names allow the
acceptance build to coexist with a differently signed normal release.

Host/build validation passed: make test (18 Python checks plus the existing C,
JNI, Java and native lifecycle/network tests); refreshed image overlay and full
image verification; release and acceptance assembly/lint, all 34 pinned JNI
signatures for both variants, and APK/OBB checks including native ELF alignment.
The host desktop smoke passed 308 decoded frames, keyboard/terminal input,
emulated sudo, shared writes, panel migration/customization, terminal/Thunar/
Firefox launchers and Firefox's offline page. PulseAudio transported 774,320
bytes of 48 kHz stereo s16le PCM. These are host regression results, separate
from Android display acceptance below.

Android acceptance passed on the Galaxy S24 FE, Android 16, 4096-byte pages,
using a separately installed acceptance APK with its own freshly verified and
extracted image. xdpyinfo reported X.Org 21.1.99 on :0; access without the
session cookie failed, as did a TCP display connection. The cookie file had
mode 0600. The installed-APK audit matched the pinned engine, found the :x11
process, same-UID XFCE and native SurfaceView, and found no app-owned VNC
server. Screenshots were visually inspected. An isolated xev window received
mouse press/release, pointer motion across 33 distinct guest coordinates, and
F1/F12 press/release sent through Android's input/view path.

Updated that acceptance installation with adb install -r after correcting a
diagnostic write: Android's System.err goes to logcat, so the engine header now
uses the actual x11.log file descriptor. The ready-rootfs inode/size/timestamp
and a guest-home marker survived the update; the final APK started XFCE again,
passed the installed-APK/surface audit and produced the expected log header.
Release/acceptance assembly/lint, JNI ABI and artifact checks passed again for
this final Java change. The normal release-signed app was not replaced.

The cleanup continuity assertion failed: Android's event log recorded the
normal app's PRoot process being killed for "Trimming phantom processes" while
both guest desktops were running. Its service then ended the session and stopped
its X11 server. Reopening the normal app started a new session using the existing
installation; uninterrupted survival of that background session is not claimed.
No global Android process limits or normal-app preferences were changed. Track
this separately from the successful native X11 display/input checks.

Evidence is retained under build/display-audit/. The temporary acceptance app
and its disposable data were removed after testing; the test environment can be
recreated from the retained APK/image. Physical input
devices and 16 KB-page runtime acceptance remain outstanding; this is not a
new performance measurement or completion of all C/package-migration checks.

## 2026-10-26: first-party Java logic moved to C

Reviewed all first-party Java classes and added `app/src/main/cpp/core/` as a
separate `libdebian_core.so`. Portable logic now lives there: viewport geometry,
resolution limits, pointer acceleration, Android/X11/toolbar key maps, regional
allowlists and shell-safe guest configuration, SHA-256 streaming import checks,
session-log truncation, scoped staging cleanup, guest account/home initialization,
and timezone/hostname/resolver file installation. Java adapters preserve existing
call sites. Language/keyboard code tables have one authoritative definition in C.

The remaining Java is Android integration: Activities/views, gesture/IME/sensor
callbacks and held-key lifecycle, Binder/Looper session ownership, notifications,
SharedPreferences, resource localization, SAF/AssetManager stream ownership,
platform timezone IDs and network discovery, and AudioTrack/focus/buffering.
Rootfs activation/recovery remains with the service that owns cancellation and
persisted phase; its portable filesystem subroutines call C. Moving these
framework calls into C would retain the same Java APIs through JNI, while
increasing callback/reference/lifetime complexity. Generated UiText remains a
resource adapter. Third-party libraries and the Termux:X11 adapter were not
modified by this C migration. No speed or APK-size improvement is claimed.

The host build compiles production C against javac-generated JNI declarations
and runs the adapters with `-Xcheck:jni`. Added regression coverage for SHA-256
padding/chunk boundaries and a fixed vector, JDK digest comparison, short/zero
stream reads, I/O exception identity, cancellation, 2000 geometry cases, key maps,
regional validation, repeatable account creation, preserved home files/modes,
zoneinfo aliases, resolver defaults/customization and symlink-safe logs/cleanup.
Cleanup permits only rootfs.installing/rootfs.previous below the app files
directory. An already-installed matching read-only sudoers rule is reused.
The APK verifier now requires the new library and checks its 16 KB ELF alignment.

Validation passed: make test and NOROOT_CORE_SANITIZERS=1 make test, including
the generated-JNI-header checks, CheckJNI adapters, known SHA-256 vectors and
ASan/UBSan algorithm harness. The C verifier accepted the complete existing
external image checksum/size. make app DEBUG=1 passed assembly/lint and APK/OBB
pairing. Release and acceptance assembly/lint passed; each APK passed artifact
verification including the new library's 16 KB ELF alignment. All 18 NativeCore
exports are present in all three APKs, all 34 pinned X11 signatures match each
variant, and the 24 Android key constants match the installed Android 36 SDK.
No image rebuild, device installation, rootfs/home replacement or third-party
library/source change was made. Release remains unsigned. Device acceptance
and any performance claims remain open; see backlog.md.

## 2026-10-26: source layout and Java package migration

Grouped tools by build/bootstrap/localization/verification/smoke/device purpose,
tests by language, and guest assets into bin/install/defaults. Java source
packages now match ui/session/settings/audio/runtime directories. Updated imports,
component declarations, generated UiText output and the five NativeBridge JNI
export names. The original launcher component remains an activity alias, while
application IDs, preference keys, guest destinations, Binder/action strings and
the pinned Termux:X11 JNI contract remain stable.

Fixed relocation gaps in verifier root discovery, native-test includes and guest
helper refresh/build/smoke paths. Updated English/Spanish build commands and
source maps. The new backlog.md records current follow-ups only; no missing
historical evidence was reconstructed.

Validation: make test passed all 12 Python tests plus Java protocol,
geometry/image/regional/log and native lifecycle/network checks. make app DEBUG=1
passed assembly, lint and all 34 pinned X11 JNI signatures, then verified the APK
and paired OBB. Full-image verification passed against unchanged image bytes;
the packaged library contains all five migrated NativeBridge exports. Release
and acceptance assembly and lint also passed; release remains unsigned.
No device installation or runtime acceptance was performed for this migration.

## Earlier development and acceptance

What it took to get a modern Debian Noroot building and running, through 2026-10-25. Reconstructed from project files and retained validation output, not a commit-by-commit transcript; where precise timing or original native behavior couldn't be established, it's left unspecified.

Started from the original Android 4-era (2020) APK, with [pelya/debian-noroot](https://github.com/pelya/debian-noroot) as upstream. When APK and source disagreed, the APK won — it's what shipped. Decompiled Java and resources gave the option inventory. The replacement already launched before feature recreation started; keeping it and any existing Debian installation intact was a hard constraint the whole way. Active code went under `app/src/main/`, guest scripts under `assets/rootfs/`, tooling under `tools/` and `tests/`. Build junk in `build/`, distributables in `dist/`. The original APK became `references/apk/debian-noroot-2020.apk`; old native sources, Buster image scripts, patches, and previous runtimes went to `references/legacy-source/`, with uncertain material preserved under `references/xfce-panel-source/` and `references/development-notes/` instead of being deleted on a hunch. Legacy material stayed until recreation was accepted, and there's no itemized deletion inventory in the checkout, so this log doesn't invent one.

On the Galaxy S24 FE, extraction failed with `Cannot create rootfs link or directory`. Extraction was hardened for Android's filesystem/SELinux restrictions, including a copy fallback when hard-linking is refused; archive integrity, safe paths, and failure handling are checked by the native extractor and tests. The missing broad file-permission prompt was never the cause — imports use Android's Storage Access Framework and its selected-file grant. The rootfs also came out of the APK entirely: it builds as `dist/debian-rootfs.tar.xz` and pairs with `dist/main.2.org.debiannoroot.obb`, with the host build hard-linking the two names so only one set of bytes exists on disk; the phone just sees ordinary file bytes. First-run imports via file picker or OBB discovery, verifies size/checksum, extracts into private storage, and reuses it later. Missing or corrupt images get recovery messages; there's no hosted first-run download service, and existing ready rootfs installations survive APK updates.

Persistent display controls returned: resolution presets and custom sizes, fit/stretch/integer/custom scaling, filtering, aspect, orientation, immersive, overscan, refresh, and compatibility drawing. The input layer restores relative cursor plus absolute touch, click modes, hold timing, drag lock, two-finger scroll and right click, physical mouse/stylus, speed and acceleration, hover filtering, joystick/trackball/D-pad, gyro, and magnification; the keyboard covers remapping, configurable shortcuts and modifiers, soft-keyboard access, Unicode, and clipboard paste. These are adaptations of the observed option inventory, not byte-for-byte SDL parity — hardware/gyro acceptance and exact legacy behavior stay distinct from "implemented and passed basic device checks." The production display path moved to an embedded, Xorg-based Termux:X11 server plus a native Android surface renderer in its own `:x11` process, pinned at commit `7e5f5c5c9226e2cdab392b2efba91bc4bd86463e`, with a per-session MIT-MAGIC-COOKIE and X11 TCP off; VNC is retained only as an explicit host regression backend. Two concrete black-screen faults got fixed: native startup was running on a Binder worker without the Looper/Choreographer frame callbacks and blowing up with `DeadObjectException` (fixed by starting on Android's main Looper), and renderer filtering was receiving booleans instead of `GL_LINEAR`/`GL_NEAREST` and tripping `GL_INVALID_ENUM`. After that, XFCE and Firefox rendered on the S24 FE, cursor movement worked, and a touch-triggered right-click menu was observed. The `_XSERVTransmkdir` root-ownership warning is noise — app-private files are owned by the app UID, not the actual failure.

Guest PulseAudio runs an `android` null sink whose monitor sends stereo s16le 48 kHz PCM through a private Unix socket to Android AudioTrack; the app owns playback, app gain, buffering, audio focus, and shutdown. Microphone recording isn't implemented. Fresh images preinstall PulseAudio, client utilities, and ALSA plugins; older containers bootstrap through `debian-noroot-ensure-audio` under guest noninteractive sudo, repeatable and retried on a later launch, with DBus and session env keeping XFCE on the same bus. On the S24 FE, an older container installed the deps itself, created the sink, ran `paplay`, and opened an active 48 kHz stereo track. Choppy playback and dead volume controls turned out to be PRoot, config, and audio-delivery problems, not a backend swap: dropped the forced `PROOT_NO_SECCOMP=1` and let PRoot auto-optimize syscalls (a 30k-iteration small-I/O benchmark went from ~2.7s to ~0.4s, about 6.3× by mean), enabled hardware-buffer drawing on the S24 FE with compatibility still selectable, moved playback to an audio-priority thread with half-buffer prefill and a 200 ms default (adjustable 20–500 ms) and stopped re-setting unchanged AudioTrack gain, routed hardware volume keys to Android media volume with a native **Volume** dialog for phone and app sliders, and added `xfce4-pulseaudio-plugin` plus `pavucontrol` with panel-config-preserving migration and PulseAudio restore modules. A Firefox canvas workload hit ~57–60 native X11 fps and ~59.9 browser fps with zero AudioTrack underruns (baseline: 5,824), and a same-video YouTube check showed 1920×1080 AV1 30 fps with zero dropped of 191 sampled and zero underruns — small sample, not a general codec guarantee. During testing, input was accidentally sent into a pending APT confirmation and canceled it. Acknowledged. Subsequent tests use isolated commands, windows, and fresh UI bounds; an existing user's terminal is not an automation input target.

First-run setup now precedes the first session: independent app and guest languages (English, Spanish, Portuguese, French, German, Japanese, Polish), a guest X11 keyboard layout, and a searchable timezone, stored in separate regional prefs so a display-settings reset doesn't erase them. App language switches immediately; guest changes apply next session. `RegionalConfig` validates allowlists before generating config; startup writes guest locale/profile and timezone files, passes env to XFCE, and applies `setxkbmap` without XFCE stomping the choice. The Debian slim base dropped translations, so image prep installs locales, Firefox language packs, XFCE translations, Japanese fonts, and generates seven UTF-8 locales; older containers get `debian-noroot-ensure-locale`. Japanese Android Unicode input works; guest IME install and changing Android's selected keyboard don't — not implemented. Translation source lives in `tools/localization/strings.tsv` and generates Android language resources plus the `UiText` lookup; bundle language splitting is disabled so offline switching works. Desktop additions: persistent 100–400% zoom, a separate scrollable F1–F12 row, fullscreen with a **☰** restore button, and Drag/Ctrl/Alt toggles that set both labels explicitly and show their name from first use (checkmark when active, instead of Android's generic localized "off"). On-device checks covered all seven app languages; English app UI with Spanish Debian, a `latam` keyboard, and `Europe/Warsaw` timezone while the phone stayed on `America/El_Salvador`; UTF-8 setup; a no-op repeat of the locale helper; zoom; fullscreen exit; initial modifier labels; and native F1/F12 press/release captured with `xev`. Final APK installed, temporary test prefs removed, originals restored, phone left at regional setup with rootfs untouched. The Android module is Gradle 8.11.1, SDK/target 36, min API 26, NDK 27.2.12479018; PRoot and its freestanding loader build for ARM64 against pinned Termux PRoot and talloc sources, with a missing `string.h` include added, static talloc linking, and 16 KB linker alignment; the native extractor builds against pinned liblzma. The image builder uses a checksum-pinned Debian OCI base and signed repos under host PRoot/QEMU, and manifests record image size/checksum plus runtime/source identity. Host tests cover extraction/link safety, image integrity, geometry and cropping, pointer acceleration, session cancellation and exit, network isolation, regional validation, and translation key/placeholder consistency, with RFB kept as test-only support. `tools/env.sh` exposes cached local JDK/SDK/Gradle/compiler tooling without checking it in. The regional-settings build passed 12 Python tests, Java protocol/geometry/image/regional tests, native lifecycle/network checks, clean `assembleDebug` and `lintDebug`, all 34 pinned JNI signatures, full-image inspection and 16 KB ELF alignment, seven UTF-8 locales with XFCE translation preflight, and an ARM64 PulseAudio smoke of 519,824 bytes; the debug APK was 2,047,573 bytes and the OBB 339,023,440 bytes (hashes recorded in project files, not reprinted here). Backlog follow-up fixed a persisted launcher `phase=starting` that got stuck when reusing an already connected DesktopActivity — persisted phase ownership now stays with DesktopService, and five background/rotation/reopen cycles kept the X11 process and running phase; made `SessionLogs.begin` an isolated production helper that truncates `desktop.log` and `x11.log` each new session, with a host regression and three on-device session checks that planted markers while stopped and verified removal (the native launcher/server also open with `O_TRUNC`, and returning to a running desktop doesn't start another session); swapped the launcher icon to the byte-identical PNG from the original APK's `res/drawable/icon.png`; added an acceptance build type (`org.debiannoroot.acceptance`) that exercises first-run import/extraction from the real 339,023,440-byte OBB, rejects a 27-byte corrupt fixture, and runs a test-only FocusProbeActivity confirming transient media focus loss drops AudioTrack left/right gains from 1.0/1.0 to 0.0/0.0 and restores them; and wired GNU Make targets (`app`, `rootfs`, `test`, `clean`) behind `DEBUG=1`, `RELEASE=1`, and `BUILD_TYPE=debug|release`, with conflicting flags failing before building, `debug`/`release` shortcut targets, `--debug|--release` and `--rebuild-rootfs` on the shell build tool, per-variant JNI class selection, explicitly unsigned release output, and a `clean` that preserves tool/source/image caches, relinking material, licenses, references, and local evidence. Final verification passed for debug, release, and acceptance variants with zero lint errors (warnings remain), all 34 pinned JNI registrations matched each variant, full-image and APK/OBB pairing checks passed, an ARM64 PulseAudio smoke captured 699,164 bytes, `make test` passed its 12 Python checks plus the Java and native checks, and both `make app DEBUG=1` and `RELEASE=1` ran their actual variant builds and verifiers — apksigner accepted the debug signature, release is intentionally unsigned, and both packaged launcher PNGs match the original bytes. `smoke-desktop.py` gained `--timeout-scale` and failure-frame capture after blank-frame and Firefox-readiness failures that weren't counted as passes; a scaled retry with a disk-backed `TMPDIR` passed 438 decoded frames, terminal sudo, shared-folder writes, panel migration/customization, notifications, terminal/Thunar/Firefox launchers, Firefox's local test page, and 752,392 bytes of stereo PCM. Remaining boundaries: runtime testing on a 16 KB-page device is still open (the S24 FE reports 4096-byte pages and no additional device was available); physical mouse/stylus/joystick and exact legacy SDL behavior remain unproven; hardware guest 3D/VirGL, microphone input, a hosted image-download service, and a separate guest Japanese IME aren't implemented. The documented recreation differences were accepted as differences, not silently claimed as measured parity, and the only open acceptance checkbox is the 16 KB-page device test.
