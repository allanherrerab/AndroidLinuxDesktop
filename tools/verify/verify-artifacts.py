#!/usr/bin/env python3
"""Fail builds that package a base image or a stale/4 KB Android runtime."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import tarfile
import zipfile
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parents[2]


def check_elf(data: bytes, name: str) -> None:
    if data[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", data, 18)[0] != 183:
        raise ValueError(f"{name}: expected a little-endian ARM64 ELF")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    loads = 0
    for index in range(count):
        kind, _, file_offset, address, _, _, _, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, offset + index * size)
        if kind == 1:
            loads += 1
            if alignment < 16384 or file_offset % 16384 != address % 16384:
                raise ValueError(f"{name}: PT_LOAD is not compatible with 16 KB pages")
    if not loads:
        raise ValueError(f"{name}: no loadable segments")


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def dex_classes(data: bytes) -> set[str]:
    """Read actual class definitions, not incidental names in DEX string pools."""
    if data[:4] != b"dex\n" or len(data) < 112:
        raise ValueError("Invalid APK DEX header")
    strings, strings_offset, types, types_offset = struct.unpack_from("<IIII", data, 56)
    count, offset = struct.unpack_from("<II", data, 96)
    classes = set()
    for index in range(count):
        type_index = struct.unpack_from("<I", data, offset + index * 32)[0]
        if type_index >= types:
            raise ValueError("Invalid DEX class type")
        string_index = struct.unpack_from("<I", data, types_offset + type_index * 4)[0]
        if string_index >= strings:
            raise ValueError("Invalid DEX class name")
        position = struct.unpack_from("<I", data, strings_offset + string_index * 4)[0]
        # Skip the ULEB128 UTF-16 length; class descriptors themselves are ASCII.
        for _ in range(5):
            if position >= len(data):
                raise ValueError("Truncated DEX class name")
            value = data[position]
            position += 1
            if value < 128:
                break
        else:
            raise ValueError("Invalid DEX string length")
        end = data.find(b"\0", position)
        if end < 0:
            raise ValueError("Unterminated DEX class name")
        classes.add(data[position:end].decode("utf-8"))
    return classes


def verify_x11_apk(apk: zipfile.ZipFile) -> None:
    classes = set()
    for name in apk.namelist():
        if name.startswith("classes") and name.endswith(".dex"):
            classes.update(dex_classes(apk.read(name)))
    required = {"Lcom/termux/x11/" + name + ";" for name in
                ("LorieView", "CmdEntryPoint", "NativeLibrary")}
    required |= {"Lorg/debiannoroot/ui/DesktopActivity;", "Lorg/debiannoroot/session/X11Service;"}
    if missing := required - classes:
        raise ValueError("APK is missing native X11 integration: " + ", ".join(sorted(missing)))
    if any("RfbConnection" in name or "VncView" in name for name in classes):
        raise ValueError("APK includes a host-only VNC client")
    apk.getinfo("lib/arm64-v8a/libXlorie.so")
    startup = apk.read("assets/rootfs/bin/debian-noroot-startx")
    if b"Xtigervnc" in startup or b"debian-noroot-vnc.sock" in startup:
        raise ValueError("APK startup still includes the host-only VNC server")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--obb", type=Path)
    parser.add_argument("--full-image", action="store_true", help="Also read and validate the entire tar.xz")
    args = parser.parse_args()
    runtime = json.loads((PROJECT / "dist/runtime.manifest.json").read_text())
    for name, expected in runtime["binaries"].items():
        path = PROJECT / "dist/runtime/arm64-v8a" / name
        if digest(path) != expected:
            raise ValueError(f"{name}: runtime manifest mismatch; run build-runtime.py")
        check_elf(path.read_bytes(), name)
    x11=json.loads((PROJECT/"dist/x11.manifest.json").read_text())
    graphics=json.loads((PROJECT/"dist/graphics.manifest.json").read_text())
    for name, expected in graphics["binaries"].items():
        binary=PROJECT/"dist/runtime/arm64-v8a"/name
        if digest(binary)!=expected:raise ValueError("VirGL renderer checksum mismatch")
        check_elf(binary.read_bytes(),name)
    runtime["binaries"].update(graphics["binaries"])
    runtime["binaries"].update(x11["binaries"])
    for name,expected in x11["binaries"].items():
        path=PROJECT/"dist/runtime/arm64-v8a"/name
        if digest(path)!=expected:raise ValueError("Pinned X11 engine checksum mismatch")
        check_elf(path.read_bytes(),name)
    image = PROJECT / "dist/debian-rootfs.tar.xz"
    metadata = json.loads((PROJECT / "dist/rootfs.manifest.json").read_text())
    if not metadata["desktop"] or not metadata.get("audio") or digest(image) != metadata["sha256"]:
        raise ValueError("External image is stale or lacks XFCE/PulseAudio; rebuild with --desktop --reuse-rootfs")
    if args.full_image:
        assets = PROJECT / "assets/rootfs"
        owned = [(assets / "bin" / name, "usr/local/bin/" + name) for name in
                 ("debian-noroot-startx", "debian-noroot-setup-desktop", "debian-noroot-ensure-audio", "debian-noroot-setup-audio-panel", "debian-noroot-ensure-locale", "debian-noroot-ensure-graphics", "debian-noroot-probe-graphics")]
        owned.append((assets / "bin/debian-noroot-firefox.js", "usr/lib/firefox-esr/defaults/pref/debian-noroot.js"))
        defaults = assets / "defaults"
        owned += [(path, "usr/share/debian-noroot/desktop-defaults/" + str(path.relative_to(defaults)))
                  for path in defaults.rglob("*") if path.is_file()]
        expected = {name: source.read_bytes() for source, name in owned}
        required = {"usr/bin/Xtigervnc", "usr/bin/xfce4-session", "usr/bin/xfce4-terminal",
                    "usr/bin/thunar", "usr/bin/firefox-esr", "usr/bin/sudo",
                    "usr/share/zoneinfo/America/El_Salvador", "usr/bin/pulseaudio", "usr/bin/paplay",
                    "usr/share/X11/xkb/rules/evdev", "usr/share/xfce4/panel/plugins/pulseaudio.desktop",
                    "usr/bin/pavucontrol",
                    "usr/bin/glxinfo", "usr/bin/glxgears", "usr/lib/aarch64-linux-gnu/libGLX_mesa.so.0",
                    "PulseAudio PCM transport module"} | expected.keys()
        panel = ET.parse(defaults / "xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml")
        for plugin in panel.findall("./property[@name='plugins']/property"):
            required.add("usr/share/xfce4/panel/plugins/" + plugin.attrib["value"] + ".desktop")
        found = set()
        # Stream once: random seeks in an xz archive would repeatedly decode
        # hundreds of megabytes merely to compare a handful of tiny templates.
        with tarfile.open(image, "r|xz") as archive:
            for member in archive:
                name = member.name
                if name.endswith("/module-simple-protocol-unix.so"):found.add("PulseAudio PCM transport module")
                if name in required:
                    found.add(name)
                if name == "usr/bin/sudo" and not member.mode & 0o4000:
                    raise ValueError("Guest sudo is missing its emulated setuid mode")
                if name in expected:
                    if not member.isfile() or archive.extractfile(member).read() != expected[name]:
                        raise ValueError(f"Stale desktop overlay: {name}; rebuild the image overlay")
        if missing := required - found:
            raise ValueError("Missing desktop image files: " + ", ".join(sorted(missing)))
    if args.apk:
        with zipfile.ZipFile(args.apk) as apk:
            verify_x11_apk(apk)
            for name in ("libdebian_native.so", "libdebian_core.so", "libproot.so", "libproot-loader.so", "libXlorie.so", "libvirgl_server.so"):
                binary = apk.read(f"lib/arm64-v8a/{name}")
                check_elf(binary, name)
                if name in runtime["binaries"] and hashlib.sha256(binary).hexdigest() != runtime["binaries"][name]:
                    raise ValueError(f"APK contains a stale {name}")
            if any(name.startswith("assets/") and name.endswith((".tar.xz",".obb")) for name in apk.namelist()):
                raise ValueError("Rootfs archive must not be embedded in the APK")
            if json.loads(apk.read("assets/rootfs/image.json"))!=metadata:
                raise ValueError("APK expects a different external rootfs image")
            assets = PROJECT / "assets/rootfs"
            for source in assets.rglob("*"):
                if source.is_file() and source != image:
                    name = "assets/rootfs/" + str(source.relative_to(assets))
                    if hashlib.sha256(apk.read(name)).hexdigest() != digest(source):
                        raise ValueError(f"APK contains a stale desktop asset: {name}")
            if apk.testzip() is not None:
                raise ValueError("APK ZIP checksum failure")
    if args.obb and (digest(args.obb)!=metadata["sha256"] or args.obb.stat().st_size!=metadata["size"]):
        raise ValueError("Expansion image checksum/size mismatch")
    print("Verified external desktop/audio image, pinned X11/PRoot, and 16 KB ELF alignment" +
          (f": {args.apk}" if args.apk else ""))


if __name__ == "__main__":
    try:
        main()
    except (OSError, KeyError, ValueError, struct.error, tarfile.TarError, zipfile.BadZipFile) as error:
        raise SystemExit(f"Artifact verification failed: {error}")
