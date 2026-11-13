package studio.pvf.cli;

import studio.pvf.preview.GameScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static studio.pvf.cli.AutomationCli.newOutput;
import static studio.pvf.cli.AutomationCli.sha256;
import static studio.pvf.cli.CliJson.object;

final class CliText {
    private static final int MAX_PAGE_CHARS = 16_384;
    private static final int MAX_SEARCH_BYTES = 16 * 1024 * 1024;
    private static final int MAX_SNIPPET = 200;
    private static final Pattern LINE = Pattern.compile("[^\\r\\n]*(?:\\r\\n|[\\r\\n]|$)");
    private static final Set<String> SCRIPTS = Set.of("ani", "act", "aic", "atk", "chr", "cre", "dgn", "equ",
            "lst", "map", "mob", "npc", "obj", "ptl", "qst", "skl", "stk", "str", "til", "txt");

    private CliText() {}

    static Object cat(CliSource source, AutomationCli.Arguments args) throws IOException {
        String path = source.requireEntry(args.arg(1));
        String text = source.read(path);
        var data = object("path", path, "charset", source.charset().name(), "sha256", sha256(source.bytes(path)));
        if (args.has("output")) {
            Path output = newOutput(Path.of(args.value("output")));
            Files.writeString(output, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            data.put("output", output.toString());
            data.put("characters", text.length());
            return data;
        }
        int from = args.number("from-line", 1);
        int maxLines = args.number("max-lines", 80);
        var lines = LINE.matcher(text);
        var page = new StringBuilder();
        int total = 0, returned = 0;
        boolean full = false;
        while (lines.find() && lines.start() != lines.end()) {
            total++;
            if (total < from || full) continue;
            int length = lines.end() - lines.start();
            if (returned == 0 && length > MAX_PAGE_CHARS) {
                throw new CliFailure("LINE_TOO_LONG", "Line " + total + " exceeds the JSON page size.",
                        "Use cat --output to save complete decoded UTF-8 text.");
            }
            if (returned >= maxLines || page.length() + length > MAX_PAGE_CHARS) {
                full = true;
                continue;
            }
            page.append(text, lines.start(), lines.end());
            returned++;
        }
        data.put("text", page.toString());
        data.put("from_line", from);
        data.put("lines_returned", returned);
        data.put("total_lines", total);
        data.put("next_line", full ? from + returned : null);
        return data;
    }

    static Object search(CliSource source, AutomationCli.Arguments args) throws IOException {
        boolean byName = args.value("field", "text").equals("name");
        var paths = source.paths().stream().filter(byName ? CliText::namedEntry : CliText::script)
                .filter(AutomationCli.pathFilter(args)).sorted().toList();
        var cursor = SearchCursor.parse(args.value("cursor"), paths.size());
        int matchLimit = args.number("limit", 50), fileLimit = args.number("scan-limit", 100);
        String query = args.arg(1);
        var pattern = Pattern.compile(query, Pattern.LITERAL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        var results = new ArrayList<Object>();
        var skipped = new ArrayList<Object>();
        var warnings = new ArrayList<String>();
        var strings = byName ? new GameScript.Strings(source::read, warnings) : null;
        int files = 0, bytes = 0;
        for (int index = cursor.file(); index < paths.size(); index++) {
            String path = paths.get(index);
            if (files >= fileLimit || bytes >= MAX_SEARCH_BYTES) {
                return searchResult(results, skipped, warnings, files, new SearchCursor(index, 1));
            }
            files++;
            int size = source.length(path);
            if (!source.textSizeSupported(size)) {
                skipped.add(object("path", path, "code", "ENTRY_TOO_LARGE"));
                continue;
            }
            bytes += size;
            String text;
            try {
                text = source.read(path);
            } catch (CliFailure unreadable) {
                if (!unreadable.code().equals("TEXT_ENCODING")) throw unreadable;
                skipped.add(object("path", path, "code", unreadable.code()));
                continue;
            }
            if (byName) {
                var fields = GameScript.fields(text);
                String name = strings.resolve(GameScript.value(fields, "name"));
                String english = strings.resolve(GameScript.value(fields, "name2"));
                if (pattern.matcher(name).find() || pattern.matcher(english).find()) {
                    if (results.size() == matchLimit) {
                        return searchResult(results, skipped, warnings, files, new SearchCursor(index, 1));
                    }
                    results.add(object("path", path, "name", abbreviate(name), "english", abbreviate(english)));
                }
                continue;
            }
            int line = 0;
            var lines = LINE.matcher(text);
            while (lines.find() && lines.start() != lines.end()) {
                line++;
                if (index == cursor.file() && line < cursor.line()) continue;
                var match = pattern.matcher(text).region(lines.start(), lines.end());
                if (!match.find()) continue;
                if (results.size() == matchLimit) {
                    return searchResult(results, skipped, warnings, files, new SearchCursor(index, line));
                }
                int start = Math.max(lines.start(), match.start() - MAX_SNIPPET / 4);
                int end = Math.min(lines.end(), start + MAX_SNIPPET);
                results.add(object("path", path, "line", line, "column", match.start() - lines.start() + 1,
                        "preview", text.substring(start, end).stripTrailing()));
            }
        }
        return searchResult(results, skipped, warnings, files, null);
    }

    private static boolean script(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 && SCRIPTS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean namedEntry(String path) {
        return path.endsWith(".equ") || path.endsWith(".skl") || path.endsWith(".stk");
    }

    private static String abbreviate(String name) {
        int end = name.offsetByCodePoints(0, Math.min(MAX_SNIPPET, name.codePointCount(0, name.length())));
        return end == name.length() ? name : name.substring(0, end) + "…";
    }

    private static Object searchResult(List<Object> results, List<Object> skipped, List<String> warnings,
                                       int scanned, SearchCursor next) {
        return object("matches", results, "files_scanned", scanned, "skipped", skipped,
                "warnings", warnings.stream().limit(20).toList(),
                "next_cursor", next == null ? null : next.file() + ":" + next.line());
    }

    private record SearchCursor(int file, int line) {
        static SearchCursor parse(String value, int files) {
            if (value == null) return new SearchCursor(0, 1);
            var parts = value.split(":", -1);
            if (parts.length == 2) {
                try {
                    int file = Integer.parseInt(parts[0]), line = Integer.parseInt(parts[1]);
                    if (file >= 0 && file <= files && line >= 1) return new SearchCursor(file, line);
                } catch (NumberFormatException ignored) { }
            }
            throw new IllegalArgumentException("--cursor must be a value returned by search.");
        }
    }
}
