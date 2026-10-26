package studio.pvf.workspace;

import org.junit.jupiter.api.Test;
import studio.pvf.core.PvfFormat;
import java.nio.charset.Charset;
import static org.junit.jupiter.api.Assertions.*;

class PvfTextEncodingTest {
    @Test void defaultsFollowContainerAndOnlyConclusiveLegacyMojibakeIsRepaired() {
        Charset gbk = Charset.forName("GBK"), korean = Charset.forName("MS949");
        assertEquals(korean, PvfTextEncoding.defaultCharset(PvfFormat.LEGACY));
        assertEquals(gbk, PvfTextEncoding.defaultCharset(PvfFormat.ACT1));
        assertEquals(Charset.forName("Big5"), PvfTextEncoding.defaultCharset(PvfFormat.UUID));
        byte[] bytes = "[타일] `Tile/bar00.til`".getBytes(korean);
        assertEquals(korean, PvfTextEncoding.restoredCharset(PvfFormat.LEGACY, bytes, gbk));
        assertEquals(gbk, PvfTextEncoding.restoredCharset(PvfFormat.ACT1, bytes, gbk));
        assertEquals(gbk, PvfTextEncoding.restoredCharset(PvfFormat.LEGACY, "[tile] `a.til`".getBytes(gbk), gbk));
        assertEquals(Charset.forName("UTF-8"), PvfTextEncoding.restoredCharset(PvfFormat.LEGACY, bytes, Charset.forName("UTF-8")));
    }
}
