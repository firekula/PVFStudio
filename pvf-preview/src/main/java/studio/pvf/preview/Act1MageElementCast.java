package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Mage state 10 elemental releases and their passive-object animation clocks. */
final class Act1MageElementCast {
    static final String CHARGED = "mage-element:charged";
    private static final String FROST_HIT = "mage-element:frost-hit";
    private static final String FROST_CHARGED_HIT = "mage-element:frost-charged-hit";
    private static final String MAGE = "character/mage/";
    private static final String EFFECT = MAGE + "effect/animation/";
    private static final int OBJECT_LAYER = 3600;

    private enum Element {
        JOL("launchjol", 15, 2, 500, 600, 1, 800, "杰克爆弹", "演示水平飞行，在射程末端模拟碰撞引爆"),
        FROST("launchfrosthead", 16, 4, 500, 600, 5, 800, "冰霜雪人", "演示不按方向键时的下落、反弹和消失；追踪、寒冰爆炸、减速与冰冻取决于实际目标"),
        FLUORESCENT("launchfluorescent", 17, 3, 500, 600, 2, 800, "雷光珠", "演示围绕静止施放者逐渐扩散的雷光珠"),
        CAT("launchblackcat", 18, 1, 600, 600, 1, 800, "暗影夜猫", "演示向前扑出、掉头并回到静止施放者"),
        VOID("void", 26, 2, 800, 700, 2, 1000, "虚无之球", "演示缓慢飞行和消散；多段伤害取决于敌人与虚无之球的重叠");

        final String stem;
        final int id, action, cast, recovery, chargeColumn, charge;
        final String label, note;

        Element(String stem, int id, int action, int cast, int recovery, int chargeColumn, int charge,
                String label, String note) {
            this.stem = stem;
            this.id = id;
            this.action = action;
            this.cast = cast;
            this.recovery = recovery;
            this.chargeColumn = chargeColumn;
            this.charge = charge;
            this.label = label;
            this.note = note;
        }

        String path() { return "skill/mage/" + stem + ".skl"; }
    }

    private Act1MageElementCast() {}

