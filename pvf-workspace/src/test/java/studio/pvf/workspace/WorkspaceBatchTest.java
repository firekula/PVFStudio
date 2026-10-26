package studio.pvf.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceBatchTest {
    @TempDir Path temporary;
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.US_ASCII); }
    private static PvfWorkspace.TextChange change(String path,String text,byte[] expected) {
        return new PvfWorkspace.TextChange(path,text,StandardCharsets.US_ASCII,expected);
    }

    @Test void cloneRegistrationAndBindingUndoTogetherWhileUnrelatedDraftsSurvive() throws Exception {
        Path archive = fixture(); byte[] original = Files.readAllBytes(archive); Path drafts = temporary.resolve("drafts");
        try (var workspace = PvfWorkspace.open(archive,drafts)) {
            workspace.createFile("map/map.lst",bytes("1 `a.map`")); workspace.createFile("map/a.map",bytes("original map"));
            workspace.createFile("dungeon/a.dgn",bytes("old binding"));
            var batch = workspace.applyTextBatch(Map.of("map/copy.map",bytes("original map")),List.of(
                    change("map/map.lst","1 `a.map` 2 `copy.map`",bytes("1 `a.map`")),
                    change("dungeon/a.dgn","new binding",bytes("old binding")),
                    change("map/copy.map","unsaved source map",null)));
            assertEquals("unsaved source map",workspace.readText("map/copy.map",StandardCharsets.US_ASCII));
            assertEquals("original map",workspace.readText("map/a.map",StandardCharsets.US_ASCII));
            workspace.stageText("test/base.etc","unrelated draft",StandardCharsets.US_ASCII);
            workspace.restoreBatch(batch,false);
            assertFalse(workspace.contains("map/copy.map")); assertEquals("1 `a.map`",workspace.readText("map/map.lst",StandardCharsets.US_ASCII));
            assertEquals("old binding",workspace.readText("dungeon/a.dgn",StandardCharsets.US_ASCII));
            assertEquals("unrelated draft",workspace.readText("test/base.etc",StandardCharsets.US_ASCII));
            workspace.restoreBatch(batch,true);
            assertTrue(workspace.contains("map/copy.map")); assertEquals("new binding",workspace.readText("dungeon/a.dgn",StandardCharsets.US_ASCII));
            assertEquals("unrelated draft",workspace.readText("test/base.etc",StandardCharsets.US_ASCII));
            workspace.export(temporary.resolve("export.pvf"));
        }
        try (var reopened = PvfWorkspace.open(archive,drafts)) { assertEquals("unsaved source map",reopened.readText("map/copy.map",StandardCharsets.US_ASCII)); }
        assertArrayEquals(original,Files.readAllBytes(archive));
    }

    @Test void staleExpectedBytesAndEncodingFailureLeaveNoPartialCreatedFiles() throws Exception {
        Path drafts = temporary.resolve("drafts");
        try (var workspace = PvfWorkspace.open(fixture(),drafts)) {
            var paths = workspace.paths(); var dirty = workspace.dirtyPaths(); byte[] manifest = Files.readAllBytes(drafts.resolve("workspace.properties"));
            assertThrows(IOException.class, () -> workspace.applyTextBatch(Map.of("map/copy.map",bytes("copy")),List.of(change("test/base.etc","new",bytes("stale")))));
            assertEquals(paths,workspace.paths()); assertEquals(dirty,workspace.dirtyPaths());
            assertThrows(IOException.class, () -> workspace.applyTextBatch(Map.of("map/copy.map",bytes("copy")),List.of(change("test/base.etc","😀",bytes("base")))));
            assertEquals(paths,workspace.paths()); assertEquals(dirty,workspace.dirtyPaths());
            assertArrayEquals(bytes("base"),workspace.readBytes("test/base.etc"));
            assertArrayEquals(manifest,Files.readAllBytes(drafts.resolve("workspace.properties")));
        }
    }

    @Test void manifestPublicationFailureCleansPreparedBlobsAndKeepsMemoryState() throws Exception {
        Path drafts = temporary.resolve("drafts");
        try (var workspace = PvfWorkspace.open(fixture(),drafts)) {
            var paths = workspace.paths(); var dirty = workspace.dirtyPaths();
            Files.move(drafts.resolve("workspace.properties"),drafts.resolve("manifest-backup"));
            Files.createDirectory(drafts.resolve("workspace.properties")); Files.write(drafts.resolve("workspace.properties/block"),new byte[]{1});
            assertThrows(IOException.class, () -> workspace.applyTextBatch(Map.of("map/copy.map",bytes("copy")),List.of(change("test/base.etc","new",bytes("base")))));
            assertEquals(paths,workspace.paths()); assertEquals(dirty,workspace.dirtyPaths()); assertArrayEquals(bytes("base"),workspace.readBytes("test/base.etc"));
            try (var files = Files.list(drafts)) { assertEquals(0,files.filter(path->path.toString().endsWith(".bin")).count()); }
        }
    }

    @Test void multipleBinaryScriptsCompileAgainstOneEvolvingStringTableAndUndoIt() throws Exception {
        var charset = java.nio.charset.Charset.forName("Big5"); byte[] emptyScript = {(byte)0xb0,(byte)0xd0};
        byte[] table = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(4).array();
        try (var workspace = PvfWorkspace.open(fixture(),temporary.resolve("drafts"))) {
            workspace.createFile("stringtable.bin",table); workspace.createFile("map/a.map",emptyScript);
            var batch = workspace.applyTextBatch(Map.of("map/b.map",emptyScript),List.of(
                    new PvfWorkspace.TextChange("map/a.map","[name] `甲`",charset,emptyScript),
                    new PvfWorkspace.TextChange("map/b.map","[name] `乙`",charset,null)));
            assertTrue(workspace.readText("map/a.map",charset).contains("甲")); assertTrue(workspace.readText("map/b.map",charset).contains("乙"));
            assertEquals(3,ByteBuffer.wrap(workspace.readBytes("stringtable.bin")).order(ByteOrder.LITTLE_ENDIAN).getInt());
            workspace.restoreBatch(batch,false); assertFalse(workspace.contains("map/b.map")); assertArrayEquals(table,workspace.readBytes("stringtable.bin"));
            assertArrayEquals(emptyScript,workspace.readBytes("map/a.map"));
            workspace.restoreBatch(batch,true); assertTrue(workspace.readText("map/a.map",charset).contains("甲")); assertTrue(workspace.readText("map/b.map",charset).contains("乙"));
        }
    }

    @Test void lateBinaryEncodingFailureDoesNotPublishEarlierCompiledScriptOrTable() throws Exception {
        var charset = java.nio.charset.Charset.forName("Big5"); byte[] script = {(byte)0xb0,(byte)0xd0};
        byte[] table = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(4).array();
        try (var workspace = PvfWorkspace.open(fixture(),temporary.resolve("drafts"))) {
            workspace.createFile("stringtable.bin",table); workspace.createFile("map/a.map",script); var beforePaths=workspace.paths(); var beforeDirty=workspace.dirtyPaths();
            assertThrows(IOException.class, () -> workspace.applyTextBatch(Map.of("map/b.map",script),List.of(
                    new PvfWorkspace.TextChange("map/a.map","[name] `甲`",charset,script),new PvfWorkspace.TextChange("map/b.map","[name] `😀`",charset,null))));
            assertEquals(beforePaths,workspace.paths()); assertEquals(beforeDirty,workspace.dirtyPaths());
            assertArrayEquals(table,workspace.readBytes("stringtable.bin")); assertArrayEquals(script,workspace.readBytes("map/a.map"));
        }
    }

    @Test void staleUndoAndDuplicateCanonicalDestinationsAreRejectedWithoutChangingDrafts() throws Exception {
        try (var workspace = PvfWorkspace.open(fixture(),temporary.resolve("drafts"))) {
            assertThrows(IOException.class, () -> workspace.applyTextBatch(Map.of("MAP/copy.map",bytes("a"),"map/copy.map",bytes("b")),List.of()));
            var batch = workspace.applyTextBatch(Map.of("map/copy.map",bytes("copy")),List.of(change("test/base.etc","new",bytes("base"))));
            workspace.stageText("test/base.etc","later",StandardCharsets.US_ASCII);
            assertThrows(IOException.class, () -> workspace.restoreBatch(batch,false));
            assertTrue(workspace.contains("map/copy.map")); assertEquals("later",workspace.readText("test/base.etc",StandardCharsets.US_ASCII));
        }
    }

    @Test void unrelatedBinaryStringAppendsSurviveUndoAndRedoWithoutReindexing() throws Exception {
        var charset=java.nio.charset.Charset.forName("Big5"); byte[] script={(byte)0xb0,(byte)0xd0};
        byte[] table=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(4).array();
        try(var workspace=PvfWorkspace.open(fixture(),temporary.resolve("drafts"))) {
            workspace.createFile("stringtable.bin",table); workspace.createFile("map/a.map",script);
            var batch=workspace.applyTextBatch(Map.of("map/b.map",script),List.of(
                    new PvfWorkspace.TextChange("map/a.map","[name] `甲`",charset,script),new PvfWorkspace.TextChange("map/b.map","[name] `乙`",charset,null)));
            workspace.createFile("map/unrelated.map",script); workspace.stageText("map/unrelated.map","[name] `丙`",charset);
            byte[] unrelated=workspace.readBytes("map/unrelated.map"), laterTable=workspace.readBytes("stringtable.bin");
            workspace.restoreBatch(batch,false);
            assertFalse(workspace.contains("map/b.map")); assertArrayEquals(script,workspace.readBytes("map/a.map"));
            assertArrayEquals(unrelated,workspace.readBytes("map/unrelated.map")); assertArrayEquals(laterTable,workspace.readBytes("stringtable.bin"));
            assertTrue(workspace.readText("map/unrelated.map",charset).contains("丙"));
            workspace.restoreBatch(batch,true);
            assertTrue(workspace.readText("map/a.map",charset).contains("甲")); assertTrue(workspace.readText("map/b.map",charset).contains("乙"));
            assertTrue(workspace.readText("map/unrelated.map",charset).contains("丙"));
        }
    }

    private Path fixture() throws IOException {
        byte[] path=bytes("test/base.etc"), content=bytes("base"); int treeSize=(20+path.length+3)&~3;
        ByteBuffer tree=ByteBuffer.allocate(treeSize).order(ByteOrder.LITTLE_ENDIAN), body=ByteBuffer.allocate((content.length+3)&~3).order(ByteOrder.LITTLE_ENDIAN);
        body.put(content); int bodyKey=crc(body.array(),0);
        tree.putInt(11).putInt(0).putInt(content.length).putInt(bodyKey).putInt(path.length).put(path); int treeKey=crc(tree.array(),1);
        encrypt(tree.array(),treeKey); encrypt(body.array(),bodyKey);
        ByteBuffer output=ByteBuffer.allocate(27+treeSize+body.capacity()).order(ByteOrder.LITTLE_ENDIAN);
        output.put(bytes("DNF_SCRIPT_PACK")).putInt(treeSize).putInt(treeKey).putInt(1).put(tree.array()).put(body.array());
        Path file=Files.createTempFile(temporary,"source-",".pvf"); Files.write(file,output.array()); return file;
    }
    private static void encrypt(byte[] bytes,int key) { var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); for(int i=0;i<bytes.length;i+=4)b.putInt(i,Integer.rotateLeft(b.getInt(i),6)^key); }
    private static int crc(byte[] bytes,int seed) { int n=~seed; for(byte b:bytes){n^=b&255;for(int bit=0;bit<8;bit++)n=(n>>>1)^((n&1)!=0?0xedb88320:0);}return ~n; }
}
