package studio.pvf.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PvfCompatibilityTest {
    @TempDir Path temporary;
    private static ByteBuffer le(byte[] b) { return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN); }
    private static int crc(byte[] bytes, long seed) {
        int crc = ~(int) seed;
        for (byte b : bytes) {
            crc ^= b & 255;
            for (int i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) == 0 ? 0 : 0xedb88320);
        }
        return ~crc;
    }
    private static byte[] encrypt(byte[] plain, int key) {
        byte[] result = plain.clone(); ByteBuffer b = le(result);
        for (int i = 0; i < result.length; i += 4) {
            int w = b.getInt(i); b.putInt(i, ((w << 6) | (w >>> 26)) ^ key);
        }
        return result;
    }

    @ParameterizedTest @ValueSource(ints = {0, 0x81a79011})
    void uuidPreservesIdentityAndDuplicateFileNumbersAndUsesNumberSeed(int mask) throws Exception {
        byte[] uuid = "fixture-uuid".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream tree = new ByteArrayOutputStream(), body = new ByteArrayOutputStream();
        for (String path : new String[]{"test/a.etc", "test/b.etc"}) {
            byte[] name = path.getBytes(StandardCharsets.US_ASCII), plain = new byte[]{1, 2, 3, 0x7f};
            int key = crc(plain, 0xfedcba98L);
            tree.writeBytes(le(new byte[8]).putInt(0xfedcba98).putInt(name.length).array()); tree.writeBytes(name);
            tree.writeBytes(le(new byte[12]).putInt(3).putInt(key).putInt(body.size()).array());
            body.writeBytes(encrypt(plain, key ^ mask));
        }
        while (tree.size() % 4 != 0) tree.write(0xab);
        int key = crc(tree.toByteArray(), 2);
        byte[] header = le(new byte[20 + uuid.length]).putInt(uuid.length).put(uuid).putInt(66282)
                .putInt(tree.size()).putInt(key).putInt(2).array();
        ByteArrayOutputStream all = new ByteArrayOutputStream(); all.writeBytes(header);
        all.writeBytes(encrypt(tree.toByteArray(), key ^ mask)); all.writeBytes(body.toByteArray()); all.write(0x56);
        Path source = Files.write(temporary.resolve("uuid.pvf"), all.toByteArray());
        try (PvfArchive archive = PvfArchive.open(source)) {
            assertEquals(PvfFormat.UUID, archive.format());
            assertArrayEquals(new byte[]{1, 2, 3}, archive.readBytes("test/a.etc"));
            Path exact = temporary.resolve("exact.pvf"); PvfWriter.export(archive, Map.of(), exact);
            assertEquals(-1, Files.mismatch(source, exact));
            Path edit = temporary.resolve("edit.pvf");
            PvfWriter.export(archive, Map.of("test/a.etc", new byte[]{9, 8, 7, 6, 5}), edit);
            try (PvfArchive changed = PvfArchive.open(edit)) {
                assertEquals(archive.entry("test/b.etc").key(), changed.entry("test/b.etc").key());
                assertArrayEquals(archive.readBytes("test/b.etc"), changed.readBytes("test/b.etc"));
                assertArrayEquals(new byte[]{9, 8, 7, 6, 5}, changed.readBytes("test/a.etc"));
                assertArrayEquals(Arrays.copyOf(header, uuid.length + 8), Arrays.copyOf(changed.header(), uuid.length + 8));
            }
        }
        byte[] corrupt = all.toByteArray(); corrupt[header.length] ^= 1;
        assertThrows(java.io.IOException.class, () -> PvfArchive.open(Files.write(temporary.resolve("bad.pvf"), corrupt)));
    }

    @Test void legacyPreservesReservedTreeAndLinksAndResizesUnalignedBlocks() throws Exception {
        int body = 32 + 4 * 1028;
        byte[] bytes = new byte[body + 24 + 3 + 24 + 4 + 3]; ByteBuffer b = le(bytes);
        b.putInt(1).putInt(1028).putInt(4 * 1028).putInt(2).putInt(2);
        b.position(32); b.put("/test/a.etc".getBytes(StandardCharsets.US_ASCII)); b.putInt(32 + 1024, body);
        b.position(32 + 1028); b.put("/test/b.etc".getBytes(StandardCharsets.US_ASCII)); b.putInt(32 + 1028 + 1024, body + 27);
        b.put(body - 1, (byte) 0x33); // opaque reserved directory bytes
        b.position(body); b.putInt(1).putInt(1).putInt(body).putInt(3).putInt(3).putInt(body);
        b.put(new byte[]{0x6e, (byte) 0xd5, 0x21}); // independent: swap16('ab' + 0xC74), odd '!'
        b.putInt(1).putInt(1).putInt(body + 27).putInt(4).putInt(4).putInt(body);
        b.put(new byte[]{0x6f, (byte) 0x9f, 0, 0});
        b.put(new byte[]{0x55, 0x44, 0x33});
        Path source = Files.write(temporary.resolve("old.pvf"), bytes);
        try (PvfArchive archive = PvfArchive.open(source)) {
            assertEquals(PvfFormat.LEGACY, archive.format());
            assertArrayEquals("ab!".getBytes(StandardCharsets.US_ASCII), archive.readBytes("test/a.etc"));
            Path exact = temporary.resolve("exact.pvf"); PvfWriter.export(archive, Map.of(), exact);
            assertEquals(-1, Files.mismatch(source, exact));
            Path edited = temporary.resolve("edited.pvf");
            PvfWriter.export(archive, Map.of("test/a.etc", "resize!".getBytes(StandardCharsets.US_ASCII)), edited);
            try (PvfArchive result = PvfArchive.open(edited)) {
                assertEquals(31, result.entry("test/b.etc").offset());
                assertEquals("resize!", new String(result.readBytes("test/a.etc"), StandardCharsets.US_ASCII));
                assertArrayEquals(archive.readBytes("test/b.etc"), result.readBytes("test/b.etc"));
                assertEquals(body, le(Files.readAllBytes(edited)).getInt(body + 31 + 20));
            }
        }
        assertArrayEquals(bytes, Files.readAllBytes(source));
    }
}
