package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditorTabsTest {
    @Test void overflowingTabsKeepOneRowAndNeverChangeOrderOnSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EditorTabs tabs = tabs(14); tabs.setSize(420, 300); tabs.doLayout();
            List<Component> original = order(tabs);
            for (int index : new int[]{0, 13, 2, 9, 1, 11, 0}) {
                tabs.setSelectedIndex(index); tabs.doLayout();
                assertEquals(original, order(tabs)); assertEquals(1, tabs.getTabRunCount());
                assertEquals(tabs.getBoundsAt(0).y, tabs.getBoundsAt(index).y);
            }
        });
    }
    @Test void recentHistoryIsIndependentOfVisualOrderAndCommitsWhenControlIsReleased() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var tabs = tabs(4); var original = order(tabs);
            tabs.setSelectedIndex(3); tabs.setSelectedIndex(0); tabs.setSelectedIndex(2);
            tabs.cycleRecent(1); assertEquals(original.get(0), tabs.getSelectedComponent());
            tabs.cycleRecent(1); assertEquals(original.get(3), tabs.getSelectedComponent());
            tabs.cycleRecent(-1); assertEquals(original.get(0), tabs.getSelectedComponent());
            assertEquals(original, order(tabs)); tabs.finishCycle();
            tabs.cycleRecent(1); assertEquals(original.get(2), tabs.getSelectedComponent());
        });
    }
    @Test void explicitMovesKeepEditorIdentityFocusHistoryAndCloseTarget() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var tabs = tabs(4); var original = order(tabs); tabs.setSelectedIndex(0);
            tabs.moveRelative(original.get(1), 1);
            assertEquals(List.of(original.get(0), original.get(2), original.get(1), original.get(3)), order(tabs));
            assertSame(original.get(0), tabs.getSelectedComponent());
            tabs.moveRelative(original.get(1), -1); assertEquals(original, order(tabs));
            List<Component> closed = new ArrayList<>(); tabs.onClose(closed::add);
            JPanel header = (JPanel) tabs.getTabComponentAt(1);
            ((JButton) header.getComponent(1)).doClick(); assertEquals(List.of(original.get(1)), closed);
            assertSame(original.get(0), tabs.getSelectedComponent());
            tabs.setSelectedIndex(2); tabs.setSelectedIndex(3);
            assertSame(original.get(2), tabs.preferredAfterClosing(List.of(original.get(3))));
        });
    }
    @Test void batchClosePublishesOnlyTheFinalSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var tabs = tabs(4); tabs.setSelectedIndex(3); List<Component> changes = new ArrayList<>();
            tabs.addChangeListener(e -> changes.add(tabs.getSelectedComponent())); Component keep = tabs.getComponentAt(0);
            tabs.runBatch(() -> { tabs.removeTabAt(3); tabs.removeTabAt(2); tabs.removeTabAt(1); tabs.setSelectedComponent(keep); });
            assertEquals(List.of(keep), changes);
            tabs.removeAll(); tabs.cycleRecent(1); assertEquals(0, tabs.getTabCount());
        });
    }
    private static EditorTabs tabs(int count) {
        var tabs = new EditorTabs();
        for (int i = 0; i < count; i++) {
            JPanel panel = new JPanel(); tabs.addTab("long-script-file-name-" + i + ".map", panel); tabs.decorate(panel, null);
        }
        return tabs;
    }
    private static List<Component> order(EditorTabs tabs) {
        List<Component> order = new ArrayList<>(); for (int i = 0; i < tabs.getTabCount(); i++) order.add(tabs.getComponentAt(i)); return order;
    }
}
