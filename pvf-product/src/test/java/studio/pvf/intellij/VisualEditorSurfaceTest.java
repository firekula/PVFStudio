package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class VisualEditorSurfaceTest {
    @Test void switchingKeepsTheSameCodeAndVisualComponentsAndTheirState() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextArea code = new JTextArea("unchanged document"); code.setCaretPosition(4);
            Object document = code.getDocument(); JPanel design = new JPanel();
            var surface = new VisualEditorSurface(code, design, () -> {});
            for (var mode : VisualEditorSurface.Mode.values()) {
                surface.setMode(mode); assertEquals(mode, surface.mode());
                assertSame(document, code.getDocument()); assertEquals("unchanged document", code.getText());
                assertEquals(4, code.getCaretPosition()); assertSame(code, surface.code());
            }
            surface.restore("SPLIT", .6); assertEquals(VisualEditorSurface.Mode.SPLIT, surface.mode());
            assertEquals(.6, surface.ratio(), .001);
            surface.restore("future-mode", Double.NaN); assertEquals(VisualEditorSurface.Mode.CODE, surface.mode());
            assertEquals(.5, surface.ratio(), .001);
        });
    }
}
