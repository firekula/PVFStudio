package studio.pvf.intellij;

import com.intellij.openapi.application.ApplicationManager;
import studio.pvf.core.PvfArchive;
import studio.pvf.preview.ResourceScript;
import studio.pvf.preview.SceneScript;
import studio.pvf.preview.ScenePreview;
import studio.pvf.preview.EntityScript;
import studio.pvf.preview.NpkResources;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Optional real-resource product smoke: same panel, controls, async index and callbacks as the editor. */
final class ResourcePreviewSmoke {
    private ResourcePreviewSmoke() {}

    static void run(Path archivePath, Path npk, Path output) throws Exception {
        String aniPath = "passiveobject/monster/ghoul/animation/skullbombsub.ani";
        String itemPath = "stackable/recipe/nrcp_tra_rudechantre.stk";
        String mobPath = "monster/goblin/goblin.mob";
        String stayPath = "monster/goblin/animation_goblin/stay.ani";
        String mapPath = "map/sunderlandpoison/b801.map", tilePath = "map/hendonmyre/tile/hm01.til";
        String townPath = "map/hendonmyre/hendon_auction.map";
        String lorienPath = "map/lorien/104.map", multipartPath = "map/ocean/b2401.map";
        String particlePath = "passiveobject/mapobject/particlefactory/particle/rayground.ptl";
        Charset charset = Charset.forName("GBK"); Map<String, String> scripts = new HashMap<>();
        try (PvfArchive archive = PvfArchive.open(archivePath)) {
            for (String path : List.of(aniPath, itemPath, mobPath, stayPath, mapPath, tilePath, townPath, lorienPath, multipartPath))
                scripts.put(path, new String(archive.readBytes(path), charset));
            Set<String> paths = new HashSet<>(); for (var entry : archive.entries()) paths.add(entry.path());
            // Resolving MAPs preloads exactly the referenced scripts, including indexed entities and equipment.
            for (String path : List.of(mapPath, townPath, lorienPath, multipartPath)) {
                ScenePreview.load(path, scripts.get(path), candidate -> {
                    if (!paths.contains(candidate)) return null;
                    if (!scripts.containsKey(candidate)) scripts.put(candidate, new String(archive.readBytes(candidate), charset));
                    return scripts.get(candidate);
                }, new ScenePreview.SpriteLookup() {
                    public NpkResources.Sprite sprite(String image, int index) throws java.io.IOException { throw new java.io.IOException("Deferred pixels"); }
                    public NpkResources.Info info(String image, int index) throws java.io.IOException { throw new java.io.IOException("Deferred metadata"); }
                });
            }
        }
        Path preferences = Files.createDirectory(output.resolve("preview-settings"));
        ResourcePreviewPanel panel = edt(ResourcePreviewPanel::new);
        JFrame window = edt(() -> {
            JFrame frame = new JFrame("PVF Studio — 资源预览检查"); frame.setContentPane(panel);
            frame.setSize(520, 760); frame.setLocationRelativeTo(null); frame.setVisible(true);
            panel.mount(preferences, archivePath.getParent()); panel.scan(npk);
            panel.mount(preferences, archivePath.getParent());
            if (!button(panel, "选择 NPK 目录").isEnabled()) throw new AssertionError("Workspace switch left NPK picker disabled");
            panel.scan(npk);
            panel.setSource(new ResourcePreviewPanel.Source(aniPath, scripts.get(aniPath), scripts::get)); return frame;
        });
        try {
            await(() -> panel.hasPreparedFrame() && panel.frameCount() == 7);
            edt(() -> {
                window.setSize(1100, 800);
                panel.setSource(new ResourcePreviewPanel.Source(lorienPath, scripts.get(lorienPath), scripts::get)); return null;
            });
            await(() -> panel.renderedScene() != null && panel.renderedScene().entities().size() == 32);
            edt(() -> {
                var frame = panel.renderedScene();
                if (frame.emitterCount() != 6 || frame.particleCount() != 36 || frame.objectCollisions().size() != 4)
                    throw new AssertionError("Lorien particles/object footprints did not load");
                if (frame.draws().stream().noneMatch(d -> d.x() == 526 && d.y() == 339)
                        || !frame.collisionAt(526, 339).contains("格 4,18")) throw new AssertionError("Client world/grid projection regressed");
                screenshot(panel, output.resolve("preview-lorien-native.png"));
                checkbox(panel, "碰撞区域").doClick(); screenshot(panel, output.resolve("preview-lorien-collision.png"));
                checkbox(panel, "碰撞区域").doClick();
                panel.setSource(new ResourcePreviewPanel.Source(multipartPath, scripts.get(multipartPath), scripts::get)); return null;
            });
            await(() -> panel.renderedScene() != null && panel.renderedScene().entities().stream().anyMatch(e -> e.info().spawn().id() == 3001));
            edt(() -> {
                for (int id : new int[]{31, 34, 3001, 701}) {
                    var entity = panel.renderedScene().entities().stream().filter(e -> e.info().spawn().kind() == EntityScript.Kind.MONSTER && e.info().spawn().id() == id).findFirst().orElseThrow();
                    int expected = id == 3001 ? 6 : id == 701 ? 7 : 5;
                    if (!entity.problem().isEmpty() || entity.info().animations().size() != expected) throw new AssertionError("Native multipart monster " + id + ": " + entity);
                }
                screenshot(panel, output.resolve("preview-ocean-multipart.png"));
                var frame = panel.renderedScene();
                if (frame.emitterCount() != 27 || frame.particleCount() != 159 || frame.objectCollisions().size() != 17 || !frame.warnings().isEmpty())
                    throw new AssertionError("Ocean particles/object footprints did not load: " + frame.warnings());
                components(panel, JSlider.class).get(0).setValue(5000); return null;
            });
            await(() -> panel.renderedScene() != null && panel.renderedScene().milliseconds() == 5000);
            edt(() -> {
                screenshot(panel, output.resolve("preview-ocean-particles.png"));
                checkbox(panel, "碰撞区域").doClick(); screenshot(panel, output.resolve("preview-ocean-object-collision.png"));
                checkbox(panel, "碰撞区域").doClick();
                panel.setSource(new ResourcePreviewPanel.Source(particlePath, scripts.get(particlePath), scripts::get)); return null;
            });
            await(() -> panel.renderedScene() != null && panel.renderedScene().tileCount() == 0 && panel.renderedScene().emitterCount() == 1);
            edt(() -> {
                screenshot(panel, output.resolve("preview-ptl.png"));
                String edited = scripts.get(particlePath) + "\n[maximum create number] 1\n";
                panel.setSource(new ResourcePreviewPanel.Source(particlePath, edited, scripts::get)); return null;
            });
            await(() -> panel.renderedScene() != null && panel.renderedScene().emitterCount() == 1);
            edt(() -> { components(panel, JSlider.class).get(0).setValue(5000); return null; });
            await(() -> panel.renderedScene() != null && panel.renderedScene().milliseconds() == 5000 && panel.renderedScene().particleCount() == 1);
            edt(() -> {
                screenshot(panel, output.resolve("preview-edited-ptl.png"));
                panel.setSource(new ResourcePreviewPanel.Source(aniPath, scripts.get(aniPath), scripts::get)); return null;
            });
            await(() -> panel.hasPreparedFrame() && panel.frameCount() == 7);
            edt(() -> {
                window.setSize(1000, 760);
                panel.setSource(new ResourcePreviewPanel.Source(mapPath, scripts.get(mapPath), scripts::get)); return null;
            });
            await(() -> panel.renderedScene() != null && panel.renderedScene().tileCount() == 7);
            edt(() -> {
                if (panel.renderedScene().entities().size() != 74 || panel.renderedScene().entities().stream().anyMatch(e -> !e.problem().isEmpty()))
                    throw new AssertionError("Real MAP entities failed to load");
                return null;
            });
            Dimension canvasSize = edt(() -> components(panel, JPanel.class).stream()
                    .filter(component -> component.getClass().getSimpleName().equals("Canvas")).findFirst().orElseThrow().getSize());
            edt(() -> { screenshot(panel, output.resolve("preview-map.png")); checkbox(panel, "碰撞区域").doClick(); return null; });
            edt(() -> {
                Dimension current = components(panel, JPanel.class).stream()
                        .filter(component -> component.getClass().getSimpleName().equals("Canvas")).findFirst().orElseThrow().getSize();
                if (!canvasSize.equals(current)) throw new AssertionError("Collision toggle changed the scene viewport");
                screenshot(panel, output.resolve("preview-map-collision.png")); button(panel, "播放").doClick(); return null;
            });
            await(() -> panel.renderedScene() != null && panel.renderedScene().milliseconds() > 150);
            edt(() -> { button(panel, "暂停").doClick(); return null; });
            long paused = edt(() -> panel.renderedScene().milliseconds());
            edt(() -> { button(panel, "▶").doClick(); return null; });
            await(() -> panel.renderedScene().milliseconds() >= paused + 16);
            // Crossing a minute exercises the rolling seek window without waiting a minute in real time.
            edt(() -> { components(panel, JSlider.class).get(0).setValue(59999); return null; });
            await(() -> panel.renderedScene().milliseconds() == 59999);
            edt(() -> { button(panel, "播放").doClick(); return null; });
            await(() -> panel.renderedScene().milliseconds() > 60100);
            edt(() -> { button(panel, "暂停").doClick(); return null; });
            String conditionalMap = scripts.get(mapPath) + "\n[monster condition]\n5001 1 0 500 300 0 1 1 `[fixed]` `[normal]`\n[/monster condition]";
            edt(() -> { panel.setSource(new ResourcePreviewPanel.Source(mapPath, conditionalMap, scripts::get)); return null; });
            await(() -> panel.renderedScene() != null && panel.renderedScene().conditionalCount() == 1 && panel.renderedScene().entities().size() == 74);
            edt(() -> { checkbox(panel, "条件怪物").doClick(); return null; });
            await(() -> panel.renderedScene().entities().size() == 75);
            edt(() -> { checkbox(panel, "条件怪物").doClick(); checkbox(panel, "对象标记").doClick(); return null; });
            await(() -> panel.renderedScene().entities().size() == 74);
            edt(() -> { screenshot(panel, output.resolve("preview-entity-markers.png")); checkbox(panel, "对象标记").doClick(); return null; });
            String objectPath = edt(() -> panel.renderedScene().entities().stream()
                    .filter(e -> e.info().spawn().kind() == EntityScript.Kind.PASSIVE).findFirst().orElseThrow().info().definition());
            Map<String, String> editedObject = new HashMap<>(scripts);
            editedObject.put(objectPath, scripts.get(objectPath).replaceFirst("(?i)(\\[basic motion\\]\\s*)`[^`]*`", "$1``"));
            edt(() -> { panel.setSource(new ResourcePreviewPanel.Source(mapPath, scripts.get(mapPath), editedObject::get)); return null; });
            await(() -> panel.renderedScene() != null && panel.renderedScene().entities().stream()
                    .anyMatch(e -> e.info().definition().equals(objectPath) && !e.problem().isEmpty()));
            edt(() -> { screenshot(panel, output.resolve("preview-entity-edited-obj.png"));
                panel.setSource(new ResourcePreviewPanel.Source(townPath, scripts.get(townPath), scripts::get)); return null; });
            await(() -> panel.renderedScene() != null && panel.renderedScene().entities().size() == 2
                    && panel.renderedScene().entities().stream().allMatch(e -> e.info().spawn().kind() == EntityScript.Kind.NPC && e.info().spawn().mirrored() && e.problem().isEmpty()));
            edt(() -> { screenshot(panel, output.resolve("preview-town-npc.png")); return null; });
            String firstTile = panelPath(mapPath, SceneScript.map(scripts.get(mapPath)).tiles().get(0), scripts);
            Map<String, String> editedTile = new HashMap<>(scripts); String original = scripts.get(firstTile);
            int passOffset = original.toLowerCase(Locale.ROOT).indexOf("[pass type]") + "[pass type]".length();
            editedTile.put(firstTile, original.substring(0, passOffset) + original.substring(passOffset).replaceFirst("\\b[012]\\b", "1"));
            edt(() -> { panel.setSource(new ResourcePreviewPanel.Source(mapPath, scripts.get(mapPath), editedTile::get)); return null; });
            await(() -> panel.renderedScene() != null && panel.renderedScene().collisions().get(0).tile().pass(0, 0) == 1);
            edt(() -> {
                screenshot(panel, output.resolve("preview-map-edited-til.png"));
                panel.setSource(new ResourcePreviewPanel.Source("broken.map", "[tile] `incomplete", scripts::get));
                panel.setSource(new ResourcePreviewPanel.Source(tilePath, scripts.get(tilePath), scripts::get)); return null;
            });
            await(() -> panel.renderedScene() != null && panel.renderedScene().tileCount() == 1);
            edt(() -> { screenshot(panel, output.resolve("preview-til-collision.png")); return null; });
            edt(() -> {
                window.setSize(520, 760);
                panel.setSource(new ResourcePreviewPanel.Source(aniPath, scripts.get(aniPath), scripts::get)); return null;
            });
            await(() -> panel.hasPreparedFrame() && panel.frameCount() == 7);
            edt(() -> { screenshot(panel, output.resolve("preview-animation.png")); button(panel, "▶").doClick(); return null; });
            await(() -> panel.hasPreparedFrame() && panel.previewDetails().contains(" #1"));
            edt(() -> {
                components(panel, JCheckBox.class).stream().filter(box -> box.getText().equals("循环")).findFirst().orElseThrow().setSelected(true);
                button(panel, "播放").doClick(); return null;
            });
            await(() -> panel.hasPreparedFrame() && !panel.previewDetails().contains(" #1"));
            edt(() -> { button(panel, "暂停").doClick(); panel.setSource(new ResourcePreviewPanel.Source(itemPath, scripts.get(itemPath), scripts::get)); return null; });
            await(() -> panel.hasPreparedFrame() && panel.previewDetails().contains("iconmaterial2.img #5"));
            edt(() -> { screenshot(panel, output.resolve("preview-item.png")); panel.setSource(new ResourcePreviewPanel.Source(mobPath, scripts.get(mobPath), scripts::get)); return null; });
            await(() -> panel.hasPreparedFrame() && panel.previewDetails().contains("monsterface.img"));
            edt(() -> {
                for (JComboBox<?> combo : components(panel, JComboBox.class))
                    if (combo.getItemCount() > 1 && combo.getItemAt(0) instanceof ResourceScript.Reference) combo.setSelectedIndex(1);
                return null;
            });
            await(() -> panel.hasPreparedFrame() && panel.previewDetails().contains(stayPath));
            Map<String, String> edited = new HashMap<>(scripts);
            String changed = scripts.get(stayPath).replaceFirst("\\[IMAGE POS\\][^\\r\\n]*", "[IMAGE POS] 12 34");
            if (changed.equals(scripts.get(stayPath))) throw new AssertionError("No ANI position to edit");
            edited.put(stayPath, changed);
            edt(() -> { panel.setSource(new ResourcePreviewPanel.Source(mobPath, scripts.get(mobPath), edited::get)); return null; });
            await(() -> panel.hasPreparedFrame() && panel.previewDetails().contains("位置 12.0, 34.0"));
            edt(() -> {
                screenshot(panel, output.resolve("preview-linked-edit.png"));
                panel.setSource(new ResourcePreviewPanel.Source("missing.stk", "[icon] `missing.img` 0", scripts::get)); return null;
            });
            await(() -> panel.previewDetails().contains("找不到 IMG"));
            edt(() -> {
                panel.setSource(new ResourcePreviewPanel.Source("invalid.ani", "[FRAME000] [IMAGE RATE] NaN 1", scripts::get));
                panel.setSource(new ResourcePreviewPanel.Source(aniPath, scripts.get(aniPath), scripts::get)); return null;
            });
            await(() -> panel.hasPreparedFrame() && panel.frameCount() == 7);
            if (!Files.isRegularFile(preferences.resolve("preview.properties"))) throw new AssertionError("NPK directory preference missing");
        } finally { edt(() -> { panel.close(); window.dispose(); return null; }); }
    }

