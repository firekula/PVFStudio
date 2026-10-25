package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** ACT1 PTL fields; defaults and units follow 0x7674D0 / 0x433560 / 0x437A20. */
public record ParticleScript(Vec position, List<Axis> axes, double term, String changing, double gravity,
                             Vec angles, long life, String landing, double alphaStart, double alphaEnd,
                             double frequency, int maximum, long createTime, Vec area, Vec areaAngles,
                             String rangeType, double horizontalRange, double verticalRange, double random,
                             boolean inOrder, boolean passive, String layer, List<ObjectRef> objects,
                             List<String> warnings) {
    public record Vec(double x, double y, double z) {
        double at(int i) { return i == 0 ? x : i == 1 ? y : z; }
    }
    public record Axis(String type, double start, double end, double periodStart, double periodEnd) {}
    public record ObjectRef(String path, int index) {}

    public static ParticleScript parse(String text) throws IOException {
        Vec position = new Vec(0, 0, 0), area = position, angles = position, areaAngles = position;
        String[] types = {"stop", "stop", "stop"}; double[][] values = new double[3][4]; int axis = 0;
        double term = 0, gravity = 0, firstAlpha = 100, lastAlpha = 100, frequency = 0, horizontal = 0, vertical = 0, random = 0;
        long life = 0, time = 0; int maximum = 0;
        String changing = "keep changing", landing = "pierce", range = "rectangle", layer = "normal";
        boolean order = false, passive = false; List<ObjectRef> objects = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        for (var s : ResourceScript.sections(text)) {
            switch (s.tag()) {
                case "creation pos" -> position = vector(s);
                case "x axis" -> axis = 0;
                case "y axis" -> axis = 1;
                case "z axis" -> axis = 2;
                case "move type" -> types[axis] = flag(s, "stop", "straight line", "sin", "cos");
                case "move variable 1" -> { values[axis][0] = number(s, 0); values[axis][1] = number(s, 1); }
                case "move variable 2" -> { values[axis][2] = number(s, 0); values[axis][3] = number(s, 1); }
                case "changing term" -> term = nonnegative(s);
                case "changing type" -> changing = flag(s, "keep changing", "keep last value", "ping pong");
                case "gravity" -> gravity = number(s, 0);
                case "rotation angle" -> angles = vector(s);
                case "life time" -> life = (long) nonnegative(s);
                case "landing type" -> landing = flag(s, "pierce", "slide", "stop", "vanish");
                case "transparent value" -> { firstAlpha = bounded(s, 0, 255); lastAlpha = bounded(s, 1, 255); }
                case "creation frequency" -> {
                    frequency = number(s, 0);
                    if (frequency < 0 && frequency != -1) throw error(s, "生成频率须为 -1 或非负值");
                }
                case "maximum create number" -> maximum = (int) nonnegative(s);
                case "maximum create time" -> time = (long) nonnegative(s);
                case "create area size" -> {
                    area = vector(s);
                    if (area.x < 0 || area.y < 0 || area.z < 0) throw error(s, "生成区域大小不能为负数");
                }
                case "create area rotation angle" -> areaAngles = vector(s);
                case "rotation angle range type" -> range = flag(s, "rectangle", "circle");
                case "rotation angle variable" -> { horizontal = nonnegative(s); vertical = Math.max(0, number(s, 1)); }
                case "random range" -> random = bounded(s, 0, 200);
                case "how to create object" -> order = flag(s, "randomize", "in order").equals("in order");
                case "object type" -> passive = flag(s, "animation", "passive object").equals("passive object");
                case "layer" -> layer = flag(s, "close", "normal", "bottom", "closeback", "middleback", "distantback");
                case "object" -> {
                    if (s.values().size() % 2 != 0) throw error(s, "粒子对象须由路径与编号成对组成");
                    for (int i = 0; i < s.values().size(); i += 2) {
                        if (objects.size() >= 256) throw error(s, "粒子对象种类超过 256 个");
                        objects.add(new ObjectRef(s.values().get(i), EntityScript.integer(s.values(), i + 1)));
                    }
                }
                case "create pos correct", "display effect rate" -> { /* Static emitter; preview uses the native high effect setting. */ }
                default -> { if (!s.tag().startsWith("/")) warnings.add("未识别的 PTL 字段 [" + s.tag() + "]"); }
            }
        }
        List<Axis> axes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double[] v = values[i]; axes.add(new Axis(types[i], v[0], v[1], v[2], v[3]));
            if ((types[i].equals("sin") || types[i].equals("cos")) && v[2] <= 0)
                warnings.add("振荡周期非正，[" + "xyz".charAt(i) + " axis] 暂不移动");
        }
        if (frequency == -1 && maximum == 0) warnings.add("瞬时无限粒子按预览上限显示");
        return new ParticleScript(position, List.copyOf(axes), term, changing, gravity, angles, life, landing,
                firstAlpha, lastAlpha, frequency, maximum, time, area, areaAngles, range, horizontal, vertical, random,
                order, passive, layer, List.copyOf(objects), List.copyOf(warnings));
    }

    /** CNParticleFactory (0x57BCA0) optionally expands emission across the map width. */
    public ParticleScript fillWidth(double width) {
        int factor = area.x > 0 ? Math.max(1, (int) (width / area.x)) : 1;
        return new ParticleScript(position, axes, term, changing, gravity, angles, life, landing, alphaStart, alphaEnd,
                frequency < 0 ? frequency : frequency * factor, maximum == 0 ? 0 : (int) Math.min(1_000_000L, (long) maximum * factor),
                createTime, new Vec(width, area.y, area.z), areaAngles, rangeType, horizontalRange, verticalRange,
                random, inOrder, passive, layer, objects, warnings);
    }
    private static Vec vector(ResourceScript.Section s) throws IOException { return new Vec(number(s, 0), number(s, 1), number(s, 2)); }
    private static double bounded(ResourceScript.Section s, int i, int max) throws IOException {
        double n = number(s, i); if (n < 0 || n > max) throw error(s, "数值须在 0–" + max + " 之间"); return n;
    }
    private static double nonnegative(ResourceScript.Section s) throws IOException {
        double n = number(s, 0); if (n < 0) throw error(s, "数值不能为负数"); return n;
    }
    private static double number(ResourceScript.Section s, int i) throws IOException {
        try {
            double n = Double.parseDouble(s.values().get(i));
            if (!Double.isFinite(n) || n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) throw error(s, "数值超出范围"); return n;
        } catch (IndexOutOfBoundsException | NumberFormatException failure) { throw error(s, "需要数值参数"); }
    }
    private static String flag(ResourceScript.Section s, String... accepted) throws IOException {
        if (s.values().isEmpty()) throw error(s, "缺少参数");
        String flag = SceneScript.flag(s.values().get(0));
        if (!List.of(accepted).contains(flag)) throw error(s, "未知类型 " + flag); return flag;
    }
    private static IOException error(ResourceScript.Section s, String message) { return new IOException("第 " + s.line() + " 行 [" + s.tag() + "]：" + message); }
}
