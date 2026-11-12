package studio.pvf.preview;

import java.util.List;
import java.util.function.LongSupplier;

/** Absolute scene time; no LCM-sized cycle or accumulated per-frame timer drift. */
public final class AnimationTimeline {
    private final long[] ends;
    private final long[] armorStarts;
    private final long armorAcrossLoop;
    private final boolean loop;
    private final List<ResourceScript.Frame> frames;
    public AnimationTimeline(ResourceScript.Animation animation) {
        frames = animation.frames(); ends = new long[frames.size()]; loop = animation.loop();
        long duration = 0;
        for (int i = 0; i < frames.size(); i++) { duration += Math.max(0, frames.get(i).delay()); ends[i] = duration; }
        armorStarts = frames.stream().anyMatch(f -> f.combat().damageType() == CombatFrame.DamageType.SUPERARMOR)
                ? new long[frames.size()] : null;
        long crossing = -1;
        if (armorStarts != null) {
            long latest = -1;
            boolean previous = false, firstArmored = false, first = true;
            for (int i = 0; i < frames.size(); i++) {
                var frame = frames.get(i);
                if (frame.delay() > 0) {
                    boolean armored = frame.combat().damageType() == CombatFrame.DamageType.SUPERARMOR;
                    if (first) { firstArmored = armored; first = false; }
                    if (armored && !previous) latest = i == 0 ? 0 : ends[i - 1];
                    previous = armored;
                }
                armorStarts[i] = latest;
            }
            if (firstArmored && previous) crossing = latest;
        }
        armorAcrossLoop = crossing;
    }
    public long duration() { return ends.length == 0 ? 0 : ends[ends.length - 1]; }
    public int frameAt(long milliseconds, boolean forceLoop) {
        if (ends.length == 0) return -1;
        long duration = duration(), time = Math.max(0, milliseconds);
        if (duration == 0 || !(loop || forceLoop) && time >= duration) return ends.length - 1;
        if (loop || forceLoop) time %= duration;
        int low = 0, high = ends.length;
        while (low < high) { int middle = (low + high) >>> 1; if (ends[middle] <= time) low = middle + 1; else high = middle; }
        return Math.min(ends.length - 1, low);
    }

    boolean hasAuthoredArmor() { return armorStarts != null; }
    boolean hasAttackBoxes() { return frames.stream().anyMatch(frame -> !frame.combat().attack().isEmpty()); }

    /** Remaining draw pulse can span an adjacent normal frame. The underlying type remains discrete. */
    long authoredArmorElapsed(long milliseconds, boolean forceLoop) {
        if (armorStarts == null) return -1;
        long time = Math.max(0, milliseconds), duration = duration();
        int frame = frameAt(time, forceLoop);
        boolean armored = frames.get(frame).combat().damageType() == CombatFrame.DamageType.SUPERARMOR;
        long lastStart = armorStarts[frame];
        if ((loop || forceLoop) && duration > 0) {
            boolean wrapped = time >= duration;
            time %= duration;
            if (wrapped && lastStart == 0 && armorAcrossLoop >= 0) lastStart = armorAcrossLoop - duration;
            else if (lastStart < 0) {
                long previous = armorStarts[armorStarts.length - 1];
                if (!wrapped || previous < 0) return -1;
                lastStart = previous - duration;
            }
        } else if (lastStart < 0) return armored ? time : -1;
        long elapsed = time - lastStart;
        return armored || elapsed < 200 ? elapsed : -1;
    }

    /** 0x82CB90: interpolate transforms/RGBA toward the next frame, retaining the current image. */
    public ResourceScript.Frame sample(long milliseconds, boolean forceLoop) {
        int index = frameAt(milliseconds, forceLoop); if (index < 0) return null;
        var a = frames.get(index);
        if (!a.interpolation() || a.delay() == 0 || index == frames.size() - 1 && !(loop || forceLoop)) return a;
        var b = frames.get((index + 1) % frames.size());
        long time = Math.max(0, milliseconds); if (duration() > 0 && (loop || forceLoop)) time %= duration();
        long start = index == 0 ? 0 : ends[index - 1]; double f = Math.max(0, Math.min(1, (time - start) / (double) a.delay()));
        return new ResourceScript.Frame(a.image(), a.index(), lerp(a.x(), b.x(), f), lerp(a.y(), b.y(), f),
                lerp(a.scaleX(), b.scaleX(), f), lerp(a.scaleY(), b.scaleY(), f), lerp(a.rotation(), b.rotation(), f),
                (int) lerp(a.r(), b.r(), f), (int) lerp(a.g(), b.g(), f), (int) lerp(a.b(), b.b(), f), (int) lerp(a.a(), b.a(), f),
                a.delay(), a.line(), a.effect(), true, a.combat());
    }
    private static double lerp(double a, double b, double f) { return a + (b - a) * f; }

    public static final class Clock {
        private final LongSupplier nanos;
        private double position, speed = 1;
        private long started;
        private boolean playing;
        public Clock() { this(System::nanoTime); }
        public Clock(LongSupplier nanos) { this.nanos = nanos; }
        public long milliseconds() { return (long) Math.min(Long.MAX_VALUE, Math.max(0, current())); }
        private double current() { return position + (playing ? (nanos.getAsLong() - started) / 1_000_000.0 * speed : 0); }
        public void play() { if (!playing) { started = nanos.getAsLong(); playing = true; } }
        public void pause() { position = current(); playing = false; }
        public void seek(long milliseconds) { position = Math.max(0, milliseconds); started = nanos.getAsLong(); }
        public void speed(double value) {
            if (!Double.isFinite(value) || value <= 0 || value > 16) throw new IllegalArgumentException("Invalid playback speed");
            position = current(); started = nanos.getAsLong(); speed = value;
        }
    }
}
