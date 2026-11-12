package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GamePreviewPainterTest {
    @Test void zoomMagnifiesTheCompositedPixelsAndPanningUpdatesTheSameFrame() throws IOException {
        var body = draw("NONE", 0xff203040, false, Double.NaN);
        var light = draw("LINEARDODGE", 0x8000ff20, true, 0);
        var frame = new GamePreview.Rendered(List.of(body, light), new Rectangle2D.Double(), 0, List.of());
        var painter = new GamePreviewPainter();
        var nativeFrame = paint(painter, frame, 1, 4);
        var enlarged = paint(painter, frame, 4, 16);
        assertEquals(0xff20b050, nativeFrame.getRGB(3, 4));
        assertEquals(0xff203040, nativeFrame.getRGB(4, 4));
        for (int y = 0; y < enlarged.getHeight(); y++) {
            for (int x = 0; x < enlarged.getWidth(); x++) {
                assertEquals(nativeFrame.getRGB(x / 4, y / 4), enlarged.getRGB(x, y));
            }
        }
        var panned = paint(painter, frame, 4, 24);
        assertEquals(Color.BLACK.getRGB(), panned.getRGB(12, 16));
        assertEquals(0xff20b050, panned.getRGB(20, 16));
    }

    private static GamePreview.Draw draw(String mode, int rgb, boolean mirrored, double clip) throws IOException {
        var img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, rgb);
        }
        var sprite = new NpkResources.Sprite(img, 0, 0, 4, 2, Path.of("sample.npk"), "test.img", 0, 0);
        var frame = ResourceScript.animation("[FRAME000] [IMAGE] `test.img` 0 [IMAGE POS] -2 0 [GRAPHIC EFFECT] `"
                + mode + "`").frames().get(0);
        return new GamePreview.Draw(PreviewRenderer.prepare(frame, sprite), 0, 0, mirrored, "sample", 1, clip);
    }

    private static BufferedImage paint(GamePreviewPainter painter, GamePreview.Rendered frame, int zoom, int x) {
        var img = new BufferedImage(8 * zoom, 8 * zoom, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        try {
            painter.paint(g, frame, img.getWidth(), img.getHeight(), x, 4 * zoom, zoom, Color.BLACK);
        } finally {
            g.dispose();
        }
        return img;
    }
}
