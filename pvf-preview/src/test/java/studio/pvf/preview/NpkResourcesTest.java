package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NpkResourcesTest {
    @TempDir Path temporary;

    @Test void oldInterleavedFramesDecodeAllColorsAndKeepTrimOffsets() throws Exception {
        byte[] img = image(1, new byte[][] {
                direct(14, 5, new byte[]{0, (byte) 0xfc}, 2, 3),
                direct(15, 6, new byte[]{(byte) 0xa3, (byte) 0x8f}, 4, 5),
                direct(16, 6, new byte[]{0x33, 0x22, 0x11, (byte) 0x80}, 6, 7), link(1) });
        Path pack = pack("original.npk", "sprite/test.img", img, false);
        NpkResources resources = NpkResources.scan(temporary);
        assertEquals(0xffff0000, resources.sprite("TEST.img", 0).image().getRGB(0, 0));
        assertEquals(0x88ffaa33, resources.sprite("sprite\\test.img", 1).image().getRGB(0, 0));
        assertEquals(0x80112233, resources.sprite("test.img", 2).image().getRGB(0, 0));
        var linked = resources.sprite("test.img", 3);
        assertEquals(1, linked.resolvedFrame()); assertEquals(4, linked.x()); assertEquals(5, linked.y());
        assertEquals(20, linked.canvasWidth()); assertEquals(30, linked.canvasHeight());
        Files.delete(pack); // Readers must not retain pack handles between requests on Windows.
    }

    @Test void v2SeparateDirectoryAndXorNamesResolveLinks() throws Exception {
        pack("xor.npk", "sprite/test.img", image(2, new byte[][]{
                direct(16, 6, new byte[]{1, 2, 3, (byte) 255}, 0, 0), link(0), link(1)}), true);
        var resources = NpkResources.scan(temporary);
        assertEquals(0xff030201, resources.sprite("test.img", 2).image().getRGB(0, 0));
        assertEquals(0, resources.sprite("test.img", 2).resolvedFrame());
        assertThrows(IOException.class, () -> resources.sprite("test.img", 3));
    }

    @Test void transparent1555AndEmptyFramesStayTransparent() throws Exception {
        pack("transparent.npk", "sprite/test.img", image(1, new byte[][]{
                direct(14, 5, new byte[]{(byte) 255, 0x7f}, 0, 0)}), false);
        assertEquals(0x00ffffff, NpkResources.scan(temporary).sprite("test.img", 0).image().getRGB(0, 0));
    }

    @Test void linkCyclesAndTruncatedPayloadsFailClearly() throws Exception {
        pack("cycle.npk", "sprite/test.img", image(1, new byte[][]{link(1), link(0)}), false);
        var resources = NpkResources.scan(temporary);
        assertTrue(assertThrows(IOException.class, () -> resources.sprite("test.img", 0)).getMessage().contains("循环"));
        byte[] invalid = image(1, new byte[][]{direct(16, 6, new byte[]{1, 2, 3, 4}, 0, 0)});
        ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putInt(48, Integer.MAX_VALUE);
        Path broken = temporary.resolve("broken"); Files.createDirectory(broken);
        Files.move(pack("bad.npk", "sprite/bad.img", invalid, false), broken.resolve("bad.npk"));
        var bad = NpkResources.scan(broken);
        assertThrows(IOException.class, () -> bad.sprite("bad.img", 0));
    }

    @Test void duplicatePathsAreReportedAndChangedPacksInvalidateCachedPixels() throws Exception {
        byte[] img = image(1, new byte[][]{direct(16, 5, new byte[]{1, 2, 3, 4}, 0, 0)});
        Path original = pack("one.npk", "sprite/test.img", img, false);
        NpkResources resources = NpkResources.scan(temporary); resources.sprite("test.img", 0);
        Files.write(original, new byte[]{0}, java.nio.file.StandardOpenOption.APPEND);
        assertTrue(assertThrows(IOException.class, () -> resources.sprite("test.img", 0)).getMessage().contains("已变化"));
        pack("two.npk", "sprite/test.img", img, false);
        var duplicate = NpkResources.scan(temporary);
        assertTrue(assertThrows(IOException.class, () -> duplicate.sprite("test.img", 0)).getMessage().contains("多个 NPK"));
    }

    @Test void malformedPacksDoNotHideUsableResources() throws Exception {
        pack("valid.npk", "sprite/test.img", image(1, new byte[][]{direct(16, 5, new byte[]{0, 0, 0, 0}, 0, 0)}), false);
        Files.write(temporary.resolve("broken.npk"), new byte[5]);
        var resources = NpkResources.scan(temporary);
        assertEquals(1, resources.packCount()); assertEquals(1, resources.warnings().size());
        assertNotNull(resources.sprite("test.img", 0));
    }

    @Test void avatarSubstitutionRequiresExplicitValidParameters() throws Exception {
        assertEquals("skin/body0301.img", NpkResources.substitute("skin/body%02d%02d.img", 3, 1));
        assertThrows(IOException.class, () -> NpkResources.substitute("%02d%02d.img", 3));
        assertThrows(IOException.class, () -> NpkResources.substitute("%1000d.img", 3));
        assertThrows(IOException.class, () -> NpkResources.substitute("%02d.img", -1));
    }

    @Test void renderingUsesTrimAndRgbaWithoutMutatingCachedImage() throws Exception {
        pack("render.npk", "sprite/test.img", image(1, new byte[][]{direct(16, 5,
                new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255}, 2, 3)}), false);
        var sprite = NpkResources.scan(temporary).sprite("test.img", 0);
        var frame = new ResourceScript.Frame("test.img", 0, -10, -20, 2, 3, 0, 255, 128, 0, 128, 80, 1);
        var prepared = PreviewRenderer.prepare(frame, sprite);
        assertEquals(0x80ff8000, prepared.image().getRGB(0, 0));
        assertEquals(0xffffffff, sprite.image().getRGB(0, 0));
        assertEquals(-16, prepared.bounds().getX()); assertEquals(-51, prepared.bounds().getY());
        assertEquals(2, prepared.bounds().getWidth()); assertEquals(3, prepared.bounds().getHeight());
    }

    @Test void scaleRotationAndFacingUseAniActorPivotInsteadOfImageCenter() {
        var image = new java.awt.image.BufferedImage(2, 3, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var sprite = new NpkResources.Sprite(image, 10, 20, 400, 500, Path.of("fake.npk"), "part.img", 0, 0);
        var frame = new ResourceScript.Frame("part.img", 0, -20, -30, 2, 3, Math.PI / 2, 255, 255, 255, 255, 80, 1);
        var prepared = PreviewRenderer.prepare(frame, sprite);
        assertEquals(21, prepared.bounds().getX(), 1e-8); assertEquals(-20, prepared.bounds().getY(), 1e-8);
        assertEquals(9, prepared.bounds().getWidth(), 1e-8); assertEquals(4, prepared.bounds().getHeight(), 1e-8);
        assertEquals(21, prepared.bounds(true).getX(), 1e-8); assertEquals(16, prepared.bounds(true).getY(), 1e-8);
    }

    @Test void linearDodgeAddsAlphaWeightedLightAndKeepsBlackTransparentOnTheScene() throws Exception {
        java.awt.image.BufferedImage source = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x80004080); source.setRGB(1, 0, 0xff000000);
        var sprite = new NpkResources.Sprite(source, 0, 0, 2, 1, Path.of("light.npk"), "light.img", 0, 0);
        var frame = ResourceScript.animation("[FRAME000] [IMAGE] `light.img` 0 [GRAPHIC EFFECT] `LINEARDODGE`").frames().get(0);
        java.awt.image.BufferedImage target = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        target.setRGB(0, 0, 0xff102030); target.setRGB(1, 0, 0xff102030);
        var graphics = target.createGraphics();
        try { PreviewRenderer.prepare(frame, sprite).paint(graphics, 0, 0, 1); } finally { graphics.dispose(); }
        assertEquals(0xff104070, target.getRGB(0, 0)); assertEquals(0xff102030, target.getRGB(1, 0));
    }

    private Path pack(String filename, String name, byte[] img, boolean xor) throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(284 + img.length).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("NeoplePack_Bill\0".getBytes(StandardCharsets.US_ASCII)).putInt(1).putInt(284).putInt(img.length);
        byte[] encoded = Arrays.copyOf(name.getBytes(StandardCharsets.US_ASCII), 256);
        if (xor) {
            byte[] key = ("puchikon@neople dungeon and fighter " + "DNF".repeat(73) + "\0").getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i < encoded.length; i++) encoded[i] ^= key[i];
        }
        bytes.put(encoded).put(img); return Files.write(temporary.resolve(filename), bytes.array());
    }
    private static byte[] image(int version, byte[][] frames) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        ByteBuffer header = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        header.put((version == 1 ? "Neople Image File" : "Neople Img File\0").getBytes(StandardCharsets.US_ASCII));
        if (version == 2) header.putInt(16, Arrays.stream(frames).mapToInt(f -> f.length == 8 ? 8 : 36).sum());
        header.putInt(24, version).putInt(28, frames.length); result.write(header.array());
        if (version == 1) for (byte[] frame : frames) result.write(frame);
        else {
            for (byte[] frame : frames) result.write(frame, 0, frame.length == 8 ? 8 : 36);
            for (byte[] frame : frames) if (frame.length != 8) result.write(frame, 36, frame.length - 36);
        }
        return result.toByteArray();
    }
    private static byte[] direct(int type, int compression, byte[] raw, int x, int y) throws IOException {
        byte[] payload = raw;
        if (compression == 6) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DeflaterOutputStream stream = new DeflaterOutputStream(bytes)) { stream.write(raw); }
            payload = bytes.toByteArray();
        }
        return ByteBuffer.allocate(36 + payload.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(type).putInt(compression).putInt(1).putInt(1).putInt(payload.length)
                .putInt(x).putInt(y).putInt(20).putInt(30).put(payload).array();
    }
    private static byte[] link(int target) { return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(17).putInt(target).array(); }
}
