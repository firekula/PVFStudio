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
    @Test void onlyNormalLayerSortsByWorldYWhileOtherLayersKeepCreationOrder() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("", false, 0),
                "map/rail.ani", ani("back.img", false, 100),
                "map/stair.ani", "[FRAME0] [IMAGE] `front.img` 0 [IMAGE POS] 0 20 [DELAY] 100");
        for (String layer : List.of("distantback", "middleback", "closeback", "bottom", "normal", "close")) {
            var frame = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] [animation] "
                    + "`rail.ani` `[" + layer + "]` 0 20 0 `stair.ani` `[" + layer + "]` 0 10 0 [/animation]",
                    scripts::get, new Images()).render(0, false);
            int expected = layer.equals("normal") ? Color.RED.getRGB() : Color.GREEN.getRGB();
            assertEquals(expected, paint(frame, false, 0).getRGB(2, 152), layer);
            var image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
            frame.paintClient(g, new Rectangle(0, 0, 640, 480), 1, new Point(), false, false); g.dispose();
            assertEquals(expected, image.getRGB(2, 152), layer + " client");
        }
    }
    @Test void newerBasicImageOffsetAlignsFloorWithoutMovingActorsPassCellsOrExtendedRows() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("tile.img", false, 2) + " [img pos] 80",
                "map/e.til", tile("ext.img", true, 0) + " [img pos] 80", "map/foot.ani", ani("front.img", false, 100));
        var frame = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] [extended tile] `e.til` [/extended tile] "
                + "[animation] `foot.ani` `[normal]` 100 240 0 [/animation]", scripts::get, new Images()).render(0, false);
        assertEquals(600, frame.bounds().getHeight()); assertEquals(-80, frame.draws().get(0).y());
        assertEquals(480, frame.draws().get(1).y()); assertEquals(360, frame.draws().get(2).y());
        assertEquals(120, frame.collisions().get(0).y()); assertEquals(480, frame.collisions().get(1).y());
        assertEquals(Color.BLUE.getRGB(), paint(frame, false, 0).getRGB(90, 360));
        assertEquals(Color.GREEN.getRGB(), paint(frame, false, 0).getRGB(100, 360));
        assertEquals(Color.RED.getRGB(), paint(frame, false, 0).getRGB(90, 480));
        assertTrue(frame.collisionAt(8, 120).contains("0,0"));
        var standalone = ScenePreview.load("map/a.til", scripts.get("map/a.til"), scripts::get, new Images()).render(0, false);
        assertEquals(-80, standalone.draws().get(0).y()); assertEquals(120, standalone.collisions().get(0).y());
    }
    @Test void seaTrainSceneryRepeatsThroughWholeMapAndClientCameraAcrossAnimationLoop() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("", false, 0),
                "passiveobject/passiveobject.lst", "723 `sea.obj` 724 `ordinary.obj`",
                "passiveobject/sea.obj", "[layer] `[distantback]` [basic motion] `sea.ani` [int data] 80 0 [/int data]",
                "passiveobject/ordinary.obj", "[basic motion] `sea.ani` [int data] 80 0 [/int data]",
                "passiveobject/sea.ani", "[LOOP] 1 [FRAME0] [IMAGE] `back.img` 0 [IMAGE POS] 0 -80 [INTERPOLATION] 1 [DELAY] 100 "
                        + "[FRAME1] [IMAGE] `back.img` 0 [IMAGE POS] -80 -80 [DELAY] 0");
        var scene = ScenePreview.load("map/sea.map", "[tile] " + "`a.til` ".repeat(5) + "[/tile] [far sight scroll] 56 "
                + "[passive object] 723 0 0 120 [/passive object]", scripts::get, new Images());
        for (long time : new long[]{0, 50, 99, 100, 60061}) {
            var frame = scene.render(time, false);
            BufferedImage image = new BufferedImage(1120, 480, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
            frame.paint(g, new Rectangle(0, 0, 1120, 480), 0, 80, 1, 0, 0, false); g.dispose();
            for (int x : new int[]{0, 555, 1119}) assertEquals(Color.RED.getRGB(), image.getRGB(x, 1));
            g = image.createGraphics(); frame.paintClient(g, new Rectangle(0, 0, 640, 480), 1, new Point(480, 0), false, false); g.dispose();
            assertEquals(Color.RED.getRGB(), image.getRGB(639, 1)); assertTrue(frame.warnings().isEmpty());
        }
        assertEquals(0, EntityScript.sceneryPeriod(scripts.get("passiveobject/ordinary.obj"), 724));
    }
    @Test void overviewIncludesDecorationOutsideFloorWithoutChangingClientLimits() throws Exception {
        var rendered = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] [animation] `a.ani` `[normal]` 300 -200 0 [/animation]",
                Map.of("map/a.til", tile("", false, 0), "map/a.ani", ani("front.img", false, 100))::get, new Images()).render(0, false);
        assertEquals(224, rendered.bounds().getWidth()); assertEquals(new SceneCamera.Limits(0, 0), rendered.cameraLimits());
        assertTrue(rendered.overviewBounds().getMaxX() > 300); assertTrue(rendered.overviewBounds().getMinY() < 0);
        BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        rendered.paint(g, new Rectangle(0, 0, 500, 500), 0, 100, 1, 0, 0, false); g.dispose();
        assertEquals(Color.GREEN.getRGB(), image.getRGB(300, 20));
    }
    @Test void clientVerticalLimitIsIndependentOfShorterBaseImageHeight() throws Exception {
        var frame = ScenePreview.load("map/test.map", "[tile] `a.til` `a.til` [/tile] [extended tile] `e.til` `e.til` `e.til` [/extended tile]",
                Map.of("map/a.til", "[pass type] " + "0 ".repeat(280), "map/e.til", tile("ext.img", true, 0))::get, new Images()).render(0, false);
        assertEquals(480, frame.bounds().getHeight());
        assertEquals(120, frame.cameraLimits().maxY()); assertEquals(0, frame.cameraLimits().maxX());
    }
    @Test void clientClipsToScreenInsteadOfTileExtentAndUsesIntegerHorizontalParallaxOnly() throws Exception {
        var frame = ScenePreview.load("map/test.map", "[tile] `a.til` `a.til` `a.til` `a.til` [/tile] "
                + "[extended tile] `a.til` `a.til` `a.til` `a.til` [/extended tile] [far sight scroll] 58 "
                + "[animation] `a.ani` `[distantback]` 100 0 0 [/animation]",
                Map.of("map/a.til", tile("", false, 0), "map/a.ani", ani("front.img", false, 100))::get, new Images()).render(0, false);
        var image = new BufferedImage(680, 520, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
        g.setColor(Color.MAGENTA); g.fillRect(0, 0, 680, 520);
        frame.paintClient(g, new Rectangle(20, 20, 640, 480), 1, new Point(50, 60), false, false); g.dispose();
        assertEquals(Color.GREEN.getRGB(), image.getRGB(91, 80)); // 20 + 100 - (50*58/100), 20 + 120 - 60
        assertEquals(Color.BLACK.getRGB(), image.getRGB(90, 80));
        assertEquals(Color.MAGENTA.getRGB(), image.getRGB(19, 80));
        var narrow = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] [animation] `a.ani` `[normal]` 300 0 0 [/animation]",
                Map.of("map/a.til", tile("", false, 0), "map/a.ani", ani("front.img", false, 100))::get, new Images()).render(0, false);
        g = image.createGraphics(); narrow.paintClient(g, new Rectangle(0, 0, 640, 480), 1, new Point(1000, 1000), false, false); g.dispose();
        assertEquals(Color.GREEN.getRGB(), image.getRGB(300, 120)); // scenery beyond a 224-wide floor remains visible
        assertEquals(Color.BLACK.getRGB(), image.getRGB(600, 400));
    }
    @Test void centeredCameraAndDragBothAffectForegroundParallaxButKeepWorldOverlayFixed() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("", false, 0), "map/gate.ani", ani("front.img", false, 100));
        var frame = ScenePreview.load("map/test.map", "[tile] `a.til` `a.til` `a.til` `a.til` `a.til` [/tile] [near sight scroll] 200 "
                + "[animation] `gate.ani` `[close]` 350 0 0 [/animation] [virtual movable area] 110 0 10 10 [/virtual movable area]",
                scripts::get, new Images()).render(0, false);
        assertEquals(Color.GREEN.getRGB(), paint(frame, false, 0).getRGB(112, 122));
        assertEquals(Color.GREEN.getRGB(), paint(frame, false, 20).getRGB(152, 122));
        assertEquals(110, frame.movable().get(0).x); assertEquals(120, frame.movable().get(0).y);
    }
    @Test void closebackBuildingsAreDrawnOverTheOpaqueTileFloor() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("tile.img", false, 0),
                "map/house.ani", ani("front.img", false, 100));
        var rendered = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] "
                + "[animation] `house.ani` `[closeback]` 10 330 0 [/animation]", scripts::get, new Images()).render(0, false);
        assertEquals(Color.GREEN.getRGB(), paint(rendered, false, 0).getRGB(12, 452));
        assertEquals(Color.BLUE.getRGB(), paint(rendered, false, 0).getRGB(30, 452));
    }
    @Test void nativeSceneryUsesExplicitPeriodAndKeepsCoveringMapDuringInterpolation() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("", false, 0),
                "passiveobject/passiveobject.lst", "333 `sky.obj`",
                "passiveobject/sky.obj", "[layer] `[distantback]` [basic motion] `sky.ani` [int data] 80 451 [/int data]",
                "passiveobject/sky.ani", "[LOOP] 1 [FRAME0] [IMAGE] `back.img` 0 [IMAGE POS] 0 0 [INTERPOLATION] 1 [DELAY] 100 "
                        + "[FRAME1] [IMAGE] `back.img` 0 [IMAGE POS] -80 0 [DELAY] 0");
        var scene = ScenePreview.load("map/test.map", "[tile] `a.til` `a.til` [/tile] [far sight scroll] 56 "
                + "[passive object]\n333 0 0 120\n333 20 30 0\n[/passive object]", scripts::get, new Images());
        for (long time : new long[]{0, 50, 99, 100, 60061}) {
            var rendered = scene.render(time, false);
            assertEquals(Color.RED.getRGB(), paint(rendered, false, 0).getRGB(440, 20));
            assertEquals(Color.RED.getRGB(), paint(rendered, false, 0).getRGB(440, 170));
            assertEquals(80, rendered.draws().get(0).repeatPeriod());
            assertTrue(rendered.draws().get(0).repeat());
            assertEquals(.56, rendered.draws().get(0).scroll());
            assertTrue(rendered.warnings().isEmpty());
        }
        assertEquals(0, EntityScript.sceneryPeriod("[int data] 80", 331));
    }
    @Test void nativeWorldOriginAndRectangularPassCellsAlignActorsWithFloor() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("tile.img", false, 2),
                "map/foot.ani", ani("front.img", true, 100));
        var frame = ScenePreview.load("map/test.map", "[tile] `a.til` [/tile] "
                + "[animation] `foot.ani` `[normal]` 100 219 0 [/animation]", scripts::get, new Images()).render(0, false);
        assertEquals(339, frame.draws().get(1).y());
        assertTrue(frame.collisionAt(100, 339).contains("格 6,18"));
        assertTrue(frame.collisionAt(8, 131.9).contains("格 0,0"));
        assertTrue(frame.collisionAt(8, 132).contains("格 0,1"));
        assertTrue(frame.collisionAt(8, 119).contains("没有"));
        assertEquals(Color.GREEN.getRGB(), paint(frame, false, 0).getRGB(100, 339));
    }
    @Test void townOverlayShowsOnlyMovableUnionInOverviewAndClientViewport() throws Exception {
        Map<String, String> scripts = Map.of("map/a.til", tile("", false, 2), "map/b.til", tile("", false, 1));
        var frame = ScenePreview.load("map/town.map", "[tile] `a.til` `b.til` [/tile] "
                + "[virtual movable area] 10 0 60 30 100 100 20 20 [/virtual movable area]", scripts::get, new Images()).render(0, false);
        var clean = paint(frame, false, 0); var overlay = paint(frame, true, 0);
        for (Point point : List.of(new Point(8, 128), new Point(20, 128), new Point(232, 128)))
            assertEquals(clean.getRGB(point.x, point.y), overlay.getRGB(point.x, point.y));
        assertNotEquals(clean.getRGB(10, 130), overlay.getRGB(10, 130)); // cyan boundary remains visible
        assertTrue(frame.collisionAt(20, 128).contains("城镇可移动区域 ·"));
        assertTrue(frame.collisionAt(110, 230).contains("城镇可移动区域 ·"));
        assertTrue(frame.collisionAt(90, 128).contains("城镇可移动区域外"));
        assertFalse(frame.collisionAt(232, 128).contains("仅浮空"));
        var client = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB); var g = client.createGraphics();
        frame.paintClient(g, new Rectangle(0, 0, 640, 480), 1, new Point(), true, false); g.dispose();
        assertEquals(Color.BLACK.getRGB(), client.getRGB(8, 128));
        assertEquals(Color.BLACK.getRGB(), client.getRGB(232, 128));
        assertNotEquals(Color.BLACK.getRGB(), client.getRGB(10, 130));
        var tile = ScenePreview.load("map/a.til", scripts.get("map/a.til"), scripts::get, new Images()).render(0, false);
        assertNotEquals(paint(tile, false, 0).getRGB(8, 128), paint(tile, true, 0).getRGB(8, 128));
        var withoutArea = ScenePreview.load("map/town.map", "[tile] `a.til` [/tile] "
                + "[town movable area] 0 0 10 10 1 1 [/town movable area]", scripts::get, new Images()).render(0, false);
        assertEquals(paint(withoutArea, false, 0).getRGB(8, 128), paint(withoutArea, true, 0).getRGB(8, 128));
        assertTrue(withoutArea.collisionAt(8, 128).contains("未配置"));
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
