package studio.pvf.sceneedit;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SceneCatalogTest {
    @Test void descendantTilesResolveRelativePathsAndExcludeSiblingDirectories() throws Exception {
        var paths = Set.of("map/foo/a.map", "map/foo/tile/a.til", "map/foo/tile/deep/b.til", "map/foo2/wrong.til", "map/other/wrong.til");
        var catalog = SceneCatalog.build(paths, path -> null);
        assertEquals(2, catalog.filesUnder("map/foo/a.map", ".til").size());
        assertEquals("map/foo/tile/a.til", catalog.resolve("map/foo/a.map", "Tile\\a.til"));
        assertEquals("map/foo/tile/deep/b.til", catalog.resolve("map/foo/a.map", "Tile/deep/b.til"));
        assertEquals("tile/a.til", catalog.relative("map/foo/a.map", "map/foo/tile/a.til"));
    }

    @Test void sparseGlobalCatalogIgnoresCommentedIdsAndDoesNotReadDefinitionsEagerly() throws Exception {
        var files = Map.of("monster/monster.lst", "#PVF_File\n// 1 `wrong.mob`\n1000 `Boss.mob`\n4 `Small.mob`",
                "map/map.lst", "3 `foo/a.map`");
        var catalog = SceneCatalog.build(Set.of("monster/monster.lst", "monster/boss.mob", "monster/small.mob", "map/map.lst", "map/foo/a.map"), path -> {
            assertTrue(path.endsWith(".lst")); return files.get(path);
        });
        assertEquals(Set.of(4, 1000), catalog.entities(studio.pvf.preview.EntityScript.Kind.MONSTER).keySet());
        assertEquals("monster/boss.mob", catalog.entities(studio.pvf.preview.EntityScript.Kind.MONSTER).get(1000));
        assertEquals("map/foo/a.map", catalog.maps().get(3));
    }

    @Test void duplicateIndexIsReportedWithoutLosingLocalTileBrowsing() throws Exception {
        var catalog = SceneCatalog.build(Set.of("monster/monster.lst", "map/foo/tile/a.til"), path -> path.equals("monster/monster.lst") ? "4 `a.mob` 4 `b.mob`" : null);
        assertTrue(catalog.entities(studio.pvf.preview.EntityScript.Kind.MONSTER).isEmpty());
        assertFalse(catalog.diagnostics().isEmpty());
        assertEquals(1, catalog.filesUnder("map/foo/a.map", ".til").size());
    }
}
