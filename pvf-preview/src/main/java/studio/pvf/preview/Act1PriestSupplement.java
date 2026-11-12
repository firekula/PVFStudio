package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Priest combat states with body and effect clocks authored separately. */
final class Act1PriestSupplement {
    private static final String SKILLS = "skill/priest/";
    private static final String THUNDER_TALISMAN = SKILLS + "thunderbolttalisman.skl";
    private static final String THUNDER_MISS = "thunderbolttalisman:miss";
    private static final String CHR = "character/priest/priest.chr";
    private static final String EFFECT = "character/priest/effect/animation/";
    private static final String SMASH = EFFECT + "repeatedsmash/";
    private static final String IDLE = "waiting motion";
    private static final String SMASHER = SKILLS + "smasher.skl";
    private static final String SMASHER_MISS = "smasher:miss";
    private static final int FRONT = 3501, FLOOR = 300;

    private Act1PriestSupplement() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level) throws IOException {
        return find(path,text,lookup,actions,level,null);
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level,String weaponPath) throws IOException {
        return find(path, text, lookup, actions, level, weaponPath, "");
    }

    static List<GameEffectRecipes.Effect> thunderChoices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1 || !path.equals(THUNDER_TALISMAN)
                || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/priestskill.lst")).get(40))) return List.of();
        return List.of(new GameEffectRecipes.Effect(THUNDER_MISS, "落雷符 · 空场", "", path, "显示落符、法阵与到期消散"));
    }

    static GameEffectRecipes.Effect smasherChoice(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1 || !SMASHER.equals(path) || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/priestskill.lst")).get(8)))
            return null;
        return new GameEffectRecipes.Effect(SMASHER_MISS, "虎袭 · 未抓住", "", path, "抓取未命中时只播放起手动作");
    }

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level, String weaponPath, String choice) throws IOException {
        if (!choice.isEmpty() && !(SMASHER.equals(path) && SMASHER_MISS.equals(choice))
                && !(THUNDER_TALISMAN.equals(path) && THUNDER_MISS.equals(choice))
                && !Act1PriestSummon.matchesChoice(path, choice)) return null;
        int id = switch (path) {
            case SKILLS + "ducking.skl" -> 2;
            case SKILLS + "sway.skl" -> 3;
            case SKILLS + "hurricaneroll.skl" -> 4;
            case SKILLS + "giantswing.skl" -> 5;
            case SMASHER -> 8;
            case SKILLS + "quakearea.skl" -> 11;
            case SKILLS + "willdriver.skl" -> 13;
            case SKILLS + "holycounter.skl" -> 30;
            case SKILLS + "gorgeouscombination.skl" -> 31;
            case SKILLS + "hurricaneofjudgement.skl" -> 37;
            case SKILLS + "oppressiontalisman.skl" -> 39;
            case THUNDER_TALISMAN -> 40;
            case SKILLS + "repeatedsmash.skl" -> 49;
            case SKILLS + "throwweapon.skl" -> 50;
            case SKILLS + "duckingstraight.skl" -> 56;
            case SKILLS + "duckingbodyblow.skl" -> 57;
            case SKILLS + "duckingupper.skl" -> 58;
            case SKILLS + "luckystraight.skl" -> 61;
            case SKILLS + "secondupper.skl" -> 63;
            case SKILLS + "hyunmoo.skl" -> 69;
            case SKILLS + "baekho.skl" -> 70;
            default -> -1;
        };
        if (id < 0 || !path.equals("skill/" + GameScript.catalog(lookup.read("skill/priestskill.lst")).get(id))) return null;
        var fields = GameScript.fields(text);
        return switch (id) {
            case 2, 3 -> dodge(id, fields, lookup, actions, level);
            case 4 -> hurricaneRoll(fields, lookup, actions, level);
            case 5 -> giantSwing(fields, lookup, actions, level);
            case 8 -> smasher(fields, lookup, actions, SMASHER_MISS.equals(choice));
            case 11 -> quakeArea(fields, lookup, actions, weaponPath);
            case 13 -> willDriver(fields,lookup,actions,weaponPath);
            case 30 -> holyCounter(fields, lookup, actions);
            case 31 -> combination(lookup, actions);
            case 37 -> judgement(fields, lookup, actions, level);
            case 39, 40 -> talisman(id, fields, lookup, actions, level, !THUNDER_MISS.equals(choice));
            case 49 -> repeatedSmash(fields, lookup, actions, level);
            case 50 -> throwWeapon(fields, lookup, actions);
            case 56, 61 -> straight(id, fields, lookup, actions);
            case 69, 70 -> Act1PriestSummon.find(id, fields, lookup, actions, level, choice);
            default -> shortPunch(id, fields, lookup, actions);
        };
    }

    /** State 23 plants object 24002 on frame three. Its skin selects the matching native weapon layers. */
    private static Act1SkillSequence willDriver(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                               Map<String,String> actions,String weaponPath) throws IOException {
        String action="etc motion:12";
        var frames=body(lookup,actions,action);
        if(frames.size()<4||!"character/priest/willdriver.obj".equals(
                GameScript.catalog(lookup.read("passiveobject/passiveobject.lst")).get(24002)))return null;
        int weapon=0;
        if(weaponPath!=null&&!weaponPath.isEmpty()){
            var equipment=GameScript.fields(lookup.read(GameScript.normalize(weaponPath)));
            if(!"weapon".equals(GameScript.flag(GameScript.value(equipment,"equipment type"))))return null;
            weapon=GameScript.integer(GameScript.value(equipment,"sub type"),-1);
            if(weapon<0||weapon>4)return null;
        }
        String path="passiveobject/character/priest/animation/willdriver"+weapon+".ani";
        String clip=lookup.read(path);
        if(clip==null)return null;
        long plant=before(frames,3);
        long recovery=Act1SkillSequence.duration(frames,frames.size(),300);
        long end=Math.max(recovery+150,plant+new AnimationTimeline(ResourceScript.animation(clip)).duration());
        double scale=Act1SkillSequence.staticValue(fields,3,500)*0.0041152262;
        if(scale<=0||scale>20)return null;
        int[] layers=switch(weapon){case 3->new int[]{0,1,2,3};case 4->new int[]{0,1,2};default->new int[]{1,3};};
        var visuals=new ArrayList<Visual>();
        for(int layer:layers)visuals.add(new Visual(path,"意念驱动 · 武器插地",layer<2?FLOOR+layer:FRONT+layer,
                plant,end,37,7,null,scale,true,VisualStyle.DEFAULT,List.of(weapon,layer)));
        var poses=new ArrayList<Pose>();
        poses.add(new Pose(action,0,recovery,300,false,null));
        idle(poses,actions,recovery,end,null);
        return sequence(poses,visuals,"意念驱动演示插地及武器类别对应的原版图层；无搭配武器时用十字架图层，收回和变更攻击动作由实战提供");
    }

    /** State 20 uses the opening swing at normal speed, then rewinds it for each input-rate-scaled repetition. */
    private static Act1SkillSequence repeatedSmash(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                   Map<String, String> actions, int level) throws IOException {
        String repeat = "etc motion:28", finish = "etc motion:29";
        var repeatFrames = body(lookup, actions, repeat);
        var finalFrames = body(lookup, actions, finish);
        if (repeatFrames.size() <= 5 || finalFrames.size() <= 5) return null;
        long repeatDuration = duration(repeatFrames), finalDuration = duration(finalFrames);
        if (repeatDuration <= 0 || finalDuration <= 0) return null;
        int minimum = Act1SkillSequence.levelValue(fields, level, 2, 2, 1, 12);
        int maximum = Math.max(minimum, Act1SkillSequence.levelValue(fields, level, 3, 4, 1, 12));
        int speedCap = Math.max(100, Act1SkillSequence.staticValue(fields, 1, 150));
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        long start = 0, end = 0;
        for (int index = 0; index < maximum; index++) {
            // A representative series of taps ramps from the middle of the input range to its cap.
            double fraction = index == 0 ? 0 : (index + 1) / (double) maximum;
            long stage = Math.max(1, Math.round(repeatDuration * 100 / (100 + (speedCap - 100) * fraction)));
            poses.add(new Pose(repeat, start, start + stage, null, true, null));
            String swing = index == 0 ? "swingstart.ani" : "swingrepeat.ani";
            linked(lookup, visuals, SMASH + swing, "狂乱锤击 · 挥击", FRONT, start, repeatFrames, stage, null);
            long impact = start + scaledFrame(repeatFrames, 5, stage);
            end = Math.max(end, add(lookup, visuals, SMASH + "crashrepeat1.ani", "狂乱锤击 · 地面冲击", FLOOR, impact, -1, 133, 0, null));
            end = Math.max(end, add(lookup, visuals, SMASH + "crashrepeat2.ani", "狂乱锤击 · 锤击飞尘", FLOOR + 1, impact, -1, 133, 0, null));
            start += stage;
        }
        poses.add(new Pose(finish, start, start + finalDuration));
        linked(lookup, visuals, SMASH + "swingfinal.ani", "狂乱锤击 · 终结挥击", FRONT, start, finalFrames, finalDuration, null);
        end = Math.max(end, add(lookup, visuals, SMASH + "charge.ani", "狂乱锤击 · 终结蓄力", FRONT + 1, start,
                start + finalDuration, 0, 0, null));
        long finalImpact = start + before(finalFrames, 5);
        var finalClips = Act1SkillSequence.objectMotions(lookup, 24015);
        for (int layer = 0; layer < finalClips.size(); layer++) {
            int drawLayer = switch (layer) { case 0 -> 3600; case 1 -> FLOOR; default -> 3601; };
            end = Math.max(end, add(lookup, visuals, finalClips.get(layer), "狂乱锤击 · 终结冲击波", drawLayer,
                    finalImpact, -1, 133, 0, null));
        }
        if (visuals.isEmpty()) return null;
        long recovery = start + finalDuration;
        idle(poses, actions, recovery, Math.max(end, recovery + 150), null);
        return sequence(poses, visuals, "狂乱锤击演示连续按键加速至当前技能的最大挥击次数，再以常速终结；冲击落点使用客户端第一种武器位置");
    }

    /** States 17/18 cover forward and backward movement and leave their dust at body frame one. */
    private static Act1SkillSequence dodge(int id, List<GameScript.Field> fields, GameScript.Lookup lookup,
                                           Map<String, String> actions, int level) throws IOException {
        boolean forward = id == 2;
        String label = forward ? "俯冲" : "摆动";
        String action = forward ? "etc motion:2" : "etc motion:3";
        var frames = body(lookup, actions, action);
        if (frames.size() < 2) return null;
        long travel = Act1SkillSequence.levelValue(fields, level, 2, 500, 1, 5000);
        long recovery = travel + Act1SkillSequence.staticValue(fields, 0, 0);
        int distance = Act1SkillSequence.levelValue(fields, level, 0, 150, 0, 3000) * (forward ? 1 : -1);
        double seconds = travel / 1000.0;
        var motion = new SkillMotion(0, travel, 2 * distance / seconds, -2 * distance / (seconds * seconds));
        long start = frames.get(0).delay();
        var visuals = new ArrayList<Visual>();
        long end = add(lookup, visuals, EFFECT + (forward ? "ducking.ani" : "sway.ani"), label + " · 残影",
                3500, start, -1, motion.offsetAt(start), 0, null);
        if (visuals.isEmpty()) return null;
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(action, 0, recovery, null, false, motion));
        idle(poses, actions, recovery, Math.max(end, recovery + 150), motion);
        return sequence(poses, visuals, label + "按脚本距离和时间演示无纵向输入时的移动，残影从动作第 2 帧的位置出现");
    }

    /** State 33 rewinds the hook, replacing its opening effect with HookLoop, before switching to the uppercut. */
    private static Act1SkillSequence hurricaneRoll(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                   Map<String, String> actions, int level) throws IOException {
        String hook = "etc motion:26", upper = "etc motion:27";
        long hookTime = duration(body(lookup, actions, hook));
        long upperTime = duration(body(lookup, actions, upper));
        if (hookTime <= 0 || upperTime <= 0) return null;
        int cycles = Act1SkillSequence.levelValue(fields, level, 2, 5, 1, 16);
        long finish = hookTime * cycles;
        String prefix = EFFECT + "hurricaneroll/";
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        for (int i = 0; i < cycles; i++) {
            long start = i * hookTime;
            poses.add(new Pose(hook, start, start + hookTime));
            add(lookup, visuals, prefix + (i == 0 ? "hookstart.ani" : "hookloop.ani"),
                    "极速飓风拳 · 连续勾拳", FRONT, start, start + hookTime, 0, 0, null);
        }
        double scale = Act1SkillSequence.levelValue(fields, level, 3, 480, 1, 3000) / 480.0;
        for (int i = 1; i <= 2; i++) {
            String area = prefix + "area" + i + ".ani";
            if (lookup.read(area) != null) visuals.add(new Visual(area, "极速飓风拳 · 牵引气流", 490 + i, 0, finish, 0, -50, null, scale, true));
        }
        poses.add(new Pose(upper, finish, finish + upperTime));
        long end = add(lookup, visuals, prefix + "upper.ani", "极速飓风拳 · 终结上勾拳", FRONT, finish, -1, 0, 0, null);
        idle(poses, actions, finish + upperTime, Math.max(end, finish + upperTime + 150), null);
        if (visuals.isEmpty()) return null;
        return sequence(poses, visuals, "极速飓风拳演示原地持续输入时的最大勾拳轮数与终结上勾拳；方向输入和目标牵引由实战提供");
    }

    /** State 22 reads the equipped weapon attack box on frame four, then emits #24001 at landing. */
    private static Act1SkillSequence quakeArea(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                               Map<String, String> actions, String weaponPath) throws IOException {
        String action = "etc motion:11";
        var frames = body(lookup, actions, action);
        if (frames.size() < 6) return null;
        var data = GameScript.values(fields, "static data");
        int gravity = data.size() < 2 ? 2000 : Math.max(1, -GameScript.integer(data.get(1), -2000));
        int height = Act1SkillSequence.staticValue(fields, 0, 150);
        double initialZ = Math.sqrt(2.0 * gravity * height);
        long suspend = before(frames, 4);
        long land = Math.max(suspend + 1, (long) Math.ceil(2000 * initialZ / gravity));
        long recovery = land + duration(frames.subList(5, frames.size()));
        var flight = new SkillMotion(0, land, 0, 0, initialZ, -gravity);
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(action, 0, suspend, null, false, flight, 0, 4));
        poses.add(new Pose(action, suspend, land, null, false, flight, 4, 5));
        poses.add(new Pose(action, land, recovery, null, false, null, 5));
        var visuals = new ArrayList<Visual>();
        double weaponOffset = quakeWeaponOffset(lookup, action, weaponPath);
        add(lookup, visuals, EFFECT + "quakeareaweapon.ani", "落凤锤 · 空中挥击", FRONT, suspend, land, 0, weaponOffset, flight);
        long end = add(lookup, visuals, EFFECT + "quakeareacrash.ani", "落凤锤 · 着地挥击", FRONT, land, -1, 0, 0, null);
        end = Math.max(end, add(lookup, visuals, EFFECT + "quakeareadust.ani", "落凤锤 · 扬尘", FLOOR, land, -1, 38, 0, null));
        for (var clip : Act1SkillSequence.objectMotions(lookup, 24001)) {
            end = Math.max(end, add(lookup, visuals, clip, "落凤锤 · 地面冲击波", FLOOR + 1, land, -1, 38, 0, null));
        }
        if (visuals.isEmpty()) return null;
        idle(poses, actions, recovery, Math.max(end, recovery + 150), null);
        return sequence(poses, visuals, "落凤锤演示无方向输入时的跳跃和着地，地面冲击在落点前方 38 像素出现");
    }

    private static double quakeWeaponOffset(GameScript.Lookup lookup, String action, String weaponPath) throws IOException {
        if (weaponPath == null || weaponPath.isEmpty()) return -200;
        var appearance = CharacterAppearance.load(SKILLS + "quakearea.skl", "", lookup,
                Set.of(), "priest", action, Map.of("weapon", weaponPath));
        double offset = -200;
        for (var part : appearance.parts()) {
            if (!part.slot().equals("weapon")) continue;
            String source = lookup.read(part.animation());
            if (source == null) continue;
            boolean strikeFrame = false;
            for (var section : ResourceScript.sections(source)) {
                if (section.tag().startsWith("frame") && !section.tag().equals("frame max"))
                    strikeFrame = section.tag().matches("frame0*4");
                if (strikeFrame && section.tag().equals("attack box") && section.values().size() >= 6)
                    offset = Math.max(offset, -GameScript.integer(section.values().get(2), 200));
            }
        }
        return offset;
    }

    /** State 19 uses an input-rate-dependent spin count and passes the current speed to the final swing. */
    private static Act1SkillSequence giantSwing(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                Map<String, String> actions, int level) throws IOException {
        String ready = "etc motion:4", swing = "etc motion:5", finish = "etc motion:6";
        long readyTime = duration(body(lookup, actions, ready));
        var swingFrames = body(lookup, actions, swing);
        var finishFrames = body(lookup, actions, finish);
        long spinTime = duration(swingFrames), finishTime = duration(finishFrames);
        if (readyTime <= 0 || spinTime <= 0 || finishTime <= 0) return null;
        int minimum = Act1SkillSequence.levelValue(fields, level, 2, 5, 1, 20);
        int maximum = Math.max(minimum, Act1SkillSequence.levelValue(fields, level, 3, 8, 1, 20));
        int cap = Math.max(100, Act1SkillSequence.staticValue(fields, 3, 180));
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        poses.add(new Pose(ready, 0, readyTime));
        long start = readyTime;
        for (int i = 0; i < maximum; i++) {
            double speed = 100 + (cap - 100) * i / (double) Math.max(1, maximum - 1);
            long cycle = Math.max(1, Math.round(spinTime * 100 / speed));
            poses.add(new Pose(swing, start, start + cycle, null, true, null));
            linked(lookup, visuals, EFFECT + "giantswing1.ani", "疾空旋风破 · 旋转前层", FRONT, start, swingFrames, cycle, null);
            linked(lookup, visuals, EFFECT + "giantswing2.ani", "疾空旋风破 · 旋转后层", 499, start, swingFrames, cycle, null);
            start += cycle;
        }
        long last = Math.max(1, Math.round(finishTime * 100.0 / cap));
        poses.add(new Pose(finish, start, start + last, null, true, null));
        linked(lookup, visuals, EFFECT + "giantswingfinal.ani", "疾空旋风破 · 终结挥击", FRONT, start, finishFrames, last, null);
        if (visuals.isEmpty()) return null;
        idle(poses, actions, start + last, start + last + 150, null);
        return sequence(poses, visuals, "疾空旋风破演示原地连续输入逐步加快旋转并接终结；方向输入和命中由实战提供");
    }

    /** State 21 draws Smasher.ani from the grab controller during its dash phase (0x54C1F0). */
    private static Act1SkillSequence smasher(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                             Map<String, String> actions, boolean miss) throws IOException {
        String attempt = "etc motion:7", caught = "etc motion:8", dash = "etc motion:9";
        var caughtFrames = body(lookup, actions, caught);
        long tryTime = duration(body(lookup, actions, attempt)), caughtTime = duration(caughtFrames);
        var dashFrames = body(lookup, actions, dash);
        if (tryTime <= 0 || caughtTime <= 0 || dashFrames.size() < 2) return null;
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        poses.add(new Pose(attempt, 0, tryTime));
        if (miss) {
            idle(poses, actions, tryTime, tryTime + 150, null);
            return sequence(poses, visuals, "虎袭未抓到目标时结束抓取动作");
        }
        long charge = tryTime + caughtTime;
        long dashTime = Math.max(1, Act1SkillSequence.staticValue(fields, 2, 200));
        long release = charge + dashTime;
        long recovery = release + duration(dashFrames.subList(1, dashFrames.size()));
        int velocity = Act1SkillSequence.staticValue(fields, 0, 600);
        var movement = new SkillMotion(charge, dashTime, velocity, 0);
        poses.add(new Pose(caught, tryTime, charge));
        poses.add(new Pose(dash, charge, release, null, false, movement, 0, 1));
        poses.add(new Pose(dash, release, recovery, null, false, movement, 1));
        idle(poses, actions, recovery, recovery + 150, movement);
        add(lookup, visuals, EFFECT + "smasher.ani", "虎袭 · 冲刺光效", FRONT, charge, release, 0, 0, movement);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "虎袭 · 抓取目标", 0, tryTime, 60, null);
        smasherHeldTarget(lookup, visuals, tryTime, tryTime + before(caughtFrames, 1), 60, 40, null);
        smasherHeldTarget(lookup, visuals, tryTime + before(caughtFrames, 1),
                tryTime + before(caughtFrames, 4), -34, 35, null);
        smasherHeldTarget(lookup, visuals, tryTime + before(caughtFrames, 4), charge, -37, 75, null);
        smasherHeldTarget(lookup, visuals, charge, release, 43, 30, movement);
        int tossSpeed = Act1SkillSequence.staticValue(fields, 5, 600);
        int tossUp = Act1SkillSequence.staticValue(fields, 6, 150);
        long tossTime = Math.max(200, Math.round((tossUp + Math.sqrt(tossUp * (double) tossUp + 120_000)) / 2));
        var toss = new SkillMotion(release, tossTime, tossSpeed, 0, tossUp, -2000, 30);
        double releaseX = movement.offsetAt(release) + 43;
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "down", "虎袭 · 投出的目标", release, release + tossTime, releaseX, toss);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "down", "虎袭 · 倒地目标", release + tossTime,
                release + tossTime + 250, releaseX + toss.offsetAt(release + tossTime), null);
        for (var clip : Act1SkillSequence.objectMotions(lookup, 48028)) {
            var source = lookup.read(clip);
            if (source != null && ResourceScript.animation(source).frames().stream().anyMatch(f -> !f.image().isEmpty()))
                add(lookup, visuals, clip, "虎袭 · 目标受击", FRONT + 100, release, -1,
                        releaseX, 0, toss);
        }
        return sequence(poses, visuals, "虎袭按抓取、冲刺和投掷阶段播放，演示目标使用普通哥布林");
    }

    private static void smasherHeldTarget(GameScript.Lookup lookup, List<Visual> visuals, long start, long end,
                                          double x, double height, SkillMotion movement) throws IOException {
        String path = Act1SwordAsuraSupplement.GOBLIN + "damage2.ani";
        if (end > start && lookup.read(path) != null) {
            var facing = new VisualStyle(null, 0, List.of(), Map.of(), true);
            visuals.add(new Visual(path, "虎袭 · 被抓住的目标", 3500, start, end, x, -height, movement, 1, true, facing));
        }
    }

    /** State 34 throws #24014 on frame three. The projectile uses the PTL's speed and then spins in place. */
    private static Act1SkillSequence throwWeapon(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                 Map<String, String> actions) throws IOException {
        String ready = "etc motion:30", throwAction = "etc motion:32";
        long readyTime = duration(body(lookup, actions, ready));
        var throwFrames = body(lookup, actions, throwAction);
        var object = Act1SkillSequence.objectMotions(lookup, 24014);
        String trajectory = lookup.read("character/priest/particle/throwweapon.ptl");
        if (readyTime <= 0 || throwFrames.size() < 4 || object.isEmpty() || trajectory == null) return null;
        long release = readyTime + before(throwFrames, 3);
        long recovered = readyTime + duration(throwFrames);
        long travel = Math.max(1, Act1SkillSequence.staticValue(fields, 0, 600));
        long hold = Act1SkillSequence.staticValue(fields, 1, 1500);
        long end = release + travel + hold;
        double velocity = ParticleScript.parse(trajectory).axes().get(0).start();
        var motion = new SkillMotion(release, travel, velocity, 0);
        var visuals = List.of(new Visual(object.get(0), "巨旋风 · 飞行与原地旋转", 3600, release, end, 59, -51, motion, 1, true));
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(ready, 0, readyTime));
        poses.add(new Pose(throwAction, readyTime, recovered));
        idle(poses, actions, recovered, Math.max(end, recovered + 150), null);
        return sequence(poses, visuals, "巨旋风演示短按出手、飞行和原地旋转；武器外观替换和驻留碎屑由实战状态提供");
    }

    /** States 24 and 25 add the glove on frame 3 and spawn the independent wave on frame 4. */
    private static Act1SkillSequence straight(int id, List<GameScript.Field> fields, GameScript.Lookup lookup,
                                              Map<String, String> actions) throws IOException {
        boolean duck = id == 56;
        String label = duck ? "俯冲直拳" : "直拳冲击";
        String action = duck ? "etc motion:14" : "etc motion:13";
        var frames = body(lookup, actions, action);
        if (frames.size() < 7) return null;
        long recovery = duration(frames), flash = before(frames, 3), wave = before(frames, 4), detach = before(frames, 6);
        var movement = moving(fields, 0, recovery, duck ? 0 : 500, duck ? 0 : -1200);
        var visuals = new ArrayList<Visual>();
        String glove = EFFECT + "luckystraightglowglove.ani";
        if (lookup.read(glove) != null) visuals.add(new Visual(glove, label + " · 拳套光效", FRONT, flash, detach, 0, 0, movement, 1, true));
        long end = add(lookup, visuals, EFFECT + "luckystraightwave.ani", label + " · 拳风", FRONT + 1,
                wave, -1, 0, 1, movement);
        if (visuals.isEmpty()) return null;
        return onePose(actions, action, recovery, end, movement, visuals,
                label + "在出拳动作中先点亮拳套，再出现拳风；命中造成的停顿由实战提供");
    }

    /** Body blow and the two uppercuts set movement and attack boxes. Their target-free path has no attached ANI. */
    private static Act1SkillSequence shortPunch(int id, List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                Map<String, String> actions) throws IOException {
        String action = switch (id) {
            case 57 -> "etc motion:10";
            case 58 -> "etc motion:16";
            case 63 -> "etc motion:15";
            default -> throw new IllegalArgumentException("Unknown Priest punch " + id);
        };
        String label = switch (id) {
            case 57 -> "俯冲腹拳";
            case 58 -> "俯冲翔拳";
            default -> "勾拳追击";
        };
        long recovery = duration(body(lookup, actions, action));
        if (recovery <= 0) return null;
        var movement = moving(fields, 0, recovery, id == 63 ? 600 : 500, id == 57 ? -3000 : -5000);
        return onePose(actions, action, recovery, recovery + 150, movement, List.of(),
                label + "使用客户端的出拳姿势及冲刺参数；眩晕和浮空需要实际命中");
    }

    /** 0x627770 attaches the authored strike trail on entry to state 28. */
    private static Act1SkillSequence combination(GameScript.Lookup lookup, Map<String, String> actions) throws IOException {
        String action = "etc motion:19";
        var frames = body(lookup, actions, action);
        long recovery = duration(frames);
        if (recovery <= 0) return null;
        var visuals = new ArrayList<Visual>();
        long end = add(lookup, visuals, EFFECT + "gorgeouscombination.ani", "圣拳连击 · 三段拳风", FRONT, 0, -1, 0, 0, null);
        if (visuals.isEmpty()) return null;
        return onePose(actions, action, recovery, Math.max(end, recovery + 150), null, visuals,
                "圣拳连击使用原版三段拳风；命中停顿和伤害由实战提供");
    }

    /** State 27 switches to Bodyblow after the contact delay configured in the Priest SD. */
    private static Act1SkillSequence holyCounter(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                  Map<String, String> actions) throws IOException {
        String stance = "etc motion:17", counter = "etc motion:18";
        var guardFrames = body(lookup, actions, stance);
        long counterTime = duration(body(lookup, actions, counter));
        if (guardFrames.size() < 2 || counterTime <= 0) return null;
        long counterStart = guardFrames.get(0).delay() + Math.max(0, setting(settings(lookup), "hc delay", 200));
        long recovered = counterStart + counterTime;
        var movement = moving(fields, counterStart, counterTime, 1500, -5000);
        var visuals = new ArrayList<Visual>();
        long end = add(lookup, visuals, EFFECT + "holycounter.ani", "神圣反击 · 反击拳风", FRONT, counterStart, -1, 0, 0, movement);
        if (visuals.isEmpty()) return null;
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(stance, 0, counterStart));
        poses.add(new Pose(counter, counterStart, recovered, null, false, movement));
        idle(poses, actions, recovered, Math.max(end, recovered + 150), movement);
        return sequence(poses, visuals, "神圣反击以进入架势后立即受击为例，按原版延迟启动反击与拳风");
    }

    /** State 30 releases #24008 at the Priest SD anchor; the object rewinds to frame six until expiry. */
    private static Act1SkillSequence judgement(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                                Map<String, String> actions, int level) throws IOException {
        String action = "etc motion:21";
        var body = body(lookup, actions, action);
        var clips = Act1SkillSequence.objectMotions(lookup, 24008);
        if (body.size() < 3 || clips.isEmpty()) return null;
        var config = settings(lookup);
        int releaseFrame = Math.max(0, Math.min(body.size() - 1, setting(config, "hoj create frame index", 2)));
        long release = before(body, releaseFrame);
        int x = setting(config, "hoj create x offset", 150);
        int life = Act1SkillSequence.levelValue(fields, level, 2, 10000, 1, 30000);
        long end = release + life;
        String path = clips.get(0);
        var frames = ResourceScript.animation(lookup.read(path)).frames();
        long intro = duration(frames);
        if (intro <= 0) return null;
        var visuals = new ArrayList<Visual>();
        visuals.add(new Visual(path, "天谴飓风 · 生成旋风", 3600, release, Math.min(end, release + intro), x, 0));
        if (frames.size() > 6 && life > intro) {
            var overrides = new LinkedHashMap<Integer, FrameOverride>();
            for (int i = 0; i < 6; i++) overrides.put(i, new FrameOverride(0, 0, 0));
            long loopTime = intro - before(frames, 6);
            var style = new VisualStyle(null, 0, List.of(), overrides, false, loopTime);
            visuals.add(new Visual(path, "天谴飓风 · 持续旋风", 3600, release + intro, end, x, 0, null, 1, true, style));
        }
        return onePose(actions, action, duration(body), end, null, visuals,
                "天谴飓风在脚本指定帧和位置生成，按技能等级持续；追踪与卷起目标由实战提供");
    }

    /** State 10 creates the field; #24011 selects eligible targets for #24013 in 0x5B5B30. */
    private static Act1SkillSequence talisman(int id, List<GameScript.Field> fields, GameScript.Lookup lookup,
                                             Map<String, String> actions, int level, boolean hitTarget) throws IOException {
        String prepare = "throw motion 3-1", release = "throw motion 3-2";
        if (!actions.containsKey(prepare) || !actions.containsKey(release)) return null;
        boolean thunder = id == 40;
        String label = thunder ? "落雷符" : "压制符";
        String object = GameEffectRecipes.passiveObject(lookup, thunder ? 24011 : 24010);
        if (object == null) return null;
        var contents = GameScript.fields(lookup.read(object));
        var loopBounds = GameScript.values(contents, "int data").stream()
                .map(value -> GameScript.integer(value, -1)).toList();
        var references = new ArrayList<String>();
        references.add(GameScript.value(contents, "basic motion"));
        references.addAll(GameScript.values(contents, "etc motion"));
        long cast = Act1SkillSequence.castingTime(fields, level, thunder ? 800 : 700);
        long recovered = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, 700));
        int x = setting(settings(lookup), thunder ? "tt create x offset" : "ot create x offset", 150);
        int life = Act1SkillSequence.levelValue(fields, level, 0, 20000, 1, 40000);
        double rangeScale = Act1SkillSequence.staticValue(fields, 1, 500) / 500.0;
        var visuals = new ArrayList<Visual>();
        long end = recovered;
        for (int slot = 0; slot < references.size(); slot++) {
            String reference = references.get(slot);
            if (reference.isEmpty()) continue;
            String path = GameScript.resolve(object, reference, lookup);
            if (path == null) continue;
            boolean floor = slot == 3;
            String layerLabel = label + (floor ? " · 地面法阵" : " · 符体");
            int layer = floor ? FLOOR : 3600 + slot;
            double scale = slot >= 2 ? rangeScale : 1;
            int bound = 2 * slot;
            long layerEnd = bound + 1 < loopBounds.size()
                    ? loopingTalisman(lookup, visuals, path, layerLabel, layer, cast, life, x, scale,
                    loopBounds.get(bound), loopBounds.get(bound + 1))
                    : heldTalisman(lookup, visuals, path, layerLabel, layer, cast, life, x, scale);
            end = Math.max(end, layerEnd);
        }
        if (visuals.isEmpty()) return null;
        var emitters = new ArrayList<SkillParticleEmitter>();
        if (thunder && hitTarget) {
            var bolt = Act1SkillSequence.objectMotions(lookup, 24013);
            if (bolt.isEmpty()) return null;
            double targetX = x + Math.max(1, Math.min(85, Act1SkillSequence.staticValue(fields, 1, 500) / 4.0));
            long interval = Math.max(100, Act1SkillSequence.staticValue(fields, 2, 2000));
            int warningSpeed = Act1SkillSequence.staticValue(fields, 3, 200);
            long previous = 0;
            for (long scheduled = cast + interval; scheduled < cast + life; scheduled += interval) {
                long strike = Act1SkillSequence.targetWarning(lookup, visuals, "落雷符 · 目标预警", scheduled, targetX, warningSpeed);
                Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "落雷符 · 法阵内目标", previous, strike, targetX, null);
                long boltEnd = add(lookup, visuals, bolt.get(0), "落雷符 · 目标落雷", 3650, strike, -1, targetX, 0, null);
                previous = Math.min(boltEnd, strike + interval);
                Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", "落雷符 · 受击目标", strike, previous, targetX, null);
                end = Math.max(end, boltEnd);
                end = Math.max(end, Act1PriestCast.thunderParticles(lookup, emitters, bolt.get(0), "落雷符 · 落雷余光", strike, targetX));
            }
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "落雷符 · 法阵内目标", previous, end, targetX, null);
        }
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions, prepare, release, cast, recovered, end, false),
                List.copyOf(visuals), List.copyOf(emitters), thunder
                ? (hitTarget ? "落雷符演示法阵内一名普通哥布林，按原版间隔在目标位置落雷"
                : "落雷符演示空场落符、持续法阵与到期消散")
                : "压制符显示落地、持续法阵与到期消散；减速由法阵中的目标状态决定");
    }

    /** CNLightTalisman (0x5B5100/0x5B5510) skips the placeholder at the end of each layer's loop. */
    static long loopingTalisman(GameScript.Lookup lookup, List<Visual> visuals, String path, String label,
                                        int layer, long cast, int life, int x, double scale,
                                        int first, int stop) throws IOException {
        var animation = ResourceScript.animation(lookup.read(path));
        var frames = animation.frames();
        if (first < 0 || first >= stop || stop >= frames.size() || duration(frames.subList(first, stop)) <= 0)
            return heldTalisman(lookup, visuals, path, label, layer, cast, life, x, scale);
        long expires = cast + life;
        long loopStart = Math.min(expires, cast + before(frames, first));
        addTalismanPhase(visuals, path, label, layer, cast, loopStart, x, scale, animation, 0, first, false);
        addTalismanPhase(visuals, path, label, layer, loopStart, expires, x, scale, animation, first, stop, true);
        long end = expires + duration(frames.subList(stop + 1, frames.size()));
        addTalismanPhase(visuals, path, label, layer, expires, end, x, scale, animation, stop + 1, frames.size(), false);
        return end;
    }

    private static void addTalismanPhase(List<Visual> visuals, String path, String label, int layer,
                                         long start, long end, int x, double scale, ResourceScript.Animation animation,
                                         int first, int stop, boolean loop) {
        if (end <= start || first >= stop) return;
        var slice = new ResourceScript.Animation(loop, animation.frames().subList(first, stop), animation.warnings());
        visuals.add(new Visual(path, label, layer, start, end, x, 0, null, scale, false, new VisualStyle(slice)));
    }

    private static long heldTalisman(GameScript.Lookup lookup, List<Visual> visuals, String path, String label,
                                      int layer, long start, int life, int x, double scale) throws IOException {
        var frames = ResourceScript.animation(lookup.read(path)).frames();
        for (int i = 0; i < frames.size(); i++) {
            if (frames.get(i).delay() < 10000) continue;
            long prefix = before(frames, i);
            int hold = (int) Math.max(1, life - prefix);
            long end = start + duration(frames) - frames.get(i).delay() + hold;
            var style = new VisualStyle(null, 0, List.of(), Map.of(i, new FrameOverride(0, 0, hold)));
            visuals.add(new Visual(path, label, layer, start, end, x, 0, null, scale, false, style));
            return end;
        }
        long end = start + duration(frames);
        if (end > start) visuals.add(new Visual(path, label, layer, start, end, x, 0, null, scale, false));
        return end;
    }

    static List<String> settings(GameScript.Lookup lookup) throws IOException {
        return GameScript.values(GameScript.fields(lookup.read("data/skill/priest.sd")), "int");
    }

    static int setting(List<String> values, String name, int fallback) {
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if (name.equals(values.get(i))) return GameScript.integer(values.get(i + 1), fallback);
        }
        return fallback;
    }

    private static List<ResourceScript.Frame> body(GameScript.Lookup lookup, Map<String, String> actions,
                                                    String action) throws IOException {
        return Act1SkillSequence.frames(CHR, actions.get(action), lookup);
    }

    private static long duration(List<ResourceScript.Frame> frames) {
        return before(frames, frames.size());
    }

    private static long before(List<ResourceScript.Frame> frames, int frame) {
        return Act1SkillSequence.duration(frames, frame, null);
    }

    private static long scaledFrame(List<ResourceScript.Frame> body, int frame, long stage) {
        return Math.round(before(body, frame) * (double) stage / duration(body));
    }

    private static long add(GameScript.Lookup lookup, List<Visual> visuals, String path, String label,
                            int layer, long start, long limit, double x, double y, SkillMotion motion) throws IOException {
        String text = lookup.read(path);
        if (text == null) return start;
        long stop = start + new AnimationTimeline(ResourceScript.animation(text)).duration();
        if (limit >= 0) stop = Math.min(stop, limit);
        if (stop > start) visuals.add(new Visual(path, label, layer, start, stop, x, y, motion, 1, true));
        return stop;
    }

    /** 0x493B70 copies each actor frame's delay to the corresponding attached effect frame. */
    private static void linked(GameScript.Lookup lookup, List<Visual> visuals, String path, String label,
                               int layer, long start, List<ResourceScript.Frame> body, long stage,
                               SkillMotion motion) throws IOException {
        String text = lookup.read(path);
        if (text == null) return;
        var frames = ResourceScript.animation(text).frames();
        var overrides = new LinkedHashMap<Integer, FrameOverride>();
        long end = 0;
        for (int index = 0; index < Math.min(body.size(), frames.size()); index++) {
            long next = scaledFrame(body, index + 1, stage);
            overrides.put(index, new FrameOverride(0, 0, (int) (next - end)));
            end = next;
        }
        var style = new VisualStyle(null, 0, List.of(), overrides);
        if (end > 0) visuals.add(new Visual(path, label, layer, start, start + end, 0, 0, motion, 1, true, style));
    }

    private static SkillMotion moving(List<GameScript.Field> fields, long start, long recovery, int fallbackSpeed, int fallbackAcceleration) {
        int speed = Act1SkillSequence.staticValue(fields, 0, fallbackSpeed);
        var data = GameScript.values(fields, "static data");
        int acceleration = data.size() < 2 ? fallbackAcceleration : GameScript.integer(data.get(1), fallbackAcceleration);
        long travel = acceleration < 0 ? Math.min(recovery, (long) Math.ceil(-1000.0 * speed / acceleration)) : recovery;
        return new SkillMotion(start, travel, speed, acceleration);
    }

    private static Act1SkillSequence onePose(Map<String, String> actions, String action, long recovery, long end,
                                             SkillMotion motion, List<Visual> visuals, String note) {
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(action, 0, recovery, null, false, motion));
        idle(poses, actions, recovery, end, motion);
        return sequence(poses, visuals, note);
    }

    private static void idle(List<Pose> poses, Map<String, String> actions, long start, long end, SkillMotion motion) {
        if (end > start && actions.containsKey(IDLE)) poses.add(new Pose(IDLE, start, end, null, false, motion));
    }

    private static Act1SkillSequence sequence(List<Pose> poses, List<Visual> visuals, String note) {
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(), note);
    }
}
