package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Act1GunnerCastCombatTest {
    @Test void ordinaryFlameKeepsTheInvisibleAttackObjectUntilFiringStops() throws Exception {
        checkFlame(false, 400, 3000, 100, new int[]{3, 6});
    }

    @Test void focusedFlameUsesItsOwnExpansionFramesAndMirrorsAroundTheMuzzle() throws Exception {
        checkFlame(true, 300, 5000, 50, new int[]{1, 3, 4});
    }

    private void checkFlame(boolean focused, int startup, int firing, int delay, int[] counts) throws Exception {
        String name = focused ? "flamethrowerboost" : "flamethrower";
        String root = "character/gunner/";
        String object = "passiveobject/character/gunner/subweaponbullet" + name;
        var files = new HashMap<String, String>();
        files.put("skill/gunnerskill.lst", (focused ? 36 : 13) + " `Gunner/" + name + ".skl`");
        files.put(root + "gunner.chr", "[waiting motion] `stay.ani` [etc motion]"
                + " `SubWeaponFrontRapid1.ani` `SubWeaponFrontRapid2.ani` [/etc motion]");
        String pose = "[FRAME000] [IMAGE] `body.img` 0 [DELAY] 400";
        for (String motion : List.of("stay", "subweaponfrontrapid1", "subweaponfrontrapid2"))
            files.put(root + motion + ".ani", pose);
        files.put("passiveobject/passiveobject.lst", (focused ? 22204 : 22201)
                + " `Character/Gunner/SubWeaponBullet" + name + ".obj`");
        files.put(object + ".obj", "[basic motion] `SubWeaponBullet" + name + ".ani`");
        var animation = new StringBuilder("[LOOP] 0 ");
        for (int frame = 0; frame < counts.length; frame++) {
            animation.append("[FRAME%03d] [IMAGE] `` 0 [DELAY] %d ".formatted(frame, delay));
            for (int box = 0; box < counts[frame]; box++)
                animation.append("[ATTACK BOX] %d -10 -20 30 20 40 ".formatted(box * 30));
        }
        files.put(object + ".ani", animation.toString());
        ScenePreview.SpriteLookup sprites = new ScenePreview.SpriteLookup() {
            public NpkResources.Info info(String path, int frame) {
                return new NpkResources.Info(2, 2, 0, 0, 2, 2);
            }
            public NpkResources.Sprite sprite(String path, int frame) {
                var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, 0xffffffff);
                return new NpkResources.Sprite(image, 0, 0, 2, 2, Path.of("fixture.npk"), path, frame, frame);
            }
        };
        var preview = GamePreview.load("skill/gunner/" + name + ".skl",
                "[static data] 700 " + startup + " [level info] 4 100 " + firing + " 250 10",
                files::get, files.keySet(), sprites, GamePreview.Options.defaults());
        assertTrue(attacks(preview, startup - 1, false).isEmpty());
        for (int frame = 0; frame < counts.length; frame++)
            assertEquals(counts[frame], attacks(preview, startup + frame * delay, false).size());
        assertEquals(counts[counts.length - 1], attacks(preview, startup + firing - 1, false).size());
        assertTrue(attacks(preview, startup + firing, false).isEmpty());
        assertTrue(preview.render(startup + 1000, false).combatVolumes().isEmpty());
        var right = attacks(preview, startup + 1000, false).get(0);
        var left = attacks(preview, startup + 1000, true).get(0);
        assertEquals(141, right.x());
        assertEquals(-88, right.y());
        assertEquals(-141, left.x());
        assertEquals(right.y(), left.y());
        assertEquals(right.box().mirrored(), left.orientedBox());
        assertEquals(counts[0], preview.render(preview.duration() + startup, false, true, true)
                .combatVolumes().stream().filter(v -> v.kind() == CombatDebug.Kind.ATTACK).count());
    }

    private List<CombatDebug.Volume> attacks(GamePreview preview, long time, boolean left) throws Exception {
        return preview.render(time, left, false, true).combatVolumes().stream()
                .filter(v -> v.kind() == CombatDebug.Kind.ATTACK).toList();
    }
}
