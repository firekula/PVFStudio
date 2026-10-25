package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecentArchivesTest {
    @TempDir Path directory;

    @Test void successfulOpenPromotesDeduplicatesAndKeepsOnlyLatestFifteen() {
        RecentArchives history = RecentArchives.EMPTY;
        for (int i = 0; i < 20; i++) history = history.opened(directory.resolve("archive-" + i + ".pvf"));
        Path previous = history.paths().get(8);
        history = history.opened(previous.getParent().resolve("sub/../" + previous.getFileName()));
        assertEquals(RecentArchives.LIMIT, history.paths().size()); assertEquals(previous, history.paths().getFirst());
        assertEquals(1, history.paths().stream().filter(previous::equals).count());
        assertFalse(history.paths().contains(directory.resolve("archive-0.pvf")));
    }
    @Test void unicodePathsAndMissingDrivesRemainAvailableAfterRestartAndClear() throws Exception {
        Path file = directory.resolve("settings/recent-files.properties");
        var history = new RecentArchives(List.of(directory.resolve("配置包/Script.pvf"), directory.resolve("unmounted/Other.pvf")));
        history.write(file); assertEquals(history, RecentArchives.read(file));
        RecentArchives.EMPTY.write(file); assertEquals(RecentArchives.EMPTY, RecentArchives.read(file));
        try (var files = Files.list(file.getParent())) { assertEquals(1, files.count()); }
    }
    @Test void malformedIndividualPathsDoNotDiscardOtherRecords() throws Exception {
        Path file = directory.resolve("recent-files.properties"); var properties = new Properties();
        properties.setProperty("format", "1"); properties.setProperty("path.0", "relative.pvf");
        properties.setProperty("path.1", directory.resolve("valid.pvf").toString()); properties.setProperty("path.2", "bad\0name.pvf");
        properties.setProperty("path.3", directory.resolve("valid.pvf").toString());
        try (var writer = Files.newBufferedWriter(file)) { properties.store(writer, "fixture"); }
        assertEquals(List.of(directory.resolve("valid.pvf")), RecentArchives.read(file).paths());
    }
    @Test void invalidOrFutureFormatReportsAnErrorAndMissingFileStartsEmpty() throws Exception {
        Path file = directory.resolve("recent-files.properties"); assertEquals(RecentArchives.EMPTY, RecentArchives.read(file));
        for (String contents : List.of("format=2", "format=1\npath.0=\\uZZZZ")) {
            Files.writeString(file, contents); assertThrows(IOException.class, () -> RecentArchives.read(file));
        }
    }
}
