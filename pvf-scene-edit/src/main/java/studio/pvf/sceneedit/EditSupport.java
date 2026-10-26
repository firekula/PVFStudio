package studio.pvf.sceneedit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Common native-source edits; comments and whitespace between value tokens are left intact. */
final class EditSupport {
    private EditSupport() {}
    static String quote(String value) throws IOException {
        if (value.indexOf('`') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0)
            throw new IOException("引用路径或标记含有不支持的字符");
        return "`" + value + "`";
    }
    static String flag(String value) throws IOException { return quote("[" + value + "]"); }
    static String number(double value) throws IOException {
        if (!Double.isFinite(value) || Math.abs(value) > 1_000_000) throw new IOException("坐标须为有限数值，范围 ±1000000");
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
    static String number(double value, SourceDocument.Token original) throws IOException {
        String result = number(value);
        if (original.raw().endsWith("f")) return (result.contains(".") ? result : result + ".0") + "f";
        if (original.raw().matches("[+-]?\\d+") && value != Math.rint(value)) throw new IOException("该坐标字段需要整数");
        return result;
    }
    static int integer(String value) throws IOException {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException failure) { throw new IOException("需要整数：" + value, failure); }
    }
    static double numeric(String value) throws IOException {
        try { double n = Double.parseDouble(value); number(n); return n; }
        catch (NumberFormatException failure) { throw new IOException("需要数值：" + value, failure); }
    }
    static void reference(String path, String extension) throws IOException {
        quote(path);
        if (path.isBlank() || !path.toLowerCase(Locale.ROOT).endsWith(extension)) throw new IOException("需要 " + extension + " 资源路径");
    }
    static List<ScriptEdit.Replacement> values(SourceDocument source, SourceDocument.Section section, List<String> raw) throws IOException {
        if (!source.safe(section.range())) throw new IOException("该区段尚未完整解析");
        List<ScriptEdit.Replacement> result = new ArrayList<>(); int shared = Math.min(raw.size(), section.values().size());
        for (int i = 0; i < shared; i++) {
            var token = section.values().get(i);
            if (!token.raw().equals(raw.get(i))) result.add(source.replacement(token.range(), raw.get(i)));
        }
        for (int i = shared; i < section.values().size(); i++) result.add(source.replacement(section.values().get(i).range(), ""));
        if (raw.size() > shared) {
            int at = section.values().isEmpty() ? section.heading().range().end() : section.values().get(section.values().size() - 1).range().end();
            result.add(source.replacement(new SourceDocument.Range(at, at), " " + String.join(" ", raw.subList(shared, raw.size()))));
        }
        return result;
    }
    static ScriptEdit.Replacement appendRecord(SourceDocument source, String tag, String record) throws IOException {
        var existing = source.sections(tag);
        if (existing.isEmpty()) {
            int at = source.text().length();
            if (!source.safe(new SourceDocument.Range(at, at))) throw new IOException("代码末尾尚未完整解析");
            String nl = source.newline();
            return source.replacement(new SourceDocument.Range(at, at), leadingNewline(source.text(), at, nl)
                    + "[" + tag + "]" + nl + record + nl + "[/" + tag + "]" + nl);
        }
        var section = existing.get(0);
        if (!source.safe(section.range())) throw new IOException("该区段尚未完整解析");
        int at = section.end(); String nl = source.newline();
        return source.replacement(new SourceDocument.Range(at, at), leadingNewline(source.text(), at, nl) + record + nl);
    }
    static String leadingNewline(String text, int at, String nl) {
        return at == 0 || text.charAt(at - 1) == '\n' || text.charAt(at - 1) == '\r' ? "" : nl;
    }
}
