package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LegacyScriptPreviewTest {
    @Test void koreanMapAndTileKeywordsResolveWithoutChangingQuotedPaths() throws Exception {
        String text = "[타입] `일반` [타일] `Tile/bar00.til` [/타일] [원경 스크롤] 70 "
                + "[중경 스크롤] 90 [접경 스크롤] 110 [배경 애니메이션] `Animation/far.ani` `원경` `아래` [/배경 애니메이션]";
        var map = SceneScript.map(text);
        assertEquals(List.of("Tile/bar00.til"), map.tiles()); assertEquals(.7, map.farScroll());
        assertEquals(.9, map.middleScroll()); assertEquals(1.1, map.nearScroll());
        assertEquals(new SceneScript.Placement("Animation/far.ani", "distantback", true, false, 0, 0, 0), map.animations().get(0));
        var tile = SceneScript.tile("[IMAGE] `Map/타일.img` 0 [통과 속성] " + "2 ".repeat(420));
        assertEquals(30, tile.rows()); assertEquals(2, tile.pass(0, 0)); assertEquals("Map/타일.img", tile.images().get(0).image());
        assertEquals(List.of("[타일].til"), SceneScript.map("[tile] `[타일].til`").tiles());
    }
    @Test void nativeLegacyGlyphTableMatchesAllSixteenEnglishShapeMasks() throws Exception {
        String glyphs = "┼┾╀╄┽┿╃╇╁╆╂╊╅╈╉╋";
        for (int mask = 0; mask < 16; mask++)
            assertEquals(List.of(mask), DungeonScript.codes(List.of(glyphs.substring(mask, mask + 1))));
        assertEquals(List.of(1, 15, 0), DungeonScript.codes(List.of("BB╋AA")));
        assertThrows(java.io.IOException.class, () -> DungeonScript.codes(List.of("╬")));
    }
    @Test void unboundKoreanDungeonShowsItsLayoutWithoutInventingMapAssociation() throws Exception {
        String text = "[통로 오브젝트] -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 [미로 정보] [크기] 1 1 [그리드] `╋`";
        var dungeon = DungeonPreview.load("dungeon/temp.dgn", text, Map.of("dungeon/dungeon.lst", "1 `Temp.dgn`",
                "map/map.lst", "1 `room.map`", "map/room.map", "[타입] `일반`")::get);
        var layout = dungeon.arrange(0, null, null, 0);
        assertEquals(1, layout.rooms().size()); assertEquals(15, layout.rooms().get(0).mask());
        assertTrue(layout.rooms().get(0).choices().isEmpty());
        assertTrue(layout.warnings().stream().anyMatch(w -> w.contains("仅显示副本布局")));
    }
    @Test void koreanEntityMotionsFacingLayersAndLegacyCatalogCommentsAreRecognized() throws Exception {
        var layout = EntityScript.placements("[NPC] 5 `오른쪽` 595 174 0 [/NPC] [몬스터] 11 0 0 1162 268 0 1 1 `고정` `일반` [/몬스터]");
        assertEquals(2, layout.spawns().size()); assertTrue(layout.warnings().isEmpty());
        assertFalse(layout.spawns().get(0).mirrored());
        var npc = EntityScript.definition("[필드 애니메이션] `Animation/Npc_su.ani`", EntityScript.Kind.NPC);
        assertEquals("Animation/Npc_su.ani", npc.idle());
        var object = EntityScript.definition("[레이어] `바닥` [기본 모션] `Idle.ani` [폭] 10 20 [통과 속성] `부유형만`", EntityScript.Kind.PASSIVE);
        assertEquals("bottom", object.layer()); assertEquals(1, object.pass());
        assertEquals(Map.of(1, "/test.mob", 2, "next.mob"), EntityScript.catalog("1 `/test.mob`\r\n/ legacy comment\r\n2 `next.mob`"));
    }
}
