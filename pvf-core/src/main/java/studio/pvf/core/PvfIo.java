package studio.pvf.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class PvfIo {
    static final int BUFFER_SIZE = 128 * 1024;
    private static final int[] CRC32_TABLE = crc32Table();
    private PvfIo() { }

    static long align4(int length) { return ((long) length + 3L) & ~3L; }

    /** Equivalent to zlib.crc32(bytes, seed), including initial/final complements. */
    static int crc32(byte[] bytes, long seed) {
        int crc = ~(int) seed;
        for (byte value : bytes) crc = (crc >>> 8) ^ CRC32_TABLE[(crc ^ value) & 0xff];
        return ~crc;
    }

    private static int[] crc32Table() {
        int[] table = new int[256];
        for (int index = 0; index < table.length; index++) {
            int value = index;
            for (int bit = 0; bit < 8; bit++) value = (value >>> 1) ^ ((value & 1) != 0 ? 0xedb88320 : 0);
            table[index] = value;
        }
        return table;
    }

    static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("PVF operation cancelled");
        }
    }

    static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            checkCancelled();
            int count = channel.read(buffer, position);
            if (count < 0) throw new EOFException("Unexpected end of PVF at byte " + position);
            if (count == 0) throw new IOException("PVF read made no progress at byte " + position);
            position += count;
        }
    }

    static void writeFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            checkCancelled();
            int count = channel.write(buffer, position);
            if (count <= 0) throw new IOException("PVF write made no progress at byte " + position);
            position += count;
        }
    }

    static byte[] read(FileChannel channel, long position, int length) throws IOException {
        byte[] data = new byte[length];
        readFully(channel, ByteBuffer.wrap(data), position);
        return data;
    }

    static void decrypt(byte[] bytes, int key) {
        ByteBuffer words = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset = 0; offset < bytes.length; offset += Integer.BYTES) {
            words.putInt(offset, Integer.rotateRight(words.getInt(offset) ^ key, 6));
        }
    }

    static void encrypt(byte[] bytes, int key) {
        ByteBuffer words = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset = 0; offset < bytes.length; offset += Integer.BYTES) {
            words.putInt(offset, Integer.rotateLeft(words.getInt(offset), 6) ^ key);
        }
    }

    // DNF_Single client: decode 0x4F5210, encode 0x4F51C0.
    // The last byte of an odd-sized entry is deliberately left unchanged.
    static void legacyCrypt(byte[] bytes, boolean encrypt) {
        for (int i = 0; i + 1 < bytes.length; i += 2) {
            int word = Byte.toUnsignedInt(bytes[i]) | Byte.toUnsignedInt(bytes[i + 1]) << 8;
            if (word != 0) {
                word = encrypt ? (word + 0x0c74) & 0xffff
                        : (Short.toUnsignedInt(Short.reverseBytes((short) word)) - 0x0c74) & 0xffff;
                if (encrypt) word = Short.toUnsignedInt(Short.reverseBytes((short) word));
            }
            bytes[i] = (byte) word; bytes[i + 1] = (byte) (word >>> 8);
        }
    }

    static void copy(FileChannel source, long sourcePosition, FileChannel target,
                     long targetPosition, long length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(BUFFER_SIZE, Math.max(1, length)));
        long copied = 0;
        while (copied < length) {
            buffer.clear().limit((int) Math.min(buffer.capacity(), length - copied));
            readFully(source, buffer, sourcePosition + copied);
            buffer.flip();
            int count = buffer.remaining();
            writeFully(target, buffer, targetPosition + copied);
            copied += count;
        }
    }

    static boolean equalRange(FileChannel first, long firstPosition, FileChannel second,
                              long secondPosition, long length) throws IOException {
        ByteBuffer a = ByteBuffer.allocate((int) Math.min(BUFFER_SIZE, Math.max(1, length)));
        ByteBuffer b = ByteBuffer.allocate(a.capacity());
        long compared = 0;
        while (compared < length) {
            int count = (int) Math.min(a.capacity(), length - compared);
            a.clear().limit(count);
            b.clear().limit(count);
            readFully(first, a, firstPosition + compared);
            readFully(second, b, secondPosition + compared);
            a.flip();
            b.flip();
            if (!a.equals(b)) return false;
            compared += count;
        }
        return true;
    }

    static String sha256(FileChannel channel) throws IOException {
        return sha256(channel, ignored -> {});
    }

    static String sha256(FileChannel channel, java.util.function.LongConsumer progress) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        long position = 0;
        long size = channel.size();
        progress.accept(0);
        while (position < size) {
            buffer.clear().limit((int) Math.min(buffer.capacity(), size - position));
            readFully(channel, buffer, position);
            int count = buffer.position();
            buffer.flip();
            digest.update(buffer);
            position += count;
            progress.accept(position);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
