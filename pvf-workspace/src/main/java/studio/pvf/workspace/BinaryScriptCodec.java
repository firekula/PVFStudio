package studio.pvf.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reversible B0 D0 script projection. Unknown token types remain explicit, lossless @type(value) tokens. */
public final class BinaryScriptCodec {
    private BinaryScriptCodec() {}
    public static boolean isScript(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == (byte) 0xb0 && bytes[1] == (byte) 0xd0;
    }

    public static final class StringTable {
        private final byte[] bytes;
        private final int[] offsets;
        private final Charset charset;
        private final Map<Integer, String> decoded = new HashMap<>();
        private Map<String, Integer> indices;

        public StringTable(byte[] bytes, Charset charset) throws IOException {
            this.bytes = bytes.clone(); this.charset = charset;
            if (bytes.length < 8) throw new IOException("Truncated stringtable.bin");
            ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int count = data.getInt();
            if (count < 0 || count > (bytes.length - 8) / 4) throw new IOException("Invalid stringtable count");
            offsets = new int[count + 1];
            int previous = (count + 1) * 4;
            for (int i = 0; i <= count; i++) {
                int offset = data.getInt();
                if (offset < previous || offset > bytes.length - 4) throw new IOException("Invalid stringtable offset " + i);
                offsets[i] = offset; previous = offset;
            }
        }

        synchronized String get(int index) throws IOException {
            if (index < 0 || index >= offsets.length - 1) throw new IOException("Invalid PVF string index: " + index);
            String found = decoded.get(index);
            if (found == null) {
                found = TextCodec.decode(Arrays.copyOfRange(bytes, 4 + offsets[index], 4 + offsets[index + 1]), charset);
                decoded.put(index, found);
            }
            return found;
        }

        synchronized int find(String text) throws IOException {
            if (indices == null) {
                indices = new HashMap<>();
                for (int i = 0; i < offsets.length - 1; i++) {
                    try { indices.putIfAbsent(get(i), i); }
                    catch (java.nio.charset.CharacterCodingException invalid) { /* Preserve undecodable strings verbatim. */ }
                }
            }
            return indices.getOrDefault(text, -1);
        }

        byte[] append(Map<String, Integer> additions) throws IOException {
            if (additions.isEmpty()) return bytes.clone();
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            List<Integer> ends = new ArrayList<>();
            int end = offsets[offsets.length - 1] + additions.size() * 4;
            for (String value : additions.keySet()) {
                byte[] encoded = TextCodec.encode(value, charset); content.writeBytes(encoded);
                end = Math.addExact(end, encoded.length); ends.add(end);
            }
            ByteBuffer result = ByteBuffer.allocate(Math.addExact(bytes.length, additions.size() * 4 + content.size()))
                    .order(ByteOrder.LITTLE_ENDIAN);
            result.putInt(offsets.length - 1 + additions.size());
            for (int offset : offsets) result.putInt(offset + additions.size() * 4);
            for (int offset : ends) result.putInt(offset);
            // Include opaque bytes before the first string, and retain any trailing bytes.
            int oldIndexEnd = 4 + offsets.length * 4;
            int oldStringEnd = 4 + offsets[offsets.length - 1];
            result.put(bytes, oldIndexEnd, oldStringEnd - oldIndexEnd);
            result.put(content.toByteArray());
            result.put(bytes, oldStringEnd, bytes.length - oldStringEnd);
            return result.array();
        }
    }

