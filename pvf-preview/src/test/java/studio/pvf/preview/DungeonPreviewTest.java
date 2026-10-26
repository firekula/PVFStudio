package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static studio.pvf.preview.DungeonScript.*;

class DungeonPreviewTest {
    private static final String DGN = "[pathgate object] 10 11 12 13 14 15 16 17 18 19 [maze info] [size] 2 2 "
            + "[greed] `JJMM\nDDGG` [start map] 0 0 [/start map] [boss map] 1 1 [/boss map]";
    private static final Position START = new Position(0, 0), BOSS = new Position(1, 1);
    @Test void explicitMapIdsSurviveGreedMismatchWhileAutomaticPoolsStayStrict() throws Exception {
        var files = files();
        var automatic = DungeonPreview.load("dungeon/a.dgn", DGN, files::get).arrange(0,START,BOSS,0);
        assertEquals(1,automatic.room(START).map().id());
        var explicit = DungeonPreview.load("dungeon/a.dgn",DGN + " [map specification] 0 0 2 [/map specification]",files::get).arrange(0,START,BOSS,0);
        assertEquals(2,explicit.room(START).map().id());
        assertTrue(explicit.warnings().stream().anyMatch(w -> w.contains("DGN 明确指定") && w.contains("保留关联")));
        assertEquals(automatic.maze().masks(),explicit.maze().masks());
        var absent = DungeonPreview.load("dungeon/a.dgn",DGN + " [map specification] 0 0 999 [/map specification]",files::get).arrange(0,START,BOSS,0);
        assertNull(absent.room(START).map());
        assertTrue(absent.warnings().stream().anyMatch(w -> w.contains("未注册") && w.contains("999")));
    }
    @Test void binaryScriptEscapedGreedSeparatorsMatchPlainTextAndKeepDirectionBits() throws Exception {
        String projected = DGN.replace("JJMM\nDDGG", "JJMM\\r\\nDDGG");
        var plain = DungeonScript.parse(DGN).mazes().get(0);
        var escaped = DungeonScript.parse(projected).mazes().get(0);
        assertEquals(plain, escaped);
        assertEquals(List.of(9,12,3,6), escaped.masks());
        assertTrue(escaped.connected(START, Direction.RIGHT)); assertTrue(escaped.connected(START, Direction.DOWN));
        var dungeon = DungeonPreview.load("dungeon/a.dgn", projected, files()::get);
        assertTrue(dungeon.arrange(0, START, BOSS, 0).warnings().isEmpty());
        assertEquals(List.of(9,0,12,0), DungeonScript.codes(List.of("JJAA\\tMMAA")));
        assertThrows(IOException.class, () -> DungeonScript.codes(List.of("JJ\\qMM")));
    }
    @Test void nativeDirectionsAndBossGateSelectionFollowBothRoomMasks() throws Exception {
        var dungeon = DungeonPreview.load("dungeon/a.dgn", DGN, files()::get); var layout = dungeon.arrange(0, START, BOSS, 0);
        assertEquals(4, layout.rooms().size()); assertTrue(layout.warnings().isEmpty());
        assertTrue(layout.maze().connected(START, Direction.RIGHT)); assertTrue(layout.maze().connected(START, Direction.DOWN));
        assertFalse(layout.maze().connected(START, Direction.LEFT)); assertFalse(layout.maze().connected(START, Direction.UP));
        var room = layout.room(new Position(1, 0)); var gates = dungeon.gates(layout, room, room.map(), true, true);
        assertEquals(List.of(10, 15, 19), gates.stream().map(DungeonPreview.Gate::objectId).toList()); // left normal, right wall, down Boss
        assertEquals(List.of(true, false, true), gates.stream().map(DungeonPreview.Gate::open).toList());
        assertEquals(List.of(new Position(16,24),new Position(208,24),new Position(112,348)),gates.stream().map(DungeonPreview.Gate::position).toList());
        assertTrue(dungeon.gates(layout, layout.room(BOSS), layout.room(BOSS).map(), true, true).stream()
                .filter(g -> g.kind() != DungeonPreview.GateKind.WALL).allMatch(g -> g.kind() == DungeonPreview.GateKind.BOSS));
        assertTrue(dungeon.gates(layout, room, room.map(), false, true).stream().noneMatch(DungeonPreview.Gate::open));
        assertFalse(dungeon.gates(layout, room, room.map(), true, false).stream().filter(g -> g.kind() == DungeonPreview.GateKind.BOSS).findFirst().orElseThrow().open());
    }
    @Test void explicitNormalAndBossPoolsDoNotReplaceEachOtherAndSeedsAreRepeatable() throws Exception {
        var files = files(); files.put("map/map.lst", files.get("map/map.lst") + " 4 `other.map` 5 `boss2.map`");
        files.put("map/other.map", map(9, "normal")); files.put("map/boss2.map", map(6, "boss"));
        String text = DGN + " [map specification] 0 0 4 [/map specification] [boss map specification] 1 1 5 [/boss map specification]";
        var dungeon = DungeonPreview.load("dungeon/a.dgn", text, files::get);
        var layout = dungeon.arrange(0, START, BOSS, 37);
        assertEquals(4, layout.room(START).map().id()); assertEquals(5, layout.room(BOSS).map().id());
        assertEquals(layout.rooms(), dungeon.arrange(0, START, BOSS, 37).rooms());
        assertTrue(layout.room(new Position(1, 0)).choices().stream().noneMatch(m -> m.info().type().equals("boss")));
        var fallback = DungeonPreview.load("dungeon/a.dgn", DGN + " [map specification] 1 1 1 [/map specification]", files::get).arrange(0, START, BOSS, 0);
        assertEquals("boss", fallback.room(BOSS).map().info().type());
    }
    @Test void invalidConnectionsDoNotInventNeighborRoomsOrFallbackMaps() throws Exception {
        var dungeon = DungeonPreview.load("dungeon/a.dgn", DGN.replace("JJMM", "BBMM")
                + " [map specification] 0 0 999 [/map specification]", files()::get);
        var layout = dungeon.arrange(0, START, BOSS, 0);
        assertNull(layout.room(START).map()); assertFalse(layout.maze().connected(new Position(0, 1), Direction.UP));
        assertTrue(layout.warnings().stream().anyMatch(w -> w.contains("没有匹配")));
        assertTrue(layout.warnings().stream().anyMatch(w -> w.contains("未与相邻")));
    }
    @Test void multipleMazesSparseTutorialAndNativeAbsentBossSentinelRemainDistinct() throws Exception {
        var definition = DungeonScript.parse(DGN + " [maze info] [size] 3 2 [greed] `AA` [start map] 0 0 [/start map] [boss map] -1 -1 [/boss map]");
        assertEquals(2, definition.mazes().size()); assertEquals(List.of(0, 0, 0, 0, 0, 0), definition.mazes().get(1).masks());
        assertTrue(definition.mazes().get(1).bosses().isEmpty());
        assertThrows(IOException.class, () -> DungeonScript.parse(DGN.replace("2 2", "51 2")));
        assertThrows(IOException.class, () -> DungeonScript.parse(DGN.replace("JJMM", "JXMM")));
        assertThrows(IOException.class, () -> DungeonScript.parse(DGN + " [start map] 2 0 [/start map]"));
    }
    @Test void gateLayersVariationsAndPassTilesAreVisibleAndDoNotAccumulateOnReload() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("passiveobject/passiveobject.lst", "10 `gate.obj`");
        files.put("passiveobject/gate.obj", "[layer] `[normal]` [string data] `always.ani` `closed.ani` `open.ani` `door.til` [/string data] "
                + "[int data] 0 1 1 2 2 3 0 0 [/int data]");
        for (String name : List.of("always", "closed", "open")) files.put("passiveobject/" + name + ".ani",
                "[FRAME0] [IMAGE] `" + name + "%d%d.img` 0 [DELAY] 100");
        files.put("map/a.til", "[pass type] " + "0 ".repeat(420));
        files.put("passiveobject/door.til", "[pass type] " + "2 ".repeat(14) + "0 ".repeat(406));
        var sprites = new ScenePreview.SpriteLookup() {
            public NpkResources.Info info(String path, int i) { return new NpkResources.Info(8, 8, 0, 0, 8, 8); }
            public NpkResources.Sprite sprite(String path, int i) {
                var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
                g.setColor(path.startsWith("closed") ? Color.RED : path.startsWith("open") ? Color.GREEN : Color.BLUE); g.fillRect(0, 0, 8, 8); g.dispose();
                return new NpkResources.Sprite(image, 0, 0, 8, 8, Path.of("test.npk"), path, i, i);
            }
        };
        for (boolean open : new boolean[]{false, true, false}) {
            var gate = new DungeonPreview.Gate(Direction.LEFT, 10, DungeonPreview.GateKind.NORMAL, new Position(32, 24), open);
            var frame = ScenePreview.loadRoom("map/a.map", "[tile] `a.til` [/tile]", files::get, sprites, List.of(gate)).render(0, false);
            assertEquals(2, frame.draws().size()); assertEquals(1, frame.entities().size()); assertTrue(frame.warnings().isEmpty());
            assertEquals("always01.img", frame.draws().get(0).prepared().source().img());
            assertEquals(open ? "open03.img" : "closed02.img", frame.draws().get(1).prepared().source().img());
            var image = new BufferedImage(224, 480, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
            frame.paint(g, new Rectangle(0, 0, 224, 480), 0, 0, 1, 0, 0, false); g.dispose();
            assertEquals((open ? Color.GREEN : Color.RED).getRGB(), image.getRGB(34, 146));
            assertTrue(frame.collisionAt(8, 148).contains("2：不可通行")); // left edge extends to map boundary
            assertTrue(frame.collisionAt(8, 148).contains("door.til"));
            assertTrue(frame.collisionAt(8, 160).contains("0：可通行"));
        }
    }
    @Test void gateAttachmentsKeepObjectLayerInsteadOfAlwaysPaintingInFrontOfNormalScenery() throws Exception {
        Map<String, String> files = new HashMap<>();
        files.put("map/a.til", "[pass type] " + "0 ".repeat(420));
        files.put("map/tent.ani", "[FRAME0] [IMAGE] `tent.img` 0 [IMAGE POS] 0 24 [DELAY] 100");
        files.put("passiveobject/passiveobject.lst", "604 `gate.obj`");
        files.put("passiveobject/gate.ani", "[FRAME0] [IMAGE] `gate.img` 0 [DELAY] 100");
        files.put("passiveobject/closed.ani", "[FRAME0] [IMAGE] `closed.img` 0 [DELAY] 100");
        var sprites = new ScenePreview.SpriteLookup() {
            public NpkResources.Info info(String path, int i) { return new NpkResources.Info(8, 8, 0, 0, 8, 8); }
            public NpkResources.Sprite sprite(String path, int i) {
                var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
                g.setColor(path.equals("tent.img") ? Color.GREEN : path.equals("closed.img") ? Color.RED : Color.BLUE);
                g.fillRect(0, 0, 8, 8); g.dispose();
                return new NpkResources.Sprite(image, 0, 0, 8, 8, Path.of("test.npk"), path, i, i);
            }
        };
        for (String layer : List.of("bottom", "normal")) {
            files.put("passiveobject/gate.obj", "[layer] `[" + layer + "]` [string data] `gate.ani` `closed.ani` [/string data] [int data] 0 -1 1 -1 [/int data]");
            var gate = new DungeonPreview.Gate(Direction.RIGHT, 604, DungeonPreview.GateKind.NORMAL, new Position(32, 24), false);
            var scene = ScenePreview.loadRoom("map/a.map", "[tile] `a.til` [/tile] [animation] `tent.ani` `[normal]` 32 0 0 [/animation]", files::get, sprites, List.of(gate));
            for (long time : new long[]{0, 60061}) {
                var frame = scene.render(time, false); var image = new BufferedImage(224, 480, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
                frame.paint(g, new Rectangle(0, 0, 224, 480), 0, 0, 1, 0, 0, false); g.dispose();
                assertEquals((layer.equals("bottom") ? Color.GREEN : Color.RED).getRGB(), image.getRGB(34, 146));
                assertTrue(frame.draws().stream().filter(d -> !d.prepared().source().img().equals("tent.img")).allMatch(d -> d.stage() == (layer.equals("bottom") ? 12 : 15)));
                assertTrue(frame.warnings().isEmpty());
            }
        }
    }
    private static Map<String, String> files() {
        var files = new HashMap<String, String>(); files.put("dungeon/dungeon.lst", "7 `a.dgn`");
        files.put("map/map.lst", "1 `a.map` 2 `b.map` 3 `boss.map`");
        files.put("map/a.map", map(9, "normal")); files.put("map/b.map", map(12, "normal") + " [greed] `DDAA MMAA`");
        files.put("map/boss.map", map(6, "boss")); return files;
    }
    private static String map(int mask, String type) {
        String code = "" + (char) ('A' + mask) + (char) ('A' + mask);
        return "[dungeon] 7 [/dungeon] [type] `[" + type + "]` [greed] `" + code + "AA` [pathgate pos] 16 24 208 24 112 12 112 348";
    }
}
