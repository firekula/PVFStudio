package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1BuffCastTest {
    private static final String COMMON = "common/commoneffect/animation/";
    private static final String MAGE = "character/mage/effect/animation/";
    private static final String PRIEST = "character/priest/effect/animation/";
    private static final String SHIELD = "skill/mage/aurashield.skl";
    private static final Map<String,String> BUFF_ACTIONS = Map.of(
            "buff motion", "animation/buff.ani", "waiting motion", "animation/stay.ani");

    @Test void fastHealTargetsSixtyPercentOfRecipientHeightAboveTheirFeet() throws Exception {
        var assets=Map.of("skill/priestskill.lst","55 `priest/fastheal.skl`",
                PRIEST+"slowhealcast1.ani",animation(320),PRIEST+"slowhealcast2.ani",animation(560),
                PRIEST+"slowhealcastwave.ani",animation(480),COMMON+"fastheal.ani",animation(700));
        var actions=Map.of("throw motion 3-1","animation/deliver1.ani",
                "throw motion 3-2","animation/deliver2.ani","waiting motion","animation/stay.ani");
        var cast=Act1BuffCast.find("skill/priest/fastheal.skl","[casting time] 800",assets::get,actions,1,job->111);
        var heal=visual(cast,COMMON+"fastheal.ani");
        assertEquals(800,heal.start());
        assertEquals(1500,heal.end());
        assertEquals(-66,heal.y());
        assertFalse(heal.mirror());
    }

    @Test void auraShieldStartsAfterCastingAndChangesToABoundedLoop() throws Exception {
        var assets = new HashMap<String,String>();
        assets.put("skill/mageskill.lst", "37 `mage/aurashield.skl`");
        assets.put(MAGE + "aurashieldstart.ani", animation(160));
        assets.put(MAGE + "aurashieldloop.ani", animation(400));
        assets.put(MAGE + "aurashieldend.ani", animation(240));
        assets.put(MAGE + "aurashieldhit1.ani", animation(430));
        assets.put(COMMON + "buffstarticon.ani", animation(1000));

        var cast = Act1BuffCast.find(SHIELD, "[casting time] 1000 1000", assets::get, BUFF_ACTIONS, 1);

        assertNotNull(cast);
        assertEquals(new Act1SkillSequence.Pose("buff motion", 0, 1000, null, true, null), cast.poses().get(0));
        assertEquals("waiting motion", cast.poses().get(1).action());
        var start = visual(cast, MAGE + "aurashieldstart.ani");
        var loop = visual(cast, MAGE + "aurashieldloop.ani");
        assertEquals(1000, start.start());
        assertEquals(start.end(), loop.start());
        assertEquals(1160, loop.start());
        assertEquals(0, start.y());
        assertEquals(0, loop.y());
        assertTrue(loop.end() > loop.start());
        assertTrue(loop.end() < 5000);
        assertFalse(cast.visuals().stream().anyMatch(v -> v.path().contains("hit") || v.path().contains("shieldend")));
        assertEquals(32, visual(cast, COMMON + "buffstarticon.ani").style().spriteOverride().index());

        assets.put("skill/mageskill.lst", "38 `mage/aurashield.skl`");
        assertNull(Act1BuffCast.find(SHIELD, "[casting time] 1000 1000", assets::get, BUFF_ACTIONS, 1));
    }

    @Test void commonBuffUsesTheCatalogOfEachJobAndTheSelectedLevelCastingTime() throws Exception {
        var assets = Map.of("skill/gunnerskill.lst", "175 `gunner/jumppowerup.skl`",
                COMMON + "buffstarticon.ani", animation(1000));
        var cast = Act1BuffCast.find("skill/gunner/jumppowerup.skl",
                "[casting time] 400 200\n[maximum level] 11", assets::get, BUFF_ACTIONS, 6);

        assertNotNull(cast);
        var icon = visual(cast, COMMON + "buffstarticon.ani");
        assertEquals(300, icon.start());
        assertEquals(4, icon.style().spriteOverride().index());
        assertNull(Act1BuffCast.find("skill/priest/jumppowerup.skl", "[casting time] 400 200",
                assets::get, BUFF_ACTIONS, 1));
    }

    @Test void chargeGlowAndParticlesUseTheIdleBodySpriteTopPlusThirty() throws Exception {
        String path = "skill/mage/jumppowerup.skl";
        String text = "[casting time] 1000";
        var assets = Map.of(
                "skill/mageskill.lst", "175 `mage/jumppowerup.skl`",
                "character/mage/mage.chr", "[waiting motion] `animation/stay.ani` [buff motion] `animation/buff.ani`",
                "character/mage/animation/stay.ani", "[FRAME000] [IMAGE] `body%04d.img` 0 [IMAGE POS] 0 -146 [IMAGE RATE] 1 1.25 [DELAY] 1000",
                "character/mage/animation/buff.ani", animation(1000),
                COMMON + "buffchargeglow.ani", animation(1000),
                COMMON + "buffstarticon.ani", animation(1000),
                "common/commoneffect/particle/buffchargeparticle.ptl", """
                        [life time] 300
                        [creation frequency] -1
                        [maximum create number] 1
                        [object type] `[animation]`
                        [object] `../Animation/BuffChargeGlow.ani` -1
                        [/object]
                        """);
        var sprites = new ScenePreview.SpriteLookup() {
            public NpkResources.Info info(String image, int index) {
                int trimY = image.equals("body0000.img") ? 19 : 0;
                return new NpkResources.Info(1, 1, 0, trimY, 1, 24);
            }

            public NpkResources.Sprite sprite(String image, int index) {
                var pixels = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                pixels.setRGB(0, 0, 0xffffffff);
                return new NpkResources.Sprite(pixels, 0, info(image, index).y(), 1, 24, Path.of("fixture.npk"), image, index, index);
            }
        };
        var preview = GamePreview.load(path, text, assets::get, assets.keySet(), sprites, GamePreview.Options.defaults());

        // ACT1 uses trunc((146 − 19) × 1.25) + 30, even during the casting pose.
        assertEquals(-188, draw(preview, 100, "跃翔 · 聚气").y());
        assertEquals(-188, draw(preview, 100, "跃翔 · 聚气粒子").y());
        assertEquals(-188, draw(preview, 1100, "跃翔 · 生效图标").y());
    }

    @Test void slowHealUsesTheRecipientLoopAfterThePriestRelease() throws Exception {
        var assets = Map.of("skill/priestskill.lst", "51 `priest/slowheal.skl`",
                PRIEST + "slowhealcast1.ani", animation(320), PRIEST + "slowhealcast2.ani", animation(560),
                PRIEST + "slowhealcastwave.ani", animation(480),
                COMMON + "priestslowhealb.ani", animation(600), COMMON + "priestslowhealf.ani", animation(600));
        var actions = Map.of("throw motion 2-1", "animation/pray1.ani", "throw motion 2-2", "animation/pray2.ani",
                "throw motion 3-1", "animation/deliver1.ani", "throw motion 3-2", "animation/deliver2.ani",
                "waiting motion", "animation/stay.ani");

        var cast = Act1BuffCast.find("skill/priest/slowheal.skl",
                "[casting time] 800\n[static data] 900 500\n[level info] 2 20000 30",
                assets::get, actions, 1);

        assertNotNull(cast);
        assertEquals("throw motion 3-1", cast.poses().get(0).action());
        assertEquals(new Act1SkillSequence.Pose("throw motion 3-2", 800, 1300), cast.poses().get(1));
        assertEquals(800, visual(cast, PRIEST + "slowhealcast2.ani").start());
        assertEquals(800, visual(cast, PRIEST + "slowhealcastwave.ani").start());
        var back = visual(cast, COMMON + "priestslowhealb.ani");
        var front = visual(cast, COMMON + "priestslowhealf.ani");
        assertEquals(800, back.start());
        assertEquals(back.end(), front.end());
        assertTrue(back.layer() < front.layer());
        assertTrue(front.end() < 5000);
    }

    @Test void divineLightUsesBothAppearLayersThenTheThreeStayLayersAtTheActorOrigin() throws Exception {
        var folder = PRIEST + "divinelight/";
        var assets = Map.of("skill/priestskill.lst", "22 `priest/divinelight.skl`",
                COMMON + "buffstarticon.ani", animation(1000),
                folder + "appear1.ani", animation(560), folder + "appear2.ani", animation(560),
                folder + "stay1.ani", animation(80), folder + "stay2.ani", animation(320),
                folder + "stay3.ani", animation(1920), folder + "damage1.ani", animation(320),
                folder + "disappear1.ani", animation(480));
        var cast = Act1BuffCast.find("skill/priest/divinelight.skl", "[casting time] 700 [level info] 1 7500",
                assets::get, BUFF_ACTIONS, 1);

        assertNotNull(cast);
        assertEquals("buff motion", cast.poses().get(0).action());
        assertEquals(44, visual(cast, COMMON + "buffstarticon.ani").style().spriteOverride().index());
        var back = visual(cast, folder + "appear1.ani");
        var front = visual(cast, folder + "appear2.ani");
        assertEquals(700, back.start());
        assertEquals(1260, back.end());
        assertEquals(back.end(), front.end());
        assertTrue(back.layer() < front.layer());
        for (String file : List.of("stay1.ani", "stay2.ani", "stay3.ani")) {
            var stay = visual(cast, folder + file);
            assertEquals(1260, stay.start());
            assertEquals(3060, stay.end());
            assertEquals(0, stay.x());
            assertEquals(0, stay.y());
        }
        assertFalse(cast.visuals().stream().anyMatch(v -> v.path().contains("/damage") || v.path().contains("/disappear")));
    }

    @Test void chakraSkillsStartTheirFrontAndBackEffectsTogetherAtTheGroundAnchor() throws Exception {
        for (var entry : Map.of("chakraofpassion", 47, "chakraofcalmness", 48).entrySet()) {
            String stem = entry.getKey();
            String folder = PRIEST + stem + "/";
            var assets = new HashMap<String,String>();
            assets.put("skill/priestskill.lst", entry.getValue() + " `priest/" + stem + ".skl`");
            assets.put(COMMON + "buffstarticon.ani", animation(1000));
            assets.put(folder + "cast.ani", animation(480));
            for (int index = 1; index <= 3; index++) {
                assets.put(folder + "front" + index + ".ani", animation(2400));
                assets.put(folder + "back" + index + ".ani", animation(2400));
            }
            var cast = Act1BuffCast.find("skill/priest/" + stem + ".skl", "[casting time] 700",
                    assets::get, BUFF_ACTIONS, 1);

            assertNotNull(cast, stem);
            assertEquals(entry.getValue() == 47 ? 47 : 46, visual(cast, COMMON + "buffstarticon.ani").style().spriteOverride().index());
            var preparation = visual(cast, folder + "cast.ani");
            assertEquals(0, preparation.start());
            assertEquals(480, preparation.end());
            assertEquals(0, preparation.y());
            for (int index = 1; index <= 3; index++) {
                var front = visual(cast, folder + "front" + index + ".ani");
                var back = visual(cast, folder + "back" + index + ".ani");
                assertEquals(700, front.start());
                assertEquals(front.start(), back.start());
                assertEquals(2500, front.end());
                assertEquals(front.end(), back.end());
                assertEquals(0, front.y());
                assertEquals(0, back.y());
                assertTrue(back.layer() < front.layer());
            }
        }
    }

    private static String animation(int delay) {
        return "[FRAME MAX] 1\n[FRAME000]\n[IMAGE] `test.img` 0\n[DELAY] " + delay;
    }

    private static Act1SkillSequence.Visual visual(Act1SkillSequence cast, String path) {
        return cast.visuals().stream().filter(v -> v.path().equals(path)).findFirst().orElseThrow();
    }

    private static GamePreview.Draw draw(GamePreview preview, long time, String label) throws IOException {
        return preview.render(time, false).draws().stream().filter(d -> d.label().equals(label)).findFirst().orElseThrow();
    }
}
