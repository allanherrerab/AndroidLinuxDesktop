#!/usr/bin/env python3
"""Assemble a verified ARM64 Debian rootfs from the official Debian OCI image."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path, PurePosixPath

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent.parent
IMAGE = "library/debian"
TAG = "trixie-slim"
REGISTRY = "https://registry-1.docker.io/v2"
AUTH = "https://auth.docker.io/token"
ACCEPT = ", ".join((
    "application/vnd.oci.image.index.v1+json",
    "application/vnd.docker.distribution.manifest.list.v2+json",
    "application/vnd.oci.image.manifest.v1+json",
    "application/vnd.docker.distribution.manifest.v2+json",
))


def request(url: str, headers: dict[str, str] | None = None) -> tuple[bytes, dict[str, str]]:
    req = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(req, timeout=90) as response:
        return response.read(), {k.lower(): v for k, v in response.headers.items()}


def auth_token() -> str:
    query = urllib.parse.urlencode({
        "service": "registry.docker.io",
        "scope": f"repository:{IMAGE}:pull",
    })
    body, _ = request(f"{AUTH}?{query}")
    return json.loads(body)["token"]


def fetch_manifest(reference: str, token: str) -> tuple[dict, str]:
    url = f"{REGISTRY}/{IMAGE}/manifests/{reference}"
    body, headers = request(url, {
        "Authorization": f"Bearer {token}",
        "Accept": ACCEPT,
    })
    digest = "sha256:" + hashlib.sha256(body).hexdigest()
    if headers.get("docker-content-digest", digest) != digest:
        raise RuntimeError("Registry returned a corrupt manifest")
    if reference.startswith("sha256:") and reference != digest:
        raise RuntimeError("Manifest does not match the pinned digest")
    return json.loads(body), digest


def select_arm64_manifest(manifest: dict) -> tuple[dict, str]:
    if "layers" in manifest:
        return manifest, ""
    for descriptor in manifest.get("manifests", []):
        platform = descriptor.get("platform", {})
        if (platform.get("os") == "linux" and
                platform.get("architecture") == "arm64" and
                platform.get("variant", "v8") == "v8"):
            return descriptor, descriptor["digest"]
    raise RuntimeError("The official Debian image has no Linux ARM64 manifest")


def safe_member(member: tarfile.TarInfo) -> bool:
    name = PurePosixPath(member.name)
    if name.is_absolute() or any(part == ".." for part in name.parts):
        return False
    if member.islnk():
        link = PurePosixPath(member.linkname)
        if link.is_absolute() or ".." in link.parts:
            return False
    return True


def remove_path(path: Path) -> None:
    if path.is_symlink() or path.is_file():
        path.unlink(missing_ok=True)
    elif path.is_dir():
        shutil.rmtree(path)


def apply_layer(blob: bytes, root: Path) -> None:
    with tempfile.NamedTemporaryFile() as layer_file:
        layer_file.write(blob)
        layer_file.flush()
        with tarfile.open(layer_file.name, mode="r:*") as layer:
            members = layer.getmembers()
            for member in members:
                if not safe_member(member):
                    raise RuntimeError(f"Unsafe path in Debian image layer: {member.name}")
                rel = PurePosixPath(member.name)
                basename = rel.name
                target = root.joinpath(*rel.parts)
                if basename == ".wh..wh..opq":
                    if target.parent.is_dir():
                        for child in target.parent.iterdir():
                            remove_path(child)
                    continue
                if basename.startswith(".wh."):
                    victim = target.with_name(basename[4:])
                    remove_path(victim)
            extractable = []
            for member in members:
                if PurePosixPath(member.name).name.startswith(".wh."):
                    continue
                extractable.append(member)
            # This is the official Debian image layer. Member paths are checked
            # above; retain absolute guest symlink targets used by Debian's
            # alternatives/merged-usr layout rather than rewriting them.
            layer.extractall(root, members=extractable, filter="fully_trusted")


def write_file(path: Path, text: str, mode: int = 0o644) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    path.chmod(mode)


def install_desktop(root: Path, proot: str, qemu: str | None) -> None:
    """Run Debian's signed package installer without root on the build host."""
    write_file(root / "usr/sbin/policy-rc.d", "#!/bin/sh\nexit 101\n", 0o755)
    # Preconfiguration spawns hundreds of emulated processes; debconf can configure
    # packages noninteractively during dpkg installation instead.
    write_file(root / "etc/apt/apt.conf.d/99noroot-build", '#clear DPkg::Pre-Install-Pkgs;\n')
    installer = PROJECT / "assets/rootfs/install/install-desktop.sh"
    shutil.copyfile(installer, root / "root/install-desktop.sh")
    command = [proot, "-0", "-r", str(root), "-w", "/",
               "-b", "/dev", "-b", "/proc", "-b", "/sys",
               "-b", "/etc/resolv.conf:/etc/resolv.conf"]
    if qemu:
        command += ["-q", qemu]
    command += ["/usr/bin/env", "-i", "HOME=/root", "TERM=dumb",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "/bin/sh", "/root/install-desktop.sh"]
    subprocess.run(command, check=True)
    (root / "root/install-desktop.sh").unlink()
    (root / "usr/sbin/policy-rc.d").unlink()
    (root / "etc/apt/apt.conf.d/99noroot-build").unlink()
    # PRoot/QEMU creates this empty host binding placeholder with mode 000.
    # It is a build-host detail, not part of the Android guest filesystem.
    placeholder = root / "host-rootfs"
    if placeholder.is_dir():
        placeholder.rmdir()


