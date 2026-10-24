package studio.pvf.intellij;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import studio.pvf.core.PvfEntry;
import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.workspace.TextCodec;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Small standalone workbench; archive I/O never runs on the Swing event thread. */
final class PvfStudioFrame extends JFrame {
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pvf-studio-io"); t.setDaemon(true); return t;
    });
    private final JButton open = new JButton("打开 PVF");
    private final JButton save = new JButton("保存草稿  Ctrl+S");
    private final JButton diff = new JButton("查看差异");
    private final JButton closeTab = new JButton("关闭标签");
    private final JButton export = new JButton("导出新 PVF");
    private final JComboBox<String> encoding = new JComboBox<>(new String[]{"GBK / CP936", "MS949 / CP949", "UTF-8"});
    private final JTextField pathSearch = new JTextField();
    private final JTree tree = new JTree(new DefaultMutableTreeNode("请打开 Script.pvf"));
    private final DefaultListModel<String> searchResults = new DefaultListModel<>();
    private final JList<String> resultList = new JList<>(searchResults);
    private final JPanel navigation = new JPanel(new CardLayout());
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel status = new JLabel("打开 PVF 后可浏览、编辑并导出新包。Ctrl+S 保存到独立草稿工作区。");
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private final Map<JDialog, Disposable> diffDialogs = new LinkedHashMap<>();
    private PvfWorkspace workspace;
    private Map<String, List<Item>> directories = Map.of();
    private boolean busy;
    private long searchVersion;

    PvfStudioFrame() {
        super("PVF Studio");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(open); toolbar.add(save); toolbar.add(diff); toolbar.add(closeTab); toolbar.add(export);
        toolbar.add(new JLabel("打开编码：")); toolbar.add(encoding);
        JPanel left = new JPanel(new BorderLayout(0, 6));
        pathSearch.setToolTipText("按路径搜索；清空后显示目录树");
        JPanel search = new JPanel(new BorderLayout(5, 0));
        search.add(new JLabel("路径"), BorderLayout.WEST); search.add(pathSearch);
        left.add(search, BorderLayout.NORTH);
        navigation.add(new JScrollPane(tree), "tree");
        navigation.add(new JScrollPane(resultList), "results");
        left.add(navigation);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, tabs);
        split.setDividerLocation(350); split.setResizeWeight(0.25);
        add(toolbar, BorderLayout.NORTH); add(split); add(status, BorderLayout.SOUTH);
        tree.setRootVisible(true);
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            public void treeWillExpand(TreeExpansionEvent event) {
                loadChildren((DefaultMutableTreeNode) event.getPath().getLastPathComponent());
            }
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) openSelectedTreeEntry();
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pvf-open");
        tree.getActionMap().put("pvf-open", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { openSelectedTreeEntry(); }
        });
        resultList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) openEntry(resultList.getSelectedValue());
            }
        });
        resultList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pvf-open");
        resultList.getActionMap().put("pvf-open", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { openEntry(resultList.getSelectedValue()); }
        });
        javax.swing.Timer searchTimer = new javax.swing.Timer(200, event -> updatePathSearch());
        searchTimer.setRepeats(false);
        pathSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { searchTimer.restart(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { searchTimer.restart(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { searchTimer.restart(); }
        });
        open.addActionListener(e -> chooseArchive());
        save.addActionListener(e -> saveDrafts(null));
        diff.addActionListener(e -> showDiff());
        closeTab.addActionListener(e -> closeSelectedTab());
        export.addActionListener(e -> chooseExport());
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save-drafts");
        getRootPane().getActionMap().put("save-drafts", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { saveDrafts(null); }
        });
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { requestClose(); }
        });
        tabs.addChangeListener(e -> updateButtons());
        updateButtons();
    }

    private Charset selectedCharset() {
        return switch (encoding.getSelectedIndex()) {
            case 1 -> Charset.forName("MS949");
            case 2 -> StandardCharsets.UTF_8;
            default -> Charset.forName("GBK");
        };
    }

    private void chooseArchive() {
        if (busy) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("打开 PVF（原包保持只读）");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            openArchive(chooser.getSelectedFile().toPath());
    }

    void openArchive(Path archive) {
        if (busy) return;
        if (workspace != null && archive.toAbsolutePath().normalize().equals(workspace.archive().sourcePath())) {
            PvfWorkspace current = workspace;
            runTask("检查当前 PVF 是否变化…", () -> {
                try { current.archive().verifyUnchanged(); return true; }
                catch (IOException changed) { return false; }
            }, unchanged -> {
                if (unchanged) { status.setText("该 PVF 已打开且内容未变：" + archive.toAbsolutePath()); toFront(); }
                else reopenArchive(archive);
            });
            return;
        }
        reopenArchive(archive);
    }

    private void reopenArchive(Path archive) {
        if (hasUnsavedEditors()) {
            handleUnsaved(() -> doOpenArchive(archive));
        } else doOpenArchive(archive);
    }

    private void doOpenArchive(Path archive) {
        PvfWorkspace previous = workspace;
        runTask("读取 PVF 目录及草稿…", () -> {
            Path directory = workspaceDirectory(archive);
            // A timestamp-only change or path alias can retain exactly the same draft identity.
            // Release the old workspace on the UI side before trying to reacquire its lock.
            if (previous != null && previous.directory().equals(directory))
                return new Loaded(null, Map.of(), true);
            PvfWorkspace loaded = PvfWorkspace.open(archive, directory);
            try {
                return new Loaded(loaded, buildDirectories(loaded.archive().entries()), false);
            } catch (Throwable failure) { loaded.close(); throw failure; }
        }, loaded -> {
            if (loaded.reopenSameWorkspace()) {
                releaseEditors(); workspace = null; directories = Map.of();
                tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("重新加载 PVF…")));
                updateButtons();
                try { previous.close(); }
                catch (IOException failure) { showError(failure); return; }
                doOpenArchive(archive); return;
            }
            releaseEditors();
            workspace = loaded.workspace(); directories = loaded.directories();
            if (previous != null) {
                try { previous.close(); } catch (IOException e) { showError(e); }
            }
            DefaultMutableTreeNode root = node(new Item(archive.getFileName().toString(), "", true));
            tree.setModel(new DefaultTreeModel(root)); loadChildren(root); tree.expandRow(0);
            pathSearch.setText("");
            setTitle("PVF Studio — " + archive.toAbsolutePath());
            status.setText("已读取 " + workspace.archive().fileCount() + " 个文件；已保存草稿 "
                    + workspace.dirtyPaths().size() + " 个。打开编码由工具栏选择，不自动猜测。");
            updateButtons();
        });
    }

    private static Path workspaceDirectory(Path archive) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(archive.toRealPath().toString().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        try (java.io.InputStream stream = Files.newInputStream(archive)) {
            byte[] buffer = new byte[1024 * 1024]; int count;
            while ((count = stream.read(buffer)) >= 0) if (count > 0) digest.update(buffer, 0, count);
        }
        byte[] hash = digest.digest();
        return Path.of(System.getProperty("user.home"), ".pvf-studio", "workspaces",
                HexFormat.of().formatHex(hash, 0, 16));
    }

    private static Map<String, List<Item>> buildDirectories(List<PvfEntry> entries) {
        Map<String, Map<String, Item>> collected = new HashMap<>();
        collected.put("", new HashMap<>());
        for (PvfEntry entry : entries) {
            String[] parts = entry.path().split("/"); String parent = "";
            for (int i = 0; i < parts.length; i++) {
                String path = parent.isEmpty() ? parts[i] : parent + "/" + parts[i];
                boolean directory = i < parts.length - 1;
                collected.computeIfAbsent(parent, unused -> new HashMap<>())
                        .putIfAbsent(parts[i], new Item(parts[i], path, directory));
                if (directory) collected.computeIfAbsent(path, unused -> new HashMap<>());
                parent = path;
            }
        }
        Map<String, List<Item>> result = new HashMap<>();
        collected.forEach((path, children) -> result.put(path, children.values().stream()
                .sorted(Comparator.comparing(Item::directory).reversed().thenComparing(Item::label)).toList()));
        return result;
    }

    private DefaultMutableTreeNode node(Item item) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(item);
        if (item.directory()) node.add(new DefaultMutableTreeNode("加载中…"));
        return node;
    }

    private void loadChildren(DefaultMutableTreeNode node) {
        if (!(node.getUserObject() instanceof Item item) || !item.directory()) return;
        if (node.getChildCount() != 1 || node.getChildAt(0) instanceof DefaultMutableTreeNode child
                && child.getUserObject() instanceof Item) return;
        node.removeAllChildren();
        for (Item child : directories.getOrDefault(item.path(), List.of())) node.add(node(child));
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
    }

    private void openSelectedTreeEntry() {
        if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof Item item && !item.directory()) openEntry(item.path());
    }

    private void updatePathSearch() {
        String query = pathSearch.getText().toLowerCase(Locale.ROOT).trim();
        long version = ++searchVersion;
        CardLayout layout = (CardLayout) navigation.getLayout();
        if (query.isEmpty()) { layout.show(navigation, "tree"); return; }
        if (workspace == null || busy) return;
        PvfWorkspace current = workspace;
        // Metadata-only search: do not create documents or decrypt payloads.
        io.execute(() -> {
            List<String> matches = current.archive().entries().stream().map(PvfEntry::path)
                    .filter(path -> path.contains(query)).sorted().toList();
            ApplicationManager.getApplication().invokeLater(() -> {
                if (version != searchVersion || workspace != current) return;
                searchResults.clear(); searchResults.addAll(matches);
                layout.show(navigation, "results");
                status.setText("路径搜索：" + matches.size() + " 个结果");
            });
        });
    }

    private void openEntry(String path) {
        if (path == null || workspace == null || busy) return;
        Session existing = sessions.get(path);
        if (existing != null) { tabs.setSelectedComponent(existing.editor.getComponent()); return; }
        Charset charset = selectedCharset(); PvfWorkspace current = workspace;
        runTask("读取 " + path + "…", () -> {
            String raw = current.readText(path, charset);
            if (raw.indexOf('\0') >= 0) throw new IOException("内容包含 NUL，当前原型只支持文本编辑：" + path);
            return raw;
        }, raw -> {
            String normalized = normalizeNewlines(raw);
            // The global undo provider must subscribe before the first edit of an unbound document.
            UndoManager.getGlobalInstance();
            Project editorProject = ProjectManager.getInstance().getDefaultProject();
            Document document = EditorFactory.getInstance().createDocument(normalized);
            Newlines lineEndings = new Newlines(raw);
            Editor editor = lineEndings.isMixed() ? EditorFactory.getInstance().createViewer(document, editorProject)
                    : EditorFactory.getInstance().createEditor(document, editorProject);
            editor.getSettings().setLineNumbersShown(true);
            editor.getSettings().setWhitespacesShown(false);
            if (editor instanceof EditorEx ex) ex.setHighlighter(new LexerEditorHighlighter(
                    new PvfSyntaxHighlighter(), EditorColorsManager.getInstance().getGlobalScheme()));
            Session session = new Session(path, charset, raw, editor);
            session.fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
            session.saveAction = new DumbAwareAction() {
                @Override public void actionPerformed(AnActionEvent event) { saveDrafts(null); }
            };
            session.saveAction.registerCustomShortcutSet(new CustomShortcutSet(
                    KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)), editor.getComponent());
            session.undoAction = createUndoAction(session, false);
            session.undoAction.registerCustomShortcutSet(new CustomShortcutSet(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)), editor.getComponent());
            session.redoAction = createUndoAction(session, true);
            session.redoAction.registerCustomShortcutSet(new CustomShortcutSet(
                    new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), null),
                    new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), null)),
                    editor.getComponent());
            sessions.put(path, session);
            document.addDocumentListener(new DocumentListener() {
                public void documentChanged(DocumentEvent event) { updateSessionTitle(session); }
            });
            tabs.addTab(shortName(path), editor.getComponent());
            tabs.setToolTipTextAt(tabs.indexOfComponent(editor.getComponent()), path + " [" + charset.name() + "]");
            tabs.setSelectedComponent(editor.getComponent());
            status.setText(path + " | " + charset.name() + " | " + session.newlines.description()
                    + (lineEndings.isMixed() ? " | 混合换行暂只读，避免隐式改写行尾" : " | Ctrl+S 保存草稿；导出生成新包"));
            updateButtons();
        });
    }

    private void saveDrafts(Runnable after) {
        if (workspace == null || busy) return;
        List<Snapshot> snapshots = snapshots(); PvfWorkspace current = workspace;
        runTask("保存草稿…", () -> {
            persistSnapshots(current, snapshots); return snapshots;
        }, saved -> {
            markSaved(saved);
            status.setText("草稿已保存；相对原包共有 " + workspace.dirtyPaths().size() + " 个修改文件。");
            if (after != null) after.run();
        });
    }

    private List<Snapshot> snapshots() {
        List<Snapshot> result = new ArrayList<>();
        for (Session session : sessions.values()) {
            String text = session.editor.getDocument().getText();
            if (!text.equals(session.persistedText))
                result.add(new Snapshot(session, text, session.newlines.restore(text),
                        session.editor.getDocument().getModificationStamp()));
            else {
                session.persistedStamp = session.editor.getDocument().getModificationStamp();
                updateSessionTitle(session);
            }
        }
        return result;
    }

    private static void persistSnapshots(PvfWorkspace current, List<Snapshot> snapshots) throws IOException {
        for (Snapshot snapshot : snapshots)
            current.stageText(snapshot.session.path, snapshot.encodedText, snapshot.session.charset);
    }

    private void markSaved(List<Snapshot> saved) {
        for (Snapshot snapshot : saved) {
            snapshot.session.persistedText = snapshot.editorText;
            snapshot.session.persistedStamp = snapshot.stamp;
            updateSessionTitle(snapshot.session);
        }
    }

    private void chooseExport() {
        if (workspace == null || busy) return;
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("导出到新 PVF 文件（不覆盖既有文件）");
        chooser.setSelectedFile(new java.io.File("Script-edited.pvf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        List<Snapshot> snapshots = snapshots(); PvfWorkspace current = workspace;
        runTask("保存草稿并导出、校验 PVF…", () -> {
            persistSnapshots(current, snapshots); current.export(target); return snapshots;
        }, saved -> {
            markSaved(saved);
            status.setText("导出并回读校验完成：" + target.toAbsolutePath());
            JOptionPane.showMessageDialog(this, "已生成新 PVF：\n" + target.toAbsolutePath()
                    + "\n\n原包未被修改。客户端加载兼容性仍需在测试副本中验证。", "导出完成", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void showDiff() {
        Session session = selectedSession(); if (session == null || busy) return;
        // Compare the mounted archive with this editor, not merely the last saved draft.
        runTask("读取原始内容…", () -> {
            byte[] bytes = workspace.archive().readBytes(session.path);
            return TextCodec.decode(bytes, session.charset);
        }, original -> showDiffDialog(new SimpleDiffRequest(session.path,
                DiffContentFactory.getInstance().create(normalizeNewlines(original)),
                DiffContentFactory.getInstance().create(session.editor.getDocument().getText()),
                "原始 PVF", "当前编辑"), session.path));
    }

    private void showDiffDialog(SimpleDiffRequest request, String path) {
        JDialog dialog = new JDialog(this, "差异 — " + path, false);
        Disposable disposable = Disposer.newDisposable("PVF Studio diff: " + path);
        try {
            DiffRequestPanel panel = DiffManager.getInstance().createRequestPanel(null, disposable, dialog);
            panel.setRequest(request);
            dialog.setContentPane(panel.getComponent());
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setSize(1100, 720); dialog.setLocationRelativeTo(this);
            diffDialogs.put(dialog, disposable);
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) { disposeDiff(dialog); }
            });
            dialog.setVisible(true);
        } catch (Throwable failure) {
            dialog.dispose();
            if (!Disposer.isDisposed(disposable)) Disposer.dispose(disposable);
            throw failure;
        }
    }

    private void disposeDiff(JDialog dialog) {
        Disposable disposable = diffDialogs.remove(dialog);
        if (disposable != null && !Disposer.isDisposed(disposable)) Disposer.dispose(disposable);
    }

    private AnAction createUndoAction(Session session, boolean redo) {
        return new DumbAwareAction() {
            @Override public ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
            @Override public void update(AnActionEvent event) {
                UndoManager manager = UndoManager.getGlobalInstance();
                event.getPresentation().setEnabled(!busy && (redo
                        ? manager.isRedoAvailable(session.fileEditor) : manager.isUndoAvailable(session.fileEditor)));
            }
            @Override public void actionPerformed(AnActionEvent event) {
                UndoManager manager = UndoManager.getGlobalInstance();
                if (redo && manager.isRedoAvailable(session.fileEditor)) manager.redo(session.fileEditor);
                else if (!redo && manager.isUndoAvailable(session.fileEditor)) manager.undo(session.fileEditor);
            }
        };
    }

    private Session selectedSession() {
        Component selected = tabs.getSelectedComponent();
        return sessions.values().stream().filter(s -> s.editor.getComponent() == selected).findFirst().orElse(null);
    }

    private void closeSelectedTab() {
        Session session = selectedSession(); if (session == null || busy) return;
        Runnable close = () -> {
            sessions.remove(session.path); tabs.remove(session.editor.getComponent());
            releaseSession(session); updateButtons();
        };
        if (session.isUnsaved()) handleUnsaved(close); else close.run();
    }

    private void updateSessionTitle(Session session) {
        int index = tabs.indexOfComponent(session.editor.getComponent());
        if (index >= 0) tabs.setTitleAt(index, (session.isUnsaved() ? "* " : "") + shortName(session.path));
        updateButtons();
    }

    private boolean hasUnsavedEditors() { return sessions.values().stream().anyMatch(Session::isUnsaved); }

    private void handleUnsaved(Runnable after) {
        Object[] choices = {"保存草稿后继续", "放弃未保存编辑", "取消"};
        int choice = JOptionPane.showOptionDialog(this,
                "有尚未保存到草稿的编辑。已保存的草稿会保留在独立工作区。", "未保存的编辑",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if (choice == 0) saveDrafts(after); else if (choice == 1) after.run();
    }

    private void requestClose() {
        if (busy) { status.setText("请等待当前任务完成后关闭。"); return; }
        if (hasUnsavedEditors()) handleUnsaved(this::closeNow); else closeNow();
    }

    private void closeNow() {
        releaseEditors();
        try { if (workspace != null) workspace.close(); } catch (IOException e) { showError(e); }
        io.shutdown(); dispose(); ApplicationManager.getApplication().exit();
    }

    private void releaseEditors() {
        for (JDialog dialog : List.copyOf(diffDialogs.keySet())) { dialog.dispose(); disposeDiff(dialog); }
        for (Session session : sessions.values()) releaseSession(session);
        sessions.clear(); tabs.removeAll();
    }

    private static void releaseSession(Session session) {
        if (session.saveAction != null) session.saveAction.unregisterCustomShortcutSet(session.editor.getComponent());
        if (session.undoAction != null) session.undoAction.unregisterCustomShortcutSet(session.editor.getComponent());
        if (session.redoAction != null) session.redoAction.unregisterCustomShortcutSet(session.editor.getComponent());
        EditorFactory.getInstance().releaseEditor(session.editor);
    }

    private void updateButtons() {
        open.setEnabled(!busy); save.setEnabled(!busy && workspace != null);
        export.setEnabled(!busy && workspace != null); diff.setEnabled(!busy && selectedSession() != null);
        closeTab.setEnabled(!busy && selectedSession() != null);
        encoding.setEnabled(!busy); pathSearch.setEnabled(!busy && workspace != null);
    }

    private <T> void runTask(String label, IoTask<T> task, Consumer<T> success) {
        busy = true; status.setText(label); updateButtons();
        io.execute(() -> {
            try {
                T value = task.run();
                ApplicationManager.getApplication().invokeLater(() -> {
                    busy = false; updateButtons();
                    try { success.accept(value); } catch (Throwable callbackFailure) { showError(callbackFailure); }
                });
            } catch (Throwable failure) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    busy = false; updateButtons(); showError(failure);
                });
            }
        });
    }

    private void showError(Throwable failure) {
        String message = failure.getMessage();
        if (failure instanceof java.nio.charset.CharacterCodingException)
            message = "所选编码无法无损转换此文本。请检查不可编码的字符，或关闭标签后用其他编码重新打开。";
        if (message == null || message.isBlank()) message = failure.toString();
        status.setText("操作失败：" + message);
        JOptionPane.showMessageDialog(this, message, "PVF Studio", JOptionPane.ERROR_MESSAGE);
    }

    private static String shortName(String path) { return path.substring(path.lastIndexOf('/') + 1); }
    private static String normalizeNewlines(String raw) { return raw.replace("\r\n", "\n").replace('\r', '\n'); }
    private record Item(String label, String path, boolean directory) { public String toString() { return label; } }
    private record Loaded(PvfWorkspace workspace, Map<String, List<Item>> directories, boolean reopenSameWorkspace) {}
    private record Snapshot(Session session, String editorText, String encodedText, long stamp) {}
    @FunctionalInterface private interface IoTask<T> { T run() throws Exception; }

    private static final class Session {
        final String path; final Charset charset; final Editor editor; final Newlines newlines;
        AnAction saveAction;
        AnAction undoAction;
        AnAction redoAction;
        TextEditor fileEditor;
        String persistedText;
        long persistedStamp;
        Session(String path, Charset charset, String raw, Editor editor) {
            this.path = path; this.charset = charset; this.editor = editor;
            this.newlines = new Newlines(raw); this.persistedText = editor.getDocument().getText();
            this.persistedStamp = editor.getDocument().getModificationStamp();
        }
        boolean isUnsaved() { return persistedStamp != editor.getDocument().getModificationStamp(); }
    }

    /** Keeps each original newline where possible, and the dominant style for added lines. */
    static final class Newlines {
        private final List<String> originals = new ArrayList<>();
        private final String dominant;
        Newlines(String raw) {
            int crlf = 0, lf = 0, cr = 0;
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (c == '\r') {
                    if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') {
                        originals.add("\r\n"); crlf++; i++;
                    } else { originals.add("\r"); cr++; }
                } else if (c == '\n') { originals.add("\n"); lf++; }
            }
            dominant = crlf >= lf && crlf >= cr ? "\r\n" : lf >= cr ? "\n" : "\r";
        }
        String restore(String text) {
            StringBuilder raw = new StringBuilder(text.length() + originals.size()); int newline = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') raw.append(newline < originals.size() ? originals.get(newline++) : dominant);
                else raw.append(c);
            }
            return raw.toString();
        }
        String description() {
            return isMixed() ? "混合换行（只读）"
                    : dominant.equals("\r\n") ? "CRLF" : dominant.equals("\n") ? "LF" : "CR";
        }
        boolean isMixed() { return originals.stream().distinct().count() > 1; }
    }
}
