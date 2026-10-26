package studio.pvf.workspace;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BinaryScriptCodecTest {
    private static final Charset BIG5 = Charset.forName("Big5");
    private static byte[] table() {
        List<String> strings = List.of("[name]", "名稱", "[weight]", "[name]", "a`b\\c\r\n\t");
        int size = 4 + (strings.size() + 1) * 4 + strings.stream().mapToInt(s -> s.getBytes(BIG5).length).sum();
        ByteBuffer b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN); b.putInt(strings.size());
        int offset = (strings.size() + 1) * 4;
        for (String s : strings) { b.putInt(offset); offset += s.getBytes(BIG5).length; } b.putInt(offset);
        for (String s : strings) b.put(s.getBytes(BIG5));
        return b.array();
    }
    private static byte[] script() {
        return ByteBuffer.allocate(2 + 7 * 5).order(ByteOrder.LITTLE_ENDIAN).put((byte) 0xb0).put((byte) 0xd0)
                .put((byte) 5).putInt(3).put((byte) 7).putInt(1).put((byte) 5).putInt(2)
                .put((byte) 2).putInt(400).put((byte) 4).putInt(0x80000000)
                .put((byte) 9).putInt(12).put((byte) 7).putInt(4).array();
    }
    @Test void exactRoundTripPreservesDuplicateStringIndicesUnknownTokensAndFloatBits() throws Exception {
        byte[] script = script(), table = table();
        var strings = new BinaryScriptCodec.StringTable(table, BIG5);
        String text = BinaryScriptCodec.decode(script, strings);
        assertTrue(text.contains("名稱")); assertTrue(text.contains("@9(12)")); assertTrue(text.contains("-0.0f"));
        var output = BinaryScriptCodec.encode(text + "\n// ignored comment\n", script, strings);
        assertArrayEquals(script, output.script()); assertArrayEquals(table, output.stringTable());
    }
    @Test void numericEditPreservesOtherWordsAndNewTextAppendsWithoutChangingExistingStrings() throws Exception {
        var strings = new BinaryScriptCodec.StringTable(table(), BIG5);
        String text = BinaryScriptCodec.decode(script(), strings).replace("400\t", "401\t").replace("`名稱`", "`新名稱`");
        var compiled = BinaryScriptCodec.encode(text, script(), strings);
        var changed = new BinaryScriptCodec.StringTable(compiled.stringTable(), BIG5);
        assertEquals(text, BinaryScriptCodec.decode(compiled.script(), changed));
        for (int i = 0; i < 5; i++) assertEquals(strings.get(i), changed.get(i));
        assertEquals(3, ByteBuffer.wrap(compiled.script()).order(ByteOrder.LITTLE_ENDIAN).getInt(3));
    }
    @Test void rejectsMalformedTokensStringTablesAndUnencodableAdditions() throws Exception {
        var strings = new BinaryScriptCodec.StringTable(table(), BIG5);
        assertThrows(IOException.class, () -> BinaryScriptCodec.encode("[bad", script(), strings));
        assertThrows(IOException.class, () -> BinaryScriptCodec.encode("@2(12x", script(), strings));
        assertThrows(IOException.class, () -> BinaryScriptCodec.encode("`😀`", script(), strings));
        assertThrows(IOException.class, () -> BinaryScriptCodec.decode(new byte[]{(byte) 0xb0, (byte) 0xd0, 1}, strings));
        byte[] bad = table(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(4, Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> new BinaryScriptCodec.StringTable(bad, BIG5));
    }
}
