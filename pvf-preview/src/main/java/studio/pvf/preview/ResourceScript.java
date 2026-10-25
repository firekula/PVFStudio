package studio.pvf.preview;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small ACT1 text lexer: quoted tags and comments never become configuration tags. */
public final class ResourceScript {
    private static final Pattern TOKENS = Pattern.compile("//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|`[^`]*`|\"[^\"]*\"|\\[[^\\]\\r\\n]+\\]|[^\\s\\[\"`]+");
    private static final Pattern FRAME = Pattern.compile("frame\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private ResourceScript() {}

    static List<Section> sections(String text) {
        List<Section> sections = new ArrayList<>(); String tag = ""; int line = 1, previous = 0, tagLine = 1;
        List<String> values = new ArrayList<>(); List<Integer> lines = new ArrayList<>(); Matcher matcher = TOKENS.matcher(text);
        while (matcher.find()) {
            for (int i = previous; i < matcher.start(); i++) {
                char c = text.charAt(i);
                if (c == '\n' || c == '\r' && (i + 1 == text.length() || text.charAt(i + 1) != '\n')) line++;
            }
            String token = matcher.group(); previous = matcher.start();
            if (token.startsWith("//") || token.startsWith("/*")) continue;
            if (token.startsWith("[")) {
                if (!tag.isEmpty()) sections.add(new Section(tag, List.copyOf(values), tagLine, List.copyOf(lines)));
                tag = token.substring(1, token.length() - 1).trim().toLowerCase(Locale.ROOT); values.clear(); lines.clear(); tagLine = line;
            } else {
                if (token.startsWith("`") || token.startsWith("\"")) token = token.substring(1, token.length() - 1);
                values.add(token);
                lines.add(line);
            }
        }
        if (!tag.isEmpty()) sections.add(new Section(tag, List.copyOf(values), tagLine, List.copyOf(lines)));
        return sections;
    }

    static List<String> listTokens(String text) {
        List<String> values = new ArrayList<>(); Matcher matcher = TOKENS.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (value.startsWith("//") || value.startsWith("/*")) continue;
            if (value.startsWith("`") || value.startsWith("\"")) value = value.substring(1, value.length() - 1);
            values.add(value);
        }
        return values;
    }

