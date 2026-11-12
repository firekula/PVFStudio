package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** Mage state 24 and the corresponding CNChaser states 6–7. */
final class Act1ChaserPress {
    private static final String CHARACTER = "character/mage/mage.chr";
    private static final String ACTION = "etc motion:8";
    private static final String EFFECTS = "character/mage/effect/animation/chasersexplosion/";
    private static final int SAMPLE_CHASERS = 3;
    private static final int SMASH_X = 77;
    private static final double GRAVITY = 1500;

    private Act1ChaserPress() {}

    static Act1SkillSequence find(String text, GameScript.Lookup lookup, Map<String, String> actions, int level)
            throws IOException {
        String reference = actions.get(ACTION);
        if (reference == null || !GameScript.normalize(reference).endsWith("/chasersexplosion.ani")) return null;
        var frames = Act1SkillSequence.frames(CHARACTER, reference, lookup);
        if (frames.size() < 11) return null;
        var impactMotions = Act1SkillSequence.objectMotions(lookup, 23030);
        var chaserMotions = Act1SkillSequence.objectMotions(lookup, 23002);
        if (impactMotions.isEmpty() || chaserMotions.isEmpty()) return null;
        String object = GameEffectRecipes.passiveObject(lookup, 23030);
        String explosion = GameScript.resolve(object, "Animation/ChasersExplosion/Exp.ani", lookup);
        if (explosion == null) return null;

        var config = GameScript.fields(lookup.read("data/skill/mage.sd"));
        var ints = GameScript.values(config, "int");
        var floats = GameScript.values(config, "float");
        double jumpHeight = setting(ints, "ce jump height", 70, 1, 1000);
        double smashHeight = setting(ints, "ce smash height", 10, 0, jumpHeight);
        double centerX = setting(ints, "ce x center", -76, -1000, 1000);
        double centerZ = setting(ints, "ce z center", 29, 0, 1000);
        double startAngle = setting(ints, "ce start angle", 120, -360, 360);
        double readyDistance = setting(ints, "ce ready distance", 80, 0, 1000);
        long gotoTerm = (long)setting(ints, "ce goto term", 300, 1, 10000);
        double chargeRate = setting(floats, "ce charge effect rate", 1.5, .01, 10);

        var skill = GameScript.fields(text);
        int casting = Math.max(1, Act1SkillSequence.castingTime(skill, level, 1000));
        long jump = Act1SkillSequence.duration(frames, 2, null);
        long charge = Act1SkillSequence.duration(frames, 6, null);
        long release = charge + casting;
        long impact = release + frames.get(7).delay() + frames.get(8).delay();
        long recovery = impact + frames.subList(9, frames.size()).stream().mapToLong(ResourceScript.Frame::delay).sum();
        if (charge <= jump || impact <= release || recovery <= impact) return null;

        double riseSeconds = (charge - jump) / 1000.0;
        var rise = new SkillMotion(jump, charge - jump, 0, 0,
                2 * jumpHeight / riseSeconds, -2 * jumpHeight / (riseSeconds * riseSeconds));
        var hover = new SkillMotion(charge, casting, 0, 0, 0, 0, jumpHeight);
        var strike = new SkillMotion(release, impact - release, 0, 0,
                1000 * (smashHeight - jumpHeight) / (impact - release), 0, jumpHeight);
        var poses = new ArrayList<Pose>();
        poses.add(new Pose(ACTION, 0, charge, null, false, rise, 0, 6));
        poses.add(new Pose(ACTION, charge, release, casting, false, hover, 6, 7));
        poses.add(new Pose(ACTION, release, recovery, null, false, strike, 7));
        long actorEnd = addLanding(poses, lookup, actions, recovery, smashHeight);

        var visuals = new ArrayList<Visual>();
        var chargeSizes = new ArrayList<VisualKeyframe>();
        double size = Math.max(1, Math.min(400, Act1SkillSequence.staticValue(skill, 0, 50))) / 100.0;
        double sizePerChaser = Math.min(100, Act1SkillSequence.staticValue(skill, 1, 6)) / 100.0;
        chargeSizes.add(new VisualKeyframe(0, size * chargeRate, 1));
        long approach = Math.min(gotoTerm, casting);
        long interval = Math.max(0, (casting - approach - 140) / SAMPLE_CHASERS);
        long sizeUpDuration = animationDuration(lookup, EFFECTS + "sizeup.ani");
        long effectEnd = release;
        for (int i = 0; i < SAMPLE_CHASERS; i++) {
            double angle = Math.toRadians(startAngle + i * (360 / SAMPLE_CHASERS));
            double readyX = centerX + Math.cos(angle) * readyDistance;
            double readyZ = centerZ + jumpHeight + Math.sin(angle) * readyDistance;
            long move = charge + i * interval;
            long absorb = move + approach;
            addChaser(visuals, chaserMotions.get(0), i, charge, move, absorb, readyX, readyZ, centerX,
                    jumpHeight + centerZ);

            chargeSizes.add(new VisualKeyframe(absorb - charge - 1, size * chargeRate, 1));
            size += sizePerChaser;
            chargeSizes.add(new VisualKeyframe(absorb - charge, size * chargeRate, 1));
            long flashEnd = absorb + sizeUpDuration;
            effectEnd = Math.max(effectEnd, flashEnd);
            visuals.add(new Visual(EFFECTS + "sizeup.ani", "炫纹强压 · 炫纹汇入", 3502, absorb, flashEnd,
                    centerX, -jumpHeight - centerZ + 1, null, size, true));
        }
        visuals.add(new Visual(EFFECTS + "charge.ani", "炫纹强压 · 聚合", 3501, charge, release, centerX,
                -centerZ, hover, 1, true, new VisualStyle(null, 0, chargeSizes)));
        long smashEnd = Math.min(recovery, release + animationDuration(lookup, EFFECTS + "smash.ani"));
        visuals.add(new Visual(EFFECTS + "smash.ani", "炫纹强压 · 挥击", 3501, release, smashEnd,
                0, 0, strike, 1, true));
        String floor = impactMotions.get(0);
        long floorEnd = impact + animationDuration(lookup, floor);
        long explosionEnd = impact + animationDuration(lookup, explosion);
        visuals.add(new Visual(floor, "炫纹强压 · 地面冲击", 300, impact, floorEnd,
                SMASH_X, 1 - smashHeight, null, size, true));
        visuals.add(new Visual(explosion, "炫纹强压 · 爆炸", 3600, impact, explosionEnd,
                SMASH_X, 1 - smashHeight, null, size, true));
        effectEnd = Math.max(effectEnd, Math.max(floorEnd, explosionEnd));
        if (effectEnd > actorEnd && actions.containsKey("waiting motion")) {
            poses.add(new Pose("waiting motion", actorEnd, effectEnd));
        }
        return new Act1SkillSequence(List.copyOf(poses), List.copyOf(visuals), List.of(),
                "炫纹强压以预存的三枚普通无属性炫纹演示，炫纹数量和大小决定聚合与爆炸大小");
    }

