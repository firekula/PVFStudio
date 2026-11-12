package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Swordman states 16 and 23, including the upgrades that change the parent cast. */
final class Act1SwordSupplement {
    private static final String ROOT = "character/swordman/";
    private static final String EFFECT = ROOT + "effect/animation/";
    private static final String CATALOG = "skill/swordmanskill.lst";
    private static final String WAVE = "skill/swordman/grandwave.skl";
    private static final String CHARGED_WAVE = "skill/swordman/grandwavecharge.skl";
    private static final String GHOST_SLASH = "skill/swordman/hardattack.skl";
    private static final String CHARGED_GHOST_SLASH = "skill/swordman/hardattackcharge.skl";
    private static final String ATTACK = "etc motion:1";
    private static final String CHARGE_AFTER = "etc motion:17";
    private static final String WAIT = "waiting motion";
    private static final String KHAZAN = "hardattackcharge:khazan";
    private static final String SAYA = "hardattackcharge:saya";
    private static final String BREMEN = "hardattackcharge:bremen";

    private record Ghost(String label, int skill, int object, int cast, int recovery, int life) {}
    private static final Ghost KHAZAN_ARRAY = new Ghost("刀魂之卡赞", 25, 20011, 300, 500, 20000);
    private static final Ghost SAYA_ARRAY = new Ghost("冰霜之萨亚", 36, 20012, 500, 600, 10000);
    private static final Ghost BREMEN_ARRAY = new Ghost("侵蚀之普戾蒙", 41, 20013, 400, 550, 20000);

    private Act1SwordSupplement() {}

