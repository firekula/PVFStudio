package studio.pvf.workspace;

import studio.pvf.core.PvfArchive;
import studio.pvf.core.PvfWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Persistent drafts bound to an exact source archive. Original PVFs are never written. */
public final class PvfWorkspace implements AutoCloseable {
    private final PvfArchive archive;
    private final Path directory;
    private final String baseSha256;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final Map<String, String> drafts = new LinkedHashMap<>();
    private boolean closed;

    private PvfWorkspace(PvfArchive archive, Path directory, FileChannel lockChannel, FileLock lock)
            throws IOException {
        this.archive = archive;
        this.directory = directory;
        this.lockChannel = lockChannel;
        this.lock = lock;
        this.baseSha256 = archive.sha256();
        load();
    }

    public static PvfWorkspace open(Path source, Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        FileChannel lockChannel = FileChannel.open(root.resolve("workspace.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = null;
        PvfArchive archive = null;
        try {
            try { lock = lockChannel.tryLock(); }
            catch (OverlappingFileLockException ex) {
                throw new IOException("Workspace is already open: " + root, ex);
            }
            if (lock == null) throw new IOException("Workspace is already open: " + root);
            archive = PvfArchive.open(source);
            return new PvfWorkspace(archive, root, lockChannel, lock);
        } catch (IOException | RuntimeException ex) {
            if (archive != null) try { archive.close(); } catch (IOException close) { ex.addSuppressed(close); }
            if (lock != null) try { lock.release(); } catch (IOException close) { ex.addSuppressed(close); }
            try { lockChannel.close(); } catch (IOException close) { ex.addSuppressed(close); }
            throw ex;
        }
    }

    public synchronized PvfArchive archive() { ensureOpen(); return archive; }
    public Path directory() { return directory; }

    public synchronized Set<String> dirtyPaths() {
        ensureOpen();
        return Collections.unmodifiableSet(new LinkedHashSet<>(drafts.keySet()));
    }

    public synchronized byte[] readBytes(String path) throws IOException {
        ensureOpen();
        String canonical = archive.entry(path).path();
        String blob = drafts.get(canonical);
        return blob == null ? archive.readBytes(canonical) : readBlob(blob);
    }

    public synchronized String readText(String path, Charset charset) throws IOException {
        return TextCodec.decode(readBytes(path), charset);
    }

    public synchronized void stageText(String path, String text, Charset charset) throws IOException {
        stageBytes(path, TextCodec.encode(text, charset));
    }

    public synchronized void stageBytes(String path, byte[] content) throws IOException {
        ensureOpen();
        String canonical = archive.entry(path).path();
        byte[] bytes = content.clone();
        String previous = drafts.get(canonical);
        if (Arrays.equals(bytes, archive.readBytes(canonical))) {
            if (previous != null) {
                Map<String, String> updated = new LinkedHashMap<>(drafts);
                updated.remove(canonical);
                persist(updated);
                drafts.remove(canonical);
                Files.deleteIfExists(directory.resolve(previous));
            }
            return;
        }
        if (previous != null && Arrays.equals(bytes, readBlob(previous))) return;
        String blob = digest(bytes) + "-" + UUID.randomUUID() + ".bin";
        Path newBlob = directory.resolve(blob);
        try (FileChannel channel = FileChannel.open(newBlob,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        Map<String, String> updated = new LinkedHashMap<>(drafts);
        updated.put(canonical, blob);
        try { persist(updated); }
        catch (IOException ex) {
            try { Files.deleteIfExists(newBlob); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
        drafts.put(canonical, blob);
        if (previous != null) Files.deleteIfExists(directory.resolve(previous));
    }

    public synchronized void revert(String path) throws IOException {
        ensureOpen();
        stageBytes(path, archive.readBytes(path));
    }

    /** A synchronized snapshot excludes edits made after export starts. */
    public void export(Path output) throws IOException {
        Map<String, byte[]> changes = new LinkedHashMap<>();
        synchronized (this) {
            ensureOpen();
            for (Map.Entry<String, String> draft : drafts.entrySet()) {
                changes.put(draft.getKey(), readBlob(draft.getValue()));
            }
        }
        PvfWriter.export(archive, changes, output);
    }

    private void load() throws IOException {
        Path manifest = directory.resolve("workspace.properties");
        if (!Files.exists(manifest)) {
            persist(drafts);
            return;
        }
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(manifest)) { properties.load(stream); }
        if (!"1".equals(properties.getProperty("format"))) throw new IOException("Unsupported workspace format");
        if (!baseSha256.equals(properties.getProperty("archive.sha256"))) {
            throw new IOException("Workspace belongs to a different source archive; use a new workspace directory");
        }
        for (String property : properties.stringPropertyNames().stream().sorted().toList()) {
            if (!property.startsWith("change.")) continue;
            String path = property.substring("change.".length());
            String canonical = archive.entry(path).path();
            if (!path.equals(canonical) || drafts.containsKey(canonical)) {
                throw new IOException("Invalid or duplicate workspace path: " + path);
            }
            String blob = properties.getProperty(property);
            readBlob(blob);
            drafts.put(canonical, blob);
        }
    }

    private byte[] readBlob(String name) throws IOException {
        if (name == null || !name.matches("[0-9a-f]{64}-[0-9a-f-]{36}\\.bin")) {
            throw new IOException("Invalid draft filename");
        }
        Path path = directory.resolve(name);
        if (Files.isSymbolicLink(path)) throw new IOException("Draft must not be a symbolic link: " + name);
        byte[] bytes = Files.readAllBytes(path);
        if (!digest(bytes).equals(name.substring(0, 64))) throw new IOException("Draft checksum mismatch: " + name);
        return bytes;
    }

    private void persist(Map<String, String> snapshot) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", "1");
        properties.setProperty("archive.path", archive.sourcePath().toString());
        properties.setProperty("archive.sha256", baseSha256);
        for (Map.Entry<String, String> draft : snapshot.entrySet()) {
            properties.setProperty("change." + draft.getKey(), draft.getValue());
        }
        Path temporary = Files.createTempFile(directory, "manifest-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                properties.store(stream, "PVF Studio workspace - original archive is never modified");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try {
                Files.move(temporary, directory.resolve("workspace.properties"),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                throw new IOException("Workspace requires atomic file replacement on its filesystem", ex);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("Workspace is closed"); }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException error = null;
        try { archive.close(); } catch (IOException ex) { error = ex; }
        try { lock.release(); } catch (IOException ex) { if (error == null) error = ex; else error.addSuppressed(ex); }
        try { lockChannel.close(); } catch (IOException ex) { if (error == null) error = ex; else error.addSuppressed(ex); }
        if (error != null) throw error;
    }
}
