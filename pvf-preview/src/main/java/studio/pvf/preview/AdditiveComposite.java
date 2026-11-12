package studio.pvf.preview;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;

/** Source-alpha additive light: black adds nothing instead of covering the scene with a black rectangle. */
final class AdditiveComposite implements Composite {
    static final AdditiveComposite INSTANCE = new AdditiveComposite();
    private final double opacity;
    AdditiveComposite() { this(1); }
    AdditiveComposite(double opacity) { this.opacity = opacity; }
    @Override public CompositeContext createContext(ColorModel source, ColorModel destination, RenderingHints hints) {
        var sourceFormat = PackedFormat.of(source);
        var destinationFormat = PackedFormat.of(destination);
        return new CompositeContext() {
            @Override public void dispose() {}
            @Override public void compose(Raster src, Raster dst, WritableRaster out) {
                int width = Math.min(src.getWidth(), Math.min(dst.getWidth(), out.getWidth()));
                int height = Math.min(src.getHeight(), Math.min(dst.getHeight(), out.getHeight()));
                if (sourceFormat != null && destinationFormat != null) {
                    var s = PackedRaster.of(src, sourceFormat);
                    var d = PackedRaster.of(dst, destinationFormat);
                    var o = PackedRaster.of(out, destinationFormat);
                    if (s != null && d != null && o != null) {
                        composePacked(s, d, o, sourceFormat, destinationFormat, width, height);
                        return;
                    }
                }
                Object s = null, d = null, result = null;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    s = src.getDataElements(src.getMinX() + x, src.getMinY() + y, s);
                    d = dst.getDataElements(dst.getMinX() + x, dst.getMinY() + y, d);
                    int a = source.getRGB(s);
                    result = destination.getDataElements(blend(a, destination.getRGB(d), sourceAlpha(a)), result);
                    out.setDataElements(out.getMinX() + x, out.getMinY() + y, result);
                }
            }
        };
    }

    private void composePacked(PackedRaster src, PackedRaster dst, PackedRaster out,
                               PackedFormat source, PackedFormat destination, int width, int height) {
        for (int y = 0, s = src.offset, d = dst.offset, o = out.offset; y < height;
             y++, s += src.stride, d += dst.stride, o += out.stride) {
            for (int x = 0; x < width; x++) {
                int a = src.data[s + x], b = dst.data[d + x];
                int alpha = sourceAlpha(source == PackedFormat.RGB ? a | 0xff000000 : a);
                if (destination == PackedFormat.RGB) {
                    int rgb = alpha == 0 ? b : blendOpaque(source.unpack(a), b, alpha);
                    out.data[o + x] = rgb & 0x00ffffff;
                } else {
                    int result = blend(source.unpack(a), destination.unpack(b), alpha);
                    out.data[o + x] = destination == PackedFormat.ARGB_PRE ? Premultiplication.convert(result, true) : result;
                }
            }
        }
    }

    private int sourceAlpha(int argb) {
        return (int) ((argb >>> 24) * opacity);
    }

    private static int blend(int source, int destination, int sourceAlpha) {
        int destinationAlpha = destination >>> 24;
        if (destinationAlpha == 255) return 0xff000000 | blendOpaque(source, destination, sourceAlpha);
        int alpha = sourceAlpha + destinationAlpha * (255 - sourceAlpha) / 255;
        if (alpha == 0) return 0;
        int result = alpha << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int sum = ((source >>> shift & 255) * sourceAlpha + (destination >>> shift & 255) * destinationAlpha) / 255;
            result |= Math.min(255, sum * 255 / alpha) << shift;
        }
        return result;
    }

    private static int blendOpaque(int source, int destination, int alpha) {
        int r = Math.min(255, (destination >>> 16 & 255) + (source >>> 16 & 255) * alpha / 255);
        int g = Math.min(255, (destination >>> 8 & 255) + (source >>> 8 & 255) * alpha / 255);
        int b = Math.min(255, (destination & 255) + (source & 255) * alpha / 255);
        return (r << 16) | (g << 8) | b;
    }

    private enum PackedFormat {
        RGB(0xff0000, 0xff00, 0xff), ARGB(0xff0000, 0xff00, 0xff, 0xff000000),
        ARGB_PRE(0xff0000, 0xff00, 0xff, 0xff000000);

        final int[] masks;
        PackedFormat(int... masks) { this.masks = masks; }

        static PackedFormat of(ColorModel model) {
            if (!(model instanceof DirectColorModel direct) || !model.getColorSpace().isCS_sRGB()) return null;
            if (Arrays.equals(direct.getMasks(), RGB.masks)) return RGB;
            if (!Arrays.equals(direct.getMasks(), ARGB.masks)) return null;
            return model.isAlphaPremultiplied() ? ARGB_PRE : ARGB;
        }

        int unpack(int pixel) {
            return switch (this) {
                case RGB -> pixel | 0xff000000;
                case ARGB -> pixel;
                case ARGB_PRE -> Premultiplication.convert(pixel, false);
            };
        }
    }

    private record PackedRaster(int[] data, int offset, int stride) {
        static PackedRaster of(Raster raster, PackedFormat format) {
            if (!(raster.getSampleModel() instanceof SinglePixelPackedSampleModel sample)
                    || !(raster.getDataBuffer() instanceof DataBufferInt buffer)
                    || !Arrays.equals(sample.getBitMasks(), format.masks)) return null;
            int x = raster.getMinX() - raster.getSampleModelTranslateX();
            int y = raster.getMinY() - raster.getSampleModelTranslateY();
            return new PackedRaster(buffer.getData(), buffer.getOffset() + sample.getOffset(x, y), sample.getScanlineStride());
        }
    }

    private static final class Premultiplication {
        private static final byte[] TO_STRAIGHT = new byte[65536], TO_PREMULTIPLIED = new byte[65536];
        static {
            // Match Java2D's color-model rounding, including translucent render targets.
            var model = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE).getColorModel();
            var data = new int[1];
            for (int alpha = 0; alpha < 256; alpha++) {
                for (int channel = 0; channel < 256; channel++) {
                    int sample = (alpha << 24) | (channel << 16);
                    TO_STRAIGHT[(alpha << 8) | channel] = (byte) (model.getRGB(sample) >>> 16);
                    model.getDataElements(sample, data);
                    TO_PREMULTIPLIED[(alpha << 8) | channel] = (byte) (data[0] >>> 16);
                }
            }
        }

        static int convert(int pixel, boolean toPremultiplied) {
            int alpha = pixel >>> 24;
            if (alpha == 255) return pixel;
            var table = toPremultiplied ? TO_PREMULTIPLIED : TO_STRAIGHT;
            int row = alpha << 8;
            int r = table[row | (pixel >>> 16 & 255)] & 255;
            int g = table[row | (pixel >>> 8 & 255)] & 255;
            int b = table[row | (pixel & 255)] & 255;
            return (pixel & 0xff000000) | (r << 16) | (g << 8) | b;
        }
    }
}
