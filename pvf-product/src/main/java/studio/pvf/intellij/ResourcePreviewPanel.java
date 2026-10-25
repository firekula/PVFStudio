package studio.pvf.intellij;

import studio.pvf.preview.NpkResources;
import studio.pvf.preview.PreviewRenderer;
import studio.pvf.preview.ResourceScript;
import studio.pvf.preview.ScenePreview;
import studio.pvf.preview.AnimationTimeline;
import studio.pvf.preview.EntityScript;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One active preview, with its own background queue and stale-result guards. All public calls use EDT. */
final class ResourcePreviewPanel extends JPanel implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "pvf-resource-preview"); thread.setDaemon(true); return thread;
    });
    private final JButton choose = new JButton("选择 NPK 目录");
    private final JButton refresh = new JButton("刷新预览");
    private final JLabel root = new JLabel("选择客户端或 ImagePacks2 目录");
    private final JComboBox<Object> references = new JComboBox<>();
    private final JTextField variants = new JTextField("0,0", 5);
    private final JButton play = new JButton("播放");
    private final JButton previous = new JButton("◀");
    private final JButton next = new JButton("▶");
    private final JCheckBox loop = new JCheckBox("循环");
    private final JComboBox<String> speed = new JComboBox<>(new String[]{"0.25×", "0.5×", "1×", "2×", "4×"});
    private final JComboBox<String> zoom = new JComboBox<>(new String[]{"自适应", "25%", "50%", "100%", "200%", "400%"});
    private final JCheckBox collision = new JCheckBox("碰撞区域");
    private final JCheckBox entityMarkers = new JCheckBox("对象标记");
    private final JCheckBox conditionalEntities = new JCheckBox("条件怪物");
    private final JLabel legend = new JLabel("红：禁行 · 橙：仅浮空 · 蓝框：城镇可移动区域");
    private final JSlider timeline = new JSlider(0, 0, 0);
    private final JLabel frameLabel = new JLabel("尚未选择配置");
    private final JTextArea details = new JTextArea(5, 24);
    private final Canvas canvas = new Canvas();
    private final javax.swing.Timer playback = new javax.swing.Timer(100, e -> advance());
    private Future<?> job;
    private NpkResources resources;
    private Source source;
    private ResourceScript.Animation animation;
    private String animationPath;
    private ScenePreview scene;
    private AnimationTimeline.Clock sceneClock = new AnimationTimeline.Clock();
    private long timelineStart;
    private long generation;
    private long directoryGeneration;
    private boolean changing, playing, closed;
    private int currentFrame;
    private Path settings;
    private Path archiveParent;

    ResourcePreviewPanel() {
        super(new BorderLayout(0, 6)); setMinimumSize(new Dimension(300, 150));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JPanel header = new JPanel(); header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actions.add(new JLabel("资源预览")); actions.add(choose); actions.add(refresh);
        header.add(actions); header.add(Box.createVerticalStrut(6)); header.add(root);
        references.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        references.setToolTipText("选择当前配置中的图标、图片或 ANI 引用");
        header.add(Box.createVerticalStrut(6)); header.add(references);
        JPanel parameters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        variants.setToolTipText("替换 IMG 路径中的 %02d 等占位符；按顺序输入逗号分隔的整数，回车应用，例如 0,0");
        parameters.add(new JLabel("外观参数")); parameters.add(variants);
        parameters.add(new JLabel("缩放")); parameters.add(zoom); header.add(parameters);
        JPanel overlays = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        collision.setToolTipText("叠加 TIL 的 16×12 通行格和被动对象 [width]/[pass type] 碰撞范围。红色不可通行，橙色仅浮空可通行；蓝框是城镇 [virtual movable area] 可移动区域。");
        entityMarkers.setToolTipText("显示所有实体的脚下原点及编号；没有可用待机动画的对象始终显示位置标记。");
        conditionalEntities.setToolTipText("同时预览 monster condition 中的候选怪物；不判断游戏中的出现条件，每条配置仅放置一个。");
        overlays.add(collision); overlays.add(entityMarkers); overlays.add(conditionalEntities); header.add(overlays);
        legend.setVisible(false); header.add(legend);
        add(header, BorderLayout.NORTH); add(canvas);
        JPanel footer = new JPanel(); footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        controls.add(previous); controls.add(play); controls.add(next); controls.add(loop); controls.add(speed);
        speed.setSelectedIndex(2); footer.add(controls); footer.add(timeline); footer.add(frameLabel);
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        details.setOpaque(false); details.setFont(UIManager.getFont("Label.font"));
        details.setText("打开 MAP/TIL 预览完整场景，打开 ANI/PTL 播放动画或粒子。\nNPC、怪物、被动对象和粒子共用场景时钟。\n拖动平移、滚轮缩放；碰撞层可查看地块及对象碰撞范围。");
        footer.add(new JScrollPane(details)); add(footer, BorderLayout.SOUTH);
        choose.addActionListener(e -> chooseDirectory());
        refresh.addActionListener(e -> setSource(source));
        references.addActionListener(e -> { if (!changing) loadSelection(); });
        variants.addActionListener(e -> loadSelection());
        collision.addActionListener(e -> {
            canvas.overlay = collision.isSelected(); legend.setVisible(scene != null);
            legend.setEnabled(collision.isSelected()); canvas.repaint();
        });
        loop.addActionListener(e -> { if (scene != null) renderScene(); });
        entityMarkers.addActionListener(e -> { canvas.markers = entityMarkers.isSelected(); canvas.repaint(); });
        conditionalEntities.addActionListener(e -> { if (scene != null) renderScene(); });
        play.addActionListener(e -> {
            if (playing) pause();
            else if (scene != null) { playing = true; sceneClock.play(); play.setText("暂停"); renderScene(); }
            else if (animation != null) {
                playing = true; play.setText("暂停");
                if (currentFrame == animation.frames().size() - 1) showFrame(0); else scheduleNext();
            }
        });
        previous.addActionListener(e -> {
            pause(); if (scene != null) { sceneClock.seek(Math.max(0, sceneClock.milliseconds() - 16)); renderScene(); }
            else showFrame(Math.max(0, currentFrame - 1));
        });
        next.addActionListener(e -> {
            pause(); if (scene != null) { sceneClock.seek(sceneClock.milliseconds() + 16); renderScene(); }
            else if (animation != null) showFrame(Math.min(animation.frames().size() - 1, currentFrame + 1));
        });
        timeline.addChangeListener(e -> {
            if (!changing && scene != null) { pause(); sceneClock.seek(timelineStart + timeline.getValue()); renderScene(); }
            else if (!changing && animation != null) { pause(); showFrame(timeline.getValue()); }
        });
        speed.addActionListener(e -> { sceneClock.speed(speedFactor()); if (playing) scheduleNext(); });
        zoom.addActionListener(e -> { canvas.zoom = zoom.getSelectedIndex(); canvas.resetView(); canvas.repaint(); });
        playback.setRepeats(false); updateControls();
    }

    void mount(Path directory, Path parent) {
        settings = directory.resolve("preview.properties"); archiveParent = parent;
        choose.setEnabled(true);
        root.setText(resources == null ? "选择客户端或 ImagePacks2 目录"
                : resources.packCount() + " 个 NPK / " + resources.imageCount() + " 个 IMG");
        long version = ++directoryGeneration; Path config = settings;
        worker.execute(() -> {
            Path saved = null;
            try {
                Properties properties = new Properties();
                if (Files.isRegularFile(config)) {
                    try (var stream = Files.newInputStream(config)) { properties.load(stream); }
                    String value = properties.getProperty("npk.directory");
                    if (value != null) saved = Path.of(value);
                } else if (parent != null && Files.isDirectory(parent.resolve("ImagePacks2"))) saved = parent.resolve("ImagePacks2");
            } catch (IOException | RuntimeException ignored) { /* A stale preference must not prevent editing. */ }
            Path selected = saved;
            SwingUtilities.invokeLater(() -> { if (!closed && version == directoryGeneration && selected != null) scan(selected); });
        });
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("选择客户端或 ImagePacks2 目录（只读）");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        Path initial = resources == null ? archiveParent : resources.directory();
        if (initial != null && Files.isDirectory(initial)) chooser.setCurrentDirectory(initial.toFile());
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            ++directoryGeneration; scan(chooser.getSelectedFile().toPath());
        }
    }

    // Also used by the product smoke check, with the same indexing/rendering path as the picker.
    void scan(Path directory) {
        long version = ++directoryGeneration; Path config = settings;
        root.setText("正在索引 NPK…"); root.setToolTipText(directory.toString()); choose.setEnabled(false);
        worker.execute(() -> {
            try {
                NpkResources indexed = NpkResources.scan(directory); String preferenceWarning = "";
                if (config != null) {
                    try {
                        Properties properties = new Properties(); properties.setProperty("npk.directory", indexed.directory().toString());
                        try (var stream = Files.newOutputStream(config)) { properties.store(stream, "PVF Studio resource preview"); }
                    } catch (IOException failure) { preferenceWarning = "；目录设置保存失败"; }
                }
                String suffix = preferenceWarning;
                SwingUtilities.invokeLater(() -> {
                    if (closed || version != directoryGeneration) return;
                    resources = indexed; choose.setEnabled(true);
                    root.setText(indexed.packCount() + " 个 NPK / " + indexed.imageCount() + " 个 IMG"
                            + (indexed.warnings().isEmpty() ? "" : " / " + indexed.warnings().size() + " 条索引提示") + suffix);
                    root.setToolTipText("<html>" + html(indexed.directory().toString()) + "<br>"
                            + html(String.join("\n", indexed.warnings().stream().limit(12).toList())).replace("\n", "<br>") + "</html>");
                    setSource(source);
                });
            } catch (Exception failure) {
                SwingUtilities.invokeLater(() -> {
                    if (closed || version != directoryGeneration) return;
                    choose.setEnabled(true); root.setText(resources == null ? "NPK 索引失败" : "索引失败，仍使用之前的 NPK 目录");
                    root.setToolTipText(message(failure)); fail(failure);
                });
            }
        });
    }

    void setSource(Source value) {
        source = value; invalidatePreview();
        Object selected = references.getSelectedItem(); String selectedName = selected == null ? "" : selected.toString();
        changing = true; references.removeAllItems(); changing = false;
        if (source == null) { frameLabel.setText("尚未选择配置"); details.setText("打开 .ani 或包含图片、动画引用的配置后预览。"); return; }
        Source snapshot = source;
        if (snapshot.path().toLowerCase(Locale.ROOT).endsWith(".ani")) {
            changing = true; references.addItem("当前动画 · " + snapshot.path()); changing = false;
            loadSelection();
        } else submit(() -> ResourceScript.references(snapshot.text()), found -> {
            changing = true;
            if (isScene(snapshot.path())) references.addItem(new SceneOption(snapshot.path()));
            for (ResourceScript.Reference reference : found) references.addItem(reference);
            for (int i = 0; i < references.getItemCount(); i++)
                if (references.getItemAt(i).toString().equals(selectedName)) references.setSelectedIndex(i);
            changing = false;
            if (references.getItemCount() == 0) { frameLabel.setText("没有可预览的资源引用"); details.setText("支持 MAP/TIL 场景、带帧号的 .img 引用，以及 .ani 动画引用。"); }
            else loadSelection();
        });
    }

    private void loadSelection() {
        invalidatePreview();
        if (source == null) return;
        if (resources == null) { frameLabel.setText("请先选择 NPK 目录"); return; }
        Source snapshot = source; Object selected = references.getSelectedItem();
        if (selected == null) return;
        if (selected instanceof SceneOption || selected instanceof ResourceScript.Reference r && r.particle()) {
            frameLabel.setText("读取场景地块与动画…"); canvas.error = "加载场景…"; canvas.repaint();
            int[] values;
            try { values = variantValues(); } catch (IOException failure) { fail(failure); return; }
            NpkResources indexed = resources;
            boolean conditional = conditionalEntities.isSelected();
            submit(() -> {
                String path = snapshot.path(), text = snapshot.text();
                if (selected instanceof ResourceScript.Reference ref) {
                    text = null;
                    for (String candidate : ResourceScript.animationCandidates(snapshot.path(), ref.path())) {
                        text = snapshot.lookup().read(candidate); if (text != null) { path = candidate; break; }
                    }
                    if (text == null) throw new IOException("找不到粒子配置 " + ref.path());
                }
                ScenePreview loaded = ScenePreview.load(path, text, snapshot.lookup()::read, indexed, values);
                return new SceneLoaded(loaded, loaded.render(0, false, conditional));
            }, loaded -> {
                scene = loaded.scene(); animationPath = snapshot.path();
                sceneClock = new AnimationTimeline.Clock(); sceneClock.speed(speedFactor());
                loop.setText("强制循环"); loop.setSelected(false);
                loop.setToolTipText("场景时间持续前进。默认各 ANI 遵循自己的 LOOP；勾选后所有动画独立循环。");
                timelineStart = 0; changing = true; timeline.setMaximum(60_000); timeline.setValue(0); changing = false;
                canvas.world = loaded.rendered().bounds(); acceptScene(loaded.rendered()); updateControls();
            });
            return;
        }
        submit(() -> {
            if (selected instanceof ResourceScript.Reference ref) {
                if (!ref.animation()) return new Loaded(snapshot.path(), new ResourceScript.Animation(false,
                        List.of(ResourceScript.Frame.still(ref.path(), ref.index())), List.of()));
                for (String path : ResourceScript.animationCandidates(snapshot.path(), ref.path())) {
                    String text = snapshot.lookup().read(path);
                    if (text != null) return new Loaded(path, ResourceScript.animation(text));
                }
                throw new IOException("找不到 ANI：" + ref.path() + "；尝试 "
                        + String.join("、", ResourceScript.animationCandidates(snapshot.path(), ref.path())));
            }
            return new Loaded(snapshot.path(), ResourceScript.animation(snapshot.text()));
        }, loaded -> {
            animationPath = loaded.path(); animation = loaded.animation(); loop.setSelected(animation.loop());
            if (animation.frames().isEmpty()) {
                frameLabel.setText("空动画 · FRAME MAX 0"); details.setText(animationPath + "\n此配置没有动画帧。");
                updateControls(); return;
            }
            changing = true; timeline.setMaximum(animation.frames().size() - 1); timeline.setValue(0); changing = false;
            updateControls(); showFrame(0);
        });
    }

    private void showFrame(int index) {
        if (animation == null || index < 0 || index >= animation.frames().size()) return;
        playback.stop(); currentFrame = index; canvas.prepared = null; canvas.error = "读取帧…"; canvas.repaint();
        changing = true; timeline.setValue(index); changing = false;
        ResourceScript.Frame frame = animation.frames().get(index); NpkResources indexed = resources;
        frameLabel.setText("帧 " + (index + 1) + " / " + animation.frames().size() + " · " + frame.delay() + " ms");
        int[] values;
        try {
            values = variantValues();
        } catch (IOException failure) { fail(failure); return; }
        submit(() -> frame.image().isEmpty() ? null : PreviewRenderer.prepare(frame,
                indexed.sprite(NpkResources.substitute(frame.image(), values), frame.index())), prepared -> {
            canvas.prepared = prepared; canvas.error = "";
            if (prepared != null && canvas.world == null) {
                // Fix the view for the animation's full native canvas, so trim changes do not cause jitter.
                var source = prepared.source();
                java.awt.geom.AffineTransform full = new java.awt.geom.AffineTransform(prepared.transform());
                full.translate(-source.x(), -source.y());
                canvas.world = full.createTransformedShape(new Rectangle2D.Double(0, 0,
                        Math.max(source.canvasWidth(), source.image().getWidth()),
                        Math.max(source.canvasHeight(), source.image().getHeight()))).getBounds2D();
                canvas.world = canvas.world.createUnion(new Rectangle2D.Double(-16, -16, 32, 32));
            }
            String description = animationPath + "\n";
            if (prepared == null) description += "空白帧";
            else description += prepared.source().pack().getFileName() + "\n" + prepared.source().img()
                    + " #" + prepared.source().requestedFrame()
                    + (prepared.source().requestedFrame() == prepared.source().resolvedFrame() ? "" : " → 链接帧 #" + prepared.source().resolvedFrame());
            description += "\n位置 " + frame.x() + ", " + frame.y() + " · 缩放 " + frame.scaleX() + ", " + frame.scaleY()
                    + " · 旋转 " + frame.rotation() + " rad";
            if (!animation.warnings().isEmpty()) description += "\n" + String.join("\n", animation.warnings().stream().limit(4).toList());
            details.setText(description); details.setCaretPosition(0); canvas.repaint(); scheduleNext();
        });
    }

    private void advance() {
        if (scene != null) {
            if (!playing || !isShowing()) { pause(); return; }
            renderScene(); return;
        }
        if (!playing || animation == null || !isShowing()) { pause(); return; }
        int index = currentFrame + 1;
        if (index == animation.frames().size()) { if (loop.isSelected()) index = 0; else { pause(); return; } }
        showFrame(index);
    }
    private void scheduleNext() {
        if (scene != null) { if (playing) { playback.setInitialDelay(16); playback.restart(); } return; }
        if (!playing || animation == null || canvas.prepared == null && !animation.frames().get(currentFrame).image().isEmpty()) return;
        double factor = speedFactor();
        playback.setInitialDelay(Math.max(1, (int) (animation.frames().get(currentFrame).delay() / factor))); playback.restart();
    }
    private void pause() { playback.stop(); sceneClock.pause(); playing = false; play.setText("播放"); }
    private void invalidatePreview() {
        pause(); generation++; if (job != null) job.cancel(false);
        animation = null; scene = null; canvas.scene = null;
        loop.setText("循环"); loop.setToolTipText(null); legend.setVisible(false);
        canvas.prepared = null; canvas.world = null; canvas.resetView(); canvas.error = ""; canvas.repaint(); updateControls();
    }
    private void updateControls() {
        boolean loaded = scene != null || animation != null && !animation.frames().isEmpty();
        previous.setEnabled(loaded); next.setEnabled(loaded); play.setEnabled(scene != null || loaded && animation.frames().size() > 1);
        timeline.setEnabled(loaded); loop.setEnabled(loaded); speed.setEnabled(loaded);
        collision.setEnabled(scene != null); legend.setVisible(scene != null); legend.setEnabled(collision.isSelected());
        entityMarkers.setEnabled(scene != null);
        conditionalEntities.setEnabled(scene != null && canvas.scene != null && canvas.scene.conditionalCount() > 0);
        previous.setToolTipText(scene == null ? "上一帧" : "场景时间后退 16 ms");
        next.setToolTipText(scene == null ? "下一帧" : "场景时间前进 16 ms");
    }

    private void renderScene() {
        if (scene == null) return;
        playback.stop(); ScenePreview snapshot = scene; long time = sceneClock.milliseconds(); boolean force = loop.isSelected();
        boolean conditional = conditionalEntities.isSelected();
        submit(() -> snapshot.render(time, force, conditional), this::acceptScene);
    }
    private void acceptScene(ScenePreview.Rendered rendered) {
        canvas.scene = rendered; canvas.overlay = collision.isSelected(); canvas.markers = entityMarkers.isSelected(); canvas.error = "";
        long time = rendered.milliseconds(); timelineStart = time / 60_000 * 60_000;
        changing = true; timeline.setValue((int) (time - timelineStart)); changing = false;
        timeline.setToolTipText("当前时间窗口 " + timelineStart / 1000 + "–" + (timelineStart / 1000 + 60) + " 秒；拖动可定位，播放持续前进");
        frameLabel.setText(String.format(Locale.ROOT, "场景 %.3f s · %d 个轨道 · 独立计时", time / 1000.0, rendered.trackCount()));
        details.setText(animationPath + "\n" + rendered.tileCount() + " 个地块 · " + rendered.draws().size()
                + " 个可绘制图层 · " + rendered.collisions().size() + " 个通行网格\n"
                + "粒子：" + rendered.emitterCount() + " 个生成器 · " + rendered.particleCount() + " 个活动粒子 · 对象碰撞 " + rendered.objectCollisions().size() + " 处\n"
                + "实体：NPC " + countEntities(rendered, EntityScript.Kind.NPC) + " · 怪物 " + countEntities(rendered, EntityScript.Kind.MONSTER)
                + " · 被动对象 " + countEntities(rendered, EntityScript.Kind.PASSIVE)
                + (rendered.conditionalCount() == 0 ? "" : " · 条件候选 " + rendered.conditionalCount()) + "\n"
                + "各 ANI 按 DELAY/LOOP 独立播放，结束的非循环动画停在末帧。拖动可观察视差。\n"
                + (rendered.warnings().isEmpty() ? "" : rendered.warnings().size() + " 条提示：\n"
                + String.join("\n", rendered.warnings().stream().limit(5).toList())));
        details.setCaretPosition(0); canvas.repaint(); scheduleNext();
    }
    private static long countEntities(ScenePreview.Rendered rendered, EntityScript.Kind kind) {
        return rendered.entities().stream().filter(entity -> entity.info().spawn().kind() == kind).count();
    }
    private int[] variantValues() throws IOException {
        try {
            String raw = variants.getText().trim();
            return raw.isEmpty() ? new int[0] : Arrays.stream(raw.split(",", -1)).map(String::trim).mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException failure) { throw new IOException("外观参数请填写逗号分隔的整数，例如 0,0"); }
    }
    private double speedFactor() { return new double[]{.25, .5, 1, 2, 4}[speed.getSelectedIndex()]; }
    private static boolean isScene(String path) {
        String lower = path.toLowerCase(Locale.ROOT); return lower.endsWith(".map") || lower.endsWith(".til") || lower.endsWith(".ptl");
    }
    private <T> void submit(Work<T> work, Consumer<T> success) {
        long version = ++generation; if (job != null) job.cancel(false);
        job = worker.submit(() -> {
            try {
                T value = work.run();
                SwingUtilities.invokeLater(() -> { if (!closed && generation == version) success.accept(value); });
            } catch (Exception failure) {
                SwingUtilities.invokeLater(() -> { if (!closed && generation == version) fail(failure); });
            }
        });
    }
    private void fail(Exception failure) {
        pause(); canvas.prepared = null; canvas.scene = null; canvas.error = "预览不可用"; canvas.repaint();
        frameLabel.setText("预览不可用");
        details.setText(message(failure)); details.setCaretPosition(0);
    }
    private static String message(Exception failure) { return failure.getMessage() == null ? failure.toString() : failure.getMessage(); }
    private static String html(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    @Override public void close() {
        closed = true; ++generation; ++directoryGeneration; pause();
        // Do not interrupt a PVF read: interrupting FileChannel I/O would close the mounted archive's handle.
        if (job != null) job.cancel(false); worker.shutdown();
    }

    boolean hasPreparedFrame() { return canvas.prepared != null; }
    String previewDetails() { return details.getText(); }
    int frameCount() { return animation == null ? 0 : animation.frames().size(); }
    ScenePreview.Rendered renderedScene() { return canvas.scene; }
    @FunctionalInterface interface TextLookup { String read(String path) throws IOException; }
    record Source(String path, String text, TextLookup lookup) {}
    private record Loaded(String path, ResourceScript.Animation animation) {}
    private record SceneLoaded(ScenePreview scene, ScenePreview.Rendered rendered) {}
    private record SceneOption(String path) { @Override public String toString() { return "完整场景 · " + path; } }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }

    private static final class Canvas extends JPanel {
        PreviewRenderer.Prepared prepared; ScenePreview.Rendered scene;
        Rectangle2D world; String error = ""; int zoom;
        boolean overlay, markers; double panX, panY, freeZoom; Point drag;
        Canvas() {
            setPreferredSize(new Dimension(400, 360)); setToolTipText("拖动平移，滚轮缩放");
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { drag = event.getPoint(); setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); }
                @Override public void mouseReleased(MouseEvent event) { drag = null; setCursor(Cursor.getDefaultCursor()); }
                @Override public void mouseDragged(MouseEvent event) {
                    if (drag == null) return;
                    panX += event.getX() - drag.x; panY += event.getY() - drag.y; drag = event.getPoint(); repaint();
                }
                @Override public void mouseMoved(MouseEvent event) {
                    View view = view();
                    double x = (event.getX() - view.x() - panX) / view.scale(), y = (event.getY() - view.y() - panY) / view.scale();
                    String entity = scene == null ? "" : scene.entityAt(x, y);
                    String grid = scene != null && overlay ? scene.collisionAt(x, y) : "";
                    String tooltip = entity + (!entity.isEmpty() && !grid.isEmpty() ? "；" : "") + grid;
                    setToolTipText(tooltip.isEmpty() ? "拖动平移，滚轮缩放" : tooltip);
                }
                @Override public void mouseWheelMoved(MouseWheelEvent event) {
                    View old = view();
                    double wx = (event.getX() - old.x() - panX) / old.scale(), wy = (event.getY() - old.y() - panY) / old.scale();
                    freeZoom = Math.max(.02, Math.min(8, old.scale() * Math.pow(1.2, -event.getPreciseWheelRotation())));
                    View current = view(); panX = event.getX() - current.x() - wx * current.scale();
                    panY = event.getY() - current.y() - wy * current.scale(); repaint();
                }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
        }
        void resetView() { panX = 0; panY = 0; freeZoom = 0; }
        private View view() {
            double scale = freeZoom > 0 ? freeZoom : new double[]{1, .25, .5, 1, 2, 4}[zoom];
            double ox = getWidth() / 2.0, oy = getHeight() * .72;
            if (world != null && (scene != null || zoom == 0 || freeZoom > 0)) {
                if (zoom == 0 && freeZoom == 0) scale = Math.min(4,
                        Math.min(Math.max(1, getWidth() - 40) / Math.max(1, world.getWidth()),
                                Math.max(1, getHeight() - 40) / Math.max(1, world.getHeight())));
                ox = getWidth() / 2.0 - world.getCenterX() * scale;
                oy = getHeight() / 2.0 - world.getCenterY() * scale;
            }
            return new View(ox, oy, scale);
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create();
            try {
                for (int y = 0; y < getHeight(); y += 16) for (int x = 0; x < getWidth(); x += 16) {
                    g.setColor(((x / 16 + y / 16) & 1) == 0 ? new Color(54, 57, 62) : new Color(63, 66, 72));
                    g.fillRect(x, y, 16, 16);
                }
                View view = view();
                if (scene != null) scene.paint(g, new Rectangle(0, 0, getWidth(), getHeight()),
                        view.x(), view.y(), view.scale(), panX, panY, overlay, markers);
                else {
                    g.setColor(new Color(140, 147, 158, 120));
                    g.drawLine(0, (int) (view.y() + panY), getWidth(), (int) (view.y() + panY));
                    g.drawLine((int) (view.x() + panX), 0, (int) (view.x() + panX), getHeight());
                    if (prepared != null) prepared.paint(g, view.x() + panX, view.y() + panY, view.scale());
                }
                if (!error.isEmpty()) { g.setColor(Color.LIGHT_GRAY); g.drawString(error, 16, 28); }
            } finally { g.dispose(); }
        }
        private record View(double x, double y, double scale) {}
    }
}
