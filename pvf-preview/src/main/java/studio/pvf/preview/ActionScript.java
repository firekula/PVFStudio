package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** Initial ACT MOTION only. Trigger/behavior instructions never run in the preview. */
public final class ActionScript {
    private ActionScript() {}
    public record Part(String path, int startFrame, int layer, int x, int y, int z) {}
    public record Motion(String base, List<Part> parts, List<String> warnings) {}
    public static Motion motion(String text) throws IOException {
        String base = ""; List<Part> parts = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        boolean inside = false, seen = false;
        for (var section : ResourceScript.sections(text)) {
            String tag = section.tag(); var values = section.values();
            if (tag.equals("motion")) {
                if (seen) throw new IOException("ACT MOTION 不能嵌套");
                inside = true; seen = true; continue;
            }
            if (tag.equals("/motion")) { inside = false; break; }
            if (!inside) continue;
            if (tag.equals("base ani")) {
                if (values.size() != 1) throw new IOException("ACT BASE ANI 需要一个路径");
                if (!base.isEmpty()) throw new IOException("ACT MOTION 含重复 BASE ANI");
                base = values.get(0); checkPath(base);
            } else if (List.of("sub ani", "sub ani with xy", "sub ani with xyz").contains(tag)) {
                int stride = tag.equals("sub ani") ? 3 : tag.equals("sub ani with xy") ? 5 : 6;
                if (values.size() % stride != 0) { warnings.add("ACT [" + tag + "] 参数数量不完整，未加载该组子动画"); continue; }
                for (int i = 0; i < values.size(); i += stride) {
                    if (parts.size() >= 256) throw new IOException("ACT 子动画超过预览上限 256");
                    try {
                        String path = values.get(i); checkPath(path);
                        int start = EntityScript.integer(values, i + 1), layer = EntityScript.integer(values, i + 2);
                        if (start < 0) throw new IOException("ACT 子动画起始帧不能为负数");
                        parts.add(new Part(path, start, layer, stride >= 5 ? EntityScript.integer(values, i + 3) : 0,
                                stride >= 5 ? EntityScript.integer(values, i + 4) : 0, stride == 6 ? EntityScript.integer(values, i + 5) : 0));
                    } catch (IOException failure) { warnings.add("ACT 子动画：" + failure.getMessage()); }
                }
            }
        }
        if (inside) throw new IOException("ACT MOTION 缺少结束标签");
        if (!seen) throw new IOException("ACT 没有初始 MOTION");
        return new Motion(base, List.copyOf(parts), List.copyOf(warnings));
    }
    private static void checkPath(String path) throws IOException {
        if (!path.isEmpty() && !path.toLowerCase(Locale.ROOT).endsWith(".ani")) throw new IOException("ACT MOTION 需要 ANI 路径：" + path);
    }
}
