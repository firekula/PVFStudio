import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.preview.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;

/** Real MAP entity/resource audit. Never executes ACT behaviors or writes source assets. */
public class EntityCompatibilityAudit {
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) throw new IllegalArgumentException("EntityCompatibilityAudit <PVF> <NPK directory> <new output directory> [map prefix]");
        Path output = Path.of(args[2]); Files.createDirectory(output);
        String prefix = args.length > 3 ? args[3] : "map/fruz_station/";
        Charset charset = Charset.forName("Big5"); var resources = NpkResources.scan(Path.of(args[1]));
        Properties report = new Properties(); List<String> scenes = new ArrayList<>(), problems = new ArrayList<>();
        int maps = 0, monsters = 0, passive = 0, visuals = 0, issues = 0;
        try (var workspace = PvfWorkspace.open(Path.of(args[0]), output.resolve("workspace"))) {
            Set<String> paths = workspace.paths(); Map<String,String> cache = new HashMap<>();
            ScenePreview.TextLookup lookup = path -> {
                if (!paths.contains(path)) return null;
                String text = cache.get(path);
                if (text == null) { text = workspace.readText(path, charset); cache.put(path, text); }
                return text;
            };
            for (String path : paths.stream().filter(p -> p.startsWith(prefix) && p.endsWith(".map")).sorted().toList()) {
                var scene = ScenePreview.load(path, lookup.read(path), lookup, resources, 0, 0);
                var frame = scene.render(2146, false); maps++;
                int m = 0, p = 0, good = 0;
                for (var entity : frame.entities()) {
                    if (entity.info().spawn().kind() == EntityScript.Kind.MONSTER) m++;
                    if (entity.info().spawn().kind() == EntityScript.Kind.PASSIVE) p++;
                    if (!entity.info().animations().isEmpty() && entity.problem().isEmpty()) good++;
                    else { issues++; problems.add(path + " " + entity.info().spawn() + " : " + entity.problem()); }
                }
                for (String warning : frame.warnings()) problems.add(path + " : " + warning);
                monsters += m; passive += p; visuals += good;
                scenes.add(path + " monsters=" + m + " passive=" + p + " visualEntities=" + good + " draws=" + frame.draws().size() + " warnings=" + frame.warnings().size());
                if (path.equals("map/fruz_station/12057(3,2)n.map") && (m != 7 || p != 15 || good != 22 || !frame.warnings().isEmpty())) throw new AssertionError("Target MAP entity regression");
            }
            if (!workspace.dirtyPaths().isEmpty()) throw new AssertionError("Audit modified drafts");
            workspace.archive().verifyUnchanged(); report.setProperty("archive.sha256", workspace.archive().sha256());
        }
        report.setProperty("maps", "" + maps); report.setProperty("monsterPlacements", "" + monsters); report.setProperty("passivePlacements", "" + passive);
        report.setProperty("visualEntities", "" + visuals); report.setProperty("entitiesWithIssues", "" + issues); report.setProperty("status", "COMPLETED");
        Files.write(output.resolve("scenes.txt"), scenes); Files.write(output.resolve("diagnostics.txt"), problems);
        try (var stream = Files.newOutputStream(output.resolve("audit.properties"))) { report.store(stream, "Read-only real entity visual audit"); }
        System.out.println("maps=" + maps + ", monsters=" + monsters + ", passive=" + passive + ", visuals=" + visuals + ", entitiesWithIssues=" + issues);
    }
}
