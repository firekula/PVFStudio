package studio.pvf.intellij;

import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.preview.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Real 86 entity counts plus marker-free pixel differences, ACT components and DGN room context. */
final class Entities86WorkbenchSmoke {
    private static final String MAP = "map/fruz_station/12057(3,2)n.map";
    static void run(Path archive, Path output) throws Exception {
        var frame = edt(() -> { var f = new PvfStudioFrame(output.resolve("workspaces"), false); f.setSize(1550, 1100); f.setVisible(true); return f; });
        try {
            await(() -> button(frame, "打开最近的文件").isEnabled());
            edt(() -> { frame.openArchive(archive); return null; }); await(() -> ready(frame));
            var field = PvfStudioFrame.class.getDeclaredField("preview"); field.setAccessible(true);
            var workspaceField = PvfStudioFrame.class.getDeclaredField("workspace"); workspaceField.setAccessible(true);
            var resourcesField = ResourcePreviewPanel.class.getDeclaredField("resources"); resourcesField.setAccessible(true);
            var panel = edt(() -> (ResourcePreviewPanel) field.get(frame)); var workspace = edt(() -> (PvfWorkspace) workspaceField.get(frame));
            edt(() -> { frame.openEntry(MAP); return null; }); await(() -> ready(frame) && panel.renderedScene() != null && panel.previewDetails().contains(MAP));
            edt(() -> { components(panel, JSlider.class).getFirst().setValue(2146); return null; });
            await(() -> panel.renderedScene() != null && panel.renderedScene().milliseconds() == 2146);
            var rendered = edt(panel::renderedScene);
            check(count(rendered, EntityScript.Kind.MONSTER) == 7, "Flat MAP lost monster records");
            check(count(rendered, EntityScript.Kind.PASSIVE) == 15, "Flat MAP lost passive records");
            check(rendered.entities().stream().allMatch(e -> e.problem().isEmpty() && !e.info().animations().isEmpty()), "ACT entity visual failed to load");
            check(rendered.warnings().isEmpty(), "Target MAP still has load warnings: " + rendered.warnings());
            for (var entity : rendered.entities()) {
                boolean hasPixels = false;
                for (var draw : rendered.draws()) {
                    var spawn = entity.info().spawn();
                    if (draw.x() != spawn.x() || draw.y() != SceneCoordinates.screenY(spawn.y(), spawn.z())) continue;
                    String active = rendered.activeFrames().keySet().stream().filter(key -> key.startsWith(draw.order() + ":")).findFirst().orElse("");
                    if (entity.info().animations().stream().anyMatch(path -> active.endsWith(":" + path)) && nonempty(draw.prepared().image())) { hasPixels = true; break; }
                }
                check(hasPixels, "Entity has a marker but no decoded sprite: " + entity.info().spawn());
            }
            NpkResources indexed = edt(() -> (NpkResources) resourcesField.get(panel)); Charset charset = Charset.forName("Big5");
            String original = workspace.readText(MAP, charset);
            String sceneryOnly = original.replaceAll("(?is)\\[monster\\].*?\\[/monster\\]", "").replaceAll("(?is)\\[passive object\\].*?\\[/passive object\\]", "");
            var baseline = ScenePreview.load(MAP, sceneryOnly, path -> workspace.contains(path) ? workspace.readText(path, charset) : null, indexed, 0, 0).render(2146, false);
            BufferedImage actualPixels = pixels(rendered), basePixels = pixels(baseline); int changed = 0;
            for (int y = 0; y < actualPixels.getHeight(); y++) for (int x = 0; x < actualPixels.getWidth(); x++) if (actualPixels.getRGB(x, y) != basePixels.getRGB(x, y)) changed++;
            check(changed > 1000, "Entity tracks did not change rendered pixels with markers disabled");
            javax.imageio.ImageIO.write(actualPixels, "png", output.resolve("scene-entities-no-markers.png").toFile());
            javax.imageio.ImageIO.write(basePixels, "png", output.resolve("scene-scenery-only.png").toFile());
            edt(() -> { screenshot(frame, output.resolve("entities-map.png")); checkbox(panel, "对象标记").doClick(); checkbox(panel, "碰撞区域").doClick();
                screenshot(frame, output.resolve("entities-map-overlays.png")); button(panel, "播放").doClick(); return null; });
            await(() -> panel.renderedScene() != null && panel.renderedScene().milliseconds() > 2300);
            edt(() -> { button(panel, "暂停").doClick(); frame.openEntry("dungeon/act9/fruz_station.dgn"); return null; });
            var controls = edt(() -> components(frame, DungeonPanel.class).getFirst());
            await(() -> ready(frame) && controls.isVisible() && panel.renderedScene() != null && panel.previewDetails().contains("dungeon/act9/fruz_station.dgn"));
            edt(() -> { controls.selectRoom(new DungeonScript.Position(3,2)); return null; });
            await(() -> panel.renderedScene() != null && panel.previewDetails().contains(MAP));
            edt(() -> { check(count(panel.renderedScene(), EntityScript.Kind.MONSTER) == 7, "DGN room lost monster visuals"); screenshot(frame, output.resolve("entities-dungeon-room.png")); return null; });
            check(workspace.dirtyPaths().isEmpty(), "Entity preview changed drafts");
            Files.writeString(output.resolve("entities-86-result.txt"), "PASS: 7 monsters + 15 passive objects; every entity has decoded pixels at its own placement; " + rendered.draws().size() + " scene draws, ACT base/sub components, marker-free pixel difference="
                    + changed + "; playback and DGN room context; source drafts unchanged\n");
        } finally {
            edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)); return null; });
            await(() -> !frame.isDisplayable());
        }
    }
    private static long count(ScenePreview.Rendered scene, EntityScript.Kind kind) { return scene.entities().stream().filter(e -> e.info().spawn().kind() == kind).count(); }
    private static boolean nonempty(BufferedImage image) { for (int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) if ((image.getRGB(x,y)>>>24)!=0) return true; return false; }
    private static BufferedImage pixels(ScenePreview.Rendered scene) {
        var image = new BufferedImage(1400,900,BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
        scene.paint(g,new Rectangle(0,0,1400,900),100,150,1,0,0,false,false); g.dispose(); return image;
    }
    private static JCheckBox checkbox(Container root, String text) { return components(root, JCheckBox.class).stream().filter(c -> text.equals(c.getText())).findFirst().orElseThrow(); }
    private static boolean ready(PvfStudioFrame frame) { return frame.isEnabled() && button(frame,"打开 PVF…").isEnabled(); }
}
