package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

class TaskProgressTest {
    @Test void replacedOrCompletedTasksCannotOverwriteOrHideTheCurrentTask() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var progress = new TaskProgress("test-progress");
            var old = progress.start("old");
            var current = progress.start("new");
            old.update("late old result", 100, 100, "stale");
            progress.finish(old);
            current.update("resources.index", 25, 100, "current.npk"); progress.refresh();
            assertTrue(progress.running()); assertTrue(progress.isVisible());
            assertFalse(progress.bar().isIndeterminate()); assertEquals(250, progress.bar().getValue());
            assertTrue(progress.bar().getString().contains("25%"));
            progress.finish(current); current.update("late", 100, 100, ""); progress.refresh();
            assertFalse(progress.running()); assertFalse(progress.isVisible());
        });
    }
    @Test void unknownTotalsAnimateAndFastPreviewLoadsStayHidden() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var progress = new TaskProgress("test-progress");
            var ticket = progress.start("resources.discover");
            assertTrue(progress.bar().isIndeterminate()); assertTrue(progress.bar().getString().contains("秒"));
            progress.finish(ticket);
            var fast = progress.start("decode", 10_000); fast.update("decode", 1, 1, ""); progress.refresh();
            assertFalse(progress.isVisible()); progress.finish(fast); assertFalse(progress.running());
        });
    }
}
