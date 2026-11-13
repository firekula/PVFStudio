package studio.pvf.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/** The CLI response uses only JSON objects, arrays and scalar values. */
final class CliJson {
    private CliJson() {}

    static Map<String, Object> object(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    static String encode(Object value) {
        var out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        if ((value instanceof Double number && !Double.isFinite(number))
                || (value instanceof Float single && !Float.isFinite(single))) {
            out.append("null");
        } else if (value == null || value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) out.append(',');
                quote(out, (String) entry.getKey());
                out.append(':');
                append(out, entry.getValue());
                first = false;
            }
            out.append('}');
        } else if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) out.append(',');
                append(out, item);
                first = false;
            }
            out.append(']');
        } else {
            quote(out, (String) value);
        }
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 32 || Character.isSurrogate(ch)) {
                        out.append("\\u");
                        for (int shift = 12; shift >= 0; shift -= 4)
                            out.append(Character.forDigit((ch >> shift) & 15, 16));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }
}
