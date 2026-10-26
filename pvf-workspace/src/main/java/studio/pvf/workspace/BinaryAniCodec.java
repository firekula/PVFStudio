package studio.pvf.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;

/** Structured ANI wire codec. The original image table, command order and float bits survive a no-op edit. */
public final class BinaryAniCodec {
    private BinaryAniCodec() {}
    private enum Kind { U8, U16, I16, I32, FLOAT, TEXT, EFFECT, DAMAGE, FLIP }
    private record Spec(int code, String tag, List<Kind> fields) {}
    private static Spec spec(int code, String tag, Kind... fields) { return new Spec(code, tag, List.of(fields)); }
    private static final List<Spec> SPECS = List.of(
            spec(0, "LOOP", Kind.U8), spec(1, "SHADOW", Kind.U8), spec(3, "COORD", Kind.U16),
            spec(7, "IMAGE RATE", Kind.FLOAT, Kind.FLOAT), spec(8, "IMAGE ROTATE", Kind.FLOAT),
            spec(9, "RGBA", Kind.U8, Kind.U8, Kind.U8, Kind.U8), spec(10, "INTERPOLATION", Kind.U8),
            spec(11, "GRAPHIC EFFECT", Kind.EFFECT), spec(12, "DELAY", Kind.I32), spec(13, "DAMAGE TYPE", Kind.DAMAGE),
            spec(14, "DAMAGE BOX", Kind.I32, Kind.I32, Kind.I32, Kind.I32, Kind.I32, Kind.I32),
            spec(15, "ATTACK BOX", Kind.I32, Kind.I32, Kind.I32, Kind.I32, Kind.I32, Kind.I32),
            spec(16, "PLAY SOUND", Kind.TEXT), spec(17, "PRELOAD"),
            spec(18, "SPECTRUM", Kind.U8, Kind.I32, Kind.I32, Kind.U8, Kind.U8, Kind.U8, Kind.U8, Kind.EFFECT),
            spec(23, "SET FLAG", Kind.I32), spec(24, "FLIP TYPE", Kind.FLIP), spec(25, "LOOP START"),
            spec(26, "LOOP END", Kind.I32), spec(27, "CLIP", Kind.I16, Kind.I16, Kind.I16, Kind.I16), spec(28, "OPERATION", Kind.U16));
    private static final Set<Integer> GLOBAL = Set.of(0, 1, 3, 18, 28);
    private static final Set<Integer> BOX = Set.of(14, 15);
    private static final Map<Kind, List<String>> ENUMS = Map.of(
            Kind.EFFECT, List.of("NONE", "DODGE", "LINEARDODGE", "DARK", "XOR", "MONOCHROME", "SPACEDISTORT"),
            Kind.DAMAGE, List.of("NORMAL", "SUPERARMOR", "UNBREAKABLE"),
            Kind.FLIP, List.of("NONE", "HORIZON", "VERTICAL", "ALL"));
    private static final Pattern FRAME = Pattern.compile("FRAME[0-9]+", Pattern.CASE_INSENSITIVE);

    public static final class FormatException extends IOException {
        public FormatException(String message) { super(message); }
        FormatException(String message, Throwable cause) { super(message, cause); }
    }
    public static boolean isBinary(String path, byte[] bytes) {
        if (!path.toLowerCase(Locale.ROOT).endsWith(".ani") || BinaryScriptCodec.isScript(bytes)) return false;
        for (int i = 0; i < Math.min(bytes.length, 16); i++) if (bytes[i] == 0) return true;
        return false;
    }
    private static Spec byCode(int code) throws FormatException {
        for (Spec s : SPECS) if (s.code == code) return s;
        throw new FormatException("尚未支持的 ANI 指令：" + code);
    }
    private static Spec byTag(String tag) throws FormatException {
        return SPECS.stream().filter(s -> s.tag.equalsIgnoreCase(tag)).findFirst().orElseThrow(() -> new FormatException("尚未支持的 ANI 标签：[" + tag + "]"));
    }
    private record Command(Spec spec, List<String> values) {}
    private record Image(String path, byte[] raw) {}
    private record FrameData(List<Command> boxes, int image, int sprite, int x, int y, List<Command> commands) {}
    private record Document(List<Image> images, List<Command> global, List<FrameData> frames, byte[] tail) {}

