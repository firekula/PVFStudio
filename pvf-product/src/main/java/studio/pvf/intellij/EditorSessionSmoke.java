package studio.pvf.intellij;

import com.intellij.openapi.editor.Editor;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Close/reopen actual workbenches with isolated on-disk sessions and saved drafts. */
final class EditorSessionSmoke {
    private EditorSessionSmoke() {}
    static void run(Path archive, Path output) throws Exception {
        Path root = output.resolve("session-workspaces");
        List<String> paths = List.of("creature/script/creature.lst", "monster/monster.lst", "npc/npc.lst");
        List<String> order = List.of(paths.get(2), paths.get(0), paths.get(1));
        PvfStudioFrame first = open(archive, root);
        EditorSessionState expected;
        try {
            EditorTabs tabs = edt(() -> tabs(first));
            for (int i = 0; i < paths.size(); i++) {
                int encodingIndex = i == 0 ? 0 : 1, count = i + 1; String path = paths.get(i);
                edt(() -> { first.selectEncoding(encodingIndex); first.openEntry(path); return null; });
                await(() -> ready(first) && tabs.getTabCount() == count);
                edt(() -> { edit(editor(tabs.getSelectedComponent()), ("\n// wide viewport fixture " + "wide ".repeat(110)).repeat(220)); return null; });
            }
            edt(() -> { action(first, "save-all"); return null; }); await(() -> ready(first));
            edt(() -> { tabs.moveTo(tabs.getComponentAt(2), 0); return null; });
            for (int i = 0; i < 3; i++) {
                int index = i;
                edt(() -> { tabs.setSelectedIndex(index); return null; });
                edt(() -> {
                    Editor editor = editor(tabs.getSelectedComponent()); editor.getCaretModel().moveToOffset(100 + index * 73);
                    editor.getScrollingModel().disableAnimation(); editor.getScrollingModel().scrollHorizontally(60 + index * 37);
                    editor.getScrollingModel().scrollVertically(800 + index * 240); editor.getScrollingModel().enableAnimation(); return null;
                });
            }
            Path directory;
            try (var directories = Files.list(root)) { directory = directories.filter(Files::isDirectory).findFirst().orElseThrow(); }
            // No tab switch follows this caret movement: caret events alone must reach disk.
            edt(() -> { editor(tabs.getSelectedComponent()).getCaretModel().moveToOffset(333); return null; });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (true) {
                var saved = EditorSessionState.read(directory).state();
                if (saved.tabs().size() == 3 && saved.tabs().get(2).caret() == 333) { expected = saved; break; }
                if (System.nanoTime() > deadline) throw new AssertionError("Editor session did not autosave caret changes");
                Thread.sleep(30);
            }
            // Scrolling without moving the caret must also be persisted.
            int nextX = expected.tabs().get(2).scrollX() + 41, nextY = expected.tabs().get(2).scrollY() + 113;
            edt(() -> {
                var scrolling = editor(tabs.getSelectedComponent()).getScrollingModel(); scrolling.disableAnimation();
                scrolling.scrollHorizontally(nextX); scrolling.scrollVertically(nextY); scrolling.enableAnimation(); return null;
            });
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (true) {
                var saved = EditorSessionState.read(directory).state();
                if (saved.tabs().size() == 3 && saved.tabs().get(2).scrollX() == nextX && saved.tabs().get(2).scrollY() == nextY) { expected = saved; break; }
                if (System.nanoTime() > deadline) throw new AssertionError("Editor session did not autosave scrolling alone");
                Thread.sleep(30);
            }
            check(expected.tabs().stream().map(EditorSessionState.Tab::path).toList().equals(order), "Autosave lost visual tab order");
            check(expected.selectedPath().equals(paths.get(1)), "Autosave lost the active tab");
            check(expected.tabs().stream().allMatch(tab -> tab.scrollX() > 0 && tab.scrollY() > 0), "Fixture did not exercise both scroll axes");
            edt(() -> { screenshot(first, output.resolve("session-before-close.png")); return null; });
            List<EditorSessionState.Tab> finalTabs = new ArrayList<>(expected.tabs());
            EditorSessionState.Tab last = finalTabs.get(2);
            finalTabs.set(2, new EditorSessionState.Tab(last.path(), last.charset(), 777, last.scrollX(), last.scrollY()));
            expected = new EditorSessionState(finalTabs, expected.selectedPath());
            edt(() -> {
                editor(tabs.getSelectedComponent()).getCaretModel().moveToOffset(777);
                first.dispatchEvent(new WindowEvent(first, WindowEvent.WINDOW_CLOSING)); return null;
            });
            await(() -> !first.isDisplayable());
            check(EditorSessionState.read(directory).state().equals(expected), "Close did not flush the most recent caret before the debounce elapsed");

            PvfStudioFrame second = open(archive, root);
            try {
                verify(second, expected, false);
                // Close again before visiting hidden tabs: their deferred viewports must survive.
                close(second);
            } finally { if (edt(second::isDisplayable)) close(second); }

            PvfStudioFrame third = open(archive, root);
            try {
                verify(third, expected, true);
                edt(() -> { screenshot(third, output.resolve("session-restored.png")); return null; });
                Path other = output.resolve("session-other.pvf"); Files.copy(archive, other);
                edt(() -> { third.openArchive(other); return null; }); await(() -> ready(third) && tabs(third).getTabCount() == 0);
                edt(() -> { third.openEntry(paths.get(0)); return null; }); await(() -> ready(third) && tabs(third).getTabCount() == 1);
                edt(() -> { editor(tabs(third).getSelectedComponent()).getCaretModel().moveToOffset(42); third.openArchive(archive); return null; });
                await(() -> ready(third) && tabs(third).getTabCount() == 3);
                verify(third, expected, true);
                edt(() -> { third.openArchive(other); return null; }); await(() -> ready(third) && tabs(third).getTabCount() == 1);
                edt(() -> { check(editor(tabs(third).getSelectedComponent()).getCaretModel().getOffset() == 42, "Other PVF session leaked or lost its caret"); third.openArchive(archive); return null; });
                await(() -> ready(third) && tabs(third).getTabCount() == 3);
                edt(() -> {
                    edit(editor(tabs(third).getSelectedComponent()), "\n// discarded session edit\n");
                    respond("取消"); third.dispatchEvent(new WindowEvent(third, WindowEvent.WINDOW_CLOSING));
                    check(third.isDisplayable() && tabs(third).getTabCount() == 3, "Cancel close changed the session");
                    respond("放弃未保存编辑"); third.dispatchEvent(new WindowEvent(third, WindowEvent.WINDOW_CLOSING)); return null;
                });
                await(() -> !third.isDisplayable());
            } finally { if (edt(third::isDisplayable)) close(third); }

            PvfStudioFrame fourth = open(archive, root);
            try {
                edt(() -> {
                    check(tabs(fourth).getTabCount() == 3, "Discarding edits discarded the tab layout");
                    for (int i = 0; i < 3; i++) {
                        String text = editor(tabs(fourth).getComponentAt(i)).getDocument().getText();
                        check(text.contains("wide viewport fixture") && !text.contains("discarded session edit"), "Session revived discarded text or lost saved drafts");
                    }
                    action(fourth, "close-all"); return null;
                });
                close(fourth);
            } finally { if (edt(fourth::isDisplayable)) close(fourth); }
            PvfStudioFrame empty = open(archive, root);
            try { edt(() -> { check(tabs(empty).getTabCount() == 0, "Closed-all tabs reappeared after reopening"); return null; }); }
            finally { close(empty); }

            // Missing entries cannot prevent valid tabs from restoring; stale carets are clamped.
            var stale = new EditorSessionState(List.of(new EditorSessionState.Tab(paths.get(0), "GBK", Integer.MAX_VALUE, 0, 0),
                    new EditorSessionState.Tab("missing/session-smoke.map", "GBK", 1, 0, 0),
                    new EditorSessionState.Tab(paths.get(2), "MS949", 23, 0, 0)), "missing/session-smoke.map");
            stale.write(directory);
            PvfStudioFrame partial = open(archive, root);
            try {
                edt(() -> {
                    check(tabs(partial).getTabCount() == 2 && tabs(partial).getSelectedIndex() == 0, "Missing tab blocked restoration or active fallback");
                    Editor editor = editor(tabs(partial).getSelectedComponent());
                    check(editor.getCaretModel().getOffset() == editor.getDocument().getTextLength(), "Stale caret was not clamped"); return null;
                });
            } finally { close(partial); }
            Files.writeString(output.resolve("session-result.txt"), "PASS: debounced metadata autosave, tab order/active/encoding/caret/two-axis scrolling, hidden tabs across two restarts, per-PVF isolation, close cancellation, draft/discard separation, explicit empty session, missing entries and stale caret\n");
        } finally { if (edt(first::isDisplayable)) close(first); }
    }

