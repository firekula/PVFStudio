package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1MageElementCastTest {
    @Test void jackOLanternStartsMovingAfterItsStartupAndShowsTheImpactAtItsSampleRange() throws Exception {
        var shortCast = preview("launchjol", "");
        assertEquals("throw motion 2-1", shortCast.appearance().action());
        assertEquals(68, draw(shortCast, "jolstart", 500, false).x());
        assertEquals(68, draw(shortCast, "jolmove", 660, false).x());
        assertEquals(-100, draw(shortCast, "jolmove", 760, true).x());
        assertTrue(imageDraws(shortCast, "blast", 1359, false).isEmpty());
        assertEquals(292, draw(shortCast, "blast", 1360, false).x());

        var charged = preview("launchjol", Act1MageElementCast.CHARGED);
        assertTrue(imageDraws(charged, "jolstart", 1299, false).isEmpty());
        assertEquals(68, draw(charged, "jolstart", 1300, false).x());
        assertEquals(404, draw(charged, "blast", 2510, false).x());
    }

    @Test void frostHeadStopsOnTheGroundBeforeItsNextBounce() throws Exception {
        var cast = preview("launchfrosthead", "");
        assertEquals("throw motion 4-1", cast.appearance().action());
        assertEquals(-100, draw(cast, "frost", 500, false).y());
        assertEquals(0, draw(cast, "frostland", 948, false).y());
        assertEquals(131.6, draw(cast, "frost", 1028, false).x(), .001);
        assertTrue(draw(cast, "frost", 1128, false).y() < -24);
    }

    @Test void frostHeadCreatesTheTargetAnchoredIceBurstOnlyOnAFullChargeHit() throws Exception {
        String choice = "mage-element:frost-charged-hit";
        var regular = preview("launchfrosthead", "mage-element:frost-hit");
        assertTrue(regular.effects().stream().anyMatch(effect -> effect.key().equals(choice)));
        assertTrue(imageDraws(regular, "iceburst", 888, false).isEmpty());
        assertFalse(imageDraws(regular, "frosthit", 888, false).isEmpty());

        var noTarget = preview("launchfrosthead", Act1MageElementCast.CHARGED);
        assertTrue(imageDraws(noTarget, "iceburst", 1688, false).isEmpty());

        var hit = preview("launchfrosthead", choice);
        assertTrue(imageDraws(hit, "iceburst", 1687, false).isEmpty());
        var iceBurst = draw(hit, "iceburst", 1688, false);
        var target = draw(hit, "goblinhit", 1688, false);
        assertEquals(target.x(), iceBurst.x());
        assertEquals(target.y(), iceBurst.y());
        assertEquals(-iceBurst.x(), draw(hit, "iceburst", 1688, true).x());
    }

    @Test void fluorescentReleasesThreeIndependentOrbitPositions() throws Exception {
        var cast = preview("launchfluorescent", "");
        assertEquals("throw motion 3-1", cast.appearance().action());
        var initial = imageDraws(cast, "fluorescent", 500, false);
        assertEquals(3, initial.size());
        assertEquals(3, initial.stream().map(GamePreview.Draw::x).distinct().count());
        assertNotEquals(initial.get(0).x(), draw(cast, "fluorescent", 700, false).x());
        assertTrue(imageDraws(cast, "fluorescent", 2500, false).isEmpty());
    }

    @Test void blackCatReturnsAfterItsTurn() throws Exception {
        var cast = preview("launchblackcat", "");
        assertEquals("throw motion 1-1", cast.appearance().action());
        assertTrue(draw(cast, "cat2", 740, false).x() > draw(cast, "cat2", 700, false).x());
        assertEquals(219, draw(cast, "cat4", 760, false).x());
        assertTrue(draw(cast, "cat6", 1160, false).x() < draw(cast, "cat6", 1120, false).x());
        assertEquals(0, draw(cast, "cat8", 1180, false).x());

        var charged = preview("launchblackcat", Act1MageElementCast.CHARGED);
        assertTrue(imageDraws(charged, "cat4", 1626, false).isEmpty());
        assertEquals(309, draw(charged, "cat4", 1627, false).x());
        assertEquals(2080, charged.duration());
    }

    @Test void voidLoopsOnlyItsTravelFramesBeforeFading() throws Exception {
        var cast = preview("void", "");
        assertEquals(0, draw(cast, "void2", 800, false).prepared().source().requestedFrame());
        assertEquals(6, draw(cast, "void2", 1499, false).prepared().source().requestedFrame());
        assertEquals(3, draw(cast, "void2", 1500, false).prepared().source().requestedFrame());
        assertEquals(3, draw(cast, "void2", 1900, false).prepared().source().requestedFrame());
        assertEquals(8, draw(cast, "void2", 5800, false).prepared().source().requestedFrame());
        assertEquals(288, draw(cast, "void2", 5800, false).x());
        assertTrue(imageDraws(cast, "void2", 6100, false).isEmpty());
    }

    private static GamePreview preview(String stem, String effect) throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/mageskill.lst", "15 `mage/launchjol.skl` 16 `mage/launchfrosthead.skl` "
                + "17 `mage/launchfluorescent.skl` 18 `mage/launchblackcat.skl` 26 `mage/void.skl`");
        StringBuilder character = new StringBuilder("[waiting motion] `body.ani`");
        for (int group = 1; group <= 4; group++) {
            character.append(" [throw motion ").append(group).append("-1] `body.ani` [throw motion ")
                    .append(group).append("-2] `body.ani`");
        }
        files.put("character/mage/mage.chr", character.toString());
        files.put("character/mage/body.ani", animation("body", 100));
        files.put("passiveobject/passiveobject.lst", "23008 `mage/jol.obj` 23009 `mage/frost.obj` "
                + "23010 `mage/fluorescent.obj` 23011 `mage/cat.obj` 23012 `mage/void.obj` 23016 `mage/blast.obj` "
                + "23026 `mage/iceburst.obj`");
        files.put("passiveobject/mage/jol.obj", "[basic motion] `jolstart.ani` [etc motion] `jolmove.ani` `jolstartfire.ani` `jolmovefire.ani`");
        files.put("passiveobject/mage/jolstart.ani", animation("jolstart", 80, 80));
        for (String name : List.of("jolmove", "jolstartfire", "jolmovefire", "frost", "frostland", "fluorescent", "fluorescentlayer")) {
            files.put("passiveobject/mage/" + name + ".ani", animation(name, 80));
        }
        files.put("passiveobject/mage/frost.obj", "[basic motion] `frost.ani` [etc motion] `frostland.ani`");
        files.put("passiveobject/mage/animation/launchfrostheadhit.ani", animation("frosthit", 90, 90));
        files.put("passiveobject/mage/iceburst.obj", "[basic motion] `burstclock.ani` [string data] `iceburst.ptl`");
        files.put("passiveobject/mage/burstclock.ani", "[FRAME000] [DELAY] 400");
        files.put("passiveobject/mage/iceburst.ptl", "[creation frequency] -1 [maximum create number] 1 "
                + "[life time] 500 [object type] `[animation]` [object] `iceburst.img` 0");
        files.put(Act1SwordAsuraSupplement.GOBLIN + "stay.ani", animation("goblin", 100));
        files.put(Act1SwordAsuraSupplement.GOBLIN + "damage1.ani", animation("goblinhit", 100));
        files.put("passiveobject/mage/fluorescent.obj", "[basic motion] `fluorescent.ani` [etc motion] `fluorescentlayer.ani`");
        var cat = new StringBuilder("[basic motion] `` [etc motion]");
        for (int i = 0; i < 10; i++) {
            cat.append(" `cat").append(i).append(".ani`");
            files.put("passiveobject/mage/cat" + i + ".ani", animation("cat" + i, 80));
        }
        files.put("passiveobject/mage/cat.obj", cat.toString());
        files.put("passiveobject/mage/void.obj", "[basic motion] `void1.ani` [etc motion] `void2.ani`");
        for (String name : List.of("void1", "void2")) {
            files.put("passiveobject/mage/" + name + ".ani", animation(name, 100, 100, 100, 100, 100, 100, 100, 10000, 100, 100, 100));
        }
        files.put("passiveobject/mage/blast.obj", "[basic motion] `fireburst.ani` [string data] `puff.ptl`");
        files.put("passiveobject/mage/fireburst.ani", "[FRAME000] [DELAY] 400 [ATTACK BOX] -65 -40 -50 130 80 100");
        files.put("passiveobject/mage/puff.ptl", "[creation frequency] -1 [maximum create number] 1 "
                + "[life time] 100 [object type] `[animation]` [object] `blast.img` 0");
        files.put("character/mage/particle/launchjol.ptl", "[x axis] [move type] `[straight line]` [move variable 1] 320 320");
        files.put("character/mage/particle/launchfrosthead.ptl",
                "[x axis] [move type] `[straight line]` [move variable 1] 200 200 [gravity] 1000 [landing type] `[stop]`");
        files.put("character/mage/particle/launchfluorescent.ptl", "[x axis] [move type] `[sin]` "
                + "[move variable 1] 50 100 [move variable 2] 1500 3000 [y axis] [move type] `[cos]` "
                + "[move variable 1] 50 100 [move variable 2] 1500 3000 [changing term] 5000");
        files.put("character/mage/particle/void.ptl", "[x axis] [move type] `[straight line]` [move variable 1] 50 50");
        return GamePreview.load("skill/mage/" + stem + ".skl", "", files::get, files.keySet(), new Pixels(),
                new GamePreview.Options("mage", "waiting motion", Map.of(), 1, true, effect));
    }

    private static GamePreview.Draw draw(GamePreview cast, String image, long time, boolean mirror) throws Exception {
        return imageDraws(cast, image, time, mirror).stream().findFirst().orElseThrow();
    }

    private static List<GamePreview.Draw> imageDraws(GamePreview cast, String image, long time, boolean mirror) throws Exception {
        return cast.render(time, mirror).draws().stream().filter(draw -> draw.prepared().source().img().equals(image + ".img")).toList();
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
