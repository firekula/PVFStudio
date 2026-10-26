package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import studio.pvf.sceneedit.SourceDocument;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SceneDocumentBridgeTest {
    static final class Port implements SceneDocumentBridge.Port {
        String text = "1"; long version; int commands;
        public String text() { return text; }
        public long version() { return version; }
        public boolean writable() { return true; }
        public void write(String label, String value) { text = value; version++; commands++; }
    }
    @Test void rejectsOutOfOrderResultsAndDragFromAnEarlierSnapshot() throws Exception {
        Port port = new Port(); List<Runnable> work = new ArrayList<>();
        var bridge = new SceneDocumentBridge<>(port, Integer::parseInt, work::add, Runnable::run, s -> {});
        bridge.refresh(); port.text = "2"; port.version++; bridge.refresh();
        work.get(1).run(); var latest = bridge.state(); work.get(0).run();
        assertEquals(2, bridge.state().model()); assertEquals(latest, bridge.state());
        bridge.apply(latest.version(), SourceDocument.parse("2").replace("move", new SourceDocument.Range(0, 1), "3"));
        assertEquals("3", port.text); assertEquals(1, port.commands);
        assertThrows(IOException.class, () -> bridge.apply(latest.version(), SourceDocument.parse("2").replace("move", new SourceDocument.Range(0, 1), "4")));
        bridge.close(); work.getLast().run(); assertTrue(bridge.closed());
    }
    @Test void retainsLastValidSceneButBlocksWritesDuringParsingOrInvalidCode() throws Exception {
        Port port = new Port(); List<Runnable> work = new ArrayList<>();
        var bridge = new SceneDocumentBridge<>(port, Integer::parseInt, work::add, Runnable::run, s -> {});
        bridge.refresh(); work.removeFirst().run(); var valid = bridge.state();
        port.text = "bad"; port.version++; bridge.refresh();
        assertFalse(bridge.state().writable()); work.removeFirst().run();
        assertEquals(1, bridge.state().model()); assertFalse(bridge.state().writable());
        assertFalse(bridge.state().error().isBlank());
        assertThrows(IOException.class, () -> bridge.apply(valid.version(), SourceDocument.parse("1").replace("x", new SourceDocument.Range(0, 1), "2")));
        port.text = "8"; port.version++; bridge.refresh(); work.removeFirst().run();
        assertTrue(bridge.state().writable()); assertEquals(8, bridge.state().model());
    }
}
