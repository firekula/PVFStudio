package studio.pvf.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Actual workbench, actual editors and confirmation dialogs; all drafts stay under the smoke output. */
final class TabWorkbenchSmoke {
    private TabWorkbenchSmoke() {}
    static void run(Path archive, Path output) throws Exception {
        PvfStudioFrame frame = edt(() -> {
            var f = new PvfStudioFrame(output.resolve("tab-workspaces"), false);
            f.setSize(1450, 850); f.setVisible(true); f.openArchive(archive); return f;
        });
        try {
            await(() -> button(frame, "打开 PVF…").isEnabled() && components(frame, JTree.class).getFirst().getRowCount() > 1);
            EditorTabs tabs = edt(() -> components(frame, EditorTabs.class).getFirst());
            List<String> paths = List.of("creature/script/creature.lst", "monster/monster.lst", "npc/npc.lst", "passiveobject/passiveobject.lst",
                    "map/ocean/b2401.map", "map/lorien/104.map", "map/sunderlandpoison/b801.map", "map/hendonmyre/hendon_auction.map",
                    "map/underfoot/h6607(2,1).map", "passiveobject/mapobject/particlefactory/particle/rayground.ptl");
            edt(() -> { frame.selectEncoding(1); return null; });
            for (int i = 0; i < paths.size(); i++) {
                String path = paths.get(i); int count = i + 1;
                edt(() -> { frame.openEntry(path); return null; }); await(() -> tabs.getTabCount() == count && button(frame, "打开 PVF…").isEnabled());
            }
            var initial = edt(() -> order(tabs));
            edt(() -> {
                for (int i : new int[]{0, 9, 2, 8, 1, 0}) {
                    tabs.setSelectedIndex(i); check(order(tabs).equals(initial), "Selection changed tab order");
                    check(tabs.getTabRunCount() == 1, "Tabs wrapped into moving rows");
                }
                screenshot(frame, output.resolve("tabs-single-row.png")); return null;
            });
            Component a = initial.get(0), b = initial.get(1), c = initial.get(2), d = initial.get(3);
            edt(() -> {
                tabs.setSelectedComponent(a); var menu = tabs.menuFor(b);
                check(tabs.getSelectedComponent() == a, "Context menu activated its target");
                item(menu, "向右移动标签页").doClick(); check(tabs.indexOfComponent(b) == 2 && tabs.getSelectedComponent() == a, "Context move changed active editor");
                item(tabs.menuFor(b), "向左移动标签页").doClick(); check(order(tabs).equals(initial), "Explicit move did not restore original order");
                check(!item(tabs.menuFor(a), "关闭左侧标签页").isEnabled(), "Left boundary action is enabled");
                var popup = tabs.menuFor(b); popup.setSize(popup.getPreferredSize()); screenshot(popup, output.resolve("tabs-context-menu.png"));
                action(frame, "next"); check(tabs.getSelectedComponent() == b, "Next editor failed");
                action(frame, "previous"); check(tabs.getSelectedComponent() == a, "Previous editor failed");
                action(frame, "move-right"); check(tabs.indexOfComponent(a) == 1, "Move shortcut failed");
                action(frame, "move-left"); check(order(tabs).equals(initial), "Move shortcut changed unrelated positions");
                tabs.setSelectedComponent(d); tabs.setSelectedComponent(b); tabs.setSelectedComponent(a);
                action(frame, "recent"); check(tabs.getSelectedComponent() == b, "Recent editor failed");
                action(frame, "recent"); check(tabs.getSelectedComponent() == d, "Held Control did not cycle history");
                tabs.finishCycle(); tabs.setSelectedComponent(a); return null;
            });
            Editor editorA = edt(() -> editor(a)), editorB = edt(() -> editor(b));
            String originalB = edt(() -> editorB.getDocument().getText());
            String savedB = originalB + "\n// saved B\n\n// typed during save B\n";
            edt(() -> { edit(editorA, "\n// unsaved A\n"); edit(editorB, "\n// saved B\n"); editorB.getCaretModel().moveToOffset(12); return null; });
            edt(() -> {
                respond("取消"); item(tabs.menuFor(b), "关闭").doClick();
                check(tabs.getTabCount() == 10 && tabs.getSelectedComponent() == a, "Cancel changed tabs or selection");
                respond("保存草稿后继续"); item(tabs.menuFor(b), "关闭").doClick();
                edit(editorB, "\n// typed during save B\n"); respond("保存草稿后继续"); return null;
            });
            await(() -> tabs.getTabCount() == 9 && button(frame, "打开 PVF…").isEnabled());
            edt(() -> {
                check(editorB.isDisposed(), "Closed editor was not released");
                check(tabs.getTitleAt(tabs.indexOfComponent(a)).startsWith("* "), "Closing B saved unrelated A");
                frame.selectEncoding(2); action(frame, "reopen"); return null;
            });
            await(() -> tabs.getTabCount() == 10 && button(frame, "打开 PVF…").isEnabled());
            Component reopenedB = edt(tabs::getSelectedComponent);
            edt(() -> {
                check(tabs.indexOfComponent(reopenedB) == 1, "Reopened tab lost its position");
                check(editor(reopenedB).getCaretModel().getOffset() == 12, "Reopened editor lost its caret");
                check(tabs.getToolTipTextAt(1).contains("949"), "Reopen used the toolbar encoding instead of the original encoding");
                check(editor(reopenedB).getDocument().getText().equals(savedB), "Edits made during close/save were lost");
                edit(editor(reopenedB), "\n// discarded B\n"); respond("放弃未保存编辑"); action(frame, "close"); return null;
            });
            await(() -> tabs.getTabCount() == 9);
            edt(() -> { action(frame, "reopen"); return null; }); await(() -> tabs.getTabCount() == 10 && button(frame, "打开 PVF…").isEnabled());
            edt(() -> {
                check(editor(tabs.getSelectedComponent()).getDocument().getText().equals(savedB), "Reopen resurrected discarded changes");
                screenshot(frame, output.resolve("tabs-scoped-save.png"));
                item(tabs.menuFor(tabs.getSelectedComponent()), "关闭未修改的标签页").doClick();
                check(tabs.getTabCount() == 1 && tabs.getSelectedComponent() == a, "Close unmodified removed a dirty editor");
                respond("取消"); action(frame, "close-all"); check(tabs.getTabCount() == 1, "Cancel close-all failed");
                respond("保存草稿后继续"); action(frame, "close-all"); return null;
            });
            await(() -> tabs.getTabCount() == 0 && button(frame, "打开 PVF…").isEnabled());
            edt(() -> { action(frame, "reopen"); return null; }); await(() -> tabs.getTabCount() == 1 && button(frame, "打开 PVF…").isEnabled());
            edt(() -> { check(editor(tabs.getSelectedComponent()).getDocument().getText().endsWith("// unsaved A\n"), "Close-all did not preserve saved draft"); frame.selectEncoding(1); return null; });
            for (int i = 1; i < 4; i++) {
                int count = i + 1; String path = paths.get(i);
                edt(() -> { frame.openEntry(path); return null; }); await(() -> tabs.getTabCount() == count && button(frame, "打开 PVF…").isEnabled());
            }
            edt(() -> { item(tabs.menuFor(tabs.getComponentAt(1)), "关闭右侧标签页").doClick(); check(tabs.getTabCount() == 2, "Close right failed"); action(frame, "reopen"); return null; });
            await(() -> tabs.getTabCount() == 3 && button(frame, "打开 PVF…").isEnabled());
            edt(() -> { action(frame, "reopen"); return null; }); await(() -> tabs.getTabCount() == 4 && button(frame, "打开 PVF…").isEnabled());
            edt(() -> {
                tabs.setSelectedIndex(0); tabs.setSelectedIndex(1); tabs.setSelectedIndex(2); frame.toFront();
                editor(tabs.getSelectedComponent()).getContentComponent().requestFocusInWindow(); return null;
            });
            await(() -> editor(tabs.getSelectedComponent()).getContentComponent().isFocusOwner());
            edt(() -> { controlTab(editor(tabs.getSelectedComponent()).getContentComponent()); return null; });
            await(() -> tabs.getSelectedIndex() == 1);
            edt(() -> { controlTab(editor(tabs.getSelectedComponent()).getContentComponent()); return null; });
            await(() -> tabs.getSelectedIndex() == 2); // Releasing Ctrl commits MRU; the next press returns to the previous editor.
            edt(() -> {
                check(order(tabs).stream().map(component -> tabs.getToolTipTextAt(tabs.indexOfComponent(component))).toList().equals(
                        List.of(paths.get(0), paths.get(1), paths.get(2), paths.get(3)).stream().map(path -> path + " [x-windows-949]").toList()), "Batch reopen changed file order");
                item(tabs.menuFor(tabs.getComponentAt(2)), "关闭左侧标签页").doClick(); check(tabs.getTabCount() == 2, "Close left failed");
                Component anchor = tabs.getComponentAt(0); item(tabs.menuFor(anchor), "关闭其他标签页").doClick();
                check(tabs.getTabCount() == 1 && tabs.getSelectedComponent() == anchor, "Close others ignored its right-click target");
                return null;
            });
            edt(() -> { frame.toFront(); editor(tabs.getSelectedComponent()).getContentComponent().requestFocusInWindow(); return null; });
            await(() -> editor(tabs.getSelectedComponent()).getContentComponent().isFocusOwner());
            edt(() -> {
                // Post only to this test workbench's editor. This exercises the IDE key dispatcher without OS input.
                Component input = editor(tabs.getSelectedComponent()).getContentComponent();
                stroke(input, KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK);
                stroke(input, KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK); return null;
            });
            await(() -> tabs.getTabCount() == 0);
            Files.writeString(output.resolve("tabs-result.txt"), "PASS: stable overflow order; context target; move; MRU; scoped save/discard/cancel; restore position/caret/encoding; close left/right/others/unmodified/all\n");
        } finally {
            edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; });
            await(() -> !frame.isDisplayable());
        }
    }
    static void action(PvfStudioFrame frame, String id) { frame.getRootPane().getActionMap().get("pvf-tab-" + id).actionPerformed(new ActionEvent(frame, 0, id)); }
    static void stroke(Component target, int code, int modifiers) {
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue(); long now = System.currentTimeMillis();
        queue.postEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, now, modifiers, code, KeyEvent.CHAR_UNDEFINED));
        queue.postEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, now, modifiers, code, KeyEvent.CHAR_UNDEFINED));
    }
    private static void controlTab(Component target) {
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue(); long now = System.currentTimeMillis();
        queue.postEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, now, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED));
        stroke(target, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
        queue.postEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0, KeyEvent.VK_CONTROL, KeyEvent.CHAR_UNDEFINED));
    }
    static Editor editor(Component tab) { return Arrays.stream(EditorFactory.getInstance().getAllEditors()).filter(e -> e.getComponent() == tab).findFirst().orElseThrow(); }
    static void edit(Editor editor, String text) {
        CommandProcessor.getInstance().executeCommand(editor.getProject(), () -> ApplicationManager.getApplication().runWriteAction(() ->
                editor.getDocument().insertString(editor.getDocument().getTextLength(), text)), "Tab scope smoke edit", null);
    }
    static AbstractButton button(Container root, String label) { return components(root, AbstractButton.class).stream().filter(b -> label.equals(b.getText().split("    ")[0])).findFirst().orElseThrow(); }
    private static JMenuItem item(JPopupMenu menu, String label) { return Arrays.stream(menu.getComponents()).filter(c -> c instanceof JMenuItem i && i.getText().split("    ")[0].equals(label)).map(c -> (JMenuItem) c).findFirst().orElseThrow(); }
    static void respond(String label) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        javax.swing.Timer timer = new javax.swing.Timer(25, null);
        timer.addActionListener(e -> {
            for (Window window : Window.getWindows()) if (window instanceof JDialog dialog && dialog.isShowing() && "未保存的编辑".equals(dialog.getTitle())) {
                timer.stop(); button(dialog, label).doClick(); return;
            }
            if (System.nanoTime() > deadline) timer.stop();
        }); timer.start();
    }
    private static List<Component> order(EditorTabs tabs) { List<Component> list = new ArrayList<>(); for (int i = 0; i < tabs.getTabCount(); i++) list.add(tabs.getComponentAt(i)); return list; }
    static <T> List<T> components(Container root, Class<T> kind) {
        List<T> list = new ArrayList<>(); if (kind.isInstance(root)) list.add(kind.cast(root));
        if (root instanceof JMenu menu) list.addAll(components(menu.getPopupMenu(), kind));
        for (Component c : root.getComponents()) { if (c instanceof Container container) list.addAll(components(container, kind)); else if (kind.isInstance(c)) list.add(kind.cast(c)); }
        return list.stream().distinct().toList();
    }
    static void screenshot(Component component, Path path) throws Exception {
        if (component instanceof Container container) container.doLayout();
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB); var g = image.createGraphics(); component.paint(g); g.dispose(); ImageIO.write(image, "png", path.toFile());
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static <T> T edt(Callable<T> call) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>(); ApplicationManager.getApplication().invokeLater(() -> { try { result.complete(call.call()); } catch (Throwable e) { result.completeExceptionally(e); } }); return result.get(20, TimeUnit.SECONDS);
    }
    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!edt(condition::getAsBoolean)) { if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for tab workbench"); Thread.sleep(30); }
    }
}
