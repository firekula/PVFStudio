#!/usr/bin/env python3
"""Real samples: independent CRC/content audit, exact reconstruction and text edits.

Usage: python validation/run_compatibility.py <sample.pvf> [<sample.pvf> ...]
All artifacts go to a fresh build/compatibility directory; input files stay read-only.
"""
from pathlib import Path
import datetime
import json
import re
import struct
import subprocess
import sys
import uuid

from verify_pvf import Archive, verify, require

ROOT = Path(__file__).resolve().parents[1]
CLI = ROOT / "pvf-cli/build/install/pvf-studio/bin/pvf-studio.bat"


def run(*args: object) -> str:
    result = subprocess.run([str(CLI), *(str(a) for a in args)], capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
    return result.stdout.decode("utf-8")


def quote(value: str) -> str:
    for a, b in [("\\", "\\\\"), ("`", "\\`"), ("\r", "\\r"), ("\n", "\\n"), ("\t", "\\t"), ("\0", "\\0")]:
        value = value.replace(a, b)
    return "`" + value + "`"


def check(path: Path, directory: Path) -> dict:
    directory.mkdir()
    original = Archive.read(path)
    print(f"{original.format}: parsed {len(original.entries)} entries", flush=True)
    exact = directory / "roundtrip.pvf"
    run("export", path, exact)
    roundtrip = verify(original, Archive.read(exact), {})
    print("  exact reconstruction and all entry checks passed", flush=True)
    expected = {}
    marker = "PVF Studio compatibility 20261026"
    if original.format == "UUID":
        target = next(e for e in original.entries if e.path.endswith(".equ") and original.plaintext(e).startswith(b"\xb0\xd0"))
        table_entry = next(e for e in original.entries if e.path == "stringtable.bin")
        table = original.plaintext(table_entry)
        count = struct.unpack_from("<I", table)[0]
        offsets = struct.unpack_from(f"<{count + 1}I", table, 4)
        script = bytearray(original.plaintext(target))
        tokens = [(i, script[i], struct.unpack_from("<i", script, i + 1)[0]) for i in range(2, len(script), 5)]
        string_at, _, string_index = next(t for t in tokens if t[1] == 7)
        int_at, _, value = next(t for t in tokens if t[1] == 2)
        text = run("cat", path, target.path, "Big5")
        old_string = table[4 + offsets[string_index]:4 + offsets[string_index + 1]].decode("big5")
        text = text.replace(quote(old_string), quote(marker), 1)
        text = re.sub(rf"(?m)^{value}\t", f"{value + 1}\t", text, count=1)
        struct.pack_into("<i", script, string_at + 1, count)
        struct.pack_into("<i", script, int_at + 1, value + 1)
        expected[target.path] = bytes(script)
        addition = marker.encode("big5")
        index_end = 4 + (count + 1) * 4
        strings_end = 4 + offsets[-1]
        expected[table_entry.path] = (struct.pack("<I", count + 1)
            + struct.pack(f"<{count + 1}I", *(o + 4 for o in offsets))
            + struct.pack("<I", offsets[-1] + 4 + len(addition))
            + table[index_end:strings_end] + addition + table[strings_end:])
        charset = "Big5"
    else:
        charset = "MS949" if original.format == "LEGACY" else "GBK"
        target = next(e for e in original.entries if e.path == ("character/character.lst" if original.format == "LEGACY" else "creature/script/creature.lst"))
        text = run("cat", path, target.path, charset) + "\r\n// " + marker + "\r\n"
        expected[target.path] = text.encode("cp949" if charset == "MS949" else "gbk")
    utf8 = directory / "edit.txt"
    utf8.write_bytes(text.encode("utf-8"))
    drafts = directory / "drafts"
    run("stage-text", path, drafts, target.path, utf8, charset)
    edited = directory / "edited.pvf"
    run("workspace-export", path, drafts, edited)
    output = Archive.read(edited)
    result = verify(original, output, expected)
    require(marker in run("cat", edited, target.path, charset), "edited text was not restored")
    require(path.read_bytes() == original.data, "source archive changed")
    print(f"  text edit, export, independent expected bytes and source preservation passed: {target.path}", flush=True)
    report = {"roundtrip": roundtrip, "edit": result, "entry": target.path, "charset": charset}
    (directory / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    output = ROOT / "build/compatibility" / (datetime.datetime.now().strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8])
    output.mkdir(parents=True)
    reports = [check(Path(p).resolve(), output / f"sample-{i}") for i, p in enumerate(sys.argv[1:])]
    (output / "summary.json").write_text(json.dumps(reports, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"PASS: {output}", flush=True)


if __name__ == "__main__":
    main()
