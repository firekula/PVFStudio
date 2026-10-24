import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest


SPEC = importlib.util.spec_from_file_location("client_sandbox", Path(__file__).with_name("client_sandbox.py"))
sandbox = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = sandbox
SPEC.loader.exec_module(sandbox)


class ClientSandboxSafetyTests(unittest.TestCase):
    def test_rejects_traversal_and_existing_destinations(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for invalid in ("..", "../outside", "C:\\outside", "/outside", "nested/child"):
                with self.assertRaises(RuntimeError):
                    sandbox.new_sandbox_path(root, invalid)
            target = sandbox.new_sandbox_path(root, "fresh-run")
            target.mkdir(parents=True)
            with self.assertRaises(RuntimeError):
                sandbox.new_sandbox_path(root, "fresh-run")

    def test_real_copy_is_independent_and_does_not_overwrite(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "source.bin"
            destination = Path(temporary) / "copy.bin"
            source.write_bytes(b"original asset")
            sandbox.copy_file(source, destination)
            destination.write_bytes(b"test changes")
            self.assertEqual(b"original asset", source.read_bytes())
            with self.assertRaises(RuntimeError):
                sandbox.copy_file(source, destination)

    def test_hardlinks_are_explicitly_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "source.bin"
            destination = Path(temporary) / "link.bin"
            source.write_bytes(b"same identity")
            os.link(source, destination)
            with self.assertRaises(RuntimeError):
                sandbox.assert_private_copy(source, destination)

    def test_all_network_config_is_loopback_and_high_port(self):
        config = sandbox.isolated_server_config(Path("client"), Path("server"))["DFLegacy"]
        for name in ("Admin", "Entrance", "Channel", "CharacterDatagram", "GameplayDatagram", "GameProbe"):
            self.assertEqual("127.0.0.1", config[name]["Host"])
            for key, value in config[name].items():
                if key in ("Port", "PrimaryPort", "SecondaryPort"):
                    self.assertGreaterEqual(value, 40000)
        self.assertFalse(config["Processes"]["Channel"]["Enabled"])
        self.assertFalse(config["CharacterDatagram"]["Enabled"])
        self.assertEqual(["pvfprobe20261024"], config["Admin"]["Users"])


if __name__ == "__main__":
    unittest.main()
