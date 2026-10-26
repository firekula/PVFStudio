package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import studio.pvf.sceneedit.MapRegistration;
import studio.pvf.preview.DungeonScript.Position;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class MapCloneCommandTest {
    private static MapRegistration.Request request() {
        return new MapRegistration.Request(Set.of("map/foo/source.map","map/map.lst","dungeon/a.dgn"),"map/foo/source.map","map/foo/copy.map",
                "original".getBytes(StandardCharsets.US_ASCII),"unsaved source", "1 `foo/source.map`", "dungeon/a.dgn",
                "[maze info] [size] 1 1 [greed] `AA` [start map] 0 0 [/start map] [boss map] -1 -1 [/boss map]",0,new Position(0,0),false,null,65535);
    }
    @Test void preparesAllThreeWritesAndDocumentsWithoutSavingTheSourceDocument() throws Exception {
        var request=request(); byte[] list={1,2},dungeon={3,4};
        var command=MapCloneCommand.prepare(request,StandardCharsets.US_ASCII,list,dungeon);
        assertEquals(3,command.changes().size());
        var newMap=command.changes().stream().filter(c->c.path().equals("map/foo/copy.map")).findFirst().orElseThrow();
        assertEquals("unsaved source",newMap.text()); assertNull(newMap.expectedBytes());
        assertArrayEquals(list,command.changes().stream().filter(c->c.path().equals("map/map.lst")).findFirst().orElseThrow().expectedBytes());
        assertFalse(command.documentTexts().containsKey("map/foo/source.map"));
        assertTrue(command.documentTexts().get("map/map.lst").contains("2\t`foo/copy.map`"));
        assertEquals("unsaved source",command.documentTexts().get("map/foo/copy.map"));
        list[0]=99;
        assertEquals(1,command.changes().stream().filter(c->c.path().equals("map/map.lst")).findFirst().orElseThrow().expectedBytes()[0]);
    }
    @Test void existingDocumentsRequireExpectedWorkspaceSnapshots() {
        assertThrows(IOException.class,()->MapCloneCommand.prepare(request(),StandardCharsets.US_ASCII,null,new byte[]{1}));
    }
}
