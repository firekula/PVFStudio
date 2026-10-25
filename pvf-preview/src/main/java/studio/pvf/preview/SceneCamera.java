package studio.pvf.preview;

import java.awt.Point;

/** ACT1 0x464BD0 / 0x464B00 / 0x463030: camera limits belong to tile layout, not image bounds. */
public final class SceneCamera {
    public static final int WIDTH = 640, HEIGHT = 480;
    private final Limits limits;
    private double x, y;

    public SceneCamera(Limits limits) { this.limits = limits; reset(); }
    public Limits limits() { return limits; }
    public Point position() { return limits.clamp(x, y); }
    public void reset() { set(limits.maxX() / 2.0, limits.maxY() / 2.0); }
    public void move(double dx, double dy) { set(x + dx, y + dy); }
    public void set(double nextX, double nextY) {
        if (!Double.isFinite(nextX) || !Double.isFinite(nextY)) throw new IllegalArgumentException("Invalid camera position");
        // Discard overscroll at each edge; reversing a drag must move immediately.
        x = Math.max(0, Math.min(limits.maxX(), nextX)); y = Math.max(0, Math.min(limits.maxY(), nextY));
    }
    /** Native integer percentage multiplication/division, avoiding floating-point rounding at pixel boundaries. */
    public static int layerOffset(int camera, double scroll) { return (int) ((long) camera * Math.round(scroll * 100) / 100); }
    public record Limits(int maxX, int maxY) {
        public Limits { if (maxX < 0 || maxY < 0) throw new IllegalArgumentException("Negative camera range"); }
        public static Limits forMap(int baseTiles, int extendedTiles) {
            if (baseTiles <= 0 || extendedTiles < 0) throw new IllegalArgumentException("Invalid map layout");
            return new Limits(Math.max(0, Math.multiplyExact(baseTiles, SceneScript.TILE_WIDTH) - WIDTH),
                    Math.multiplyExact(extendedTiles / baseTiles, SceneCoordinates.EXTENDED_HEIGHT));
        }
        public Point clamp(double x, double y) {
            return new Point((int) Math.max(0, Math.min(maxX, x)), (int) Math.max(0, Math.min(maxY, y)));
        }
    }
}
