package studio.pvf.preview;

import java.awt.image.BufferedImage;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only NPK directory index. Payloads and pixels are loaded only when requested. */
public final class NpkResources implements ScenePreview.SpriteLookup {
    private static final byte[] MAGIC = "NeoplePack_Bill\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NAME_KEY = ("puchikon@neople dungeon and fighter " + "DNF".repeat(73) + "\0")
            .getBytes(StandardCharsets.US_ASCII);
    private static final Pattern VARIABLE = Pattern.compile("%0?(\\d{0,2})d");
    private static final long CACHE_LIMIT = 32L * 1024 * 1024;
    private final Path directory;
    private final int packCount;
    private final Map<String, List<Entry>> entries;
    private final List<String> warnings;
    private final LinkedHashMap<Entry, ImgArchive> albums = new LinkedHashMap<>(16, .75f, true);
    private final LinkedHashMap<Key, Sprite> pixels = new LinkedHashMap<>(16, .75f, true);
    private long cacheBytes;

    private NpkResources(Path directory, int packCount, Map<String, List<Entry>> entries, List<String> warnings) {
        this.directory = directory; this.packCount = packCount;
        Map<String, List<Entry>> copy = new HashMap<>();
        entries.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        this.entries = Map.copyOf(copy); this.warnings = List.copyOf(warnings);
    }

