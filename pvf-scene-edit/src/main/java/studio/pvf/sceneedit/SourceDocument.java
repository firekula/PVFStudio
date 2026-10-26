package studio.pvf.sceneedit;

import studio.pvf.preview.ScriptDialect;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Lossless source offsets. Whitespace/comments remain in the source, never in a regenerated AST. */
public final class SourceDocument {
    public record Range(int start, int end) {
        public Range { if (start < 0 || end < start) throw new IllegalArgumentException("无效源范围"); }
        public boolean intersects(Range other) { return start < other.end && other.start < end; }
    }
    public enum Kind { TAG, STRING, ATOM }
    public record Token(Kind kind, String raw, Range range) {
        public String value() {
            if (kind == Kind.TAG) return raw.substring(1, raw.length() - 1);
            if (kind == Kind.STRING) {
                char delimiter = raw.charAt(0);
                return raw.substring(1, raw.length() > 1 && raw.charAt(raw.length() - 1) == delimiter ? raw.length() - 1 : raw.length());
            }
            return raw;
        }
    }
    public record Section(String tag, int occurrence, Token heading, List<Token> values, int end) {
        public Section { values = List.copyOf(values); }
        public Range range() { return new Range(heading.range.start, end); }
        public Range bodyRange() { return new Range(heading.range.end, end); }
    }
    public record Diagnostic(Range range, String message) {}

    private final String text;
    private final List<Token> tokens;
    private final List<Section> sections;
    private final List<Diagnostic> diagnostics;

    private SourceDocument(String text, List<Token> tokens, List<Section> sections, List<Diagnostic> diagnostics) {
        this.text = text; this.tokens = List.copyOf(tokens); this.sections = List.copyOf(sections);
        this.diagnostics = List.copyOf(diagnostics);
    }

    public static SourceDocument parse(String text) {
        Objects.requireNonNull(text);
        List<Token> tokens = new ArrayList<>(); List<Diagnostic> diagnostics = new ArrayList<>();
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '#' || text.startsWith("//", i)) {
                while (i < text.length() && text.charAt(i) != '\r' && text.charAt(i) != '\n') i++;
                continue;
            }
            if (text.startsWith("/*", i)) {
                int end = text.indexOf("*/", i + 2);
                if (end < 0) { diagnostics.add(new Diagnostic(new Range(i, text.length()), "块注释尚未结束")); break; }
                i = end + 2; continue;
            }
            int start = i++;
            Kind kind = Kind.ATOM;
            if (c == '`' || c == '"') {
                kind = Kind.STRING; boolean closed = false;
                while (i < text.length()) {
                    char next = text.charAt(i++);
                    if (next == '\\' && i < text.length()) i++;
                    else if (next == c) { closed = true; break; }
                }
                if (!closed) diagnostics.add(new Diagnostic(new Range(start, i), "字符串尚未结束"));
            } else if (c == '[') {
                boolean closed = false;
                while (i < text.length() && text.charAt(i) != '\r' && text.charAt(i) != '\n') {
                    if (text.charAt(i++) == ']') { closed = true; break; }
                }
                if (closed) kind = Kind.TAG;
                else diagnostics.add(new Diagnostic(new Range(start, i), "标签尚未结束"));
            } else {
                while (i < text.length() && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '['
                        && !text.startsWith("//", i) && !text.startsWith("/*", i)) i++;
            }
            tokens.add(new Token(kind, text.substring(start, i), new Range(start, i)));
        }
        List<Section> sections = new ArrayList<>(); Map<String, Integer> occurrences = new HashMap<>();
        Token heading = null; String tag = ""; int occurrence = 0; List<Token> values = new ArrayList<>();
        for (Token token : tokens) {
            if (token.kind == Kind.TAG) {
                if (heading != null) sections.add(new Section(tag, occurrence, heading, values, token.range.start));
                heading = token; tag = ScriptDialect.tag(token.value()); occurrence = occurrences.getOrDefault(tag, 0);
                occurrences.put(tag, occurrence + 1); values = new ArrayList<>();
            } else if (heading != null) values.add(token);
        }
        if (heading != null) sections.add(new Section(tag, occurrence, heading, values, text.length()));
        return new SourceDocument(text, tokens, sections, diagnostics);
    }

    public String text() { return text; }
    public List<Token> tokens() { return tokens; }
    public List<Section> sections() { return sections; }
    public List<Section> sections(String tag) {
        String canonical = ScriptDialect.tag(tag);
        return sections.stream().filter(section -> section.tag.equals(canonical)).toList();
    }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    public String slice(Range range) { return text.substring(range.start, range.end); }
    public boolean safe(Range range) {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.range.intersects(range)
                || range.start == range.end && diagnostic.range.start <= range.start && range.start <= diagnostic.range.end);
    }
    public ScriptEdit replace(String label, Range range, String value) throws IOException {
        if (!safe(range)) throw new IOException("该片段尚未完整解析，不能从旧画面写入");
        return new ScriptEdit(label, text, List.of(replacement(range, value)));
    }
    public ScriptEdit.Replacement replacement(Range range, String value) {
        return new ScriptEdit.Replacement(range.start, range.end, slice(range), value);
    }
    public String newline() { return text.contains("\r\n") ? "\r\n" : text.indexOf('\n') >= 0 ? "\n" : text.indexOf('\r') >= 0 ? "\r" : "\n"; }
}
