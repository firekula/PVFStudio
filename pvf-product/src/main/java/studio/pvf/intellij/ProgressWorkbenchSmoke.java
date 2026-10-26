package studio.pvf.intellij;

import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.preview.NpkResources;
import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Observes real large-file progress, UI responsiveness and cleanup without synthetic delays. */
final class ProgressWorkbenchSmoke {
    static void run(Path archive, Path output) throws Exception {
        var frame = edt(() -> { var f = new PvfStudioFrame(output.resolve("workspaces"), false); f.setSize(1450, 850); f.setVisible(true); return f; });
        try {
            await(() -> button(frame, "打开最近的文件").isEnabled());
            TaskProgress main = edt(() -> find(frame, "pvf-task-progress"));
            edt(() -> { frame.openArchive(archive); return null; });
            boolean[] mainNumeric = {false}; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            while (!edt(() -> ready(frame))) {
                edt(() -> {
                    if (main.isVisible() && !main.bar().isIndeterminate() && main.bar().getValue() > 0 && main.bar().getValue() < 1000) {
                        if (!mainNumeric[0]) screenshot(frame, output.resolve("loading-pvf.png")); mainNumeric[0] = true;
                    }
                    return null;
                });
                if (System.nanoTime() > deadline) throw new AssertionError("PVF progress load timed out"); Thread.sleep(40);
            }
            var field = PvfStudioFrame.class.getDeclaredField("workspace"); field.setAccessible(true);
            PvfWorkspace workspace = edt(() -> (PvfWorkspace) field.get(frame));
            check(workspace != null && workspace.archive().fileCount() > 0, "PVF mount did not complete");
            check(edt(() -> !main.running() && !main.isVisible()), "PVF progress did not clear on success");
            if (Files.size(archive) > 128L * 1024 * 1024) check(mainNumeric[0], "Large PVF did not show measurable progress");
            var previewField = PvfStudioFrame.class.getDeclaredField("preview"); previewField.setAccessible(true);
            var panel = edt(() -> (ResourcePreviewPanel) previewField.get(frame));
            var resourcesField = ResourcePreviewPanel.class.getDeclaredField("resources"); resourcesField.setAccessible(true);
            TaskProgress index = edt(() -> find(panel, "resource-index-progress"));
            boolean[] indexNumeric = {false};
            String npk = System.getProperty("pvf.preview.npk");
            if (npk != null) {
                // Normal opening auto-mounts ImagePacks2. Only fall back to the supplied directory if no auto scan starts.
                long discovery = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (edt(() -> !index.running() && resourcesField.get(panel) == null) && System.nanoTime() < discovery) Thread.sleep(40);
                if (edt(() -> !index.running() && resourcesField.get(panel) == null)) edt(() -> { panel.scan(Path.of(npk)); return null; });
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
                while (edt(() -> index.running() || resourcesField.get(panel) == null)) {
                    edt(() -> {
                        if (index.isVisible() && !index.bar().isIndeterminate() && index.bar().getValue() > 0 && index.bar().getValue() < 1000) {
                            if (!indexNumeric[0]) screenshot(frame, output.resolve("loading-npk.png")); indexNumeric[0] = true;
                        }
                        return null;
                    });
                    if (System.nanoTime() > deadline) throw new AssertionError("Resource progress load timed out: " + edt(panel::previewDetails));
                    Thread.sleep(40);
                }
            }
            NpkResources resources = edt(() -> (NpkResources) resourcesField.get(panel));
            if (resources != null && resources.packCount() > 100) check(indexNumeric[0], "Large resource index did not show measurable progress");
            edt(() -> { check(!index.isVisible(), "Index progress did not clear on success"); screenshot(frame, output.resolve("loading-complete.png")); return null; });
            if (resources != null) {
                Path empty = Files.createDirectory(output.resolve("empty-resources"));
                edt(() -> { panel.scan(empty); return null; });
                await(() -> !index.running() && button(panel, "选择资源目录").isEnabled());
                check(edt(() -> !index.isVisible() && resourcesField.get(panel) == resources), "Failed index did not clear progress/preserve existing resources");
            }
            Properties report = new Properties(); report.setProperty("status", "PASS");
            report.setProperty("entries", Integer.toString(workspace.archive().fileCount()));
            report.setProperty("packs", Integer.toString(resources == null ? 0 : resources.packCount()));
            report.setProperty("images", Integer.toString(resources == null ? 0 : resources.imageCount()));
            report.setProperty("pvfNumericProgress", Boolean.toString(mainNumeric[0])); report.setProperty("npkNumericProgress", Boolean.toString(indexNumeric[0]));
            report.setProperty("successAndFailureCleanup", "PASS");
            try (var stream = Files.newOutputStream(output.resolve("progress-report.properties"))) { report.store(stream, "Actual loading progress smoke"); }
        } finally {
            edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING)); return null; });
            await(() -> !frame.isDisplayable());
        }
    }
    private static TaskProgress find(java.awt.Container root, String name) { return components(root, TaskProgress.class).stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow(); }
    private static boolean ready(PvfStudioFrame frame) { return frame.isEnabled() && button(frame, "打开 PVF…").isEnabled(); }
}
