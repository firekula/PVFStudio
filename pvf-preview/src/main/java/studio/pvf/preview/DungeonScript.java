package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** ACT1 DGN layout and native CNPathGate configuration. No filesystem access. */
public final class DungeonScript {
    private DungeonScript() {}
    public enum Direction {
        LEFT("左", -1, 0, 4), RIGHT("右", 1, 0, 1), UP("上", 0, -1, 2), DOWN("下", 0, 1, 8);
        public final String label; public final int dx, dy, bit;
        Direction(String label, int dx, int dy, int bit) { this.label = label; this.dx = dx; this.dy = dy; this.bit = bit; }
        public Direction opposite() { return values()[ordinal() ^ 1]; }
    }
    public record Position(int x, int y) { @Override public String toString() { return "(" + x + "," + y + ")"; } }
    public record Maze(int width, int height, List<Integer> masks, List<Position> starts, List<Position> bosses,
                       Map<Position, List<Integer>> maps, Map<Position, List<Integer>> bossMaps) {
        public int mask(Position p) { return p.x >= 0 && p.y >= 0 && p.x < width && p.y < height ? masks.get(p.x + p.y * width) : 0; }
        public Position neighbor(Position p, Direction d) { return new Position(p.x + d.dx, p.y + d.dy); }
        public boolean connected(Position p, Direction d) { return (mask(p) & d.bit) != 0 && (mask(neighbor(p, d)) & d.opposite().bit) != 0; }
        @Override public String toString() { return width + "×" + height; }
    }
    public record Definition(List<Maze> mazes, List<Integer> gateObjects, boolean bossCondition) {}
    public record MapInfo(List<Integer> dungeons, String type, Map<Integer, Integer> shapes, List<Position> gates) {
        public boolean supports(int mask) { return shapes.isEmpty() || shapes.containsKey(mask); }
    }
    public record GatePart(String path, int state, int variation) {}
    public record GateObject(String layer, List<GatePart> parts) {}

    public static Definition parse(String text) throws IOException {
        List<Maze> mazes = new ArrayList<>(); List<Integer> gates = Collections.nCopies(12, -1);
        Builder maze = null; boolean bossCondition = false; int fixedMap = -1;
        for (var section : ResourceScript.sections(text)) {
            var v = section.values();
            switch (section.tag()) {
                case "pathgate object" -> {
                    var ids = ints(v); if (ids.size() != 10) throw error(section, "需要左/右各 3 个、上/下各 2 个门对象编号");
                    gates = List.of(ids.get(0), ids.get(1), ids.get(2), ids.get(3), ids.get(4), ids.get(5),
                            ids.get(6), ids.get(7), -1, ids.get(8), ids.get(9), -1);
                }
                case "boss room entrance condition" -> bossCondition = !v.isEmpty();
                case "warroom map index" -> fixedMap = integer(v, 0);
                case "maze info" -> {
                    if (maze != null) mazes.add(maze.build());
                    if (mazes.size() >= 256) throw error(section, "布局超过 256 个");
                    maze = new Builder();
                }
                case "size", "greed", "start map", "boss map", "map specification", "boss map specification" -> {
                    if (maze == null) continue;
                    switch (section.tag()) {
                        case "size" -> { maze.width = integer(v, 0); maze.height = integer(v, 1); }
                        case "greed" -> maze.masks = codes(v);
                        case "start map" -> maze.starts = positions(v);
                        case "boss map" -> maze.bosses = positions(v).stream().filter(p -> p.x != -1 || p.y != -1).toList();
                        default -> {
                            if (v.size() < 3) throw error(section, "需要房间坐标和 MAP 编号列表");
                            (section.tag().equals("map specification") ? maze.maps : maze.bossMaps)
                                    .put(new Position(integer(v, 0), integer(v, 1)), ints(v.subList(2, v.size())));
                        }
                    }
                }
                default -> { }
            }
        }
        if (maze != null) mazes.add(maze.build());
        if (mazes.isEmpty() && fixedMap >= 0) mazes.add(new Maze(1, 1, List.of(0), List.of(new Position(0, 0)), List.of(),
                Map.of(new Position(0, 0), List.of(fixedMap)), Map.of()));
        if (mazes.isEmpty()) throw new IOException("DGN 没有 [maze info] 布局或 [warroom map index]");
        return new Definition(List.copyOf(mazes), List.copyOf(gates), bossCondition);
    }