    public static List<Reference> references(String text) throws IOException {
        List<Reference> result = new ArrayList<>();
        for (Section section : sections(text)) {
            for (int i = 0; i < section.values().size(); i++) {
                String path = section.values().get(i), lower = path.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".img")) {
                    if (i + 1 >= section.values().size()) continue;
                    try { result.add(new Reference(section.tag(), path, Integer.parseInt(section.values().get(i + 1)), section.line())); }
                    catch (NumberFormatException ignored) { /* E.g. a filename list without frame indexes. */ }
                } else if (lower.endsWith(".ani") || lower.endsWith(".ptl")) result.add(new Reference(section.tag(), path, -1, section.line()));
                if (result.size() > 4096) throw new IOException("配置中的资源引用超过 4096 个");
            }
        }
        return List.copyOf(result);
    }

    public static Animation animation(String text) throws IOException {
        List<Frame> frames = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        Builder frame = null; int declared = -1; boolean loop = false;
        for (Section section : sections(text)) {
            Matcher number = FRAME.matcher(section.tag());
            if (number.matches()) {
                if (frame != null) frames.add(frame.build());
                int index;
                try { index = Integer.parseInt(number.group(1)); }
                catch (NumberFormatException failure) { throw error(section, "帧号无效"); }
                if (index != frames.size()) warnings.add("第 " + section.line() + " 行：帧标签编号不连续，预览按文件顺序排列");
                if (frames.size() >= 4096) throw error(section, "动画超过 4096 帧");
                frame = new Builder(section.line()); continue;
            }
            switch (section.tag()) {
                case "loop" -> loop = integer(section, 0) != 0;
                case "frame max" -> declared = integer(section, 0);
                default -> {
                    if (frame == null) continue;
                    switch (section.tag()) {
                        case "image" -> {
                            frame.image = value(section, 0); frame.index = integer(section, 1);
                            if (!frame.image.isEmpty() && frame.index < 0) throw error(section, "IMG 帧号不能为负数");
                        }
                        case "image pos" -> { frame.x = number(section, 0); frame.y = number(section, 1); }
                        case "image rate" -> { frame.scaleX = number(section, 0); frame.scaleY = number(section, 1); }
                        case "image rotate" -> frame.rotation = number(section, 0);
                        case "rgba" -> {
                            frame.r = color(section, 0); frame.g = color(section, 1);
                            frame.b = color(section, 2); frame.a = color(section, 3);
                        }
                        case "delay" -> {
                            frame.delay = integer(section, 0);
                            if (frame.delay < 0) throw error(section, "DELAY 不能为负数");
                        }
                        case "interpolation" -> frame.interpolation = number(section, 0) != 0;
                        case "graphic effect" -> {
                            frame.effect = value(section, 0).toUpperCase(Locale.ROOT);
                            if (!frame.effect.equals("NONE") && !frame.effect.equals("LINEARDODGE"))
                                warnings.add("第 " + section.line() + " 行：暂未模拟 GRAPHIC EFFECT " + value(section, 0));
                        }
                        default -> { /* Gameplay, sound and collision tags do not change the sprite. */ }
                    }
                }
            }
        }
        if (frame != null) frames.add(frame.build());
        if (frames.isEmpty() && declared != 0) throw new IOException("未找到 [FRAME000] 动画帧");
        if (declared >= 0 && declared != frames.size()) warnings.add("FRAME MAX 为 " + declared
                + "，实际配置了 " + frames.size() + " 帧；预览按实际帧排列");
        return new Animation(loop, List.copyOf(frames), List.copyOf(warnings));
    }

    /** Virtual PVF candidates: file-relative first, then root-relative. No filesystem access. */
    public static List<String> animationCandidates(String source, String reference) {
        String normalized = reference.replace('\\', '/').toLowerCase(Locale.ROOT);
        String parent = source.contains("/") ? source.substring(0, source.lastIndexOf('/') + 1) : "";
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        if (!normalized.startsWith("/")) addCandidate(paths, parent + normalized);
        addCandidate(paths, normalized);
        return List.copyOf(paths);
    }

    private static void addCandidate(Set<String> paths, String raw) {
        Deque<String> parts = new ArrayDeque<>();
        for (String part : raw.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (parts.isEmpty()) return; parts.removeLast(); }
            else parts.add(part);
        }
        paths.add(String.join("/", parts));
    }

    private static String value(Section section, int index) throws IOException {
        if (section.values().size() <= index) throw error(section, "参数不完整");
        return section.values().get(index);
    }
    private static int integer(Section section, int index) throws IOException {
        try { return Integer.parseInt(value(section, index)); }
        catch (NumberFormatException failure) { throw error(section, "需要整数参数"); }
    }
    private static double number(Section section, int index) throws IOException {
        try {
            double value = Double.parseDouble(value(section, index));
            if (!Double.isFinite(value) || Math.abs(value) > 32768) throw error(section, "数值超出范围");
            return value;
        } catch (NumberFormatException failure) { throw error(section, "需要数值参数"); }
    }
    private static int color(Section section, int index) throws IOException {
        int value = integer(section, index);
        if (value < 0 || value > 255) throw error(section, "RGBA 应为 0–255");
        return value;
    }
    private static IOException error(Section section, String message) {
        return new IOException("第 " + section.line() + " 行 [" + section.tag() + "]：" + message);
    }

    record Section(String tag, List<String> values, int line, List<Integer> valueLines) {
        List<Row> rows() {
            List<Row> result = new ArrayList<>();
            for (int start = 0; start < values.size();) {
                int end = start + 1;
                while (end < values.size() && valueLines.get(end).equals(valueLines.get(start))) end++;
                result.add(new Row(valueLines.get(start), values.subList(start, end))); start = end;
            }
            return result;
        }
    }
    record Row(int line, List<String> values) {}
    public record Reference(String tag, String path, int index, int line) {
        public boolean animation() { return path.toLowerCase(Locale.ROOT).endsWith(".ani"); }
        public boolean particle() { return path.toLowerCase(Locale.ROOT).endsWith(".ptl"); }
        @Override public String toString() { return "[" + tag + "] " + path + (animation() || particle() ? "" : " #" + index); }
    }
    public record Animation(boolean loop, List<Frame> frames, List<String> warnings) {}
    public record Frame(String image, int index, double x, double y, double scaleX, double scaleY,
                        double rotation, int r, int g, int b, int a, int delay, int line, String effect, boolean interpolation) {
        public Frame(String image, int index, double x, double y, double scaleX, double scaleY,
                     double rotation, int r, int g, int b, int a, int delay, int line, String effect) {
            this(image, index, x, y, scaleX, scaleY, rotation, r, g, b, a, delay, line, effect, false);
        }
        public Frame(String image, int index, double x, double y, double scaleX, double scaleY,
                     double rotation, int r, int g, int b, int a, int delay, int line) {
            this(image, index, x, y, scaleX, scaleY, rotation, r, g, b, a, delay, line, "NONE");
        }
        public static Frame still(String path, int index) { return new Frame(path, index, 0, 0, 1, 1, 0, 255, 255, 255, 255, 100, 1); }
        public Frame withImage(String path) { return new Frame(path, index, x, y, scaleX, scaleY, rotation, r, g, b, a, delay, line, effect, interpolation); }
    }
    private static final class Builder {
        String image = ""; int index, r = 255, g = 255, b = 255, a = 255, delay = 100;
        String effect = "NONE"; boolean interpolation;
        double x, y, scaleX = 1, scaleY = 1, rotation; final int line;
        Builder(int line) { this.line = line; }
        Frame build() { return new Frame(image, index, x, y, scaleX, scaleY, rotation, r, g, b, a, delay, line, effect, interpolation); }
    }
}
