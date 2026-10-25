# Third-party components

| Component | Version/source | Integration |
| --- | --- | --- |
| PRoot | [Termux PRoot 5.1.107.100](https://github.com/termux/proot/tree/v5.1.107.100) | Rebuilt ARM64 executable and freestanding loader; SysV IPC and fake identity extensions. GPL-2.0-or-later. |
| talloc | [Samba talloc 2.4.3](https://www.samba.org/ftp/talloc/talloc-2.4.3.tar.gz) | Linked statically into PRoot. LGPL-3.0-or-later; redistribution of the combined runtime must account for this license. |
| liblzma | [XZ Utils v5.8.4](https://github.com/tukaani-project/xz/tree/v5.8.4) | CMake builds the archive extraction library from a SHA-256-pinned source archive. Preserve the upstream per-file license notices. |
| Debian | [Official Debian Trixie ARM64 OCI image](https://hub.docker.com/_/debian) | Base manifest/layers pinned in `rootfs.lock.json`; desktop packages installed from Debian's signed repositories. Individual package copyright and license files remain in `/usr/share/doc`. |
| Termux:X11 | [Commit 7e5f5c5](https://github.com/termux/termux-x11/tree/7e5f5c5c9226e2cdab392b2efba91bc4bd86463e) | Pinned unmodified ARM64 Xorg-based native server/renderer from the matching upstream artifact. GPL-3.0; upstream components retain their licenses. |
| PulseAudio | [Debian Trixie package](https://packages.debian.org/trixie/pulseaudio) | Guest native protocol, null sink, and simple-protocol monitor transport. Debian copyright/license files remain in the image. |
| TigerVNC | [Debian Trixie package](https://packages.debian.org/trixie/tigervnc-standalone-server) | Guest X server used only by the explicit Linux regression harness; not the Android production backend. Preserve Debian's package copyright notices. |
| Firefox ESR | [Debian Trixie package](https://packages.debian.org/trixie/firefox-esr) | Guest web browser with software rendering defaults. Debian's package copyright notices remain in the image. |
| RFB | [Protocol specification](https://github.com/rfbproto/rfbproto) | Regression-harness Java decoder, using Raw, CopyRect, and DesktopSize encodings; not the production Android display transport. |
| XSDL | [Original behavioral reference](https://github.com/pelya/xserver-xsdl) | Original APK behavioral reference; current production X11 uses the embedded Termux:X11 engine. |

`dist/runtime.manifest.json` records exact PRoot/talloc source URLs, source checksums,
and binary checksums. `tools/build-runtime.py` contains the complete build
recipe and the two source adjustments: a missing `string.h` include and 16 KB
loader alignment. It also sets the runtime version string. License texts for
PRoot and talloc are in `licenses/`, together with GPLv3.

When distributing an APK, distribute the corresponding PRoot/talloc source
archives and the build recipe/changes with it, including material required for
LGPL relinking. The verified archives and built object files are retained under
`build/runtime/`. Debian package sources are available through Debian's source
repositories; preserve and satisfy the licenses for all redistributed packages.
The legacy runtime/preload assets are no longer packaged by the current app.

The X11 artifact/source commit and checksums are recorded in
`dist/x11.manifest.json`; `tools/prepare-x11.py` pins them in source. The artifact
and matching upstream source ZIP are retained in `build/x11/`. The JNI host
implements the upstream API without modifying the native library. The GPLv3
license is preserved as `licenses/Termux-X11-GPL-3.0.txt`. Upstream's complete
source tree includes pinned submodules; to obtain all corresponding component
sources use the upstream repository at the recorded commit and initialize its
submodules recursively. Preserve those sources, build recipes/patches, and
per-component license notices when distributing the combined application.

The nightly URL is mutable, but its expected SHA-256 is fixed. A later nightly
is rejected; use the cached pinned artifact or deliberately update/revalidate
the source/artifact pin together. This build does not silently take a new engine.
