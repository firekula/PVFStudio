package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Swordman state 10 cast attachments and state 14 Auto Guard. */
final class Act1SwordBuffSupplement {
    private static final String SKILLS = "skill/swordman/";
    private static final String EFFECT = "character/swordman/effect/animation/";
    private static final String COMMON = "common/commoneffect/";
    private static final String BUFF = "buff motion";
    private static final String WAIT = "waiting motion";
    private static final int BACK = 499;
    private static final int FRONT = 3501;
    private static final long LOOP_PREVIEW_MS = 1800;

    private enum Buff {
        GUARD(3, 700, -1, 0, "自动格挡", "预览施法和生效图标。格挡动作需要受到攻击。"),
        GHOST_STEP(18, 600, 0, 500, "鬼影步", "预览鬼影展开和站立维持。移动中的隐身需要持续前冲。"),
        SACRIFICE(23, 250, 2, 500, "血气分流", "预览施法者聚气和释放。目标的回复与增益需要队友。"),
        MOON_CURTAIN(30, 400, 0, 500, "暗月降临", "预览以施法者为中心的原版暗月帷幕。"),
        DIE_HARD(34, 300, 1, 300, "死亡抗拒", "预览满足生命值门槛时的两层恢复动画。"),
        FRENZY(76, 500, 0, 350, "血之狂暴", "预览开启时的聚气、爆发和一段维持动画。双刀攻击和击杀回血需要实战。"),
        KEIGA(84, 800, 0, 500, "残影之凯贾", "预览凯贾展开和一段护体动画。耗尽防御次数时会触发破碎。");

        final int id, cast, recoveryField, recovery;
        final String label, note;

        Buff(int id, int cast, int recoveryField, int recovery, String label, String note) {
            this.id = id;
            this.cast = cast;
            this.recoveryField = recoveryField;
            this.recovery = recovery;
            this.label = label;
            this.note = note;
        }
    }

