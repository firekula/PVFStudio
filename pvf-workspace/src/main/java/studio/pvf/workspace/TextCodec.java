package studio.pvf.workspace;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;

/** No replacement characters, newline conversion or BOM removal. */
public final class TextCodec {
    private TextCodec() {}

    public static String decode(byte[] bytes, Charset charset) throws CharacterCodingException {
        String text = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        if (!Arrays.equals(bytes, encode(text, charset))) {
            throw new CharacterCodingException();
        }
        return text;
    }

    public static byte[] encode(String text, Charset charset) throws CharacterCodingException {
        ByteBuffer encoded = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        String roundTrip = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        if (!text.equals(roundTrip)) throw new CharacterCodingException();
        return bytes;
    }
}
