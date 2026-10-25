package studio.pvf.intellij;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Per-archive editor metadata. Document text belongs exclusively to the draft workspace. */
record EditorSessionState(List<EditorSessionState.Tab> tabs, String selectedPath) {
    static final String FILE_NAME = "editor-session.properties";
    static final EditorSessionState EMPTY = new EditorSessionState(List.of(), "");

    EditorSessionState {
        tabs = List.copyOf(tabs);
        String requested = selectedPath;
        if (tabs.stream().noneMatch(tab -> tab.path().equals(requested)))
            selectedPath = tabs.isEmpty() ? "" : tabs.getFirst().path();
    }

    record Tab(String path, String charset, int caret, int scrollX, int scrollY) {
        Tab {
            Objects.requireNonNull(path); Objects.requireNonNull(charset);
            caret = Math.max(0, caret); scrollX = Math.max(0, scrollX); scrollY = Math.max(0, scrollY);
        }
    }
    record ReadResult(EditorSessionState state, List<String> warnings) {
        ReadResult { warnings = List.copyOf(warnings); }
    }

    static ReadResult read(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        if (!Files.exists(file)) return new ReadResult(EMPTY, List.of());
        List<String> warnings = new ArrayList<>();
        try {
            if (Files.size(file) > 4 * 1024 * 1024) throw new IOException("会话文件过大");
            Properties properties = new Properties();
            try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(input); }
            if (!"1".equals(properties.getProperty("format"))) throw new IOException("不支持的会话格式");
            int count = Integer.parseInt(properties.getProperty("tabs.count", "0"));
            if (count < 0 || count > 2000) throw new IOException("标签数量无效");
            List<Tab> tabs = new ArrayList<>(); Set<String> paths = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String prefix = "tab." + i + ".";
                String path = properties.getProperty(prefix + "path", "");
                String charset = properties.getProperty(prefix + "charset", "");
                if (path.isBlank() || path.indexOf('\0') >= 0 || paths.contains(path)) {
                    warnings.add("已跳过无效或重复的标签 " + i); continue;
                }
                try { Charset.forName(charset); }
                catch (IllegalArgumentException invalid) { warnings.add("已跳过编码无效的标签：" + path); continue; }
                paths.add(path);
                tabs.add(new Tab(path, charset, offset(properties, prefix + "caret"),
                        offset(properties, prefix + "scrollX"), offset(properties, prefix + "scrollY")));
            }
            return new ReadResult(new EditorSessionState(tabs, properties.getProperty("selected", "")), warnings);
        } catch (IOException | IllegalArgumentException failure) {
            return new ReadResult(EMPTY, List.of("无法读取标签会话：" + failure.getMessage()));
        }
    }

    private static int offset(Properties properties, String key) {
        try { return Math.max(0, Integer.parseInt(properties.getProperty(key, "0"))); }
        catch (NumberFormatException invalid) { return 0; }
    }

    void write(Path directory) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", "1"); properties.setProperty("selected", selectedPath);
        properties.setProperty("tabs.count", Integer.toString(tabs.size()));
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i); String prefix = "tab." + i + ".";
            properties.setProperty(prefix + "path", tab.path()); properties.setProperty(prefix + "charset", tab.charset());
            properties.setProperty(prefix + "caret", Integer.toString(tab.caret()));
            properties.setProperty(prefix + "scrollX", Integer.toString(tab.scrollX()));
            properties.setProperty(prefix + "scrollY", Integer.toString(tab.scrollY()));
        }
        Path temporary = Files.createTempFile(directory, "editor-session-", ".tmp");
        try {
            try (Writer output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(output, "PVF Studio editor session (metadata only)");
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temporary, directory.resolve(FILE_NAME), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, directory.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
