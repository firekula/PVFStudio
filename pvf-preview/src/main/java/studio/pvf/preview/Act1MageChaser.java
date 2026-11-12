package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;

/** ACT1 Mage chaser inventory, launch and timed generation. */
final class Act1MageChaser {
    private static final String MAGE = "character/mage/";
    private static final String EFFECT = MAGE + "effect/";
    private static final String COMMON = "common/commoneffect/";
    private static final String WAIT = "waiting motion";
    private static final int COLORLESS_CHASER = 23002;

    private Act1MageChaser() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level, String choice,
                                  Act1BuffCast.ActorHeight height, ScenePreview.SpriteLookup sprites) throws IOException {
        int id = switch (path) {
            case "skill/mage/chaserlaunch.skl" -> 39;
            case "skill/mage/chaserautocreate.skl" -> 40;
            default -> 0;
        };
        if (id == 0 || !choice.isEmpty() || !Act1SkillSequence.matches(path, "mage", id, lookup)
                || !actions.containsKey(WAIT)) return null;
        var fields = GameScript.fields(text);
        String type = GameScript.flag(GameScript.value(fields, "type"));
        if (!type.isEmpty() && !type.equals("active")) return null;
        var chaser = Act1SkillSequence.objectMotions(lookup, COLORLESS_CHASER);
        if (chaser.size() < 5) return null;
        return id == 39 ? launch(fields, lookup, actions, chaser, Act1SwordAsuraSupplement.targetHeight(lookup, sprites))
                : autoCreate(fields, lookup, actions, level,
                chaser, height.read("mage"));
    }

    /** 0x60CCD0 sends state 3 with 400 ms. State 3 pauses for 100 ms, then moves to half target height. */
    private static Act1SkillSequence launch(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                            Map<String, String> actions, List<String> chaser, int targetHeight) throws IOException {
        String attack = "etc motion:9";
        var frames = Act1SkillSequence.frames(MAGE + "mage.chr", actions.get(attack), lookup);
        if (frames.isEmpty()) return null;
        long attackEnd = Act1SkillSequence.duration(frames, frames.size(), null);
        long contact = Act1SkillSequence.duration(frames, Math.min(2, frames.size() - 1), null);
        long window = Act1SkillSequence.staticValue(fields, 0, 500);
        if (window <= 0 || attackEnd <= 0) return null;
        long launch = contact + Math.min(100, window - 1);
        long depart = launch + 100;
        long hit = launch + 400;
        double fromX = -30, fromZ = 80, targetX = 72, targetZ = targetHeight / 2;
        double travelSeconds = (hit - depart) / 1000.0;
        // 0x5AE6C0 passes mode zero to 0x8F5010, accelerating from rest over the last 300 ms.
        double travelSquared = travelSeconds * travelSeconds;
        var motion = new SkillMotion(depart, hit - depart, 0, 2 * (targetX - fromX) / travelSquared,
                0, 2 * (targetZ - fromZ) / travelSquared, fromZ);
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        visuals.add(new Visual(chaser.get(1), "炫纹发射 · 待发无属性炫纹", 3600, 0, launch, fromX, -fromZ));
        visuals.add(new Visual(chaser.get(3), "炫纹发射 · 追踪", 3600, launch, hit, fromX, 0, motion, 1, true));
        if (add(visuals, lookup, EFFECT + "animation/chaser/startattack.ani", "炫纹发射 · 出射",
                launch, fromX, -fromZ) == launch
                || add(visuals, lookup, EFFECT + "animation/chaser/target.ani", "炫纹发射 · 锁定目标",
                launch, targetX, -targetZ) == launch) return null;
        long end = add(visuals, lookup, chaser.get(4), "炫纹发射 · 命中", hit, targetX, -targetZ);
        for (int i = 1; i <= 4; i++) {
            String path = EFFECT + "particle/chaserdefault" + i + ".ptl";
            String source = lookup.read(path);
            if (source == null) return null;
            emitters.add(new SkillParticleEmitter(path, "炫纹发射 · 命中爆裂 " + i, hit, 1, 0,
                    targetX, 0, targetZ, Double.NaN, 1, true));
            end = Math.max(end, hit + SkillParticleEmitter.tail(ParticleScript.parse(source)));
        }
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "炫纹发射 · 演示目标", 0, contact, targetX, null);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", "炫纹发射 · 直接攻击受击", contact, hit, targetX, null);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage2", "炫纹发射 · 炫纹受击", hit, end, targetX, null);
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(attack, 0, Math.min(end, attackEnd)));
        if (attackEnd < end) poses.add(new Pose(WAIT, attackEnd, end));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters),
                "炫纹发射演示一次近身攻击命中后发射已有的无属性炫纹，先锁定哥布林，再播放追踪与独立命中粒子。"
                        + "演示使用固定目标和接触帧。");
    }

    /** Mage state 14 applies status icon 33, using level columns 0 and 5 for duration and interval. */
    private static Act1SkillSequence autoCreate(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                Map<String, String> actions, int level, List<String> chaser,
                                                int height) throws IOException {
        String buff = "buff motion";
        if (!actions.containsKey(buff)) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 800);
        long duration = Act1SkillSequence.levelValue(fields, level, 0, 30000, 1, 120000);
        long interval = Act1SkillSequence.levelValue(fields, level, 5, 12000, 1, 60000);
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        String glow = COMMON + "animation/buffchargeglow.ani";
        if (cast > 0 && lookup.read(glow) != null) {
            visuals.add(new Visual(glow, "自动炫纹 · 聚气", 3501, 0, cast, 0, -height - 30));
        }
        String charge = COMMON + "particle/buffchargeparticle.ptl";
        if (cast > 0 && lookup.read(charge) != null) {
            emitters.add(new SkillParticleEmitter(charge, "自动炫纹 · 聚气粒子", 0, cast, 0, 0, height + 30));
        }
        long end = Act1BuffCast.activate("自动炫纹", 33, cast, height, lookup, visuals, emitters);
        if (interval <= duration) {
            long generated = cast + interval;
            long appeared = add(visuals, lookup, chaser.get(0), "自动炫纹 · 定时生成", generated, -30, -80);
            if (appeared == generated) return null;
            end = Math.max(end, appeared + 1200);
            visuals.add(new Visual(chaser.get(1), "自动炫纹 · 无属性炫纹", 3600, appeared, end, -30, -80));
        }
        var poses = new ArrayList<Pose>();
        if (cast > 0) poses.add(new Pose(buff, 0, cast, null, true, null));
        if (end > cast) poses.add(new Pose(WAIT, cast, end));
        if (poses.isEmpty()) return null;
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters),
                "自动炫纹演示状态生效和当前等级的首个定时生成，初次生成使用无属性炫纹。"
                        + "生成后的外观截取 1.2 秒，命中追加生成、强力炫纹概率和 MP 消耗由实战处理。");
    }

    private static long add(List<Visual> visuals, GameScript.Lookup lookup, String path, String label,
                            long start, double x, double y) throws IOException {
        String source = lookup.read(path);
        if (source == null) return start;
        long end = start + new AnimationTimeline(ResourceScript.animation(source)).duration();
        if (end > start) visuals.add(new Visual(path, label, 3601, start, end, x, y));
        return end;
    }
}
