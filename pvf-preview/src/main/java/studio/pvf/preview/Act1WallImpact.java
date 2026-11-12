package studio.pvf.preview;

import java.io.IOException;
import java.util.List;

import studio.pvf.preview.Act1SkillSequence.Visual;

/** The shared ACT1 terrain response of passive objects 48028 and 23029. */
final class Act1WallImpact {
    private static final String ART = "character/fighter/effect/animation/throwenemywall";
    static final double GRAVITY = 1500;

    private Act1WallImpact() {}

    static boolean available(GameScript.Lookup lookup) throws IOException {
        return lookup.read(ART + "0.ani") != null;
    }

    static long airborne(double height, double lift) {
        return Math.max(1, (long) Math.ceil(1000 * (lift + Math.sqrt(lift * lift + 2 * GRAVITY * height)) / GRAVITY));
    }

    /** Sample a nearby wall at the first third of the original flight, up to 100 ms after launch. */
    static long contactTime(SkillMotion flight) {
        return flight.start() + Math.max(1, Math.min(100, flight.travelTime() / 3));
    }

    /** The caster and target share the wall used for the preview collision. */
    static SkillMotion stopAtWall(SkillMotion slide, double wallX) {
        if (slide.offsetAt(slide.start() + slide.travelTime()) <= wallX) return slide;
        double velocity = slide.velocity();
        double velocityAtWall = Math.sqrt(velocity * velocity + 2 * slide.acceleration() * wallX);
        long travel = (long) Math.floor(2000 * wallX / (velocity + velocityAtWall));
        return new SkillMotion(slide.start(), travel, velocity, slide.acceleration());
    }

    static long addRebound(List<Visual> visuals, GameScript.Lookup lookup, List<GameScript.Field> fields,
                           String skill, double origin, SkillMotion flight, long impact) throws IOException {
        double x = origin + flight.offsetAt(impact);
        double height = flight.heightAt(impact);
        int targetHeight = Act1SwordAsuraSupplement.goblinHeight(lookup);
        long effectEnd = impact;
        // 0x5BF7C0 loads all three clips at contact with height/160 scale and a half-height Z offset.
        for (int index = 0; index < 3; index++) {
            String path = ART + index + ".ani";
            String source = lookup.read(path);
            if (source == null) continue;
            long duration = new AnimationTimeline(ResourceScript.animation(source)).duration();
            if (duration <= 0) continue;
            effectEnd = Math.max(effectEnd, impact + duration);
            visuals.add(new Visual(path, skill + " · 撞墙冲击", 3601 + index, impact, impact + duration,
                    x, -height - targetHeight / 2, null, targetHeight / 160.0, true));
        }

        // The packet stores lift first and horizontal strength second. State 4 reverses direction once.
        double lift = Math.max(0, Math.min(1500, Act1SkillSequence.staticValue(fields, 4, 500)));
        double push = lift > 0 ? Math.max(0, Math.min(3000, Act1SkillSequence.staticValue(fields, 3, 200))) : 0;
        long duration = airborne(height, lift);
        var rebound = new SkillMotion(impact, duration, -push, 0, lift, -GRAVITY, height);
        long landed = impact + duration;
        long end = Math.max(landed + 400, effectEnd);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "overturn", skill + " · 撞墙反弹", impact, landed, x, rebound);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "down", skill + " · 目标落地",
                landed, end, x + rebound.offsetAt(landed), null);
        return end;
    }
}
