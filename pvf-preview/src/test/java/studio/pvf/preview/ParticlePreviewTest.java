package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ParticlePreviewTest {
    private static final String OBJECT = "[object] `dot.img` 0 [/object] ";
    @Test void nativeDefaultsAndAxisSectionsRemainIndependent() throws Exception {
        var p = ParticleScript.parse("[x axis] [move type] `[straight line]` [move variable 1] 12 30 "
                + "[z axis] [move type] `[sin]` [move variable 1] 10 15 [move variable 2] 2000 3000 " + OBJECT);
        assertEquals("stop", p.axes().get(1).type()); assertEquals(12, p.axes().get(0).start());
        assertEquals(2000, p.axes().get(2).periodStart()); assertEquals(0, p.maximum());
        assertEquals(100, p.alphaEnd()); assertEquals("normal", p.layer()); assertEquals("pierce", p.landing());
        assertThrows(java.io.IOException.class, () -> ParticleScript.parse("[creation frequency] NaN"));
        assertThrows(java.io.IOException.class, () -> ParticleScript.parse("[object] `a.ani` [/object]"));
    }
    @Test void nativeStartTimePrepopulatesFiniteEmittersAndSeekIsDeterministic() throws Exception {
        var script = ParticleScript.parse("[creation frequency] 10 [maximum create number] 10 [maximum create time] 1000 "
                + "[create area size] 300 200 0 [random range] 72 [life time] 1000000 " + OBJECT);
        var timeline = new ParticleTimeline(script, 42, 1000);
        assertEquals(10, timeline.sample(0, 10, 20, 30, false).particles().size());
        var before = timeline.sample(513, 10, 20, 30, false);
        timeline.sample(1_000_000_000L, 10, 20, 30, false);
        assertEquals(before, timeline.sample(513, 10, 20, 30, false));
        assertNotEquals(before, new ParticleTimeline(script, 43, 1000).sample(513, 10, 20, 30, false));
    }
    @Test void emissionLifespanAndUnlimitedWorkAreBoundedAtLargeTimes() throws Exception {
        var script = ParticleScript.parse("[creation frequency] 2 [maximum create number] 0 [life time] 1000 " + OBJECT);
        var t = new ParticleTimeline(script, 0, 0);
        assertEquals(1, t.sample(0, 0, 0, 0, false).particles().size());
        assertEquals(2, t.sample(500, 0, 0, 0, false).particles().size());
        assertEquals(2, t.sample(1_000_000_000_000L, 0, 0, 0, false).particles().size());
        assertFalse(t.sample(1_000_000_000_000L, 0, 0, 0, false).limited());
        var immortal = ParticleScript.parse("[creation frequency] 1000000 [maximum create number] 0 " + OBJECT);
        var sample = new ParticleTimeline(immortal, 0, 0).sample(Long.MAX_VALUE, 0, 0, 0, false);
        assertTrue(sample.limited()); assertEquals(1024, sample.particles().size());
    }
    @Test void straightMotionIntegratesSpeedAndPingPongWithoutClockReset() {
        var axis = new ParticleScript.Axis("straight line", 10, -10, 0, 0);
        assertEquals(7.5, ParticleTimeline.displacement(axis, 3000, "ping pong", 1500), 1e-6);
        assertEquals(0, ParticleTimeline.displacement(axis, 3000, "ping pong", 3000), 1e-6);
        assertEquals(-7.5, ParticleTimeline.displacement(axis, 3000, "ping pong", 4500), 1e-6);
        assertEquals(-20, ParticleTimeline.displacement(axis, 3000, "keep last value", 5000), 1e-6);
    }
    @Test void nativeYScaleGravityFadeAndLandingAreApplied() throws Exception {
        String body = "[creation frequency] -1 [maximum create number] 1 [life time] 2000 [transparent value] 100 0 "
                + "[y axis] [move type] `[straight line]` [move variable 1] 100 100 [gravity] 20 " + OBJECT;
        var t = new ParticleTimeline(ParticleScript.parse(body), 0, 0);
        var p = t.sample(1000, 20, 30, 20, false).particles().get(0);
        assertEquals(59, p.y(), 1e-6); assertEquals(10, p.z(), 1e-6); assertEquals(.5, p.alpha(), 1e-6);
        assertTrue(new ParticleTimeline(ParticleScript.parse(body + "[landing type] `[vanish]`"), 0, 0)
                .sample(1500, 20, 30, 20, false).particles().isEmpty());
        var stopped = new ParticleTimeline(ParticleScript.parse(body + "[landing type] `[stop]`"), 0, 0);
        assertEquals(stopped.sample(1500, 20, 30, 20, false).particles().get(0).y(),
                stopped.sample(1800, 20, 30, 20, false).particles().get(0).y(), .001);
    }
    @Test void sineKeepsConfiguredAmplitudeWhenRandomRangeIsZero() throws Exception {
        var script = ParticleScript.parse("[creation frequency] -1 [maximum create number] 1 "
                + "[z axis] [move type] `[sin]` [move variable 1] 20 0 [move variable 2] 2000 0 " + OBJECT);
        assertEquals(120, new ParticleTimeline(script, 0, 0).sample(500, 0, 0, 100, false).particles().get(0).z(), 1e-6);
    }
    @Test void mapFactoryOverridesCreationPosAndResolvesRelativeAnimationAndNpk() throws Exception {
        Map<String, String> files = base();
        files.put("passiveobject/passiveobject.lst", "5 `mapobject/factory.obj`");
        files.put("passiveobject/mapobject/factory.obj", "[string data] `particle/light.ptl` [/string data] [int data] 1000 [/int data]");
        files.put("passiveobject/mapobject/particle/light.ptl", "[creation pos] 900 900 900 [creation frequency] 2 [maximum create number] 2 "
                + "[object] `../ani/light.ani` -1 [/object]");
        files.put("passiveobject/mapobject/ani/light.ani", "[LOOP] 1 [FRAME000] [IMAGE] `dot.img` 0 [DELAY] 100");
        var scene = ScenePreview.load("map/a.map", "[tile] `a.til` [/tile]\n[passive object]\n5 40 50 10\n[/passive object]", files::get, sprites());
        var frame = scene.render(0, false);
        assertEquals(2, frame.particleCount()); assertEquals(1, frame.emitterCount()); assertTrue(frame.warnings().isEmpty(), frame.warnings().toString());
        assertTrue(frame.entities().get(0).problem().isEmpty());
        assertEquals(40, frame.draws().get(0).x()); assertEquals(160, frame.draws().get(0).y());
    }
    @Test void particlesSortBySceneLayerAndGroundDepthAndDoNotMutateSharedPixels() throws Exception {
        Map<String, String> files = base();
        files.put("passiveobject/passiveobject.lst", "5 `f.obj`"); files.put("passiveobject/f.obj", "[string data] `a.ptl` [/string data]");
        files.put("passiveobject/a.ptl", "[creation frequency] -1 [maximum create number] 1 [transparent value] 50 50 [layer] `[bottom]` " + OBJECT);
        files.put("map/front.ani", "[FRAME000] [IMAGE] `front.img` 0");
        var scene = ScenePreview.load("map/a.map", "[tile] `a.til` [/tile]\n[passive object]\n5 40 50 0\n[/passive object] "
                + "[animation] `front.ani` `[normal]` 40 50 0 [/animation]", files::get, sprites());
        var f = scene.render(0, false); assertEquals(2, f.draws().size());
        assertEquals(.5, f.draws().get(0).opacity()); assertEquals(15, f.draws().get(1).stage());
        assertEquals(255, f.draws().get(0).prepared().image().getRGB(0, 0) >>> 24);
    }
    @Test void passiveParticleChildrenResolveTheirOwnInitialMotionAndGuardCycles() throws Exception {
        Map<String, String> files = base(); files.put("passiveobject/passiveobject.lst", "20 `child.obj`");
        files.put("passiveobject/child.obj", "[basic motion] `child.ani`"); files.put("passiveobject/child.ani", "[FRAME000] [IMAGE] `dot.img` 0");
        String ptl = "[creation frequency] -1 [maximum create number] 1 [object type] `[passive object]` [object] `` 20 [/object]";
        var f = ScenePreview.load("a.ptl", ptl, files::get, sprites()).render(100, false);
        assertEquals(1, f.draws().size()); assertTrue(f.warnings().isEmpty());
        files.put("passiveobject/child.obj", "[string data] `../a.ptl` [/string data]"); files.put("a.ptl", ptl);
        assertTrue(ScenePreview.load("a.ptl", ptl, files::get, sprites()).render(100, false).warnings().stream().anyMatch(w -> w.contains("循环引用")));
    }
    @Test void collisionOverlayUsesObjectFootprintAndTownExplanation() throws Exception {
        Map<String, String> files = base(); files.put("passiveobject/passiveobject.lst", "40 `solid.obj`");
        files.put("passiveobject/solid.obj", "[width] 20 10 [pass type] `[do not pass]` [basic motion] `solid.ani`");
        files.put("passiveobject/solid.ani", "[FRAME000] [IMAGE] `dot.img` 0");
        String map = "[tile] `a.til` [/tile]\n[passive object]\n40 40 50 0\n[/passive object] ";
        var f = ScenePreview.load("map/a.map", map, files::get, sprites()).render(0, false);
        assertEquals(new java.awt.geom.Rectangle2D.Double(30, 165, 20, 10), f.objectCollisions().get(0).bounds());
        assertTrue(f.collisionAt(35, 170).contains("被动对象 #40"));
        BufferedImage image = new BufferedImage(224, 480, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        f.paint(g, new Rectangle(0, 0, 224, 480), 0, 0, 1, 0, 0, false); assertEquals(0, image.getRGB(32, 170));
        f.paint(g, new Rectangle(0, 0, 224, 480), 0, 0, 1, 0, 0, true); g.dispose(); assertNotEquals(0, image.getRGB(32, 170));
        var town = ScenePreview.load("map/a.map", map + "[virtual movable area] 25 40 100 100 [/virtual movable area]",
                files::get, sprites()).render(0, false);
        assertTrue(town.collisionAt(35, 170).contains("城镇可移动区域 ·")); // footprint must not override the town area
        image = new BufferedImage(224, 480, BufferedImage.TYPE_INT_ARGB); g = image.createGraphics();
        town.paint(g, new Rectangle(0, 0, 224, 480), 0, 0, 1, 0, 0, true); g.dispose();
        assertEquals(0, image.getRGB(32, 170));
        assertNotEquals(0, image.getRGB(25, 180));
    }
    @Test void equipmentWaitingMotionKeepsItsOwnLayerAndSupportsSeveralWaitingLayers() throws Exception {
        var gear = EntityScript.equipment("[LAYER] -200 [waiting motion] `back.ani` [LAYER] 400 [attack motion] `hit.ani` [/attack motion]");
        assertEquals(-200, gear.layer()); assertEquals(List.of(new EntityScript.Motion("back.ani", -200)), gear.motions());
        var multi = EntityScript.equipment("[LAYER] -100 [waiting motion] `back.ani` [LAYER] 300 [waiting motion] `front.ani`");
        assertEquals(2, multi.motions().size()); assertEquals(300, multi.motions().get(1).layer());
        assertEquals(2, ResourceScript.references("[string data] `a.ptl` `b.ani` [/string data]").size());
    }
    @Test void interpolationSamplesGeometryAndRgbaWithCurrentImageAndLoopBoundary() throws Exception {
        var ani = ResourceScript.animation("[LOOP] 1 [FRAME000] [IMAGE] `a.img` 0 [IMAGE POS] 0 -20 [RGBA] 255 255 255 0 [INTERPOLATION] 1 [DELAY] 2000 "
                + "[FRAME001] [IMAGE] `b.img` 1 [IMAGE POS] 20 0 [RGBA] 0 255 255 200 [INTERPOLATION] 1 [DELAY] 2000");
        var clock = new AnimationTimeline(ani); var frame = clock.sample(1000, false);
        assertEquals("a.img", frame.image()); assertEquals(10, frame.x()); assertEquals(-10, frame.y()); assertEquals(100, frame.a());
        assertEquals(-10, clock.sample(3000, false).y()); assertEquals(0, clock.sample(4000, false).x());
        var noLoop = new AnimationTimeline(new ResourceScript.Animation(false, ani.frames(), List.of()));
        assertEquals(20, noLoop.sample(3000, false).x());
    }
    @Test void actorWaitingPartsDoNotTakeLayerNumbersFromAttackOnlySections() throws Exception {
        Map<String, String> files = base(); files.put("monster/monster.lst", "999 `a.mob`");
        files.put("monster/a.mob", "[waiting motion] `body.ani` [equipment] 1 `test.equ` [/equipment]");
        files.put("monster/body.ani", "[FRAME000] [IMAGE] `body.img` 0");
        files.put("monster/test.equ", "[LAYER] -200 [waiting motion] `back.ani` [LAYER] 800 [attack motion] `attack.ani` [/attack motion]");
        files.put("monster/back.ani", "[FRAME000] [IMAGE] `back.img` 0");
        var frame = ScenePreview.load("map/a.map", "[tile] `a.til` [/tile]\n[monster]\n999 1 0 40 50 0 1 1 `[fixed]` `[normal]`\n[/monster]", files::get, sprites()).render(0, false);
        assertTrue(frame.warnings().isEmpty()); assertEquals("back.img", frame.draws().get(0).prepared().source().img());
        assertEquals("body.img", frame.draws().get(1).prepared().source().img());
    }
    @Test void unresolvedPassTypeDoesNotHideAnOtherwiseValidObject() throws Exception {
        Map<String, String> files = base(); files.put("passiveobject/passiveobject.lst", "40 `solid.obj`");
        files.put("passiveobject/solid.obj", "[width] 20 10 [pass type] <9::pass_type_5140> [basic motion] `solid.ani`");
        files.put("passiveobject/solid.ani", "[FRAME000] [IMAGE] `dot.img` 0");
        var f = ScenePreview.load("map/a.map", "[tile] `a.til` [/tile]\n[passive object]\n40 40 50 0\n[/passive object]", files::get, sprites()).render(0, false);
        assertEquals(1, f.draws().size()); assertTrue(f.entities().get(0).problem().isEmpty()); assertTrue(f.objectCollisions().isEmpty());
        assertTrue(f.warnings().get(0).contains("无法识别"));
    }
    @Test void oscillatingParticlesDoNotReappearAfterLanding() throws Exception {
        String ptl = "[creation frequency] -1 [maximum create number] 1 [z axis] [move type] `[sin]` "
                + "[move variable 1] 20 0 [move variable 2] 2000 0 " + OBJECT;
        var vanished = new ParticleTimeline(ParticleScript.parse(ptl + "[landing type] `[vanish]`"), 0, 0);
        assertFalse(vanished.sample(500, 0, 0, 1, false).particles().isEmpty());
        assertTrue(vanished.sample(2500, 0, 0, 1, false).particles().isEmpty());
        var stopped = new ParticleTimeline(ParticleScript.parse(ptl + "[landing type] `[stop]`"), 0, 0);
        assertEquals(0, stopped.sample(2500, 0, 0, 1, false).particles().get(0).z());
    }
    private static Map<String, String> base() { var result = new HashMap<String, String>(); result.put("map/a.til", "[pass type] " + "0 ".repeat(420)); return result; }
    private static ScenePreview.SpriteLookup sprites() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, 2, 2); g.dispose();
        return new ScenePreview.SpriteLookup() {
            public NpkResources.Sprite sprite(String path, int index) { return new NpkResources.Sprite(image, 0, 0, 2, 2, Path.of("test.npk"), path, index, index); }
            public NpkResources.Info info(String path, int index) { return new NpkResources.Info(2, 2, 0, 0, 2, 2); }
        };
    }
}
