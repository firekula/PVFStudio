package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import studio.pvf.preview.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class ClientViewportTest {
    @Test void dragClampsCameraAndReverseDoesNotStickAtEdges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var canvas = canvas(); Rectangle viewport = canvas.clientBounds(scale(canvas));
            int x = (int) viewport.getCenterX(), y = (int) viewport.getCenterY();
            drag(canvas, x, y, -10000, -10000);
            assertEquals(new Point(256, 120), canvas.camera.position());
            drag(canvas, x, y, x + 2, y + 2);
            assertTrue(canvas.camera.position().x < 256 && canvas.camera.position().y < 120);
            drag(canvas, x, y, 10000, 10000); assertEquals(new Point(0, 0), canvas.camera.position());
            drag(canvas, x, y, x - 2, y - 2);
            assertTrue(canvas.camera.position().x > 0 && canvas.camera.position().y > 0);
        });
    }
    @Test void resizeAndWheelPreserveCameraAndBorderIsOutsideTheSceneClip() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var canvas = canvas(); canvas.camera.set(100, 70); Point original = canvas.camera.position();
            Rectangle viewport = canvas.clientBounds(scale(canvas));
            canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, 0, 0,
                    (int) viewport.getCenterX(), (int) viewport.getCenterY(), 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1));
            assertEquals(original, canvas.camera.position());
            canvas.setSize(1000, 750); assertEquals(original, canvas.camera.position());
            canvas.freeZoom = 0; viewport = canvas.clientBounds(scale(canvas));
            var image = new BufferedImage(1000, 750, BufferedImage.TYPE_INT_RGB); var g = image.createGraphics(); canvas.paint(g); g.dispose();
            assertEquals(new Color(125, 195, 250).getRGB(), image.getRGB(viewport.x - 1, viewport.y + 10));
            assertEquals(Color.BLACK.getRGB(), image.getRGB(viewport.x + 10, viewport.y + 10));
            drag(canvas, 0, 0, -10000, -10000); assertEquals(original, canvas.camera.position());
        });
    }
    private static ResourcePreviewPanel.Canvas canvas() {
        try {
            var frame = ScenePreview.load("map/test.map", "[tile] `a.til` `a.til` `a.til` `a.til` [/tile] [extended tile] `a.til` `a.til` `a.til` `a.til` [/extended tile]",
                    p -> "[pass type] " + "0 ".repeat(420), new ScenePreview.SpriteLookup() {
                        public NpkResources.Info info(String path, int index) { throw new AssertionError("No image expected"); }
                        public NpkResources.Sprite sprite(String path, int index) { throw new AssertionError("No image expected"); }
                    }).render(0, false);
            var canvas = new ResourcePreviewPanel.Canvas(); canvas.scene = frame; canvas.world = frame.bounds();
            canvas.camera = new SceneCamera(frame.cameraLimits()); canvas.clientViewport = true; canvas.setSize(900, 650); return canvas;
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private static double scale(ResourcePreviewPanel.Canvas canvas) { return Math.min(4, Math.min((canvas.getWidth() - 40) / 640.0, (canvas.getHeight() - 40) / 480.0)); }
    private static void drag(Component canvas, int x, int y, int tx, int ty) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK, tx, ty, 0, false, MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, tx, ty, 1, false, MouseEvent.BUTTON1));
    }
}
