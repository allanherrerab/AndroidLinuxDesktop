import importlib.util
import io
import os
from pathlib import Path
import struct
import subprocess
import unittest
import zipfile

PROJECT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("artifacts", PROJECT / "tools/verify/verify-artifacts.py")
artifacts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(artifacts)


def dex_fixture(names):
    """Minimal class/string tables for the verifier, with no executable code."""
    count = len(names)
    strings = 112
    types = strings + 4 * count
    classes = types + 4 * count
    data = bytearray(classes + 32 * count)
    data[:8] = b"dex\n035\0"
    struct.pack_into("<IIII", data, 56, count, strings, count, types)
    struct.pack_into("<II", data, 96, count, classes)
    for index, name in enumerate(names):
        struct.pack_into("<I", data, strings + 4 * index, len(data))
        struct.pack_into("<I", data, types + 4 * index, index)
        struct.pack_into("<I", data, classes + 32 * index, index)
        assert len(name) < 128
        data.extend(bytes([len(name)]) + name.encode() + b"\0")
    return bytes(data)


class DisplayBackendTest(unittest.TestCase):
    names = ["Lcom/termux/x11/" + name + ";" for name in
             ("LorieView", "CmdEntryPoint", "NativeLibrary")]
    names += ["Lorg/debiannoroot/ui/DesktopActivity;", "Lorg/debiannoroot/session/X11Service;"]

    def verify(self, names, startup=b"#!/bin/sh\n"):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as apk:
            # Cover multidex: the native adapters need not live in classes.dex.
            apk.writestr("classes.dex", dex_fixture(names[:2]))
            apk.writestr("classes2.dex", dex_fixture(names[2:]))
            apk.writestr("lib/arm64-v8a/libXlorie.so", b"fixture")
            apk.writestr("assets/rootfs/bin/debian-noroot-startx", startup)
        with zipfile.ZipFile(buffer) as apk:
            artifacts.verify_x11_apk(apk)

    def test_x11_classes_in_multidex(self):
        self.verify(self.names)

    def test_missing_renderer_rejected(self):
        with self.assertRaisesRegex(ValueError, "missing native X11"):
            self.verify(self.names[1:])

    def test_vnc_client_rejected(self):
        with self.assertRaisesRegex(ValueError, "VNC client"):
            self.verify(self.names + ["Lorg/debiannoroot/RfbConnection;"])

    def test_vnc_guest_launcher_rejected(self):
        with self.assertRaisesRegex(ValueError, "VNC server"):
            self.verify(self.names, b"Xtigervnc :1")

    def test_invalid_dex_rejected(self):
        with self.assertRaises(ValueError):
            artifacts.dex_classes(b"dex\n")

    def test_guest_rejects_other_backends_before_starting(self):
        for backend in ("vnc", "unknown"):
            result = subprocess.run(["sh", str(PROJECT / "assets/rootfs/bin/debian-noroot-startx")],
                                    env={**os.environ, "NOROOT_DISPLAY_BACKEND": backend},
                                    capture_output=True, text=True, timeout=5)
            self.assertEqual(result.returncode, 2)
            self.assertIn("requires embedded Termux:X11", result.stderr)
