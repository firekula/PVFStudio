package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1SuperArmorTest {
    private static final String FIGHTER_ARMOR = "skill/fighter/superarmor.skl";
    private static final String SKILL = """
            [casting time] 700 500
            [maximum level] 2
            [level info] 3 6000 150 5 9000 300 10 [/level info]
            """;
    private static final GameScript.Lookup FIGHTER_CATALOG = Map.of(
            "skill/fighterskill.lst", "20 `Fighter/SuperArmor.skl` 180 `Fighter/SuperArmorOnCast.skl`")::get;

    @Test void contourColorUsesTheNativeTriangleWaveAndConstantAlpha() {
        assertEquals(0x96FF0000, Act1SuperArmor.outlineColor(0));
        assertEquals(0x96FF1400, Act1SuperArmor.outlineColor(100));
        assertEquals(0x96FFC800, Act1SuperArmor.outlineColor(250));
        assertEquals(0x96FF1400, Act1SuperArmor.outlineColor(400));
        assertEquals(0x96FF0000, Act1SuperArmor.outlineColor(500));
        assertEquals(0x96FFC800, Act1SuperArmor.outlineColor(750));
    }

    @Test void authoredAndRuntimeExpansionUseTheirOwnNativeDuration() {
        assertEquals(0.0, Act1SuperArmor.outlineExpansion(-1));
        assertEquals(0.5, Act1SuperArmor.outlineExpansion(0));
        assertEquals(0.25, Act1SuperArmor.outlineExpansion(100));
        assertEquals(0.0, Act1SuperArmor.outlineExpansion(200));
        assertEquals(0.25, Act1SuperArmor.runtimeExpansion(75));
        assertEquals(0.0, Act1SuperArmor.runtimeExpansion(150));
    }

    @Test void fighterBuffStartsOnReleaseAndUsesTheLevelDuration() throws Exception {
        var levelOne = Act1SuperArmor.runtimeSpan(FIGHTER_ARMOR, SKILL, FIGHTER_CATALOG, 1);
        assertNotNull(levelOne);
        assertEquals(700, levelOne.start());
        assertEquals(6700, levelOne.end());
        assertFalse(levelOne.contains(699));
        assertTrue(levelOne.contains(700));
        assertTrue(levelOne.contains(6699));
        assertFalse(levelOne.contains(6700));
        assertEquals(0.0, levelOne.expansionAt(699));
        assertEquals(0.5, levelOne.expansionAt(700));
        assertEquals(0.25, levelOne.expansionAt(775));
        assertEquals(0.0, levelOne.expansionAt(850));
        var levelTwo = Act1SuperArmor.runtimeSpan(FIGHTER_ARMOR, SKILL, FIGHTER_CATALOG, 2);
        assertEquals(500, levelTwo.start());
        assertEquals(9500, levelTwo.end());
    }

    @Test void laterCastProtectionAndMissingLevelDataDoNotSetAnImmediateBuff() throws Exception {
        assertNull(Act1SuperArmor.runtimeSpan("skill/fighter/superarmoroncast.skl", SKILL, FIGHTER_CATALOG, 1));
        assertNull(Act1SuperArmor.runtimeSpan(FIGHTER_ARMOR, SKILL, path -> "21 `Fighter/SuperArmor.skl`", 1));
        assertNull(Act1SuperArmor.runtimeSpan(FIGHTER_ARMOR, "[casting time] 700", FIGHTER_CATALOG, 1));
    }

    @Test void fighterSequenceKeepsItsIdlePoseThroughTheBuffAndShowsExpiry() throws Exception {
        GameScript.Lookup lookup = path -> switch (path) {
            case "skill/fighterskill.lst" -> "20 `Fighter/SuperArmor.skl`";
            case "common/commoneffect/animation/buffchargeglow.ani" -> "[FRAME000] [DELAY] 50";
            default -> null;
        };
        var actions = Map.of("buff motion", "cast.ani", "waiting motion", "wait.ani");
        var sequence = Act1FighterCast.find(FIGHTER_ARMOR, SKILL, lookup, actions, 1, "", job -> 90);
        assertNotNull(sequence);
        assertEquals(700, sequence.poses().get(1).start());
        assertTrue(sequence.poses().get(1).end() > 6700);
    }

    @Test void nativeSwordSkillStatesProtectOnlyTheMatchingReleasePose() throws Exception {
        var catalog = Map.of("skill/swordmanskill.lst", """
                32 `Swordman/ReleaseWave.skl`
                44 `Swordman/TombStoneRain.skl`
                74 `Swordman/WaveSpinArea.skl`
                """);
        for (String skill : List.of("releasewave", "tombstonerain", "wavespinarea")) {
            String path = "skill/swordman/" + skill + ".skl";
            var sequence = stages(new Act1SkillSequence.Pose("throw motion 2-1", 0, 500),
                    new Act1SkillSequence.Pose("throw motion 2-2", 500, 2600),
                    new Act1SkillSequence.Pose("etc motion:56", 2600, 3000),
                    new Act1SkillSequence.Pose("waiting motion", 3000, 3300));
            var armor = Act1SuperArmor.runtimeSpan(path, "", catalog::get, 1, sequence);
            assertNotNull(armor, skill);
            assertEquals(500, armor.start(), skill);
            assertEquals(2600, armor.end(), skill);
            assertEquals(0.0, armor.expansionAt(500), skill);
            assertNull(Act1SuperArmor.runtimeSpan(path, "", catalog::get, 1, null), skill);
        }
    }

    @Test void mageChannelArmorContinuesBetweenIndividualStrikes() throws Exception {
        var catalog = Map.of("skill/mageskill.lst", """
                55 `Mage/ThunderStrike.skl`
                59 `Mage/IceField.skl`
                """);
        for (String skill : List.of("thunderstrike", "icefield")) {
            int group = skill.equals("thunderstrike") ? 4 : 2;
            String path = "skill/mage/" + skill + ".skl";
            var sequence = stages(new Act1SkillSequence.Pose("throw motion " + group + "-1", 0, 900),
                    new Act1SkillSequence.Pose("throw motion " + group + "-2", 900, 1350),
                    new Act1SkillSequence.Pose("throw motion " + group + "-1", 1350, 1700),
                    new Act1SkillSequence.Pose("throw motion " + group + "-2", 1700, 2150),
                    new Act1SkillSequence.Pose("waiting motion", 2150, 2400));
            var armor = Act1SuperArmor.runtimeSpan(path, "", catalog::get, 1, sequence);
            assertNotNull(armor, skill);
            assertFalse(armor.contains(899), skill);
            assertTrue(armor.contains(900), skill);
            assertTrue(armor.contains(1500), skill);
            assertFalse(armor.contains(2150), skill);
            assertEquals(0.0, armor.expansionAt(900), skill);
        }
    }

    private static Act1SkillSequence stages(Act1SkillSequence.Pose... poses) {
        return new Act1SkillSequence(List.of(poses), List.of(), List.of(), "");
    }
}