    private record Token(int type, int value, String text) {}
    private static List<Token> tokens(byte[] bytes, StringTable strings) throws IOException {
        if (!isScript(bytes) || (bytes.length - 2) % 5 != 0) throw new IOException("Invalid B0 D0 script length");
        List<Token> tokens = new ArrayList<>();
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); data.position(2);
        while (data.hasRemaining()) {
            int type = Byte.toUnsignedInt(data.get()); int value = data.getInt();
            String text;
            if (type == 2) text = Integer.toString(value);
            else if (type == 4 && Float.isFinite(Float.intBitsToFloat(value))) text = Float.toString(Float.intBitsToFloat(value)) + "f";
            else if (type == 5) {
                String s = strings.get(value);
                text = s.startsWith("[") && s.endsWith("]") && s.indexOf('\n') < 0 && s.indexOf('\r') < 0
                        && s.indexOf(']') == s.length() - 1 ? s : "@5(" + value + ")";
            } else if (type == 7) text = quote(strings.get(value));
            else text = "@" + type + "(" + value + ")";
            tokens.add(new Token(type, value, text));
        }
        return tokens;
    }

    public static String decode(byte[] bytes, StringTable strings) throws IOException {
        StringBuilder text = new StringBuilder("#PVF_File\n");
        for (Token t : tokens(bytes, strings)) {
            if (t.type == 5) {
                if (text.charAt(text.length() - 1) != '\n') text.append('\n');
                text.append('\n').append(t.text).append('\n');
            } else text.append(t.text).append('\t');
        }
        if (text.charAt(text.length() - 1) != '\n') text.append('\n');
        return text.toString();
    }

    public record Compiled(byte[] script, byte[] stringTable) {}
    public static Compiled encode(String text, byte[] original, StringTable strings) throws IOException {
        List<Token> before = tokens(original, strings);
        List<String> lexemes = lex(text);
        ByteArrayOutputStream result = new ByteArrayOutputStream(); result.write(0xb0); result.write(0xd0);
        Map<String, Integer> additions = new LinkedHashMap<>();
        for (int i = 0; i < lexemes.size(); i++) {
            String s = lexemes.get(i); int type, value;
            if (i < before.size() && before.get(i).text.equals(s)) {
                type = before.get(i).type; value = before.get(i).value;
            } else try {
                if (s.startsWith("@")) {
                    if (!s.endsWith(")")) throw new IllegalArgumentException();
                    int open = s.indexOf('('); type = Integer.parseInt(s.substring(1, open));
                    value = Integer.parseInt(s.substring(open + 1, s.length() - 1));
                    if (type < 0 || type > 255) throw new IllegalArgumentException();
                    if (type == 5 || type == 7) strings.get(value);
                } else if (s.startsWith("[") || s.startsWith("`")) {
                    type = s.startsWith("[") ? 5 : 7;
                    String valueText = type == 5 ? s : unquote(s);
                    value = strings.find(valueText);
                    if (value < 0) {
                        Integer added = additions.get(valueText);
                        if (added == null) { added = strings.offsets.length - 1 + additions.size(); additions.put(valueText, added); }
                        value = added;
                    }
                } else if (s.endsWith("f")) {
                    type = 4; float f = Float.parseFloat(s.substring(0, s.length() - 1));
                    if (!Float.isFinite(f)) throw new IllegalArgumentException();
                    value = Float.floatToRawIntBits(f);
                } else { type = 2; value = Integer.parseInt(s); }
            } catch (RuntimeException bad) { throw new IOException("无法编译二进制脚本词项 " + (i + 1) + "：" + s, bad); }
            result.write(type); result.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
        }
        return new Compiled(result.toByteArray(), strings.append(additions));
    }

    static List<String> lex(String text) throws IOException {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '#' || (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/')) {
                int end = text.indexOf('\n', i); i = end < 0 ? text.length() : end + 1; continue;
            }
            int start = i++;
            if (c == '[' || c == '`') {
                char end = c == '[' ? ']' : '`'; boolean closed = false;
                while (i < text.length()) {
                    char ch = text.charAt(i++);
                    if (c == '`' && ch == '\\') { if (i < text.length()) i++; else throw new IOException("Unterminated escape"); }
                    else if (ch == end) { closed = true; break; }
                }
                if (!closed) throw new IOException("Unterminated script tag/string");
            } else while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
            result.add(text.substring(start, i));
        }
        return result;
    }

    static String quote(String s) {
        return "`" + s.replace("\\", "\\\\").replace("`", "\\`").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t").replace("\0", "\\0") + "`";
    }
    static String unquote(String s) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                if (++i >= s.length() - 1) throw new IOException("Unterminated string escape");
                c = switch (s.charAt(i)) { case 'r' -> '\r'; case 'n' -> '\n'; case 't' -> '\t'; case '0' -> '\0';
                    case '\\' -> '\\'; case '`' -> '`'; default -> throw new IOException("Unknown string escape"); };
            }
            result.append(c);
        }
        return result.toString();
    }
}
