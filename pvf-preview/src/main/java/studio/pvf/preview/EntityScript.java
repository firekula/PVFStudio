package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** Explicit placements, initial visuals, and native particle-factory parameters; never executes AI or drops. */
public final class EntityScript {
    private EntityScript() {}
    public enum Kind {
        NPC("NPC", "npc/npc.lst", "field animation"),
        MONSTER("怪物", "monster/monster.lst", "waiting motion"),
        PASSIVE("被动对象", "passiveobject/passiveobject.lst", "basic motion");
        private final String label, list, motion;
        Kind(String label, String list, String motion) { this.label = label; this.list = list; this.motion = motion; }
        public String label() { return label; }
        public String list() { return list; }
        public String motion() { return motion; }
    }

    public static Layout placements(String text) {
        List<Spawn> spawns = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        for (var section : ResourceScript.sections(text)) {
            Kind kind = switch (section.tag()) {
                case "npc" -> Kind.NPC;
                case "monster", "monster condition" -> Kind.MONSTER;
                case "passive object", "special passive object" -> Kind.PASSIVE;
                default -> null;
            };
            if (kind == null) continue;
            boolean special = section.tag().equals("special passive object");
            List<ResourceScript.Row> rows = section.rows();
            for (int i = 0; i < rows.size(); i++) {
                if (spawns.size() >= 8192) { warnings.add("实体超过 8192 个，后续未加载"); return new Layout(List.copyOf(spawns), List.copyOf(warnings)); }
                var row = rows.get(i); var fields = row.values();
                try {
                    int expected = kind == Kind.MONSTER ? 10 : kind == Kind.NPC ? 4 : special ? 3 : 4;
                    if (fields.size() < expected) throw new IOException("实体坐标参数不完整");
                    int id = integer(fields, 0);
                    int offset = kind == Kind.MONSTER ? 3 : kind == Kind.NPC ? 2 : 1;
                    double x = number(fields, offset), y = number(fields, offset + 1);
                    double z = fields.size() > offset + 2 ? number(fields, offset + 2) : 0;
                    String facing = kind == Kind.NPC ? SceneScript.flag(fields.get(1)) : "right";
                    if (!facing.equals("left") && !facing.equals("right")) throw new IOException("未知朝向：" + facing);
                    spawns.add(new Spawn(kind, id, x, y, z, facing.equals("left"),
                            section.tag().equals("monster condition"), special, row.line()));
                    if (special) {
                        // The following count/payload rows describe the container's contents, not extra map spawns.
                        int count;
                        if (fields.size() >= 5) count = integer(fields, 4);
                        else {
                            if (i + 1 >= rows.size() || rows.get(i + 1).values().size() != 1)
                                throw new IOException("特殊被动对象缺少内容数量；仅显示容器待机");
                            count = integer(rows.get(++i).values(), 0);
                        }
                        if (count < 0 || count > 4096) throw new IOException("特殊被动对象内容数量无效");
                        for (int item = 0; item < count; item++) {
                            if (i + 1 >= rows.size()) throw new IOException("特殊被动对象内容不足");
                            var next = rows.get(i + 1).values(); String flag = SceneScript.flag(next.get(0));
                            if (!List.of("item", "monster", "trap", "quest").contains(flag))
                                throw new IOException("特殊被动对象内容格式不符");
                            i++;
                        }
                    }
                } catch (IOException failure) { warnings.add("第 " + row.line() + " 行 [" + section.tag() + "]：" + failure.getMessage()); }
            }
        }
        return new Layout(List.copyOf(spawns), List.copyOf(warnings));
    }

    public static Map<Integer, String> catalog(String text) throws IOException {
        List<String> tokens = ResourceScript.listTokens(text);
        if (!tokens.isEmpty() && tokens.get(0).equalsIgnoreCase("#PVF_File")) tokens = tokens.subList(1, tokens.size());
        if (tokens.size() % 2 != 0) throw new IOException("索引列表须由编号与文件路径成对组成");
        Map<Integer, String> result = new LinkedHashMap<>();
        for (int i = 0; i < tokens.size(); i += 2) {
            int id = integer(tokens, i); String path = tokens.get(i + 1);
            if (path.isBlank()) throw new IOException("索引 " + id + " 的文件路径为空");
            if (result.putIfAbsent(id, path) != null) throw new IOException("重复的实体索引：" + id);
        }
        return Map.copyOf(result);
    }

