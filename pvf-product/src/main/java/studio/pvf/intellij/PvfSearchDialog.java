package studio.pvf.intellij;

import studio.pvf.workspace.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Searches archive/drafts plus a fresh snapshot of open documents without opening every entry. */
final class PvfSearchDialog extends JDialog {
    record Source(String text, Charset charset) {}
    interface OpenHit { void open(PvfTextSearch.Hit hit, Charset charset, String query, boolean matchCase); }
    private final JTextField query = new JTextField(28), filter = new JTextField(18);
    private final JCheckBox matchCase = new JCheckBox("区分大小写");
    private final JComboBox<String> encoding = new JComboBox<>(new String[]{"GBK", "MS949", "UTF-8", "Big5"});
    private final DefaultListModel<PvfTextSearch.Hit> results = new DefaultListModel<>();
    private final JList<PvfTextSearch.Hit> list = new JList<>(results);
    private final JLabel status = new JLabel("搜索当前 PVF 正文，包含草稿和未保存编辑。双击结果跳转。 ");
    private final JButton cancel = new JButton("取消搜索");
    private final TaskProgress searchProgress = new TaskProgress("search-progress");
    private AtomicBoolean cancellation = new AtomicBoolean();
    private long version;
    private Map<String, Source> searchedSources = Map.of();
    private Charset searchedCharset;
    private String searchedQuery = "";
    private boolean searchedCase;

    PvfSearchDialog(JFrame owner, Executor io, PvfWorkspace workspace, Charset charset,
                    Supplier<Map<String, Source>> snapshots, OpenHit open) {
        super(owner, "全局正文搜索", false); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        for (int i = 0; i < encoding.getItemCount(); i++) if (Charset.forName(encoding.getItemAt(i)).equals(charset)) encoding.setSelectedIndex(i);
        JPanel fields = new JPanel(new GridLayout(0, 1, 4, 4));
        JPanel first = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton search = new JButton("搜索"); first.add(new JLabel("内容")); first.add(query); first.add(matchCase); first.add(search); first.add(cancel);
        JPanel second = new JPanel(new FlowLayout(FlowLayout.LEFT)); second.add(new JLabel("路径包含")); second.add(filter);
        second.add(new JLabel("未打开文件编码")); second.add(encoding); fields.add(first); fields.add(second);
        JPanel footer = new JPanel(new BorderLayout()); footer.add(status, BorderLayout.NORTH); footer.add(searchProgress);
        add(fields, BorderLayout.NORTH); add(new JScrollPane(list), BorderLayout.CENTER); add(footer, BorderLayout.SOUTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        Runnable navigate = () -> { var hit = list.getSelectedValue(); if (hit != null) {
            var source = searchedSources.get(hit.path()); open.open(hit, source == null ? searchedCharset : source.charset(), searchedQuery, searchedCase);
        }};
        list.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) navigate.run(); }});
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        list.getActionMap().put("open", new AbstractAction() { public void actionPerformed(ActionEvent e) { navigate.run(); }});
        Runnable start = () -> {
            if (query.getText().isEmpty()) { status.setText("请输入搜索内容。"); query.requestFocusInWindow(); return; }
            cancellation.set(true); AtomicBoolean token = new AtomicBoolean(); cancellation = token; long generation = ++version;
            results.clear(); searchedSources = Map.copyOf(snapshots.get()); searchedCharset = Charset.forName((String) encoding.getSelectedItem());
            searchedQuery = query.getText(); searchedCase = matchCase.isSelected();
            var sources = searchedSources; var selectedCharset = searchedCharset; String needle = searchedQuery, pathFilter = filter.getText(); boolean sensitive = searchedCase;
            TaskProgress.Ticket progress = searchProgress.start("准备全文搜索");
            status.setText("正在搜索…"); cancel.setEnabled(true);
            io.execute(() -> {
                try {
                    String normalizedFilter = pathFilter.toLowerCase(Locale.ROOT).trim();
                    var paths = workspace.paths().stream()
                            .filter(path -> path.toLowerCase(Locale.ROOT).contains(normalizedFilter)).sorted().toList();
                    progress.update("搜索配置内容", 0, paths.size(), needle);
                    var result = PvfTextSearch.scan(paths, path -> {
                        Source source = sources.get(path); if (source != null) return BinaryView.isView(source.text()) ? null : source.text();
                        if (workspace.length(path) > 16 * 1024 * 1024) return null;
                        try { return workspace.readText(path, selectedCharset); }
                        catch (BinaryAniCodec.FormatException unsupported) { return null; }
                    }, needle, "", sensitive, 5000, token::get,
                            count -> progress.update("搜索配置内容", count, paths.size(), needle));
                    SwingUtilities.invokeLater(() -> {
                        searchProgress.finish(progress);
                        if (version != generation || !isDisplayable()) return;
                        results.addAll(result.hits()); cancel.setEnabled(false);
                        status.setText((result.cancelled() ? "已取消，部分结果：" : result.limited() ? "达到 5000 条上限，部分结果：" : "搜索完成：")
                                + result.hits().size() + " 条，已检查 " + result.scanned() + " 个文件；跳过 " + result.skipped() + " 个编码不兼容、二进制或超大文件");
                    });
                } catch (Exception failure) { SwingUtilities.invokeLater(() -> {
                    searchProgress.finish(progress);
                    if (version == generation && isDisplayable()) { cancel.setEnabled(false); status.setText("搜索失败：" + failure.getMessage()); }
                }); }
            });
        };
        search.addActionListener(e -> start.run()); query.addActionListener(e -> start.run()); filter.addActionListener(e -> start.run());
        cancel.setEnabled(false); cancel.addActionListener(e -> { cancellation.set(true); status.setText("正在取消…"); });
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(1080, 580); setLocationRelativeTo(owner);
    }
    void focusQuery(String selected) { if (selected != null && !selected.isEmpty()) query.setText(selected); query.requestFocusInWindow(); query.selectAll(); }
    @Override public void dispose() { cancellation.set(true); version++; searchProgress.clear(); super.dispose(); }
}
