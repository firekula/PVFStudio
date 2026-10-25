package studio.pvf.intellij;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Real menu commands, persistent recent PVFs and session restoration through a recent item. */
final class MenuWorkbenchSmoke {
    private MenuWorkbenchSmoke() {}
    static void run(Path archive, Path output) throws Exception {
        Path root = output.resolve("menu-workspaces"), history = root.resolve("recent-files.properties");
        Path other = output.resolve("menu-other.pvf"); Files.copy(archive, other);
        PvfStudioFrame first = create(root);
        try {
            await(() -> button(first, "打开最近的文件").isEnabled());
            edt(() -> {
                var bar = first.getJMenuBar(); check(bar.getMenuCount() == 6, "Menu bar is missing supported groups");
                check(Arrays.asList("文件(F)", "编辑(E)", "选择(S)", "查看(V)", "转到(G)", "帮助(H)")
                        .equals(java.util.stream.IntStream.range(0, 6).mapToObj(i -> bar.getMenu(i).getText()).toList()), "Menu group order changed");
                check(!button(first, "保存草稿").isEnabled() && !button(first, "导出新 PVF…").isEnabled(), "Archive actions enabled without an archive");
                check(components(first, JButton.class).stream().noneMatch(b -> "打开 PVF…".equals(b.getText())), "Old toolbar remains visible");
                first.openArchive(archive); return null;
            }); await(() -> ready(first));
            edt(() -> {
                click(first, "文件(F)", "打开编码"); button(first, "MS949 / CP949").doClick();
                first.openEntry("monster/monster.lst"); return null;
            }); await(() -> ready(first) && tabs(first).getTabCount() == 1);
            edt(() -> {
                var editor = editor(tabs(first).getSelectedComponent()); editor.getCaretModel().moveToOffset(321);
                edit(editor, "\n// menu undo fixture\n"); click(first, "编辑(E)", "撤销");
                check(!editor.getDocument().getText().endsWith("// menu undo fixture\n"), "Menu undo did not affect the editor");
                click(first, "编辑(E)", "重做"); check(editor.getDocument().getText().endsWith("// menu undo fixture\n"), "Menu redo did not affect the editor");
                click(first, "编辑(E)", "撤销");
                click(first, "选择(S)", "全选"); check(editor.getSelectionModel().getSelectionEnd() == editor.getDocument().getTextLength(), "Select-all did not target the active editor");
                editor.getSelectionModel().removeSelection(); editor.getCaretModel().moveToOffset(321);
                click(first, "查看(V)", "自动换行"); check(editor.getSettings().isUseSoftWraps(), "Wrap menu did not update editor settings");
                click(first, "查看(V)", "自动换行");
                click(first, "文件(F)", "打开最近的文件");
                check(recentItems(first).size() == 1, "Successful open was not recorded");
                first.getJMenuBar().getMenu(0).getPopupMenu().setSize(first.getJMenuBar().getMenu(0).getPopupMenu().getPreferredSize());
                screenshot(first.getJMenuBar().getMenu(0).getPopupMenu(), output.resolve("menu-file.png"));
                screenshot(first, output.resolve("menu-workbench.png"));
                MenuSelectionManager.defaultManager().clearSelectedPath(); first.openArchive(other); return null;
            }); await(() -> ready(first) && tabs(first).getTabCount() == 0);
            edt(() -> {
                click(first, "文件(F)", "打开最近的文件");
                var recent = recentItems(first); check(recent.size() == 2 && other.equals(recent.getFirst().getClientProperty("pvf.archive")), "Recent list did not promote the latest archive");
                recent.stream().filter(item -> archive.equals(item.getClientProperty("pvf.archive"))).findFirst().orElseThrow().doClick();
                MenuSelectionManager.defaultManager().clearSelectedPath(); return null;
            }); await(() -> ready(first) && tabs(first).getTabCount() == 1);
            edt(() -> {
                check(tabs(first).getToolTipTextAt(0).contains("949"), "Recent open lost the original tab encoding");
                check(editor(tabs(first).getSelectedComponent()).getCaretModel().getOffset() == 321, "Recent open did not restore the caret");
                click(first, "文件(F)", "打开最近的文件"); check(recentItems(first).getFirst().getClientProperty("pvf.archive").equals(archive), "Reopening did not promote the recent entry");
                JMenu recent = (JMenu) button(first, "打开最近的文件"); recent.getPopupMenu().setSize(recent.getPopupMenu().getPreferredSize());
                screenshot(recent.getPopupMenu(), output.resolve("menu-recent.png")); MenuSelectionManager.defaultManager().clearSelectedPath(); return null;
            });
            close(first);
            check(RecentArchives.read(history).paths().equals(List.of(archive, other)), "History was not persisted in recency order");

            PvfStudioFrame second = create(root);
            try {
                await(() -> button(second, "打开最近的文件").isEnabled());
                edt(() -> {
                    click(second, "文件(F)", "打开最近的文件"); check(recentItems(second).size() == 2, "Recent entries did not survive restart");
                    recentItems(second).getFirst().doClick(); MenuSelectionManager.defaultManager().clearSelectedPath(); return null;
                }); await(() -> ready(second) && tabs(second).getTabCount() == 1);
                edt(() -> { click(second, "文件(F)", "打开最近的文件"); button(second, "清除最近打开的文件").doClick();
                    check(recentItems(second).isEmpty() && tabs(second).getTabCount() == 1, "Clear history closed the current editor"); MenuSelectionManager.defaultManager().clearSelectedPath(); return null;
                });
                close(second);
            } finally { if (edt(second::isDisplayable)) close(second); }
            check(RecentArchives.read(history).paths().isEmpty(), "Clearing recent entries was not persisted");
            PvfStudioFrame third = create(root);
            try {
                await(() -> button(third, "打开最近的文件").isEnabled());
                edt(() -> { click(third, "文件(F)", "打开最近的文件"); check(recentItems(third).isEmpty(), "Cleared entries reappeared after restart");
                    MenuSelectionManager.defaultManager().clearSelectedPath(); third.openArchive(archive); return null; });
                await(() -> ready(third) && tabs(third).getTabCount() == 1);
            } finally { close(third); }
            Files.writeString(output.resolve("menu-result.txt"), "PASS: menu groups, action enablement, encoding radio, undo/redo/select-all/wrap commands, recent promotion and deduplication, session restore through recent menu, restart persistence, clearing preserves current editor and saved sessions\n");
        } finally { if (edt(first::isDisplayable)) close(first); }
    }
    private static PvfStudioFrame create(Path root) throws Exception {
        return edt(() -> { var frame = new PvfStudioFrame(root, false); frame.setSize(1450, 850); frame.setVisible(true); return frame; });
    }
    private static EditorTabs tabs(PvfStudioFrame frame) { return components(frame, EditorTabs.class).getFirst(); }
    private static boolean ready(PvfStudioFrame frame) { return frame.isEnabled() && button(frame, "打开 PVF…").isEnabled(); }
    private static List<JMenuItem> recentItems(PvfStudioFrame frame) {
        return components(frame, JMenuItem.class).stream().filter(item -> item.getClientProperty("pvf.archive") instanceof Path).toList();
    }
    private static void click(PvfStudioFrame frame, String group, String item) {
        ((JMenu) button(frame, group)).doClick(); button(frame, item).doClick();
    }
    private static void close(PvfStudioFrame frame) throws Exception {
        edt(() -> { MenuSelectionManager.defaultManager().clearSelectedPath(); respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; });
        await(() -> !frame.isDisplayable());
    }
}
