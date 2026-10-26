package studio.pvf.intellij;

import studio.pvf.workspace.PvfWorkspace;
import javax.swing.*;
import java.nio.file.*;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Real 86 projected DGN strings, layout controls and room resources through the actual editor. */
final class Dungeon86WorkbenchSmoke {
    static void run(Path archive, Path output) throws Exception {
        var frame = edt(() -> { var f = new PvfStudioFrame(output.resolve("workspaces"), false); f.setSize(1550, 1150); f.setVisible(true); return f; });
        List<String> report = new ArrayList<>();
        try {
            await(() -> button(frame, "打开最近的文件").isEnabled());
            edt(() -> { frame.openArchive(archive); return null; }); await(() -> ready(frame));
            var field = PvfStudioFrame.class.getDeclaredField("preview"); field.setAccessible(true);
            var workspaceField = PvfStudioFrame.class.getDeclaredField("workspace"); workspaceField.setAccessible(true);
            var panel = edt(() -> (ResourcePreviewPanel) field.get(frame));
            var workspace = edt(() -> (PvfWorkspace) workspaceField.get(frame));
            var controls = edt(() -> components(frame, DungeonPanel.class).getFirst());
            for (String path : List.of("dungeon/act9/fruz_station.dgn", "dungeon/act9/kohlepowerstation.dgn", "dungeon/act9/grandine.dgn",
                    "dungeon/anton_awakening/phase2/blackvolcano.dgn", "dungeon/anton_raid/phase2/blackvolcano.dgn")) {
                edt(() -> { frame.openEntry(path); return null; });
                await(() -> ready(frame) && controls.isVisible() && panel.renderedScene() != null && panel.previewDetails().contains(path));
                edt(() -> {
                    var selection = controls.selection();
                    check(selection != null && selection.map() != null && !panel.renderedScene().draws().isEmpty(), "DGN room did not render: " + path);
                    String text = editor(components(frame, EditorTabs.class).getFirst().getSelectedComponent()).getDocument().getText();
                    check(text.contains("\\r\\n"), "Fixture does not exercise projected line escapes");
                    if (path.endsWith("fruz_station.dgn")) {
                        check(selection.layout().rooms().size() == 15 && selection.layout().warnings().isEmpty(), "Fruz layout or candidate resolution failed");
                        check(selection.layout().rooms().stream().allMatch(r -> r.map() != null), "Fruz room candidate missing");
                    }
                    report.add(path + " layouts=" + selection.dungeon().definition().mazes().size() + " rooms=" + selection.layout().rooms().size()
                            + " map=" + selection.map().path() + " draws=" + panel.renderedScene().draws().size());
                    screenshot(frame, output.resolve(path.replace('/', '-') + "-start.png"));
                    return null;
                });
                var selection = edt(controls::selection);
                if (selection.layout().boss() != null) {
                    edt(() -> { controls.selectRoom(selection.layout().boss()); return null; });
                    await(() -> panel.renderedScene() != null && controls.selection().room().boss()
                            && panel.previewDetails().contains(controls.selection().map().path()));
                    edt(() -> { screenshot(frame, output.resolve(path.replace('/', '-') + "-boss.png")); return null; });
                }
                edt(() -> { checkbox(frame, "房间已清理").doClick(); return null; });
                await(() -> panel.renderedScene() != null && !controls.selection().cleared());
                edt(() -> { checkbox(frame, "房间已清理").doClick(); return null; });
                await(() -> panel.renderedScene() != null && controls.selection().cleared());
                if (path.endsWith("fruz_station.dgn")) {
                    edt(() -> { button(panel, "▶").doClick(); return null; });
                    await(() -> panel.renderedScene() != null && panel.renderedScene().milliseconds() >= 16);
                    edt(() -> { button(panel, "播放").doClick(); return null; });
                    await(() -> panel.renderedScene() != null && panel.renderedScene().milliseconds() > 50);
                    edt(() -> { button(panel, "暂停").doClick(); return null; });
                }
                if (path.endsWith("grandine.dgn")) {
                    edt(() -> { components(frame, JComboBox.class).stream().filter(c -> "dungeon-maze".equals(c.getName())).findFirst().orElseThrow().setSelectedIndex(1); return null; });
                    await(() -> panel.renderedScene() != null && controls.selection().layout().maze().width() == 6 && controls.selection().map() != null);
                    edt(() -> { screenshot(frame, output.resolve("grandine-layout-2.png")); return null; });
                }
            }
            check(workspace.dirtyPaths().isEmpty(), "Preview changed source drafts");
            Files.write(output.resolve("dungeon-86-scenes.txt"), report);
            Files.writeString(output.resolve("dungeon-86-result.txt"), "PASS: five real 86 DGNs, projected CRLF greed, layouts and MAP candidates, normal/Boss rooms, gate toggles, scene playback/step and alternate maze; no draft changes\n");
        } finally {
            edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; });
            await(() -> !frame.isDisplayable());
        }
    }
    private static JCheckBox checkbox(PvfStudioFrame frame, String text) { return components(frame, JCheckBox.class).stream().filter(c -> text.equals(c.getText())).findFirst().orElseThrow(); }
    private static boolean ready(PvfStudioFrame frame) { return frame.isEnabled() && button(frame, "打开 PVF…").isEnabled(); }
}
