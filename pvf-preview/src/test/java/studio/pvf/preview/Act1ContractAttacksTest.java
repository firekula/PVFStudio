package studio.pvf.preview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Act1ContractAttacksTest {
    private static final String SKILL = "skill/mage/summonauxo.skl";
    private static final String BODY = "monster/monsterflower/animation/attack2.ani";
    private static final String ROOT = "passiveobject/monster/monsterflower/ani/auxoroot.ani";
    private static final String ANIMATION = "[frame max] 1 [frame000] [image] `sprite.img` 0 [delay] 100";

    @Test void auxoRootSpawnsAtTheTargetOnFrameTwoAndOwnsTheDamageBoxes() throws Exception {
        String skill = "[casting time] 500 [static data] 2 0 903 2 800 100 0 0";
        var files = new HashMap<>(Map.of(
                "skill/mageskill.lst", "45 `mage/summonauxo.skl`",
                SKILL, skill,
                "monster/monster.lst", "903 `monsterflower/auxo.mob`",
                "monster/monsterflower/auxo.mob", """
                        [waiting motion] `animation/stay.ani`
                        [attack motion] `animation/attack1.ani` `animation/attack2.ani` [/attack motion]
                        """,
                "monster/monsterflower/animation/stay.ani", ANIMATION,
                BODY, """
                        [frame max] 4
                        [frame000] [delay] 80
                        [frame001] [delay] 80
                        [frame002] [delay] 80
                        [frame003] [delay] 80
                        """,
                "passiveobject/passiveobject.lst", "30351 `monster/monsterflower/auxoroot.obj`",
                "passiveobject/monster/monsterflower/auxoroot.obj", "[basic motion] `ani/auxoroot.ani`",
                ROOT, """
                        [frame max] 3
                        [frame000] [delay] 100 [attack box] -27 -10 -3 67 20 25
                        [frame001] [delay] 100 [attack box] -27 -10 -3 67 20 25
                        [frame002] [delay] 100 [attack box] -27 -10 -3 67 20 25
                        """
        ));
        var actions = Map.of("throw motion 3-1", "prepare.ani", "throw motion 3-2", "release.ani");
        var sequence = Act1SkillSequence.find(SKILL, skill, files::get, actions, 1, "mage-contract:auxo-root", true);
        assertNotNull(sequence);
        var body = layer(sequence, BODY);
        var root = layer(sequence, ROOT);
        assertEquals(1300, body.start());
        assertEquals(1460, root.start());
        assertEquals(1760, root.end());
        assertTrue(body.decorative());
        assertTrue(root.alliedAttack());
        assertTrue(root.x() > body.x());
        assertFalse(root.style().animation().frames().get(1).combat().attack().isEmpty());
        assertTrue(root.style().animation().frames().get(2).combat().attack().isEmpty());
    }

    @Test void luisExpirySelectsOneTargetAndFirstKnockdownSelectsTwo() throws Exception {
        String mob = "monster/soceress/luis.mob";
        String stone = "passiveobject/monster/soceress/stone.ani";
        String impact = "passiveobject/monster/soceress/impact.ani";
        String unsummon = "monster/common/animation/unsummon.ani";
        var files = Map.of(
                mob, "[waiting motion] `stay.ani` [down motion] `down.ani`",
                "monster/soceress/stay.ani", ANIMATION,
                "monster/soceress/down.ani", ANIMATION,
                unsummon, ANIMATION,
                "passiveobject/passiveobject.lst", "30362 `monster/soceress/stone.obj` 30363 `monster/soceress/impact.obj`",
                "passiveobject/monster/soceress/stone.obj", "[basic motion] `stone.ani` [etc motion] `glow.ani`",
                "passiveobject/monster/soceress/impact.obj", "[basic motion] `impact.ani`",
                "passiveobject/monster/soceress/glow.ani", ANIMATION,
                stone, ANIMATION + " [attack box] -43 -20 -35 89 40 77",
                impact, ANIMATION + " [attack box] -100 -30 0 200 60 40"
        );
        var archive = new HashMap<>(files);
        archive.put("passiveobject/common/particle/shootingstarpath.ptl", """
                [x axis] [move type] `[straight line]` [move variable 1] 500 1000
                [z axis] [move type] `[straight line]` [move variable 1] -400 -800
                """);
        var visuals = new ArrayList<Act1SkillSequence.Visual>();
        var expiry = Act1ContractAttacks.append(902, mob, "mage-contract:luis-meteor-expiry", archive::get, 1000,
                100, visuals, new ArrayList<>());
        assertNotNull(expiry);
        assertTrue(expiry.despawned());
        assertEquals(1100, expiry.bodyEnd());
        assertEquals(1, visuals.stream().filter(v -> v.path().equals(stone)).count());
        assertTrue(visuals.stream().anyMatch(v -> v.path().equals(unsummon)));

        visuals.clear();
        var death = Act1ContractAttacks.append(902, mob, "mage-contract:luis-meteor-death", archive::get, 1000,
                100, visuals, new ArrayList<>());
        assertNotNull(death);
        assertTrue(death.despawned());
        var stones = visuals.stream().filter(v -> v.path().equals(stone)).toList();
        var impacts = visuals.stream().filter(v -> v.path().equals(impact)).toList();
        assertEquals(List.of(1463L, 1925L), stones.stream().map(Act1SkillSequence.Visual::end).toList());
        assertEquals(List.of(1463L, 1925L), impacts.stream().map(Act1SkillSequence.Visual::start).toList());
        assertTrue(stones.get(0).motion().velocity() > 0);
        assertTrue(stones.get(1).motion().velocity() < 0);
        assertTrue(impacts.stream().allMatch(Act1SkillSequence.Visual::alliedAttack));
    }

    @Test void hodorTripleThrowLaunchesThreeIndependentStoneClocks() throws Exception {
        String mob = "monster/goblin/goblinhodor.mob";
        String stone = "passiveobject/monster/goblin/animation/throwstone.ani";
        var motion = new StringBuilder("[frame max] 12");
        for (int i = 0; i < 12; i++) motion.append(" [frame").append(String.format("%03d", i)).append("] [delay] 80");
        var files = Map.of(
                mob, "[attack motion] `attack.ani` `throw.ani` `throw.ani` `triplethrow.ani`",
                "monster/goblin/triplethrow.ani", motion.toString(),
                "monster/goblin/particle/throwstone.ptl", """
                        [x axis] [move type] `[straight line]` [move variable 1] 250 250 [life time] 1200
                        """,
                "passiveobject/passiveobject.lst", "30372 `monster/goblin/hodorthrowstone.obj`",
                "passiveobject/monster/goblin/hodorthrowstone.obj", "[basic motion] `animation/throwstone.ani`",
                stone, ANIMATION + " [attack box] -10 -10 -10 20 20 20"
        );
        var visuals = new ArrayList<Act1SkillSequence.Visual>();
        var result = Act1ContractAttacks.append(900, mob, "mage-contract:hodor-triple", files::get, 1000,
                100, visuals, new ArrayList<>());
        assertNotNull(result);
        var stones = visuals.stream().filter(v -> v.path().equals(stone)).toList();
        assertEquals(List.of(1160L, 1480L, 1800L), stones.stream().map(Act1SkillSequence.Visual::start).toList());
        assertTrue(stones.stream().allMatch(Act1SkillSequence.Visual::alliedAttack));
        assertTrue(stones.stream().allMatch(v -> v.x() == 120 && v.motion().height() == 60));
    }

    private static Act1SkillSequence.Visual layer(Act1SkillSequence sequence, String path) {
        return sequence.visuals().stream().filter(visual -> visual.path().equals(path)).findFirst().orElseThrow();
    }
}
