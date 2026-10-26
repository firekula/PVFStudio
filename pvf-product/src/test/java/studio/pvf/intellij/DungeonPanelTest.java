package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import studio.pvf.preview.DungeonPreview;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DungeonPanelTest {
    @Test void roomClicksSelectTheBossAndClicksOutsideTheLayoutHaveNoEffect() throws Exception {
        var dungeon = dungeon();
        SwingUtilities.invokeAndWait(() -> {
            List<DungeonPanel.Selection> changes = new ArrayList<>(); var panel = new DungeonPanel(changes::add);
            panel.install(dungeon); panel.setSize(800, 300); panel.doLayout(); assertEquals(1, changes.size());
            click(panel, true); assertTrue(panel.selection().room().boss()); assertEquals(3, panel.selection().map().id());
            var grid = named(panel, "dungeon-rooms");
            grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1));
            assertEquals(2, changes.size()); assertTrue(panel.selection().room().boss());
        });
    }
    @Test void roomMapChoiceAndClearStateSurviveLeavingAndReturning() throws Exception {
        var dungeon = dungeon();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new DungeonPanel(selection -> {}); panel.install(dungeon); panel.setSize(800, 300); panel.doLayout();
            var maps = (JComboBox<?>) named(panel, "dungeon-map"); maps.setSelectedIndex(0); assertEquals(1, panel.selection().map().id());
            components(panel).stream().filter(c -> c instanceof JCheckBox box && box.getText().equals("房间已清理"))
                    .map(c -> (JCheckBox) c).findFirst().orElseThrow().doClick();
            assertFalse(panel.selection().cleared()); click(panel, true); assertTrue(panel.selection().cleared());
            click(panel, false); assertFalse(panel.selection().cleared()); assertEquals(1, panel.selection().map().id());
        });
    }
    @Test void codePreviewUsesNativeTilesKeepsSelectionAndFallsBackOnMissingOrStaleAssets()throws Exception{
        var dungeon=dungeon();
        var sprites=new studio.pvf.preview.ScenePreview.SpriteLookup(){
            public studio.pvf.preview.NpkResources.Info info(String path,int frame){return new studio.pvf.preview.NpkResources.Info(18,18,0,0,18,18);}
            public studio.pvf.preview.NpkResources.Sprite sprite(String path,int frame)throws java.io.IOException{if(path.startsWith("Interface"))throw new java.io.IOException("optional");var image=new java.awt.image.BufferedImage(18,18,2);var g=image.createGraphics();g.setColor(Color.MAGENTA);g.fillRect(0,0,18,18);g.dispose();return new studio.pvf.preview.NpkResources.Sprite(image,0,0,18,18,java.nio.file.Path.of("test.npk"),path,frame,frame);}
        };
        var atlas=studio.pvf.preview.DungeonMinimap.load("Map/MiniMap/Act1.img",sprites);
        SwingUtilities.invokeAndWait(() -> {
            var changes=new ArrayList<DungeonPanel.Selection>();var panel=new DungeonPanel(changes::add);panel.install(dungeon,"Map/MiniMap/Act1.img");panel.setSize(320,280);panel.doLayout();
            var grid=(JComponent)named(panel,"dungeon-rooms");grid.setSize(64,32);var pixels=new java.awt.image.BufferedImage(64,32,2);
            int callbacks=changes.size();panel.setMinimap("Map/MiniMap/Act1.img",atlas,"");grid.paint(pixels.createGraphics());assertEquals(Color.MAGENTA.getRGB(),pixels.getRGB(20,25));assertEquals(callbacks,changes.size(),"Resource completion must not select rooms or reload the main preview");
            grid.dispatchEvent(new MouseEvent(grid,MouseEvent.MOUSE_PRESSED,0,0,48,16,1,false));assertTrue(panel.selection().room().boss());
            panel.install(dungeon,"Map/MiniMap/Act2.img");panel.setMinimap("Map/MiniMap/Act1.img",atlas,"");grid.paint(pixels.createGraphics());assertNotEquals(Color.MAGENTA.getRGB(),pixels.getRGB(20,25));
            panel.setMinimap("Map/MiniMap/Act2.img",atlas,"");panel.setMinimap("Map/MiniMap/Act2.img",null,"资源缺失");grid.paint(pixels.createGraphics());assertNotEquals(Color.MAGENTA.getRGB(),pixels.getRGB(20,25));assertTrue(grid.getToolTipText(new MouseEvent(grid,MouseEvent.MOUSE_MOVED,0,0,16,16,0,false)).contains("资源缺失"));
        });
    }
    private static void click(DungeonPanel panel, boolean boss) {
        var grid = named(panel, "dungeon-rooms");
        grid.dispatchEvent(new MouseEvent(grid, MouseEvent.MOUSE_PRESSED, 0, 0, grid.getWidth() / 2 + (boss ? 16 : -16), 16, 1, false, MouseEvent.BUTTON1));
    }
    private static Component named(Container root, String name) { return components(root).stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow(); }
    private static List<Component> components(Container root) {
        List<Component> result = new ArrayList<>(); for (Component c : root.getComponents()) { result.add(c); if (c instanceof Container child) result.addAll(components(child)); } return result;
    }
    private static DungeonPreview dungeon() throws Exception {
        var files = Map.of("dungeon/dungeon.lst", "1 `a.dgn`", "map/map.lst", "1 `a.map` 2 `b.map` 3 `boss.map`",
                "map/a.map", "[dungeon] 1 [/dungeon] [type] `[normal]` [greed] `BBBB`",
                "map/b.map", "[dungeon] 1 [/dungeon] [type] `[normal]` [greed] `BBBB`",
                "map/boss.map", "[dungeon] 1 [/dungeon] [type] `[boss]` [greed] `EEEE`");
        return DungeonPreview.load("dungeon/a.dgn", "[maze info] [size] 2 1 [greed] `BBEE` [start map] 0 0 [/start map] [boss map] 1 0 [/boss map]", files::get);
    }
}
