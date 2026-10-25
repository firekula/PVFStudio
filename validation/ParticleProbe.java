import studio.pvf.core.PvfArchive;
import studio.pvf.preview.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Read-only PTL/map-factory audit, collision and multipart regression images. */
public final class ParticleProbe {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[2]); Files.createDirectory(output);
        var resources = NpkResources.scan(Path.of(args[1])); Properties report = new Properties();
        try (var archive = PvfArchive.open(Path.of(args[0]))) {
            Set<String> paths = new HashSet<>(); archive.entries().forEach(e -> paths.add(e.path()));
            Map<String, String> texts = new HashMap<>();
            ScenePreview.TextLookup lookup = path -> {
                if (path.equals("map/probe.til")) return "[pass type] " + "0 ".repeat(420);
                if (!paths.contains(path)) return null;
                if (!texts.containsKey(path)) texts.put(path, new String(archive.readBytes(path), Charset.forName("MS949")));
                return texts.get(path);
            };
            List<String> parseFailures = new ArrayList<>(), notes = new ArrayList<>(); int parsed = 0;
            for (String path : paths.stream().filter(p -> p.endsWith(".ptl")).sorted().toList()) {
                try {
                    var p = ParticleScript.parse(lookup.read(path)); parsed++;
                    if (!p.warnings().isEmpty()) notes.add(path + ": " + p.warnings());
                } catch (Exception error) { parseFailures.add(path + ": " + error.getMessage()); }
            }
            Files.write(output.resolve("ptl-parse-failures.txt"), parseFailures); Files.write(output.resolve("ptl-notes.txt"), notes);
            report.setProperty("ptlParsed", "" + parsed); report.setProperty("ptlParseFailures", "" + parseFailures.size());
            var catalog = EntityScript.catalog(lookup.read("passiveobject/passiveobject.lst"));
            Map<Integer, String> factories = new TreeMap<>(); Map<Integer, Integer> instances = new TreeMap<>();
            List<String> snowMaps = new ArrayList<>();
            for (String path : paths.stream().filter(p -> p.endsWith(".map") && !p.startsWith("sample/")).sorted().toList()) {
                for (var spawn : EntityScript.placements(lookup.read(path)).spawns()) {
                    if (spawn.kind() != EntityScript.Kind.PASSIVE || !catalog.containsKey(spawn.id())) continue;
                    String definition = ResourceScript.animationCandidates("passiveobject/passiveobject.lst", catalog.get(spawn.id())).stream().filter(paths::contains).findFirst().orElseThrow();
                    if (EntityScript.particleFactory(lookup.read(definition), spawn.id()) != null) {
                        factories.put(spawn.id(), definition); instances.merge(spawn.id(), 1, Integer::sum);
                        if (spawn.id() == 51 || spawn.id() == 52) snowMaps.add(path);
                    }
                }
            }
            List<String> audit = new ArrayList<>(); int errors = 0;
            for (var entry : factories.entrySet()) {
                String map = "[tile] `probe.til` `probe.til` `probe.til` [/tile]\n[passive object]\n" + entry.getKey() + " 250 220 160\n[/passive object]";
                var scene = ScenePreview.load("map/a.map", map, lookup, resources, 0, 0);
                for (long time : new long[]{0, 500, 1500, 5000, 60061, 1_200_001}) {
                    var frame = scene.render(time, false);
                    if (frame.warnings().stream().anyMatch(w -> !w.contains("帧标签编号不连续")) || !frame.entities().get(0).problem().isEmpty()) errors++;
                    audit.add(entry.getKey() + " " + entry.getValue() + " @" + time + " particles=" + frame.particleCount()
                            + " draws=" + frame.draws().size() + " warnings=" + frame.warnings() + " problem=" + frame.entities().get(0).problem());
                    if (time <= 1500) screenshot(frame, "factory #" + entry.getKey() + " @" + time, output.resolve("factory-" + entry.getKey() + "-" + time + ".png"), false);
                }
            }
            Files.write(output.resolve("factory-audit.txt"), audit);
            report.setProperty("factoryDefinitions", "" + factories.size()); report.setProperty("factoryInstances", instances.toString());
            report.setProperty("factoryResourceErrors", "" + errors);
            List<String> maps = new ArrayList<>(List.of("map/lorien/104.map", "map/ocean/b2401.map", "map/hendonmyre/hendon_auction.map"));
            if (!snowMaps.isEmpty()) maps.add(snowMaps.get(0));
            for (int i = 0; i < maps.size(); i++) {
                String path = maps.get(i); var scene = ScenePreview.load(path, lookup.read(path), lookup, resources, 0, 0);
                for (long time : new long[]{0, 5000, 60061}) {
                    var frame = scene.render(time, false); screenshot(frame, path + " @" + time, output.resolve("scene-" + i + "-" + time + ".png"), false);
                    if (time == 5000) screenshot(frame, path + " / collision", output.resolve("scene-" + i + "-collision.png"), true);
                    Files.write(output.resolve("scene-" + i + "-" + time + "-warnings.txt"), frame.warnings());
                    report.setProperty("scene." + i + "." + time, path + ": emitters=" + frame.emitterCount() + " particles=" + frame.particleCount()
                            + " objectCollisions=" + frame.objectCollisions().size() + " warnings=" + frame.warnings().size());
                    var again = scene.render(time, false); scene.render(time + 1234, false);
                    var back = scene.render(time, false);
                    var a = again.draws().stream().map(d -> List.of(d.x(), d.y(), d.opacity(), d.prepared().source().img(), d.prepared().source().requestedFrame())).toList();
                    var b = back.draws().stream().map(d -> List.of(d.x(), d.y(), d.opacity(), d.prepared().source().img(), d.prepared().source().requestedFrame())).toList();
                    if (!a.equals(b)) throw new AssertionError("Nondeterministic seek: " + path);
                }
            }
            StringBuilder monsters = new StringBuilder("[tile] " + "`probe.til` ".repeat(6) + "[/tile]\n[monster]\n");
            int[] ids = {21, 22, 50023, 50024, 63, 1071};
            for (int i = 0; i < ids.length; i++) monsters.append(ids[i]).append(" 1 0 ").append(100 + i * 224).append(" 220 0 1 1 `[fixed]` `[normal]`\n");
            monsters.append("[/monster]");
            var frame = ScenePreview.load("map/witches.map", monsters.toString(), lookup, resources, 0, 0).render(400, false);
            screenshot(frame, "Equipment LAYER sections / waiting @400ms", output.resolve("equipment-layers.png"), false);
            Files.write(output.resolve("equipment-layers.txt"), frame.entities().stream().map(e -> e.info().spawn().label() + " " + e.info().animations() + " " + e.problem()).toList());
            report.setProperty("sourceSha256", archive.sha256());
            if (errors != 0) throw new AssertionError("Map factory resource errors: " + errors);
        } finally {
            try (var stream = Files.newOutputStream(output.resolve("result.properties"))) { report.store(stream, "ACT1 particles, object collision, equipment LAYER regression"); }
            report.forEach((k, v) -> System.out.println(k + "=" + v));
        }
    }
    private static void screenshot(ScenePreview.Rendered scene, String label, Path file, boolean collision) throws Exception {
        int w = 1800, h = 850; var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics();
        g.setColor(new Color(48, 51, 57)); g.fillRect(0, 0, w, h); var b = scene.bounds();
        double zoom = Math.min(2, Math.min((w - 40) / Math.max(1, b.getWidth()), (h - 80) / Math.max(1, b.getHeight())));
        scene.paint(g, new Rectangle(0, 40, w, h - 40), w / 2.0 - b.getCenterX() * zoom, (h + 40) / 2.0 - b.getCenterY() * zoom, zoom, 0, 0, collision);
        g.setColor(Color.BLACK); g.fillRect(0, 0, w, 38); g.setColor(Color.WHITE); g.drawString(label + " particles=" + scene.particleCount(), 12, 24); g.dispose();
        ImageIO.write(image, "png", file.toFile());
    }
}
