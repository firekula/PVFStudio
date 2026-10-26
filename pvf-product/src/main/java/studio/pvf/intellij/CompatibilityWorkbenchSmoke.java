package studio.pvf.intellij;

import studio.pvf.core.PvfFormat;
import studio.pvf.workspace.PvfWorkspace;
import javax.swing.*;
import java.awt.event.WindowEvent;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Actual workbench integration for non-ACT1 samples, using isolated drafts and preferences. */
final class CompatibilityWorkbenchSmoke {
    static void run(Path archive, Path output, PvfFormat format) throws Exception {
        boolean uuid = format == PvfFormat.UUID;
        Charset charset = Charset.forName(uuid ? "Big5" : "MS949");
        var frame = edt(() -> { var f = new PvfStudioFrame(output.resolve("workspaces"), false); f.setVisible(true); return f; });
        try {
            await(() -> button(frame, "打开最近的文件").isEnabled());
            edt(() -> { frame.openArchive(archive); return null; });
            await(() -> ready(frame));
            var field = PvfStudioFrame.class.getDeclaredField("workspace"); field.setAccessible(true);
            PvfWorkspace workspace = edt(() -> (PvfWorkspace) field.get(frame));
            String path = uuid ? workspace.archive().entries().stream().filter(e -> e.path().endsWith(".equ")).findFirst().orElseThrow().path()
                    : "character/character.lst";
            String original = workspace.readText(path, charset);
            edt(() -> { frame.openEntry(path); return null; });
            await(() -> ready(frame) && tabs(frame).getTabCount() == 1);
            String marker = "PVF_STUDIO_COMPAT_UI_20261026";
            String changed;
            if (uuid) {
                int start = original.indexOf('`'), end = original.indexOf('`', start + 1);
                changed = original.substring(0, start + 1) + marker + original.substring(end);
            } else changed = original + "\r\n// " + marker + "\r\n";
            String normalized = changed.replace("\r\n", "\n").replace('\r', '\n');
            edt(() -> {
                var editor = editor(tabs(frame).getSelectedComponent()); check(!editor.isViewer(), "Script opened read-only");
                com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(editor.getProject(),
                    () -> com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> editor.getDocument().setText(normalized)),
                    "Compatibility edit", null);
                button(frame, "保存草稿").doClick(); return null;
            });
            await(() -> ready(frame));
            check(workspace.readText(path, charset).contains(marker), "Editor save did not compile/persist script");
            edt(() -> { screenshot(frame, output.resolve("compatibility-editor.png")); action(frame, "global-search"); return null; });
            var dialog = edt(() -> java.util.Arrays.stream(frame.getOwnedWindows()).filter(w -> w instanceof PvfSearchDialog && w.isShowing())
                    .map(w -> (PvfSearchDialog) w).findFirst().orElseThrow());
            edt(() -> {
                var fields = components(dialog, JTextField.class); fields.get(1).setText(path);
                fields.getFirst().setText(marker); fields.getFirst().postActionEvent(); return null;
            });
            await(() -> components(dialog, JList.class).getFirst().getModel().getSize() == 1);
            edt(() -> { screenshot(dialog, output.resolve("compatibility-search.png")); dialog.dispose(); return null; });
            Path exported = output.resolve("ui-edited.pvf"); workspace.export(exported);
            try (PvfWorkspace reopened = PvfWorkspace.open(exported, output.resolve("reopened"))) {
                check(reopened.readText(path, charset).contains(marker), "Exported script did not reopen");
                check(reopened.archive().format() == format, "Container identity changed");
            }
            if (!uuid && System.getProperty("pvf.preview.npk") != null)
                LegacyPreviewSmoke.run(frame, workspace, Path.of(System.getProperty("pvf.preview.npk")), output);
            if (uuid) {
                String binary = workspace.archive().entries().stream().filter(e -> e.path().endsWith(".ani")).findFirst().orElseThrow().path();
                edt(() -> { frame.openEntry(binary); return null; }); await(() -> ready(frame) && tabs(frame).getTabCount() == 2);
                edt(() -> { check(editor(tabs(frame).getSelectedComponent()).isViewer(), "Binary resource must remain read-only");
                    screenshot(frame, output.resolve("compatibility-binary.png")); return null; });
            }
            Files.writeString(output.resolve("compatibility-report.json"), "{\"status\":\"PASS\",\"format\":\"" + format
                    + "\",\"entries\":" + workspace.archive().fileCount() + ",\"editorSaveSearchExportReopen\":true}");
        } finally {
            edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; });
            await(() -> !frame.isDisplayable());
        }
    }
    private static EditorTabs tabs(PvfStudioFrame frame) { return components(frame, EditorTabs.class).getFirst(); }
    private static boolean ready(PvfStudioFrame frame) { return frame.isEnabled() && button(frame, "打开 PVF…").isEnabled(); }
}
