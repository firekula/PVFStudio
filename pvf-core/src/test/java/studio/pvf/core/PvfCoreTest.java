package studio.pvf.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Independent encrypted fixtures exercise the wire format, rather than reusing production codecs. */
class PvfCoreTest {
    private static final int HEADER_SIZE = 27;
    private static final byte[] MAGIC = "DNF_SCRIPT_PACK".getBytes(StandardCharsets.US_ASCII);
    private static final int TREE_KEY = 0x87654321;
    private static final String TARGET = "config/a.etc";

    @TempDir Path temporary;

    @Test
    void readsTreeOrderIndependentlyOfPhysicalOrderAndNormalizesLookup() throws Exception {
        Fixture fixture = fixture(false);
        Path source = fixture.write(temporary.resolve("source.pvf"));
        try (PvfArchive archive = PvfArchive.open(source)) {
            assertEquals(3, archive.fileCount());
            assertEquals(List.of(TARGET, "config/b.etc", "config/c.etc"),
                    archive.entries().stream().map(PvfEntry::path).toList());
            assertTrue(archive.entry(TARGET).offset() > archive.entry("config/b.etc").offset());
            assertArrayEquals(fixture.definitions.get(0).content, archive.readBytes("/CONFIG\\A.ETC"));
            for (int index = 0; index < fixture.definitions.size(); index++) {
                Definition definition = fixture.definitions.get(index);
                PvfEntry entry = archive.entries().get(index);
                assertEquals(index, entry.index());
                assertEquals(definition.number, entry.fileNumber());
                assertEquals(definition.key, entry.key());
                assertArrayEquals(definition.content, archive.readBytes(entry));
            }
        }
    }

    @Test
    void rebuildingWithoutChangesIsByteExactIncludingNonzeroPadding() throws Exception {
        Fixture fixture = fixture(false);
        Path source = fixture.write(temporary.resolve("source.pvf"));
        Path output = temporary.resolve("roundtrip.pvf");
        try (PvfArchive archive = PvfArchive.open(source)) {
            PvfWriter.export(archive, Map.of(), output);
        }
        assertArrayEquals(fixture.bytes, Files.readAllBytes(output));
        assertArrayEquals(fixture.bytes, Files.readAllBytes(source));
    }

