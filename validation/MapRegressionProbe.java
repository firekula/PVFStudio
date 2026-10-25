import studio.pvf.core.PvfArchive;
import studio.pvf.preview.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

/** Read-only rendering of the reported maps at deterministic times. */
public final class MapRegressionProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[2]); Files.createDirectory(output);
        var resources = NpkResources.scan(Path.of(args[1]));
        try (var archive = PvfArchive.open(Path.of(args[0]))) {
            Set<String> paths = new HashSet<>(); archive.entries().forEach(e -> paths.add(e.path()));
            ScenePreview.TextLookup lookup = path -> paths.contains(path)
                    ? new String(archive.readBytes(path), Charset.forName("MS949")) : null;
            for (String map : java.util.List.of("darkhell/b3402", "alfhlyra/alfhlyra")) {
                String path = "map/" + map + ".map", name = map.replace('/', '-');
                var scene = ScenePreview.load(path, lookup.read(path), lookup, resources, 0, 0);
                for (long time : new long[]{0, 10000, 60061}) {
                    var rendered = scene.render(time, false);
                    Files.write(output.resolve(name + "-" + time + ".txt"), rendered.draws().stream().map(d ->
                            d.stage() + " " + d.prepared().source().img() + " " + d.x() + "," + d.y()
                            + " repeat=" + d.repeat() + " scroll=" + d.scroll() + " " + d.prepared().bounds()).toList());
                    for (boolean overlay : new boolean[]{false, true}) {
                        int width = 1700, height = 620;
                        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
                        g.setColor(new Color(48, 51, 57)); g.fillRect(0, 0, width, height);
                        var b = rendered.bounds(); double zoom = Math.min((width - 40) / b.getWidth(), (height - 60) / b.getHeight());
                        rendered.paint(g, new Rectangle(0, 35, width, height - 35), 20, 45, zoom, 0, 0, overlay);
                        g.setColor(Color.WHITE); g.drawString(path + " / " + time + " ms / overlay=" + overlay, 20, 22); g.dispose();
                        ImageIO.write(image, "png", output.resolve(name + "-" + time + (overlay ? "-overlay" : "") + ".png").toFile());
                    }
                    Files.write(output.resolve(name + "-warnings.txt"), rendered.warnings());
                    var limits = rendered.cameraLimits(); var cameras = new LinkedHashSet<Point>();
                    cameras.add(new Point(0, 0)); cameras.add(new Point(limits.maxX(), 0));
                    cameras.add(new Point(0, limits.maxY())); cameras.add(new Point(limits.maxX(), limits.maxY()));
                    cameras.add(new Point(limits.maxX() / 2, limits.maxY() / 2));
                    for (Point camera : cameras) {
                        var image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
                        rendered.paintClient(g, new Rectangle(0, 0, 640, 480), 1, camera, false, false); g.dispose();
                        ImageIO.write(image, "png", output.resolve(name + "-" + time + "-client-" + camera.x + "-" + camera.y + ".png").toFile());
                    }
                    System.out.println(map + " " + time + " draws=" + rendered.draws().size() + " warnings=" + rendered.warnings().size());
                }
            }
        }
    }
}
