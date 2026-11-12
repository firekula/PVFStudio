package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1PriestSupplementTest {
    private static final String PRIEST = "character/priest/";
    private static final String EFFECT = PRIEST + "effect/animation/";

    @Test void talismanCallsTheLightningObjectOnTheGoblinAtTheConfiguredInterval() throws Exception {
        var f = new Fixture(new HashMap<>(), new HashMap<>());
        f.files.put("skill/priestskill.lst", "40 `priest/thunderbolttalisman.skl`");
        f.files.put("passiveobject/passiveobject.lst", "24011 `priest/talisman.obj` 24013 `priest/bolt.obj` 48012 `common/warning.obj`");
        f.files.put("passiveobject/priest/talisman.obj", "[basic motion] `talisman.ani` [int data] 3 7");
        f.files.put("passiveobject/priest/bolt.obj", "[basic motion] `bolt.ani`");
        f.files.put("passiveobject/priest/talisman.ani", ani(120, 120, 120, 120, 120, 120, 120, 10000, 120));
        f.files.put("passiveobject/priest/bolt.ani", ani(60, 60, 120));
        f.files.put("passiveobject/common/warning.obj", "[etc motion] `nodraw.ani` `warning.ani`");
        f.files.put("passiveobject/common/nodraw.ani", ani(1000));
        f.files.put("passiveobject/common/warning.ani", ani(250, 250, 250, 250));
        f.files.put("passiveobject/common/particle/thunderboltparticle.ptl", "[life time] 600 [creation frequency] -1 [maximum create number] 18");
        f.files.put(Act1SwordAsuraSupplement.GOBLIN + "stay.ani", ani(100));
        f.files.put(Act1SwordAsuraSupplement.GOBLIN + "damage1.ani", ani(100));
        f.action("throw motion 3-1", "deliver1", 100);
        f.action("throw motion 3-2", "deliver2", 100);
        String path = "skill/priest/thunderbolttalisman.skl";
        String text = "[casting time] 800 [static data] 700 500 2000 200 [level info] 4 6000 110 3 1500";
        var hit = Act1PriestSupplement.find(path, text, f.files::get, f.actions, 1);
        var miss = Act1PriestSupplement.find(path, text, f.files::get, f.actions, 1, null, "thunderbolttalisman:miss");
        var bolts = hit.visuals().stream().filter(v -> v.path().endsWith("/bolt.ani")).toList();
        assertEquals(2, bolts.size());
        var warning = hit.visuals().stream().filter(v -> v.path().endsWith("/warning.ani")).findFirst().orElseThrow();
        assertEquals(2800, warning.start());
        assertEquals(3300, warning.end());
        assertEquals(3300, bolts.get(0).start());
        assertEquals(bolts.get(0).x(), warning.x());
        assertEquals(2000, bolts.get(1).start() - bolts.get(0).start());
        assertTrue(bolts.get(0).x() > 150);
        assertEquals(3360, hit.emitters().get(0).start());
        assertEquals(bolts.get(0).x(), hit.emitters().get(0).x());
        assertTrue(miss.visuals().stream().noneMatch(v -> v.path().endsWith("/bolt.ani")));
        var glyph = miss.visuals().stream().filter(v -> v.path().endsWith("/talisman.ani")).toList();
        assertEquals(3, glyph.size());
        var loop = glyph.get(1);
        assertEquals(1160, loop.start());
        assertEquals(6800, loop.end());
        assertTrue(loop.style().animation().loop());
        assertEquals(4, loop.style().animation().frames().size());
        assertEquals(6800, glyph.get(2).start());
        assertEquals(6920, glyph.get(2).end());
    }

    @Test void hyunmooChasesThenHoldsARealTargetBeforeReleasingIt() throws Exception {
        var f = summonFixture(69, "hyunmoo", 24019, 21);
        var attack = ani(160, 160, 160, 160, 160)
                .replace("[FRAME001]", "[FRAME001] [ATTACK BOX] -52 -15 31 66 30 48")
                .replace("[FRAME002]", "[FRAME002] [ATTACK BOX] -91 -15 15 135 30 22");
        f.files.put("passiveobject/priest/motion6.ani", attack);
        String path = "skill/priest/hyunmoo.skl";
        String text = "[casting time] 600 [static data] 600 600 150 75 [level info] 2 20000 7500";
        var hit = Act1PriestSupplement.find(path, text, f.files::get, f.actions, 1);
        var charge = hit.visuals().stream().filter(v -> v.path().endsWith("/motion6.ani")).findFirst().orElseThrow();
        var hold = hit.visuals().stream().filter(v -> v.path().endsWith("/motion10.ani")).findFirst().orElseThrow();
        assertTrue(charge.motion().offsetAt(charge.end()) > charge.motion().offsetAt(charge.start()));
        assertTrue(charge.end() >= charge.start() + 320);
        assertTrue(hold.start() > charge.start());
        var released = hit.visuals().stream().filter(v -> v.path().equals(Act1SwordAsuraSupplement.GOBLIN + "stay.ani"))
                .filter(v -> v.start() > hold.start()).findFirst().orElseThrow();
        assertEquals(hold.x(), released.x(), 0.001);
        var underlay = hit.visuals().stream().filter(v -> v.path().endsWith("/motion12.ani") && v.start() == hold.start())
                .findFirst().orElseThrow();
        var release = hit.visuals().stream().filter(v -> v.path().endsWith("/motion14.ani")).findFirst().orElseThrow();
        var releaseUnderlay = hit.visuals().stream().filter(v -> v.path().endsWith("/motion16.ani")).findFirst().orElseThrow();
        assertTrue(underlay.layer() < hold.layer());
        assertEquals(hold.y(), underlay.y());
        assertTrue(underlay.style().reverseFacing());
        assertTrue(releaseUnderlay.layer() < release.layer());
        assertEquals(release.y(), releaseUnderlay.y());
        assertTrue(releaseUnderlay.style().reverseFacing());
        assertTrue(hit.visuals().stream().noneMatch(v -> v.path().startsWith(Act1SwordAsuraSupplement.GOBLIN)
                && v.start() <= hold.start() && v.end() > hold.start()));
        assertTrue(hit.visuals().stream().anyMatch(v -> v.path().endsWith("/motion10.ani")
                && v.style().animation() != null && v.style().animation().loop()
                && v.style().animation().frames().size() == 3));
        assertTrue(hit.visuals().stream().anyMatch(v -> v.path().endsWith("/motion19.ani")
                && v.style().animation() != null && v.style().animation().loop()
                && v.style().animation().frames().size() == 2));
        var empty = Act1PriestSupplement.find(path, text, f.files::get, f.actions, 1, null, "hyunmoo:empty");
        assertNotNull(empty);
        assertTrue(empty.visuals().stream().noneMatch(v -> v.path().contains("/goblin/") || v.path().endsWith("/motion6.ani")));
    }

    @Test void baekhoRequiresTwoSummonsForTheRelayAndHitsTheTargetInBothDirections() throws Exception {
        var f = summonFixture(70, "baekho", 24020, 28);
        f.files.compute("passiveobject/passiveobject.lst", (k, v) -> v + " 24021 `priest/thunder.obj`");
        f.files.put("passiveobject/priest/thunder.obj", "[basic motion] `thunder.ani` [etc motion] `thunderglow.ani`");
        f.files.put("passiveobject/priest/thunder.ani", ani(120, 120, 120, 120)
                .replace("[FRAME000]", "[FRAME000] [ATTACK BOX] -28 -26 -30 60 52 61"));
        f.files.put("passiveobject/priest/thunderglow.ani", ani(120));
        f.files.put("passiveobject/character/priest/animation/baekhothunderhit.ani", ani(120));
        f.files.put("passiveobject/character/priest/particle/baekho.ptl",
                "[x axis]\n[move type] `[straight line]`\n[move variable 1] 750 750");
        String path = "skill/priest/baekho.skl";
        String text = "[casting time] 600 [cool time] 6000 [static data] 500 150 500 100 640 960 30 50 100 150 1000 "
                + "[level info] 4 30000 1500 70 100";
        var chain = Act1PriestSupplement.find(path, text, f.files::get, f.actions, 1);
        var orbs = chain.visuals().stream().filter(v -> v.path().endsWith("/thunder.ani")).limit(2).toList();
        assertEquals(2, orbs.size());
        assertEquals(3260, orbs.get(0).start());
        assertEquals(750, orbs.get(0).motion().velocity());
        assertEquals(-750, orbs.get(1).motion().velocity());
        var hits = chain.visuals().stream().filter(v -> v.path().endsWith("/baekhothunderhit.ani")).limit(2).toList();
        assertEquals(2, hits.size());
        assertEquals(hits.get(0).x(), hits.get(1).x());
        assertTrue(hits.get(0).start() > orbs.get(0).start() && hits.get(0).start() < orbs.get(0).end());
        var single = Act1PriestSupplement.find(path, text, f.files::get, f.actions, 1, null, "baekho:single");
        assertNotNull(single);
        assertTrue(single.visuals().stream().noneMatch(v -> v.path().endsWith("/thunder.ani") || v.path().contains("/goblin/")));
        assertTrue(single.visuals().stream().anyMatch(v -> v.path().endsWith("/motion25.ani")
                && v.style().animation() != null && v.style().animation().loop() && v.end() == 30600));
        assertTrue(chain.visuals().stream().noneMatch(v -> v.path().endsWith("/motion25.ani")
                && v.start() <= 5000 && v.end() > 5000));
    }

    private static Fixture summonFixture(int id, String skill, int objectId, int slots) {
        var fixture = new Fixture(new HashMap<>(), new HashMap<>());
        fixture.files.put("skill/priestskill.lst", id + " `priest/" + skill + ".skl`");
        fixture.files.put("passiveobject/passiveobject.lst", objectId + " `priest/summon.obj`");
        var object = new StringBuilder("[basic motion] `motion0.ani` [etc motion]");
        for (int slot = 0; slot < slots; slot++) {
            if (slot > 0) object.append(" `motion").append(slot).append(".ani`");
            fixture.files.put("passiveobject/priest/motion" + slot + ".ani", ani(120, 120, 120, 120, 120, 120, 120, 120, 120));
        }
        fixture.files.put("passiveobject/priest/summon.obj", object.toString());
        for (String pose : List.of("stay", "damage1", "damage2", "down"))
            fixture.files.put(Act1SwordAsuraSupplement.GOBLIN + pose + ".ani", ani(120));
        fixture.action("throw motion 2-1", "prepare", 100);
        fixture.action("throw motion 2-2", "release", 100);
        fixture.action("waiting motion", "stay", 100);
        return fixture;
    }

    @Test void repeatedSmashAcceleratesOnlyTheRepeatedSwingsAndUsesTheFinalObjectAtFrameFive() throws Exception {
        var f = new Fixture(new HashMap<>(), new HashMap<>());
        f.files.put("skill/priestskill.lst", "49 `Priest/RepeatedSmash.skl`");
        f.action("etc motion:28", "repeatedsmash", 70, 70, 100, 60, 60, 60, 60, 70);
        f.action("etc motion:29", "repeatedsmashfinal", 70, 70, 500, 60, 60, 60, 60, 260);
        f.action("waiting motion", "stay", 100);
        for (String name : List.of("swingstart", "swingrepeat", "swingfinal", "charge", "crashrepeat1", "crashrepeat2")) {
            f.files.put(EFFECT + "repeatedsmash/" + name + ".ani", ani(80, 80, 100, 60, 60));
        }
        String floor = "passiveobject/character/priest/";
        f.files.put("passiveobject/passiveobject.lst", "24015 `Character/Priest/RepeatedSmashSub.obj`");
        f.files.put(floor + "repeatedsmashsub.obj", """
                [basic motion] `Animation/RepeatedSmashSub1.ani`
                [etc motion] `Animation/RepeatedSmashSub2.ani` `Animation/RepeatedSmashSub3.ani` [/etc motion]
                """);
        for (int i = 1; i <= 3; i++) f.files.put(floor + "animation/repeatedsmashsub" + i + ".ani", ani(70, 70));
        var sequence = Act1PriestSupplement.find("skill/priest/repeatedsmash.skl",
                "[static data] 4 150 200 [level info] 9 120 200 2 4 200 1168 300 36 2000",
                f.files::get, f.actions, 1);
        assertNotNull(sequence);
        assertEquals(List.of(550L, 440L, 400L, 367L, 1140L),
                sequence.poses().stream().limit(5).map(p -> p.end() - p.start()).toList());
        assertTrue(sequence.poses().get(1).fitToStage());
        var repeat = sequence.visuals().stream().filter(v -> v.path().endsWith("/swingrepeat.ani")).findFirst().orElseThrow();
        assertEquals(56, repeat.style().frameOverrides().get(0).delay());
        var hit = sequence.visuals().stream().filter(v -> v.path().endsWith("/crashrepeat1.ani")).findFirst().orElseThrow();
        assertEquals(360, hit.start());
        assertEquals(133, hit.x());
        var finalBlasts = sequence.visuals().stream().filter(v -> v.path().contains("/repeatedsmashsub")).toList();
        assertEquals(3, finalBlasts.size());
        assertEquals(2517, finalBlasts.get(0).start());
    }

    @Test void duckingAndSwayUseOppositeTravelAndLeaveTheFrameOneAfterimageAtItsSpawnPosition() throws Exception {
        var f = new Fixture(new HashMap<>(), new HashMap<>());
        f.files.put("skill/priestskill.lst", "2 `Priest/Ducking.skl` 3 `Priest/Sway.skl`");
        f.action("etc motion:2", "ducking", 70, 70, 170);
        f.action("etc motion:3", "sway", 70, 70, 70);
        f.files.put(EFFECT + "ducking.ani", ani(70, 70, 70, 70));
        f.files.put(EFFECT + "sway.ani", ani(70, 70, 70, 70));
        String text = "[static data] 0 [level info] 3 150 50 500";
        var duck = Act1PriestSupplement.find("skill/priest/ducking.skl", text, f.files::get, f.actions, 1);
        var sway = Act1PriestSupplement.find("skill/priest/sway.skl", text, f.files::get, f.actions, 1);
        assertNotNull(duck);
        assertNotNull(sway);
        assertEquals(150, duck.poses().get(0).motion().offsetAt(500), 0.001);
        assertEquals(-150, sway.poses().get(0).motion().offsetAt(500), 0.001);
        assertEquals(70, duck.visuals().get(0).start());
        assertEquals(39.06, duck.visuals().get(0).x(), .001);
        assertNull(duck.visuals().get(0).motion());
    }

    @Test void quakeAreaHoldsItsAirborneFrameUntilTheScriptedTrajectoryLands() throws Exception {
        var f = new Fixture(new HashMap<>(), new HashMap<>());
        f.files.put("skill/priestskill.lst", "11 `Priest/QuakeArea.skl`");
        f.action("etc motion:11", "quakearea", 70, 70, 70, 240, 10000, 70, 70, 500, 70, 70, 70, 80);
        f.files.put(EFFECT + "quakeareaweapon.ani", ani(80, 80));
        f.files.put(EFFECT + "quakeareacrash.ani", ani(70, 70, 70));
        f.files.put(EFFECT + "quakeareadust.ani", ani(60, 60, 60, 60, 60));
        f.files.put("passiveobject/passiveobject.lst", "24001 `Character/Priest/QuakeArea.obj`");
        f.files.put("passiveobject/character/priest/quakearea.obj", "[basic motion] `Animation/QuakeArea.ani`");
        f.files.put("passiveobject/character/priest/animation/quakearea.ani", ani(60, 60, 60, 60));
        var sequence = Act1PriestSupplement.find("skill/priest/quakearea.skl", "[static data] 150 -2000 100 500",
                f.files::get, f.actions, 1);
        assertNotNull(sequence);
        assertEquals(450, sequence.poses().get(1).start());
        assertEquals(775, sequence.poses().get(2).start());
        assertEquals(5, sequence.poses().get(2).firstFrame());
        var ground = sequence.visuals().stream().filter(v -> v.path().startsWith("passiveobject/")).findFirst().orElseThrow();
        assertEquals(775, ground.start());
        assertEquals(38, ground.x());
    }

    @Test void quakeAreaPlacesItsAirborneEffectAtTheEquippedWeaponsAttackBox() throws Exception {
        var f = new Fixture(new HashMap<>(), new HashMap<>());
        f.files.put("skill/priestskill.lst", "11 `Priest/QuakeArea.skl`");
        f.action("etc motion:11", "quakearea", 70, 70, 70, 240, 10000, 70);
        String weapon="equipment/character/priest/weapon/test.equ";
        String motions="[etc motion] "+String.join(" ", java.util.Collections.nCopies(12,"`Animation/QuakeArea.ani`"))+" [/etc motion]";
        f.files.put("character/priest/priest.chr", motions);
        f.files.put(weapon,"[equipment type] `[weapon]` "+motions);
        f.files.put("equipment/character/priest/weapon/animation/quakearea.ani",
                ani(70,70,70,240,10000)+" [ATTACK BOX] 7 -15 22 47 30 93");
        f.files.put(EFFECT+"quakeareaweapon.ani",ani(80,80));
        var sequence=Act1PriestSupplement.find("skill/priest/quakearea.skl","[static data] 150 -2000 100 500",
                f.files::get,f.actions,1,weapon);
        assertNotNull(sequence);
        assertEquals(-22,sequence.visuals().get(0).y());
    }

    @Test void smasherShowsItsDashGlowAndCapturedTargetOnlyInTheHitBranch() throws Exception {
        var f = new Fixture(new HashMap<>(), new HashMap<>());
        f.files.put("skill/priestskill.lst","8 `Priest/Smasher.skl`");
        f.action("etc motion:7","smashertrygrab",300);
        f.action("etc motion:8","smashersuccessgrab",80,70,70,200,80);
        f.action("etc motion:9","smasherdash",10000,200);
        f.files.put(EFFECT+"smasher.ani",ani(70,70,70));
        for(String pose:List.of("stay","damage2","down"))
            f.files.put(Act1SwordAsuraSupplement.GOBLIN+pose+".ani",ani(100));
        String path="skill/priest/smasher.skl";
        String text="[static data] 600 0 200 2 500 600 150";
        var sequence=Act1PriestSupplement.find(path,text,f.files::get,f.actions,1);
        assertNotNull(sequence);
        assertEquals(800,sequence.visuals().get(0).start());
        assertEquals(1000,sequence.visuals().get(0).end());
        assertTrue(sequence.visuals().stream().anyMatch(v->v.path().contains("/goblin/")&&v.start()==1000));
        var choice=Act1PriestSupplement.smasherChoice(path,f.files::get,true);
        var miss=Act1PriestSupplement.find(path,text,f.files::get,f.actions,1,null,choice.key());
        assertNotNull(miss);
        assertTrue(miss.visuals().isEmpty());
    }

    @Test void hurricaneRollUsesHookStartThenHookLoopThenUppercut() throws Exception {
        var f = new Fixture(new HashMap<>(), new HashMap<>());
        f.files.put("skill/priestskill.lst", "4 `Priest/HurricaneRoll.skl`");
        f.action("etc motion:26", "hurricanerollhook", 40, 40, 40, 40, 40, 20, 20, 40, 40, 40, 40, 40);
        f.action("etc motion:27", "hurricanerollupper", 50, 50, 50, 50, 70, 140, 200);
        for (String effect : List.of("hookstart", "hookloop", "upper", "area1", "area2"))
            f.files.put(EFFECT + "hurricaneroll/" + effect + ".ani", ani(50, 50, 50));
        var sequence = Act1PriestSupplement.find("skill/priest/hurricaneroll.skl", "[level info] 6 70 350 3 480 100 60",
                f.files::get, f.actions, 1);
        assertNotNull(sequence);
        assertEquals(List.of("etc motion:26", "etc motion:26", "etc motion:26", "etc motion:27"),
                sequence.poses().stream().map(Act1SkillSequence.Pose::action).toList());
        assertEquals(List.of(0L, 440L, 880L, 1320L), sequence.poses().stream().map(Act1SkillSequence.Pose::start).toList());
        assertEquals(2, sequence.visuals().stream().filter(v -> v.path().endsWith("/hookloop.ani")).count());
        var area = sequence.visuals().stream().filter(v -> v.path().endsWith("/area1.ani")).findFirst().orElseThrow();
        assertEquals(-50, area.y());
        assertEquals(1320, area.end());
    }

    private record Fixture(Map<String, String> files, Map<String, String> actions) {
        void action(String name, String stem, int... delays) {
            actions.put(name, "animation/" + stem + ".ani");
            files.put(PRIEST + "animation/" + stem + ".ani", ani(delays));
        }
    }

    private static String ani(int... delays) {
        var text = new StringBuilder("[FRAME MAX] ").append(delays.length);
        for (int i = 0; i < delays.length; i++) text.append(String.format(" [FRAME%03d] [DELAY] %d", i, delays[i]));
        return text.toString();
    }
}
