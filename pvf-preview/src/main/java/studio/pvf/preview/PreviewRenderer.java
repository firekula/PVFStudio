package studio.pvf.preview;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SinglePixelPackedSampleModel;

/** Prepared off the UI thread: native trim offsets, ANI position/rate/rotation and RGBA. */
public final class PreviewRenderer {
    static final String WHITE_MASK = "WHITE_MASK";

    private PreviewRenderer() {}
    public static Prepared prepare(ResourceScript.Frame frame, NpkResources.Sprite sprite) {
        if (WHITE_MASK.equals(frame.effect())) return colorMask(frame, sprite, 0xffffffff, "NONE");
        BufferedImage image = sprite.image();
        if (frame.r() != 255 || frame.g() != 255 || frame.b() != 255 || frame.a() != 255) {
            image = tint(image, frame);
        }
        return prepare(frame, sprite, image, frame.effect());
    }

    static Prepared armorOutline(ResourceScript.Frame frame, NpkResources.Sprite sprite, int color) {
        return colorMask(frame, sprite, color, "LINEARDODGE");
    }

    private static Prepared colorMask(ResourceScript.Frame frame, NpkResources.Sprite sprite, int color, String effect) {
        var image = sprite.image();
        int width = image.getWidth(), height = image.getHeight();
        int alpha = color >>> 24, rgb = color & 0x00ffffff;
        var mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int[] target = ((DataBufferInt) mask.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < pixels.length; i++) target[i] = ((pixels[i] >>> 24) * alpha / 255 << 24) | rgb;
        return prepare(frame, sprite, mask, effect);
    }

    private static Prepared prepare(ResourceScript.Frame frame, NpkResources.Sprite sprite, BufferedImage image, String effect) {
        AffineTransform transform = transform(frame, sprite.canvasWidth(), sprite.canvasHeight(), sprite.x(), sprite.y());
        Rectangle2D bounds = transform.createTransformedShape(new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight())).getBounds2D();
        AffineTransform mirrored = transform(frame, sprite.x(), sprite.y(), true);
        Rectangle2D mirroredBounds = mirrored.createTransformedShape(new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight())).getBounds2D();
        return new Prepared(image, transform, bounds, sprite, effect, mirrored, mirroredBounds);
    }

    private static BufferedImage tint(BufferedImage image, ResourceScript.Frame frame) {
        int width = image.getWidth(), height = image.getHeight();
        var tinted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] target = ((DataBufferInt) tinted.getRaster().getDataBuffer()).getData();
        var raster = image.getRaster();
        int[] pixels;
        int offset;
        int stride;
        if (image.getType() == BufferedImage.TYPE_INT_ARGB
                && raster.getSampleModel() instanceof SinglePixelPackedSampleModel sample
                && raster.getDataBuffer() instanceof DataBufferInt buffer) {
            pixels = buffer.getData();
            offset = buffer.getOffset() + sample.getOffset(-raster.getSampleModelTranslateX(), -raster.getSampleModelTranslateY());
            stride = sample.getScanlineStride();
        } else {
            pixels = image.getRGB(0, 0, width, height, null, 0, width);
            offset = 0;
            stride = width;
        }
        int red = frame.r(), green = frame.g(), blue = frame.b(), alpha = frame.a();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = pixels[offset + x];
                target[y * width + x] = ((color >>> 24) * alpha / 255) << 24
                        | ((color >>> 16 & 255) * red / 255) << 16
                        | ((color >>> 8 & 255) * green / 255) << 8
                        | (color & 255) * blue / 255;
            }
            offset += stride;
        }
        return tinted;
    }

    public static AffineTransform transform(ResourceScript.Frame frame, int width, int height, int trimX, int trimY) {
        return transform(frame, trimX, trimY, false);
    }
    private static AffineTransform transform(ResourceScript.Frame frame, int trimX, int trimY, boolean mirrored) {
        AffineTransform transform = new AffineTransform();
        // 0x82C160 supplies -IMAGE_POS as pivot: rotate/scale around the actor's origin, including trim.
        transform.rotate(frame.rotation());
        transform.scale(mirrored ? -frame.scaleX() : frame.scaleX(), frame.scaleY());
        transform.translate(frame.x() + trimX, frame.y() + trimY);
        return transform;
    }

    public record Prepared(BufferedImage image, AffineTransform transform, Rectangle2D bounds, NpkResources.Sprite source, String effect,
                           AffineTransform mirroredTransform, Rectangle2D mirroredBounds) {
        public Rectangle2D bounds(boolean mirrored) { return mirrored ? mirroredBounds : bounds; }
        public Prepared positioned(ResourceScript.Frame frame) {
            AffineTransform regular = PreviewRenderer.transform(frame, source.x(), source.y(), false);
            AffineTransform mirror = PreviewRenderer.transform(frame, source.x(), source.y(), true);
            var rect = new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight());
            return new Prepared(image, regular, regular.createTransformedShape(rect).getBounds2D(), source, effect,
                    mirror, mirror.createTransformedShape(rect).getBounds2D());
        }
        public void paint(Graphics2D graphics, double originX, double originY, double zoom) {
            paint(graphics, originX, originY, zoom, false);
        }
        public void paint(Graphics2D graphics, double originX, double originY, double zoom, boolean mirrored) {
            paint(graphics, originX, originY, zoom, mirrored, 1);
        }
        public void paint(Graphics2D graphics, double originX, double originY, double zoom, boolean mirrored, double opacity) {
            if (opacity <= 0) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                if (effect.equals("LINEARDODGE")) g.setComposite(opacity == 1 ? AdditiveComposite.INSTANCE : new AdditiveComposite(opacity));
                else if (opacity < 1) g.setComposite(AlphaComposite.SrcOver.derive((float) opacity));
                g.translate(originX, originY); g.scale(zoom, zoom); g.drawImage(image, mirrored ? mirroredTransform : transform, null);
            } finally { g.dispose(); }
        }
    }
}
