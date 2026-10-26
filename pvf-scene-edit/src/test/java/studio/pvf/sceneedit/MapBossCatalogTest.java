package studio.pvf.sceneedit;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapBossCatalogTest {
 @Test void cachesImmutableArchiveButRechecksUnsavedAndDraftedMapSnapshots()throws Exception{
  var files=new HashMap<String,String>();files.put("map/map.lst","1 `a.map` 2 `b.map`");files.put("map/a.map","[type] `[boss]`");files.put("map/b.map","[monster] 1 0 1 0 0 0 1 1 `[fixed]` `[boss]` [/monster]");
  var catalog=SceneCatalog.build(files.keySet(),files::get);var cache=new MapBossCatalog();int[] reads={0};SceneCatalog.TextLookup lookup=path -> {reads[0]++;return files.get(path);};
  assertEquals(Map.of(1,MapBossContent.NONE,2,MapBossContent.BOSS),cache.classify(catalog,lookup,Set.of()));assertEquals(2,reads[0]);
  cache.classify(catalog,lookup,Set.of());assertEquals(2,reads[0]);
  files.put("map/a.map",files.get("map/b.map"));assertEquals(MapBossContent.BOSS,cache.classify(catalog,lookup,Set.of("map/a.map")).get(1));assertEquals(3,reads[0]);
  assertEquals(MapBossContent.NONE,cache.classify(catalog,lookup,Set.of()).get(1),"Undo/discard must restore cached original classification");
 }
}
