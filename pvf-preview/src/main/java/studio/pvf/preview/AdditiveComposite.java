package studio.pvf.preview;

import java.awt.*;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/** Source-alpha additive light: black adds nothing instead of covering the scene with a black rectangle. */
final class AdditiveComposite implements Composite {
    static final AdditiveComposite INSTANCE = new AdditiveComposite();
    private final double opacity;
    AdditiveComposite() { this(1); }
    AdditiveComposite(double opacity) { this.opacity = opacity; }
    @Override public CompositeContext createContext(ColorModel source, ColorModel destination, RenderingHints hints) {
        return new CompositeContext() {
            @Override public void dispose() {}
            @Override public void compose(Raster src, Raster dst, WritableRaster out) {
                int width = Math.min(src.getWidth(), Math.min(dst.getWidth(), out.getWidth()));
                int height = Math.min(src.getHeight(), Math.min(dst.getHeight(), out.getHeight()));
                Object s = null, d = null, result = null;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    s = src.getDataElements(src.getMinX() + x, src.getMinY() + y, s);
                    d = dst.getDataElements(dst.getMinX() + x, dst.getMinY() + y, d);
                    int a = source.getRGB(s), b = destination.getRGB(d), sa = (int) ((a >>> 24) * opacity), da = b >>> 24;
                    int alpha = sa + da * (255 - sa) / 255, color = alpha << 24;
                    for (int shift = 16; shift >= 0; shift -= 8) {
                        int premultiplied = ((a >> shift & 255) * sa + (b >> shift & 255) * da) / 255;
                        int value = alpha == 0 ? 0 : Math.min(255, premultiplied * 255 / alpha);
                        color |= value << shift;
                    }
                    result = destination.getDataElements(color, result);
                    out.setDataElements(out.getMinX() + x, out.getMinY() + y, result);
                }
            }
        };
    }
}
