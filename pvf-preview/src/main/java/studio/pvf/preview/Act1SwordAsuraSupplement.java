package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Swordman wave skills that use the ACT1 caster, passive object, and status clocks. */
final class Act1SwordAsuraSupplement {
    private static final String ROOT = "character/swordman/";
    private static final String EFFECT = ROOT + "effect/animation/";
    private static final String PARTICLES = ROOT + "effect/particle/";
    private static final String WAIT = "waiting motion";
    private static final String CAST = "throw motion 2-1";
    private static final String RELEASE = "throw motion 2-2";
    private static final String MARK_CHOICE = "act1-wave-marks-";
    private static final String SHOCK_HIT = "act1-shockwave-hit";
    private static final String SHOCK_MISS = "act1-shockwave-miss";
    private static final String REFLECT_GUARD = "skill/swordman/reflectguard.skl";
    private static final String REFLECT_MISS = "reflectguard:miss";
    static final String GOBLIN = "monster/goblin/animation_goblin/";

    private Act1SwordAsuraSupplement() {}

    static List<GameEffectRecipes.Effect> choices(String path,String text,GameScript.Lookup lookup,
                                                  boolean act1) throws IOException {
        if(!act1)return List.of();
        int id=switch(path){
            case REFLECT_GUARD -> 2;
            case "skill/swordman/wavemark.skl"->47;
            case "skill/swordman/shockwavearea.skl"->57;
            case "skill/swordman/wavespinarea.skl"->74;
            default->-1;
        };
        if(id<0||!path.equals("skill/"+GameScript.catalog(lookup.read("skill/swordmanskill.lst")).get(id)))return List.of();
        if(id==2)return List.of(new GameEffectRecipes.Effect(REFLECT_MISS,"鬼印珠 · 空场","",path,"展示鬼印珠飞行至到期消散"));
        if(id==57)return List.of(
                new GameEffectRecipes.Effect(SHOCK_HIT,"邪光波动阵 · 命中","",path,"显示拘束和目标爆炸"),
                new GameEffectRecipes.Effect(SHOCK_MISS,"邪光波动阵 · 空场","",path,"显示拍地和无目标法阵"));
        String source=id==47?text:lookup.read("skill/swordman/wavemark.skl");
        int maximum=Math.max(1,Math.min(5,Act1SkillSequence.staticValue(GameScript.fields(source),2,5)));
        var effects=new ArrayList<GameEffectRecipes.Effect>();
        for(int count=1;count<=maximum;count++)effects.add(new GameEffectRecipes.Effect(MARK_CHOICE+count,
                count+" 枚波动刻印","",path,id==47?"展示印记数量及原版图案":"按刻印数生成焰珠"));
        return List.copyOf(effects);
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level) throws IOException {
        return find(path, text, lookup, actions, level, "");
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level, String choice) throws IOException {
        int skill = switch (path) {
            case REFLECT_GUARD -> 2;
            case "skill/swordman/releasewave.skl" -> 32;
            case "skill/swordman/wavemark.skl" -> 47;
            case "skill/swordman/dotarea.skl" -> 52;
            case "skill/swordman/shockwavearea.skl" -> 57;
            default -> -1;
        };
        if (skill < 0 || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/swordmanskill.lst")).get(skill))) {
            return null;
        }
        if (!choice.isEmpty() && !(skill == 47 && markChoice(choice))
                && !(skill == 2 && choice.equals(REFLECT_MISS))
                && !(skill == 57 && (choice.equals(SHOCK_HIT) || choice.equals(SHOCK_MISS)))) return null;
        var fields = GameScript.fields(text);
        return switch (skill) {
            case 2 -> reflectGuard(fields, lookup, actions, level, !choice.equals(REFLECT_MISS));
            case 32 -> releaseWave(fields, lookup, actions);
            case 47 -> waveMark(fields, lookup, actions, level, choice);
            case 57 -> shockWaveArea(fields, lookup, actions, level, !choice.equals(SHOCK_MISS));
            default -> dotArea(fields, lookup, actions, level);
        };
    }

