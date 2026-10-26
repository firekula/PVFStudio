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
    private static final com.intellij.openapi.util.Key<Newlines> SOURCE_LINE_ENDINGS=com.intellij.openapi.util.Key.create("pvf.source.line.endings");
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pvf-studio-io"); t.setDaemon(true); return t;
    });
    private final ExecutorService sceneWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pvf-scene-parser"); t.setDaemon(true); return t;
    });
    // Owned by sceneWorker; immutable archive results are reused across dungeon tabs.
    private PvfWorkspace bossCatalogOwner;
    private Charset bossCatalogCharset;
    private studio.pvf.sceneedit.MapBossCatalog bossCatalog=new studio.pvf.sceneedit.MapBossCatalog();
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
    private int encodingIndex = 4;
    private boolean showLineNumbers = true;
    private boolean softWraps;
    private boolean showResourcePreview = true;
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
    private final Map<String,Document> retainedSceneDocuments=new LinkedHashMap<>();
    private final Set<String> hellMapSelections=new HashSet<>();
    private String previewSignature="";
    private final Map<String,String> roomOrigins = new HashMap<>();
    private final Deque<ClosedTab> closedTabs = new ArrayDeque<>();
    private final Path draftsRoot;
    private final boolean exitOnClose;
    private final KeyEventDispatcher tabKeyDispatcher = event -> {
        if (event.getID() == KeyEvent.KEY_RELEASED && event.getKeyCode() == KeyEvent.VK_CONTROL
                && KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == this) tabs.finishCycle();
        return false;
    };
    private final ResourcePreviewPanel preview = new ResourcePreviewPanel();
    private final JPanel previewDock = new JPanel(new BorderLayout());
    private Session previewOwner;
    private final javax.swing.Timer previewRefresh = new javax.swing.Timer(350, e -> updatePreview());
    private final javax.swing.Timer sessionSave = new javax.swing.Timer(650,
            e -> ApplicationManager.getApplication().invokeLater(this::saveEditorState));
    private final JLabel status = new JLabel("打开 PVF 后可浏览、编辑并导出新包。Ctrl+S 保存到独立草稿工作区。");
    private final TaskProgress taskProgress = new TaskProgress("pvf-task-progress");
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
        preview.onViewChanged(this::scheduleEditorStateSave);
        preview.onResourcesChanged(() -> sessions.values().stream().filter(tab -> tab.mapPanel!=null).forEach(tab -> tab.mapPanel.browser().setThumbnailLoader((asset,result) -> preview.thumbnail(sceneSource(tab),asset,result))));
        com.intellij.ui.AppUIUtilKt.updateAppWindowIcon(this);
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
        previewDock.add(preview);
        editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabs, previewDock);
        editorSplit.setResizeWeight(0.7); editorSplit.setDividerLocation(540);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, editorSplit);
        split.setDividerLocation(280); split.setResizeWeight(0.2);
        JPanel footer = new JPanel(new BorderLayout()); footer.add(status, BorderLayout.NORTH); footer.add(taskProgress);
        add(split); add(footer, BorderLayout.SOUTH);
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
        configureFileOperations();
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
            if (!selected.editor.isDisposed() && selected == selectedSession()) {
                if (selected.surface != null && selected.surface.mode() == VisualEditorSurface.Mode.DESIGN) selected.surface.focusContent();
                else selected.editor.getContentComponent().requestFocusInWindow();
            }
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
        ButtonGroup group = new ButtonGroup(); String[] encodings = {"GBK / CP936", "MS949 / CP949", "UTF-8", "Big5 / CP950", "自动（按 PVF 格式）"};
        for (int i = 0; i < encodings.length; i++) {
            int index = i; var item = new JRadioButtonMenuItem(encodings[i], i == encodingIndex);
            item.addActionListener(e -> selectEncoding(index)); group.add(item); encoding.add(item); encodingItems.add(item);
        }
        encoding.setToolTipText("自动：ACT1 使用 GBK、旧版固定目录使用 MS949、UUID 使用 Big5；手动选择用于下一次打开的文件。"); fileMenu.add(encoding);
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
            showResourcePreview = previewVisible.isSelected();
            if (!previewVisible.isSelected()) previewDivider = editorSplit.getDividerLocation();
            previewDock.setVisible(previewVisible.isSelected()); editorSplit.setDividerSize(previewVisible.isSelected() ? UIManager.getInt("SplitPane.dividerSize") : 0);
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
        if (selected.surface != null && selected.surface.mode() == VisualEditorSurface.Mode.DESIGN) selected.surface.setMode(VisualEditorSurface.Mode.SPLIT);
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
            if (session.surface != null && session.surface.mode() == VisualEditorSurface.Mode.DESIGN) session.surface.setMode(VisualEditorSurface.Mode.SPLIT);
            tabs.setSelectedComponent(session.component());
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
            case 4 -> studio.pvf.workspace.PvfTextEncoding.defaultCharset(workspace == null
                    ? studio.pvf.core.PvfFormat.ACT1 : workspace.archive().format());
            case 1 -> Charset.forName("MS949");
            case 3 -> Charset.forName("Big5");
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
        runTask("读取 PVF 目录及草稿…", progress -> {
            if (previous != null) previousState.write(previous.directory());
            Path directory = workspaceDirectory(archive, progress);
            // A timestamp-only change or path alias can retain exactly the same draft identity.
            // Release the old workspace on the UI side before trying to reacquire its lock.
            if (previous != null && previous.directory().equals(directory))
                return new Loaded(null, Map.of(), true, List.of(), "", List.of());
            PvfWorkspace loaded = PvfWorkspace.open(archive, directory, progress);
            try {
                EditorSessionState.ReadResult saved = EditorSessionState.read(directory);
                List<RestoredTab> restored = new ArrayList<>(); List<String> warnings = new ArrayList<>(saved.warnings());
                int restoredCount = 0;
                for (EditorSessionState.Tab tab : saved.state().tabs()) {
                    progress.update("workspace.tabs", restoredCount++, saved.state().tabs().size(), tab.path());
                    try {
                        Charset savedCharset = Charset.forName(tab.charset());
                        Charset actual = studio.pvf.workspace.PvfTextEncoding.restoredCharset(loaded.archive().format(), loaded.readBytes(tab.path()), savedCharset);
                        EditorSessionState.Tab position = tab;
                        if (!actual.equals(savedCharset)) {
                            position = new EditorSessionState.Tab(tab.path(), actual.name(), tab.caret(), tab.scrollX(), tab.scrollY(), tab.viewMode(), tab.splitRatio(), tab.visualState());
                            warnings.add(tab.path() + "：已按韩文标签将旧 GBK 编码修正为 MS949");
                        }
                        restored.add(new RestoredTab(position, readEditorText(loaded, tab.path(), actual)));
                    }
                    catch (Exception invalid) { warnings.add("未能恢复 " + tab.path() + "：" + invalid.getMessage()); }
                }
                return new Loaded(loaded, buildDirectories(loaded, progress), false,
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
                            new ClosedTab(tab.path(), Charset.forName(tab.charset()), tabs.getTabCount(), tab.caret(), tab.scrollX(), tab.scrollY(), tab.viewMode(), tab.splitRatio(), tab.visualState()));
                }
                Session selected = sessions.get(loaded.selectedPath());
                if (selected != null) tabs.setSelectedComponent(selected.component());
                else if (tabs.getTabCount() > 0) tabs.setSelectedIndex(0);
            });
            resumeEditorState();
            rememberArchive(workspace.archive().sourcePath());
            status.setText(workspace.archive().format().description() + " | 已读取 " + workspace.archive().fileCount() + " 个文件；已保存草稿 "
                    + workspace.dirtyPaths().size() + " 个；已恢复 " + tabs.getTabCount() + " 个标签。"
                    + (loaded.warnings().isEmpty() ? "" : "恢复提示：" + String.join("；", loaded.warnings())));
            updateButtons();
        }, this::resumeEditorState);
    }

    private Path workspaceDirectory(Path archive, studio.pvf.core.ProgressListener progress) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(archive.toRealPath().toString().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        long total = Files.size(archive), completed = 0;
        progress.update("workspace.identity", 0, total, archive.getFileName().toString());
        try (java.io.InputStream stream = Files.newInputStream(archive)) {
            byte[] buffer = new byte[1024 * 1024]; int count;
            while ((count = stream.read(buffer)) >= 0) if (count > 0) {
                digest.update(buffer, 0, count); completed += count;
                progress.update("workspace.identity", completed, total, archive.getFileName().toString());
            }
        }
        byte[] hash = digest.digest();
        Path root = draftsRoot == null ? Path.of(System.getProperty("user.home"), ".pvf-studio", "workspaces") : draftsRoot;
        return root.resolve(HexFormat.of().formatHex(hash, 0, 16));
    }

    private static Map<String, List<Item>> buildDirectories(PvfWorkspace workspace, studio.pvf.core.ProgressListener progress) {
        Set<String> entries = workspace.paths(), folders = workspace.folders();
        Set<String> all = new LinkedHashSet<>(entries); all.addAll(folders);
        Map<String, Map<String, Item>> collected = new HashMap<>();
        collected.put("", new HashMap<>());
        int completed = 0; progress.update("workspace.tree", 0, all.size(), "");
        for (String entry : all) {
            String[] parts = entry.split("/"); String parent = "";
            for (int i = 0; i < parts.length; i++) {
                String path = parent.isEmpty() ? parts[i] : parent + "/" + parts[i];
                boolean directory = i < parts.length - 1 || folders.contains(entry);
                collected.computeIfAbsent(parent, unused -> new HashMap<>())
                        .putIfAbsent(parts[i], new Item(parts[i], path, directory));
                if (directory) collected.computeIfAbsent(path, unused -> new HashMap<>());
                parent = path;
            }
            if ((++completed & 1023) == 0 || completed == all.size())
                progress.update("workspace.tree", completed, all.size(), entry);
        }
        Map<String, List<Item>> result = new HashMap<>();
        completed = 0; progress.update("workspace.sort", 0, collected.size(), "");
        for (var directory : collected.entrySet()) {
            result.put(directory.getKey(), directory.getValue().values().stream()
                    .sorted(Comparator.comparing(Item::directory).reversed().thenComparing(Item::label)).toList());
            if ((++completed & 127) == 0 || completed == collected.size())
                progress.update("workspace.sort", completed, collected.size(), directory.getKey());
        }
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

    private void configureFileOperations() {
        for (JComponent navigation : List.of(tree, resultList)) {
            navigation.addMouseListener(new MouseAdapter() {
                private void popup(MouseEvent event) {
                    if (!event.isPopupTrigger() || workspace == null || busy) return;
                    Item item;
                    if (navigation == tree) {
                        var path = tree.getPathForLocation(event.getX(), event.getY());
                        if (path != null) tree.setSelectionPath(path);
                        item = path == null ? new Item("Script.pvf", "", true) : selectedFileItem(tree);
                    } else {
                        int index = resultList.locationToIndex(event.getPoint());
                        if (index >= 0) resultList.setSelectedIndex(index);
                        item = index < 0 || !resultList.getCellBounds(index, index).contains(event.getPoint())
                                ? new Item("Script.pvf", "", true) : selectedFileItem(resultList);
                    }
                    if (item == null) return;
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem file = new JMenuItem("新建文件…"), directory = new JMenuItem("新建文件夹…"), rename = new JMenuItem("重命名…"), delete = new JMenuItem("删除…");
                    file.addActionListener(e -> chooseCreate(item, false)); directory.addActionListener(e -> chooseCreate(item, true));
                    rename.addActionListener(e -> chooseRename(item)); delete.addActionListener(e -> chooseDelete(item));
                    rename.setEnabled(!item.path().isEmpty()); delete.setEnabled(!item.path().isEmpty());
                    menu.add(file); menu.add(directory); menu.addSeparator(); menu.add(rename); menu.add(delete); menu.show(navigation, event.getX(), event.getY());
                }
                @Override public void mousePressed(MouseEvent event) { popup(event); }
                @Override public void mouseReleased(MouseEvent event) { popup(event); }
            });
            navigation.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "pvf-rename");
            navigation.getActionMap().put("pvf-rename", new AbstractAction() { public void actionPerformed(ActionEvent e) { chooseRename(selectedFileItem(navigation)); } });
            navigation.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "pvf-delete");
            navigation.getActionMap().put("pvf-delete", new AbstractAction() { public void actionPerformed(ActionEvent e) { chooseDelete(selectedFileItem(navigation)); } });
            navigation.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), "pvf-create");
            navigation.getActionMap().put("pvf-create", new AbstractAction() { public void actionPerformed(ActionEvent e) { chooseCreate(selectedFileItem(navigation), false); } });
        }
    }
    private Item selectedFileItem(JComponent component) {
        if (component == resultList) { String path = resultList.getSelectedValue(); return path == null ? null : new Item(shortName(path), path, false); }
        if (tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Item item) return item;
        return new Item("Script.pvf", "", true);
    }
    private void chooseCreate(Item item, boolean directory) {
        if (workspace == null || busy || item == null) return;
        String parent = item.directory() ? item.path() : item.path().contains("/") ? item.path().substring(0, item.path().lastIndexOf('/')) : "";
        String path = JOptionPane.showInputDialog(this, "PVF 内的新路径：", directory ? "新建文件夹" : "新建文件", JOptionPane.PLAIN_MESSAGE,
                null, null, (parent.isEmpty() ? "" : parent + "/") + (directory ? "new_folder" : "new.etc")) instanceof String text ? text : null;
        if (path == null) return;
        createEntry(path, directory);
    }
    void createEntry(String path, boolean directory) {
        if (workspace == null || busy) return;
        PvfWorkspace current = workspace; Charset charset = selectedCharset();
        runTask(directory ? "创建文件夹…" : "创建文件…", progress -> {
            if (directory) current.createDirectory(path); else current.createTextFile(path, charset);
            return buildDirectories(current, progress);
        }, updated -> {
            refreshFileTree(updated, path);
            status.setText("已在草稿中创建：" + path + (directory ? "；空文件夹仅保存在工作区，加入文件后随导出写入。" : ""));
            if (!directory) openEntry(path);
        }, () -> {});
    }
    private void chooseRename(Item item) {
        if (workspace == null || busy || item == null || item.path().isEmpty()) return;
        String path = JOptionPane.showInputDialog(this, "PVF 内的新路径（配置正文中的引用不会自动改写）：", item.path());
        if (path != null) renameEntry(item.path(), path);
    }
    void renameEntry(String from, String to) {
        if (workspace == null || busy) return;
        PvfWorkspace current = workspace;
        boolean subtree;
        try { subtree = !current.contains(from); } catch (IOException invalid) { showError(invalid); return; }
        runTask("重命名并保存目录草稿…", progress -> {
            current.rename(from, to); return buildDirectories(current, progress);
        }, updated -> {
            String oldPath, newPath;
            try { oldPath = studio.pvf.core.PvfArchive.normalizePath(from); newPath = studio.pvf.core.PvfArchive.normalizePath(to); }
            catch (IOException invalid) { throw new IllegalStateException(invalid); }
            Map<String, Session> renamed = new LinkedHashMap<>();
            for (Session session : sessions.values()) {
                if (session.path.equals(oldPath) || subtree && within(session.path, oldPath)) {
                    JComponent previous=session.component();String previousType=session.dungeonPanel!=null?"dgn":session.mapPanel!=null?"map":"";
                    session.path = newPath + session.path.substring(oldPath.length());
                    String nextType=session.path.endsWith(".dgn")?"dgn":session.path.endsWith(".map")?"map":"";
                    if(!previousType.equals(nextType)){if(session.bridge!=null)session.bridge.close();session.bridge=null;session.mapPanel=null;session.dungeonPanel=null;configureVisualSession(session);
                        if(session.surface!=null&&nextType.isEmpty()){session.surface.setVisual(new JLabel("当前扩展名使用代码编辑",SwingConstants.CENTER));session.surface.setMode(VisualEditorSurface.Mode.CODE);}
                        int oldIndex=tabs.indexOfComponent(previous);if(oldIndex>=0&&previous!=session.component())tabs.setComponentAt(oldIndex,session.component());
                        session.saveAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_S,InputEvent.CTRL_DOWN_MASK)),session.component());
                        session.undoAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_Z,InputEvent.CTRL_DOWN_MASK)),session.component());
                        session.redoAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_Y,InputEvent.CTRL_DOWN_MASK)),session.component());
                        tabShortcuts.forEach((action,shortcut) -> action.registerCustomShortcutSet(shortcut,session.component()));
                    }
                    int index = tabs.indexOfComponent(session.component());
                    if (index >= 0) { tabs.setToolTipTextAt(index, session.path + " [" + session.charset.name() + "]"); tabs.decorate(session.component(), PvfFileIcons.forEntry(session.path, false, false)); }
                    updateSessionTitle(session);
                    if(session.bridge!=null)session.bridge.refresh();
                }
                renamed.put(session.path, session);
            }
            Map<String,String> origins=new HashMap<>();roomOrigins.forEach((map,dgn) -> origins.put(map.equals(oldPath)||subtree&&within(map,oldPath)?newPath+map.substring(oldPath.length()):map,dgn.equals(oldPath)||subtree&&within(dgn,oldPath)?newPath+dgn.substring(oldPath.length()):dgn));roomOrigins.clear();roomOrigins.putAll(origins);
            sessions.clear(); sessions.putAll(renamed); closedTabs.removeIf(tab -> tab.path().equals(oldPath) || subtree && within(tab.path(), oldPath));
            if (contentSearch != null) { contentSearch.dispose(); contentSearch = null; }
            refreshFileTree(updated, newPath); updatePreview(); scheduleEditorStateSave();
            status.setText("已在草稿中重命名：" + oldPath + " → " + newPath + "；请按需修改配置正文中的引用。");
        }, () -> {});
    }
    private static boolean within(String path, String parent) { return path.equals(parent) || path.startsWith(parent + "/"); }
    private void chooseDelete(Item item) {
        if (workspace == null || busy || item == null || item.path().isEmpty()) return;
        long count = item.directory() ? workspace.paths().stream().filter(path -> within(path, item.path())).count() : 1;
        long unsaved = sessions.values().stream().filter(session -> (session.path.equals(item.path()) || item.directory() && within(session.path, item.path())) && session.isUnsaved()).count();
        if (JOptionPane.showConfirmDialog(this, "从草稿中删除 " + item.path() + "？\n包含 " + count + " 个文件"
                + (unsaved == 0 ? "。" : "，并放弃 " + unsaved + " 个标签页的未保存编辑。")
                + "\n导出时会移除这些条目，原包保持只读。", "删除文件或目录", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION)
            deleteEntry(item.path());
    }
    void deleteEntry(String path) {
        if (workspace == null || busy) return;
        PvfWorkspace current = workspace;
        boolean subtree;
        try { subtree = !current.contains(path); } catch (IOException invalid) { showError(invalid); return; }
        runTask("删除并保存目录草稿…", progress -> {
            current.delete(path); return buildDirectories(current, progress);
        }, updated -> {
            String canonical;
            try { canonical = studio.pvf.core.PvfArchive.normalizePath(path); } catch (IOException invalid) { throw new IllegalStateException(invalid); }
            tabs.runBatch(() -> {
                for (Session session : List.copyOf(sessions.values())) if (session.path.equals(canonical) || subtree && within(session.path, canonical)) {
                    sessions.remove(session.path); tabs.remove(session.component()); releaseSession(session);
                }
            });
            closedTabs.removeIf(tab -> tab.path().equals(canonical) || subtree && within(tab.path(), canonical));
            if (contentSearch != null) { contentSearch.dispose(); contentSearch = null; }
            refreshFileTree(updated, canonical.contains("/") ? canonical.substring(0, canonical.lastIndexOf('/')) : "");
            updatePreview(); scheduleEditorStateSave(); status.setText("已在草稿中删除：" + canonical + "；导出新包后生效。");
        }, () -> {});
    }
    private void refreshFileTree(Map<String,List<Item>> updated, String selection) {
        Set<String> expanded = new TreeSet<>(Comparator.comparingInt(String::length).thenComparing(String::compareTo));
        for (int row = 0; row < tree.getRowCount(); row++) {
            var path = tree.getPathForRow(row);
            if (tree.isExpanded(path) && path.getLastPathComponent() instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Item item) expanded.add(item.path());
        }
        directories = updated;
        DefaultMutableTreeNode root = node(new Item(workspace.archive().sourcePath().getFileName().toString(), "", true));
        tree.setModel(new DefaultTreeModel(root)); loadChildren(root); tree.expandRow(0);
        for (String path : expanded) { var node = findTreeNode(root, path); if (node != null) tree.expandPath(new javax.swing.tree.TreePath(node.getPath())); }
        String normalized = selection.replace('\\', '/').toLowerCase(Locale.ROOT);
        var selected = findTreeNode(root, normalized);
        if (selected != null) { var path = new javax.swing.tree.TreePath(selected.getPath()); tree.setSelectionPath(path); tree.scrollPathToVisible(path); }
        updatePathSearch(); updateButtons();
    }
    private DefaultMutableTreeNode findTreeNode(DefaultMutableTreeNode root, String path) {
        DefaultMutableTreeNode node = root;
        if (path.isEmpty()) return root;
        for (String segment : path.split("/")) {
            loadChildren(node); DefaultMutableTreeNode next = null;
            for (int i = 0; i < node.getChildCount(); i++) if (node.getChildAt(i) instanceof DefaultMutableTreeNode child && child.getUserObject() instanceof Item item && item.label().equals(segment)) { next = child; break; }
            if (next == null) return null; node = next;
        }
        return node;
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
            List<String> matches = current.paths().stream()
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
        String canonical;
        try { canonical = studio.pvf.core.PvfArchive.normalizePath(path); }
        catch (IOException invalid) { showError(invalid); return; }
        Session existing = sessions.get(canonical);
        if (existing != null) { tabs.finishCycle(); tabs.setSelectedComponent(existing.component()); focusSelectedEditor(); return; }
        PvfWorkspace current = workspace;
        runTask("读取 " + canonical + "…", () -> readEditorText(current, canonical, charset),
                raw -> createSession(canonical, charset, raw, restore));
    }

    private static String readEditorText(PvfWorkspace workspace, String path, Charset charset) throws IOException {
        byte[] bytes = workspace.readBytes(path);
        if (studio.pvf.workspace.BinaryAniCodec.isBinary(path, bytes)) {
            try { return workspace.readText(path, charset); }
            catch (studio.pvf.workspace.BinaryAniCodec.FormatException unsupported) {
                return studio.pvf.workspace.BinaryView.render(bytes, unsupported.getMessage());
            }
        }
        if (!studio.pvf.workspace.BinaryScriptCodec.isScript(bytes)) {
            for (byte b : bytes) if (b == 0) return studio.pvf.workspace.BinaryView.render(bytes);
        }
        String raw = workspace.readText(path, charset);
        if (raw.indexOf('\0') >= 0) return studio.pvf.workspace.BinaryView.render(bytes);
        return raw;
    }

    private void createSession(String path, Charset charset, String raw, ClosedTab restore) {
        String normalized = normalizeNewlines(raw);
        // The global undo provider must subscribe before the first edit of an unbound document.
        UndoManager.getGlobalInstance();
        Project editorProject = ProjectManager.getInstance().getDefaultProject();
        Document retained=retainedSceneDocuments.remove(path);
        Document document = retained!=null&&retained.getText().equals(normalized)?retained:EditorFactory.getInstance().createDocument(normalized);
        Newlines lineEndings = new Newlines(raw);
        boolean binary = studio.pvf.workspace.BinaryView.isView(raw);
        Editor editor = binary ? EditorFactory.getInstance().createViewer(document, editorProject)
                : EditorFactory.getInstance().createEditor(document, editorProject);
        editor.getSettings().setLineNumbersShown(showLineNumbers);
        editor.getSettings().setUseSoftWraps(softWraps);
        editor.getSettings().setWhitespacesShown(false);
        if (editor instanceof EditorEx ex) ex.setHighlighter(new LexerEditorHighlighter(
                new PvfSyntaxHighlighter(), EditorColorsManager.getInstance().getGlobalScheme()));
        Session session = new Session(path, charset, raw, editor);
        configureVisualSession(session);
        session.fileEditor = TextEditorProvider.getInstance().getTextEditor(editor);
        session.saveAction = new DumbAwareAction() {
            @Override public void actionPerformed(AnActionEvent event) { if (!busy) saveSessions(List.of(session), null); }
        };
        session.saveAction.registerCustomShortcutSet(new CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)), session.component());
        session.undoAction = createUndoAction(session, false);
        session.undoAction.registerCustomShortcutSet(new CustomShortcutSet(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)), session.component());
        session.redoAction = createUndoAction(session, true);
        session.redoAction.registerCustomShortcutSet(new CustomShortcutSet(
                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), null),
                new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_Z,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), null)),
                session.component());
        tabShortcuts.forEach((action, shortcut) -> action.registerCustomShortcutSet(shortcut, session.component()));
        sessions.put(path, session);
        session.documentListener=new DocumentListener() {
            private String beforeText;
            public void beforeDocumentChange(DocumentEvent event){beforeText=event.getDocument().getText();}
            public void documentChanged(DocumentEvent event) {
                session.newlines.recordChange(beforeText,event.getDocument().getText(),event.getOffset(),event.getOldFragment().toString(),event.getNewFragment().toString(),event.getOldTimeStamp(),event.getDocument().getModificationStamp());
                updateSessionTitle(session); previewRefresh.restart(); scheduleEditorStateSave();
                if (session.bridge != null) session.bridge.refresh();
            }
        };document.addDocumentListener(session.documentListener);
        editor.getCaretModel().addCaretListener(new CaretListener() {
            @Override public void caretPositionChanged(CaretEvent event) { scheduleEditorStateSave();int offset=session.editor.getCaretModel().getOffset();if(session.mapPanel!=null)session.mapPanel.selectSourceOffset(offset);if(session.dungeonPanel!=null)session.dungeonPanel.selectSourceOffset(offset); }
        });
        editor.getScrollingModel().addVisibleAreaListener(event -> scheduleEditorStateSave());
        session.component().addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent event) { restoreSelectedViewport(); }
            @Override public void componentResized(ComponentEvent event) { restoreSelectedViewport(); }
        });
        if (restore != null) {
            if (session.surface != null) session.surface.restore(restore.viewMode(), restore.splitRatio());
            if(session.mapPanel!=null)session.mapPanel.restoreState(restore.visualState());if(session.dungeonPanel!=null)session.dungeonPanel.restoreState(restore.visualState());
            session.pendingViewport = new Point(restore.scrollX(), restore.scrollY());
            editor.getCaretModel().moveToOffset(Math.min(restore.caret(), document.getTextLength()));
        }
        closedTabs.removeIf(tab -> tab.path().equals(path));
        tabs.finishCycle();
        tabs.runBatch(() -> {
            int index = restore == null ? tabs.getTabCount() : Math.min(restore.index(), tabs.getTabCount());
            tabs.insertTab(shortName(path), null, session.component(), path + " [" + charset.name() + "]", index);
            tabs.decorate(session.component(), PvfFileIcons.forEntry(path, false, false)); tabs.setSelectedComponent(session.component());
        });
        status.setText(path + " | " + charset.name() + " | " + session.newlines.description()
                + (binary ? " | 二进制只读预览" : lineEndings.isMixed() ? " | 编辑时保留原有混合换行；Ctrl+S 保存草稿" : " | Ctrl+S 保存草稿；导出生成新包"));
        updateButtons();
    }

    private void configureVisualSession(Session session) {
        if (!(session.path.endsWith(".map") || session.path.endsWith(".dgn")) || studio.pvf.workspace.BinaryView.isView(session.persistedText)) return;
        JLabel visual = new JLabel("正在解析场景…", SwingConstants.CENTER);
        if(session.surface==null)session.surface = new VisualEditorSurface(session.editor.getComponent(), visual, () -> { scheduleEditorStateSave(); previewRefresh.restart(); });
        if(session.path.endsWith(".dgn")) {
            session.dungeonPanel = new DungeonEditorPanel((stamp,edit) -> session.bridge.apply(stamp,edit),
                    map -> openRoomMap(session,map), () -> { scheduleEditorStateSave();if(selectedSession()==session)previewRefresh.restart(); }, range -> revealSource(session,range));
            session.dungeonPanel.onClone(request -> cloneRoomMap(session,request));
            session.dungeonPanel.onHellTemplates(done -> {
                var current=workspace;if(current==null)return;var snapshot=sceneSource(session);var paths=current.paths();var mapIds=session.catalog==null?Set.<Integer>of():session.catalog.maps().keySet();
                sceneWorker.execute(() -> {var templates=new ArrayList<DungeonEditorPanel.HellTemplate>();
                    for(String path:paths.stream().filter(p -> p.endsWith(".dgn")).sorted().toList())try{
                        var document=studio.pvf.sceneedit.DungeonDocument.parse(path,snapshot.lookup().read(path));
                        for(int i=0;i<document.mazes().size();i++){var h=document.hell(i);if(h.enabled()&&!h.excluded()&&h.position()!=null&&h.mapId()>0&&!h.hitCounts().isEmpty()&&mapIds.contains(h.mapId())){templates.add(new DungeonEditorPanel.HellTemplate(path+" #"+(i+1),h));break;}}
                    }catch(Exception ignored){}
                    SwingUtilities.invokeLater(() -> {if(workspace==current&&!session.editor.isDisposed())done.accept(List.copyOf(templates));});
                });
            });
            session.surface.setVisual(session.dungeonPanel);
        } else {
            session.mapPanel=new MapEditorPanel(session.path,(stamp,edit) -> session.bridge.apply(stamp,edit),
                    () -> session.editor.getDocument().getModificationStamp(),this::openEntry,range -> revealSource(session,range),() -> previewRefresh.restart());
            session.mapPanel.onStateChanged(this::scheduleEditorStateSave);
            session.mapPanel.browser().setThumbnailLoader((asset,result) -> preview.thumbnail(sceneSource(session),asset,result));
            session.surface.setVisual(session.mapPanel);
        }
        session.bridge = new SceneDocumentBridge<>(new SceneDocumentBridge.Port() {
            public String text() { return session.editor.getDocument().getText(); }
            public long version() { return session.editor.getDocument().getModificationStamp(); }
            public boolean writable() { return !session.editor.isViewer() && !session.editor.isDisposed() && session.editor.getDocument().isWritable(); }
            public void write(String label, String value) {
                com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(session.editor.getProject(),
                    () -> ApplicationManager.getApplication().runWriteAction(() -> session.editor.getDocument().setText(value)), label, null);
            }
            public void writeEdit(studio.pvf.sceneedit.ScriptEdit edit,String value){
                com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(session.editor.getProject(),() -> ApplicationManager.getApplication().runWriteAction(() -> {
                    var changes=new ArrayList<>(edit.replacements());changes.sort(java.util.Comparator.comparingInt(studio.pvf.sceneedit.ScriptEdit.Replacement::start).reversed());
                    for(var change:changes)session.editor.getDocument().replaceString(change.start(),change.end(),change.value());
                }),edit.label(),null);
            }
        }, text -> {
            var source = studio.pvf.sceneedit.SourceDocument.parse(text);
            if (!source.diagnostics().isEmpty()) throw new IOException(source.diagnostics().getFirst().message());
            return session.path.endsWith(".map") ? studio.pvf.sceneedit.MapDocument.parse(session.path, text)
                    : studio.pvf.sceneedit.DungeonDocument.parse(session.path, text);
        }, sceneWorker, SwingUtilities::invokeLater, state -> {
            if(session.dungeonPanel!=null)session.dungeonPanel.install((studio.pvf.sceneedit.DungeonDocument)state.model(),state.version(),state.writable(),state.error());
            else if(session.mapPanel!=null){session.mapPanel.install((studio.pvf.sceneedit.MapDocument)state.model(),state.version(),state.writable(),state.error());if(state.writable()&&hellMapSelections.remove(session.path))session.mapPanel.selectHellPillar();}
        });
        session.bridge.refresh();
        session.surface.setMode(VisualEditorSurface.Mode.DESIGN);
        decorateRoomOrigin(session);
        loadSceneCatalog(session);
    }

    private void revealSource(Session session, studio.pvf.sceneedit.SourceDocument.Range range) {
        if(session.surface!=null)session.surface.setMode(VisualEditorSurface.Mode.SPLIT);
        session.editor.getCaretModel().moveToOffset(Math.min(range.start(),session.editor.getDocument().getTextLength()));
        session.editor.getScrollingModel().scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER);
        session.editor.getContentComponent().requestFocusInWindow();
    }
    private void openRoomMap(Session origin,String path) {
        roomOrigins.put(path,origin.path); if(origin.dungeonPanel!=null&&origin.bridge.state().model() instanceof studio.pvf.sceneedit.DungeonDocument d&&Objects.equals(origin.catalog.maps().get(d.hell(origin.dungeonPanel.preview().maze()).mapId()),path))hellMapSelections.add(path);openEntry(path);
        Session existing=sessions.get(path); if(existing!=null){decorateRoomOrigin(existing);if(hellMapSelections.remove(path)&&existing.mapPanel!=null)existing.mapPanel.selectHellPillar();}
    }
    private void decorateRoomOrigin(Session session) {
        if(session.surface!=null&&roomOrigins.containsKey(session.path)) session.surface.action("返回副本", "scene-return-dungeon", () -> openEntry(roomOrigins.get(session.path)));
    }
    private void loadSceneCatalog(Session session) {
        if(workspace==null)return;
        var current=workspace;var charset=session.charset; var paths=current.paths();String key=paths.hashCode()+"|"+charset.name()+"|"+sessions.values().stream().map(s -> s.path+":"+s.editor.getDocument().getModificationStamp()).sorted().toList();
        if(key.equals(session.catalogSignature))return;session.catalogSignature=key;var texts=new HashMap<String,String>();
        sessions.forEach((path,tab) -> texts.put(path,tab.editor.getDocument().getText()));
        sceneWorker.execute(() -> {
            try {
            studio.pvf.sceneedit.SceneCatalog.TextLookup lookup=path -> texts.containsKey(path)?texts.get(path):current.readText(path,charset);
            var catalog=studio.pvf.sceneedit.SceneCatalog.build(paths,lookup);
            Map<Integer,studio.pvf.sceneedit.MapBossContent> bossContent=Map.of();
            if(session.dungeonPanel!=null){if(bossCatalogOwner!=current||!charset.equals(bossCatalogCharset)){bossCatalogOwner=current;bossCatalogCharset=charset;bossCatalog=new studio.pvf.sceneedit.MapBossCatalog();}var volatilePaths=new HashSet<>(current.dirtyPaths());volatilePaths.addAll(texts.keySet());bossContent=bossCatalog.classify(catalog,lookup,volatilePaths);}
            var checks=new HashMap<Integer,studio.pvf.sceneedit.DungeonMapChecks.Check>();String baseline=texts.get(session.path);
            if(session.path.endsWith(".dgn"))try{var dungeon=studio.pvf.sceneedit.DungeonDocument.parse(session.path,baseline);for(var maze:dungeon.mazes())for(var room:maze.rooms()){
                var ids=new ArrayList<>(maze.layout().maps().getOrDefault(room,List.of()));ids.addAll(maze.layout().bossMaps().getOrDefault(room,List.of()));for(int id:ids){String path=catalog.maps().get(id);if(path!=null){String text=texts.containsKey(path)?texts.get(path):current.readText(path,session.charset);var check=studio.pvf.sceneedit.DungeonMapChecks.inspect(path,text,maze.layout().bosses().contains(room)||maze.layout().bossMaps().getOrDefault(room,List.of()).contains(id),maze.layout().mask(room));var before=checks.get(id);if(before!=null){var messages=new LinkedHashSet<>(before.messages());messages.addAll(check.messages());check=new studio.pvf.sceneedit.DungeonMapChecks.Check(path,check.info(),List.copyOf(messages));}checks.put(id,check);}}
            }}catch(Exception ignored){}
            var classified=bossContent;
            SwingUtilities.invokeLater(() -> { if(session.editor.isDisposed()||workspace!=current||!key.equals(session.catalogSignature))return;
                session.catalog=catalog; if(session.dungeonPanel!=null){session.dungeonPanel.setCatalog(catalog);session.dungeonPanel.setMapBossContent(classified);if(session.editor.getDocument().getText().equals(baseline))session.dungeonPanel.setMapChecks(checks);}
                if(session.mapPanel!=null)session.mapPanel.setCatalog(catalog);
            });
            } catch(RuntimeException failure) {
                SwingUtilities.invokeLater(() -> { if(!session.editor.isDisposed()&&workspace==current)status.setText("资源索引读取失败："+failure.getMessage()); });
            }
        });
    }

    private void cloneRoomMap(Session origin,DungeonEditorPanel.Preview selected) {
        if(busy||origin.catalog==null||selected.mapId()==null)return;
        String source=origin.catalog.maps().get(selected.mapId()); if(source==null)return;
        String destination=JOptionPane.showInputDialog(this,"同目录的新 MAP 路径；克隆、注册和绑定会一起保存为草稿，可整体撤销。",source.substring(0,source.length()-4)+"_copy.map");
        if(destination!=null)cloneRoomMap(origin.path,selected,destination);
    }
    void cloneRoomMap(String dungeonPath,DungeonEditorPanel.Preview selected,String destination) {
        Session origin=sessions.get(dungeonPath);
        if(busy||workspace==null||origin==null||origin.catalog==null||selected.mapId()==null)return;
        String source=origin.catalog.maps().get(selected.mapId()); if(source==null)return;
        var current=workspace;
        boolean bindHell=selected.hell()&&origin.bridge.state().model() instanceof studio.pvf.sceneedit.DungeonDocument hellDocument&&Objects.equals(selected.position(),hellDocument.hell(selected.maze()).position());
        boolean bindBoss=origin.bridge.state().model() instanceof studio.pvf.sceneedit.DungeonDocument dungeon
                && dungeon.mazes().get(selected.maze()).layout().bosses().contains(selected.position());
        List<Session> affected=sessions.values().stream().filter(s -> s.path.equals(source)||s.path.equals(dungeonPath)||s.path.equals("map/map.lst")).toList();
        Map<Session,Boolean> writable=new LinkedHashMap<>(); Map<Session,Long> stamps=new LinkedHashMap<>(); Map<String,String> raw=new HashMap<>(); Map<Session,String> beforePersisted=new HashMap<>();
        Map<String,Charset> charsets=new HashMap<>(); affected.forEach(s -> charsets.put(s.path,s.charset));
        for(Session s:affected) {
            if(s.editor.isViewer()) { status.setText("涉及只读文档，无法克隆绑定"); return; }
            var doc=s.editor.getDocument(); writable.put(s,doc.isWritable()); stamps.put(s,doc.getModificationStamp());
            raw.put(s.path,s.newlines.restore(doc.getText(),doc.getModificationStamp())); beforePersisted.put(s,s.persistedText);
        }
        affected.forEach(s -> s.editor.getDocument().setReadOnly(true));
        Runnable thaw=() -> writable.forEach((s,value) -> { if(!s.editor.isDisposed()) {
            s.editor.getDocument().setReadOnly(!value); if(s.bridge!=null)s.bridge.refresh();
        } });
        runTask("准备 MAP 克隆与注册…", () -> {
            Charset sourceCharset=charsets.getOrDefault(source,origin.charset);
            String sourceText=raw.containsKey(source)?raw.get(source):current.readText(source,sourceCharset);
            String listText=raw.containsKey("map/map.lst")?raw.get("map/map.lst"):current.readText("map/map.lst",origin.charset);
            var request=new studio.pvf.sceneedit.MapRegistration.Request(current.paths(),source,destination,current.readBytes(source),sourceText,listText,
                    dungeonPath,raw.get(dungeonPath),selected.maze(),selected.position(),bindBoss,null,Integer.MAX_VALUE,bindHell);
            var prepared=MapCloneCommand.prepare(request,origin.charset,current.readBytes("map/map.lst"),current.readBytes(dungeonPath));
            List<PvfWorkspace.TextChange> changes=new ArrayList<>();
            for(var change:prepared.changes()) {
                changes.add(new PvfWorkspace.TextChange(change.path(),change.text(),change.path().equals(prepared.registration().destinationPath())?sourceCharset:charsets.getOrDefault(change.path(),origin.charset),change.expectedBytes()));
            }
            return new MapCloneCommand.Prepared(prepared.registration(),changes,prepared.documentTexts());
        }, prepared -> {
            if(workspace!=current||stamps.entrySet().stream().anyMatch(e -> e.getKey().editor.isDisposed()||e.getKey().editor.getDocument().getModificationStamp()!=e.getValue())) {
                thaw.run(); status.setText("文档已变化，克隆未提交"); return;
            }
            runTask("原子提交 MAP、索引与房间绑定…", () -> prepared.publish(current), batch -> {
                thaw.run();
                try {
                    var touched=affected.stream().filter(s -> prepared.documentTexts().containsKey(s.path)).toList();
                    var beforeRawTexts=Map.of("map/map.lst",prepared.registration().listEdit().baseline(),dungeonPath,prepared.registration().dungeonEdit().baseline());
                    var beforeTexts=new HashMap<String,String>();beforeRawTexts.forEach((path,text) -> beforeTexts.put(path,normalizeNewlines(text)));
                    var afterTexts=new HashMap<String,String>();prepared.documentTexts().forEach((path,text) -> afterTexts.put(path,normalizeNewlines(text)));
                    var originalDocuments=new HashMap<String,Document>();var persistedBefore=new HashMap<>(beforeTexts);
                    touched.forEach(tab -> {originalDocuments.put(tab.path,tab.editor.getDocument());persistedBefore.put(tab.path,beforePersisted.get(tab));});
                    com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(origin.editor.getProject(), () -> {
                        ApplicationManager.getApplication().runWriteAction(() -> {
                            for(Session s:touched) {
                                setTransactionText(s.editor.getDocument(),afterTexts.get(s.path),prepared.documentTexts().get(s.path));
                                s.persistedText=s.editor.getDocument().getText(); s.persistedStamp=s.editor.getDocument().getModificationStamp(); updateSessionTitle(s);
                            }
                        });
                        UndoManager.getGlobalInstance().undoableActionPerformed(new com.intellij.openapi.command.undo.BasicUndoableAction(touched.stream().map(s -> s.editor.getDocument()).toArray(Document[]::new)) {
                            public boolean isGlobal() { return true; }
                            public void undo() throws com.intellij.openapi.command.undo.UnexpectedUndoException { restore(false); }
                            public void redo() throws com.intellij.openapi.command.undo.UnexpectedUndoException { restore(true); }
                            private void restore(boolean after) throws com.intellij.openapi.command.undo.UnexpectedUndoException {
                                try {
                                    if(workspace!=current)throw new IOException("副本工作区已关闭");
                                    Session clone=sessions.get(prepared.registration().destinationPath());
                                    if(!after&&clone!=null&&clone.isUnsaved())throw new IOException("新 MAP 有未保存编辑，请先撤销这些编辑");
                                    var documents=new LinkedHashMap<String,Document>();var texts=new HashMap<String,String>();
                                    for(String path:beforeTexts.keySet()){
                                        Session live=sessions.get(path);Document document=live!=null?live.editor.getDocument():retainedSceneDocuments.getOrDefault(path,originalDocuments.get(path));
                                        if(document!=null){if(!document.isWritable())throw new IOException("关联文档只读："+path);documents.put(path,document);texts.put(path,document.getText());}
                                    }
                                    var targets=SceneDocumentReconciliation.targets(beforeTexts,afterTexts,texts,after);
                                    current.restoreBatch(batch,after);
                                    ApplicationManager.getApplication().runWriteAction(() -> targets.forEach((path,text) -> {
                                        setTransactionText(documents.get(path),text,after?prepared.documentTexts().get(path):beforeRawTexts.get(path));Session live=sessions.get(path);
                                        if(live!=null){live.persistedText=after?afterTexts.get(path):persistedBefore.get(path);live.persistedStamp=-1;updateSessionTitle(live);}
                                    }));
                                    if(!after&&clone!=null){sessions.remove(clone.path);tabs.remove(clone.component());releaseSession(clone);}
                                    refreshSceneStructure(prepared.registration().destinationPath());
                                } catch(IOException failure) { throw new com.intellij.openapi.command.undo.UnexpectedUndoException(failure.getMessage()); }
                            }
                        });
                    },"克隆、注册并绑定 MAP",null,com.intellij.openapi.command.UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
                    refreshSceneStructure(prepared.registration().destinationPath()); status.setText("已克隆 MAP #"+prepared.registration().mapId()+"；Ctrl+Z 可整体撤销");
                } catch(Throwable failure) {
                    try { current.restoreBatch(batch,false); } catch(IOException rollback) { failure.addSuppressed(rollback); }
                    ApplicationManager.getApplication().runWriteAction(() -> affected.forEach(s -> { if(prepared.documentTexts().containsKey(s.path)) {
                        setTransactionText(s.editor.getDocument(),normalizeNewlines(raw.get(s.path)),raw.get(s.path)); s.persistedText=beforePersisted.get(s); s.persistedStamp=-1;
                    } }));
                    showError(failure);
                }
            },thaw);
        },thaw);
    }
    private static void setTransactionText(Document document,String text,String raw){
        // The custom global action owns the entire batch; avoid separate Document actions with different lifetimes.
        com.intellij.openapi.command.undo.UndoUtil.disableUndoIn(document,() -> document.setText(text));
        Newlines lines=document.getUserData(SOURCE_LINE_ENDINGS);if(lines!=null)lines.rememberRaw(raw,document.getModificationStamp());
    }
    private void refreshSceneStructure(String selection) {
        previewSignature="";
        if(workspace==null)return; var current=workspace;
        runTask("刷新文件目录…",progress -> buildDirectories(current,progress), updated -> {
            refreshFileTree(updated,selection); sessions.values().stream().filter(s -> s.surface!=null).forEach(this::loadSceneCatalog); previewRefresh.restart();
        },() -> {});
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
                result.add(new Snapshot(session, text, session.newlines.restore(text,session.editor.getDocument().getModificationStamp()),
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
        List<Snapshot> snapshots = snapshots(); PvfWorkspace current = workspace;Charset fallback=selectedCharset();var encodings=new HashMap<String,Charset>();sessions.forEach((path,session) -> encodings.put(path,session.charset));
        runTask(backup ? "保存草稿、备份旧版本并导出校验 PVF…" : "保存草稿并导出、校验 PVF…", () -> {
            persistSnapshots(current, snapshots);
            for(String path:current.dirtyPaths())if(path.endsWith(".dgn")&&current.contains(path)){
                Charset charset=encodings.getOrDefault(path,fallback);var changed=studio.pvf.sceneedit.DungeonDocument.parse(path,current.readText(path,charset));studio.pvf.sceneedit.DungeonDocument baseline=null;
                try{String original=current.readOriginalText(path,charset);if(!original.isBlank())baseline=studio.pvf.sceneedit.DungeonDocument.parse(path,original);}catch(IOException ignored){}
                var errors=changed.newRoomErrors(baseline);if(!errors.isEmpty())throw new IOException(path+"：新房间尚不可导出；"+errors.getFirst().message()+"。请完成连接后再导出，草稿已保留。");
            }
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
            if (studio.pvf.workspace.BinaryView.isView(session.persistedText))
                return studio.pvf.workspace.BinaryView.render(workspace.readOriginalBytes(session.path));
            return workspace.readOriginalText(session.path, session.charset);
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
    private Session sessionFor(Component component) { return sessions.values().stream().filter(s -> s.component() == component).findFirst().orElse(null); }

    private Map<String,String> visualState(Session session) {
        if(session==previewOwner&&session.dungeonPanel!=null)preview.captureEditorView();
        return session.mapPanel!=null?session.mapPanel.captureState():session.dungeonPanel!=null?session.dungeonPanel.captureState():Map.of();
    }
    private EditorSessionState captureEditorState() {
        List<EditorSessionState.Tab> saved = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Session session = sessionFor(tabs.getComponentAt(i));
            if (session == null || session.editor.isDisposed()) continue;
            Point pending = session.pendingViewport;
            saved.add(new EditorSessionState.Tab(session.path, session.charset.name(), session.editor.getCaretModel().getOffset(),
                    pending == null ? session.editor.getScrollingModel().getHorizontalScrollOffset() : pending.x,
                    pending == null ? session.editor.getScrollingModel().getVerticalScrollOffset() : pending.y,
                    session.surface == null ? "CODE" : session.surface.mode().name(), session.surface == null ? .5 : session.surface.ratio(),visualState(session)));
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

    private ResourcePreviewPanel.Source sceneSource(Session selected) {
        if(workspace==null||selected.editor.isDisposed())return null;
        var current=workspace; Map<String,String> editors=new HashMap<>();
        sessions.forEach((path,session) -> editors.put(path,session.editor.getDocument().getText()));
        var paths=current.paths();
        return new ResourcePreviewPanel.Source(selected.path,selected.editor.getDocument().getText(),path -> {
            String text=editors.get(path); return text!=null?text:paths.contains(path)?current.readText(path,selected.charset):null;
        });
    }

    private void updatePreview() {
        previewRefresh.stop(); Session selected = selectedSession();
        if(previewOwner!=null&&previewOwner.mapPanel!=null)previewOwner.mapPanel.captureLayout();
        if (selected == null || workspace == null) { preview.setSource(null); previewOwner=null; return; }
        if (studio.pvf.workspace.BinaryView.isView(selected.persistedText)) { preview.setSource(null); return; }
        if(selected.surface!=null)loadSceneCatalog(selected);
        var source=sceneSource(selected);
        boolean design=selected.surface!=null&&selected.surface.mode()!=VisualEditorSurface.Mode.CODE;
        JPanel host=design?(selected.dungeonPanel!=null?selected.dungeonPanel.previewHost():selected.mapPanel.previewHost()):previewDock;
        if(preview.getParent()!=host) { if(preview.getParent()!=null)preview.getParent().remove(preview); host.add(preview); host.revalidate(); }
        previewDock.setVisible(!design&&showResourcePreview);
        editorSplit.setDividerSize(previewDock.isVisible()?UIManager.getInt("SplitPane.dividerSize"):0);
        if(previewDock.isVisible())editorSplit.setDividerLocation(previewDivider);
        Object parsed=selected.bridge==null?null:selected.bridge.state().model();String parsedText=parsed instanceof studio.pvf.sceneedit.MapDocument m?m.source().text():parsed instanceof studio.pvf.sceneedit.DungeonDocument d?d.source().text():"";
        String signature=parsedText+"|"+selected.path+"|"+design+"|"+(selected.bridge==null||selected.bridge.state().model()==null)+"|"+(selected.dungeonPanel==null?"":selected.dungeonPanel.preview())+"|"+sessions.values().stream().map(s -> s.path+":"+s.editor.getDocument().getModificationStamp()).sorted().toList();
        if(previewOwner==selected&&signature.equals(previewSignature))return;
        previewSignature=signature;
        if(design) {
            if(selected.bridge.state().model() instanceof studio.pvf.sceneedit.DungeonDocument document)
                preview.setDungeonEditorSource(new ResourcePreviewPanel.Source(selected.path,document.source().text(),source.lookup()),selected.dungeonPanel.preview(),selected.dungeonPanel::resolvedChoices,selected.dungeonPanel);
            else if(selected.bridge.state().model() instanceof studio.pvf.sceneedit.MapDocument document)
                preview.setMapEditorSource(new ResourcePreviewPanel.Source(selected.path,document.source().text(),source.lookup()),selected.mapPanel);
            else preview.setSource(null);
        } else preview.setSource(source);
        previewOwner=selected;
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
            List<Component> components = targets.stream().map(s -> (Component) s.component()).toList();
            Component preferred = tabs.preferredAfterClosing(components);
            List<ClosedTab> history = targets.stream().map(s -> new ClosedTab(s.path, s.charset, tabs.indexOfComponent(s.component()),
                    s.editor.getCaretModel().getOffset(), s.pendingViewport == null ? s.editor.getScrollingModel().getHorizontalScrollOffset() : s.pendingViewport.x,
                    s.pendingViewport == null ? s.editor.getScrollingModel().getVerticalScrollOffset() : s.pendingViewport.y, s.surface == null ? "CODE" : s.surface.mode().name(), s.surface == null ? .5 : s.surface.ratio(),visualState(s))).toList();
            tabs.runBatch(() -> {
                for (Session session : targets) {
                    sessions.remove(session.path); tabs.remove(session.component()); releaseSession(session);
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
        int index = tabs.indexOfComponent(session.component());
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
        io.shutdown(); sceneWorker.shutdownNow(); dispose(); if (exitOnClose) ApplicationManager.getApplication().exit();
    }
    @Override public void dispose() {
        taskProgress.clear();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(tabKeyDispatcher);
        tabShortcuts.keySet().forEach(action -> action.unregisterCustomShortcutSet(getRootPane())); super.dispose();
    }

    private void releaseEditors() {
        previewOwner=null;
        sessionSave.stop();
        previewRefresh.stop(); preview.setSource(null);
        for (JDialog dialog : List.copyOf(diffDialogs.keySet())) { dialog.dispose(); disposeDiff(dialog); }
        for (Session session : sessions.values()) releaseSession(session);
        sessions.clear(); tabs.removeAll(); closedTabs.clear();retainedSceneDocuments.clear();hellMapSelections.clear();roomOrigins.clear();
    }

    private void releaseSession(Session session) {
        if(session.documentListener!=null)session.editor.getDocument().removeDocumentListener(session.documentListener);
        if(session.surface!=null&&!session.isUnsaved()){retainedSceneDocuments.put(session.path,session.editor.getDocument());while(retainedSceneDocuments.size()>100)retainedSceneDocuments.remove(retainedSceneDocuments.keySet().iterator().next());}
        EditorSearchSession search = EditorSearchSession.get(session.editor); if (search != null) search.close();
        tabShortcuts.keySet().forEach(action -> action.unregisterCustomShortcutSet(session.component()));
        if (session.saveAction != null) session.saveAction.unregisterCustomShortcutSet(session.component());
        if (session.undoAction != null) session.undoAction.unregisterCustomShortcutSet(session.component());
        if (session.redoAction != null) session.redoAction.unregisterCustomShortcutSet(session.component());
        if (session.bridge != null) session.bridge.close();
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
        runTask(label, ignored -> task.run(), success, onFailure);
    }

    private <T> void runTask(String label, ProgressTask<T> task, Consumer<T> success, Runnable onFailure) {
        busy = true; status.setText(label); updateButtons();
        TaskProgress.Ticket progress = taskProgress.start(label);
        io.execute(() -> {
            try {
                T value = task.run(progress::update);
                ApplicationManager.getApplication().invokeLater(() -> {
                    taskProgress.finish(progress);
                    busy = false; updateButtons();
                    try { success.accept(value); }
                    catch (Throwable callbackFailure) { onFailure.run(); showError(callbackFailure); }
                });
            } catch (Throwable failure) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    taskProgress.finish(progress);
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
    private record ClosedTab(String path, Charset charset, int index, int caret, int scrollX, int scrollY, String viewMode, double splitRatio,Map<String,String> visualState) {}
    @FunctionalInterface private interface IoTask<T> { T run() throws Exception; }
    @FunctionalInterface private interface ProgressTask<T> { T run(studio.pvf.core.ProgressListener progress) throws Exception; }

    private static final class Session {
        String path; final Charset charset; final Editor editor; final Newlines newlines;
        DocumentListener documentListener;
        AnAction saveAction;
        AnAction undoAction;
        AnAction redoAction;
        TextEditor fileEditor;
        VisualEditorSurface surface;
        SceneDocumentBridge<Object> bridge;
        DungeonEditorPanel dungeonPanel;
        MapEditorPanel mapPanel;
        studio.pvf.sceneedit.SceneCatalog catalog;
        String catalogSignature="";
        JComponent component() { return surface == null ? editor.getComponent() : surface; }
        String persistedText;
        long persistedStamp;
        Point pendingViewport;
        Session(String path, Charset charset, String raw, Editor editor) {
            this.path = path; this.charset = charset; this.editor = editor;
            Newlines held=editor.getDocument().getUserData(SOURCE_LINE_ENDINGS);this.newlines=held==null?new Newlines(raw,editor.getDocument().getModificationStamp()):held;this.newlines.rememberRaw(raw,editor.getDocument().getModificationStamp());editor.getDocument().putUserData(SOURCE_LINE_ENDINGS,this.newlines); this.persistedText = editor.getDocument().getText();
            this.persistedStamp = editor.getDocument().getModificationStamp();
        }
        boolean isUnsaved() { return persistedStamp != editor.getDocument().getModificationStamp() && !persistedText.equals(editor.getDocument().getText()); }
    }

    /** Newline identity follows document edit ranges and revision stamps, including native undo/redo. */
    static final class Newlines {
        private record State(String fingerprint,byte[] endings) {}
        private final Map<Long,State> revisions=new HashMap<>();
        private final String dominant,initialNormalized;
        private final long initialStamp;
        private final boolean mixed;
        Newlines(String raw){this(raw,0);}
        Newlines(String raw,long stamp){
            initialNormalized=normalize(raw);initialStamp=stamp;byte[] kinds=endings(raw);int[] counts=new int[3];for(byte kind:kinds)counts[kind]++;
            dominant=counts[0]>=counts[1]&&counts[0]>=counts[2]?"\r\n":counts[1]>=counts[2]?"\n":"\r";
            mixed=java.util.stream.IntStream.of(counts).filter(n -> n>0).count()>1;rememberRaw(raw,stamp);
        }
        private static String normalize(String raw){return raw.replace("\r\n","\n").replace('\r','\n');}
        private static String fingerprint(String text){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
        private static byte[] endings(String raw){var result=new java.io.ByteArrayOutputStream();for(int i=0;i<raw.length();i++){char c=raw.charAt(i);if(c=='\r'){if(i+1<raw.length()&&raw.charAt(i+1)=='\n'){result.write(0);i++;}else result.write(2);}else if(c=='\n')result.write(1);}return result.toByteArray();}
        private static int count(String text,int end){int n=0;for(int i=0;i<end;i++)if(text.charAt(i)=='\n')n++;return n;}
        void rememberRaw(String raw,long stamp){revisions.put(stamp,new State(fingerprint(normalize(raw)),endings(raw)));}
        void recordChange(String before,String after,int offset,String removed,String added,long oldStamp,long newStamp){
            State old=revisions.get(oldStamp);if(old==null||!old.fingerprint.equals(fingerprint(before)))throw new IllegalStateException("未找到编辑前的原始行尾版本");
            String digest=fingerprint(after);State known=revisions.get(newStamp);if(known!=null&&known.fingerprint.equals(digest))return;
            if(offset<0||offset+removed.length()>before.length()||!before.substring(offset,offset+removed.length()).equals(removed))throw new IllegalStateException("编辑范围与行尾版本不一致");
            int prefix=count(before,offset),deleted=count(removed,removed.length()),inserted=count(added,added.length());byte[] kinds=old.endings;
            if(deleted!=0||inserted!=0){kinds=new byte[old.endings.length-deleted+inserted];System.arraycopy(old.endings,0,kinds,0,prefix);java.util.Arrays.fill(kinds,prefix,prefix+inserted,(byte)(dominant.equals("\r\n")?0:dominant.equals("\n")?1:2));System.arraycopy(old.endings,prefix+deleted,kinds,prefix+inserted,old.endings.length-prefix-deleted);}
            revisions.put(newStamp,new State(digest,kinds));
        }
        String restore(String text,long stamp){
            State state=revisions.get(stamp);if(state==null||!state.fingerprint.equals(fingerprint(text)))throw new IllegalStateException("未找到保存内容对应的原始行尾版本");
            var raw=new StringBuilder(text.length()+state.endings.length);int line=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='\n'){byte kind=state.endings[line++];raw.append(kind==0?"\r\n":kind==1?"\n":"\r");}else raw.append(c);}return raw.toString();
        }
        String formatAdded(String text){return text.replace("\n",dominant);}
        String restore(String text){if(initialNormalized.equals(text))return restore(text,initialStamp);if(!mixed)return text.replace("\n",dominant);throw new IllegalStateException("混合行尾需要对应的文档编辑版本");}
        String description(){return mixed?"混合换行（保留原行尾）":dominant.equals("\r\n")?"CRLF":dominant.equals("\n")?"LF":"CR";}
        boolean isMixed(){return mixed;}
    }
}
