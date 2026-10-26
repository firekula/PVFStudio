package studio.pvf.sceneedit;

import org.junit.jupiter.api.Test;
import studio.pvf.preview.DungeonScript;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static studio.pvf.preview.DungeonScript.*;

class DungeonDocumentTest {
    private static final String FIRST = "[hell dungeon] 1\n[maze info]\n[quest connection] 0 4511 -1\n"
            + "[size] 4 1\n[greed] `BBFFEEAA`\n"
            + "[map specification] 0 0 1000 [/map specification]\n"
            + "[map specification] 1 0 1001 1002 [/map specification]\n"
            + "[boss map specification] 2 0 2000 [/boss map specification]\n"
            + "[start map] 0 0 [/start map]\n[boss map] 2 0 [/boss map]\n"
            + "[seal door map index] 60067\n[seal door pos] 1 0\n[hit count] 50 50 50 50 50\n[seal door appear rate] 10\n";
    private static final String SECOND = "[maze info]\n[quest connection] 1 4512 -1\n[size] 2 1\n[greed] `BBEE`\n"
            + "[start map] 0 0 [/start map]\n[boss map] 1 0 [/boss map]\n[escape hell] 1\n[custom] @9(6) @10(77)\n";
    private static DungeonDocument parse(String text) throws IOException { return DungeonDocument.parse("dungeon/test.dgn", text); }

    @Test void createRoomAndConnectUpdatesReciprocalBits() throws Exception {
        String created = parse(FIRST).createRoom(0, new Position(3,0), List.of(1003)).applyTo(FIRST);
        var document = parse(created);
        String connected = document.connect(0, new Position(2,0), Direction.RIGHT, true).applyTo(created);
        var maze = parse(connected).mazes().get(0).layout();
        assertEquals(List.of(1,5,5,4), maze.masks());
        assertEquals(List.of(1003), maze.maps().get(new Position(3,0)));
        assertTrue(maze.connected(new Position(2,0), Direction.RIGHT));
        assertThrows(IOException.class, () -> parse(FIRST).connect(0, new Position(2,0), Direction.RIGHT, true));
    }

    @Test void deleteRoomRemovesItsReferencesWithoutDeletingOtherMapDefinitions() throws Exception {
        String deleted = parse(FIRST).removeRoom(0, new Position(1,0)).applyTo(FIRST);
        var document = parse(deleted); var maze = document.mazes().get(0).layout();
        assertEquals(0, maze.mask(new Position(0,0))); assertEquals(0, maze.mask(new Position(2,0)));
        assertFalse(maze.maps().containsKey(new Position(1,0)));
        assertEquals(List.of(1000), maze.maps().get(new Position(0,0)));
        assertEquals(List.of(2000), maze.bossMaps().get(new Position(2,0)));
        assertNull(document.hell(0).position()); assertEquals(0, document.hell(0).mapId());
    }

    @Test void resizeRefusesImplicitCroppingAndEnforcesNativeLimits() throws Exception {
        var document = parse(FIRST);
        assertThrows(IOException.class, () -> document.resize(0, 2, 1, false));
        assertThrows(IOException.class, () -> document.resize(0, 51, 1, false));
        var cropped = parse(document.resize(0,2,1,true).applyTo(FIRST));
        assertEquals(2, cropped.mazes().get(0).layout().width());
        assertEquals(List.of(1,4), cropped.mazes().get(0).layout().masks());
        assertTrue(cropped.mazes().get(0).layout().bosses().isEmpty());
        assertFalse(cropped.mazes().get(0).layout().bossMaps().containsKey(new Position(2,0)));
    }

    @Test void retainsMultipleCandidatesAndNoBoss() throws Exception {
        String noBoss = parse(FIRST).setBosses(0,List.of()).applyTo(FIRST);
        assertTrue(noBoss.contains("-1 -1")); assertTrue(parse(noBoss).mazes().get(0).layout().bosses().isEmpty());
        String starts = parse(FIRST).setStarts(0,List.of(new Position(0,0),new Position(1,0))).applyTo(FIRST);
        assertEquals(2, parse(starts).mazes().get(0).layout().starts().size());
        String maps = parse(FIRST).setCandidates(0,new Position(1,0),false,List.of(1004,1005,1006)).applyTo(FIRST);
        assertEquals(List.of(1004,1005,1006), parse(maps).mazes().get(0).layout().maps().get(new Position(1,0)));
    }

    @Test void keepsQuestFieldsAndOtherMazesUntouched() throws Exception {
        String text = FIRST + SECOND;
        String changed = parse(text).setCandidates(0,new Position(1,0),false,List.of(18000)).applyTo(text);
        assertTrue(changed.endsWith(SECOND));
        assertTrue(changed.contains("[quest connection] 0 4511 -1"));
        String copied = parse(text).copyMaze(0).applyTo(text);
        assertEquals(3, parse(copied).mazes().size());
        assertTrue(copied.endsWith(SECOND));
    }