    private static final class Input {
        final ByteBuffer b; final Charset charset;
        Input(byte[] bytes, Charset charset) { b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); this.charset = charset; }
        void need(int n) throws FormatException { if (n < 0 || n > b.remaining()) throw new FormatException("ANI 数据被截断，偏移 " + b.position()); }
        int u8() throws FormatException { need(1); return Byte.toUnsignedInt(b.get()); }
        int u16() throws FormatException { need(2); return Short.toUnsignedInt(b.getShort()); }
        int i16() throws FormatException { need(2); return b.getShort(); }
        int i32() throws FormatException { need(4); return b.getInt(); }
        byte[] bytes() throws FormatException {
            int n = i32(); if (n < 0 || n > 1024 * 1024) throw new FormatException("ANI 字符串长度无效：" + n);
            need(n); byte[] bytes = new byte[n]; b.get(bytes); return bytes;
        }
        String text(byte[] bytes) throws FormatException {
            try { return TextCodec.decode(bytes, charset); }
            catch (IOException invalid) { throw new FormatException("ANI 字符串无法使用 " + charset.name() + " 无损解码", invalid); }
        }
        String read(Kind kind) throws FormatException {
            if (kind == Kind.TEXT) return BinaryScriptCodec.quote(text(bytes()));
            if (ENUMS.containsKey(kind)) {
                int n = u16(); var names = ENUMS.get(kind);
                if (n >= names.size()) throw new FormatException("尚未支持的 ANI " + kind + " 枚举：" + n);
                return BinaryScriptCodec.quote(names.get(n));
            }
            if (kind == Kind.FLOAT) {
                float f = Float.intBitsToFloat(i32());
                if (!Float.isFinite(f)) throw new FormatException("ANI 浮点数不是有限值");
                return Float.toString(f);
            }
            return Integer.toString(switch (kind) { case U8 -> u8(); case U16 -> u16(); case I16 -> i16(); default -> i32(); });
        }
    }
    private static Document parse(byte[] bytes, Charset charset) throws FormatException {
        Input in = new Input(bytes, charset); int frameCount = in.u16(), imageCount = in.u16();
        if (imageCount > (in.b.remaining() - 2) / 4) throw new FormatException("ANI 图片表长度无效");
        List<Image> images = new ArrayList<>();
        for (int i = 0; i < imageCount; i++) { byte[] raw = in.bytes(); images.add(new Image(in.text(raw), raw)); }
        List<Command> global = commands(in, in.u16(), 0);
        if (frameCount > in.b.remaining() / 14) throw new FormatException("ANI 帧数超出文件容量：" + frameCount);
        List<FrameData> frames = new ArrayList<>();
        for (int i = 0; i < frameCount; i++) {
            List<Command> boxes = commands(in, in.u16(), 1);
            int image = in.i16(), sprite = image < 0 ? 0 : in.u16();
            if (image >= images.size()) throw new FormatException("ANI 第 " + i + " 帧图片编号越界：" + image);
            int x = in.i32(), y = in.i32(); List<Command> commands = commands(in, in.u16(), 2);
            frames.add(new FrameData(boxes, image, sprite, x, y, commands));
        }
        if (in.b.remaining() > 3) throw new FormatException("ANI 末尾存在尚未识别的 " + in.b.remaining() + " 字节，偏移 " + in.b.position());
        byte[] tail = new byte[in.b.remaining()]; in.b.get(tail);
        return new Document(List.copyOf(images), List.copyOf(global), List.copyOf(frames), tail);
    }
    private static List<Command> commands(Input in, int count, int scope) throws FormatException {
        if (count > in.b.remaining() / 2) throw new FormatException("ANI 指令数量超出文件容量");
        List<Command> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int offset = in.b.position(), code = in.u16();
            if (scope == 0 && !GLOBAL.contains(code) || scope == 1 && !BOX.contains(code)
                    || scope == 2 && (BOX.contains(code) || code == 18 || code == 28))
                throw new FormatException("尚未支持的 ANI 指令 " + code + "，区域 " + scope + "，偏移 " + offset);
            Spec spec = byCode(code); List<String> values = new ArrayList<>();
            for (Kind kind : spec.fields) values.add(in.read(kind));
            if (code == 11) for (Kind kind : effectExtras(values.get(0))) values.add(in.read(kind));
            result.add(new Command(spec, List.copyOf(values)));
        }
        return result;
    }
    private static List<Kind> effectExtras(String effect) {
        return effect.equals("`MONOCHROME`") ? List.of(Kind.U8, Kind.U8, Kind.U8)
                : effect.equals("`SPACEDISTORT`") ? List.of(Kind.I16, Kind.I16) : List.of();
    }
    public static String decode(byte[] bytes, Charset charset) throws FormatException {
        return render(parse(bytes, charset));
    }
    private static String render(Document doc) {
        StringBuilder text = new StringBuilder("#PVF_File\n");
        doc.global.forEach(c -> render(text, c));
        text.append("[FRAME MAX]\n\t").append(doc.frames.size()).append('\n');
        for (int i = 0; i < doc.frames.size(); i++) {
            FrameData frame = doc.frames.get(i);
            text.append(String.format(Locale.ROOT, "\n[FRAME%03d]\n", i));
            frame.boxes.forEach(c -> render(text, c));
            text.append("[IMAGE]\n\t").append(BinaryScriptCodec.quote(frame.image < 0 ? "" : doc.images.get(frame.image).path))
                    .append('\t').append(frame.sprite).append("\n[IMAGE POS]\n\t").append(frame.x).append('\t').append(frame.y).append('\n');
            frame.commands.forEach(c -> render(text, c));
        }
        if (doc.tail.length > 0) text.append("\n// ANI 尾部保留 ").append(doc.tail.length).append(" 字节填充\n");
        return text.toString();
    }
    private static void render(StringBuilder text, Command command) {
        var v = command.values;
        text.append('[').append(command.spec.tag).append("]\n");
        if (command.spec.code == 18) {
            text.append('\t').append(v.get(0)).append("\n[SPECTRUM TERM]\n\t").append(v.get(1))
                    .append("\n[SPECTRUM LIFE TIME]\n\t").append(v.get(2)).append("\n[SPECTRUM COLOR]\n\t")
                    .append(String.join("\t", v.subList(3, 7))).append("\n[SPECTRUM EFFECT]\n\t").append(v.get(7)).append('\n');
        } else if (command.spec.code == 17) text.append("\t1\n");
        else if (!v.isEmpty()) text.append('\t').append(String.join("\t", v)).append('\n');
    }

    private record Section(String tag, List<String> values) {}
    private static final class TextInput {
        final List<Section> sections = new ArrayList<>(); int index;
        TextInput(String text) throws IOException {
            String tag = null; List<String> values = new ArrayList<>();
            for (String token : BinaryScriptCodec.lex(text)) {
                if (token.startsWith("[")) {
                    if (tag != null) sections.add(new Section(tag, List.copyOf(values)));
                    tag = token.substring(1, token.length() - 1).trim().toUpperCase(Locale.ROOT); values.clear();
                } else { if (tag == null) throw new FormatException("ANI 标签前存在无法识别的内容"); values.add(token); }
            }
            if (tag != null) sections.add(new Section(tag, List.copyOf(values)));
        }
        boolean end() { return index == sections.size(); }
        Section next() throws FormatException { if (end()) throw new FormatException("ANI 文本提前结束"); return sections.get(index++); }
        Section expect(String name, int count) throws FormatException {
            Section s = next(); if (!s.tag.equals(name) || s.values.size() != count) throw new FormatException("ANI 需要 [" + name + "] 和 " + count + " 个参数"); return s;
        }
    }
    private static Command command(TextInput input, Section section) throws IOException {
        Spec spec = byTag(section.tag); List<String> values = new ArrayList<>(section.values);
        if (spec.code == 18) {
            if (values.size() != 1) throw new FormatException("[SPECTRUM] 需要一个参数");
            values.addAll(input.expect("SPECTRUM TERM", 1).values); values.addAll(input.expect("SPECTRUM LIFE TIME", 1).values);
            values.addAll(input.expect("SPECTRUM COLOR", 4).values); values.addAll(input.expect("SPECTRUM EFFECT", 1).values);
        } else if (spec.code == 17) {
            if (values.size() != 1 || integer(values.get(0), Kind.U8) != 1) throw new FormatException("[PRELOAD] 需要参数 1"); values.clear();
        }
        int count = spec.fields.size() + (spec.code == 11 && !values.isEmpty() ? effectExtras(values.get(0).toUpperCase(Locale.ROOT)).size() : 0);
        if (values.size() != count) throw new FormatException("[" + spec.tag + "] 需要 " + count + " 个参数");
        return new Command(spec, List.copyOf(values));
    }
    public static byte[] encode(String text, byte[] original, Charset charset) throws IOException {
        Document source = parse(original, charset); TextInput in = new TextInput(text);
        List<Command> global = new ArrayList<>();
        while (!in.end() && !in.sections.get(in.index).tag.equals("FRAME MAX")) {
            Command c = command(in, in.next()); if (!GLOBAL.contains(c.spec.code)) throw new FormatException("不支持的 ANI 全局标签：[" + c.spec.tag + "]"); global.add(c);
        }
        int count = integer(in.expect("FRAME MAX", 1).values.get(0), Kind.U16);
        List<Image> images = new ArrayList<>(source.images); List<FrameData> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Section header = in.next();
            if (!FRAME.matcher(header.tag).matches() || Integer.parseInt(header.tag.substring(5)) != i || !header.values.isEmpty())
                throw new FormatException("ANI 帧编号须从 FRAME000 按顺序排列");
            List<Command> boxes = new ArrayList<>(), properties = new ArrayList<>();
            Integer image = null, sprite = null, x = null, y = null;
            while (!in.end() && !FRAME.matcher(in.sections.get(in.index).tag).matches()) {
                Section section = in.next(); var values = section.values;
                if (section.tag.equals("IMAGE")) {
                    if (image != null || values.size() != 2) throw new FormatException("每帧需要唯一 [IMAGE] 和路径/帧号两个参数");
                    String path = unquote(values.get(0)); sprite = integer(values.get(1), Kind.U16);
                    if (i < source.frames.size() && source.frames.get(i).image >= 0
                            && source.images.get(source.frames.get(i).image).path.equals(path)) image = source.frames.get(i).image;
                    else if (path.isEmpty()) {
                        if (sprite != 0) throw new FormatException("空图片的帧号须为 0");
                        image = i < source.frames.size() && source.frames.get(i).image < 0 ? source.frames.get(i).image : -1;
                    } else {
                        image = -1;
                        for (int j = 0; j < images.size(); j++) if (images.get(j).path.equals(path)) { image = j; break; }
                        if (image < 0) { image = images.size(); images.add(new Image(path, TextCodec.encode(path, charset))); }
                        if (image > Short.MAX_VALUE) throw new FormatException("ANI 图片编号超出 int16 范围");
                    }
                } else if (section.tag.equals("IMAGE POS")) {
                    if (x != null || values.size() != 2) throw new FormatException("每帧需要唯一 [IMAGE POS] 和 x/y 两个参数");
                    x = integer(values.get(0), Kind.I32); y = integer(values.get(1), Kind.I32);
                } else {
                    Command c = command(in, section);
                    if (BOX.contains(c.spec.code)) boxes.add(c);
                    else if (c.spec.code == 18 || c.spec.code == 28) throw new FormatException("不支持的逐帧 ANI 标签：[" + c.spec.tag + "]");
                    else properties.add(c);
                }
            }
            if (image == null || x == null) throw new FormatException("ANI 第 " + i + " 帧缺少 IMAGE / IMAGE POS");
            frames.add(new FrameData(boxes, image, sprite, x, y, properties));
        }
        if (!in.end()) throw new FormatException("ANI 文本帧数与 FRAME MAX 不符");
        Output out = new Output(charset); out.u16(frames.size()); out.u16(images.size());
        for (Image image : images) out.bytes(image.raw);
        out.commands(global);
        for (FrameData frame : frames) {
            out.commands(frame.boxes); out.i16(frame.image);
            if (frame.image >= 0) out.u16(frame.sprite);
            out.i32(frame.x); out.i32(frame.y); out.commands(frame.commands);
        }
        out.bytes.writeBytes(source.tail);
        byte[] encoded = out.bytes.toByteArray(); parse(encoded, charset); return encoded;
    }
    private static String unquote(String value) throws IOException {
        if (value.length() < 2 || !value.startsWith("`") || !value.endsWith("`")) throw new FormatException("ANI 字符串需要反引号");
        return BinaryScriptCodec.unquote(value);
    }
    private static int integer(String text, Kind kind) throws FormatException {
        try {
            int value = Integer.parseInt(text); long min = switch (kind) { case U8, U16 -> 0; case I16 -> Short.MIN_VALUE; default -> Integer.MIN_VALUE; };
            long max = switch (kind) { case U8 -> 255; case U16 -> 65535; case I16 -> Short.MAX_VALUE; default -> Integer.MAX_VALUE; };
            if (value < min || value > max) throw new NumberFormatException(); return value;
        } catch (NumberFormatException invalid) { throw new FormatException("ANI 数值超出 " + kind + " 范围：" + text); }
    }
    private static final class Output {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(); final Charset charset;
        Output(Charset charset) { this.charset = charset; }
        void u16(int n) throws FormatException { if (n < 0 || n > 65535) throw new FormatException("ANI 数量超出 uint16 范围"); bytes.write(n); bytes.write(n >>> 8); }
        void i16(int n) { bytes.write(n); bytes.write(n >>> 8); }
        void i32(int n) { for (int i = 0; i < 4; i++) bytes.write(n >>> (i * 8)); }
        void bytes(byte[] b) { i32(b.length); bytes.writeBytes(b); }
        void write(Kind kind, String value) throws IOException {
            if (kind == Kind.TEXT) { bytes(TextCodec.encode(unquote(value), charset)); return; }
            if (ENUMS.containsKey(kind)) {
                int index = ENUMS.get(kind).indexOf(unquote(value).toUpperCase(Locale.ROOT));
                if (index < 0) throw new FormatException("未知 ANI " + kind + "：" + value); u16(index); return;
            }
            if (kind == Kind.FLOAT) {
                try { float f = Float.parseFloat(value); if (!Float.isFinite(f)) throw new NumberFormatException(); i32(Float.floatToRawIntBits(f)); }
                catch (NumberFormatException invalid) { throw new FormatException("ANI 需要有限浮点数：" + value); } return;
            }
            int n = integer(value, kind);
            if (kind == Kind.U8) bytes.write(n); else if (kind == Kind.U16 || kind == Kind.I16) i16(n); else i32(n);
        }
        void commands(List<Command> commands) throws IOException {
            u16(commands.size());
            for (Command c : commands) {
                u16(c.spec.code); List<Kind> fields = new ArrayList<>(c.spec.fields);
                if (c.spec.code == 11) fields.addAll(effectExtras(c.values.get(0).toUpperCase(Locale.ROOT)));
                for (int i = 0; i < fields.size(); i++) write(fields.get(i), c.values.get(i));
            }
        }
    }
}
