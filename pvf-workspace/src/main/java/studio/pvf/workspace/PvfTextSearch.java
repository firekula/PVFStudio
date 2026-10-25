package studio.pvf.workspace;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/** Bounded content scan. Cancellation is cooperative: interrupting a PVF FileChannel closes it. */
public final class PvfTextSearch {
    private PvfTextSearch() {}
    public static Result scan(List<String> paths, Reader reader, String query, String pathFilter,
                              boolean matchCase, int limit, BooleanSupplier cancelled, IntConsumer progress) throws IOException {
        if (query.isEmpty() || limit < 1) throw new IllegalArgumentException("需要搜索内容和正数结果上限");
        var pattern = Pattern.compile(Pattern.quote(query), matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        String filter = pathFilter.toLowerCase(Locale.ROOT).trim();
        List<Hit> hits = new ArrayList<>(); int scanned = 0, skipped = 0;
        for (String path : paths) {
            if (cancelled.getAsBoolean()) return new Result(List.copyOf(hits), scanned, skipped, true, false);
            if (!path.toLowerCase(Locale.ROOT).contains(filter)) continue;
            String text;
            try { text = reader.read(path); }
            catch (CharacterCodingException invalid) { skipped++; continue; }
            if (text == null || text.indexOf('\0') >= 0) { skipped++; continue; }
            text = text.replace("\r\n", "\n").replace('\r', '\n');
            var matcher = pattern.matcher(text); int line = 1, lineStart = 0, cursor = 0;
            while (matcher.find()) {
                if (cancelled.getAsBoolean()) return new Result(List.copyOf(hits), scanned, skipped, true, false);
                while (cursor < matcher.start()) if (text.charAt(cursor++) == '\n') { line++; lineStart = cursor; }
                int end = text.indexOf('\n', matcher.start()); if (end < 0) end = text.length();
                int start = Math.max(lineStart, matcher.start() - 60);
                hits.add(new Hit(path, matcher.start(), matcher.end(), line, matcher.start() - lineStart + 1,
                        (start > lineStart ? "…" : "") + text.substring(start, Math.min(end, start + 200))));
                if (hits.size() >= limit) return new Result(List.copyOf(hits), scanned + 1, skipped, false, true);
            }
            scanned++; if ((scanned & 127) == 0) progress.accept(scanned);
        }
        return new Result(List.copyOf(hits), scanned, skipped, cancelled.getAsBoolean(), false);
    }
    @FunctionalInterface public interface Reader { String read(String path) throws IOException; }
    public record Hit(String path, int start, int end, int line, int column, String excerpt) {
        @Override public String toString() { return path + ":" + line + ":" + column + "    " + excerpt; }
    }
    public record Result(List<Hit> hits, int scanned, int skipped, boolean cancelled, boolean limited) {}
}
