package studio.pvf.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Positional, read-only access to supported PVF containers. No entry content is cached. */
public final class PvfArchive implements AutoCloseable {
    static final byte[] MAGIC = "DNF_SCRIPT_PACK".getBytes(StandardCharsets.US_ASCII);
    static final int HEADER_LENGTH = MAGIC.length + 12;
    private static final int MAX_TREE_BYTES = 256 * 1024 * 1024;
    private static final int MAX_PATH_BYTES = 1024 * 1024;
    private static final Charset PATH_CHARSET = Charset.forName("MS949");

    private final Object owner = new Object();
    private final Path sourcePath;
    private final FileChannel channel;
    private final BasicFileAttributes initialAttributes;
    private final long archiveSize;
    private final byte[] header;
    private final byte[] plainTree;
    private final int treeKey;
    private final PvfFormat format;
    private final int keyMask;
    private final int treeKeyPosition;
    private final long bodyOffset;
    private final List<PvfEntry> entries;
    private final List<PvfEntry> physicalEntries;
    private final Map<String, PvfEntry> byPath;
    private final String sha256;

    private PvfArchive(Path path, FileChannel source, ProgressListener progress) throws IOException {
        String detail = path.getFileName().toString();
        progress.update("pvf.header", 0, -1, detail);
        sourcePath = path;
        channel = source;
        initialAttributes = Files.readAttributes(path, BasicFileAttributes.class);
        archiveSize = channel.size();
        if (!initialAttributes.isRegularFile() || archiveSize != initialAttributes.size()) {
            throw invalid("Source is not a stable regular file");
        }
        if (archiveSize < HEADER_LENGTH) throw invalid("Truncated PVF header");
        byte[] prefix = PvfIo.read(channel, 0, HEADER_LENGTH);
        int first = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN).getInt();
        int headerLength;
        int fieldPosition;
        if (Arrays.equals(MAGIC, Arrays.copyOf(prefix, MAGIC.length))) {
            format = PvfFormat.ACT1; headerLength = HEADER_LENGTH; fieldPosition = MAGIC.length;
        } else if (first == 1 && ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN).getInt(4) == 1028) {
            format = PvfFormat.LEGACY; headerLength = 32; fieldPosition = 8;
        } else if (first >= 1 && first <= 256 && first + 20L <= archiveSize) {
            format = PvfFormat.UUID; headerLength = first + 20; fieldPosition = first + 8;
        } else {
            throw invalid("Unsupported PVF header (expected ACT1, UUID or legacy fixed directory)");
        }
        if (archiveSize < headerLength) throw invalid("Truncated PVF header");
        header = PvfIo.read(channel, 0, headerLength);
        ByteBuffer fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int treeLength = fields.getInt(fieldPosition);
        treeKeyPosition = fieldPosition + 4;
        treeKey = format == PvfFormat.LEGACY ? 0 : fields.getInt(treeKeyPosition);
        long fileCount = Integer.toUnsignedLong(fields.getInt(fieldPosition + (format == PvfFormat.LEGACY ? 4 : 8)));
        if (treeLength <= 0 || (treeLength & 3) != 0 || treeLength > MAX_TREE_BYTES
                || treeLength > archiveSize - headerLength) {
            throw invalid("Invalid or unsupported directory size: " + treeLength);
        }
        if (fileCount > treeLength / 21L) throw invalid("File count exceeds directory capacity");
        bodyOffset = (long) headerLength + treeLength;
        progress.update("pvf.directory.read", 0, treeLength, detail);
        byte[] ciphertext = PvfIo.read(channel, headerLength, treeLength);
        progress.update("pvf.directory.read", treeLength, treeLength, detail);
        progress.update("pvf.directory.decode", 0, -1, detail);
        if (format == PvfFormat.LEGACY) {
            if (treeLength % 1028 != 0 || fileCount > treeLength / 1028
                    || fields.getInt(16) != fileCount) throw invalid("Invalid legacy directory capacity/count");
            plainTree = ciphertext; keyMask = 0;
        } else {
            byte[] decoded = ciphertext.clone();
            PvfIo.decrypt(decoded, treeKey);
            if (PvfIo.crc32(decoded, fileCount) == treeKey) {
                plainTree = decoded; keyMask = 0;
            } else if (format == PvfFormat.UUID) {
                decoded = ciphertext.clone(); PvfIo.decrypt(decoded, treeKey ^ 0x81a79011);
                if (PvfIo.crc32(decoded, fileCount) != treeKey) throw invalid("Directory CRC32 mismatch (seed = file count)");
                plainTree = decoded; keyMask = 0x81a79011;
            } else throw invalid("Directory CRC32 mismatch (seed = file count)");
        }
        ByteBuffer tree = ByteBuffer.wrap(plainTree).order(ByteOrder.LITTLE_ENDIAN);
        List<PvfEntry> parsed = new ArrayList<>((int) fileCount);
        Map<String, PvfEntry> paths = new HashMap<>();
        HashSet<Long> fileNumbers = new HashSet<>();
        progress.update("pvf.entries", 0, fileCount, detail);
        for (int index = 0; index < (int) fileCount; index++) {
            PvfIo.checkCancelled();
            int position = tree.position();
            if (tree.remaining() < 20) throw invalid("Truncated directory record " + index);
            long fileNumber = Integer.toUnsignedLong(tree.getInt());
            long offset, unsignedLength, pathLength;
            int key;
            byte[] rawPath;
            if (format == PvfFormat.LEGACY) {
                position = index * 1028; tree.position(position);
                rawPath = new byte[1024]; tree.get(rawPath);
                offset = Integer.toUnsignedLong(tree.getInt()) - bodyOffset;
                if (offset < 0 || offset > archiveSize - bodyOffset - 24) throw invalid("Legacy block outside archive");
                ByteBuffer block = ByteBuffer.wrap(PvfIo.read(channel, bodyOffset + offset, 24)).order(ByteOrder.LITTLE_ENDIAN);
                if (block.getInt(0) != 1 || block.getInt(4) != 1
                        || Integer.toUnsignedLong(block.getInt(8)) != bodyOffset + offset
                        || block.getInt(12) != block.getInt(16)) throw invalid("Unsupported legacy block at record " + index);
                unsignedLength = Integer.toUnsignedLong(block.getInt(12)); key = 0; fileNumber = index;
            } else if (format == PvfFormat.UUID) {
                pathLength = Integer.toUnsignedLong(tree.getInt());
                if (pathLength == 0 || pathLength > MAX_PATH_BYTES || pathLength > tree.remaining() - 12L)
                    throw invalid("Invalid path size at directory record " + index);
                rawPath = new byte[(int) pathLength]; tree.get(rawPath);
                unsignedLength = Integer.toUnsignedLong(tree.getInt()); key = tree.getInt();
                offset = Integer.toUnsignedLong(tree.getInt());
            } else {
                offset = Integer.toUnsignedLong(tree.getInt());
                unsignedLength = Integer.toUnsignedLong(tree.getInt());
                key = tree.getInt(); pathLength = Integer.toUnsignedLong(tree.getInt());
                if (pathLength == 0 || pathLength > MAX_PATH_BYTES || pathLength > tree.remaining())
                    throw invalid("Invalid path size at directory record " + index);
                rawPath = new byte[(int) pathLength]; tree.get(rawPath);
            }
            if (unsignedLength > Integer.MAX_VALUE) {
                throw invalid("Entries above 2 GiB are unsupported at directory record " + index);
            }
            String pathValue = decodePath(rawPath);
            int length = (int) unsignedLength;
            long paddedLength = format == PvfFormat.LEGACY ? 24L + length : PvfIo.align4(length);
            if (offset > archiveSize - bodyOffset || paddedLength > archiveSize - bodyOffset - offset) {
                throw invalid("Entry outside data area: " + pathValue);
            }
            PvfEntry entry = new PvfEntry(owner, index, fileNumber, offset, length, key,
                    pathValue, rawPath, position, format == PvfFormat.LEGACY);
            if (paths.putIfAbsent(pathValue, entry) != null) {
                throw invalid("Duplicate normalized path: " + pathValue);
            }
            if (!fileNumbers.add(fileNumber) && format == PvfFormat.ACT1) throw invalid("Duplicate file number: " + fileNumber);
            parsed.add(entry);
            if ((index & 1023) == 1023 || index + 1 == fileCount)
                progress.update("pvf.entries", index + 1L, fileCount, pathValue);
        }
        progress.update("pvf.index", 0, -1, detail);
        entries = List.copyOf(parsed);
        byPath = Map.copyOf(paths);
        ArrayList<PvfEntry> physical = new ArrayList<>(entries);
        physical.sort(Comparator.comparingLong(PvfEntry::offset)
                .thenComparingLong(PvfEntry::paddedLength).thenComparingInt(PvfEntry::index));
        long previousEnd = 0;
        for (PvfEntry entry : physical) {
            if (entry.offset() < previousEnd) throw invalid("Overlapping data entry: " + entry.path());
            previousEnd = entry.offset() + entry.storedLength();
        }
        physicalEntries = List.copyOf(physical);
        sha256 = PvfIo.sha256(channel, bytes -> progress.update("pvf.hash", bytes, archiveSize, detail));
        checkAttributes();
        // The directory was parsed before the streaming hash. Recheck its exact
        // ciphertext so a same-size, restored-mtime edit cannot pair stale entry
        // metadata with a newer body snapshot.
        byte[] encryptedTree = plainTree.clone();
        if (format != PvfFormat.LEGACY) PvfIo.encrypt(encryptedTree, treeKey ^ keyMask);
        if (!Arrays.equals(header, PvfIo.read(channel, 0, header.length))
                || !Arrays.equals(encryptedTree, PvfIo.read(channel, header.length, treeLength))) {
            throw invalid("Source directory changed while opening");
        }
        try (FileChannel current = FileChannel.open(sourcePath, StandardOpenOption.READ)) {
            if (!sha256.equals(PvfIo.sha256(current,
                    bytes -> progress.update("pvf.snapshot", bytes, archiveSize, detail)))) {
                throw invalid("Source contents or path changed while opening");
            }
        }
        checkAttributes();
        progress.update("pvf.ready", entries.size(), entries.size(), detail);
    }

    public static PvfArchive open(Path path) throws IOException {
        return open(path, ProgressListener.NONE);
    }

    public static PvfArchive open(Path path, ProgressListener progress) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(progress, "progress");
        Path resolved = path.toRealPath();
        FileChannel channel = FileChannel.open(resolved, StandardOpenOption.READ);
        try {
            return new PvfArchive(resolved, channel, progress);
        } catch (Throwable failure) {
            try { channel.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    public Path sourcePath() { return sourcePath; }
    public long archiveSize() { return archiveSize; }
    public int fileCount() { return entries.size(); }
    public List<PvfEntry> entries() { return entries; }
    public String sha256() { return sha256; }
    public PvfFormat format() { return format; }

    public PvfEntry entry(String virtualPath) throws IOException {
        ensureOpen();
        String normalized = normalizePath(virtualPath);
        PvfEntry entry = byPath.get(normalized);
        if (entry == null) throw new FileNotFoundException("PVF entry not found: " + normalized);
        return entry;
    }

    public byte[] readBytes(String virtualPath) throws IOException { return readBytes(entry(virtualPath)); }

    public byte[] readBytes(PvfEntry entry) throws IOException {
        byte[] padded = readPlainPadded(entry);
        return padded.length == entry.length() ? padded : Arrays.copyOf(padded, entry.length());
    }

    /** Confirms both the original open handle and current source path still match the mounted snapshot. */
    public void verifyUnchanged() throws IOException {
        ensureOpen();
        checkAttributes();
        if (!sha256.equals(PvfIo.sha256(channel))) throw invalid("Source contents changed since opening");
        try (FileChannel current = FileChannel.open(sourcePath, StandardOpenOption.READ)) {
            if (!sha256.equals(PvfIo.sha256(current))) throw invalid("Source path was replaced since opening");
        }
        checkAttributes();
    }

    private void checkAttributes() throws IOException {
        BasicFileAttributes current = Files.readAttributes(sourcePath, BasicFileAttributes.class);
        if (!current.isRegularFile() || current.size() != initialAttributes.size()
                || !current.lastModifiedTime().equals(initialAttributes.lastModifiedTime())
                || !Objects.equals(current.fileKey(), initialAttributes.fileKey())
                || channel.size() != archiveSize) {
            throw invalid("Source identity, size or modification time changed since opening");
        }
    }

    private static String decodePath(byte[] bytes) throws IOException {
        int length = bytes.length;
        while (length > 0 && bytes[length - 1] == 0) length--;
        try {
            String decoded = PATH_CHARSET.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, length)).toString();
            ByteBuffer encoded = PATH_CHARSET.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(decoded));
            if (!encoded.equals(ByteBuffer.wrap(bytes, 0, length))) {
                throw new IOException("PVF path is not a lossless CP949 sequence");
            }
            return normalizePath(decoded);
        } catch (CharacterCodingException malformed) {
            throw new IOException("PVF path is not valid CP949", malformed);
        }
    }

    public static String normalizePath(String path) throws IOException {
        if (path == null || path.isEmpty()) throw new IOException("PVF path cannot be empty");
        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0 || normalized.indexOf(':') >= 0) {
            throw new IOException("Invalid PVF path: " + path);
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Invalid PVF path: " + path);
            }
        }
        return normalized;
    }

    public static byte[] encodeNewPath(String path) throws IOException {
        String normalized = normalizePath(path);
        try {
            ByteBuffer encoded = PATH_CHARSET.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(normalized));
            byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes);
            if (bytes.length > MAX_PATH_BYTES) throw new IOException("PVF path exceeds " + MAX_PATH_BYTES + " bytes");
            return bytes;
        } catch (CharacterCodingException invalid) { throw new IOException("PVF path cannot be represented in CP949: " + path, invalid); }
    }

    private IOException invalid(String detail) { return new IOException(sourcePath + ": " + detail); }

    private void ensureOpen() throws IOException {
        if (!channel.isOpen()) throw new IOException("PVF archive is closed: " + sourcePath);
    }

    byte[] readPadded(PvfEntry entry) throws IOException {
        ensureOpen();
        if (entry == null || !entry.belongsTo(owner)) throw new IOException("Entry belongs to another archive");
        if (entry.paddedLength() > Integer.MAX_VALUE) throw invalid("Padded entry exceeds array capacity");
        return PvfIo.read(channel, bodyOffset + entry.offset() + (format == PvfFormat.LEGACY ? 24 : 0), (int) entry.paddedLength());
    }

    byte[] readPlainPadded(PvfEntry entry) throws IOException {
        byte[] padded = readPadded(entry);
        if (format == PvfFormat.LEGACY) { PvfIo.legacyCrypt(padded, false); return padded; }
        PvfIo.decrypt(padded, entry.key() ^ keyMask);
        if (PvfIo.crc32(padded, entrySeed(entry, entry.offset())) != entry.key()) {
            throw invalid("Entry CRC32 mismatch: " + entry.path());
        }
        return padded;
    }

    byte[] header() { return header.clone(); }
    byte[] plainTree() { return plainTree.clone(); }
    int treeKey() { return treeKey; }
    int treeKeyPosition() { return treeKeyPosition; }
    int keyMask() { return keyMask; }
    long entrySeed(PvfEntry entry, long offset) { return format == PvfFormat.UUID ? entry.fileNumber() : offset; }
    void updateRecord(ByteBuffer tree, PvfEntry entry, long offset, int length, int key) {
        int p = entry.treePosition();
        if (format == PvfFormat.UUID) {
            p += 8 + entry.rawPath().length;
            tree.putInt(p, length).putInt(p + 4, key).putInt(p + 8, (int) offset);
        } else tree.putInt(p + 4, (int) offset).putInt(p + 8, length).putInt(p + 12, key);
    }
    long bodyOffset() { return bodyOffset; }
    FileChannel channel() throws IOException { ensureOpen(); return channel; }
    List<PvfEntry> physicalEntries() { return physicalEntries; }

    @Override public void close() throws IOException { channel.close(); }
}