    @Test
    void supplyingIdenticalPlaintextDoesNotChangeExistingPadding() throws Exception {
        Fixture fixture = fixture(false);
        Path source = fixture.write(temporary.resolve("source.pvf"));
        Path output = temporary.resolve("same-plaintext.pvf");
        try (PvfArchive archive = PvfArchive.open(source)) {
            PvfWriter.export(archive, Map.of(TARGET, archive.readBytes(TARGET)), output);
        }
        assertArrayEquals(fixture.bytes, Files.readAllBytes(output));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 4, 5, 7, 8, 9, 16, 257})
    void editsPreserveAllOtherPlaintextCiphertextAndMetadata(int replacementLength) throws Exception {
        Fixture fixture = fixture(false);
        Path source = fixture.write(temporary.resolve("source.pvf"));
        Path output = temporary.resolve("edited.pvf");
        byte[] replacement = replacement(replacementLength);
        try (PvfArchive original = PvfArchive.open(source)) {
            PvfWriter.export(original, Map.of(TARGET, replacement), output);
            try (PvfArchive edited = PvfArchive.open(output)) {
                assertEquals(original.fileCount(), edited.fileCount());
                assertArrayEquals(replacement, edited.readBytes(TARGET));
                byte[] outputBytes = Files.readAllBytes(output);
                assertArrayEquals(treeTail(fixture.bytes), treeTail(outputBytes));
                for (PvfEntry before : original.entries()) {
                    PvfEntry after = edited.entry(before.path());
                    assertEquals(before.index(), after.index());
                    assertEquals(before.fileNumber(), after.fileNumber());
                    assertEquals(before.key(), after.key());
                    assertArrayEquals(rawPath(fixture.bytes, before.index()), rawPath(outputBytes, after.index()));
                    if (!before.path().equals(TARGET)) {
                        assertEquals(before.length(), after.length());
                        assertArrayEquals(original.readBytes(before), edited.readBytes(after));
                        assertArrayEquals(ciphertext(fixture.bytes, before), ciphertext(outputBytes, after));
                    }
                }
            }
        }
        assertArrayEquals(fixture.bytes, Files.readAllBytes(source));
    }

    @Test
    void preservesLeadingAndInterEntryGapsAndTrailingDataAfterResize() throws Exception {
        Fixture fixture = fixture(true);
        Path source = fixture.write(temporary.resolve("gaps.pvf"));
        Path exact = temporary.resolve("gaps-exact.pvf");
        Path output = temporary.resolve("gaps-edit.pvf");
        try (PvfArchive original = PvfArchive.open(source)) {
            PvfWriter.export(original, Map.of(), exact);
            assertArrayEquals(fixture.bytes, Files.readAllBytes(exact));
            PvfWriter.export(original, Map.of(TARGET, replacement(17)), output);
            try (PvfArchive edited = PvfArchive.open(output)) {
                byte[] outputBytes = Files.readAllBytes(output);
                List<PvfEntry> physical = original.entries().stream()
                        .sorted(Comparator.comparingLong(PvfEntry::offset)).toList();
                long oldCursor = bodyOffset(fixture.bytes);
                long newCursor = bodyOffset(outputBytes);
                for (PvfEntry before : physical) {
                    PvfEntry after = edited.entry(before.path());
                    int oldStart = Math.toIntExact(bodyOffset(fixture.bytes) + before.offset());
                    int newStart = Math.toIntExact(bodyOffset(outputBytes) + after.offset());
                    assertArrayEquals(Arrays.copyOfRange(fixture.bytes, Math.toIntExact(oldCursor), oldStart),
                            Arrays.copyOfRange(outputBytes, Math.toIntExact(newCursor), newStart));
                    oldCursor = oldStart + padded(before.length());
                    newCursor = newStart + padded(after.length());
                }
                assertArrayEquals(Arrays.copyOfRange(fixture.bytes, Math.toIntExact(oldCursor), fixture.bytes.length),
                        Arrays.copyOfRange(outputBytes, Math.toIntExact(newCursor), outputBytes.length));
            }
        }
    }

    @Test
    void zeroLengthEntriesSharingOffsetsCanGrowShrinkAndRoundTripAgain() throws Exception {
        Fixture fixture = fixture(List.of(
                new Definition("empty/first.etc", new byte[0], 101, 0x12345678),
                new Definition("config/b.etc", replacement(8), 102, 0x87654321),
                new Definition("empty/second.etc", new byte[0], 103, 0x10203040),
                new Definition("config/final.etc", replacement(3), 104, 0xfedcba98)),
                new int[]{2, 0, 1, 3}, false);
        Path source = fixture.write(temporary.resolve("zero-entries.pvf"));
        List<Map<String, byte[]>> cases = List.of(
                Map.of("empty/second.etc", replacement(7)),
                Map.of("config/b.etc", new byte[0]),
                Map.of("empty/first.etc", replacement(1),
                        "empty/second.etc", replacement(9), "config/b.etc", new byte[0]));
        try (PvfArchive original = PvfArchive.open(source)) {
            assertEquals(original.entry("empty/first.etc").offset(), original.entry("empty/second.etc").offset());
            assertEquals(original.entry("empty/first.etc").offset(), original.entry("config/b.etc").offset());
            for (int index = 0; index < cases.size(); index++) {
                Map<String, byte[]> changes = cases.get(index);
                Path editedPath = temporary.resolve("zero-edit-" + index + ".pvf");
                Path secondRoundTrip = temporary.resolve("zero-edit-roundtrip-" + index + ".pvf");
                PvfWriter.export(original, changes, editedPath);
                byte[] editedBytes = Files.readAllBytes(editedPath);
                try (PvfArchive edited = PvfArchive.open(editedPath)) {
                    for (PvfEntry before : original.entries()) {
                        PvfEntry after = edited.entry(before.path());
                        assertEquals(before.fileNumber(), after.fileNumber());
                        assertEquals(before.key(), after.key());
                        assertArrayEquals(changes.getOrDefault(before.path(), original.readBytes(before)), edited.readBytes(after));
                        if (!changes.containsKey(before.path())) {
                            assertArrayEquals(ciphertext(fixture.bytes, before), ciphertext(editedBytes, after));
                        }
                    }
                    PvfWriter.export(edited, Map.of(), secondRoundTrip);
                }
                assertArrayEquals(editedBytes, Files.readAllBytes(secondRoundTrip));
                assertArrayEquals(treeTail(fixture.bytes), treeTail(editedBytes));
            }
        }
    }

    @Test
    void emptyArchiveKeepsItsOpaqueDirectoryAndBodyBytes() throws Exception {
        Fixture fixture = fixture(List.of(), new int[0], true);
        Path source = fixture.write(temporary.resolve("empty.pvf"));
        Path output = temporary.resolve("empty-roundtrip.pvf");
        try (PvfArchive archive = PvfArchive.open(source)) {
            assertEquals(0, archive.fileCount());
            PvfWriter.export(archive, Map.of(), output);
        }
        assertArrayEquals(fixture.bytes, Files.readAllBytes(output));
    }

    @Test
    void rejectsMalformedHeadersAndTruncatedBodies() throws Exception {
        Fixture fixture = fixture(false);
        List<byte[]> invalid = new ArrayList<>();
        invalid.add(new byte[0]);
        invalid.add(Arrays.copyOf(fixture.bytes, HEADER_SIZE - 1));
        byte[] wrongMagic = fixture.bytes.clone();
        wrongMagic[0] ^= 1;
        invalid.add(wrongMagic);
        byte[] negativeTree = fixture.bytes.clone();
        little(negativeTree).putInt(15, -4);
        invalid.add(negativeTree);
        byte[] misalignedTree = fixture.bytes.clone();
        little(misalignedTree).putInt(15, 3);
        invalid.add(misalignedTree);
        byte[] impossibleCount = fixture.bytes.clone();
        little(impossibleCount).putInt(23, -1);
        invalid.add(impossibleCount);
        invalid.add(Arrays.copyOf(fixture.bytes, fixture.bytes.length - 1));
        for (int index = 0; index < invalid.size(); index++) {
            Path path = temporary.resolve("bad-header-" + index + ".pvf");
            Files.write(path, invalid.get(index));
            assertThrows(IOException.class, () -> {
                try (PvfArchive ignored = PvfArchive.open(path)) { /* must fail during open */ }
            }, "malformed fixture " + index);
        }
    }

    @Test
    void rejectsMalformedTreePathsOffsetsLengthsAndDuplicateNumbers() throws Exception {
        Fixture fixture = fixture(false);
        int first = fixture.recordPositions.get(0);
        int second = fixture.recordPositions.get(1);
        List<Consumer<ByteBuffer>> mutations = List.of(
                tree -> tree.putInt(first + 4, -1),
                tree -> tree.putInt(first + 8, -1),
                tree -> tree.putInt(first + 16, -1),
                tree -> tree.putInt(first + 16, Integer.MAX_VALUE),
                tree -> tree.putInt(second, tree.getInt(first)),
                tree -> tree.putInt(second + 4, tree.getInt(first + 4)));
        for (int index = 0; index < mutations.size(); index++) {
            Path path = temporary.resolve("bad-tree-" + index + ".pvf");
            Files.write(path, fixture.mutateTree(mutations.get(index)));
            assertThrows(IOException.class, () -> {
                try (PvfArchive ignored = PvfArchive.open(path)) { /* must fail during open */ }
            }, "tree mutation " + index);
        }
        Fixture duplicatePath = fixture(List.of(
                new Definition("CONFIG\\A.ETC", new byte[]{1}, 1, 1),
                new Definition("config/a.etc", new byte[]{2}, 2, 2)), new int[]{1, 0}, false);
        Path path = duplicatePath.write(temporary.resolve("duplicate-path.pvf"));
        assertThrows(IOException.class, () -> {
            try (PvfArchive ignored = PvfArchive.open(path)) { /* must fail during open */ }
        });
    }

    @Test
    void refusesUnknownOrAmbiguousChangesAndLeavesNoOutput() throws Exception {
        Path source = fixture(false).write(temporary.resolve("source.pvf"));
        try (PvfArchive archive = PvfArchive.open(source)) {
            Path unknown = temporary.resolve("unknown.pvf");
            assertThrows(IOException.class,
                    () -> PvfWriter.export(archive, Map.of("missing.etc", new byte[]{1}), unknown));
            assertFalse(Files.exists(unknown));
            Map<String, byte[]> ambiguous = new LinkedHashMap<>();
            ambiguous.put("CONFIG/A.ETC", new byte[]{1});
            ambiguous.put("config\\a.etc", new byte[]{2});
            Path duplicate = temporary.resolve("duplicate.pvf");
            assertThrows(IOException.class, () -> PvfWriter.export(archive, ambiguous, duplicate));
            assertFalse(Files.exists(duplicate));
        }
    }

    @Test
    void neverOverwritesSourceOrExistingDestination() throws Exception {
        Fixture fixture = fixture(false);
        Path source = fixture.write(temporary.resolve("source.pvf"));
        Path destination = temporary.resolve("existing.pvf");
        byte[] sentinel = {11, 22, 33};
        Files.write(destination, sentinel);
        try (PvfArchive archive = PvfArchive.open(source)) {
            assertThrows(IOException.class,
                    () -> PvfWriter.export(archive, Map.of(TARGET, replacement(12)), source));
            assertThrows(IOException.class,
                    () -> PvfWriter.export(archive, Map.of(TARGET, replacement(12)), destination));
        }
        assertArrayEquals(fixture.bytes, Files.readAllBytes(source));
        assertArrayEquals(sentinel, Files.readAllBytes(destination));
    }

    @Test
    void refusesHardLinkToSource() throws Exception {
        Fixture fixture = fixture(false);
        Path source = fixture.write(temporary.resolve("source.pvf"));
        Path linked = temporary.resolve("linked.pvf");
        boolean supported = true;
        try {
            Files.createLink(linked, source);
        } catch (UnsupportedOperationException | IOException exception) {
            supported = false;
        }
        assumeTrue(supported, "filesystem does not permit hard links");
        try (PvfArchive archive = PvfArchive.open(source)) {
            assertThrows(IOException.class, () -> PvfWriter.export(archive, Map.of(), linked));
        }
        assertArrayEquals(fixture.bytes, Files.readAllBytes(source));
    }

    @Test
    void detectsChangedSourceEvenWhenSizeAndTimestampAreRestored() throws Exception {
        Fixture fixture = fixture(false);
        Path source = fixture.write(temporary.resolve("source.pvf"));
        Path destination = temporary.resolve("changed-source.pvf");
        FileTime timestamp = Files.getLastModifiedTime(source);
        try (PvfArchive archive = PvfArchive.open(source)) {
            byte[] changed = fixture.bytes.clone();
            changed[bodyOffset(changed) + 1] ^= 0x40;
            Files.write(source, changed);
            Files.setLastModifiedTime(source, timestamp);
            assertThrows(IOException.class, () -> PvfWriter.export(archive, Map.of(), destination));
        }
        assertFalse(Files.exists(destination));
    }

    private static Fixture fixture(boolean gaps) throws IOException {
        return fixture(List.of(
                new Definition("CONFIG\\A.ETC\0", new byte[]{1, 2, 3, 4, 5}, 0x00006a15L, 0xe9e2f76b),
                new Definition("config/b.etc", "eight!!!".getBytes(StandardCharsets.US_ASCII), 0xf1234567L, 0x12345678),
                new Definition("config/c.etc", new byte[]{9, 8, 7}, 13L, 0x0badcafe)),
                new int[]{1, 2, 0}, gaps);
    }

    private static Fixture fixture(List<Definition> definitions, int[] physicalOrder, boolean gaps) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int[] offsets = new int[definitions.size()];
        for (int physical = 0; physical < physicalOrder.length; physical++) {
            int index = physicalOrder[physical];
            if (gaps) {
                byte[] gap = new byte[(physical + 1) * 4];
                Arrays.fill(gap, (byte) (0x70 + physical));
                body.write(gap);
            }
            Definition definition = definitions.get(index);
            offsets[index] = body.size();
            byte[] padded = Arrays.copyOf(definition.content, padded(definition.content.length));
            for (int position = definition.content.length; position < padded.length; position++) {
                padded[position] = (byte) (0xb0 + position);
            }
            body.write(encrypt(padded, definition.key));
        }
        if (gaps) body.write(new byte[]{0x55, 0x44, 0x33, 0x22, 0x11, 0x66, 0x77});
        ByteArrayOutputStream tree = new ByteArrayOutputStream();
        List<Integer> recordPositions = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            Definition definition = definitions.get(index);
            byte[] rawPath = definition.rawPath.getBytes(StandardCharsets.US_ASCII);
            recordPositions.add(tree.size());
            ByteBuffer record = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
            record.putInt((int) definition.number).putInt(offsets[index]).putInt(definition.content.length)
                    .putInt(definition.key).putInt(rawPath.length);
            tree.write(record.array());
            tree.write(rawPath);
        }
        // Include opaque tree tail, not just the minimum needed for DWORD alignment.
        do {
            tree.write(0x90 + (tree.size() & 0xf));
        } while (tree.size() % 4 != 0);
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        archive.write(MAGIC);
        archive.write(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(tree.size()).putInt(TREE_KEY).putInt(definitions.size()).array());
        archive.write(encrypt(tree.toByteArray(), TREE_KEY));
        archive.write(body.toByteArray());
        return new Fixture(archive.toByteArray(), definitions, recordPositions);
    }

    /** Explicit shifts keep fixture encryption independent from production code and rotate helpers. */
    private static byte[] encrypt(byte[] plaintext, int key) {
        ByteBuffer input = little(plaintext);
        ByteBuffer output = ByteBuffer.allocate(plaintext.length).order(ByteOrder.LITTLE_ENDIAN);
        while (input.hasRemaining()) {
            int value = input.getInt();
            output.putInt(((value << 6) | (value >>> 26)) ^ key);
        }
        return output.array();
    }

    private static byte[] decrypt(byte[] ciphertext, int key) {
        ByteBuffer input = little(ciphertext);
        ByteBuffer output = ByteBuffer.allocate(ciphertext.length).order(ByteOrder.LITTLE_ENDIAN);
        while (input.hasRemaining()) {
            int value = input.getInt() ^ key;
            output.putInt((value >>> 6) | (value << 26));
        }
        return output.array();
    }

    private static byte[] replacement(int length) {
        byte[] result = new byte[length];
        for (int index = 0; index < length; index++) result[index] = (byte) (index * 17 + 43);
        return result;
    }

    private static byte[] ciphertext(byte[] archive, PvfEntry entry) {
        int start = Math.toIntExact(bodyOffset(archive) + entry.offset());
        return Arrays.copyOfRange(archive, start, start + padded(entry.length()));
    }

    private static int bodyOffset(byte[] archive) {
        return HEADER_SIZE + little(archive).getInt(15);
    }

    private static byte[] decryptedTree(byte[] archive) {
        return decrypt(Arrays.copyOfRange(archive, HEADER_SIZE, bodyOffset(archive)), little(archive).getInt(19));
    }

    private static byte[] rawPath(byte[] archive, int index) {
        byte[] tree = decryptedTree(archive);
        ByteBuffer buffer = little(tree);
        int cursor = 0;
        for (int current = 0; current <= index; current++) {
            int length = buffer.getInt(cursor + 16);
            if (current == index) return Arrays.copyOfRange(tree, cursor + 20, cursor + 20 + length);
            cursor += 20 + length;
        }
        throw new AssertionError("missing record");
    }

    private static byte[] treeTail(byte[] archive) {
        byte[] tree = decryptedTree(archive);
        ByteBuffer buffer = little(tree);
        int cursor = 0;
        int count = little(archive).getInt(23);
        for (int index = 0; index < count; index++) cursor += 20 + buffer.getInt(cursor + 16);
        return Arrays.copyOfRange(tree, cursor, tree.length);
    }

    private static int padded(int length) { return (length + 3) & ~3; }
    private static ByteBuffer little(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }

    private record Definition(String rawPath, byte[] content, long number, int key) { }

    private record Fixture(byte[] bytes, List<Definition> definitions, List<Integer> recordPositions) {
        Path write(Path path) throws IOException { return Files.write(path, bytes); }

        byte[] mutateTree(Consumer<ByteBuffer> mutation) {
            byte[] result = bytes.clone();
            byte[] tree = decryptedTree(result);
            mutation.accept(little(tree));
            byte[] encrypted = encrypt(tree, TREE_KEY);
            System.arraycopy(encrypted, 0, result, HEADER_SIZE, encrypted.length);
            return result;
        }
    }
}