    public static MapInfo map(String text) throws IOException {
        List<Integer> dungeons = List.of(); String type = "normal"; Map<Integer, Integer> shapes = new LinkedHashMap<>();
        List<Position> gates = List.of();
        for (var section : ResourceScript.sections(text)) switch (section.tag()) {
            case "dungeon" -> dungeons = ints(section.values());
            case "type" -> { if (!section.values().isEmpty()) type = SceneScript.flag(section.values().get(0)); }
            case "greed" -> {
                var codes = codes(section.values()); if (codes.size() % 2 != 0) throw error(section, "MAP greed 需要形状/入口成对配置");
                for (int i = 0; i < codes.size(); i += 2) shapes.put(codes.get(i), codes.get(i + 1));
            }
            case "pathgate pos" -> { gates = positions(section.values()); if (gates.size() != 4) throw error(section, "门位置须为左、右、上、下四组坐标"); }
            default -> { }
        }
        return new MapInfo(dungeons, type, Map.copyOf(shapes), gates);
    }

    public static GateObject gateObject(String text) throws IOException {
        String layer = "normal"; List<String> paths = List.of(); List<Integer> data = List.of();
        for (var section : ResourceScript.sections(text)) switch (section.tag()) {
            case "layer" -> { if (!section.values().isEmpty()) layer = SceneScript.flag(section.values().get(0)); }
            case "string data" -> paths = section.values();
            case "int data" -> data = ints(section.values());
            default -> { }
        }
        if (paths.size() > 256 || data.size() != paths.size() * 2) throw new IOException("门对象 string data 与 int data 须逐项对应");
        List<GatePart> parts = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i); int state = data.get(i * 2), variation = data.get(i * 2 + 1);
            if (!path.toLowerCase(Locale.ROOT).endsWith(".til") && (state < 0 || state > 2 || variation < -1))
                throw new IOException("门动画类型须为 0/1/2，variation 须 >= -1");
            parts.add(new GatePart(path, state, variation));
        }
        return new GateObject(layer, List.copyOf(parts));
    }

    // 0x725CE0 / 0x748D40 compare native two-byte shape codes; A..P encode a four-bit mask.
    static List<Integer> codes(List<String> values) throws IOException {
        // Binary-script text projects embedded whitespace as escapes inside the greed string.
        // Interpret only separators here; shape characters and unknown escapes remain validated below.
        String raw = String.join("", values).replace("\\r", "\r").replace("\\n", "\n").replace("\\t", "\t").replaceAll("\\s+", "");
        List<Integer> result = new ArrayList<>();
        // DNF_Single DNF.exe: table 0x594278, comparisons at 0x483278/0x48539C.
        // The sixteen glyphs map directly to the same four direction bits as AA..PP.
        String legacy = "┼┾╀╄┽┿╃╇╁╆╂╊╅╈╉╋";
        for (int i = 0; i < raw.length();) {
            char c = raw.charAt(i);
            int mask = legacy.indexOf(c);
            if (mask >= 0) { result.add(mask); i++; continue; }
            if (c < 'A' || c > 'P' || i + 1 == raw.length() || raw.charAt(i + 1) != c)
                throw new IOException("无法识别 greed 形状；支持 AA～PP 及旧版韩文框线形状码：" + raw);
            result.add(c - 'A'); i += 2;
        }
        return List.copyOf(result);
    }
    static List<Integer> ints(List<String> values) throws IOException {
        List<Integer> result = new ArrayList<>(); for (int i = 0; i < values.size(); i++) result.add(integer(values, i)); return List.copyOf(result);
    }
    static int integer(List<String> values, int i) throws IOException { return EntityScript.integer(values, i); }
    private static List<Position> positions(List<String> values) throws IOException {
        if (values.size() % 2 != 0) throw new IOException("区域坐标需要 x/y 成对配置");
        List<Position> result = new ArrayList<>(); for (int i = 0; i < values.size(); i += 2) result.add(new Position(integer(values, i), integer(values, i + 1)));
        return List.copyOf(result);
    }
    private static IOException error(ResourceScript.Section s, String message) { return new IOException("第 " + s.line() + " 行 [" + s.tag() + "]：" + message); }
    private static final class Builder {
        int width, height; List<Integer> masks = List.of(); List<Position> starts = List.of(), bosses = List.of();
        Map<Position, List<Integer>> maps = new LinkedHashMap<>(), bossMaps = new LinkedHashMap<>();
        Maze build() throws IOException {
            if (width < 1 || width > 50 || height < 1 || height > 50 || masks.size() > width * height)
                throw new IOException("布局 size 须为 1～50，greed 房间数不能超过宽×高");
            var padded = new ArrayList<>(masks); while (padded.size() < width * height) padded.add(0);
            for (var p : starts) check(p); for (var p : bosses) check(p); for (var p : maps.keySet()) check(p); for (var p : bossMaps.keySet()) check(p);
            return new Maze(width, height, List.copyOf(padded), starts, bosses, Map.copyOf(maps), Map.copyOf(bossMaps));
        }
        private void check(Position p) throws IOException {
            if (p.x < 0 || p.y < 0 || p.x >= width || p.y >= height) throw new IOException("布局中的坐标越界：" + p);
        }
    }
}
