package studio.pvf.preview;

import java.io.IOException;
import java.util.*;
import static studio.pvf.preview.DungeonScript.*;

/** A reproducible script layout; the online server's random instance is not available to the editor. */
public final class DungeonPreview {
    public record MapChoice(int id, String path, MapInfo info) { @Override public String toString() { return id + " · " + path; } }
    public record Room(Position position, int mask, boolean start, boolean boss, List<MapChoice> choices, int selected) {
        public MapChoice map() { return choices.isEmpty() ? null : choices.get(selected); }
    }
    public record Layout(Maze maze, Position start, Position boss, List<Room> rooms, List<String> warnings) {
        public Room room(Position position) { return rooms.stream().filter(r -> r.position.equals(position)).findFirst().orElse(null); }
    }
    public enum GateKind { NORMAL, BOSS, WALL }
    public record Gate(Direction direction, int objectId, GateKind kind, Position position, boolean open) {}
    private final Definition definition;
    private final Map<Integer, MapChoice> maps;
    private final Map<Integer, List<MapChoice>> pools = new HashMap<>();
    private final List<String> warnings;
    private DungeonPreview(Definition definition, List<MapChoice> maps, List<String> warnings) {
        this.definition = definition; this.warnings = warnings; Map<Integer, MapChoice> indexed = new HashMap<>();
        maps.forEach(m -> indexed.put(m.id(), m)); this.maps = Map.copyOf(indexed);
        for (int mask = 0; mask < 16; mask++) for (boolean boss : new boolean[]{false, true}) {
            int shape = mask; String type = boss ? "boss" : "normal";
            pools.put(mask + (boss ? 16 : 0), maps.stream().filter(m -> m.info().type().equals(type) && m.info().supports(shape)).toList());
        }
    }
    public Definition definition() { return definition; }

    public static DungeonPreview load(String path, String text, ScenePreview.TextLookup lookup) throws IOException {
        Definition definition = DungeonScript.parse(text);
        String dgnList = lookup.read("dungeon/dungeon.lst"), mapList = lookup.read("map/map.lst");
        if (mapList == null) throw new IOException("找不到 map/map.lst");
        int id = -1;
        if (dgnList != null) for (var entry : EntityScript.catalog(dgnList).entrySet())
            if (resolvePath("dungeon/", entry.getValue()).equals(path.toLowerCase(Locale.ROOT))) { id = entry.getKey(); break; }
        Set<Integer> explicit = new HashSet<>();
        for (var maze : definition.mazes()) { maze.maps().values().forEach(explicit::addAll); maze.bossMaps().values().forEach(explicit::addAll); }
        List<MapChoice> maps = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        if (id < 0) warnings.add("DGN 未注册于 dungeon/dungeon.lst，只使用明确指定的 MAP");
        var catalog = new TreeMap<>(EntityScript.catalog(mapList));
        if (catalog.size() > 8192) throw new IOException("MAP 索引超过预览上限 8192 个");
        for (var entry : catalog.entrySet()) {
            String mapPath = resolvePath("map/", entry.getValue());
            try {
                String raw = lookup.read(mapPath); if (raw == null) { if (explicit.contains(entry.getKey())) warnings.add("找不到 " + mapPath); continue; }
                MapInfo info = DungeonScript.map(raw);
                if (explicit.contains(entry.getKey()) || id >= 0 && info.dungeons().contains(id)) maps.add(new MapChoice(entry.getKey(), mapPath, info));
            } catch (IOException failure) { if (explicit.contains(entry.getKey()) && warnings.size() < 100) warnings.add(mapPath + "：" + failure.getMessage()); }
        }
        return new DungeonPreview(definition, List.copyOf(maps), List.copyOf(warnings));
    }
    private static String resolvePath(String root, String reference) {
        String value = reference.replace('\\', '/').toLowerCase(Locale.ROOT); return value.startsWith(root) ? value : root + value;
    }

    public Layout arrange(int mazeIndex, Position start, Position boss, long seed) {
        Maze maze = definition.mazes().get(mazeIndex); Random random = new Random(seed); List<Room> rooms = new ArrayList<>();
        List<String> problems = new ArrayList<>(warnings);
        for (int y = 0; y < maze.height(); y++) for (int x = 0; x < maze.width(); x++) {
            Position position = new Position(x, y); int mask = maze.mask(position);
            if (mask == 0 && maze.width() * maze.height() > 1 && !position.equals(start) && !position.equals(boss)) continue;
            boolean isBoss = position.equals(boss); List<Integer> specified = isBoss && maze.bossMaps().containsKey(position)
                    ? maze.bossMaps().get(position) : maze.maps().get(position);
            List<MapChoice> pool = specified == null ? pools.get(mask + (isBoss ? 16 : 0))
                    : specified.stream().map(maps::get).filter(Objects::nonNull)
                    .filter(m -> (isBoss ? m.info.type().equals("boss") : m.info.type().equals("normal")) && m.info.supports(mask)).toList();
            // Ordinary coordinate lists often contain no Boss MAP; the separate Boss pool then applies.
            if (isBoss && pool.isEmpty() && !maze.bossMaps().containsKey(position))
                pool = pools.get(mask + 16);
            if (pool.isEmpty()) problems.add("房间 " + position + "（" + (char) ('A' + mask) + "）没有匹配的 " + (isBoss ? "Boss" : "普通") + " MAP");
            for (Direction d : Direction.values()) if ((mask & d.bit) != 0 && !maze.connected(position, d))
                problems.add("房间 " + position + " " + d.label + "方向连接未与相邻房间对应，预览关闭该出口");
            rooms.add(new Room(position, mask, position.equals(start), isBoss, pool, pool.isEmpty() ? 0 : random.nextInt(pool.size())));
        }
        if (start != null && start.equals(boss)) problems.add("起点与 Boss 坐标相同");
        return new Layout(maze, start, boss, List.copyOf(rooms), List.copyOf(problems));
    }

    public List<Gate> gates(Layout layout, Room room, MapChoice map, boolean cleared, boolean bossCondition) {
        List<Gate> gates = new ArrayList<>();
        for (Direction d : Direction.values()) {
            boolean connected = layout.maze.connected(room.position, d);
            boolean boss = connected && (room.boss || layout.maze.neighbor(room.position, d).equals(layout.boss));
            GateKind kind = !connected ? GateKind.WALL : boss ? GateKind.BOSS : GateKind.NORMAL;
            int id = definition.gateObjects().get(d.ordinal() * 3 + (kind == GateKind.WALL ? 2 : kind == GateKind.BOSS ? 1 : 0));
            if (id < 0) continue;
            if (map.info.gates().size() != 4) continue;
            gates.add(new Gate(d, id, kind, map.info.gates().get(d.ordinal()), connected && cleared && (!boss || bossCondition)));
        }
        return List.copyOf(gates);
    }
}
