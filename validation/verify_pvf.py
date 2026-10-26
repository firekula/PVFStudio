#!/usr/bin/env python3
"""Independent, standard-library PVF round-trip verifier.

This script does not import PVF Studio code. Both archives are opened read-only.
Expected changes are a JSON object mapping PVF paths to replacement payload files;
relative payload paths are resolved relative to the JSON file, not the process cwd.
Use no --changes for a reconstruction that must be byte-for-byte identical.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
import struct
import sys
import zlib
from typing import Any


MAGIC = b"DNF_SCRIPT_PACK"
HEADER_SIZE = len(MAGIC) + 12
U32 = struct.Struct("<I")
RECORD = struct.Struct("<IIIII")


class VerificationError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise VerificationError(message)


def normalize(path: str) -> str:
    return path.replace("\\", "/").lstrip("/").lower()


def decrypt(ciphertext: bytes, key: int) -> bytes:
    require(len(ciphertext) % 4 == 0, "encrypted range is not DWORD aligned")
    plaintext = bytearray(len(ciphertext))
    for offset in range(0, len(ciphertext), 4):
        value = U32.unpack_from(ciphertext, offset)[0] ^ key
        # Explicit bit shifts provide an independent implementation of ROR32.
        U32.pack_into(plaintext, offset, ((value >> 6) | (value << 26)) & 0xFFFFFFFF)
    return bytes(plaintext)


@dataclass(frozen=True)
class Entry:
    path: str
    number: int
    offset: int
    length: int
    key: int
    raw_path: bytes
    tree_offset: int
    legacy: bool = False

    @property
    def padded_length(self) -> int:
        return self.length if self.legacy else (self.length + 3) & ~3

    @property
    def stored_length(self) -> int:
        return self.padded_length + (24 if self.legacy else 0)


@dataclass
class Archive:
    path: Path
    data: bytes
    tree: bytes
    tree_key: int
    body_offset: int
    entries: list[Entry]
    format: str = "ACT1"
    header_size: int = HEADER_SIZE
    mask: int = 0

    @classmethod
    def read(cls, path: Path) -> "Archive":
        data = path.read_bytes()
        require(len(data) >= HEADER_SIZE, f"{path}: truncated header")
        first = U32.unpack_from(data)[0]
        kind, header_size, mask = "ACT1", HEADER_SIZE, 0
        if data.startswith(MAGIC):
            tree_length, tree_key, count = struct.unpack_from("<III", data, len(MAGIC))
        elif first == 1 and U32.unpack_from(data, 4)[0] == 1028:
            kind, header_size = "LEGACY", 32
            tree_length, count, count2 = struct.unpack_from("<III", data, 8)
            require(count == count2 and tree_length % 1028 == 0 and count <= tree_length // 1028, "invalid legacy directory")
            tree_key = 0
        else:
            require(1 <= first <= 256 and first + 20 <= len(data), f"{path}: invalid UUID header")
            kind, header_size = "UUID", first + 20
            tree_length, tree_key, count = struct.unpack_from("<III", data, first + 8)
        require(tree_length > 0 and tree_length % 4 == 0, f"{path}: invalid tree length")
        body_offset = header_size + tree_length
        require(body_offset <= len(data), f"{path}: tree extends beyond archive")
        require(count <= tree_length // 21, f"{path}: impossible entry count")
        tree = data[header_size:body_offset]
        if kind != "LEGACY":
            tree = decrypt(tree, tree_key)
            if kind == "UUID" and zlib.crc32(tree, count) != tree_key:
                mask = 0x81a79011
                tree = decrypt(data[header_size:body_offset], tree_key ^ mask)
            require(zlib.crc32(tree, count) == tree_key, f"{path}: client directory CRC mismatch")
        entries = []
        paths = set()
        numbers = set()
        cursor = 0
        for index in range(count):
            require(cursor + RECORD.size <= len(tree), f"{path}: truncated entry {index}")
            record_start = cursor
            if kind == "LEGACY":
                record_start = index * 1028
                raw_path = tree[record_start:record_start + 1024]
                absolute = U32.unpack_from(tree, record_start + 1024)[0]
                require(body_offset <= absolute <= len(data) - 24, "legacy block outside archive")
                flags, active, own, length, stored, link = struct.unpack_from("<6I", data, absolute)
                require(flags == active == 1 and own == absolute and length == stored, "unsupported legacy block")
                number, offset, key, path_length = index, absolute - body_offset, 0, 1024
                cursor = record_start + 1028
            elif kind == "UUID":
                number, path_length = struct.unpack_from("<II", tree, cursor)
                cursor += 8
                require(0 < path_length <= len(tree) - cursor - 12, "invalid UUID path length")
                raw_path = tree[cursor:cursor + path_length]; cursor += path_length
                length, key, offset = struct.unpack_from("<III", tree, cursor); cursor += 12
            else:
                number, offset, length, key, path_length = RECORD.unpack_from(tree, cursor)
                cursor += RECORD.size
            require(path_length > 0 and (kind != "ACT1" or path_length <= len(tree) - cursor),
                    f"{path}: invalid path length at entry {index}")
            if kind == "ACT1":
                raw_path = tree[cursor:cursor + path_length]
                cursor += path_length
            try:
                virtual_path = normalize(raw_path.decode("cp949", errors="strict").rstrip("\0"))
            except UnicodeDecodeError as exception:
                raise VerificationError(f"{path}: entry {index} has invalid CP949 path") from exception
            require(virtual_path and "\0" not in virtual_path, f"{path}: invalid path at entry {index}")
            require(virtual_path not in paths, f"{path}: duplicate path {virtual_path}")
            require(kind != "ACT1" or number not in numbers, f"{path}: duplicate file number {number}")
            entry = Entry(virtual_path, number, offset, length, key, raw_path, record_start, kind == "LEGACY")
            require(offset + entry.stored_length <= len(data) - body_offset,
                    f"{path}: out-of-range content for {virtual_path}")
            entries.append(entry)
            paths.add(virtual_path)
            numbers.add(number)
        end = 0
        for entry in sorted(entries, key=lambda item: (item.offset, item.padded_length)):
            if entry.stored_length:
                require(entry.offset >= end, f"{path}: overlapping content for {entry.path}")
                end = entry.offset + entry.stored_length
        return cls(path, data, tree, tree_key, body_offset, entries, kind, header_size, mask)

    def ciphertext(self, entry: Entry) -> bytes:
        start = self.body_offset + entry.offset + (24 if entry.legacy else 0)
        return self.data[start:start + entry.padded_length]

    def padded_plaintext(self, entry: Entry) -> bytes:
        if entry.legacy:
            plaintext = bytearray(self.ciphertext(entry))
            for i in range(0, len(plaintext) - 1, 2):
                word = int.from_bytes(plaintext[i:i + 2], "big")
                plaintext[i:i + 2] = ((word - 0x0c74) & 0xffff if word else 0).to_bytes(2, "little")
            return bytes(plaintext)
        plaintext = decrypt(self.ciphertext(entry), entry.key ^ self.mask)
        require(zlib.crc32(plaintext, entry.number if self.format == "UUID" else entry.offset) == entry.key,
                f"{self.path}: client entry CRC mismatch: {entry.path}")
        return plaintext

    def plaintext(self, entry: Entry) -> bytes:
        return self.padded_plaintext(entry)[:entry.length]


def read_changes(manifest: Path | None) -> dict[str, bytes]:
    if manifest is None:
        return {}
    raw = json.loads(manifest.read_text(encoding="utf-8-sig"))
    require(isinstance(raw, dict), "changes manifest must be a JSON object")
    changes = {}
    for virtual_path, payload_path in raw.items():
        require(isinstance(virtual_path, str) and isinstance(payload_path, str),
                "changes manifest must map string PVF paths to string payload paths")
        path = normalize(virtual_path)
        require(path not in changes, f"duplicate normalized change path: {path}")
        replacement = Path(payload_path)
        if not replacement.is_absolute():
            replacement = manifest.parent / replacement
        changes[path] = replacement.read_bytes()
    return changes


def verify(source: Archive, output: Archive, expected: dict[str, bytes]) -> dict[str, Any]:
    require(source.format == output.format and source.mask == output.mask, "container format changed")
    source_header = bytearray(source.data[:source.header_size])
    output_header = bytearray(output.data[:output.header_size])
    if source.format != "LEGACY":
        output_header[-8:-4] = source_header[-8:-4]
    require(source_header == output_header, "container identity/header changed")
    require(len(source.entries) == len(output.entries), "entry count changed")
    require(len(source.tree) == len(output.tree), "directory length changed")
    source_paths = {entry.path for entry in source.entries}
    require(not (set(expected) - source_paths), f"unknown expected paths: {sorted(set(expected) - source_paths)}")
    output_paths = {entry.path: entry for entry in output.entries}
    relocated_links = {source.body_offset + e.offset: output.body_offset + output_paths[e.path].offset for e in source.entries}
    normalized_tree = bytearray(output.tree)
    changed_entries = 0
    preserved_ciphertext_entries = 0
    rekeyed_unchanged_entries = 0
    for before, after in zip(source.entries, output.entries):
        require(before.path == after.path, "directory entry order or path changed")
        require(before.raw_path == after.raw_path, f"raw path bytes changed: {before.path}")
        require(before.number == after.number, f"file number changed: {before.path}")
        require(before.tree_offset == after.tree_offset, f"entry directory layout changed: {before.path}")
        original_padded = source.padded_plaintext(before)
        exported_padded = output.padded_plaintext(after)
        original_content = original_padded[:before.length]
        expected_content = expected.get(before.path, original_content)
        require(exported_padded[:after.length] == expected_content, f"plaintext mismatch: {before.path}")
        if expected_content != original_content:
            changed_entries += 1
            if before.length == after.length:
                require(exported_padded[after.length:] == original_padded[before.length:],
                        f"same-length edit padding changed: {before.path}")
        else:
            require(original_padded == exported_padded, f"unmodified padding changed: {before.path}")
            if before.offset == after.offset:
                require(before.key == after.key and source.ciphertext(before) == output.ciphertext(after),
                        f"unmodified ciphertext changed without relocation: {before.path}")
                preserved_ciphertext_entries += 1
            else:
                rekeyed_unchanged_entries += 1
        # Relocation changes the CRC seed/key; opaque bytes, names and IDs stay exact.
        if source.format == "LEGACY":
            start, size = before.tree_offset + 1024, 4
            old_block = source.body_offset + before.offset
            new_block = output.body_offset + after.offset
            require(source.data[old_block:old_block + 8] == output.data[new_block:new_block + 8], "legacy block flags changed")
            old_link = U32.unpack_from(source.data, old_block + 20)[0]
            require(relocated_links.get(old_link) == U32.unpack_from(output.data, new_block + 20)[0], "legacy link relocation failed")
        else:
            start = before.tree_offset + (8 + len(before.raw_path) if source.format == "UUID" else 4)
            size = 12
        normalized_tree[start:start + size] = source.tree[start:start + size]
    require(bytes(normalized_tree) == source.tree, "opaque directory bytes or tail changed")

    # Check opaque bytes around entries using the original physical order. A resized
    # entry may move subsequent entries, but gaps and trailing data must survive.
    physical = sorted(source.entries, key=lambda item: (item.offset, item.padded_length))
    old_cursor = source.body_offset
    new_cursor = output.body_offset
    for before in physical:
        after = output_paths[before.path]
        old_start = source.body_offset + before.offset
        new_start = output.body_offset + after.offset
        require(old_start >= old_cursor and new_start >= new_cursor,
                f"physical entry order changed: {before.path}")
        require(source.data[old_cursor:old_start] == output.data[new_cursor:new_start],
                f"opaque gap bytes changed before: {before.path}")
        old_cursor = old_start + before.stored_length
        new_cursor = new_start + after.stored_length
    require(source.data[old_cursor:] == output.data[new_cursor:], "trailing archive bytes changed")
    byte_exact = source.data == output.data
    if not changed_entries:
        require(byte_exact, "unmodified reconstruction is not byte-for-byte identical")
    return {
        "status": "passed",
        "input": str(source.path.resolve()),
        "output": str(output.path.resolve()),
        "input_sha256": hashlib.sha256(source.data).hexdigest(),
        "output_sha256": hashlib.sha256(output.data).hexdigest(),
        "entries_checked": len(source.entries),
        "changed_entries": changed_entries,
        "preserved_ciphertext_entries": preserved_ciphertext_entries,
        "rekeyed_unchanged_entries": rekeyed_unchanged_entries,
        "format": source.format,
        "seeded_client_crc_verified": source.format != "LEGACY",
        "byte_exact": byte_exact,
        "directory_metadata_preserved": True,
        "opaque_gaps_and_trailer_preserved": True,
        "client_compatibility": ("legacy_layout_codec_verified" if source.format == "LEGACY" else "binary_crc_rules_verified") + "; runtime_validation_separate",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="original PVF (read-only)")
    parser.add_argument("--output", required=True, type=Path, help="exported PVF (read-only)")
    parser.add_argument("--changes", type=Path, help="JSON path -> replacement-file mapping")
    parser.add_argument("--report", type=Path, help="write a new report file; never overwrites an existing file")
    arguments = parser.parse_args()
    try:
        report = verify(Archive.read(arguments.input), Archive.read(arguments.output), read_changes(arguments.changes))
        serialized = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
        if arguments.report:
            with arguments.report.open("x", encoding="utf-8", newline="\n") as destination:
                destination.write(serialized)
        print(serialized, end="")
        return 0
    except (VerificationError, OSError, ValueError, struct.error) as exception:
        print(f"PVF verification failed: {exception}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
