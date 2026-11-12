package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1MageSacrificeTest {
    private static final String PATH = "skill/mage/sacrificespirit.skl";
    private static final String FIRE = "skill/mage/sacrificespiritattackfire.skl";
    private static final String WATER = "skill/mage/sacrificespiritattackwater.skl";
    private static final String DARK = "skill/mage/sacrificespiritattackdark.skl";
    private static final String LIGHT = "skill/mage/sacrificespiritattacklight.skl";
    private static final String COMMON = "passiveobject/common/";
    private static final String FRAME = "[FRAME000] [IMAGE] `sample.img` 0 [DELAY] 100";

    @Test void ritualOffersAllFourOwnedSpiritScenariosAndKeepsTheEmptyCircle() throws Exception {
        var assets = assets();
        var preview = preview(PATH, assets, 1, "");
        assertEquals(List.of("mage-sacrifice:fire", "mage-sacrifice:water", "mage-sacrifice:dark", "mage-sacrifice:light",
                        "mage-sacrifice:group", "mage-sacrifice:light-group"),
                preview.effects().stream().map(GameEffectRecipes.Effect::key)
                        .filter(key -> key.startsWith("mage-sacrifice:")).toList());
        assertTrue(attacks(preview, 800, false).isEmpty());
        assertTrue(attacks(preview(PATH, assets, 1, "mage-sacrifice:fire"), 800, false)
                .stream().anyMatch(box -> box.box().width() == 195));
    }

    @Test void fireAndDarkPassivesUseTheExplosionsScaledByTheirOwnSkillLevel() throws Exception {
        var assets = assets();
        var fire = preview(FIRE, assets, 2, "");
        assertTrue(attacks(fire, 799, false).isEmpty());
        var fireBox = attacks(fire, 800, false).get(0);
        assertEquals(205, fireBox.x());
        assertEquals(260, fireBox.box().width());
        assertTrue(attacks(fire, 1200, false).isEmpty());
        var dark = preview(DARK, assets, 1, "");
        var darkBox = attacks(dark, 800, true).get(0);
        assertEquals(-205, darkBox.x());
        assertTrue(darkBox.facingLeft());
        assertEquals(112.5, darkBox.box().depth());
    }

    @Test void iceCreatesEightMovingNativeBoxesAndLightUsesAnEnemyPosition() throws Exception {
        var assets = assets();
        var ice = preview(WATER, assets, 1, "");
        var boxes = attacks(ice, 900, false);
        assertEquals(8, boxes.size());
        assertTrue(boxes.stream().anyMatch(box -> box.x() == 255 && Math.abs(box.y()) < .01));
        assertTrue(boxes.stream().anyMatch(box -> Math.abs(box.x() - 205) < .01 && Math.abs(box.y() - 14.5) < .01));
        assertTrue(boxes.stream().allMatch(box -> box.box().width() == 18));

        var light = preview(LIGHT, assets, 1, "");
        assertTrue(attacks(light, 799, false).isEmpty());
        var bolt = attacks(light, 800, false).get(0);
        assertEquals(280, bolt.x());
        assertEquals(42, bolt.box().width());
    }

    @Test void lightHitsAllTargetsInsideTheGroundRangeTogetherAndTheFourSpiritRitualKeepsAllElements() throws Exception {
        var assets = assets();
        var light = preview(LIGHT, assets, 1, "mage-sacrifice:light-group");
        assertTrue(attacks(light, 799, false).isEmpty());
        var boxes = light.render(800, false, false, true).combatVolumes();
        var targets = boxes.stream().filter(v -> v.kind() == CombatDebug.Kind.ENEMY_HURT).toList();
        var bolts = boxes.stream().filter(v -> v.kind() == CombatDebug.Kind.ATTACK).toList();
        assertEquals(4, targets.size());
        assertEquals(3, bolts.size());
        assertEquals(3, bolts.stream().map(CombatDebug.Volume::y).distinct().count());
        for (var target : targets) {
            boolean hasBolt = bolts.stream().anyMatch(bolt -> bolt.x() == target.x() && bolt.y() == target.y());
            assertEquals(!target.label().contains("范围外"), hasBolt);
        }
        assertTrue(attacks(light, 900, false).isEmpty());

        var group = preview(PATH, assets, 1, "mage-sacrifice:group");
        var byLabel = attacks(group, 800, false).stream().collect(java.util.stream.Collectors.groupingBy(CombatDebug.Volume::label));
        assertEquals(1, byLabel.get("献祭攻击 · 火焰爆炸").size());
        assertEquals(8, byLabel.get("献祭攻击 · 冰柱").size());
        assertEquals(1, byLabel.get("献祭攻击 · 暗影爆炸").size());
        assertEquals(3, byLabel.get("献祭攻击 · 落雷").size());
    }

    @Test void summonedSpiritAttacksWithItsOwnNativeFramesAndKeepsASpawnOnlyChoice() throws Exception {
        var assets = assets();
        String summon = "skill/mage/summonspiritfire.skl";
        assets.merge("skill/mageskill.lst", "21 `mage/summonspiritfire.skl`", (list, entry) -> list + "\n" + entry);
        assets.put(summon, "[type] `[active]` [casting time] 700 [static data] 0 0 60007 3 600 640 1 0");
        assets.put("monster/fire/fire.mob", "[waiting motion] `stay.ani` [attack motion] `punch.ani`");
        assets.put("monster/fire/punch.ani", FRAME + " [FRAME001] [IMAGE] `sample.img` 0 [DELAY] 70 "
                + "[ATTACK BOX] 0 -10 -20 70 20 65 [FRAME002] [IMAGE] `sample.img` 0 [DELAY] 250");
        assets.put("monster/goblin/animation_goblin/stay.ani", FRAME + " [ATTACK BOX] 0 -10 -20 25 20 65");
        var preview = preview(summon, assets, 1, "");
        assertTrue(attacks(preview, 1399, false).isEmpty());
        var attack = attacks(preview, 1400, false);
        assertEquals(1, attack.size());
        assertEquals(100, attack.get(0).x());
        assertEquals(70, attack.get(0).box().width());
        assertTrue(attacks(preview, 1470, false).isEmpty());
        assertTrue(preview.effects().stream().anyMatch(effect -> effect.key().equals("mage-summon:appear")));
        assertTrue(attacks(preview(summon, assets, 1, "mage-summon:appear"), 1400, false).isEmpty());
    }

    @Test void sandorSwingsWithAnEquipmentBoxWhenHisBodyHasNoAttackBox() throws Exception {
        var assets = assets();
        String summon = "skill/mage/summonsandor.skl";
        assets.merge("skill/mageskill.lst", "44 `mage/summonsandor.skl`", (list, entry) -> list + "\n" + entry);
        assets.put(summon, "[type] `[active]` [casting time] 1200 [static data] 2 0 901 2 800 100 1 0");
        assets.put("character/mage/mage.chr", assets.get("character/mage/mage.chr")
                + " [throw motion 3-1] `body.ani` [throw motion 3-2] `body.ani`");
        assets.merge("monster/monster.lst", "901 `soldier/sandor.mob`", (list, entry) -> list + "\n" + entry);
        assets.put("monster/soldier/sandor.mob", "[waiting motion] `stay.ani` [attack motion] `attack.ani`");
        assets.put("monster/soldier/stay.ani", FRAME);
        assets.put("monster/soldier/attack.ani", FRAME + " [FRAME001] [IMAGE] `sample.img` 0 [DELAY] 40");
        String folder = "equipment/monster/soldier/sandorexpeller/";
        assets.put(folder + "sandorexpellerleftarm.equ", "[layer] 500 [attack motion] `armattack.ani`");
        assets.put(folder + "armattack.ani", FRAME + " [FRAME001] [IMAGE] `sample.img` 0 [DELAY] 40 "
                + "[ATTACK BOX] -72 -19 5 145 35 65");
        var preview = preview(summon, assets, 1, "");
        assertTrue(attacks(preview, 2099, false).isEmpty());
        var swing = attacks(preview, 2100, false);
        assertEquals(1, swing.size());
        assertEquals(145, swing.get(0).box().width());
        assertTrue(attacks(preview, 2140, false).isEmpty());
    }

    private static List<CombatDebug.Volume> attacks(GamePreview preview, long time, boolean left) throws Exception {
        return preview.render(time, left, false, true).combatVolumes().stream()
                .filter(box -> box.kind() == CombatDebug.Kind.ATTACK).toList();
    }

    private static GamePreview preview(String path, Map<String, String> assets, int level, String choice) throws Exception {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffffffff);
        var sprites = new ScenePreview.SpriteLookup() {
            @Override public NpkResources.Info info(String path, int frame) {
                return new NpkResources.Info(1, 1, 0, 0, 1, 1);
            }
            @Override public NpkResources.Sprite sprite(String path, int frame) {
                return new NpkResources.Sprite(image, 0, 0, 1, 1, Path.of("fixture.npk"), path, frame, frame);
            }
        };
        return GamePreview.load(path, assets.get(path), assets::get, assets.keySet(), sprites,
                new GamePreview.Options("mage", "waiting motion", Map.of(), level, true, choice));
    }

    private static Map<String, String> assets() {
        var files = new HashMap<String, String>();
        files.put("skill/mageskill.lst", "49 `mage/sacrificespirit.skl` 51 `mage/sacrificespiritattackfire.skl` "
                + "52 `mage/sacrificespiritattackwater.skl` 53 `mage/sacrificespiritattackdark.skl` "
                + "54 `mage/sacrificespiritattacklight.skl`");
        files.put(PATH, "[type] `[active]` [casting time] 800 [static data] 205 2 700 [level info] 1 400");
        files.put(FIRE, "[type] `[passive]` [level info] 1 150 200");
        files.put(WATER, "[type] `[passive]` [level info] 1 500");
        files.put(DARK, "[type] `[passive]` [level info] 1 125");
        files.put(LIGHT, "[type] `[passive]` [level info] 1 150");
        files.put("character/mage/mage.chr", "[waiting motion] `body.ani` [throw motion 4-1] `body.ani` [throw motion 4-2] `body.ani`");
        files.put("character/mage/body.ani", FRAME);
        files.put("character/mage/effect/animation/sacrificespirit1.ani", FRAME);
        files.put("character/mage/effect/animation/sacrificespirit2.ani", FRAME);
        files.put("monster/monster.lst", "60006 `water/water.mob` 60007 `fire/fire.mob` "
                + "60008 `dark/dark.mob` 60009 `light/light.mob`");
        for (String spirit : List.of("fire", "water", "dark", "light")) {
            files.put("monster/" + spirit + "/" + spirit + ".mob", "[waiting motion] `stay.ani`");
            files.put("monster/" + spirit + "/stay.ani", FRAME);
        }
        files.put("monster/goblin/animation_goblin/stay.ani", FRAME + " [DAMAGE BOX] -20 -10 0 40 20 65");
        files.put("passiveobject/passiveobject.lst", "40002 `common/fire.obj` 42007 `common/ice.obj` "
                + "44001 `common/dark.obj` 23019 `common/bolt.obj`");
        files.put(COMMON + "fire.obj", "[basic motion] `fire.ani`");
        files.put(COMMON + "fire.ani", "[FRAME000] [DELAY] 400 [ATTACK BOX] -65 -40 -50 130 80 100");
        files.put(COMMON + "dark.obj", "[basic motion] `dark.ani`");
        files.put(COMMON + "dark.ani", "[FRAME000] [DELAY] 400 [ATTACK BOX] -65 -45 -50 130 90 100");
        files.put(COMMON + "ice.obj", "[basic motion] `ice.ani` [etc motion] `spike.ani`");
        files.put(COMMON + "ice.ani", "[LOOP] 1 [FRAME000] [DELAY] 100 [ATTACK BOX] -9 -6 0 18 12 41");
        files.put(COMMON + "spike.ani", FRAME);
        files.put(COMMON + "bolt.obj", "[basic motion] `bolt.ani`");
        files.put(COMMON + "bolt.ani", FRAME + " [ATTACK BOX] -21 -17 0 42 34 470");
        files.put("monster/spirit/particle/naiassacrificeattack.ptl",
                "[x axis] [move type] `[straight line]` [move variable 1] 300 300");
        return files;
    }
}