    public static NpkResources scan(Path selected) throws IOException {
        Path root = selected.toRealPath();
        if (Files.isDirectory(root.resolve("ImagePacks2"))) root = root.resolve("ImagePacks2").toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("请选择 ImagePacks2 或客户端目录");
        List<Path> packs;
        try (var paths = Files.walk(root)) {
            packs = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".npk"))
                    .sorted().toList();
        }
        if (packs.isEmpty()) throw new IOException("目录中没有 NPK 文件：" + root);
        Map<String, List<Entry>> entries = new HashMap<>(); List<String> warnings = new ArrayList<>();
        int loaded = 0;
        for (Path pack : packs) {
            checkInterrupted();
            try {
                List<Entry> found = readDirectory(pack, warnings);
                for (Entry entry : found) entries.computeIfAbsent(entry.name(), unused -> new ArrayList<>()).add(entry);
                loaded++;
            } catch (IOException failure) { warnings.add(pack.getFileName() + "：" + failure.getMessage()); }
        }
        if (entries.isEmpty()) throw new IOException("未找到可用的 IMG 资源；" + String.join("；", warnings));
        return new NpkResources(root, loaded, entries, warnings);
    }

    private static List<Entry> readDirectory(Path pack, List<String> warnings) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(pack, BasicFileAttributes.class);
        try (FileChannel channel = FileChannel.open(pack, StandardOpenOption.READ)) {
            ByteBuffer header = read(channel, 0, 20);
            byte[] magic = new byte[16]; header.get(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException("不支持的 NPK 文件头");
            long count = Integer.toUnsignedLong(header.getInt());
            if (count > 200_000 || 20 + count * 264 > channel.size()) throw new IOException("NPK 目录长度无效");
            List<Entry> found = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                checkInterrupted();
                ByteBuffer record = read(channel, 20L + i * 264L, 264);
                long offset = Integer.toUnsignedLong(record.getInt()), length = Integer.toUnsignedLong(record.getInt());
                byte[] nameBytes = new byte[256]; record.get(nameBytes);
                String name = decodeName(nameBytes);
                // Some ACT1 packs contain deleted directory slots, with an unsigned -1 length.
                if (length == 0xffffffffL || length == 0) {
                    warnings.add(pack.getFileName() + "：跳过已删除条目 " + name); continue;
                }
                if (offset < 20 + count * 264 || length < 32 || offset > channel.size() - length)
                    throw new IOException("IMG 范围越界：" + name);
                if (name.endsWith(".img")) found.add(new Entry(pack, name, offset, length,
                        attributes.size(), attributes.lastModifiedTime()));
            }
            return found;
        }
    }

    private static String decodeName(byte[] bytes) throws IOException {
        String plain = terminated(bytes);
        if (plain.toLowerCase(Locale.ROOT).replace('\\', '/').startsWith("sprite/")) return normalize(plain);
        byte[] decoded = bytes.clone();
        for (int i = 0; i < decoded.length; i++) decoded[i] ^= NAME_KEY[i];
        String name = terminated(decoded);
        if (!name.toLowerCase(Locale.ROOT).replace('\\', '/').startsWith("sprite/"))
            throw new IOException("无法识别 NPK 资源路径");
        return normalize(name);
    }

    private static String terminated(byte[] bytes) {
        int end = 0; while (end < bytes.length && bytes[end] != 0) end++;
        return new String(bytes, 0, end, Charset.forName("MS949"));
    }

    public static String normalize(String path) {
        String name = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (name.startsWith("/")) name = name.substring(1);
        if (name.startsWith("sprite/")) name = name.substring(7);
        return "sprite/" + name;
    }

    /** Only decimal printf placeholders used by ACT1 avatar resources are accepted. */
    public static String substitute(String path, int... values) throws IOException {
        Matcher matcher = VARIABLE.matcher(path); StringBuilder result = new StringBuilder(); int index = 0;
        while (matcher.find()) {
            if (index >= values.length) throw new IOException("资源路径需要更多外观参数：" + path);
            int value = values[index++];
            int width = matcher.group(1).isEmpty() ? 0 : Integer.parseInt(matcher.group(1));
            if (value < 0 || value > 999999 || width > 8) throw new IOException("外观参数或占位符宽度超出范围");
            String number = Integer.toString(value);
            matcher.appendReplacement(result, "0".repeat(Math.max(0, width - number.length())) + number);
        }
        matcher.appendTail(result);
        if (result.indexOf("%") >= 0) throw new IOException("不支持的资源占位符：" + path);
        return result.toString();
    }

    public synchronized Sprite sprite(String path, int frame) throws IOException {
        Entry entry = resolve(path);
        Key key = new Key(entry, frame);
        Sprite cached = pixels.get(key); if (cached != null) return cached;
        Sprite sprite = album(entry).sprite(frame);
        long bytes = (long) sprite.image().getWidth() * sprite.image().getHeight() * 4;
        while (!pixels.isEmpty() && cacheBytes + bytes > CACHE_LIMIT) {
            Sprite removed = pixels.remove(pixels.keySet().iterator().next());
            cacheBytes -= (long) removed.image().getWidth() * removed.image().getHeight() * 4;
        }
        if (bytes <= CACHE_LIMIT) { pixels.put(key, sprite); cacheBytes += bytes; }
        return sprite;
    }

    public synchronized Info info(String path, int frame) throws IOException { return album(resolve(path)).info(frame); }

    private Entry resolve(String path) throws IOException {
        checkInterrupted();
        List<Entry> matches = entries.get(normalize(path));
        if (matches == null) throw new IOException("找不到 IMG：" + path);
        if (matches.size() != 1) throw new IOException("IMG 位于多个 NPK，请使用无重复资源的目录：" + path
                + "（" + String.join("、", matches.stream().map(e -> e.pack().getFileName().toString()).toList()) + "）");
        Entry entry = matches.get(0);
        BasicFileAttributes attributes = Files.readAttributes(entry.pack(), BasicFileAttributes.class);
        if (attributes.size() != entry.packSize() || !attributes.lastModifiedTime().equals(entry.modified()))
            throw new IOException("NPK 已变化，请重新选择目录：" + entry.pack().getFileName());
        return entry;
    }

    private ImgArchive album(Entry entry) throws IOException {
        ImgArchive album = albums.get(entry);
        if (album == null) {
            album = ImgArchive.open(entry); albums.put(entry, album);
            if (albums.size() > 32) albums.remove(albums.keySet().iterator().next());
        }
        return album;
    }

    static ByteBuffer read(FileChannel channel, long position, int length) throws IOException {
        if (position < 0 || length < 0 || position > channel.size() - length) throw new EOFException("资源数据被截断");
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.hasRemaining()) {
            int count = channel.read(buffer, position + buffer.position());
            if (count < 0) throw new EOFException("资源数据被截断");
        }
        return buffer.flip();
    }

    static void checkInterrupted() throws java.io.InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("预览读取已取消");
    }

    public Path directory() { return directory; }
    public int packCount() { return packCount; }
    public int imageCount() { return entries.size(); }
    public List<String> warnings() { return warnings; }
    record Entry(Path pack, String name, long offset, long length, long packSize,
                 java.nio.file.attribute.FileTime modified) {}
    private record Key(Entry entry, int frame) {}
    public record Info(int width, int height, int x, int y, int canvasWidth, int canvasHeight) {}
    public record Sprite(BufferedImage image, int x, int y, int canvasWidth, int canvasHeight,
                         Path pack, String img, int requestedFrame, int resolvedFrame) {}
}
