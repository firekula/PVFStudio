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
                assertEquals(referenceCrc(decrypt(ciphertext(fixture.bytes, entry), entry.key()), entry.offset()), entry.key());
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
    void editsPreserveUnchangedPlaintextAndUnmovedCiphertext(int replacementLength) throws Exception {
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
                    assertEquals(referenceCrc(decrypt(ciphertext(outputBytes, after), after.key()), after.offset()), after.key());
                    assertArrayEquals(rawPath(fixture.bytes, before.index()), rawPath(outputBytes, after.index()));
                    if (!before.path().equals(TARGET)) {
                        assertEquals(before.length(), after.length());
                        assertArrayEquals(original.readBytes(before), edited.readBytes(after));
                        assertArrayEquals(decrypt(ciphertext(fixture.bytes, before), before.key()),
                                decrypt(ciphertext(outputBytes, after), after.key()));
                        if (before.offset() == after.offset())
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
                new Definition("empty/first.etc", new byte[0], 101),
                new Definition("config/b.etc", replacement(8), 102),
                new Definition("empty/second.etc", new byte[0], 103),
                new Definition("config/final.etc", replacement(3), 104)),
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
                        assertEquals(referenceCrc(decrypt(ciphertext(editedBytes, after), after.key()), after.offset()), after.key());
                        assertArrayEquals(changes.getOrDefault(before.path(), original.readBytes(before)), edited.readBytes(after));
                        if (!changes.containsKey(before.path())) {
                            assertArrayEquals(decrypt(ciphertext(fixture.bytes, before), before.key()),
                                    decrypt(ciphertext(editedBytes, after), after.key()));
                            if (before.offset() == after.offset())
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
                new Definition("CONFIG\\A.ETC", new byte[]{1}, 1),
                new Definition("config/a.etc", new byte[]{2}, 2)), new int[]{1, 0}, false);
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

    @Test
    void seededCrcMatchesKnownZlibVectors() {
        byte[] input = "123456789".getBytes(StandardCharsets.US_ASCII);
        assertEquals(0xcbf43926, PvfIo.crc32(input, 0));
        assertEquals(0x01f4807b, PvfIo.crc32(input, 0x12345678L));
        assertEquals(0xffffffff, PvfIo.crc32(new byte[0], 0xffffffffL));
    }

    @Test
    void sameLengthEditUpdatesEntryAndDirectoryCrcWhilePreservingPadding() throws Exception {
        Fixture fixture = fixture(false);
        Path source = fixture.write(temporary.resolve("same-length-source.pvf"));
        Path output = temporary.resolve("same-length-edited.pvf");
        try (PvfArchive archive = PvfArchive.open(source)) {
            PvfEntry before = archive.entry(TARGET);
            byte[] paddedBefore = decrypt(ciphertext(fixture.bytes, before), before.key());
            PvfWriter.export(archive, Map.of(TARGET, replacement(before.length())), output);
            byte[] written = Files.readAllBytes(output);
            assertClientChecksums(written);
            try (PvfArchive edited = PvfArchive.open(output)) {
                PvfEntry after = edited.entry(TARGET);
                assertEquals(before.offset(), after.offset());
                assertNotEquals(before.key(), after.key());
                byte[] paddedAfter = decrypt(ciphertext(written, after), after.key());
                assertArrayEquals(Arrays.copyOfRange(paddedBefore, before.length(), paddedBefore.length),
                        Arrays.copyOfRange(paddedAfter, after.length(), paddedAfter.length));
                assertNotEquals(little(fixture.bytes).getInt(19), little(written).getInt(19));
                for (PvfEntry entry : archive.entries()) {
                    if (!entry.path().equals(TARGET))
                        assertArrayEquals(ciphertext(fixture.bytes, entry), ciphertext(written, edited.entry(entry.path())));
                }
            }
        }
    }

    @Test
    void resizingFirstPhysicalEntryRekeysMovedUnchangedEntries() throws Exception {
        Fixture fixture = fixture(false);
        Path source = fixture.write(temporary.resolve("shift-source.pvf"));
        Path output = temporary.resolve("shift-output.pvf");
        Path secondRoundTrip = temporary.resolve("shift-roundtrip.pvf");
        String firstPhysical = "config/b.etc";
        try (PvfArchive original = PvfArchive.open(source)) {
            assertEquals(0, original.entry(firstPhysical).offset());
            PvfWriter.export(original, Map.of(firstPhysical, replacement(13)), output);
            byte[] written = Files.readAllBytes(output);
            assertClientChecksums(written);
            try (PvfArchive edited = PvfArchive.open(output)) {
                for (PvfEntry before : original.entries()) {
                    PvfEntry after = edited.entry(before.path());
                    if (before.path().equals(firstPhysical)) continue;
                    assertEquals(before.offset() + 8, after.offset());
                    assertNotEquals(before.key(), after.key());
                    assertArrayEquals(original.readBytes(before), edited.readBytes(after));
                    assertArrayEquals(decrypt(ciphertext(fixture.bytes, before), before.key()),
                            decrypt(ciphertext(written, after), after.key()));
                    assertFalse(Arrays.equals(ciphertext(fixture.bytes, before), ciphertext(written, after)),
                            "Offset-dependent ciphertext must change when an untouched entry moves");
                }
                PvfWriter.export(edited, Map.of(), secondRoundTrip);
            }
            assertArrayEquals(written, Files.readAllBytes(secondRoundTrip));
        }
    }

    @Test
    void rejectsDirectoryChecksumCorruption() throws Exception {
        Fixture fixture = fixture(false);
        byte[] corrupt = fixture.bytes.clone();
        // Alter opaque tree padding so the directory fields still have their old shape.
        corrupt[bodyOffset(corrupt) - 1] ^= 1;
        Path path = temporary.resolve("bad-tree-checksum.pvf");
        Files.write(path, corrupt);
        IOException failure = assertThrows(IOException.class, () -> {
            try (PvfArchive ignored = PvfArchive.open(path)) { /* CRC must reject it */ }
        });
        assertTrue(failure.getMessage().contains("Directory CRC32 mismatch"));
    }

    @Test
    void rejectsLegacyPreservedEntryKeyOnReadAndNoOpExport() throws Exception {
        Fixture fixture = fixture(false);
        Path originalPath = fixture.write(temporary.resolve("legacy-source.pvf"));
        byte[] legacy = fixture.bytes.clone();
        try (PvfArchive original = PvfArchive.open(originalPath)) {
            PvfEntry entry = original.entry(TARGET);
            byte[] plaintext = decrypt(ciphertext(fixture.bytes, entry), entry.key());
            plaintext[0] ^= 0x40;
            byte[] oldKeyCiphertext = encrypt(plaintext, entry.key());
            System.arraycopy(oldKeyCiphertext, 0, legacy,
                    Math.toIntExact(bodyOffset(legacy) + entry.offset()), oldKeyCiphertext.length);
        }
        Path legacyPath = temporary.resolve("legacy-preserved-key.pvf");
        Path output = temporary.resolve("must-not-exist.pvf");
        Files.write(legacyPath, legacy);
        try (PvfArchive archive = PvfArchive.open(legacyPath)) {
            IOException failure = assertThrows(IOException.class, () -> archive.readBytes(TARGET));
            assertTrue(failure.getMessage().contains("Entry CRC32 mismatch"));
            assertThrows(IOException.class, () -> PvfWriter.export(archive, Map.of(), output));
        }
        assertFalse(Files.exists(output));
        try (var paths = Files.list(temporary)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith(".pvf-export-")));
        }
    }

    private static Fixture fixture(boolean gaps) throws IOException {
        return fixture(List.of(
                new Definition("CONFIG\\A.ETC\0", new byte[]{1, 2, 3, 4, 5}, 0x00006a15L),
                new Definition("config/b.etc", "eight!!!".getBytes(StandardCharsets.US_ASCII), 0xf1234567L),
                new Definition("config/c.etc", new byte[]{9, 8, 7}, 13L)),
                new int[]{1, 2, 0}, gaps);
    }

    private static Fixture fixture(List<Definition> definitions, int[] physicalOrder, boolean gaps) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int[] offsets = new int[definitions.size()];
        int[] keys = new int[definitions.size()];
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
            keys[index] = referenceCrc(padded, offsets[index]);
            body.write(encrypt(padded, keys[index]));
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
                    .putInt(keys[index]).putInt(rawPath.length);
            tree.write(record.array());
            tree.write(rawPath);
        }
        // Include opaque tree tail, not just the minimum needed for DWORD alignment.
        do {
            tree.write(0x90 + (tree.size() & 0xf));
        } while (tree.size() % 4 != 0);
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        archive.write(MAGIC);
        int treeKey = referenceCrc(tree.toByteArray(), definitions.size());
        archive.write(ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(tree.size()).putInt(treeKey).putInt(definitions.size()).array());
        archive.write(encrypt(tree.toByteArray(), treeKey));
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

    /** Bit-at-a-time oracle; intentionally does not reuse the production CRC table. */
    private static int referenceCrc(byte[] plaintext, long seed) {
        int crc = ~(int) seed;
        for (byte value : plaintext) {
            crc ^= value & 0xff;
            for (int bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ ((crc & 1) != 0 ? 0xedb88320 : 0);
        }
        return ~crc;
    }

    private static void assertClientChecksums(byte[] archive) {
        int count = little(archive).getInt(23);
        byte[] tree = decryptedTree(archive);
        assertEquals(referenceCrc(tree, Integer.toUnsignedLong(count)), little(archive).getInt(19));
        ByteBuffer fields = little(tree);
        int cursor = 0;
        for (int index = 0; index < count; index++) {
            long offset = Integer.toUnsignedLong(fields.getInt(cursor + 4));
            int length = fields.getInt(cursor + 8);
            int key = fields.getInt(cursor + 12);
            int pathLength = fields.getInt(cursor + 16);
            int start = Math.toIntExact(bodyOffset(archive) + offset);
            byte[] plaintext = decrypt(Arrays.copyOfRange(archive, start, start + padded(length)), key);
            assertEquals(referenceCrc(plaintext, offset), key, "Client checksum for directory entry " + index);
            cursor += 20 + pathLength;
        }
    }

    private record Definition(String rawPath, byte[] content, long number) { }

    private record Fixture(byte[] bytes, List<Definition> definitions, List<Integer> recordPositions) {
        Path write(Path path) throws IOException { return Files.write(path, bytes); }

        byte[] mutateTree(Consumer<ByteBuffer> mutation) {
            byte[] result = bytes.clone();
            byte[] tree = decryptedTree(result);
            mutation.accept(little(tree));
            int treeKey = referenceCrc(tree, Integer.toUnsignedLong(little(result).getInt(23)));
            little(result).putInt(19, treeKey);
            byte[] encrypted = encrypt(tree, treeKey);
            System.arraycopy(encrypted, 0, result, HEADER_SIZE, encrypted.length);
            return result;
        }
    }
}
