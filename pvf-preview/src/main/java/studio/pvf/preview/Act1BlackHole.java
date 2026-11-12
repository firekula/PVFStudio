package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

import static studio.pvf.preview.Act1SkillSequence.castPoses;
import static studio.pvf.preview.Act1SkillSequence.castingTime;
import static studio.pvf.preview.Act1SkillSequence.duration;
import static studio.pvf.preview.Act1SkillSequence.levelValue;
import static studio.pvf.preview.Act1SkillSequence.objectMotions;
import static studio.pvf.preview.Act1SkillSequence.staticValue;

/** Mage state 10 releases CNBlackHole (#23025), whose states 2–4 own the effect clocks. */
final class Act1BlackHole {
    private static final int OBJECT_ID = 23025;
    private static final String PREPARE = "throw motion 2-1";
    private static final String RELEASE = "throw motion 2-2";
    private static final String VORTEX_BASE = "湮灭黑洞 · 涡流底层";
    private static final String VORTEX = "湮灭黑洞 · 涡流";
    private static final String LIGHTNING = "湮灭黑洞 · 电光";
    private static final double FADED_OPACITY = 80.0 / 255;

    private Act1BlackHole() {}

    static Act1SkillSequence find(String text, GameScript.Lookup lookup, Map<String, String> actions,
                                  int level) throws IOException {
        if (!actions.containsKey(PREPARE) || !actions.containsKey(RELEASE)) return null;
        var motions = objectMotions(lookup, OBJECT_ID);
        if (motions.size() < 12) return null;
        var appear = frames(motions.get(1), lookup);
        var explodeDown = frames(motions.get(7), lookup);
        var explodeUp = frames(motions.get(8), lookup);
        if (appear.size() < 10 || explodeDown.size() < 4 || explodeUp.size() < 5) return null;
        long appearLength = duration(appear, appear.size(), null);
        long explosionLength = duration(explodeUp, explodeUp.size(), null);
        if (appearLength == 0 || explosionLength == 0) return null;

        var skill = GameScript.fields(text);
        var params = GameScript.fields(lookup.read("data/skill/mage.sd"));
        double x = GameScript.integer(parameter(params, "int", "bh x offset"), 200);
        double z = GameScript.integer(parameter(params, "int", "bh z offset"), 80);
        double y = -z;
        double initialScale = initialScale(params);
        long release = castingTime(skill, level, 1500);
        long recovery = Math.max(1, staticValue(skill, 0, 800));
        long formed = release + recovery;
        // 0x5ADC80 sets both appearance ANI clocks from the release-stage recovery.
        long appearUpLength = length(motions.get(2), lookup);
        long appearUpTime = scaledTime(appearUpLength, appearLength, recovery);
        long growthStart = release + scaledTime(duration(appear, 7, null), appearLength, recovery);
        long growthEnd = release + scaledTime(duration(appear, 9, null), appearLength, recovery);
        long collapse = Math.max(formed, release + levelValue(skill, level, 4, 3000, 1, 30000));
        long warning = Math.max(formed, collapse - length(motions.get(6), lookup));
        long shrunk = collapse + duration(explodeDown, 3, null);
        long vanish = collapse + explosionLength;
        long particlesStart = collapse + duration(explodeUp, 4, null);

        var grow = transition(growthEnd - growthStart, initialScale, 1, 1, 1);
        var growAndBrighten = transition(growthEnd - growthStart, initialScale, 1, FADED_OPACITY, 1);
        var shrink = transition(shrunk - collapse, 1, initialScale, 1, 1);
        var shrinkAndFade = transition(shrunk - collapse, 1, initialScale, 1, FADED_OPACITY);
        var visuals = List.of(
                styled(motions.get(1), "湮灭黑洞 · 形成底层", 3604, release, formed, x, y, retimed(recovery)),
                styled(motions.get(2), "湮灭黑洞 · 形成光效", 3605, release, formed, x, y, retimed(appearUpTime)),
                // 0x5ADE80 attaches the vortex at Appear1 frame 7. State 3 restarts the base ANI.
                styled(motions.get(0), VORTEX_BASE, 3600, growthStart, formed, x, y, growAndBrighten),
                new Visual(motions.get(0), VORTEX_BASE, 3600, formed, collapse, x, y),
                styled(motions.get(3), VORTEX, 3601, growthStart, collapse, x, y, grow),
                styled(motions.get(4), LIGHTNING, 3602, growthStart, collapse, x, y, grow),
                styled(motions.get(5), "湮灭黑洞 · 核心", 3603, growthStart, collapse, x, y, growAndBrighten),
                new Visual(motions.get(11), "湮灭黑洞 · 地面投影", 300, formed, collapse, x, 0),
                new Visual(motions.get(6), "湮灭黑洞 · 爆裂预兆", 3604, warning, collapse, x, y),
                // 0x5AD830 reattaches Stay1–3 behind Explosion2 during the first three Explosion1 frames.
                styled(motions.get(0), VORTEX_BASE, 3596, collapse, Math.min(shrunk, vanish), x, y, shrinkAndFade),
                styled(motions.get(3), VORTEX, 3597, collapse, Math.min(shrunk, vanish), x, y, shrink),
                styled(motions.get(4), LIGHTNING, 3598, collapse, Math.min(shrunk, vanish), x, y, shrink),
                new Visual(motions.get(7), "湮灭黑洞 · 爆裂底层", 3599, collapse,
                        Math.min(vanish, collapse + duration(explodeDown, explodeDown.size(), null)), x, y),
                new Visual(motions.get(8), "湮灭黑洞 · 爆裂", 3600, collapse, vanish, x, y),
                new Visual(motions.get(9), "湮灭黑洞 · 冲击环", 3601, collapse, vanish, x, y),
                new Visual(motions.get(10), "湮灭黑洞 · 冲击环", 3602, collapse, vanish, x, y));

        var emitters = new ArrayList<SkillParticleEmitter>(1);
        long finish = vanish;
        String object = GameEffectRecipes.passiveObject(lookup, OBJECT_ID);
        String particle = object == null ? null : GameScript.resolve(object, "Particle/BlackHoleExp.ptl", lookup);
        if (particle != null && particlesStart < vanish) {
            emitters.add(new SkillParticleEmitter(particle, "湮灭黑洞 · 爆裂粒子",
                    particlesStart, vanish - particlesStart, 0, x, z));
            finish += SkillParticleEmitter.tail(ParticleScript.parse(lookup.read(particle)));
        }
        return new Act1SkillSequence(castPoses(actions, PREPARE, RELEASE, release, formed, finish, false),
                visuals, List.copyOf(emitters), "湮灭黑洞按当前等级的地下城时序播放。吸引与受击取决于实际敌人");
    }