    static boolean markChoice(String choice) {
        if (!choice.startsWith(MARK_CHOICE) || choice.length() != MARK_CHOICE.length() + 1) return false;
        char count = choice.charAt(MARK_CHOICE.length());
        return count >= '1' && count <= '5';
    }

    static int markCount(String choice) {
        return markChoice(choice) ? choice.charAt(MARK_CHOICE.length()) - '0' : 1;
    }

    /** 0x53B9A0 places each active glyph at z=130 and spaces its phase by 360 / count. */
    private static Act1SkillSequence waveMark(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                              Map<String, String> actions, int level, String choice) throws IOException {
        if (!actions.containsKey(CAST) || !actions.containsKey(RELEASE)) return null;
        String folder = EFFECT + "wavemark/";
        long cast = Act1SkillSequence.castingTime(fields, level, 200);
        long recovery = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 500));
        long markLife = Math.max(1, Act1SkillSequence.staticValue(fields, 1, 30000));
        int maximum = Math.max(1, Act1SkillSequence.staticValue(fields, 2, 5));
        int count = Math.min(markCount(choice), maximum);
        var visuals = new ArrayList<Visual>();
        add(visuals, lookup, folder + "cast.ani", "波动刻印 · 施法", 3501, 0, cast, 0, 0, 1, cast);
        long markStart = cast;
        long openingEnd = cast;
        String add = folder + "add.ani";
        String addScript = lookup.read(add);
        if (addScript != null) {
            var opening = ResourceScript.animation(addScript);
            markStart = cast + Act1SkillSequence.duration(opening.frames(), 8, null);
            openingEnd = add(visuals, lookup, add, "波动刻印 · 首次生成", 3501, cast, -1, 0, 0, 1, 0);
        }
        long end = Math.max(Math.max(recovery, openingEnd), markStart + Math.min(markLife, 2000));
        var x = new ParticleScript.Axis("cos", 30, 30, 2000, 2000);
        var y = new ParticleScript.Axis("sin", -30, -30, 2000, 2000);
        for (int i = 0; i < count; i++) {
            String glyph = folder + "addmark" + (i + 1) + ".ani";
            if (duration(glyph, lookup) == 0) return null;
            var orbit = SkillMotion.orbit(markStart, end - markStart,
                    new SkillMotion.Orbit(x, y, 2000, "keep changing", -360.0 * i / count));
            visuals.add(new Visual(glyph, "波动刻印 · 第 " + (i + 1) + " 枚", 3700 + i,
                    markStart, end, 0, -130, orbit, 1, false));
        }
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions, CAST, RELEASE, cast, recovery, end, true),
                List.copyOf(visuals), List.of(), "波动刻印演示首次施放和 " + count + " 枚已激活印记的环绕；实战按技能间隔累积");
    }

    /** State 27 creates the caster's area even when 0x634800 finds no targets for object 20021. */
    private static Act1SkillSequence shockWaveArea(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                   Map<String, String> actions, int level, boolean hitTarget) throws IOException {
        String prepare = "etc motion:12";
        String release = "etc motion:13";
        if (body(actions, prepare, lookup).isEmpty() || body(actions, release, lookup).isEmpty()) return null;
        String folder = EFFECT + "shockwavearea/";
        String areaScript = lookup.read(folder + "area.ani");
        if (areaScript == null) return null;
        var area = ResourceScript.animation(areaScript);
        if (area.frames().size() < 4) return null;
        var settings = GameScript.values(GameScript.fields(lookup.read("data/skill/swordman.sd")), "int");
        int offset = setting(settings, "swa area offset", 146);
        int tail = setting(settings, "swa after delay", 500);
        long cast = Act1SkillSequence.castingTime(fields, level, 400);
        long hit = cast + Act1SkillSequence.duration(area.frames(), 3, null);
        long hold = Math.max(1, Act1SkillSequence.staticValue(fields, 1, 2000));
        long explode = hit + hold;
        long end = explode + tail;
        double scale = Act1SkillSequence.levelValue(fields, level, 4, 400, 1, 10000) / 444.0;
        var visuals = new ArrayList<Visual>();
        add(visuals, lookup, folder + "cast.ani", "邪光波动阵 · 聚气", 3501, 0, cast, 0, 0, 1, cast);
        add(visuals, lookup, folder + "smash.ani", "邪光波动阵 · 捶地", 3501, cast, -1, offset, 0, 1, 0);
        add(visuals, lookup, folder + "area.ani", "邪光波动阵 · 地面冲击", 280, cast, -1, offset, 0, scale, 0);
        if (hitTarget) {
            var object = Act1SkillSequence.objectMotions(lookup, 20021);
            if (object.size() < 3) return null;
            double targetX = offset + 30;
            addTarget(visuals, lookup, "stay", "邪光波动阵 · 目标", 0, hit, targetX, null);
            addTarget(visuals, lookup, "damage1", "邪光波动阵 · 束缚目标", hit, explode, targetX, null);
            long ending = Math.max(hit, explode - duration(object.get(1), lookup));
            String first = object.get(0);
            var initial = ResourceScript.animation(lookup.read(first));
            long firstLength = new AnimationTimeline(initial).duration();
            long cycleStart = Math.min(ending, hit + firstLength);
            add(visuals, lookup, first, "邪光波动阵 · 目标拘束", 3600, hit, cycleStart, targetX, .29, .6, 0);
            if (cycleStart < ending && initial.frames().size() > 2) {
                long skip = Act1SkillSequence.duration(initial.frames(), 2, null);
                var slice = new VisualStyle(null, 0, List.of(), Map.of(0, new FrameOverride(0, 0, 0),
                        1, new FrameOverride(0, 0, 0)), false, Math.max(1, firstLength - skip));
                visuals.add(new Visual(first, "邪光波动阵 · 目标拘束", 3600, cycleStart, ending,
                        targetX, .29, null, .6, true, slice));
            }
            add(visuals, lookup, object.get(1), "邪光波动阵 · 拘束收束", 3600, ending, explode, targetX, .29, .6, 0);
            add(visuals, lookup, object.get(2), "邪光波动阵 · 目标爆炸", 3600, explode, -1, targetX, .29, .6, 0);
            var knockback = new SkillMotion(explode, 500, 200, -400, 200, -800);
            addTarget(visuals, lookup, "overturn", "邪光波动阵 · 爆炸击飞", explode, end, targetX, knockback);
        }
        return new Act1SkillSequence(List.of(new Pose(prepare, 0, cast, null, true, null), new Pose(release, cast, end)),
                List.copyOf(visuals), List.of(), hitTarget ? "邪光波动阵演示命中一名普通哥布林后的拘束与爆炸"
                : "邪光波动阵演示空场聚气和地面冲击");
    }

    /** State 34 spawns #20032 on frame 4; 0x5634C0 counts hits and 0x5630C0 spawns #20048 on the cap. */
    private static Act1SkillSequence reflectGuard(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                  Map<String, String> actions, int level, boolean hitTarget) throws IOException {
        String action = "etc motion:46";
        var body = body(actions, action, lookup);
        var bead = Act1SkillSequence.objectMotions(lookup, 20032);
        String move = lookup.read(ROOT + "particle/reflectguardbead.ptl");
        if (body.size() < 5 || bead.size() < 2 || move == null) return null;
        var horizontal = ParticleScript.parse(move).axes().get(0);
        if (!horizontal.type().equals("straight line") || horizontal.start() <= 0) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 400);
        long released = cast + Act1SkillSequence.duration(body.subList(1, 4), 3, null);
        long recover = cast + Act1SkillSequence.duration(body.subList(1, body.size()), Integer.MAX_VALUE, null);
        long life = Act1SkillSequence.levelValue(fields, level, 0, 3000, 1, 15000);
        long fade = Math.min(300, duration(bead.get(1), lookup));
        long finish = released + life + fade;
        double scale = Act1SkillSequence.levelValue(fields, level, 2, 1000, 1, 10000) / 1000.0;
        var motion = new SkillMotion(released, life + fade, horizontal.start(), 0);
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        add(visuals, lookup, EFFECT + "reflectguard/charge.ani", "鬼印珠 · 聚气", 3501, 0, cast, -35, -68, 1, 0);
        add(visuals, lookup, EFFECT + "reflectguard/slash.ani", "鬼印珠 · 出手", 3502, cast, recover, 0, 0, 1, 0);
        long flightEnd = released + life;
        if (hitTarget) {
            long interval = Math.max(1, Act1SkillSequence.staticValue(fields, 1, 150));
            int cap = Math.max(1, Act1SkillSequence.staticValue(fields, 2, 10));
            long firstHit = released + 100;
            long capTime = firstHit + (cap - 1L) * interval;
            double targetStart = 150 + motion.offsetAt(firstHit) + 15;
            long contactsEnd = Math.min(capTime, flightEnd);
            var targetTravel = new SkillMotion(firstHit, Math.max(0, contactsEnd - firstHit), horizontal.start(), 0);
            addTarget(visuals, lookup, "stay", "鬼印珠 · 哥布林", 0, Math.min(firstHit, flightEnd), targetStart, null);
            String spark = "passiveobject/character/swordman/animation/reflectguardbead/damage.ani";
            long sampledInterval = interval * Math.max(1, (Math.min(cap, 1 + life / interval) + 255) / 256);
            for (long hit = firstHit; hit <= contactsEnd && hit < flightEnd; hit += sampledInterval) {
                double targetX = targetStart + targetTravel.offsetAt(hit);
                add(visuals, lookup, spark, "鬼印珠 · 多段命中", 3601, hit, -1, targetX, -55, 1, 0);
                addTarget(visuals, lookup, "damage1", "鬼印珠 · 受击目标", hit, Math.min(contactsEnd, hit + sampledInterval),
                        targetStart, targetTravel);
            }
            if (capTime < flightEnd) {
                String object = GameEffectRecipes.passiveObject(lookup, 20048);
                var explosion = Act1SkillSequence.objectMotions(lookup, 20048);
                if (object == null || explosion.isEmpty()) return null;
                flightEnd = capTime;
                double x = 150 + motion.offsetAt(capTime);
                double size = scale * Math.max(1, Act1SkillSequence.staticValue(fields, 3, 120)) / 100;
                finish = add(visuals, lookup, explosion.get(0), "鬼印珠 · 终结爆炸", 3602, capTime, -1, x, -68, size, 0);
                long active = Math.max(1, finish - capTime);
                for (String ref : GameScript.values(GameScript.fields(lookup.read(object)), "string data")) {
                    String path = GameScript.resolve(object, ref, lookup);
                    if (path == null || !path.endsWith(".ptl")) continue;
                    var script = ParticleScript.parse(lookup.read(path));
                    emitters.add(new SkillParticleEmitter(path, "鬼印珠 · 终结爆炸粒子", capTime, active, 0,
                            x, 0, 68, Double.NaN, size, false, 1));
                    finish = Math.max(finish, capTime + active + SkillParticleEmitter.tail(script));
                }
                var knockback = new SkillMotion(capTime, 550, 200, -360, 200, -800);
                addTarget(visuals, lookup, "overturn", "鬼印珠 · 爆炸击飞", capTime, finish,
                        targetStart + targetTravel.offsetAt(capTime), knockback);
                fade = 0;
            } else {
                addTarget(visuals, lookup, "stay", "鬼印珠 · 哥布林", contactsEnd, finish,
                        targetStart + targetTravel.offsetAt(contactsEnd), null);
            }
        }
        visuals.add(new Visual(bead.get(0), "鬼印珠 · 飞行", 3600, released, flightEnd, 150, -68, motion, scale, true));
        if (fade > 0) visuals.add(new Visual(bead.get(1), "鬼印珠 · 消散", 3600, flightEnd, finish, 150, -68,
                motion, scale, true));
        var poses = new ArrayList<Pose>();
        if (cast > 0) poses.add(new Pose(action, 0, cast, null, false, null, 0, 1));
        poses.add(new Pose(action, cast, recover, null, false, null, 1));
        if (finish > recover && actions.containsKey(WAIT)) poses.add(new Pose(WAIT, recover, finish));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters), hitTarget
                ? "鬼印珠以一枚波动刻印演示持续接触一名同向移动的哥布林；达到多段命中上限后在珠体位置爆炸"
                : "鬼印珠以一枚波动刻印演示空场出手、飞行和到期消散");
    }

    /** State 10 releases both radial visual layers immediately; object 20018 carries the attack boxes. */
    private static Act1SkillSequence releaseWave(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                 Map<String, String> actions) throws IOException {
        if (!actions.containsKey(RELEASE) || GameEffectRecipes.passiveObject(lookup, 20018) == null) return null;
        long recovery = Math.max(1, Act1SkillSequence.staticValue(fields, 0, 600));
        var visuals = new ArrayList<Visual>();
        add(visuals, lookup, EFFECT + "releasewave2.ani", "波动爆发 · 波动后层", 3499, 0, -1, 0, 0, 1, 0);
        add(visuals, lookup, EFFECT + "releasewave1.ani", "波动爆发 · 波动前层", 3501, 0, -1, 0, 0, 1, 0);
        if (visuals.size() < 2) return null;
        var motions = Act1SkillSequence.objectMotions(lookup, 20018);
        if (!motions.isEmpty()) add(visuals, lookup, motions.get(0), "波动爆发 · 攻击判定",
                3499, 0, -1, 0, 0, 1, 0);
        return new Act1SkillSequence(List.of(new Pose(RELEASE, 0, recovery)),
                List.copyOf(visuals), List.of(), "波动爆发演示瞬发的原版前后两层冲击波；命中时会击飞目标");
    }

    /** CNDoTArea installs three world-space emitters around the grounded caster. */
    private static Act1SkillSequence dotArea(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                             Map<String, String> actions, int level) throws IOException {
        if (!actions.containsKey("buff motion") || !actions.containsKey(WAIT)) return null;
        long cast = Act1SkillSequence.castingTime(fields, level, 500);
        int height = Act1BuffCast.stockHeight("swordman") + 30;
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        add(visuals, lookup, "common/commoneffect/animation/buffchargeglow.ani", "杀意波动 · 聚气",
                3700, 0, cast, 0, -height, 1, 0);
        if (cast > 0) particle(emitters, lookup, "common/commoneffect/particle/buffchargeparticle.ptl", "杀意波动 · 聚气",
                0, cast, 0, 0, height);
        long end = cast;
        long active = 1800;
        for (String kind : List.of("quake", "rock", "dust")) {
            String path = PARTICLES + "dotarea" + kind + ".ptl";
            end = Math.max(end, particle(emitters, lookup, path, "杀意波动 · 持续波动", cast, active, 0, 0, 0));
        }
        long activeEnd = cast + active;
        end = Math.max(end, activeEnd);
        // 0x6361D0 passes half of level column 1 and static column 0 to the hostile aura.
        int radius = Act1SkillSequence.levelValue(fields, level, 1, 300, 1, 10000) / 2;
        int heightLimit = Act1SkillSequence.staticValue(fields, 0, 50);
        var areas = radius > 0 ? List.of(new Act1SkillSequence.AttackArea("杀意波动 · 目标筛选范围",
                cast, activeEnd, 0, 0, new CombatDebug.TargetRange(radius, heightLimit, 250)))
                : List.<Act1SkillSequence.AttackArea>of();
        var poses = new ArrayList<Pose>();
        if (cast > 0) poses.add(new Pose("buff motion", 0, cast, null, true, null));
        poses.add(new Pose(WAIT, cast, end));
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(emitters),
                "杀意波动演示开启和一段持续效果；判定范围以每 250 ms 的客户端目标筛选节拍高亮，实际 HP 扣减另由目标状态结算",
                areas);
    }

    private static int setting(List<String> data, String name, int fallback) {
        for (int i = 0; i + 1 < data.size(); i += 2) {
            if (data.get(i).equals(name)) return Math.max(0, Math.min(10000, GameScript.integer(data.get(i + 1), fallback)));
        }
        return fallback;
    }

    private static List<ResourceScript.Frame> body(Map<String, String> actions, String action, GameScript.Lookup lookup)
            throws IOException {
        return Act1SkillSequence.frames(ROOT + "swordman.chr", actions.get(action), lookup);
    }

    private static long duration(String path, GameScript.Lookup lookup) throws IOException {
        String text = lookup.read(path);
        return text == null ? 0 : new AnimationTimeline(ResourceScript.animation(text)).duration();
    }

    static void addTarget(List<Visual> visuals, GameScript.Lookup lookup, String animation, String label,
                          long start, long end, double x, SkillMotion motion) throws IOException {
        addTarget(visuals, lookup, animation, label, start, end, x, 0, motion);
    }

    static void addTarget(List<Visual> visuals, GameScript.Lookup lookup, String animation, String label,
                          long start, long end, double x, double y, SkillMotion motion) throws IOException {
        String path = GOBLIN + animation + ".ani";
        if (end <= start || duration(path, lookup) == 0) return;
        var facing = new VisualStyle(null, 0, List.of(), Map.of(), true);
        visuals.add(new Visual(path, label, 3550, start, end, x, y, motion, 1, true, facing));
    }

    static int targetHeight(GameScript.Lookup lookup, ScenePreview.SpriteLookup sprites) throws IOException {
        String text = lookup.read(GOBLIN + "stay.ani");
        if (text == null || sprites == null) return 60;
        var frames = ResourceScript.animation(text).frames();
        if (frames.isEmpty()) return 60;
        var frame = frames.get(0);
        try {
            var trim = sprites.info(frame.image(), frame.index());
            return (int) Math.max(1, -(frame.y() + trim.y()) * frame.scaleY());
        } catch (IOException unavailable) {
            return 60;
        }
    }

    static int goblinHeight(GameScript.Lookup lookup) throws IOException {
        var box = GameScript.values(GameScript.fields(lookup.read(GOBLIN + "stay.ani")), "damage box");
        return box.size() < 6 ? 60 : Math.max(1, Math.min(1000, GameScript.integer(box.get(2), 0)
                + GameScript.integer(box.get(5), 60)));
    }

    private static long add(List<Visual> visuals, GameScript.Lookup lookup, String path, String label, int layer,
                            long start, long end, double x, double y, double scale, long fit) throws IOException {
        long life = duration(path, lookup);
        if (life == 0 || (end >= 0 && end <= start)) return start;
        long stop = end >= 0 ? end : start + life;
        var style = fit > 0 ? new VisualStyle(null, fit, List.of()) : VisualStyle.DEFAULT;
        visuals.add(new Visual(path, label, layer, start, stop, x, y, null, scale, true, style));
        return stop;
    }

    private static long particle(List<SkillParticleEmitter> emitters, GameScript.Lookup lookup, String path,
                                 String label, long start, long active, double x, double y, double z) throws IOException {
        String text = lookup.read(path);
        if (text == null) return start;
        emitters.add(new SkillParticleEmitter(path, label, start, active, 0, x, y, z, Double.NaN, 1));
        return start + active + SkillParticleEmitter.tail(ParticleScript.parse(text));
    }
}