def install_overlay(root: Path) -> None:
    write_file(root / "etc/hostname", "debian-noroot\n")
    write_file(root / "etc/hosts", "127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n")
    write_file(root / "etc/issue", "Debian GNU/Linux \\n \\l\n")
    write_file(root / "etc/apt/sources.list.d/debian.sources", """Types: deb
URIs: https://deb.debian.org/debian
Suites: trixie trixie-updates
Components: main
Signed-By: /usr/share/keyrings/debian-archive-keyring.gpg

Types: deb
URIs: https://security.debian.org/debian-security
Suites: trixie-security
Components: main
Signed-By: /usr/share/keyrings/debian-archive-keyring.gpg
""")
    assets = PROJECT / "assets/rootfs"
    bin_assets = assets / "bin"
    for name in ("debian-noroot-startx", "debian-noroot-setup-desktop", "debian-noroot-ensure-audio", "debian-noroot-setup-audio-panel", "debian-noroot-ensure-locale", "debian-noroot-ensure-graphics", "debian-noroot-probe-graphics"):
        destination = root / "usr/local/bin" / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(bin_assets / name, destination)
        destination.chmod(0o755)
    shutil.copytree(assets / "defaults", root / "usr/share/debian-noroot/desktop-defaults",
                    dirs_exist_ok=True)
    write_file(root / "usr/lib/firefox-esr/defaults/pref/debian-noroot.js",
               (bin_assets / "debian-noroot-firefox.js").read_text())
    shutil.copyfile(PROJECT / "assets/rootfs/install/install-desktop.sh",
                    root / "root/debian-noroot-install-desktop.sh")
    (root / "root/debian-noroot-install-desktop.sh").chmod(0o755)
    for name in ("dev", "proc", "sys", "run", "tmp", "sdcard", "storage", "mnt"):
        (root / name).mkdir(parents=True, exist_ok=True)
    (root / "tmp").chmod(0o1777)

    # The current PRoot --sysvipc extension replaces the obsolete guest preload.
    write_file(root / "etc/skel/.config/xfce4/xfconf/xfce-perchannel-xml/xfwm4.xml",
               '<?xml version="1.0" encoding="UTF-8"?>\n'
               '<channel name="xfwm4" version="1.0"><property name="general" type="empty">'
               '<property name="use_compositing" type="bool" value="false"/>'
               '</property></channel>\n')
    status = (root / "var/lib/dpkg/status").read_text()
    packages = []
    for paragraph in status.split("\n\n"):
        fields = dict(line.split(": ", 1) for line in paragraph.splitlines()
                      if ": " in line and not line.startswith(" "))
        if fields.get("Status") == "install ok installed":
            packages.append(f"{fields['Package']}\t{fields['Version']}\t{fields['Architecture']}")
    write_file(root / "usr/share/debian-noroot/packages.txt", "\n".join(sorted(packages)) + "\n")


