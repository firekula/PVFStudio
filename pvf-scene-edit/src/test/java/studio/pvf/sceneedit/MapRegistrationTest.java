package studio.pvf.sceneedit;

import org.junit.jupiter.api.Test;
import studio.pvf.preview.DungeonScript.Position;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MapRegistrationTest {
    private static final String DGN="[maze info] [size] 2 1 [greed] `BBEE` [map specification] 0 0 1 [/map specification] [start map] 0 0 [/start map] [boss map] 1 0 [/boss map]";
    private static MapRegistration.Request request(String destination,Integer id) {
        return new MapRegistration.Request(Set.of("map/foo/a.map","map/map.lst","dungeon/a.dgn"),"map/foo/a.map",destination,
                "source bytes".getBytes(StandardCharsets.US_ASCII),"unsaved source text","#PVF_File\n1 `foo/a.map`\n//2 `commented.map`\n3 `elsewhere.map`\n",
                "dungeon/a.dgn",DGN,0,new Position(0,0),false,id,65535);
    }
    @Test void preparesUnusedIdRelativeListReferenceAndRoomBindingFromUnsavedSource() throws Exception {
        var prepared=MapRegistration.prepare(request("map/foo/copy.map",null));
        assertEquals(2,prepared.mapId()); assertEquals("unsaved source text",prepared.mapText());
        assertTrue(prepared.listEdit().applyTo(request("map/foo/copy.map",null).mapListText()).endsWith("2\t`foo/copy.map`\n"));
        var dgn=DungeonDocument.parse("dungeon/a.dgn",prepared.dungeonEdit().applyTo(DGN));
        assertEquals(List.of(2),dgn.mazes().get(0).layout().maps().get(new Position(0,0)));
        assertArrayEquals("source bytes".getBytes(StandardCharsets.US_ASCII),prepared.mapBytes());
    }
    @Test void hellCloneBindsOnlyTheSealMapAndPreservesOrdinaryAndBossCandidates() throws Exception {
        var old=request("map/foo/copy.map",null);String text="[hell dungeon] 1 "+DGN+" [seal door map index] 1 [seal door pos] 0 0";
        var r=new MapRegistration.Request(old.paths(),old.sourcePath(),old.destinationPath(),old.sourceBytes(),old.sourceText(),old.mapListText(),old.dungeonPath(),text,0,old.room(),false,null,65535,true);
        var prepared=MapRegistration.prepare(r);var after=DungeonDocument.parse(old.dungeonPath(),prepared.dungeonEdit().applyTo(text));
        assertEquals(2,after.hell(0).mapId());assertEquals(List.of(1),after.mazes().get(0).layout().maps().get(old.room()));
    }
    @Test void rejectsExistingPathDuplicateIdAndCrossDirectoryReferencesBeforeAnyMutation() {
        assertThrows(IOException.class,()->MapRegistration.prepare(request("MAP/FOO/A.MAP",null)));
        assertThrows(IOException.class,()->MapRegistration.prepare(request("map/foo/copy.map",1)));
        assertThrows(IOException.class,()->MapRegistration.prepare(request("map/other/copy.map",null)));
        assertThrows(IOException.class,()->MapRegistration.prepare(request("../copy.map",null)));
    }
}
