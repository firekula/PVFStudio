#!/usr/bin/env python3
"""Prepare a private ACT1 client/server validation sandbox; never launches either.

Only a new direct child of this project's build/client-validation/ may be created.
All resources are real copies. Existing credentials, stores, and logs are excluded.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parent.parent
SOURCE_CLIENT = PROJECT_ROOT.parent / "DF"
SOURCE_SERVER = SOURCE_CLIENT / "town-test"
ASSET_DIRECTORIES = ("ImagePacks2", "Music", "SoundPacks", "Plugin")
CLIENT_FILES = {"dnf.exe", "7z.exe", "audio.xml", "dfighter.ini", "localpackage.lst",
                "tenparty.dat", "config.ini", "script.pvf"}
SERVER_JSON_SUFFIXES = (".deps.json", ".runtimeconfig.json", ".staticwebassets.runtime.json",
                        ".staticwebassets.endpoints.json")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def new_sandbox_path(project_root: Path, run_id: str) -> Path:
    require(bool(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}", run_id)), "Invalid run id")
    require(run_id not in (".", ".."), "Run id must be a directory name")
    root = project_root.resolve(strict=True)
    allowed = (root / "build/client-validation").resolve()
    require(allowed.is_relative_to(root), "Sandbox root resolves outside the project")
    target = allowed / run_id
    require(target.parent == allowed and target.is_relative_to(allowed), "Unsafe sandbox path")
    require(not target.exists() and not target.is_symlink(), f"Sandbox already exists: {target}")
    return target


def file_sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def file_identity(path: Path) -> dict[str, Any]:
    attributes = path.stat()
    return {"path": str(path.absolute()), "resolved_path": str(path.resolve(strict=True)),
            "bytes": attributes.st_size, "sha256": file_sha256(path),
            "source_is_symlink": path.is_symlink(), "source_link_count": attributes.st_nlink,
            "mtime_ns": attributes.st_mtime_ns}


def critical_sources() -> dict[str, Path]:
    files = {
        "client_dnf": SOURCE_CLIENT / "DNF.exe",
        "client_pvf": SOURCE_CLIENT / "Script.pvf",
        "client_ijl15": SOURCE_CLIENT / "ijl15.dll",
        "client_config": SOURCE_CLIENT / "Config.ini",
        # Record only its hash; never copy or serialize its private contents.
        "private_client_config": SOURCE_CLIENT / "DFLConfig.ini",
        "external_strings": SOURCE_CLIENT / "Plugin/dnf.str",
        "server_config": SOURCE_SERVER / "server.json",
        "server_executable": SOURCE_SERVER / "DFLegacy.Server.exe",
        "server_assembly": SOURCE_SERVER / "DFLegacy.Server.dll",
    }
    require(all(path.is_file() for path in files.values()), "Required source files are missing")
    return files


def server_observation(process_id: int) -> dict[str, Any]:
    shell = shutil.which("pwsh") or shutil.which("powershell")
    require(shell is not None, "PowerShell is required for a read-only process/socket baseline")
    # This child is only a read-only inventory command. It cannot launch or stop apps.
    command = r"""
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$serverProcess = Get-Process -Id PROCESS_ID -ErrorAction Stop
$tcp = @(Get-NetTCPConnection -State Listen -ErrorAction Stop |
  Where-Object OwningProcess -eq PROCESS_ID |
  Select-Object LocalAddress,LocalPort,OwningProcess)
$udp = @(Get-NetUDPEndpoint -ErrorAction Stop |
  Where-Object OwningProcess -eq PROCESS_ID |
  Select-Object LocalAddress,LocalPort,OwningProcess)
