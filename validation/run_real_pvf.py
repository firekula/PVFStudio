#!/usr/bin/env python3
"""Export and independently verify existing PVFs without changing source assets.

Run after gradlew check :pvf-cli:installDist. All generated payloads, archives and
reports go in a new build/validation directory. No copyrighted assets are committed.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import platform
import re
import subprocess
import time
import uuid

from verify_pvf import Archive, require, verify


def write_json(path: Path, value: object) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def command(arguments: list[str], cwd: Path) -> tuple[str, float]:
    start = time.perf_counter()
    result = subprocess.run(arguments, cwd=cwd, capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {arguments[0]}\n{result.stdout}\n{result.stderr}")
    return result.stdout.strip(), time.perf_counter() - start


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, action="append", required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--server-reader", type=Path, help="Built ServerReader.dll for the original C# reader")
    arguments = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    installed = root / "pvf-cli/build/install/pvf-studio/lib"
    require(installed.is_dir(), "Build :pvf-cli:installDist first")
    run_directory = root / "build/validation" / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8])
    run_directory.mkdir(parents=True, exist_ok=False)
    java = [arguments.java, "-Xmx256m", "-cp", str(installed / "*"), "studio.pvf.cli.Main"]
    summary = {"status": "running", "created_utc": datetime.now(timezone.utc).isoformat(),
               "machine": platform.platform(), "java": arguments.java, "samples": [],
               "scope": "reader/writer compatibility; no running service or client was modified",
               "client_compatibility": "not_verified"}
    for source_path in arguments.input:
        source_path = source_path.resolve(strict=True)
        baseline_hash = hashlib.sha256(source_path.read_bytes()).hexdigest()
        sample_directory = run_directory / baseline_hash[:12]
        sample_directory.mkdir(exist_ok=False)
        baseline = Archive.read(source_path)
        paths = sample_directory / "paths.json"
        write_json(paths, [entry.path for entry in baseline.entries])
        info, _ = command(java + ["info", str(source_path)], root)
        sample = {"source": str(source_path), "source_sha256": baseline_hash,
                  "entry_count": len(baseline.entries), "info": info, "checks": []}

        # A valid script edit: change a CP936 item name and append a comment to a list.
        item_path = "stackable/recipe/nrcp_tra_rudechantre.stk"
        entries = {entry.path: entry for entry in baseline.entries}
        require(item_path in entries, f"Expected ACT1 validation item missing: {item_path}")
        text = baseline.plaintext(entries[item_path]).decode("gbk", errors="strict")
        name_pattern = re.compile(r"(\[name\]\s*`)([^`]*)(`)", re.IGNORECASE)
        modified, count = name_pattern.subn(lambda match: match[1] + match[2] + "_PVF验证" + match[3], text, count=1)
        require(count == 1, "Validation item has no editable [name] field")
        item_payload = sample_directory / "item-name.gbk.stk"
        item_payload.write_bytes(modified.encode("gbk", errors="strict"))
        list_path = "creature/script/creature.lst"
        require(list_path in entries, f"Validation list missing: {list_path}")
        list_payload = sample_directory / "creature.lst"
        list_payload.write_bytes(baseline.plaintext(entries[list_path]) + b"\r\n// PVF Studio round-trip validation\r\n")
        manifest = sample_directory / "changes.json"
        write_json(manifest, {item_path: item_payload.name, list_path: list_payload.name})
        changes = {item_path: item_payload.read_bytes(), list_path: list_payload.read_bytes()}

        for label, expected in [("noop", {}), ("modified", changes)]:
            output = sample_directory / (label + ".pvf")
            export_command = java + ["export", str(source_path), str(output)]
            if expected:
                export_command += ["--replace", item_path, str(item_payload), "--replace", list_path, str(list_payload)]
            export_stdout, export_seconds = command(export_command, root)
            verify_start = time.perf_counter()
            result = verify(baseline, Archive.read(output), expected)
            result["independent_verify_seconds"] = round(time.perf_counter() - verify_start, 3)
            result["cli_process_seconds"] = round(export_seconds, 3)
            result["cli"] = export_stdout
            if arguments.server_reader:
                server_command = ["dotnet", str(arguments.server_reader.resolve()), str(source_path), str(output), str(paths)]
                if expected:
                    server_command.append(str(manifest))
                server_output, _ = command(server_command, root)
                result["original_csharp_reader"] = json.loads(server_output)
            write_json(sample_directory / (label + "-report.json"), result)
            sample["checks"].append(result)
            print(f"{baseline_hash[:12]} {label}: {result['entries_checked']} entries; byte_exact={result['byte_exact']}; changed={result['changed_entries']}", flush=True)

        # Persist a draft, close/reopen in separate CLI processes, export and verify it.
        workspace = sample_directory / "workspace"
        command(java + ["stage", str(source_path), str(workspace), item_path, str(item_payload)], root)
        status, _ = command(java + ["status", str(source_path), str(workspace)], root)
        require("changed=1" in status, "Persisted workspace draft was not restored")
        workspace_output = sample_directory / "workspace.pvf"
        command(java + ["workspace-export", str(source_path), str(workspace), str(workspace_output)], root)
        workspace_result = verify(baseline, Archive.read(workspace_output), {item_path: item_payload.read_bytes()})
        write_json(sample_directory / "workspace-report.json", workspace_result)
        sample["workspace_restart"] = workspace_result
        require(hashlib.sha256(source_path.read_bytes()).hexdigest() == baseline_hash, "Original PVF changed")
        sample["original_preserved"] = True
        summary["samples"].append(sample)
    summary["status"] = "passed"
    write_json(run_directory / "summary.json", summary)
    print("summary=" + str(run_directory / "summary.json"), flush=True)


if __name__ == "__main__":
    main()
