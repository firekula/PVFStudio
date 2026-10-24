package studio.pvf.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Rebuilds existing-entry archives with original keys, untouched ciphertext and layout metadata preserved. */
public final class PvfWriter {
    private static final long MAX_UNSIGNED_INT = 0xffff_ffffL;
    private PvfWriter() { }

    /**
     * Exports a new file. Never replaces an existing destination or the source archive.
     * No-op replacements preserve original encrypted padding as well as content.
     * Source integrity and output contents are checked before publication.
     */
    public static void export(PvfArchive archive, Map<String, byte[]> changes, Path output) throws IOException {
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(output, "output");
        Path requested = output.toAbsolutePath().normalize();
        if (requested.getFileName() == null || requested.getParent() == null) {
            throw new IOException("Output must name a new file");
        }
        Path target = requested.getParent().toRealPath().resolve(requested.getFileName());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || target.equals(archive.sourcePath())) {
            throw new IOException("Output already exists; refusing to overwrite: " + target);
        }
        Map<String, byte[]> replacements = snapshotChanges(archive, changes);
        archive.verifyUnchanged();
        long[] offsets = new long[archive.fileCount()];
        int[] lengths = new int[archive.fileCount()];
        long newBodyLength = calculateLayout(archive, replacements, offsets, lengths);
        byte[] tree = archive.plainTree();
        ByteBuffer treeFields = ByteBuffer.wrap(tree).order(ByteOrder.LITTLE_ENDIAN);
        for (PvfEntry entry : archive.entries()) {
            treeFields.putInt(entry.treePosition() + 4, (int) offsets[entry.index()]);
            treeFields.putInt(entry.treePosition() + 8, lengths[entry.index()]);
        }
        PvfIo.encrypt(tree, archive.treeKey());
        Path temporary = Files.createTempFile(target.getParent(), ".pvf-export-", ".tmp");
        try {
            try (FileChannel destination = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                PvfIo.writeFully(destination, ByteBuffer.wrap(archive.header()), 0);
                PvfIo.writeFully(destination, ByteBuffer.wrap(tree), PvfArchive.HEADER_LENGTH);
                writeBody(archive, replacements, destination, offsets);
                destination.truncate(Math.addExact(archive.bodyOffset(), newBodyLength));
                destination.force(true);
            }
            archive.verifyUnchanged();
            verifyOutput(archive, replacements, temporary, offsets, lengths, newBodyLength);
            archive.verifyUnchanged();
            PvfIo.checkCancelled();
            // No ATOMIC_MOVE: its specification permits replacing an existing destination.
            // Same-directory, no-REPLACE_EXISTING move provides the required no-clobber semantics.
            Files.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Map<String, byte[]> snapshotChanges(PvfArchive archive,
                                                       Map<String, byte[]> changes) throws IOException {
        Map<String, byte[]> normalized = new HashMap<>();
        for (Map.Entry<String, byte[]> change : changes.entrySet()) {
            String path = PvfArchive.normalizePath(change.getKey());
            archive.entry(path);
            byte[] content = change.getValue();
            if (content == null) throw new IOException("Null replacement for " + path);
            if (normalized.putIfAbsent(path, content.clone()) != null) {
                throw new IOException("Duplicate normalized replacement path: " + path);
            }
        }
        var iterator = normalized.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, byte[]> change = iterator.next();
            if (Arrays.equals(change.getValue(), archive.readBytes(change.getKey()))) iterator.remove();
        }
        return normalized;
    }

    private static long calculateLayout(PvfArchive archive, Map<String, byte[]> changes,
                                         long[] offsets, int[] lengths) throws IOException {
        long oldCursor = 0;
        long newCursor = 0;
        for (PvfEntry entry : archive.physicalEntries()) {
            newCursor = add(newCursor, entry.offset() - oldCursor);
            if (newCursor > MAX_UNSIGNED_INT) throw new IOException("Output entry offset exceeds uint32");
            offsets[entry.index()] = newCursor;
            byte[] replacement = changes.get(entry.path());
            int newLength = replacement == null ? entry.length() : replacement.length;
            lengths[entry.index()] = newLength;
            newCursor = add(newCursor, PvfIo.align4(newLength));
            oldCursor = entry.offset() + entry.paddedLength();
        }
        return add(newCursor, archive.archiveSize() - archive.bodyOffset() - oldCursor);
    }

    private static long add(long a, long b) throws IOException {
        try {
            if (b < 0) throw new ArithmeticException("negative layout segment");
            return Math.addExact(a, b);
        } catch (ArithmeticException failure) {
            throw new IOException("PVF output layout overflow", failure);
        }
    }

