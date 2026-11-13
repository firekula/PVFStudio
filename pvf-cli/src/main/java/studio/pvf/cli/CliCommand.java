package studio.pvf.cli;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static studio.pvf.cli.CliJson.object;

enum CliCommand {
    INFO("info", "archive", "Read PVF format, entry count and SHA-256.", "info Script.pvf"),
    LIST("list", "archive", "Find virtual paths by prefix, suffix or substring.", "list Script.pvf --prefix skill/gunner/ --limit 20",
            Options.PREFIX, Options.SUFFIX, Options.CONTAINS, Options.LIMIT, Options.OFFSET, Options.WORKSPACE),
    CAT("cat", "archive path", "Read decoded script text. Use --output to save the complete UTF-8 text.",
            "cat Script.pvf skill/gunner/laserrifle.skl --max-lines 40", Options.CHARSET, Options.WORKSPACE, Options.FROM_LINE, Options.MAX_LINES, Options.OUTPUT),
    SEARCH("search", "archive query", "Find script text or localized skill and item names, ignoring case.",
            "search Script.pvf 激光炮 --field name --prefix skill/gunner/",
            new Flag("field", "text|name", "Search raw script lines (default) or localized names in .skl, .equ and .stk."),
            Options.PREFIX, Options.SUFFIX, Options.CHARSET, Options.WORKSPACE, Options.LIMIT, Options.SCAN_LIMIT, Options.CURSOR),
    INSPECT("inspect", "archive path", "Read localized skill or item information without image resources.",
            "inspect Script.pvf skill/gunner/laserrifle.skl", Options.CHARSET, Options.WORKSPACE, Options.LEVEL),
    EXTRACT("extract", "archive path new-file", "Copy an entry's original bytes to a new file.",
            "extract Script.pvf skill/gunner/laserrifle.skl laser.skl", Options.WORKSPACE),
    STAGE_TEXT("stage-text", "archive workspace path utf8-file", "Stage a complete UTF-8 script in a workspace.",
            "stage-text Script.pvf draft skill/gunner/laserrifle.skl edited.txt", Options.CHARSET, Options.EXPECT_SHA),
    REPLACE_TEXT("replace-text", "archive workspace path", "Stage a literal edit when the old text occurs exactly once.",
            "replace-text Script.pvf draft skill/gunner/laserrifle.skl --from OLD --to NEW", Options.CHARSET,
            new Flag("from", "text", "Exact case-sensitive text to find. Required and nonempty."),
            new Flag("to", "text", "Replacement text. Required. Use an empty value to remove the matched text."),
            Options.EXPECT_SHA),
    STATUS("status", "archive workspace", "List changed entries in a workspace.", "status Script.pvf draft", Options.LIMIT, Options.OFFSET),
    REVERT("revert", "archive workspace path", "Discard a staged entry in a workspace.",
            "revert Script.pvf draft skill/gunner/laserrifle.skl"),
    WORKSPACE_EXPORT("workspace-export", "archive workspace new-pvf", "Export drafts to a new PVF file.",
            "workspace-export Script.pvf draft Edited.pvf"),
    PREVIEW("preview", "archive path", "List ACT1 preview branches or sample a frame with combat boxes.",
            "preview Script.pvf skill/gunner/laserrifle.skl --npk ImagePacks2 --time 600 --output laser.png --hitboxes",
            Options.CHARSET, Options.WORKSPACE, Options.LEVEL,
            new Flag("npk", "directory", "Matching ACT1 NPK resource directory. Required."),
            new Flag("effect", "key", "Preview branch key from data.effects. Default auto."),
            new Flag("time", "ms", "Frame time in milliseconds. With --output, defaults to 0."),
            new Flag("output", "new-png", "Save a PNG frame to a new file."),
            new Flag("hitboxes", null, "Draw combat boxes on the output PNG."),
            new Flag("facing", "right|left", "Preview direction. Default right."));

    record Flag(String name, String value, String detail) {}

    private static final class Options {
        static final Flag PREFIX = new Flag("prefix", "path", "Virtual path prefix. Use a trailing / for a directory.");
        static final Flag SUFFIX = new Flag("suffix", "extension", "Virtual path suffix, for example .skl.");
        static final Flag CONTAINS = new Flag("contains", "text", "Case-insensitive substring in a virtual path.");
        static final Flag LIMIT = new Flag("limit", "count", "Page size. Default 50, maximum 500.");
        static final Flag OFFSET = new Flag("offset", "count", "Result offset. Pass data.next_offset to continue.");
        static final Flag WORKSPACE = new Flag("workspace", "directory", "Read the saved drafts in this existing workspace.");
        static final Flag CHARSET = new Flag("charset", "encoding", "Override the container default encoding.");
        static final Flag FROM_LINE = new Flag("from-line", "line", "First line to return, starting at 1.");
        static final Flag MAX_LINES = new Flag("max-lines", "count", "Number of lines. Default 80, maximum 500.");
        static final Flag OUTPUT = new Flag("output", "new-file", "Write complete decoded UTF-8 text to a new file.");
        static final Flag SCAN_LIMIT = new Flag("scan-limit", "count", "Maximum candidate files per call. Default 100, maximum 500.");
        static final Flag CURSOR = new Flag("cursor", "cursor", "Pass data.next_cursor to continue with the same query and filters.");
        static final Flag LEVEL = new Flag("level", "number", "Preview skill level. Default 1, maximum 1000.");
        static final Flag EXPECT_SHA = new Flag("expect-sha256", "hash", "Only edit if raw entry SHA-256 matches data.sha256 from cat.");
    }

    final String name;
    final List<String> positionals;
    final String summary;
    final String example;
    final Map<String, Flag> flags = new LinkedHashMap<>();

    CliCommand(String name, String positionals, String summary, String example, Flag... options) {
        this.name = name;
        this.positionals = List.of(positionals.split(" "));
        this.summary = summary;
        this.example = "PVFStudioCLI.exe --json " + example;
        for (Flag flag : options) flags.put(flag.name(), flag);
    }

    static CliCommand find(String name) {
        return Arrays.stream(values()).filter(command -> command.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown command: " + name));
    }

    Map<String, Object> schema() {
        return object("name", name, "summary", summary, "positionals", positionals,
                "options", flags.values().stream().map(flag -> object(
                        "name", "--" + flag.name(), "value", flag.value(), "required",
                        switch (flag.name()) { case "npk", "from", "to" -> true; default -> false; },
                        "description", flag.detail())).toList(),
                "example", example);
    }
}
