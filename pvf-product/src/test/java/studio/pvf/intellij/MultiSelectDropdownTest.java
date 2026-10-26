package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiSelectDropdownTest {
    @Test void multipleChoicesPreserveOrderUnknownIdsAndSilentRefresh() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var field=new MultiSelectDropdown<Integer>();var events=new ArrayList<List<Integer>>();field.onChange(events::add);
            field.options(Map.of(10,"10 · a.map",20,"20 · b.map"));field.selected(List.of(99,10));
            assertEquals(List.of(99,10),field.selected());assertTrue(events.isEmpty());
            field.toggle(20);assertEquals(List.of(99,10,20),field.selected());assertEquals(List.of(List.of(99,10,20)),events);
            field.options(Map.of(20,"20 · new.map"));assertEquals(List.of(99,10,20),field.selected());
            field.toggle(10);assertEquals(List.of(99,20),field.selected());
            field.setEnabled(false);field.toggle(20);assertEquals(List.of(99,20),field.selected());
        });
    }
}
