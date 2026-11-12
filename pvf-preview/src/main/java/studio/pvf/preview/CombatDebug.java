package studio.pvf.preview;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Locale;

/** Diagnostic projection of the ACT1 world-space boxes into the skill viewport. */
public final class CombatDebug {
    private CombatDebug() {}

    private static final BasicStroke SOLID = new BasicStroke(1.3f);
    private static final BasicStroke EDGE_BACKING = new BasicStroke(3.3f);
    private static final Color EDGE_SHADOW = new Color(12, 15, 20, 190);
    private static final BasicStroke SELECTED = new BasicStroke(2.25f);
    private static final BasicStroke DASHED = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1,
            new float[]{3, 3}, 0);
    private static final int[][] FACES = {{4, 5, 7, 6}, {0, 2, 6, 4}, {2, 3, 7, 6}};
    private static final int[][] HIDDEN_EDGES = {{0, 1}, {1, 3}, {1, 5}};

    public enum Kind {
        PLAYER_HURT("角色受击", new Color(87, 192, 255)),
        ATTACK("攻击判定", new Color(255, 114, 104)),
        ENEMY_HURT("敌人受击", new Color(255, 208, 105));

        private final String label;
        private final Color color;
        private final Color[] faceColors;
        private final Color hiddenEdgeColor;
        Kind(String label, Color color) {
            this.label = label;
            this.color = color;
            faceColors = new Color[]{
                    new Color((color.getRed() + 255) / 2, (color.getGreen() + 255) / 2,
                            (color.getBlue() + 255) / 2, 104),
                    new Color(color.getRed() / 2, color.getGreen() / 2, color.getBlue() / 2, 72),
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), 32)
            };
            hiddenEdgeColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 155);
        }
    }

    /** ACT1 0x471900 uses truncated, rescaled Y and integer distance; callers check Z separately. */
    public record TargetRange(int radius, Integer maxZ, int interval) {
        private static final int DEPTH_PERCENT = 29; // stock dword at 0xB51B20

        boolean contains(int dx, int dy, int z) {
            int normalizedY = 100 * dy / DEPTH_PERCENT;
            return (maxZ == null || z <= maxZ) && (int) Math.hypot(dx, normalizedY) <= radius;
        }

        CombatFrame.Box envelope() {
            double yRadius = radius * (DEPTH_PERCENT / 100.0);
            return new CombatFrame.Box(-radius, -yRadius, 0, 2.0 * radius, 2 * yRadius, maxZ == null ? 0 : maxZ);
        }
    }

    public record Volume(Kind kind, CombatFrame.Box box, double x, double y, boolean facingLeft, String label,
                         TargetRange targetRange, boolean pulse) {
        public Volume(Kind kind, CombatFrame.Box box, double x, double y, boolean facingLeft, String label) {
            this(kind, box, x, y, facingLeft, label, null, false);
        }

        public CombatFrame.Box orientedBox() {
            return facingLeft ? box.mirrored() : box;
        }

        public String description() {
            return title() + "   " + coordinates() + "   " + dimensions();
        }

        private String title() {
            return kind.label + " · " + label;
        }

        private String coordinates() {
            if (targetRange != null) return "中心 X " + units(x) + "  Y " + units(y)
                    + (targetRange.maxZ() == null ? "  不检查高度" : "  目标 Z ≤ " + targetRange.maxZ());
            var b = orientedBox();
            return "相对 X " + units(b.x()) + "  Y " + units(b.y()) + "  Z " + units(b.z());
        }

        private String dimensions() {
            if (targetRange != null) return "横向半径 " + targetRange.radius() + "  纵深半径 " + units(box.depth()/2)
                    + "  检查间隔 " + targetRange.interval() + " ms";
            return "宽 " + units(box.width()) + "  深 " + units(box.depth()) + "  高 " + units(box.height());
        }
    }

    private static String units(double value) {
        return value == (long) value ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    static Point2D.Double[] corners(Volume volume, double originX, double originY, double zoom) {
        var b = volume.orientedBox();
        var corners = new Point2D.Double[8];
        for (int i = 0; i < corners.length; i++) {
            double x = b.x() + ((i & 1) == 0 ? 0 : b.width());
            double y = b.y() + ((i & 2) == 0 ? 0 : b.depth());
            double z = b.z() + ((i & 4) == 0 ? 0 : b.height());
            // ACT1 0x4C4F20/0x4C4F60: ground depth affects screen Y, never screen X.
            corners[i] = new Point2D.Double(originX + (volume.x() + x) * zoom,
                    originY + (volume.y() + y - z) * zoom);
        }
        return corners;
    }

    public static void paint(Graphics2D graphics, List<Volume> volumes, double x, double y, double zoom,
                             boolean showCoordinates) {
        paint(graphics, volumes, x, y, zoom, showCoordinates, -1);
    }

    public static void paint(Graphics2D graphics, List<Volume> volumes, double x, double y, double zoom,
                             boolean showCoordinates, int selected) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (var volume : volumes) {
                if (volume.targetRange() != null) {
                    paintRange(g, volume, x, y, zoom, showCoordinates, false);
                    continue;
                }
                var p = corners(volume, x, y, zoom);
                var color = volume.kind().color;
                var edges = new Path2D.Double();
                for (int i = 0; i < FACES.length; i++) {
                    var face = path(p, FACES[i], true);
                    g.setColor(volume.kind().faceColors[i]);
                    g.fill(face);
                    edges.append(face, false);
                }
                g.setStroke(DASHED);
                g.setColor(volume.kind().hiddenEdgeColor);
                for (var edge : HIDDEN_EDGES) g.draw(path(p, edge, false));
                // A dark backing keeps the face boundaries readable over bright skill effects.
                g.setStroke(EDGE_BACKING);
                g.setColor(EDGE_SHADOW);
                g.draw(edges);
                g.setStroke(SOLID);
                g.setColor(color);
                g.draw(edges);
                if (showCoordinates) paintLabel(g, volume.description(), p[4].x, p[4].y - 4, color);
            }
            if (selected >= 0 && selected < volumes.size()) {
                var volume = volumes.get(selected);
                if (volume.targetRange() != null) {
                    paintRange(g, volume, x, y, zoom, false, true);
                } else {
                    var p = corners(volume, x, y, zoom);
                    g.setStroke(SELECTED);
                    g.setColor(volume.kind().color.brighter());
                    for (var face : FACES) g.draw(path(p, face, true));
                }
                paintSelection(g, volume);
            }
            paintLegend(g);
        } finally {
            g.dispose();
        }
    }

    /** Pick in reverse paint order, starting behind the current selection when it also covers the click. */
    public static int pick(List<Volume> volumes, double x, double y, double zoom, double mouseX, double mouseY,
                           int selected) {
        int first = -1, behind = -1;
        boolean selectionHit = false;
        for (int i = volumes.size() - 1; i >= 0; i--) {
            var volume = volumes.get(i);
            boolean hit;
            if (volume.targetRange() != null) {
                hit = projectedRange(volume,x,y,zoom).silhouette().intersects(mouseX-2,mouseY-2,4,4);
            } else {
                var p = corners(volume, x, y, zoom);
                hit = false;
                for (var face : FACES) {
                    // Include the outline, so clicking directly on an edge also works.
                    if (path(p, face, true).intersects(mouseX - 2, mouseY - 2, 4, 4)) {
                        hit = true;
                        break;
                    }
                }
            }
            if (!hit) continue;
            if (first < 0) first = i;
            if (i == selected) selectionHit = true;
            else if (selectionHit && behind < 0) behind = i;
        }
        return behind >= 0 ? behind : first;
    }

    private record RangeProjection(Ellipse2D top, Ellipse2D bottom, Area silhouette) {}

    private static RangeProjection projectedRange(Volume volume, double x, double y, double zoom) {
        var b = volume.box();
        double left = x + (volume.x() + b.x()) * zoom;
        double floor = y + (volume.y() + b.y()) * zoom;
        double height = b.height() * zoom, width = b.width() * zoom, depth = b.depth() * zoom;
        var top = new Ellipse2D.Double(left, floor-height, width, depth);
        var bottom = new Ellipse2D.Double(left, floor, width, depth);
        var silhouette = new Area(new Rectangle2D.Double(left,floor+depth/2-height,width,height));
        silhouette.add(new Area(top));
        silhouette.add(new Area(bottom));
        return new RangeProjection(top,bottom,silhouette);
    }

    private static void paintRange(Graphics2D g, Volume volume, double x, double y, double zoom,
                                   boolean showCoordinates, boolean selected) {
        var shape = projectedRange(volume,x,y,zoom);
        var kind = volume.kind();
        if (!selected) {
            g.setColor(kind.faceColors[volume.pulse() ? 1 : 2]);
            g.fill(shape.silhouette());
            g.setColor(kind.faceColors[volume.pulse() ? 0 : 2]);
            g.fill(shape.top());
            g.setStroke(DASHED);
            g.setColor(kind.hiddenEdgeColor);
            g.draw(shape.bottom());
            g.setStroke(EDGE_BACKING);
            g.setColor(EDGE_SHADOW);
            g.draw(shape.silhouette());
        }
        var color = selected || volume.pulse() ? kind.color.brighter() : kind.color;
        g.setColor(color);
        g.setStroke(selected ? SELECTED : SOLID);
        g.draw(shape.silhouette());
        g.draw(shape.top());
        if (showCoordinates) paintLabel(g,volume.description(),shape.top().getMinX(),shape.top().getMinY()-4,color);
    }

    private static Path2D path(Point2D.Double[] points, int[] indices, boolean closed) {
        var path = new Path2D.Double();
        var first = points[indices[0]];
        path.moveTo(first.x, first.y);
        for (int i = 1; i < indices.length; i++) {
            var point = points[indices[i]];
            path.lineTo(point.x, point.y);
        }
        if (closed) path.closePath();
        return path;
    }

    private static void paintLabel(Graphics2D g, String label, double x, double y, Color color) {
        var fm = g.getFontMetrics();
        int left = (int) Math.round(x), baseline = (int) Math.round(y);
        g.setColor(new Color(16, 18, 22, 216));
        g.fillRoundRect(left - 3, baseline - fm.getAscent() - 1, fm.stringWidth(label) + 6, fm.getHeight() + 2, 5, 5);
        g.setColor(color);
        g.drawString(label, left, baseline);
    }

    private static void paintLegend(Graphics2D g) {
        FontMetrics fm = g.getFontMetrics();
        int width = 12;
        for (var kind : Kind.values()) width += 20 + fm.stringWidth(kind.label);
        g.setColor(new Color(16, 18, 22, 220));
        g.fillRoundRect(8, 8, width, fm.getHeight() + 8, 8, 8);
        int left = 16, baseline = 12 + fm.getAscent();
        for (var kind : Kind.values()) {
            g.setColor(kind.color);
            g.fillRect(left, baseline - 8, 8, 8);
            g.drawString(kind.label, left + 12, baseline);
            left += 20 + fm.stringWidth(kind.label);
        }
    }

    private static void paintSelection(Graphics2D g, Volume volume) {
        var lines = List.of(volume.title(), volume.coordinates(), volume.dimensions());
        var fm = g.getFontMetrics();
        int width = lines.stream().mapToInt(fm::stringWidth).max().orElse(0);
        int top = 21 + fm.getHeight();
        g.setColor(new Color(16, 18, 22, 232));
        g.fillRoundRect(8, top, width + 16, lines.size() * fm.getHeight() + 10, 8, 8);
        int baseline = top + 5 + fm.getAscent();
        for (int i = 0; i < lines.size(); i++) {
            g.setColor(i == 0 ? volume.kind().color : Color.WHITE);
            g.drawString(lines.get(i), 16, baseline + i * fm.getHeight());
        }
    }
}
