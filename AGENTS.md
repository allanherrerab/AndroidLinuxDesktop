# Guidance for future AI agents

Read this file, [README.md](README.md), [Process.md](Process.md), and
[backlog.md](backlog.md) before changing the application. `AGENTS.md` is the
standard discovery entry point; `CLAUDE.md` also delegates here. These rules
apply throughout the repository. Explicit current user instructions take
precedence; historical requests do not authorize unrelated work.

## Product and preservation

- This is an ARM64, rootless Debian Trixie/XFCE Android app. Preserve working
  desktop, input, audio, installed rootfs and user files while extending it.
- The supplied `references/apk/debian-noroot-2020.apk` is the behavioral ground
  truth. Upstream is <https://github.com/pelya/debian-noroot>. State uncertainty
  when behavior cannot be recovered; do not invent an exact SDL parity claim.
- On 2026-10-25 the user accepted the differences in `PARITY.md` and explicitly
  authorized deleting references/legacy-source/, references/xfce-panel-source/,
  references/development-notes/ and their obsolete .gitmodules entries. This
  cleanup is complete. Retain the original APK, current pinned runtime/X11
  sources, relinking material, licenses and manifests.
- Do not reset or clean the worktree to normalize it. This checkout contains
  historical moves/deletions and active files that may be untracked. Inspect
  status/diffs and distinguish pre-existing changes from your own.
- Update the normal device installation with `adb install -r`. Never uninstall,
  clear app data, wipe/reimport a ready rootfs or replace home directories for
  an ordinary upgrade. Back up preferences before temporary changes and restore
  the user's choices afterward. A newly created acceptance test installation
  may be removed as test cleanup; it is separate from the normal app.
- Do not recreate missing historical docs/ reports as validation evidence.
  Current guidance and results live in root Markdown files.

## Source map

