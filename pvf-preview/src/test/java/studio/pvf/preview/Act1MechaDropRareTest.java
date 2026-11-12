package studio.pvf.preview;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Act1MechaDropRareTest {
    private static final String SKILL = "skill/gunner/mechadrop.skl";
    private static final String ROBOT = "monster/gunner/robot/";
    private static final String COMMON = "common/commoneffect/animation/";
    private static final String BLAST = "passiveobject/common/animation/";
    private static final String SKILL_TEXT = """
            [casting time] 700 700
            [static data] 75 22 1 2 5000 3 200 -500 250 3 800 [/static data]
            [level info] 15 500 120 20 3 40 200 100 320 150 200 300 40 2000 500 500 [/level info]
            """;

    @Test void onlyTheAct1MechaDropOffersFourExplicitRareScenarios() throws Exception {
        GameScript.Lookup lookup = fixture()::get;
        assertEquals(List.of("mechadrop:fire", "mechadrop:heading", "mechadrop:hp", "mechadrop:mp"),
                Act1GunnerSupportCast.choices(SKILL, lookup, true).stream().map(GameEffectRecipes.Effect::key).toList());
        assertTrue(Act1GunnerSupportCast.choices(SKILL, lookup, false).isEmpty());
    }

    @Test void fireSubstitutesThreeDropsAndUsesTheFireRootAfterLandingAndWindup() throws Exception {
        var sequence = scene("mechadrop:fire", fixture());
        assertEquals(17, visuals(sequence, ROBOT + "rx78_ani/drop.ani").size());
        assertEquals(3, visuals(sequence, ROBOT + "firerx78_ani/drop.ani").size());
        var drop = visuals(sequence, ROBOT + "firerx78_ani/drop.ani").get(0);
        var land = visuals(sequence, ROBOT + "firerx78_ani/land.ani").get(0);
        var windup = visuals(sequence, ROBOT + "firerx78_ani/boom.ani").get(0);
        assertEquals(2500, drop.end());
        assertEquals(drop.end(), land.start());
        assertEquals(3760, land.end());
        assertEquals(4260, windup.start());
        assertTrue(windup.decorative());
        var blast = visuals(sequence, BLAST + "fireexplosion.ani").get(0);
        assertEquals(4710, blast.start());
        assertEquals(5110, blast.end());
        assertEquals(1.5, blast.scale());
        assertEquals(125, blast.x());
        assertEquals(12, sequence.emitters().stream().filter(e -> e.path().contains("fireexplosionparticle")).count());
    }

    @Test void headingUsesAlliedChargeVolumesThenTheNormalExplosionAfterTheThirdCharge() throws Exception {
        var sequence = scene("mechadrop:heading", fixture());
        var charges = visuals(sequence, ROBOT + "headingrx78_ani/boom.ani");
        assertEquals(9, charges.size());
        var firstRobot = charges.subList(0, 3);
        assertTrue(firstRobot.stream().allMatch(Act1SkillSequence.Visual::alliedAttack));
        assertEquals(List.of(4130L, 5710L, 7290L), firstRobot.stream().map(Act1SkillSequence.Visual::start).toList());
        assertEquals(List.of(60.0, 140.0, 220.0), firstRobot.stream().map(Act1SkillSequence.Visual::x).toList());
        var explosion = visuals(sequence, BLAST + "noneexplosion.ani").stream()
                .filter(v -> v.label().contains("头撞")).findFirst().orElseThrow();
        assertEquals(7870, explosion.start());
        assertEquals(338, explosion.x());
        assertEquals(1, explosion.scale());
    }

    @Test void hpAndMpShareRobotArtButShowDifferentRecoveryEffectsWithoutEnemyDamage() throws Exception {
        var hp = scene("mechadrop:hp", fixture());
        var mp = scene("mechadrop:mp", fixture());
        assertEquals(3, visuals(hp, ROBOT + "healrx78_ani/heal.ani").size());
        assertEquals(3, visuals(mp, ROBOT + "healrx78_ani/heal.ani").size());
        assertEquals(3830, visuals(hp, ROBOT + "healrx78_ani/heal.ani").get(0).start());
        assertEquals(3830, visuals(hp, COMMON + "heal.ani").get(0).start());
        assertEquals(3830, visuals(mp, COMMON + "mpup.ani").get(0).start());
        assertEquals(3970, hp.emitters().stream().filter(e -> e.path().endsWith("healparticle.ptl")).findFirst().orElseThrow().start());
        assertEquals(4030, mp.emitters().stream().filter(e -> e.path().endsWith("healparticle.ptl")).findFirst().orElseThrow().start());
        assertTrue(visuals(hp, COMMON + "mpup.ani").isEmpty());
        assertTrue(visuals(mp, COMMON + "heal.ani").isEmpty());
        assertEquals(17, visuals(hp, BLAST + "noneexplosion.ani").size());
        assertEquals(17, visuals(mp, BLAST + "noneexplosion.ani").size());
        assertTrue(visuals(hp, ROBOT + "healrx78_ani/stay.ani").stream()
                .allMatch(Act1SkillSequence.Visual::alliedAttack));
        assertTrue(hp.visuals().stream().filter(v -> v.path().startsWith(ROBOT + "healrx78_ani/")
                && !v.path().endsWith("/stay.ani")).allMatch(Act1SkillSequence.Visual::decorative));
    }

    @Test void damagingVariantsShowOneGoblinAtTheFirstHitAndThroughTheFinalBlast() throws Exception {
        String goblin = Act1SwordAsuraSupplement.GOBLIN;
        var fire = visuals(scene("mechadrop:fire", fixture()), goblin + "damage1.ani");
        assertEquals(1, fire.size());
        assertEquals(4710, fire.get(0).start());
        assertEquals(125, fire.get(0).x());
        assertEquals(-17.4, fire.get(0).y(), .001);

        var heading = visuals(scene("mechadrop:heading", fixture()), goblin + "damage1.ani");
        assertEquals(List.of(4170L, 5750L, 7330L, 7870L),
                heading.stream().map(Act1SkillSequence.Visual::start).toList());
        assertEquals(List.of(100.0, 180.0, 260.0, 340.0),
                heading.stream().map(Act1SkillSequence.Visual::x).toList());
    }

    @Test void missingFireRootInvalidatesOnlyTheFireScenario() throws Exception {
        var files = fixture();
        files.remove(BLAST + "fireexplosion.ani");
        assertNull(scene("mechadrop:fire", files));
        assertNotNull(scene("", files));
    }

    private static List<Act1SkillSequence.Visual> visuals(Act1SkillSequence sequence, String path) {
        assertNotNull(sequence);
        return sequence.visuals().stream().filter(v -> v.path().equals(path)).toList();
    }

    private static Act1SkillSequence scene(String choice, HashMap<String, String> files) throws Exception {
        return Act1GunnerSupportCast.find(SKILL, SKILL_TEXT, files::get, Act1GunnerSupportCastTest.ACTIONS, 1, choice);
    }

    private static HashMap<String, String> fixture() {
        var files = Act1RobotAttackTest.files();
        files.merge("monster/monster.lst", """
                60014 `gunner/robot/firerx78.mob`
                60015 `gunner/robot/headingrx78.mob`
                60016 `gunner/robot/hphealrx78.mob`
                60017 `gunner/robot/mphealrx78.mob`
                """, (left, right) -> left + " " + right);
        addRobot(files, "firerx78", "firerx78", 500, 1260, 40, 40, 40, 40, 40, 250, 0);
        addRobot(files, "headingrx78", "headingrx78", 1000, 630, 40, 80, 80, 80, 300);
        addRobot(files, "hphealrx78", "healrx78", 500, 630, 40, 40, 40, 40, 40);
        addRobot(files, "mphealrx78", "healrx78", 500, 630, 40, 40, 40, 40, 40);
        files.computeIfPresent(ROBOT + "headingrx78_ani/boom.ani", (path, text) -> text.replace(
                "[FRAME001]", "[FRAME001] [ATTACK BOX] -7 -16 5 36 32 43"));
        for (String target : List.of("stay", "damage1")) {
            files.put(Act1SwordAsuraSupplement.GOBLIN + target + ".ani", animation(100));
        }
        files.put(ROBOT + "attackinfo/heading.atk", "[attack enemy] 1");
        files.put(ROBOT + "attackinfo/heal.atk", "[attack enemy] 0 [attack friend] 1");
        files.put(ROBOT + "healrx78_ani/heal.ani", animation(300, 0));
        files.put(COMMON + "heal.ani", animation(35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35));
        files.put(COMMON + "mpup.ani", animation(50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 0));
        files.merge("passiveobject/passiveobject.lst", "22222 `character/gunner/firerx78explosion.obj`",
                (left, right) -> left + " " + right);
        files.put("passiveobject/character/gunner/firerx78explosion.obj", """
                [basic motion] `../../Common/Animation/FireExplosion.ani`
                [string data] `../../Common/Particle/FireExplosionParticle1.ptl`
                `../../Common/Particle/FireExplosionParticle2.ptl` `../../Common/Particle/FireExplosionParticle3.ptl`
                `../../Common/Particle/FireExplosionParticle4.ptl` [/string data]
                """);
        files.put(BLAST + "fireexplosion.ani", "[FRAME000] [DELAY] 400 [ATTACK BOX] -65 -40 -50 130 80 100");
        for (String art : List.of("appear", "move", "disappear"))
            files.put("passiveobject/character/gunner/animation/mechadrop" + art + ".ani", animation(100));
        String particle = "[life time] 100 [creation frequency] 100 [maximum create number] 16";
        files.put("common/commoneffect/particle/healparticle.ptl", particle);
        files.put(ROBOT + "particle/rx78_boom.ptl", particle);
        for (String kind : List.of("none", "fire")) {
            for (int i = 1; i <= 4; i++) files.put("passiveobject/common/particle/" + kind + "explosionparticle" + i + ".ptl", particle);
        }
        return files;
    }

    private static void addRobot(HashMap<String, String> files, String stem, String art, int delay, int landing,
                                 int... attackDelays) {
        String directory = art + "_ani/";
        String extra = stem.equals("headingrx78") ? "[attack info] `AttackInfo/Heading.atk` [/attack info]"
                : art.equals("healrx78") ? "[attack info] `AttackInfo/Heal.atk` [/attack info]" : "";
        String heal = art.equals("healrx78") ? " `" + directory + "heal.ani`" : "";
        files.put(ROBOT + stem + ".mob", """
                [waiting motion] `%1$sstay.ani`
                [attack delay] %2$d
                [attack motion] `%1$sboom.ani` [/attack motion]
                [etc motion] `%1$sboomeffect.ani` `%1$sdrop.ani` `%1$sdropeffect.ani` `%1$sland.ani`%3$s [/etc motion]
                %4$s
                """.formatted(directory, delay, heal, extra));
        files.put(ROBOT + directory + "stay.ani", animation(100));
        files.put(ROBOT + directory + "land.ani", animation(landing));
        files.put(ROBOT + directory + "drop.ani", animation(40));
        files.put(ROBOT + directory + "dropeffect.ani", animation(80, 80, 80));
        files.put(ROBOT + directory + "boomeffect.ani", animation(80, 80, 80, 80, 80, 500, 0));
        files.put(ROBOT + directory + "boom.ani", animation(attackDelays));
    }

    private static String animation(int... delays) {
        var source = new StringBuilder();
        for (int i = 0; i < delays.length; i++) source.append("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d%n".formatted(i, delays[i]));
        return source.toString();
    }
}
