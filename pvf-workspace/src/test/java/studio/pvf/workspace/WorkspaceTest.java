package studio.pvf.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {
    @TempDir Path temporary;

    @Test void persistedDraftSurvivesRestartAndCanBeExported() throws Exception {
        Path source = fixture("[name]\r\n`原始`\r\n".getBytes(Charset.forName("GBK")));
        byte[] originalArchive = Files.readAllBytes(source);
        Path drafts = temporary.resolve("drafts");
        String changed = "[name]\r\n`修改后的中文`\r\n";
        try (PvfWorkspace workspace = PvfWorkspace.open(source, drafts)) {
            workspace.stageText("TEST\\ITEM.STK", changed, Charset.forName("GBK"));
            assertEquals(Set.of("test/item.stk"), workspace.dirtyPaths());
        }
        try (PvfWorkspace workspace = PvfWorkspace.open(source, drafts)) {
            assertEquals(changed, workspace.readText("test/item.stk", Charset.forName("GBK")));
            Path output = temporary.resolve("export.pvf");
            workspace.export(output);
            try (var archive = studio.pvf.core.PvfArchive.open(output)) {
                assertEquals(changed, TextCodec.decode(archive.readBytes("test/item.stk"), Charset.forName("GBK")));
            }
            workspace.revert("test/item.stk");
            assertTrue(workspace.dirtyPaths().isEmpty());
        }
        assertArrayEquals(originalArchive, Files.readAllBytes(source));
        try (PvfWorkspace workspace = PvfWorkspace.open(source, drafts)) { assertTrue(workspace.dirtyPaths().isEmpty()); }
    }

    @Test void rejectsUnencodableTextWithoutChangingDraft() throws Exception {
        Path source = fixture("abc\r\n".getBytes(StandardCharsets.US_ASCII));
        try (PvfWorkspace workspace = PvfWorkspace.open(source, temporary.resolve("drafts"))) {
            workspace.stageText("test/item.stk", "preserved", StandardCharsets.US_ASCII);
            assertThrows(CharacterCodingException.class,
                    () -> workspace.stageText("test/item.stk", "无法写入", StandardCharsets.US_ASCII));
            assertEquals("preserved", workspace.readText("test/item.stk", StandardCharsets.US_ASCII));
        }
    }

    @Test void rejectsDifferentSourceAndConcurrentWorkspace() throws Exception {
        Path source = fixture(new byte[]{1, 2, 3});
        Path directory = temporary.resolve("drafts");
        try (PvfWorkspace ignored = PvfWorkspace.open(source, directory)) {
            assertThrows(IOException.class, () -> PvfWorkspace.open(source, directory));
        }
        Path other = fixture(new byte[]{4, 5, 6});
        assertThrows(IOException.class, () -> PvfWorkspace.open(other, directory));
    }

    @Test void detectsCorruptedDraft() throws Exception {
        Path source = fixture(new byte[]{1});
        Path directory = temporary.resolve("drafts");
        try (PvfWorkspace workspace = PvfWorkspace.open(source, directory)) {
            workspace.stageBytes("test/item.stk", new byte[]{9});
        }
        Path blob;
        try (var files = Files.list(directory)) { blob = files.filter(p -> p.toString().endsWith(".bin")).findFirst().orElseThrow(); }
        Files.write(blob, new byte[]{8});
        assertThrows(IOException.class, () -> PvfWorkspace.open(source, directory));
    }

    @Test void codecPreservesBomAndNewlinesAndRejectsBadInput() throws Exception {
        String text = "\ufeff你好\r\ntext\r\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        assertEquals(text, TextCodec.decode(bytes, StandardCharsets.UTF_8));
        assertArrayEquals(bytes, TextCodec.encode(text, StandardCharsets.UTF_8));
        assertThrows(CharacterCodingException.class, () -> TextCodec.decode(new byte[]{(byte) 0xff}, StandardCharsets.UTF_8));
        assertThrows(CharacterCodingException.class, () -> TextCodec.encode("\u00a5", Charset.forName("windows-31j")));
    }

    private Path fixture(byte[] content) throws IOException {
        byte[] path = "test/item.stk".getBytes(StandardCharsets.US_ASCII);
        int treeSize = (20 + path.length + 3) & ~3;
        ByteBuffer tree = ByteBuffer.allocate(treeSize).order(ByteOrder.LITTLE_ENDIAN);
        tree.putInt(11).putInt(0).putInt(content.length).putInt(0x19ec0041).putInt(path.length).put(path);
        ByteBuffer body = ByteBuffer.allocate((content.length + 3) & ~3).order(ByteOrder.LITTLE_ENDIAN);
        body.put(content);
        encrypt(tree.array(), 0x37fc1101);
        encrypt(body.array(), 0x19ec0041);
        ByteBuffer archive = ByteBuffer.allocate(27 + treeSize + body.capacity()).order(ByteOrder.LITTLE_ENDIAN);
        archive.put("DNF_SCRIPT_PACK".getBytes(StandardCharsets.US_ASCII));
        archive.putInt(treeSize).putInt(0x37fc1101).putInt(1).put(tree.array()).put(body.array());
        Path result = Files.createTempFile(temporary, "source-", ".pvf");
        Files.write(result, archive.array());
        return result;
    }

    private static void encrypt(byte[] bytes, int key) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset = 0; offset < bytes.length; offset += 4) {
            buffer.putInt(offset, Integer.rotateLeft(buffer.getInt(offset), 6) ^ key);
        }
    }
}
