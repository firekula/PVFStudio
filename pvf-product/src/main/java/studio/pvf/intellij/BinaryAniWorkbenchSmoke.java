package studio.pvf.intellij;

import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.workspace.BinaryAniCodec;
import studio.pvf.core.PvfArchive;
import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Real binary ANI editor, playback, byte-preserving save and export against the 86 sample. */
final class BinaryAniWorkbenchSmoke {
    static void run(Path archive, Path output) throws Exception {
        String root = "map/246_energycut/animation/", target = root + "back_steam.ani";
        var frame = edt(() -> { var f = new PvfStudioFrame(output.resolve("workspaces"), false); f.setSize(1450, 850); f.setVisible(true); return f; });
        try {
            await(() -> button(frame, "打开最近的文件").isEnabled());
            edt(() -> { frame.openArchive(archive); return null; }); await(() -> ready(frame));
            var workspaceField = PvfStudioFrame.class.getDeclaredField("workspace"); workspaceField.setAccessible(true);
            var previewField = PvfStudioFrame.class.getDeclaredField("preview"); previewField.setAccessible(true);
            PvfWorkspace workspace = edt(() -> (PvfWorkspace) workspaceField.get(frame));
            ResourcePreviewPanel panel = edt(() -> (ResourcePreviewPanel) previewField.get(frame));
            Charset charset = Charset.forName("Big5"); byte[] originalBytes = workspace.archive().readBytes(target);
            String original = workspace.readText(target, charset);
            check(original.contains("[FRAME MAX]\n\t9"), "Binary ANI did not decode its nine frames");
            edt(() -> { frame.openEntry(target); return null; }); await(() -> ready(frame) && panel.hasPreparedFrame() && panel.frameCount() == 9);
            edt(() -> {
                check(!editor(tabs(frame).getSelectedComponent()).isViewer(), "Binary ANI projection stayed read-only");
                check(editor(tabs(frame).getSelectedComponent()).getDocument().getText().contains("[IMAGE POS]"), "Editor still shows hexadecimal bytes");
                screenshot(frame, output.resolve("binary-ani-back-steam.png")); button(panel, "▶").doClick(); return null;
            });
            await(() -> panel.hasPreparedFrame() && panel.previewDetails().contains(" #1"));
            edt(() -> { button(panel, "播放").doClick(); return null; });
            await(() -> panel.hasPreparedFrame() && !panel.previewDetails().contains(" #1"));
            edt(() -> { button(panel, "暂停").doClick(); return null; });
            check(Arrays.equals(originalBytes, BinaryAniCodec.encode(original, originalBytes, charset)), "Real ANI no-op save changed bytes");
            String changed = original.replace("\t150\n", "\t175\n"); check(!changed.equals(original), "No original delay found");
            edt(() -> {
                var editor = editor(tabs(frame).getSelectedComponent());
                com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(editor.getProject(),
                        () -> com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> editor.getDocument().setText(changed)),
                        "ANI delay edit", null);
                button(frame, "保存草稿").doClick(); return null;
            }); await(() -> ready(frame));
            check(workspace.readText(target, charset).contains("\t175\n"), "UI save did not compile ANI");
            check(Arrays.equals(originalBytes, workspace.archive().readBytes(target)), "ANI source entry changed");
            edt(() -> { button(frame, "查看差异").doClick(); return null; });
            await(() -> Arrays.stream(frame.getOwnedWindows()).anyMatch(w -> w instanceof JDialog d && d.isShowing() && d.getTitle().startsWith("差异")));
            edt(() -> { for (var w : frame.getOwnedWindows()) if (w instanceof JDialog d && d.getTitle().startsWith("差异")) d.dispose(); return null; });
            for (String name : new String[]{"boil_1", "bones_1"}) {
                int count = name.equals("boil_1") ? 5 : 1;
                edt(() -> { frame.openEntry(root + name + ".ani"); return null; });
                await(() -> ready(frame) && panel.hasPreparedFrame() && panel.frameCount() == count);
                edt(() -> { screenshot(frame, output.resolve("binary-ani-" + name + ".png")); return null; });
            }
            Path exported = output.resolve("ani-edited.pvf"); workspace.export(exported);
            byte[] staged = workspace.readBytes(target);
            try (PvfArchive reopened = PvfArchive.open(exported)) {
                check(Arrays.equals(staged, reopened.readBytes(target)), "Export did not retain compiled ANI bytes");
                check(BinaryAniCodec.decode(reopened.readBytes(target), charset).contains("\t175\n"), "Exported ANI delay did not reopen");
            }
            Files.write(output.resolve("original.ani"), originalBytes); Files.write(output.resolve("edited.ani"), staged);
            Files.writeString(output.resolve("binary-ani-result.txt"), "PASS: nine-frame binary ANI text/editor/playback/step; no-op byte identity; UI delay edit, diff, persisted draft, export/reopen; boil_1 and bones_1 IMG previews\n");
        } finally {
            edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING)); return null; });
            await(() -> !frame.isDisplayable());
        }
    }
    private static EditorTabs tabs(PvfStudioFrame frame) { return components(frame, EditorTabs.class).getFirst(); }
    private static boolean ready(PvfStudioFrame frame) { return frame.isEnabled() && button(frame, "打开 PVF…").isEnabled(); }
}
