# Third-party components

The launcher icon is copied unchanged from `res/drawable/icon.png` in the supplied
`references/debian-noroot-2020.apk`, replacing the modern approximation.
Its SHA-256 is `1254c38af8cd257616d40f883e6bbd2ccb132580895ce1178f7aaddfd7dae6d2`.
The original APK and its upstream attribution are retained.

| Component | Version/source | Integration |
| --- | --- | --- |
| PRoot | [Termux PRoot 5.1.107.100](https://github.com/termux/proot/tree/v5.1.107.100) | Rebuilt ARM64 executable and freestanding loader; SysV IPC and fake identity extensions. GPL-2.0-or-later. |
| talloc | [Samba talloc 2.4.3](https://www.samba.org/ftp/talloc/talloc-2.4.3.tar.gz) | Linked statically into PRoot. LGPL-3.0-or-later; redistribution of the combined runtime must account for this license. |
| liblzma | [XZ Utils v5.8.4](https://github.com/tukaani-project/xz/tree/v5.8.4) | CMake builds the archive extraction library from a SHA-256-pinned source archive. Preserve the upstream per-file license notices. |
| Debian | [Official Debian Trixie ARM64 OCI image](https://hub.docker.com/_/debian) | Base manifest/layers pinned in `rootfs.lock.json`; desktop packages installed from Debian's signed repositories. Individual package copyright and license files remain in `/usr/share/doc`. |
| Termux:X11 | [Commit 7e5f5c5](https://github.com/termux/termux-x11/tree/7e5f5c5c9226e2cdab392b2efba91bc4bd86463e) | Pinned unmodified ARM64 Xorg-based native server/renderer from the matching upstream artifact. GPL-3.0; upstream components retain their licenses. |
| VirGL renderer | [virglrenderer 1.3.0](https://gitlab.freedesktop.org/virgl/virglrenderer/-/tree/virglrenderer-1.3.0) | Android EGL/GLES vtest server; static linkage, private Unix socket, no DRM/GBM/Vulkan backend. MIT. Selected pinned Termux Android patches and a local context-switch repair. |
| libepoxy | [1.5.10](https://github.com/anholt/libepoxy/tree/1.5.10) | Statically linked into the Android VirGL server for EGL/GLES dispatch. MIT. |
| PulseAudio | [Debian Trixie package](https://packages.debian.org/trixie/pulseaudio) | Guest native protocol, null sink, and simple-protocol monitor transport. Debian copyright/license files remain in the image. |
| TigerVNC | [Debian Trixie package](https://packages.debian.org/trixie/tigervnc-standalone-server) | Guest X server used only by the explicit Linux regression harness; not the Android production backend. Preserve Debian's package copyright notices. |
| Firefox ESR | [Debian Trixie package](https://packages.debian.org/trixie/firefox-esr) | Guest web browser with software rendering defaults. Debian's package copyright notices remain in the image. |
| RFB | [Protocol specification](https://github.com/rfbproto/rfbproto) | Regression-harness Java decoder, using Raw, CopyRect, and DesktopSize encodings; not the production Android display transport. |
| XSDL | [Original behavioral reference](https://github.com/pelya/xserver-xsdl) | Original APK behavioral reference; current production X11 uses the embedded Termux:X11 engine. |

`dist/runtime.manifest.json` records exact PRoot/talloc source URLs, source checksums,
and binary checksums. `tools/build/build-runtime.py` contains the complete build
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
`dist/x11.manifest.json`; `tools/build/prepare-x11.py` pins them in source. The artifact
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

`tools/build/prepare-virgl.py` builds `libvirgl_server.so` (a PIE executable named
for Android native-library packaging) from checksum-pinned virglrenderer and
libepoxy sources. The selected Android patches come from Termux packages commit
`9c4e57f8e1d8525ea9267572351a15449de34292`; the recipe adapts the GBM patch's Meson
context to 1.3.0 with checked replacements. ANGLE-specific patches are not used.
The Termux DXT decompression patch is also excluded: it redefines immutable
compressed textures during upload and produces invalid operations/corrupt pixels.
Upstream format probing and compressed uploads are retained. The original patch
and its MIT notices remain available for provenance of earlier builds.
The local `tools/build/patches/virgl-context-switch.patch` isolates capability
probes in the host GL context so a new client cannot disturb an active client's
texture/framebuffer bindings.
It also updates context-switch bookkeeping after creating a client/subcontext.
Its SHA-256 is recorded in the manifest and participates in build-cache checks;
retain this patch alongside the recipe when redistributing the renderer.
The local `tools/build/patches/virgl-fence-ownership.patch` prevents destruction
of one context from invalidating another context's in-flight fence. Its hash
also participates in the manifest and cache checks; preserve both local patches.
The source archives, original patches, static libraries and build tree remain in
`build/graphics/`. `dist/graphics.manifest.json` records URLs, checksums and the
binary identity. Preserve these sources and the build recipe with redistributed
binaries. Notices are in `licenses/virglrenderer-MIT.txt`, `libepoxy-MIT.txt` and
`VirGL-Android-texture-MIT.txt`. Pinned Meson, PyYAML and pkgconf are build tools;
they are not shipped inside the APK.
