package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AdditiveCompositeTest {
    @Test void packedAndFallbackFormatsPreserveBlendOnTranslatedRasterChildren() {
        int[] sources = {BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE,
                BufferedImage.TYPE_4BYTE_ABGR_PRE};
        int[] destinations = {BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB,
                BufferedImage.TYPE_INT_ARGB_PRE, BufferedImage.TYPE_4BYTE_ABGR};
        for (int source : sources) {
            for (int destination : destinations) {
                for (double opacity : new double[] {0, .267, 1}) {
                    verify(source, destination, opacity);
                }
            }
        }
    }

    private static void verify(int sourceType, int destinationType, double opacity) {
        var source = new BufferedImage(23, 15, sourceType);
        var destination = new BufferedImage(31, 20, destinationType);
        var output = new BufferedImage(29, 22, destinationType);
        var expected = new BufferedImage(29, 22, destinationType);
        var random = new Random(42);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) source.setRGB(x, y, random.nextInt());
        }
        for (int y = 0; y < destination.getHeight(); y++) {
            for (int x = 0; x < destination.getWidth(); x++) destination.setRGB(x, y, random.nextInt());
        }
        var s = source.getRaster().createChild(2, 3, 17, 9, 41, -10, null);
        var d = destination.getRaster().createChild(4, 5, 17, 9, -3, 11, null);
        var o = output.getRaster().createWritableChild(5, 7, 17, 9, -8, -17, null);
        var context = new AdditiveComposite(opacity).createContext(source.getColorModel(), destination.getColorModel(), null);
        try {
            context.compose(s, d, o);
        } finally {
            context.dispose();
        }
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 17; x++) {
                expected.setRGB(x + 5, y + 7, reference(source.getRGB(x + 2, y + 3), destination.getRGB(x + 4, y + 5), opacity));
            }
        }
        assertArrayEquals(expected.getRGB(0, 0, 29, 22, null, 0, 29),
                output.getRGB(0, 0, 29, 22, null, 0, 29),
                "source=" + sourceType + ", destination=" + destinationType + ", opacity=" + opacity);
    }

    private static int reference(int source, int destination, double opacity) {
        int a = (int) ((source >>> 24) * opacity), b = destination >>> 24;
        int alpha = a + b * (255 - a) / 255;
        int result = alpha << 24;
        for (int shift : new int[] {16, 8, 0}) {
            int sum = ((source >>> shift & 255) * a + (destination >>> shift & 255) * b) / 255;
            result |= (alpha == 0 ? 0 : Math.min(255, sum * 255 / alpha)) << shift;
        }
        return result;
    }
}
