import studio.pvf.core.PvfArchive;
import studio.pvf.preview.NpkResources;
import studio.pvf.preview.ScenePreview;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/** Focused real-map evidence at native pixel scale; no game or source package writes. */
public final class CoordinateProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("CoordinateProbe <PVF> <NPK-directory> <MAP-path> <new-output>");
        Path output = Path.of(args[3]); Files.createDirectory(output);
        NpkResources resources = NpkResources.scan(Path.of(args[1]));
        try (PvfArchive archive = PvfArchive.open(Path.of(args[0]))) {
            Map<String, String> scripts = new HashMap<>(); Set<String> paths = new HashSet<>();
            archive.entries().forEach(entry -> paths.add(entry.path()));
            ScenePreview.TextLookup lookup = path -> {
                if (!paths.contains(path)) return null;
                if (!scripts.containsKey(path)) scripts.put(path, new String(archive.readBytes(path), Charset.forName("MS949")));
                return scripts.get(path);
            };
            ScenePreview scene = ScenePreview.load(args[2], lookup.read(args[2]), lookup, resources, 0, 0);
            var rendered = scene.render(0, false); var bounds = rendered.bounds();
            List<String> lines = new ArrayList<>(); lines.add("map=" + args[2]); lines.add("bounds=" + bounds);
            lines.add("sourceSha256=" + archive.sha256());
            for (var draw : rendered.draws()) {
                var s = draw.prepared().source();
                lines.add("DRAW origin=" + draw.x() + "," + draw.y() + " " + s.img() + " #" + s.requestedFrame()
                        + " crop=" + s.x() + "," + s.y() + " " + s.image().getWidth() + "x" + s.image().getHeight()
                        + " canvas=" + s.canvasWidth() + "x" + s.canvasHeight() + " transform=" + draw.prepared().transform());
            }
            for (var entity : rendered.entities()) lines.add("ENTITY " + entity.info().spawn() + " " + entity.bounds() + " " + entity.problem());
            lines.addAll(rendered.warnings()); Files.write(output.resolve("coordinates.txt"), lines);
            for (boolean overlay : new boolean[]{false, true}) {
                int width = (int) Math.ceil(bounds.getWidth()), height = (int) Math.ceil(bounds.getHeight());
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
                g.setColor(new Color(48, 51, 57)); g.fillRect(0, 0, width, height);
                rendered.paint(g, new Rectangle(0, 0, width, height), -bounds.getX(), -bounds.getY(), 1, 0, 0, overlay, overlay);
                g.dispose(); ImageIO.write(image, "png", output.resolve(overlay ? "collision.png" : "scene.png").toFile());
            }
            System.out.println(output.toAbsolutePath()); System.out.println("draws=" + rendered.draws().size() + " entities=" + rendered.entities().size());
        }
    }
}
