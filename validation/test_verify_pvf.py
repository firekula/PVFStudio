"""The independent verifier must reject altered exports, not just accept matching files."""

from pathlib import Path
import struct
import tempfile
import unittest

from verify_pvf import Archive, VerificationError, verify


def encrypt(data: bytes, key: int) -> bytes:
    output = bytearray()
    for value, in struct.iter_unpack("<I", data):
        output += struct.pack("<I", (((value << 6) | (value >> 26)) & 0xFFFFFFFF) ^ key)
    return bytes(output)


def fixture(first: bytes = b"first", second_key: int = 0x12345678,
            second_padding: bytes = b"\xa1\xb2", gap: bytes = b"GAP!") -> bytes:
    # Tree order is a,b; physical order is b,a, with opaque gaps and trailing data.
    key_a = 0x87654321
    body_b = encrypt(b"bb" + second_padding, second_key)
    padded_a = first + b"\0" * ((-len(first)) % 4)
    body = gap + body_b + gap + encrypt(padded_a, key_a) + b"TRAILER"
    tree = struct.pack("<IIIII", 10, len(gap) + len(body_b) + len(gap), len(first), key_a, 5) + b"a.etc"
    tree += struct.pack("<IIIII", 11, len(gap), 2, second_key, 5) + b"b.etc"
    tree += b"\xee\xff"
    return b"DNF_SCRIPT_PACK" + struct.pack("<III", len(tree), 0x10203040, 2) + encrypt(tree, 0x10203040) + body


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
        with self.assertRaisesRegex(VerificationError, "ciphertext or padding changed"):
            verify(self.read("source.pvf", fixture()),
                   self.read("output.pvf", fixture(second_padding=b"\0\0")), {})

    def test_key_change_is_rejected_even_when_plaintext_matches(self) -> None:
        with self.assertRaisesRegex(VerificationError, "encryption key changed"):
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


if __name__ == "__main__":
    unittest.main()
