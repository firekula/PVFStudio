package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1PriestProjectileTest {
    @Test void wallRewindsItsSustainedFramesAndFadesWhileAdvancing() throws Exception {
        var preview = preview("deflectwall", "[level info] 2 825 10000");
        assertEquals("throw motion 3-1", preview.appearance().action());
        assertFalse(has(preview, "wall", 699));
        assertEquals(60, draw(preview, "wall", 700, false).x());
        assertEquals(5, draw(preview, "wall", 1449, false).prepared().source().requestedFrame());
        assertEquals(3, draw(preview, "wall", 1450, false).prepared().source().requestedFrame());
        assertEquals(-90, draw(preview, "wall", 1700, true).x());
        assertEquals(.5, draw(preview, "wall", 10200, false).opacity(), .001);
        assertFalse(has(preview, "wall", 10700));
    }

    @Test void lightGlobeStopsAtItsExplosionOrigin() throws Exception {
        var preview = preview("flashglobe", "[level info] 5 190 1329 5000 35 3000");
        assertEquals("throw motion 3-1", preview.appearance().action());
        assertEquals(7, draw(preview, "globe", 1459, false).prepared().source().requestedFrame());
        assertEquals(5, draw(preview, "globe", 1460, false).prepared().source().requestedFrame());
        assertTrue(has(preview, "globeup", 1460));
        assertTrue(has(preview, "globedown", 1460));
        assertFalse(has(preview, "globeblast", 5499));
        assertFalse(has(preview, "globe", 5500));
        assertEquals(310, draw(preview, "globeblast", 5500, false).x());
        assertEquals(-310, draw(preview, "globeblast", 5800, true).x());
    }

    @Test void editedFastWallCycleStaysVisibleThroughItsLifetime() throws Exception {
        var preview = preview("deflectwall", "", 1, 1, 1, 1, 1, 1);
        assertTrue(has(preview, "wall", 10699));
        assertTrue(preview.warnings().stream().noneMatch(warning -> warning.contains("256")));
    }

    @Test void talismanUsesTheNativeSpawnOffsetAndKeepsTheExplosionFacing() throws Exception {
        var preview = preview("enteringnirvana", "");
        assertEquals("throw motion 3-1", preview.appearance().action());
        var flying = draw(preview, "talisman", 500, true);
        assertEquals(-200, flying.x());
        assertEquals(-99, flying.y());
        assertTrue(flying.mirrored());
        assertFalse(has(preview, "talismanblast", 1099));
        var burst = draw(preview, "talismanblast", 1100, true);
        assertEquals(-200, burst.x());
        assertFalse(burst.mirrored());
        assertTrue(has(preview, "talismanblastglow", 1100));
    }

    private static GamePreview preview(String stem, String skill) throws Exception {
        return preview(stem, skill, 100, 100, 100, 150, 150, 150);
    }

    private static GamePreview preview(String stem, String skill, int... wallDelays) throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/priestskill.lst", "27 `priest/deflectwall.skl` 28 `priest/flashglobe.skl` 38 `priest/enteringnirvana.skl`");
        files.put("character/priest/priest.chr", "[waiting motion] `body.ani` [throw motion 3-1] `body.ani` "
                + "[throw motion 3-2] `body.ani`");
        files.put("character/priest/body.ani", animation("body", 80));
        files.put("passiveobject/passiveobject.lst", "24004 `priest/wall.obj` 24005 `priest/globe.obj` 24009 `priest/talisman.obj`");
        files.put("passiveobject/priest/wall.obj", "[basic motion] `wall.ani`");
        files.put("passiveobject/priest/wall.ani", animation("wall", wallDelays));
        files.put("passiveobject/priest/globe.obj", "[basic motion] `globe.ani` [etc motion] `globeup.ani` `globedown.ani` `globeblast.ani`");
        files.put("passiveobject/priest/globe.ani", animation("globe", 120, 120, 120, 120, 120, 120, 120, 120));
        files.put("passiveobject/priest/globeup.ani", animation("globeup", 120, 120, 120, 120, 120));
        files.put("passiveobject/priest/globedown.ani", animation("globedown", 120, 120, 120, 120, 120, 120, 120, 120));
        files.put("passiveobject/priest/globeblast.ani", animation("globeblast", 910));
        files.put("character/priest/particle/flashglobe.ptl", "[x axis] [move type] `[straight line]` [move variable 1] 50 50");
        files.put("passiveobject/priest/talisman.obj", "[basic motion] `talisman.ani` [etc motion] `talismanglow.ani` "
                + "`talismanblast.ani` `talismanblastglow.ani`");
        files.put("passiveobject/priest/talisman.ani", animation("talisman", 600));
        files.put("passiveobject/priest/talismanglow.ani", animation("talismanglow", 600));
        files.put("passiveobject/priest/talismanblast.ani", animation("talismanblast", 1900));
        files.put("passiveobject/priest/talismanblastglow.ani", animation("talismanblastglow", 1400));
        return GamePreview.load("skill/priest/" + stem + ".skl", skill, files::get, files.keySet(), new Pixels(),
                new GamePreview.Options("priest", "waiting motion", Map.of(), 1, true, ""));
    }

    private static GamePreview.Draw draw(GamePreview cast, String image, long time, boolean mirrored) throws Exception {
        return cast.render(time, mirrored).draws().stream()
                .filter(draw -> draw.prepared().source().img().equals(image + ".img")).findFirst().orElseThrow();
    }

    private static boolean has(GamePreview cast, String image, long time) throws Exception {
        return cast.render(time, false).draws().stream().anyMatch(draw -> draw.prepared().source().img().equals(image + ".img"));
    }

    private static String animation(String image, int... delays) {
        var text = new StringBuilder();
        for (int i = 0; i < delays.length; i++) {
            text.append(String.format(Locale.ROOT, "[FRAME%03d] [IMAGE] `%s.img` %d [DELAY] %d%n", i, image, i, delays[i]));
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
