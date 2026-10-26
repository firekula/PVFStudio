import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.preview.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;

/** Read-only projected DGN/layout audit; assets and diagnostics remain in a new local output directory. */
public class DungeonCompatibilityAudit {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("DungeonCompatibilityAudit <PVF> <new output directory> [charset=Big5]");
        Charset charset = Charset.forName(args.length == 3 ? args[2] : "Big5");
        Path output = Path.of(args[1]); Files.createDirectory(output);
        Properties result = new Properties(); List<String> failures = new ArrayList<>(), rooms = new ArrayList<>();
        try (var workspace = PvfWorkspace.open(Path.of(args[0]), output.resolve("workspace"))) {
            Set<String> paths = workspace.paths(); Map<String,String> cache = new HashMap<>();
            ScenePreview.TextLookup lookup = path -> {
                if (!paths.contains(path)) return null;
                String text = cache.get(path);
                if (text == null) { text = workspace.readText(path, charset); cache.put(path, text); }
                return text;
            };
            int parsed = 0, mazes = 0, selected = 0;
            for (String path : paths.stream().sorted().toList()) {
                if (!path.startsWith("dungeon/") || !path.endsWith(".dgn")) continue;
                try { var definition = DungeonScript.parse(lookup.read(path)); parsed++; mazes += definition.mazes().size(); }
                catch (Exception failure) { failures.add(path + " : " + failure.getMessage()); }
            }
            for (String path : paths.stream().sorted().toList()) {
                if (!List.of("/fruz_station.dgn", "/kohlepowerstation.dgn", "/grandine.dgn", "/blackvolcano.dgn").stream().anyMatch(path::endsWith)) continue;
                var dungeon = DungeonPreview.load(path, lookup.read(path), lookup); selected++;
                for (int i = 0; i < dungeon.definition().mazes().size(); i++) {
                    var maze = dungeon.definition().mazes().get(i);
                    var start = maze.starts().isEmpty() ? null : maze.starts().get(0);
                    var boss = maze.bosses().isEmpty() ? null : maze.bosses().get(0);
                    var layout = dungeon.arrange(i, start, boss, 0);
                    rooms.add(path + " #" + i + " size=" + maze.width() + "x" + maze.height() + " rooms=" + layout.rooms().size()
                            + " resolved=" + layout.rooms().stream().filter(r -> r.map() != null).count()
                            + " start=" + start + " boss=" + boss + " warnings=" + layout.warnings());
                }
            }
            if (!workspace.dirtyPaths().isEmpty()) throw new AssertionError("Audit modified drafts");
            result.setProperty("archive.sha256", workspace.archive().sha256()); result.setProperty("charset", charset.name());
            result.setProperty("parsed", "" + parsed); result.setProperty("layouts", "" + mazes);
            result.setProperty("unsupported", "" + failures.size()); result.setProperty("selectedDungeons", "" + selected);
            result.setProperty("status", "COMPLETED");
        }
        Files.write(output.resolve("parse-errors.txt"), failures); Files.write(output.resolve("rooms.txt"), rooms);
        try (var stream = Files.newOutputStream(output.resolve("audit.properties"))) { result.store(stream, "Real projected DGN audit; unsupported structures are reported, never guessed"); }
        System.out.println("DGN parsed=" + result.getProperty("parsed") + ", layouts=" + result.getProperty("layouts") + ", unsupported=" + failures.size());
    }
}