[pscustomobject]@{
  sampled_at_utc = [DateTime]::UtcNow.ToString('o')
  pid = $serverProcess.Id
  executable = $serverProcess.Path
  started_at_utc = $serverProcess.StartTime.ToUniversalTime().ToString('o')
  listening_tcp = $tcp
  bound_udp = $udp
} | ConvertTo-Json -Depth 5 -Compress
""".replace("PROCESS_ID", str(process_id))
    result = subprocess.run([shell, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command],
                            check=True, capture_output=True, encoding="utf-8", timeout=45)
    return json.loads(result.stdout.lstrip("\ufeff"))


def assert_private_copy(source: Path, destination: Path) -> None:
    require(destination.is_file() and not destination.is_symlink(), f"Not a regular copied file: {destination}")
    require(not os.path.samefile(source, destination), f"Source and destination share file identity: {destination}")
    require(destination.stat().st_nlink == 1, f"Destination has shared hard links: {destination}")
    require(source.stat().st_size == destination.stat().st_size, f"Copy size mismatch: {destination}")


def copy_file(source: Path, destination: Path) -> None:
    require(not destination.exists() and not destination.is_symlink(), f"Refusing existing output: {destination}")
    shutil.copy2(source, destination, follow_symlinks=True)
    assert_private_copy(source, destination)


def listener_identities(observation: dict[str, Any]) -> set[tuple[str, str, int, int]]:
    return {(protocol, endpoint["LocalAddress"], endpoint["LocalPort"], endpoint["OwningProcess"])
            for protocol, field in (("tcp", "listening_tcp"), ("udp", "bound_udp"))
            for endpoint in observation[field]}


def verify_tree(source: Path, destination: Path) -> tuple[int, int]:
    files = 0
    total = 0
    for directory, directories, names in os.walk(destination, followlinks=False):
        here = Path(directory)
        for name in directories:
            child = here / name
            require(not child.is_symlink() and not (hasattr(child, "is_junction") and child.is_junction()),
                    f"Destination directory is a link: {child}")
        for name in names:
            target = here / name
            original = source / target.relative_to(destination)
            assert_private_copy(original, target)
            files += 1
            total += target.stat().st_size
    return files, total


def patch_client_config(path: Path) -> None:
    raw = path.read_bytes()
    # The source INI is ASCII, so this also preserves any unrelated 8-bit comments.
    text = raw.decode("latin-1")
    overrides = {"EnableAutoInput": "0", "DumpNetwork": "1",
                 "DumpNetworkPayloadBytes": "0", "DumpNetworkMaxMegabytes": "8"}
    for key, value in overrides.items():
        pattern = re.compile(r"(?im)^" + re.escape(key) + r"\s*=.*$")
        require(len(pattern.findall(text)) == 1, f"Expected one INI setting: {key}")
        text = pattern.sub(key + "=" + value, text)
    normalized = text.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n")
    path.write_bytes(normalized.encode("latin-1"))


def isolated_server_config(client: Path, server: Path) -> dict[str, Any]:
    return {"DFLegacy": {
        "ScriptPvfPath": str((client / "Script.pvf").absolute()),
        "SkillScriptPath": "", "OriginalSkillScriptPath": "",
        "DataPath": str((server / "data/state.json").absolute()),
        "Admin": {"Host": "127.0.0.1", "Port": 48081, "Users": ["pvfprobe20261024"]},
        "Processes": {"Channel": {"Enabled": False, "LinkPort": 47004},
                      "Game": {"EntranceBinding": True}},
        "Entrance": {"Enabled": True, "Host": "127.0.0.1", "Port": 42311,
                     "AdditionalPorts": [47001, 48080]},
        "Channel": {"Name": "PVF Validation", "Host": "127.0.0.1", "Port": 47001,
                    "ChannelNumber": 1, "MaximumUsers": 1, "CurrentUsers": 0},
        # Real captured ACT1 sessions use the two advertised gameplay UDP ports.
        # This unrelated legacy listener is disabled, never left at port 2311.
        "CharacterDatagram": {"Enabled": False, "Host": "127.0.0.1", "Port": 42311},
        "GameplayDatagram": {"Enabled": True, "Host": "127.0.0.1", "AdvertisedHost": "127.0.0.1",
                             "PrimaryPort": 47002, "SecondaryPort": 47003},
        "GameProbe": {"Enabled": False, "Host": "127.0.0.1", "Port": 47004},
        "Debug": {"TownGhost": {"Enabled": False}},
        "TownSync": {"Enabled": True, "ForeignAppearanceBeforeRoster": True, "PrivateAreas": ["1:1"]},
        "Drop": {"Enabled": True, "RatePercent": 100, "EconomicRate": 1, "ForceDrops": False},
        "EnablePacketTracing": True, "HexDumpLimit": 64,
    }}


def prepare(run_id: str, original_server_pid: int) -> dict[str, Any]:
    target = new_sandbox_path(PROJECT_ROOT, run_id)
    sources = critical_sources()
    before = {name: file_identity(path) for name, path in sources.items()}
    original_process = server_observation(original_server_pid)
    require(original_process["executable"].lower() == str(SOURCE_SERVER / "DFLegacy.Server.exe").lower(),
            "Original server PID no longer belongs to the expected deployment")
    baseline = {"format": 1, "created_at_utc": datetime.now(timezone.utc).isoformat(),
                "source_client": str(SOURCE_CLIENT), "source_server": str(SOURCE_SERVER),
                "critical_source_files": before, "original_server": original_process,
                "scope": "Only independent new copies; no application processes or HTTP requests are started"}
    target.mkdir(parents=True, exist_ok=False)
    client = target / "client"
    server = target / "server"
    client.mkdir()
    server.mkdir()
    (target / "baseline.json").write_text(json.dumps(baseline, indent=2) + "\n", encoding="utf-8")
    copied = 0
    total = 0
    started = time.monotonic()
    for source in sorted(SOURCE_CLIENT.iterdir()):
        if source.is_file() and (source.name.lower() in CLIENT_FILES or source.suffix.lower() == ".dll"):
            copy_file(source, client / source.name)
            copied += 1
            total += source.stat().st_size
    for name in ASSET_DIRECTORIES:
        source = SOURCE_CLIENT / name
        require(source.is_dir(), f"Required asset directory missing: {source}")
        print(f"Copying client resource directory: {name}", flush=True)
        shutil.copytree(source, client / name, symlinks=False, copy_function=shutil.copy2)
        count, byte_count = verify_tree(source, client / name)
        copied += count
        total += byte_count
    for source in sorted(SOURCE_SERVER.iterdir()):
        if source.is_file() and (source.suffix.lower() in (".exe", ".dll")
                                 or source.name.lower().endswith(SERVER_JSON_SUFFIXES)):
            copy_file(source, server / source.name)
            copied += 1
            total += source.stat().st_size
    if (SOURCE_SERVER / "wwwroot").is_dir():
        shutil.copytree(SOURCE_SERVER / "wwwroot", server / "wwwroot", symlinks=False, copy_function=shutil.copy2)
        count, byte_count = verify_tree(SOURCE_SERVER / "wwwroot", server / "wwwroot")
        copied += count
        total += byte_count
    require(not (server / "data").exists(), "Original server state was unexpectedly copied")
    require(not (client / "DFLConfig.ini").exists() and not (client / "loginInfo.inf").exists(),
            "Private client credentials were unexpectedly copied")
    patch_client_config(client / "Config.ini")
    (server / "server.json").write_text(json.dumps(isolated_server_config(client, server), indent=2) + "\n", encoding="utf-8")
    (client / "Logs").mkdir()
    (client / "ScreenShot").mkdir()
    after = {name: file_identity(path) for name, path in sources.items()}
    require(all(before[name]["sha256"] == after[name]["sha256"] for name in sources),
            "An original critical source changed during preparation; do not start this sandbox")
    process_after = server_observation(original_server_pid)
    require(process_after["started_at_utc"] == original_process["started_at_utc"],
            "Original server identity changed during preparation")
    require(listener_identities(process_after) == listener_identities(original_process),
            "Original server listener endpoints changed during preparation")
    result = {"status": "prepared", "sandbox": str(target), "client": str(client), "server": str(server),
              "files_copied": copied, "bytes_copied": total,
              "elapsed_seconds": round(time.monotonic() - started, 3),
              "separate_file_identities_verified": True,
              "critical_source_sha256_unchanged": True,
              "original_server_pid_and_start_time_unchanged": True,
              "original_server_listener_endpoints_unchanged": True,
              "original_server_after": process_after,
              "client_pvf_sha256": file_sha256(client / "Script.pvf"),
              "applications_launched": False, "http_requests_sent": False,
              "remaining_step": "Root must install the freshly fixed exported PVF, bootstrap only this new store, and launch the isolated copies"}
    (target / "preparation.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["prepare"])
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--original-server-pid", type=int, default=47176)
    arguments = parser.parse_args()
    require(os.name == "nt", "This sandbox is for the Windows ACT1 client")
    require(arguments.original_server_pid > 0, "Invalid original server PID")
    print(json.dumps(prepare(arguments.run_id, arguments.original_server_pid), indent=2), flush=True)


if __name__ == "__main__":
    main()
