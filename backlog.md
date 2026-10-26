# Current follow-up work

This file records current status from 2026-10-26 onward. It does not reconstruct
the missing historical backlog; earlier results remain in Process.md.

- [x] Complete directory/package migration and update build, test, asset refresh,
  generator and documentation references. Host tests and debug/release/acceptance
  assembly and lint passed; device acceptance remains a separate item below.
- [ ] Validate Android runtime behavior on a device with 16 KB memory pages.
- [x] Move portable first-party Java algorithms and guest filesystem routines to
  C, retaining Android framework adapters in Java. Host JNI, ASan/UBSan algorithm
  tests, complete-image C verification and debug/release/acceptance build/lint,
  ABI and artifact checks passed. Third-party libraries remain unchanged.
- [ ] Validate the first-party C migration on Android: first-run import and
  cancellation, initialized-rootfs upgrade, language/keyboard/timezone settings,
  scaling/input, session-log replacement and unchanged audio behavior. Host JNI
  checks and APK/native alignment checks do not constitute device acceptance.
- [ ] Validate the Java package migration on Android, including upgrade through
  the stable launcher alias, session startup/helper refresh, desktop rendering,
  input and audio. Host/build checks do not constitute device acceptance.

Accepted feature differences and hardware-testing limits remain in PARITY.md.
