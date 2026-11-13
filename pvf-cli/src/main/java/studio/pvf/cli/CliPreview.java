package studio.pvf.cli;

import studio.pvf.core.PvfFormat;
import studio.pvf.preview.CombatDebug;
import studio.pvf.preview.GameItemInfo;
import studio.pvf.preview.GamePreview;
import studio.pvf.preview.GamePreviewPainter;
import studio.pvf.preview.GameScript;
import studio.pvf.preview.NpkResources;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import static studio.pvf.cli.CliJson.object;

final class CliPreview {
    private static final int WIDTH = 960, HEIGHT = 640;

    private CliPreview() {}

    static Object inspect(CliSource source, AutomationCli.Arguments args) throws IOException {
        String path = entry(source, args);
        String script = source.read(path);
        int level = level(script, args);
        var info = GameItemInfo.read(path, script, source::read, level);
        return object("path", path, "name", info.name(), "english", info.english(), "type", info.type(),
                "level", path.endsWith(".skl") ? level : null, "rarity", info.rarity(),
                "rows", info.rows().stream().map(row -> object("label", row.label(), "value", row.value())).toList(),
                "description", info.explanation(), "warnings", info.warnings());
    }

    static Object preview(CliSource source, AutomationCli.Arguments args) throws IOException {
        if (source.archive().format() != PvfFormat.ACT1) {
            throw new CliFailure("UNSUPPORTED_FORMAT", "Game-effect preview currently requires an ACT1 PVF.",
                    "Use inspect or cat to read other supported PVF formats.");
        }
        String path = entry(source, args);
        String script = source.read(path);
        int level = level(script, args);
        String effect = args.value("effect", "auto");
        boolean left = args.value("facing", "right").equals("left");
        Path output = args.has("output") ? AutomationCli.newOutput(Path.of(args.value("output"))) : null;
        Path directory = Path.of(args.value("npk"));
        if (!Files.isDirectory(directory)) {
            throw new CliFailure("RESOURCES_NOT_FOUND", "NPK directory not found: " + directory,
                    "Pass the matching ImagePacks2 directory to --npk.");
        }
        var resources = NpkResources.scan(directory);
        if (resources.imageCount() == 0) {
            throw new CliFailure("RESOURCES_NOT_FOUND", "No supported images in: " + directory,
                    "Pass the matching ImagePacks2 directory to --npk.");
        }
        var options = new GamePreview.Options("gunner", "waiting motion", Map.of(), level, true,
                effect.equals("auto") ? "" : effect);
        var model = GamePreview.load(path, script, source::read, source.paths(), resources, options);
        var effects = model.effects();
        if (!effect.equals("auto") && effects.stream().noneMatch(choice -> choice.key().equals(effect))) {
            throw new CliFailure("UNKNOWN_EFFECT", "Unknown preview effect: " + effect,
                    "Run preview without --effect and choose a key from data.effects.");
        }
        var choices = new java.util.ArrayList<Map<String, Object>>();
        choices.add(object("key", "auto", "label", "自动", "note", ""));
        effects.forEach(choice -> choices.add(object("key", choice.key(), "label", choice.label(), "note", choice.note())));
        var data = object("path", path, "name", model.info().name(), "level", level, "effect", effect,
                "facing", left ? "left" : "right", "available", model.unavailableReason().isEmpty(),
                "unavailable_reason", model.unavailableReason(), "duration_ms", model.duration(), "effects", choices,
                "job", model.appearance().job(), "warnings", model.warnings().stream().limit(30).toList());
        if (output == null && !args.has("time")) return data;
        if (!model.unavailableReason().isEmpty()) {
            throw new CliFailure("PREVIEW_UNAVAILABLE", model.unavailableReason(),
                    "Run preview without --time or --output to see the available branches.");
        }
        int time = args.number("time", 0);
        if (model.usingSequence() && time >= model.duration()) {
            throw new IllegalArgumentException("--time must be less than " + model.duration() + " ms for this effect.");
        }
        var frame = model.render(time, left, false, true);
        if (frame.draws().isEmpty() && frame.combatVolumes().isEmpty()) {
            throw new CliFailure("PREVIEW_EMPTY", "No sprites or combat boxes are visible at " + time + " ms.",
                    "Check the matching resources and choose another time or effect.");
        }
        data.put("time_ms", frame.milliseconds());
        data.put("warnings", frame.warnings().stream().limit(30).toList());
        data.put("combat_boxes", frame.combatVolumes().stream().map(CliPreview::box).toList());
        if (output != null) {
            writePng(frame, output, args.has("hitboxes"));
            data.put("output", output.toString());
            data.put("width", WIDTH);
            data.put("height", HEIGHT);
        }
        return data;
    }

    private static String entry(CliSource source, AutomationCli.Arguments args) throws IOException {
        String path = source.requireEntry(args.arg(1));
        if (List.of(".equ", ".skl", ".stk").stream().noneMatch(path::endsWith)) {
            throw new IllegalArgumentException(args.command().name + " supports .equ, .skl and .stk entries. Use cat for other paths.");
        }
        return path;
    }

    private static int level(String script, AutomationCli.Arguments args) {
        int maximum = GameScript.integer(GameScript.value(GameScript.fields(script), "maximum level"), 1);
        return Math.min(Math.max(1, maximum), args.number("level", 1));
    }

    private static Object box(CombatDebug.Volume volume) {
        var box = volume.orientedBox();
        var range = volume.targetRange();
        return object("kind", volume.kind().name(), "label", volume.label(),
                "origin_x", volume.x(), "origin_screen_y", volume.y(),
                "x", box.x(), "y", box.y(), "z", box.z(), "width", box.width(), "depth", box.depth(), "height", box.height(),
                "target_range", range == null ? null : object("radius", range.radius(), "max_z", range.maxZ(),
                        "interval_ms", range.interval(), "pulse", volume.pulse()));
    }

    private static void writePng(GamePreview.Rendered frame, Path output, boolean hitboxes) throws IOException {
        var bounds = frame.bounds();
        double zoom = Math.min(4, Math.min((WIDTH - 48) / Math.max(1, bounds.getWidth()),
                (HEIGHT - 48) / Math.max(1, bounds.getHeight())));
        double x = WIDTH / 2.0 - bounds.getCenterX() * zoom;
        double y = HEIGHT / 2.0 - bounds.getCenterY() * zoom;
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            new GamePreviewPainter().paint(graphics, frame, WIDTH, HEIGHT, x, y, zoom, new Color(44, 47, 51));
            if (hitboxes) CombatDebug.paint(graphics, frame.combatVolumes(), x, y, zoom, false);
        } finally {
            graphics.dispose();
        }
        var encoded = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", encoded)) throw new IOException("PNG encoder unavailable.");
        Files.write(output, encoded.toByteArray(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
