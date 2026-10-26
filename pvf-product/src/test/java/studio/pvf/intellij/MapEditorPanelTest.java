package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import studio.pvf.sceneedit.*;
import javax.swing.*;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static studio.pvf.intellij.DungeonEditorPanelTest.find;

class MapEditorPanelTest {
    static class Harness {
        String text="[tile] `a.til` [/tile]\n[passive object] 1 100 120 5 [/passive object]\n[unknown] @9(77) // preserve\n";
        MapEditorPanel panel; List<String> opened=new ArrayList<>(); long version=1;
        Harness() throws Exception {
            panel=new MapEditorPanel("map/a.map",(stamp,edit) -> { assertEquals(version,stamp); text=edit.applyTo(text); version++; install(); },() -> version,opened::add,range -> {},() -> {});
            panel.setCatalog(SceneCatalog.build(Set.of("map/a.til","map/sub/b.til","map/sub/a.ani","map/other.map","map/map.lst","monster/monster.lst","monster/a.mon","passiveobject/passiveobject.lst","passiveobject/a.obj"),p -> switch(p) {
                case "monster/monster.lst" -> "2 `a.mon`"; case "passiveobject/passiveobject.lst" -> "1 `a.obj`"; default -> null;
            })); install();
        }
        void install() throws Exception { panel.install(MapDocument.parse("map/a.map",text),version,true,""); }
        void click(String name) { find(panel,name,AbstractButton.class).doClick(); }
    }
    @Test void catalogFiltersDescendantsAndAddsNativePlacementsAndTiles() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            var h=new Harness(); var browser=h.panel.browser();
            browser.select(SceneResourceBrowser.Category.TILE,"map/sub/b.til"); h.panel.addSelectedAt(130,140);
            assertEquals(List.of("a.til","sub/b.til"),MapDocument.parse("map/a.map",h.text).tiles(false).stream().map(MapDocument.TileEntry::reference).toList());
            browser.select(SceneResourceBrowser.Category.MONSTER,"monster/a.mon"); h.panel.addSelectedAt(130,140);
            browser.select(SceneResourceBrowser.Category.PASSIVE,"passiveobject/a.obj"); h.panel.addSelectedAt(130,140);
            browser.select(SceneResourceBrowser.Category.ANIMATION,"map/sub/a.ani"); h.panel.addSelectedAt(130,140);
            var model=MapDocument.parse("map/a.map",h.text);
            assertEquals(4,model.entries().size()); assertTrue(h.text.contains("[unknown] @9(77) // preserve"));
            h.panel.selectEntry(model.entries().getLast().id());
            find(h.panel,"map-prop-x",JTextField.class).setText("170"); h.click("map-apply-properties");
            assertEquals(170,MapDocument.parse("map/a.map",h.text).entries().getLast().x());
            h.click("map-clone-entry"); assertEquals(5,MapDocument.parse("map/a.map",h.text).entries().size());
        } catch(Exception e){throw new RuntimeException(e);} });
    }
    @Test void globalFieldsNavigateToDefinitionAndInvalidSourceDisablesWrites() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            var h=new Harness(); var model=MapDocument.parse("map/a.map",h.text); h.panel.selectEntry(model.entries().getFirst().id());
            h.click("map-open-definition"); assertEquals(List.of("passiveobject/a.obj"),h.opened);
            assertNull(findOptional(h.panel,"map-prop-layer"),"Entity layer belongs to the global definition");
            h.panel.install(model,2,false,"invalid source"); assertFalse(find(h.panel,"map-add-selected",JButton.class).isEnabled());
            h.install(); assertTrue(find(h.panel,"map-add-selected",JButton.class).isEnabled());
        } catch(Exception e){throw new RuntimeException(e);} });
    }
    @Test void championTraitsAndHellpartyPayloadEditTheirNativeLists() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            var h=new Harness(); h.text+="[monster] 2 1 0 100 120 0 1 1 `[fixed]` `[champion]` 2 3 4 [/monster]\n"
                    +"[special passive object] 1 50 120 0 1 `[hellparty]` 117 1 1 `[/hellparty]` [/special passive object]";
            h.install(); var model=MapDocument.parse("map/a.map",h.text);
            h.panel.selectEntry(model.entries().get(1).id());
            find(h.panel,"map-prop-traits",JTextField.class).setText("5, 6, 7"); h.click("map-apply-properties");
            assertTrue(h.text.contains("3 5 6 7"));
            h.panel.selectEntry(MapDocument.parse("map/a.map",h.text).entries().getLast().id());
            find(h.panel,"map-special-contents",JTextArea.class).setText("hellparty 118 2 1\nmonster 2 1 0 0 0"); h.click("map-apply-contents");
            assertTrue(h.text.contains("118 2 1")); assertEquals("2",MapDocument.parse("map/a.map",h.text).entries().getLast().properties().get("contentCount"));
        } catch(Exception e){throw new RuntimeException(e);} });
    }
    @Test void addingATileColumnRequiresExplicitExtendedCellsAndKeepsRowsRectangular() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            var h=new Harness(); h.text+="[extended tile] `ext1.til` `ext2.til` [/extended tile]";h.install();
            h.panel.tileColumnPicker(defaults -> {assertEquals(List.of("ext1.til","ext2.til"),defaults);return List.of("sub/b.til","sub/b.til");});
            h.panel.browser().select(SceneResourceBrowser.Category.TILE,"map/sub/b.til");h.panel.addSelectedAt(0,0);
            var updated=MapDocument.parse("map/a.map",h.text);
            assertEquals(2,updated.tiles(false).size());assertEquals(List.of("ext1.til","sub/b.til","ext2.til","sub/b.til"),updated.tiles(true).stream().map(MapDocument.TileEntry::reference).toList());
            String before=h.text;h.panel.tileColumnPicker(defaults -> null);h.panel.addSelectedAt(0,0);assertEquals(before,h.text,"Cancel must not partially add a base tile");
        } catch(Exception e){throw new RuntimeException(e);} });
    }
    @Test void returningToMapPreservesCameraButDoesNotOverrideTheSharedViewportCheckbox() throws Exception {
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();var canvas=SceneCanvasTest.canvas();h.panel.attach(canvas);
            canvas.clientViewport=true;canvas.panX=40;canvas.freeZoom=1.5;canvas.camera.set(100,10);h.panel.captureView(canvas);
            canvas.clientViewport=false;canvas.panX=0;h.panel.restoreView(canvas);
            assertFalse(canvas.clientViewport);assertEquals(40,canvas.panX);assertEquals(1.5,canvas.freeZoom);assertEquals(new java.awt.Point(100,0),canvas.camera.position());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void renamedMapUsesTheNewFolderForAssetBrowsingAndRelativeReferences() throws Exception {
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();h.panel.setCatalog(SceneCatalog.build(Set.of("map/renamed/other.til"),p -> null));
            h.panel.install(MapDocument.parse("map/renamed/new.map",h.text),h.version,true,"");
            h.panel.browser().select(SceneResourceBrowser.Category.TILE,"map/renamed/other.til");
            assertNotNull(h.panel.browser().selected());assertEquals("map/renamed/other.til",h.panel.browser().selected().path());
            h.panel.addSelectedAt(0,0);assertEquals("other.til",MapDocument.parse("map/a.map",h.text).tiles(false).getLast().reference());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void applyingUnchangedPropertiesPreservesCommentsInsideTheTraitVector() throws Exception {
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();h.text+="[monster] 2 1 0 100 120 0 1 1 `[fixed]` `[champion]` 2\t3 # trait comment\n4 [/monster]";h.install();
            h.panel.selectEntry(MapDocument.parse("map/a.map",h.text).entries().getLast().id());String before=h.text;
            h.click("map-apply-properties");assertEquals(before,h.text);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void aLargePlacementListIsPublishedAsABatch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();var list=find(h.panel,"map-object-list",JList.class);int[] additions={0};
            list.getModel().addListDataListener(new javax.swing.event.ListDataListener(){public void intervalAdded(javax.swing.event.ListDataEvent e){additions[0]++;}public void intervalRemoved(javax.swing.event.ListDataEvent e){}public void contentsChanged(javax.swing.event.ListDataEvent e){}});
            StringBuilder text=new StringBuilder("[tile] `a.til` [/tile] [passive object] ");for(int i=0;i<3000;i++)text.append("1 ").append(i).append(" 120 0 ");text.append("[/passive object]");h.text=text.toString();h.install();
            assertEquals(3000,list.getModel().getSize());assertTrue(additions[0]<=2,"Large native lists must not remeasure every existing row after each insertion");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void previewPaneReturnsToItsOriginalSizeAfterAHiddenTabWasTemporarilyNarrowed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {try{
            var h=new Harness();var canvas=SceneCanvasTest.canvas();h.panel.previewHost().add(canvas);h.panel.attach(canvas);
            h.panel.setSize(1200,800);layout(h.panel);var before=canvas.getSize();h.panel.captureLayout();h.panel.captureView(canvas);
            h.panel.setSize(600,800);layout(h.panel);h.panel.setSize(1200,800);layout(h.panel);h.panel.restoreView(canvas);layout(h.panel);
            assertEquals(before,canvas.getSize());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    private static void layout(java.awt.Container parent){parent.doLayout();for(var c:parent.getComponents())if(c instanceof java.awt.Container child)layout(child);}
    private static Object findOptional(java.awt.Container root,String name) { try{return find(root,name,JComponent.class);}catch(NoSuchElementException ignored){return null;} }
}
