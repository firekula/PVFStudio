package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1BloodBlastCombatTest {
    @Test void invisibleOuterAttackStartsAtTheCasterWhileTheCoreUsesItsGroundOffset() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "24 `swordman/bloodblast.skl`");
        files.put("passiveobject/passiveobject.lst",
                "20009 `sword/core.obj` 20010 `sword/outer.obj` 20031 `sword/cast.obj`");
        files.put("passiveobject/sword/core.obj", """
                [basic motion] `core.ani`
                [etc motion] `back.ani` `front.ani` `floor1.ani` `floor2.ani` `floor3.ani`
                [/etc motion]
                """);
        files.put("passiveobject/sword/cast.obj",
                "[basic motion] `castback.ani` [etc motion] `castfront.ani` [/etc motion]");
        files.put("passiveobject/sword/outer.obj", "[basic motion] `outer.ani`");
        files.put("passiveobject/sword/core.ani", "[FRAME000] [DELAY] 2240");
        for (String name : new String[]{"back", "front", "floor1", "floor2", "floor3", "castback", "castfront"}) {
            files.put("passiveobject/sword/" + name + ".ani", "[FRAME000] [DELAY] 2240");
        }
        files.put("passiveobject/sword/outer.ani", """
                [FRAME000] [DELAY] 1120
                [FRAME001] [ATTACK BOX] -73 -50 0 146 100 330 [DELAY] 240
                [FRAME002] [DELAY] 800
                """);
        var actions = Map.of("throw motion 2-1", "prepare.ani", "throw motion 2-2", "release.ani");
        var sequence = Act1SkillSequence.find("skill/swordman/bloodblast.skl", "",
                files::get, actions, 1, "", true);
        assertNotNull(sequence);
        var outer = sequence.visuals().stream().filter(v -> v.path().endsWith("/outer.ani"))
                .findFirst().orElseThrow();
        assertEquals(0, outer.start());
        assertEquals(0, outer.x());
        assertEquals(0, outer.y());
        assertEquals(-1, outer.end());
        assertEquals(50, sequence.visuals().stream().filter(v -> v.path().endsWith("/core.ani"))
                .findFirst().orElseThrow().y());
        var clock = new AnimationTimeline(ResourceScript.animation(files.get(outer.path())));
        assertTrue(clock.sample(1119, false).combat().attack().isEmpty());
        assertEquals(1, clock.sample(1120, false).combat().attack().size());
        assertTrue(clock.sample(1360, false).combat().attack().isEmpty());
    }
}
