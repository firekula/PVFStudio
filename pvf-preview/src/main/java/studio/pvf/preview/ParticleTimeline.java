package studio.pvf.preview;

import java.util.*;

/** Deterministic absolute sampling: seeking never changes random placement or replays an unbounded history. */
public final class ParticleTimeline {
    public static final int LIMIT = 1024;
    private final ParticleScript script;
    private final long seed, initialTime;
    private final double firstEnd;
    private final long firstCount;

    public ParticleTimeline(ParticleScript script, long seed, long initialTime) {
        this.script = script; this.seed = seed; this.initialTime = Math.max(0, initialTime);
        firstEnd = emissionEnd(this.initialTime); firstCount = count(firstEnd);
    }
    public record Particle(long sequence, long age, double x, double y, double z, double alpha, int object) {}
    public record Sample(List<Particle> particles, boolean limited) {}

    public Sample sample(long milliseconds, double rootX, double rootY, double rootZ, boolean mirrored) {
        double time = Math.max(0, milliseconds), clock = initialTime + time;
        long total = count(emissionEnd(clock));
        List<Particle> particles = new ArrayList<>();
        long start = Math.max(0, total - LIMIT);
        boolean limited = false;
        // Bound both work and output, including intentionally unlimited/immortal effects.
        for (long i = start; i < total; i++) {
            double age = script.frequency() == -1 ? time + initialTime : i < firstCount ? time + (firstCount == 0 ? 0 : i * firstEnd / firstCount)
                    : clock - i * 1000.0 / script.frequency();
            if (age < 0) continue;
            SplittableRandom rng = new SplittableRandom(mix(seed ^ mix(i)));
            double[] area = {spread(rng, script.area().x()), spread(rng, script.area().y()), spread(rng, script.area().z())};
            area = rotate(area, script.areaAngles());
            double horizontal = script.angles().x(), vertical = script.angles().y(), roll = script.angles().z();
            if (script.rangeType().equals("circle")) {
                double around = rng.nextDouble(360), radius = Math.sqrt(rng.nextDouble()) * script.horizontalRange() / 2;
                horizontal += Math.cos(Math.toRadians(around)) * radius;
                vertical += Math.sin(Math.toRadians(around)) * radius; roll = 90 - around;
            } else {
                horizontal += spread(rng, script.horizontalRange()); vertical += spread(rng, script.verticalRange());
            }
            List<ParticleScript.Axis> axes = new ArrayList<>();
            double sinAmplitude = script.random() == 0 ? 1 : (.25 + spread(rng, script.random()) / 100), sinPeriod = jitter(rng, script.random());
            for (var axis : script.axes()) {
                double factor = jitter(rng, script.random());
                boolean oscillating = axis.type().equals("sin") || axis.type().equals("cos");
                axes.add(new ParticleScript.Axis(axis.type(), axis.start() * (oscillating ? sinAmplitude : factor),
                        axis.end() * (oscillating ? sinAmplitude : factor), axis.periodStart() * sinPeriod, axis.periodEnd() * sinPeriod));
            }
            double term = script.term() * jitter(rng, script.random());
            double life = script.life() * jitter(rng, script.random());
            if (script.life() != 0) life = Math.max(1, life);
            if (life > 0 && age >= life) continue;
            int object = script.objects().isEmpty() ? -1 : script.inOrder() ? (int) (i % script.objects().size()) : rng.nextInt(script.objects().size());
            if (object < 0) continue;
            var direction = new ParticleScript.Vec(horizontal, vertical, roll);
            double[] movement = movement(axes, term, age, direction);
            double x = rootX + (mirrored ? -1 : 1) * (area[0] + movement[0]);
            double y = rootY + .29 * (area[1] + movement[1]);
            double z = rootZ + area[2] + movement[2] - script.gravity() * age * age / 2_000_000.0;
            double landing = script.landing().equals("pierce") ? -1 : landingTime(axes, term, age, direction, rootZ + area[2]);
            if (landing >= 0) {
                if (script.landing().equals("vanish")) continue;
                if (script.landing().equals("stop")) {
                    movement = movement(axes, term, landing, direction);
                    x = rootX + (mirrored ? -1 : 1) * (area[0] + movement[0]); y = rootY + .29 * (area[1] + movement[1]);
                }
                z = script.landing().equals("slide") ? Math.max(0, z) : 0;
            }
            double alphaAge = landing >= 0 && script.landing().equals("stop") ? Math.min(age, landing) : age;
            double alpha = (script.alphaStart() + (life == 0 ? 0 : (script.alphaEnd() - script.alphaStart()) * alphaAge / life)) / 100;
            if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Math.abs(x) < 10_000_000
                    && Math.abs(y) < 10_000_000 && Math.abs(z) < 10_000_000)
                particles.add(new Particle(i, (long) age, x, y, z, Math.max(0, Math.min(1, alpha)), object));
        }
        if (start > 0) {
            double maxLife = script.life() * (1 + script.random() / 200);
            // Only report clipping if older particles could still be alive.
            double youngestOmitted = start - 1 < firstCount ? time + (start - 1) * firstEnd / firstCount
                    : clock - (start - 1) * 1000.0 / script.frequency();
            limited = maxLife == 0 || youngestOmitted < maxLife;
        }
        return new Sample(List.copyOf(particles), limited);
    }
    private double emissionEnd(double time) {
        double end = script.createTime() == 0 ? time : Math.min(time, Math.max(0, script.createTime() - 1));
        if (script.maximum() > 0 && script.frequency() > 0)
            end = Math.min(end, (script.maximum() - 1) * 1000.0 / script.frequency());
        return Math.max(0, end);
    }
    private long count(double time) {
        if (script.frequency() == 0 || script.objects().isEmpty()) return 0;
        if (script.frequency() == -1) return script.maximum() == 0 ? LIMIT + 1 : script.maximum();
        long count = (long) Math.min(Long.MAX_VALUE / 2.0, Math.floor(1 + time * script.frequency() / 1000.0 + 1e-9));
        return script.maximum() == 0 ? count : Math.min(script.maximum(), count);
    }
    private double[] movement(List<ParticleScript.Axis> axes, double term, double age, ParticleScript.Vec angles) {
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) result[i] = displacement(axes.get(i), term, script.changing(), age);
        return rotate(result, angles);
    }
    private double height(List<ParticleScript.Axis> axes, double term, double t, ParticleScript.Vec angles, double origin) {
        return origin + movement(axes, term, t, angles)[2] - script.gravity() * t * t / 2_000_000.0;
    }
    private double landingTime(List<ParticleScript.Axis> axes, double term, double age, ParticleScript.Vec angles, double origin) {
        if (origin < 0) return 0;
        boolean polynomial = axes.stream().noneMatch(a -> a.type().equals("sin") || a.type().equals("cos"))
                && (term == 0 || !script.changing().equals("ping pong"));
        if (polynomial) {
            double split = term > 0 && script.changing().equals("keep last value") ? Math.min(term, age) : age;
            double hit = polynomialLanding(axes, term, 0, split, angles, origin);
            return hit >= 0 || split == age ? hit : polynomialLanding(axes, term, split, age, angles, origin);
        }
        // Bound complex oscillating ground contact as well as emission. Search its initial cycles densely.
        double step = 50;
        for (var a : axes) if (a.periodStart() > 0) step = Math.min(step, a.periodStart() / 32);
        if (term > 0) step = Math.min(step, term / 32);
        step = Math.max(1, step); double previous = 0;
        for (int i = 1; i <= 512; i++) {
            double next = Math.min(age, step * i);
            if (height(axes, term, next, angles, origin) < 0) return crossing(axes, term, previous, next, angles, origin);
            if (next == age) return -1; previous = next;
        }
        return height(axes, term, age, angles, origin) < 0 ? crossing(axes, term, previous, age, angles, origin) : -1;
    }
    private double polynomialLanding(List<ParticleScript.Axis> axes, double term, double from, double to, ParticleScript.Vec angles, double origin) {
        if (to <= from) return -1;
        double span = to - from, c = height(axes, term, from, angles, origin), mid = height(axes, term, (from + to) / 2, angles, origin);
        double end = height(axes, term, to, angles, origin), b = (4 * mid - end - 3 * c) / span;
        double a = 2 * (end - 2 * mid + c) / (span * span);
        if (c < 0 || c == 0 && (b < 0 || b == 0 && a < 0)) return from;
        double root;
        if (Math.abs(a) < 1e-15) root = b < 0 ? -c / b : -1;
        else {
            double d = b * b - 4 * a * c;
            if (d < 0) return -1;
            // The root with a negative derivative is the downward ground crossing.
            root = (-b - Math.sqrt(d)) / (2 * a);
        }
        return root >= 0 && root <= span && (end < 0 || b + 2 * a * root < 0) ? from + root : -1;
    }
    private double crossing(List<ParticleScript.Axis> axes, double term, double lo, double hi, ParticleScript.Vec angles, double origin) {
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (height(axes, term, mid, angles, origin) < 0) hi = mid; else lo = mid;
        }
        return lo;
    }
    /** 0x78A8F0 integrates linear speed; sine/cosine use elapsed milliseconds and a varying mean period. */
    static double displacement(ParticleScript.Axis axis, double term, String changing, double age) {
        if (axis.type().equals("stop")) return 0;
        double start = axis.start(), end = axis.end(), t = age;
        if (axis.type().equals("straight line")) {
            if (term <= 0) return start * t / 1000;
            if (changing.equals("keep last value") && t >= term) return (start + end) * .5 * term / 1000 + (t - term) * end / 1000;
            if (changing.equals("ping pong")) {
                double cycles = Math.floor(t / term), rest = t % term;
                double a = ((long) cycles & 1) == 0 ? start : end, b = ((long) cycles & 1) == 0 ? end : start;
                return cycles * (start + end) * .5 * term / 1000 + (a + (b - a) * rest / term + a) * .5 * rest / 1000;
            }
            return (start + (end - start) * t / term + start) * .5 * t / 1000;
        }
        double period = axis.periodStart(), finalPeriod = axis.periodEnd(), phase = 0, amplitude = start;
        if (period <= 0) return 0;
        if (term <= 0) phase = t / period;
        else if (changing.equals("keep last value") && t >= term) {
            if (finalPeriod <= 0 || period + finalPeriod <= 0) return 0;
            phase = term / ((period + finalPeriod) * .5) + (t - term) / finalPeriod; amplitude = end;
        } else if (changing.equals("ping pong")) {
            double cycles = Math.floor(t / term), rest = t % term;
            boolean reverse = ((long) cycles & 1) != 0;
            double p = reverse ? finalPeriod : period, q = reverse ? period : finalPeriod;
            double mean = (p + (q - p) * rest / term + p) * .5;
            if (mean <= 0 || cycles > 0 && period + finalPeriod <= 0) return 0;
            phase = (cycles == 0 ? 0 : cycles * term / ((period + finalPeriod) * .5)) + rest / mean;
            amplitude = (reverse ? end : start) + (reverse ? start - end : end - start) * rest / term;
        } else {
            double mean = (period + (finalPeriod - period) * t / term + period) * .5;
            if (mean <= 0) return 0; phase = t / mean; amplitude = start + (end - start) * t / term;
        }
        return amplitude * (axis.type().equals("sin") ? Math.sin(phase * Math.PI * 2) : Math.cos(phase * Math.PI * 2));
    }
    private static double[] rotate(double[] v, ParticleScript.Vec angles) {
        double x = v[0], y = v[1], z = v[2];
        double a = Math.toRadians(angles.z()), b = Math.toRadians(angles.y()), c = Math.toRadians(angles.x());
        double rz = z * Math.cos(a) - y * Math.sin(a), ry = z * Math.sin(a) + y * Math.cos(a);
        double rx = x * Math.cos(b) - rz * Math.sin(b); z = x * Math.sin(b) + rz * Math.cos(b);
        x = rx * Math.cos(c) - ry * Math.sin(c); y = rx * Math.sin(c) + ry * Math.cos(c);
        return new double[]{x, y, z};
    }
    private static double spread(SplittableRandom random, double size) { return size == 0 ? 0 : random.nextDouble() * size - size / 2; }
    private static double jitter(SplittableRandom random, double percent) { return 1 + spread(random, percent) / 100; }
    private static long mix(long x) { x = (x ^ x >>> 30) * 0xbf58476d1ce4e5b9L; x = (x ^ x >>> 27) * 0x94d049bb133111ebL; return x ^ x >>> 31; }
}
