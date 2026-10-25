package studio.pvf.intellij;

import com.intellij.find.EditorSearchSession;
import javax.swing.*;
import java.awt.event.*;
import java.nio.file.*;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Actual editor headers, keyboard shortcuts, draft/unsaved content search and navigation. */
final class SearchWorkbenchSmoke {
    static void run(Path archive, Path output) throws Exception {
        String marker = "pvf_search_smoke_20261025";
        var frame = edt(() -> { var f = new PvfStudioFrame(output.resolve("search-workspaces"), false); f.setVisible(true); return f; });
        try {
            await(() -> button(frame, "打开最近的文件").isEnabled());
            edt(() -> { frame.openArchive(archive); return null; }); await(() -> ready(frame));
            edt(() -> { frame.openEntry("creature/script/creature.lst"); return null; }); await(() -> ready(frame) && tabs(frame).getTabCount() == 1);
            edt(() -> { edit(editor(tabs(frame).getSelectedComponent()), "\n// " + marker); button(frame, "保存草稿").doClick(); return null; }); await(() -> ready(frame));
            edt(() -> { action(frame, "close"); frame.openEntry("monster/monster.lst"); return null; }); await(() -> ready(frame) && tabs(frame).getTabCount() == 1);
            edt(() -> { var editor = editor(tabs(frame).getSelectedComponent()); edit(editor, "\n// " + marker);
                editor.getContentComponent().requestFocusInWindow(); return null; });
            edt(() -> { stroke(editor(tabs(frame).getSelectedComponent()).getContentComponent(), KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK); return null; });
            await(() -> EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())) != null);
            edt(() -> { EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())).setTextInField(marker); return null; });
            await(() -> EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())).hasMatches());
            await(() -> { var component = EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())).getComponent(); return component.isShowing() && component.getHeight() > 20; });
            edt(() -> { action(frame, "replace"); check(EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())).getFindModel().isReplaceState(), "Replace header did not open"); return null; });
            await(() -> EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())).getComponent().getReplaceTextComponent().isShowing());
            edt(() -> { var search = EditorSearchSession.get(editor(tabs(frame).getSelectedComponent()));
                search.getComponent().getReplaceTextComponent().setText(marker + "_replaced"); return null; });
            await(() -> { var search = EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())); return !search.isSearchInProgress() && search.hasMatches()
                    && search.getFindModel().getStringToReplace().equals(marker + "_replaced"); });
            edt(() -> { EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())).searchBackward(); return null; });
            await(() -> EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())).getSearchResults().getCursor() != null);
            edt(() -> { EditorSearchSession.get(editor(tabs(frame).getSelectedComponent())).getComponent().replace(); return null; });
            await(() -> editor(tabs(frame).getSelectedComponent()).getDocument().getText().contains(marker + "_replaced"));
            edt(() -> { button(frame, "撤销").doClick(); return null; });
            await(() -> !editor(tabs(frame).getSelectedComponent()).getDocument().getText().contains(marker + "_replaced"));
            edt(() -> { screenshot(frame, output.resolve("search-in-file.png")); action(frame, "global-search"); return null; });
            var dialog = edt(() -> java.util.Arrays.stream(frame.getOwnedWindows()).filter(w -> w instanceof PvfSearchDialog && w.isShowing()).map(w -> (PvfSearchDialog) w).findFirst().orElseThrow());
            edt(() -> { var query = components(dialog, JTextField.class).getFirst(); query.setText(marker); query.postActionEvent(); return null; });
            await(() -> components(dialog, JList.class).getFirst().getModel().getSize() == 2);
            edt(() -> { check(tabs(frame).getTabCount() == 1, "Content scan opened documents"); screenshot(dialog, output.resolve("search-global.png"));
                var list = components(dialog, JList.class).getFirst(); list.setSelectedIndex(0); list.getActionMap().get("open").actionPerformed(new ActionEvent(list, 0, "open")); return null; });
            await(() -> ready(frame) && tabs(frame).getTabCount() == 2);
            edt(() -> { check(marker.equals(editor(tabs(frame).getSelectedComponent()).getSelectionModel().getSelectedText()), "Search result did not select the matching text");
                var query = components(dialog, JTextField.class).getFirst(); query.setText("cancel_search_20261025"); query.postActionEvent(); button(dialog, "取消搜索").doClick(); return null; });
            await(() -> components(dialog, JLabel.class).stream().anyMatch(label -> label.getText().startsWith("已取消")));
            edt(() -> { dialog.dispose(); frame.openEntry("map/alfhlyra/alfhlyra.map"); return null; });
            await(() -> ready(frame) && tabs(frame).getTabCount() == 3);
            Files.writeString(output.resolve("search-result.txt"), "PASS: Ctrl+F, visible native header/live matches, replacement and undo, draft and unsaved global matches, scan opens no documents, exact result selection, cancellation keeps archive readable\n");
        } finally {
            edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; });
            await(() -> !frame.isDisplayable());
        }
    }
    private static EditorTabs tabs(PvfStudioFrame frame) { return components(frame, EditorTabs.class).getFirst(); }
    private static boolean ready(PvfStudioFrame frame) { return button(frame, "打开 PVF…").isEnabled(); }
}
