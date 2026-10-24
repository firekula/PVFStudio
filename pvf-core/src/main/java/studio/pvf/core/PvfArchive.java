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

/** Positional, read-only access to the ACT1 DNF_SCRIPT_PACK format. No entry content is cached. */
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
    private final long bodyOffset;
    private final List<PvfEntry> entries;
    private final List<PvfEntry> physicalEntries;
    private final Map<String, PvfEntry> byPath;
    private final String sha256;

    private PvfArchive(Path path, FileChannel source) throws IOException {
        sourcePath = path;
        channel = source;
        initialAttributes = Files.readAttributes(path, BasicFileAttributes.class);
        archiveSize = channel.size();
        if (!initialAttributes.isRegularFile() || archiveSize != initialAttributes.size()) {
            throw invalid("Source is not a stable regular file");
        }
        if (archiveSize < HEADER_LENGTH) throw invalid("Truncated PVF header");
        header = PvfIo.read(channel, 0, HEADER_LENGTH);
        if (!Arrays.equals(MAGIC, Arrays.copyOf(header, MAGIC.length))) {
            throw invalid("Expected DNF_SCRIPT_PACK magic");
        }
        ByteBuffer fields = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int treeLength = fields.getInt(MAGIC.length);
        treeKey = fields.getInt(MAGIC.length + 4);
        long fileCount = Integer.toUnsignedLong(fields.getInt(MAGIC.length + 8));
        if (treeLength <= 0 || (treeLength & 3) != 0 || treeLength > MAX_TREE_BYTES
                || treeLength > archiveSize - HEADER_LENGTH) {
            throw invalid("Invalid or unsupported directory size: " + treeLength);
        }
        if (fileCount > treeLength / 21L) throw invalid("File count exceeds directory capacity");
        bodyOffset = (long) HEADER_LENGTH + treeLength;
        plainTree = PvfIo.read(channel, HEADER_LENGTH, treeLength);
        PvfIo.decrypt(plainTree, treeKey);
        ByteBuffer tree = ByteBuffer.wrap(plainTree).order(ByteOrder.LITTLE_ENDIAN);
        List<PvfEntry> parsed = new ArrayList<>((int) fileCount);
        Map<String, PvfEntry> paths = new HashMap<>();
        HashSet<Long> fileNumbers = new HashSet<>();
        for (int index = 0; index < (int) fileCount; index++) {
            PvfIo.checkCancelled();
            int position = tree.position();
            if (tree.remaining() < 20) throw invalid("Truncated directory record " + index);
            long fileNumber = Integer.toUnsignedLong(tree.getInt());
            long offset = Integer.toUnsignedLong(tree.getInt());
            long unsignedLength = Integer.toUnsignedLong(tree.getInt());
            int key = tree.getInt();
            long pathLength = Integer.toUnsignedLong(tree.getInt());
            if (unsignedLength > Integer.MAX_VALUE) {
                throw invalid("Entries above 2 GiB are unsupported at directory record " + index);
            }
            if (pathLength == 0 || pathLength > MAX_PATH_BYTES || pathLength > tree.remaining()) {
                throw invalid("Invalid path size at directory record " + index);
            }
            byte[] rawPath = new byte[(int) pathLength];
            tree.get(rawPath);
            String pathValue = decodePath(rawPath);
            int length = (int) unsignedLength;
            long paddedLength = PvfIo.align4(length);
            if (offset > archiveSize - bodyOffset || paddedLength > archiveSize - bodyOffset - offset) {
                throw invalid("Entry outside data area: " + pathValue);
            }
            PvfEntry entry = new PvfEntry(owner, index, fileNumber, offset, length, key,
                    pathValue, rawPath, position);
            if (paths.putIfAbsent(pathValue, entry) != null) {
                throw invalid("Duplicate normalized path: " + pathValue);
            }
            if (!fileNumbers.add(fileNumber)) throw invalid("Duplicate file number: " + fileNumber);
            parsed.add(entry);
        }
        entries = List.copyOf(parsed);
        byPath = Map.copyOf(paths);
        ArrayList<PvfEntry> physical = new ArrayList<>(entries);
        physical.sort(Comparator.comparingLong(PvfEntry::offset)
                .thenComparingLong(PvfEntry::paddedLength).thenComparingInt(PvfEntry::index));
        long previousEnd = 0;
        for (PvfEntry entry : physical) {
            if (entry.offset() < previousEnd) throw invalid("Overlapping data entry: " + entry.path());
            previousEnd = entry.offset() + entry.paddedLength();
        }
        physicalEntries = List.copyOf(physical);
        sha256 = PvfIo.sha256(channel);
        checkAttributes();
        // The directory was parsed before the streaming hash. Recheck its exact
        // ciphertext so a same-size, restored-mtime edit cannot pair stale entry
        // metadata with a newer body snapshot.
        byte[] encryptedTree = plainTree.clone();
        PvfIo.encrypt(encryptedTree, treeKey);
        if (!Arrays.equals(header, PvfIo.read(channel, 0, HEADER_LENGTH))
                || !Arrays.equals(encryptedTree, PvfIo.read(channel, HEADER_LENGTH, treeLength))) {
            throw invalid("Source directory changed while opening");
        }
        try (FileChannel current = FileChannel.open(sourcePath, StandardOpenOption.READ)) {
            if (!sha256.equals(PvfIo.sha256(current))) {
                throw invalid("Source contents or path changed while opening");
            }
        }
        checkAttributes();
    }

    public static PvfArchive open(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path resolved = path.toRealPath();
        FileChannel channel = FileChannel.open(resolved, StandardOpenOption.READ);
        try {
            return new PvfArchive(resolved, channel);
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

    public PvfEntry entry(String virtualPath) throws IOException {
        ensureOpen();
        String normalized = normalizePath(virtualPath);
        PvfEntry entry = byPath.get(normalized);
        if (entry == null) throw new FileNotFoundException("PVF entry not found: " + normalized);
        return entry;
    }

    public byte[] readBytes(String virtualPath) throws IOException { return readBytes(entry(virtualPath)); }

    public byte[] readBytes(PvfEntry entry) throws IOException {
        byte[] padded = readPadded(entry);
        PvfIo.decrypt(padded, entry.key());
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

    private IOException invalid(String detail) { return new IOException(sourcePath + ": " + detail); }

    private void ensureOpen() throws IOException {
        if (!channel.isOpen()) throw new IOException("PVF archive is closed: " + sourcePath);
    }

    byte[] readPadded(PvfEntry entry) throws IOException {
        ensureOpen();
        if (entry == null || !entry.belongsTo(owner)) throw new IOException("Entry belongs to another archive");
        if (entry.paddedLength() > Integer.MAX_VALUE) throw invalid("Padded entry exceeds array capacity");
        return PvfIo.read(channel, bodyOffset + entry.offset(), (int) entry.paddedLength());
    }

    byte[] header() { return header.clone(); }
    byte[] plainTree() { return plainTree.clone(); }
    int treeKey() { return treeKey; }
    long bodyOffset() { return bodyOffset; }
    FileChannel channel() throws IOException { ensureOpen(); return channel; }
    List<PvfEntry> physicalEntries() { return physicalEntries; }

    @Override public void close() throws IOException { channel.close(); }
}
