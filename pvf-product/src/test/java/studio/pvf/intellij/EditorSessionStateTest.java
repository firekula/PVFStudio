package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorSessionStateTest {
    @TempDir Path directory;

    @Test void roundTripPreservesOrderUnicodeEncodingActiveTabAndIndependentPositions() throws Exception {
        var original = new EditorSessionState(List.of(
                new EditorSessionState.Tab("map/森林/104.map", "GBK", 400, 80, 1200),
                new EditorSessionState.Tab("monster/monster.lst", "x-windows-949", 12, 5, 200)), "map/森林/104.map");
        original.write(directory);
        assertEquals(original, EditorSessionState.read(directory).state());
        assertTrue(EditorSessionState.read(directory).warnings().isEmpty());
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count(), "Atomic saves must not leave temporary files");
        }
    }

    @Test void explicitEmptySessionReplacesPreviousTabs() throws Exception {
        new EditorSessionState(List.of(new EditorSessionState.Tab("a.map", "UTF-8", 1, 0, 0)), "a.map").write(directory);
        EditorSessionState.EMPTY.write(directory);
        assertEquals(EditorSessionState.EMPTY, EditorSessionState.read(directory).state());
    }

    @Test void badRecordsDoNotPreventOtherTabsFromRestoring() throws Exception {
        Files.writeString(directory.resolve(EditorSessionState.FILE_NAME), """
                format=1
                tabs.count=5
                selected=missing.map
                tab.0.path=a.map
                tab.0.charset=GBK
                tab.0.caret=-1
                tab.0.scrollY=not-an-offset
                tab.1.path=a.map
                tab.1.charset=GBK
                tab.2.path=invalid.map
                tab.2.charset=unknown-charset
                tab.3.path=b.map
                tab.3.charset=UTF-8
                tab.3.caret=2147483648
                tab.3.scrollX=36
                tab.4.path=
                """, StandardCharsets.UTF_8);
        var result = EditorSessionState.read(directory);
        assertEquals(List.of(new EditorSessionState.Tab("a.map", "GBK", 0, 0, 0),
                new EditorSessionState.Tab("b.map", "UTF-8", 0, 36, 0)), result.state().tabs());
        assertEquals("a.map", result.state().selectedPath());
        assertEquals(3, result.warnings().size());
    }

    @Test void missingCorruptOrFutureFilesFailWithoutBlockingArchiveOpen() throws Exception {
        assertEquals(EditorSessionState.EMPTY, EditorSessionState.read(directory).state());
        for (String invalid : List.of("format=2\ntabs.count=1", "format=1\ntabs.count=999999999", "format=1\ntabs.count=-1", "format=1\nbad=\\uXYZA")) {
            Files.writeString(directory.resolve(EditorSessionState.FILE_NAME), invalid);
            var result = EditorSessionState.read(directory);
            assertEquals(EditorSessionState.EMPTY, result.state()); assertFalse(result.warnings().isEmpty());
        }
    }
}
