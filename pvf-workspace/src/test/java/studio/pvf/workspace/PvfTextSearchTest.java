package studio.pvf.workspace;

import org.junit.jupiter.api.Test;
import java.nio.charset.CharacterCodingException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class PvfTextSearchTest {
    @Test void literalMatchesHaveNormalizedEditorOffsetsAndUnicodeCaseWithoutLosingColumns() throws Exception {
        String raw = "first\r\n中文 [Tile]\r[Tile] last";
        var result = PvfTextSearch.scan(List.of("map/a.map"), path -> raw, "[tile]", "MAP/", false, 20, () -> false, n -> {});
        assertEquals(2, result.hits().size());
        var hit = result.hits().get(0); assertEquals(2, hit.line()); assertEquals(4, hit.column());
        assertEquals("[Tile]", raw.replace("\r\n", "\n").replace('\r', '\n').substring(hit.start(), hit.end()));
        assertTrue(PvfTextSearch.scan(List.of("map/a.map"), p -> raw, "[tile]", "", true, 20, () -> false, n -> {}).hits().isEmpty());
    }
    @Test void filtersLimitsSkippedFilesAndCooperativeCancellationAreExplicit() throws Exception {
        var paths = List.of("a.map", "b.map", "c.map", "d.map");
        var result = PvfTextSearch.scan(paths, p -> {
            if (p.equals("a.map")) throw new CharacterCodingException();
            return p.equals("b.map") ? "binary\0" : "needle needle";
        }, "needle", "", true, 3, () -> false, n -> {});
        assertEquals(3, result.hits().size()); assertTrue(result.limited()); assertEquals(2, result.skipped());
        AtomicBoolean stop = new AtomicBoolean();
        var cancelled = PvfTextSearch.scan(paths, p -> { stop.set(true); return "needle"; }, "needle", "", true, 10, stop::get, n -> {});
        assertTrue(cancelled.cancelled()); assertTrue(cancelled.hits().isEmpty());
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
