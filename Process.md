# Development and repair log

This records the work performed to make the modern Debian Noroot application
build and run, through 2026-10-25. It is reconstructed from the project files,
conversation, and retained validation output; it is not a complete commit-by-commit
or command-by-command transcript. Where precise timing or original native
behavior was not established, it is left unspecified.

## 1. Establish the reference and preserve the working app

The user supplied the original 2020 APK and identified
[pelya/debian-noroot](https://github.com/pelya/debian-noroot) as the upstream
source. The APK was designated the ground truth when its behavior disagreed
with source. Its resources and decompiled Java settings were inspected to
inventory display, mouse, and convenience options.

The replacement was already able to launch before the feature recreation work.
Preserving its working behavior and existing Debian installation remained a
constraint throughout. Exact SDL-native acceleration curves, pressure/gyro
calibration, and some legacy control-layout behavior could not be proven from
the Java menus alone. Corresponding modern controls are adaptations, not claims
of byte-for-byte native parity.

## 2. Separate active source, references, and outputs

The active Android project was organized under `app/src/main/`, guest scripts
under `assets/rootfs/`, and build/test tooling under `tools/` and `tests/`.
Generated intermediates and local toolchains use `build/`; distributable outputs
use `dist/`. Runtime and image manifests record provenance and checksums.

The ambiguous original APK name became
`references/apk/debian-noroot-2020.apk`. Old native sources, Buster image scripts,
package patches, cross-compilation material, and previous runtimes were moved
to `references/legacy-source/`. Other uncertain material was retained under
`references/xfce-panel-source/` and `references/development-notes/` rather than
being deleted without certainty. Obsolete build/temp/duplicate material was
removed during cleanup; a complete itemized deletion inventory is not present
in the current checkout and is not reconstructed here as fact.

The user explicitly requested preservation of legacy material until recreation
is accepted. Running the backlog subsequently authorized feature work. That did
not authorize silently deleting the retained references. Earlier documents
referred to a `docs/` folder that is no longer present; current instructions are
consolidated into the root documentation without recreating those missing files.

## 3. Make extraction work on the Galaxy S24 FE

The phone reported `Cannot create rootfs link or directory`. Extraction was
hardened for Android's filesystem/SELinux restrictions, including a copy fallback
when creation of a rootfs hard link is denied. Archive integrity, safe paths,
and extraction failure handling are checked by the native extractor and tests.

The absence of a broad file-access permission prompt was not the cause: image
import uses Android's Storage Access Framework and its selected-file grant.
The corrected build was confirmed working by the user.

## 4. Externalize the Debian root filesystem

The large rootfs archive was removed from the APK. It is built as
`dist/debian-rootfs.tar.xz` and paired with
`dist/main.2.org.debiannoroot.obb`. The host build uses a hard link for the two
output names to avoid duplicate storage; the phone receives ordinary file bytes.
Only image metadata is staged into the APK.

First-run handling supports file-picker import or discovery in the app OBB
location, verifies size/checksum, extracts into private storage, and reuses the
ready installation on later launches. Missing/corrupt/mismatched images receive
recovery messages. There is no implemented hosted first-run image download
service. Existing ready rootfs installations survive APK updates.

## 5. Recreate display and input settings

Persistent display controls include resolution presets/custom dimensions,
fit/stretch/integer/custom scaling, filtering, aspect ratio, orientation,
immersive mode, overscan, refresh rate, and compatibility drawing. Viewport
geometry and input mapping account for cropping and scale choices.

The input layer restores relative cursor movement as well as absolute touch,
click modes, hold timing, drag lock, two-finger scrolling/right click, physical
mouse/stylus input, pointer speed/acceleration, hover filtering, joystick/
trackball and D-pad controls, gyro options, and magnification. Keyboard features
include remapping, configurable shortcuts/modifiers and their appearance,
Android soft-keyboard access, Unicode text, and explicit clipboard paste.

These controls were implemented from the observed option inventory. Full
hardware/gyro acceptance and exact original SDL-native behavior remain distinct
from implementation and basic device checks.

## 6. Replace Android VNC presentation with embedded X11

At the user's request, the production display path was changed to an embedded,
Xorg-based Termux:X11 server and native Android surface renderer. It runs in
its own `:x11` Android process and does not require another installed X server
app. A Java adapter preserves the native engine's registered JNI contract.
The engine and matching upstream source are pinned at commit
`7e5f5c5c9226e2cdab392b2efba91bc4bd86463e`.

PRoot launches XFCE against the embedded X server using a per-session
MIT-MAGIC-COOKIE. X11 TCP is disabled. VNC is retained only for an explicitly
selected host regression backend, allowing automated guest inspection under
ARM64 emulation. It is not the Android production video transport.

Session handling includes start/stop/cancellation, process cleanup, logs, and
private socket lifecycle. Host checks cover launcher behavior and isolation;
they do not replace Android lifecycle testing.

## 7. Diagnose the native X11 black screen

Two concrete faults were fixed:

| Fault | Correction |
| --- | --- |
| Native startup ran on a Binder worker without the Looper/Choreographer needed for frame callbacks, producing a native failure and `DeadObjectException`. | Start the native X11 engine on Android's main Looper. |
| Renderer filtering received boolean integers instead of valid OpenGL filter constants, producing `GL_INVALID_ENUM`. | Pass `GL_LINEAR` or `GL_NEAREST`. |

The `_XSERVTransmkdir` warning about root ownership of `/tmp/.X11-unix` was
separated from actual startup failures: app-private files are owned by the
Android application UID. Following the fixes, XFCE and Firefox were visible
on the S24 FE; cursor movement and a touch-triggered right-click menu were
observed. The user's existing rootfs was retained.

## 8. Add first-run PulseAudio setup and Android playback

Guest PulseAudio provides an `android` null sink; its monitor sends stereo
signed 16-bit, 48 kHz PCM through a private Unix socket to Android AudioTrack.
The app handles playback enablement, app gain, buffering, audio focus, and
transport shutdown. Microphone recording was not implemented.

Fresh images preinstall PulseAudio, client utilities, and ALSA plugins. Existing
containers run the app-owned `debian-noroot-ensure-audio` helper using guest
noninteractive sudo when packages are missing. The helper is repeatable, skips
installed dependencies, and retries after a failed download on a later launch.
DBus and session environment setup keep XFCE and its audio controls on the same
session bus.

On the S24 FE, an older container without PulseAudio installed the dependencies
automatically, created the sink, completed `paplay`, and opened an active Android
48 kHz stereo track. Subsequent setup skipped installation.

## 9. Fix choppy playback and volume control

The user reported choppy video/audio and ineffective volume controls. Diagnosis
confirmed the Android session already used X11; performance required fixes in
PRoot, drawing configuration, and audio delivery rather than another backend
replacement.

- Removed forced `PROOT_NO_SECCOMP=1`, allowing PRoot's automatic syscall
  optimization. A 30,000-iteration small-I/O device benchmark improved from
  2.676/2.874 seconds to 0.515/0.366 seconds, about 6.3× by mean duration.
- Enabled normal Android hardware-buffer drawing on the S24 FE rather than
  compatibility drawing. Compatibility mode remains selectable for other devices.
- Moved playback to an audio-priority thread, added half-buffer prefill, set the
  default buffer to 200 ms, and avoided repeated unchanged AudioTrack gain calls.
  Buffer size remains adjustable from 20–500 ms.
- Routed hardware volume keys to Android media volume and added a native
  **Volume** dialog with phone media and app playback sliders.
- Added `xfce4-pulseaudio-plugin` and `pavucontrol` to fresh images and the older
  container migration. The panel helper adds the missing plugin while preserving
  existing panel configuration. PulseAudio restore modules persist sink and
  stream volume settings.

The controlled Firefox canvas workload delivered approximately 57–60 native X11
frames/s and ~59.9 browser frames/s. AudioTrack reported zero underruns during
that test, compared with 5,824 in the retained baseline. Phone key tests changed
media volume 10→11→10; an app gain setting of 22% was observed in AudioFlinger.
Guest volume/mute changed captured PCM as expected.

A subsequent same-video YouTube screenshot reported 1920×1080 at 30 FPS, AV1,
with 0 dropped frames out of 191 sampled, and zero AudioTrack underruns. This is
a small device sample, not a general codec/video performance guarantee. The
user subsequently confirmed the result worked well.

During testing, input was accidentally sent into an existing terminal's pending
APT confirmation and canceled it. This was acknowledged. Subsequent tests use
isolated commands/windows and fresh UI bounds; an existing user's terminal must
never be treated as an automation input target.

## 10. Add first-install regional setup and desktop controls

The app now opens a setup screen before the first Debian session. App and guest
language choices are independent: English, Spanish, Portuguese, French, German,
Japanese, and Polish. Settings also select a guest X11 keyboard layout and a
searchable timezone. Choices persist in separate regional preferences so a
display-settings reset does not erase them. App language switches immediately;
guest changes apply on the next session start.

`RegionalConfig` validates allowlisted languages/layouts and timezone identifiers
before generating guest configuration. Startup writes guest locale/profile and
timezone files, passes the selected environment to XFCE, and applies `setxkbmap`
without XFCE's saved layout overriding the selection.

The Debian slim base excluded translation files. Image preparation removes only
the relevant dpkg locale exclusion, installs locales and Firefox language packs,
restores XFCE translations, adds Japanese fonts, and generates all seven UTF-8
locales. Existing containers run `debian-noroot-ensure-locale` when needed;
preparation/failure messages and session logs explain progress and retries.
Japanese Android Unicode input is supported; guest IME installation and changing
Android's selected keyboard are not implemented.

The translation source is `tools/localization/strings.tsv`.
`tools/generate-ui-resources.py` generates Android language resources and the
`UiText` lookup. Bundle language splitting is disabled to retain the app's
language choices for offline switching.

Desktop additions include persistent 100–400% zoom, a separate scrollable
F1–F12 row, and fullscreen with a **☰** restore button. Drag/Ctrl/Alt toggle
buttons now set both their on/off labels explicitly and display their name from
first use, adding a checkmark when active instead of showing Android's generic
localized “off” label.

On-device checks covered all seven app languages; English app UI with Spanish
Debian, a `latam` keyboard, and `Europe/Warsaw` timezone; unchanged phone timezone
`America/El_Salvador`; successful UTF-8 setup; and a no-op repeat of the locale
helper. Zoom, fullscreen exit, and initial modifier labels passed. Native F1/F12
press and release events were captured with `xev`.

The final APK was installed. Temporary regional test preferences were removed
and original display preferences restored. The phone was left at regional setup
so the user could choose their own settings; the rootfs was not replaced.

## Build and verification infrastructure

The Android module uses Gradle 8.11.1, SDK/target 36, minimum API 26, and NDK
27.2.12479018. PRoot and its freestanding loader are built for ARM64 with pinned
Termux PRoot and talloc sources. The recipe adds a missing `string.h` include,
sets the runtime version, statically links the talloc object, and requests
16 KB linker alignment for runtime/loader compatibility. The native extractor
builds against pinned liblzma source. Corresponding source archives, recipes,
objects, and license material are retained for provenance and redistribution.

The desktop image builder uses a checksum-pinned Debian OCI base and signed
Debian package repositories under host PRoot/QEMU. Image scripts install XFCE,
Firefox, desktop integration, audio, and later locale packages. Generated
manifests record image size/checksum and runtime/source identities. Direct
Gradle builds consume prepared inputs; the top-level APK script prepares X11,
runs assembly/lint and ABI/integrity checks, then publishes the APK and matching
OBB. Publication uses temporary output names before replacement. The X11
preparation helper also creates its license output directory when needed.

The host suite was extended around extraction/link safety, image integrity,
display geometry/cropping, pointer acceleration, native session cancellation and
exit handling, network isolation, regional value validation, and translation
key/placeholder consistency. The RFB decoder remains test support. Desktop
smoke tests exercise sudo, shared files, desktop applications, and audio monitor
transport; Android presentation is verified separately on the device. Cached
local JDK/SDK/Gradle/compiler tooling is exposed by `tools/env.sh` without making
those generated dependencies part of the checked-in application source.

## Validation and artifact snapshot

The regional-settings implementation checks passed before that documentation update:

- 12 Python regression tests, Java protocol/geometry/image/regional tests, and
  native launcher lifecycle/network isolation checks.
- Android `assembleDebug` and `lintDebug`, with no lint errors.
- All 34 registered native X11 JNI signatures.
- Full image inspection, artifact integrity, and 16 KB ELF alignment.
- Seven UTF-8 image locales and XFCE translation preflight.
- ARM64 PulseAudio smoke test: 519,824 bytes of 48 kHz stereo s16le PCM.
- Device regional, toolbar, native function-key, volume, and rendering checks
  described above.

| Artifact at the regional-settings build | Bytes | SHA-256 |
| --- | ---: | --- |
| `debian-noroot-debug.apk` | 2,047,573 | `775907745188bd31f243192ba1b3a43173fbe40ae4a1b320844b47985080dee8` |
| `main.2.org.debiannoroot.obb` | 339,023,440 | `67f8da06b8ed32d8e76ff75bd44405eb04b06207c3665179dde4ae2fc94f2fbd` |

These hashes identify a historical build, not a promise that future builds are
identical. Local evidence includes `build/s24-black-screen-validation.json`,
`build/audio-bootstrap-validation.json`, `build/performance-validation.json`,
`build/regional-validation.json`, regional UI screenshots/key logs, and build/
smoke logs. `build/` is ignored and these files will not accompany a source-only
clone. The significant results are recorded here so the history remains useful.

## Remaining boundaries

The S24 FE acceptance follow-up below covers lifecycle, Samsung keyboard,
virtual pointer/gyro, focus and first-run import/extraction. Runtime testing on a
16 KB-page device remains open: the available S24 FE reports 4096-byte pages and
the user confirmed no working additional device. Physical mouse/stylus/joystick
and exact legacy SDL behavior remain unproven; see [PARITY.md](PARITY.md).
Hardware guest 3D/VirGL, microphone input, a hosted image-download service and
a separate guest Japanese IME are not implemented. The user accepted the documented recreation differences on 2026-10-25; the
obsolete-reference cleanup is recorded below.


## 11. Backlog acceptance and follow-up requests (2026-10-25)

Restored the missing canonical `Agents.md` from the guidance found in
`CLAUDE.md`; the latter now delegates to it. Replaced stale backlog/reference
links to absent docs/ reports with the root documentation and a new `PARITY.md`
review matrix. The matrix distinguishes implemented modern controls from SDL
rendering switches, arbitrary control placement/gesture mappings, download/data
configuration, calibration and hardware behavior that are not recreated or
remain unproven. After reviewing the matrix, the user explicitly accepted the differences and
authorized deleting references/legacy-source/, references/xfce-panel-source/,
references/development-notes/ and their obsolete .gitmodules entries. Those
folders and the obsolete .gitmodules file were removed. Current dependency
sources, relinking material, licenses and the original APK were retained; the
APK checksum remained unchanged. No Git reset, clean or metadata removal was
used to normalize unrelated historical changes.

The launcher previously wrote phase=starting before asking the service to open
a desktop. Reusing an already connected DesktopActivity need not emit another
CONNECTED event, leaving the UI's saved phase stuck at starting. The launcher
now leaves persisted phase ownership with DesktopService. Five background,
rotation and reopen cycles passed on the final debug APK, retaining the X11
process and running phase. Three stop/start cycles retained a guest-home test
marker and the original ready-rootfs marker's inode/size/mtime.

SessionLogs.begin explicitly truncates desktop.log and x11.log for each new
session. This behavior already existed as inline writes; it is now an isolated
production helper with a host regression proving removal of longer previous
contents. The native launcher/server also open their current log with O_TRUNC.
Device checks planted prior-session markers in both logs while stopped and
verified removal on each of three new sessions. No session-log archive is kept.
Returning to a running desktop does not begin another session.

Samsung HoneyBoard was the selected Android IME. Tapping its actual Spanish
keyboard generated native X11 press/release events for a, A, ñ, BackSpace and
Return in an isolated xev window. Relative pointer motion, left tap and
long-press right-click also reached xev. The user tilted the phone with gyro
control enabled and confirmed pointer movement; the temporary setting was
restored afterward.

Added an acceptance build type with a separate application id,
org.debiannoroot.acceptance, so first-run tests do not replace the normal app's
private rootfs/preferences. It showed the missing-image recovery message,
imported the matching 339,023,440-byte OBB through Android's file picker,
extracted a fresh rootfs and displayed XFCE. A 27-byte corrupt OBB fixture was
rejected with the checksum/size recovery message. The temporary acceptance app
and transferred test files were removed after testing.

Its test-only FocusProbeActivity requested transient media focus while the
normal app played PCM. The same active AudioTrack's left/right gains changed
from 1.0/1.0 to 0.0/0.0 during focus loss and back to 1.0/1.0 after release.
The captured track reported zero underruns. This tests transient focus loss and
recovery, not calls, Bluetooth routing or permanent focus-loss policy.

The launcher icon is now the exact PNG from res/drawable/icon.png in the
original APK. The packaged PNG bytes were compared with the reference. The
updated signed debug APK was installed with adb install -r, preserving the
normal rootfs. Display, regional and Android rotation settings were restored;
the phone was left in the running normal desktop.

GNU Make now exposes app, rootfs, test and clean. App defaults to debug; DEBUG=1,
RELEASE=1 and BUILD_TYPE=debug|release select the variant, with debug/release
shortcut targets. Conflicting flags fail before building. The shell build tool
also supports --debug|--release with --rebuild-rootfs. JNI verification selects
the compiled classes for that variant. Release artifacts are explicitly
unsigned; no release signing key was created. clean uses an explicit output
list and preserves tool/source/image caches, relinking material, licenses,
references and local evidence. make help and clean dry-run were checked;
make rootfs ROOTFS_FLAGS=--help exercised target forwarding without rebuilding
an unchanged system image.

The new tools/device-guest.py runs isolated ADB guest commands without injecting
text into a terminal and without disabling PRoot seccomp. The new
tools/smoke-device-lifecycle.py repeats the lifecycle/log/preservation checks
and restores rotation settings in cleanup. Both accept device selection; the
latter saves JSON and a screenshot under build/.

Local evidence: build/backlog-device/ (keyboard/xev, focus and lifecycle logs,
JSON, screenshots and preference backups), build/backlog-host-final.log,
build/backlog-make-test.log, build/backlog-full-image.log,
build/backlog-session-log-build.log, build/backlog-icon-final-build.log,
build/backlog-make-app.log and build/backlog-make-release.log. These ignored
files are local evidence; the significant results are recorded above.


Final build verification passed for debug, release and acceptance variants with
zero lint errors (warnings remain). All 34 pinned JNI registrations matched each
variant checked. Full-image verification and APK/OBB pairing passed; the ARM64
PulseAudio host smoke captured 699,164 bytes of stereo 48 kHz s16le PCM. make test
passed 12 Python checks, the Java protocol/geometry/image/regional/session-log
checks and native lifecycle/network checks. make app DEBUG=1 and RELEASE=1 both
ran their actual variant builds and verifiers. apksigner accepted the debug
signature; the release APK is intentionally unsigned. Both packaged launcher
PNGs match the original bytes (release resource optimization changes its ZIP
entry name, not the PNG data).

| Backlog follow-up artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| debian-noroot-debug.apk | 2118536 | `ed2a368660c4ce23c745cc9976c0718d769de9940a2c81c6fc2bd06872dda28f` |
| debian-noroot-release-unsigned.apk | 1820727 | `038b94ba30e8cfb1c22e5d23907c8900663b8d396cea9f9e363af7d2da557cfb` |
| main.2.org.debiannoroot.obb | 339023440 | `67f8da06b8ed32d8e76ff75bd44405eb04b06207c3665179dde4ae2fc94f2fbd` |

The normal device installation uses the signed debug APK. No unsigned release
was installed or externally published. The OBB bytes were unchanged by these
application/build-tool changes.


Host desktop revalidation initially failed: one run returned a blank frame,
then unscaled retries timed out waiting for Firefox/window-title readiness.
These were not counted as passes. Added failure-frame capture in DesktopSmoke
and a positive --timeout-scale option in smoke-desktop.py; it multiplies bounded
readiness deadlines and the outer timeout, retaining every assertion. The final
run used a disk-backed TMPDIR and --timeout-scale 2, with no concurrent Gradle
build, and passed all assertions: 438 decoded frames, terminal keyboard/sudo,
shared-folder writes, panel migration/customization, notifications, terminal/
Thunar/Firefox launchers, Firefox's local test page, and 752,392 bytes of stereo
48 kHz s16le PCM. Final evidence is build/backlog-desktop-scaled.log and the
corresponding screenshots. The failed runs remain in backlog-desktop-smoke.log,
backlog-desktop-retry.log and backlog-desktop-final.log, with failure screenshots
where available. This does not establish Android presentation or 16 KB runtime
support; device acceptance is separately recorded above.

All actionable S24 FE backlog work and the explicitly approved reference cleanup
are complete. The only open acceptance checkbox in backlog.md is runtime testing
on a 16 KB-page Android device, unavailable in this session. Exact SDL/physical
hardware boundaries remain explicitly documented and were accepted as recreation
differences, not silently claimed as measured parity.
