package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1SwordMeleeSupplementTest {
    private static final String ROOT = "character/swordman/";
    private static final String EFFECT = ROOT + "effect/animation/";

    @Test void guardUsesTheBlockedFrameAndMasteryOnlyWaveAfterRecoil() throws Exception {
        var f = new Fixture();
        String path = "skill/swordman/guard.skl";
        String text = "[static data] 500 400 100 400 3000 [level info] 5 200 200 40 15 10";
        f.files.put("skill/swordmanskill.lst", "1 `swordman/guard.skl` 12 `swordman/shortswordmastery.skl` "
                + "14 `swordman/heavyswordmastery.skl` 27 `swordman/weaponmasteryup.skl`");
        f.files.put("skill/swordman/shortswordmastery.skl", "[static data] 7 5 3");
        f.files.put("skill/swordman/heavyswordmastery.skl", "[static data] 5 3 7");
        f.files.put("skill/swordman/weaponmasteryup.skl", "[static data] 12 13 14 15");
        f.files.put("passiveobject/passiveobject.lst", "20039 `character/swordman/guardwave.obj`");
        f.files.put("passiveobject/character/swordman/guardwave.obj", "[basic motion] `wave.ani` [add object effect] 1 `dust.ani` 1");
        f.files.put("passiveobject/character/swordman/wave.ani", ani(70, 70, 70));
        f.files.put("passiveobject/character/swordman/dust.ani", ani(70, 70, 70));
        f.action("etc motion:0", "guard", 10000, 10000);
        f.action("waiting motion", "stay", 80);
        f.files.put(EFFECT + "guard/spark.ani", ani(50, 50));
        f.files.put(EFFECT + "guard/dust.ani", ani(40, 40));
        f.files.put(Act1SwordAsuraSupplement.GOBLIN + "stay.ani", ani(80));
        f.files.put(Act1SwordAsuraSupplement.GOBLIN + "attack1.ani", ani(80, 80, 80, 80, 200));
        f.files.put(Act1SwordAsuraSupplement.GOBLIN + "damage1.ani", ani(80));

        var block = Act1SwordMeleeSupplement.find(path, text, f.files::get, f.actions, 1, null);
        assertNotNull(block);
        assertEquals(1, block.poses().get(1).firstFrame());
        assertEquals(340, block.visuals().get(0).start());
        assertEquals(501, block.visuals().get(1).start());
        assertFalse(block.visuals().stream().anyMatch(v -> v.path().endsWith("/wave.ani")));

        var choices = Act1SwordMeleeSupplement.choices(path, f.files::get, true);
        assertEquals(List.of("guard:hold", "guard:short", "guard:heavy"), choices.stream().map(GameEffectRecipes.Effect::key).toList());
        assertEquals(0, Act1SwordMeleeSupplement.masteryWeapon(path, "guard:short"));
        assertEquals(3, Act1SwordMeleeSupplement.masteryWeapon(path, "guard:heavy"));
        for (var choice : choices.subList(1, choices.size())) {
            var counter = Act1SwordMeleeSupplement.find(path, text, f.files::get, f.actions, 1, null, choice.key());
            assertNotNull(counter);
            var wave = counter.visuals().stream().filter(v -> v.path().endsWith("/wave.ani")).findFirst().orElseThrow();
            assertEquals(540, wave.start());
            assertEquals(-20, wave.x());
            assertEquals(540, counter.poses().get(2).start());
            assertEquals(0, counter.poses().get(2).motion().offsetAt(640));
            assertTrue(counter.visuals().stream().anyMatch(v -> v.label().contains("冲击波命中") && v.start() == wave.start()));
        }
    }

    @Test void moonlightSlashAndFullMoonUseTheSameNativeChain() throws Exception {
        var f = new Fixture();
        f.files.put("skill/swordmanskill.lst", "77 `Swordman/MoonlightSlash.skl` 80 `Swordman/MoonlightSlashFull.skl`");
        String text = "[static data] 200 -500 100 -50";
        f.files.put("skill/swordman/moonlightslash.skl", text);
        for (int phase = 0; phase < 2; phase++) {
            int n = phase + 1;
            f.action("etc motion:" + (61 + phase), "moonlightslash" + n, 80, 80, 80, 80, 300);
            f.files.put(EFFECT + "moonlightslash" + n + ".ani", ani(80, 80, 80, 80, 80));
        }
        f.action("etc motion:65", "moonlightslashfull", 50, 50, 50, 50, 50, 50, 50, 50, 150);
        f.files.put(EFFECT + "moonlightslashfull.ani", ani(50, 50, 50, 50, 50, 50, 50));
        var moon = Act1SwordMeleeSupplement.find("skill/swordman/moonlightslash.skl", text, f.files::get, f.actions, 1, null);
        var full = Act1SwordMeleeSupplement.find("skill/swordman/moonlightslashfull.skl", "", f.files::get, f.actions, 1, null);
        assertNotNull(moon);
        assertNotNull(full);
        assertEquals(List.of("etc motion:61", "etc motion:62"), moon.poses().stream().map(Act1SkillSequence.Pose::action).toList());
        assertEquals(List.of("etc motion:61", "etc motion:62", "etc motion:65"), full.poses().stream().map(Act1SkillSequence.Pose::action).toList());
        assertEquals(List.of(160L, 240L, 480L), full.visuals().stream().map(Act1SkillSequence.Visual::start).toList());
        assertEquals(33.6, full.poses().get(1).motion().offsetAt(240), .001);
        assertEquals(67.2, full.poses().get(2).motion().offsetAt(480), .001);
    }

    @Test void upperSlashUsesTheThirdAttackAndTheShortSwordTrailOffset() throws Exception {
        var f = new Fixture();
        f.files.put("skill/swordmanskill.lst", "46 `Swordman/UpperSlash.skl`");
        f.action("attack motion:2", "attack3", 50, 50, 50, 50, 50, 50, 50, 50, 150);
        f.files.put(EFFECT + "upperslash1.ani", ani(50, 50, 50, 50));
        var sequence = Act1SwordMeleeSupplement.find("skill/swordman/upperslash.skl", "", f.files::get, f.actions, 1, null);
        assertNotNull(sequence);
        assertEquals("attack motion:2", sequence.poses().get(0).action());
        var trail = sequence.visuals().get(0);
        assertEquals(150, trail.start());
        assertEquals(5, trail.x());
        assertEquals(-18, trail.y());
        assertEquals(.8, trail.scale());
    }

    @Test void hopSmashUsesTheShortChargeApexAndLandingFrames() throws Exception {
        var f = new Fixture();
        f.files.put("skill/swordmanskill.lst", "65 `Swordman/HopSmash.skl`");
        f.action("etc motion:26", "hopsmashready", 400);
        f.action("etc motion:27", "hopsmash", 120, 240, 60, 180, 60, 60, 180);
        f.files.put(EFFECT + "hopsmash/sword.ani", ani(360, 60, 60, 60));
        f.files.put(EFFECT + "hopsmash/smash.ani", ani(420, 180, 60, 60, 60, 60, 60));
        var sequence = Act1SwordMeleeSupplement.find("skill/swordman/hopsmash.skl",
                "[static data] 30 200 50 200 50 120 150 100 2 3 300 500 500", f.files::get, f.actions, 1, null);
        assertNotNull(sequence);
        assertEquals(List.of(0L, 30L, 270L, 430L), sequence.poses().stream().map(Act1SkillSequence.Pose::start).toList());
        assertEquals(List.of(270L, 310L, 430L), sequence.visuals().stream().map(Act1SkillSequence.Visual::start).toList());
        assertEquals(50, sequence.poses().get(2).motion().heightAt(270), .001);
        assertEquals(0, sequence.poses().get(2).motion().heightAt(430), .001);
        assertEquals(40, sequence.poses().get(3).motion().offsetAt(430), .001);
    }

    @Test void berserkerHopAddsTheFourStockShockwaveLayersAtTouchdown() throws Exception {
        var f = new Fixture();
        String path = "skill/swordman/hopsmash.skl";
        f.files.put("skill/swordmanskill.lst", "65 `Swordman/HopSmash.skl` 63 `Swordman/BloodyVigorous.skl`");
        f.files.put("passiveobject/passiveobject.lst", "20050 `Character/Swordman/HopSmashSub.obj`");
        f.files.put("passiveobject/character/swordman/hopsmashsub.obj", "[basic motion] `Animation/HopSmashSubFront1.ani` "
                + "[add object effect] 1 `Animation/HopSmashSubFront2.ani` 1 [/add object effect]");
        for (String stem : List.of("front1", "front2", "back1", "back2")) {
            f.files.put("passiveobject/character/swordman/animation/hopsmashsub" + stem + ".ani", ani(80, 80, 80, 80, 80, 80));
        }
        f.action("etc motion:26", "hopsmashready", 400);
        f.action("etc motion:27", "hopsmash", 120, 240, 60, 180, 60, 60, 180);
        f.action("waiting motion", "stay", 120);
        f.files.put(EFFECT + "hopsmash/sword.ani", ani(360, 60, 60, 60));
        f.files.put(EFFECT + "hopsmash/smash.ani", ani(420, 180, 60, 60, 60, 60, 60));
        var choice = Act1SwordMeleeSupplement.choices(path, f.files::get, true).get(0);
        var sequence = Act1SwordMeleeSupplement.find(path, "[static data] 30 200 50 200 50 120 150 100 2 3 300 500 500",
                f.files::get, f.actions, 1, null, choice.key());
        assertNotNull(sequence);
        var shock = sequence.visuals().subList(3, 7);
        assertEquals(4, shock.size());
        assertTrue(shock.stream().allMatch(layer -> layer.start() == 430 && layer.end() == 910));
        assertEquals(300 / 292.0, shock.get(0).scale(), .001);
        assertEquals(40, shock.get(0).motion().offsetAt(430), .001);
        assertTrue(shock.stream().allMatch(layer -> layer.x() == 123 && layer.y() == .29 && layer.layer() < 500));
        assertEquals(910, sequence.poses().get(4).end());
    }

    @Test void tripleSlashAddsTwoAuthoredStagesOnlyForExplicitMasteryBranches() throws Exception {
        var f = new Fixture();
        String path = "skill/swordman/tripleslash.skl";
        f.files.put("skill/swordmanskill.lst", "8 `swordman/tripleslash.skl` 4 `swordman/lightswordmastery.skl` "
                + "13 `swordman/blademastery.skl` 27 `swordman/weaponmasteryup.skl`");
        f.files.put("skill/swordman/lightswordmastery.skl", "[static data] 3 5");
        f.files.put("skill/swordman/blademastery.skl", "[static data] 5");
        f.files.put("skill/swordman/weaponmasteryup.skl", "[static data] 12 13 14 15");
        for (int i = 1; i <= 5; i++) {
            f.action("etc motion:" + (i + 2), "tripleslash" + i, 70, 70, 70, 70, 300);
            f.files.put(EFFECT + "tripleslash/slash" + i + ".ani", ani(70, 70, 70, 70, 70));
        }
        for (int i = 1; i <= 2; i++) f.files.put(EFFECT + "tripleslash/move" + i + ".ani", ani(70, 70, 70, 70));
        var choices = Act1SwordMeleeSupplement.choices(path, f.files::get, true);
        assertEquals(2, choices.size());
        assertEquals(3, Act1SwordMeleeSupplement.find(path, "", f.files::get, f.actions, 1, null).poses().size());
        for (var choice : choices) {
            assertTrue(choice.label().contains("5"));
            var sequence = Act1SwordMeleeSupplement.find(path, "", f.files::get, f.actions, 1, null, choice.key());
            assertNotNull(sequence);
            assertEquals(List.of(0L, 210L, 420L, 630L, 840L),
                    sequence.poses().stream().map(Act1SkillSequence.Pose::start).toList());
            assertTrue(sequence.visuals().stream().anyMatch(v -> v.path().endsWith("tripleslash/slash5.ani")));
        }
    }

    @Test void chargeCrashKeepsNativeChargeAndFrameThreeImpactInBothMasteryBranches() throws Exception {
        var f = new Fixture();
        String path = "skill/swordman/chargecrash.skl";
        f.files.put("skill/swordmanskill.lst", "68 `swordman/chargecrash.skl` 14 `swordman/heavyswordmastery.skl` 15 `swordman/bluntmastery.skl`");
        f.files.put("skill/swordman/heavyswordmastery.skl", "[static data] 5 3 7");
        f.files.put("skill/swordman/bluntmastery.skl", "[static data] 3 7");
        f.files.put("passiveobject/passiveobject.lst", "20030 `character/swordman/crash.obj`");
        f.files.put("passiveobject/character/swordman/crash.obj", "[basic motion] `damage-back.ani` [etc motion] `damage-front.ani`");
        f.files.put("passiveobject/character/swordman/damage-back.ani", ani(100, 100));
        f.files.put("passiveobject/character/swordman/damage-front.ani", ani(100));
        f.action("etc motion:43", "dash", 50, 50, 50, 50, 150);
        f.action("etc motion:44", "upper", 30, 30, 60, 60, 160);
        f.action("etc motion:45", "finish", 80, 80, 80, 80, 80, 80, 80, 80);
        for (String stem : List.of("dash", "dustdash", "dustdashlast", "up-slash", "charge", "down-slash")) {
            f.files.put(EFFECT + "chargecrash/" + stem + ".ani", ani(100, 100, 100, 100));
        }
        String text = "[static data] 700 -1500 300 600";
        var choices = Act1SwordMeleeSupplement.choices(path, f.files::get, true);
        assertEquals(2, choices.size());
        assertEquals(2, Act1SwordMeleeSupplement.find(path, text, f.files::get, f.actions, 1, null).poses().size());
        for (var choice : choices) {
            var sequence = Act1SwordMeleeSupplement.find(path, text, f.files::get, f.actions, 1, null, choice.key());
            assertNotNull(sequence);
            assertEquals(690, sequence.poses().get(2).start());
            assertEquals(1290, sequence.poses().get(3).start());
            var slash = sequence.visuals().stream().filter(v -> v.path().endsWith("down-slash.ani")).findFirst().orElseThrow();
            assertEquals(1290, slash.start());
            assertEquals(135, slash.x());
            assertEquals(10, slash.y());
            var shock = sequence.visuals().stream().filter(v -> v.path().endsWith("damage-back.ani")).findFirst().orElseThrow();
            assertEquals(slash.start(), shock.start());
            assertEquals(120, shock.x());
        }
    }

    private static final class Fixture {
        final Map<String, String> files = new HashMap<>();
        final Map<String, String> actions = new HashMap<>();

        void action(String name, String stem, int... delays) {
            actions.put(name, "animation/" + stem + ".ani");
            files.put(ROOT + "animation/" + stem + ".ani", ani(delays));
        }
    }

    private static String ani(int... delays) {
        var text = new StringBuilder("[FRAME MAX] ").append(delays.length);
        for (int i = 0; i < delays.length; i++) text.append(String.format(" [FRAME%03d] [DELAY] %d", i, delays[i]));
        return text.toString();
    }
}
