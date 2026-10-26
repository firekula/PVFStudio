package studio.pvf.sceneedit;
import org.junit.jupiter.api.Test;
import studio.pvf.preview.*;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class FinalReviewRegressionTest {
 @Test void previewAndEditorKeepIdenticalMonsterBoundariesAcrossNativeVariants()throws Exception{
  String[] records={"51 1 0 100 120 0 1 1 `[fixed]` `[champion]` 1 77","52 1 0 200 120 0 1 1 `[fixed]` `[normal]`","53 1 0 300 120 0 1 1 `[fixed]` `[super champion]` 2 8 9","54 1 0 400 120 0 1 1 `[fixed]` `[npc]` 100 `[champion]` 1 5","55 1 0 500 120 0 1 1 `[fixed]` `[boss]`"};
  for(String separator:List.of(" ","\n")){String text="[monster] "+String.join(separator,records)+" [/monster]";var model=MapDocument.parse("map/a.map",text);var preview=EntityScript.placements(text);assertTrue(preview.warnings().isEmpty(),preview.warnings().toString());assertEquals(5,preview.spawns().size());
   for(int i=0;i<5;i++){assertEquals(model.entries().get(i).definitionId(),preview.spawns().get(i).id());assertEquals(model.entries().get(i).id().recordOrdinal(),preview.spawns().get(i).source().recordOrdinal());}
  }
 }
 @Test void repeatedMonsterSectionsKeepAiAttachedToTheirMonstersAfterAdd()throws Exception{
  String text="[monster] 51 1 0 1 2 3 1 1 `[fixed]` `[normal]` [/monster] [monster] 52 1 0 4 5 6 1 1 `[fixed]` `[normal]` [/monster] [monster specific AI] `a` `b` [/monster specific AI]";
  var model=MapDocument.parse("map/a.map",text);String added=model.addEntity(EntityScript.Kind.MONSTER,53,7,8,9).applyTo(text);var after=MapDocument.parse("map/a.map",added);var ai=after.source().sections("monster specific ai").get(0).values();var pairs=new HashMap<Integer,String>();for(int i=0;i<after.entries().size();i++)pairs.put(after.entries().get(i).definitionId(),ai.get(i).value());assertEquals("a",pairs.get(51));assertEquals("b",pairs.get(52));assertEquals("[normal]",pairs.get(53));
 }
 @Test void unsupportedTileSpanBlocksEveryStructuralTileCommand()throws Exception{
  String text="[tile] `a.til` @9(7) `b.til` [/tile]";var model=MapDocument.parse("map/a.map",text);assertFalse(model.diagnostics().isEmpty());
  assertThrows(IOException.class,() -> model.replaceTileGrid(List.of("a.til","new.til"),List.of()));assertThrows(IOException.class,() -> model.moveTileColumn(0,0));assertThrows(IOException.class,() -> model.replaceTiles(false,List.of("new.til")));assertEquals(text,model.source().text());
 }
 @Test void copiedFirstMazeMaterializesItsEffectivePrefixHellFields()throws Exception{
  String prefix="[hell dungeon] 1 [hit count] 1 2 3 4 5 [seal door map index] 60001 [seal door pos] 0 0 [escape hell] 1 ";String first="[maze info] [size] 1 1 [greed] `AA` [start map] 0 0 [/start map] ";String second="[maze info] [size] 1 1 [greed] `AA` [seal door map index] 60002 [seal door pos] 0 0 [escape hell] 0";String text=prefix+first+second;var before=DungeonDocument.parse("dungeon/a.dgn",text);String copied=before.copyMaze(0).applyTo(text);var after=DungeonDocument.parse("dungeon/a.dgn",copied);assertEquals(before.hell(0),after.hell(1));assertEquals(before.hell(1),after.hell(2));assertTrue(copied.startsWith(prefix+first));
 }
}