| Area | Location |
| --- | --- |
| UI, settings, session, input, audio | app/src/main/java/org/debiannoroot/ |
| Native X11 Java adapter and surface | app/src/main/java/com/termux/x11/ |
| Native launcher and safe extractor | app/src/main/cpp/ |
| Guest startup, upgrades and defaults | assets/rootfs/ |
| Translation source and generator | tools/localization/strings.tsv, tools/generate-ui-resources.py |
| Build, verification and device tools | tools/ |
| Host regressions | tests/ |
| Isolated test build and focus client | app/src/acceptance/ |
| Provenance | rootfs.lock.json, THIRD_PARTY.md, licenses/, dist/*.manifest.json |

build/ and dist/ are ignored generated outputs/caches/evidence. Keep source
changes in checked-in source paths, not only installed images or build trees.

## Architecture invariants

1. Android uses embedded native X11; VNC is only an explicitly selected host
   regression backend. Retain the separate :x11 process, per-session
   MIT-MAGIC-COOKIE authentication and disabled X11 TCP/network discovery.
2. Start the native X11 engine on the main Looper. Binder-thread startup lacks
   Choreographer and previously caused a native crash/black screen.
3. Preserve the pinned unmodified Termux:X11 JNI contract, registered fields,
   methods, descriptors and annotations. Run verify-x11-abi.py after adapter
   changes, selecting the variant's compiled classes as appropriate.
4. Filtering takes GL_LINEAR/GL_NEAREST, not booleans. Normal hardware-buffer
   drawing works on the S24 FE; retain compatibility drawing for other devices.
5. Preserve PRoot's automatic syscall optimization. PROOT_NO_SECCOMP=1 caused
   substantial slowdown; add a fallback only after diagnosing incompatibility.
6. Preserve image checksum/size pairing, traversal/link validation, cancellation
   and the Android hard-link copy fallback. Keep the rootfs external to the APK.
   Import/extract a private verified copy, not mutable external file bytes.
7. Refresh app-owned guest helpers on startup. Audio/locale/panel migrations
   must be repeatable, retry after failures and preserve unrelated user/dpkg/
   panel configuration. Fresh images include dependencies for offline startup.
8. Preserve AudioTrack audio-priority execution, prefill/buffering and gain
   changes only when needed. Phone media volume, app gain and guest sink/stream
   gain are distinct. Keep PulseAudio restore modules and panel migration.
9. Keep regional settings independent of display settings. Validate language,
   guest keyboard and timezone before generating configuration. App language
   updates immediately; guest choices apply next session. Keep seven complete
   translations/placeholders. Android selects its IME; guest keyboard layout
   selection does not change that Android setting.
10. Keep modifier/drag states explicitly labeled and fullscreen exit reachable.
    Preserve viewport/input mapping across scaling, crop, zoom and rotation.
11. DesktopService owns persisted session phase. Reopening a connected desktop
    must not set phase=starting in the launcher; CONNECTED may not fire again.
12. New sessions replace desktop.log and x11.log through SessionLogs.begin and
    native O_TRUNC logging. Reopening/rotating an active desktop is the same
    session and must not clear its logs. Do not add session-log archives.
13. PRoot is not a VM/kernel security boundary and guest sudo is emulated.
    Firefox disables its nested sandbox for compatibility and retains the
    Android app's privileges. Do not claim guest hardware acceleration,
    privileged device access or isolation beyond the implementation.

## Build workflow

Use Bash at the repository root. `. tools/env.sh` exposes cached local tools
when present without installing dependencies or changing system directories.
Use installed tools or read-only alternatives when rg/Git is initially absent.

```sh
. tools/env.sh
make test
make app DEBUG=1
# or: make app RELEASE=1 / make debug / make release
```

GNU Make also accepts BUILD_TYPE=debug|release; DEBUG=1 and RELEASE=1 conflict.
Debug APKs are development-signed; release APKs are explicitly unsigned until
signed with a release key. Build scripts run assembly, lint, native ABI and
artifact/image-pairing verification. Do not silently sign a release with the
inappropriate development key. Do not publish releases without authorization.

- Follow README's ordered bootstrap for a fresh checkout. Java/resources-only
  changes normally need appropriate host checks and make app; avoid rebuilding
  a large unchanged image.
- Guest asset changes: edit assets/rootfs/ first, then use
  `make rootfs ROOTFS_FLAGS="--reuse-rootfs --overlay-only"`. Confirm service
  helper refresh, builder inclusion and verifier coverage when adding files.
- Package/image changes: use `make rootfs ROOTFS_FLAGS="--reuse-rootfs"`, then
  make app to publish matching metadata/APK/OBB. Base images are not desktop
  replacements; preserve an initialized user's rootfs during upgrades.
- Localization: edit the TSV, regenerate UI resources, check translations and
  rebuild. Preserve offline access to all supported app languages.
- make clean removes an explicit list of generated outputs/deliverables. It
  retains toolchains, image/source caches, relinking material, licenses,
  references and local acceptance evidence. Do not broaden it to erase current
  pinned sources or user installations.
- X11's nightly URL is mutable but checksums are pinned. Preserve the matching
  cached artifact/source; deliberately update both pins and revalidate instead
  of silently accepting different upstream bytes. Preserve corresponding source
  and per-component licenses according to THIRD_PARTY.md.

## Validation and devices

```sh
python3 tools/verify-artifacts.py --full-image \
  --apk dist/debian-noroot-debug.apk --obb dist/main.2.org.debiannoroot.obb
python3 tools/verify-x11-abi.py --build-type debug
python3 tools/smoke-desktop.py --proot "$PWD/build/host-proot/bin/proot"
python3 tools/smoke-audio.py --proot "$PWD/build/host-proot/bin/proot"
```

Choose checks proportionate to changes. Do not equate host QEMU/VNC smoke,
JNI checks or ELF alignment with Android runtime acceptance. The available
Galaxy S24 FE SM-S721B runs Android 16 with 4 KB pages; 16 KB runtime acceptance
remains outstanding. Identify workload, sample, device and limits in performance
claims. Do not count timed-out host smoke runs as passes. Preserve failure
screenshots/logs; readiness timeout scaling for slower emulation does not skip
any assertions.

For authorized device testing:

- Discover current ADB device/address; never hardcode a historical wireless
  endpoint. Request an unlock if locked instead of bypassing the lock screen.
- Save guest work before lifecycle stress. tools/smoke-device-lifecycle.py
  stops/restarts sessions, checks rootfs/home/log behavior and restores rotation
  settings. Its --serial and --package options select the installation.
- Never type commands into an existing user terminal or unknown focused window.
  Use a dedicated process/window and current UI bounds. tools/device-guest.py
  runs isolated commands without terminal injection or disabling seccomp.
- The acceptance build uses org.debiannoroot.acceptance and private data separate
  from the normal app. Its test-only FocusProbeActivity requests transient
  media focus. Keep these exported test components out of production variants.
- Account for translated/uppercased UI labels and asynchronous preferences.
  Avoid edge gestures on toolbars, check current rotation/bounds and retain a
  fullscreen exit. Do not interrupt package installs or kill unrelated work.
- Clean up only identified test processes/files/browser profiles/servers/ADB
  forwards. Restore preferences and leave the device in a useful state.
- Diagnose with View session log and private files/desktop.log and x11.log. An
  isolated /tmp/.X11-unix ownership warning is not proof of a startup failure.
  Do not expose personal device information in committed evidence.

## Reporting

Describe what changed, why, checks actually run and untested limits. Keep README
focused on usage/builds, record material repairs/results in Process.md and update
backlog.md with actual status. Preserve PARITY.md's accepted differences and
uncertainties. Never mark device acceptance complete from host results alone.
Do not send messages, publish artifacts or create releases without user
instructions. Routine reversible edits/builds within the requested scope do
not require another approval flow.
