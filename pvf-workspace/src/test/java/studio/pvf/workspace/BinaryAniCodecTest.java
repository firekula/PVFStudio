package studio.pvf.workspace;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class BinaryAniCodecTest {
    record Fixture(byte[] bytes, int delayOffset, int imageOffset) {}
    static Fixture fixture() {
        ByteBuffer b = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 3).putShort((short) 4);
        for (String s : new String[]{"", "Map/test.img", "Map/test.img", "unused.img"}) {
            byte[] raw = s.getBytes(StandardCharsets.US_ASCII); b.putInt(raw.length).put(raw);
        }
        b.putShort((short) 3); // global LOOP, SPECTRUM and OPERATION
        b.putShort((short) 0).put((byte) 1);
        b.putShort((short) 18).put((byte) 2).putInt(30).putInt(60).put(new byte[]{1, 2, 3, (byte) 255}).putShort((short) 0);
        b.putShort((short) 28).putShort((short) 3);
        b.putShort((short) 1).putShort((short) 15); // attack box
        for (int n : new int[]{-10, -20, -30, 40, 50, 60}) b.putInt(n);
        int imageOffset = b.position();
        b.putShort((short) 2).putShort((short) 5).putInt(-120).putInt(-208); // duplicate table index must stay 2
        b.putShort((short) 9);
        b.putShort((short) 7).putInt(0x3eaaaaab).putInt(0x80000000);
        b.putShort((short) 8).putFloat(.125f);
        b.putShort((short) 9).put(new byte[]{1, 2, 3, 4});
        b.putShort((short) 11).putShort((short) 5).put(new byte[]{5, 6, 7});
        b.putShort((short) 24).putShort((short) 3);
        b.putShort((short) 27).putShort((short) -2).putShort((short) -3).putShort((short) 20).putShort((short) 30);
        b.putShort((short) 17);
        b.putShort((short) 16); byte[] sound = "a`b\\sound".getBytes(StandardCharsets.US_ASCII); b.putInt(sound.length).put(sound);
        b.putShort((short) 12); int delayOffset = b.position(); b.putInt(80);
        b.putShort((short) 0).putShort((short) 0).putShort((short) 7).putInt(0).putInt(0).putShort((short) 1).putShort((short) 12).putInt(100);
        b.putShort((short) 0).putShort((short) -3).putInt(0).putInt(0).putShort((short) 1).putShort((short) 12).putInt(0);
        b.put(new byte[]{0x12, 0x34, 0x56});
        return new Fixture(Arrays.copyOf(b.array(), b.position()), delayOffset, imageOffset);
    }
    @Test void exactRoundTripPreservesFloatBitsDuplicateAndEmptyImageReferencesAndOpaqueTail() throws Exception {
        var f = fixture(); String text = BinaryAniCodec.decode(f.bytes, StandardCharsets.US_ASCII);
        assertTrue(text.contains("0.33333334\t-0.0")); assertTrue(text.contains("[SPECTRUM COLOR]"));
        assertTrue(text.contains("``\t7")); assertTrue(text.contains("[FRAME002]"));
        assertArrayEquals(f.bytes, BinaryAniCodec.encode(text + "\n// comment\n", f.bytes, StandardCharsets.US_ASCII));
    }
    @Test void delayEditChangesOnlyItsFourByteField() throws Exception {
        var f = fixture(); String text = BinaryAniCodec.decode(f.bytes, StandardCharsets.US_ASCII).replace("\t80\n", "\t81\n");
        byte[] expected = f.bytes.clone(); ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN).putInt(f.delayOffset, 81);
        assertArrayEquals(expected, BinaryAniCodec.encode(text, f.bytes, StandardCharsets.US_ASCII));
    }
    @Test void newImagePathsAppendWithoutDiscardingTheOriginalImageTable() throws Exception {
        var f = fixture(); String text = BinaryAniCodec.decode(f.bytes, StandardCharsets.US_ASCII).replace("Map/test.img", "New/path.img");
        byte[] compiled = BinaryAniCodec.encode(text, f.bytes, StandardCharsets.US_ASCII);
        assertEquals(5, Short.toUnsignedInt(ByteBuffer.wrap(compiled).order(ByteOrder.LITTLE_ENDIAN).getShort(2)));
        assertTrue(BinaryAniCodec.decode(compiled, StandardCharsets.US_ASCII).contains("New/path.img"));
        assertEquals("Map/test.img", new String(f.bytes, 12, 12, StandardCharsets.US_ASCII));
    }
    @Test void malformedDataAndOutOfRangeOrUnknownTextAreRejected() throws Exception {
        var f = fixture(); String text = BinaryAniCodec.decode(f.bytes, StandardCharsets.US_ASCII);
        assertThrows(BinaryAniCodec.FormatException.class, () -> BinaryAniCodec.decode(new byte[]{1}, StandardCharsets.US_ASCII));
        byte[] bad = f.bytes.clone(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putShort(f.imageOffset, (short) 32767);
        assertThrows(BinaryAniCodec.FormatException.class, () -> BinaryAniCodec.decode(bad, StandardCharsets.US_ASCII));
        assertThrows(java.io.IOException.class, () -> BinaryAniCodec.encode(text.replace("[DELAY]", "[FUTURE]"), f.bytes, StandardCharsets.US_ASCII));
        assertThrows(java.io.IOException.class, () -> BinaryAniCodec.encode(text.replace("\t1\t2\t3\t4\n", "\t256\t2\t3\t4\n"), f.bytes, StandardCharsets.US_ASCII));
        assertThrows(java.io.IOException.class, () -> BinaryAniCodec.encode(text.replace("0.33333334", "NaN"), f.bytes, StandardCharsets.US_ASCII));
        assertThrows(BinaryAniCodec.FormatException.class, () -> BinaryAniCodec.decode(Arrays.copyOf(f.bytes, f.bytes.length + 1), StandardCharsets.US_ASCII));
    }
    @Test void binaryDetectionLeavesTextAndB0D0ScriptsAloneAndSupportsEmptyAnimations() throws Exception {
        assertFalse(BinaryAniCodec.isBinary("test.ani", "[FRAME MAX] 0".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(BinaryAniCodec.isBinary("test.ani", new byte[]{(byte) 0xb0, (byte) 0xd0, 0, 0, 0, 0}));
        assertFalse(BinaryAniCodec.isBinary("test.map", fixture().bytes));
        byte[] empty = new byte[6]; assertTrue(BinaryAniCodec.isBinary("TEST.ANI", empty));
        assertArrayEquals(empty, BinaryAniCodec.encode(BinaryAniCodec.decode(empty, StandardCharsets.US_ASCII), empty, StandardCharsets.US_ASCII));
    }
}
