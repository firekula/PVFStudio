package studio.pvf.intellij;

import studio.pvf.preview.NpkResources;
import studio.pvf.preview.PreviewRenderer;
import studio.pvf.preview.ResourceScript;
import studio.pvf.preview.ScenePreview;
import studio.pvf.preview.SceneCamera;
import studio.pvf.preview.AnimationTimeline;
import studio.pvf.preview.EntityScript;
import studio.pvf.preview.DungeonPreview;

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
import java.util.function.BooleanSupplier;

/** One active preview, with its own background queue and stale-result guards. All public calls use EDT. */
final class ResourcePreviewPanel extends JPanel implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "pvf-resource-preview"); thread.setDaemon(true); return thread;
    });
    private final JButton choose = new JButton("选择资源目录");
    private final JButton refresh = new JButton("刷新预览");
    private final JLabel root = new JLabel("选择客户端或 ImagePacks2 目录");
    private final TaskProgress indexProgress = new TaskProgress("resource-index-progress");
    private final TaskProgress previewProgress = new TaskProgress("resource-load-progress");
    private final JComboBox<Object> references = new JComboBox<>();
    private final JTextField variants = new JTextField("0,0", 5);
    private final JButton play = new JButton("播放");
    private final JButton previous = new JButton("◀");
    private final JButton next = new JButton("▶");
    private final JCheckBox loop = new JCheckBox("循环");
    private final JComboBox<String> speed = new JComboBox<>(new String[]{"0.25×", "0.5×", "1×", "2×", "4×"});
    private final JComboBox<String> zoom = new JComboBox<>(new String[]{"自适应", "25%", "50%", "100%", "200%", "400%"});
    private final JCheckBox collision = new JCheckBox("碰撞区域");
    private final JCheckBox clientViewport = new JCheckBox("客户端视口");
    private final JCheckBox entityMarkers = new JCheckBox("对象标记");
    private final JCheckBox conditionalEntities = new JCheckBox("条件怪物");
    private final JToggleButton options = new JToggleButton("预览设置");
    private final JToggleButton showDetails = new JToggleButton("显示详情");
    private final JLabel legend = new JLabel("红：禁行 · 橙：仅浮空");
    private final JSlider timeline = new JSlider(0, 0, 0);
    private final JLabel frameLabel = new JLabel("尚未选择配置");
    private final JTextArea details = new JTextArea(5, 24);
    private final Canvas canvas = new Canvas();
    private final DungeonPanel dungeonPanel = new DungeonPanel(this::loadDungeonRoom);
    private final javax.swing.Timer playback = new javax.swing.Timer(100, e -> advance());
    private Future<?> job;
    private NpkResources resources;
    private Path indexedSettings;
    private record MinimapResult(studio.pvf.preview.DungeonMinimap atlas,String reason) {}
    private final Map<String,MinimapResult> minimaps=new LinkedHashMap<>();
    private Future<?> minimapJob;
    private long minimapGeneration;
    private boolean resourcesLoading;
    private Source source;
    private DungeonEditorPanel.Preview editorDungeon;
    private DungeonEditorPanel editorDungeonPanel;
    private Consumer<DungeonPanel.Selection> editorResolved;
    private MapEditorPanel editorMap;
    private Future<?> thumbnailJob;
    private Runnable resourcesChanged=() -> {};
    private ResourceScript.Animation animation;
    private String animationPath;
    private ScenePreview scene;
    private DungeonPanel.Selection dungeonSelection;
    private AnimationTimeline.Clock sceneClock = new AnimationTimeline.Clock();
    private long timelineStart;
    private long generation;
    private long directoryGeneration;
    private boolean changing, playing, closed;
    private int currentFrame, detailWarnings;
    private Path settings;
    private Path archiveParent;

    ResourcePreviewPanel() {
        super(new BorderLayout(0, 6)); setMinimumSize(new Dimension(300, 150));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JPanel header = new JPanel(); header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actions.add(new JLabel("资源预览")); actions.add(choose); actions.add(refresh);
        header.add(actions);
        header.add(indexProgress);
        references.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        references.setToolTipText("选择当前配置中的图标、图片或 ANI 引用");
        header.add(Box.createVerticalStrut(6)); header.add(references);
        JPanel parameters = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        variants.setToolTipText("替换 IMG 路径中的 %02d 等占位符；按顺序输入逗号分隔的整数，回车应用，例如 0,0");
        parameters.add(new JLabel("外观参数")); parameters.add(variants);
        parameters.add(new JLabel("缩放")); parameters.add(zoom);
        JPanel settingsPanel = new JPanel(); settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        root.setAlignmentX(LEFT_ALIGNMENT); settingsPanel.add(root); settingsPanel.add(parameters);
        JPanel overlays = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        entityMarkers.setToolTipText("显示所有实体的脚下原点及编号；没有可用待机动画的对象始终显示位置标记。");
        conditionalEntities.setToolTipText("同时预览 monster condition 中的候选怪物；不判断游戏中的出现条件，每条配置仅放置一个。");
        clientViewport.setToolTipText("MAP 的原生 640×480 视口；拖动相机受客户端边界限制。缩放只改变显示大小。边框内为实际画面。");
        overlays.add(collision); overlays.add(entityMarkers); overlays.add(conditionalEntities); settingsPanel.add(overlays);
        JPanel loopOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)); loopOptions.add(loop); settingsPanel.add(loopOptions);
        JPanel cameraControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)); cameraControls.add(clientViewport); cameraControls.add(options); header.add(cameraControls);
        settingsPanel.setVisible(false); header.add(settingsPanel);
        header.add(dungeonPanel);
        options.setName("preview-options"); options.setToolTipText("展开外观、缩放、叠加显示和副本布局选项");
        options.addActionListener(e -> {
            settingsPanel.setVisible(options.isSelected()); dungeonPanel.setOptionsVisible(options.isSelected());
            options.setText(options.isSelected() ? "收起设置" : "预览设置"); revalidate();
        });
        clientViewport.addItemListener(e -> { canvas.clientViewport = clientViewport.isSelected(); canvas.resetView(); canvas.repaint(); });
        legend.setVisible(false); settingsPanel.add(legend);
        add(header, BorderLayout.NORTH); add(canvas);
        JPanel footer = new JPanel(); footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(previewProgress);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        controls.add(previous); controls.add(play); controls.add(next); controls.add(speed);
        previous.setPreferredSize(new Dimension(32, 28)); next.setPreferredSize(new Dimension(32, 28));
        speed.setSelectedIndex(2); footer.add(controls); footer.add(timeline);
        JPanel summary = new JPanel(new BorderLayout(4, 0)); summary.add(frameLabel); summary.add(showDetails, BorderLayout.EAST); footer.add(summary);
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true);
        details.setOpaque(false); details.setFont(UIManager.getFont("Label.font"));
        details.setText("打开 DGN 查看副本布局并切换房间；MAP/TIL 预览完整场景，ANI/PTL 播放动画或粒子。\n拖动平移、滚轮缩放；碰撞层可查看地块及对象碰撞范围。");
        JScrollPane detailScroll = new JScrollPane(details); detailScroll.setVisible(false); footer.add(detailScroll); add(footer, BorderLayout.SOUTH);
        showDetails.setName("preview-details"); showDetails.setToolTipText("展开资源路径、房间连接与解析诊断");
        showDetails.addActionListener(e -> {
            detailScroll.setVisible(showDetails.isSelected()); updateDetailsLabel(); revalidate();
        });
        choose.addActionListener(e -> chooseDirectory());
        refresh.addActionListener(e -> reloadSource());
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
        zoom.addActionListener(e -> { canvas.zoom = zoom.getSelectedIndex(); canvas.freeZoom = 0;
            if (!canvas.usingClient()) { canvas.panX = 0; canvas.panY = 0; } canvas.repaint(); });
        playback.setRepeats(false); updateControls();
    }

    void mount(Path directory, Path parent) {
        indexProgress.clear();
        settings = directory.resolve("preview.properties"); archiveParent = parent;
        choose.setEnabled(true);
        root.setText(resources == null ? "选择客户端、ImagePacks2 或 Sprite 目录" : resourceSummary(resources));
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
                else if (parent != null && Files.isDirectory(parent.resolve("Sprite"))) saved = parent.resolve("Sprite");
            } catch (IOException | RuntimeException ignored) { /* A stale preference must not prevent editing. */ }
            Path selected = saved;
            SwingUtilities.invokeLater(() -> { if (!closed && version == directoryGeneration && selected != null) scan(selected); });
        });
    }

    private static String resourceSummary(NpkResources resources) {
        return resources.packCount() + " 个 NPK / " + resources.imageCount() + " 个 IMG"
                + (resources.looseImageCount() == 0 ? "" : "（" + resources.looseImageCount() + " 个独立文件）");
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("选择客户端、ImagePacks2 或 Sprite 目录（只读）");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        Path initial = resources == null ? archiveParent : resources.directory();
        if (initial != null && Files.isDirectory(initial)) chooser.setCurrentDirectory(initial.toFile());
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            ++directoryGeneration; scan(chooser.getSelectedFile().toPath());
        }
    }
    NpkResources resources() { return Objects.equals(indexedSettings,settings)?resources:null; }
    void chooseResources() { chooseDirectory(); }

    // Also used by the product smoke check, with the same indexing/rendering path as the picker.
    void scan(Path directory) {
        long version = ++directoryGeneration; Path config = settings;
        resourcesLoading=true;++minimapGeneration;minimaps.clear();
        if(editorDungeonPanel!=null)editorDungeonPanel.setMinimap(editorDungeonPanel.minimapImage(),null,"正在索引资源…");
        if(dungeonPanel.isVisible())dungeonPanel.setMinimap(dungeonPanel.minimapImage(),null,"正在索引资源…");
        TaskProgress.Ticket progress = indexProgress.start("resources.discover");
        root.setText("正在索引 NPK / IMG…"); root.setToolTipText(directory.toString()); choose.setEnabled(false);
        worker.execute(() -> {
            try {
                NpkResources indexed = NpkResources.scan(directory, progress::update); String preferenceWarning = "";
                if (config != null) {
                    try {
                        Properties properties = new Properties(); properties.setProperty("npk.directory", indexed.directory().toString());
                        try (var stream = Files.newOutputStream(config)) { properties.store(stream, "PVF Studio resource preview"); }
                    } catch (IOException failure) { preferenceWarning = "；目录设置保存失败"; }
                }
                String suffix = preferenceWarning;
                SwingUtilities.invokeLater(() -> {
                    indexProgress.finish(progress);
                    if (closed || version != directoryGeneration) return;
                    resources = indexed; indexedSettings=config; resourcesLoading=false; choose.setEnabled(true);
                    resourcesChanged.run();
                    root.setText(resourceSummary(indexed)
                            + (indexed.warnings().isEmpty() ? "" : " / " + indexed.warnings().size() + " 条索引提示") + suffix);
                    root.setToolTipText("<html>" + html(indexed.directory().toString()) + "<br>"
                            + html(String.join("\n", indexed.warnings().stream().limit(12).toList())).replace("\n", "<br>") + "</html>");
                    reloadSource();
                });
            } catch (Exception failure) {
                SwingUtilities.invokeLater(() -> {
                    indexProgress.finish(progress);
                    if (closed || version != directoryGeneration) return;
                    resourcesLoading=false;choose.setEnabled(true); root.setText(resources == null ? "NPK 索引失败" : "索引失败，仍使用之前的 NPK 目录");
                    root.setToolTipText(message(failure)); fail(failure);
                });
            }
        });
    }

    void setSource(Source value) {
        if(editorMap!=null)editorMap.captureView(canvas);
        if(editorDungeonPanel!=null)editorDungeonPanel.captureView(canvas);
        editorDungeon=null; editorDungeonPanel=null; editorResolved=null; editorMap=null; canvas.interaction=null; references.setVisible(true);
        source = value; invalidatePreview();
        dungeonPanel.setVisible(false); dungeonSelection = null;
        Object selected = references.getSelectedItem(); String selectedName = selected == null ? "" : selected.toString();
        changing = true; references.removeAllItems(); changing = false;
        if (source == null) { frameLabel.setText("尚未选择配置"); details.setText("打开 .ani 或包含图片、动画引用的配置后预览。"); return; }
        Source snapshot = source;
        if (snapshot.path().toLowerCase(Locale.ROOT).endsWith(".ani")) {
            changing = true; references.addItem("当前动画 · " + snapshot.path()); changing = false;
            loadSelection();
        } else submit("解析资源引用", () -> ResourceScript.references(snapshot.text()), found -> {
            changing = true;
            if (snapshot.path().toLowerCase(Locale.ROOT).endsWith(".dgn")) references.addItem(new DungeonOption(snapshot.path()));
            if (isScene(snapshot.path())) references.addItem(new SceneOption(snapshot.path()));
            for (ResourceScript.Reference reference : found) references.addItem(reference);
            for (int i = 0; i < references.getItemCount(); i++)
                if (references.getItemAt(i).toString().equals(selectedName)) references.setSelectedIndex(i);
            changing = false;
            if (references.getItemCount() == 0) { frameLabel.setText("没有可预览的资源引用"); details.setText("支持 DGN 副本、MAP/TIL 场景、带帧号的 .img 引用，以及 .ani 动画引用。"); }
            else loadSelection();
        });
    }

    private void reloadSource() {
        if(editorDungeon!=null)setDungeonEditorSource(source,editorDungeon,editorResolved,editorDungeonPanel);
        else if(editorMap!=null)setMapEditorSource(source,editorMap); else setSource(source);
    }

    void setMapEditorSource(Source value,MapEditorPanel panel) {
        setSource(value); editorMap=panel; panel.attach(canvas); references.setVisible(false);
    }

    void setDungeonEditorSource(Source value, DungeonEditorPanel.Preview selected, Consumer<DungeonPanel.Selection> resolved,DungeonEditorPanel panel) {
        if(editorMap!=null)editorMap.captureView(canvas);
        editorMap=null; canvas.interaction=null;
        if(editorDungeonPanel!=null)editorDungeonPanel.captureView(canvas);editorDungeonPanel=panel;
        editorDungeon=selected; editorResolved=resolved; references.setVisible(false);
        source=value; invalidatePreview(); dungeonPanel.setVisible(false); dungeonSelection=null;
        if(value==null||selected==null)return;
        loadMinimap(panel);
        submit("载入所选副本房间",() -> DungeonPreview.load(value.path(),value.text(),value.lookup()::read), dungeon -> {
            if(selected.maze()>=dungeon.definition().mazes().size())return;
            var selection=DungeonEditorPreview.resolve(dungeon,selected);
            if(selection==null){canvas.error="该房间待创建或连接";canvas.repaint();return;}
            resolved.accept(selection); loadDungeonRoom(selection);
        });
    }

    private void loadSelection() {
        if(editorDungeon!=null) { reloadSource(); return; }
        invalidatePreview();
        dungeonPanel.setVisible(false); dungeonSelection = null;
        if (source == null) return;
        Source snapshot = source; Object selected = editorMap==null?references.getSelectedItem():new SceneOption(snapshot.path());
        if (selected == null) return;
        if (selected instanceof DungeonOption) {
            frameLabel.setText("读取副本布局与房间候选…"); canvas.error = "加载副本…"; canvas.repaint();
            submit("载入副本布局与房间候选", () -> DungeonPreview.load(snapshot.path(), snapshot.text(), snapshot.lookup()::read), loaded -> {
                String image=studio.pvf.preview.DungeonMinimap.image(snapshot.text());dungeonPanel.install(loaded,image);
                loadMinimap(image,result -> dungeonPanel.setMinimap(image,result.atlas(),result.reason()),() -> source==snapshot&&editorDungeonPanel==null&&dungeonPanel.isVisible());
                if (dungeonPanel.selection() == null) fail(new IOException("布局没有可预览的房间；请检查 greed 与起点配置"));
            });
            return;
        }
        if (resources == null) { frameLabel.setText("请先选择资源目录（NPK 或 Sprite/IMG）"); return; }
        if (selected instanceof SceneOption || selected instanceof ResourceScript.Reference r && r.particle()) {
            frameLabel.setText("读取场景地块与动画…"); canvas.error = "加载场景…"; canvas.repaint();
            int[] values;
            try { values = variantValues(); } catch (IOException failure) { fail(failure); return; }
            NpkResources indexed = resources;
            boolean conditional = conditionalEntities.isSelected();
            submit("载入地图、地块与场景资源", () -> {
                String path = snapshot.path(), text = snapshot.text();
                if (selected instanceof ResourceScript.Reference ref) {
                    text = null;
                    for (String candidate : ResourceScript.animationCandidates(snapshot.path(), ref.path())) {
                        text = snapshot.lookup().read(candidate); if (text != null) { path = candidate; break; }
                    }
                    if (text == null) throw new IOException("找不到粒子配置 " + ref.path());
                }
                ScenePreview loaded = ScenePreview.load(path, text, snapshot.lookup()::read, indexed, values);
                return new SceneLoaded(loaded, loaded.render(0, false, conditional),path,text);
            }, loaded -> {
                acceptLoadedScene(loaded, snapshot.path());
            });
            return;
        }
        submit("载入动画配置", () -> {
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
        submit("解码图片帧", () -> frame.image().isEmpty() ? null : PreviewRenderer.prepare(frame,
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
            detailWarnings = animation.warnings().size(); updateDetailsLabel();
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
        previewProgress.clear();
        detailWarnings = 0; updateDetailsLabel();
        pause(); generation++; if (job != null) job.cancel(false);
        animation = null; scene = null; canvas.scene = null; canvas.camera = null;canvas.sceneSourcePath=null;canvas.sceneSourceText=null;
        loop.setText("循环"); loop.setToolTipText(null); legend.setVisible(false);
        canvas.prepared = null; canvas.world = null; canvas.resetView(); canvas.error = ""; canvas.repaint(); updateControls();
    }
    private void updateControls() {
        boolean loaded = scene != null || animation != null && !animation.frames().isEmpty();
        previous.setEnabled(loaded); next.setEnabled(loaded); play.setEnabled(scene != null || loaded && animation.frames().size() > 1);
        timeline.setEnabled(loaded); loop.setEnabled(loaded); speed.setEnabled(loaded);
        collision.setEnabled(scene != null); clientViewport.setEnabled(canvas.scene != null && canvas.scene.cameraLimits() != null);
        boolean town = canvas.scene != null && canvas.scene.town();
        legend.setText(town ? "青框：城镇可移动区域（所有区域的并集）" : "红：禁行 · 橙：仅浮空");
        collision.setToolTipText(town ? "显示城镇 [virtual movable area] 可移动范围的青色边框。"
                : "叠加 TIL 的 16×12 通行格和被动对象碰撞范围。红色不可通行，橙色仅浮空可通行。");
        legend.setVisible(scene != null); legend.setEnabled(collision.isSelected());
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
    private void loadDungeonRoom(DungeonPanel.Selection selection) {
        boolean keepView = dungeonSelection != null && dungeonSelection.room().position().equals(selection.room().position())
                && dungeonSelection.map() != null && selection.map() != null && dungeonSelection.map().path().equals(selection.map().path());
        Point cameraPosition = canvas.camera == null ? null : canvas.camera.position();
        double panX = canvas.panX, panY = canvas.panY, freeZoom = canvas.freeZoom;
        invalidatePreview(); dungeonSelection = selection;
        if (source == null) return;
        if (selection.map() == null) {
            detailWarnings = selection.layout().warnings().size() + 1; updateDetailsLabel();
            frameLabel.setText("副本布局已加载 · 此房间未关联 MAP");
            canvas.error = "此房间未关联 MAP"; details.setText(selection.description()
                    + "此房间没有符合配置的 MAP 候选。请检查 DGN 的 MAP 指定及 MAP 的 [dungeon] 关联。");
            details.setCaretPosition(0); canvas.repaint(); return;
        }
        if (resources == null) { frameLabel.setText("副本布局已加载 · 请选择资源目录显示房间画面");
            canvas.error = "请选择资源目录"; details.setText(selection.description()); canvas.repaint(); return; }
        Source snapshot = source; var indexed = resources; boolean conditional = conditionalEntities.isSelected(); int[] values;
        try { values = variantValues(); } catch (IOException failure) { fail(failure); return; }
        frameLabel.setText("读取副本房间 " + selection.room().position() + "…"); canvas.error = "加载房间…"; canvas.repaint();
        submit("载入副本房间", () -> {
            String path = selection.map().path(), text = snapshot.lookup().read(path);
            if (text == null) throw new IOException("找不到房间 MAP " + path);
            var gates = selection.dungeon().gates(selection.layout(), selection.room(), selection.map(), selection.cleared(), selection.bossCondition());
            var loaded = ScenePreview.loadRoom(path, text, snapshot.lookup()::read, indexed, gates, values);
            return new SceneLoaded(loaded, loaded.render(0, false, conditional),path,text);
        }, loaded -> {
            acceptLoadedScene(loaded, selection.map().path());
            if (keepView) {
                if (cameraPosition != null && canvas.camera != null) canvas.camera.set(cameraPosition.x, cameraPosition.y);
                canvas.panX = panX; canvas.panY = panY; canvas.freeZoom = freeZoom; canvas.repaint();
            }
        });
    }
    private void acceptLoadedScene(SceneLoaded loaded, String path) {
        canvas.sceneSourcePath=loaded.sourcePath();canvas.sceneSourceText=loaded.sourceText();
        scene = loaded.scene(); animationPath = path;
        sceneClock = new AnimationTimeline.Clock(); sceneClock.speed(speedFactor());
        loop.setText("强制循环"); loop.setSelected(false);
        loop.setToolTipText("场景时间持续前进。默认各 ANI 遵循自己的 LOOP；勾选后所有动画独立循环。");
        timelineStart = 0; changing = true; timeline.setMaximum(60_000); timeline.setValue(0); changing = false;
        canvas.clientViewport = clientViewport.isSelected();
        canvas.world = loaded.rendered().overviewBounds(); canvas.resetView(); acceptScene(loaded.rendered()); updateControls();
        if(editorMap!=null) { editorMap.restoreView(canvas); canvas.repaint(); }
        if(editorDungeonPanel!=null){editorDungeonPanel.restoreView(canvas);canvas.repaint();}
    }
    private void acceptScene(ScenePreview.Rendered rendered) {
        canvas.scene = rendered; canvas.overlay = collision.isSelected(); canvas.markers = entityMarkers.isSelected(); canvas.error = "";
        updateControls();
        if (rendered.cameraLimits() != null && (canvas.camera == null || !canvas.camera.limits().equals(rendered.cameraLimits())))
            canvas.camera = new SceneCamera(rendered.cameraLimits());
        long time = rendered.milliseconds(); timelineStart = time / 60_000 * 60_000;
        changing = true; timeline.setValue((int) (time - timelineStart)); changing = false;
        timeline.setToolTipText("当前时间窗口 " + timelineStart / 1000 + "–" + (timelineStart / 1000 + 60) + " 秒；拖动可定位，播放持续前进");
        frameLabel.setText(String.format(Locale.ROOT, "场景 %.3f s · %d 个轨道 · 独立计时", time / 1000.0, rendered.trackCount()));
        detailWarnings = rendered.warnings().size() + (dungeonSelection == null ? 0 : dungeonSelection.layout().warnings().size()); updateDetailsLabel();
        details.setText((dungeonSelection == null ? "" : source.path() + "\n" + dungeonSelection.description())
                + animationPath + "\n" + rendered.tileCount() + " 个地块 · " + rendered.draws().size()
                + " 个可绘制图层\n"
                + (rendered.town() ? "通行范围：城镇可移动区域 " + rendered.movable().size() + " 处（青框，并集）\n"
                        : "通行范围：" + rendered.collisions().size() + " 个通行网格 · 对象碰撞 " + rendered.objectCollisions().size() + " 处\n")
                + "粒子：" + rendered.emitterCount() + " 个生成器 · " + rendered.particleCount() + " 个活动粒子\n"
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
    void captureEditorView(){if(editorDungeonPanel!=null)editorDungeonPanel.captureView(canvas);if(editorMap!=null)editorMap.captureView(canvas);}
    void onViewChanged(Runnable listener){canvas.viewChanged=listener;}
    void onResourcesChanged(Runnable listener) { resourcesChanged=listener; }
    private void loadMinimap(DungeonEditorPanel panel){
        String path=panel.minimapImage();loadMinimap(path,result -> panel.setMinimap(path,result.atlas(),result.reason()),() -> editorDungeonPanel==panel);
    }
    private void loadMinimap(String path,Consumer<MinimapResult> accept,BooleanSupplier active){
        long request=++minimapGeneration,directory=directoryGeneration;
        if(minimapJob!=null)minimapJob.cancel(false);
        if(resources==null||resourcesLoading||path.isBlank()){accept.accept(new MinimapResult(null,path.isBlank()?"副本未指定小地图图像":resourcesLoading?"正在索引资源…":"未选择 NPK / IMG 目录"));return;}
        String key=NpkResources.normalize(path);var cached=minimaps.get(key);if(cached!=null){accept.accept(cached);return;}
        NpkResources indexed=resources;accept.accept(new MinimapResult(null,"正在读取原生小地图…"));
        minimapJob=worker.submit(() -> {
            MinimapResult result;try{result=new MinimapResult(studio.pvf.preview.DungeonMinimap.load(path,indexed),"");}catch(Exception failure){result=new MinimapResult(null,message(failure));}
            var loaded=result;SwingUtilities.invokeLater(() -> {
                if(closed||directory!=directoryGeneration)return;
                minimaps.put(key,loaded);if(minimaps.size()>24)minimaps.remove(minimaps.keySet().iterator().next());
                if(request==minimapGeneration&&active.getAsBoolean())accept.accept(loaded);
            });
        });
    }
    void thumbnail(Source snapshot,SceneResourceBrowser.Asset asset,Consumer<SceneResourceBrowser.Thumbnail> result) {
        NpkResources indexed=resources; long directory=directoryGeneration;
        if(snapshot==null) { result.accept(new SceneResourceBrowser.Thumbnail(null,"未载入文档\n"+asset)); return; }
        if(thumbnailJob!=null)thumbnailJob.cancel(false);
        thumbnailJob=worker.submit(() -> {
            SceneResourceBrowser.Thumbnail loaded;String description=asset.toString();Boolean extendedTile=null;
            try {
                String raw=snapshot.lookup().read(asset.path()); if(raw==null)throw new IOException("找不到 "+asset.path());
                description+="\n"+SceneAssetMetadata.describe(asset.category(),raw);if(asset.category()==SceneResourceBrowser.Category.TILE)extendedTile=studio.pvf.preview.SceneScript.tile(raw).extended();if(indexed==null)throw new IOException("选择资源目录后显示原生缩略图");
                java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(128,96,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=image.createGraphics();
                try {
                    if(asset.category()==SceneResourceBrowser.Category.ANIMATION) {
                        var ani=ResourceScript.animation(raw); if(ani.frames().isEmpty())throw new IOException("ANI 没有帧");
                        var frame=ani.frames().getFirst(); if(frame.image().isEmpty())throw new IOException("首帧没有 IMAGE");
                        var prepared=PreviewRenderer.prepare(frame,indexed.sprite(NpkResources.substitute(frame.image(),new int[]{0,0}),frame.index()));
                        var bounds=prepared.bounds(); double scale=Math.min(4,Math.min(120/Math.max(1,bounds.getWidth()),88/Math.max(1,bounds.getHeight())));
                        prepared.paint(g,64-bounds.getCenterX()*scale,48-bounds.getCenterY()*scale,scale);
                    } else {
                        String scenePath=asset.path(),text=raw;
                        if(asset.category()!=SceneResourceBrowser.Category.TILE) {
                            var kind=asset.category()==SceneResourceBrowser.Category.NPC?EntityScript.Kind.NPC:asset.category()==SceneResourceBrowser.Category.MONSTER?EntityScript.Kind.MONSTER:EntityScript.Kind.PASSIVE;
                            scenePath="map/__thumbnail__.map";
                            text="[tile] `__thumbnail__.til` [/tile]";
                            text=studio.pvf.sceneedit.MapDocument.parse(scenePath,text).addEntity(kind,asset.id(),112,120,0).applyTo(text);
                            if(kind==EntityScript.Kind.PASSIVE) {
                                var factory=EntityScript.particleFactory(raw,asset.id());
                                if(factory!=null)description+="\n已识别粒子工厂 → "+factory.path();
                            }
                        }
                        var scene=ScenePreview.load(scenePath,text,p -> p.equals("map/__thumbnail__.til")?"[pass type] "+"0 ".repeat(420):snapshot.lookup().read(p),indexed,0,0).render(200,false,true);
                        Rectangle2D bounds=null;
                        for(var draw:scene.draws()) { var part=draw.prepared().bounds(draw.mirrored()); var positioned=new Rectangle2D.Double(draw.x()+part.getX(),draw.y()+part.getY(),part.getWidth(),part.getHeight()); bounds=bounds==null?positioned:bounds.createUnion(positioned); }
                        if(bounds==null)throw new IOException("逻辑对象或当前帧没有图像；仍可放置并编辑原生参数");
                        double scale=Math.min(4,Math.min(120/Math.max(1,bounds.getWidth()),88/Math.max(1,bounds.getHeight())));
                        scene.paint(g,new Rectangle(0,0,128,96),64-bounds.getCenterX()*scale,48-bounds.getCenterY()*scale,scale,0,0,false);
                    }
                } finally { g.dispose(); }
                loaded=new SceneResourceBrowser.Thumbnail(image,description,extendedTile);
            } catch(Exception failure) { loaded=new SceneResourceBrowser.Thumbnail(null,description+"\n"+message(failure),extendedTile); }
            var value=loaded; SwingUtilities.invokeLater(() -> { if(!closed&&directory==directoryGeneration)result.accept(value); });
        });
    }
    private <T> void submit(Work<T> work, Consumer<T> success) {
        submit("更新预览画面", work, success);
    }
    private <T> void submit(String label, Work<T> work, Consumer<T> success) {
        long version = ++generation; if (job != null) job.cancel(false);
        TaskProgress.Ticket progress = previewProgress.start(label, 250);
        job = worker.submit(() -> {
            try {
                T value = work.run();
                SwingUtilities.invokeLater(() -> { previewProgress.finish(progress); if (!closed && generation == version) success.accept(value); });
            } catch (Exception failure) {
                SwingUtilities.invokeLater(() -> { previewProgress.finish(progress); if (!closed && generation == version) fail(failure); });
            }
        });
    }
    private void fail(Exception failure) {
        pause(); canvas.prepared = null; canvas.scene = null; canvas.error = "预览不可用"; canvas.repaint();
        frameLabel.setText("预览不可用");
        detailWarnings = 1; updateDetailsLabel();
        details.setText(message(failure)); details.setCaretPosition(0);
    }
    private void updateDetailsLabel() {
        showDetails.setText((showDetails.isSelected() ? "收起详情" : "显示详情") + (detailWarnings == 0 ? "" : " (" + detailWarnings + ")"));
    }
    private static String message(Exception failure) { return failure.getMessage() == null ? failure.toString() : failure.getMessage(); }
    private static String html(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    @Override public void close() {
        if(thumbnailJob!=null)thumbnailJob.cancel(false);
        indexProgress.clear(); previewProgress.clear();
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
    private record SceneLoaded(ScenePreview scene, ScenePreview.Rendered rendered,String sourcePath,String sourceText) {}
    private record SceneOption(String path) { @Override public String toString() { return "完整场景 · " + path; } }
    private record DungeonOption(String path) { @Override public String toString() { return "副本布局 · " + path; } }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }

    static final class Canvas extends SceneCanvas {}
}
