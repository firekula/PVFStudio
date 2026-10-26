package studio.pvf.sceneedit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapBossContentTest {
 @Test void usesMonsterRoleIncludingConditionalNpcAndChampionBoundaries(){
  assertEquals(MapBossContent.NONE,MapBossContent.inspect("[type] `[boss]` [monster] 1 0 1 0 0 0 1 1 `[fixed]` `[normal]` [/monster]"));
  assertEquals(MapBossContent.BOSS,MapBossContent.inspect("[type] `[normal]` [monster condition] 1 0 1 0 0 0 1 1 `[fixed]` `[champion]` 2 7 8 2 0 1 0 0 0 1 1 `[fixed]` `[boss]` [/monster condition]"));
  assertEquals(MapBossContent.BOSS,MapBossContent.inspect("[monster] 1 0 1 0 0 0 1 1 `[fixed]` `[npc]` 5 `[boss]` [/monster]"));
  assertEquals(MapBossContent.NONE,MapBossContent.inspect("[special passive object] 1 0 0 0 1 `[monster]` 5 1 1 1 1 [/special passive object]"));
 }
 @Test void incompleteUnknownAndMalformedSourcesDoNotBecomeOrdinaryChoices(){
  assertEquals(MapBossContent.UNKNOWN,MapBossContent.inspect("[monster] 1 0 1 [/monster]"));
  assertEquals(MapBossContent.UNKNOWN,MapBossContent.inspect("[monster] 1 0 1 0 0 0 1 1 `[fixed]` `[unknown]` [/monster]"));
  assertEquals(MapBossContent.UNKNOWN,MapBossContent.inspect(null));
  assertEquals(MapBossContent.NONE,MapBossContent.inspect("[type] `[normal]`"));
 }
}