    private static void addChaser(List<Visual> visuals, String animation, int slot, long charge, long move,
                                  long absorb, double readyX, double readyZ, double centerX, double centerZ) {
        // CNChaser idle slots (0x5AE450) and ease-in approach (0x5AE6C0).
        double idleAngle = Math.toRadians(110 * slot);
        double idleX = -30 - 8 * slot;
        double idleHeight = 60 + (int)(Math.sin(idleAngle) * 20) - (int)(Math.cos(idleAngle) * 29 * .2);
        double prepSeconds = charge / 1000.0;
        double approachSeconds = (absorb - move) / 1000.0;
        var readyMotion = new SkillMotion(0, charge, 0, 2 * (readyX - idleX) / (prepSeconds * prepSeconds),
                0, 2 * (readyZ - 1 - idleHeight) / (prepSeconds * prepSeconds), idleHeight);
        var approachMotion = new SkillMotion(move, absorb - move, 0,
                2 * (centerX - readyX) / (approachSeconds * approachSeconds), 0,
                2 * (centerZ - readyZ) / (approachSeconds * approachSeconds), readyZ - 1);
        String label = "炫纹强压 · 无属性炫纹";
        visuals.add(new Visual(animation, label, 3499, 0, charge, idleX, 0, readyMotion, 1, true));
        visuals.add(new Visual(animation, label, 3499, charge, absorb, readyX, 0, approachMotion, 1, true));
    }

    private static long addLanding(List<Pose> poses, GameScript.Lookup lookup, Map<String, String> actions,
                                   long recovery, double height) throws IOException {
        var frames = Act1SkillSequence.frames(CHARACTER, actions.get("jump motion"), lookup);
        for (int i = frames.size() - 2; i >= 0; i--) {
            if (frames.get(i).delay() < 10000) continue;
            int fall = Math.max(1, (int)Math.ceil(1000 * Math.sqrt(2 * height / GRAVITY)));
            long land = frames.subList(i + 1, frames.size()).stream().mapToLong(ResourceScript.Frame::delay).sum();
            var fallMotion = new SkillMotion(recovery, fall, 0, 0, 0, -GRAVITY, height);
            long end = recovery + fall + land;
            poses.add(new Pose("jump motion", recovery, end, fall, false, fallMotion, i));
            return end;
        }
        return recovery;
    }

    private static long animationDuration(GameScript.Lookup lookup, String path) throws IOException {
        String text = lookup.read(path);
        return text == null ? 0 : Act1SkillSequence.duration(ResourceScript.animation(text).frames(), Integer.MAX_VALUE, null);
    }

    private static double setting(List<String> values, String name, double fallback, double min, double max) {
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if (!values.get(i).equalsIgnoreCase(name)) continue;
            try {
                double value = Double.parseDouble(values.get(i + 1));
                if (Double.isFinite(value)) return Math.max(min, Math.min(max, value));
            } catch (NumberFormatException ignored) {}
            break;
        }
        return fallback;
    }
}
