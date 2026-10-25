import studio.pvf.core.PvfArchive;
import studio.pvf.preview.NpkResources;
import studio.pvf.preview.PreviewRenderer;
import studio.pvf.preview.ResourceScript;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/** Read-only real-data probe. Outputs belong under build/, never inside the game directories. */
public final class PreviewProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("PreviewProbe <PVF> <NPK-directory> <new-output-directory>");
        Path output = Path.of(args[2]); Files.createDirectory(output);
        NpkResources resources = NpkResources.scan(Path.of(args[1]));
        Properties report = new Properties();
        report.setProperty("packs", "" + resources.packCount()); report.setProperty("imgs", "" + resources.imageCount());
        report.setProperty("indexWarnings", String.join("; ", resources.warnings()));
        Charset charset = Charset.forName("GBK"); List<String> failures = new ArrayList<>();
        try (PvfArchive archive = PvfArchive.open(Path.of(args[0]))) {
            int parsed = 0;
            for (var entry : archive.entries()) if (entry.path().endsWith(".ani")) {
                try { ResourceScript.animation(new String(archive.readBytes(entry), charset)); parsed++; }
                catch (Exception failure) { failures.add(entry.path() + ": " + failure.getMessage()); }
            }
            report.setProperty("parsedAnimations", "" + parsed);
            report.setProperty("parseFailures", "" + failures.size());
            Files.write(output.resolve("parse-failures.txt"), failures);
            List<String> samples = List.of("passiveobject/monster/ghoul/animation/skullbombsub.ani",
                    "monster/goblin/animation_goblin/stay.ani", "character/mage/animation/stay.ani");
            int decoded = 0;
            for (int sample = 0; sample < samples.size(); sample++) {
                String path = samples.get(sample);
                var animation = ResourceScript.animation(new String(archive.readBytes(path), charset));
                BufferedImage sheet = new BufferedImage(240 * Math.min(8, animation.frames().size()), 260, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = sheet.createGraphics(); g.setColor(new Color(50, 53, 60)); g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                for (int i = 0; i < animation.frames().size(); i++) {
                    var frame = animation.frames().get(i);
                    if (frame.image().isEmpty()) continue;
                    var sprite = resources.sprite(NpkResources.substitute(frame.image(), 0, 0), frame.index());
                    var prepared = PreviewRenderer.prepare(frame, sprite); decoded++;
                    if (i < 8) {
                        var b = prepared.bounds(); double scale = Math.min(2, Math.min(210 / Math.max(1, b.getWidth()), 210 / Math.max(1, b.getHeight())));
                        prepared.paint(g, i * 240 + 120 - b.getCenterX() * scale, 120 - b.getCenterY() * scale, scale);
                        g.setColor(Color.WHITE); g.drawString("Frame " + i + " / " + frame.delay() + "ms", i * 240 + 12, 246);
                    }
                }
                g.dispose(); ImageIO.write(sheet, "png", output.resolve("animation-" + sample + ".png").toFile());
                report.setProperty("sample." + sample, path + " / " + animation.frames().size() + " frames");
            }
            String item = new String(archive.readBytes("stackable/recipe/nrcp_tra_rudechantre.stk"), charset);
            for (var ref : ResourceScript.references(item)) {
                if (!ref.animation()) {
                    ImageIO.write(resources.sprite(ref.path(), ref.index()).image(), "png", output.resolve(ref.tag().replace(' ', '-') + ".png").toFile()); decoded++;
                }
            }
            report.setProperty("decodedFrames", "" + decoded);
        }
        try (var stream = Files.newOutputStream(output.resolve("result.properties"))) { report.store(stream, "Real ACT1 preview validation"); }
        report.forEach((key, value) -> System.out.println(key + "=" + value));
    }
}
