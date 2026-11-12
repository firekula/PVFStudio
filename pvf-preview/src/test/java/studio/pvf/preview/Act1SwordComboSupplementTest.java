package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1SwordComboSupplementTest {
    private static final String ROOT = "character/swordman/";
    private static final String EFFECT = ROOT + "effect/animation/";

    @Test void shortSwordComboUsesTableOrderAndThirdSwingTrail() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "67 `Swordman/WeaponCombo.skl`");
        var actions = new HashMap<String, String>();
        for (int index = 28; index <= 30; index++) {
            actions.put("etc motion:" + index, "animation/test" + index + ".ani");
            files.put(ROOT + actions.get("etc motion:" + index), ani(60, 60, 60, 60, 60, 300));
        }
        files.put(EFFECT + "weaponcombo/short.ani", ani(60, 60, 60, 60, 60));
        var sequence = Act1SwordComboSupplement.find("skill/swordman/weaponcombo.skl", "", files::get, actions, 1, null);
        assertNotNull(sequence);
        assertEquals(List.of("etc motion:28", "etc motion:29", "etc motion:30"),
                sequence.poses().stream().map(Act1SkillSequence.Pose::action).toList());
        assertEquals(List.of(0L, 180L, 360L), sequence.poses().stream().map(Act1SkillSequence.Pose::start).toList());
        assertEquals(360, sequence.visuals().get(0).start());
    }

    @Test void bluntComboKeepsBothOutwardStrikeFramesBeforeTheNextInput() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "67 `Swordman/WeaponCombo.skl`");
        files.put("equipment/club.equ", "[equipment type] `[weapon]` [sub type] 2");
        var actions = new HashMap<String, String>();
        for (int index = 35; index <= 37; index++) {
            actions.put("etc motion:" + index, "animation/test" + index + ".ani");
            files.put(ROOT + actions.get("etc motion:" + index), ani(40, 40, 40, 40, 40, 300));
        }
        var sequence = Act1SwordComboSupplement.find("skill/swordman/weaponcombo.skl", "", files::get, actions, 1,
                "equipment/club.equ");
        assertNotNull(sequence);
        assertEquals(List.of(0L, 160L, 360L, 560L), sequence.poses().stream().map(Act1SkillSequence.Pose::start).toList());
    }

    @Test void shoulderChargeUsesTheFighterDashAttackAnimation() throws Exception {
        String catalog = "skill/fighterskill.lst";
        var files = Map.of(catalog, "1 `Fighter/ShoulderCharge.skl`", "character/fighter/animation/dashattack.ani", ani(100, 200, 50, 50, 201));
        var sequence = Act1FighterCast.find("skill/fighter/shouldercharge.skl", "[static data] 50 20", files::get,
                Map.of("dashattack motion", "animation/dashattack.ani"), 1, "", job -> 96);
        assertNotNull(sequence);
        assertEquals("dashattack motion", sequence.poses().get(0).action());
        assertEquals(601, sequence.poses().get(0).end());
        assertTrue(sequence.visuals().isEmpty());
    }

    @Test void katanaAirComboDrawsTheSecondCutAndHasNoLandingEffect() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "17 `Swordman/JumpAttackMulti.skl`");
        files.put("equipment/katana.equ", "[equipment type] `[weapon]` [sub type] 1");
        files.put(ROOT + "animation/jump.ani", ani(100, 10000, 50));
        files.put(ROOT + "animation/slash1.ani", ani(50, 80, 80, 80, 80));
        files.put(ROOT + "animation/slash2.ani", ani(50, 80, 80, 80, 80));
        files.put("common/commoneffect/particle/dustdownsmall.ptl", "[life time] 100");
        String blade = EFFECT + "jumpattackmulti/jumpchainattackslash2_upper_effect.ani";
        files.put(blade, ani(60, 60, 60, 60));
        var actions = Map.of("jump motion", "animation/jump.ani", "etc motion:23", "animation/slash1.ani",
                "etc motion:24", "animation/slash2.ani");
        var sequence = Act1SwordComboSupplement.find("skill/swordman/jumpattackmulti.skl", "", files::get, actions, 1,
                "equipment/katana.equ");
        assertNotNull(sequence);
        assertTrue(sequence.emitters().isEmpty());
        var trail = sequence.visuals().stream().filter(visual -> visual.path().equals(blade)).findFirst().orElseThrow();
        assertEquals(650, trail.start());
        assertTrue(trail.motion().heightAt(trail.start()) > 0);
        assertTrue(trail.label().contains("第二刀"));
    }

    @Test void illusionFinishesPreviousSwingInAFadingPoseAndKeepsWaveLayersAligned() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "73 `Swordman/IllusionSlash.skl`");
        files.put("passiveobject/passiveobject.lst", "20033 `Character/Swordman/Melee.obj` 20037 `Character/Swordman/Wave.obj`");
        String objects = "passiveobject/character/swordman/";
        files.put(objects + "melee.obj", "[basic motion] `Animation/Slash.ani`");
        files.put(objects + "wave.obj", "[basic motion] `Animation/Wave.ani` [etc motion] `Animation/Bottom1.ani` `Animation/Bottom2.ani` [/etc motion]");
        for (String path : List.of("slash", "wave", "bottom1", "bottom2")) files.put(objects + "animation/" + path + ".ani", ani(50, 50, 50, 50, 500, 0));
        String slash = objects + "animation/slash.ani";
        files.put(slash, files.get(slash).replace("[FRAME002]", "[FRAME002] [IMAGE] `test.img` 0"));
        files.put(ROOT + "particle/illusionslash.ptl", "[x axis] [move type] `[straight line]` [move variable 1] 500 500");
        files.put(ROOT + "animation/slash.ani", ani(50, 50, 50, 50, 50, 50, 0));
        files.put(ROOT + "animation/finisher.ani", ani(50, 50, 50, 50, 50, 50, 80, 80, 80, 400));
        var actions = Map.of("etc motion:51", "animation/slash.ani", "etc motion:55", "animation/finisher.ani");
        var sequence = Act1SwordComboSupplement.find("skill/swordman/illusionslash.skl", "[static data] 7 1", files::get, actions, 1, null);
        assertNotNull(sequence);
        var previous = sequence.poses().stream().filter(Act1SkillSequence.Pose::secondary).findFirst().orElseThrow();
        assertEquals(150, previous.start());
        assertEquals(3, previous.firstFrame());
        assertEquals(0, previous.keyframes().get(1).opacity());
        var leading = sequence.visuals().get(0);
        assertTrue(leading.motion().offsetAt(180) > leading.motion().offsetAt(100));
        var waves = sequence.visuals().stream().filter(visual -> visual.label().contains("剑气")).toList();
        assertEquals(2, waves.size());
        assertEquals(waves.get(0).motion(), waves.get(1).motion());
        assertTrue(waves.stream().allMatch(wave -> wave.x() == 65 && wave.y() == 0));
        assertEquals(0, waves.get(0).motion().heightAt(450));
    }

    private static String ani(int... delays) {
        var text = new StringBuilder("[FRAME MAX] ").append(delays.length);
        for (int index = 0; index < delays.length; index++) {
            text.append(String.format(" [FRAME%03d] [DELAY] %d", index, delays[index]));
        }
        return text.toString();
    }
}
