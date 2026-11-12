package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** ACT1 priest summons #24019, #24020 and the linked Baekho lightning object #24021. */
final class Act1PriestSummon {
    private static final String HYUNMOO = "skill/priest/hyunmoo.skl";
    private static final String BAEKHO = "skill/priest/baekho.skl";
    private static final String EMPTY = "hyunmoo:empty", SINGLE = "baekho:single";
    private static final String OBJECT = "passiveobject/character/priest/";
    private static final String PREPARE = "throw motion 2-1", RELEASE = "throw motion 2-2", IDLE = "waiting motion";
    private static final int BODY = 3600, BELOW = 490, FLOOR = 300;
    private static final VisualStyle REVERSED = new VisualStyle(null, 0, List.of(), Map.of(), true);

    private Act1PriestSummon() {}

    static boolean matchesChoice(String path, String choice) {
        return HYUNMOO.equals(path) && EMPTY.equals(choice) || BAEKHO.equals(path) && SINGLE.equals(choice);
    }

    static List<GameEffectRecipes.Effect> choices(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1) return List.of();
        if (HYUNMOO.equals(path) && Act1SkillSequence.matches(path, "priest", 69, lookup))
            return List.of(new GameEffectRecipes.Effect(EMPTY, "玄武 · 空场", "", path, "展示空场召唤和到期离场"));
        if (BAEKHO.equals(path) && Act1SkillSequence.matches(path, "priest", 70, lookup))
            return List.of(new GameEffectRecipes.Effect(SINGLE, "白虎 · 单个光环", "", path, "展示一个白虎的持续光环"));
        return List.of();
    }

    static Act1SkillSequence find(int id, List<GameScript.Field> fields, GameScript.Lookup lookup,
                                  Map<String, String> actions, int level, String choice) throws IOException {
        if (!actions.containsKey(PREPARE) || !actions.containsKey(RELEASE)) return null;
        boolean tiger = id == 70;
        var paths = Act1SkillSequence.objectMotions(lookup, tiger ? 24020 : 24019);
        if (paths.size() < (tiger ? 28 : 21)) return null;
        var animations = new ArrayList<ResourceScript.Animation>();
        for (String path : paths) animations.add(ResourceScript.animation(lookup.read(path)));
        var clips = new Clips(paths, animations);
        var config = Act1PriestSupplement.settings(lookup);
        long cast = Act1SkillSequence.castingTime(fields, level, 600);
        int recovery = Math.max(1, Act1SkillSequence.staticValue(fields, 0, tiger ? 500 : 600));
        int life = Act1SkillSequence.levelValue(fields, level, 0, tiger ? 30000 : 20000, 1, 40000);
        int x = setting(config, tiger ? "bh x offset" : "hm x offset", 100);
        return tiger ? baekho(fields, lookup, actions, clips, config, cast, recovery, life, x, !SINGLE.equals(choice))
                : hyunmoo(fields, lookup, actions, clips, config, cast, recovery, life, x, level, !EMPTY.equals(choice));
    }

    /** 0x5BB910 advances the chase, jump, grab, hold and release stages on the summon clock. */
    private static Act1SkillSequence hyunmoo(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                             Map<String, String> actions, Clips clips, List<String> config,
                                             long cast, int recovery, int life, int x, int level, boolean showGrab)
            throws IOException {
        var visuals = new ArrayList<Visual>();
        long active = cast + clips.duration(0), expires = cast + life;
        pair(visuals, clips, 0, "玄武 · 召唤现身", cast, Math.min(active, expires), x, null);
        int range = Math.max(1, setting(config, "hm attack target x range", 150));
        int chaseOffset = Math.max(1, setting(config, "hm chase target offset", 150));
        int chaseDistance = Math.min(80, range);
        double targetX = x + Math.min(range, chaseOffset) + chaseDistance;
        double speed = Math.max(1, Act1SkillSequence.staticValue(fields, 2, 150));
        long attack = active + (long) Math.ceil(1000 * chaseDistance / speed);
        double attackX = x + chaseDistance;
        var boxes = attackBoxes(lookup.read(clips.paths().get(6)));
        long jumpAge = clips.before(6, 1);
        long moveTime = clips.before(6, 4) - jumpAge;
        int jumpDistance = Math.max(1, setting(config, "hm attack move distnace", 150));
        double gravity = setting(config, "hm attack gravity", -1500);
        var leap = jump(attack + jumpAge, moveTime, jumpDistance, gravity);
        long contactAge = grabContact(clips, boxes, attack, targetX - attackX, leap);
        showGrab &= contactAge < clips.duration(8) && attack + contactAge < expires;
        double endX = x;
        long end = expires;
        if (showGrab) {
            var chase = new SkillMotion(active, attack - active, speed, 0);
            pair(visuals, clips, 4, "玄武 · 追赶目标", active, attack, x, chase);
            long contact = attack + contactAge;
            long hold = attack + clips.duration(8);
            pair(visuals, clips, 6, "玄武 · 扑击", attack, contact, attackX, leap);
            for (int slot = 8; slot <= 9; slot++) {
                var animation = seek(clips.animations().get(slot), contactAge);
                if (!animation.frames().isEmpty())
                    visuals.add(new Visual(clips.paths().get(slot), "玄武 · 擒抱起始", BODY + slot - 8, contact, hold,
                            attackX, 0, leap, 1, true, new VisualStyle(animation)));
            }
            double heldX = attackX + leap.offsetAt(hold);
            long releaseFrame = clips.before(14, 7);
            int holdTime = Act1SkillSequence.levelValue(fields, level, 1, 7500, 1, 30000);
            long release = hold + Math.max(0, holdTime - releaseFrame);
            long detach = release + releaseFrame;
            long releaseEnd = release + clips.duration(14);
            holdHyunmoo(visuals, clips, hold, release, heldX);
            clips.add(visuals, 13, "玄武 · 擒抱闪光", BODY + 3, hold,
                    Math.min(release, hold + clips.duration(13)), heldX, 0, null, false);
            long releaseMoveTime = clips.before(14, 10) - releaseFrame;
            int releaseDistance = setting(config, "hm hold end move distance", 100);
            var exit = jump(detach, releaseMoveTime, releaseDistance, gravity);
            pair(visuals, clips, 14, "玄武 · 松开目标", release, releaseEnd, heldX, exit);
            clips.add(visuals, 16, "玄武 · 松开底层", BODY - 1, release, detach, heldX, 0, null, true);
            end = Math.max(end, releaseEnd);
            endX = heldX + exit.offsetAt(releaseEnd);
            String target = "玄武 · 哥布林";
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", target, 0, contact, targetX, null);
            // 0x54C870 keeps the target ahead until the tackle reaches it, then sets X to the Hyunmoo.
            long crossesTarget = leap.start() + (long) Math.ceil(1000 * (targetX - attackX) / leap.velocity());
            long stopMoving = Math.min(hold, leap.start() + moveTime);
            long moveTarget = crossesTarget < stopMoving ? Math.max(contact, crossesTarget) : hold;
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", "玄武 · 扑抱目标", contact, moveTarget, targetX, null);
            if (moveTarget < hold) {
                var targetMove = new SkillMotion(moveTarget, Math.max(0, stopMoving - moveTarget), leap.velocity(), 0);
                Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", "玄武 · 扑抱目标", moveTarget, hold, targetX, targetMove);
            }
            Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", target, detach, end, heldX, null);
            pair(visuals, clips, 2, "玄武 · 持续待机", releaseEnd, end, endX, null);
        } else {
            pair(visuals, clips, 2, "玄武 · 持续待机", active, end, x, null);
        }
        pair(visuals, clips, 17, "玄武 · 到期离场", end, end + clips.duration(17), endX, null);
        end = Math.max(end + clips.duration(17), floors(lookup, visuals, clips, 19, 2, "玄武", cast, end, x));
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions, PREPARE, RELEASE, cast, cast + recovery, end, false),
                List.copyOf(visuals), List.of(), showGrab ? "玄武演示追赶一名普通哥布林、扑击、擒抱和松开"
                : "玄武演示空场召唤、待机与离场");
    }

    /** 0x5BACC0 rewinds to frame six. 0x54CA10 draws the third layer on the target at Y-1, before the summon. */
    private static void holdHyunmoo(List<Visual> visuals, Clips clips, long hold, long release, double x) {
        long loop = Math.min(release, hold + clips.duration(10));
        for (int slot = 10; slot <= 12; slot++) {
            boolean onTarget = slot == 12;
            String label = onTarget ? "玄武 · 擒抱底层" : "玄武 · 持续擒抱";
            int layer = onTarget ? BODY - 1 : BODY + slot - 10;
            // The attachment cancels the target's Y-1 screen offset and uses its opposite facing.
            clips.add(visuals, slot, label, layer, hold, loop, x, 0, null, onTarget);
            var animation = clips.animations().get(slot);
            if (release > loop && animation.frames().size() > 6) {
                var repeat = new ResourceScript.Animation(true,
                        animation.frames().subList(6, animation.frames().size()), animation.warnings());
                visuals.add(new Visual(clips.paths().get(slot), label, layer, loop, release, x, 0, null, 1, true,
                        new VisualStyle(null, 0, List.of(), Map.of(), onTarget, 0, repeat)));
            }
        }
    }

    private static SkillMotion jump(long start, long time, int distance, double gravity) {
        return new SkillMotion(start, Math.max(0, time), time > 0 ? 1000.0 * distance / time : 0, 0,
                -gravity * time / 2000.0, gravity);
    }

    private static long grabContact(Clips clips, List<HitBox> boxes, long start, double distance, SkillMotion leap) {
        if (leap.velocity() <= 0) return Long.MAX_VALUE;
        long contact = Long.MAX_VALUE;
        for (var box : boxes) {
            long first = start + clips.before(6, box.frame());
            long end = start + clips.before(6, box.frame() + 1);
            long reach = leap.start() + (long) Math.ceil(1000 * Math.max(0, distance - box.right() - 12) / leap.velocity());
            long time = Math.max(first, reach);
            if (time < end && leap.offsetAt(time) + box.right() >= distance - 12)
                contact = Math.min(contact, time - start);
        }
        return contact;
    }

    /** 0x5B7550 changes the linked tigers' layers; 0x5B7260 sends a bolt to the next tiger. */
    private static Act1SkillSequence baekho(List<GameScript.Field> fields, GameScript.Lookup lookup,
                                            Map<String, String> actions, Clips clips, List<String> config,
                                            long cast, int recovery, int life, int x, boolean link) throws IOException {
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        // Start the linked preview with the first tiger already active and its skill cooldown elapsed.
        int cooldown = Math.max(0, GameScript.integer(GameScript.value(fields, "cool time"), 6000));
        long firstAge = Math.max(Math.max(cooldown - cast, clips.duration(0)), life - 12000);
        long firstExpiry = life - firstAge;
        long ready = cast + clips.duration(0);
        long chain = ready + clips.duration(4);
        int firstDelay = Math.max(0, setting(config, "bh init delay", 500));
        var bolts = link && chain + firstDelay < firstExpiry ? Act1SkillSequence.objectMotions(lookup, 24021) : List.<String>of();
        link &= bolts.size() >= 2;
        int gap = Math.max(160, Math.min(1500, Act1SkillSequence.staticValue(fields, 1, 150) + 50));
        long expires = cast + life;
        long end;
        if (link) {
            long earlyEnd = tiger(lookup, visuals, clips, -firstAge, firstExpiry, x + gap,
                    ready, chain, firstExpiry, true, true);
            end = Math.max(earlyEnd, tiger(lookup, visuals, clips, cast, expires, x,
                    ready, chain, firstExpiry, false, false));
            relay(fields, lookup, visuals, emitters, bolts, config, chain + firstDelay, firstExpiry, end, x, x + gap);
        } else {
            end = tiger(lookup, visuals, clips, cast, expires, x, expires, expires, expires, false, false);
        }
        var poses = Act1SkillSequence.castPoses(actions, PREPARE, RELEASE, cast, cast + recovery, end, false);
        return new Act1SkillSequence(poses, List.copyOf(visuals), List.copyOf(emitters),
                link ? "已有一只白虎，召唤第二只后演示来回传递的雷球和途中的哥布林受击"
                        : "白虎演示单个式神的召唤、持续光环与到期离场");
    }

    private static long tiger(GameScript.Lookup lookup, List<Visual> visuals, Clips clips, long cast, long expires,
                              int x, long linkStart, long chainStart, long unlink, boolean expiresLinked, boolean reverse)
            throws IOException {
        long active = cast + clips.duration(0);
        if (cast >= 0)
            tigerLayers(visuals, clips, 0, 4, "白虎 · 召唤现身", cast, Math.min(active, expires), x, false);
        tigerLayers(visuals, clips, 14, 4, "白虎 · 持续光环", active, linkStart, x, reverse);
        long vanish = expires;
        long floorEnd = floors(lookup, visuals, clips, 25, 3, "白虎", cast, linkStart, x);
        if (chainStart < unlink) {
            tigerLayers(visuals, clips, 4, 5, "白虎 · 光环转连锁", linkStart, chainStart, x, reverse);
            tigerLayers(visuals, clips, 18, 3, "白虎 · 连锁状态", chainStart, unlink, x, reverse);
            long returnEnd = unlink + clips.duration(9);
            if (expiresLinked) {
                tigerLayers(visuals, clips, 9, 2, "白虎 · 连锁解除", unlink, returnEnd, x, reverse);
                clips.add(visuals, 13, "白虎 · 连锁解除", BELOW + 4, unlink, returnEnd, x, -1, null, reverse);
                vanish = returnEnd;
            } else {
                tigerLayers(visuals, clips, 9, 5, "白虎 · 连锁解除", unlink, Math.min(returnEnd, expires), x, reverse);
                tigerLayers(visuals, clips, 14, 4, "白虎 · 持续光环", returnEnd, expires, x, reverse);
                floorEnd = Math.max(floorEnd, floors(lookup, visuals, clips, 25, 3, "白虎", unlink, expires, x));
            }
        }
        long end = vanish + clips.duration(21);
        tigerLayers(visuals, clips, 21, expiresLinked ? 2 : 4, "白虎 · 到期离场", vanish, end, x, reverse);
        return Math.max(end, floorEnd);
    }

    private static void relay(List<GameScript.Field> fields, GameScript.Lookup lookup, List<Visual> visuals,
                              List<SkillParticleEmitter> emitters, List<String> bolts, List<String> config,
                              long start, long expires, long end, double left, double right) throws IOException {
        String path = OBJECT + "particle/baekho.ptl";
        String motion = lookup.read(path);
        if (motion == null) return;
        double speed = ParticleScript.parse(motion).axes().get(0).start();
        if (speed <= 0 || !Double.isFinite(speed)) return;
        int xOffset = setting(config, "bh thunder x offset", 10);
        int z = setting(config, "bh thunder z offset", 50);
        int pause = Act1SkillSequence.staticValue(fields, 10, 1000);
        String spark = OBJECT + "animation/baekhothunderhit.ani";
        String flash = OBJECT + "animation/baekhothundersend.ani";
        String smoke = OBJECT + "particle/baekhothundersmoke.ptl";
        long impactLength = duration(lookup, spark), flashLength = duration(lookup, flash);
        boolean trail = lookup.read(smoke) != null;
        var box = attackBoxes(lookup.read(bolts.get(0))).stream().findFirst().orElse(null);
        double targetX = (left + right) / 2;
        long targetClock = 0;
        for (int hop = 0; start < expires && visuals.size() < 220; hop++) {
            boolean reverse = (hop & 1) != 0;
            int direction = reverse ? -1 : 1;
            double from = (reverse ? right : left) + direction * xOffset;
            double to = (reverse ? left : right) - direction * xOffset;
            long travel = Math.max(1, (long) (1000 * Math.abs(to - from) / speed));
            long arrival = start + travel;
            long stop = Math.min(arrival, expires);
            var flight = new SkillMotion(start, travel, direction * speed, 0);
            VisualStyle facing = reverse ? REVERSED : VisualStyle.DEFAULT;
            visuals.add(new Visual(bolts.get(0), "白虎 · 连锁雷球", BODY + 2, start, stop, from, -z, flight, 1, true, facing));
            visuals.add(new Visual(bolts.get(1), "白虎 · 连锁雷光", BODY + 3, start, stop, from, -z, flight, 1, true, facing));
            if (flashLength > 0)
                visuals.add(new Visual(flash, "白虎 · 发射闪光", BODY + 1, start, start + flashLength, from, -z, null, 1, true, facing));
            if (trail) emitters.add(new SkillParticleEmitter(smoke, "白虎 · 雷球余光", start, stop - start,
                    0, from, 0, z, Double.NaN, 1, true, 1, reverse, flight));
            long contact = start + Math.max(1, Math.round(1000 * Math.abs(targetX - from) / speed));
            if (box != null && contact < stop) {
                long hitStart = start + Math.max(1, Math.round(1000 * Math.max(0, Math.abs(targetX - from) - box.right()) / speed));
                long reaction = Math.max(targetClock, hitStart);
                Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "白虎 · 哥布林", targetClock, hitStart, targetX, null);
                if (impactLength > 0)
                    visuals.add(new Visual(spark, "白虎 · 雷球命中", BODY + 4, hitStart, hitStart + impactLength,
                            targetX, -z, null, 1, true, facing));
                targetClock = Math.max(targetClock, hitStart + Math.min(travel, 240));
                Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", "白虎 · 受击目标", reaction, targetClock, targetX, null);
            }
            start = arrival + (reverse ? pause : 0);
        }
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "白虎 · 哥布林", targetClock, end, targetX, null);
    }

    private record HitBox(int frame, int right) {}

    private static List<HitBox> attackBoxes(String text) {
        var boxes = new ArrayList<HitBox>();
        int frame = -1;
        for (var field : GameScript.fields(text)) {
            if (field.tag().matches("frame[0-9]+")) frame = GameScript.integer(field.tag().substring(5), -1);
            if (frame >= 0 && field.tag().equals("attack box") && field.values().size() >= 6)
                boxes.add(new HitBox(frame, GameScript.integer(field.values().get(0), 0)
                        + GameScript.integer(field.values().get(3), 0)));
        }
        return boxes;
    }

    private static ResourceScript.Animation seek(ResourceScript.Animation animation, long age) {
        var frames = new ArrayList<ResourceScript.Frame>();
        for (var frame : animation.frames()) {
            if (age >= frame.delay()) {
                age -= frame.delay();
            } else {
                frames.add(frame.withDelay((int) (frame.delay() - age)));
                age = 0;
            }
        }
        return new ResourceScript.Animation(false, List.copyOf(frames), animation.warnings());
    }

    private static int setting(List<String> config, String name, int fallback) {
        return Act1PriestSupplement.setting(config, name, fallback);
    }

    private static long duration(GameScript.Lookup lookup, String path) throws IOException {
        String text = lookup.read(path);
        return text == null ? 0 : Act1SkillSequence.duration(ResourceScript.animation(text).frames(), Integer.MAX_VALUE, null);
    }

    private static void pair(List<Visual> visuals, Clips clips, int first, String label, long start, long end,
                             double x, SkillMotion motion) {
        clips.add(visuals, first, label, BODY, start, end, x, 0, motion, false);
        clips.add(visuals, first + 1, label, BODY + 1, start, end, x, 0, motion, false);
    }

    private static void tigerLayers(List<Visual> visuals, Clips clips, int first, int count, String label,
                                    long start, long end, double x, boolean reverse) {
        for (int i = 0; i < count; i++)
            clips.add(visuals, first + i, label, i < 2 ? BODY + i : BELOW + i, start, end, x,
                    i == 4 || first == 18 && i == 2 ? -1 : 0, null, reverse);
    }

    private static long floors(GameScript.Lookup lookup, List<Visual> visuals, Clips clips, int first, int count,
                               String label, long cast, long expires, int x) throws IOException {
        long end = expires;
        for (int i = 0; i < count; i++)
            end = Math.max(end, Act1PriestSupplement.loopingTalisman(lookup, visuals, clips.paths().get(first + i),
                    label + " · 地面法阵", FLOOR + i, cast, (int) (expires - cast), x, 1, 1, 3));
        return end;
    }

    private record Clips(List<String> paths, List<ResourceScript.Animation> animations) {
        long duration(int slot) {
            return before(slot, Integer.MAX_VALUE);
        }

        long before(int slot, int frame) {
            return Act1SkillSequence.duration(animations.get(slot).frames(), frame, null);
        }

        void add(List<Visual> visuals, int slot, String label, int layer, long start, long end, double x, double y,
                 SkillMotion motion, boolean reverse) {
            if (end > start && !animations.get(slot).frames().isEmpty())
                visuals.add(new Visual(paths.get(slot), label, layer, start, end, x, y, motion, 1, true,
                        reverse ? REVERSED : VisualStyle.DEFAULT));
        }
    }
}
