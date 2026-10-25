package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScenePreviewTest {
    @Test void nativeWorldOriginAndRectangularPassCellsAlignActorsWithFloor() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("tile.img", false, 2),
                "map/foot.ani", ani("front.img", true, 100));
        var frame = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] "
                + "[animation] `foot.ani` `[normal]` 100 219 0 [/animation] "
                + "[virtual movable area] 10 20 30 40 [/virtual movable area]", scripts::get, new Images()).render(0, false);
        assertEquals(339, frame.draws().get(1).y());
        assertTrue(frame.collisionAt(100, 339).contains("格 6,18"));
        assertTrue(frame.collisionAt(8, 131.9).contains("格 0,0"));
        assertTrue(frame.collisionAt(8, 132).contains("格 0,1"));
        assertTrue(frame.collisionAt(8, 119).contains("没有"));
        assertEquals(140, frame.movable().get(0).y);
        assertEquals(Color.GREEN.getRGB(), paint(frame, false, 0).getRGB(100, 339));
    }
    @Test void shorterBaseTilesAndIncompleteExtensionUseClientExtent() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", "[pass type] " + "0 ".repeat(280),
                "map/x.til", tile("ext.img", true, 2));
        var frame = ScenePreview.load("map/test.map", "[tile] `a.til` `a.til` [/tile] "
                + "[extended tile] `x.til` `x.til` `x.til` [/extended tile]", scripts::get, new Images()).render(0, false);
        assertEquals(480, frame.bounds().getHeight()); assertEquals(4, frame.tileCount());
        assertEquals(360, frame.collisions().get(2).y());
        assertTrue(frame.warnings().stream().anyMatch(w -> w.contains("末行不完整")));
    }
    @Test void baseTilesExtendHorizontallyAndExtendedTilesWrapBelowAt480() throws Exception {
        Map<String, String> scripts = new HashMap<>(); scripts.put("map/a.til", tile("tile.img", false, 0));
        scripts.put("map/x.til", tile("ext.img", true, 2));
        var scene = ScenePreview.load("map/test.map", "[tile] `a.til` `a.til` [/tile] "
                + "[extended tile] `x.til` `x.til` `x.til` `x.til` [/extended tile]", scripts::get, new Images());
        var rendered = scene.render(0, false); assertEquals(6, rendered.tileCount());
        assertEquals(448, rendered.bounds().getWidth()); assertEquals(720, rendered.bounds().getHeight());
        assertEquals(224, rendered.collisions().get(1).x()); assertEquals(480, rendered.collisions().get(2).y());
        assertEquals(0, rendered.collisions().get(4).x()); assertEquals(600, rendered.collisions().get(4).y());
        assertTrue(rendered.collisionAt(8, 608).contains("2：不可通行"));
    }
    @Test void floorTrimPlacedZAndLayerOrderAreVisibleInPixels() throws Exception {
        Map<String, String> scripts = new HashMap<>(); scripts.put("map/a.til", tile("tile.img", false, 0));
        scripts.put("map/back.ani", ani("back.img", true, 100)); scripts.put("map/front.ani", ani("front.img", false, 100));
        var scene = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] "
                + "[background animation] `back.ani` `[distantback]` `[below]` [/background animation] "
                + "[animation] `front.ani` `[normal]` 10 30 15 [/animation]", scripts::get, new Images());
        BufferedImage image = paint(scene.render(0, false), false, 0);
        assertEquals(Color.RED.getRGB(), image.getRGB(5, 10));
        assertEquals(Color.GREEN.getRGB(), image.getRGB(12, 138));
        assertEquals(Color.BLUE.getRGB(), image.getRGB(5, 450));
    }
    @Test void collisionToggleOnlyChangesOverlayAndIncludesFloatingOnlyAndEditedTile() throws Exception {
        Map<String, String> scripts = new HashMap<>(); scripts.put("map/a.til", tile("tile.img", false, 2));
        var scene = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile]", scripts::get, new Images());
        var frame = scene.render(0, false); BufferedImage clean = paint(frame, false, 0), overlay = paint(frame, true, 0);
        assertNotEquals(clean.getRGB(8, 128), overlay.getRGB(8, 128));
        assertEquals(clean.getRGB(8, 160), overlay.getRGB(8, 160));
        scripts.put("map/a.til", tile("tile.img", false, 1));
        var edited = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile]", scripts::get, new Images()).render(0, false);
        assertTrue(edited.collisionAt(8, 128).contains("1：仅浮空"));
        assertNotEquals(overlay.getRGB(8, 128), paint(edited, true, 0).getRGB(8, 128));
    }
    @Test void missingTileDoesNotShiftTheRemainingTileAndPartialScenesHaveDiagnostics() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("tile.img", false, 2));
        var frame = ScenePreview.load("map/test.map", "[tile] `missing.til` `a.til` [/tile]", scripts::get, new Images()).render(0, false);
        assertEquals(224, frame.collisions().get(0).x()); assertFalse(frame.warnings().isEmpty());
    }
    @Test void unrelatedAnimationPeriodsAndNonLoopingEndStatesShareAbsoluteTime() throws Exception {
        Map<String, String> scripts = new HashMap<>(); scripts.put("map/a.til", tile("tile.img", false, 0));
        scripts.put("map/a.ani", ani("front.img", true, 100, 100));
        scripts.put("map/b.ani", ani("front.img", true, 150, 150));
        scripts.put("map/c.ani", ani("front.img", false, 50, 50));
        Images images = new Images();
        var scene = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] [animation] "
                + "`a.ani` `[normal]` 0 0 0 `b.ani` `[normal]` 10 0 0 `c.ani` `[normal]` 20 0 0 [/animation]", scripts::get, images);
        assertEquals(0, images.pixelReads); // All-frame viewport bounds require metadata only.
        var frame = scene.render(200, false);
        assertEquals(0, active(frame, "a.ani")); assertEquals(1, active(frame, "b.ani")); assertEquals(1, active(frame, "c.ani"));
        assertEquals(0, active(scene.render(200, true), "c.ani"));
    }
    @Test void backgroundRepeatsAndPanningUsesItsOwnScrollRatio() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("", false, 0), "map/back.ani", ani("stripe.img", true, 100));
        var frame = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] [far sight scroll] 50 "
                + "[background animation] `back.ani` `[distantback]` `[below]` [/background animation]", scripts::get, new Images()).render(0, false);
        assertEquals(Color.GREEN.getRGB(), paint(frame, false, 0).getRGB(55, 20));
        assertEquals(Color.RED.getRGB(), paint(frame, false, 20).getRGB(55, 20));
        assertEquals(0, paint(frame, false, 0).getRGB(155, 120));
    }
    private static int active(ScenePreview.Rendered frame, String suffix) {
        return frame.activeFrames().entrySet().stream().filter(e -> e.getKey().endsWith(suffix)).findFirst().orElseThrow().getValue();
    }
    private static String tile(String image, boolean extended, int pass) {
        return "[IMAGE] `" + image + "` 0 [tile type] `[" + (extended ? "extended" : "basic") + "]` [pass type] "
                + (pass + " ").repeat(14) + "0 ".repeat((extended ? 10 : 30) * 14 - 14);
    }
    private static String ani(String image, boolean loop, int... delays) {
        StringBuilder source = new StringBuilder("[LOOP] " + (loop ? 1 : 0));
        for (int i = 0; i < delays.length; i++) source.append(" [FRAME").append(i).append("] [IMAGE] `").append(image)
                .append("` ").append(i).append(" [DELAY] ").append(delays[i]);
        return source.toString();
    }
    private static BufferedImage paint(ScenePreview.Rendered frame, boolean overlay, double pan) {
        BufferedImage image = new BufferedImage(448, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); try { frame.paint(g, new Rectangle(0, 0, 448, 800), 0, 0, 1, pan, 0, overlay); }
        finally { g.dispose(); } return image;
    }
    private static class Images implements ScenePreview.SpriteLookup {
        int pixelReads;
        @Override public NpkResources.Info info(String path, int index) {
            return path.equals("tile.img") ? new NpkResources.Info(224, 40, 0, 440, 224, 480)
                    : path.equals("ext.img") ? new NpkResources.Info(224, 160, 0, 0, 224, 160)
                    : path.equals("front.img") ? new NpkResources.Info(10, 10, 0, 0, 20, 30)
                    : new NpkResources.Info(100, 100, 0, 0, 100, 100);
        }
        @Override public NpkResources.Sprite sprite(String path, int index) throws IOException {
            pixelReads++; var info = info(path, index);
            BufferedImage image = new BufferedImage(info.width(), info.height(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setColor(path.equals("tile.img") ? Color.BLUE : path.equals("front.img") ? Color.GREEN : Color.RED);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            if (path.equals("stripe.img")) { g.setColor(Color.GREEN); g.fillRect(50, 0, 50, 100); }
            g.dispose(); return new NpkResources.Sprite(image, info.x(), info.y(), info.canvasWidth(), info.canvasHeight(),
                    Path.of("fake.npk"), path, index, index);
        }
    }
}
