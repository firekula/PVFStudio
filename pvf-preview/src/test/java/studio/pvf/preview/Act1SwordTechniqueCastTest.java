package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class Act1SwordTechniqueCastTest {
    private static final String ROOT="character/swordman/", EFFECT=ROOT+"effect/animation/";
    private static final Map<String,String> ACTIONS=Map.of("etc motion:8","Animation/MomentarySlash.ani","etc motion:14","Animation/VaneSlashTry.ani","etc motion:15","Animation/VaneSlash.ani","waiting motion","Animation/Stay.ani");
    private static String ani(int... delays){var b=new StringBuilder();for(int i=0;i<delays.length;i++)b.append("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d\n".formatted(i,delays[i]));return b.toString();}
    private static HashMap<String,String> fixture(){
        var f=new HashMap<String,String>();
        f.put("skill/swordmanskill.lst","9 `swordman/momentaryslash.skl` 58 `swordman/vaneslash.skl`");
        f.put(ROOT+"animation/momentaryslash.ani",ani(500,25,25,25,50,50,50,50,50,50,30,150));
        f.put(ROOT+"animation/vaneslashtry.ani",ani(70,70,70,70,70));
        f.put(ROOT+"animation/vaneslash.ani",ani(70,70,140,70,70,70,1000));
        f.put(EFFECT+"vaneslash/upper.ani",ani(70,70,70,70,70));
        f.put(EFFECT+"vaneslash/dust.ani",ani(70,70,70,70,70,70));
        f.put(EFFECT+"vaneslash/smash.ani",ani(70,70,140,70,70,70,70,70));
        for(String color:new String[]{"none","blue_ldodge","white_ldodge","red_ldodge"})for(String side:new String[]{"under","upper"})f.put(EFFECT+"momentaryslash/momentaryslash_"+color+"_"+side+".ani",ani(25,25,50,50,50));
        f.put("data/skill/swordman.sd","[int] `vs hit x offset` 121 `vs hit z offset` 80 [/int]");
        f.put("passiveobject/passiveobject.lst","20022 `character/swordman/vaneslash.obj`");
        f.put("passiveobject/character/swordman/vaneslash.obj","[basic motion] `Animation/VaneSlash.ani` [etc motion] `Animation/VaneSlashNormal.ani` [/etc motion]");
        f.put("passiveobject/character/swordman/animation/vaneslash.ani",ani(70,70,70,70,70,70,70,70,70,70,70,70,10000,70,70,70,70,70,70));
        f.put("passiveobject/character/swordman/animation/vaneslashnormal.ani",ani(70,70,70,70));
        return f;
    }
    @Test void singleSlashStartsFourLayersAtBodyFrameTwo()throws Exception{
        var s=Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.MOMENTARY_PATH,"",fixture()::get,ACTIONS,1,"");
        assertNotNull(s);assertEquals(1055,s.poses().get(0).end());assertEquals(4,s.visuals().size());
        assertTrue(s.visuals().stream().allMatch(v->v.start()==525));
        assertEquals(2,s.visuals().stream().filter(v->v.path().contains("blue_ldodge")).count());
        assertTrue(s.visuals().get(0).layer()<3500);assertTrue(s.visuals().get(2).layer()>3500);
    }
    @Test void followupReentersFrameOneAtEarliestNativeInputWindow()throws Exception{
        for(String choice:new String[]{Act1SwordTechniqueCast.KATANA,Act1SwordTechniqueCast.LIGHTSABER}){
            var s=Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.MOMENTARY_PATH,"",fixture()::get,ACTIONS,30,choice);
            assertEquals(775,s.poses().get(0).end());assertEquals(1,s.poses().get(1).firstFrame());assertEquals(1330,s.poses().get(1).end());
            assertEquals(8,s.visuals().size());assertTrue(s.visuals().subList(4,8).stream().allMatch(v->v.start()==800));
            String color=choice.equals(Act1SwordTechniqueCast.KATANA)?"red_ldodge":"white_ldodge";
            assertEquals(2,s.visuals().stream().filter(v->v.path().contains(color)).count());
            assertTrue(s.note().contains("精通"));
        }
    }
    @Test void vaneMissEndsAfterTryWithoutSmashOrBloodWave()throws Exception{
        var f=fixture();f.remove("passiveobject/passiveobject.lst");
        var s=Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.VANE_PATH,"",f::get,ACTIONS,1,Act1SwordTechniqueCast.VANE_MISS);
        assertNotNull(s);assertEquals(350,s.poses().get(0).end());assertEquals(2,s.visuals().size());
        assertTrue(s.visuals().stream().allMatch(v->v.end()<=350));
    }
    @Test void vaneHitLoopsMiddleThenReleasesBeforeActorRecovers()throws Exception{
        var s=Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.VANE_PATH,"",fixture()::get,ACTIONS,1,"");
        assertNotNull(s);assertEquals(350,s.poses().get(1).start());assertEquals(1840,s.poses().get(1).end());
        var wave=s.visuals().stream().filter(v->v.label().equals("裂波斩 · 裂波起势")).findFirst().orElseThrow();
        assertEquals(350,wave.start());assertEquals(1190,wave.end());assertEquals(121,wave.x());assertEquals(-79,wave.y());
        var loop=s.visuals().stream().filter(v->v.label().equals("裂波斩 · 裂波旋转")).findFirst().orElseThrow();
        assertEquals(1190,loop.start());assertEquals(1420,loop.end());assertEquals(210,loop.style().loopPeriod());
        var tail=s.visuals().stream().filter(v->v.label().equals("裂波斩 · 裂波消散")).findFirst().orElseThrow();
        assertEquals(1420,tail.start());assertEquals(1840,tail.end());assertEquals(0,tail.style().frameOverrides().get(12).delay());
    }
    @Test void vaneTargetIsLiftedToTheWaveOnlyOnTheHitBranch() throws Exception {
        var files = fixture();
        for (String animation : java.util.List.of("stay", "overturn", "down")) {
            files.put(Act1SwordAsuraSupplement.GOBLIN + animation + ".ani", ani(70));
        }
        var hit = Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.VANE_PATH, "", files::get, ACTIONS, 1, "");
        var miss = Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.VANE_PATH, "", files::get, ACTIONS, 1, Act1SwordTechniqueCast.VANE_MISS);
        var caught = hit.visuals().stream().filter(v -> v.label().endsWith("空中束缚")).findFirst().orElseThrow();
        var impact = hit.visuals().stream().filter(v -> v.label().endsWith("裂波受击")).findFirst().orElseThrow();
        assertEquals(630, impact.start());
        assertEquals(impact.x(), caught.x() + caught.motion().offsetAt(impact.start()), 0.01);
        assertEquals(impact.motion().heightAt(impact.start()), caught.motion().heightAt(caught.end()), 0.01);
        assertTrue(miss.visuals().stream().noneMatch(v -> v.path().startsWith(Act1SwordAsuraSupplement.GOBLIN)));
    }
    @Test void editedVaneCoordinatesAreReadFromDataAndInvalidHoldIsRejected()throws Exception{
        var f=fixture();f.put("data/skill/swordman.sd","[int] `vs hit x offset` 140 `vs hit z offset` 90 [/int]");
        var s=Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.VANE_PATH,"",f::get,ACTIONS,1,"");
        assertTrue(s.visuals().stream().filter(v->v.path().startsWith("passiveobject/")).allMatch(v->v.x()==140&&v.y()==-89));
        f.put(ROOT+"animation/vaneslash.ani",ani(1,1,1,1,1,1,1));
        assertNull(Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.VANE_PATH,"",f::get,ACTIONS,1,""));
    }
    @Test void vaneDownslashPreservesNativePerFrameProportions()throws Exception{
        var s=Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.VANE_PATH,"",fixture()::get,ACTIONS,1,"");
        var slash=s.visuals().stream().filter(v->v.path().endsWith("/smash.ani")).findFirst().orElseThrow();
        assertEquals(8,slash.style().frameOverrides().size());
        assertEquals(.8,slash.style().frameOverrides().get(0).scaleX(),.001);
        assertEquals(1.1,slash.style().frameOverrides().get(3).scaleX(),.001);
        assertEquals(.88,slash.style().frameOverrides().get(3).scaleY(),.001);
        var f=fixture();f.put("equipment/heavy.equ","[equipment type] `[weapon]` 0 [sub type] 3");
        var heavy=Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.VANE_PATH,"",f::get,ACTIONS,1,"","equipment/heavy.equ");
        var h=heavy.visuals().stream().filter(v->v.path().endsWith("/smash.ani")).findFirst().orElseThrow();
        assertEquals(1.4,h.style().frameOverrides().get(3).scaleX(),.001);assertEquals(1.12,h.style().frameOverrides().get(3).scaleY(),.001);
    }
    @Test void sharedDispatcherPassesSelectedWeaponToVaneScale()throws Exception{
        var f=fixture();f.put("equipment/heavy.equ","[equipment type] `[weapon]` 0 [sub type] 3");
        var s=Act1SkillSequence.find(Act1SwordTechniqueCast.VANE_PATH,"",f::get,ACTIONS,1,"",true,Act1BuffCast::stockHeight,"equipment/heavy.equ");
        var slash=s.visuals().stream().filter(v->v.path().endsWith("/smash.ani")).findFirst().orElseThrow();
        assertEquals(1.4,slash.style().frameOverrides().get(3).scaleX(),.001);
    }
    @Test void choiceMetadataIsCatalogAndDialectGated()throws Exception{
        var f=fixture();
        assertEquals(java.util.List.of(Act1SwordTechniqueCast.VANE_MISS),Act1SwordTechniqueCast.choices(Act1SwordTechniqueCast.VANE_PATH,f::get,true).stream().map(GameEffectRecipes.Effect::key).toList());
        assertEquals(2,Act1SwordTechniqueCast.choices(Act1SwordTechniqueCast.MOMENTARY_PATH,f::get,true).size());
        assertTrue(Act1SwordTechniqueCast.choices(Act1SwordTechniqueCast.MOMENTARY_PATH,f::get,false).isEmpty());
        f.put("skill/swordmanskill.lst","9 `swordman/other.skl`");
        assertTrue(Act1SwordTechniqueCast.choices(Act1SwordTechniqueCast.MOMENTARY_PATH,f::get,true).isEmpty());
    }
    @Test void foreignBindingsMissingLayersAndUnrelatedChoicesFailClosed()throws Exception{
        var f=fixture();f.put("skill/swordmanskill.lst","9 `swordman/other.skl`");assertNull(Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.MOMENTARY_PATH,"",f::get,ACTIONS,1,""));
        f=fixture();f.remove(EFFECT+"momentaryslash/momentaryslash_none_under.ani");assertNull(Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.MOMENTARY_PATH,"",f::get,ACTIONS,1,""));
        assertNull(Act1SwordTechniqueCast.find(Act1SwordTechniqueCast.VANE_PATH,"",fixture()::get,ACTIONS,1,Act1SwordTechniqueCast.KATANA));
    }
    @Test void thrustLayersStartAtFrameOneAndTravelStopsAtZeroVelocity()throws Exception{
        var f=fixture();f.put("skill/swordmanskill.lst","11 `swordman/dashattackmultihit.skl`");
        f.put(ROOT+"animation/dashattackmultihit.ani",ani(100,100,60,60,60,60,30,30));
        f.put(EFFECT+"dashattackmultihit1.ani",ani(100,100,100));f.put(EFFECT+"dashattackmultihit2.ani",ani(100,100,100));
        var a=new HashMap<>(ACTIONS);a.put("etc motion:11","Animation/DashAttackMultiHit.ani");
        var s=Act1SwordTechniqueCast.find("skill/swordman/dashattackmultihit.skl","[static data] 300 -1000 500 -1000 [/static data]",f::get,a,1,"");
        assertNotNull(s);assertEquals(500,s.poses().get(0).end());assertEquals(2,s.visuals().size());
        assertTrue(s.visuals().stream().allMatch(v->v.start()==100));assertEquals(45,s.poses().get(0).motion().offsetAt(500),.001);
        assertEquals(25,s.visuals().get(0).motion().offsetAt(100),.001);assertTrue(s.note().contains("光剑"));
    }
}
