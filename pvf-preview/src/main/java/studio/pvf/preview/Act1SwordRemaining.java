package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

import static studio.pvf.preview.Act1SkillSequence.castingTime;
import static studio.pvf.preview.Act1SkillSequence.duration;
import static studio.pvf.preview.Act1SkillSequence.levelValue;
import static studio.pvf.preview.Act1SkillSequence.staticValue;

/** ACT1 Swordman stages with independent held, moving, and passive-object animation clocks. */
final class Act1SwordRemaining {
    private static final String SWORD = "character/swordman/";
    private static final String EFFECT = SWORD + "effect/animation/";
    private static final String OBJECT = "passiveobject/character/swordman/animation/";
    private static final String WAIT = "waiting motion";
    private static final String GHOST_STEP = "skill/swordman/ghoststepslash.skl";
    private static final String GHOST_STEP_MISS = "ghoststepslash:miss";
    private static final String KALLA = "skill/swordman/kalla.skl";
    private static final String KALLA_MISS = "kalla:miss";
    private static final String RASA = "skill/swordman/epidemicrasa.skl";
    private static final String RASA_MISS = "epidemicrasa:miss";

    private Act1SwordRemaining() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level) throws IOException {
        return find(path, text, lookup, actions, level, "");
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level, String choice) throws IOException {
        int skill = switch (path) {
            case "skill/swordman/grabblastblood.skl" -> 31;
            case "skill/swordman/tombstonerain.skl" -> 44;
            case GHOST_STEP -> 60;
            case "skill/swordman/standalonewave.skl" -> 62;
            case "skill/swordman/wavespinarea.skl" -> 74;
            case RASA -> 75;
            case "skill/swordman/bloodyrave.skl" -> 79;
            case "skill/swordman/outragebreak.skl" -> 81;
            case KALLA -> 82;
            default -> -1;
        };
        if (skill < 0 || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/swordmanskill.lst")).get(skill))) {
            return null;
        }
        if (!choice.isEmpty() && !(skill == 74 && Act1SwordAsuraSupplement.markChoice(choice))
                && !(skill == 60 && choice.equals(GHOST_STEP_MISS))
                && !(skill == 75 && choice.equals(RASA_MISS))
                && !(skill == 82 && choice.equals(KALLA_MISS))) return null;
        var fields = GameScript.fields(text);
        int group=skill==75||skill==82?1:2;
        var cast = new Cast(lookup, actions, group);
        return switch (skill) {
            case 31 -> grab(cast, fields);
            case 44 -> tombstones(cast, fields, level);
            case 60 -> ghostStep(cast, fields, !choice.equals(GHOST_STEP_MISS));
            case 62 -> standalone(cast, fields);
            case 74 -> waveSpin(cast, fields, level, Act1SwordAsuraSupplement.markCount(choice));
            case 75 -> rasa(cast, fields, level, !choice.equals(RASA_MISS));
            case 79 -> bloodyRave(cast, fields);
            case 81 -> outrage(cast, fields);
            default -> kalla(cast, fields, level, !choice.equals(KALLA_MISS));
        };
    }

    static List<GameEffectRecipes.Effect> bloodChoices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        return act1 && path.equals("skill/swordman/grabblastblood.skl")
                && path.equals("skill/" + GameScript.catalog(lookup.read("skill/swordmanskill.lst")).get(31))
                ? List.of(new GameEffectRecipes.Effect("sword-final:bleeding", "嗜魂之手 · 出血目标", "", path,
                "成功抓取已出血目标，使用原版六层强化血气喷发")) : List.of();
    }

    static List<GameEffectRecipes.Effect> targetChoices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1) return List.of();
        if (path.equals(GHOST_STEP) && Act1SkillSequence.matches(path, "swordman", 60, lookup)) {
            return List.of(new GameEffectRecipes.Effect(GHOST_STEP_MISS, "鬼影闪 · 空场", "", path, "仅播放前冲与瞬身斩"));
        }
        if (path.equals(KALLA) && Act1SkillSequence.matches(path, "swordman", 82, lookup)) {
            return List.of(new GameEffectRecipes.Effect(KALLA_MISS, "冥炎之卡洛 · 空场", "", path, "展示无目标时的分身发射与终结"));
        }
        if (path.equals(RASA) && Act1SkillSequence.matches(path, "swordman", 75, lookup)) {
            return List.of(new GameEffectRecipes.Effect(RASA_MISS, "瘟疫之罗刹 · 空场", "", path, "展示法阵与鬼神维持"));
        }
        return List.of();
    }

    static Act1SkillSequence bloodChoice(String path, String text, GameScript.Lookup lookup,
                                         Map<String,String> actions, String choice) throws IOException {
        if (!choice.equals("sword-final:bleeding") || bloodChoices(path, lookup, true).isEmpty()) return null;
        return grab(new Cast(lookup, actions, 2), GameScript.fields(text), true);
    }

    /** State22 starts at frame3; success holds frame16 and creates 20017/20049 at elapsed750ms. */
    private static Act1SkillSequence grab(Cast cast, List<GameScript.Field> fields) throws IOException {
        return grab(cast, fields, false);
    }

    private static Act1SkillSequence grab(Cast cast, List<GameScript.Field> fields, boolean bleeding) throws IOException {
        var actor = cast.body(10);
        var blast = Act1SkillSequence.objectMotions(cast.lookup, bleeding ? 20049 : 20017);
        String absorb = EFFECT + "grabblastblood.ani";
        long absorbLife = cast.ani(absorb);
        if (actor.size() <= 16 || blast.size() != (bleeding ? 6 : 4) || absorbLife <= 0) return null;
        int catchFrame = 7; // Earliest scripted contact: frame7 is the first authored grab box.
        long caught = duration(actor.subList(3, catchFrame), Integer.MAX_VALUE, null);
        long release = caught + 750;
        long recovered = caught + Math.max(751, staticValue(fields, 0, 1200));
        cast.poses.add(new Pose(etc(10), 0, caught, null, false, null, 3, catchFrame));
        cast.poses.add(new Pose(etc(10), caught, recovered, null, false, null, 16));
        // Native attachment is removed when its own ANI ends (0x472FC0 / 0x473390), not at recovery.
        cast.add(absorb, "嗜魂之手 · 吸血", 3501, caught, Math.min(release, caught + absorbLife), 43, -90, null, 1);
        for (int i = 0; i < blast.size(); i++) {
            String path = blast.get(i);
            long stop = -1;
            VisualStyle style = VisualStyle.DEFAULT;
            if (i == 0) {
                var core = cast.animation(path).frames();
                var halo = cast.animation(blast.get(1)).frames();
                var timing = new HashMap<Integer, FrameOverride>();
                long life = cast.ani(path);
                // Match the impact-normal frames to their impact-dodge counterparts. The unmatched core tail
                // and the separate contraction/spray ANIs retain their authored delays.
                for (int frame = 0; frame < Math.min(core.size(), halo.size()); frame++) {
                    int delay = halo.get(frame).delay();
                    if (delay != core.get(frame).delay()) {
                        timing.put(frame, new FrameOverride(0, 0, delay));
                        life += delay - core.get(frame).delay();
                    }
                }
                style = new VisualStyle(null, 0, List.of(), timing);
                stop = release + life;
            }
            // 0x643D00 creates the passive object at (+43,+1,+90).
            cast.add(path, bleeding ? "嗜魂之手 · 出血目标喷发" : "嗜魂之手 · 血气喷发",
                    3600 + i, release, stop, 43, .29 - 90, null, 1, style);
        }
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "stay", "嗜魂之手 · 目标", 0, caught, 43, null);
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "damage1", "嗜魂之手 · 抓取目标",
                caught, release, 43, null);
        long targetEnd = Math.max(release + 1, cast.effectEnd);
        var knockback = new SkillMotion(release, targetEnd - release, 180, -300, 250, -1000);
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "overturn", "嗜魂之手 · 目标击飞",
                release, targetEnd, 43, knockback);
        return cast.finish(recovered, null, "嗜魂之手演示首次可抓取帧成功命中" + (bleeding ? "出血目标" : "普通目标")
                + "，吸血动画自然结束后按原版抓取计时喷发；可切换出血目标分支");
    }

    /** State 29 captures via #20026 (0x562330), marks in 0x638640, and releases via 0x643D00. */
    private static Act1SkillSequence ghostStep(Cast cast, List<GameScript.Field> fields, boolean hitTarget) throws IOException {
        long ready = cast.bodyDuration(18);
        long move = cast.bodyDuration(19);
        var slash = Act1SkillSequence.objectMotions(cast.lookup, 20026);
        if (ready == 0 || move == 0 || slash.isEmpty()) return null;
        double distance = staticValue(fields, 0, 300);
        var motion = new SkillMotion(ready, 0, 0, 0, distance);
        cast.poses.add(new Pose(etc(18), 0, ready));
        cast.poses.add(new Pose(etc(19), ready, ready + move, null, false, motion));
        cast.add(slash.get(0), "鬼影闪 · 瞬身斩", 3500, ready, -1, 0, .29, null, 1);
        cast.add(OBJECT + "ghoststepslash2.ani", "鬼影闪 · 瞬身斩前层", 3501, ready, -1, 0, .29, null, 1);
        cast.add(EFFECT + "ghoststepslash/dust.ani", "鬼影闪 · 起点尘烟", 3502, ready, -1, 0, 0, null, 1);
        cast.add(EFFECT + "ghoststepslash/move.ani", "鬼影闪 · 终点残影", 3502, ready, ready + move, distance, 0, null, 1);
        long moved = ready + move;
        if (!hitTarget) return cast.finish(moved, motion, "鬼影闪演示已进入鬼影步前冲无敌阶段后的空场瞬身斩");

        var settings = cast.swordData();
        long release = moved + Math.max(1, Math.round(number(settings, "gss release delay", 500)));
        long recovery = release + Math.max(1, Math.round(number(settings, "gss after delay", 500)));
        double targetX = distance * .5;
        int height = Act1SwordAsuraSupplement.goblinHeight(cast.lookup);
        double scale = Math.max(1, Math.min(4, 1 + (height - 60) / 60.0));
        String effects = EFFECT + "ghoststepslash/";
        cast.poses.add(new Pose(etc(19), moved, recovery, null, false, motion, 2));
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "stay", "鬼影闪 · 哥布林", 0, ready, targetX, null);
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "damage1", "鬼影闪 · 拘束目标", ready, release, targetX, null);
        cast.add(effects + "slash2.ani", "鬼影闪 · 捕获斩光", 3501, ready, -1, targetX, -height / 2, null, scale);
        cast.add(effects + "skull.ani", "鬼影闪 · 目标骷髅", 3602, moved, -1, targetX, -height, null, 1);
        cast.add(effects + "slash1.ani", "鬼影闪 · 延时斩光前层", 3601, release, -1, targetX, -height / 2, null, scale);
        cast.add(effects + "slash2.ani", "鬼影闪 · 延时斩光后层", 3501, release, -1, targetX, -height / 2, null, scale);
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "stay", "鬼影闪 · 释放目标", release,
                Math.max(recovery, cast.effectEnd), targetX, null);
        return cast.finish(recovery, motion, "鬼影闪演示前冲捕获一名普通哥布林、骷髅标记和延时释放斩光");
    }

    /** State 30's one-capture branch creates the strong wave on the first attack frame. */
    private static Act1SkillSequence standalone(Cast cast, List<GameScript.Field> fields) throws IOException {
        var slash = cast.body(22);
        var wave = Act1SkillSequence.objectMotions(cast.lookup, 20027);
        if (cast.body(20).isEmpty() || slash.size() < 2 || wave.isEmpty()) return null;
        long ready = Math.max(1, staticValue(fields, 0, 1000));
        long hit = ready + duration(slash, 1, null);
        long recover = ready + duration(slash, Integer.MAX_VALUE, null);
        String folder = EFFECT + "standalonewave/";
        cast.poses.add(new Pose(etc(20), 0, ready));
        cast.poses.add(new Pose(etc(22), ready, recover));
        cast.add(folder + "floor.ani", "无双波 · 吸附阵", 290, 0, ready, 0, 0, null, 1);
        cast.add(folder + "charge1.ani", "无双波 · 聚气", 3501, 250, ready, 0, 0, null, 1);
        cast.add(folder + "charge2.ani", "无双波 · 聚气前层", 3502, 250, ready, 4, -94, null,
                number(cast.swordData(), "saw eye init size", 1));
        cast.add(folder + "slash2.ani", "无双波 · 强化斩击", 3501, ready, -1, 0, 0, null, 1);
        cast.add(folder + "blast1.ani", "无双波 · 斩击爆发", 3502, ready, -1, 0, 0, null, 1);
        cast.add(folder + "blast2.ani", "无双波 · 斩击爆发前层", 3503, ready, -1, 0, 0, null, 1);
        cast.add(wave.get(0), "无双波 · 强化冲击波", 3600, hit, -1, 30, -94 + .29,
                null, staticValue(fields, 1, 100) / 100.0);
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "stay", "无双波 · 吸附目标",
                0, hit, 30, new SkillMotion(0, hit, -55_000.0 / hit, 0, 55));
        long targetEnd = Math.max(recover, cast.effectEnd);
        var knockback = new SkillMotion(hit, targetEnd - hit, 160, -250, 220, -900);
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "overturn", "无双波 · 目标击飞",
                hit, targetEnd, 30, knockback);
        return cast.finish(recover, null, "无双波按已开启杀意波动、满足挫折意志前置条件并吸附一名敌人演示强化斩击；空场使用较小的斩击");
    }

    /** #20040 selects a target via 0x591CE0 and schedules #20042; 0x592690 attaches the child on contact. */
    private static Act1SkillSequence rasa(Cast cast, List<GameScript.Field> fields, int level, boolean hitTarget) throws IOException {
        var layers = Act1SkillSequence.objectMotions(cast.lookup, 20040);
        if (!cast.hasSummon() || layers.size() < 6) return null;
        long release = castingTime(fields, level, 500);
        long recover = release + Math.max(1, staticValue(fields, 0, 600));
        long life = levelValue(fields, level, 0, 10000, 1, 30000);
        long end = release + life;
        long opened = Math.min(end, release + cast.ani(layers.get(1)));
        long close = Math.max(opened, end - cast.ani(layers.get(3)));
        double scale = staticValue(fields, 6, 250) / 233.0;
        var fadeIn = new VisualStyle(null, 0, List.of(new VisualKeyframe(0, 1, 0),
                new VisualKeyframe(opened - release, 1, 180.0 / 255)));
        cast.add(layers.get(0), "瘟疫之罗刹 · 法阵展开", 280, release, opened, 122, 0, null, scale, fadeIn);
        cast.add(layers.get(1), "瘟疫之罗刹 · 法阵展开前层", 281, release, opened, 122, 0, null, scale, fadeIn);
        cast.add(layers.get(2), "瘟疫之罗刹 · 法阵维持", 280, opened, close, 122, 0, null, scale);
        var fadeOut = new VisualStyle(null, 0, List.of(new VisualKeyframe(0, 1, 180.0 / 255),
                new VisualKeyframe(end - close, 1, 0)));
        cast.add(layers.get(3), "瘟疫之罗刹 · 法阵消散", 280, close, end, 122, 0, null, scale, fadeOut);
        for (int i = 4; i < 6; i++) {
            cast.add(layers.get(i), "瘟疫之罗刹 · 鬼神", 3500 + i, opened, end, 122, 0, null, 1);
        }
        int scanEvery = Math.max(1, staticValue(fields, 1, 1000));
        // 0x591CE0 selects by normalized XY distance <= 233; static column 6 only scales the art.
        cast.areas.add(new Act1SkillSequence.AttackArea("瘟疫之罗刹 · 附身目标筛选", release, end, 122, 0,
                new CombatDebug.TargetRange(233, null, scanEvery)).startingPulseAt(release+scanEvery));
        if (hitTarget) {
            var ghost = Act1SkillSequence.objectMotions(cast.lookup, 20042);
            if (ghost.size() < 8) return null;
            long scheduled = release + scanEvery;
            double targetX = 122 + Math.min(72, Math.max(1, staticValue(fields, 6, 250) / 4.0));
            if (scheduled < end) {
                long born = Act1SkillSequence.targetWarning(cast.lookup, cast.visuals,
                        "瘟疫之罗刹 · 目标预警", scheduled, targetX, 100);
                long contact = born + cast.ani(ghost.get(0));
                // 0x5923E0 installs Stay after Startup. Show its first contact frame before 0x592690 attaches the child.
                long attach = contact + cast.animation(ghost.get(2)).frames().stream()
                        .findFirst().map(ResourceScript.Frame::delay).orElse(1);
                long expires = attach + levelValue(fields, level, 19, 6000, 1, 30000);
                long loop = Math.min(expires, attach + cast.ani(ghost.get(4)));
                for (int i = 0; i < 2; i++) {
                    cast.add(ghost.get(i), "瘟疫之罗刹 · 附身小鬼出现", 3600 + i, born, contact, targetX, 0, null, 1);
                    cast.add(ghost.get(2 + i), "瘟疫之罗刹 · 附身接触", 3600 + i, contact, attach, targetX, 0, null, 1);
                    cast.add(ghost.get(4 + i), "瘟疫之罗刹 · 小鬼附身", 3600 + i, attach, loop, targetX, .87, null, 1);
                    cast.add(ghost.get(6 + i), "瘟疫之罗刹 · 附身维持", 3600 + i, loop, expires, targetX,
                            .87 + 48 - 2 * (Act1SwordAsuraSupplement.goblinHeight(cast.lookup) / 4), null, 1);
                }
                Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "stay", "瘟疫之罗刹 · 哥布林目标",
                        0, Math.max(end, expires), targetX, null);
            }
        }
        cast.summon(release, recover);
        return cast.finish(recover, null, hitTarget ? "瘟疫之罗刹演示选中一名普通哥布林后，小鬼出现、附身及持续状态"
                : "瘟疫之罗刹演示空场召唤、鬼神维持和法阵消散");
    }

    /** Object 20014 samples the ground region and spawns the layered 20015 at 1000 / rate. */
    private static Act1SkillSequence tombstones(Cast cast, List<GameScript.Field> fields, int level) throws IOException {
        var stones = Act1SkillSequence.objectMotions(cast.lookup, 20015);
        if (!cast.hasSummon() || stones.size() < 4 || GameEffectRecipes.passiveObject(cast.lookup, 20014) == null) return null;
        long release = castingTime(fields, level, 500);
        long rain = levelValue(fields, level, 1, 3000, 1, 30000);
        long interval = 1000 / levelValue(fields, level, 2, 8, 1, 25);
        int radius = levelValue(fields, level, 0, 180, 1, 1600);
        long channelEnd = release + rain;
        var random = new SplittableRandom(20015);
        for (int i = 0; i < 48 && i * interval <= rain; i++) {
            long at = release + i * interval;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            double angle = random.nextDouble() * 2 * Math.PI;
            double x = distance * Math.cos(angle);
            double y = distance * Math.sin(angle) * .29;
            var style = new VisualStyle(null, 0, List.of(), Map.of(), random.nextBoolean());
            for (int layer = 0; layer < stones.size(); layer++) {
                cast.add(stones.get(layer), "死亡墓碑 · 落碑", 3501 + (int)Math.round(y) * 4 + layer,
                        at, -1, x, y, null, 1, style);
            }
        }
        cast.summon(release, channelEnd);
        return cast.finish(channelEnd, null, "死亡墓碑演示完整引导及原版多层墓碑；落点与翻面以固定随机样本重现，实战可提前中止");
    }

    /** State 39 uses native hold limits and switches to the four sword layers on release. */
    private static Act1SkillSequence bloodyRave(Cast cast, List<GameScript.Field> fields) throws IOException {
        var inhale = cast.body(63);
        var slash = cast.body(64);
        var impact = Act1SkillSequence.objectMotions(cast.lookup, 20041);
        if (inhale.size() < 9 || slash.size() < 4 || impact.isEmpty()) return null;
        long start = duration(inhale, 3, null);
        long loop = duration(inhale, 6, null);
        long lines = duration(inhale, 8, null);
        long ready = duration(inhale, 9, null);
        long hold = Math.max(1, staticValue(fields, 1, 3000));
        long release = ready + hold;
        long recover = release + duration(slash, Integer.MAX_VALUE, null);
        cast.poses.add(new Pose(etc(63), 0, release));
        cast.poses.add(new Pose(etc(64), release, recover));
        String folder = EFFECT + "bloodyrave/";
        for (int i = 1; i <= 2; i++) {
            cast.add(folder + "start" + i + ".ani", "嗜魂封魔斩 · 血气启动", 3500 + i,
                    start, -1, 55, -96, null, 1);
            cast.add(folder + "loop" + i + ".ani", "嗜魂封魔斩 · 吸附", 3500 + i,
                    loop, release, 55, -96, null, 1);
            cast.add(folder + "line" + i + ".ani", "嗜魂封魔斩 · 血气牵引", 3497 + i,
                    lines, release, 55, -96, null, 1, new VisualStyle(null, 0, List.of(), Map.of(), false,
                            cast.ani(folder + "line" + i + ".ani")));
        }
        var data = cast.swordData();
        double from = number(data, "br typhoon start offset", 10);
        double to = number(data, "br typhoon end offset", 210);
        double speed = Math.max(1, number(data, "br typhoon move speed", 200));
        double spacing = Math.max(1, number(data, "br typhoon interval", 30));
        long swirlLife = Math.max(1, Math.round(1000 * (to - from) / Math.max(1, speed)));
        long period = Math.max(1, Math.round(1000 * spacing / Math.max(1, speed)));
        double near = number(data, "br typhoon start size", .45);
        double far = number(data, "br typhoon end size", 1.1);
        var scale = new VisualStyle(null, 0, List.of(new VisualKeyframe(0, far, 1),
                new VisualKeyframe(swirlLife, near, 1)));
        for (long time = lines; time < release && time < lines + 28 * period; time += period) {
            cast.add(folder + "typhoon.ani", "嗜魂封魔斩 · 内吸旋风", 3499, time, Math.min(release, time + swirlLife),
                    55 + to, -96, new SkillMotion(time, swirlLife, -speed, 0), 1, scale);
        }
        cast.add(folder + "end.ani", "嗜魂封魔斩 · 吸附结束", 3501, release, -1, 55, -96, null, 1);
        for (int i = 1; i <= 4; i++) {
            cast.add(folder + "sword" + i + ".ani", "嗜魂封魔斩 · 血气之剑", i < 3 ? 3497 + i : 3500 + i,
                    release, -1, 0, 0, null, 1);
        }
        long impactAt = release + duration(slash, 3, null);
        cast.add(OBJECT + "bloodyravelayer.ani", "嗜魂封魔斩 · 斩击波后层", 3499, impactAt, -1, 0, 0, null, 1);
        cast.add(impact.get(0), "嗜魂封魔斩 · 斩击波", 3501, impactAt, -1, 0, 0, null, 1);
        return cast.finish(recover, null, "嗜魂封魔斩按已开启血之狂暴、持续按住技能键演示吸附和终结斩击；目标牵引及出血由实战处理");
    }

    /** State 41 lands on frame 5. Object 20043 erupts on FloorOver frame 4 and creates 20044. */
    private static Act1SkillSequence outrage(Cast cast, List<GameScript.Field> fields) throws IOException {
        long ready = cast.bodyDuration(66);
        var actor = cast.body(67);
        var floor = Act1SkillSequence.objectMotions(cast.lookup, 20043);
        if (ready == 0 || actor.size() < 6 || floor.size() < 3
                || GameEffectRecipes.passiveObject(cast.lookup, 20044) == null) return null;
        long fly = duration(actor, 5, null);
        long land = ready + fly;
        long recover = ready + duration(actor, Integer.MAX_VALUE, null);
        double distance = staticValue(fields, 0, 130);
        double height = staticValue(fields, 1, 150);
        double seconds = Math.max(1, fly) / 1000.0;
        var leap = new SkillMotion(ready, fly, distance / seconds, 0, 4 * height / seconds, -8 * height / (seconds * seconds));
        cast.poses.add(new Pose(etc(66), 0, ready));
        cast.poses.add(new Pose(etc(67), ready, recover, null, false, leap));
        String folder = EFFECT + "outragebreak/sword_";
        for (String blend : List.of("ldodge", "none")) {
            int layer = blend.equals("ldodge") ? 3502 : 3501;
            cast.add(folder + "ready_" + blend + ".ani", "崩山裂地斩 · 血剑准备", layer, 0, ready, 0, 0, null, 1);
            cast.add(folder + "slash_" + blend + ".ani", "崩山裂地斩 · 跳劈", layer, ready, recover, 0, 0, leap, 1);
            cast.add(folder + "slash_impact_" + blend + ".ani", "崩山裂地斩 · 落地冲击", layer,
                    land, -1, distance, 0, null, 1);
        }
        cast.add(folder + "slash_stone.ani", "崩山裂地斩 · 崩碎", 3503, land, -1, distance + 121, 10, null, 1);
        String object = OBJECT + "outragebreak/";
        double center = distance + 150;
        cast.add(object + "floor_impact_ldodge.ani", "崩山裂地斩 · 裂地闪光", 297, land, -1, center, 0, null, 1);
        long healed = land + cast.ani(floor.get(0));
        cast.add(floor.get(0), "崩山裂地斩 · 地裂底层", 280, land, healed, center, 0, null, 1);
        cast.add(floor.get(1), "崩山裂地斩 · 地裂边缘", 281, land, healed, center, 0, null, 1);
        cast.add(floor.get(2), "崩山裂地斩 · 地裂岩浆", 282, land, healed, center, 0, null, 1);
        // Object #20043 triggers eruption on the opening overlay's frame 4. The drops only update
        // once the basic ground animation has ended, while the sparks update from eruption onward.
        long eruption = land + duration(cast.animation(floor.get(1)).frames(), 4, null);
        int[][] vents = {{22, -12}, {-117, 15}, {-126, 37}, {104, -6}, {-60, 58}, {76, 33}, {10, 80}};
        int hits = Math.max(1, Math.min(16, staticValue(fields, 3, 5)));
        long eruptionLife = cast.ani(object + "bloodexp_damage.ani");
        for (int i = 0; i < hits; i++) {
            int[] vent = vents[i % vents.length];
            int variant = i % 2 + 1;
            long pulse = eruption + i * eruptionLife / hits;
            for (String part : List.of("bloodexp" + variant + "_none", "bloodexp" + variant + "_ldodge", "bloodexp_glow")) {
                cast.add(object + part + ".ani", "崩山裂地斩 · 岩浆喷发", 3600, pulse,
                        -1, center + vent[0], vent[1] * .29, null, 1);
            }
        }
        cast.particle(SWORD + "effect/particle/outragebreak_stone_part.ptl", "崩山裂地斩 · 碎石", land, 300, center, 0);
        String particles = "passiveobject/character/swordman/particle/outragebreak_";
        cast.particle(particles + "drops.ptl", "崩山裂地斩 · 岩浆飞溅", healed, 500, center, 0);
        cast.particle(particles + "light_part.ptl", "崩山裂地斩 · 裂地火星", eruption, 300, center, 0);
        // Native0x643D00/0x596A20 overwrite the emitter world position through vtable+40.
        // In particular drops.ptl's authored (450,100,0) is not an additional local offset.
        for (int i = 0; i < cast.emitters.size(); i++) {
            var e = cast.emitters.get(i);
            cast.emitters.set(i, new SkillParticleEmitter(e.path(), e.label(), e.start(), e.emitFor(), e.frequency(),
                    e.x(), e.y(), e.z(), e.clipX(), e.scale(), true, e.movementScale(), e.reverseFacing(), e.sourceMotion()));
        }
        return cast.finish(recover, leap, "崩山裂地斩按已开启血之狂暴演示血剑、跳劈、地裂和岩浆；轨迹使用原版高度与距离");
    }

    /** State 40 throws #20045, whose collision callback 0x5963D0 attaches fire #20047. */
    private static Act1SkillSequence kalla(Cast cast, List<GameScript.Field> fields, int level, boolean hitTarget) throws IOException {
        var land = cast.body(68);
        var shadow = Act1SkillSequence.objectMotions(cast.lookup, 20045);
        String travel = cast.lookup.read(SWORD + "particle/kallaland.ptl");
        if (!cast.hasSummon() || land.size() <= 3 || shadow.isEmpty() || travel == null) return null;
        var ptl = ParticleScript.parse(travel);
        long release = castingTime(fields, level, 500);
        long recovered = release + Math.max(1, staticValue(fields, 0, 500));
        String folder = EFFECT + "kalla/";
        long appear = cast.ani(folder + "kallaappear1.ani");
        long throwAt = Math.max(recovered, release + appear);
        long ready = throwAt + duration(land, Integer.MAX_VALUE, null);
        long shot = throwAt + duration(land, 3, null);
        long hideKalla = ready + duration(cast.body(70), 1, null);
        cast.summon(release, recovered);
        cast.poses.add(new Pose(WAIT, recovered, throwAt));
        cast.poses.add(new Pose(etc(68), throwAt, ready));
        for (int i = 1; i <= 2; i++) {
            cast.add(folder + "kallaappear" + i + ".ani", "冥炎之卡洛 · 降临", 3496 + i, release, throwAt, 0, 0, null, 1);
            cast.add(folder + "kallastay" + i + ".ani", "冥炎之卡洛 · 鬼神维持", 3496 + i, throwAt, hideKalla, 0, 0, null, 1);
        }
        for (int i = 1; i <= 4; i++) {
            cast.add(folder + "land" + i + ".ani", "冥炎之卡洛 · 发射分身", 3497 + i, throwAt, ready, 0, 0, null, 1);
        }
        long flight = ptl.life();
        var motion = new SkillMotion(shot, flight, ptl.axes().get(0).start(), 0);
        long contact = hitTarget ? shot + Math.min(180, flight) : shot + flight;
        cast.add(shadow.get(0), "冥炎之卡洛 · 地面分身", 3600, shot, contact, 110, -53, motion, 1);
        cast.add(OBJECT + "kalla/shadowlanddodge.ani", "冥炎之卡洛 · 分身冥炎", 3601, shot, contact, 110, -53, motion, 1);
        if (hitTarget) {
            var fire = Act1SkillSequence.objectMotions(cast.lookup, 20047);
            if (fire.size() < 2) return null;
            double targetX = 110 + motion.offsetAt(contact);
            long period = levelValue(fields, level, 6, 1000, 1, 20000);
            long burnEnd = contact + period * levelValue(fields, level, 5, 5, 1, 20);
            cast.add(folder + "expeffect1.ani", "冥炎之卡洛 · 分身命中爆发", 3602, contact, -1, targetX, -53, null, 1);
            long overlay = contact + duration(cast.animation(fire.get(0)).frames(), 5, null);
            for (int layer = 0; layer < fire.size(); layer++) {
                String path = fire.get(layer);
                long loop = cast.ani(path);
                if (loop > 0) {
                    var style = new VisualStyle(null, 0, List.of(), Map.of(), false, loop);
                    cast.add(path, "冥炎之卡洛 · 目标附身冥炎", 3603 + layer, layer < 2 ? contact : overlay,
                            burnEnd, targetX, .29 - 53, null, 1, style);
                }
            }
            Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "stay", "冥炎之卡洛 · 哥布林", 0, contact, targetX, null);
            long reacted = Math.min(burnEnd, contact + 250);
            Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "damage1", "冥炎之卡洛 · 分身命中目标",
                    contact, reacted, targetX, null);
            Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "stay", "冥炎之卡洛 · 冥炎附着目标",
                    reacted, burnEnd, targetX, null);
        } else {
            cast.add(folder + "expeffect1.ani", "冥炎之卡洛 · 分身落地爆发", 3602, contact, -1,
                    110 + motion.offsetAt(contact), 0, null, 1);
        }
        long end = ready;
        double x = 0;
        var values = GameScript.values(fields, "static data");
        int speed = staticValue(fields, 1, 100);
        int friction = values.size() > 2 ? GameScript.integer(values.get(2), -300) : -300;
        SkillMotion movement = null;
        for (int action = 70; action <= 73; action++) {
            long span = cast.bodyDuration(action);
            if (span == 0) return null;
            String stem = action == 70 ? "finishready" : "finish" + (action - 70) + "-";
            String label = action == 70 ? "冥炎之卡洛 · 冥炎剑准备" : "冥炎之卡洛 · 终结第" + (action - 70) + "击";
            if (action > 70) {
                long travelTime = friction < 0 ? Math.min(span, Math.round(-1000.0 * speed / friction)) : span;
                movement = new SkillMotion(end, travelTime, speed, friction, x);
            }
            cast.poses.add(new Pose(etc(action), end, end + span, null, false, movement));
            for (int i = 1; i <= (action == 73 ? 6 : 4); i++) {
                cast.add(folder + stem + i + ".ani", label, i < 3 ? 3496 + i : 3500 + i, end,
                        action == 73 ? -1 : end + span, 0, 0, movement, 1);
            }
            if (action == 73) {
                long strike = end + duration(cast.body(73), 2, null);
                for (int i = 1; i <= 4; i++) {
                    cast.add(folder + "exp" + i + ".ani", "冥炎之卡洛 · 终结冥炎", 3600 + i, strike,
                            -1, 89 + movement.offsetAt(strike), -96, null, 1);
                }
            }
            end += span;
            if (movement != null) x = movement.offsetAt(end);
        }
        return cast.finish(end, movement, hitTarget ? "冥炎之卡洛演示地面分身命中一名哥布林后附着冥炎，以及再次输入的三连斩"
                : "冥炎之卡洛演示空场地面分身和再次输入的三连斩");
    }

    /** State 10 and object 20038 use the existing mark count; state 37 switches each orb at frame 6. */
    private static Act1SkillSequence waveSpin(Cast cast, List<GameScript.Field> fields, int level, int count) throws IOException {
        var orb = Act1SkillSequence.objectMotions(cast.lookup, 20038);
        var burst = cast.body(56);
        if (!cast.hasSummon() || orb.size() < 4 || burst.size() <= 6) return null;
        long release = castingTime(fields, level, 500);
        long spin = Math.max(1, staticValue(fields, 0, 6000));
        long period = Math.max(1, staticValue(fields, 1, 1500));
        long windup = duration(burst, 6, null);
        long explode = release + spin;
        long finish = Math.max(release, explode - windup);
        long recover = explode + duration(burst.subList(6, burst.size()), Integer.MAX_VALUE, null);
        String folder = EFFECT + "wavespinarea/";
        double circleScale = staticValue(fields, 4, 350) / 350.0;
        waveCircle(cast, folder + "circle.ani", "不动明王阵 · 地面法阵", 281, release, recover, circleScale, true);
        waveCircle(cast, folder + "circleback.ani", "不动明王阵 · 法阵后层", 280, release, recover, circleScale, false);
        waveCircle(cast, folder + "circlefront.ani", "不动明王阵 · 法阵前层", 3501, release, recover, circleScale, false);
        String create = folder + "create.ani";
        long orbitStart = release + duration(cast.animation(create).frames(), 5, null);
        cast.add(create, "不动明王阵 · 焰珠生成", 3502, release, -1, 0, -130, null, 1);
        var data = cast.swordData();
        double minRadius = number(data, "wsa min radius", 120);
        double maxRadius = number(data, "wsa max radius", 150);
        double minTerm = number(data, "wsa radius change min term", 200);
        double maxTerm = number(data, "wsa radius change max term", 600);
        var x = new ParticleScript.Axis("cos", minRadius, maxRadius, period, period);
        var y = new ParticleScript.Axis("sin", minRadius, maxRadius, period, period);
        double radiusTerm = Math.max(1, (minTerm + maxTerm) * .5);
        for (int i = 0; i < count; i++) {
            var motion = SkillMotion.orbit(orbitStart, Math.max(1, explode - orbitStart),
                    new SkillMotion.Orbit(x, y, radiusTerm, "ping pong", 360.0 * i / count, null, true));
            for (int layer = 0; layer < 4; layer++) {
                cast.add(orb.get(layer), "不动明王阵 · 焰珠旋转", GamePreview.WORLD_IN_FRONT_OF_ACTOR + layer, orbitStart,
                        explode, 0, -130, motion, 1);
            }
            int burstLayer = motion.groundDepthAt(explode) < 0 ? GamePreview.WORLD_BEHIND_ACTOR : GamePreview.WORLD_IN_FRONT_OF_ACTOR;
            cast.add(OBJECT + "wavespinareabomb" + (i % 3 + 1) + ".ani", "不动明王阵 · 焰珠爆炸", burstLayer,
                    explode, -1, motion.offsetAt(explode), -130 + motion.offsetYAt(explode), null, 1);
        }
        waveSpinTarget(cast, fields, folder, data, release, explode, recover);
        cast.summon(release, finish);
        cast.poses.add(new Pose(etc(56), finish, explode, null, false, null, 0, 6));
        cast.poses.add(new Pose(etc(56), explode, recover, null, false, null, 6));
        return cast.finish(recover, null, "不动明王阵以 " + count + " 枚波动印和基础转速演示焰珠，"
                + "并以一名普通哥布林演示圈内拘束、浮空与释放");
    }

    /** 0x6380B0 and 0x539AE0 bring a held target to radius 150 and center height 130 over 500 ms. */
    private static void waveSpinTarget(Cast cast, List<GameScript.Field> fields, String folder,
                                       List<String> data, long release, long explode, long recover) throws IOException {
        double distance = number(data, "wsa hold distance", 150);
        double targetX = Math.min(distance, staticValue(fields, 4, 350) / 3.0);
        double targetHeight = 60;
        double lift = 130 - targetHeight / 2;
        double scale = .5 + (targetHeight - 50) * 3.5 / 250;
        String floor = folder + "holdfloor.ani";
        long liftAt = cast.add(floor, "不动明王阵 · 目标拘束标记", 295,
                release, -1, targetX, 0, null, scale);
        liftAt = Math.min(liftAt, explode);
        // The target's draw callback starts the glow at ground-marker frame 2, before lift.
        long glowAt = Math.min(liftAt, release + duration(cast.animation(floor).frames(), 2, null));
        var held = new SkillMotion(liftAt, 500, 2 * (distance - targetX), 0, 2 * lift, 0);
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "stay", "不动明王阵 · 目标",
                0, liftAt, targetX, null);
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "damage1", "不动明王阵 · 浮空目标",
                liftAt, explode, targetX, held);
        cast.add(folder + "hold.ani", "不动明王阵 · 目标拘束光效", 3551, glowAt,
                explode, targetX, 0, held, scale);
        long fallTime = Math.max(1, Math.min(500, recover - explode));
        double seconds = fallTime / 1000.0;
        double dropHeight = held.heightAt(explode);
        var fall = new SkillMotion(explode, fallTime, 140, -280, 0, -2 * dropHeight / (seconds * seconds), dropHeight);
        Act1SwordAsuraSupplement.addTarget(cast.visuals, cast.lookup, "overturn", "不动明王阵 · 解除浮空",
                explode, recover, targetX + held.offsetAt(explode), fall);
    }

    /** 0x4E3FF0 draws both horizontal halves; the floor also reflects over the ground baseline. */
    private static void waveCircle(Cast cast, String path, String label, int layer, long start, long end,
                                   double scale, boolean reflectVertically) throws IOException {
        cast.add(path, label, layer, start, end, 0, 0, null, scale);
        cast.add(path, label, layer, start, end, 0, 0, null, scale,
                new VisualStyle(null, 0, List.of(), Map.of(), true));
        if (reflectVertically) {
            var frames = new HashMap<Integer, FrameOverride>();
            int count = cast.animation(path).frames().size();
            for (int i = 0; i < count; i++) {
                frames.put(i, new FrameOverride(0, 0, null, 1, -1));
            }
            cast.add(path, label, layer, start, end, 0, 0, null, scale,
                    new VisualStyle(null, 0, List.of(), frames));
            cast.add(path, label, layer, start, end, 0, 0, null, scale,
                    new VisualStyle(null, 0, List.of(), frames, true));
        }
    }

    private static String etc(int action) {
        return "etc motion:" + action;
    }

    private static double number(List<String> data, String key, double fallback) {
        for (int i = 0; i + 1 < data.size(); i += 2) {
            if (!data.get(i).equals(key)) continue;
            try {
                double value = Double.parseDouble(data.get(i + 1));
                return Double.isFinite(value) ? value : fallback;
            } catch (NumberFormatException invalid) {
                return fallback;
            }
        }
        return fallback;
    }

    private static final class Cast {
        final GameScript.Lookup lookup;
        final Map<String, String> actions;
        final String prepareAction,releaseAction;
        final List<Pose> poses = new ArrayList<>();
        final List<Visual> visuals = new ArrayList<>();
        final List<SkillParticleEmitter> emitters = new ArrayList<>();
        final List<Act1SkillSequence.AttackArea> areas = new ArrayList<>();
        final Map<String, ResourceScript.Animation> animations = new HashMap<>();
        long effectEnd;

        Cast(GameScript.Lookup lookup, Map<String, String> actions,int group) {
            this.lookup = lookup;
            this.actions = actions;
            prepareAction="throw motion "+group+"-1";
            releaseAction="throw motion "+group+"-2";
        }

        boolean hasSummon() {
            return actions.containsKey(prepareAction) && actions.containsKey(releaseAction);
        }

        List<ResourceScript.Frame> body(int action) throws IOException {
            return Act1SkillSequence.frames(SWORD + "swordman.chr", actions.get(etc(action)), lookup);
        }

        long bodyDuration(int action) throws IOException {
            return duration(body(action), Integer.MAX_VALUE, null);
        }

        List<String> swordData() throws IOException {
            var fields = GameScript.fields(lookup.read("data/skill/swordman.sd"));
            var data = new ArrayList<>(GameScript.values(fields, "int"));
            data.addAll(GameScript.values(fields, "float"));
            return data;
        }

        ResourceScript.Animation animation(String path) throws IOException {
            if (!animations.containsKey(path)) {
                String text = lookup.read(path);
                animations.put(path, text == null ? new ResourceScript.Animation(false, List.of(), List.of())
                        : ResourceScript.animation(text));
            }
            return animations.get(path);
        }

        long ani(String path) throws IOException {
            return duration(animation(path).frames(), Integer.MAX_VALUE, null);
        }

        void summon(long release, long recovery) {
            poses.addAll(Act1SkillSequence.castPoses(actions, prepareAction, releaseAction, release, recovery, recovery, false));
        }

        long add(String path, String label, int layer, long start, long end, double x, double y,
                 SkillMotion motion, double scale) throws IOException {
            return add(path, label, layer, start, end, x, y, motion, scale, VisualStyle.DEFAULT);
        }

        long add(String path, String label, int layer, long start, long end, double x, double y,
                 SkillMotion motion, double scale, VisualStyle style) throws IOException {
            long length = ani(path);
            if (length == 0 || (end >= 0 && end <= start)) return start;
            long stop = end < 0 ? start + length : end;
            visuals.add(new Visual(path, label, layer, start, stop, x, y, motion, scale, true, style));
            effectEnd = Math.max(effectEnd, stop);
            return stop;
        }

        void particle(String path, String label, long start, long active, double x, double z) throws IOException {
            String text = lookup.read(path);
            if (text == null) return;
            emitters.add(new SkillParticleEmitter(path, label, start, active, 0, x, 0, z, Double.NaN, 1));
            effectEnd = Math.max(effectEnd, start + active + SkillParticleEmitter.tail(ParticleScript.parse(text)));
        }

        Act1SkillSequence finish(long actorEnd, SkillMotion finalPosition, String note) {
            if (visuals.isEmpty()) return null;
            if (effectEnd > actorEnd && actions.containsKey(WAIT)) {
                poses.add(new Pose(WAIT, actorEnd, effectEnd, null, false, finalPosition));
            }
            return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters), note, List.copyOf(areas));
        }
    }
}
