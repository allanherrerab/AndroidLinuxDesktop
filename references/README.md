# Reference material

`debian-noroot-2020.apk` is the supplied behavioral reference and the source
of the restored launcher icon. Its SHA-256 is
`c923ca810cabf16451194dff44f4f7eced69a2926cdaf91cf7be903a6983c833`.
The upstream reference is <https://github.com/pelya/debian-noroot>.

On 2026-10-25 the user accepted the differences in [../PARITY.md](../PARITY.md)
and authorized removing the obsolete legacy-source/, xfce-panel-source/ and
development-notes/ directories and their .gitmodules entries. Those directories
were removed. [../Process.md](../Process.md) records the acceptance and cleanup;
[../backlog.md](../backlog.md) lists outstanding runtime requirements.

Current pinned PRoot/talloc and X11 sources, object/relinking material, licenses
and manifests were retained separately from the obsolete references. Preserve
them according to [../THIRD_PARTY.md](../THIRD_PARTY.md).