def reuse_rootfs(root: Path) -> None:
    archive = PROJECT / "dist/debian-rootfs.tar.xz"
    metadata = json.loads((PROJECT / "dist/rootfs.manifest.json").read_text())
    locked = json.loads((PROJECT / "rootfs.lock.json").read_text())
    if metadata.get("base_manifest") != locked["manifest_digest"]:
        raise RuntimeError("Existing image has a different base; rebuild without --reuse-rootfs")
    with archive.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != metadata["sha256"]:
            raise RuntimeError("Existing rootfs checksum mismatch")

    def confined(member: tarfile.TarInfo, destination: str) -> tarfile.TarInfo:
        if not safe_member(member):
            raise RuntimeError(f"Unsafe path in existing rootfs: {member.name}")
        for name in (member.name, member.linkname if member.islnk() else member.name):
            parent = Path(destination)
            for component in PurePosixPath(name).parts[:-1]:
                parent /= component
                if parent.is_symlink():
                    raise RuntimeError(f"Symlink ancestor in existing rootfs: {name}")
        return member

    with tarfile.open(archive, "r:xz") as package:
        package.extractall(root, filter=confined)


def archive_rootfs(root: Path, output: Path, preset: int = 3) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)

    def stable_metadata(info: tarfile.TarInfo) -> tarfile.TarInfo:
        info.uid = 0
        info.gid = 0
        info.uname = "root"
        info.gname = "root"
        info.mtime = 0
        return info

    with tarfile.open(output, mode="w:xz", format=tarfile.GNU_FORMAT,
                      preset=preset) as archive:
        for entry in sorted(root.iterdir(), key=lambda item: item.name):
            archive.add(entry, arcname=entry.name, recursive=True, filter=stable_metadata)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", nargs="?", type=Path,
                        default=PROJECT / "dist/debian-rootfs.tar.xz")
    parser.add_argument("--cache", type=Path, default=PROJECT / "build/rootfs-cache")
    parser.add_argument("--desktop", action="store_true", help="Preinstall the offline XFCE desktop")
    parser.add_argument("--reuse-rootfs", action="store_true", help="Update the last checksum-verified image instead of reinstalling Debian packages")
    parser.add_argument("--overlay-only", action="store_true", help="With --reuse-rootfs, refresh app-owned desktop defaults without running apt")
    parser.add_argument("--compression-preset", type=int, choices=range(10), default=3)
    local_proot = PROJECT / "build/host-proot/bin/proot"
    parser.add_argument("--proot", default=str(local_proot) if local_proot.is_file() else shutil.which("proot"),
                        help="Build-host PRoot executable")
    parser.add_argument("--qemu", default=shutil.which("qemu-aarch64"), help="QEMU user emulator (x86 build hosts)")
    args = parser.parse_args()
    if args.overlay_only and not (args.desktop and args.reuse_rootfs):
        parser.error("--overlay-only requires --desktop --reuse-rootfs")
    if args.desktop and not args.proot:
        parser.error("--desktop needs a recent host PRoot; run tools/build/build-host-proot.sh or pass --proot")
    if args.desktop:
        version = subprocess.check_output([args.proot, "--version"], stderr=subprocess.STDOUT).decode()
        match = re.search(r"\b(\d+)\.(\d+)\.(\d+)(?:\.\d+)?\b", version)
        if not match or tuple(map(int, match.groups())) < (5, 1, 107):
            parser.error("Host PRoot is too old for current Debian dpkg. Run tools/build/build-host-proot.sh")
    if args.desktop and os.uname().machine not in ("aarch64", "arm64") and not args.qemu:
        parser.error("--desktop on this host needs qemu-aarch64; install qemu-user or pass --qemu")
    args.cache.mkdir(parents=True, exist_ok=True)
    token = auth_token()
    lock_path = PROJECT / "rootfs.lock.json"
    if lock_path.is_file():
        locked = json.loads(lock_path.read_text(encoding="utf-8"))
        if locked.get("architecture") != "arm64" or locked.get("os") != "linux":
            raise RuntimeError("rootfs.lock.json must pin a Linux ARM64 manifest")
        arm_digest = locked.get("manifest_digest", "")
        if not arm_digest.startswith("sha256:"):
            raise RuntimeError("rootfs.lock.json has no pinned ARM64 manifest digest")
        selected, actual_digest = fetch_manifest(arm_digest, token)
        if actual_digest != arm_digest:
            raise RuntimeError("Pinned Debian manifest digest does not match the registry")
        index_digest = locked.get("index_digest", arm_digest)
    else:
        index, index_digest = fetch_manifest(TAG, token)
        selected, arm_digest = select_arm64_manifest(index)
        if "layers" not in selected:
            selected, _ = fetch_manifest(arm_digest, token)
    lock = {
        "image": "debian:trixie-slim",
        "os": "linux",
        "architecture": "arm64",
        "index_digest": index_digest,
        "manifest_digest": arm_digest or index_digest,
        "layers": [layer["digest"] for layer in selected["layers"]],
    }
    lock_path.write_text(
        json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    root = Path(tempfile.mkdtemp(prefix="debian-rootfs-"))
    try:
        if args.reuse_rootfs:
            if not args.desktop:
                parser.error("--reuse-rootfs requires --desktop")
            reuse_rootfs(root)
        for layer in ([] if args.reuse_rootfs else selected["layers"]):
            digest = layer["digest"]
            cached = args.cache / (digest.replace(":", "-") + ".blob")
            if cached.is_file():
                blob = cached.read_bytes()
            else:
                body, _ = request(f"{REGISTRY}/{IMAGE}/blobs/{digest}", {
                    "Authorization": f"Bearer {token}",
                })
                blob = body
            actual = "sha256:" + hashlib.sha256(blob).hexdigest()
            if actual != digest:
                raise RuntimeError(f"Debian layer digest mismatch: {digest}")
            if not cached.is_file():
                cached.write_bytes(blob)
            apply_layer(blob, root)
        if args.desktop and not args.overlay_only:
            install_desktop(root, str(Path(args.proot).resolve()),
                            str(Path(args.qemu).resolve()) if args.qemu else None)
        install_overlay(root)
        output = args.output.resolve()
        pending = output.with_name(output.name + ".installing")
        archive_rootfs(root, pending, args.compression_preset)
        os.replace(pending, output)
        asset = PROJECT / "dist/debian-rootfs.tar.xz"
        asset.parent.mkdir(parents=True, exist_ok=True)
        if output != asset:
            pending_asset = asset.with_name(asset.name + ".installing")
            shutil.copyfile(output, pending_asset)
            os.replace(pending_asset, asset)
        print(f"Wrote {args.output.resolve()}")
        print(f"Pinned ARM64 image manifest: {lock['manifest_digest']}")
        metadata = {"size": asset.stat().st_size, "audio": args.desktop, "desktop": args.desktop, "sha256": hashlib.sha256(asset.read_bytes()).hexdigest(),
                    "base_manifest": lock["manifest_digest"], "compression_preset": args.compression_preset}
        (PROJECT / "dist/rootfs.manifest.json").write_text(json.dumps(metadata, indent=2) + "\n")
        print("Image includes offline XFCE/PulseAudio for embedded X11; VNC is reserved for host tests." if args.desktop else
              "Base image only. Use --desktop to build a launchable desktop image.")
    finally:
        shutil.rmtree(root, ignore_errors=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, urllib.error.URLError, KeyError, RuntimeError, tarfile.TarError,
            subprocess.CalledProcessError) as error:
        print(f"rootfs build failed: {error}", file=sys.stderr)
        raise SystemExit(1)
