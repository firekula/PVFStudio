package studio.pvf.cli;

import studio.pvf.core.PvfArchive;
import studio.pvf.core.PvfEntry;
import studio.pvf.core.PvfWriter;
import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.workspace.TextCodec;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        int status = run(args, new PrintStream(System.out, true, StandardCharsets.UTF_8),
                new PrintStream(System.err, true, StandardCharsets.UTF_8));
        if (status != 0) System.exit(status);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
                help(out);
                return 0;
            }
            switch (args[0]) {
                case "info" -> {
                    require(args, 2);
                    long start = System.nanoTime();
                    try (PvfArchive archive = PvfArchive.open(Path.of(args[1]))) {
                        out.println("source=" + archive.sourcePath());
                        out.println("bytes=" + archive.archiveSize());
                        out.println("entries=" + archive.fileCount());
                        out.println("format=" + archive.format().description());
                        out.println("sha256=" + archive.sha256());
                        out.printf(Locale.ROOT, "open_ms=%.3f%n", (System.nanoTime() - start) / 1_000_000.0);
                    }
                }
                case "list" -> {
                    if (args.length < 2 || args.length > 3) throw usage("list <archive> [path-filter]");
                    String filter = args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "";
                    try (PvfArchive archive = PvfArchive.open(Path.of(args[1]))) {
                        for (PvfEntry entry : archive.entries()) {
                            if (entry.path().contains(filter)) out.println(entry.length() + "\t" + entry.path());
                        }
                    }
                }
                case "extract" -> {
                    require(args, 4);
                    try (PvfArchive archive = PvfArchive.open(Path.of(args[1]))) {
                        Path output = Path.of(args[3]);
                        Files.write(output, archive.readBytes(args[2]), StandardOpenOption.CREATE_NEW);
                        out.println("extracted=" + output.toAbsolutePath());
                    }
                }
                case "cat" -> {
                    if (args.length != 3 && args.length != 4) throw usage("cat <archive> <virtual-path> [charset=GBK]");
                    Charset charset = Charset.forName(args.length == 4 ? args[3] : "GBK");
                    try (PvfArchive archive = PvfArchive.open(Path.of(args[1]))) {
                        byte[] bytes = archive.readBytes(args[2]);
                        out.print(studio.pvf.workspace.BinaryAniCodec.isBinary(args[2], bytes)
                                ? studio.pvf.workspace.BinaryAniCodec.decode(bytes, charset)
                                : studio.pvf.workspace.BinaryScriptCodec.isScript(bytes)
                                ? studio.pvf.workspace.BinaryScriptCodec.decode(bytes,
                                    new studio.pvf.workspace.BinaryScriptCodec.StringTable(archive.readBytes("stringtable.bin"), charset))
                                : TextCodec.decode(bytes, charset));
                    }
                }
                case "export" -> export(args, out);
                case "stage" -> {
                    require(args, 5);
                    try (PvfWorkspace workspace = PvfWorkspace.open(Path.of(args[1]), Path.of(args[2]))) {
                        workspace.stageBytes(args[3], Files.readAllBytes(Path.of(args[4])));
                        out.println("staged=" + workspace.dirtyPaths().size());
                    }
                }
                case "stage-text" -> {
                    if (args.length != 5 && args.length != 6) throw usage("stage-text <archive> <workspace-dir> <virtual-path> <utf8-file> [charset=GBK]");
                    try (PvfWorkspace workspace = PvfWorkspace.open(Path.of(args[1]), Path.of(args[2]))) {
                        workspace.stageText(args[3], Files.readString(Path.of(args[4]), StandardCharsets.UTF_8),
                                Charset.forName(args.length == 6 ? args[5] : "GBK"));
                        out.println("staged=" + workspace.dirtyPaths().size());
                    }
                }
                case "status" -> {
                    require(args, 3);
                    try (PvfWorkspace workspace = PvfWorkspace.open(Path.of(args[1]), Path.of(args[2]))) {
                        for (String path : workspace.dirtyPaths()) out.println(path);
                        out.println("changed=" + workspace.dirtyPaths().size());
                    }
                }
                case "revert" -> {
                    require(args, 4);
                    try (PvfWorkspace workspace = PvfWorkspace.open(Path.of(args[1]), Path.of(args[2]))) {
                        workspace.revert(args[3]);
                        out.println("changed=" + workspace.dirtyPaths().size());
                    }
                }
                case "workspace-export" -> {
                    require(args, 4);
                    try (PvfWorkspace workspace = PvfWorkspace.open(Path.of(args[1]), Path.of(args[2]))) {
                        workspace.export(Path.of(args[3]));
                        out.println("exported=" + Path.of(args[3]).toAbsolutePath());
                        out.println("changed=" + workspace.dirtyPaths().size());
                    }
                }
                default -> throw usage("Unknown command: " + args[0]);
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println("Invalid argument: " + ex.getMessage());
            return 2;
        } catch (IOException ex) {
            err.println("Operation failed: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            return 3;
        }
    }

    private static void export(String[] args, PrintStream out) throws IOException {
        if (args.length < 3 || (args.length - 3) % 3 != 0) {
            throw usage("export <archive> <new.pvf> [--replace <virtual-path> <local-file>]...");
        }
        try (PvfArchive archive = PvfArchive.open(Path.of(args[1]))) {
            Map<String, byte[]> changes = new LinkedHashMap<>();
            for (int i = 3; i < args.length; i += 3) {
                if (!args[i].equals("--replace")) throw usage("Expected --replace, got: " + args[i]);
                String canonical = archive.entry(args[i + 1]).path();
                if (changes.containsKey(canonical)) throw usage("Duplicate replacement: " + canonical);
                changes.put(canonical, Files.readAllBytes(Path.of(args[i + 2])));
            }
            long start = System.nanoTime();
            PvfWriter.export(archive, changes, Path.of(args[2]));
            out.println("exported=" + Path.of(args[2]).toAbsolutePath());
            out.println("changed=" + changes.size());
            out.printf(Locale.ROOT, "export_ms=%.3f%n", (System.nanoTime() - start) / 1_000_000.0);
        }
    }

    private static void require(String[] args, int length) {
        if (args.length != length) throw usage("Wrong number of arguments for " + args[0] + "; use --help");
    }

    private static IllegalArgumentException usage(String text) { return new IllegalArgumentException(text); }

    private static void help(PrintStream out) {
        String version = Main.class.getPackage().getImplementationVersion();
        out.println("PVF Studio " + (version == null ? "development" : version) + " — ACT1 / UUID / legacy PVF reader and lossless exporter");
        out.println("info <archive>");
        out.println("list <archive> [path-filter]");
        out.println("extract <archive> <virtual-path> <new-file>");
        out.println("cat <archive> <virtual-path> [charset=GBK]");
        out.println("export <archive> <new.pvf> [--replace <virtual-path> <local-file>]...");
        out.println("stage <archive> <workspace-dir> <virtual-path> <local-file>");
        out.println("stage-text <archive> <workspace-dir> <virtual-path> <utf8-file> [charset=GBK]");
        out.println("status <archive> <workspace-dir>");
        out.println("revert <archive> <workspace-dir> <virtual-path>");
        out.println("workspace-export <archive> <workspace-dir> <new.pvf>");
        out.println("All outputs must be new files. Offset-seeded client CRCs are verified and rebuilt; validate edited scripts in a test client.");
    }
}
