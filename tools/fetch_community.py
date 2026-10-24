#!/usr/bin/env python3
"""Fetch the pinned, public Community SDK with bounded parallel HTTP ranges.

Optional alternative to Gradle's download on slow single-connection links.
The official SHA-256 is checked before the ZIP is published. Output stays in build/.
"""
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import os
from pathlib import Path
import re
import threading
import time
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
URL = "https://download.jetbrains.com/idea/ideaIC-2025.2.6.3.win.zip"
OUTPUT = ROOT / "build/sdk/ideaIC-2025.2.6.3.win.zip"
CHUNK = 8 * 1024 * 1024
WORKERS = 16


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(URL + ".sha256", timeout=30) as response:
        expected = response.read(512).decode("ascii").split()[0]
    if not re.fullmatch("[a-f0-9]{64}", expected):
        raise RuntimeError("Invalid official SHA-256 response")
    if OUTPUT.exists():
        with OUTPUT.open("rb") as stream:
            actual = hashlib.file_digest(stream, "sha256").hexdigest()
        if actual != expected:
            raise RuntimeError("Existing output has a different checksum; choose a clean build/sdk directory")
        print("SDK already verified: " + str(OUTPUT), flush=True)
        return
    with urllib.request.urlopen(urllib.request.Request(URL, headers={"Range": "bytes=0-0"}), timeout=30) as response:
        total = int(response.headers["Content-Range"].rsplit("/", 1)[1])
        location = response.url
    temporary = OUTPUT.with_suffix(".zip.part")
    # Keep any earlier interrupted run intact rather than silently overwriting it.
    with temporary.open("xb") as stream:
        stream.truncate(total)
    completed = 0
    started = time.monotonic()
    next_report = 0.0
    lock = threading.Lock()

    def fetch(offset):
        nonlocal completed, next_report
        end = min(offset + CHUNK, total) - 1
        request = urllib.request.Request(location, headers={"Range": f"bytes={offset}-{end}"})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=40) as response:
                    content_range = response.headers.get("Content-Range", "")
                    if response.status != 206 or content_range != f"bytes {offset}-{end}/{total}":
                        raise RuntimeError("Server did not honor the requested range")
                    data = response.read(end - offset + 2)
                if len(data) != end - offset + 1:
                    raise RuntimeError("Incomplete HTTP range")
                with temporary.open("r+b") as stream:
                    stream.seek(offset)
                    stream.write(data)
                with lock:
                    completed += len(data)
                    elapsed = time.monotonic() - started
                    if elapsed >= next_report or completed == total:
                        print(f"Community SDK {completed / total:.1%}, {completed / 1048576:.0f}/{total / 1048576:.0f} MiB, {completed / max(elapsed, 0.001) / 1048576:.2f} MiB/s", flush=True)
                        next_report = elapsed + 15
                return
            except Exception:
                if attempt == 3:
                    raise
                time.sleep(attempt + 1)

    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        for future in as_completed([pool.submit(fetch, offset) for offset in range(0, total, CHUNK)]):
            future.result()
    with temporary.open("rb") as stream:
        actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != expected:
        raise RuntimeError("Downloaded SDK SHA-256 does not match official checksum")
    if OUTPUT.exists():
        raise RuntimeError("Output appeared during download; partial file retained")
    # Hard-link publication is no-clobber; unlink only our own temporary name.
    os.link(temporary, OUTPUT)
    temporary.unlink()
    print("Official SDK SHA-256 verified: " + actual, flush=True)
    print("SDK=" + str(OUTPUT), flush=True)


if __name__ == "__main__":
    main()
