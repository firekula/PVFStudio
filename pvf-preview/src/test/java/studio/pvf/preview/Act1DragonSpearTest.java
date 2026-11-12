package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1DragonSpearTest {
    private static final String SKILL = "[static data] 7 [level info] 6 350 744 0 120 2046 135 700 2704 30 150 7436 169";
    private static final String EQUIPMENT = "equipment/coat.equ";
    private static final String EFFECTS = "character/mage/effect/animation/dragonspear/";

    @Test void hitExtendsTheSpearAndPlaysSevenBlastsBeforeTheFinale() throws Exception {
        var preview = preview("", 1);
        assertTrue(has(preview, "spearattack2", 1759));
        assertFalse(has(preview, "chaser1", 1759));
        assertEquals(7, draws(preview, "chaser1", 1760));
        assertTrue(has(preview, "coat", 1760));
        assertFalse(has(preview, "burst", 2259));
        for (int i = 0; i < 7; i++) {
            assertEquals(6 - i, draws(preview, "chaser1", 2260 + 140L * i));
        }
        assertFalse(has(preview, "final", 3499));
        assertTrue(has(preview, "final", 3500));
        assertEquals(-322, preview.render(3500, true).draws().stream()
                .filter(draw -> draw.prepared().source().img().equals("final.img"))
                .findFirst().orElseThrow().x());
        assertTrue(has(preview, "finalfront", 3500));
        assertTrue(has(preview, "finalback", 3500));
        assertTrue(has(preview, "speardisappear2", 3800));
        assertTrue(has(preview, "coat", 4439));
        assertEquals(4440, preview.duration());
    }

    @Test void missSkipsTheDragonChasersAndTheExplosions() throws Exception {
        var preview = preview(Act1DragonSpear.MISS, 1);
        assertTrue(preview.effects().stream().anyMatch(effect -> effect.key().equals(Act1DragonSpear.MISS)));
        assertTrue(has(preview, "spearattack2", 1759));
        assertTrue(has(preview, "speardisappear2", 1760));
        assertFalse(has(preview, "chaser1", 1760));
        assertFalse(has(preview, "burst", 2260));
        assertEquals(2400, preview.duration());
    }

    @Test void attackSpeedRetimeKeepsTheFixedPostBlastDelay() throws Exception {
        var preview = preview("", 2);
        assertEquals(7, draws(preview, "chaser1", 1348));
        assertFalse(has(preview, "final", 2773));
        assertTrue(has(preview, "final", 2774));
        assertFalse(has(preview, "speardisappear2", 3073));
        assertTrue(has(preview, "speardisappear2", 3074));
        assertEquals(3562, preview.duration());
    }

    private static GamePreview preview(String choice, int level) throws Exception {
        var files = new HashMap<String, String>();
        String action = "[etc motion] " + "`other.ani` ".repeat(17);
        files.put("skill/mageskill.lst", "69 `Mage/DragonSpear.skl`");
        files.put("character/mage/mage.chr", action + "`Animation/DragonSpear.ani`");
        files.put(EQUIPMENT, "[equipment type] `[coat avatar]` [layer] 1001 " + action + "`coat.ani`");
        int[] body = {720, 80, 80, 80, 80, 80, 80, 80, 80, 80, 320, 5000};
        int[] coat = {720, 80, 80, 80, 80, 80, 80, 80, 80, 80, 320};
        files.put("character/mage/animation/dragonspear.ani", animation("body", body));
        files.put("equipment/coat.ani", animation("coat", coat));
        for (int i = 1; i <= 3; i++) {
            files.put(EFFECTS + "spearattack" + i + ".ani", animation("spearattack" + i, 1760));
            files.put(EFFECTS + "spearchaser" + i + ".ani", animation("spearchaser" + i, 80));
        }
        files.put(EFFECTS + "speardisappear1.ani", animation("speardisappear1", 80));
        files.put(EFFECTS + "speardisappear2.ani", animation("speardisappear2", 80, 80, 80, 80, 80, 80, 80, 80));
        for (int i = 1; i <= 2; i++) {
            files.put(EFFECTS + "chaser" + i + ".ani", animation("chaser" + i, 70, 70, 70, 70, 70, 0, 150, 0));
        }
        files.put("passiveobject/passiveobject.lst", "23031 `mage/burst.obj` 23032 `mage/final.obj`");
        files.put("passiveobject/mage/burst.obj", "[basic motion] `burst.ani`");
        files.put("passiveobject/mage/final.obj",
                "[basic motion] `final.ani` [add object effect] 2 `front.ani` 1 `back.ani` -1");
        files.put("passiveobject/mage/burst.ani", animation("burst", 400));
        files.put("passiveobject/mage/final.ani", animation("final", 434));
        files.put("passiveobject/mage/front.ani", animation("finalfront", 434));
        files.put("passiveobject/mage/back.ani", animation("finalback", 434));
        return GamePreview.load(Act1DragonSpear.PATH, SKILL, files::get, files.keySet(), new Pixels(),
                new GamePreview.Options("mage", "waiting motion", Map.of("coat avatar", EQUIPMENT), level, true, choice));
    }

    private static boolean has(GamePreview preview, String image, long time) throws Exception {
        return draws(preview, image, time) > 0;
    }

    private static long draws(GamePreview preview, String image, long time) throws Exception {
        return preview.render(time, false).draws().stream()
                .filter(draw -> draw.prepared().source().img().equals(image + ".img")).count();
    }

    private static String animation(String image, int... delays) {
        var text = new StringBuilder();
        for (int i = 0; i < delays.length; i++) {
            text.append(String.format(Locale.ROOT, "[FRAME%03d] [IMAGE] `%s.img` %d [DELAY] %d%n",
                    i, image, Math.min(i, 10), delays[i]));
        }
        return text.toString();
    }

    private static final class Pixels implements ScenePreview.SpriteLookup {
        private final BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        private Pixels() {
            image.setRGB(0, 0, 0xffffffff);
        }

        public NpkResources.Info info(String path, int frame) {
            return new NpkResources.Info(1, 1, 0, 0, 1, 1);
        }

        public NpkResources.Sprite sprite(String path, int frame) {
            return new NpkResources.Sprite(image, 0, 0, 1, 1, Path.of("fixture.npk"), path, frame, frame);
        }
    }
}
