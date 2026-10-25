package studio.pvf.preview;

import java.util.List;
import java.util.function.LongSupplier;

/** Absolute scene time; no LCM-sized cycle or accumulated per-frame timer drift. */
public final class AnimationTimeline {
    private final long[] ends;
    private final boolean loop;
    private final List<ResourceScript.Frame> frames;
    public AnimationTimeline(ResourceScript.Animation animation) {
        frames = animation.frames(); ends = new long[frames.size()]; loop = animation.loop();
        long duration = 0;
        for (int i = 0; i < frames.size(); i++) { duration += Math.max(0, frames.get(i).delay()); ends[i] = duration; }
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
                a.delay(), a.line(), a.effect(), true);
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
