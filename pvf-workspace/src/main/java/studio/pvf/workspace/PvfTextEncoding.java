package studio.pvf.workspace;

import studio.pvf.core.PvfFormat;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.util.regex.Pattern;

/** Container defaults and a narrow repair for legacy sessions saved with the old GBK default. */
public final class PvfTextEncoding {
    private static final Charset GBK = Charset.forName("GBK"), KOREAN = Charset.forName("MS949");
    private static final Pattern LEGACY_TAG = Pattern.compile("\\[(?:타일|통과 속성|미로 정보|필드 애니메이션|대기 모션|기본 모션|그리드)\\]");
    private PvfTextEncoding() {}
    public static Charset defaultCharset(PvfFormat format) {
        return switch (format) { case LEGACY -> KOREAN; case UUID -> Charset.forName("Big5"); case ACT1 -> GBK; };
    }
    public static Charset restoredCharset(PvfFormat format, byte[] bytes, Charset saved) {
        if (format != PvfFormat.LEGACY || !saved.equals(GBK)) return saved;
        try {
            if (LEGACY_TAG.matcher(TextCodec.decode(bytes, KOREAN)).find()) {
                try { if (!LEGACY_TAG.matcher(TextCodec.decode(bytes, saved)).find()) return KOREAN; }
                catch (CharacterCodingException invalidSaved) { return KOREAN; }
            }
        } catch (CharacterCodingException invalid) { /* Keep an explicit encoding unless the Korean tags are conclusive. */ }
        return saved;
    }
}
