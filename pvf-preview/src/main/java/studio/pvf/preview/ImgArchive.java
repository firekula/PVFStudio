package studio.pvf.preview;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** IMG v1 has interleaved records/payloads; v2 stores a separate frame directory. */
final class ImgArchive {
    private static final int MAX_PIXELS = 16 * 1024 * 1024;
    private final NpkResources.Entry entry;
    private final List<Frame> frames;

    private ImgArchive(NpkResources.Entry entry, List<Frame> frames) { this.entry = entry; this.frames = frames; }

    static ImgArchive open(NpkResources.Entry entry) throws IOException {
        try (FileChannel channel = FileChannel.open(entry.pack(), StandardOpenOption.READ)) {
            ByteBuffer header = NpkResources.read(channel, entry.offset(), 32);
            byte[] signature = new byte[16]; header.get(signature);
            String magic = new String(signature, StandardCharsets.US_ASCII);
            int version = header.getInt(24), count = header.getInt(28);
            if (!(version == 1 && magic.equals("Neople Image Fil"))
                    && !(version == 2 && magic.startsWith("Neople Img File")))
                throw new IOException("不支持的 IMG 格式/版本 " + version + "：" + entry.name() + "（支持 v1/v2）");
            if (count < 1 || count > 100_000 || count > (entry.length() - 32) / 8)
                throw new IOException("IMG 帧数无效：" + entry.name());
            long tableSize = Integer.toUnsignedLong(header.getInt(16));
            if (version == 2 && (tableSize < count * 8L || tableSize > entry.length() - 32))
                throw new IOException("IMG 帧目录长度无效");
            long cursor = 32, data = version == 2 ? 32 + tableSize : 0;
            List<Frame> frames = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                NpkResources.checkInterrupted();
                requireRange(entry, cursor, 8);
                ByteBuffer prefix = NpkResources.read(channel, entry.offset() + cursor, 8);
                int type = prefix.getInt(), mode = prefix.getInt();
                if (type == 17) {
                    if (mode < 0 || mode >= count) throw new IOException("IMG 链接帧越界：" + i + " → " + mode);
                    frames.add(new Frame(type, 0, 0, 0, 0, 0, 0, 0, 0, mode)); cursor += 8; continue;
                }
                if (type < 14 || type > 16) throw new IOException("不支持的 IMG 颜色格式：" + type);
                if (mode != 5 && mode != 6) throw new IOException("不支持的 IMG 压缩格式：" + mode);
                requireRange(entry, cursor, 36);
                ByteBuffer record = NpkResources.read(channel, entry.offset() + cursor + 8, 28);
                int width = record.getInt(), height = record.getInt(), stored = record.getInt();
                int x = record.getInt(), y = record.getInt(), canvasWidth = record.getInt(), canvasHeight = record.getInt();
                if (width < 0 || height < 0 || width > 16384 || height > 16384 || (long) width * height > MAX_PIXELS
                        || canvasWidth < 0 || canvasWidth > 32768 || canvasHeight < 0 || canvasHeight > 32768
                        || Math.abs((long) x) > 32768 || Math.abs((long) y) > 32768)
                    throw new IOException("IMG 帧尺寸或偏移超出范围：" + i);
                int rawLength = width * height * (type == 16 ? 4 : 2);
                int length = mode == 5 ? rawLength : stored;
                if (length < 0 || length > 64 * 1024 * 1024) throw new IOException("IMG 数据长度超出范围");
                cursor += 36;
                long offset = version == 1 ? cursor : data;
                requireRange(entry, offset, length);
                frames.add(new Frame(type, mode, width, height, x, y, canvasWidth, canvasHeight, offset, length));
                if (version == 1) cursor += length; else data += length;
            }
            if (version == 2 && cursor != 32 + tableSize) throw new IOException("IMG 帧目录长度与记录不符");
            return new ImgArchive(entry, List.copyOf(frames));
        }
    }

    NpkResources.Sprite sprite(int requested) throws IOException {
        int index = resolve(requested); Frame frame = frames.get(index);
        byte[] raw;
        try (FileChannel channel = FileChannel.open(entry.pack(), StandardOpenOption.READ)) {
            raw = NpkResources.read(channel, entry.offset() + frame.offset(), frame.length()).array();
        }
        int expected = frame.width() * frame.height() * (frame.type() == 16 ? 4 : 2);
        if (frame.mode() == 6) raw = inflate(raw, expected);
        BufferedImage image = new BufferedImage(Math.max(1, frame.width()), Math.max(1, frame.height()), BufferedImage.TYPE_INT_ARGB);
        int[] colors = new int[frame.width() * frame.height()];
        for (int i = 0, position = 0; i < colors.length; i++) {
            if ((i & 65535) == 0) NpkResources.checkInterrupted();
            if (frame.type() == 16) {
                int b = raw[position++] & 255, g = raw[position++] & 255, r = raw[position++] & 255, a = raw[position++] & 255;
                colors[i] = a << 24 | r << 16 | g << 8 | b;
            } else {
                int value = (raw[position++] & 255) | (raw[position++] & 255) << 8;
                if (frame.type() == 14) colors[i] = ((value & 0x8000) == 0 ? 0 : 0xff000000)
                        | expand5(value >> 10 & 31) << 16 | expand5(value >> 5 & 31) << 8 | expand5(value & 31);
                else colors[i] = (value >> 12 & 15) * 17 << 24 | (value >> 8 & 15) * 17 << 16
                        | (value >> 4 & 15) * 17 << 8 | (value & 15) * 17;
            }
        }
        if (colors.length > 0) image.setRGB(0, 0, frame.width(), frame.height(), colors, 0, frame.width());
        return new NpkResources.Sprite(image, frame.x(), frame.y(), frame.canvasWidth(), frame.canvasHeight(),
                entry.pack(), entry.name(), requested, index);
    }

    NpkResources.Info info(int requested) throws IOException {
        Frame frame = frames.get(resolve(requested));
        return new NpkResources.Info(frame.width(), frame.height(), frame.x(), frame.y(), frame.canvasWidth(), frame.canvasHeight());
    }

    private int resolve(int requested) throws IOException {
        if (requested < 0 || requested >= frames.size()) throw new IOException("IMG 帧号越界：" + requested
                + "（共 " + frames.size() + " 帧）" + entry.name());
        int index = requested; Set<Integer> seen = new HashSet<>(); Frame frame = frames.get(index);
        while (frame.type() == 17) {
            if (!seen.add(index)) throw new IOException("IMG 链接帧循环：" + entry.name());
            index = frame.length(); frame = frames.get(index);
        }
        return index;
    }

    private static byte[] inflate(byte[] input, int length) throws IOException {
        Inflater inflater = new Inflater(); byte[] output = new byte[length]; inflater.setInput(input);
        try {
            int position = 0;
            while (!inflater.finished() && position < length) {
                NpkResources.checkInterrupted();
                int count = inflater.inflate(output, position, length - position);
                if (count == 0) break;
                position += count;
            }
            // Also consume the zlib trailer when the output buffer ended exactly at the last pixel.
            byte[] extra = new byte[1];
            int overflow = inflater.finished() ? 0 : inflater.inflate(extra);
            if (position != length || overflow != 0 || !inflater.finished() || inflater.getRemaining() != 0)
                throw new IOException("IMG 解压长度不匹配或压缩流损坏");
            return output;
        } catch (DataFormatException failure) { throw new IOException("IMG zlib 数据损坏", failure); }
        finally { inflater.end(); }
    }

    private static int expand5(int value) { return value << 3 | value >> 2; }
    private static void requireRange(NpkResources.Entry entry, long offset, long length) throws IOException {
        if (offset < 32 || length < 0 || offset > entry.length() - length) throw new IOException("IMG 数据超出条目范围");
    }
    private record Frame(int type, int mode, int width, int height, int x, int y,
                         int canvasWidth, int canvasHeight, long offset, int length) {}
}
