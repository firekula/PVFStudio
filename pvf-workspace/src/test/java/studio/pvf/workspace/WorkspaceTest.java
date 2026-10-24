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

    @Test void firstBackupExportPreservesAnIndependentOriginalAndVerifiedOutput() throws Exception {
        Path source = fixture("original".getBytes(StandardCharsets.UTF_8));
        byte[] original = Files.readAllBytes(source);
        Path target = temporary.resolve("中文 导出.pvf");
        try (PvfWorkspace workspace = PvfWorkspace.open(source, temporary.resolve("drafts"))) {
            workspace.stageText("test/item.stk", "first version", StandardCharsets.UTF_8);
            PvfBackupExport.Result result = PvfBackupExport.export(workspace, target);
            assertEquals(target, result.output());
            assertEquals(temporary.resolve("backups"), result.backup().getParent());
            assertArrayEquals(original, Files.readAllBytes(result.backup()));
            assertFalse(Files.isSameFile(source, result.backup()));
            try (var exported = studio.pvf.core.PvfArchive.open(target)) {
                assertEquals("first version", TextCodec.decode(exported.readBytes("test/item.stk"), StandardCharsets.UTF_8));
            }
            Files.write(target, new byte[]{0});
            assertArrayEquals(original, Files.readAllBytes(result.backup()));
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test void updatingAnExportKeepsEveryPriorVersionRecoverable() throws Exception {
        Path source = fixture("original".getBytes(StandardCharsets.UTF_8));
        byte[] original = Files.readAllBytes(source);
        Path target = temporary.resolve("export.pvf");
        try (PvfWorkspace workspace = PvfWorkspace.open(source, temporary.resolve("drafts"))) {
            workspace.stageText("test/item.stk", "version one", StandardCharsets.UTF_8);
            PvfBackupExport.Result first = PvfBackupExport.export(workspace, target);
            byte[] versionOne = Files.readAllBytes(target);
            workspace.stageText("test/item.stk", "version two", StandardCharsets.UTF_8);
            PvfBackupExport.Result second = PvfBackupExport.export(workspace, target);
            assertNotEquals(first.backup(), second.backup());
            assertArrayEquals(original, Files.readAllBytes(first.backup()));
            assertArrayEquals(versionOne, Files.readAllBytes(second.backup()));
            assertFalse(Files.isSameFile(target, second.backup()));
            try (var backup = studio.pvf.core.PvfArchive.open(second.backup());
                 var exported = studio.pvf.core.PvfArchive.open(target)) {
                assertEquals("version one", TextCodec.decode(backup.readBytes("test/item.stk"), StandardCharsets.UTF_8));
                assertEquals("version two", TextCodec.decode(exported.readBytes("test/item.stk"), StandardCharsets.UTF_8));
            }
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test void backupFailureNeverChangesAnExistingOrNewDestination() throws Exception {
        Path source = fixture(new byte[]{1, 2, 3});
        Path target = temporary.resolve("export.pvf");
        byte[] old = new byte[]{9, 8, 7};
        Files.write(target, old);
        Files.writeString(temporary.resolve("backups"), "not a directory");
        try (PvfWorkspace workspace = PvfWorkspace.open(source, temporary.resolve("drafts"))) {
            assertThrows(IOException.class, () -> PvfBackupExport.export(workspace, target));
            assertArrayEquals(old, Files.readAllBytes(target));
            Path newTarget = temporary.resolve("new.pvf");
            assertThrows(IOException.class, () -> PvfBackupExport.export(workspace, newTarget));
            assertFalse(Files.exists(newTarget));
        }
    }

    @Test void backupExportStillRejectsTheSourceAndItsHardLinkAlias() throws Exception {
        Path source = fixture(new byte[]{1, 2, 3});
        byte[] original = Files.readAllBytes(source);
        Path alias = temporary.resolve("source-alias.pvf");
        Files.createLink(alias, source);
        try (PvfWorkspace workspace = PvfWorkspace.open(source, temporary.resolve("drafts"))) {
            assertThrows(IOException.class, () -> PvfBackupExport.export(workspace, source));
            assertThrows(IOException.class, () -> PvfBackupExport.export(workspace, alias));
            assertFalse(Files.exists(temporary.resolve("backups")));
        }
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test void failedExportRetainsTheDestinationAndCompletedBackup() throws Exception {
        Path source = fixture(new byte[]{1, 2, 3});
        Path target = temporary.resolve("export.pvf");
        byte[] old = new byte[]{9, 8, 7};
        Files.write(target, old);
        Path drafts = temporary.resolve("drafts");
        try (PvfWorkspace workspace = PvfWorkspace.open(source, drafts)) {
            workspace.stageText("test/item.stk", "changed", StandardCharsets.UTF_8);
            Path blob;
            try (var files = Files.list(drafts)) {
                blob = files.filter(path -> path.toString().endsWith(".bin")).findFirst().orElseThrow();
            }
            Files.write(blob, new byte[]{0});
            assertThrows(IOException.class, () -> PvfBackupExport.export(workspace, target));
            assertArrayEquals(old, Files.readAllBytes(target));
            try (var backups = Files.list(temporary.resolve("backups"))) {
                var saved = backups.toList();
                assertEquals(1, saved.size());
                assertArrayEquals(old, Files.readAllBytes(saved.get(0)));
            }
            try (var files = Files.list(temporary)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".pvf-version-export-")));
            }
        }
    }

    private Path fixture(byte[] content) throws IOException {
        byte[] path = "test/item.stk".getBytes(StandardCharsets.US_ASCII);
        int treeSize = (20 + path.length + 3) & ~3;
        ByteBuffer tree = ByteBuffer.allocate(treeSize).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer body = ByteBuffer.allocate((content.length + 3) & ~3).order(ByteOrder.LITTLE_ENDIAN);
        body.put(content);
        int entryKey = crc32(body.array(), 0);
        tree.putInt(11).putInt(0).putInt(content.length).putInt(entryKey).putInt(path.length).put(path);
        int treeKey = crc32(tree.array(), 1);
        encrypt(tree.array(), treeKey);
        encrypt(body.array(), entryKey);
        ByteBuffer archive = ByteBuffer.allocate(27 + treeSize + body.capacity()).order(ByteOrder.LITTLE_ENDIAN);
        archive.put("DNF_SCRIPT_PACK".getBytes(StandardCharsets.US_ASCII));
        archive.putInt(treeSize).putInt(treeKey).putInt(1).put(tree.array()).put(body.array());
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

    private static int crc32(byte[] bytes, int seed) {
        int state = ~seed;
        for (byte value : bytes) {
            state ^= value & 0xff;
            for (int bit = 0; bit < 8; bit++) state = (state >>> 1) ^ ((state & 1) != 0 ? 0xedb88320 : 0);
        }
        return ~state;
    }
}
