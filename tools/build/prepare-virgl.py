#!/usr/bin/env python3
"""Build a pinned standalone Bionic VirGL/vtest server using Android EGL/GLES.

Static libepoxy/virglrenderer linkage avoids Termux-prefix dependencies. No ANGLE,
Vulkan, DRM device, GBM, or system installation is required by this build.
"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import urllib.request
import zipfile

PROJECT = Path(__file__).resolve().parents[2]
BUILD = PROJECT / "build/graphics"
TERMUX = "9c4e57f8e1d8525ea9267572351a15449de34292"
SOURCES = {
    "virgl.tar.gz": ("https://gitlab.freedesktop.org/virgl/virglrenderer/-/archive/virglrenderer-1.3.0/virglrenderer-virglrenderer-1.3.0.tar.gz", "56170f8caa1bb642a2624b649e3bcca095ec2834814e5c308efc8a85a709e4ce"),
    "epoxy.tar.gz": ("https://codeload.github.com/anholt/libepoxy/tar.gz/refs/tags/1.5.10", "a7ced37f4102b745ac86d6a70a9da399cc139ff168ba6b8002b4d8d43c900c15"),
    "pkgconf.tar.xz": ("https://distfiles.ariadne.space/pkgconf/pkgconf-2.3.0.tar.xz", "3a9080ac51d03615e7c1910a0a2a8df08424892b5f13b0628a204d3fcce0ea8b"),
    "meson.whl": ("https://files.pythonhosted.org/packages/e5/2b/46bda4ef5a7ae4135dbfe27fc0368c44e5a349a897a54fdf2cedb8dcb66e/meson-1.7.2-py3-none-any.whl", "82c6818dc81743c96de3a458f06175776ebfde4081195ea31ea6971838f25e38"),
    "yaml.tar.gz": ("https://files.pythonhosted.org/packages/54/ed/79a089b6be93607fa5cdaedf301d7dfb23af5f25c398d5ead2525b063e17/pyyaml-6.0.2.tar.gz", "d584d9ec91ad65861cc08d42e834324ef890a082e591037abe114850ff7bbc3e"),
}
PATCHES = {
    "0001-do-not-use-gbm": "3c6c26054a8c575e7b19bc374bc0b5976506df10bed38dde46259e0252c87270",
    "0002-avoid-gnu offsetof-extensions-error".replace(" ", "-"): "886b58f56917b29b3dc47e2dc7b277a1510209702190133d58ff0f5aab61affb",
    "0004-Fallback-to-ashmem": "3848656628611e71bfab705d23146390e7778132ccb327a9368d71dc81ef1f17",
    "0005-Enable-debug-for-EGL": "7c40e61af71b53e2067254727fd7126ca05a97139b35e62f1b065222c2584828",
    "0006-Disable-feature-feat_dual_src_blend": "eeaa564b1f0317e6285d74414cbf58dbe9f083e30c3cad034ff800d3d2b6518e",
    "0008-Impl-DXTn-S3TC-texture-decompression-in-virglrenderer": "40e8f14aa6b9f95795d64bbd55346e7ba1401f7b3e8f06bdade311bfa27e66f5",
    "0009-disable-termux-detection": "5e466a64a3d375fa23dffd1bde72ddc51f6adce328e57df6c214b29bdd7ca337",
    "0010-disable-memfd_create-timespec_get": "3fc7b763aa16addbcbd81145b1d9cde1227bef60961feafed51594440a0ab65c",
}
LOCAL_PATCHES = [PROJECT / "tools/build/patches/virgl-context-switch.patch"]
for name, sha in PATCHES.items():
    SOURCES[name + ".patch"] = (f"https://raw.githubusercontent.com/termux/termux-packages/{TERMUX}/packages/virglrenderer-android/{name}.patch.beforehostbuild", sha)


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def main():
    downloads = BUILD / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    target = PROJECT / "dist/runtime/arm64-v8a/libvirgl_server.so"
    manifest = PROJECT / "dist/graphics.manifest.json"
    recipe = digest(Path(__file__))
    local_patches = {path.name: digest(path) for path in LOCAL_PATCHES}
    if manifest.exists() and target.exists():
        old = json.loads(manifest.read_text())
        if (old.get("recipe_sha256") == recipe
                and old.get("local_patches") == local_patches
                and old["binaries"].get(target.name) == digest(target)):
            print("Verified cached Android VirGL renderer")
            return
    for name, (url, sha) in SOURCES.items():
        path = downloads / name
        if not path.exists():
            print("Downloading " + name, flush=True)
            with urllib.request.urlopen(url, timeout=90) as source, path.with_suffix(".partial").open("wb") as out:
                shutil.copyfileobj(source, out)
            path.with_suffix(".partial").replace(path)
        if digest(path) != sha:
            raise RuntimeError("Source checksum mismatch: " + str(path))
    meson = BUILD / "meson"
    meson.mkdir(exist_ok=True)
    with zipfile.ZipFile(downloads / "meson.whl") as archive:
        archive.extractall(meson)
    with tarfile.open(downloads / "yaml.tar.gz") as archive:
        archive.extractall(BUILD, filter="data")
    pkgconf = BUILD / "host/bin/pkgconf"
    if not pkgconf.exists():
        with tarfile.open(downloads / "pkgconf.tar.xz") as archive:
            archive.extractall(BUILD, filter="data")
        source = BUILD / "pkgconf-2.3.0"
        env = {**os.environ, "CC": os.environ.get("CC", shutil.which("cc")), "CFLAGS": "-O2"}
        subprocess.run(["./configure", "--prefix=" + str(BUILD / "host"), "--disable-shared"], cwd=source, env=env, check=True)
        subprocess.run(["make", "-j4", "install"], cwd=source, env=env, check=True)
    sdk = Path(os.environ["ANDROID_SDK_ROOT"])
    toolchain = sdk / "ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin"
    ninja = sdk / "cmake/3.22.1/bin/ninja"
    work = BUILD / "work"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir()
    for name in ("virgl.tar.gz", "epoxy.tar.gz"):
        with tarfile.open(downloads / name) as archive:
            archive.extractall(work, filter="data")
    virgl = work / "virglrenderer-virglrenderer-1.3.0"
    epoxy = work / "libepoxy-1.5.10"
    for name in PATCHES:
        patch = (downloads / (name + ".patch")).read_text()
        if name == "0001-do-not-use-gbm":
            # 1.3 moved Meson's platform declarations; retain the same Android
            # change with exact replacements instead of accepting fuzzy hunks.
            config = virgl / "meson.build"
            text = config.read_text()
            for before, after in (("with_host_darwin =", "with_host_android = host_machine.system() == 'android'\nwith_host_darwin ="),
                                  ("if with_host_windows or with_host_darwin", "if with_host_windows or with_host_darwin or with_host_android")):
                if text.count(before) != 1:
                    raise RuntimeError("Unexpected upstream Meson Android integration")
                text = text.replace(before, after)
            config.write_text(text)
            patch = patch[patch.index("diff -ur orig/src/"):]
        subprocess.run(["git", "apply", "--unsafe-paths", "--directory=" + str(virgl)], input=patch.encode(), cwd=PROJECT, check=True)
    for path in LOCAL_PATCHES:
        subprocess.run(["git", "apply", "--unsafe-paths", "--directory=" + str(virgl), str(path)],
                       cwd=PROJECT, check=True)
    prefix = work / "prefix"
    cross = work / "android.ini"
    cross.write_text(f"""[binaries]
