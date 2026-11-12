package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1SwordAsuraSupplementTest {
    @Test void releaseWaveUsesSummonReleaseAndEndsBothLayersOnTheirOwnClocks() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "32 `swordman/releasewave.skl`");
        files.put("passiveobject/passiveobject.lst", "20018 `sword/wave.obj`");
        files.put("passiveobject/sword/wave.obj", "[basic motion] `wave.ani`");
        files.put("character/swordman/effect/animation/releasewave1.ani", animation(70, 70, 70));
        files.put("character/swordman/effect/animation/releasewave2.ani", animation(70, 70));
        var sequence = Act1SwordAsuraSupplement.find("skill/swordman/releasewave.skl", "[static data] 600",
                files::get, Map.of("throw motion 2-2", "summon2.ani"), 1);
        assertNotNull(sequence);
        assertEquals("throw motion 2-2", sequence.poses().get(0).action());
        assertEquals(600, sequence.poses().get(0).end());
        assertEquals(140, sequence.visuals().get(0).end());
        assertEquals(210, sequence.visuals().get(1).end());
        assertTrue(sequence.visuals().get(0).layer() < 3500 && sequence.visuals().get(1).layer() > 3500);
    }

    @Test void shockWaveGroundGraphicExpiresWhileTargetHoldAndExplosionContinue() throws Exception {
        String path = "skill/swordman/shockwavearea.skl";
        String folder = "character/swordman/effect/animation/shockwavearea/";
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "57 `swordman/shockwavearea.skl`");
        files.put("passiveobject/passiveobject.lst", "20021 `sword/shock.obj`");
        files.put("passiveobject/sword/shock.obj", "[basic motion] `hold.ani` [etc motion] `end.ani` `blast.ani`");
        files.put("passiveobject/sword/hold.ani", animation(85, 85, 85, 85, 85));
        files.put("passiveobject/sword/end.ani", animation(85));
        files.put("passiveobject/sword/blast.ani", animation(85, 85, 85, 85, 85));
        files.put(folder + "area.ani", animation(85, 85, 85, 85, 85));
        files.put("character/swordman/cast.ani", animation(80));
        files.put("character/swordman/smash.ani", animation(85));
        String skill = "[casting time] 400 [static data] 80 2000";
        var poses = Map.of("etc motion:12", "cast.ani", "etc motion:13", "smash.ani");
        var hit = Act1SwordAsuraSupplement.find(path, skill, files::get, poses, 1, "act1-shockwave-hit");
        var miss = Act1SwordAsuraSupplement.find(path, skill, files::get, poses, 1, "act1-shockwave-miss");
        assertNotNull(hit);
        assertNotNull(miss);
        assertEquals(825, hit.visuals().get(0).end());
        assertEquals(825, miss.visuals().get(0).end());
        var hold = hit.visuals().stream().filter(visual -> visual.path().endsWith("/hold.ani")).toList();
        assertEquals(655, hold.get(0).start());
        assertEquals(255, hold.get(1).style().loopPeriod());
        assertEquals(2570, hold.get(1).end());
        assertEquals(2655, hit.visuals().stream().filter(visual -> visual.path().endsWith("/blast.ani"))
                .findFirst().orElseThrow().start());
        assertEquals(1, miss.visuals().size());
    }

    @Test void fiveMarkChoiceUsesAllFiveStockGlyphs() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "47 `swordman/wavemark.skl`");
        for (int i = 1; i <= 5; i++) {
            files.put("character/swordman/effect/animation/wavemark/addmark" + i + ".ani", animation(80, 80));
        }
        var actions = Map.of("throw motion 2-1", "summon1.ani", "throw motion 2-2", "summon2.ani",
                "waiting motion", "wait.ani");
        String path = "skill/swordman/wavemark.skl";
        String skill = "[casting time] 200 [static data] 500 30000 5";
        var one = Act1SwordAsuraSupplement.find(path, skill, files::get, actions, 1, "act1-wave-marks-1");
        var five = Act1SwordAsuraSupplement.find(path, skill, files::get, actions, 1, "act1-wave-marks-5");
        assertNotNull(one);
        assertNotNull(five);
        assertEquals(1, one.visuals().size());
        assertEquals(5, five.visuals().size());
        assertEquals(5, five.visuals().stream().map(Act1SkillSequence.Visual::path).distinct().count());
        assertEquals(5, five.visuals().stream().map(visual -> visual.motion().offsetAt(300)).distinct().count());
    }

    @Test void reflectGuardFadesAtRangeWithoutTheHitTriggeredExplosion() throws Exception {
        String path = "skill/swordman/reflectguard.skl";
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "2 `swordman/reflectguard.skl`");
        files.put("passiveobject/passiveobject.lst", "20032 `sword/bead.obj`");
        files.put("passiveobject/sword/bead.obj", "[basic motion] `ball.ani` [etc motion] `fade.ani`");
        files.put("passiveobject/sword/ball.ani", animation(70));
        files.put("passiveobject/sword/fade.ani", animation(350));
        files.put("character/swordman/reflectguard.ani", animation(80, 80, 80, 80, 80));
        files.put("character/swordman/particle/reflectguardbead.ptl",
                "[x axis] [move type] `[straight line]` [move variable 1] 180 180");
        var sequence = Act1SwordAsuraSupplement.find(path, "[casting time] 400 [level info] 3 3000 30 1000",
                files::get, Map.of("etc motion:46", "reflectguard.ani", "waiting motion", "wait.ani"), 1, "reflectguard:miss");
        assertNotNull(sequence);
        assertEquals(2, sequence.visuals().size());
        var flight = sequence.visuals().get(0);
        var fade = sequence.visuals().get(1);
        assertEquals(640, flight.start());
        assertEquals(150, flight.x(), .001);
        assertEquals(-68, flight.y(), .001);
        assertEquals(3640, fade.start());
        assertEquals(3940, fade.end());
        assertEquals(540, fade.motion().offsetAt(fade.start()), .001);
    }

    @Test void reflectGuardSpawnsItsOwnExplosionAtTheBeadOnlyAfterTheLastRepeatedHit() throws Exception {
        String path = "skill/swordman/reflectguard.skl";
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "2 `swordman/reflectguard.skl`");
        files.put("passiveobject/passiveobject.lst", "20032 `sword/bead.obj` 20048 `sword/boom.obj`");
        files.put("passiveobject/sword/bead.obj", "[basic motion] `ball.ani` [etc motion] `fade.ani`");
        files.put("passiveobject/sword/boom.obj", "[basic motion] `boom.ani`");
        for (String name : new String[]{"ball", "fade", "boom"}) {
            files.put("passiveobject/sword/" + name + ".ani", animation(350));
        }
        files.put("passiveobject/character/swordman/animation/reflectguardbead/damage.ani", animation(120));
        files.put("character/swordman/reflectguard.ani", animation(80, 80, 80, 80, 80));
        files.put("character/swordman/particle/reflectguardbead.ptl",
                "[x axis] [move type] `[straight line]` [move variable 1] 180 180");
        for (String action : new String[]{"stay", "damage1", "overturn"}) {
            files.put(Act1SwordAsuraSupplement.GOBLIN + action + ".ani", animation(100));
        }
        var sequence = Act1SwordAsuraSupplement.find(path,
                "[casting time] 400 [static data] 250 150 10 120 [level info] 3 3000 30 1000",
                files::get, Map.of("etc motion:46", "reflectguard.ani", "waiting motion", "wait.ani"), 1);
        var flight = sequence.visuals().stream().filter(v -> v.path().endsWith("ball.ani")).findFirst().orElseThrow();
        var hits = sequence.visuals().stream().filter(v -> v.path().endsWith("damage.ani")).toList();
        var boom = sequence.visuals().stream().filter(v -> v.path().endsWith("boom.ani")).findFirst().orElseThrow();
        assertEquals(10, hits.size());
        assertEquals(150, hits.get(1).start() - hits.get(0).start());
        assertEquals(hits.get(0).start() + 1350, boom.start());
        assertEquals(boom.start(), flight.end());
        assertEquals(flight.x() + flight.motion().offsetAt(boom.start()), boom.x(), .001);
        assertEquals(-68, boom.y());
        assertEquals(1.2, boom.scale(), .001);
        assertFalse(sequence.visuals().stream().anyMatch(v -> v.path().endsWith("fade.ani")));
    }

    private static String animation(int... delays) {
        var text = new StringBuilder("[frame max] ").append(delays.length);
        for (int frame = 0; frame < delays.length; frame++) {
            text.append(" [frame").append(String.format("%03d", frame)).append("] [image] `sprite.img` 0 [delay] ")
                    .append(delays[frame]);
        }
        return text.toString();
    }
}
