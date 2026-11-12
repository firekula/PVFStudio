package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Act1GunnerSupportCastTest {
    static final Map<String,String> ACTIONS=Map.of("waiting motion","Animation/Stay.ani","throw motion 2-1","Animation/Command1.ani","throw motion 2-2","Animation/Command2.ani");
    static String ani(){return "[FRAME000] [IMAGE] `fixture.img` 0 [DELAY] 100";}
    static HashMap<String,String> fixture(){var f=new HashMap<String,String>();f.put("skill/gunnerskill.lst","43 `gunner/camouflage.skl` 50 `gunner/robotsexplosion.skl` 45 `gunner/uraniumbomb.skl` 54 `gunner/satellitelaser.skl` 59 `gunner/mechadrop.skl`");for(String n:List.of("command1","command2","stay"))f.put("character/gunner/animation/"+n+".ani",ani());for(String n:List.of("camouflage","decamouflage"))f.put("common/commoneffect/animation/"+n+".ani",ani());for(int n=1;n<=3;n++)f.put("character/gunner/effect/animation/robotsexplosion"+n+".ani",ani());return f;}
    @Test void camouflageHasBoundedInvisiblePhaseAndRestoresAtActualExpiry()throws Exception{var f=fixture();var s=Act1GunnerSupportCast.find("skill/gunner/camouflage.skl","[casting time] 700 700 [static data] 500 [/static data] [level info] 1 6000 8000 [/level info]",f::get,ACTIONS,2,"");assertNotNull(s);var invisible=s.poses().stream().filter(p->!p.keyframes().isEmpty()).findFirst().orElseThrow();assertEquals(700,invisible.start());assertEquals(8700,invisible.end());assertEquals(0,invisible.keyframes().get(0).opacity());assertEquals(8700,s.visuals().get(1).start());assertTrue(s.note().contains("敌方"));}
    @Test void mechanicalRangeScalesRingsWithoutInventingRobotTargets()throws Exception{var f=fixture();var s=Act1GunnerSupportCast.find("skill/gunner/robotsexplosion.skl","[casting time] 400 400 [static data] 500 [/static data] [level info] 1 530 1060 [/level info]",f::get,ACTIONS,2,"gunner-final:empty");assertNotNull(s);assertEquals(3,s.visuals().size());assertTrue(s.emitters().isEmpty());for(var v:s.visuals()){assertEquals(400,v.start());assertEquals(2,v.scale());}assertTrue(s.note().contains("空场"));}
    @Test void ownedRxDetonatesAtCommandCompletionWithNativeOriginAndFiniteParticleBudget()throws Exception{
        var f=fixture();f.put("monster/monster.lst","60002 `gunner/robot/rx78.mob`");
        f.put("monster/gunner/robot/rx78.mob","[waiting motion] `RX78_Ani/Stay.ani`");
        f.put("passiveobject/passiveobject.lst","22214 `character/gunner/rx78explosion.obj`");
        f.put("passiveobject/character/gunner/rx78explosion.obj","[basic motion] `../../Common/Animation/NoneExplosion.ani` [string data] `../../Common/Particle/NoneExplosionParticle1.ptl` `../../Common/Particle/NoneExplosionParticle2.ptl` `../../Common/Particle/NoneExplosionParticle3.ptl` `../../Common/Particle/NoneExplosionParticle4.ptl` [/string data]");
        GameScript.Lookup lookup=p->{if(f.containsKey(p))return f.get(p);if(p.endsWith(".ani"))return ani();if(p.endsWith(".ptl"))return "[life time] 700 [creation frequency] 100 [maximum create number] 16";return null;};
        var s=Act1GunnerSupportCast.find("skill/gunner/robotsexplosion.skl","[casting time] 400 400 [static data] 500 [/static data] [level info] 1 530 [/level info]",lookup,ACTIONS,1,"");
        assertNotNull(s);var robot=s.visuals().stream().filter(v->v.path().endsWith("rx78_ani/stay.ani")).findFirst().orElseThrow();
        assertEquals(0,robot.start());assertEquals(400,robot.end());assertEquals(120,robot.x());
        assertEquals(5,s.emitters().size());for(var e:s.emitters()){assertEquals(400,e.start());assertEquals(120,e.x());assertEquals(30,e.z());assertEquals(0,e.frequency(),"Keep stock finite maximum; positive override clears it");}
        var quake=s.visuals().stream().filter(v->v.path().endsWith("earthquake.ani")).findFirst().orElseThrow();assertEquals(400,quake.start());assertEquals(-29.71,quake.y(),.00001);
        assertTrue(s.note().contains("己方 RX-78"));
        f.put("passiveobject/passiveobject.lst","22214 `character/gunner/wrong.obj`");
        assertNull(Act1GunnerSupportCast.find("skill/gunner/robotsexplosion.skl","[casting time] 400 400 [static data] 500 [/static data] [level info] 1 530 [/level info]",lookup,ACTIONS,1,""));
    }
    @Test void foreignBindingAndChoiceFailClosed()throws Exception{var f=fixture();assertNull(Act1GunnerSupportCast.find("skill/gunner/camouflage.skl","",f::get,ACTIONS,1,"precision:down"));f.put("skill/gunnerskill.lst","43 `gunner/fake.skl`");assertNull(Act1GunnerSupportCast.find("skill/gunner/camouflage.skl","",f::get,ACTIONS,1,""));}
    @Test void emptyFieldChoiceIsLimitedToStockMechanicalCommand()throws Exception{var f=fixture();assertEquals("gunner-final:empty",Act1GunnerSupportCast.choices("skill/gunner/robotsexplosion.skl",f::get,true).get(0).key());assertTrue(Act1GunnerSupportCast.choices("skill/gunner/camouflage.skl",f::get,true).isEmpty());assertTrue(Act1GunnerSupportCast.choices("skill/gunner/robotsexplosion.skl",f::get,false).isEmpty());f.put("skill/gunnerskill.lst","50 `gunner/other.skl`");assertTrue(Act1GunnerSupportCast.choices("skill/gunner/robotsexplosion.skl",f::get,true).isEmpty());}
    @Test void missingRequiredEffectDoesNotSilentlyClaimCoverage()throws Exception{var f=fixture();f.remove("character/gunner/effect/animation/robotsexplosion2.ani");assertNull(Act1GunnerSupportCast.find("skill/gunner/robotsexplosion.skl","[casting time] 400 400 [static data] 500 [/static data] [level info] 1 530 [/level info]",f::get,ACTIONS,1,""));}
    private static GameScript.Lookup complete() {
        var files = fixture();
        files.put("passiveobject/passiveobject.lst", """
                22206 `character/gunner/dropbomblight.obj`
                22208 `character/gunner/lightexplosion.obj`
                22211 `character/gunner/satelasermanager.obj`
                22223 `character/gunner/mechadrop.obj`
                22227 `character/gunner/mechadropexplosion.obj`
                """);
        files.put("passiveobject/character/gunner/mechadropexplosion.obj",
                Act1RobotAttackTest.files().get("passiveobject/character/gunner/mechadropexplosion.obj"));
        files.put("passiveobject/character/gunner/satelasermanager.obj", """
                [basic motion] `Animation/SateLaserWarningFloor.ani`
                [etc motion] `Animation/SateLaserCrashFloor.ani` `Animation/SateLaserMove.ani` [/etc motion]
                """);
        files.put("monster/monster.lst","60013 `gunner/robot/droprx78.mob`");
        files.put("passiveobject/character/gunner/lightexplosion.obj","[basic motion] `../../Common/Animation/LightExplosion.ani`");
        files.put("passiveobject/character/gunner/dropbomblight.obj","[int data] -100 -1500 5 400 [/int data]");
        return path -> {
            if (files.containsKey(path)) return files.get(path);
            if (path.endsWith(".ani")) return ani();
            if (path.endsWith("satelaser.ptl")) return """
                    [x axis] [move type] `[sin]` [move variable 1] 0 75 [move variable 2] 1500 1500
                    [y axis] [move type] `[cos]` [move variable 1] 0 75 [move variable 2] 1500 1500
                    [changing term] 1000 [changing type] `[keep last value]`
                    """;
            if (path.endsWith(".ptl")) return "[life time] 100 [creation frequency] 10 [maximum create number] 1";
            return null;
        };
    }
    @Test void quantumFallEndsAtGroundAndTailFollowsItsBirthAnchor()throws Exception{var s=Act1GunnerSupportCast.find("skill/gunner/uraniumbomb.skl","[static data] 600 400 600 [/static data] [level info] 6 0 0 0 0 0 200 [/level info]",complete(),ACTIONS,1,"");assertNotNull(s);var bomb=s.visuals().get(0);assertEquals(600,bomb.start());assertEquals(1267,bomb.end());assertEquals(400,bomb.motion().heightAt(600));assertEquals(0,bomb.motion().heightAt(1267));assertEquals(bomb.motion(),s.emitters().get(0).sourceMotion());assertEquals(70,s.emitters().get(0).z());assertEquals(2,s.emitters().get(2).scale());}
    @Test void airDropRetainsNegativeVerticalSpeedAndExactCountCadence()throws Exception{var s=Act1GunnerSupportCast.find("skill/gunner/mechadrop.skl","[casting time] 700 700 [static data] 75 22 1 2 5000 3 200 -500 250 3 800 [/static data] [level info] 3 500 120 20 [/level info]",complete(),ACTIONS,1,"");assertNotNull(s);var drops=s.visuals().stream().filter(v->v.path().endsWith("/drop.ani")).toList();assertEquals(20,drops.size());assertEquals(1700,drops.get(0).start());assertEquals(2500,drops.get(0).end());assertEquals(6450,drops.get(19).start());assertEquals(-500,drops.get(0).motion().verticalVelocity());assertEquals(160,drops.get(0).motion().offsetAt(2500));assertEquals(400,drops.get(19).x());}
    @Test void satelliteEntranceIsSeparateFromSixFrameLoopAndTilesAreGapless()throws Exception{var s=Act1GunnerSupportCast.find("skill/gunner/satellitelaser.skl","[static data] 600 600 300 5 500 [/static data] [level info] 3 1000 200 12000 [/level info]",complete(),ACTIONS,1,"");assertNotNull(s);var tiles=s.visuals().stream().filter(v->v.label().startsWith("卫星射线 · 柱延展 0/")).toList();assertEquals(10,tiles.size());assertEquals(40,tiles.get(0).y()-tiles.get(1).y(),.000001);assertEquals(1400,tiles.get(0).start());assertEquals(13100,tiles.get(0).end());assertEquals(600,tiles.get(0).style().loopPeriod());assertEquals(0,tiles.get(0).style().frameOverrides().get(0).delay());assertEquals(0,tiles.get(0).style().sample(11700).opacity());}
    @Test void quantumLightBurstUsesOriginalRootClockAndFiniteCounts()throws Exception{
        var source=complete();GameScript.Lookup lookup=p->p.equals("passiveobject/character/gunner/lightexplosion.obj")?"[basic motion] `../../Common/Animation/LightExplosion.ani`":p.equals("passiveobject/common/animation/lightexplosion.ani")?"[FRAME000] [DELAY] 400":source.read(p);
        var s=Act1GunnerSupportCast.find("skill/gunner/uraniumbomb.skl","[static data] 600 400 600 [/static data] [level info] 6 0 0 0 0 0 200 [/level info]",lookup,ACTIONS,1,"");assertNotNull(s);
        var burst=s.emitters().stream().filter(e->e.path().contains("lightexplosionparticle")).toList();assertEquals(4,burst.size());
        for(var e:burst){assertEquals(400,e.emitFor());assertEquals(0,e.frequency());assertEquals(2,e.scale());assertEquals(2,e.movementScale());}
    }
}
