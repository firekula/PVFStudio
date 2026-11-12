package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1ChaserPressTest {
    @Test void jumpsAndGathersBeforeTheForwardImpact() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/mageskill.lst", "10 `Mage/ChasersExplosion.skl`");
        files.put("character/mage/mage.chr",
                "[etc motion] " + "`Animation/Other.ani` ".repeat(8) + "`Animation/ChasersExplosion.ani` "
                        + "[waiting motion] `Animation/Stay.ani`");
        files.put("character/mage/animation/stay.ani", animation("body", 100));
        files.put("equipment/title.equ", "[equipment type] `[title name]` [custom animation] `Title.ani`");
        files.put("equipment/title.ani", animation("title", 100));
        var body = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            body.append(String.format(Locale.ROOT, "[FRAME%03d] [IMAGE] `body.img` %d [DELAY] %d%n",
                    i, i, i == 6 ? 10000 : 80));
        }
        files.put("character/mage/animation/chasersexplosion.ani", body.toString());
        String effects = "character/mage/effect/animation/chasersexplosion/";
        files.put(effects + "charge.ani", "[LOOP] 1 " + animation("charge", 80));
        files.put(effects + "sizeup.ani", animation("absorb", 180));
        files.put(effects + "smash.ani", animation("smash", 160));
        files.put("passiveobject/passiveobject.lst", "23002 `mage/chaser.obj` 23030 `mage/press.obj`");
        files.put("passiveobject/mage/chaser.obj", "[basic motion] `orb.ani`");
        files.put("passiveobject/mage/orb.ani", animation("orb", 80));
        files.put("passiveobject/mage/press.obj", "[basic motion] `Animation/ChasersExplosion/Floor.ani`");
        files.put("passiveobject/mage/animation/chasersexplosion/floor.ani", animation("floor", 275));
        files.put("passiveobject/mage/animation/chasersexplosion/exp.ani", animation("explosion", 400));
        var preview = GamePreview.load("skill/mage/chasersexplosion.skl", "[casting time] 1000 1000",
                files::get, files.keySet(), new Pixels(), new GamePreview.Options("mage", "waiting motion",
                        Map.of("title name", "equipment/title.equ"), 1, true, ""));

        assertEquals(3, preview.render(0, false).draws().stream()
                .filter(draw -> draw.prepared().source().img().equals("orb.img")).count());
        assertEquals(-70, draw(preview, "body", 480, false).y());
        assertEquals(6, draw(preview, "body", 1479, false).prepared().source().requestedFrame());
        assertEquals(-99, draw(preview, "charge", 780, false).y());
        assertEquals(-76, draw(preview, "absorb", 780, false).x());
        assertEquals(7, draw(preview, "body", 1480, false).prepared().source().requestedFrame());
        assertFalse(preview.render(1639, false).draws().stream()
                .anyMatch(draw -> draw.prepared().source().img().equals("explosion.img")));
        assertEquals(9, draw(preview, "body", 1640, false).prepared().source().requestedFrame());
        assertEquals(77, draw(preview, "floor", 1640, false).x());
        assertEquals(-77, draw(preview, "explosion", 1640, true).x());
        double titleY = draw(preview, "title", 0, false).y();
        assertEquals(-9, titleY);
        assertEquals(titleY - 70, draw(preview, "title", 900, false).y(), .001);
        assertEquals(titleY - 10, draw(preview, "title", 1640, false).y(), .001);
    }

    private static GamePreview.Draw draw(GamePreview preview, String image, long time, boolean mirrored) throws Exception {
        return preview.render(time, mirrored).draws().stream()
                .filter(draw -> draw.prepared().source().img().equals(image + ".img")).findFirst().orElseThrow();
    }

    private static String animation(String image, int duration) {
        return "[FRAME000] [IMAGE] `" + image + ".img` 0 [DELAY] " + duration;
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
