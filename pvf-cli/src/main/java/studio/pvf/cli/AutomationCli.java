package studio.pvf.cli;

import studio.pvf.core.PvfArchive;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static studio.pvf.cli.CliJson.object;

final class AutomationCli {
    private static final int SCHEMA_VERSION = 1;

    private AutomationCli() {}

    static boolean handles(String[] args) {
        if (args.length == 0) return false;
        return List.of("--json", "schema", "search", "inspect", "preview", "replace-text", "--version").contains(args[0]);
    }

    static int run(String[] args, PrintStream out) {
        int start = args[0].equals("--json") ? 1 : 0;
        String name = args.length > start ? args[start] : "schema";
        try {
            var tail = Arrays.copyOfRange(args, Math.min(start + 1, args.length), args.length);
            Object data = switch (name) {
                case "schema", "help", "--help" -> schema(tail);
                case "--version" -> {
                    if (tail.length != 0) throw new IllegalArgumentException("--version accepts no arguments.");
                    yield object("version", Main.version());
                }
                default -> execute(Arguments.parse(CliCommand.find(name), tail));
            };
            write(out, name, true, "data", data);
            return 0;
        } catch (IllegalArgumentException ex) {
            return error(out, name, 2, "INVALID_ARGUMENT", ex.getMessage(), "Use schema or schema " + name + " to check the syntax.");
        } catch (FileAlreadyExistsException ex) {
            return error(out, name, 3, "OUTPUT_EXISTS", "Output already exists: " + ex.getFile(), "Choose a new output filename.");
        } catch (CliFailure ex) {
            return error(out, name, 3, ex.code(), ex.getMessage(), ex.hint());
        } catch (IOException ex) {
            return error(out, name, 3, "IO_ERROR", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    "Check the input files, resource directory, workspace lock and output directory.");
        }
    }

    private static int error(PrintStream out, String name, int status, String code, String message, String hint) {
        write(out, name, false, "error", object("code", code, "message", message, "hint", hint));
        return status;
    }

    private static void write(PrintStream out, String command, boolean ok, String field, Object data) {
        out.println(CliJson.encode(object("schema_version", SCHEMA_VERSION, "ok", ok, "command", command, field, data)));
    }

    private static Object schema(String[] args) {
        if (args.length > 1) throw new IllegalArgumentException("Expected schema [command].");
        if (args.length == 1) return CliCommand.find(args[0]).schema();
        return object("version", Main.version(), "usage", "PVFStudioCLI.exe --json <command> [arguments]",
                "detail", "Run schema <command> for its options and a complete example.",
                "exit_codes", object("success", 0, "invalid_argument", 2, "operation_failed", 3),
                "commands", Arrays.stream(CliCommand.values())
                        .map(command -> object("name", command.name, "summary", command.summary)).toList());
    }

    private static Object execute(Arguments args) throws IOException {
        Path archive = args.path(0);
        Path workspace = switch (args.command()) {
            case STAGE_TEXT, REPLACE_TEXT, STATUS, REVERT, WORKSPACE_EXPORT -> args.path(1);
            default -> args.has("workspace") ? Path.of(args.value("workspace")) : null;
        };
        boolean edits = args.command() == CliCommand.STAGE_TEXT || args.command() == CliCommand.REPLACE_TEXT;
        try (var source = CliSource.open(archive, workspace, args.value("charset"), edits)) {
            return switch (args.command()) {
                case INFO -> info(source);
                case LIST -> list(source, args);
                case CAT -> CliText.cat(source, args);
                case SEARCH -> CliText.search(source, args);
                case INSPECT -> CliPreview.inspect(source, args);
                case EXTRACT -> extract(source, args);
                case STAGE_TEXT, REPLACE_TEXT -> edit(source, args);
                case STATUS -> status(source, args);
                case REVERT -> {
                    String path = source.requireEntry(args.arg(2));
                    source.workspace().revert(path);
                    yield object("path", path, "changed", source.workspace().dirtyPaths().size());
                }
                case WORKSPACE_EXPORT -> {
                    Path output = newOutput(args.path(2));
                    source.workspace().export(output);
                    yield object("output", output.toString(), "changed", source.workspace().dirtyPaths().size());
                }
                case PREVIEW -> CliPreview.preview(source, args);
            };
        }
    }

