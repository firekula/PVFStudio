package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1SwordBuffSupplementTest {
    private static final String COMMON = "common/commoneffect/animation/";
    private static final String EFFECT = "character/swordman/effect/animation/";
    private static final String ANI = "[FRAME MAX] 2 [FRAME000] [DELAY] 100 [FRAME001] [DELAY] 200";
    private static final Map<String, String> ACTIONS = Map.of("throw motion 1-1", "Animation/Throw1.ani",
            "throw motion 1-2", "Animation/Throw2.ani", "throw motion 2-1", "Animation/Summon1.ani",
            "throw motion 2-2", "Animation/Summon2.ani", "buff motion", "Animation/Summon2.ani",
            "waiting motion", "Animation/Stay.ani");

    @Test void autoGuardUsesNativeBuffActivationIconAndMeasuredHeight() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "3 `Swordman/AutoGuard.skl`");
        files.put(COMMON + "buffstarticon.ani", ANI);
        files.put(COMMON + "buffchargeglow.ani", ANI);
        var sequence = Act1SwordBuffSupplement.find("skill/swordman/autoguard.skl", "[casting time] 700", files::get,
                ACTIONS, 1, job -> 91);
        assertNotNull(sequence);
        assertEquals(List.of("buff motion", "waiting motion"), sequence.poses().stream().map(Act1SkillSequence.Pose::action).toList());
        var icon = sequence.visuals().get(1);
        assertEquals(700, icon.start());
        assertEquals(-121, icon.y());
        assertEquals(48, icon.style().spriteOverride().index());
    }

    @Test void sacrificeSwitchesBothPreparationAttachmentsAtRelease() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "23 `Swordman/Sacrifice.skl`");
        for (String name : List.of("givebloodcharge1", "givebloodcharge2", "givebloodshoot1", "givebloodshoot2")) {
            files.put(EFFECT + name + ".ani", ANI);
        }
        var sequence = Act1SwordBuffSupplement.find("skill/swordman/sacrifice.skl",
                "[casting time] 250 [static data] 5 700 500", files::get, ACTIONS, 1, job -> 100);
        assertNotNull(sequence);
        assertEquals(List.of(0L, 0L, 250L, 250L), sequence.visuals().stream().map(Act1SkillSequence.Visual::start).toList());
        assertEquals(List.of(250L, 250L, 550L, 550L), sequence.visuals().stream().map(Act1SkillSequence.Visual::end).toList());
        assertEquals("throw motion 1-1", sequence.poses().get(0).action());
        assertEquals("throw motion 1-2", sequence.poses().get(1).action());
        assertEquals(750, sequence.poses().get(1).end());
    }

    @Test void frenzyFitsThrowOneAndShoulderAttachmentToTheSameChargeClock() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/swordmanskill.lst", "76 `Swordman/Frenzy.skl`");
        files.put(EFFECT + "frenzy/cast.ani", ANI);
        files.put(EFFECT + "frenzy/blast.ani", ANI);
        files.put(EFFECT + "frenzy/buff.ani", ANI);
        var sequence = Act1SwordBuffSupplement.find("skill/swordman/frenzy.skl", "[casting time] 500 [static data] 350",
                files::get, ACTIONS, 1, job -> 100);
        assertNotNull(sequence);
        assertEquals(List.of("throw motion 1-1", "throw motion 1-2", "waiting motion"),
                sequence.poses().stream().map(Act1SkillSequence.Pose::action).toList());
        assertTrue(sequence.poses().get(0).fitToStage());
        assertEquals(500, sequence.visuals().get(0).style().animationDuration());
        assertEquals(500, sequence.visuals().get(0).end());
        assertEquals(500, sequence.visuals().get(1).start());
        assertEquals(750, sequence.visuals().get(2).start());
    }

    @Test void dieHardRetainsTheSummonPairFromNativeModeOne() throws Exception {
        var files = Map.of("skill/swordmanskill.lst", "34 `Swordman/DieHard.skl`",
                EFFECT + "pinchhpregen1.ani", ANI, EFFECT + "pinchhpregen2.ani", ANI);
        var sequence = Act1SwordBuffSupplement.find("skill/swordman/diehard.skl", "[casting time] 300 [static data] 15 300",
                files::get, ACTIONS, 1, job -> 100);
        assertNotNull(sequence);
        assertEquals(List.of("throw motion 2-1", "throw motion 2-2"),
                sequence.poses().stream().map(Act1SkillSequence.Pose::action).toList());
    }
}
