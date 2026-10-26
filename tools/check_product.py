#!/usr/bin/env python3
"""Check a built product without error dialogs; optionally run editor/PVF recovery integration."""
from pathlib import Path
import argparse
import datetime
import subprocess
import uuid

ROOT = Path(__file__).resolve().parent.parent


def run(app: Path, root: Path, mode: str, archive: Path | None, npk: Path | None = None, progress_only: bool = False, binary_ani: bool = False, map_files: bool = False, dungeon_86: bool = False, entities_86: bool = False, dungeon_100: bool = False):
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
    if npk is not None:
        command.append(f"-Dpvf.preview.npk={npk}")
    if progress_only:
        command.append("-Dpvf.smoke.progressOnly=true")
    if binary_ani:
        command.append("-Dpvf.smoke.binaryAni=true")
    if map_files:
        command.append("-Dpvf.smoke.mapFiles=true")
    if dungeon_86:
        command.append("-Dpvf.smoke.dungeon86=true")
    if entities_86:
        command.append("-Dpvf.smoke.entities86=true")
    if dungeon_100:
        command.append("-Dpvf.smoke.dungeon100=true")
    command += ["-cp", f"{app / 'lib/platform-loader.jar'};{app / 'lib/pvf-studio.jar'}", "studio.pvf.product.PvfStudioMain"]
    if mode == "check":
        command += ["--check", str(root / "check.properties")]
    else:
        command += ["--smoke", str(archive), str(root / "smoke-results")]
    log = root / f"{mode}.log"
    with log.open("w", encoding="utf-8") as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, timeout=300 if progress_only or binary_ani or map_files or dungeon_86 or entities_86 or dungeon_100 else 120)
    if result.returncode:
        print(log.read_text(encoding="utf-8", errors="replace")[-10000:])
        raise RuntimeError(f"Product {mode} failed ({result.returncode}); logs: {root}")
    print(f"Product {mode}: PASS; logs={root}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--product", required=True, type=Path)
    parser.add_argument("--archive", type=Path, help="Also run the real editor, export, backup and recovery smoke")
    parser.add_argument("--npk", type=Path, help="With --archive, also verify resource preview against this NPK directory")
    parser.add_argument("--progress-only", action="store_true", help="With --archive, verify real loading progress instead of editing/export smoke")
    parser.add_argument("--binary-ani", action="store_true", help="With --archive, verify 86 binary ANI editor/playback/save/export")
    parser.add_argument("--map-files", action="store_true", help="With 86 --archive/--npk, verify background/viewport and file tree operations")
    parser.add_argument("--dungeon-86", action="store_true", help="With 86 --archive/--npk, verify DGN layouts, room/Boss switching and rendered scenes")
    parser.add_argument("--entities-86", action="store_true", help="With 86 --archive/--npk, verify flat MAP placements and ACT entity sprite rendering")
    parser.add_argument("--dungeon-100", action="store_true", help="With 100 --archive/--npk, verify explicit room choices and original gate coordinates")
    args = parser.parse_args()
    if args.npk and not args.archive:
        parser.error("--npk requires --archive")
    if args.progress_only and not args.archive:
        parser.error("--progress-only requires --archive")
    if args.binary_ani and (not args.archive or args.progress_only):
        parser.error("--binary-ani requires --archive and cannot combine with --progress-only")
    if args.map_files and (not args.archive or not args.npk or args.progress_only or args.binary_ani):
        parser.error("--map-files requires --archive/--npk and cannot combine with other smoke modes")
    if args.dungeon_86 and (not args.archive or not args.npk or args.progress_only or args.binary_ani or args.map_files):
        parser.error("--dungeon-86 requires --archive/--npk and cannot combine with other smoke modes")
    if args.entities_86 and (not args.archive or not args.npk or args.progress_only or args.binary_ani or args.map_files or args.dungeon_86):
        parser.error("--entities-86 requires --archive/--npk and cannot combine with other smoke modes")
    if args.dungeon_100 and (not args.archive or not args.npk or args.progress_only or args.binary_ani or args.map_files or args.dungeon_86 or args.entities_86):
        parser.error("--dungeon-100 requires --archive/--npk and cannot combine with other smoke modes")
    product = args.product.resolve()
    root = ROOT / "build/product-checks" / (datetime.datetime.now().strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8])
    root.mkdir(parents=True)
    run(product / "app", root, "check", None)
    if args.archive:
        run(product / "app", root, "smoke", args.archive.resolve(), args.npk.resolve() if args.npk else None, args.progress_only, args.binary_ani, args.map_files, args.dungeon_86, args.entities_86, args.dungeon_100)


if __name__ == "__main__":
    main()