    public static Definition definition(String text, Kind kind) throws IOException {
        String idle = "", layer = "normal"; List<String> equipment = new ArrayList<>(), effects = new ArrayList<>();
        double width = 0, depth = 0; int pass = 0;
        boolean found = false;
        for (var section : ResourceScript.sections(text)) {
            if (kind == Kind.PASSIVE && section.tag().equals("width")) {
                width = number(section.values(), 0); depth = number(section.values(), 1);
                if (width < 0 || depth < 0) throw new IOException("对象碰撞宽度不能为负数");
            } else if (kind == Kind.PASSIVE && section.tag().equals("pass type") && !section.values().isEmpty()) {
                pass = switch (SceneScript.flag(section.values().get(0))) {
                    case "pass all" -> 0; case "pass only float type" -> 1; case "do not pass" -> 2;
                    default -> -1; // Preserve visuals when the original package contains an unresolved/localized string link.
                };
            } else if (section.tag().equals(kind.motion()) && !found) {
                if (!section.values().isEmpty()) idle = section.values().get(0); found = true;
            } else if (section.tag().equals("layer") && !section.values().isEmpty()) layer = SceneScript.flag(section.values().get(0));
            else if (kind == Kind.MONSTER && section.tag().equals("equipment")) {
                int count = integer(section.values(), 0);
                if (count < 0 || count > 128 || section.values().size() != count + 1) throw new IOException("怪物装备数量与列表不符");
                equipment.addAll(section.values().subList(1, section.values().size()));
            } else if (kind == Kind.PASSIVE && section.tag().equals("add object effect")) {
                int count = integer(section.values(), 0);
                if (count < 0 || count > 128 || section.values().size() != 1 + count * 2) throw new IOException("对象附加动画数量与列表不符");
                effects.addAll(section.values().subList(1, section.values().size()));
            }
        }
        if (!List.of("distantback", "middleback", "closeback", "bottom", "normal", "close").contains(layer))
            throw new IOException("未知实体绘制层：" + layer);
        return new Definition(idle, layer, List.copyOf(equipment), List.copyOf(effects), width, depth, pass);
    }

    public static Part equipment(String text) throws IOException {
        String idle = "", slot = ""; int layer = 0; List<Integer> variation = List.of(0, 0);
        List<Motion> motions = new ArrayList<>(); List<Integer> hidden = new ArrayList<>();
        for (var section : ResourceScript.sections(text)) {
            if (section.tag().equals("waiting motion") && !section.values().isEmpty()) {
                String path = section.values().get(0); motions.add(new Motion(path, layer));
                if (idle.isEmpty()) idle = path;
            }
            if (section.tag().equals("layer")) layer = integer(section.values(), 0);
            if (section.tag().equals("hide layer")) for (int i = 0; i < section.values().size(); i++) hidden.add(integer(section.values(), i));
            if (section.tag().equals("equipment type") && !section.values().isEmpty()) slot = SceneScript.flag(section.values().get(0));
            if (section.tag().equals("variation")) {
                int first = integer(section.values(), 0), second = integer(section.values(), 1);
                if (first < 0 || second < 0 || first > 9999 || second > 9999) throw new IOException("装备 variation 超出范围");
                variation = List.of(first, second);
            }
        }
        return new Part(idle, motions.isEmpty() ? layer : motions.get(0).layer(), slot, variation, List.copyOf(motions), List.copyOf(hidden));
    }

    /** 0x56E6A0 selects CNParticleFactory for these ACT1 passive-object IDs. */
    public static Factory particleFactory(String text, int id) throws IOException {
        if (!Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 13, 14, 15, 16, 17, 18, 19, 20, 51, 52).contains(id)) return null;
        String path = ""; List<String> integers = List.of();
        for (var section : ResourceScript.sections(text)) {
            if (section.tag().equals("string data") && !section.values().isEmpty()) path = section.values().get(0);
            if (section.tag().equals("int data")) integers = section.values();
        }
        if (!path.toLowerCase(Locale.ROOT).endsWith(".ptl")) return null;
        long initial = integers.isEmpty() ? 0 : integer(integers, 0);
        if (initial < 0) throw new IOException("粒子初始时间不能为负数");
        return new Factory(path, initial, integers.size() > 2 && integer(integers, 2) == 1);
    }
    /** 0x56E6A0 -> 0x56DBC0 / 0x578AF0: native scrolling scenery, not a particle emitter. */
    public static int sceneryPeriod(String text, int id) throws IOException {
        if (id < 333 || id > 337) return 0;
        for (var section : ResourceScript.sections(text)) if (section.tag().equals("int data")) {
            int period = integer(section.values(), 0);
            if (period < 0 || period > 1_000_000) throw new IOException("背景平铺间距超出范围");
            return period;
        }
        return 0;
    }
    static int integer(List<String> fields, int index) throws IOException {
        try { return Integer.parseInt(fields.get(index)); }
        catch (NumberFormatException | IndexOutOfBoundsException failure) { throw new IOException("需要整数参数"); }
    }
    private static double number(List<String> fields, int index) throws IOException {
        try {
            double value = Double.parseDouble(fields.get(index));
            if (!Double.isFinite(value) || Math.abs(value) > 1_000_000) throw new IOException("实体坐标超出范围");
            return value;
        } catch (NumberFormatException | IndexOutOfBoundsException failure) { throw new IOException("需要坐标参数"); }
    }
    public record Spawn(Kind kind, int id, double x, double y, double z, boolean mirrored, boolean conditional,
                        boolean special, int line) {
        public String label() { return kind.label() + " #" + id; }
    }
    public record Layout(List<Spawn> spawns, List<String> warnings) {}
    public record Definition(String idle, String layer, List<String> equipment, List<String> effects, double width, double depth, int pass) {}
    public record Motion(String path, int layer) {}
    public record Part(String idle, int layer, String slot, List<Integer> variation, List<Motion> motions, List<Integer> hidden) {}
    public record Factory(String path, long initialTime, boolean fillMap) {}
}
