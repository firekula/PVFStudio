package studio.pvf.preview;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Act1GunnerAdvancedFireTest {
    private static String ani(int... times){var b=new StringBuilder();for(int i=0;i<times.length;i++)b.append("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d ".formatted(i,times[i]));return b.toString();}
    private static Map<String,String> actions(){return Map.of("waiting motion","Animation/Stay.ani","etc motion:15","Animation/SubWeaponFrontOnce1.ani","etc motion:16","Animation/SubWeaponFrontOnce2.ani");}
    private static HashMap<String,String> data(){var f=new HashMap<String,String>();f.put("skill/gunnerskill.lst","40 `gunner/steyrheavyrifle.skl`");f.put("character/gunner/animation/subweaponfrontonce1.ani",ani(100,100,100,50,50));f.put("character/gunner/animation/subweaponfrontonce2.ani",ani(100,50,250));f.put("character/gunner/animation/stay.ani",ani(100));for(int i=1;i<=2;i++)for(String c:List.of("a","b"))f.put("character/gunner/effect/animation/subweapon/subweaponfrontonce"+i+c+".ani",ani(100,100,100,50,50));f.put("character/gunner/effect/animation/shooteffect/steyrshoot.ani",ani(50,50,50));f.put("character/gunner/particle/subweapon/steyr.ptl","[x axis] [move type] `[straight line]` [move variable 1] 800 800 [life time] 1000 [maximum create number] 1 [creation frequency] -1 [object type] `[passive object]` [object] `` 22203 [/object]");f.put("passiveobject/passiveobject.lst","22203 `character/gunner/subweaponbulletsteyr.obj` 22218 `character/gunner/steyrfireexplosion.obj`");f.put("passiveobject/character/gunner/steyrfireexplosion.obj","[basic motion] `../../Common/Animation/FireExplosion.ani`");f.put("passiveobject/common/animation/fireexplosion.ani",ani(400));f.put("passiveobject/character/gunner/subweaponbulletsteyr.obj","[basic motion] `Animation/SubWeaponBulletSteyr.ani` [etc motion] `Animation/SubWeaponBulletSteyrHit.ani` [/etc motion]");f.put("passiveobject/character/gunner/animation/subweaponbulletsteyr.ani",ani(10000));f.put("passiveobject/character/gunner/animation/subweaponbulletsteyrhit.ani",ani(1000));f.put("character/gunner/effect/animation/hiteffect/steyrhit.ani",ani(60,60,60,60,60));for(int i=1;i<=4;i++)f.put("passiveobject/common/particle/fireexplosionparticle"+i+".ptl","[life time] 500 [object type] `[animation]` [object] `../Animation/FireExplosionParticle"+i+".ani` -1 [/object]");return f;}
    @Test void steyrMissUsesNativeOriginAndHasNoInventedExplosion()throws Exception{var f=data();var s=Act1GunnerAdvancedFire.find("skill/gunner/steyrheavyrifle.skl","",f::get,actions(),1,Act1GunnerAdvancedFire.MISS,null);assertNotNull(s);assertEquals(400,s.emitters().get(0).start());assertEquals(150,s.emitters().get(0).x());assertEquals(85,s.emitters().get(0).z());assertEquals(1,s.emitters().size());assertTrue(s.visuals().stream().noneMatch(v->v.label().contains("命中")));assertEquals(List.of(4,0),s.visuals().get(0).variations());}
    @Test void steyrScriptedHitShowsTheTargetAndWaitsForTheLevelFuse()throws Exception{
        var f=data();
        for(String motion:List.of("stay","damage1","overturn"))f.put("monster/goblin/animation_goblin/"+motion+".ani",ani(100));
        var s=Act1GunnerAdvancedFire.find("skill/gunner/steyrheavyrifle.skl","[level info] 3 700 1000 300 800 1100 250 [/level info]",f::get,actions(),2,"",null);
        assertNotNull(s);
        var lodged=s.visuals().stream().filter(v->v.label().contains("嵌入")).findFirst().orElseThrow();
        assertEquals(700,lodged.start());
        assertEquals(950,lodged.end());
        assertEquals(950,s.emitters().get(0).start());
        assertTrue(s.visuals().stream().anyMatch(v->v.path().endsWith("/damage1.ani")&&v.x()==lodged.x()&&v.start()==700));
        assertTrue(s.visuals().stream().anyMatch(v->v.path().endsWith("/overturn.ani")&&v.x()==lodged.x()&&v.start()==950));
    }
    @Test void steyrExplosionUsesOriginalRootClockAndKeepsFiniteParticleCounts()throws Exception{
        var f=data();var s=Act1GunnerAdvancedFire.find("skill/gunner/steyrheavyrifle.skl","[level info] 3 700 1000 300 [/level info]",f::get,actions(),1,"",null);assertNotNull(s);assertEquals(4,s.emitters().size());
        for(var e:s.emitters()){assertEquals(1000,e.start());assertEquals(400,e.emitFor());assertEquals(0,e.frequency());assertEquals(390,e.x());assertEquals(40,e.z());}
        f.put("passiveobject/common/animation/fireexplosion.ani",ani(250));
        s=Act1GunnerAdvancedFire.find("skill/gunner/steyrheavyrifle.skl","[level info] 3 700 1000 300 [/level info]",f::get,actions(),1,"",null);assertNotNull(s);assertEquals(250,s.emitters().get(0).emitFor());
        f.put("passiveobject/passiveobject.lst","22203 `character/gunner/subweaponbulletsteyr.obj` 22218 `character/gunner/foreign.obj`");
        assertNull(Act1GunnerAdvancedFire.find("skill/gunner/steyrheavyrifle.skl","[level info] 3 700 1000 300 [/level info]",f::get,actions(),1,"",null));
        assertNotNull(Act1GunnerAdvancedFire.find("skill/gunner/steyrheavyrifle.skl","",f::get,actions(),1,Act1GunnerAdvancedFire.MISS,null));
    }
    @Test void foreignSkillChoiceAndMissingObjectFailClosed()throws Exception{var f=data();assertNull(Act1GunnerAdvancedFire.find("skill/gunner/randomshoot.skl","",f::get,actions(),1,Act1GunnerAdvancedFire.MISS,null));f.put("passiveobject/passiveobject.lst","22203 `character/gunner/foreign.obj`");assertNull(Act1GunnerAdvancedFire.find("skill/gunner/steyrheavyrifle.skl","",f::get,actions(),1,Act1GunnerAdvancedFire.MISS,null));}
    @Test void nativeNapalmPitchAppliesOnlyToItsFlightTailAndGroundIsSingleObject()throws Exception{var p=ParticleScript.parse("[life time] 800 [object type] `[animation]`");assertEquals(-30,Act1GunnerAdvancedFire.particleScript("passiveobject/character/gunner/particle/napalmbombfire.ptl",p).angles().y());assertEquals(1,Act1GunnerAdvancedFire.particleScript("passiveobject/character/gunner/particle/napalmbombareafirelight.ptl",p).maximum());assertSame(p,Act1GunnerAdvancedFire.particleScript("passiveobject/other.ptl",p));}
    @Test void napalmShowsOriginalFlamePixelsWithAuthoredOpacityAndBlend() {
        String path="passiveobject/character/gunner/animation/napalmbombfirelight1.ani";
        var stock=new ResourceScript.Frame("fire.img",0,0,0,1,1,0,0,100,200,170,100,1,"LINEARDODGE");
        var changed=new ResourceScript.Frame("fire.img",0,0,0,1,1,0,80,120,160,170,100,1,"LINEARDODGE");
        var animation=new ResourceScript.Animation(false,List.of(stock,changed),List.of());
        var result=Act1GunnerAdvancedFire.particleVisual(path,animation);
        var frame=result.frames().get(0);
        assertEquals(List.of(255,255,255,170),List.of(frame.r(),frame.g(),frame.b(),frame.a()));
        assertEquals("LINEARDODGE",frame.effect());
        assertSame(changed,result.frames().get(1));
        assertSame(stock,Act1GunnerAdvancedFire.napalmFrame("other/fire.ani",stock));
    }
    @Test void randomFourWayPoseRangesNeverReplayOtherDirections()throws Exception{var f=data();f.put("skill/gunnerskill.lst","9 `gunner/randomshoot.skl`");var a=new HashMap<>(actions());a.put("etc motion:13","Animation/RandomShoot.ani");int[] delays=new int[19];Arrays.fill(delays,150);f.put("character/gunner/animation/randomshoot.ani",ani(delays));for(int i=1;i<=2;i++)f.put("character/gunner/effect/animation/shooteffect/randomshoot"+i+".ani",ani(delays));f.put("character/gunner/effect/animation/bullet/randomshoot.ani",ani(200));var s=Act1GunnerAdvancedFire.find("skill/gunner/randomshoot.skl","[static data] 250 [/static data] [level info] 3 130 12 20 [/level info]",f::get,a,1,"",null);assertNotNull(s);assertEquals(4,s.poses().size());assertEquals(3000,s.poses().get(3).end());assertEquals(4,s.poses().get(1).firstFrame());assertEquals(9,s.poses().get(1).endFrame());var second=s.visuals().get(2);assertEquals(0,second.style().frameOverrides().get(0).delay());assertEquals(150,second.style().frameOverrides().get(4).delay());assertTrue(s.note().contains("没有图像"));}
    @Test void desperadoSpawnsTwoOriginalBulletVisualsPerShotAndBurstsAtTheTarget() throws Exception {
        var a=new HashMap<>(actions());
        var f=data();
        f.put("skill/gunnerskill.lst","64 `gunner/desperado.skl`");
        for(int id:new int[]{26,35,36,37,38,39,40}){
            a.put("etc motion:"+id,"Animation/fixture"+id+".ani");
            int[] delays=new int[id==35?10:id==37?14:id==40?5:4];
            Arrays.fill(delays,100);
            f.put("character/gunner/animation/fixture"+id+".ani",ani(delays));
        }
        GameScript.Lookup lookup=p->f.containsKey(p)?f.get(p):p.endsWith(".ani")?ani(25,25):null;
        var sequence=Act1GunnerAdvancedFire.find("skill/gunner/desperado.skl",
                "[static data] 60 60 250 10 200 70 140 500 [/static data] [level info] 9 70 0 20 30 105 -400 14 21 594 [/level info]",
                lookup,a,1,"",null);
        assertNotNull(sequence);
        var bullets=sequence.visuals().stream().filter(v->v.path().endsWith("/bullet/desperadoeffect.ani")).toList();
        assertEquals(68,bullets.size());
        assertEquals(bullets.get(0).start(),bullets.get(1).start());
        assertEquals(0,bullets.get(0).style().frameOverrides().get(0).delay());
        assertEquals(30,bullets.get(1).style().frameOverrides().get(0).delay());
        var burst=sequence.visuals().stream().filter(v->v.path().endsWith("/desperado/burst.ani")).findFirst().orElseThrow();
        assertTrue(sequence.visuals().stream().anyMatch(v->v.path().endsWith("/overturn.ani")&&v.x()==burst.x()&&v.start()==burst.start()));
        assertTrue(burst.y()<0);
    }
    @Test void desperadoAirFallsBeforeFinishAndNeverPlaysTenSecondSentinel()throws Exception{var a=new HashMap<>(actions());var f=data();f.put("skill/gunnerskill.lst","64 `gunner/desperado.skl`");for(int id:new int[]{26,35,36,37,38,39,40}){a.put("etc motion:"+id,"Animation/fixture"+id+".ani");int[] delays=new int[id==35?10:id==37?14:id==40?5:4];Arrays.fill(delays,100);if(id==40)delays[3]=10000;f.put("character/gunner/animation/fixture"+id+".ani",ani(delays));}GameScript.Lookup l=p->f.containsKey(p)?f.get(p):p.endsWith(".ani")?ani(100):null;var s=Act1GunnerAdvancedFire.find("skill/gunner/desperado.skl","[static data] 60 60 250 10 200 70 140 500 [/static data] [level info] 9 70 0 20 30 105 -400 14 21 594 [/level info]",l,a,1,"",null);assertNotNull(s);var falling=s.poses().stream().filter(p->p.action().equals("etc motion:40")&&p.firstFrame()==0).findFirst().orElseThrow();assertEquals(-1500,falling.motion().verticalAcceleration());assertEquals(200,falling.motion().heightAt(falling.start()));assertEquals(0,falling.motion().heightAt(falling.end()));assertTrue(falling.end()-falling.start()<1000);assertEquals("etc motion:39",s.poses().get(s.poses().size()-1).action());}
}