    private static Object info(CliSource source) {
        var archive = source.archive();
        return object("source", archive.sourcePath().toString(), "bytes", archive.archiveSize(), "entries", archive.fileCount(),
                "format", archive.format().name(), "description", archive.format().description(),
                "charset", source.charset().name(), "sha256", archive.sha256());
    }

    private static Object list(CliSource source, Arguments args) throws IOException {
        var matching = source.paths().stream().filter(pathFilter(args)).sorted().toList();
        int from = Math.min(args.number("offset", 0), matching.size());
        int end = Math.min(from + args.number("limit", 50), matching.size());
        var entries = new ArrayList<Map<String, Object>>();
        for (String path : matching.subList(from, end)) entries.add(object("path", path, "bytes", source.length(path)));
        return object("entries", entries, "total", matching.size(), "next_offset", end < matching.size() ? end : null);
    }

    static Predicate<String> pathFilter(Arguments args) {
        String prefix = normalizeFilter(args.value("prefix", ""));
        String suffix = normalizeFilter(args.value("suffix", ""));
        String contains = normalizeFilter(args.value("contains", ""));
        return path -> path.startsWith(prefix) && path.endsWith(suffix) && path.contains(contains);
    }

    private static Object status(CliSource source, Arguments args) {
        var paths = source.workspace().dirtyPaths().stream().sorted().toList();
        int from = Math.min(args.number("offset", 0), paths.size());
        int end = Math.min(from + args.number("limit", 50), paths.size());
        return object("workspace", source.workspace().directory().toString(), "changed", paths.size(),
                "paths", paths.subList(from, end), "next_offset", end < paths.size() ? end : null);
    }

