import studio.pvf.core.PvfArchive;
import studio.pvf.preview.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Real-resource idle composition audit and contact sheets; inputs stay read-only. */
public final class MultipartProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[2]); Files.createDirectory(output);
        NpkResources resources = NpkResources.scan(Path.of(args[1]));
        try (PvfArchive archive = PvfArchive.open(Path.of(args[0]))) {
            Set<String> paths = new HashSet<>(); archive.entries().forEach(e -> paths.add(e.path()));
            Map<String, String> texts = new HashMap<>();
            ScenePreview.TextLookup lookup = path -> {
                if (path.equals("map/probe.til")) return "[pass type] " + "0 ".repeat(420);
                if (!paths.contains(path)) return null;
                if (!texts.containsKey(path)) texts.put(path, new String(archive.readBytes(path), Charset.forName("MS949")));
                return texts.get(path);
            };
            var catalog = EntityScript.catalog(lookup.read("monster/monster.lst"));
            List<Integer> ids = catalog.keySet().stream().sorted().toList();
            List<String> audit = new ArrayList<>(); int composed = 0, failures = 0;
            Map<Integer, ScenePreview> scenes = new HashMap<>();
            for (int id : ids) {
                var scene = ScenePreview.load("map/probe.map", "[tile] `probe.til` [/tile]\n[monster]\n"
                        + id + " 1 0 100 220 0 1 1 `[fixed]` `[normal]`\n[/monster]", lookup, resources, 0, 0);
                var entity = scene.render(0, false).entities().get(0);
                for (long time : new long[]{160, 400, 60061}) {
                    var frame = scene.render(time, false);
                    if (!frame.entities().get(0).problem().isEmpty()) throw new AssertionError(id + " @" + time + ": " + frame.warnings());
                }
                if (!entity.problem().isEmpty()) failures++;
                if (entity.info().animations().size() > 1) composed++;
                scenes.put(id, scene);
                audit.add(id + " " + catalog.get(id) + " parts=" + entity.info().animations().size() + " " + entity.problem()
                        + "\n  " + entity.info().animations() + "\n  " + scene.render(0, false).warnings());
            }
            Files.write(output.resolve("all-monsters.txt"), audit);
            List<Integer> selected = List.of(31, 32, 33, 34, 35, 3001, 11, 12, 14, 700, 701, 702, 704, 705, 50063, 200, 201, 202, 37, 3000, 5006, 50070, 50071, 413);
            for (long time : new long[]{0, 160, 400, 60061}) {
                int columns = 6, cellWidth = 240, cellHeight = 300;
                BufferedImage image = new BufferedImage(columns * cellWidth, ((selected.size() + columns - 1) / columns) * cellHeight, BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics(); g.setColor(new Color(48, 51, 57)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
                for (int i = 0; i < selected.size(); i++) {
                    int id = selected.get(i), x = (i % columns) * cellWidth, y = (i / columns) * cellHeight;
                    var frame = scenes.get(id).render(time, false); Rectangle2D bounds = frame.entities().get(0).bounds();
                    double scale = Math.min(1, Math.min((cellWidth - 20) / bounds.getWidth(), (cellHeight - 50) / bounds.getHeight()));
                    double ox = x + (cellWidth - bounds.getWidth() * scale) / 2 - bounds.getX() * scale, oy = y + 35 - bounds.getY() * scale;
                    Graphics2D cell = (Graphics2D) g.create(); cell.clipRect(x, y, cellWidth, cellHeight);
                    for (var draw : frame.draws()) draw.prepared().paint(cell, ox + draw.x() * scale, oy + draw.y() * scale, scale, draw.mirrored());
                    cell.setColor(Color.CYAN); int footX = (int) (ox + 100 * scale), footY = (int) (oy + 340 * scale);
                    cell.drawLine(footX - 4, footY, footX + 4, footY); cell.drawLine(footX, footY - 4, footX, footY + 4);
                    cell.setColor(Color.WHITE); cell.drawString("#" + id + "  parts=" + frame.entities().get(0).info().animations().size(), x + 8, y + 18);
                    cell.setColor(Color.GRAY); cell.drawRect(x, y, cellWidth - 1, cellHeight - 1); cell.dispose();
                }
                g.dispose(); ImageIO.write(image, "png", output.resolve("multipart-" + time + ".png").toFile());
            }
            if (failures != 0) throw new AssertionError("Monster failures: " + failures);
            System.out.println("monsters=" + ids.size() + " composed=" + composed + " failures=" + failures);
        }
    }
}