    @Test void hellReplacementAndEscapeAreScopedPerMaze() throws Exception {
        String text = FIRST + SECOND; var document = parse(text);
        assertEquals(60067,document.hell(0).mapId()); assertFalse(document.hell(0).excluded());
        assertTrue(document.hell(1).excluded()); assertEquals(0,document.hell(1).mapId());
        var setting = new DungeonDocument.HellSettings(true,false,60068,new Position(1,0),List.of(50,50,50,50,50),10,null);
        String changed = document.setHell(1,setting).applyTo(text); var updated = parse(changed);
        assertTrue(changed.startsWith(FIRST));
        assertEquals(60067,updated.hell(0).mapId()); assertEquals(60068,updated.hell(1).mapId());
        assertFalse(updated.hell(1).excluded());
        assertEquals(List.of(new Position(2,0)),updated.mazes().get(0).layout().bosses());
    }

    @Test void preservesFourAndFiveHitCountsWithoutGuessingProbabilityUnits() throws Exception {
        String old = FIRST.replace("50 50 50 50 50", "50 50 50 50");
        var document = parse(old); assertEquals(4,document.hell(0).hitCounts().size());
        var settings = document.hell(0);
        String changed = document.setHell(0,new DungeonDocument.HellSettings(true,false,60068,new Position(1,0),settings.hitCounts(),settings.appearRate(),settings.coinLimit())).applyTo(old);
        assertEquals(4,parse(changed).hell(0).hitCounts().size()); assertTrue(changed.contains("[seal door appear rate] 10"));
        assertThrows(IOException.class, () -> document.setHell(0,new DungeonDocument.HellSettings(true,false,60068,new Position(1,0),List.of(1,2,3,4,5),10,null)));
        assertThrows(IOException.class, () -> document.setHell(0,new DungeonDocument.HellSettings(true,true,60068,new Position(1,0),settings.hitCounts(),10,null)));
    }

    @Test void pendingAaRoomIsNotReportedPlayableAndEveryStartCandidateIsChecked() throws Exception {
        String created = parse(FIRST).createRoom(0,new Position(3,0),List.of(1003)).applyTo(FIRST);
        var pending = parse(created); assertFalse(pending.mazes().get(0).playable(new Position(3,0)));
        assertTrue(pending.diagnostics().stream().anyMatch(p -> p.message().contains("待完成")));
        String starts = pending.setStarts(0,List.of(new Position(0,0),new Position(3,0))).applyTo(created);
        assertTrue(parse(starts).diagnostics().stream().anyMatch(p -> p.message().contains("(3,0)") && p.message().contains("不可达")));
        assertThrows(IOException.class, () -> parse(FIRST).addMaze(0,1));
    }

    @Test void legacyGlyphsAndProjectedEscapedLineSeparatorsRemainInTheirDialect() throws Exception {
        String text = "[maze info] [size] 2 1 [greed] `┾┽` [start map] 0 0 [/start map] [boss map] 1 0 [/boss map]";
        String changed = parse(text).connect(0,new Position(0,0),Direction.RIGHT,false).applyTo(text);
        assertTrue(changed.contains("`┼┼`")); assertFalse(changed.contains("AAAA"));
        assertThrows(IOException.class, () -> parse(text).createRoom(0,new Position(1,0),List.of(2)));
    }

    @Test void ordinaryRoomWithoutHellConfigurationCanBeDeleted() throws Exception {
        String text = "[maze info] [size] 2 1 [greed] `BBEE` [start map] 0 0 [/start map] [boss map] 1 0 [/boss map]";
        String deleted = parse(text).removeRoom(0,new Position(1,0)).applyTo(text);
        assertEquals(List.of(0,0),parse(deleted).mazes().get(0).layout().masks());
        assertTrue(parse(deleted).mazes().get(0).layout().bosses().isEmpty());
    }

    @Test void fixedWarRoomCannotBeSilentlyConvertedToMazeLayout() throws Exception {
        var document = parse("[warroom map index] 1000");
        assertThrows(IOException.class, () -> document.addMaze(2,1));
    }

    @Test void resizeIncludesInactiveHellCoordinatesInTheCropDecision() throws Exception {
        String text = "[maze info] [size] 4 1 [greed] `BBEEAAAA` [start map] 0 0 [/start map] [boss map] 1 0 [/boss map] [seal door map index] 60067 [seal door pos] 3 0 [escape hell] 1";
        assertThrows(IOException.class, () -> parse(text).resize(0,2,1,false));
        String cropped = parse(text).resize(0,2,1,true).applyTo(text);
        assertNull(parse(cropped).hell(0).position());
    }
}
