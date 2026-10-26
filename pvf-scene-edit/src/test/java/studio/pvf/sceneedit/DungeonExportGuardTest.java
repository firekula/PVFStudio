package studio.pvf.sceneedit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import studio.pvf.preview.DungeonScript.Position;
import java.util.List;
class DungeonExportGuardTest {
 @Test void onlyNewUnreachableRoomsBlockExportWhileSingleAaStartIsValid()throws Exception{
  String text="[maze info] [size] 2 1 [greed] `AAAA` [start map] 0 0 [/start map]";
  var before=DungeonDocument.parse("dungeon/a.dgn",text);assertTrue(before.newRoomErrors(before).isEmpty());
  var after=DungeonDocument.parse("dungeon/a.dgn",before.createRoom(0,new Position(1,0),List.of(1)).applyTo(text));assertFalse(after.newRoomErrors(before).isEmpty());
 }
}
