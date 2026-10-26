package studio.pvf.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Preserves the reserved directory, block headers, links, gaps and odd final bytes. */
final class LegacyPvfWriter {
    static void export(PvfArchive source, Map<String, byte[]> changes, Path target) throws IOException {
        long[] offsets = new long[source.fileCount()];
        Map<Long, Long> relocated = new HashMap<>();
        long oldCursor = 0, newCursor = 0;
        for (PvfEntry e : source.physicalEntries()) {
            newCursor += e.offset() - oldCursor;
            offsets[e.index()] = newCursor;
            relocated.put(source.bodyOffset() + e.offset(), source.bodyOffset() + newCursor);
            newCursor += 24L + (changes.containsKey(e.path()) ? changes.get(e.path()).length : e.length());
            oldCursor = e.offset() + e.storedLength();
        }
        long size = source.bodyOffset() + newCursor + source.archiveSize() - source.bodyOffset() - oldCursor;
        if (size > 0xffff_ffffL) throw new IOException("Legacy PVF exceeds uint32 offsets");
        byte[] tree = source.plainTree();
        ByteBuffer fields = ByteBuffer.wrap(tree).order(ByteOrder.LITTLE_ENDIAN);
        for (PvfEntry e : source.entries()) fields.putInt(e.treePosition() + 1024, (int) (source.bodyOffset() + offsets[e.index()]));
        Path temporary = Files.createTempFile(target.getParent(), ".pvf-export-", ".tmp");
        try {
            try (FileChannel out = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                PvfIo.writeFully(out, ByteBuffer.wrap(source.header()), 0);
                PvfIo.writeFully(out, ByteBuffer.wrap(tree), source.header().length);
                oldCursor = 0; newCursor = 0;
                for (PvfEntry e : source.physicalEntries()) {
                    long gap = e.offset() - oldCursor;
                    PvfIo.copy(source.channel(), source.bodyOffset() + oldCursor, out, source.bodyOffset() + newCursor, gap);
                    newCursor = offsets[e.index()];
                    byte[] block = PvfIo.read(source.channel(), source.bodyOffset() + e.offset(), 24);
                    ByteBuffer metadata = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
                    long link = Integer.toUnsignedLong(metadata.getInt(20));
                    if (!relocated.containsKey(link)) throw new IOException("Unsupported legacy block link: " + e.path());
                    byte[] replacement = changes.get(e.path());
                    int length = replacement == null ? e.length() : replacement.length;
                    metadata.putInt(8, (int) (source.bodyOffset() + newCursor)).putInt(12, length).putInt(16, length)
                            .putInt(20, relocated.get(link).intValue());
                    PvfIo.writeFully(out, ByteBuffer.wrap(block), source.bodyOffset() + newCursor);
                    if (replacement == null) PvfIo.copy(source.channel(), source.bodyOffset() + e.offset() + 24,
                            out, source.bodyOffset() + newCursor + 24, e.length());
                    else {
                        byte[] encrypted = replacement.clone(); PvfIo.legacyCrypt(encrypted, true);
                        byte[] check = encrypted.clone(); PvfIo.legacyCrypt(check, false);
                        if (!Arrays.equals(check, replacement)) throw new IOException("Legacy encryption cannot represent this byte pair: " + e.path());
                        PvfIo.writeFully(out, ByteBuffer.wrap(encrypted), source.bodyOffset() + newCursor + 24);
                    }
                    oldCursor = e.offset() + e.storedLength(); newCursor += 24L + length;
                }
                PvfIo.copy(source.channel(), source.bodyOffset() + oldCursor, out,
                        source.bodyOffset() + newCursor, source.archiveSize() - source.bodyOffset() - oldCursor);
                out.truncate(size); out.force(true);
            }
            source.verifyUnchanged();
            try (PvfArchive result = PvfArchive.open(temporary)) {
                if (!Arrays.equals(source.header(), result.header()) || !Arrays.equals(tree, result.plainTree()))
                    throw new IOException("Legacy export header/directory validation failed");
                for (PvfEntry e : source.entries()) {
                    PvfEntry actual = result.entries().get(e.index());
                    if (actual.offset() != offsets[e.index()] || !actual.path().equals(e.path())
                            || !Arrays.equals(changes.containsKey(e.path()) ? changes.get(e.path()) : source.readBytes(e), result.readBytes(actual)))
                        throw new IOException("Legacy export content validation failed: " + e.path());
                    byte[] before = PvfIo.read(source.channel(), source.bodyOffset() + e.offset(), 24);
                    ByteBuffer metadata = ByteBuffer.wrap(before).order(ByteOrder.LITTLE_ENDIAN);
                    long oldLink = Integer.toUnsignedLong(metadata.getInt(20));
                    metadata.putInt(8, (int) (result.bodyOffset() + actual.offset())).putInt(12, actual.length())
                            .putInt(16, actual.length()).putInt(20, relocated.get(oldLink).intValue());
                    if (!Arrays.equals(before, PvfIo.read(result.channel(), result.bodyOffset() + actual.offset(), 24)))
                        throw new IOException("Legacy export block/link validation failed: " + e.path());
                    if (!changes.containsKey(e.path()) && !PvfIo.equalRange(source.channel(), source.bodyOffset() + e.offset() + 24,
                            result.channel(), result.bodyOffset() + actual.offset() + 24, e.length()))
                        throw new IOException("Legacy untouched ciphertext changed: " + e.path());
                }
                oldCursor = 0; newCursor = 0;
                for (PvfEntry e : source.physicalEntries()) {
                    if (!PvfIo.equalRange(source.channel(), source.bodyOffset() + oldCursor,
                            result.channel(), result.bodyOffset() + newCursor, e.offset() - oldCursor))
                        throw new IOException("Legacy opaque gap preservation failed");
                    oldCursor = e.offset() + e.storedLength();
                    PvfEntry actual = result.entries().get(e.index()); newCursor = actual.offset() + actual.storedLength();
                }
                if (!PvfIo.equalRange(source.channel(), source.bodyOffset() + oldCursor,
                        result.channel(), result.bodyOffset() + newCursor, source.archiveSize() - source.bodyOffset() - oldCursor))
                    throw new IOException("Legacy trailer preservation failed");
            }
            source.verifyUnchanged(); PvfIo.checkCancelled(); Files.move(temporary, target);
        } finally { Files.deleteIfExists(temporary); }
    }
}
