#!/usr/bin/env python3
"""Check a built product without error dialogs; optionally run editor/PVF recovery integration."""
from pathlib import Path
import argparse
import datetime
import subprocess
import uuid

ROOT = Path(__file__).resolve().parent.parent


def run(app: Path, root: Path, mode: str, archive: Path | None):
    config = root / mode / "config"
    config.mkdir(parents=True)
    command = [
        str(app / "jbr/bin/java.exe"), f"@{app / 'bin/pvf-studio.vmoptions'}",
        f"-Xbootclasspath/a:{app / 'lib/nio-fs.jar'}", f"-Dpvf.studio.home={app}",
        f"-Didea.config.path={config}", f"-Didea.system.path={root / mode / 'system'}",
        f"-Didea.log.path={root / mode / 'log'}", f"-Didea.plugins.path={root / mode / 'plugins'}",
        "-Didea.is.integration.test=true",
    ]
    if mode == "check":
        command.append("-Djava.awt.headless=true")
    command += ["-cp", f"{app / 'lib/platform-loader.jar'};{app / 'lib/pvf-studio.jar'}", "studio.pvf.product.PvfStudioMain"]
    if mode == "check":
        command += ["--check", str(root / "check.properties")]
    else:
        command += ["--smoke", str(archive), str(root / "smoke-results")]
    log = root / f"{mode}.log"
    with log.open("w", encoding="utf-8") as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, timeout=120)
    if result.returncode:
        print(log.read_text(encoding="utf-8", errors="replace")[-10000:])
        raise RuntimeError(f"Product {mode} failed ({result.returncode}); logs: {root}")
    print(f"Product {mode}: PASS; logs={root}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--product", required=True, type=Path)
    parser.add_argument("--archive", type=Path, help="Also run the real editor, export, backup and recovery smoke")
    args = parser.parse_args()
    product = args.product.resolve()
    root = ROOT / "build/product-checks" / (datetime.datetime.now().strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8])
    root.mkdir(parents=True)
    run(product / "app", root, "check", None)
    if args.archive:
        run(product / "app", root, "smoke", args.archive.resolve())


if __name__ == "__main__":
    main()
