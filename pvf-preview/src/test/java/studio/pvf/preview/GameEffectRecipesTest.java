package studio.pvf.preview;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameEffectRecipesTest {
 @Test void laserDelayAndRangeComeFromCurrentScriptAndOtherVersionsDoNotActivateRecipe(){
  var laser=GameEffectRecipes.laser("skill/gunner/laserrifle.skl","[static data] 300 900 [/static data]",true,false);
  assertNotNull(laser);assertEquals(300,laser.fireAt());assertEquals(900,laser.range());
  assertEquals(15,laser.tileCount());assertEquals(750,laser.endOffset());
  var edited=GameEffectRecipes.laser("skill/gunner/laserrifle.skl","[static data] 480 600 [/static data]",true,false);
  assertEquals(480,edited.fireAt());assertEquals(600,edited.range());
  assertNull(GameEffectRecipes.laser("skill/gunner/laserrifle.skl","[static data] 300 900",false,false));
  assertNull(GameEffectRecipes.laser("skill/gunner/other.skl","[static data] 300 900",true,false));
 }
 @Test void fullyChargedBranchReadsPassiveSkillTimeAndRangeBonus(){
  var laser=GameEffectRecipes.laser("skill/gunner/laserrifle.skl","[static data] 300 900",true,true,"[static data] 200 [level info] 2 1000 40");
  assertEquals(1000,laser.fireAt());assertEquals(1100,laser.range());
  var edited=GameEffectRecipes.laser("skill/gunner/laserrifle.skl","[static data] 300 900",true,true,"[static data] 150 [level info] 2 700 40");
  assertEquals(700,edited.fireAt());assertEquals(1050,edited.range());
 }
}
