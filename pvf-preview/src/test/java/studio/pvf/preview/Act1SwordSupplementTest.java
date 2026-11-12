package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1SwordSupplementTest {
    @Test void upgradedWaveUsesTheParentLifeAndTheChargedObject() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "50 `swordman/grandwave.skl` 51 `swordman/grandwavecharge.skl`");
        files.put("skill/swordman/grandwave.skl", "[casting time] 300 [level info] 2 700 1");
        files.put("passiveobject/passiveobject.lst", "20008 `sword/wave.obj`");
        files.put("passiveobject/sword/wave.obj", "[basic motion] `body.ani` [etc motion] `glow.ani`");
        files.put("passiveobject/sword/body.ani", animation(50));
        files.put("passiveobject/sword/glow.ani", animation(50));
        files.put("character/swordman/hardattack.ani", animation(50, 50, 50, 25, 25, 50, 50, 100));
        files.put("character/swordman/particle/grandwave.ptl", "[x axis] [move type] `[straight line]` [move variable 1] 300 300");
        var sequence = Act1SwordSupplement.find("skill/swordman/grandwavecharge.skl",
                "[static data] 700 120 150 300", files::get,
                Map.of("etc motion:1", "hardattack.ani", "waiting motion", "wait.ani"), 1, "");
        assertNotNull(sequence);
        assertEquals(1000, sequence.poses().get(0).end());
        assertEquals(1, sequence.poses().get(1).firstFrame());
        var projectile = sequence.visuals().get(0);
        assertEquals(1125, projectile.start());
        assertEquals(1825, projectile.end());
        assertEquals(1.2, projectile.scale(), .001);
        assertEquals(45, projectile.motion().offsetAt(1225), .001);
    }

    @Test void chargedGhostWavesAdvanceAheadOfTheCasterInTheFollowUp() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "5 `swordman/hardattack.skl` 6 `swordman/hardattackcharge.skl`");
        files.put("passiveobject/passiveobject.lst", "20023 `sword/ghost.obj`");
        files.put("passiveobject/sword/ghost.obj", "[etc motion] `low.ani` `high.ani` `low.ani` `high.ani` `low.ani` `high.ani`");
        files.put("passiveobject/sword/low.ani", animation(100, 100, 100, 100));
        files.put("passiveobject/sword/high.ani", animation(100, 100, 100, 100));
        files.put("character/swordman/hardattack.ani", animation(50, 50, 50, 25, 25, 50, 50, 50, 50, 50));
        files.put("character/swordman/hardattackchargeafter.ani", animation(50, 50, 50, 50, 50, 300));
        var sequence = Act1SwordSupplement.find("skill/swordman/hardattackcharge.skl",
                "[static data] 100 500 750 -1200", files::get,
                Map.of("etc motion:1", "hardattack.ani", "etc motion:17", "hardattackchargeafter.ani"), 1, "");
        assertNotNull(sequence);
        var follow = sequence.poses().get(2);
        var front = sequence.visuals().get(1);
        assertEquals(follow.start(), front.start());
        assertEquals(sequence.visuals().get(0).motion(), front.motion());
        assertNotNull(front.motion());
        assertTrue(front.motion().offsetAt(follow.start() + 200) > follow.motion().offsetAt(follow.start() + 200));
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
