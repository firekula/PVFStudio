package studio.pvf.intellij;
import org.junit.jupiter.api.Test;
import studio.pvf.preview.*;
import studio.pvf.sceneedit.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DungeonEditorPreviewTest {
 @Test void secondBossCandidateUsesItsBossMapForPreviewNavigationAndClone()throws Exception{
  String text="[maze info] [size] 3 1 [greed] `BBFFEE` [start map] 0 0 [/start map] [boss map] 1 0 2 0 [/boss map] [map specification] 1 0 1 [/map specification] [map specification] 2 0 1 [/map specification] [boss map specification] 1 0 2 [/boss map specification] [boss map specification] 2 0 3 [/boss map specification]";
  var files=Map.of("map/map.lst","1 `a.map` 2 `b.map` 3 `c.map`","map/a.map","[type] `[normal]`","map/b.map","[type] `[boss]`","map/c.map","[type] `[boss]`");var dungeon=DungeonPreview.load("dungeon/a.dgn",text,files::get);var position=new DungeonScript.Position(2,0);
  var selected=DungeonEditorPreview.resolve(dungeon,new DungeonEditorPanel.Preview(0,position,3,false));assertTrue(selected.room().boss());assertEquals(3,selected.map().id());assertEquals("map/c.map",selected.map().path());
  var r=new MapRegistration.Request(files.keySet(),selected.map().path(),"map/copy.map",new byte[0],files.get(selected.map().path()),files.get("map/map.lst"),"dungeon/a.dgn",text,0,position,selected.room().boss(),null,65535);var prepared=MapRegistration.prepare(r);var changed=DungeonDocument.parse("dungeon/a.dgn",prepared.dungeonEdit().applyTo(text));assertEquals(List.of(4),changed.mazes().getFirst().layout().bossMaps().get(position));assertEquals(List.of(1),changed.mazes().getFirst().layout().maps().get(position));
 }
}
