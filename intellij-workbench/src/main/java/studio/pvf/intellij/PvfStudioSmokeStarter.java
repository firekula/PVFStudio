package studio.pvf.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationStarter;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import studio.pvf.workspace.PvfWorkspace;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/** Exercises the actual initialized IntelliJ editor and draft/export integration. */
public final class PvfStudioSmokeStarter implements ApplicationStarter {
    @Override public boolean isHeadless() { return false; }

    @Override public void main(List<String> args) {
        if (args.size() != 3) {
            System.err.println("pvf-studio-smoke <archive> <new-output-directory>"); System.exit(2); return;
        }
        Path archive = Path.of(args.get(1)); Path output = Path.of(args.get(2));
        CompletableFuture.runAsync(() -> run(archive, output));
    }

    private static void run(Path archivePath, Path output) {
        try {
            Files.createDirectory(output);
            verifyNewlineHandling();
            Charset charset = Charset.forName("GBK");
            String entry = "creature/script/creature.lst";
            Path exported = output.resolve("IntelliJ-edited.pvf");
            Properties report = new Properties();
            try (PvfWorkspace workspace = PvfWorkspace.open(archivePath, output.resolve("drafts"))) {
                String raw = workspace.readText(entry, charset);
                PvfStudioFrame.Newlines newlines = new PvfStudioFrame.Newlines(raw);
                String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
                CompletableFuture<String> edit = new CompletableFuture<>();
                ApplicationManager.getApplication().invokeLater(() -> {
                    Editor editor = null;
                    try {
                        UndoManager manager = UndoManager.getGlobalInstance();
                        Project editorProject = ProjectManager.getInstance().getDefaultProject();
                        Document document = EditorFactory.getInstance().createDocument(normalized);
                        editor = EditorFactory.getInstance().createEditor(document, editorProject);
                        TextEditor fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
                        if (!(editor instanceof EditorEx ex)) throw new IllegalStateException("Expected IntelliJ EditorEx");
                        ex.setHighlighter(new LexerEditorHighlighter(new PvfSyntaxHighlighter(),
                                EditorColorsManager.getInstance().getGlobalScheme()));
                        report.setProperty("editorClass", editor.getClass().getName());
                        CommandProcessor.getInstance().executeCommand(editorProject,
                                () -> ApplicationManager.getApplication().runWriteAction(() -> document.insertString(
                                        document.getTextLength(), "\n// PVF Studio IntelliJ integration smoke\n")),
                                "PVF Studio editor integration smoke", null);
                        String expectedEdit = document.getText();
                        if (!manager.isUndoAvailable(fileEditor)) throw new AssertionError("Global editor undo was not recorded");
                        manager.undo(fileEditor);
                        if (!normalized.equals(document.getText())) throw new AssertionError("Editor undo did not restore source");
                        if (!manager.isRedoAvailable(fileEditor)) throw new AssertionError("Editor redo is unavailable");
                        manager.redo(fileEditor);
                        if (!expectedEdit.equals(document.getText())) throw new AssertionError("Editor redo did not restore edit");
                        report.setProperty("undoRedo", "PASS");
                        javax.swing.JDialog diffDialog = new javax.swing.JDialog((java.awt.Frame) null);
                        Disposable diffDisposable = Disposer.newDisposable("PVF Studio smoke diff");
                        try {
                            DiffRequestPanel panel = DiffManager.getInstance().createRequestPanel(null, diffDisposable, diffDialog);
                            panel.setRequest(new SimpleDiffRequest("PVF Studio smoke",
                                    DiffContentFactory.getInstance().create(normalized),
                                    DiffContentFactory.getInstance().create(expectedEdit), "Original", "Edited"));
                            diffDialog.setContentPane(panel.getComponent());
                            report.setProperty("diffPanel", "PASS");
                        } finally { Disposer.dispose(diffDisposable); diffDialog.dispose(); }
                        edit.complete(document.getText());
                    } catch (Throwable failure) { edit.completeExceptionally(failure); }
                    finally { if (editor != null) EditorFactory.getInstance().releaseEditor(editor); }
                });
                String edited = newlines.restore(edit.get());
                workspace.stageText(entry, edited, charset);
                workspace.export(exported);
                report.setProperty("status", "PASS");
                report.setProperty("archive", archivePath.toAbsolutePath().toString());
                report.setProperty("sourceSha256", workspace.archive().sha256());
                report.setProperty("entryCount", Integer.toString(workspace.archive().fileCount()));
                report.setProperty("modifiedEntry", entry);
                report.setProperty("export", exported.toAbsolutePath().toString());
                report.setProperty("lineEndings", newlines.description());
                report.setProperty("lineEndingChecks", "PASS");
            }
            try (OutputStream stream = Files.newOutputStream(output.resolve("report.properties"))) {
                report.store(stream, "Actual IntelliJ EditorFactory + persistent draft + PVF export smoke");
            }
            System.out.println("PVF_STUDIO_UI_SMOKE_OK " + exported.toAbsolutePath());
            // This automation changed no IDE preferences. Avoid starting a modal settings-save
            // dialog during shutdown of a command that has no IDE project/frame.
            ApplicationManager.getApplication().invokeLater(() ->
                    ((ApplicationEx) ApplicationManager.getApplication()).exit(ApplicationEx.EXIT_CONFIRMED));
        } catch (Throwable failure) {
            failure.printStackTrace(); System.exit(1);
        }
    }

    private static void verifyNewlineHandling() {
        for (String ending : List.of("\r\n", "\n", "\r")) {
            PvfStudioFrame.Newlines lines = new PvfStudioFrame.Newlines("a" + ending + "b" + ending);
            if (lines.isMixed() || !lines.restore("a\nx\nb\n").equals("a" + ending + "x" + ending + "b" + ending)
                    || !lines.restore("a\nb").equals("a" + ending + "b"))
                throw new AssertionError("Editor newline/final-newline preservation failed");
        }
        if (!new PvfStudioFrame.Newlines("a\r\nb\nc\r").isMixed())
            throw new AssertionError("Mixed newline files must be opened read-only");
    }
}
