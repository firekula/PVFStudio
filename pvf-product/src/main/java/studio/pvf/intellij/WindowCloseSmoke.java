package studio.pvf.intellij;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.util.concurrent.*;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Post genuine AWT close events; direct dispatch inside Application.invokeLater masks the missing read lock. */
final class WindowCloseSmoke {
    static void run(Path archive, Path output) throws Exception {
        Path root = output.resolve("window-close-workspaces");
        var first = create(root, archive);
        try {
            edt(() -> { check(!first.getIconImages().isEmpty(), "Window icon was not applied"); return null; });
            postClose(first); await(() -> !first.isDisplayable());
        } finally { cleanup(first); }
        var second = create(root, archive);
        try {
            edt(() -> { edit(editor(tabs(second).getSelectedComponent()), "\n// native_close_saved"); return null; });
            var cancelled = answer("取消"); postClose(second); cancelled.get(6, TimeUnit.SECONDS);
            edt(() -> { check(second.isDisplayable() && editor(tabs(second).getSelectedComponent()).getDocument().getText().contains("native_close_saved"), "Cancel changed the editor"); return null; });
            var saved = answer("保存草稿后继续"); postClose(second); saved.get(6, TimeUnit.SECONDS); await(() -> !second.isDisplayable());
        } finally { cleanup(second); }
        var third = create(root, archive);
        try {
            edt(() -> { var editor = editor(tabs(third).getSelectedComponent()); check(editor.getDocument().getText().contains("native_close_saved"), "Native close lost the saved draft");
                edit(editor, "\n// native_close_discarded"); return null; });
            var discarded = answer("放弃未保存编辑"); postClose(third); discarded.get(6, TimeUnit.SECONDS); await(() -> !third.isDisplayable());
        } finally { cleanup(third); }
        var fourth = create(root, archive);
        try {
            edt(() -> { check(!editor(tabs(fourth).getSelectedComponent()).getDocument().getText().contains("native_close_discarded"), "Discard persisted edits"); return null; });
            postClose(fourth); await(() -> !fourth.isDisplayable());
        } finally { cleanup(fourth); }
        Files.writeString(output.resolve("window-close-result.txt"), "PASS: posted native AWT WINDOW_CLOSING with an open editor, cancel preserves content, save closes and restores draft, discard closes without saving\n");
    }
    private static PvfStudioFrame create(Path root, Path archive) throws Exception {
        var frame = edt(() -> { var f = new PvfStudioFrame(root, false); f.setVisible(true); f.openArchive(archive); return f; });
        await(() -> button(frame, "打开 PVF…").isEnabled());
        edt(() -> { frame.openEntry("monster/monster.lst"); return null; });
        await(() -> button(frame, "打开 PVF…").isEnabled() && tabs(frame).getTabCount() == 1); return frame;
    }
    private static EditorTabs tabs(PvfStudioFrame frame) { return components(frame, EditorTabs.class).getFirst(); }
    private static void postClose(PvfStudioFrame frame) { Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); }
    private static CompletableFuture<Void> answer(String choice) throws Exception {
        var answered = new CompletableFuture<Void>();
        edt(() -> { long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); var timer = new Timer(20, null);
            timer.addActionListener(event -> {
                for (Window window : Window.getWindows()) if (window instanceof JDialog dialog && dialog.isShowing() && "未保存的编辑".equals(dialog.getTitle())) {
                    timer.stop(); button(dialog, choice).doClick(); answered.complete(null); return;
                }
                if (System.nanoTime() > until) { timer.stop(); answered.completeExceptionally(new AssertionError("Native close did not prompt")); }
            }); timer.start(); return null;
        }); return answered;
    }
    private static void cleanup(PvfStudioFrame frame) throws Exception {
        if (edt(frame::isDisplayable)) { edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; }); await(() -> !frame.isDisplayable()); }
    }
}
