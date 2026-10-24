"""The independent verifier must reject altered exports, not just accept matching files."""

from pathlib import Path
import struct
import tempfile
import unittest
import zlib

from verify_pvf import Archive, VerificationError, verify


def encrypt(data: bytes, key: int) -> bytes:
    output = bytearray()
    for value, in struct.iter_unpack("<I", data):
        output += struct.pack("<I", (((value << 6) | (value >> 26)) & 0xFFFFFFFF) ^ key)
    return bytes(output)


def fixture(first: bytes = b"first", second_key: int | None = None,
            second_padding: bytes = b"\xa1\xb2", gap: bytes = b"GAP!", second: bytes = b"bb") -> bytes:
    # Tree order is a,b; physical order is b,a, with opaque gaps and trailing data.
    padding_length = (-len(second)) % 4
    padding_b = second_padding if len(second_padding) == padding_length else bytes(padding_length)
    padded_b = second + padding_b
    key_b = zlib.crc32(padded_b, len(gap)) if second_key is None else second_key
    body_b = encrypt(padded_b, key_b)
    padded_a = first + b"\0" * ((-len(first)) % 4)
    key_a = zlib.crc32(padded_a, len(gap) + len(body_b) + len(gap))
    body = gap + body_b + gap + encrypt(padded_a, key_a) + b"TRAILER"
    tree = struct.pack("<IIIII", 10, len(gap) + len(body_b) + len(gap), len(first), key_a, 5) + b"a.etc"
    tree += struct.pack("<IIIII", 11, len(gap), len(second), key_b, 5) + b"b.etc"
    tree += b"\xee\xff"
    tree_key = zlib.crc32(tree, 2)
    return b"DNF_SCRIPT_PACK" + struct.pack("<III", len(tree), tree_key, 2) + encrypt(tree, tree_key) + body


class VerifyPvfTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def read(self, filename: str, data: bytes) -> Archive:
        path = self.root / filename
        path.write_bytes(data)
        return Archive.read(path)

    def test_exact_reconstruction_is_accepted(self) -> None:
        data = fixture()
        report = verify(self.read("source.pvf", data), self.read("output.pvf", data), {})
        self.assertTrue(report["byte_exact"])
        self.assertEqual(2, report["preserved_ciphertext_entries"])

    def test_resized_expected_edit_is_accepted(self) -> None:
        source = self.read("source.pvf", fixture())
        output = self.read("output.pvf", fixture(first=b"a longer replacement"))
        report = verify(source, output, {"a.etc": b"a longer replacement"})
        self.assertFalse(report["byte_exact"])
        self.assertEqual(1, report["changed_entries"])
        self.assertEqual(1, report["preserved_ciphertext_entries"])

    def test_unexpected_plaintext_edit_is_rejected(self) -> None:
        with self.assertRaisesRegex(VerificationError, "plaintext mismatch"):
            verify(self.read("source.pvf", fixture()), self.read("output.pvf", fixture(first=b"wrong")), {})

    def test_ciphertext_padding_change_is_rejected_even_when_plaintext_matches(self) -> None:
        with self.assertRaisesRegex(VerificationError, "padding changed"):
            verify(self.read("source.pvf", fixture()),
                   self.read("output.pvf", fixture(second_padding=b"\0\0")), {})

    def test_key_change_is_rejected_even_when_plaintext_matches(self) -> None:
        with self.assertRaisesRegex(VerificationError, "client entry CRC mismatch"):
            verify(self.read("source.pvf", fixture()),
                   self.read("output.pvf", fixture(second_key=0x99887766)), {})

    def test_gap_change_is_rejected(self) -> None:
        with self.assertRaisesRegex(VerificationError, "gap bytes changed"):
            verify(self.read("source.pvf", fixture()),
                   self.read("output.pvf", fixture(gap=b"nope")), {})

    def test_truncated_body_is_rejected(self) -> None:
        data = fixture()
        # Remove enough to cut actual encrypted entry data, beyond the opaque tail.
        with self.assertRaisesRegex(VerificationError, "out-of-range"):
            self.read("truncated.pvf", data[:-8])

    def test_shifted_unchanged_entries_are_rekeyed_and_preserve_plaintext_padding(self) -> None:
        source = self.read("source.pvf", fixture())
        output = self.read("output.pvf", fixture(second=b"longer second payload"))
        report = verify(source, output, {"b.etc": b"longer second payload"})
        self.assertEqual(1, report["rekeyed_unchanged_entries"])
        self.assertEqual(0, report["preserved_ciphertext_entries"])
        self.assertTrue(report["seeded_client_crc_verified"])

    def test_bad_directory_crc_is_rejected_before_parsing(self) -> None:
        data = bytearray(fixture())
        tree_length = struct.unpack_from("<I", data, 15)[0]
        data[27 + tree_length - 1] ^= 1
        with self.assertRaisesRegex(VerificationError, "client directory CRC mismatch"):
            self.read("bad-tree.pvf", data)

    def test_wrong_entry_crc_is_rejected_even_with_valid_directory(self) -> None:
        data = bytearray(fixture())
        source = self.read("source.pvf", data)
        first = source.entries[0]
        data[source.body_offset + first.offset + first.padded_length - 1] ^= 1
        corrupt = self.read("bad-entry.pvf", data)
        with self.assertRaisesRegex(VerificationError, "client entry CRC mismatch"):
            verify(source, corrupt, {})


if __name__ == "__main__":
    unittest.main()
