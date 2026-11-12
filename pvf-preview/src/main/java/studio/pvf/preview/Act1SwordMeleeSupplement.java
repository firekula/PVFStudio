package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Native Swordman chains with their corresponding authored actor layers. */
final class Act1SwordMeleeSupplement {
    private static final String SKILLS = "skill/swordman/";
    private static final String ROOT = "character/swordman/";
    private static final String EFFECT = ROOT + "effect/animation/";
    private static final String CHR = ROOT + "swordman.chr";
    private static final String HOP = SKILLS + "hopsmash.skl", HOP_BERSERKER = "hopsmash:berserker";
    private static final String TRIPLE = SKILLS + "tripleslash.skl";
    private static final String CRASH = SKILLS + "chargecrash.skl";
    private static final String GUARD = SKILLS + "guard.skl";
    private static final String GUARD_HOLD = "guard:hold";
    private static final int FRONT = 3501;
    private static final int BOTTOM = 280;

    private enum MasteryBranch {
        TRIPLE_KATANA(TRIPLE, "tripleslash:katana", 1, "太刀", 13, "blademastery", 0),
        TRIPLE_LIGHTSABER(TRIPLE, "tripleslash:lightsaber", 5, "光剑", 4, "lightswordmastery", 1),
        CRASH_HEAVY(CRASH, "chargecrash:heavy", 3, "巨剑", 14, "heavyswordmastery", 2),
        CRASH_BLUNT(CRASH, "chargecrash:blunt", 2, "钝器", 15, "bluntmastery", 1),
        GUARD_SHORT(GUARD, "guard:short", 0, "短剑", 12, "shortswordmastery", 1),
        GUARD_HEAVY(GUARD, "guard:heavy", 3, "巨剑", 14, "heavyswordmastery", 0);

        final String path, key, weapon, mastery;
        final int subtype, masteryId, thresholdColumn;

        MasteryBranch(String path, String key, int subtype, String weapon, int masteryId, String mastery, int thresholdColumn) {
            this.path = path;
            this.key = key;
            this.subtype = subtype;
            this.weapon = weapon;
            this.masteryId = masteryId;
            this.mastery = mastery;
            this.thresholdColumn = thresholdColumn;
        }
    }

