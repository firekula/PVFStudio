package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import studio.pvf.sceneedit.*;
import studio.pvf.preview.DungeonScript.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DungeonEditorPanelTest {
    static final String SOURCE = "[hell dungeon] 1\n[hit count] 1 2 3 4 5\n[maze info]\n[size] 3 1\n[greed] `BBEEAA`\n"
            + "[map specification] 0 0 10 [/map specification]\n[map specification] 1 0 11 [/map specification]\n"
            + "[start map] 0 0 [/start map]\n[boss map] 1 0 [/boss map]\n[seal door map index] 20\n[seal door pos] 0 0\n"
            + "[maze info]\n[size] 1 1\n[greed] `AA`\n[start map] 0 0 [/start map]\n[escape hell] 1\n";
    static class Harness {
        String text = SOURCE; DungeonEditorPanel panel; List<String> opened = new ArrayList<>();
        Harness() throws Exception {
            panel = new DungeonEditorPanel((version, edit) -> { text = edit.applyTo(text); install(); }, opened::add, () -> {}, range -> {});
            panel.setCatalog(SceneCatalog.build(Set.of("map/map.lst", "map/a.map", "map/b.map", "map/hell.map"),
                    path -> path.equals("map/map.lst") ? "10 `a.map`\n11 `b.map`\n20 `hell.map`" : null)); install();
        }
        void install() throws Exception { panel.install(DungeonDocument.parse("dungeon/x.dgn", text), 1, true, ""); }
        DungeonDocument document() throws Exception { return DungeonDocument.parse("dungeon/x.dgn",text); }
        void click(String name) { find(panel, name, AbstractButton.class).doClick(); }
        void value(String name,String value) { find(panel, name, JTextField.class).setText(value); }
    }
    @Test void emptyClickCreatesRoomConnectsBothWaysAndNavigatesActualMap() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            var h = new Harness(); JComponent grid = find(h.panel,"dgn-editor-grid",JComponent.class);
            grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_PRESSED,0,0,2*64+32,32,1,false));
            assertEquals(new Position(2,0), h.panel.position());
            h.value("dgn-room-maps","10, 11"); h.click("dgn-apply-maps");
            assertEquals(List.of(10,11),h.document().mazes().getFirst().layout().maps().get(new Position(2,0)));
            h.click("dgn-connect-left"); assertTrue(h.document().mazes().getFirst().layout().connected(new Position(2,0),Direction.LEFT));
            h.click("dgn-open-map"); assertEquals(List.of("map/a.map"),h.opened);
            h.click("dgn-start"); h.click("dgn-boss");
            assertEquals(2,h.document().mazes().getFirst().layout().starts().size());
            assertEquals(new Position(0,0),h.document().hell(0).position());
            h.click("dgn-delete-room"); assertFalse(h.document().mazes().getFirst().rooms().contains(new Position(2,0)));
        } catch(Exception e) { throw new RuntimeException(e); } });
    }
    @Test void mazeAndHellEditsStayIndependentAndRecoverFromInvalidSource() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            var h = new Harness(); h.panel.selectRoom(new Position(1,0)); h.click("dgn-hell-here");
            assertEquals(new Position(1,0),h.document().hell(0).position());
            assertEquals(List.of(new Position(1,0)),h.document().mazes().getFirst().layout().bosses());
            find(h.panel,"dgn-editor-maze",JComboBox.class).setSelectedIndex(1);
            assertTrue(find(h.panel,"dgn-escape-hell",JCheckBox.class).isSelected());
            h.value("dgn-hell-map","20"); h.click("dgn-hell-here"); h.click("dgn-escape-hell");
            assertFalse(h.document().hell(1).excluded()); assertEquals(20,h.document().hell(0).mapId());
            h.click("dgn-copy-maze"); assertEquals(3,h.document().mazes().size());
            String beforePreview=h.text; find(h.panel,"dgn-preview-mode",JComboBox.class).setSelectedIndex(1);
            assertEquals(beforePreview,h.text,"Preview mode must not write source");
            h.click("dgn-new-maze"); assertEquals(4,h.document().mazes().size());
            h.panel.install(h.document(),2,false,"syntax error");
            assertFalse(find(h.panel,"dgn-apply-maps",JButton.class).isEnabled());
            h.install(); assertTrue(find(h.panel,"dgn-apply-maps",JButton.class).isEnabled());
            assertTrue(h.panel.cropSummary(1,1).contains("0"));
        } catch(Exception e) { throw new RuntimeException(e); } });
    }
    @Test void fixedLayoutsAndUnverifiedEscapeSupportHaveDisabledControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            var h=new Harness();
            String first=SOURCE.substring(0,SOURCE.lastIndexOf("[maze info]")).replace("1 2 3 4 5","1 2 3 4");
            h.panel.install(DungeonDocument.parse("dungeon/x.dgn",first),2,true,"");
            assertFalse(find(h.panel,"dgn-escape-hell",JCheckBox.class).isEnabled());
            h.panel.install(DungeonDocument.parse("dungeon/x.dgn","[warroom map index] 10"),3,true,"");
            assertFalse(find(h.panel,"dgn-apply-maps",JButton.class).isEnabled());
        } catch(Exception e) { throw new RuntimeException(e); } });
    }
    static <T> T find(Container parent,String name,Class<T> type) {
        for(Component c:parent.getComponents()) {
            if(name.equals(c.getName()) && type.isInstance(c)) return type.cast(c);
            if(c instanceof Container child) { try { return find(child,name,type); } catch(NoSuchElementException ignored) {} }
        }
        throw new NoSuchElementException(name);
    }
}
