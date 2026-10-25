package studio.pvf.intellij;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.find.EditorSearchSession;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
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
import studio.pvf.workspace.PvfBackupExport;
import studio.pvf.workspace.TextCodec;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import com.intellij.ui.render.LabelBasedRenderer;
import com.intellij.util.ui.JBUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.StringSelection;
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
import java.util.function.BooleanSupplier;

/** Small standalone workbench; archive I/O never runs on the Swing event thread. */
final class PvfStudioFrame extends JFrame {
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pvf-studio-io"); t.setDaemon(true); return t;
    });
    private final JMenuItem open = new JMenuItem("打开 PVF…");
    private final JMenuItem save = new JMenuItem("保存草稿");
    private final JMenuItem diff = new JMenuItem("查看差异");
    private final JMenuItem closeTab = new JMenuItem("关闭编辑器");
    private final JMenuItem export = new JMenuItem("导出新 PVF…");
    private final JCheckBoxMenuItem backupOnExport = new JCheckBoxMenuItem("导出前自动备份", true);
    private final JMenu encoding = new JMenu("打开编码");
    private final JMenu fileMenu = new JMenu("文件(F)");
    private final JMenu recentMenu = new JMenu("打开最近的文件");
    private final Map<AbstractButton, BooleanSupplier> menuEnabled = new LinkedHashMap<>();
    private final List<JRadioButtonMenuItem> encodingItems = new ArrayList<>();
    private int encodingIndex;
    private boolean showLineNumbers = true;
    private boolean softWraps;
    private RecentArchives recentArchives = RecentArchives.EMPTY;
    private boolean recentLoaded;
    private final Path recentFile;
    private JSplitPane editorSplit;
    private int previewDivider = 540;
    private final JTextField pathSearch = new JTextField();
    private final JTree tree = new JTree(new DefaultMutableTreeNode("请打开 Script.pvf"));
    private final DefaultListModel<String> searchResults = new DefaultListModel<>();
    private final JList<String> resultList = new JList<>(searchResults);
    private final JPanel navigation = new JPanel(new CardLayout());
    private final EditorTabs tabs = new EditorTabs();
    private final Map<AnAction, CustomShortcutSet> tabShortcuts = new LinkedHashMap<>();
    private final Deque<ClosedTab> closedTabs = new ArrayDeque<>();
    private final Path draftsRoot;
    private final boolean exitOnClose;
    private final KeyEventDispatcher tabKeyDispatcher = event -> {
        if (event.getID() == KeyEvent.KEY_RELEASED && event.getKeyCode() == KeyEvent.VK_CONTROL
                && KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == this) tabs.finishCycle();
        return false;
    };
    private final ResourcePreviewPanel preview = new ResourcePreviewPanel();
    private final javax.swing.Timer previewRefresh = new javax.swing.Timer(350, e -> updatePreview());
    private final javax.swing.Timer sessionSave = new javax.swing.Timer(650,
            e -> ApplicationManager.getApplication().invokeLater(this::saveEditorState));
    private final JLabel status = new JLabel("打开 PVF 后可浏览、编辑并导出新包。Ctrl+S 保存到独立草稿工作区。");
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private final Map<JDialog, Disposable> diffDialogs = new LinkedHashMap<>();
    private PvfWorkspace workspace;
    private Map<String, List<Item>> directories = Map.of();
    private boolean busy;
    private boolean sessionStatePaused;
    private boolean restoringViewport;
    private EditorSessionState lastQueuedState;
    private Path lastQueuedStateDirectory;
    private long searchVersion;
    private PvfSearchDialog contentSearch;

    PvfStudioFrame() { this(null, true); }

    PvfStudioFrame(Path draftsRoot, boolean exitOnClose) {
        super("PVF Studio");
        setGlassPane(new com.intellij.openapi.wm.impl.IdeGlassPaneImpl(getRootPane(), false));
        this.draftsRoot = draftsRoot; this.exitOnClose = exitOnClose;
        recentFile = (draftsRoot == null ? Path.of(System.getProperty("user.home"), ".pvf-studio") : draftsRoot)
                .resolve("recent-files.properties");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
        backupOnExport.setToolTipText("首次导出备份原包；更新已有导出文件前备份旧版本。备份位于导出目录的 backups/，保留所有版本。");
        JPanel left = new JPanel(new BorderLayout(0, 6));
        pathSearch.setToolTipText("按路径搜索；清空后显示目录树");
        JPanel search = new JPanel(new BorderLayout(5, 0));
        search.add(new JLabel("路径"), BorderLayout.WEST); search.add(pathSearch);
        left.add(search, BorderLayout.NORTH);
        configureNavigation();
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.getViewport().setBackground(tree.getBackground());
        navigation.add(treeScroll, "tree");
        JScrollPane resultScroll = new JScrollPane(resultList);
        resultScroll.getViewport().setBackground(tree.getBackground());
        navigation.add(resultScroll, "results");
        left.add(navigation);
        editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, preview);
        editorSplit.setResizeWeight(0.7); editorSplit.setDividerLocation(540);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editorSplit);
        split.setDividerLocation(280); split.setResizeWeight(0.2);
        add(split); add(status, BorderLayout.SOUTH);
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
        save.addActionListener(e -> saveCurrentDraft());
        diff.addActionListener(e -> showDiff());
        closeTab.addActionListener(e -> closeSelectedTab());
        export.addActionListener(e -> chooseExport());
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save-drafts");
        getRootPane().getActionMap().put("save-drafts", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { saveCurrentDraft(); }
        });
        configureTabs();
        configureMenuBar();
        loadRecentArchives();
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                // Native WINDOW_CLOSING is dispatched without the editor's read lock, unlike menu actions.
                com.intellij.openapi.application.WriteIntentReadAction.run((Runnable) PvfStudioFrame.this::requestClose);
            }
        });
        previewRefresh.setRepeats(false);
        sessionSave.setRepeats(false);
        tabs.addChangeListener(e -> {
            updateButtons(); updatePreview(); focusSelectedEditor(); restoreSelectedViewport(); scheduleEditorStateSave();
        });
        updateButtons();
    }

    private void configureTabs() {
        tabs.onClose(component -> closeTabs(component, CloseScope.CURRENT)); tabs.onMenu(this::tabMenu);
        bindTabAction("close", () -> closeSelectedTab(), key(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), key(KeyEvent.VK_F4, InputEvent.CTRL_DOWN_MASK));
        bindTabAction("next", () -> tabs.selectRelative(1), key(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK));
        bindTabAction("previous", () -> tabs.selectRelative(-1), key(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK));
        bindTabAction("recent", () -> tabs.cycleRecent(1), key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
        bindTabAction("recent-reverse", () -> tabs.cycleRecent(-1), key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        bindTabAction("move-left", () -> tabs.moveRelative(tabs.getSelectedComponent(), -1), key(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        bindTabAction("move-right", () -> tabs.moveRelative(tabs.getSelectedComponent(), 1), key(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        bindTabAction("reopen", this::reopenClosedTab, key(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        bindTabAction("close-all", () -> closeTabs(tabs.getSelectedComponent(), CloseScope.ALL),
                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK)),
                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), KeyStroke.getKeyStroke(KeyEvent.VK_W, 0)));
        bindTabAction("copy-path", () -> copyTabPath(tabs.getSelectedComponent()),
                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), KeyStroke.getKeyStroke(KeyEvent.VK_P, 0)));
        bindTabAction("save-all", () -> saveDrafts(null),
                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), KeyStroke.getKeyStroke(KeyEvent.VK_S, 0)));
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(tabKeyDispatcher);
        addWindowFocusListener(new WindowAdapter() { @Override public void windowLostFocus(WindowEvent e) { tabs.finishCycle(); } });
    }
    private static KeyboardShortcut key(int key, int modifiers) { return new KeyboardShortcut(KeyStroke.getKeyStroke(key, modifiers), null); }
    private void bindTabAction(String id, Runnable action, KeyboardShortcut... shortcuts) {
        bindWindowAction(id, () -> { action.run(); focusSelectedEditor(); }, shortcuts);
    }
    private void bindWindowAction(String id, Runnable action, KeyboardShortcut... shortcuts) {
        Runnable guarded = () -> { if (!busy) action.run(); };
        AnAction binding = new DumbAwareAction() {
            @Override public void actionPerformed(AnActionEvent event) { guarded.run(); }
        };
        CustomShortcutSet set = new CustomShortcutSet(shortcuts); tabShortcuts.put(binding, set);
        binding.registerCustomShortcutSet(set, getRootPane());
        getRootPane().getActionMap().put("pvf-tab-" + id, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { guarded.run(); }
        });
        for (KeyboardShortcut shortcut : shortcuts) if (shortcut.getSecondKeyStroke() == null)
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(shortcut.getFirstKeyStroke(), "pvf-tab-" + id);
    }
    private void focusSelectedEditor() {
        Session selected = selectedSession();
        if (selected != null) ApplicationManager.getApplication().invokeLater(() -> {
            if (!selected.editor.isDisposed() && selected == selectedSession()) selected.editor.getContentComponent().requestFocusInWindow();
        });
    }

    private void configureMenuBar() {
        JMenuBar bar = new JMenuBar(); setJMenuBar(bar);
        fileMenu.setMnemonic(KeyEvent.VK_F); bar.add(fileMenu);
        bindWindowAction("open-archive", this::chooseArchive, key(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        bindWindowAction("open-recent", this::showRecentMenu, key(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK)); fileMenu.add(open);
        fileMenu.add(recentMenu); fileMenu.addSeparator();
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)); fileMenu.add(save);
        command(fileMenu, "全部保存    Ctrl+K S", "save-all", () -> !sessions.isEmpty(), null);
        fileMenu.add(export); fileMenu.add(backupOnExport); fileMenu.addSeparator();
        ButtonGroup group = new ButtonGroup(); String[] encodings = {"GBK / CP936", "MS949 / CP949", "UTF-8"};
        for (int i = 0; i < encodings.length; i++) {
            int index = i; var item = new JRadioButtonMenuItem(encodings[i], i == 0);
            item.addActionListener(e -> selectEncoding(index)); group.add(item); encoding.add(item); encodingItems.add(item);
        }
        encoding.setToolTipText("用于下一次打开的配置文件；恢复标签时沿用各自原编码。"); fileMenu.add(encoding);
        fileMenu.addSeparator(); closeTab.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK)); fileMenu.add(closeTab);
        command(fileMenu, "关闭所有编辑器    Ctrl+K Ctrl+W", "close-all", () -> !sessions.isEmpty(), null);
        fileMenu.addSeparator(); menuItem(fileMenu, "退出", this::requestClose, () -> recentLoaded, null);

        JMenu editMenu = menu(bar, "编辑(E)", KeyEvent.VK_E);
        menuItem(editMenu, "撤销", () -> undo(false), () -> undoAvailable(false), KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        menuItem(editMenu, "重做", () -> undo(true), () -> undoAvailable(true), KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        editMenu.addSeparator();
        editorCommand(editMenu, "剪切", IdeActions.ACTION_EDITOR_CUT, KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK, true);
        editorCommand(editMenu, "复制", IdeActions.ACTION_EDITOR_COPY, KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK, false);
        editorCommand(editMenu, "粘贴", IdeActions.ACTION_EDITOR_PASTE, KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK, true);
        editMenu.addSeparator();
        bindWindowAction("find", () -> findInEditor(false), key(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        bindWindowAction("replace", () -> findInEditor(true), key(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
        command(editMenu, "查找", "find", () -> selectedSession() != null, KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        command(editMenu, "替换", "replace", () -> selectedSession() != null && !selectedSession().editor.isViewer(), KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
        bindWindowAction("global-search", this::showContentSearch, key(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        command(editMenu, "全局搜索…", "global-search", () -> workspace != null, KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));

        JMenu selectionMenu = menu(bar, "选择(S)", KeyEvent.VK_S);
        editorCommand(selectionMenu, "全选", IdeActions.ACTION_SELECT_ALL, KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK, false);
        editorCommand(selectionMenu, "扩大选择", IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET, KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK, false);
        editorCommand(selectionMenu, "缩小选择", IdeActions.ACTION_EDITOR_UNSELECT_WORD_AT_CARET, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK, false);

        JMenu viewMenu = menu(bar, "查看(V)", KeyEvent.VK_V); viewMenu.add(diff);
        var previewVisible = new JCheckBoxMenuItem("资源预览", true); viewMenu.add(previewVisible);
        previewVisible.addActionListener(e -> {
            if (!previewVisible.isSelected()) previewDivider = editorSplit.getDividerLocation();
            preview.setVisible(previewVisible.isSelected()); editorSplit.setDividerSize(previewVisible.isSelected() ? UIManager.getInt("SplitPane.dividerSize") : 0);
            if (previewVisible.isSelected()) editorSplit.setDividerLocation(previewDivider); editorSplit.revalidate();
        });
        viewMenu.addSeparator();
        var wrap = new JCheckBoxMenuItem("自动换行"); wrap.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.ALT_DOWN_MASK)); viewMenu.add(wrap);
        wrap.addActionListener(e -> { softWraps = wrap.isSelected(); for (Session session : sessions.values()) session.editor.getSettings().setUseSoftWraps(softWraps); });
        bindWindowAction("wrap", wrap::doClick, key(KeyEvent.VK_Z, InputEvent.ALT_DOWN_MASK));
        var lines = new JCheckBoxMenuItem("行号", true); viewMenu.add(lines);
        lines.addActionListener(e -> { showLineNumbers = lines.isSelected(); for (Session session : sessions.values()) session.editor.getSettings().setLineNumbersShown(showLineNumbers); });

        JMenu goMenu = menu(bar, "转到(G)", KeyEvent.VK_G);
        bindWindowAction("quick-open", () -> { pathSearch.requestFocusInWindow(); pathSearch.selectAll(); }, key(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
        command(goMenu, "按路径打开配置…", "quick-open", () -> workspace != null, KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
        goMenu.addSeparator();
        command(goMenu, "下一个编辑器", "next", () -> tabs.getTabCount() > 1, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK));
        command(goMenu, "上一个编辑器", "previous", () -> tabs.getTabCount() > 1, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK));
        command(goMenu, "最近使用的编辑器", "recent", () -> tabs.getTabCount() > 1, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));

        JMenu helpMenu = menu(bar, "帮助(H)", KeyEvent.VK_H);
        menuItem(helpMenu, "关于 PVF Studio", () -> JOptionPane.showMessageDialog(this,
                "PVF Studio " + com.intellij.openapi.application.ApplicationInfo.getInstance().getFullVersion()
                        + "\nDNF ACT1 PVF 配置编辑与场景预览\nCtrl+S 保存草稿；导出新 PVF 生成修改包。", "关于 PVF Studio", JOptionPane.INFORMATION_MESSAGE), () -> true, null);
        watchMenu(fileMenu); refreshRecentMenu();
    }

    private JMenu menu(JMenuBar bar, String label, int mnemonic) {
        JMenu menu = new JMenu(label); menu.setMnemonic(mnemonic); bar.add(menu); watchMenu(menu); return menu;
    }
    private void watchMenu(JMenu menu) {
        menu.addMenuListener(new MenuListener() {
            public void menuSelected(MenuEvent event) { updateButtons(); if (menu == fileMenu) refreshRecentMenu(); }
            public void menuDeselected(MenuEvent event) {}
            public void menuCanceled(MenuEvent event) {}
        });
    }
    private JMenuItem menuItem(JMenu menu, String label, Runnable action, BooleanSupplier enabled, KeyStroke shortcut) {
        JMenuItem item = new JMenuItem(label); if (shortcut != null) item.setAccelerator(shortcut);
        item.addActionListener(e -> { if (!busy && enabled.getAsBoolean()) action.run(); });
        menuEnabled.put(item, enabled); menu.add(item); return item;
    }
    private void command(JMenu menu, String label, String id, BooleanSupplier enabled, KeyStroke shortcut) {
        menuItem(menu, label, () -> getRootPane().getActionMap().get("pvf-tab-" + id).actionPerformed(new ActionEvent(this, 0, id)), enabled, shortcut);
    }
    private void editorCommand(JMenu menu, String label, String action, int key, int modifiers, boolean writable) {
        Runnable invoke = () -> performEditorAction(action);
        bindWindowAction("editor-" + action, invoke, key(key, modifiers));
        menuItem(menu, label, invoke, () -> selectedSession() != null && (!writable || !selectedSession().editor.isViewer()), KeyStroke.getKeyStroke(key, modifiers));
    }
    private void performEditorAction(String id) {
        Session selected = selectedSession(); if (selected == null) return;
        AnAction action = ActionManager.getInstance().getAction(id); if (action == null) return;
        AnActionEvent event = AnActionEvent.createEvent(action,
                DataManager.getInstance().getDataContext(selected.editor.getContentComponent()), null,
                ActionPlaces.MAIN_MENU, ActionUiKind.MAIN_MENU, null);
        action.update(event); if (event.getPresentation().isEnabled()) action.actionPerformed(event);
    }

    private void findInEditor(boolean replace) {
        Session selected = selectedSession(); if (selected == null || replace && selected.editor.isViewer()) return;
        EditorSearchSession search = EditorSearchSession.get(selected.editor);
        if (search == null) search = EditorSearchSession.start(selected.editor, selected.editor.getProject());
        search.getFindModel().setReplaceState(replace);
        search.getComponent().update(search.getFindModel().getStringToFind(), search.getFindModel().getStringToReplace(), replace, false);
        search.getComponent().requestFocusInTheSearchFieldAndSelectContent(selected.editor.getProject());
    }

    private void showContentSearch() {
        if (workspace == null) return;
        if (contentSearch == null || !contentSearch.isDisplayable()) contentSearch = new PvfSearchDialog(this, io, workspace,
                selectedCharset(), () -> {
                    Map<String, PvfSearchDialog.Source> sources = new HashMap<>();
                    sessions.forEach((path, session) -> sources.put(path, new PvfSearchDialog.Source(session.editor.getDocument().getText(), session.charset)));
                    return sources;
                }, this::openSearchHit);
        contentSearch.setVisible(true); contentSearch.toFront();
        Session selected = selectedSession(); contentSearch.focusQuery(selected == null ? null : selected.editor.getSelectionModel().getSelectedText());
    }
    private void openSearchHit(studio.pvf.workspace.PvfTextSearch.Hit hit, Charset charset, String query, boolean matchCase) {
        if (workspace == null || busy) return;
        Consumer<Session> navigate = session -> {
            tabs.setSelectedComponent(session.editor.getComponent());
            String text = session.editor.getDocument().getText();
            if (hit.end() > text.length() || !text.regionMatches(!matchCase, hit.start(), query, 0, query.length())) {
                status.setText("搜索后正文已变化，请重新搜索：" + hit.path()); return;
            }
            session.editor.getCaretModel().moveToOffset(hit.start()); session.editor.getSelectionModel().setSelection(hit.start(), hit.end());
            session.editor.getScrollingModel().scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER);
            toFront(); focusSelectedEditor();
        };
        Session existing = sessions.get(hit.path()); if (existing != null) { navigate.accept(existing); return; }
        PvfWorkspace current = workspace;
        runTask("读取搜索结果…", () -> readEditorText(current, hit.path(), charset), raw -> {
            createSession(hit.path(), charset, raw, null); navigate.accept(sessions.get(hit.path()));
        });
    }
    private boolean undoAvailable(boolean redo) {
        Session selected = selectedSession(); if (selected == null) return false;
        UndoManager manager = UndoManager.getGlobalInstance();
        return redo ? manager.isRedoAvailable(selected.fileEditor) : manager.isUndoAvailable(selected.fileEditor);
    }
    private void undo(boolean redo) {
        if (!undoAvailable(redo)) return;
        Session selected = selectedSession(); UndoManager manager = UndoManager.getGlobalInstance();
        if (redo) manager.redo(selected.fileEditor); else manager.undo(selected.fileEditor);
    }
    void selectEncoding(int index) {
        if (index < 0 || index >= encodingItems.size()) throw new IllegalArgumentException("Invalid encoding index");
        encodingIndex = index; encodingItems.get(index).setSelected(true);
    }

    private void loadRecentArchives() {
        io.execute(() -> {
            RecentArchives loaded; String warning = null;
            try { loaded = RecentArchives.read(recentFile); }
            catch (IOException failure) { loaded = RecentArchives.EMPTY; warning = failure.getMessage(); }
            RecentArchives history = loaded; String message = warning;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (!isDisplayable()) return;
                recentArchives = history; recentLoaded = true; refreshRecentMenu(); updateButtons();
                if (message != null && workspace == null && !busy) status.setText("无法读取最近文件记录：" + message);
            });
        });
    }
    private void rememberArchive(Path archive) {
        recentArchives = recentArchives.opened(archive); persistRecentArchives(); refreshRecentMenu();
    }
    private void persistRecentArchives() {
        RecentArchives snapshot = recentArchives;
        io.execute(() -> {
            try { snapshot.write(recentFile); }
            catch (IOException failure) { ApplicationManager.getApplication().invokeLater(() -> {
                if (isDisplayable()) status.setText("最近文件记录保存失败：" + failure.getMessage());
            }); }
        });
    }
    private void refreshRecentMenu() {
        recentMenu.removeAll();
        JMenuItem reopen = new JMenuItem("重新打开已关闭的编辑器"); reopen.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        reopen.setEnabled(!busy && !closedTabs.isEmpty()); reopen.addActionListener(e -> reopenClosedTab()); recentMenu.add(reopen); recentMenu.addSeparator();
        if (recentArchives.paths().isEmpty()) { JMenuItem empty = new JMenuItem(recentLoaded ? "暂无最近打开的 PVF" : "正在读取…"); empty.setEnabled(false); recentMenu.add(empty); }
        for (Path path : recentArchives.paths()) {
            JMenuItem item = new JMenuItem(path.toString()); item.putClientProperty("pvf.archive", path); item.setToolTipText(path.toString());
            item.setEnabled(!busy); item.addActionListener(e -> openArchive(path)); recentMenu.add(item);
        }
        recentMenu.addSeparator();
        JMenuItem clear = new JMenuItem("清除最近打开的文件"); clear.setEnabled(!busy && recentLoaded && !recentArchives.paths().isEmpty());
        clear.addActionListener(e -> { recentArchives = RecentArchives.EMPTY; persistRecentArchives(); refreshRecentMenu(); }); recentMenu.add(clear);
    }
    private void showRecentMenu() {
        if (!recentLoaded) return; refreshRecentMenu();
        MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{getJMenuBar(), fileMenu, fileMenu.getPopupMenu(), recentMenu, recentMenu.getPopupMenu()});
    }

    enum CloseScope { CURRENT, OTHERS, LEFT, RIGHT, UNMODIFIED, ALL }
    private JPopupMenu tabMenu(Component target) {
        Session session = sessionFor(target); int index = tabs.indexOfComponent(target); JPopupMenu menu = new JPopupMenu();
        if (session == null) return menu;
        tabMenuItem(menu, "保存草稿", "Ctrl+S", !busy && session.isUnsaved(), () -> saveSessions(List.of(session), null));
        menu.addSeparator();
        tabMenuItem(menu, "关闭", "Ctrl+W", !busy, () -> closeTabs(target, CloseScope.CURRENT));
        tabMenuItem(menu, "关闭其他标签页", "", !busy && tabs.getTabCount() > 1, () -> closeTabs(target, CloseScope.OTHERS));
        tabMenuItem(menu, "关闭左侧标签页", "", !busy && index > 0, () -> closeTabs(target, CloseScope.LEFT));
        tabMenuItem(menu, "关闭右侧标签页", "", !busy && index < tabs.getTabCount() - 1, () -> closeTabs(target, CloseScope.RIGHT));
        tabMenuItem(menu, "关闭未修改的标签页", "", !busy && sessions.values().stream().anyMatch(s -> !s.isUnsaved()), () -> closeTabs(target, CloseScope.UNMODIFIED));
        tabMenuItem(menu, "全部关闭", "Ctrl+K Ctrl+W", !busy, () -> closeTabs(target, CloseScope.ALL));
        menu.addSeparator();
        tabMenuItem(menu, "向左移动标签页", "Ctrl+Shift+PageUp", !busy && index > 0, () -> tabs.moveRelative(target, -1));
        tabMenuItem(menu, "向右移动标签页", "Ctrl+Shift+PageDown", !busy && index < tabs.getTabCount() - 1, () -> tabs.moveRelative(target, 1));
        tabMenuItem(menu, "重新打开关闭的标签页", "Ctrl+Shift+T", !busy && !closedTabs.isEmpty(), this::reopenClosedTab);
        menu.addSeparator();
        tabMenuItem(menu, "复制 PVF 内路径", "Ctrl+K P", true, () -> copyTabPath(target));
        return menu;
    }
    private static void tabMenuItem(JPopupMenu menu, String title, String shortcut, boolean enabled, Runnable action) {
        JMenuItem item = new JMenuItem(title + (shortcut.isEmpty() ? "" : "    " + shortcut)); item.setEnabled(enabled);
        item.addActionListener(e -> action.run()); menu.add(item);
    }
    private void copyTabPath(Component target) {
        Session session = sessionFor(target);
        if (session != null) { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(session.path), null); status.setText("已复制 PVF 内路径：" + session.path); }
    }

    private void configureNavigation() {
        configureFileTree(tree);
        ToolTipManager.sharedInstance().registerComponent(tree);
        resultList.setBackground(tree.getBackground());
        resultList.setFixedCellHeight(JBUI.scale(24));
        resultList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> view, Object value,
                    int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(view, value, index, selected, focused);
                setIcon(PvfFileIcons.forEntry(String.valueOf(value), false, false));
                setIconTextGap(JBUI.scale(6));
                setBorder(JBUI.Borders.empty(0, 4));
                return this;
            }
        });
    }

    static void configureFileTree(JTree tree) {
        tree.setRowHeight(JBUI.scale(24));
        tree.setLargeModel(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new LabelBasedRenderer.Tree() {
            @Override public Component getTreeCellRendererComponent(JTree view, Object value, boolean selected,
                    boolean expanded, boolean leaf, int row, boolean focused) {
                super.getTreeCellRendererComponent(view, value, selected, expanded, leaf, row, focused);
                // The platform paints row backgrounds. A label renderer avoids Swing's
                // DefaultTreeCellRenderer fallback to a previously selected row's background.
                setOpaque(false);
                setIconTextGap(JBUI.scale(6));
                setToolTipText(null);
                if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Item item) {
                    setText(item.label());
                    setIcon(PvfFileIcons.forEntry(item.label(), item.directory() && !node.isRoot(), expanded));
                    setToolTipText(item.path().isEmpty() ? item.label() : item.path());
                }
                return this;
            }
        });
    }

    private Charset selectedCharset() {
        return switch (encodingIndex) {
            case 1 -> Charset.forName("MS949");
            case 2 -> StandardCharsets.UTF_8;
            default -> Charset.forName("GBK");
        };
    }

    private void chooseArchive() {
        if (busy) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("打开 PVF（原包保持只读）");
        chooser.setFileFilter(new FileNameExtensionFilter("PVF 配置包 (*.pvf)", "pvf")); chooser.setAcceptAllFileFilterUsed(false);
        Path initial = workspace == null ? recentArchives.paths().stream().findFirst().orElse(null) : workspace.archive().sourcePath();
        if (initial != null && initial.getParent() != null) chooser.setCurrentDirectory(initial.getParent().toFile());
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
                if (unchanged) { rememberArchive(current.archive().sourcePath()); status.setText("该 PVF 已打开且内容未变：" + archive.toAbsolutePath()); toFront(); }
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
        if (contentSearch != null) { contentSearch.dispose(); contentSearch = null; }
        PvfWorkspace previous = workspace;
        EditorSessionState previousState = captureEditorState();
        pauseEditorState();
        runTask("读取 PVF 目录及草稿…", () -> {
            if (previous != null) previousState.write(previous.directory());
            Path directory = workspaceDirectory(archive);
            // A timestamp-only change or path alias can retain exactly the same draft identity.
            // Release the old workspace on the UI side before trying to reacquire its lock.
            if (previous != null && previous.directory().equals(directory))
                return new Loaded(null, Map.of(), true, List.of(), "", List.of());
            PvfWorkspace loaded = PvfWorkspace.open(archive, directory);
            try {
                EditorSessionState.ReadResult saved = EditorSessionState.read(directory);
                List<RestoredTab> restored = new ArrayList<>(); List<String> warnings = new ArrayList<>(saved.warnings());
                for (EditorSessionState.Tab tab : saved.state().tabs()) {
                    try { restored.add(new RestoredTab(tab, readEditorText(loaded, tab.path(), Charset.forName(tab.charset())))); }
                    catch (Exception invalid) { warnings.add("未能恢复 " + tab.path() + "：" + invalid.getMessage()); }
                }
                return new Loaded(loaded, buildDirectories(loaded.archive().entries()), false,
                        restored, saved.state().selectedPath(), warnings);
            } catch (Throwable failure) { loaded.close(); throw failure; }
        }, loaded -> {
            if (loaded.reopenSameWorkspace()) {
                releaseEditors(); workspace = null; directories = Map.of();
                tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("重新加载 PVF…")));
                updateButtons();
                try { previous.close(); }
                catch (IOException failure) { resumeEditorState(); showError(failure); return; }
                doOpenArchive(archive); return;
            }
            releaseEditors();
            workspace = loaded.workspace(); directories = loaded.directories();
            preview.mount(workspace.directory(), workspace.archive().sourcePath().getParent());
            if (previous != null) {
                try { previous.close(); } catch (IOException e) { showError(e); }
            }
            DefaultMutableTreeNode root = node(new Item(archive.getFileName().toString(), "", true));
            tree.setModel(new DefaultTreeModel(root)); loadChildren(root); tree.expandRow(0);
            pathSearch.setText("");
            setTitle("PVF Studio — " + archive.toAbsolutePath());
            tabs.runBatch(() -> {
                for (RestoredTab restored : loaded.tabs()) {
                    EditorSessionState.Tab tab = restored.position();
                    createSession(tab.path(), Charset.forName(tab.charset()), restored.raw(),
                            new ClosedTab(tab.path(), Charset.forName(tab.charset()), tabs.getTabCount(), tab.caret(), tab.scrollX(), tab.scrollY()));
                }
                Session selected = sessions.get(loaded.selectedPath());
                if (selected != null) tabs.setSelectedComponent(selected.editor.getComponent());
                else if (tabs.getTabCount() > 0) tabs.setSelectedIndex(0);
            });
            resumeEditorState();
            rememberArchive(workspace.archive().sourcePath());
            status.setText("已读取 " + workspace.archive().fileCount() + " 个文件；已保存草稿 "
                    + workspace.dirtyPaths().size() + " 个；已恢复 " + tabs.getTabCount() + " 个标签。"
                    + (loaded.warnings().isEmpty() ? "" : "恢复提示：" + String.join("；", loaded.warnings())));
            updateButtons();
        }, this::resumeEditorState);
    }

    private Path workspaceDirectory(Path archive) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(archive.toRealPath().toString().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        try (java.io.InputStream stream = Files.newInputStream(archive)) {
            byte[] buffer = new byte[1024 * 1024]; int count;
            while ((count = stream.read(buffer)) >= 0) if (count > 0) digest.update(buffer, 0, count);
        }
        byte[] hash = digest.digest();
        Path root = draftsRoot == null ? Path.of(System.getProperty("user.home"), ".pvf-studio", "workspaces") : draftsRoot;
        return root.resolve(HexFormat.of().formatHex(hash, 0, 16));
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

    void openEntry(String path) {
        openEntry(path, selectedCharset(), null);
    }
    private void openEntry(String path, Charset charset, ClosedTab restore) {
        if (path == null || workspace == null || busy) return;
        Session existing = sessions.get(path);
        if (existing != null) { tabs.finishCycle(); tabs.setSelectedComponent(existing.editor.getComponent()); focusSelectedEditor(); return; }
        PvfWorkspace current = workspace;
        runTask("读取 " + path + "…", () -> readEditorText(current, path, charset),
                raw -> createSession(path, charset, raw, restore));
    }

    private static String readEditorText(PvfWorkspace workspace, String path, Charset charset) throws IOException {
        String raw = workspace.readText(path, charset);
        if (raw.indexOf('\0') >= 0) throw new IOException("内容包含 NUL，当前只支持文本编辑：" + path);
        return raw;
    }

    private void createSession(String path, Charset charset, String raw, ClosedTab restore) {
        String normalized = normalizeNewlines(raw);
        // The global undo provider must subscribe before the first edit of an unbound document.
        UndoManager.getGlobalInstance();
        Project editorProject = ProjectManager.getInstance().getDefaultProject();
        Document document = EditorFactory.getInstance().createDocument(normalized);
        Newlines lineEndings = new Newlines(raw);
        Editor editor = lineEndings.isMixed() ? EditorFactory.getInstance().createViewer(document, editorProject)
                : EditorFactory.getInstance().createEditor(document, editorProject);
        editor.getSettings().setLineNumbersShown(showLineNumbers);
        editor.getSettings().setUseSoftWraps(softWraps);
        editor.getSettings().setWhitespacesShown(false);
        if (editor instanceof EditorEx ex) ex.setHighlighter(new LexerEditorHighlighter(
                new PvfSyntaxHighlighter(), EditorColorsManager.getInstance().getGlobalScheme()));
        Session session = new Session(path, charset, raw, editor);
        session.fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        session.saveAction = new DumbAwareAction() {
            @Override public void actionPerformed(AnActionEvent event) { if (!busy) saveSessions(List.of(session), null); }
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
        tabShortcuts.forEach((action, shortcut) -> action.registerCustomShortcutSet(shortcut, editor.getComponent()));
        sessions.put(path, session);
        document.addDocumentListener(new DocumentListener() {
            public void documentChanged(DocumentEvent event) {
                updateSessionTitle(session); previewRefresh.restart(); scheduleEditorStateSave();
            }
        });
        editor.getCaretModel().addCaretListener(new CaretListener() {
            @Override public void caretPositionChanged(CaretEvent event) { scheduleEditorStateSave(); }
        });
        editor.getScrollingModel().addVisibleAreaListener(event -> scheduleEditorStateSave());
        editor.getComponent().addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent event) { restoreSelectedViewport(); }
            @Override public void componentResized(ComponentEvent event) { restoreSelectedViewport(); }
        });
        if (restore != null) {
            session.pendingViewport = new Point(restore.scrollX(), restore.scrollY());
            editor.getCaretModel().moveToOffset(Math.min(restore.caret(), document.getTextLength()));
        }
        closedTabs.removeIf(tab -> tab.path().equals(path));
        tabs.finishCycle();
        tabs.runBatch(() -> {
            int index = restore == null ? tabs.getTabCount() : Math.min(restore.index(), tabs.getTabCount());
            tabs.insertTab(shortName(path), null, editor.getComponent(), path + " [" + charset.name() + "]", index);
            tabs.decorate(editor.getComponent(), PvfFileIcons.forEntry(path, false, false)); tabs.setSelectedComponent(editor.getComponent());
        });
        status.setText(path + " | " + charset.name() + " | " + session.newlines.description()
                + (lineEndings.isMixed() ? " | 混合换行暂只读，避免隐式改写行尾" : " | Ctrl+S 保存草稿；导出生成新包"));
        updateButtons();
    }

    private void saveDrafts(Runnable after) {
        saveSessions(List.copyOf(sessions.values()), after);
    }
    private void saveCurrentDraft() {
        Session session = selectedSession(); if (session != null) saveSessions(List.of(session), null);
    }
    private void saveSessions(List<Session> targets, Runnable after) {
        if (workspace == null || busy) return;
        List<Snapshot> snapshots = snapshots(targets); PvfWorkspace current = workspace;
        runTask("保存草稿…", () -> {
            persistSnapshots(current, snapshots); return snapshots;
        }, saved -> {
            markSaved(saved);
            status.setText("草稿已保存；相对原包共有 " + workspace.dirtyPaths().size() + " 个修改文件。");
            if (after != null) after.run();
        });
    }

    private List<Snapshot> snapshots() {
        return snapshots(sessions.values());
    }
    private List<Snapshot> snapshots(Collection<Session> targets) {
        List<Snapshot> result = new ArrayList<>();
        for (Session session : targets) {
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
        boolean backup = backupOnExport.isSelected();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(backup ? "导出 PVF（自动备份旧版本）" : "导出到新 PVF 文件（不覆盖既有文件）");
        chooser.setSelectedFile(new java.io.File("Script-edited.pvf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path target = chooser.getSelectedFile().toPath();
        if (backup && Files.exists(target) && JOptionPane.showConfirmDialog(this,
                "将先把旧版本保存到导出目录的 backups 文件夹，\n再用校验后的新版本更新：\n" + target.toAbsolutePath(),
                "备份并更新导出文件", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
        List<Snapshot> snapshots = snapshots(); PvfWorkspace current = workspace;
        runTask(backup ? "保存草稿、备份旧版本并导出校验 PVF…" : "保存草稿并导出、校验 PVF…", () -> {
            persistSnapshots(current, snapshots);
            if (backup) return PvfBackupExport.export(current, target);
            current.export(target);
            return new PvfBackupExport.Result(target.toAbsolutePath(), null);
        }, result -> {
            markSaved(snapshots);
            status.setText("导出完成：" + result.output() + (result.backup() == null ? "" : " | 旧版本备份：" + result.backup()));
            JOptionPane.showMessageDialog(this, "已导出并通过回读校验：\n" + result.output()
                    + (result.backup() == null ? "" : "\n\n旧版本备份：\n" + result.backup()
                        + "\n\n需要恢复时，可直接打开备份 PVF，或将它复制回所需位置。")
                    + "\n\n原包未被修改。", "导出完成", JOptionPane.INFORMATION_MESSAGE);
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
        return sessionFor(tabs.getSelectedComponent());
    }
    private Session sessionFor(Component component) { return sessions.values().stream().filter(s -> s.editor.getComponent() == component).findFirst().orElse(null); }

    private EditorSessionState captureEditorState() {
        List<EditorSessionState.Tab> saved = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Session session = sessionFor(tabs.getComponentAt(i));
            if (session == null || session.editor.isDisposed()) continue;
            Point pending = session.pendingViewport;
            saved.add(new EditorSessionState.Tab(session.path, session.charset.name(), session.editor.getCaretModel().getOffset(),
                    pending == null ? session.editor.getScrollingModel().getHorizontalScrollOffset() : pending.x,
                    pending == null ? session.editor.getScrollingModel().getVerticalScrollOffset() : pending.y));
        }
        Session selected = selectedSession();
        return new EditorSessionState(saved, selected == null ? "" : selected.path);
    }

    private void scheduleEditorStateSave() {
        if (!sessionStatePaused && !restoringViewport && workspace != null) sessionSave.restart();
    }

    private void saveEditorState() {
        if (sessionStatePaused || workspace == null) return;
        Path directory = workspace.directory(); EditorSessionState state = captureEditorState();
        if (directory.equals(lastQueuedStateDirectory) && state.equals(lastQueuedState)) return;
        lastQueuedStateDirectory = directory; lastQueuedState = state;
        // The same executor serializes autosaves, final flushes and archive transitions.
        io.execute(() -> {
            try { state.write(directory); }
            catch (IOException failure) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!isDisplayable() || workspace == null || !workspace.directory().equals(directory)) return;
                    if (lastQueuedState == state) lastQueuedState = null;
                    status.setText("标签会话保存失败：" + failure.getMessage() + "；关闭或切换 PVF 时将重试。");
                });
            }
        });
    }

    private void pauseEditorState() {
        sessionStatePaused = true; sessionSave.stop(); setEnabled(false);
    }

    private void resumeEditorState() {
        sessionStatePaused = false; setEnabled(true); restoreSelectedViewport(); scheduleEditorStateSave();
    }

    private void restoreSelectedViewport() {
        Session session = selectedSession();
        if (sessionStatePaused || session == null || session.pendingViewport == null) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            if (sessionStatePaused || session.editor.isDisposed() || selectedSession() != session || session.pendingViewport == null) return;
            validate();
            if (!session.editor.getContentComponent().isShowing() || session.editor.getScrollingModel().getVisibleArea().height <= 0) return;
            Point position = session.pendingViewport;
            restoringViewport = true; session.editor.getScrollingModel().disableAnimation();
            try {
                session.editor.getScrollingModel().scrollHorizontally(position.x);
                session.editor.getScrollingModel().scrollVertically(position.y);
                session.pendingViewport = null;
            } finally { session.editor.getScrollingModel().enableAnimation(); restoringViewport = false; }
            scheduleEditorStateSave();
        });
    }

    private void updatePreview() {
        previewRefresh.stop(); Session selected = selectedSession();
        if (selected == null || workspace == null) { preview.setSource(null); return; }
        PvfWorkspace current = workspace;
        Map<String, String> editors = new HashMap<>();
        for (Session session : sessions.values()) editors.put(session.path, session.editor.getDocument().getText());
        Set<String> paths = new HashSet<>();
        for (PvfEntry entry : current.archive().entries()) paths.add(entry.path());
        preview.setSource(new ResourcePreviewPanel.Source(selected.path, selected.editor.getDocument().getText(), path -> {
            String text = editors.get(path);
            return text != null ? text : paths.contains(path) ? current.readText(path, selected.charset) : null;
        }));
    }

    private void closeSelectedTab() {
        closeTabs(tabs.getSelectedComponent(), CloseScope.CURRENT);
    }
    private void closeTabs(Component anchor, CloseScope scope) {
        if (busy) return;
        int index = tabs.indexOfComponent(anchor); if (index < 0) return;
        List<Session> targets = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Session session = sessionFor(tabs.getComponentAt(i));
            boolean include = switch (scope) {
                case CURRENT -> i == index; case OTHERS -> i != index; case LEFT -> i < index;
                case RIGHT -> i > index; case UNMODIFIED -> session != null && !session.isUnsaved(); case ALL -> true;
            };
            if (include && session != null) targets.add(session);
        }
        if (targets.isEmpty()) return;
        tabs.finishCycle();
        Runnable close = () -> {
            List<Component> components = targets.stream().map(s -> (Component) s.editor.getComponent()).toList();
            Component preferred = tabs.preferredAfterClosing(components);
            List<ClosedTab> history = targets.stream().map(s -> new ClosedTab(s.path, s.charset, tabs.indexOfComponent(s.editor.getComponent()),
                    s.editor.getCaretModel().getOffset(), s.pendingViewport == null ? s.editor.getScrollingModel().getHorizontalScrollOffset() : s.pendingViewport.x,
                    s.pendingViewport == null ? s.editor.getScrollingModel().getVerticalScrollOffset() : s.pendingViewport.y)).toList();
            tabs.runBatch(() -> {
                for (Session session : targets) {
                    sessions.remove(session.path); tabs.remove(session.editor.getComponent()); releaseSession(session);
                }
                if (preferred != null && tabs.indexOfComponent(preferred) >= 0) tabs.setSelectedComponent(preferred);
            });
            for (ClosedTab tab : history) {
                closedTabs.removeIf(old -> old.path().equals(tab.path())); closedTabs.addFirst(tab);
            }
            while (closedTabs.size() > 100) closedTabs.removeLast();
            updateButtons(); focusSelectedEditor(); status.setText("已关闭 " + targets.size() + " 个标签页。Ctrl+Shift+T 可重新打开。");
        };
        if (targets.stream().anyMatch(Session::isUnsaved)) handleUnsaved(targets, close); else close.run();
    }
    private void reopenClosedTab() {
        if (workspace == null || busy) return;
        while (!closedTabs.isEmpty() && sessions.containsKey(closedTabs.peekFirst().path())) closedTabs.removeFirst();
        if (!closedTabs.isEmpty()) { ClosedTab tab = closedTabs.peekFirst(); openEntry(tab.path(), tab.charset(), tab); }
    }

    private void updateSessionTitle(Session session) {
        int index = tabs.indexOfComponent(session.editor.getComponent());
        if (index >= 0) tabs.setTitleAt(index, (session.isUnsaved() ? "* " : "") + shortName(session.path));
        updateButtons();
    }

    private boolean hasUnsavedEditors() { return sessions.values().stream().anyMatch(Session::isUnsaved); }

    private void handleUnsaved(Runnable after) {
        handleUnsaved(List.copyOf(sessions.values()), after);
    }
    private void handleUnsaved(List<Session> targets, Runnable after) {
        Object[] choices = {"保存草稿后继续", "放弃未保存编辑", "取消"};
        List<String> changed = targets.stream().filter(Session::isUnsaved).map(s -> s.path).toList();
        int choice = JOptionPane.showOptionDialog(this,
                "以下 " + changed.size() + " 个标签页有未保存编辑：\n" + String.join("\n", changed.stream().limit(6).toList())
                        + (changed.size() > 6 ? "\n…" : "") + "\n仅处理这些标签页；已保存的草稿会保留在独立工作区。", "未保存的编辑",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if (choice == 0) saveSessions(targets, () -> {
            // Editors remain usable during disk I/O. A later edit must not be closed with an older saved snapshot.
            if (targets.stream().anyMatch(Session::isUnsaved)) handleUnsaved(targets, after); else after.run();
        }); else if (choice == 1) after.run();
    }

    private void requestClose() {
        if (!recentLoaded) { status.setText("正在读取最近文件记录，请稍后关闭。"); return; }
        if (busy) { status.setText("请等待当前任务完成后关闭。"); return; }
        if (hasUnsavedEditors()) handleUnsaved(this::closeNow); else closeNow();
    }

    private void closeNow() {
        if (contentSearch != null) { contentSearch.dispose(); contentSearch = null; }
        EditorSessionState state = captureEditorState(); Path directory = workspace == null ? null : workspace.directory();
        RecentArchives history = recentArchives;
        pauseEditorState();
        runTask("保存标签页及编辑位置…", () -> { history.write(recentFile); if (directory != null) state.write(directory); return true; },
                ignored -> finishClose(), this::resumeEditorState);
    }

    private void finishClose() {
        if (contentSearch != null) contentSearch.dispose();
        preview.close();
        releaseEditors();
        try { if (workspace != null) workspace.close(); } catch (IOException e) { showError(e); }
        io.shutdown(); dispose(); if (exitOnClose) ApplicationManager.getApplication().exit();
    }
    @Override public void dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(tabKeyDispatcher);
        tabShortcuts.keySet().forEach(action -> action.unregisterCustomShortcutSet(getRootPane())); super.dispose();
    }

    private void releaseEditors() {
        sessionSave.stop();
        previewRefresh.stop(); preview.setSource(null);
        for (JDialog dialog : List.copyOf(diffDialogs.keySet())) { dialog.dispose(); disposeDiff(dialog); }
        for (Session session : sessions.values()) releaseSession(session);
        sessions.clear(); tabs.removeAll(); closedTabs.clear();
    }

    private void releaseSession(Session session) {
        EditorSearchSession search = EditorSearchSession.get(session.editor); if (search != null) search.close();
        tabShortcuts.keySet().forEach(action -> action.unregisterCustomShortcutSet(session.editor.getComponent()));
        if (session.saveAction != null) session.saveAction.unregisterCustomShortcutSet(session.editor.getComponent());
        if (session.undoAction != null) session.undoAction.unregisterCustomShortcutSet(session.editor.getComponent());
        if (session.redoAction != null) session.redoAction.unregisterCustomShortcutSet(session.editor.getComponent());
        EditorFactory.getInstance().releaseEditor(session.editor);
    }

    private void updateButtons() {
        open.setEnabled(!busy); save.setEnabled(!busy && selectedSession() != null);
        export.setEnabled(!busy && workspace != null); diff.setEnabled(!busy && selectedSession() != null);
        backupOnExport.setEnabled(!busy && workspace != null);
        closeTab.setEnabled(!busy && selectedSession() != null);
        encoding.setEnabled(!busy); pathSearch.setEnabled(!busy && workspace != null);
        recentMenu.setEnabled(!busy && recentLoaded);
        menuEnabled.forEach((item, enabled) -> item.setEnabled(!busy && enabled.getAsBoolean()));
    }

    private <T> void runTask(String label, IoTask<T> task, Consumer<T> success) {
        runTask(label, task, success, () -> {});
    }

    private <T> void runTask(String label, IoTask<T> task, Consumer<T> success, Runnable onFailure) {
        busy = true; status.setText(label); updateButtons();
        io.execute(() -> {
            try {
                T value = task.run();
                ApplicationManager.getApplication().invokeLater(() -> {
                    busy = false; updateButtons();
                    try { success.accept(value); }
                    catch (Throwable callbackFailure) { onFailure.run(); showError(callbackFailure); }
                });
            } catch (Throwable failure) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    busy = false; updateButtons(); onFailure.run(); showError(failure);
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
    private record Loaded(PvfWorkspace workspace, Map<String, List<Item>> directories, boolean reopenSameWorkspace,
                          List<RestoredTab> tabs, String selectedPath, List<String> warnings) {}
    private record RestoredTab(EditorSessionState.Tab position, String raw) {}
    private record Snapshot(Session session, String editorText, String encodedText, long stamp) {}
    private record ClosedTab(String path, Charset charset, int index, int caret, int scrollX, int scrollY) {}
    @FunctionalInterface private interface IoTask<T> { T run() throws Exception; }

    private static final class Session {
        final String path; final Charset charset; final Editor editor; final Newlines newlines;
        AnAction saveAction;
        AnAction undoAction;
        AnAction redoAction;
        TextEditor fileEditor;
        String persistedText;
        long persistedStamp;
        Point pendingViewport;
        Session(String path, Charset charset, String raw, Editor editor) {
            this.path = path; this.charset = charset; this.editor = editor;
            this.newlines = new Newlines(raw); this.persistedText = editor.getDocument().getText();
            this.persistedStamp = editor.getDocument().getModificationStamp();
        }
        boolean isUnsaved() { return persistedStamp != editor.getDocument().getModificationStamp() && !persistedText.equals(editor.getDocument().getText()); }
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