    private Act1SwordBuffSupplement() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup, Map<String, String> actions,
                                  int level, Act1BuffCast.ActorHeight actorHeight) throws IOException {
        Buff buff = switch (path) {
            case SKILLS + "autoguard.skl" -> Buff.GUARD;
            case SKILLS + "ghoststep.skl" -> Buff.GHOST_STEP;
            case SKILLS + "sacrifice.skl" -> Buff.SACRIFICE;
            case SKILLS + "darktoleranceup.skl" -> Buff.MOON_CURTAIN;
            case SKILLS + "diehard.skl" -> Buff.DIE_HARD;
            case SKILLS + "frenzy.skl" -> Buff.FRENZY;
            case SKILLS + "keiga.skl" -> Buff.KEIGA;
            default -> null;
        };
        if (buff == null || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/swordmanskill.lst")).get(buff.id))) return null;
        var fields = GameScript.fields(text);
        String type = GameScript.flag(GameScript.value(fields, "type"));
        if (!type.isEmpty() && !type.equals("active")) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, buff.cast);
        var visuals = new ArrayList<Visual>();
        var particles = new ArrayList<SkillParticleEmitter>();
        if (buff == Buff.GUARD) {
            return autoGuard(cast, actorHeight.read("swordman"), lookup, actions, visuals, particles);
        }
        // The state 10 vectors in 0x643D00 write the zero-based CHR throw group after the recovery duration.
        int group = switch (buff) {
            case MOON_CURTAIN, DIE_HARD -> 2;
            default -> 1;
        };
        String prepare = "throw motion " + group + "-1", release = "throw motion " + group + "-2";
        if (!actions.containsKey(prepare) || !actions.containsKey(release)) return null;
        long recovered = cast + Math.max(1, Act1SkillSequence.staticValue(fields, buff.recoveryField, buff.recovery));
        // 0x638640 attaches the cast/release ANI at the actor anchor. Status classes own persistent effects.
        switch (buff) {
            case SACRIFICE -> {
                clip(visuals, lookup, "givebloodcharge1", "血气分流 · 聚气一层", FRONT, 0, cast, 1);
                clip(visuals, lookup, "givebloodcharge2", "血气分流 · 聚气二层", FRONT + 1, 0, cast, 1);
                clip(visuals, lookup, "givebloodshoot1", "血气分流 · 释放一层", FRONT, cast, recovered, 1);
                clip(visuals, lookup, "givebloodshoot2", "血气分流 · 释放二层", FRONT + 1, cast, recovered, 1);
            }
            case MOON_CURTAIN -> {
                // 0x6429E3 applies (integer diameter / 2) times the A01118 float to the three area clips.
                double areaScale = Math.max(1, Act1SkillSequence.staticValue(fields, 1, 500) / 2) * 0.005524862f;
                clip(visuals, lookup, "mooncurtain_floor", "暗月降临 · 地面", BACK - 2, cast, recovered, areaScale);
                clip(visuals, lookup, "mooncurtain_eff_under", "暗月降临 · 帷幕后层", BACK - 1, cast, recovered, areaScale);
                clip(visuals, lookup, "mooncurtain_under", "暗月降临 · 暗月后层", BACK, cast, recovered, 1);
                clip(visuals, lookup, "mooncurtain_eff_upper", "暗月降临 · 帷幕前层", FRONT, cast, recovered, areaScale);
                clip(visuals, lookup, "mooncurtain_upper", "暗月降临 · 暗月前层", FRONT + 1, cast, recovered, 1);
                Act1BuffCast.activate(buff.label, 1, cast, actorHeight.read("swordman"), lookup, visuals, particles);
            }
            case DIE_HARD -> {
                clip(visuals, lookup, "pinchhpregen1", "死亡抗拒 · 恢复闪光", FRONT, cast, Long.MAX_VALUE, 1);
                clip(visuals, lookup, "pinchhpregen2", "死亡抗拒 · 恢复光柱", FRONT + 1, cast, Long.MAX_VALUE, 1);
            }
            case FRENZY -> {
                // State 10 phase 0 redistributes the six Cast frames to the casting duration.
                String charge = EFFECT + "frenzy/cast.ani";
                if (cast > 0 && lookup.read(charge) != null) {
                    var style = new VisualStyle(null, cast, List.of());
                    visuals.add(new Visual(charge, "血之狂暴 · 聚气", FRONT, 0, cast, 0, 0, null, 1, true, style));
                }
                clip(visuals, lookup, "frenzy/blast", "血之狂暴 · 爆发", FRONT, cast, recovered, 1);
                // The CNFrenzy draw clock in 0x537C00 adds a 250 ms delay to the standing attachment.
                loop(visuals, lookup, "frenzy/buff", "血之狂暴 · 维持", FRONT + 1, cast + 250, cast + 250 + LOOP_PREVIEW_MS, VisualStyle.DEFAULT);
            }
            case GHOST_STEP -> {
                long expires = cast + Act1SkillSequence.levelValue(fields, level, 0, 20000, 1, 60000);
                appearance(visuals, lookup, "ghoststep/", "stay", buff.label, new int[] {BACK - 1, BACK}, cast, expires);
                Act1BuffCast.activate(buff.label, 20, cast, actorHeight.read("swordman"), lookup, visuals, particles);
            }
            case KEIGA -> {
                long expires = cast + Act1SkillSequence.levelValue(fields, level, 3, 60000, 1, 60000);
                appearance(visuals, lookup, "keiga/", "loop", buff.label, new int[] {BACK - 1, BACK, FRONT, FRONT + 1}, cast, expires);
            }
            default -> throw new AssertionError(buff);
        }
        if (visuals.isEmpty()) return null;
        long end = Math.max(recovered, visualEnd(visuals));
        var poses = Act1SkillSequence.castPoses(actions, prepare, release, cast, recovered, end, buff == Buff.FRENZY);
        return new Act1SkillSequence(poses, List.copyOf(visuals), List.copyOf(particles), buff.note);
    }

    /** 0x6361D0 passes BuffIcon index 48 to the shared state 14 activation. */
    private static Act1SkillSequence autoGuard(long cast, int height, GameScript.Lookup lookup, Map<String, String> actions,
                                              List<Visual> visuals, List<SkillParticleEmitter> particles) throws IOException {
        if (!actions.containsKey(BUFF)) return null;
        if (cast > 0) {
            int z = height + 30;
            String glow = COMMON + "animation/buffchargeglow.ani";
            if (lookup.read(glow) != null) visuals.add(new Visual(glow, "自动格挡 · 聚气", FRONT, 0, cast, 0, -z));
            String particle = COMMON + "particle/buffchargeparticle.ptl";
            if (lookup.read(particle) != null) particles.add(new SkillParticleEmitter(particle, "自动格挡 · 聚气粒子", 0, cast, 0, 0, z));
        }
        long end = Act1BuffCast.activate(Buff.GUARD.label, 48, cast, height, lookup, visuals, particles);
        if (end == cast) return null;
        var poses = new ArrayList<Pose>();
        if (cast > 0) poses.add(new Pose(BUFF, 0, cast, null, true, null));
        if (actions.containsKey(WAIT)) poses.add(new Pose(WAIT, cast, end));
        if (poses.isEmpty()) poses.add(new Pose(BUFF, 0, end));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(particles), Buff.GUARD.note);
    }

    private static void appearance(List<Visual> visuals, GameScript.Lookup lookup, String folder, String loop,
                                   String label, int[] layers, long start, long expires) throws IOException {
        long opened = start;
        for (int i = 0; i < layers.length; i++) {
            opened = Math.max(opened, clip(visuals, lookup, folder + "appear" + (i + 1), label + " · 展开", layers[i], start, expires, 1));
        }
        long end = Math.min(expires, opened + LOOP_PREVIEW_MS);
        for (int i = 0; i < layers.length; i++) {
            // 0x538650 fades the two standing GhostStep layers to alpha 100 and 0 over 300 ms.
            var style = loop.equals("stay") ? new VisualStyle(null, 0, List.of(new VisualKeyframe(0, 1, 1),
                    new VisualKeyframe(300, 1, i == 0 ? 100 / 255.0 : 0))) : VisualStyle.DEFAULT;
            loop(visuals, lookup, folder + loop + (i + 1), label + " · 维持", layers[i], opened, end, style);
        }
    }

    private static long clip(List<Visual> visuals, GameScript.Lookup lookup, String stem, String label,
                             int layer, long start, long limit, double scale) throws IOException {
        if (limit <= start) return start;
        String path = EFFECT + stem + ".ani";
        String text = lookup.read(path);
        if (text == null) return start;
        long duration = new AnimationTimeline(ResourceScript.animation(text)).duration();
        long end = Math.min(start + duration, limit);
        if (end > start) visuals.add(new Visual(path, label, layer, start, end, 0, 0, null, scale, true));
        return end;
    }

    private static void loop(List<Visual> visuals, GameScript.Lookup lookup, String stem, String label,
                             int layer, long start, long end, VisualStyle style) throws IOException {
        String path = EFFECT + stem + ".ani";
        if (end > start && lookup.read(path) != null) visuals.add(new Visual(path, label, layer, start, end, 0, 0, null, 1, true, style));
    }

    private static long visualEnd(List<Visual> visuals) {
        return visuals.stream().mapToLong(Visual::end).max().orElse(0);
    }
}
