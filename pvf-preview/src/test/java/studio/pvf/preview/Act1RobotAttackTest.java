package studio.pvf.preview;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Act1RobotAttackTest {
    private static final String ROBOT = "monster/gunner/robot/";
    private static final String PASSIVE = "passiveobject/character/gunner/";
    private static final String EXPLOSION = "passiveobject/common/animation/noneexplosion.ani";
    private static final String MECHADROP = """
            [casting time] 700 700
            [static data] 75 22 1 2 5000 3 200 -500 250 3 800 [/static data]
            [level info] 7 500 120 3 0 40 200 150 [/level info]
            """;

    @Test void rxAttackAndExpiryUseTheirDistinctNativeExplosionOrigins() throws Exception {
        var attack = summon("robotrx78", "[casting time] 300 300 [level info] 3 5 250 7000 [/level info]", "");
        var blast = one(attack, EXPLOSION);
        var windup = one(attack, ROBOT + "rx78_ani/boom.ani");
        assertEquals(800, windup.start());
        assertEquals(1250, blast.start());
        assertEquals(27, windup.motion().offsetAt(blast.start()));
        assertEquals(95, blast.x());
        assertEquals(-20, blast.y());
        assertFalse(blast.decorative());

        var expiry = summon("robotrx78", "[casting time] 300 300 [level info] 3 5 250 7000 [/level info]", "robot-rx78:expiry");
        blast = one(expiry, EXPLOSION);
        assertEquals(7300, blast.start());
        assertEquals(30, blast.x());
        assertEquals(-30, blast.y());
    }

    @Test void ez8UsesAcceleratedWaitAndArmingBeforeItsLevelScaledExplosion() throws Exception {
        var s = summon("robotez8", "[casting time] 400 400 [level info] 4 18 500 3000 125 [/level info]", "");
        assertEquals(400, one(s, ROBOT + "ez8_ani/stay2.ani").start());
        var arm = one(s, ROBOT + "ez8_ani/boom.ani");
        assertEquals(3300, arm.start());
        assertEquals(3900, arm.end());
        var blast = one(s, EXPLOSION);
        assertEquals(3900, blast.start());
        assertEquals(4300, blast.end());
        assertEquals(1.25, blast.scale());
        assertTrue(s.emitters().stream().filter(e -> e.path().contains("noneexplosionparticle"))
                .allMatch(e -> e.start() == 3900 && e.emitFor() == 400 && e.frequency() == 0 && e.scale() == 1.25));
    }

    @Test void exsShowsConfiguredVolleyAndExplosionWhenLifetimeExpires() throws Exception {
        var s = summon("robotexs", """
                [casting time] 500 500
                [static data] 200 5 1000 [/static data]
                [level info] 4 30 50 400 30000 [/level info]
                """, "");
        var bullets = byPath(s, PASSIVE + "animation/exsviperbullet.ani");
        assertEquals(85, bullets.size());
        assertEquals(List.of(200L, 200L, 200L, 200L), java.util.stream.IntStream.range(1, 5)
                .mapToObj(i -> bullets.get(i).start() - bullets.get(i - 1).start()).toList());
        assertTrue(bullets.stream().allMatch(v -> !v.decorative() && v.motion() != null));
        assertEquals(30500, one(s, EXPLOSION).start());
    }

    @Test void tempesterExposesGunMissileAndExpiryRushDamage() throws Exception {
        var s = summon("robottempester", """
                [casting time] 600 600
                [static data] 150 5 200 2 400 10000 [/static data]
                [level info] 6 35 219 1314 657 1533 30000 [/level info]
                """, "");
        assertEquals(5, byPath(s, PASSIVE + "animation/tempestergunbullet.ani").size());
        assertEquals(2, byPath(s, PASSIVE + "animation/tempestermissile.ani").size());
        var dash = one(s, ROBOT + "tempesterani/bodydash.ani");
        var blast = one(s, EXPLOSION);
        assertEquals(31100, dash.start());
        assertFalse(dash.decorative());
        assertTrue(dash.alliedAttack());
        assertEquals(dash.end(), blast.start());
    }

    @Test void delayedEz8ReusesBothOrdinaryArmingTransitionsWithTheAdditionalTime() throws Exception {
        var s = Act1GunnerUtilityCast.find("skill/gunner/delayedrobotez8.skl", "[static data] 10000 [/static data]",
                fixture(), Act1GunnerMechanicalSupplementTest.ACTIONS, 1, "", "");
        assertEquals(8400, one(s, ROBOT + "ez8_ani/stay2.ani").start());
        assertEquals(13300, one(s, ROBOT + "ez8_ani/boom.ani").start());
        assertEquals(13900, one(s, EXPLOSION).start());
    }

    @Test void normalAirDropsExplodeOnTheSameTickTheyReachGround() throws Exception {
        var s = Act1GunnerSupportCast.find("skill/gunner/mechadrop.skl", MECHADROP, fixture(),
                Act1GunnerSupportCastTest.ACTIONS, 1, "");
        var drops = byPath(s, ROBOT + "rx78_ani/drop.ani");
        var blasts = byPath(s, EXPLOSION);
        assertEquals(3, blasts.size());
        for (int i = 0; i < blasts.size(); i++) {
            var drop = drops.get(i);
            var blast = blasts.get(i);
            assertEquals(drop.end(), blast.start());
            assertEquals(drop.x() + drop.motion().offsetAt(drop.end()), blast.x());
            assertEquals(drop.y() - 30, blast.y());
            assertEquals(1.5, blast.scale());
        }
        assertEquals(15, s.emitters().size());
        assertTrue(byPath(s, ROBOT + "rx78_ani/stay.ani").isEmpty());
    }

    @Test void detonationCanUseTheExistingEz8WithoutWaitingForItsCountdown() throws Exception {
        var s = Act1GunnerSupportCast.find("skill/gunner/robotsexplosion.skl", """
                [casting time] 400 400 [static data] 500 [/static data] [level info] 1 530 [/level info]
                """, fixture(), Act1GunnerSupportCastTest.ACTIONS, 1, "gunner-final:ez8");
        assertEquals(400, one(s, ROBOT + "ez8_ani/stay2.ani").end());
        assertEquals(400, one(s, EXPLOSION).start());
        assertTrue(s.emitters().stream().anyMatch(e -> e.path().endsWith("/ez8_boom.ptl")));
    }

    private static Act1SkillSequence summon(String name, String skill, String choice) throws Exception {
        return Act1GunnerMechanicalSupplement.find("skill/gunner/" + name + ".skl", skill,
                fixture(), Act1GunnerMechanicalSupplementTest.ACTIONS, 1, choice);
    }

    private static List<Act1SkillSequence.Visual> byPath(Act1SkillSequence sequence, String path) {
        assertNotNull(sequence);
        return sequence.visuals().stream().filter(v -> v.path().equals(path)).toList();
    }

    private static Act1SkillSequence.Visual one(Act1SkillSequence sequence, String path) {
        var matches = byPath(sequence, path);
        assertEquals(1, matches.size(), path);
        return matches.get(0);
    }

    static GameScript.Lookup fixture() {
        var files = files();
        return path -> {
            if (files.containsKey(path)) return files.get(path);
            if (path.endsWith(".ani")) return Act1GunnerSupportCastTest.ani();
            if (path.endsWith(".ptl")) return "[life time] 100 [creation frequency] 100 [maximum create number] 16";
            return null;
        };
    }

    static HashMap<String, String> files() {
        var files = Act1GunnerMechanicalSupplementTest.bodyFiles();
        var support = Act1GunnerSupportCastTest.fixture();
        support.put("skill/gunnerskill.lst", files.get("skill/gunnerskill.lst") + " " + support.get("skill/gunnerskill.lst")
                + " 51 `gunner/delayedrobotez8.skl`");
        files.putAll(support);
        files.merge("monster/monster.lst", "60013 `gunner/robot/droprx78.mob`", (left, right) -> left + " " + right);
        files.merge(ROBOT + "rx78.mob", """
                [attack delay] 500 [attack motion] `RX78_Ani/Boom.ani` [/attack motion]
                [etc motion] `RX78_Ani/BoomEffect.ani` [/etc motion]
                """,
                (left, right) -> left + " " + right);
        files.merge(ROBOT + "ez8.mob", "[etc motion] `Ez8_Ani/Stay2.ani` `Ez8_Ani/Boom.ani` [/etc motion]",
                (left, right) -> left + " " + right);
        files.put(ROBOT + "tempester.mob", files.get(ROBOT + "tempester.mob") + " [attack delay] 1500");
        files.put(ROBOT + "rx78_ani/boom.ani", """
                [FRAME000] [IMAGE] `fixture.img` 0 [DELAY] 40
                [FRAME001] [IMAGE] `fixture.img` 0 [DELAY] 40
                [FRAME002] [IMAGE] `fixture.img` 0 [DELAY] 40
                [FRAME003] [IMAGE] `fixture.img` 0 [DELAY] 40
                [FRAME004] [IMAGE] `fixture.img` 0 [DELAY] 40
                [FRAME005] [IMAGE] `fixture.img` 0 [DELAY] 250
                """);
        files.put(ROBOT + "ez8_ani/boom.ani", "[FRAME000] [IMAGE] `fixture.img` 0 [DELAY] 360");
        files.put("skill/gunner/robotez8.skl", "[casting time] 400 400 [level info] 4 18 500 3000 100 [/level info]");
        files.put(ROBOT + "particle/exs_bullet.ptl", projectile(600, 600, 1000));
        files.put(ROBOT + "particle/tempestergun.ptl", projectile(600, 600, 800));
        files.put(ROBOT + "particle/tempestermissile.ptl", projectile(-400, 800, 1000)
                + " [changing term] 500 [changing type] `[keep last value]`");
        for (String art : List.of("ez8_ani/stay2", "exs_ani/attack", "exs_ani/cannonattack", "exs_ani/shoot1",
                "exs_ani/shoot2", "exs_ani/smoke", "tempesterani/bodygun2", "tempesterani/bodymissile",
                "tempesterani/effectboostattack", "tempesterani/effectgunshoot", "tempesterani/bodydash",
                "tempesterani/effectdash", "tempesterani/effectboostfront2", "rx78_ani/drop", "rx78_ani/dropeffect")) {
            files.put(ROBOT + art + ".ani", Act1GunnerSupportCastTest.ani());
        }
        files.put("passiveobject/common/animation/earthquake.ani", Act1GunnerSupportCastTest.ani());
        files.put(EXPLOSION, "[FRAME000] [DELAY] 400 [ATTACK BOX] -65 -45 -50 130 90 100");
        var catalog = new HashMap<>(java.util.Map.of(22214, "rx78explosion", 22215, "ez8explosion",
                22216, "exsexplosion", 22226, "tempesterexplosion", 22227, "mechadropexplosion"));
        var passiveList = new StringBuilder("22223 `character/gunner/mechadrop.obj` ");
        catalog.forEach((id, stem) -> {
            passiveList.append(id).append(" `character/gunner/").append(stem).append(".obj` ");
            files.put(PASSIVE + stem + ".obj", """
                    [basic motion] `../../Common/Animation/NoneExplosion.ani`
                    [string data] `../../Common/Particle/NoneExplosionParticle1.ptl`
                    `../../Common/Particle/NoneExplosionParticle2.ptl` `../../Common/Particle/NoneExplosionParticle3.ptl`
                    `../../Common/Particle/NoneExplosionParticle4.ptl` [/string data]
                    """);
        });
        java.util.Map.of(22016, "exsviperbullet", 22224, "tempestergunbullet", 22225, "tempestermissile").forEach((id, stem) -> {
            passiveList.append(id).append(" `character/gunner/").append(stem).append(".obj` ");
            files.put(PASSIVE + stem + ".obj", "[basic motion] `Animation/" + stem + ".ani`");
            files.put(PASSIVE + "animation/" + stem + ".ani", "[FRAME000] [DELAY] 150 [ATTACK BOX] -16 -10 -16 32 20 32");
        });
        files.put("passiveobject/passiveobject.lst", passiveList.toString());
        return files;
    }

    private static String projectile(int start, int end, int life) {
        return "[x axis] [move type] `[straight line]` [move variable 1] " + start + " " + end + " [life time] " + life;
    }
}
