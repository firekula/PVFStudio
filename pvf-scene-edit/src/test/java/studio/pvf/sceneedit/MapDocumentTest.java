package studio.pvf.sceneedit;

import org.junit.jupiter.api.Test;
import studio.pvf.preview.EntityScript;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MapDocumentTest {
    @Test void malformedPlacementSectionBlocksStructuralEditsButKeepsKnownCoordinatesEditable() throws Exception {
        String text="[tile] `a.til` [/tile]\n[passive object] 1 100 120 0 2 20 [/passive object]\n[unknown] @9(7)";
        var model=MapDocument.parse("map/a.map",text);var id=model.entries().get(0).id();
        assertFalse(model.diagnostics().isEmpty());
        assertThrows(java.io.IOException.class,() -> model.addEntity(studio.pvf.preview.EntityScript.Kind.PASSIVE,3,200,120,0));
        assertThrows(java.io.IOException.class,() -> model.copy(id));assertThrows(java.io.IOException.class,() -> model.remove(id));
        String moved=model.move(id,200,120,0).applyTo(text);assertTrue(moved.contains("200 120 0 2 20"));assertTrue(moved.contains("[unknown] @9(7)"));
    }
    private static MapDocument parse(String text) throws IOException { return MapDocument.parse("map/test/a.map", text); }

    @Test void movesOnlyTheSecondIdenticalFlatEntityAndPreservesUnknownSource() throws Exception {
        String text = "// header\r\n[passive object] 100 1 2 3 100 4 5 6 [/passive object]\r\n[unknown] @9(6) @10(123)\r\n";
        var map = parse(text); assertEquals(2, map.entries().size());
        assertNotEquals(map.entries().get(0).id(), map.entries().get(1).id());
        String moved = map.move(map.entries().get(1).id(), 700, 800, 9).applyTo(text);
        assertEquals(text.replace("100 4 5 6", "100 700 800 9"), moved);
    }

    @Test void npcOptionalZAndChampionRecordsHaveIndependentBoundaries() throws Exception {
        String text = "[NPC] 4 `[left]` 100 200 7 `[right]` 300 400 30 [/NPC]\n"
                + "[monster] 51 1 0 150 250 20 1 1 `[ratio]` `[champion]` 0 52 1 0 99 88 7 1 1 `[fixed]` `[boss]` [/monster]";
        var map = parse(text); assertEquals(4, map.entries().size());
        assertEquals(0, map.entries().get(0).z()); assertEquals(30, map.entries().get(1).z());
        assertEquals("boss", map.entries().get(3).properties().get("monsterType"));
        String moved = map.move(map.entries().get(0).id(), 100, 200, 15).applyTo(text);
        assertTrue(moved.contains("4 `[left]` 100 200 15 7 `[right]` 300 400 30"));
        String boss = map.setField(map.entries().get(2).id(), "monsterType", "boss").applyTo(text);
        assertTrue(boss.contains("`[ratio]` `[boss]`  52"));
        assertEquals(4, parse(boss).entries().size());
    }

    @Test void specialPayloadDoesNotBecomeExtraMapSpawnsAndCopiesAsAWhole() throws Exception {
        String text = "[special passive object] 30528 352 270 0 1 `[hellparty]` 125 25 1 126 25 1 `[/hellparty]` 1032 300 200 0 0 [/special passive object]\n[unknown] @10(73)";
        var map = parse(text); assertEquals(2, map.entries().size());
        String copied = map.copy(map.entries().get(0).id()).applyTo(text);
        assertEquals(3, parse(copied).entries().size());
        assertEquals(2, copied.split("125 25 1", -1).length - 1);
        assertTrue(copied.endsWith("[unknown] @10(73)"));
        String payload = map.setSpecialContents(map.entries().get(0).id(), List.of(
                new MapDocument.SpecialContent("hellparty", List.of("155", "40", "2")))).applyTo(text);
        assertTrue(payload.contains("155 40 2")); assertFalse(payload.contains("126 25 1"));
        assertTrue(payload.contains("1032 300 200 0 0"));
    }

    @Test void oldDirectMonsterSpecialPayloadAndOmittedZAreRetained() throws Exception {
        String text = "[special passive object]\n30507 619 296\n1\n`[monster]` 1050 16 1 1 -1\n[/special passive object]";
        var map = parse(text); assertEquals(1, map.entries().size());
        String moved = map.move(map.entries().get(0).id(), 620, 297, 0).applyTo(text);
        assertEquals(text.replace("619 296", "620 297"), moved);
        assertFalse(moved.contains("hellparty"));
    }

    @Test void monsterAiCompanionsFollowCopyDeleteAndAdd() throws Exception {
        String text = "[monster] 51 1 0 1 2 3 1 1 `[fixed]` `[normal]` 52 1 0 4 5 6 1 1 `[fixed]` `[normal]` [/monster]\n"
                + "[monster specific AI] `[normal]` `[aggressive]` [/monster specific AI]";
        var map = parse(text);
        String removed = map.remove(map.entries().get(0).id()).applyTo(text);
        assertEquals(1, parse(removed).entries().size());
        assertEquals(List.of("[aggressive]"), SourceDocument.parse(removed).sections("monster specific ai").get(0).values().stream().map(SourceDocument.Token::value).toList());
        String copied = map.copy(map.entries().get(1).id()).applyTo(text);
        assertEquals(List.of("[normal]", "[aggressive]", "[aggressive]"), SourceDocument.parse(copied).sections("monster specific ai").get(0).values().stream().map(SourceDocument.Token::value).toList());
        String added = map.addEntity(EntityScript.Kind.MONSTER, 53, 10, 20, 0).applyTo(text);
        assertEquals(3, parse(added).entries().size());
        assertEquals(3, SourceDocument.parse(added).sections("monster specific ai").get(0).values().size());
    }

    @Test void unsafeAuxiliaryAiDisablesStructuralMonsterEditsButAllowsKnownCoordinates() throws Exception {
        String text = "[monster] 51 1 0 1 2 3 1 1 `[fixed]` `[normal]` [/monster]\n[monster specific AI] `[normal]` @9(6) [/monster specific AI]";
        var map = parse(text); var id = map.entries().get(0).id();
        assertThrows(IOException.class, () -> map.remove(id));
        assertThrows(IOException.class, () -> map.copy(id));
        assertThrows(IOException.class, () -> map.addEntity(EntityScript.Kind.MONSTER, 53, 0, 0, 0));
        assertEquals(text.replace("0 1 2 3", "0 10 20 30"), map.move(id, 10, 20, 30).applyTo(text));
    }

    @Test void nestedBackgroundLayerEditPreservesUnknownFieldsAndStructure() throws Exception {
        String text = "[background animation]\n[ani info]\n[filename] `Animation/far.ani`\n[layer] `[distantback]`\n[custom] @10(777)\n[order] `[below]`\n[/ani info]\n[/background animation]";
        var map = parse(text); assertEquals(1, map.entries().size());
        assertEquals(text.replace("[distantback]", "[middleback]"), map.setField(map.entries().get(0).id(), "layer", "middleback").applyTo(text));
        assertThrows(IOException.class, () -> map.move(map.entries().get(0).id(), 1, 2, 3));
    }

    @Test void movingBaseColumnCarriesExtendedCellsAndRejectsIncompleteNewRows() throws Exception {
        String text = "[tile] `Tile/a.til` `Tile/b.til` [/tile]\n[extended tile] `Tile/e1.til` `Tile/e2.til` `Tile/e3.til` `Tile/e4.til` [/extended tile]\n[unknown] 33";
        var map = parse(text);
        var moved = parse(map.moveTileColumn(0, 1).applyTo(text));
        assertEquals(List.of("Tile/b.til", "Tile/a.til"), moved.tiles(false).stream().map(MapDocument.TileEntry::reference).toList());
        assertEquals(List.of("Tile/e2.til", "Tile/e1.til", "Tile/e4.til", "Tile/e3.til"), moved.tiles(true).stream().map(MapDocument.TileEntry::reference).toList());
        assertThrows(IOException.class, () -> map.replaceTiles(false, List.of("Tile/a.til", "Tile/b.til", "Tile/c.til")));
        assertThrows(IOException.class, () -> map.replaceTiles(true, List.of("Tile/e1.til")));
        assertThrows(IOException.class, () -> map.replaceTiles(false, List.of()));
    }

    @Test void koreanTagsAndConditionalEntitiesAreEditableWithoutRenamingTags() throws Exception {
        String text = "[몬스터 조건] 51 1 0 100 200 0 1 1 `[fixed]` `[normal]` [/몬스터 조건]";
        var map = parse(text); assertTrue(map.entries().get(0).conditional());
        assertEquals(text.replace("100 200", "101 201"), map.move(map.entries().get(0).id(), 101, 201, 0).applyTo(text));
        assertThrows(IOException.class, () -> map.setField(map.entries().get(0).id(), "layer", "close"));
    }

    @Test void addingFactoryObjectDoesNotInventPtlTagAndAddsNativeAnimation() throws Exception {
        String text = "#PVF_File\n[tile] `Tile/a.til` [/tile]";
        String object = parse(text).addEntity(EntityScript.Kind.PASSIVE, 333, 10, 20, 0).applyTo(text);
        assertTrue(object.contains("[passive object]")); assertTrue(object.contains("333 10 20 0")); assertFalse(object.contains("[particle]"));
        String ani = parse(object).addAnimation("Animation/far.ani", "distantback", true, false, 0, 0, 0).applyTo(object);
        assertTrue(ani.contains("`Animation/far.ani` `[distantback]` `[below]`"));
        assertThrows(IOException.class, () -> parse(text).addAnimation("fake.ptl", "normal", false, false, 0, 0, 0));
    }

    @Test void newEntityCoordinatesMustCompileAsNativeIntegersWhileExistingFloatTokensKeepTheirType() throws Exception {
        String text = "[animation] `a.ani` `[normal]` 1.0f 2.0f 0.0f [/animation]";
        var map = parse(text);
        assertEquals(text.replace("1.0f 2.0f", "3.5f 4.25f"), map.move(map.entries().get(0).id(), 3.5, 4.25, 0).applyTo(text));
        assertThrows(IOException.class, () -> map.addEntity(EntityScript.Kind.MONSTER, 51, 12.5, 0, 0));
        assertThrows(IOException.class, () -> map.move(map.entries().get(0).id(), Double.NaN, 0, 0));
    }
}
