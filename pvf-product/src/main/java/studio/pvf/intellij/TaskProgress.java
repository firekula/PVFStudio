package studio.pvf.intellij;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** One latest-value mailbox per task: workers never enqueue a Swing event per file/byte chunk. */
final class TaskProgress extends JPanel {
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel stageLabel = new JLabel(" ");
    private final JLabel detail = new JLabel(" ");
    private final Timer timer = new Timer(100, e -> refresh());
    private Ticket current;

    TaskProgress(String name) {
        super(new BorderLayout(0, 1)); setName(name); bar.setName(name + "-bar");
        bar.setStringPainted(false); bar.setBorderPainted(false); bar.setPreferredSize(new Dimension(0, 7));
        bar.getAccessibleContext().setAccessibleName("加载进度"); stageLabel.getAccessibleContext().setAccessibleName("当前加载阶段");
        stageLabel.setPreferredSize(new Dimension(0, 17));
        detail.setPreferredSize(new Dimension(0, 16));
        add(stageLabel, BorderLayout.NORTH); add(bar); add(detail, BorderLayout.SOUTH);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 44)); setVisible(false);
    }

    Ticket start(String label) { return start(label, 0); }
    Ticket start(String label, int delayMillis) {
        if (current != null) current.active = false;
        current = new Ticket(label, delayMillis); setVisible(delayMillis == 0);
        refresh(); timer.start(); return current;
    }

    void finish(Ticket ticket) {
        ticket.active = false;
        if (current != ticket) return;
        current = null; timer.stop(); bar.setIndeterminate(false); setVisible(false);
    }
    void clear() { if (current != null) finish(current); }
    boolean running() { return current != null; }
    JProgressBar bar() { return bar; }

    void refresh() {
        Ticket task = current; if (task == null) return;
        long elapsed = System.nanoTime() - task.started;
        if (!isVisible() && elapsed >= task.delayMillis * 1_000_000L) setVisible(true);
        Event event = task.latest.get(); boolean known = event.total > 0;
        bar.setIndeterminate(!known);
        long done = Math.max(0, event.completed), total = event.total;
        int value = known ? (int) Math.min(1000, done * 1000.0 / total) : 0;
        bar.setValue(value);
        String counts = known ? value / 10 + "% · " + amount(done, event.stage) + " / " + amount(total, event.stage)
                : done > 0 ? "已处理 " + amount(done, event.stage) : "正在处理";
        String text = label(event.stage) + " · " + counts + " · " + String.format(Locale.ROOT, "%.1f 秒", elapsed / 1e9);
        bar.setString(text); stageLabel.setText(text); stageLabel.setToolTipText(text);
        detail.setText(event.detail.isEmpty() ? " " : " · " + event.detail);
        bar.setToolTipText("当前阶段进度；阶段切换时重新计数。" + event.detail); detail.setToolTipText(event.detail);
    }

    private static String amount(long value, String stage) {
        if (stage.equals("workspace.identity") || stage.equals("pvf.directory.read") || stage.equals("pvf.hash") || stage.equals("pvf.snapshot"))
            return String.format(Locale.ROOT, "%.1f MiB", value / 1048576.0);
        return String.format(Locale.ROOT, "%,d", value);
    }
    private static String label(String stage) {
        return switch (stage) {
            case "workspace.identity" -> "识别工作区";
            case "pvf.header" -> "识别 PVF 格式";
            case "pvf.directory.read" -> "读取配置目录";
            case "pvf.directory.decode" -> "解密并校验目录";
            case "pvf.entries" -> "解析文件目录";
            case "pvf.index" -> "整理文件索引";
            case "pvf.hash" -> "校验 PVF 内容";
            case "pvf.snapshot" -> "确认源文件完整性";
            case "pvf.ready" -> "PVF 读取完成";
            case "workspace.drafts" -> "载入已有草稿";
            case "workspace.tabs" -> "恢复编辑标签";
            case "workspace.tree" -> "构建文件树";
            case "workspace.sort" -> "整理文件目录";
            case "resources.discover" -> "查找资源文件";
            case "resources.index" -> "索引 NPK / IMG";
            case "resources.finish" -> "整理资源索引";
            default -> stage;
        };
    }
    static final class Ticket {
        private final long started = System.nanoTime();
        private final int delayMillis;
        private final AtomicReference<Event> latest;
        private volatile boolean active = true;
        private Ticket(String label, int delayMillis) {
            this.delayMillis = delayMillis; latest = new AtomicReference<>(new Event(label, 0, -1, ""));
        }
        void update(String stage, long completed, long total, String detail) {
            if (active) latest.set(new Event(stage, completed, total, detail == null ? "" : detail));
        }
    }
    private record Event(String stage, long completed, long total, String detail) {}
}
