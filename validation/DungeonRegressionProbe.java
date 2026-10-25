import studio.pvf.core.PvfArchive;
import studio.pvf.preview.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.List;

/** Read-only DGN/catalog audit, with no filename-based room selection. */
public final class DungeonRegressionProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]); Files.createDirectory(output);
        List<String> results = new ArrayList<>(); int successes = 0, failures = 0, combinations = 0, frames = 0;
        var resources = args.length > 2 ? NpkResources.scan(Path.of(args[2])) : null;
        try (var archive = PvfArchive.open(Path.of(args[0]))) {
            Set<String> paths = new HashSet<>(); archive.entries().forEach(entry -> paths.add(entry.path()));
            Map<String, String> cache = new HashMap<>();
            ScenePreview.TextLookup lookup = path -> {
                if (!paths.contains(path)) return null;
                String text = cache.get(path);
                if (text == null) { text = new String(archive.readBytes(path), Charset.forName("MS949")); cache.put(path, text); }
                return text;
            };
            for (var entry : archive.entries()) {
                if (!entry.path().startsWith("dungeon/") || !entry.path().endsWith(".dgn")) continue;
                try {
                    var dungeon = DungeonPreview.load(entry.path(), lookup.read(entry.path()), lookup);
                    for (int i = 0; i < dungeon.definition().mazes().size(); i++) {
                        var maze = dungeon.definition().mazes().get(i);
                        var start = maze.starts().isEmpty() ? null : maze.starts().get(0);
                        var boss = maze.bosses().stream().filter(p -> !p.equals(start)).findFirst().orElse(null);
                        var layout = dungeon.arrange(i, start, boss, 0);
                        results.add(entry.path() + " maze=" + i + " rooms=" + layout.rooms().size() + " warnings=" + layout.warnings());
                        for (var candidateStart : maze.starts().isEmpty() ? Collections.singletonList(start) : maze.starts())
                            for (var candidateBoss : maze.bosses().isEmpty() ? Collections.singletonList(boss) : maze.bosses()) {
                                if (candidateStart != null && candidateStart.equals(candidateBoss)) continue;
                                var candidate = dungeon.arrange(i, candidateStart, candidateBoss, 0); combinations++;
                                if (!candidate.warnings().isEmpty()) results.add("COMBINATION " + entry.path() + " " + i + " " + candidateStart + " " + candidateBoss + " " + candidate.warnings());
                            }
                        if (resources != null) {
                            boolean everyRoom = entry.path().equals("dungeon/act1/lorien.dgn") || entry.path().equals("dungeon/act1/mirkwooddeep.dgn");
                            for (var room : layout.rooms()) for (boolean open : new boolean[]{false, true}) {
                                if (!everyRoom && (i != 0 || !room.start() && !room.boss())) continue;
                                if (room.map() == null) continue;
                                var gates = dungeon.gates(layout, room, room.map(), open, true);
                                var scene = ScenePreview.loadRoom(room.map().path(), lookup.read(room.map().path()), lookup, resources, gates, 0, 0);
                                var frame = scene.render(60061, false); frames++;
                                if (!frame.warnings().isEmpty()) results.add("RENDER " + room.map().path() + " " + frame.warnings());
                                if (room.start() || room.boss()) {
                                    var image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
                                    frame.paintClient(g, new Rectangle(0, 0, 640, 480), 1, new Point(), false, false); g.dispose();
                                    ImageIO.write(image, "png", output.resolve(entry.path().replace('/', '-') + "-" + i + "-" + room.position().x() + "-" + room.position().y() + "-" + open + ".png").toFile());
                                }
                            }
                        }
                    }
                    successes++;
                } catch (Exception failure) { failures++; results.add("ERROR " + entry.path() + " " + failure); }
            }
        }
        Files.write(output.resolve("audit.txt"), results);
        System.out.println("DGN parsed=" + successes + " failures=" + failures + " combinations=" + combinations + " rendered=" + frames);
    }
}