    static List<GameEffectRecipes.Effect> choices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1 || !(path.equals(CHARGED_GHOST_SLASH) || path.equals(GHOST_SLASH))
                || !binding(CHARGED_GHOST_SLASH, 6, lookup) || !binding(GHOST_SLASH, 5, lookup)) return List.of();
        return List.of(
                new GameEffectRecipes.Effect(KHAZAN, "满蓄鬼斩 · 卡赞", "", path, "模拟最后召唤的鬼神为卡赞"),
                new GameEffectRecipes.Effect(SAYA, "噬灵鬼斩 · 萨亚", "", path, "模拟最后召唤的鬼神为萨亚"),
                new GameEffectRecipes.Effect(BREMEN, "噬灵鬼斩 · 普戾蒙", "", path, "模拟最后召唤的鬼神为普戾蒙"));
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level, String choice) throws IOException {
        return switch (path) {
            case WAVE -> choice.isEmpty() && binding(path, 50, lookup)
                    ? wave(text, null, lookup, actions, level) : null;
            case CHARGED_WAVE -> choice.isEmpty() && binding(path, 51, lookup) && binding(WAVE, 50, lookup)
                    ? wave(lookup.read(WAVE), text, lookup, actions, 1) : null;
            case CHARGED_GHOST_SLASH -> (choice.isEmpty() || ghostChoice(choice))
                    && binding(path, 6, lookup) && binding(GHOST_SLASH, 5, lookup)
                    ? ghostSlash(text, lookup, actions, choice) : null;
            case GHOST_SLASH -> ghostChoice(choice) && binding(path, 5, lookup) && binding(CHARGED_GHOST_SLASH, 6, lookup)
                    ? ghostSlash(lookup.read(CHARGED_GHOST_SLASH), lookup, actions, choice) : null;
            case "skill/swordman/khazan.skl" -> choice.isEmpty() && binding(path, KHAZAN_ARRAY.skill(), lookup)
                    ? ghostArray(KHAZAN_ARRAY, text, lookup, actions, level) : null;
            case "skill/swordman/saya.skl" -> choice.isEmpty() && binding(path, SAYA_ARRAY.skill(), lookup)
                    ? ghostArray(SAYA_ARRAY, text, lookup, actions, level) : null;
            case "skill/swordman/bremen.skl" -> choice.isEmpty() && binding(path, BREMEN_ARRAY.skill(), lookup)
                    ? ghostArray(BREMEN_ARRAY, text, lookup, actions, level) : null;
            default -> null;
        };
    }

    /** 0x638640 creates the ghost at X122 and 0x593670 switches its area through appear, stay, and disappear. */
    private static Act1SkillSequence ghostArray(Ghost ghost, String text, GameScript.Lookup lookup,
                                                Map<String, String> actions, int level) throws IOException {
        // The state 10 vector for all four ghost arrays selects mode zero (0x643D00, 0xB145E0).
        String prepare = "throw motion 1-1";
        String release = "throw motion 1-2";
        if (!actions.containsKey(prepare) || !actions.containsKey(release)) return null;
        String object = GameEffectRecipes.passiveObject(lookup, ghost.object());
        if (object == null) return null;
        var refs = GameScript.values(GameScript.fields(lookup.read(object)), "etc motion");
        if (refs.size() < 6) return null;
        var layers = new ArrayList<String>();
        for (String ref : refs.subList(0, 6)) {
            String path = GameScript.resolve(object, ref, lookup);
            if (path == null) return null;
            layers.add(path);
        }
        var fields = GameScript.fields(text);
        long cast = Act1SkillSequence.castingTime(fields, level, ghost.cast());
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, ghost.recovery()));
        long life = Act1SkillSequence.levelValue(fields, level, 1, ghost.life(), 1, 45000);
        long opens = Math.min(life, animationDuration(layers.get(2), lookup));
        long closes = Math.min(life - opens, animationDuration(layers.get(5), lookup));
        long end = cast + life;
        double x = 122;
        double scale = Act1SkillSequence.staticValue(fields, 1, 250) / 250.0;
        double opacity = 180 / 255.0;
        var fade = new VisualStyle(null, 0, List.of(
                new VisualKeyframe(0, 1, 0), new VisualKeyframe(opens, 1, opacity),
                new VisualKeyframe(life - closes, 1, opacity), new VisualKeyframe(life, 1, 0)));
        var visuals = new ArrayList<Visual>();
        for (int i = 0; i < 2; i++) {
            visuals.add(new Visual(layers.get(i), ghost.label() + " · 鬼神", 3550 + i, cast, end, x, 0, null, 1, true, fade));
            if (opens > 0) {
                visuals.add(new Visual(layers.get(i + 2), ghost.label() + " · 法阵展开", 280 + i,
                        cast, cast + opens, x, 0, null, scale, true));
            }
        }
        if (life > opens + closes) {
            visuals.add(new Visual(layers.get(4), ghost.label() + " · 法阵维持", 280, cast + opens, end - closes,
                    x, 0, null, scale, true));
        }
        if (closes > 0) {
            visuals.add(new Visual(layers.get(5), ghost.label() + " · 法阵消散", 280, end - closes, end,
                    x, 0, null, scale, true));
        }
        var areas = new ArrayList<Act1SkillSequence.AttackArea>();
        if (ghost == SAYA_ARRAY) {
            // 0x5940C0 reads static columns 1–3; 0x593670 tests target height and distance on each pulse.
            int radius = Act1SkillSequence.staticValue(fields, 1, 250);
            int height = Act1SkillSequence.staticValue(fields, 2, 70);
            int interval = Act1SkillSequence.staticValue(fields, 3, 1000);
            if (radius > 0 && interval > 0) {
                areas.add(new Act1SkillSequence.AttackArea("冰霜之萨亚 · 目标筛选范围", cast, end, x, 0,
                        new CombatDebug.TargetRange(radius, height, interval)));
                Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "冰霜之萨亚 · 阵内目标",
                        cast, end, x + radius / 2.0, null);
            }
        }
        String note=ghost.label()+"按技能时长演示原版法阵的展开、维持和消散；"
                +(areas.isEmpty()?"范围内的状态效果由战斗处理":"判定模式显示实际的目标筛选范围");
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions, prepare, release, cast, recovery, end, false),
                List.copyOf(visuals), List.of(), note, List.copyOf(areas));
    }

    private static boolean binding(String path, int id, GameScript.Lookup lookup) throws IOException {
        return path.equals("skill/" + GameScript.catalog(lookup.read(CATALOG)).get(id));
    }

    private static boolean ghostChoice(String choice) {
        return choice.equals(KHAZAN) || choice.equals(SAYA) || choice.equals(BREMEN);
    }

    /** 0x643D00 fires the ground wave at frame 4 after the HardAttack motion restarts at frame 1. */
    private static Act1SkillSequence wave(String text, String upgrade, GameScript.Lookup lookup,
                                          Map<String, String> actions, int level) throws IOException {
        if (text == null) return null;
        boolean charged = upgrade != null;
        var body = body(ATTACK, "hardattack.ani", actions, lookup);
        var objects = Act1SkillSequence.objectMotions(lookup, charged ? 20008 : 20019);
        String movement = lookup.read(ROOT + "particle/grandwave.ptl");
        if (body.size() < 8 || objects.size() < 2 || movement == null) return null;
        var axis = ParticleScript.parse(movement).axes().get(0);
        if (!axis.type().equals("straight line") || axis.start() <= 0) return null;
        var fields = GameScript.fields(text);
        var charge = GameScript.fields(upgrade);
        long cast = Act1SkillSequence.castingTime(fields, level, 300);
        long ready = cast + (charged ? Act1SkillSequence.staticValue(charge, 0, 700) : 0);
        long released = ready + duration(body, 1, 4);
        long recovery = ready + duration(body, 1, body.size());
        long life = Act1SkillSequence.levelValue(fields, level, 0, 700, 1, 10000);
        double speed = axis.start() * (charged ? Act1SkillSequence.staticValue(charge, 2, 150) / 100.0 : 1);
        double scale = charged ? Act1SkillSequence.staticValue(charge, 1, 120) / 100.0 : 1;
        long end = Math.max(recovery, released + life);
        var motion = new SkillMotion(released, life, speed, 0);
        String name = charged ? "修罗邪光斩" : "邪光斩";
        var visuals = new ArrayList<Visual>();
        for (int layer = 0; layer < 2; layer++) {
            visuals.add(new Visual(objects.get(layer), name + " · 剑气", 3600 + layer, released, released + life,
                    80, 0, motion, scale, true));
        }
        add(visuals, lookup, "grandwave", name + " · 挥剑", 3501, ready, recovery, null);
        if (charged) {
            addPair(visuals, lookup, "grandwaveoncharge", name + " · 蓄力", cast, ready);
            addPair(visuals, lookup, "grandwavefullcharge", name + " · 满蓄闪光", ready, -1);
        }
        var poses = new ArrayList<Pose>();
        if (ready > 0) poses.add(new Pose(ATTACK, 0, ready, null, false, null, 0, 1));
        poses.add(new Pose(ATTACK, ready, recovery, null, false, null, 1));
        if (end > recovery && actions.containsKey(WAIT)) poses.add(new Pose(WAIT, recovery, end));
        String note = charged ? "修罗邪光斩以 Lv.1 邪光斩演示满蓄后的原版剑气与射程；目标击退与波动印由战斗状态处理"
                : "邪光斩演示未蓄力的原版剑气；满蓄效果见修罗邪光斩";
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(), note);
    }

    /** State 16 enters the follow-up at frame 9 and chooses the ghost last summoned in combat. */
    private static Act1SkillSequence ghostSlash(String text, GameScript.Lookup lookup,
                                                Map<String, String> actions, String choice) throws IOException {
        var body = body(ATTACK, "hardattack.ani", actions, lookup);
        var slide = body(CHARGE_AFTER, "hardattackchargeafter.ani", actions, lookup);
        if (body.size() < 10 || slide.isEmpty()) return null;
        String object = GameEffectRecipes.passiveObject(lookup, 20023);
        if (object == null) return null;
        var references = GameScript.values(GameScript.fields(lookup.read(object)), "etc motion");
        if (references.size() < 6) return null;
        int variant = choice.equals(SAYA) ? 1 : choice.equals(BREMEN) ? 2 : 0;
        String name = switch (variant) {
            case 1 -> "萨亚";
            case 2 -> "普戾蒙";
            default -> "卡赞";
        };
        var fields = GameScript.fields(text);
        var data = GameScript.values(fields, "static data");
        int acceleration = data.size() < 4 ? -1200 : Math.max(-30000, Math.min(0, GameScript.integer(data.get(3), -1200)));
        long chargeStarts = body.get(0).delay();
        long release = chargeStarts + Act1SkillSequence.staticValue(fields, 1, 500);
        long follow = release + duration(body, 1, 9);
        long end = follow + duration(slide, 0, slide.size());
        double velocity = Act1SkillSequence.staticValue(fields, 2, 750);
        long travel = acceleration < 0 ? Math.min(end - follow, Math.round(-1000 * velocity / acceleration)) : end - follow;
        var motion = new SkillMotion(follow, travel, velocity, acceleration);
        var visuals = new ArrayList<Visual>();
        for (int i = 0; i < 2; i++) {
            String path = GameScript.resolve(object, references.get(variant * 2 + i), lookup);
            if (path == null) return null;
            long length = animationDuration(path, lookup);
            if (length == 0) return null;
            // The preview wave continues at release speed while the follow-up pose decelerates.
            var wave = new SkillMotion(follow, length, velocity, 0);
            visuals.add(new Visual(path, "噬灵鬼斩 · " + name + "鬼神冲波", 3600 + i,
                    follow, follow + length, 0, .29 - 1, wave, 1, true));
        }
        addPair(visuals, lookup, "hardattackoncharge", "噬灵鬼斩 · 蓄力", chargeStarts, release);
        addPair(visuals, lookup, "hardattackfullcharge", "噬灵鬼斩 · 满蓄闪光", release, -1);
        addPair(visuals, lookup, "hardattack", "噬灵鬼斩 · 鬼斩", release, follow);
        add(visuals, lookup, "hardattackchargeafterdust", "噬灵鬼斩 · 冲锋烟尘", 3501, follow, -1, null);
        add(visuals, lookup, "wave-down", "噬灵鬼斩 · 追击刀光", 3501, follow, end, motion);
        add(visuals, lookup, "wave-up", "噬灵鬼斩 · 追击刀光", 3502, follow, end, motion);
        var poses = List.of(new Pose(ATTACK, 0, release, null, false, null, 0, 1),
                new Pose(ATTACK, release, follow, null, false, null, 1, 9),
                new Pose(CHARGE_AFTER, follow, end, null, false, motion));
        return new Act1SkillSequence(poses, List.copyOf(visuals), List.of(),
                "噬灵鬼斩演示满蓄鬼斩、前滑追击和" + name + "冲波；鬼神由最后召唤记录决定，未召唤时使用卡赞");
    }

    private static List<ResourceScript.Frame> body(String action, String file, Map<String, String> actions,
                                                    GameScript.Lookup lookup) throws IOException {
        String ref = actions.get(action);
        if (ref == null || !GameScript.normalize(ref).endsWith(file)) return List.of();
        return Act1SkillSequence.frames(ROOT + "swordman.chr", ref, lookup);
    }

    private static long duration(List<ResourceScript.Frame> frames, int first, int end) {
        return Act1SkillSequence.duration(frames.subList(first, end), Integer.MAX_VALUE, null);
    }

    private static long animationDuration(String path, GameScript.Lookup lookup) throws IOException {
        String text = lookup.read(path);
        return text == null ? 0 : new AnimationTimeline(ResourceScript.animation(text)).duration();
    }

    private static void addPair(List<Visual> visuals, GameScript.Lookup lookup, String stem, String label,
                                long start, long end) throws IOException {
        add(visuals, lookup, stem + "1", label, 3501, start, end, null);
        add(visuals, lookup, stem + "2", label, 3502, start, end, null);
    }

    private static void add(List<Visual> visuals, GameScript.Lookup lookup, String stem, String label,
                            int layer, long start, long end, SkillMotion motion) throws IOException {
        String path = EFFECT + stem + ".ani";
        long life = animationDuration(path, lookup);
        if (life > 0 && (end < 0 || end > start)) {
            visuals.add(new Visual(path, label, layer, start, end < 0 ? start + life : end, 0, 0, motion, 1, true));
        }
    }
}
