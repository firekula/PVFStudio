package studio.pvf.sceneedit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DungeonMapChecksTest {
 @Test void bossRoleAndMissingGateCoordinatesProduceSourceSpecificWarnings() throws Exception {
  var check=DungeonMapChecks.inspect("map/a.map","[type] `normal`",true,1);
  assertTrue(check.messages().stream().anyMatch(m -> m.contains("Boss")));assertFalse(check.hasGate(0));
  var valid=DungeonMapChecks.inspect("map/b.map","[pathgate pos] 1 2 3 4 5 6 7 8",false,0);
  assertTrue(valid.hasGate(0));assertEquals("map/a.map",check.path());
 }
}
