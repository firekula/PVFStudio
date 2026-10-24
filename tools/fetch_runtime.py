#!/usr/bin/env python3
"""Fetch the pinned vanilla JBR, independently verified against its official SHA-512."""
from pathlib import Path
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
NAME = "jbr-21.0.9-windows-x64-b1038.78"
SHA512 = "2d7a3423d7dff070790493d0dd2ddf3da6b7f554acfbacd736f3316aee0c9b767337ce7dd66fa7a8c6d8f5116af4b98dfffd70347f66ac4adb5345fdd2099b57"
URL = f"https://cache-redirector.jetbrains.com/intellij-jbr/{NAME}.zip"
ARCHIVE = ROOT / "build/sdk/jbr-vanilla-download" / f"{NAME}.zip"
DESTINATION = ROOT / "build/sdk/jbr-21.0.9-b1038.78"


def main():
    ARCHIVE.parent.mkdir(parents=True, exist_ok=True)
    if not ARCHIVE.exists():
        temporary = ARCHIVE.with_suffix(".zip.part")
        with urllib.request.urlopen(URL, timeout=60) as response, temporary.open("xb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        temporary.rename(ARCHIVE)
    with ARCHIVE.open("rb") as stream:
        if hashlib.file_digest(stream, "sha512").hexdigest() != SHA512:
            raise RuntimeError("Vanilla JBR SHA-512 does not match the pinned official checksum")
    if not DESTINATION.exists():
        DESTINATION.mkdir(parents=True)
        with zipfile.ZipFile(ARCHIVE) as archive:
            for entry in archive.infolist():
                name = Path(entry.filename).relative_to(NAME)
                target = DESTINATION / name
                if not target.resolve().is_relative_to(DESTINATION.resolve()):
                    raise RuntimeError("Runtime archive path escapes the destination")
                if entry.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with archive.open(entry) as source, target.open("xb") as output:
                        while chunk := source.read(1024 * 1024):
                            output.write(chunk)
    if not (DESTINATION / "bin/javaw.exe").is_file() or "jcef" in (DESTINATION / "release").read_text():
        raise RuntimeError("Pinned vanilla JBR is incomplete or unexpectedly contains JCEF")
    print(f"Verified vanilla JBR: {DESTINATION}")


if __name__ == "__main__":
    main()