c = '{toolchain}/aarch64-linux-android26-clang'
cpp = '{toolchain}/aarch64-linux-android26-clang++'
ar = '{toolchain}/llvm-ar'
strip = '{toolchain}/llvm-strip'
pkg-config = '{pkgconf}'
[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
[properties]
needs_exe_wrapper = true
[built-in options]
c_link_args = ['-Wl,-z,max-page-size=16384']
cpp_link_args = ['-Wl,-z,max-page-size=16384']
""")
    env = {**os.environ, "PYTHONPATH": str(meson) + os.pathsep + str(BUILD / "pyyaml-6.0.2/lib"), "PKG_CONFIG_LIBDIR": str(prefix / "lib/pkgconfig"),
           "PKG_CONFIG_PATH": "", "PATH": str(ninja.parent) + os.pathsep + os.environ["PATH"]}
    # Host toolchain flags must never leak into the Android cross build.
    for name in ("CC", "CXX", "CFLAGS", "CPPFLAGS", "LDFLAGS", "PKG_CONFIG_SYSROOT_DIR"):
        env.pop(name, None)
    setup = [sys.executable, "-m", "mesonbuild.mesonmain", "setup", "--cross-file", str(cross),
             "--prefix", str(prefix), "--libdir", "lib", "--buildtype", "release", "-Ddefault_library=static"]
    for source, folder, options in (
        (epoxy, "epoxy-build", ["-Degl=yes", "-Dglx=no", "-Dx11=false", "-Dtests=false"]),
        (virgl, "virgl-build", ["-Dplatforms=egl", "-Dvenus=false", "-Dvideo=false", "-Dtests=false"])):
        output = work / folder
        subprocess.run(setup + options + [str(output), str(source)], env=env, check=True)
        subprocess.run([str(ninja), "-C", str(output), "install", "-j4"], env=env, check=True)
    binary = prefix / "bin/virgl_test_server"
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(binary, target)
    subprocess.run([str(toolchain / "llvm-strip"), str(target)], check=True)
    target.chmod(0o755)
    spec = importlib.util.spec_from_file_location("artifacts", PROJECT / "tools/verify/verify-artifacts.py")
    checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(checker)
    checker.check_elf(target.read_bytes(), target.name)
    licenses = PROJECT / "licenses"
    shutil.copyfile(virgl / "COPYING", licenses / "virglrenderer-MIT.txt")
    shutil.copyfile(epoxy / "COPYING", licenses / "libepoxy-MIT.txt")
    # Texture decompression code carries its own MIT notices in the retained patch.
    manifest.write_text(json.dumps({"virglrenderer": "1.3.0", "libepoxy": "1.5.10", "termux_patches_commit": TERMUX,
        "ndk": "27.2.12479018", "recipe_sha256": recipe, "local_patches": local_patches,
        "binaries": {target.name: digest(target)},
        "sources": {name: {"url": url, "sha256": sha} for name, (url, sha) in SOURCES.items()}}, indent=2) + "\n")
    print("Built pinned Android VirGL server with static renderer/epoxy and native EGL/GLES")


if __name__ == "__main__":
    main()
