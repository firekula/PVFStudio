package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** ACT1 Priest state 10, Deliver motion, and the three native passive objects. */
final class Act1PriestProjectile {
    private static final String PREPARE = "throw motion 3-1";
    private static final String RELEASE = "throw motion 3-2";
    private static final int LAYER = 3600;

    private Act1PriestProjectile() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level) throws IOException {
        int id = switch (path) {
            case "skill/priest/deflectwall.skl" -> 27;
            case "skill/priest/flashglobe.skl" -> 28;
            case "skill/priest/enteringnirvana.skl" -> 38;
            default -> -1;
        };
        if (id == -1 || !actions.containsKey(PREPARE) || !actions.containsKey(RELEASE)) return null;
        if (!path.equals("skill/" + GameScript.catalog(lookup.read("skill/priestskill.lst")).get(id))) return null;
        var fields = GameScript.fields(text);
        long cast = Act1SkillSequence.castingTime(fields, level, id == 27 ? 700 : 500);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 500));
        var visuals = new ArrayList<Visual>();
        long end = switch (id) {
            case 27 -> wall(lookup, fields, level, cast, visuals);
            case 28 -> globe(lookup, fields, level, cast, visuals);
            case 38 -> talisman(lookup, cast, visuals);
            default -> -1;
        };
        if (end < 0) return null;
        String note = switch (id) {
            case 27 -> "圣光沁盾演示无障碍时前进和到期淡出；阻挡与破盾取决于实际碰撞和伤害";
            case 28 -> "圣光球演示飞行、闪光预警与爆炸；眩晕取决于目标距离和朝向";
            default -> "破魔符演示原版符咒的放飞与爆炸；减速、伤害及击杀后的恢复由游戏计算";
        };
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions, PREPARE, RELEASE, cast, recovery, end, false),
                List.copyOf(visuals), List.of(), note);
    }

    /** #24004 rewinds to frame 3, advances at the packet's speed and fades during the last second. */
    private static long wall(GameScript.Lookup lookup, List<GameScript.Field> fields, int level,
                             long start, List<Visual> visuals) throws IOException {
        var motions = Act1SkillSequence.objectMotions(lookup, 24004);
        if (motions.isEmpty()) return -1;
        String path = motions.get(0);
        var frames = frames(lookup, path);
        if (frames.size() <= 3) return -1;
        long initial = duration(frames);
        long repeated = duration(frames.subList(3, frames.size()));
        if (initial <= 0 || repeated <= 0) return -1;
        long life = Act1SkillSequence.levelValue(fields, level, 1, 10000, 1, 15000);
        long end = start + life;
        int speed = Act1SkillSequence.staticValue(fields, 1, 30);
        var motion = new SkillMotion(start, life, speed, 0);
        long repeatStart = Math.min(end, start + initial);
        var opening = new VisualStyle(null, 0, wallFade(start, repeatStart, end));
        visuals.add(new Visual(path, "圣光沁盾 · 盾壁", LAYER, start, repeatStart, 60, 0, motion, 1, true, opening));
        if (repeatStart < end) {
            var loop = new VisualStyle(null, 0, wallFade(repeatStart, end, end), skipFrames(frames, 3), false, repeated);
            visuals.add(new Visual(path, "圣光沁盾 · 盾壁", LAYER, repeatStart, end, 60, 0, motion, 1, true, loop));
        }
        return end;
    }

    private static List<VisualKeyframe> wallFade(long start, long end, long expires) {
        long fades = expires - 1000;
        if (end <= fades) return List.of();
        var keys = new ArrayList<VisualKeyframe>();
        keys.add(new VisualKeyframe(0, 1, Math.min(1, (expires - start) / 1000.0)));
        if (fades > start) keys.add(new VisualKeyframe(fades - start, 1, 1));
        keys.add(new VisualKeyframe(end - start, 1, (expires - end) / 1000.0));
        return keys;
    }

    /** #24005 rewinds the three flight tracks to frame 5, then stops for the explosion ANI. */
    private static long globe(GameScript.Lookup lookup, List<GameScript.Field> fields, int level,
                              long start, List<Visual> visuals) throws IOException {
        var motions = Act1SkillSequence.objectMotions(lookup, 24005);
        String ptl = lookup.read("character/priest/particle/flashglobe.ptl");
        if (motions.size() < 4 || ptl == null) return -1;
        var base = frames(lookup, motions.get(0));
        if (base.size() <= 5) return -1;
        long initial = duration(base);
        long repeated = duration(base.subList(5, base.size()));
        if (initial <= 0 || repeated <= 0) return -1;
        var axis = ParticleScript.parse(ptl).axes().get(0);
        if (!axis.type().equals("straight line")) return -1;
        long life = Act1SkillSequence.levelValue(fields, level, 2, 5000, 1, 15000);
        long impact = start + life;
        long repeatStart = Math.min(impact, start + initial);
        var motion = new SkillMotion(start, life, axis.start(), 0);
        for (int i = 0; i < 3; i++) {
            String path = motions.get(i);
            int layer = i == 1 ? LAYER + 1 : i == 2 ? LAYER - 1 : LAYER;
            visuals.add(new Visual(path, "圣光球 · 飞行", layer, start, repeatStart, 60, -60, motion, 1, true));
            if (repeatStart < impact) {
                var loop = new VisualStyle(null, 0, List.of(), skipFrames(frames(lookup, path), 5), false, repeated);
                visuals.add(new Visual(path, "圣光球 · 飞行", layer, repeatStart, impact, 60, -60, motion, 1, true, loop));
            }
        }
        long end = impact + duration(frames(lookup, motions.get(3)));
        visuals.add(new Visual(motions.get(3), "圣光球 · 爆炸", LAYER, impact, end, 60 + motion.offsetAt(impact), -60));
        return end;
    }

    /** #24009 keeps its native spawn anchor while the ANI flies out and switches to the explosion layers. */
    private static long talisman(GameScript.Lookup lookup, long start, List<Visual> visuals) throws IOException {
        var motions = Act1SkillSequence.objectMotions(lookup, 24009);
        if (motions.size() < 4) return -1;
        var values = GameScript.values(GameScript.fields(lookup.read("data/skill/priest.sd")), "int");
        int x = setting(values, "en x offset", 200);
        int y = -setting(values, "en z offset", 99);
        long impact = start + duration(frames(lookup, motions.get(0)));
        long end = impact + duration(frames(lookup, motions.get(2)));
        long glowEnd = Math.min(end, impact + duration(frames(lookup, motions.get(3))));
        visuals.add(new Visual(motions.get(0), "破魔符 · 放飞", LAYER, start, impact, x, y));
        visuals.add(new Visual(motions.get(1), "破魔符 · 放飞", LAYER + 1, start, impact, x, y));
        visuals.add(new Visual(motions.get(2), "破魔符 · 爆炸", LAYER, impact, end, x, y, null, 1, false));
        visuals.add(new Visual(motions.get(3), "破魔符 · 爆炸", LAYER + 1, impact, glowEnd, x, y, null, 1, false));
        return end;
    }

    private static List<ResourceScript.Frame> frames(GameScript.Lookup lookup, String path) throws IOException {
        return ResourceScript.animation(lookup.read(path)).frames();
    }

    private static long duration(List<ResourceScript.Frame> frames) {
        return Act1SkillSequence.duration(frames, Integer.MAX_VALUE, null);
    }

    private static Map<Integer, FrameOverride> skipFrames(List<ResourceScript.Frame> frames, int count) {
        var overrides = new HashMap<Integer, FrameOverride>();
        for (int i = 0; i < Math.min(frames.size(), count); i++) overrides.put(i, new FrameOverride(0, 0, 0));
        return overrides;
    }

    private static int setting(List<String> values, String name, int fallback) {
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if (values.get(i).equalsIgnoreCase(name)) {
                return Math.max(-10000, Math.min(10000, GameScript.integer(values.get(i + 1), fallback)));
            }
        }
        return fallback;
    }
}