    static Act1SkillSequence find(String path, String text, GameScript.Lookup lookup, Map<String, String> actions,
                                  int level, String choice) throws IOException {
        var element = element(path, lookup);
        boolean chargedFrostHit = element == Element.FROST && choice.equals(FROST_CHARGED_HIT);
        boolean frostHit = element == Element.FROST && (choice.equals(FROST_HIT) || chargedFrostHit);
        if (element == null || !choice.isEmpty() && !choice.equals(CHARGED) && !frostHit) return null;
        String prepare = "throw motion " + element.action + "-1";
        String release = "throw motion " + element.action + "-2";
        if (!actions.containsKey(prepare) || !actions.containsKey(release)) return null;
        var fields = GameScript.fields(text);
        boolean charged = choice.equals(CHARGED) || chargedFrostHit;
        long cast = Act1SkillSequence.castingTime(fields, level, element.cast);
        if (charged) cast += Act1SkillSequence.staticValue(fields, element.chargeColumn, element.charge);
        long recovered = cast + Math.max(1, Act1SkillSequence.staticValue(fields, 0, element.recovery));
        var visuals = new ArrayList<Visual>();
        var emitters = new ArrayList<SkillParticleEmitter>();
        long end = switch (element) {
            case JOL -> jol(lookup, fields, level, charged, cast, visuals, emitters);
            case FROST -> frost(lookup, fields, level, charged, cast, visuals, emitters, frostHit);
            case FLUORESCENT -> fluorescent(lookup, fields, level, charged, cast, visuals);
            case CAT -> cat(lookup, fields, level, charged, cast, visuals);
            case VOID -> voidBall(lookup, fields, level, charged, cast, visuals);
        };
        if (end < 0) return null;
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions, prepare, release, cast, recovered, end, false),
                List.copyOf(visuals), List.copyOf(emitters), chargedFrostHit
                ? "冰霜雪人满蓄力首次命中哥布林时，在目标位置额外生成冰爆粒子。"
                : frostHit ? "冰霜雪人演示首次下落时与哥布林接触及原版寒冰爆炸，目标位置为固定取样。"
                : element.label + element.note);
    }

    static GameEffectRecipes.Effect chargeChoice(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1) return null;
        var element = element(path, lookup);
        return element == null ? null : new GameEffectRecipes.Effect(CHARGED, element.label + " · 满蓄力", "", path,
                "使用当前等级的满蓄力参数");
    }

    static GameEffectRecipes.Effect frostHitChoice(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (!act1 || element(path, lookup) != Element.FROST
                || relative(lookup, 23009, "Animation/LaunchFrostHeadHit.ani") == null) return null;
        return new GameEffectRecipes.Effect(FROST_HIT, "冰霜雪人 · 命中", "", path,
                "演示雪人接触目标后触发的寒冰爆炸");
    }

    static GameEffectRecipes.Effect frostChargedHitChoice(String path, GameScript.Lookup lookup, boolean act1) throws IOException {
        if (frostHitChoice(path, lookup, act1) == null || GameEffectRecipes.passiveObject(lookup, 23026) == null)
            return null;
        return new GameEffectRecipes.Effect(FROST_CHARGED_HIT, "冰霜雪人 · 满蓄力命中", "", path,
                "展示满蓄力首次命中时附加的冰爆粒子");
    }

    private static Element element(String path, GameScript.Lookup lookup) throws IOException {
        for (var element : Element.values()) {
            if (element.path().equals(path)) {
                String catalog = GameScript.catalog(lookup.read("skill/mageskill.lst")).get(element.id);
                return path.equals("skill/" + catalog) ? element : null;
            }
        }
        return null;
    }

    /** #23008 waits for its startup ANI, then loads LaunchJOL.ptl and sends #23016 on collision. */
    private static long jol(GameScript.Lookup lookup, List<GameScript.Field> fields, int level, boolean charged,
                            long cast, List<Visual> visuals, List<SkillParticleEmitter> emitters) throws IOException {
        var motions = objectAnimations(lookup, 23008);
        var trajectory = movement(lookup, "launchjol");
        if (motions.size() < 4 || trajectory == null) return -1;
        double speed = trajectory.axes().get(0).start();
        double scale = size(fields, level, charged ? 5 : 4, charged);
        double x = 68, z = 53;
        long startup = duration(lookup, motions.get(0));
        long flight = cast + startup;
        long end = flight + life(fields, level, charged, 700, 1050);
        var motion = new SkillMotion(flight, end - flight, speed, 0);
        visuals.add(new Visual(motions.get(0), "杰克爆弹 · 出手", OBJECT_LAYER, cast, flight, x, -z, null, scale, true));
        visuals.add(new Visual(motions.get(2), "杰克爆弹 · 火光", OBJECT_LAYER - 1, cast, flight, x, -z, null, scale, true));
        visuals.add(new Visual(motions.get(1), "杰克爆弹 · 飞行", OBJECT_LAYER, flight, end, x, -z, motion, scale, true));
        visuals.add(new Visual(motions.get(3), "杰克爆弹 · 尾焰", OBJECT_LAYER - 1, flight, end, x, -z, motion, scale, true));
        int blastSize = Act1SkillSequence.levelValue(fields, level, charged ? 7 : 6, charged ? 1100 : 1000, 1, 4000);
        double blastScale = (blastSize * 6 / 100) / 100.0;
        return explosionParticles(lookup, 23016, "杰克爆弹 · 爆炸", end,
                x + motion.offsetAt(end), z, blastScale, visuals, emitters);
    }

    /** Generic explosion objects use a root ANI for damage and separate emitters for the visual effects. */
    private static long explosionParticles(GameScript.Lookup lookup, int id, String label, long start, double x,
                                            double z, double scale, List<Visual> visuals,
                                            List<SkillParticleEmitter> emitters) throws IOException {
        String object = GameEffectRecipes.passiveObject(lookup, id);
        if (object == null) return start;
        var fields = GameScript.fields(lookup.read(object));
        String root = GameScript.resolve(object, GameScript.value(fields, "basic motion"), lookup);
        if (root == null) return start;
        long emissionLimit = duration(lookup, root);
        if (emissionLimit <= 0) return start;
        long end = start + emissionLimit;
        visuals.add(new Visual(root, label + "判定", OBJECT_LAYER, start, end, x, -z, null, scale, true));
        for (String reference : GameScript.values(fields, "string data")) {
            if (emitters.size() >= 4 || !GameScript.normalize(reference).endsWith(".ptl")) continue;
            String path = GameScript.resolve(object, reference, lookup);
            if (path == null) continue;
            var script = ParticleScript.parse(lookup.read(path));
            long emission = script.maximum() > 1 && script.frequency() > 0
                    ? (long)Math.ceil((script.maximum() - 1) * 1000 / script.frequency()) + 1 : 1;
            emission = Math.min(emissionLimit, emission);
            if (emission <= 0) continue;
            emitters.add(new SkillParticleEmitter(path, label, start, emission, 0, x, 0, z, Double.NaN, scale));
            end = Math.max(end, start + emission + SkillParticleEmitter.tail(script));
        }
        return end;
    }

    /** #23009 alternates the airborne ANI and one landing frame, with a new PTL bounce each time. */
    private static long frost(GameScript.Lookup lookup, List<GameScript.Field> fields, int level, boolean charged,
                              long cast, List<Visual> visuals, List<SkillParticleEmitter> emitters, boolean hit) throws IOException {
        var motions = objectAnimations(lookup, 23009);
        var trajectory = movement(lookup, "launchfrosthead");
        if (motions.size() < 2 || trajectory == null || trajectory.gravity() <= 0) return -1;
        addFrostCast(lookup, cast, visuals);
        double x = 42, z = 100;
        double speed = trajectory.axes().get(0).start();
        double velocity = GameScript.integer(GameScript.values(fields, "static data").stream().skip(2).findFirst().orElse("0"), 0);
        double bounce = Math.max(1, Act1SkillSequence.staticValue(fields, 4, 300));
        double scale = size(fields, level, charged ? 6 : 5, charged);
        long end = cast + life(fields, level, charged, 1600, 2400);
        if (hit) {
            return frostHit(lookup, visuals, emitters, motions.get(0), cast, end, x, z, speed, velocity, trajectory.gravity(), scale, charged);
        }
        long groundDelay = Math.max(1, duration(lookup, motions.get(1)));
        String jumpEffect = relative(lookup, 23009, "Animation/LaunchFrostHeadJumpEffect.ani");
        long jumpEffectDuration = jumpEffect == null ? 0 : duration(lookup, jumpEffect);
        long finish = end;
        long start = cast;
        while (start < end) {
            double g = trajectory.gravity();
            long airtime = Math.max(1, (long)Math.ceil(1000 * (velocity + Math.sqrt(velocity * velocity + 2 * g * z)) / g));
            long land = Math.min(end, start + airtime);
            var motion = new SkillMotion(start, land - start, speed, 0, velocity, -g, z);
            visuals.add(new Visual(motions.get(0), "冰霜雪人 · 跳跃", OBJECT_LAYER, start, land, x, 0, motion, scale, true));
            x += motion.offsetAt(land);
            z = motion.heightAt(land);
            if (land >= end) break;
            long next = Math.min(end, land + groundDelay);
            z = 0;
            visuals.add(new Visual(motions.get(1), "冰霜雪人 · 落地", OBJECT_LAYER, land, next, x, 0, null, scale, true));
            if (jumpEffect != null) {
                visuals.add(new Visual(jumpEffect, "冰霜雪人 · 落地飞雪", OBJECT_LAYER + 1, land,
                        land + jumpEffectDuration, x, 0, null, 1, true));
                finish = Math.max(finish, land + jumpEffectDuration);
            }
            start = next;
            velocity = bounce;
        }
        String disappear = relative(lookup, 23009, "Animation/LaunchFrostHeadDisappear.ani");
        if (disappear != null) {
            long fade = end + duration(lookup, disappear);
            visuals.add(new Visual(disappear, "冰霜雪人 · 消失", OBJECT_LAYER, end, fade, x, -z, null, scale, true));
            finish = Math.max(finish, fade);
        }
        return finish;
    }

    /** 0x5B3470 draws the hit ANI at collision with the snowman's scale and target depth. */
    private static long frostHit(GameScript.Lookup lookup, List<Visual> visuals, List<SkillParticleEmitter> emitters,
                                 String flight, long cast, long expiry, double x, double z, double speed,
                                 double velocity, double gravity, double scale, boolean charged) throws IOException {
        String explosion = relative(lookup, 23009, "Animation/LaunchFrostHeadHit.ani");
        if (explosion == null) return -1;
        double contactHeight = 25;
        long travel = Math.max(1, (long)Math.ceil(1000 *
                (velocity + Math.sqrt(velocity * velocity + 2 * gravity * (z - contactHeight))) / gravity));
        long contact = cast + travel;
        if (contact >= expiry) return -1;
        var motion = new SkillMotion(cast, travel, speed, 0, velocity, -gravity, z);
        double targetX = x + motion.offsetAt(contact);
        long end = contact + duration(lookup, explosion);
        if (end == contact) return -1;
        visuals.add(new Visual(flight, "冰霜雪人 · 跳跃", OBJECT_LAYER, cast, contact, x, 0, motion, scale, true));
        visuals.add(new Visual(explosion, "冰霜雪人 · 寒冰爆炸", OBJECT_LAYER + 1, contact, end,
                targetX, -motion.heightAt(contact), null, scale, true));
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "冰霜雪人 · 演示目标", 0, contact, targetX, null);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "damage1", "冰霜雪人 · 目标受击", contact, end, targetX, null);
        // The first confirmed full-charge hit sends #23026 at the target's position with 100% particle scale.
        long finish = charged ? Math.max(end, explosionParticles(lookup, 23026, "冰霜雪人 · 满蓄力冰爆",
                contact, targetX, 0, 1, visuals, emitters)) : end;
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "冰霜雪人 · 演示目标", end, finish, targetX, null);
        return finish;
    }

    private static void addFrostCast(GameScript.Lookup lookup, long cast, List<Visual> visuals) throws IOException {
        String path = EFFECT + "launchfrosthead.ani";
        var frames = frames(lookup, path);
        if (frames.size() < 3) return;
        int first = (int)Math.min(cast, frames.get(0).delay());
        var style = new VisualStyle(null, 0, List.of(), Map.of(
                0, new FrameOverride(0, 0, first), 1, new FrameOverride(0, 0, (int)(cast - first))));
        long end = cast + Act1SkillSequence.duration(frames.subList(2, frames.size()), Integer.MAX_VALUE, null);
        visuals.add(new Visual(path, "冰霜雪人 · 施法", 3501, 0, end, 0, 0, null, 1, true, style));
    }

    /** #23010 creates the original pair of looping ANI for each PTL bearing. */
    private static long fluorescent(GameScript.Lookup lookup, List<GameScript.Field> fields, int level, boolean charged,
                                    long cast, List<Visual> visuals) throws IOException {
        var motions = objectAnimations(lookup, 23010);
        var trajectory = movement(lookup, "launchfluorescent");
        if (motions.size() < 2 || trajectory == null) return -1;
        int count = Math.min(12, Math.max(1, Act1SkillSequence.staticValue(fields, 1, 3)));
        long life = life(fields, level, charged, 2000, 3000);
        double scale = size(fields, level, charged ? 5 : 4, charged);
        String shoot = EFFECT + "launchfluorescentshoot.ani";
        long shotDuration = duration(lookup, shoot);
        if (shotDuration > 0) visuals.add(new Visual(shoot, "雷光珠 · 出手", 3501, cast, cast + shotDuration, 44, -60));
        for (int i = 0; i < count; i++) {
            var orbit = new SkillMotion.Orbit(trajectory.axes().get(0), trajectory.axes().get(1), trajectory.term(),
                    trajectory.changing(), i * 360.0 / count);
            var motion = SkillMotion.orbit(cast, life, orbit);
            for (int layer = 0; layer < 2; layer++) {
                visuals.add(new Visual(motions.get(layer), "雷光珠 · 环绕", OBJECT_LAYER + layer,
                        cast, cast + life, 0, -60, motion, scale, true));
            }
        }
        return cast + Math.max(life, shotDuration);
    }

    /** #23011 has five paired clips, with separate linear outbound and return position updates. */
    private static long cat(GameScript.Lookup lookup, List<GameScript.Field> fields, int level, boolean charged,
                            long cast, List<Visual> visuals) throws IOException {
        var motions = objectAnimations(lookup, 23011);
        if (motions.size() < 10) return -1;
        for (int i = 0; i < 2; i++) {
            String path = EFFECT + "launchblackcatcast" + (i + 1) + ".ani";
            if (lookup.read(path) != null) visuals.add(new Visual(path, "暗影夜猫 · 施法", 3501 + i, 0, cast, 0, 0));
        }
        // The client subtracts the authored sprite reach before converting range into movement time.
        int distance = Act1SkillSequence.levelValue(fields, level, charged ? 5 : 4, charged ? 270 : 180, 141, 2000) - 140;
        double rate = 1 + Act1SkillSequence.levelValue(fields, level, charged ? 3 : 2, charged ? 500 : 0, 0, 5000) / 1000.0;
        double scale = size(fields, level, charged ? 7 : 6, charged);
        long launch = cast + (long)Math.ceil(80 / rate);
        long turn = cast + (long)Math.ceil((80 + 2 * distance) / rate);
        long back = cast + (long)Math.ceil((420 + 2 * distance) / rate);
        long returned = cast + (long)Math.ceil((420 + 4 * distance) / rate);
        long end = cast + (long)Math.ceil((500 + 4 * distance) / rate);
        long outbound = Math.max(1, turn - launch);
        long inbound = Math.max(1, returned - back);
        var forward = new SkillMotion(launch, outbound, 1000.0 * distance / outbound, 0, -10000.0 / outbound, 0, 60);
        var backward = new SkillMotion(back, inbound, -1000.0 * distance / inbound, 0, 10000.0 / inbound, 0, 50);
        addCatPair(lookup, visuals, motions, 0, "暗影夜猫 · 出手", cast, launch, 0, -60, null, scale, rate);
        addCatPair(lookup, visuals, motions, 2, "暗影夜猫 · 前扑", launch, turn, 84, 0, forward, scale, rate);
        addCatPair(lookup, visuals, motions, 4, "暗影夜猫 · 掉头", turn, back, 179 + distance, 0, null, scale, rate);
        addCatPair(lookup, visuals, motions, 6, "暗影夜猫 · 返回", back, returned, 84 + distance, 0, backward, scale, rate);
        addCatPair(lookup, visuals, motions, 8, "暗影夜猫 · 收回", returned, end, 0, -60, null, scale, rate);
        return end;
    }

    private static void addCatPair(GameScript.Lookup lookup, List<Visual> visuals, List<String> motions, int offset,
                                   String label, long start, long end, double x, double y, SkillMotion motion,
                                   double scale, double rate) throws IOException {
        for (int i = 0; i < 2; i++) {
            String path = motions.get(offset + i);
            long pacedDuration = rate == 1 ? 0 : Math.round(duration(lookup, path) / rate);
            visuals.add(new Visual(path, label, OBJECT_LAYER + i, start, end, x, y, motion, scale, true,
                    new VisualStyle(null, pacedDuration, List.of())));
        }
    }

    /** #23012 rewinds frame 7 to frame 3 until its final 300 ms, then jumps to frame 8. */
    private static long voidBall(GameScript.Lookup lookup, List<GameScript.Field> fields, int level, boolean charged,
                                 long cast, List<Visual> visuals) throws IOException {
        var motions = objectAnimations(lookup, 23012);
        var trajectory = movement(lookup, "void");
        if (motions.size() < 2 || trajectory == null) return -1;
        long life = life(fields, level, charged, 5000, 7500);
        long fade = cast + life;
        long end = fade + 300;
        double scale = size(fields, level, charged ? 5 : 4, charged);
        var motion = new SkillMotion(cast, end - cast, trajectory.axes().get(0).start(), 0);
        for (int layer = 0; layer < 2; layer++) {
            String path = motions.get(layer);
            var frames = frames(lookup, path);
            if (frames.size() < 9) return -1;
            long startup = Act1SkillSequence.duration(frames, 7, null);
            long cycle = Act1SkillSequence.duration(frames.subList(3, 7), 4, null);
            if (startup == 0 || cycle <= 0) return -1;
            int order = OBJECT_LAYER + layer;
            visuals.add(voidClip(path, "虚无之球 · 生成", order, cast, Math.min(fade, cast + startup), motion,
                    scale, frameSlice(frames, 0, 7, 0)));
            if (cast + startup < fade) {
                visuals.add(voidClip(path, "虚无之球 · 飞行", order, cast + startup, fade, motion,
                        scale, frameSlice(frames, 3, 7, cycle)));
            }
            visuals.add(voidClip(path, "虚无之球 · 消散", order, fade, end, motion, scale,
                    frameSlice(frames, 8, frames.size(), 0)));
        }
        return end;
    }

    private static Visual voidClip(String path, String label, int layer, long start, long end, SkillMotion motion,
                                   double scale, VisualStyle style) {
        return new Visual(path, label, layer, start, end, 38, -62, motion, scale, true, style);
    }

    private static VisualStyle frameSlice(List<ResourceScript.Frame> frames, int first, int end, long period) {
        var overrides = new HashMap<Integer, FrameOverride>();
        for (int i = 0; i < frames.size(); i++) {
            if (i < first || i >= end) overrides.put(i, new FrameOverride(0, 0, 0));
        }
        return new VisualStyle(null, 0, List.of(), overrides, false, period);
    }

    private static List<String> objectAnimations(GameScript.Lookup lookup, int id) throws IOException {
        String owner = GameEffectRecipes.passiveObject(lookup, id);
        if (owner == null) return List.of();
        var fields = GameScript.fields(lookup.read(owner));
        var refs = new ArrayList<String>();
        refs.add(GameScript.value(fields, "basic motion"));
        refs.addAll(GameScript.values(fields, "etc motion"));
        var result = new ArrayList<String>();
        for (String ref : refs) {
            if (ref.isEmpty()) continue;
            String resolved = GameScript.resolve(owner, ref, lookup);
            if (resolved == null) return List.of();
            result.add(resolved);
        }
        return result;
    }

    private static String relative(GameScript.Lookup lookup, int id, String reference) throws IOException {
        String owner = GameEffectRecipes.passiveObject(lookup, id);
        return owner == null ? null : GameScript.resolve(owner, reference, lookup);
    }

    private static ParticleScript movement(GameScript.Lookup lookup, String stem) throws IOException {
        String text = lookup.read(MAGE + "particle/" + stem + ".ptl");
        return text == null ? null : ParticleScript.parse(text);
    }

    private static List<ResourceScript.Frame> frames(GameScript.Lookup lookup, String path) throws IOException {
        String text = lookup.read(path);
        return text == null ? List.of() : ResourceScript.animation(text).frames();
    }

    private static long duration(GameScript.Lookup lookup, String path) throws IOException {
        return Act1SkillSequence.duration(frames(lookup, path), Integer.MAX_VALUE, null);
    }

    private static long life(List<GameScript.Field> fields, int level, boolean charged, int normal, int full) {
        return Act1SkillSequence.levelValue(fields, level, charged ? 3 : 2, charged ? full : normal, 1, 15000);
    }

    private static double size(List<GameScript.Field> fields, int level, int column, boolean charged) {
        return Act1SkillSequence.levelValue(fields, level, column, charged ? 1200 : 1000, 1, 4000) / 1000.0;
    }
}
