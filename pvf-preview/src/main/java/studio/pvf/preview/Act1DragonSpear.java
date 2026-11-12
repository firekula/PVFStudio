package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Mage state 25, with separate hit and miss transitions at the held body frame. */
final class Act1DragonSpear {
    static final String PATH = "skill/mage/dragonspear.skl";
    static final String MISS = "dragonspear:miss";
    private static final String CHARACTER = "character/mage/mage.chr";
    private static final String ACTION = "etc motion:17";
    private static final String EFFECT = "character/mage/effect/animation/dragonspear/";
    private static final int HOLD_FRAME = 11;
    private static final int TIP_X = 322;
    private static final int TIP_Z = 43;
    private static final int ATTACHED_LAYER = 3501;
    private static final int BLAST_LAYER = 3600;

    private Act1DragonSpear() {}

    static Act1SkillSequence find(String text, GameScript.Lookup lookup, Map<String, String> actions,
                                  int level, boolean miss) throws IOException {
        String reference = actions.get(ACTION);
        if (reference == null || !GameScript.normalize(reference).endsWith("/dragonspear.ani")) return null;
        var body = Act1SkillSequence.frames(CHARACTER, reference, lookup);
        if (body.size() <= HOLD_FRAME) return null;
        var strike = spearClips(lookup, "spearattack", 3);
        var disappear = spearClips(lookup, "speardisappear", 2);
        if (strike.isEmpty() || disappear.isEmpty()) return null;

        var fields = GameScript.fields(text);
        int speed = 100 + Act1SkillSequence.levelValue(fields, level, 2, 0, 0, 200);
        long hold = duration(body.subList(0, HOLD_FRAME), speed);
        var visuals = new ArrayList<Visual>();
        addSpearLayers(visuals, strike, "煌龙偃月 · 刺击", 0, hold, speed);

        long fade = hold;
        long lastEffect = hold;
        if (!miss) {
            var extended = spearClips(lookup, "spearchaser", 3);
            var chasers = spearClips(lookup, "chaser", 2);
            var bursts = objectLayers(lookup, 23031);
            var finalBlast = objectLayers(lookup, 23032);
            if (extended.isEmpty() || chasers.isEmpty() || chasers.stream().anyMatch(clip -> clip.frames().size() < 8)
                    || bursts.isEmpty() || finalBlast.isEmpty()) return null;
            var settings = GameScript.values(GameScript.fields(lookup.read("data/skill/mage.sd")), "int");
            int count = Math.min(16, Act1SkillSequence.staticValue(fields, 0, 7));
            int radius = setting(settings, "ds chaser distance", 100, 1000);
            int interval = setting(settings, "ds chaser term", 140, 2000);
            int finalDelay = setting(settings, "ds final bomb delay", 400, 10000);
            int lastDelay = setting(settings, "ds last delay", 300, 10000);
            double burstScale = Act1SkillSequence.levelValue(fields, level, 3, 120, 1, 400) / 100.0;
            double finalScale = Act1SkillSequence.levelValue(fields, level, 5, 135, 1, 400) / 100.0;

            // The client randomizes the azimuth order and blast offsets. One repeatable sample is used in the preview.
            var random = new Random(69);
            var angles = new ArrayList<Integer>();
            int startAngle = random.nextInt(30);
            for (int i = 0; i < count; i++) angles.add(startAngle + i * 360 / count);
            Collections.shuffle(angles, random);
            long lastBurst = hold;
            for (int i = 0; i < angles.size(); i++) {
                long burst = addChaser(visuals, chasers, hold, speed, radius, angles.get(i), i * interval);
                int x = TIP_X - 30 + random.nextInt(60);
                int z = 13 + random.nextInt(60);
                lastEffect = Math.max(lastEffect,
                        addBlast(visuals, bursts, "煌龙偃月 · 炫纹爆炸", burst, x, z, burstScale));
                lastBurst = Math.max(lastBurst, burst);
            }
            long finalStart = lastBurst + finalDelay;
            lastEffect = Math.max(lastEffect,
                    addBlast(visuals, finalBlast, "煌龙偃月 · 最终爆炸", finalStart, TIP_X, TIP_Z, finalScale));
            fade = finalStart + lastDelay;
            addSpearLayers(visuals, extended, "煌龙偃月 · 刀尖", hold, fade, speed);
        }

        long end = fade + duration(disappear.get(1).frames(), speed);
        for (int i = 0; i < disappear.size(); i++) {
            Clip clip = disappear.get(i);
            long clipEnd = Math.min(end, fade + duration(clip.frames(), speed));
            visuals.add(clip.visual("煌龙偃月 · 消散", ATTACHED_LAYER + i, fade, clipEnd, speed));
        }
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(ACTION, 0, hold, null, speed != 100, null, 0, HOLD_FRAME));
        // Frame 11 is the state boundary, but the stock avatar ANI ends at the visually identical frame 10.
        int heldFrame = sameVisual(body.get(HOLD_FRAME - 1), body.get(HOLD_FRAME)) ? HOLD_FRAME - 1 : HOLD_FRAME;
        poses.add(new Pose(ACTION, hold, end, null, false, null, heldFrame, heldFrame + 1));
        if (lastEffect > end && actions.containsKey("waiting motion")) {
            poses.add(new Pose("waiting motion", end, lastEffect));
        }
        String note = miss
                ? "煌龙偃月未命中时，刺击结束后刀身直接消散"
                : "煌龙偃月演示命中后刀尖周围的龙之炫纹、连续爆炸与最终爆炸；目标位置采用客户端的 322 像素参考点";
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(), note);
    }

    private static boolean sameVisual(ResourceScript.Frame first, ResourceScript.Frame second) {
        return first.image().equals(second.image()) && first.index() == second.index()
                && first.x() == second.x() && first.y() == second.y()
                && first.scaleX() == second.scaleX() && first.scaleY() == second.scaleY()
                && first.rotation() == second.rotation() && first.effect().equals(second.effect())
                && first.r() == second.r() && first.g() == second.g() && first.b() == second.b() && first.a() == second.a();
    }

    /** State-entry handler 0x614D40 rewrites ANI positions and the frame 5 delay before attaching each pair. */
    private static long addChaser(List<Visual> visuals, List<Clip> chasers, long start, int speed, int radius,
                                  int angle, int extraDelay) {
        double radians = Math.toRadians(angle);
        double x = Math.cos(radians) * radius;
        double y = Math.sin(radians) * radius;
        long burst = start;
        for (int layer = 0; layer < chasers.size(); layer++) {
            Clip clip = chasers.get(layer);
            var overrides = new HashMap<Integer, FrameOverride>();
            long duration = 0;
            for (int i = 0; i < clip.frames().size(); i++) {
                int delay = scaled(i == 5 ? extraDelay : clip.frames().get(i).delay(), speed);
                double distance = i == 0 ? .5 : i <= 6 ? 1 : 0;
                overrides.put(i, new FrameOverride((int)(x * distance), (int)(y * distance), delay));
                duration += delay;
            }
            if (layer == 0) burst = start + duration;
            visuals.add(new Visual(clip.path(), "煌龙偃月 · 龙之炫纹", ATTACHED_LAYER + 1, start, burst,
                    TIP_X, -TIP_Z, null, 1, true, new VisualStyle(null, 0, List.of(), overrides)));
        }
        return burst;
    }

    private static void addSpearLayers(List<Visual> visuals, List<Clip> clips, String label, long start, long end, int speed) {
        for (int i = 0; i < clips.size(); i++) {
            visuals.add(clips.get(i).visual(label, ATTACHED_LAYER + i, start, end, speed));
        }
    }

    private static long addBlast(List<Visual> visuals, List<ObjectLayer> clips, String label, long start,
                                 int x, int z, double scale) {
        long life = duration(clips.get(0).clip().frames(), 100);
        for (var part : clips) {
            long end = start + Math.min(life, duration(part.clip().frames(), 100));
            visuals.add(new Visual(part.clip().path(), label, BLAST_LAYER + part.layer(), start, end,
                    x, -z, null, scale, true));
        }
        return start + life;
    }

    private static List<Clip> spearClips(GameScript.Lookup lookup, String stem, int count) throws IOException {
        var clips = new ArrayList<Clip>();
        for (int i = 1; i <= count; i++) {
            Clip clip = clip(lookup, EFFECT + stem + i + ".ani");
            if (clip == null) return List.of();
            clips.add(clip);
        }
        return clips;
    }

    private static List<ObjectLayer> objectLayers(GameScript.Lookup lookup, int id) throws IOException {
        String path = GameEffectRecipes.passiveObject(lookup, id);
        if (path == null) return List.of();
        var fields = GameScript.fields(lookup.read(path));
        String basic = GameScript.resolve(path, GameScript.value(fields, "basic motion"), lookup);
        Clip main = clip(lookup, basic);
        if (main == null) return List.of();
        var result = new ArrayList<ObjectLayer>();
        result.add(new ObjectLayer(main, 0));
        var effects = GameScript.values(fields, "add object effect");
        for (int i = 1; i + 1 < effects.size() && i <= 31; i += 2) {
            Clip attachment = clip(lookup, GameScript.resolve(path, effects.get(i), lookup));
            int layer = GameScript.integer(effects.get(i + 1), 0);
            if (attachment != null && layer >= -16 && layer <= 16) result.add(new ObjectLayer(attachment, layer));
        }
        return result;
    }

    private static Clip clip(GameScript.Lookup lookup, String path) throws IOException {
        String text = path == null ? null : lookup.read(path);
        if (text == null) return null;
        var frames = ResourceScript.animation(text).frames();
        return frames.isEmpty() ? null : new Clip(path, frames);
    }

    private static int setting(List<String> values, String name, int fallback, int maximum) {
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if (values.get(i).equalsIgnoreCase(name)) {
                return Math.max(0, Math.min(maximum, GameScript.integer(values.get(i + 1), fallback)));
            }
        }
        return fallback;
    }

    private static int scaled(int delay, int speed) {
        return (int)(delay * 100L / speed);
    }

    private static long duration(List<ResourceScript.Frame> frames, int speed) {
        return frames.stream().mapToLong(frame -> scaled(frame.delay(), speed)).sum();
    }

    private record ObjectLayer(Clip clip, int layer) {}

    private record Clip(String path, List<ResourceScript.Frame> frames) {
        Visual visual(String label, int layer, long start, long end, int speed) {
            if (speed == 100) return new Visual(path, label, layer, start, end, 0, 0);
            var overrides = new HashMap<Integer, FrameOverride>();
            for (int i = 0; i < frames.size(); i++) {
                overrides.put(i, new FrameOverride(0, 0, scaled(frames.get(i).delay(), speed)));
            }
            return new Visual(path, label, layer, start, end, 0, 0, null, 1, true,
                    new VisualStyle(null, 0, List.of(), overrides));
        }
    }
}
