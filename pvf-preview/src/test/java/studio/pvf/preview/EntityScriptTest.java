package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EntityScriptTest {
    @Test void flattenedRecordsKeepEverySpawnIncludingChampionAndOptionalNpcZ() {
        var layout = EntityScript.placements("[NPC] 4 `[left]` 100 200 30 7 `[right]` 300 400 [/NPC] "
                + "[monster] 51 1 9 150 250 20 1 1 `[ratio]` `[champion]` 0 52 1 0 99 88 7 1 1 `[fixed]` `[normal]` [/monster] "
                + "[passive object] 221 175 275 40 222 180 280 45 [/passive object] "
                + "[monster condition] 53 1 0 1 2 3 1 1 `[fixed]` `[normal]` 54 1 0 4 5 6 1 1 `[fixed]` `[normal]` [/monster condition]");
        assertTrue(layout.warnings().isEmpty()); assertEquals(List.of(4,7,51,52,221,222,53,54),layout.spawns().stream().map(EntityScript.Spawn::id).toList());
        assertEquals(0,layout.spawns().get(1).z()); assertEquals(99,layout.spawns().get(3).x()); assertEquals(45,layout.spawns().get(5).z());
        assertTrue(layout.spawns().get(7).conditional());
        var partial = EntityScript.placements("[passive object] 1 2 3 4 99 [/passive object]");
        assertEquals(1,partial.spawns().size()); assertEquals(1,partial.warnings().size());
    }
    @Test void flattenedSpecialContainersSkipPayloadWithoutInventingSpawns() {
        var layout = EntityScript.placements("[special passive object] 221 100 200 0 2 `[monster]` 51 18 3 10 -1 `[item]` 0 2 -1 -1 -1 1032 300 200 0 0 [/special passive object]");
        assertTrue(layout.warnings().isEmpty()); assertEquals(List.of(221,1032),layout.spawns().stream().map(EntityScript.Spawn::id).toList());
    }
    @Test void hellpartyPayloadAndOmittedTextZDoNotBecomeMapEntities() {
        var flat = EntityScript.placements("[special passive object] 30528 352 270 0 1 `[hellparty]` 125 25 1 126 25 1 `[/hellparty]` 1032 300 200 0 0 [/special passive object]");
        assertTrue(flat.warnings().isEmpty()); assertEquals(List.of(30528,1032),flat.spawns().stream().map(EntityScript.Spawn::id).toList());
        var text = EntityScript.placements("[special passive object]\n221 100 200\n0\n[/special passive object]");
        assertTrue(text.warnings().isEmpty()); assertEquals(0,text.spawns().get(0).z());
    }
    @Test void actionReferencesStayTypeSpecificAndDoNotReplaceAnExplicitIdleMotion() throws Exception {
        var monster = EntityScript.definition("[waiting motion] `body.ani` [waiting action] `stay.act` [basic action] `wrong.act`",EntityScript.Kind.MONSTER);
        assertEquals("body.ani",monster.idle()); assertEquals("stay.act",monster.action());
        assertEquals("pipe.act",EntityScript.definition("[basic motion] `` [basic action] `pipe.act`",EntityScript.Kind.PASSIVE).action());
    }
    @Test void mapRowsUseCorrectNpcMonsterAndPassiveCoordinates() {
        var layout = EntityScript.placements("""
                [NPC]
                4 `[left]` 100 200 30
                7 `[right]` 300 400
                [/NPC]
                [monster]
                51 1 9 150 250 20 1 1 `[ratio]` `[champion]` 0
                [/monster]
                [passive object]
                221 175 275 40
                [/passive object]
                """);
        assertTrue(layout.warnings().isEmpty()); assertEquals(4, layout.spawns().size());
        var npc = layout.spawns().get(0); assertTrue(npc.mirrored()); assertEquals(100, npc.x()); assertEquals(30, npc.z());
        assertFalse(layout.spawns().get(1).mirrored()); assertEquals(0, layout.spawns().get(1).z());
        var monster = layout.spawns().get(2); assertEquals(150, monster.x()); assertEquals(250, monster.y()); assertEquals(20, monster.z());
        assertEquals(EntityScript.Kind.PASSIVE, layout.spawns().get(3).kind());
    }
    @Test void specialContainersSkipContentsAndAcceptInlineZeroAndQuestPayload() {
        var layout = EntityScript.placements("""
                [special passive object]
                221 100 200 0
                2
                `[monster]` 51 18 3 10 -1
                `[item]` 0 2 -1 -1 -1
                1032 300 200 0 0
                829 400 200 0
                1
                `[quest]` 1 -1 -1 -1 -1
                [/special passive object]
                """);
        assertTrue(layout.warnings().isEmpty()); assertEquals(List.of(221, 1032, 829), layout.spawns().stream().map(EntityScript.Spawn::id).toList());
        assertTrue(layout.spawns().stream().allMatch(EntityScript.Spawn::special));
    }
    @Test void conditionalPlacementsAreMarkedAndBadRowsDoNotEraseGoodOnes() {
        var layout = EntityScript.placements("""
                [monster condition]
                3 1 0 5 6 7 1 1 `[fixed]` `[normal]`
                [/monster condition]
                [NPC]
                4 `[invalid]` 1 2 3
                7 `[right]` 10 20 0
                [/NPC]
                """);
        assertEquals(2, layout.spawns().size()); assertTrue(layout.spawns().get(0).conditional()); assertEquals(1, layout.warnings().size());
    }
    @Test void catalogIgnoresCommentsKeepsSparseIdsAndRejectsAmbiguity() throws Exception {
        var index = EntityScript.catalog("// sample\n1000 `Delilah.npc`\n//4 `wrong.npc`\n4 `Sinda.npc` /* ignored */");
        assertEquals("Sinda.npc", index.get(4)); assertEquals(2, index.size());
        assertThrows(IOException.class, () -> EntityScript.catalog("4 `one.npc` 4 `two.npc`"));
        assertThrows(IOException.class, () -> EntityScript.catalog("4"));
    }
    @Test void idleTagsAreTypeSpecificAndEmptyBasicMotionIsNotAnEtcMotion() throws Exception {
        assertEquals("field.ani", EntityScript.definition("[field animation] `field.ani`", EntityScript.Kind.NPC).idle());
        assertEquals("wait.ani", EntityScript.definition("[waiting motion] `wait.ani`", EntityScript.Kind.MONSTER).idle());
        var object = EntityScript.definition("[layer] `[bottom]` [basic motion] `` [etc motion] `destroy.ani` [/etc motion]", EntityScript.Kind.PASSIVE);
        assertEquals("", object.idle()); assertEquals("bottom", object.layer());
    }
    @Test void equipmentAndAttachedEffectsKeepTheirOrderingMetadata() throws Exception {
        var monster = EntityScript.definition("[waiting motion] `body.ani` [equipment] 2 `Monster/a.equ` `Monster/b.equ` [/equipment]", EntityScript.Kind.MONSTER);
        assertEquals(2, monster.equipment().size());
        var gear = EntityScript.equipment("[LAYER] -100 [waiting motion] `gear.ani`");
        assertEquals(-100, gear.layer()); assertEquals("gear.ani", gear.idle());
        var object = EntityScript.definition("[basic motion] `body.ani` [add object effect] 1 `light.ani` 100 [/add object effect]", EntityScript.Kind.PASSIVE);
        assertEquals(List.of("light.ani", "100"), object.effects());
        assertThrows(IOException.class, () -> EntityScript.definition("[equipment] 2 `a.equ` [/equipment]", EntityScript.Kind.MONSTER));
    }
}
