#!/usr/bin/env python3
"""Create positive/negative PVF copies for the isolated native client matrix.

This does not start applications or change the client sandbox's live Script.pvf.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct
import zlib

from verify_pvf import Archive, HEADER_SIZE
from client_sandbox import PROJECT_ROOT, require


def encrypt(data, key):
    result = bytearray(len(data))
    for offset in range(0, len(data), 4):
        word = struct.unpack_from("<I", data, offset)[0]
        struct.pack_into("<I", result, offset, (((word << 6) | (word >> 26)) & 0xffffffff) ^ key)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sandbox", type=Path, required=True)
    parser.add_argument("--fresh-validation", type=Path, required=True)
    parser.add_argument("--legacy-validation", type=Path, required=True)
    args = parser.parse_args()
    sandbox = args.sandbox.resolve(strict=True)
    require(sandbox.parent == (PROJECT_ROOT / "build/client-validation").resolve(), "Sandbox must be a prepared direct child")
    require(json.loads((sandbox / "preparation.json").read_text())["status"] == "prepared", "Sandbox is not ready")
    cases = sandbox / "cases"
    cases.mkdir(exist_ok=False)
    fresh = args.fresh_validation.resolve(strict=True)
    legacy = args.legacy_validation.resolve(strict=True)
    sources = {
        "original": sandbox / "client/Script.pvf",
        "noop": fresh / "5fcd4b94dfb0/noop.pvf",
        "legacy-modified": legacy / "5fcd4b94dfb0/modified.pvf",
        "fixed-modified": fresh / "5fcd4b94dfb0/modified.pvf",
        "fixed-repository": fresh / "bc397d1463f1/modified.pvf",
    }
    manifest = {}
    for label, source in sources.items():
        target = cases / (label + ".pvf")
        shutil.copy2(source, target)
        require(not source.samefile(target) and target.stat().st_nlink == 1, "Case file must be a true copy")
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        manifest[label] = {"path": str(target), "sha256": digest,
                           "expected": "reject_native_directory_crc" if label == "legacy-modified" else "load_normally"}
    original = Archive.read(cases / "original.pvf")
    tree = bytearray(original.tree)
    consumed = max(entry.tree_offset + 20 + len(entry.raw_path) for entry in original.entries)
    require(consumed < len(tree), "This CRC-only negative needs directory padding")
    tree[-1] ^= 1
    require(zlib.crc32(tree, len(original.entries)) != original.tree_key, "Negative CRC must differ")
    data = bytearray(original.data)
    data[HEADER_SIZE:original.body_offset] = encrypt(tree, original.tree_key)
    negative = cases / "invalid-directory-padding-crc.pvf"
    with negative.open("xb") as output:
        output.write(data)
    manifest["invalid-directory-padding-crc"] = {
        "path": str(negative), "sha256": hashlib.sha256(data).hexdigest(),
        "expected": "reject_native_directory_crc", "all_directory_entries_and_body_unchanged": True,
        "changed_decrypted_directory_padding_bytes": 1,
    }
    with (cases / "cases.json").open("x", encoding="utf-8") as output:
        json.dump({"cases": manifest, "test_item_id": 5642, "new_item_name_suffix": "_PVF验证"}, output, ensure_ascii=False, indent=2)
    print(json.dumps({"status": "prepared", "case_count": len(manifest), "directory": str(cases)}, indent=2))


if __name__ == "__main__":
    main()