    private static PvfStudioFrame open(Path archive, Path root) throws Exception {
        PvfStudioFrame frame = edt(() -> {
            var created = new PvfStudioFrame(root, false); created.setSize(1450, 850); created.setVisible(true); created.openArchive(archive); return created;
        });
        await(() -> ready(frame) && components(frame, javax.swing.JTree.class).getFirst().getRowCount() > 1); return frame;
    }
    private static void close(PvfStudioFrame frame) throws Exception {
        edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; });
        await(() -> !frame.isDisplayable());
    }
    private static boolean ready(PvfStudioFrame frame) { return frame.isEnabled() && button(frame, "打开 PVF…").isEnabled(); }
    private static EditorTabs tabs(PvfStudioFrame frame) { return components(frame, EditorTabs.class).getFirst(); }
    private static void verify(PvfStudioFrame frame, EditorSessionState expected, boolean visitHidden) throws Exception {
        edt(() -> {
            EditorTabs tabs = tabs(frame); check(tabs.getTabCount() == expected.tabs().size(), "Session tab count changed");
            check(tabs.getToolTipTextAt(tabs.getSelectedIndex()).startsWith(expected.selectedPath() + " ["), "Active tab was not restored");
            for (int i = 0; i < tabs.getTabCount(); i++) {
                EditorSessionState.Tab position = expected.tabs().get(i);
                check(tabs.getToolTipTextAt(i).equals(position.path() + " [" + position.charset() + "]"), "Order or encoding was not restored");
                check(editor(tabs.getComponentAt(i)).getCaretModel().getOffset() == position.caret(), "Hidden-tab caret was not restored");
            }
            return null;
        });
        for (int i = 0; i < expected.tabs().size(); i++) {
            int index = i; EditorSessionState.Tab position = expected.tabs().get(i);
            if (!visitHidden && !position.path().equals(expected.selectedPath())) continue;
            edt(() -> { tabs(frame).setSelectedIndex(index); return null; });
            await(() -> {
                Editor editor = editor(tabs(frame).getComponentAt(index));
                return Math.abs(editor.getScrollingModel().getHorizontalScrollOffset() - position.scrollX()) <= 1
                        && Math.abs(editor.getScrollingModel().getVerticalScrollOffset() - position.scrollY()) <= 1;
            });
        }
        edt(() -> {
            for (int i = 0; i < expected.tabs().size(); i++) if (expected.tabs().get(i).path().equals(expected.selectedPath())) tabs(frame).setSelectedIndex(i);
            return null;
        });
    }
}
