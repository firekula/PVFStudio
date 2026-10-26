package studio.pvf.intellij;

import studio.pvf.workspace.PvfWorkspace;
import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Real DNF_Single MAP/TIL/DGN and loose IMG regression through the actual workbench. */
final class LegacyPreviewSmoke {
    private static final String MAP = "map/hendonmyre/hendon_pub_pvp.map", TILE = "map/hendonmyre/tile/bar00.til", DGN = "dungeon/temp.dgn";
    static void run(PvfStudioFrame frame, PvfWorkspace workspace, Path resources, Path output) throws Exception {
        var field = PvfStudioFrame.class.getDeclaredField("preview"); field.setAccessible(true);
        ResourcePreviewPanel panel = edt(() -> (ResourcePreviewPanel) field.get(frame));
        edt(() -> { panel.scan(resources); frame.openEntry(MAP); return null; });
        await(() -> ready(frame) && panel.renderedScene() != null);
        edt(() -> {
            var rendered = panel.renderedScene(); check(rendered.tileCount() == 6, "Legacy MAP tiles were not parsed");
            check(rendered.collisions().size() == 6, "Legacy TIL pass grids were not parsed");
            check(rendered.draws().size() >= 8, "Legacy map/background IMG pixels were not loaded");
            check(editor(tabs(frame).getSelectedComponent()).getDocument().getText().contains("[타일]"), "Default encoding corrupted Korean tags");
            screenshot(frame, output.resolve("legacy-map.png")); frame.openEntry(TILE); return null;
        });
        await(() -> ready(frame) && panel.renderedScene() != null && panel.renderedScene().tileCount() == 1);
        edt(() -> { check(panel.renderedScene().collisions().size() == 1, "Standalone legacy TIL collision is missing");
            screenshot(frame, output.resolve("legacy-tile.png")); frame.openEntry(DGN); return null; });
        await(() -> ready(frame) && panel.previewDetails().contains("副本布局 1×1") && panel.previewDetails().contains("关联"));
        edt(() -> {
            check(components(frame, DungeonPanel.class).getFirst().isVisible(), "Unbound DGN layout was hidden");
            check(panel.renderedScene() == null, "Template DGN invented a MAP association");
            screenshot(frame, output.resolve("legacy-dungeon.png")); return null;
        });
        // Restore a separate, closed-on-disk session using the former GBK default.
        Path migrationRoot = output.resolve("migration-workspaces");
        var first = edt(() -> { var f = new PvfStudioFrame(migrationRoot, false); f.setVisible(true); return f; });
        Path directory;
        try {
            await(() -> button(first, "打开最近的文件").isEnabled());
            edt(() -> { first.openArchive(workspace.archive().sourcePath()); return null; }); await(() -> ready(first));
            var workspaceField = PvfStudioFrame.class.getDeclaredField("workspace"); workspaceField.setAccessible(true);
            directory = edt(() -> ((PvfWorkspace) workspaceField.get(first)).directory());
        } finally { close(first); }
        new EditorSessionState(List.of(new EditorSessionState.Tab(MAP, "GBK", 5, 0, 0)), MAP).write(directory);
        var restored = edt(() -> { var f = new PvfStudioFrame(migrationRoot, false); f.setVisible(true); return f; });
        try {
            await(() -> button(restored, "打开最近的文件").isEnabled());
            edt(() -> { restored.openArchive(workspace.archive().sourcePath()); return null; });
            await(() -> ready(restored) && tabs(restored).getTabCount() == 1);
            edt(() -> {
                check(editor(tabs(restored).getSelectedComponent()).getDocument().getText().contains("[타일]"), "Restored GBK session was not repaired");
                check(tabs(restored).getToolTipTextAt(0).contains("949"), "Repaired encoding was not bound to the editor session"); return null;
            });
        } finally { close(restored); }
        Files.writeString(output.resolve("legacy-preview-result.txt"), "PASS: automatic MS949; six MAP tiles/backgrounds/entities; TIL pass grid; native 1x1 DGN glyph and unbound diagnosis; loose IMG; restored GBK session migration\n");
    }
    private static void close(PvfStudioFrame frame) throws Exception {
        edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING)); return null; });
        await(() -> !frame.isDisplayable());
    }
    private static EditorTabs tabs(PvfStudioFrame frame) { return components(frame, EditorTabs.class).getFirst(); }
    private static boolean ready(PvfStudioFrame frame) { return frame.isEnabled() && button(frame, "打开 PVF…").isEnabled(); }
}
