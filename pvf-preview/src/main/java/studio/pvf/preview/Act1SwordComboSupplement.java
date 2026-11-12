package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Swordman airborne attacks, state 8 weapon combos, and the state 35/36 chained sword arts. */
final class Act1SwordComboSupplement {
    private static final String SKILLS = "skill/swordman/";
    private static final String CHR = "character/swordman/swordman.chr";
    private static final String EFFECT = "character/swordman/effect/animation/";
    private static final String JUMP = "jump motion", AIR_ATTACK = "jumpattack motion", IDLE = "waiting motion";
    private static final int BACK = 499, FRONT = 3501;
    private static final double GRAVITY = 1500;

    private Act1SwordComboSupplement() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup, Map<String, String> actions,
                                  int level, String weaponPath) throws IOException {
        int skill = switch (path) {
            case SKILLS + "ashenfork.skl" -> 16;
            case SKILLS + "jumpattackmulti.skl" -> 17;
            case SKILLS + "backstepcutter.skl" -> 49;
            case SKILLS + "weaponcombo.skl" -> 67;
            case SKILLS + "rapidmoveslash.skl" -> 72;
            case SKILLS + "illusionslash.skl" -> 73;
            default -> -1;
        };
        if (skill < 0) return null;
        var catalog = GameScript.catalog(lookup.read("skill/swordmanskill.lst"));
        if (!path.equals("skill/" + catalog.get(skill))) return null;
        var fields = GameScript.fields(text);
        int weapon = weaponType(lookup, weaponPath);
        return switch (skill) {
            case 16 -> ashenFork(fields, lookup, actions, level);
            case 17 -> airCombo(lookup, actions, weapon);
            case 49 -> backstepCut(lookup, actions, weapon, catalog);
            case 67 -> weaponCombo(lookup, actions, weapon);
            case 72 -> rapidMove(fields, lookup, actions, weapon);
            case 73 -> illusion(fields, lookup, actions, weapon);
            default -> throw new AssertionError(skill);
        };
    }

    /** State 21 holds the last jump-attack frame for 200 ms, then starts the forward plunge. */
    private static Act1SkillSequence ashenFork(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                              Map<String, String> actions, int level) throws IOException {
        var body = frames(lookup, actions, AIR_ATTACK);
        var objects = Act1SkillSequence.objectMotions(lookup, 20016);
        if (body.isEmpty() || objects.size() < 3 || !actions.containsKey(IDLE)) return null;
        int last = body.size() - 1;
        long drop = 200;
        double height = Math.max(120, Act1SkillSequence.staticValue(fields, 3, 100) + 20);
        double verticalSpeed = -.9 * jumpPower(lookup);
        long flight = Math.max(1, (long)Math.ceil(1000 * (verticalSpeed + Math.sqrt(verticalSpeed * verticalSpeed + 2 * GRAVITY * height)) / GRAVITY));
        long land = drop + flight;
        var hold = new SkillMotion(0, drop, 0, 0, 0, 0, height);
        var dive = new SkillMotion(drop, flight, 470, 0, verticalSpeed, -GRAVITY, height);
        var stop = new SkillMotion(land, 0, 0, 0, dive.offsetAt(land));
        var visuals = new ArrayList<Visual>();
        add(visuals, lookup, EFFECT + "jumpattackhold.ani", "银光落刃 · 下落气流", FRONT, drop, land, dive, 1, false);
        double scale = Act1SkillSequence.levelValue(fields, level, 2, 100, 1, 400) / 100.0;
        add(visuals, lookup, objects.get(0), "银光落刃 · 地面冲击波", 300, land, Long.MAX_VALUE, stop, scale, false);
        add(visuals, lookup, objects.get(1), "银光落刃 · 落地扬尘", 301, land, Long.MAX_VALUE, stop, scale, false);
        add(visuals, lookup, objects.get(2), "银光落刃 · 冲击闪光", 302, land, Long.MAX_VALUE, stop, scale, false);
        long end = Math.max(land + 150, visualEnd(visuals));
        return sequence(List.of(new Pose(AIR_ATTACK, 0, drop, null, false, hold, last),
                new Pose(AIR_ATTACK, drop, land, null, false, dive, last),
                new Pose(IDLE, land, end, null, false, stop)), visuals, List.of(),
                "银光落刃从满足溅射门槛的示例高度演示空中停顿、俯冲和着地冲击。巨剑精通与受击高度需要实战条件。");
    }

    /** State 6 keeps the backstep trajectory when skill 49 changes to the shared airborne attack. */
    private static Act1SkillSequence backstepCut(GameScript.Lookup lookup, Map<String, String> actions,
                                                 int weapon, Map<Integer, String> catalog) throws IOException {
        if (!("swordman/backstep.skl").equals(catalog.get(169))) return null;
        var step = Act1Backstep.find("swordman", lookup.read(SKILLS + "backstep.skl"), lookup, actions);
        var strike = frames(lookup, actions, AIR_ATTACK);
        if (step == null || strike.size() <= 2) return null;
        var initial = step.poses().get(0);
        long land = initial.holdDelay();
        long cut = Math.min(140, land / 3);
        if (land - cut <= delay(strike, 2)) return null;
        var motion = initial.motion();
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(JUMP, 0, cut, initial.holdDelay(), false, motion, initial.firstFrame(), initial.firstFrame() + 1));
        poses.add(new Pose(AIR_ATTACK, cut, land, null, false, motion));
        poses.add(new Pose(JUMP, land, initial.end(), null, false, motion, initial.firstFrame() + 1));
        poses.addAll(step.poses().subList(1, step.poses().size()));
        var visuals = new ArrayList<Visual>();
        if (weapon == 1) {
            add(visuals, lookup, EFFECT + "jumpattackmulti/jumpchainattacknormal_upper_effect.ani", "后跳斩 · 太刀刀光",
                    FRONT, cut + delay(strike, 2), land, motion, .9, false);
        }
        return sequence(poses, visuals, step.emitters(), "后跳斩演示后跳中输入攻击。太刀刀光按已学习空中连斩演示，钝器反弹需要命中条件。");
    }

    /** State 7 uses the ordinary attack except for the learned Katana mastery branch. */
    private static Act1SkillSequence airCombo(GameScript.Lookup lookup, Map<String, String> actions, int weapon) throws IOException {
        var jump = frames(lookup, actions, JUMP);
        if (jump.isEmpty()) return null;
        int held = -1;
        for (int i = 0; i + 1 < jump.size(); i++) if (jump.get(i).delay() >= 10000) held = i;
        if (held < 0) return null;
        double launch = Math.sqrt(2 * GRAVITY * 150);
        long land = (long)Math.ceil(2000 * launch / GRAVITY);
        var flight = new SkillMotion(0, land, 0, 0, launch, -GRAVITY);
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        long time = 150;
        poses.add(new Pose(JUMP, 0, time, null, false, flight));
        for (int i = 0; i < 2 && time < land; i++) {
            String action = weapon == 1 ? etc(23 + i) : AIR_ATTACK;
            var strike = frames(lookup, actions, action);
            if (strike.size() <= 2) return null;
            long end = Math.min(land, time + delay(strike, strike.size()));
            poses.add(new Pose(action, time, end, null, false, flight));
            if (weapon == 1) {
                String stem = EFFECT + "jumpattackmulti/jumpchainattackslash" + (i + 1);
                String label = i == 0 ? "空中连斩 · 第一刀" : "空中连斩 · 第二刀";
                add(visuals, lookup, stem + "_katana_under.ani", label + "太刀残影后层", BACK, time, end, flight, .9, false);
                add(visuals, lookup, stem + "_katana_upper.ani", label + "太刀残影前层", FRONT, time, end, flight, .9, false);
                long swing = time + delay(strike, 2);
                add(visuals, lookup, stem + "_under_effect.ani", label + "后层刀光", FRONT + 1, swing, end, flight, .9, false);
                add(visuals, lookup, stem + "_upper_effect.ani", label + "前层刀光", FRONT + 2, swing, end, flight, .9, false);
            }
            time = end;
        }
        if (time < land) poses.add(new Pose(JUMP, time, land, (int)(land - time), false, flight, held, held + 1));
        long landingPose = land + Math.max(50, delay(jump, jump.size()) - delay(jump, held + 1));
        poses.add(new Pose(JUMP, land, landingPose, null, false, null, held + 1));
        if (actions.containsKey(IDLE)) poses.add(new Pose(IDLE, landingPose, landingPose + 150));
        return sequence(poses, visuals, List.of(), "空中连斩以示例起跳高度连续输入两次。太刀路线按达到精通门槛演示两式刀光。");
    }

    /** A00060 gives the exact CHR motions for each weapon's state 8 sequence. */
    private static Act1SkillSequence weaponCombo(GameScript.Lookup lookup, Map<String, String> actions, int weapon) throws IOException {
        int[] motions = switch (weapon) {
            case 1 -> new int[] {31, 32, 33, 34};
            case 2 -> new int[] {35, 36, 36, 37};
            case 3 -> new int[] {38, 39};
            case 5 -> new int[] {40, 41, 42};
            default -> new int[] {28, 29, 30};
        };
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        long time = 0;
        for (int i = 0; i < motions.length; i++) {
            String action = etc(motions[i]);
            var body = frames(lookup, actions, action);
            if (body.size() < 4) return null;
            // A00120 supplies the first frame on which the next attack can replace this motion.
            int cancelFrame = weapon == 2 ? (i == 0 ? 4 : 5) : 3;
            long end = time + delay(body, i == motions.length - 1 ? body.size() : cancelFrame);
            poses.add(new Pose(action, time, end));
            String trail = switch (weapon) {
                case 1 -> i == 3 ? "blade" : "";
                case 2 -> i == 3 ? "blunt" : "";
                case 3 -> i == 1 ? "heavy" : "";
                case 5 -> i == 2 ? "light" : "";
                default -> i == 2 ? "short" : "";
            };
            int layer = weapon == 3 ? BACK : FRONT;
            if (!trail.isEmpty()) add(visuals, lookup, EFFECT + "weaponcombo/" + trail + ".ani", "里鬼剑术 · 刀光", layer, time, end, null, 1, false);
            if (weapon == 5) {
                for (int part = 1; part <= 6; part++) {
                    int order = part <= 3 ? BACK - 3 + part : FRONT - 4 + part;
                    add(visuals, lookup, EFFECT + "weaponcombo/light" + (i + 1) + "-" + part + ".ani",
                            "里鬼剑术 · 光剑层" + part, order, time, end, null, 1, false);
                }
            }
            time = end;
        }
        return sequence(poses, visuals, List.of(), "里鬼剑术按当前武器展示原版连招。预览采用连续输入，命中与取消时机由实战决定。");
    }

    /** State 35 changes from Ready to Move when Ready ends and accepts the next input at Move frame two. */
    private static Act1SkillSequence rapidMove(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                               Map<String, String> actions, int weapon) throws IOException {
        int strikes = Math.max(1, Math.min(8, Act1SkillSequence.staticValue(fields, 0, 4)));
        int speed = Act1SkillSequence.staticValue(fields, 2, 2000);
        var data = GameScript.values(fields, "static data");
        int acceleration = data.size() > 3 ? Math.min(0, GameScript.integer(data.get(3), -5000)) : -5000;
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        long time = 0;
        double x = 0;
        for (int i = 0; i < strikes; i++) {
            boolean last = i + 1 == strikes;
            boolean reverse = i % 2 == 1;
            int direction = reverse ? -1 : 1;
            String ready = etc(last ? 48 : 47), move = etc(last ? 50 : 49);
            var readyFrames = frames(lookup, actions, ready);
            var moveFrames = frames(lookup, actions, move);
            if (readyFrames.isEmpty() || moveFrames.size() <= 2) return null;
            long start = time + delay(readyFrames, readyFrames.size());
            long end = start + delay(moveFrames, last ? moveFrames.size() : 2);
            long travel = acceleration < 0 ? Math.min(end - start, Math.round(-1000.0 * speed / acceleration)) : end - start;
            var hold = new SkillMotion(time, 0, 0, 0, x);
            var dash = new SkillMotion(start, travel, speed * direction, acceleration * direction, x);
            poses.add(new Pose(ready, time, start, null, false, hold, 0, Integer.MAX_VALUE, reverse));
            poses.add(new Pose(move, start, end, null, false, dash, 0, Integer.MAX_VALUE, reverse));
            String path = EFFECT + "rapidmoveslash/";
            String label = "猛龙断空斩 · 第" + (i + 1) + "段";
            double scale = weapon == 3 ? 1 : weapon == 0 ? (last ? .68 : .85) : (last ? .74 : .9);
            add(visuals, lookup, path + "move1.ani", label + "疾行轨迹", FRONT, start, end, dash, 1, reverse);
            add(visuals, lookup, path + "move2.ani", label + "前冲轨迹", FRONT + 1, start, end, dash, 1, reverse);
            add(visuals, lookup, path + (last ? "slash2.ani" : "slash1.ani"), label + "刀光", FRONT + 2, start, end, dash, scale, reverse);
            add(visuals, lookup, path + "dust.ani", label + "起步扬尘", BACK, start, Long.MAX_VALUE, hold, 1, reverse);
            if (weapon == 5) {
                int variant = last ? 2 : 1;
                for (int part = 1; part <= 6; part++) {
                    int order = part <= 3 ? BACK - 3 + part : FRONT - 4 + part;
                    add(visuals, lookup, path + "lightready" + variant + "-" + part + ".ani", label + "光剑准备层", order, time, start, hold, 1, reverse);
                    add(visuals, lookup, path + "lightmove" + variant + "-" + part + ".ani", label + "光剑疾行层", order, start, end, dash, 1, reverse);
                }
            }
            x = dash.offsetAt(end);
            time = end;
        }
        return sequence(poses, visuals, List.of(), "猛龙断空斩按原版允许的最早时间交替左右输入。命中追加次数及武器精通附加效果需要实战条件。");
    }

    /** State 36 cycles four slash motions through the low-input limit, then attaches Upper and Smash before #20037. */
    private static Act1SkillSequence illusion(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                              Map<String, String> actions, int weapon) throws IOException {
        int minimum = Math.max(1, Math.min(30, Act1SkillSequence.staticValue(fields, 2 * weapon + 1, 10)));
        int[] order = {51, 53, 52, 54, 53, 51, 54, 52};
        var poses = new ArrayList<Pose>();
        var visuals = new ArrayList<Visual>();
        long time = 0;
        for (int i = 0; i < minimum; i++) {
            int index = order[i % order.length];
            String action = etc(index);
            var body = frames(lookup, actions, action);
            var object = Act1SkillSequence.objectMotions(lookup, 20033 + index - 51);
            if (body.size() <= 3 || object.isEmpty()) return null;
            long end = time + delay(body, 3);
            poses.add(new Pose(action, time, end));
            long fade = time + delay(body, body.size()) - end;
            if (fade > 0) {
                poses.add(new Pose(action, end, end + fade, null, false, null, 3, Integer.MAX_VALUE,
                        false, List.of(new VisualKeyframe(0, 1, .65), new VisualKeyframe(fade, 1, 0)), true));
            }
            String slash = object.get(0);
            var slashFrames = ResourceScript.animation(lookup.read(slash)).frames();
            long visible = time;
            for (var frame : slashFrames) {
                if (!frame.image().isBlank()) break;
                visible += frame.delay();
            }
            // Keep the original ANI fade and give the leading slash a short representative forward travel.
            var advance = new SkillMotion(visible, 120, 400, -2000);
            add(visuals, lookup, slash, "幻影剑舞 · 第" + (i + 1) + "段刀光", FRONT, time, Long.MAX_VALUE, advance, 1, false);
            time = end;
        }
        String finish = etc(55);
        var body = frames(lookup, actions, finish);
        var waves = Act1SkillSequence.objectMotions(lookup, 20037);
        String movement = lookup.read("character/swordman/particle/illusionslash.ptl");
        if (body.size() <= 6 || waves.size() < 3 || movement == null) return null;
        long release = time + delay(body, 6);
        long recover = time + delay(body, body.size());
        long life = Math.max(1, Act1SkillSequence.staticValue(fields, 13, 500));
        double speed = ParticleScript.parse(movement).axes().get(0).start();
        double scale = weapon == 3 ? 1 : weapon == 0 ? .85 : .9;
        poses.add(new Pose(finish, time, recover));
        add(visuals, lookup, EFFECT + "illusionslash/upper.ani", "幻影剑舞 · 终结上斩", FRONT, time + delay(body, 2), recover, null, scale, false);
        add(visuals, lookup, EFFECT + "illusionslash/smash.ani", "幻影剑舞 · 终结下斩", FRONT + 1, release, recover, null, scale, false);
        var ground = new SkillMotion(release, life, speed, 0);
        // The native spawn offsets X by 65. 0x595500 attaches Bottom1 at the same anchor for a horizontal wave.
        visuals.add(new Visual(waves.get(1), "幻影剑舞 · 剑气地面层", 301, release, release + life, 65, 0, ground, 1, true));
        visuals.add(new Visual(waves.get(0), "幻影剑舞 · 终结剑气", FRONT + 3, release, release + life, 65, 0, ground, 1, true));
        if (release + life > recover && actions.containsKey(IDLE)) poses.add(new Pose(IDLE, recover, release + life));
        return sequence(poses, visuals, List.of(), "幻影剑舞演示普通连斩与贴地水平剑气。前段刀光采用示例的短距离前移，残影接续上一刀并淡出。");
    }

    private static List<ResourceScript.Frame> frames(GameScript.Lookup lookup, Map<String, String> actions, String action) throws IOException {
        return Act1SkillSequence.frames(CHR, actions.get(action), lookup);
    }

    private static long delay(List<ResourceScript.Frame> frames, int count) {
        return Act1SkillSequence.duration(frames, count, null);
    }

    private static String etc(int index) { return "etc motion:" + index; }

    private static double jumpPower(GameScript.Lookup lookup) throws IOException {
        String value = GameScript.value(GameScript.fields(lookup.read(CHR)), "jump power");
        try {
            double power = Double.parseDouble(value);
            if (Double.isFinite(power) && power > 0 && power <= 2000) return power;
        } catch (NumberFormatException ignored) { }
        return 430;
    }

    private static int weaponType(GameScript.Lookup lookup, String path) throws IOException {
        if (path == null || path.isEmpty()) return 0;
        var fields = GameScript.fields(lookup.read(GameScript.normalize(path)));
        if (!GameScript.flag(GameScript.value(fields, "equipment type")).equals("weapon")) return 0;
        int type = GameScript.integer(GameScript.value(fields, "sub type"), 0);
        return type >= 0 && type <= 5 ? type : 0;
    }

    private static void add(List<Visual> visuals, GameScript.Lookup lookup, String path, String label, int layer,
                            long start, long cap, SkillMotion motion, double scale, boolean reverse) throws IOException {
        String text = lookup.read(path);
        if (text == null || cap <= start) return;
        var animation = ResourceScript.animation(text);
        long duration = new AnimationTimeline(animation).duration();
        long end = animation.loop() && cap < Long.MAX_VALUE ? cap : Math.min(start + duration, cap);
        var style = reverse ? new VisualStyle(null, 0, List.of(), Map.of(), true) : VisualStyle.DEFAULT;
        if (end > start) visuals.add(new Visual(path, label, layer, start, end, 0, 0, motion, scale, true, style));
    }

    private static long visualEnd(List<Visual> visuals) {
        return visuals.stream().mapToLong(Visual::end).max().orElse(0);
    }

    private static Act1SkillSequence sequence(List<Pose> poses, List<Visual> visuals, List<SkillParticleEmitter> particles, String note) {
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.copyOf(particles), note);
    }
}
