package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1FighterIllusionCombatTest {
    private static final String SKILL = "skill/fighter/illusionbomb.skl";
    private static final String TEXT = "[casting time] 600 600 [pre required skill] 8 2 [static data] 120 700";
    private static final String CLONES = "monster/fighter/animation/";
    private static final String COMMON = "passiveobject/common/";

    @Test void contactSpawnsTheAuthoredLightBlastAtTheFrontCloneAndEndsItsBody() throws Exception {
        var preview = preview(files(), "");
        assertTrue(attacks(preview, 1149, false).isEmpty());
        var hit = attacks(preview, 1150, false);
        assertEquals(1, hit.size());
        var blast = hit.get(0);
        assertEquals(new CombatFrame.Box(-78, -54, -60, 156, 108, 120), blast.box());
        assertEquals(100, blast.x());
        assertEquals(-50, blast.y());
        assertEquals(-100, attacks(preview, 1150, true).get(0).x());
        assertEquals(1, attacks(preview, 1549, false).size());
        assertTrue(attacks(preview, 1550, false).isEmpty());
        var draws = preview.render(1150, false).draws();
        assertEquals(3, draws.stream().filter(draw -> draw.label().equals("幻影爆碎 · 分身维持")).count());
        assertEquals(1, draws.stream().filter(draw -> draw.label().equals("幻影爆碎 · 分身消失")).count());
        assertTrue(draws.stream().anyMatch(draw -> draw.label().equals("幻影爆碎 · 目标受击")));
    }

    @Test void clonePersistsUntilExpiryWhenItsAttackBoxMissesTheGoblin() throws Exception {
        var files = files();
        files.put(CLONES + "illusion.ani", activeClone("-23 200 14 47 30 83"));
        var preview = preview(files, "");
        assertTrue(attacks(preview, 1150, false).isEmpty());
        assertEquals(4, preview.render(1150, false).draws().stream()
                .filter(draw -> draw.label().equals("幻影爆碎 · 分身维持")).count());
        assertTrue(preview.render(7600, false).draws().stream()
                .anyMatch(draw -> draw.label().equals("幻影爆碎 · 分身消失")));
        assertTrue(attacks(preview, 7600, false).isEmpty());
    }

    private static Map<String, String> files() {
        var files = new HashMap<String, String>();
        files.put("skill/fighterskill.lst", "8 `Fighter/Kensin.skl` 69 `Fighter/IllusionBomb.skl`");
        files.put("skill/fighter/kensin.skl", "[level info] 3 1 7000 75 4 7000 100");
        files.put("character/fighter/fighter.chr", """
                [waiting motion] `body.ani` [throw motion 3-1] `body.ani` [throw motion 3-2] `body.ani`
                """);
        files.put("character/fighter/body.ani", visibleFrame(100));
        files.put("monster/monster.lst", "60001 `Fighter/FighterIllusion.mob`");
        files.put("monster/fighter/fighterillusion.mob", """
                [waiting motion] `Animation/IllusionCreate.ani`
                [etc motion] `Animation/IllusionCreateEffect.ani` `Animation/Illusion.ani` `Animation/IllusionEffect.ani` [/etc motion]
                """);
        files.put(CLONES + "illusioncreate.ani", visibleFrame(500));
        files.put(CLONES + "illusioncreateeffect.ani", visibleFrame(500));
        files.put(CLONES + "illusion.ani", activeClone("-23 -15 14 47 30 83"));
        files.put(CLONES + "illusioneffect.ani", visibleFrame(150));
        files.put(CLONES + "illusiondestroy.ani", visibleFrame(100));
        files.put(CLONES + "illusiondestroyeffect.ani", visibleFrame(1000));
        files.put(Act1SwordAsuraSupplement.GOBLIN + "stay.ani", visibleFrame(100) + " [DAMAGE BOX] -12 -8 0 24 16 60");
        files.put(Act1SwordAsuraSupplement.GOBLIN + "damage1.ani", visibleFrame(250));
        files.put("passiveobject/passiveobject.lst", "46004 `Common/LightExplosion.obj`");
        files.put(COMMON + "lightexplosion.obj", "[basic motion] `Animation/LightExplosion.ani`");
        files.put(COMMON + "animation/lightexplosion.ani", """
                [FRAME000] [DELAY] 400 [ATTACK BOX] -65 -45 -50 130 90 100
                """);
        return files;
    }

    private static String visibleFrame(int delay) {
        return "[FRAME000] [IMAGE] `sample.img` 0 [DELAY] " + delay;
    }

    private static String activeClone(String box) {
        return visibleFrame(50) + " [FRAME001] [IMAGE] `sample.img` 0 [DELAY] 100 [ATTACK BOX] " + box;
    }

    private static GamePreview preview(Map<String, String> files, String effect) throws Exception {
        var sprites = new ScenePreview.SpriteLookup() {
            @Override public NpkResources.Info info(String path, int frame) {
                return new NpkResources.Info(8, 8, 0, 0, 8, 8);
            }

            @Override public NpkResources.Sprite sprite(String path, int frame) {
                return new NpkResources.Sprite(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), 0, 0, 8, 8, Path.of("fixture.npk"), path, frame, 0);
            }
        };
        var options = new GamePreview.Options("fighter", "waiting motion", Map.of(), 1, true, effect);
        return GamePreview.load(SKILL, TEXT, files::get, files.keySet(), sprites, options);
    }

    private static List<CombatDebug.Volume> attacks(GamePreview preview, long time, boolean mirrored) throws Exception {
        return preview.render(time, mirrored, false, true).combatVolumes().stream()
                .filter(volume -> volume.kind() == CombatDebug.Kind.ATTACK).toList();
    }
}
