package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class SceneScriptTest {
    @Test void passGridKeepsTypesAndBasicExtendedGeometry() throws Exception {
        var tile = SceneScript.tile("[IMAGE] `tile.img` 2 [IMAGE] `deco.img` 3 [tile type] `[extended]` "
                + "[pass type] 0 1 2 0 0 0 0 0 0 0 0 0 0 0 " + "0 ".repeat(126));
        assertTrue(tile.extended()); assertEquals(120, tile.height()); assertEquals(10, tile.rows());
        assertEquals(1, tile.pass(1, 0)); assertEquals(2, tile.images().size()); assertTrue(tile.warnings().isEmpty());
        assertEquals(480, SceneScript.tile("[IMAGE] `` 0 [pass type] " + "2 ".repeat(420)).height());
    }
    @Test void partialGridIsReportedAndUnknownPassTypesAreRejected() throws Exception {
        var tile = SceneScript.tile("[pass type] " + "0 ".repeat(280));
        assertEquals(20, tile.rows()); assertTrue(tile.warnings().isEmpty()); assertEquals(360, tile.height());
        assertThrows(IOException.class, () -> SceneScript.tile("[pass type] 0 1 2"));
        assertThrows(IOException.class, () -> SceneScript.tile("[pass type] 3 " + "0 ".repeat(13)));
        var copiedTemplate = SceneScript.tile("[tile type] `[extended]` [pass type] " + "2 ".repeat(420));
        assertEquals(10, copiedTemplate.rows()); assertFalse(copiedTemplate.warnings().isEmpty());
    }
    @Test void mapSeparatesRepeatingLayersAndPlacedAnimationXyz() throws Exception {
        var map = SceneScript.map("""
                [tile] `Tile/a.til` `Tile/b.til` [/tile]
                [extended tile] `Tile/x.til` `Tile/y.til` `Tile/z.til` [/extended tile]
                [far sight scroll] 56 [middle sight scroll] 90 [near sight scroll] 110
                [background animation] `far.ani` `[distantback]` `[below]` `fog.ani` `[close]` `upper` [/background animation]
                [animation] `tree.ani` `[normal]` 30 100 25 [/animation]
                [virtual movable area] 10 20 30 40 [/virtual movable area]
                """);
        assertEquals(2, map.tiles().size()); assertEquals(3, map.extended().size()); assertEquals(3, map.animations().size());
        assertTrue(map.animations().get(0).repeat()); assertTrue(map.animations().get(1).upper());
        assertEquals(25, map.animations().get(2).z()); assertEquals(.56, map.farScroll()); assertEquals(1.1, map.nearScroll());
        assertEquals(40, map.movable().get(0).height);
        assertTrue(SceneScript.stage(map.animations().get(0)) < 10);
        assertTrue(SceneScript.stage(map.animations().get(1)) > SceneScript.stage(map.animations().get(2)));
    }
    @Test void closeBelowBackgroundIsBeforeTileAndIncompleteMapEditsFailClearly() throws Exception {
        var placement = new SceneScript.Placement("fog.ani", "close", true, false, 0, 0, 0);
        assertEquals(9, SceneScript.stage(placement));
        assertThrows(IOException.class, () -> SceneScript.map("[tile] `a.til` [/tile] [animation] `a.ani` `[normal]` 2 3 [/animation]"));
        assertThrows(IOException.class, () -> SceneScript.map("[tile] `a.til` [/tile] [animation] `a.ani` `[bogus]` 1 2 3 [/animation]"));
    }
}
