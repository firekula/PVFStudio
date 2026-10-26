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
        void value(String name,String value) {
            if(name.equals("dgn-room-maps")||name.equals("dgn-room-boss-maps")) {
                var field=find(panel,name,MultiSelectDropdown.class);
                for(Object id:List.copyOf(field.selected()))field.toggle(id);
                if(!value.isBlank())for(String id:value.split(","))field.toggle(Integer.valueOf(id.trim()));
            } else find(panel,name,JTextField.class).setText(value);
        }
    }
    @Test void emptyClickCreatesRoomConnectsBothWaysAndNavigatesActualMap() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            var h = new Harness(); JComponent grid = find(h.panel,"dgn-editor-grid",JComponent.class);
            grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_PRESSED,0,0,2*64+32,32,1,false));
            assertEquals(new Position(2,0), h.panel.position());
            h.value("dgn-room-maps","10, 11");h.value("dgn-room-boss-maps","20"); h.click("dgn-apply-maps");
            assertEquals(List.of(10,11),h.document().mazes().getFirst().layout().maps().get(new Position(2,0)));
            assertEquals(List.of(20),h.document().mazes().getFirst().layout().bossMaps().get(new Position(2,0)));
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
    @Test void newlyEnabledHellUsesExistingArchiveTemplateAndKeepsExistingDifficultyShape() throws Exception {
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();h.text=h.text.replace("[hell dungeon] 1","[hell dungeon] 0").replace("[hit count] 1 2 3 4 5\n","").replace("[seal door map index] 20","").replace("[seal door pos] 0 0","");h.install();
            h.panel.onHellTemplates(done -> done.accept(List.of(new DungeonEditorPanel.HellTemplate("dungeon/template.dgn",new DungeonDocument.HellSettings(true,false,20,new Position(1,0),List.of(3,4,5,6),7,8)))));
            h.panel.hellTemplatePicker(values -> values.getFirst());h.click("dgn-hell-enabled");
            assertTrue(h.document().hell(0).enabled());assertEquals(List.of(3,4,5,6),h.document().hell(0).hitCounts());assertEquals(new Position(0,0),h.document().hell(0).position());assertEquals(8,h.document().hell(0).coinLimit());
            String before=h.text;h.click("dgn-open-hell-map");assertEquals(before,h.text);assertEquals(List.of("map/hell.map"),h.opened);
        }catch(Exception e){throw new RuntimeException(e);}});
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
    @Test void sourceGridCaretAndOnlyRoleActionsPreserveIndependentHellConfiguration() throws Exception {
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();h.panel.selectSourceOffset(h.text.indexOf("BBEEAA")+4);assertEquals(new Position(2,0),h.panel.position());
            h.value("dgn-room-maps","10");h.click("dgn-apply-maps");h.click("dgn-only-start");h.click("dgn-only-boss");
            assertEquals(List.of(new Position(2,0)),h.document().mazes().getFirst().layout().starts());assertEquals(List.of(new Position(2,0)),h.document().mazes().getFirst().layout().bosses());
            assertTrue(h.panel.activeHellAt(new Position(0,0)));h.text=h.text.replace("[hell dungeon] 1","[hell dungeon] 0");h.install();assertFalse(h.panel.activeHellAt(new Position(0,0)));
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void oneFullHeightPreviewWithCompactRoomSelectionAndNoEmptyDiagnosticFooter()throws Exception{
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();var split=(JSplitPane)((BorderLayout)h.panel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            assertSame(h.panel.previewHost(),split.getLeftComponent(),"The main column must contain only the full preview");
            assertNotNull(find((Container)split.getRightComponent(),"dgn-editor-grid",JComponent.class));
            var footer=((BorderLayout)h.panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH);assertTrue(footer.getPreferredSize().height<40,"An empty diagnostics area must not reserve rows");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void roomGridClearsOldScenePixelsAndInspectorNeverScrolls()throws Exception{
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();var grid=find(h.panel,"dgn-editor-grid",JComponent.class);grid.setSize(320,180);var pixels=new java.awt.image.BufferedImage(320,180,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=pixels.createGraphics();g.setColor(Color.MAGENTA);g.fillRect(0,0,320,180);grid.paint(g);g.dispose();assertNotEquals(Color.MAGENTA.getRGB(),pixels.getRGB(250,140),"A room grid must erase cached map pixels");
            var field=find(h.panel,"dgn-room-maps",MultiSelectDropdown.class);assertNull(SwingUtilities.getAncestorOfClass(JScrollPane.class,field));assertNull(SwingUtilities.getAncestorOfClass(JScrollPane.class,grid));assertNotNull(find(h.panel,"dgn-inspector-tabs",JTabbedPane.class));
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void automaticPoolsAreVisibleEditableAndUntouchedPoolsStayAutomatic()throws Exception{
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();h.text="[maze info] [size] 2 1 [greed] `BBEE` [start map] 0 0 [/start map] [boss map] 1 0 [/boss map]";h.install();
            var files=Map.of("dungeon/dungeon.lst","1 `x.dgn`","map/map.lst","10 `a.map` 20 `b.map`","map/a.map","[dungeon] 1 [type] `[normal]` [greed] `BBBBEEEE`","map/b.map","[dungeon] 1 [type] `[boss]` [greed] `BBBBEEEE`");
            var dungeon=studio.pvf.preview.DungeonPreview.load("dungeon/x.dgn",h.text,files::get);var choice=DungeonEditorPreview.resolve(dungeon,h.panel.preview());String unchanged=h.text;h.panel.resolvedChoices(choice);
            assertEquals(List.of(10),find(h.panel,"dgn-room-maps",MultiSelectDropdown.class).selected());assertEquals(List.of(20),find(h.panel,"dgn-room-boss-maps",MultiSelectDropdown.class).selected());assertEquals(unchanged,h.text);
            h.value("dgn-room-maps","99");h.panel.resolvedChoices(choice);assertEquals(List.of(99),find(h.panel,"dgn-room-maps",MultiSelectDropdown.class).selected(),"An async pool resolution must not overwrite choices");
            h.panel.selectRoom(new Position(0,0));h.panel.resolvedChoices(choice);h.value("dgn-room-boss-maps","20");h.click("dgn-apply-maps");
            assertFalse(h.document().mazes().getFirst().layout().maps().containsKey(new Position(0,0)),"Displaying an automatic pool must not silently pin it");assertEquals(List.of(20),h.document().mazes().getFirst().layout().bossMaps().get(new Position(0,0)));
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void roleDropdownsSupportMultipleOverlappingCandidates()throws Exception{
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();h.text=h.text.replace("BBEEAA","BBFFEE");h.install();
            var starts=find(h.panel,"dgn-start-rooms",MultiSelectDropdown.class);var bosses=find(h.panel,"dgn-boss-rooms",MultiSelectDropdown.class);
            starts.toggle(new Position(1,0));bosses.toggle(new Position(2,0));
            assertEquals(List.of(new Position(0,0),new Position(1,0)),h.document().mazes().getFirst().layout().starts());
            assertEquals(List.of(new Position(1,0),new Position(2,0)),h.document().mazes().getFirst().layout().bosses());
            h.panel.selectRoom(new Position(2,0));assertEquals(2,starts.selected().size());assertEquals(2,bosses.selected().size());
            var grid=find(h.panel,"dgn-editor-grid",JComponent.class);grid.setSize(192,64);var image=new java.awt.image.BufferedImage(192,64,2);grid.paint(image.createGraphics());
            assertTrue(hasColor(image,0,64,new Color(66,190,125)));assertTrue(hasColor(image,64,128,new Color(66,190,125)));
            assertTrue(hasColor(image,64,128,new Color(245,157,66)));assertTrue(hasColor(image,128,192,new Color(245,157,66)));
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    private static boolean hasColor(java.awt.image.BufferedImage image,int x0,int x1,Color color){for(int y=0;y<image.getHeight();y++)for(int x=x0;x<x1;x++)if(image.getRGB(x,y)==color.getRGB())return true;return false;}
    static <T> T find(Container parent,String name,Class<T> type) {
        for(Component c:parent.getComponents()) {
            if(name.equals(c.getName()) && type.isInstance(c)) return type.cast(c);
            if(c instanceof Container child) { try { return find(child,name,type); } catch(NoSuchElementException ignored) {} }
        }
        throw new NoSuchElementException(name);
    }
}
