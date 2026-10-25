package studio.pvf.intellij;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Global recent PVFs; successful opens are promoted without changing archive contents. */
record RecentArchives(List<Path> paths) {
    static final int LIMIT = 15;
    static final RecentArchives EMPTY = new RecentArchives(List.of());

    RecentArchives {
        paths = paths.stream().map(path -> path.toAbsolutePath().normalize()).distinct().limit(LIMIT).toList();
    }
    RecentArchives opened(Path path) {
        List<Path> updated = new ArrayList<>(); updated.add(path); updated.addAll(paths);
        return new RecentArchives(updated);
    }
    static RecentArchives read(Path file) throws IOException {
        if (!Files.exists(file)) return EMPTY;
        if (Files.size(file) > 1024 * 1024) throw new IOException("最近文件记录过大");
        Properties properties = new Properties();
        try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(input); }
        catch (IllegalArgumentException invalid) { throw new IOException("最近文件记录格式无效", invalid); }
        if (!"1".equals(properties.getProperty("format"))) throw new IOException("不支持的最近文件记录格式");
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < LIMIT; i++) {
            String value = properties.getProperty("path." + i);
            if (value == null || value.isBlank()) continue;
            try { Path path = Path.of(value); if (path.isAbsolute()) paths.add(path); }
            catch (InvalidPathException invalid) { /* Retain all other valid records. */ }
        }
        return new RecentArchives(paths);
    }
    void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Properties properties = new Properties(); properties.setProperty("format", "1");
        for (int i = 0; i < paths.size(); i++) properties.setProperty("path." + i, paths.get(i).toString());
        Path temporary = Files.createTempFile(file.getParent(), "recent-files-", ".tmp");
        try {
            try (Writer output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { properties.store(output, "PVF Studio recent archives"); }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
