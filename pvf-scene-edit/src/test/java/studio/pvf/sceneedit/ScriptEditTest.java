package studio.pvf.sceneedit;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScriptEditTest {
    @Test void appliesSeveralCoordinateEditsWithoutTouchingSurroundings() throws Exception {
        String text = "// note\r\n[passive object] 100 1 2 3 100 4 5 6 [/passive object]\r\n";
        int start = text.indexOf("4 5 6");
        var edit = new ScriptEdit("移动第二个对象", text, List.of(
                new ScriptEdit.Replacement(start, start + 1, "4", "400"),
                new ScriptEdit.Replacement(start + 2, start + 3, "5", "500")));
        assertEquals("// note\r\n[passive object] 100 1 2 3 100 400 500 6 [/passive object]\r\n", edit.applyTo(text));
    }

    @Test void rejectsStaleOrOverlappingEdits() {
        var stale = new ScriptEdit("移动", "12345", List.of(new ScriptEdit.Replacement(1, 2, "2", "X")));
        assertThrows(IOException.class, () -> stale.applyTo("12346"));
        var overlap = new ScriptEdit("错误补丁", "12345", List.of(
                new ScriptEdit.Replacement(1, 3, "23", "X"), new ScriptEdit.Replacement(2, 4, "34", "Y")));
        assertThrows(IOException.class, () -> overlap.applyTo("12345"));
        var wrongSlice = new ScriptEdit("错误位置", "12345", List.of(new ScriptEdit.Replacement(1, 2, "9", "X")));
        assertThrows(IOException.class, () -> wrongSlice.applyTo("12345"));
        var ambiguousInsert = new ScriptEdit("重复插入", "abc", List.of(
                new ScriptEdit.Replacement(1, 1, "", "X"), new ScriptEdit.Replacement(1, 1, "", "Y")));
        assertThrows(IOException.class, () -> ambiguousInsert.applyTo("abc"));
    }
}
