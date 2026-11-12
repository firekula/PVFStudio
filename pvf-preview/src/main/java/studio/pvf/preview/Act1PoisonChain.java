package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.SplittableRandom;

/** Fire contacts and the per-mine fuse for the fully grown ACT1 poison mist. */
final class Act1PoisonChain {
    static final int FADE = 1000;
    private static final int MAX_GRID_POINTS = 4096;

    record Mine(int x, int y, int z) {}
    record Blast(Mine mine, long time) {}
    private record Contact(long delay, CombatFrame.Box box) {}
    private record Event(long time, int mine, CombatFrame.Box box) {}

    private Act1PoisonChain() {}

    static List<Blast> schedule(GameScript.Lookup lookup, int radius, int height, int spacing,
                                double scale, long cutoff) throws IOException {
        var mineFrames = Act1SkillSequence.objectMotions(lookup, 21019);
        var explosionFrames = Act1SkillSequence.objectMotions(lookup, 40002);
        if (mineFrames.isEmpty() || explosionFrames.isEmpty() || scale <= 0) return List.of();
        var hurt = ResourceScript.animation(lookup.read(mineFrames.get(0))).frames().stream().findFirst()
                .map(frame -> frame.combat().hurt()).orElse(List.of());
        if (hurt.isEmpty()) return List.of();
        var contacts = new ArrayList<Contact>();
        long delay = 0;
        for (var frame : ResourceScript.animation(lookup.read(explosionFrames.get(0))).frames()) {
            if (frame.delay() <= 0) continue;
            for (var box : frame.combat().attack()) contacts.add(new Contact(delay, box.scaled(scale)));
            delay += frame.delay();
        }
        if (contacts.isEmpty()) return List.of();

        var random = new SplittableRandom(21019L);
        var mines = grid(radius, height, spacing, random);
        if (mines.isEmpty()) return List.of();
        // Place the first external fire contact on the lowest mine in this sample.
        var first = mines.stream().min(Comparator.comparingInt(Mine::z)
                .thenComparingDouble(mine -> Math.hypot(mine.x(), mine.y() * 100.0 / 29))).orElseThrow();
        var queue = new PriorityQueue<>(Comparator.comparingLong(Event::time).thenComparingInt(Event::mine));
        boolean[] ignited = new boolean[mines.size()];
        ignite(queue, ignited, mines.indexOf(first), 0, random);
        var blasts = new ArrayList<Blast>();
        while (!queue.isEmpty() && queue.peek().time() < cutoff) {
            var event = queue.remove();
            var mine = mines.get(event.mine());
            if (event.box() == null) {
                blasts.add(new Blast(mine, event.time()));
                for (var contact : contacts) {
                    queue.add(new Event(event.time() + contact.delay(), event.mine(), contact.box()));
                }
                continue;
            }
            for (int i = 0; i < mines.size(); i++) {
                if (!ignited[i] && intersects(event.box(), mine, hurt, mines.get(i))) {
                    ignite(queue, ignited, i, event.time(), random);
                }
            }
        }
        return List.copyOf(blasts);
    }

    // 0x59C690 starts one independent fuse per fire hit.
    private static void ignite(PriorityQueue<Event> queue, boolean[] ignited, int mine, long time,
                               SplittableRandom random) {
        ignited[mine] = true;
        queue.add(new Event(time + 100 + 100 * random.nextInt(1000) / 999, mine, null));
    }

    // 0x59BF10 walks a staggered grid, clips it to the poison volume and only then applies random offsets.
    private static List<Mine> grid(int radius, int height, int spacing, SplittableRandom random) {
        if (spacing <= 0 || radius <= 0 || height <= 0) return List.of();
        long columns = 2L * radius / spacing + 1;
        long rows = (columns - 1) / 2 + 1;
        long layers = (long) height / spacing + 1;
        if (columns > MAX_GRID_POINTS || rows > MAX_GRID_POINTS || layers > MAX_GRID_POINTS
                || columns * rows * layers > MAX_GRID_POINTS) return List.of();
        int edge = spacing / 3;
        var range = new CombatDebug.TargetRange(radius - edge, height - edge, 0);
        var mines = new ArrayList<Mine>();
        for (int layer = 0; layer < layers; layer++) {
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    int x = spacing / 2 - radius + column * spacing + row % 2 * (spacing / 2);
                    int y = (int) ((spacing / 4 + spacing * row) * .5 - radius * 29 / 100) + column % 2 * (spacing / 4);
                    int z = (int) ((spacing / 4 + spacing * layer) * .7) + column % 2 * (spacing / 4);
                    if (range.contains(x, y, z)) {
                        mines.add(new Mine(x + random.nextInt(20) - 10, y + random.nextInt(12) - 6,
                                z + random.nextInt(20) - 10));
                    }
                }
            }
        }
        return mines;
    }

    private static boolean intersects(CombatFrame.Box attack, Mine source, List<CombatFrame.Box> hurt, Mine target) {
        int dx = target.x() - source.x();
        int dy = target.y() - source.y();
        int dz = target.z() - source.z();
        for (var box : hurt) {
            if (overlaps(attack.x(), attack.width(), dx + box.x(), box.width())
                    && overlaps(attack.y(), attack.depth(), dy + box.y(), box.depth())
                    && overlaps(attack.z(), attack.height(), dz + box.z(), box.height())) return true;
        }
        return false;
    }

    private static boolean overlaps(double a, double lengthA, double b, double lengthB) {
        return a < b + lengthB && b < a + lengthA;
    }
}
