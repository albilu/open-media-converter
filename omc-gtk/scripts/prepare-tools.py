#!/usr/bin/env python3
"""Prepare checksum-verified, architecture-specific tools for Maven resources."""
import argparse
import hashlib
import json
from pathlib import Path
import platform
import shutil
import tarfile
import urllib.request


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def prepare():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", default="false", choices=("true", "false"))
    parser.add_argument("--arch", default=platform.machine())
    args = parser.parse_args()
    architecture = {"amd64": "x86_64", "x86_64": "x86_64", "aarch64": "aarch64", "arm64": "aarch64"}.get(args.arch)
    if platform.system() != "Linux" or architecture is None:
        parser.error("Embedded tools support Linux x86_64 and aarch64. Use -Domc.skipEmbeddedTools=true for a system-tools build.")
    module = Path(__file__).resolve().parents[1]
    manifest_path = module / "scripts" / "tools.json"
    manifest = json.loads(manifest_path.read_text())
    cache = module / ".tool-cache"
    resources = cache / "resources"
    destination = resources / "bin" / ("linux-" + architecture)
    cache.mkdir(exist_ok=True)
    for tool, spec in manifest[architecture].items():
        archive = cache / (spec["sha256"] + ".archive")
        if not archive.exists() or sha256(archive) != spec["sha256"]:
            if args.offline == "true":
                parser.error("Missing verified " + tool + " archive. Run an online package build once to populate .tool-cache.")
            temporary = archive.with_suffix(".download")
            print("Downloading " + spec["url"], flush=True)
            request = urllib.request.Request(spec["url"], headers={"User-Agent": "Open-Media-Converter-build"})
            try:
                with urllib.request.urlopen(request, timeout=60) as source, temporary.open("wb") as output:
                    shutil.copyfileobj(source, output)
                if sha256(temporary) != spec["sha256"]:
                    raise ValueError("Checksum mismatch for " + tool)
                temporary.replace(archive)
            finally:
                temporary.unlink(missing_ok=True)
        target = destination / tool
        target.mkdir(parents=True, exist_ok=True)
        with tarfile.open(archive) as bundle:
            members = bundle.getmembers()
            for name in spec["binaries"]:
                matches = [m for m in members if m.isfile() and m.name.endswith("/bin/" + name)]
                if len(matches) != 1:
                    raise ValueError("Expected one executable named " + name)
                output = target / name
                with bundle.extractfile(matches[0]) as source, output.open("wb") as stream:
                    shutil.copyfileobj(source, stream)
                output.chmod(0o755)
                output.with_suffix(".sha256").write_text(sha256(output) + "\n")
            for member in members:
                name = Path(member.name).name
                if member.isfile() and name.lower().startswith(("license", "copying", "readme")):
                    # Copy individual files; never trust archive paths or symlinks.
                    with bundle.extractfile(member) as source, (target / name).open("wb") as output:
                        shutil.copyfileobj(source, output)
        (target / "SOURCE.json").write_text(json.dumps(spec, indent=2) + "\n")
        if tool == "pandoc":
            shutil.copyfile(module / "scripts" / "licenses" / "PANDOC-COPYING", target / "COPYING")
        print("Prepared " + tool + " for " + architecture, flush=True)
    shutil.copyfile(manifest_path, resources / "bin" / "tools.json")
    shutil.copyfile(module.parent / "BINARY_LICENSES.md", resources / "bin" / "BINARY_LICENSES.md")


if __name__ == "__main__":
    prepare()