    private static Object extract(CliSource source, Arguments args) throws IOException {
        String path = source.requireEntry(args.arg(1));
        Path output = newOutput(args.path(2));
        byte[] bytes = source.bytes(path);
        Files.write(output, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return object("path", path, "output", output.toString(), "bytes", bytes.length, "sha256", sha256(bytes));
    }

    private static Object edit(CliSource source, Arguments args) throws IOException {
        String path = source.requireEntry(args.arg(2));
        var workspace = source.workspace();
        String expected = args.value("expect-sha256");
        if (expected != null && !sha256(source.bytes(path)).equalsIgnoreCase(expected)) {
            throw new CliFailure("STALE_ENTRY", "Entry bytes have changed: " + path,
                    "Read the current draft with cat --workspace and use its sha256.");
        }
        String text;
        if (args.command() == CliCommand.STAGE_TEXT) {
            try {
                text = Files.readString(args.path(3), StandardCharsets.UTF_8);
                if (text.startsWith("\uFEFF") && !source.read(path).startsWith("\uFEFF")) text = text.substring(1);
            } catch (CharacterCodingException invalidUtf8) {
                throw new CliFailure("TEXT_ENCODING", "The input file is not UTF-8: " + args.path(3),
                        "Save the edited text file as UTF-8 and retry.");
            }
        } else {
            String original = source.read(path);
            String from = args.value("from");
            int index = original.indexOf(from);
            if (index < 0 || original.indexOf(from, index + 1) >= 0) {
                throw new CliFailure("MATCH_COUNT", "Expected exactly one match, found " + (index < 0 ? "zero" : "more than one") + ".",
                        "Read the draft with cat and use a longer exact string, or use stage-text.");
            }
            text = original.substring(0, index) + args.value("to") + original.substring(index + from.length());
        }
        try {
            workspace.stageText(path, text, source.charset());
        } catch (CharacterCodingException invalidEncoding) {
            throw new CliFailure("TEXT_ENCODING", "Cannot encode " + path + " as " + source.charset().name(),
                    "Use characters supported by the archive charset or specify the correct --charset.");
        }
        return object("path", path, "workspace", workspace.directory().toString(),
                "changed", workspace.dirtyPaths().size(), "sha256", sha256(source.bytes(path)));
    }

    static Path newOutput(Path path) throws IOException {
        Path output = path.toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(output.toString());
        if (output.getParent() == null || !Files.isDirectory(output.getParent())) {
            throw new CliFailure("OUTPUT_DIRECTORY", "Output parent directory does not exist: " + output,
                    "Create the parent directory or choose an existing directory.");
        }
        return output;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String normalizeFilter(String value) {
        return value.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    record Arguments(CliCommand command, List<String> positional, Map<String, String> flags) {
        String arg(int index) { return positional.get(index); }
        Path path(int index) { return Path.of(arg(index)); }
        boolean has(String name) { return flags.containsKey(name); }
        String value(String name) { return flags.get(name); }
        String value(String name, String fallback) { return flags.getOrDefault(name, fallback); }
        int number(String name, int fallback) { return has(name) ? Integer.parseInt(value(name)) : fallback; }

        static Arguments parse(CliCommand command, String[] input) {
            var positional = new ArrayList<String>();
            var flags = new LinkedHashMap<String, String>();
            for (int i = 0; i < input.length; i++) {
                String token = input[i];
                if (token.equals("--")) {
                    positional.addAll(Arrays.asList(input).subList(i + 1, input.length));
                    break;
                }
                if (!token.startsWith("--")) {
                    positional.add(token);
                    continue;
                }
                int equals = token.indexOf('=');
                String name = token.substring(2, equals < 0 ? token.length() : equals);
                var flag = command.flags.get(name);
                if (flag == null) throw new IllegalArgumentException("Unknown option for " + command.name + ": --" + name);
                String value = "";
                if (flag.value() == null) {
                    if (equals >= 0) throw new IllegalArgumentException("--" + name + " takes no value.");
                } else if (equals >= 0) {
                    value = token.substring(equals + 1);
                } else if (i + 1 < input.length && !input[i + 1].startsWith("--")) {
                    value = input[++i];
                } else {
                    throw new IllegalArgumentException("Missing value for --" + name + ".");
                }
                if (flags.putIfAbsent(name, value) != null) throw new IllegalArgumentException("Repeated option: --" + name);
            }
            if (positional.size() != command.positionals.size()) {
                throw new IllegalArgumentException("Expected " + command.name + " " + String.join(" ", command.positionals));
            }
            var args = new Arguments(command, List.copyOf(positional), Map.copyOf(flags));
            args.validate();
            return args;
        }

        private void validate() {
            for (String value : positional) if (value.isBlank()) throw new IllegalArgumentException("Empty positional argument.");
            for (var flag : flags.entrySet()) {
                String name = flag.getKey(), value = flag.getValue();
                if (command.flags.get(name).value() != null && value.isEmpty() && !name.equals("to")) {
                    throw new IllegalArgumentException("Empty value for --" + name + ".");
                }
            }
            range("offset", 0, Integer.MAX_VALUE);
            range("limit", 1, 500);
            range("max-lines", 1, 500);
            range("from-line", 1, Integer.MAX_VALUE);
            range("scan-limit", 1, 500);
            range("level", 1, 1000);
            range("time", 0, Integer.MAX_VALUE);
            if (command == CliCommand.REPLACE_TEXT && (!has("from") || !has("to"))) {
                throw new IllegalArgumentException("replace-text requires --from and --to. Use --to= for deletion.");
            }
            if (has("expect-sha256") && !value("expect-sha256").matches("[a-fA-F0-9]{64}")) {
                throw new IllegalArgumentException("--expect-sha256 must contain 64 hex digits.");
            }
            if (has("field") && !List.of("text", "name").contains(value("field"))) {
                throw new IllegalArgumentException("--field must be text or name.");
            }
            if (command == CliCommand.CAT && has("output") && (has("from-line") || has("max-lines"))) {
                throw new IllegalArgumentException("cat --output writes the complete script and cannot be combined with line options.");
            }
            if (command == CliCommand.PREVIEW) {
                if (!has("npk")) throw new IllegalArgumentException("preview requires --npk <directory>.");
                if (!List.of("right", "left").contains(value("facing", "right"))) {
                    throw new IllegalArgumentException("--facing must be right or left.");
                }
                if (has("hitboxes") && !has("output") && !has("time")) {
                    throw new IllegalArgumentException("Use --time to sample hitboxes or --output to draw them.");
                }
            }
        }

        private void range(String name, int min, int max) {
            if (!has(name)) return;
            try {
                int value = number(name, min);
                if (value >= min && value <= max) return;
            } catch (NumberFormatException ignored) { }
            throw new IllegalArgumentException("--" + name + " must be an integer from " + min + " to " + max + ".");
        }
    }
}
