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
    private PvfIo() { }

    static long align4(int length) { return ((long) length + 3L) & ~3L; }

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
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        long position = 0;
        long size = channel.size();
        while (position < size) {
            buffer.clear().limit((int) Math.min(buffer.capacity(), size - position));
            readFully(channel, buffer, position);
            int count = buffer.position();
            buffer.flip();
            digest.update(buffer);
            position += count;
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
