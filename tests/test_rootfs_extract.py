"""Exercise the actual JNI extractor on the host (CC and liblzma-dev required)."""
import ctypes
import errno
import io
import lzma
import os
from pathlib import Path
import shlex
import subprocess
import tarfile
import tempfile
import unittest

PROJECT = Path(__file__).resolve().parents[1]


class ExtractorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = tempfile.TemporaryDirectory()
        library = Path(cls.build.name) / "extract.so"
        subprocess.run(shlex.split(os.environ.get("CC", "cc")) + [
            "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror", "-std=c11",
            str(PROJECT / "app/src/main/cpp/rootfs_extract.c"), "-llzma", "-o", str(library)
        ], check=True)
        cls.native = ctypes.CDLL(str(library))
        # Reproduce Android's SELinux hard-link denial on the host.
        denial = Path(cls.build.name) / "deny_link.c"
        denial.write_text("#include <errno.h>\nint test_link_errno = EPERM;\n"
                          "int __wrap_link(const char *source, const char *destination) {\n"
                          "    (void)source; (void)destination; errno = test_link_errno; return -1;\n}\n")
        denied_library = Path(cls.build.name) / "extract-denied.so"
        subprocess.run(shlex.split(os.environ.get("CC", "cc")) + [
            "-shared", "-fPIC", "-Wall", "-Wextra", "-Werror", "-std=c11",
            str(PROJECT / "app/src/main/cpp/rootfs_extract.c"), str(denial),
            "-Wl,--wrap=link", "-llzma", "-o", str(denied_library)
        ], check=True)
        cls.denied_native = ctypes.CDLL(str(denied_library))
        for native in (cls.native, cls.denied_native):
            native.rootfs_extract_xz.argtypes = [ctypes.c_char_p, ctypes.c_char_p,
                                                ctypes.c_char_p, ctypes.c_size_t]

    @classmethod
    def tearDownClass(cls):
        cls.build.cleanup()

    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory()
        self.root = Path(self.scratch.name)
        self.addCleanup(self.scratch.cleanup)

    def archive(self, entries):
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode="w", format=tarfile.GNU_FORMAT) as tar:
            for name, kind, value in entries:
                member = tarfile.TarInfo(name)
                member.mode = 0o755
                if kind == "file":
                    data = value.encode()
                    member.size = len(data)
                    tar.addfile(member, io.BytesIO(data))
                else:
                    member.type = {"dir": tarfile.DIRTYPE, "link": tarfile.SYMTYPE,
                                   "hard": tarfile.LNKTYPE}[kind]
                    member.linkname = value
                    tar.addfile(member)
        return lzma.compress(stream.getvalue())

    def extract(self, blob, native=None):
        archive = self.root / "image.tar.xz"
        archive.write_bytes(blob)
        error = ctypes.create_string_buffer(256)
        code = (native or self.native).rootfs_extract_xz(os.fsencode(archive), os.fsencode(self.root / "fs"),
                                           error, len(error))
        return code, error.value.decode()

    def test_roundtrip_merged_usr_links_long_names_and_existing_directories(self):
        name = "usr/share/" + "a" * 120 + "/example"
        blob = self.archive([("bin", "link", "usr/bin"), ("usr/bin/sh", "file", "shell"),
                             ("usr/bin/bash", "hard", "usr/bin/sh"),
                             ("etc/tool", "link", "/usr/bin/sh"),
                             (name, "file", "long path"), ("usr", "dir", "")])
        self.assertEqual(self.extract(blob), (0, ""))
        root = self.root / "fs"
        self.assertEqual((root / name).read_text(), "long path")
        self.assertEqual((root / "bin").readlink(), Path("usr/bin"))
        self.assertEqual((root / "etc/tool").readlink(), Path("/usr/bin/sh"))
        self.assertEqual((root / "usr/bin/sh").stat().st_ino, (root / "usr/bin/bash").stat().st_ino)

    def test_hardlink_denial_copies_content_and_source_mode(self):
        # Larger than the native I/O buffer; aliases may refer to another alias.
        content = "debian\n" * 20000
        blob = self.archive([("usr/bin/tool", "file", content),
                             ("usr/bin/alias", "hard", "usr/bin/tool"),
                             ("usr/bin/alias2", "hard", "usr/bin/alias")])
        for failure in (errno.EPERM, errno.EACCES, errno.EOPNOTSUPP, errno.EXDEV, errno.ENOSYS):
            with self.subTest(errno=failure):
                ctypes.c_int.in_dll(self.denied_native, "test_link_errno").value = failure
                self.assertEqual(self.extract(blob, self.denied_native), (0, ""))
                root = self.root / "fs/usr/bin"
                for name in ("alias", "alias2"):
                    self.assertEqual((root / name).read_text(), content)
                    self.assertEqual((root / name).stat().st_mode & 0o7777, 0o755)
                    self.assertNotEqual((root / name).stat().st_ino, (root / "tool").stat().st_ino)

    def test_hardlink_unrelated_failure_reports_path_and_errno(self):
        ctypes.c_int.in_dll(self.denied_native, "test_link_errno").value = errno.ENOSPC
        blob = self.archive([("tool", "file", "content"), ("alias", "hard", "tool")])
        code, message = self.extract(blob, self.denied_native)
        self.assertNotEqual(code, 0)
        self.assertIn("alias", message)
        self.assertIn(os.strerror(errno.ENOSPC), message)
        self.assertIn(f"errno {errno.ENOSPC}", message)
        self.assertFalse((self.root / "fs/alias").exists())

    def test_hardlink_source_must_be_regular_inside_rootfs(self):
        outside = self.root / "outside"
        outside.write_text("private")
        for native in (self.native, self.denied_native):
            for target in (str(outside), "../outside", "escape", "directory", "missing"):
                with self.subTest(native=native, target=target):
                    blob = self.archive([("escape", "link", str(outside)),
                                         ("directory", "dir", ""), ("alias", "hard", target)])
                    self.assertNotEqual(self.extract(blob, native)[0], 0)
                    self.assertFalse((self.root / "fs/alias").exists())
                    self.assertEqual(outside.read_text(), "private")

    def test_symlink_parent_cannot_overwrite_host(self):
        outside = self.root / "outside"
        outside.mkdir()
        sentinel = outside / "keep"
        sentinel.write_text("original")
        for kind in ("file", "hard"):
            blob = self.archive([("escape", "link", str(outside)),
                                 ("escape/keep", kind, "replacement")])
            self.assertNotEqual(self.extract(blob)[0], 0)
            self.assertEqual(sentinel.read_text(), "original")

    def test_rejects_traversal(self):
        for name in ("../outside", "/absolute", "a/../../outside"):
            self.assertNotEqual(self.extract(self.archive([(name, "file", "bad")]))[0], 0)

    def test_keeps_guest_sudo_mode(self):
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode="w", format=tarfile.GNU_FORMAT) as tar:
            member = tarfile.TarInfo("usr/bin/sudo")
            member.mode = 0o4755
            tar.addfile(member, io.BytesIO())
            alias = tarfile.TarInfo("usr/bin/sudo-alias")
            alias.type = tarfile.LNKTYPE
            alias.linkname = member.name
            alias.mode = 0o644  # The target's permissions apply to a hard-link copy.
            tar.addfile(alias)
        ctypes.c_int.in_dll(self.denied_native, "test_link_errno").value = errno.EPERM
        for native in (self.native, self.denied_native):
            self.assertEqual(self.extract(lzma.compress(stream.getvalue()), native), (0, ""))
            for name in ("sudo", "sudo-alias"):
                self.assertEqual((self.root / "fs/usr/bin" / name).stat().st_mode & 0o7777, 0o4755)

    def test_hardlink_source_must_not_cross_symlink_parent(self):
        outside = self.root / "outside"
        outside.mkdir()
        (outside / "keep").write_text("original")
        blob = self.archive([("escape", "link", str(outside)), ("copy", "hard", "escape/keep")])
        self.assertNotEqual(self.extract(blob)[0], 0)
        self.assertFalse((self.root / "fs/copy").exists())

    def test_verifies_xz_trailer_after_tar_end(self):
        blob = self.archive([("hello", "file", "world")])
        self.assertNotEqual(self.extract(blob[:-10])[0], 0)
        corrupt = bytearray(blob)
        corrupt[-12] ^= 1
        self.assertNotEqual(self.extract(bytes(corrupt))[0], 0)

    def test_rejects_missing_tar_end(self):
        self.assertNotEqual(self.extract(lzma.compress(b""))[0], 0)
        blob = self.archive([("hello", "file", "world")])
        self.assertNotEqual(self.extract(lzma.compress(lzma.decompress(blob)[:1024]))[0], 0)


if __name__ == "__main__":
    unittest.main()
