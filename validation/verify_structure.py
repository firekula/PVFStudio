#!/usr/bin/env python3
"""Independent read-only verification of directory edits; plan has rename, remove and add payload files."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from verify_pvf import Archive, require, normalize


def verify_structure(source, output, renamed, removed, added):
    require(source.format == output.format and source.mask == output.mask, "container changed")
    before = {e.path: e for e in source.entries}
    after = {e.path: e for e in output.entries}
    require(set(renamed) <= before.keys() and set(removed) <= before.keys(), "unknown source path")
    expected = (before.keys() - set(renamed) - set(removed)) | set(renamed.values()) | added.keys()
    require(expected == after.keys(), "unexpected final directory paths")
    source_header = bytearray(source.data[:source.header_size])
    final_header = bytearray(output.data[:output.header_size])
    if source.format == "LEGACY":
        final_header[8:20] = source_header[8:20]
    else:
        final_header[-12:] = source_header[-12:]
    require(source_header == final_header, "container identity changed")
    survivors = {p: renamed.get(p, p) for p in before if p not in removed}
    relocated = {0: 0}
    for path, target in survivors.items():
        relocated[source.body_offset + before[path].offset] = output.body_offset + after[target].offset
    for path, original in before.items():
        padded = source.padded_plaintext(original)
        if path in removed:
            continue
        target = survivors[path]
        actual = after[target]
        require(original.length == actual.length, f"unexpected content length: {path}")
        if source.format != "LEGACY":
            require(original.number == actual.number, f"file identity changed: {path}")
        if path == target:
            require(original.raw_path == actual.raw_path, f"raw path changed: {path}")
        if original.key == actual.key and source.ciphertext(original) == output.ciphertext(actual):
            require(source.format in {"UUID", "LEGACY"} or original.offset == actual.offset, "invalid unchanged CRC seed")
        else:
            require(padded == output.padded_plaintext(actual), f"content or padding changed: {path}")
        if source.format == "LEGACY":
            start = source.body_offset + original.offset
            final = output.body_offset + actual.offset
            old_link = struct.unpack_from("<I", source.data, start + 20)[0]
            expected_link = relocated.get(old_link, final)
            require(struct.unpack_from("<I", output.data, final + 20)[0] == expected_link, "legacy link changed")
    for path, payload in added.items():
        require(output.plaintext(after[path]) == payload, f"new payload differs: {path}")
    old_cursor, new_cursor = source.body_offset, output.body_offset
    for original in sorted(source.entries, key=lambda e: (e.offset, e.padded_length)):
        start = source.body_offset + original.offset
        gap = source.data[old_cursor:start]
        require(gap == output.data[new_cursor:new_cursor + len(gap)], "opaque gap changed")
        new_cursor += len(gap)
        if original.path not in removed:
            actual = after[survivors[original.path]]
            require(new_cursor == output.body_offset + actual.offset, "physical order changed")
            new_cursor += actual.stored_length
        old_cursor = start + original.stored_length
    for actual in sorted((after[p] for p in added), key=lambda e: (e.offset, e.padded_length)):
        require(output.body_offset + actual.offset == new_cursor, "new entry layout changed")
        new_cursor += actual.stored_length
    require(source.data[old_cursor:] == output.data[new_cursor:], "opaque trailer changed")
    def used(archive):
        return len(archive.entries) * 1028 if archive.format == "LEGACY" else max((e.tree_offset + 20 + len(e.raw_path) for e in archive.entries), default=0)
    opaque = source.tree[used(source):]
    final_opaque = output.tree[used(output):]
    require(final_opaque.startswith(opaque) and not any(final_opaque[len(opaque):]), "reserved directory bytes changed")
    return {"status": "PASS", "format": source.format, "source_entries": len(before), "output_entries": len(after),
            "renamed": len(renamed), "removed": len(removed), "added": len(added), "all_payloads_and_crcs": "PASS",
            "opaque_gaps_and_trailer": "PASS", "directory_reserved_bytes": "PASS",
            "source_sha256": hashlib.sha256(source.data).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--plan", required=True, type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    plan = json.loads(args.plan.read_text(encoding="utf-8-sig"))
    added = {normalize(p): (args.plan.parent / f).read_bytes() for p, f in plan.get("add", {}).items()}
    report = verify_structure(Archive.read(args.input), Archive.read(args.output),
                              {normalize(p): normalize(t) for p, t in plan.get("rename", {}).items()},
                              {normalize(p) for p in plan.get("remove", [])}, added)
    result = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        with args.report.open("x", encoding="utf-8") as f:
            f.write(result)
    print(result)


if __name__ == "__main__":
    main()
