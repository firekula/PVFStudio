package studio.pvf.workspace;

import studio.pvf.core.PvfArchive;
import studio.pvf.core.PvfWriter;
import studio.pvf.core.ProgressListener;

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
    private final Map<String, String> origins = new LinkedHashMap<>();
    private final Set<String> folders = new LinkedHashSet<>();
    private final Map<Charset, BinaryScriptCodec.StringTable> scriptTables = new LinkedHashMap<>();
    private final Map<Charset, BinaryScriptCodec.StringTable> originalScriptTables = new LinkedHashMap<>();
    private boolean closed;

    private PvfWorkspace(PvfArchive archive, Path directory, FileChannel lockChannel, FileLock lock, ProgressListener progress)
            throws IOException {
        this.archive = archive;
        this.directory = directory;
        this.lockChannel = lockChannel;
        this.lock = lock;
        this.baseSha256 = archive.sha256();
        for (var entry : archive.entries()) origins.put(entry.path(), entry.path());
        progress.update("workspace.drafts", 0, -1, directory.getFileName().toString());
        load(progress);
    }

    public static PvfWorkspace open(Path source, Path directory) throws IOException {
        return open(source, directory, ProgressListener.NONE);
    }

    public static PvfWorkspace open(Path source, Path directory, ProgressListener progress) throws IOException {
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
            archive = PvfArchive.open(source, progress);
            return new PvfWorkspace(archive, root, lockChannel, lock, progress);
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
        Set<String> result = new LinkedHashSet<>(drafts.keySet());
        for (var entry : archive.entries()) if (!origins.containsKey(entry.path())) result.add(entry.path());
        origins.forEach((path, origin) -> { if (!path.equals(origin)) result.add(path); });
        return Collections.unmodifiableSet(result);
    }

    public synchronized Set<String> paths() { ensureOpen(); return new LinkedHashSet<>(origins.keySet()); }
    public synchronized Set<String> folders() { ensureOpen(); return new LinkedHashSet<>(folders); }
    public synchronized boolean contains(String path) throws IOException { ensureOpen(); return origins.containsKey(PvfArchive.normalizePath(path)); }
    public synchronized int length(String path) throws IOException {
        String canonical = canonical(path); String blob = drafts.get(canonical);
        return blob == null ? archive.entry(origins.get(canonical)).length() : readBlob(blob).length;
    }
    private String canonical(String path) throws IOException {
        String canonical = PvfArchive.normalizePath(path);
        if (!origins.containsKey(canonical)) throw new java.io.FileNotFoundException("PVF entry not found: " + canonical);
        return canonical;
    }

    public synchronized byte[] readBytes(String path) throws IOException {
        ensureOpen();
        String canonical = canonical(path);
        String blob = drafts.get(canonical);
        return blob == null ? archive.readBytes(origins.get(canonical)) : readBlob(blob);
    }

    public synchronized String readText(String path, Charset charset) throws IOException {
        byte[] bytes = readBytes(path);
        if (BinaryAniCodec.isBinary(path, bytes)) return BinaryAniCodec.decode(bytes, charset);
        return BinaryScriptCodec.isScript(bytes) ? BinaryScriptCodec.decode(bytes, scriptTable(charset, false))
                : TextCodec.decode(bytes, charset);
    }

    public synchronized String readOriginalText(String path, Charset charset) throws IOException {
        byte[] bytes = readOriginalBytes(path);
        if (BinaryAniCodec.isBinary(path, bytes)) return BinaryAniCodec.decode(bytes, charset);
        return BinaryScriptCodec.isScript(bytes) ? BinaryScriptCodec.decode(bytes, scriptTable(charset, true))
                : TextCodec.decode(bytes, charset);
    }
    public synchronized byte[] readOriginalBytes(String path) throws IOException {
        String origin = origins.get(canonical(path));
        return origin.isEmpty() ? new byte[0] : archive.readBytes(origin);
    }

    private BinaryScriptCodec.StringTable scriptTable(Charset charset, boolean original) throws IOException {
        Map<Charset, BinaryScriptCodec.StringTable> tables = original ? originalScriptTables : scriptTables;
        BinaryScriptCodec.StringTable found = tables.get(charset);
        if (found == null) {
            found = new BinaryScriptCodec.StringTable(original ? archive.readBytes("stringtable.bin") : readBytes("stringtable.bin"), charset);
            tables.put(charset, found);
        }
        return found;
    }

    public synchronized void stageText(String path, String text, Charset charset) throws IOException {
        byte[] before = readBytes(path);
        if (BinaryScriptCodec.isScript(before)) {
            BinaryScriptCodec.Compiled compiled = BinaryScriptCodec.encode(text, before, scriptTable(charset, false));
            stageBatch(Map.of(path, compiled.script(), "stringtable.bin", compiled.stringTable()));
        } else if (BinaryAniCodec.isBinary(path, before)) stageBytes(path, BinaryAniCodec.encode(text, before, charset));
        else stageBytes(path, TextCodec.encode(text, charset));
    }

    public synchronized void stageBytes(String path, byte[] content) throws IOException {
        stageBatch(Map.of(path, content));
    }

    /** Publish related script/string-table drafts in a single manifest transaction. */
    private void stageBatch(Map<String, byte[]> changes) throws IOException {
        ensureOpen();
        Map<String, String> updated = new LinkedHashMap<>(drafts);
        Set<String> created = new LinkedHashSet<>();
        try {
            for (Map.Entry<String, byte[]> change : changes.entrySet()) {
                String canonical = canonical(change.getKey());
                byte[] bytes = change.getValue().clone();
                String previous = drafts.get(canonical);
                String origin = origins.get(canonical);
                if (!origin.isEmpty() && Arrays.equals(bytes, archive.readBytes(origin))) { updated.remove(canonical); continue; }
                if (previous != null && Arrays.equals(bytes, readBlob(previous))) continue;
                String blob = digest(bytes) + "-" + UUID.randomUUID() + ".bin";
                created.add(blob);
                try (FileChannel channel = FileChannel.open(directory.resolve(blob), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                updated.put(canonical, blob);
            }
            if (!updated.equals(drafts)) persist(updated, origins, folders);
        } catch (IOException | RuntimeException ex) {
            for (String blob : created) try { Files.deleteIfExists(directory.resolve(blob)); }
            catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
        Set<String> obsolete = new LinkedHashSet<>(drafts.values()); obsolete.removeAll(updated.values());
        drafts.clear(); drafts.putAll(updated);
        if (changes.keySet().stream().anyMatch(path -> path.equalsIgnoreCase("stringtable.bin"))) scriptTables.clear();
        for (String blob : obsolete) Files.deleteIfExists(directory.resolve(blob));
    }

    public synchronized void revert(String path) throws IOException {
        ensureOpen();
        String origin = origins.get(canonical(path));
        if (origin.isEmpty()) { delete(path); return; }
        stageBytes(path, archive.readBytes(origin));
    }

    private void checkDestination(String path, Set<String> excluded) throws IOException {
        PvfArchive.encodeNewPath(path);
        if (archive.format() == studio.pvf.core.PvfFormat.LEGACY && PvfArchive.encodeNewPath(path).length > 1024) throw new IOException("Legacy 路径超过 1024 字节");
        for (String existing : origins.keySet()) if (!excluded.contains(existing)
                && (existing.equals(path) || existing.startsWith(path + "/") || path.startsWith(existing + "/")))
            throw new IOException("路径已存在或与文件冲突：" + path);
        if (folders.contains(path) && !excluded.contains(path)) throw new IOException("目录已存在：" + path);
    }
    public synchronized void createFile(String path, byte[] content) throws IOException {
        ensureOpen(); String target = PvfArchive.normalizePath(path); checkDestination(target, Set.of());
        // The blob and structural metadata are published by the same manifest replacement.
        Map<String,String> old = new LinkedHashMap<>(origins); origins.put(target, "");
        try { stageBytes(target, content); } catch (IOException | RuntimeException failure) { origins.clear(); origins.putAll(old); throw failure; }
    }
    public synchronized void createTextFile(String path, Charset charset) throws IOException {
        String target = PvfArchive.normalizePath(path);
        boolean binary = archive.format() == studio.pvf.core.PvfFormat.UUID;
        String extension = target.substring(target.lastIndexOf('.') + 1);
        byte[] content;
        if (binary && extension.equals("ani")) content = new byte[6];
        else if (binary && origins.containsKey("stringtable.bin") && Set.of("map", "til", "dgn", "equ", "stk", "obj", "mob", "npc", "chr", "lst", "etc", "ptl", "skl", "qst", "aic", "cre", "pet").contains(extension))
            content = new byte[]{(byte) 0xb0, (byte) 0xd0};
        else content = TextCodec.encode("#PVF_File\n", charset);
        createFile(target, content);
    }
    public synchronized void createDirectory(String path) throws IOException {
        ensureOpen(); String target = PvfArchive.normalizePath(path); checkDestination(target, Set.of());
        Set<String> updated = new LinkedHashSet<>(folders); updated.add(target);
        persist(drafts, origins, updated); folders.clear(); folders.addAll(updated);
    }
    /** Rename one file or an entire virtual directory without rewriting references inside scripts. */
    public synchronized void rename(String path, String destination) throws IOException {
        ensureOpen(); String from = PvfArchive.normalizePath(path), to = PvfArchive.normalizePath(destination);
        if (from.equals(to)) return;
        boolean subtree = !origins.containsKey(from);
        if (subtree && to.startsWith(from + "/")) throw new IOException("不能将目录移动到自身内部");
        Set<String> affected = new LinkedHashSet<>();
        for (String item : origins.keySet()) if (item.equals(from) || subtree && item.startsWith(from + "/")) affected.add(item);
        for (String item : folders) if (subtree && (item.equals(from) || item.startsWith(from + "/"))) affected.add(item);
        if (affected.isEmpty()) throw new IOException("路径不存在：" + from);
        checkDestination(to, affected);
        Map<String,String> updatedOrigins = new LinkedHashMap<>(), updatedDrafts = new LinkedHashMap<>();
        for (var item : origins.entrySet()) {
            String key = affected.contains(item.getKey()) ? to + item.getKey().substring(from.length()) : item.getKey();
            byte[] encoded = PvfArchive.encodeNewPath(key);
            if (archive.format() == studio.pvf.core.PvfFormat.LEGACY && encoded.length > 1024) throw new IOException("Legacy 路径超过 1024 字节：" + key);
            updatedOrigins.put(key, item.getValue());
        }
        for (var item : drafts.entrySet()) updatedDrafts.put(affected.contains(item.getKey()) ? to + item.getKey().substring(from.length()) : item.getKey(), item.getValue());
        Set<String> updatedFolders = new LinkedHashSet<>();
        for (String folder : folders) updatedFolders.add(affected.contains(folder) ? to + folder.substring(from.length()) : folder);
        persist(updatedDrafts, updatedOrigins, updatedFolders);
        drafts.clear(); drafts.putAll(updatedDrafts); origins.clear(); origins.putAll(updatedOrigins); folders.clear(); folders.addAll(updatedFolders); scriptTables.clear();
    }
    public synchronized void delete(String path) throws IOException {
        ensureOpen(); String target = PvfArchive.normalizePath(path);
        boolean subtree = !origins.containsKey(target);
        Map<String,String> updatedOrigins = new LinkedHashMap<>(origins), updatedDrafts = new LinkedHashMap<>(drafts);
        Set<String> updatedFolders = new LinkedHashSet<>(folders);
        java.util.function.Predicate<String> match = p -> p.equals(target) || subtree && p.startsWith(target + "/");
        boolean changed = updatedOrigins.keySet().removeIf(match); changed |= updatedFolders.removeIf(match);
        if (!changed) throw new IOException("路径不存在：" + target);
        updatedDrafts.keySet().removeIf(match);
        persist(updatedDrafts, updatedOrigins, updatedFolders);
        drafts.clear(); drafts.putAll(updatedDrafts); origins.clear(); origins.putAll(updatedOrigins); folders.clear(); folders.addAll(updatedFolders); scriptTables.clear();
    }

    /** A synchronized snapshot excludes edits made after export starts. */
    public void export(Path output) throws IOException {
        Map<String, byte[]> changes = new LinkedHashMap<>();
        Map<String, String> snapshot;
        synchronized (this) {
            ensureOpen();
            for (Map.Entry<String, String> draft : drafts.entrySet()) {
                changes.put(draft.getKey(), readBlob(draft.getValue()));
            }
            snapshot = new LinkedHashMap<>(origins);
        }
        PvfWriter.exportStructure(archive, snapshot, changes, output);
    }

    private void load(ProgressListener progress) throws IOException {
        Path manifest = directory.resolve("workspace.properties");
        if (!Files.exists(manifest)) {
            persist(drafts, origins, folders);
            return;
        }
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(manifest)) { properties.load(stream); }
        if (!Set.of("1", "2").contains(properties.getProperty("format"))) throw new IOException("Unsupported workspace format");
        if (!baseSha256.equals(properties.getProperty("archive.sha256"))) {
            throw new IOException("Workspace belongs to a different source archive; use a new workspace directory");
        }
        for (String key : properties.stringPropertyNames()) if (key.startsWith("delete.")) origins.remove(archive.entry(key.substring(7)).path());
        for (String key : properties.stringPropertyNames()) if (key.startsWith("origin.")) {
            String path = key.substring(7), origin = properties.getProperty(key);
            if (!path.equals(PvfArchive.normalizePath(path)) || origins.containsKey(path)) throw new IOException("Invalid structural path: " + path);
            PvfArchive.encodeNewPath(path); if (!origin.isEmpty()) archive.entry(origin);
            origins.put(path, origin);
        }
        for (String key : properties.stringPropertyNames()) if (key.startsWith("folder.")) folders.add(PvfArchive.normalizePath(key.substring(7)));
        var changes = properties.stringPropertyNames().stream().filter(p -> p.startsWith("change.")).sorted().toList();
        int completed = 0;
        for (String property : changes) {
            String path = property.substring("change.".length());
            String canonical = canonical(path);
            if (!path.equals(canonical) || drafts.containsKey(canonical)) {
                throw new IOException("Invalid or duplicate workspace path: " + path);
            }
            String blob = properties.getProperty(property);
            readBlob(blob);
            drafts.put(canonical, blob);
            progress.update("workspace.drafts", ++completed, changes.size(), canonical);
        }
        for (var item : origins.entrySet()) if (item.getValue().isEmpty() && !drafts.containsKey(item.getKey())) throw new IOException("New entry has no draft: " + item.getKey());
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

    private void persist(Map<String, String> snapshot, Map<String,String> structure, Set<String> directories) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", "2");
        properties.setProperty("archive.path", archive.sourcePath().toString());
        properties.setProperty("archive.sha256", baseSha256);
        for (Map.Entry<String, String> draft : snapshot.entrySet()) {
            properties.setProperty("change." + draft.getKey(), draft.getValue());
        }
        for (var entry : archive.entries()) if (!entry.path().equals(structure.get(entry.path()))) properties.setProperty("delete." + entry.path(), "true");
        structure.forEach((path, origin) -> { if (!path.equals(origin)) properties.setProperty("origin." + path, origin); });
        for (String folder : directories) properties.setProperty("folder." + folder, "true");
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
