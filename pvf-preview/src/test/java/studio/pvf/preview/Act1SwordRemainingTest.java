package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Act1SwordRemainingTest {
    @Test void rasaWarnsOneTargetThenSpawnsAndAttachesTheOriginalPairedGhost() throws Exception {
        var files = new HashMap<String, String>();
        String path = "skill/swordman/epidemicrasa.skl";
        files.put("skill/swordmanskill.lst", "75 `swordman/epidemicrasa.skl`");
        files.put("passiveobject/passiveobject.lst", "20040 `sword/field.obj` 20042 `sword/ghost.obj` 48012 `common/warning.obj`");
        files.put("passiveobject/sword/field.obj", "[basic motion] `field.ani` [etc motion] `field.ani` `field.ani` `field.ani` `field.ani` `field.ani`");
        files.put("passiveobject/sword/field.ani", animation(5, 100));
        files.put("passiveobject/common/warning.obj", "[etc motion] `nodraw.ani` `warning.ani`");
        files.put("passiveobject/common/nodraw.ani", animation(10, 100));
        files.put("passiveobject/common/warning.ani", animation(10, 100));
        files.put("passiveobject/sword/ghost.obj", "[basic motion] `startup.ani` [etc motion] `startup_ldodge.ani` "
                + "`stay.ani` `stay_ldodge.ani` `grab.ani` `grab_ldodge.ani` `grab_stay.ani` `grab_stay_ldodge.ani`");
        for (String name : new String[]{"startup", "stay", "grab", "grab_stay"}) {
            String text = animation(name.equals("startup") ? 12 : 8, 100);
            files.put("passiveobject/sword/" + name + ".ani", text);
            files.put("passiveobject/sword/" + name + "_ldodge.ani", text);
        }
        files.put(Act1SwordAsuraSupplement.GOBLIN + "stay.ani", animation(4, 100));
        var actions = Map.of("throw motion 1-1", "prepare.ani", "throw motion 1-2", "release.ani", "waiting motion", "wait.ani");
        String text = "[casting time] 500 [static data] 600 1000 80 60 50 50 250 "
                + "[level info] 20 10000 6000 20 1000 20 1500 1000 36 5000 350 1000 36 10000 50 20 1000 36 5000 350 6000";
        var hit = Act1SwordRemaining.find(path, text, files::get, actions, 1);
        var miss = Act1SwordRemaining.find(path, text, files::get, actions, 1, "epidemicrasa:miss");
        var warning = hit.visuals().stream().filter(v -> v.path().endsWith("/warning.ani")).findFirst().orElseThrow();
        var startup = hit.visuals().stream().filter(v -> v.path().endsWith("/startup.ani")).findFirst().orElseThrow();
        var held = hit.visuals().stream().filter(v -> v.path().endsWith("/grab_stay.ani")).findFirst().orElseThrow();
        assertEquals(1500, warning.start());
        assertEquals(2500, startup.start());
        assertEquals(warning.x(), startup.x());
        var contact = hit.visuals().stream().filter(v -> v.path().endsWith("/stay.ani")
                && v.path().startsWith("passiveobject/")).findFirst().orElseThrow();
        assertEquals(3700, contact.start());
        assertEquals(3800, contact.end());
        assertEquals(4600, held.start());
        assertEquals(9800, held.end());
        assertTrue(miss.visuals().stream().noneMatch(v -> v.path().endsWith("/grab_stay.ani")));
    }

    @Test void kallaStopsTheShadowOnContactAndKeepsBothTargetFireLayersForTheirAttackCount() throws Exception {
        var files = new HashMap<String, String>();
        String path = "skill/swordman/kalla.skl";
        files.put("skill/swordmanskill.lst", "82 `swordman/kalla.skl`");
        files.put("passiveobject/passiveobject.lst", "20045 `sword/shadow.obj` 20047 `sword/fire.obj`");
        files.put("passiveobject/sword/shadow.obj", "[basic motion] `shadow.ani`");
        files.put("passiveobject/sword/fire.obj", "[basic motion] `dodge.ani` [etc motion] `normal.ani`");
        files.put("passiveobject/sword/shadow.ani", animation(5, 80));
        files.put("passiveobject/sword/dodge.ani", animation(10, 60));
        files.put("passiveobject/sword/normal.ani", animation(10, 60));
        files.put("character/swordman/particle/kallaland.ptl", "[x axis] [move type] `[straight line]` "
                + "[move variable 1] 500 500 [life time] 700");
        for (String body : new String[]{"kallaland", "ready", "finish1", "finish2", "finish3"}) {
            files.put("character/swordman/" + body + ".ani", animation(5, 80));
        }
        files.put("character/swordman/effect/animation/kalla/expeffect1.ani", animation(6, 70));
        files.put(Act1SwordAsuraSupplement.GOBLIN + "stay.ani", animation(4, 80));
        var actions = Map.of("etc motion:68", "kallaland.ani", "etc motion:70", "ready.ani",
                "etc motion:71", "finish1.ani", "etc motion:72", "finish2.ani", "etc motion:73", "finish3.ani",
                "throw motion 1-1", "prepare.ani", "throw motion 1-2", "release.ani", "waiting motion", "wait.ani");
        String text = "[casting time] 500 [static data] 500 100 -300 [level info] 8 20000 558 150 225 300 5 1000 56";
        var hit = Act1SwordRemaining.find(path, text, files::get, actions, 1);
        var miss = Act1SwordRemaining.find(path, text, files::get, actions, 1, "kalla:miss");
        var shadow = hit.visuals().stream().filter(v -> v.path().endsWith("/shadow.ani")).findFirst().orElseThrow();
        var fire = hit.visuals().stream().filter(v -> v.path().endsWith("/dodge.ani")).findFirst().orElseThrow();
        assertEquals(shadow.end(), fire.start());
        assertEquals(shadow.x() + shadow.motion().offsetAt(shadow.end()), fire.x(), .001);
        assertEquals(5000, fire.end() - fire.start());
        assertTrue(hit.visuals().stream().anyMatch(v -> v.path().endsWith("/normal.ani") && v.start() == fire.start()));
        assertTrue(hit.visuals().stream().anyMatch(v -> v.path().endsWith("/expeffect1.ani") && v.start() == fire.start()));
        assertTrue(miss.visuals().stream().noneMatch(v -> v.path().endsWith("/dodge.ani")));
    }

    @Test void ghostStepMarksTheCapturedTargetAndReleasesBothSlashLayersAfterTheHold() throws Exception {
        var files = new HashMap<String, String>();
        String path = "skill/swordman/ghoststepslash.skl";
        String folder = "character/swordman/effect/animation/ghoststepslash/";
        files.put("skill/swordmanskill.lst", "60 `swordman/ghoststepslash.skl`");
        files.put("passiveobject/passiveobject.lst", "20026 `sword/step.obj`");
        files.put("passiveobject/sword/step.obj", "[basic motion] `step.ani`");
        files.put("passiveobject/sword/step.ani", animation(6, 100));
        files.put("character/swordman/ready.ani", animation(2, 120));
        files.put("character/swordman/move.ani", animation(4, 100));
        for (String effect : new String[]{"skull", "slash1", "slash2"}) {
            files.put(folder + effect + ".ani", animation(8, 90));
        }
        for (String action : new String[]{"stay", "damage1"}) {
            files.put(Act1SwordAsuraSupplement.GOBLIN + action + ".ani", animation(1, 100));
        }
        var actions = Map.of("etc motion:18", "ready.ani", "etc motion:19", "move.ani", "waiting motion", "wait.ani");
        var hit = Act1SwordRemaining.find(path, "[static data] 300", files::get, actions, 1);
        var miss = Act1SwordRemaining.find(path, "[static data] 300", files::get, actions, 1, "ghoststepslash:miss");
        var skull = hit.visuals().stream().filter(v -> v.path().equals(folder + "skull.ani")).findFirst().orElseThrow();
        var slash = hit.visuals().stream().filter(v -> v.path().equals(folder + "slash1.ani")).findFirst().orElseThrow();
        assertEquals(640, skull.start());
        assertEquals(1140, slash.start());
        assertEquals(skull.x(), slash.x());
        assertEquals(skull.y() / 2, slash.y());
        assertEquals(2, hit.visuals().stream().filter(v -> v.path().equals(folder + "slash2.ani")).count());
        assertNotNull(miss);
        assertEquals(0, miss.visuals().stream().filter(v -> v.path().startsWith(Act1SwordAsuraSupplement.GOBLIN)
                || v.path().startsWith(folder)).count());
    }

    @Test void waveSpinExplodesFromTheOrbAtFrameSixOfTheFifteenFramePose() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "74 `swordman/wavespinarea.skl`");
        files.put("passiveobject/passiveobject.lst", "20038 `sword/orb.obj`");
        files.put("passiveobject/sword/orb.obj",
                "[basic motion] `orb.ani` [etc motion] `dodge1.ani` `dodge2.ani` `hot.ani`");
        for (String name : new String[]{"orb", "dodge1", "dodge2", "hot"}) {
            files.put("passiveobject/sword/" + name + ".ani", animation(4, 80));
        }
        files.put("character/swordman/bomb.ani", animation(15, 70));
        files.put("character/swordman/effect/animation/wavespinarea/circle.ani", animation(1, 80));
        files.put("character/swordman/effect/animation/wavespinarea/create.ani", animation(8, 80));
        files.put("character/swordman/effect/animation/wavespinarea/holdfloor.ani", animation(3, 80));
        files.put("character/swordman/effect/animation/wavespinarea/hold.ani", animation(5, 80));
        for (int i = 1; i <= 3; i++) {
            files.put("passiveobject/character/swordman/animation/wavespinareabomb" + i + ".ani", animation(12, 80));
        }
        for (String action : new String[]{"stay", "damage1", "overturn"}) {
            files.put(Act1SwordAsuraSupplement.GOBLIN + action + ".ani", animation(1, 80));
        }
        var sequence = Act1SwordRemaining.find("skill/swordman/wavespinarea.skl",
                "[casting time] 500 [static data] 6000 1500", files::get,
                Map.of("throw motion 2-1", "summon.ani", "throw motion 2-2", "release.ani",
                        "etc motion:56", "bomb.ani", "waiting motion", "wait.ani"), 1, "act1-wave-marks-3");

        assertNotNull(sequence);
        var orb = sequence.visuals().stream().filter(visual -> visual.path().endsWith("/orb.ani")).findFirst().orElseThrow();
        var explosion = sequence.visuals().stream().filter(visual -> visual.path().contains("bomb1")).findFirst().orElseThrow();
        assertEquals(900, orb.start());
        assertNotEquals(orb.motion().offsetAt(1000), orb.motion().offsetAt(1250));
        assertEquals(6500, explosion.start());
        assertEquals(orb.end(), explosion.start());
        assertEquals(orb.motion().offsetAt(orb.end()), explosion.x(), .001);
        assertEquals(orb.y() + orb.motion().offsetYAt(orb.end()), explosion.y(), .001);
        assertEquals(6, sequence.poses().stream().filter(pose -> pose.start() == explosion.start())
                .findFirst().orElseThrow().firstFrame());
        var floor = sequence.visuals().stream().filter(visual -> visual.path().endsWith("/circle.ani")).toList();
        assertEquals(4, floor.size());
        assertEquals(2, floor.stream().filter(visual -> visual.style().reverseFacing()).count());
        assertEquals(2, floor.stream().filter(visual -> visual.style().frameOverrides().containsKey(0)).count());
        assertEquals(3, sequence.visuals().stream().filter(visual -> visual.path().endsWith("/orb.ani"))
                .map(visual -> visual.motion().offsetAt(950)).distinct().count());
        assertEquals(3, sequence.visuals().stream().filter(visual -> visual.path().contains("wavespinareabomb")).count());
        var heldTarget = sequence.visuals().stream().filter(visual -> visual.path().endsWith("/damage1.ani"))
                .findFirst().orElseThrow();
        var holdGlow = sequence.visuals().stream().filter(visual -> visual.path().endsWith("/hold.ani"))
                .findFirst().orElseThrow();
        assertEquals(660, holdGlow.start());
        assertEquals(740, heldTarget.start());
        assertEquals(100, heldTarget.motion().heightAt(1240), .001);
        assertEquals(150, heldTarget.x() + heldTarget.motion().offsetAt(1240), .001);
        assertEquals(6500, heldTarget.end());
    }

    @Test void successfulGrabRetainsTheCapturePoseAndUsesItsOwnReleaseClock() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "31 `swordman/grabblastblood.skl`");
        files.put("passiveobject/passiveobject.lst", "20017 `sword/blood.obj`");
        files.put("passiveobject/sword/blood.obj",
                "[basic motion] `blood.ani` [etc motion] `blood2.ani` `blood3.ani` `blood4.ani`");
        for (String name : new String[]{"blood", "blood2", "blood3", "blood4"}) {
            files.put("passiveobject/sword/" + name + ".ani", animation(6, 100));
        }
        files.put("character/swordman/grab.ani", animation(17, 40));
        files.put("character/swordman/effect/animation/grabblastblood.ani", animation(12, 50));
        var sequence = Act1SwordRemaining.find("skill/swordman/grabblastblood.skl",
                "[static data] 1200 500", files::get,
                Map.of("etc motion:10", "grab.ani", "waiting motion", "wait.ani"), 1);

        assertNotNull(sequence);
        var capture = sequence.poses().get(1);
        assertEquals(16, capture.firstFrame());
        assertEquals(160, capture.start());
        assertEquals(1360, capture.end());
        var blasts = sequence.visuals().stream().filter(visual -> visual.path().startsWith("passiveobject/")).toList();
        assertEquals(4, blasts.size());
        for (var blast : blasts) {
            assertEquals(910, blast.start());
            assertEquals(43, blast.x(), .001);
            assertEquals(-89.71, blast.y(), .001);
        }
        assertEquals(1510, sequence.poses().get(2).end());
    }

    private static String animation(int count, int delay) {
        var text = new StringBuilder("[frame max] ").append(count);
        for (int frame = 0; frame < count; frame++) {
            text.append(" [frame").append(String.format("%03d", frame))
                    .append("] [image] `sprite.img` 0 [delay] ").append(delay);
        }
        return text.toString();
    }
}
