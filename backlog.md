# Current follow-up work

This file records current status from 2026-10-26 onward. It does not reconstruct
the missing historical backlog; earlier results remain in Process.md.

- [x] Implement opt-in VirGL with a session-owned Android EGL/GLES server,
  mapped-workload startup validation and software fallback; verify actual GPU
  rendering on the S24 FE separately from X11 presentation. See Process.md.
- [x] Add optional system-granted /storage access, retain /sdcard's app-folder
  mapping, and verify denial/grant plus initialized-rootfs upgrade preservation
  using the isolated acceptance installation.
- [ ] Extend VirGL coverage to other GPUs/GL applications, Android 8–10 storage
  permissions and a physical removable volume. The S24 FE smoke is not a
  universal hardware/performance claim.
- [x] Repair and validate concurrent VirGL clients: reproduce gears/STK
  disconnects, fix context-switch bookkeeping, and test reconnects/client death.
  Startup no longer launches glxgears; application-level SuperTuxKart coverage
  remains a device follow-up.

- [x] Complete directory/package migration and update build, test, asset refresh,
  generator and documentation references. Host tests and debug/release/acceptance
  assembly and lint passed; device acceptance remains a separate item below.
- [ ] Validate Android runtime behavior on a device with 16 KB memory pages.
- [ ] Investigate Android phantom-process trimming with concurrent guest
  sessions. During the 2026-10-26 isolated X11 acceptance test Android killed
  the normal app's background PRoot process for "Trimming phantom processes".
  Its existing installation was reopened; no global Android limits were changed.
- [x] Move portable first-party Java algorithms and guest filesystem routines to
  C, retaining Android framework adapters in Java. Host JNI, ASan/UBSan algorithm
  tests, complete-image C verification and debug/release/acceptance build/lint,
  ABI and artifact checks passed. Third-party libraries remain unchanged.
- [x] Make Android's existing native Termux:X11 selection explicit, remove the
  packaged VNC startup branch, isolate host VNC startup in test support and add
  APK/device display-path audits. See Process.md for validation and limits.
- [ ] Validate the first-party C migration on Android: first-run import and
  cancellation, initialized-rootfs upgrade, language/keyboard/timezone settings,
  scaling/input, session-log replacement and unchanged audio behavior. Host JNI
  checks and APK/native alignment checks do not constitute device acceptance.
- [ ] Validate the Java package migration on Android, including upgrade through
  the stable launcher alias, session startup/helper refresh, desktop rendering,
  input and audio. Host/build checks do not constitute device acceptance.

Accepted feature differences and hardware-testing limits remain in PARITY.md.
