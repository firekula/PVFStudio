package studio.pvf.preview;

/** ACT1 client: 0x4C4F60 projects actors, 0x463AE0 queries the 16 by 12 pass grid. */
public final class SceneCoordinates {
    public static final int CELL_WIDTH = 16, CELL_HEIGHT = 12, ORIGIN_Y = 120;
    public static final int BASIC_ROWS = 30, EXTENDED_ROWS = 10;
    public static final int EXTENDED_HEIGHT = EXTENDED_ROWS * CELL_HEIGHT;
    private SceneCoordinates() {}

    public static double screenY(double y, double z) { return y - z + ORIGIN_Y; }
}
