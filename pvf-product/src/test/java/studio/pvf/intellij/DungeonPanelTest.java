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
