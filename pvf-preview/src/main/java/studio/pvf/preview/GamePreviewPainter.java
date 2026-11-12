package studio.pvf.preview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/** Draws the skill viewport, blending light at game pixel density before magnifying it. */
public final class GamePreviewPainter {
    private static final int CELL = 16;
    private static final int BUFFER_ALIGNMENT = 64;
    private static final long MAX_BUFFER_PIXELS = 8L * 1024 * 1024;

    private BufferedImage buffer;
    private GamePreview.Rendered inspectedFrame;
    private boolean hasAdditiveLight;
    private View lastView;

    private record View(int width, int height, double x, double y, double zoom, Color background) {}

    public void paint(Graphics2D graphics, GamePreview.Rendered frame, int width, int height,
                      double x, double y, double zoom, Color background) {
        if (width <= 0 || height <= 0) return;
        boolean newFrame = frame != inspectedFrame;
        if (newFrame) {
            inspectedFrame = frame;
            hasAdditiveLight = frame != null && frame.draws().stream()
                    .anyMatch(draw -> draw.prepared().effect().equals("LINEARDODGE"));
            lastView = null;
        }
        double magnification = Math.max(1, zoom);
        int columns = (int) Math.ceil(width / magnification);
        int rows = (int) Math.ceil(height / magnification);
        if (!hasAdditiveLight || !ensureBuffer(columns, rows)) {
            paintCheckerboard(graphics, width, height, 1, background);
            if (frame != null) paintDraws(graphics, frame.draws(), width, height, x, y, zoom);
            return;
        }
        var view = new View(width, height, x, y, zoom, background);
        if (!view.equals(lastView)) {
            // The checkerboard belongs to the opaque destination, so light uses the same
            // blend with the background and with sprites that precede it in draw order.
            Graphics2D layer = buffer.createGraphics();
            try {
                layer.clipRect(0, 0, columns, rows);
                paintCheckerboard(layer, width, height, magnification, background);
                paintDraws(layer, frame.draws(), columns, rows, x / magnification, y / magnification,
                        zoom / magnification);
            } finally {
                layer.dispose();
            }
            lastView = view;
        }
        Graphics2D output = (Graphics2D) graphics.create();
        try {
            output.clipRect(0, 0, width, height);
            output.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            output.scale(magnification, magnification);
            output.drawImage(buffer, 0, 0, null);
        } finally {
            output.dispose();
        }
    }

    private boolean ensureBuffer(int width, int height) {
        if (buffer != null && width <= buffer.getWidth() && height <= buffer.getHeight()) return true;
        int columns = align(width), rows = align(height);
        if ((long) columns * rows > MAX_BUFFER_PIXELS) return false;
        buffer = new BufferedImage(columns, rows, BufferedImage.TYPE_INT_RGB);
        lastView = null;
        return true;
    }

    private static int align(int value) {
        return (value + BUFFER_ALIGNMENT - 1) / BUFFER_ALIGNMENT * BUFFER_ALIGNMENT;
    }

    private static void paintDraws(Graphics2D graphics, List<GamePreview.Draw> draws, int width, int height,
                                   double x, double y, double zoom) {
        for (var draw : draws) {
            var bounds = draw.prepared().bounds(draw.mirrored());
            double left = x + (draw.x() + bounds.getMinX()) * zoom;
            double right = x + (draw.x() + bounds.getMaxX()) * zoom;
            double top = y + (draw.y() + bounds.getMinY()) * zoom;
            double bottom = y + (draw.y() + bounds.getMaxY()) * zoom;
            if (right < -1 || left > width + 1 || bottom < -1 || top > height + 1) continue;
            draw.paint(graphics, x, y, zoom);
        }
    }

    private static void paintCheckerboard(Graphics2D g, int width, int height, double scale, Color background) {
        g.setColor(background);
        g.fillRect(0, 0, (int) Math.ceil(width / scale), (int) Math.ceil(height / scale));
        g.setColor(new Color(Math.max(0, background.getRed() - 8), Math.max(0, background.getGreen() - 8),
                Math.max(0, background.getBlue() - 8)));
        for (int y = 0, row = 0; y < height; y += CELL, row++) {
            int top = (int) Math.round(y / scale), bottom = (int) Math.round((y + CELL) / scale);
            for (int x = row % 2 * CELL; x < width; x += 2 * CELL) {
                int left = (int) Math.round(x / scale), right = (int) Math.round((x + CELL) / scale);
                g.fillRect(left, top, right - left, bottom - top);
            }
        }
    }
}
