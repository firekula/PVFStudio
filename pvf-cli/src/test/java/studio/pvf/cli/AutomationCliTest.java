package studio.pvf.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutomationCliTest {
    @TempDir Path temporary;

    @Test void schemaAndInvalidCommandHavePredictableEnvelopes() {
        var schema = run("schema", "list");
        assertEquals(0, schema.status());
        assertTrue(schema.out().contains("\"schema_version\":1"));
        assertTrue(schema.out().contains("\"--limit\""));
        assertEquals("", schema.err());
        var error = run("--json", "list", "missing.pvf", "--limit", "0");
        assertEquals(2, error.status());
        assertTrue(error.out().contains("\"code\":\"INVALID_ARGUMENT\""));
        assertEquals("", error.err());
    }

    @Test void pagesAreOrderedAndCatPreservesLineEndings() throws Exception {
        var archive = archive();
        var page = run("--json", "list", archive, "--prefix", "TEST/", "--limit", "1");
        assertEquals(0, page.status(), page.out());
        assertTrue(page.out().contains("\"path\":\"test/a.stk\""));
        assertTrue(page.out().contains("\"next_offset\":1"));
        var next = run("--json", "list", archive, "--prefix", "test/", "--limit", "1", "--offset", "1");
        assertTrue(next.out().contains("\"path\":\"test/b.stk\""));
        assertTrue(next.out().contains("\"next_offset\":null"));
        var cat = run("--json", "cat", archive, "TEST/A.STK", "--max-lines", "1");
        assertEquals(0, cat.status(), cat.out());
        assertTrue(cat.out().contains("\"text\":\"alpha\\r\\n\""));
        assertTrue(cat.out().contains("\"next_line\":2"));
        assertEquals("alpha\r\nbeta\n", run("cat", archive, "test/a.stk").out());
        var search = run("--json", "search", archive, "BETA", "--prefix", "test/");
        assertEquals(0, search.status(), search.out());
        assertTrue(search.out().contains("\"line\":2"));
        assertTrue(search.out().contains("\"preview\":\"beta\""));
        var partial = run("--json", "search", archive, "x", "--scan-limit", "1");
        assertTrue(partial.out().contains("\"matches\":[]"));
        assertTrue(partial.out().contains("\"next_cursor\":\"1:1\""));
        var rest = run("--json", "search", archive, "x", "--scan-limit", "1", "--cursor", "1:1");
        assertTrue(rest.out().contains("\"path\":\"test/b.stk\""));
        assertTrue(rest.out().contains("\"next_cursor\":null"));
    }

    @Test void workspaceEditsCanBeReadAndExportedWithoutOverwriting() throws Exception {
        var archive = archive();
        byte[] original = Files.readAllBytes(Path.of(archive));
        String workspace = temporary.resolve("draft").toString();
        String output = temporary.resolve("new.pvf").toString();
        var ambiguous = run("--json", "replace-text", archive, workspace, "test/b.stk", "--from", "x", "--to", "y");
        assertEquals(3, ambiguous.status(), ambiguous.out());
        assertTrue(ambiguous.out().contains("\"code\":\"MATCH_COUNT\""));
        assertTrue(run("--json", "status", archive, workspace).out().contains("\"changed\":0"));
        var edit = run("--json", "replace-text", archive, workspace, "test/a.stk", "--from", "beta", "--to", "gamma");
        assertEquals(0, edit.status(), edit.out());
        var cat = run("--json", "cat", archive, "test/a.stk", "--workspace", workspace);
        assertTrue(cat.out().contains("alpha\\r\\ngamma\\n"), cat.out());
        var exported = run("--json", "workspace-export", archive, workspace, output);
        assertEquals(0, exported.status(), exported.out());
        assertEquals("alpha\r\ngamma\n", run("cat", output, "test/a.stk").out());
        var existing = run("--json", "workspace-export", archive, workspace, output);
        assertEquals(3, existing.status(), existing.out());
        assertTrue(existing.out().contains("\"code\":\"OUTPUT_EXISTS\""), existing.out());
        assertArrayEquals(original, Files.readAllBytes(Path.of(archive)));
    }

    @Test void missingWorkspaceIsNotCreatedByReading() throws Exception {
        Path missing = Files.createDirectory(temporary.resolve("empty"));
        var result = run("--json", "status", archive(), missing.toString());
        assertEquals(3, result.status(), result.out());
        assertTrue(result.out().contains("\"code\":\"WORKSPACE_NOT_FOUND\""), result.out());
        assertFalse(Files.exists(missing.resolve("workspace.lock")));
    }

    private String archive() throws Exception {
        var names = List.of("test/b.stk", "test/a.stk");
        var contents = List.of("x x\n", "alpha\r\nbeta\n");
        var tree = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        var body = new ByteArrayOutputStream();
        for (int i = 0; i < names.size(); i++) {
            byte[] path = names.get(i).getBytes(StandardCharsets.US_ASCII);
            byte[] content = contents.get(i).getBytes(StandardCharsets.US_ASCII);
            byte[] padded = java.util.Arrays.copyOf(content, (content.length + 3) & ~3);
            int key = crc(padded, body.size());
            tree.putInt(i + 1).putInt(body.size()).putInt(content.length).putInt(key).putInt(path.length).put(path);
            encrypt(padded, key);
            body.write(padded);
        }
        int key = crc(tree.array(), names.size());
        encrypt(tree.array(), key);
        var packed = ByteBuffer.allocate(27 + tree.capacity() + body.size()).order(ByteOrder.LITTLE_ENDIAN);
        packed.put("DNF_SCRIPT_PACK".getBytes(StandardCharsets.US_ASCII));
        packed.putInt(tree.capacity()).putInt(key).putInt(names.size()).put(tree.array()).put(body.toByteArray());
        return Files.write(temporary.resolve("source.pvf"), packed.array()).toString();
    }

    private static int crc(byte[] bytes, int seed) {
        int state = ~seed;
        for (byte value : bytes) {
            state ^= value & 0xff;
            for (int bit = 0; bit < 8; bit++) state = (state >>> 1) ^ ((state & 1) == 0 ? 0 : 0xedb88320);
        }
        return ~state;
    }

    private static void encrypt(byte[] bytes, int key) {
        var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < bytes.length; i += 4)
            buffer.putInt(i, Integer.rotateLeft(buffer.getInt(i), 6) ^ key);
    }

    private record Result(int status, String out, String err) {}

    private static Result run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int status = Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
}
