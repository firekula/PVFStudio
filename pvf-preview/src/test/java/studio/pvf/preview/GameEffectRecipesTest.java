package studio.pvf.preview;
import java.util.List;
import java.util.Map;
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

 @Test void blockbusterUsesNativeMotionIndexAndKeepsTheBrokenBrickBehindTheActor() throws Exception {
  String skill = "skill/fighter/blockbuster.skl";
  Map<String,String> resources = Map.of("skill/fighterskill.lst", "60 `Fighter/BlockBuster.skl`",
          "character/fighter/animation/edited.ani", "[FRAME MAX] 2 [FRAME000] [DELAY] 120 [FRAME001] [DELAY] 180");
  Map<String,String> actions = Map.of("etc motion:17", "Animation/Edited.ani", "etc motion:2", "Animation/BlockBuster.ani");

  var cast = GameEffectRecipes.fighterCast(skill, resources::get, actions, true);

  assertNotNull(cast);
  assertEquals("etc motion:17", cast.action());
  assertEquals(List.of("blockbusterblock2.ani", "blockbusterblock1.ani", "blockbusterglow.ani"),
          cast.clips().stream().map(clip -> clip.path().substring(clip.path().lastIndexOf('/')+1)).toList());
  assertEquals(List.of(-1, 1, 2), cast.clips().stream().map(GameEffectRecipes.Attachment::layer).toList());
  assertTrue(cast.clips().stream().allMatch(clip -> clip.start()==0 && clip.end()==300 && clip.x()==0 && clip.y()==0));
  assertTrue(cast.clips().get(0).renderLayer()<1000);
  assertTrue(cast.clips().get(2).renderLayer()>2000);
 }

 @Test void fighterCastRequiresTheAct1CatalogBindingAndMotionIndex() throws Exception {
  String skill = "skill/fighter/blockbuster.skl";
  Map<String,String> actions = Map.of("etc motion:17", "Animation/BlockBuster.ani");
  Map<String,String> resources = Map.of("skill/fighterskill.lst", "60 `Fighter/BlockBuster.skl`");
  Map<String,String> shifted = Map.of("skill/fighterskill.lst", "61 `Fighter/BlockBuster.skl`");

  assertNull(GameEffectRecipes.fighterCast(skill, resources::get, actions, false));
  assertNull(GameEffectRecipes.fighterCast(skill, shifted::get, actions, true));
  assertNull(GameEffectRecipes.fighterCast(skill, resources::get, Map.of("etc motion:16", "Animation/BlockBuster.ani"), true));
 }

 @Test void fighterAttachmentsUseTheStateEntryResourcesAndOffsets() throws Exception {
  Map<String,String> resources = Map.of("skill/fighterskill.lst", """
          52 `Fighter/ThrowWeb.skl`
          58 `Fighter/WhirlWindKick.skl`
          71 `Fighter/StrongestLowKick.skl`
          75 `Fighter/HiddenSting.skl`
          77 `Fighter/JunkSpin.skl`
          """);
  Map<String,String> actions = Map.of("etc motion:16", "Animation/WhirlWindKick.ani", "etc motion:24", "Animation/StrongestLowKick.ani",
          "etc motion:29", "Animation/ThrowWeb.ani", "etc motion:30", "Animation/HiddenSting.ani", "etc motion:33", "Animation/JunkSpin.ani");
  var kick = GameEffectRecipes.fighterCast("skill/fighter/strongestlowkick.skl", resources::get, actions, true);
  var ready = kick.clips().get(0);
  assertEquals("etc motion:24", kick.action());
  assertEquals("character/fighter/effect/animation/strongestlowkick/ready.ani", ready.path());
  assertEquals(-22, ready.x());
  assertEquals(-45, ready.y());
  var whirlwind = GameEffectRecipes.fighterCast("skill/fighter/whirlwindkick.skl", resources::get, actions, true);
  assertEquals("character/fighter/effect/animation/whirlwindkick.ani", whirlwind.clips().get(0).path());
  var sting = GameEffectRecipes.fighterCast("skill/fighter/hiddensting.skl", resources::get, actions, true);
  assertEquals(List.of("character/fighter/effect/animation/hiddenstingpin.ani"), sting.clips().stream().map(GameEffectRecipes.Attachment::path).toList());
  var web = GameEffectRecipes.fighterCast("skill/fighter/throwweb.skl", resources::get, actions, true);
  assertEquals(List.of("character/fighter/effect/animation/throwweblineardodge.ani", "character/fighter/effect/animation/throwwebnormal.ani"),
          web.clips().stream().map(GameEffectRecipes.Attachment::path).toList());
  var spin = GameEffectRecipes.fighterCast("skill/fighter/junkspin.skl", resources::get, actions, true);
  assertEquals(List.of(-3,-2,-1,1,2), spin.clips().stream().map(GameEffectRecipes.Attachment::layer).toList());
 }
}