    private Act1SwordMeleeSupplement() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup, Map<String, String> actions,
                                  int level, String weaponPath) throws IOException {
        return find(path, text, lookup, actions, level, weaponPath, "");
    }

    static List<GameEffectRecipes.Effect> choices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1 || !(path.equals(HOP) || path.equals(TRIPLE) || path.equals(CRASH) || path.equals(GUARD))) return List.of();
        var catalog = GameScript.catalog(lookup.read("skill/swordmanskill.lst"));
        int id = path.equals(HOP) ? 65 : path.equals(TRIPLE) ? 8 : path.equals(GUARD) ? 1 : 68;
        if (!path.equals("skill/" + catalog.get(id))) return List.of();
        if (path.equals(HOP)) {
            if (!hasBloodyVigorous(catalog) || hopShockwave(lookup).size() != 4) return List.of();
            return List.of(new GameEffectRecipes.Effect(HOP_BERSERKER, "崩山击 · 狂战士血色冲击波", "", path,
                    "已学习血气旺盛，着地时触发血色冲击波"));
        }
        var choices = new ArrayList<GameEffectRecipes.Effect>();
        if (path.equals(GUARD)) {
            choices.add(new GameEffectRecipes.Effect(GUARD_HOLD, "格挡 · 持续按住", "", path, "演示未受到攻击时的防御姿势"));
            if (guardWave(lookup).size() != 2) return List.copyOf(choices);
        }
        for (var branch : MasteryBranch.values()) {
            if (!branch.path.equals(path)) continue;
            int threshold = masteryThreshold(branch, catalog, lookup);
            if (threshold <= 0) continue;
            String suffix = path.equals(GUARD) ? " · 格挡冲击波" : path.equals(TRIPLE) ? " · 五段斩" : " · 满蓄下斩";
            String label = branch.weapon + "精通 Lv." + threshold + suffix;
            String note = path.equals(TRIPLE) ? "已学习武器奥义并达到武器精通门槛，连续输入五段斩"
                    : path.equals(GUARD) ? "已学习武器奥义，在有效窗口内挡住哥布林攻击，退步结束后反击"
                    : "上挑结束后持续按住技能键，满蓄释放强力下斩";
            choices.add(new GameEffectRecipes.Effect(branch.key, label, "", path, note));
        }
        return List.copyOf(choices);
    }

    static Integer masteryWeapon(String path, String choice) {
        var branch = masteryBranch(path, choice);
        return branch == null ? null : branch.subtype;
    }

    private static MasteryBranch masteryBranch(String path, String choice) {
        for (var branch : MasteryBranch.values()) {
            if (branch.path.equals(path) && branch.key.equals(choice)) return branch;
        }
        return null;
    }

    private static int masteryThreshold(MasteryBranch branch, Map<Integer, String> catalog,
                                        GameScript.Lookup lookup) throws IOException {
        String file = branch.mastery + ".skl";
        if (!("swordman/" + file).equals(catalog.get(branch.masteryId))) return -1;
        if ((branch.path.equals(TRIPLE) || branch.path.equals(GUARD))
                && (!"swordman/weaponmasteryup.skl".equals(catalog.get(27))
                || lookup.read(SKILLS + "weaponmasteryup.skl") == null)) return -1;
        return Act1SkillSequence.staticValue(GameScript.fields(lookup.read(SKILLS + file)), branch.thresholdColumn, -1);
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup, Map<String, String> actions,
                                  int level, String weaponPath, String choice) throws IOException {
        int id = switch (path) {
            case GUARD -> 1;
            case SKILLS + "hitback.skl" -> 7;
            case TRIPLE -> 8;
            case SKILLS + "upperslash.skl" -> 46;
            case HOP -> 65;
            case CRASH -> 68;
            case SKILLS + "moonlightslash.skl" -> 77;
            case SKILLS + "moonlightslashfull.skl" -> 80;
            default -> -1;
        };
        if (id < 0) return null;
        var catalog = GameScript.catalog(lookup.read("skill/swordmanskill.lst"));
        if (!path.equals("skill/" + catalog.get(id))) return null;
        if (id == 1) {
            var guardBranch = masteryBranch(path, choice);
            if (guardBranch != null && masteryThreshold(guardBranch, catalog, lookup) <= 0) return null;
            return guard(GameScript.fields(text), lookup, actions, level, choice, guardBranch != null);
        }
        boolean berserker = path.equals(HOP) && choice.equals(HOP_BERSERKER) && hasBloodyVigorous(catalog);
        var branch = masteryBranch(path, choice);
        if (!choice.isEmpty() && !berserker && (branch == null || masteryThreshold(branch, catalog, lookup) <= 0)) return null;
        return switch (id) {
            case 7 -> hitBack(lookup, actions);
            case 8 -> tripleSlash(GameScript.fields(text), lookup, actions, branch == null ? 3 : 5);
            case 46 -> upperSlash(lookup, actions, weaponType(lookup, weaponPath));
            case 65 -> hopSmash(GameScript.fields(text), lookup, actions, weaponType(lookup, weaponPath), berserker);
            case 68 -> chargeCrash(GameScript.fields(text), lookup, actions,
                    branch == null ? weaponType(lookup, weaponPath) : branch.subtype, branch != null);
            case 77 -> moonlight(text, lookup, actions, false);
            case 80 -> (SKILLS + "moonlightslash.skl").equals("skill/" + catalog.get(77))
                    ? moonlight(lookup.read(SKILLS + "moonlightslash.skl"), lookup, actions, true) : null;
            default -> null;
        };
    }

    private static boolean hasBloodyVigorous(Map<Integer, String> catalog) {
        return "swordman/bloodyvigorous.skl".equals(catalog.get(63));
    }

    /** State 15 switches the authored guard frame and creates the impact spark and stopping dust. */
    private static Act1SkillSequence guard(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                          Map<String, String> actions, int level, String choice, boolean counter) throws IOException {
        String action = "etc motion:0";
        if (body(lookup, actions, action).size() < 2) return null;
        int minimum = Act1SkillSequence.staticValue(fields, 0, 500);
        if (minimum < 1 || minimum > 5000) return null;
        if (choice.equals(GUARD_HOLD)) {
            return sequence(List.of(new Pose(action, 0, minimum, null, false, null, 0, 1)),
                    List.of(), "按原版最短防御时间展示未受击的格挡姿势");
        }
        if (!choice.isEmpty() && !counter) return null;

        String attack = lookup.read(Act1SwordAsuraSupplement.GOBLIN + "attack1.ani");
        if (attack == null) return null;
        var frames = ResourceScript.animation(attack).frames();
        if (frames.size() < 4 || !actions.containsKey("waiting motion")) return null;
        long attackStart = 100;
        long impact = attackStart + duration(frames, 3);
        long attackEnd = attackStart + duration(frames, frames.size());
        int speed = Act1SkillSequence.levelValue(fields, level, 0, 0, 1, 5000);
        int recoil = Act1SkillSequence.levelValue(fields, level, 1, 0, 1, 5000);
        if (speed <= 0 || recoil <= 0) return null;
        long stopped = impact + recoil;
        long dustAt = impact + Math.max(1, (long) Math.floor(recoil * (1.0 - 40.0 / speed)) + 1);
        var pushback = new SkillMotion(impact, recoil, -speed, 1000.0 * speed / recoil);
        var visuals = new ArrayList<Visual>();
        if (!add(lookup, visuals, EFFECT + "guard/spark.ani", "格挡 · 受击火花", FRONT, impact,
                Long.MAX_VALUE, 23, -45, null, 1)) return null;
        if (!add(lookup, visuals, EFFECT + "guard/dust.ani", "格挡 · 退步扬尘", BOTTOM, dustAt,
                Long.MAX_VALUE, -45 + pushback.offsetAt(dustAt), 0, null, 1)) return null;
        long dustEnd = visuals.get(visuals.size() - 1).end();

        var poses = new ArrayList<Pose>();
        poses.add(new Pose(action, 0, impact, null, false, null, 0, 1));
        double targetX = 42;
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "格挡 · 哥布林", 0, attackStart, targetX, null);
        if (counter) {
            var wave = guardWave(lookup);
            int advanceTime = Act1SkillSequence.staticValue(fields, 2, 100);
            int advanceSpeed = Act1SkillSequence.staticValue(fields, 3, 400);
            if (wave.size() != 2 || advanceTime < 1 || advanceTime > 5000 || advanceSpeed > 5000) return null;
            double origin = pushback.offsetAt(stopped);
            if (!add(lookup, visuals, wave.get(1), "格挡 · 冲击波扬尘", FRONT, stopped, Long.MAX_VALUE, origin, 0, null, 1)
                    || !add(lookup, visuals, wave.get(0), "格挡 · 精通冲击波", FRONT + 1, stopped, Long.MAX_VALUE, origin, 0, null, 1)) return null;
            long released = stopped + advanceTime + 100L;
            long end = Math.max(released + 150, visuals.get(visuals.size() - 1).end());
            var advance = new SkillMotion(stopped, advanceTime, advanceSpeed, -1000.0 * advanceSpeed / advanceTime, origin);
            poses.add(new Pose(action, impact, stopped, null, false, pushback, 1, 2));
            poses.add(new Pose(action, stopped, released, null, false, advance, 0, 1));
            poses.add(new Pose("waiting motion", released, end, null, false, advance));
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "attack1", "格挡 · 哥布林攻击", attackStart, stopped, targetX, null);
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", "格挡 · 冲击波命中", stopped, end, targetX, null);
            return sequence(poses, visuals, "在武器精通的有效时间内格挡正面攻击，退步结束后生成原版冲击波，再向前回位");
        }

        long release = Math.max(minimum, stopped);
        long end = Math.max(release + 200, Math.max(attackEnd, dustEnd));
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "attack1", "格挡 · 哥布林攻击", attackStart, attackEnd, targetX, null);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "格挡 · 哥布林", attackEnd, end, targetX, null);
        poses.add(new Pose(action, impact, release, null, false, pushback, 1, 2));
        poses.add(new Pose("waiting motion", release, end, null, false, pushback));
        return sequence(poses, visuals,
                "用哥布林一次正面攻击演示受击帧、格挡火花和后退扬尘，命中高度取客户端允许范围内的 45 像素");
    }

    private static List<String> guardWave(GameScript.Lookup lookup) throws IOException {
        String object = GameEffectRecipes.passiveObject(lookup, 20039);
        if (!("passiveobject/character/swordman/guardwave.obj".equals(object))) return List.of();
        var fields = GameScript.fields(lookup.read(object));
        String wave = GameScript.resolve(object, GameScript.value(fields, "basic motion"), lookup);
        var layers = GameScript.values(fields, "add object effect");
        String dust = layers.size() == 3 ? GameScript.resolve(object, layers.get(1), lookup) : null;
        return wave != null && dust != null ? List.of(wave, dust) : List.of();
    }

    /** State 38 accepts successive inputs from body frame three; its first trail starts on frame two. */
    private static Act1SkillSequence moonlight(String text, GameScript.Lookup lookup, Map<String, String> actions,
                                                boolean full) throws IOException {
        if (text == null) return null;
        var fields = GameScript.fields(text);
        String[] bodyActions = {"etc motion:61", "etc motion:62", "etc motion:65"};
        String[] clips = {"moonlightslash1.ani", "moonlightslash2.ani", "moonlightslashfull.ani"};
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        long start = 0;
        double x = 0;
        int stages = full ? 3 : 2;
        for (int i = 0; i < stages; i++) {
            var body = body(lookup, actions, bodyActions[i]);
            if (body.size() <= 3) return null;
            boolean next = i + 1 < stages;
            long end = start + duration(body, next ? 3 : body.size());
            var motion = slide(fields, start, end, x, 200, -500);
            poses.add(new Pose(bodyActions[i], start, end, null, false, motion));
            long effectStart = start + (i == 0 ? duration(body, 2) : 0);
            String label = i == 2 ? "满月斩 · 双手升斩" : "月光斩 · 第" + (i + 1) + "段刀光";
            if (!add(lookup, visuals, EFFECT + clips[i], label, FRONT, effectStart, end, 0, 0, motion, 1)) return null;
            x = motion.offsetAt(end);
            start = end;
        }
        return sequence(poses, visuals, full
                ? "满月斩在月光斩两段后接原版双手升斩；按可接续的最早时刻输入"
                : "月光斩演示两段斩击和原版减速滑行；按可接续的最早时刻输入");
    }

    /** State 18 selects three or five actor motions and alternates the two movement layers. */
    private static Act1SkillSequence tripleSlash(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                  Map<String, String> actions, int stages) throws IOException {
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        long start = 0;
        double x = 0;
        for (int i = 0; i < stages; i++) {
            String action = "etc motion:" + (3 + i);
            var body = body(lookup, actions, action);
            if (body.size() <= 3) return null;
            long end = start + duration(body, i + 1 < stages ? 3 : body.size());
            var motion = slide(fields, start, end, x, 600, -1000);
            poses.add(new Pose(action, start, end, null, false, motion));
            if (!add(lookup, visuals, EFFECT + "tripleslash/slash" + (i + 1) + ".ani", "三段斩 · 刀光", FRONT + 1,
                    start, end, 0, 0, motion, 1)) return null;
            if (!add(lookup, visuals, EFFECT + "tripleslash/move" + (i % 2 + 1) + ".ani", "三段斩 · 前冲轨迹", FRONT,
                    start, end, 0, 0, motion, 1)) return null;
            x = motion.offsetAt(end);
            start = end;
        }
        return sequence(poses, visuals, stages == 5 ? "按武器奥义和精通门槛演示原版五段斩及每段减速前移"
                : "三段斩演示普通三段和每段减速前移");
    }

    /** State 24 forwards attack index two; 0x643D00 adds UpperSlash1 on body frame three. */
    private static Act1SkillSequence upperSlash(GameScript.Lookup lookup, Map<String, String> actions,
                                                 int weaponType) throws IOException {
        String action = "attack motion:2";
        var body = body(lookup, actions, action);
        if (body.size() <= 3) return null;
        long end = duration(body, body.size()), start = duration(body, 3);
        double scale = switch (weaponType) { case 0 -> .8; case 3 -> 1; case 5 -> 1.1; default -> .9; };
        var visuals = new ArrayList<Visual>();
        if (!add(lookup, visuals, EFFECT + "upperslash1.ani", "上挑 · 刀光", FRONT, start, end,
                weaponType == 0 ? 5 : 0, weaponType == 0 ? -18 : 0, null, scale)) return null;
        return sequence(List.of(new Pose(action, 0, end)), visuals,
                "上挑按当前武器类别绘制第一段刀光；短剑精通追加上挑需要实战条件");
    }

    /** State 33 switches to its uppercut at the end of the dash and attaches the sword trail on frame one. */
    private static Act1SkillSequence chargeCrash(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                  Map<String, String> actions, int weaponType,
                                                  boolean chargedSlam) throws IOException {
        String dash = "etc motion:43", upper = "etc motion:44";
        var dashBody = body(lookup, actions, dash);
        var upperBody = body(lookup, actions, upper);
        if (dashBody.size() <= 3 || upperBody.size() <= 1) return null;
        long dashEnd = duration(dashBody, dashBody.size());
        long end = dashEnd + duration(upperBody, upperBody.size());
        var motion = slide(fields, 0, dashEnd, 0, 700, -1500);
        var stop = new SkillMotion(dashEnd, 0, 0, 0, motion.offsetAt(dashEnd));
        var visuals = new ArrayList<Visual>();
        double scale = weaponType == 3 ? 1.3 : weaponType == 0 ? 1.2 : 1.25;
        String prefix = EFFECT + "chargecrash/";
        if (!add(lookup, visuals, prefix + "dash.ani", "破军升龙击 · 肩撞", FRONT, 0, dashEnd, 0, 0, motion, scale)) return null;
        if (!add(lookup, visuals, prefix + "dustdash.ani", "破军升龙击 · 前冲扬尘", FRONT + 1, 0, dashEnd, 0, 0, motion, 1)) return null;
        if (!add(lookup, visuals, prefix + "dustdashlast.ani", "破军升龙击 · 刹停扬尘", FRONT + 2,
                duration(dashBody, 3), dashEnd, 0, 0, motion, 1)) return null;
        if (!add(lookup, visuals, prefix + "up-slash.ani", "破军升龙击 · 上挑刀光", FRONT,
                dashEnd + upperBody.get(0).delay(), end, 0, 0, stop, 1)) return null;
        var poses = new ArrayList<>(List.of(new Pose(dash, 0, dashEnd, null, false, motion),
                new Pose(upper, dashEnd, end, null, false, stop)));
        if (!chargedSlam) return sequence(poses, visuals, "破军升龙击演示肩撞减速后接上挑");

        // State 33 stage two holds frame 0 while charging; stage three selects frame 3 directly.
        String finish = "etc motion:45";
        var finishBody = body(lookup, actions, finish);
        var shock = Act1SkillSequence.objectMotions(lookup, 20030);
        if (finishBody.size() <= 3 || shock.size() != 2) return null;
        long chargeTime = Math.max(1, Act1SkillSequence.staticValue(fields, 3, 600));
        long impact = end + chargeTime;
        long finishTime = impact + duration(finishBody, finishBody.size()) - duration(finishBody, 3);
        poses.add(new Pose(finish, end, impact, null, false, stop, 0, 1));
        poses.add(new Pose(finish, impact, finishTime, null, false, stop, 3));
        String charge = prefix + "charge.ani";
        var chargeFrames = ResourceScript.animation(lookup.read(charge)).frames();
        if (chargeFrames.isEmpty()) return null;
        var timing = new LinkedHashMap<Integer, FrameOverride>();
        int chargeDelay = Math.max(1, (int) (chargeTime / chargeFrames.size()));
        for (int i = 0; i < chargeFrames.size(); i++) timing.put(i, new FrameOverride(0, 0, chargeDelay));
        visuals.add(new Visual(charge, "破军升龙击 · 下斩蓄力", FRONT, end, impact, -55, -130, stop, 1, true,
                new VisualStyle(null, 0, List.of(), timing)));
        if (!add(lookup, visuals, prefix + "down-slash.ani", "破军升龙击 · 强力下斩", FRONT,
                impact, Long.MAX_VALUE, 135, 10, stop, 1)) return null;
        for (int i = 0; i < shock.size(); i++) {
            if (!add(lookup, visuals, shock.get(i), "破军升龙击 · 满蓄地面冲击", BOTTOM + i,
                    impact, Long.MAX_VALUE, 120, 0, stop, 1)) return null;
        }
        long visualEnd = visuals.stream().mapToLong(Visual::end).max().orElse(finishTime);
        if (visualEnd > finishTime && actions.containsKey("waiting motion")) {
            poses.add(new Pose("waiting motion", finishTime, visualEnd, null, false, stop));
        }
        return sequence(poses, visuals, "破军升龙击按当前武器精通演示肩撞、上挑和满蓄强力下斩");
    }

    /** State 32 eases to its configured apex at frame two and lands on frame four. */
    private static Act1SkillSequence hopSmash(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                               Map<String, String> actions, int weaponType, boolean berserker) throws IOException {
        String ready = "etc motion:26", hop = "etc motion:27";
        var frames = body(lookup, actions, hop);
        if (body(lookup, actions, ready).isEmpty() || frames.size() <= 4) return null;
        long takeoff = Math.max(1, Act1SkillSequence.staticValue(fields, 0, 30));
        double speed = Math.max(100, Act1SkillSequence.staticValue(fields, 6, 150)) / 100.0;
        long riseTime = Math.max(1, Math.round(duration(frames, 2) / speed));
        long descentTime = Math.max(1, Math.round((duration(frames, 4) - duration(frames, 2)) / speed));
        long apex = takeoff + riseTime;
        long landing = apex + descentTime;
        long end = landing + Math.max(1, Math.round((duration(frames, frames.size()) - duration(frames, 4)) / speed));
        int height = Act1SkillSequence.staticValue(fields, 4, 50);
        double groundX = .8 * Act1SkillSequence.staticValue(fields, 2, 50);
        double riseSeconds = riseTime / 1000.0, descentSeconds = descentTime / 1000.0;
        var rise = new SkillMotion(takeoff, riseTime, 0, 0, 2 * height / riseSeconds, -2 * height / (riseSeconds * riseSeconds));
        var fall = new SkillMotion(apex, descentTime, 0, 0, 0, -2 * height / (descentSeconds * descentSeconds), height, null, groundX);
        var ground = new SkillMotion(landing, 0, 0, 0, groundX);
        var poses = new ArrayList<>(List.of(new Pose(ready, 0, takeoff),
                new Pose(hop, takeoff, apex, null, true, rise, 0, 2),
                new Pose(hop, apex, landing, null, true, fall, 2, 4),
                new Pose(hop, landing, end, null, true, ground, 4)));
        var visuals = new ArrayList<Visual>();
        double width = weaponType == 3 ? 1.2 : 1;
        String sword = EFFECT + "hopsmash/sword.ani", smash = EFFECT + "hopsmash/smash.ani";
        long slash = takeoff + Math.round(duration(frames, 3) / speed);
        if (!hopClip(lookup, visuals, sword, "崩山击 · 下斩刀光", FRONT, 1, Integer.MAX_VALUE, apex, landing, speed, fall, width)
                || !hopClip(lookup, visuals, smash, "崩山击 · 着地刀光", FRONT + 1, 1, 2, slash, landing, speed, fall, width)
                || !hopClip(lookup, visuals, smash, "崩山击 · 着地冲击", FRONT + 1, 2, Integer.MAX_VALUE, landing, end, speed, ground, width)) return null;
        if (!berserker) return sequence(poses, visuals, "崩山击演示短按的小跳、下斩和着地刀光。");

        var shock = hopShockwave(lookup);
        if (shock.size() != 4) return null;
        // State 32 sends the short-charge radius to #20050. 0x5627D0 scales all four ANI by radius / 292.
        double scale = Math.max(1, Act1SkillSequence.staticValue(fields, 10, 300)) / 292.0;
        long shockEnd = landing + new AnimationTimeline(ResourceScript.animation(lookup.read(shock.get(0)))).duration();
        if (shockEnd <= landing) return null;
        for (int index = 0; index < shock.size(); index++) {
            boolean rear = index >= 2;
            int layer = BOTTOM + (rear ? index - 2 : index + 2);
            // 0x562A10 lowers the rear pair's world Y and Z together. Keep both pairs on the ground.
            if (!add(lookup, visuals, shock.get(index), "崩山击 · 血色冲击波" + (rear ? "后层" : "前层") + (index % 2 + 1),
                    layer, landing, shockEnd, 123, .29, ground, scale)) return null;
        }
        if (shockEnd > end && actions.containsKey("waiting motion")) {
            poses.add(new Pose("waiting motion", end, shockEnd, null, false, ground));
        }
        return sequence(poses, visuals, "已学习血气旺盛的狂战士短按崩山击，着地时触发原版四层血色冲击波。");
    }

    private static List<String> hopShockwave(GameScript.Lookup lookup) throws IOException {
        String object = GameEffectRecipes.passiveObject(lookup, 20050);
        if (object == null) return List.of();
        var fields = GameScript.fields(lookup.read(object));
        var attachments = GameScript.values(fields, "add object effect");
        if (attachments.size() != 3 || !attachments.get(0).equals("1") || !attachments.get(2).equals("1")) return List.of();
        var layers = new ArrayList<String>();
        for (String reference : List.of(GameScript.value(fields, "basic motion"), attachments.get(1),
                "Animation/HopSmashSubBack1.ani", "Animation/HopSmashSubBack2.ani")) {
            String path = GameScript.resolve(object, reference, lookup);
            if (path == null) return List.of();
            layers.add(path);
        }
        return List.copyOf(layers);
    }

    private static boolean hopClip(GameScript.Lookup lookup, List<Visual> visuals, String path, String label, int layer,
                                   int first, int last, long start, long limit, double speed, SkillMotion motion,
                                   double width) throws IOException {
        String text = lookup.read(path);
        if (text == null) return false;
        var frames = ResourceScript.animation(text).frames();
        var overrides = new LinkedHashMap<Integer, FrameOverride>();
        long time = 0;
        for (int i = 0; i < frames.size(); i++) {
            int delay = i < first || i >= last ? 0 : Math.max(1, (int) Math.round(frames.get(i).delay() / speed));
            overrides.put(i, new FrameOverride(0, 0, delay, width, 1));
            time += delay;
        }
        long end = Math.min(limit, start + time);
        if (end <= start) return false;
        var style = new VisualStyle(null, 0, List.of(), overrides);
        visuals.add(new Visual(path, label, layer, start, end, 0, 0, motion, 1, true, style));
        return true;
    }

    /** The counter state selects HitBack after an eligible rear strike and makes the actor face the attacker. */
    private static Act1SkillSequence hitBack(GameScript.Lookup lookup, Map<String, String> actions) throws IOException {
        String action = "etc motion:2";
        var body = body(lookup, actions, action);
        if (body.isEmpty()) return null;
        var pose = new Pose(action, 0, duration(body, body.size()), null, false, null, 0, Integer.MAX_VALUE, true);
        return sequence(List.of(pose), List.of(), "逆转反击演示背后受击触发后，转身使用原版反击姿势");
    }

    private static SkillMotion slide(List<GameScript.Field> fields, long start, long end, double x,
                                     int stockSpeed, int stockAcceleration) {
        int speed = Act1SkillSequence.staticValue(fields, 0, stockSpeed);
        var config = GameScript.values(fields, "static data");
        int acceleration = config.size() > 1 ? GameScript.integer(config.get(1), stockAcceleration) : stockAcceleration;
        long travel = acceleration < 0 ? Math.min(end - start, Math.round(-1000.0 * speed / acceleration)) : end - start;
        return new SkillMotion(start, travel, speed, acceleration, x);
    }

    private static List<ResourceScript.Frame> body(GameScript.Lookup lookup, Map<String, String> actions, String action) throws IOException {
        return Act1SkillSequence.frames(CHR, actions.get(action), lookup);
    }

    private static long duration(List<ResourceScript.Frame> frames, int count) {
        return Act1SkillSequence.duration(frames, count, null);
    }

    private static boolean add(GameScript.Lookup lookup, List<Visual> visuals, String path, String label, int layer,
                               long start, long cap, double x, double y, SkillMotion motion, double scale) throws IOException {
        String text = lookup.read(path);
        if (text == null) return false;
        long life = new AnimationTimeline(ResourceScript.animation(text)).duration();
        long end = Math.min(start + life, cap);
        if (end <= start) return false;
        visuals.add(new Visual(path, label, layer, start, end, x, y, motion, scale, true));
        return true;
    }

    private static int weaponType(GameScript.Lookup lookup, String path) throws IOException {
        if (path == null || path.isEmpty()) return 0;
        var fields = GameScript.fields(lookup.read(GameScript.normalize(path)));
        return GameScript.flag(GameScript.value(fields, "equipment type")).equals("weapon")
                ? GameScript.integer(GameScript.value(fields, "sub type"), 0) : 0;
    }

    private static Act1SkillSequence sequence(List<Pose> poses, List<Visual> visuals, String note) {
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(), note);
    }
}
