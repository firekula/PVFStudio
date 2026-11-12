package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class Act1SwordWaveCastTest {
    private static final String ROOT="character/swordman/",OBJ="passiveobject/character/swordman/";
    private static final Map<String,String> ACTIONS=Map.of("etc motion:9","Animation/Wave.ani","waiting motion","Animation/Stay.ani");
    private static String animation(int... delays){
        var out=new StringBuilder();for(int i=0;i<delays.length;i++)out.append("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d\n".formatted(i,delays[i]));return out.toString();
    }
    private static HashMap<String,String> fixture(){
        var files=new HashMap<String,String>();
        files.put(ROOT+"animation/wave.ani",animation(10000,50,50,50,50,50,50,50,150));
        files.put(ROOT+"effect/animation/normalwaveslash.ani",animation(250));
        files.put(ROOT+"effect/animation/icewave.ani",animation(350));
        files.put(ROOT+"effect/animation/firewavebig-sword-effect.ani",animation(400));
        var catalog=new StringBuilder();
        for(int id:new int[]{20001,20002,20003,20004,20005,20006,20007,20024,20025}){
            catalog.append(id+" `character/swordman/"+id+".obj`\n");
            files.put(OBJ+id+".obj","[basic motion] `"+id+".ani`"+(id>=20024?" [etc motion] `"+id+"back.ani` [/etc motion]":""));
            files.put(OBJ+id+".ani",id==20001?animation(65,65,65,65,65):id<20024?animation(80,50,50,100):id==20024?animation(70,70,70,70,70,70,70):animation(70,70,70,70,70,70,70,70));
            if(id>=20024)files.put(OBJ+id+"back.ani",id==20024?animation(70,70,70,70,70,70):animation(70,70,70,70,70));
        }
        files.put("passiveobject/passiveobject.lst",catalog.toString());
        for(String particle:new String[]{"icewaveparticle","icewaveparticlesmoke","icewaveparticlestar"})files.put(OBJ+"particle/"+particle+".ptl","[life time] 500 [maximum count] 3");
        return files;
    }
    private static String skill(int cast,String statics,String rows){return "[maximum level] 2 [casting time] "+cast+" "+cast+" [static data] "+statics+" [/static data] [level info] "+rows+" [/level info]";}
    @Test void iceUsesHeldBodyFrameThenRecursiveColumnClockAndNativeScale() throws Exception {
        var cast=Act1SwordWaveCast.find(21,skill(300,"100 500","7 200 5 100 172 200 21 2000 240 8 100 172 200 21 2000"),fixture()::get,ACTIONS,1);
        assertNotNull(cast);assertEquals(150,cast.poses().get(0).holdDelay());assertEquals(800,cast.poses().get(0).end());
        assertEquals(6,cast.visuals().size());assertEquals(150,cast.visuals().get(0).start());assertEquals(59,cast.visuals().get(0).x());
        var first=cast.visuals().get(1);assertEquals(300,first.start());assertEquals(650,first.end());assertEquals(100,first.x());assertEquals(-4,first.y());assertEquals(1.5,first.scale());assertEquals(120,first.style().frameOverrides().get(1).delay());
        var second=cast.visuals().get(2);assertEquals(400,second.start());assertEquals(121,second.x());assertEquals(4,second.y());assertEquals(1.575,second.scale(),1e-9);
        assertEquals(15,cast.emitters().size());assertEquals(300,cast.emitters().get(0).start());assertEquals(550,cast.emitters().get(1).start());assertEquals(20,cast.emitters().get(1).z());
        assertEquals(1.5,cast.emitters().get(1).scale());assertEquals(1,cast.emitters().get(2).scale());
        assertTrue(cast.emitters().stream().allMatch(e->e.movementScale()==1),"Native scaling changes ice/smoke images, not their trajectories");
        assertTrue(cast.poses().get(1).end()>cast.visuals().get(5).end(),"Particle tails survive the body and column clocks");
    }
    @Test void iceLevelAndEditedAniControlCountVariantCycleAndShatterTime() throws Exception {
        var files=fixture();files.put(OBJ+"20002.ani",animation(90,60,70,110));
        var cast=Act1SwordWaveCast.find(21,skill(300,"120 500","7 200 5 100 172 200 21 2000 240 8 100 172 200 21 2000"),files::get,ACTIONS,2);
        assertEquals(9,cast.visuals().size());assertEquals(150,cast.visuals().get(1).style().frameOverrides().get(1).delay());
        assertEquals(610,cast.emitters().get(1).start());assertEquals(1020,cast.visuals().get(7).start());assertTrue(cast.visuals().get(7).path().endsWith("20002.ani"));assertEquals(24,cast.emitters().size());
    }
    @Test void fireSpawnsBlastOnlyAfterFrontEndsAndRemovesBlastBackAtFrameFour() throws Exception {
        var cast=Act1SwordWaveCast.find(22,skill(400,"500","2 964 1606"),fixture()::get,ACTIONS,1);
        assertNotNull(cast);assertEquals(250,cast.poses().get(0).holdDelay());assertEquals(5,cast.visuals().size());
        var back=cast.visuals().get(1);var front=cast.visuals().get(2);var blastBack=cast.visuals().get(3);var blast=cast.visuals().get(4);
        assertEquals(400,front.start());assertEquals(890,front.end());assertEquals(890,back.end());assertEquals(890,blast.start());assertEquals(360,blast.x());assertEquals(1170,blastBack.end());assertEquals(1450,blast.end());
        assertEquals(1450,cast.poses().get(1).end());assertTrue(blast.mirror());assertTrue(blastBack.layer()<blast.layer());
    }
    @Test void normalMovesCasterButLeavesWaveAtItsSpawnOriginAndScalesByLevel() throws Exception {
        var cast=Act1SwordWaveCast.find(20,skill(200,"500 100 -150","3 31 125 95"),fixture()::get,ACTIONS,1);
        assertNotNull(cast);assertEquals(50,cast.poses().get(0).holdDelay());assertEquals(70,cast.poses().get(0).motion().offsetAt(700),1e-9);
        var projectile=cast.visuals().get(1);assertEquals(150,projectile.start());assertEquals(475,projectile.end());assertEquals(15,projectile.x(),1e-9);assertNull(projectile.motion());assertEquals(1.25,projectile.scale());assertEquals(cast.poses().get(0).motion(),cast.visuals().get(0).motion());
    }
    @Test void groundWaveHoldsItsFinalReleaseFrameWhileCasterKeepsSliding() throws Exception {
        var cast = Act1SwordWaveCast.find(20, skill(200, "500 100 -150", "3 31 125 95"),
                fixture()::get, ACTIONS, 1);
        var hold = cast.poses().get(1);
        assertEquals("etc motion:9", hold.action());
        assertEquals(8, hold.firstFrame());
        assertEquals(550, hold.start());
        assertEquals(700, hold.end());
        assertTrue(hold.motion().offsetAt(699) > hold.motion().offsetAt(hold.start()));
    }

    @Test void missingOrUnsupportedDependenciesDoNotProducePartialRecipes() throws Exception {
        var files=fixture();files.remove(OBJ+"20025back.ani");assertNull(Act1SwordWaveCast.find(22,skill(400,"500","2 964 1606"),files::get,ACTIONS,1));
        files=fixture();files.remove(OBJ+"particle/icewaveparticlestar.ptl");assertNull(Act1SwordWaveCast.find(21,skill(300,"100 500","7 200 5 100 172 200 21 2000"),files::get,ACTIONS,1));
        assertNull(Act1SwordWaveCast.find(21,skill(300,"100 500","7 200 99 100 172 200 21 2000"),fixture()::get,ACTIONS,1));
        assertNull(Act1SwordWaveCast.find(20,"",fixture()::get,Map.of("etc motion:9","Animation/Other.ani"),1));
    }
}
