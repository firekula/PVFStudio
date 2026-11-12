package studio.pvf.preview;

import java.io.IOException;
import java.util.List;

/** State and contour timing from the ACT1 active-object draw and super armor appendage. */
final class Act1SuperArmor {
    private static final String FIGHTER_ARMOR = "skill/fighter/superarmor.skl";

    private Act1SuperArmor() {}

    static Span runtimeSpan(String path, String text, GameScript.Lookup lookup, int level) throws IOException {
        if (!FIGHTER_ARMOR.equals(path) || !Act1SkillSequence.matches(path, "fighter", 20, lookup)) return null;
        return fighterBuff(GameScript.fields(text), level);
    }

    static Span runtimeSpan(String path, String text, GameScript.Lookup lookup, int level, Act1SkillSequence sequence)
            throws IOException {
        if (sequence == null) return null;
        if (FIGHTER_ARMOR.equals(path)) return runtimeSpan(path, text, lookup, level);
        int skill = switch (path) {
            // 0x643D00 enables armor in state 10. 0x638640 clears it when the release ends.
            case "skill/swordman/releasewave.skl" -> 32;
            case "skill/swordman/tombstonerain.skl" -> 44;
            case "skill/swordman/wavespinarea.skl" -> 74;
            // 0x6103F0 checks the selection timer or ice field count until state 10 exits.
            case "skill/mage/thunderstrike.skl" -> 55;
            case "skill/mage/icefield.skl" -> 59;
            default -> -1;
        };
        if (skill < 0) return null;
        String job = path.startsWith("skill/swordman/") ? "swordman" : "mage";
        if (!Act1SkillSequence.matches(path, job, skill, lookup)) return null;
        return releaseSpan(sequence, skill == 55 ? "throw motion 4-2" : "throw motion 2-2");
    }

    /** The native status remains active between release poses during the mage channels. */
    private static Span releaseSpan(Act1SkillSequence sequence, String action) {
        long start = Long.MAX_VALUE;
        long end = 0;
        for (var pose : sequence.poses()) {
            if (pose.secondary() || !action.equals(pose.action())) continue;
            start = Math.min(start, pose.start());
            end = Math.max(end, pose.end());
        }
        return end > start ? new Span(start, end, false) : null;
    }

    /** Fighter release handler 0x5E2260 copies level column zero into the status lifetime. */
    static Span fighterBuff(List<GameScript.Field> fields, int level) {
        int duration = Act1SkillSequence.levelValue(fields, level, 0, 0, 0, Integer.MAX_VALUE);
        if (duration == 0) return null;
        long release = Act1SkillSequence.castingTime(fields, level, 500);
        return new Span(release, release + duration, true);
    }

    /** The eight draws at 0x480700 use this flat ARGB color with additive blending. */
    static int outlineColor(long actorElapsed) {
        int phase = (int) (Math.max(0, actorElapsed) % 500);
        int distance = Math.min(phase, 500 - phase);
        int green = Math.max(0, 300 * distance / 250 - 100);
        return 0x96FF0000 | (green << 8);
    }

    /** Only the base body frame's contour receives this scale offset. */
    static double outlineExpansion(long authoredSuperArmorElapsed) {
        return expansion(authoredSuperArmorElapsed, 200);
    }

    /** The runtime appendage has a shorter opening pulse at 0x531150. */
    static double runtimeExpansion(long runtimeSuperArmorElapsed) {
        return expansion(runtimeSuperArmorElapsed, 150);
    }

    private static double expansion(long elapsed, int duration) {
        return elapsed < 0 || elapsed >= duration ? 0 : 0.5 * (duration - elapsed) / duration;
    }

    record Span(long start, long end, boolean buffPulse) {
        boolean contains(long time) {
            return start <= time && time < end;
        }

        double expansionAt(long time) {
            return buffPulse && contains(time) ? runtimeExpansion(time - start) : 0;
        }
    }
}
