package studio.pvf.sceneedit;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SourceDocumentTest {
    @Test void quotedAndCommentedTagsAreNotSections() {
        String text = "#PVF_File\r\n// [monster] fake\r\n/* [NPC] fake */\r\n"
                + "[passive object] 100 1 2 3 [/passive object]\r\n"
                + "[animation] `Animation/[fake].ani` `[normal]` 10 20 0 [/animation]\r\n";
        var source = SourceDocument.parse(text);
        assertTrue(source.diagnostics().isEmpty());
        assertEquals(List.of("passive object", "/passive object", "animation", "/animation"),
                source.sections().stream().map(SourceDocument.Section::tag).toList());
        assertEquals("Animation/[fake].ani", source.sections("animation").get(0).values().get(0).value());
        assertEquals(text, source.text());
    }

    @Test void keepsSeparateFlatRecordsAndOpaqueTokens() {
        String text = "[passive object] 100 1 2 3 100 4 5 6 @9(6) @10(123) [/passive object]";
        var source = SourceDocument.parse(text);
        var values = source.sections("passive object").get(0).values();
        assertEquals(10, values.size());
        assertEquals("100", values.get(4).raw());
        assertEquals("@10(123)", values.get(9).raw());
        assertEquals(text.indexOf("100 4"), values.get(4).range().start());
        assertEquals("100 1 2 3", text.substring(values.get(0).range().start(), values.get(3).range().end()));
    }

    @Test void preservesCrLfAndKoreanTagSpelling() throws Exception {
        String text = "// keep this\r\n[몬스터]\r\n51 1 0 100 200 0 1 1 `[fixed]` `[normal]`\r\n[/몬스터]\r\n[unknown] @10(42)\r\n";
        var source = SourceDocument.parse(text);
        var section = source.sections("monster").get(0);
        assertEquals("[몬스터]", section.heading().raw());
        var x = section.values().get(3);
        var changed = source.replace("移动怪物", x.range(), "125").applyTo(text);
        assertEquals(text.replace("100 200", "125 200"), changed);
        assertEquals("51", section.values().get(0).value());
    }

    @Test void escapedQuotesAndProjectedWhitespaceRetainOriginalLexemes() {
        String text = "[name] `a\\`b` [greed] `BBEE\\r\\nAABB` [path] \"Tile\\floor.til\"";
        var source = SourceDocument.parse(text);
        assertTrue(source.diagnostics().isEmpty());
        assertEquals("`a\\`b`", source.sections("name").get(0).values().get(0).raw());
        assertEquals("BBEE\\r\\nAABB", source.sections("greed").get(0).values().get(0).value());
        assertEquals("Tile\\floor.til", source.sections("path").get(0).values().get(0).value());
    }

    @Test void unterminatedStringsAndCommentsIdentifyUnsafeRanges() {
        var string = SourceDocument.parse("[animation] `broken");
        assertFalse(string.diagnostics().isEmpty());
        assertEquals(12, string.diagnostics().get(0).range().start());
        assertEquals(19, string.diagnostics().get(0).range().end());
        var comment = SourceDocument.parse("[tile] /* unclosed");
        assertFalse(comment.diagnostics().isEmpty());
        assertEquals(7, comment.diagnostics().get(0).range().start());
    }

    @Test void unsafeInsertionCannotExtendUnterminatedComment() {
        String text = "[tile] /* unfinished";
        var source = SourceDocument.parse(text);
        assertThrows(java.io.IOException.class, () -> source.replace("添加地块",
                new SourceDocument.Range(text.length(), text.length()), "`Tile/new.til`"));
    }
}