    private static List<ResourceScript.Frame> frames(String path, GameScript.Lookup lookup) throws IOException {
        return ResourceScript.animation(lookup.read(path)).frames();
    }

    private static long length(String path, GameScript.Lookup lookup) throws IOException {
        return duration(frames(path, lookup), Integer.MAX_VALUE, null);
    }

    private static long scaledTime(long time, long original, long target) {
        return Math.round(time * (double) target / original);
    }

    private static Visual styled(String path, String label, int layer, long start, long end, double x, double y,
                                 VisualStyle style) {
        return new Visual(path, label, layer, start, end, x, y, null, 1, true, style);
    }

    private static VisualStyle retimed(long duration) {
        return new VisualStyle(null, duration, List.of());
    }

    private static VisualStyle transition(long duration, double scaleFrom, double scaleTo,
                                          double opacityFrom, double opacityTo) {
        return new VisualStyle(null, 0, List.of(new VisualKeyframe(0, scaleFrom, opacityFrom),
                new VisualKeyframe(duration, scaleTo, opacityTo)));
    }

    private static double initialScale(List<GameScript.Field> fields) {
        try {
            double scale = Double.parseDouble(parameter(fields, "float", "bh init size"));
            if (Double.isFinite(scale) && scale > 0 && scale <= 4) return scale;
        } catch (NumberFormatException ignored) {
            // Match the ACT1 data when an edited parameter is unavailable or malformed.
        }
        return 0.5;
    }

    private static String parameter(List<GameScript.Field> fields, String type, String key) {
        var values = GameScript.values(fields, type);
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if (values.get(i).equalsIgnoreCase(key)) return values.get(i + 1);
        }
        return "";
    }
}
