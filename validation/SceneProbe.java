import studio.pvf.core.PvfArchive;
import studio.pvf.preview.NpkResources;
import studio.pvf.preview.ScenePreview;
import studio.pvf.preview.SceneScript;
import studio.pvf.preview.EntityScript;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/** Read-only real-data scene probe. Output must be a new directory outside the source game data. */
public final class SceneProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("SceneProbe <PVF> <NPK-directory> <new-output-directory>");
        Path output = Path.of(args[2]); Files.createDirectory(output); NpkResources resources = NpkResources.scan(Path.of(args[1]));
        Charset charset = Charset.forName("MS949"); Properties report = new Properties();
        try (PvfArchive archive = PvfArchive.open(Path.of(args[0]))) {
            Map<String, String> texts = new HashMap<>(); Set<String> paths = new HashSet<>();
            for (var entry : archive.entries()) paths.add(entry.path());
            ScenePreview.TextLookup lookup = path -> {
                if (!paths.contains(path)) return null;
                String cached = texts.get(path);
                if (cached != null) return cached;
                String text = new String(archive.readBytes(path), charset); texts.put(path, text); return text;
            };
            int maps = 0, tiles = 0; List<String> failures = new ArrayList<>(), templateNotes = new ArrayList<>();
            Map<EntityScript.Kind, Set<Integer>> used = new EnumMap<>(EntityScript.Kind.class);
            for (String path : paths.stream().sorted().toList()) {
                try {
                    if (path.endsWith(".map")) {
                        SceneScript.map(lookup.read(path)); maps++;
                        var layout = EntityScript.placements(lookup.read(path));
                        for (String warning : layout.warnings()) (path.startsWith("sample/") ? templateNotes : failures).add(path + "：" + warning);
                        if (!path.startsWith("sample/")) for (var spawn : layout.spawns()) used.computeIfAbsent(spawn.kind(), k -> new TreeSet<>()).add(spawn.id());
                    }
                    else if (path.endsWith(".til")) { SceneScript.tile(lookup.read(path)); tiles++; }
                } catch (Exception failure) { failures.add(path + "：" + failure.getMessage()); }
            }
            report.setProperty("parsedMaps", "" + maps); report.setProperty("parsedTiles", "" + tiles);
            report.setProperty("parseFailures", "" + failures.size()); Files.write(output.resolve("parse-failures.txt"), failures);
            Files.write(output.resolve("template-notes.txt"), templateNotes);
            List<String> audit = new ArrayList<>(); int idleEntities = 0, logicEntities = 0, failedEntities = 0;
            for (var group : used.entrySet()) for (int id : group.getValue()) {
                String placement = switch (group.getKey()) {
                    case NPC -> "[NPC]\n" + id + " `[right]` 100 300 0\n[/NPC]";
                    case MONSTER -> "[monster]\n" + id + " 1 0 100 300 0 1 1 `[fixed]` `[normal]`\n[/monster]";
                    case PASSIVE -> "[passive object]\n" + id + " 100 300 0\n[/passive object]";
                };
                var scene = ScenePreview.load("map/entity-audit.map", "[tile] `map/hendonmyre/tile/hm01.til` [/tile]\n" + placement, lookup, resources, 0, 0);
                var entity = scene.render(0, false).entities().get(0);
                String problem = entity.problem();
                if (problem.isEmpty()) idleEntities++; else if (problem.startsWith("没有 [basic motion]")) logicEntities++; else failedEntities++;
                audit.add(entity.info().spawn().label() + " " + entity.info().definition() + " " + (problem.isEmpty() ? "OK " + entity.info().animations() : problem));
            }
            Files.write(output.resolve("entity-audit.txt"), audit);
            report.setProperty("entityIdleResolved", "" + idleEntities); report.setProperty("entityLogicOnly", "" + logicEntities);
            report.setProperty("entityProblems", "" + failedEntities);
            List<String> samples = List.of("map/sunderlandpoison/b801.map", "map/hendonmyre/hendon_auction.map",
                    "map/underfoot/h6607(2,1).map", "map/hendonmyre/tile/hm01.til");
            for (int i = 0; i < samples.size(); i++) {
                String path = samples.get(i); ScenePreview scene = ScenePreview.load(path, lookup.read(path), lookup, resources, 0, 0);
                for (long time : new long[]{0, 450, 1234, 60061}) {
                    ScenePreview.Rendered rendered = scene.render(time, false);
                    if (time == 0) {
                        report.setProperty("sample." + i, path + ": tiles=" + rendered.tileCount() + ", tracks=" + rendered.trackCount()
                                + ", draws=" + rendered.draws().size() + ", collisionGrids=" + rendered.collisions().size()
                                + ", entities=" + rendered.entities().size() + ", unresolved="
                                + rendered.entities().stream().filter(entity -> !entity.problem().isEmpty()).count());
                        Files.write(output.resolve("entities-" + i + ".txt"), rendered.entities().stream().map(entity ->
                                entity.info().spawn().label() + " " + entity.info().definition() + " " + entity.info().animations()
                                        + (entity.problem().isEmpty() ? "" : " ERROR " + entity.problem())).toList());
                        Files.write(output.resolve("warnings-" + i + ".txt"), rendered.warnings());
                    }
                    if (time <= 450) for (boolean collision : new boolean[]{false, true}) {
                        int width = 1800, height = 900;
                        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB); Graphics2D g = image.createGraphics();
                        g.setColor(new Color(48, 51, 57)); g.fillRect(0, 0, width, height);
                        var bounds = rendered.bounds();
                        double zoom = Math.min(2, Math.min((width - 40) / Math.max(1, bounds.getWidth()), (height - 80) / Math.max(1, bounds.getHeight())));
                        rendered.paint(g, new Rectangle(0, 40, width, height - 40), width / 2.0 - bounds.getCenterX() * zoom,
                                (height + 40) / 2.0 - bounds.getCenterY() * zoom, zoom, 0, 0, collision);
                        g.setColor(Color.BLACK); g.fillRect(0, 0, width, 38); g.setColor(Color.WHITE);
                        g.drawString(path + " / " + time + " ms / collision=" + collision, 12, 24); g.dispose();
                        ImageIO.write(image, "png", output.resolve("scene-" + i + "-" + time + (collision ? "-collision" : "") + ".png").toFile());
                    }
                    if (rendered.draws().isEmpty()) throw new AssertionError("No drawables: " + path);
                }
            }
            report.setProperty("sourceSha256", archive.sha256());
        }
        try (var stream = Files.newOutputStream(output.resolve("result.properties"))) { report.store(stream, "ACT1 real map/tile scene validation"); }
        report.forEach((key, value) -> System.out.println(key + "=" + value));
    }
}