    private static void writeBody(PvfArchive archive, Map<String, byte[]> changes,
                                   FileChannel destination, long[] offsets) throws IOException {
        long oldCursor = 0;
        long newCursor = 0;
        for (PvfEntry entry : archive.physicalEntries()) {
            long gapLength = entry.offset() - oldCursor;
            PvfIo.copy(archive.channel(), archive.bodyOffset() + oldCursor, destination,
                    archive.bodyOffset() + newCursor, gapLength);
            newCursor = offsets[entry.index()];
            byte[] replacement = changes.get(entry.path());
            long newPaddedLength;
            if (replacement == null) {
                newPaddedLength = entry.paddedLength();
                PvfIo.copy(archive.channel(), archive.bodyOffset() + entry.offset(), destination,
                        archive.bodyOffset() + newCursor, newPaddedLength);
            } else {
                newPaddedLength = PvfIo.align4(replacement.length);
                if (newPaddedLength > Integer.MAX_VALUE) throw new IOException("Replacement exceeds array capacity");
                byte[] padded;
                if (replacement.length == entry.length()) {
                    padded = archive.readPadded(entry);
                    PvfIo.decrypt(padded, entry.key());
                } else {
                    padded = new byte[(int) newPaddedLength];
                }
                System.arraycopy(replacement, 0, padded, 0, replacement.length);
                PvfIo.encrypt(padded, entry.key());
                PvfIo.writeFully(destination, ByteBuffer.wrap(padded), archive.bodyOffset() + newCursor);
            }
            oldCursor = entry.offset() + entry.paddedLength();
            newCursor += newPaddedLength;
        }
        long tailLength = archive.archiveSize() - archive.bodyOffset() - oldCursor;
        PvfIo.copy(archive.channel(), archive.bodyOffset() + oldCursor, destination,
                archive.bodyOffset() + newCursor, tailLength);
    }

    private static void verifyOutput(PvfArchive source, Map<String, byte[]> changes, Path path,
                                      long[] offsets, int[] lengths, long newBodyLength) throws IOException {
        try (PvfArchive exported = PvfArchive.open(path)) {
            if (exported.fileCount() != source.fileCount()
                    || exported.archiveSize() != source.bodyOffset() + newBodyLength
                    || !Arrays.equals(source.header(), exported.header())) {
                throw new IOException("Export validation failed: header, file count or total size");
            }
            for (PvfEntry original : source.entries()) {
                PvfEntry actual = exported.entries().get(original.index());
                if (actual.offset() != offsets[original.index()] || actual.length() != lengths[original.index()]
                        || actual.fileNumber() != original.fileNumber() || actual.key() != original.key()
                        || !Arrays.equals(actual.rawPath(), original.rawPath())) {
                    throw new IOException("Export metadata validation failed: " + original.path());
                }
                byte[] replacement = changes.get(original.path());
                if (replacement != null) {
                    if (!Arrays.equals(replacement, exported.readBytes(actual))) {
                        throw new IOException("Export content validation failed: " + original.path());
                    }
                } else if (!PvfIo.equalRange(source.channel(), source.bodyOffset() + original.offset(),
                        exported.channel(), exported.bodyOffset() + actual.offset(), original.paddedLength())) {
                    throw new IOException("Untouched ciphertext changed: " + original.path());
                }
            }
            // Also validates directory padding, non-entry gaps and trailing opaque data.
            byte[] expectedTree = source.plainTree();
            ByteBuffer fields = ByteBuffer.wrap(expectedTree).order(ByteOrder.LITTLE_ENDIAN);
            for (PvfEntry entry : source.entries()) {
                fields.putInt(entry.treePosition() + 4, (int) offsets[entry.index()]);
                fields.putInt(entry.treePosition() + 8, lengths[entry.index()]);
            }
            if (!Arrays.equals(expectedTree, exported.plainTree())) {
                throw new IOException("Export directory preservation validation failed");
            }
            long oldCursor = 0;
            long newCursor = 0;
            for (PvfEntry entry : source.physicalEntries()) {
                long gap = entry.offset() - oldCursor;
                if (!PvfIo.equalRange(source.channel(), source.bodyOffset() + oldCursor,
                        exported.channel(), exported.bodyOffset() + newCursor, gap)) {
                    throw new IOException("Export data gap preservation failed");
                }
                oldCursor = entry.offset() + entry.paddedLength();
                newCursor = offsets[entry.index()] + PvfIo.align4(lengths[entry.index()]);
            }
            long tail = source.archiveSize() - source.bodyOffset() - oldCursor;
            if (!PvfIo.equalRange(source.channel(), source.bodyOffset() + oldCursor,
                    exported.channel(), exported.bodyOffset() + newCursor, tail)) {
                throw new IOException("Export trailer preservation failed");
            }
        }
    }
}
