package studio.pvf.intellij;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class SceneDocumentReconciliationTest {
 @Test void lateOpenedPathsAreIncludedAndLaterEditsRejectBeforeAnyMutation()throws Exception{
  var before=Map.of("map/map.lst","old list","dungeon/a.dgn","old dgn");var after=Map.of("map/map.lst","new list","dungeon/a.dgn","new dgn","map/copy.map","new map");
  assertEquals(Map.of("map/map.lst","old list"),SceneDocumentReconciliation.targets(before,after,Map.of("map/map.lst","new list"),false));
  assertEquals(Map.of("map/map.lst","new list"),SceneDocumentReconciliation.targets(before,after,Map.of("map/map.lst","old list"),true));
  assertThrows(IOException.class,() -> SceneDocumentReconciliation.targets(before,after,Map.of("map/map.lst","new list + unsaved user change"),false));
 }
}