    private static JButton button(Container panel, String text) {
        return components(panel, JButton.class).stream().filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
    }
    private static JCheckBox checkbox(Container panel, String text) {
        return components(panel, JCheckBox.class).stream().filter(box -> box.getText().equals(text)).findFirst().orElseThrow();
    }
    private static String panelPath(String source, String ref, Map<String, String> texts) {
        return ResourceScript.animationCandidates(source, ref).stream().filter(texts::containsKey).findFirst().orElseThrow();
    }
    private static <T extends Component> List<T> components(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) found.add(type.cast(component));
            if (component instanceof Container nested) found.addAll(components(nested, type));
        }
        return found;
    }
    private static void screenshot(JPanel panel, Path output) throws Exception {
        BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); try { panel.paint(graphics); } finally { graphics.dispose(); }
        ImageIO.write(image, "png", output.toFile());
    }
    private static void await(BooleanSupplier condition) throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();
        edt(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            javax.swing.Timer timer = new javax.swing.Timer(30, null);
            timer.addActionListener(event -> {
                if (condition.getAsBoolean()) { timer.stop(); result.complete(null); }
                else if (System.nanoTime() > deadline) { timer.stop(); result.completeExceptionally(new AssertionError("Resource preview timed out")); }
            }); timer.start(); return null;
        });
        result.get(25, TimeUnit.SECONDS);
    }
    private static <T> T edt(Callable<T> work) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        ApplicationManager.getApplication().invokeLater(() -> {
            try { result.complete(work.call()); } catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(20, TimeUnit.SECONDS);
    }
}
