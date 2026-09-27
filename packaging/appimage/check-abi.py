#!/usr/bin/env python3
"""Reject AppImage ELF payloads that cannot run on Ubuntu 22.04's glibc."""
from pathlib import Path
import re
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
checked = 0
for path in root.rglob("*"):
    if not path.is_file() or path.is_symlink():
        continue
    if re.fullmatch(r"(?:libc|libpthread|libdl|librt|libm|libresolv)\.so(?:\..*)?", path.name) or path.name.startswith("ld-linux"):
        raise RuntimeError(f"AppImage must use the host C library and loader: {path}")
    with path.open("rb") as binary:
        if binary.read(4) != b"\x7fELF":
            continue
    symbols = subprocess.check_output(["readelf", "--dyn-syms", "--wide", str(path)], text=True)
    versions = re.findall(r"@GLIBC_(\d+)\.(\d+)", symbols)
    if any(tuple(map(int, version)) > (2, 35) for version in versions):
        raise RuntimeError(f"Native payload exceeds the glibc 2.35 floor: {path}")
    checked += 1
if checked == 0:
    raise RuntimeError(f"No ELF payloads found in {root}")
print(f"Validated glibc 2.35 compatibility for {checked} ELF payloads")
