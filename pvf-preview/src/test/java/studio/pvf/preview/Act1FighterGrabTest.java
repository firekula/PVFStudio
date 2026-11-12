package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1FighterGrabTest {
    private static final String LEG="skill/fighter/legsuplex.skl";
    private static final String SUPLEX="skill/fighter/suplex.skl";
    private static final String SPIRE="skill/fighter/spire.skl";
    private static final String FIGHTER="character/fighter/";
    private static final Map<String,String> ACTIONS=Map.of(
            "waiting motion","Animation/Stay.ani","jump motion","Animation/Jump.ani",
            "etc motion:18","Animation/TryLegSuplex.ani","etc motion:19","Animation/LegSuplexThrow.ani",
            "etc motion:4","Animation/TrySuplex.ani","etc motion:5","Animation/Suplex.ani",
            "etc motion:11","Animation/TryJumpSuplex.ani","etc motion:12","Animation/JumpSuplex.ani");

    @Test void airGrabFollowsTheFighterUntilLandingAndSpawnsGroundEffects() throws Exception {
        var sequence=Act1FighterGrab.find(LEG,"[static data] 500 500",resources()::get,ACTIONS,1,false);
        assertNotNull(sequence);
        assertEquals("etc motion:18",sequence.poses().get(0).action());
        assertEquals("etc motion:19",sequence.poses().get(1).action());
        assertEquals(50,sequence.poses().get(1).start());
        assertEquals(50,sequence.poses().get(1).motion().heightAt(350));
        assertTrue(sequence.poses().get(1).motion().heightAt(549)>0);
        assertEquals(0,sequence.poses().get(1).motion().heightAt(550));
        var crash=sequence.visuals().stream().filter(v->v.path().endsWith("legsuplexcrash.ani")).findFirst().orElseThrow();
        var shock=sequence.visuals().stream().filter(v->v.path().endsWith("legsuplexsubthrow.ani")).findFirst().orElseThrow();
        assertEquals(550,crash.start());
        assertEquals(550,shock.start());
        assertEquals(-28,shock.x());
        assertEquals(600,sequence.poses().get(2).start());
        assertTrue(sequence.poses().get(2).motion().heightAt(900)>80);
    }

    @Test void missingAirGrabUsesTheConfiguredTimeoutAndHasNoImpact() throws Exception {
        var sequence=Act1FighterGrab.find(LEG,"[static data] 100 500",resources()::get,ACTIONS,1,true);
        assertNotNull(sequence);
        assertEquals(100,sequence.poses().get(0).end());
        assertEquals("jump motion",sequence.poses().get(1).action());
        assertTrue(sequence.visuals().isEmpty());
        assertEquals("legsuplex:miss",Act1FighterGrab.missChoice(LEG,resources()::get,true).key());
        assertTrue(Act1FighterGrab.missSelected(LEG,"legsuplex:miss"));
        assertFalse(Act1FighterGrab.missSelected(LEG,"suplex:miss"));
        assertNull(Act1FighterGrab.missChoice(LEG,resources()::get,false));
    }

    @Test void suplexDustStartsOnNativeFrameIndexFourOnlyAfterASuccessfulGrab() throws Exception {
        var success=Act1FighterGrab.find(SUPLEX,"[static data] 300 500",resources()::get,ACTIONS,1,false);
        assertNotNull(success);
        assertEquals("etc motion:5",success.poses().get(1).action());
        var dust=success.visuals().get(0);
        assertEquals(600,dust.start());
        assertEquals(-47,dust.x());
        var miss=Act1FighterGrab.find(SUPLEX,"[static data] 450 500",resources()::get,ACTIONS,1,true);
        assertEquals(450,miss.poses().get(0).end());
        assertTrue(miss.visuals().isEmpty());
    }

    @Test void spireUsesTheCurrentLaunchSpeedAndShowsDistinctRollAndImpactClips() throws Exception {
        var data="[static data] 360 700 [level info] 3 100 50 1100 200 100 1300";
        var first=Act1FighterGrab.find(SPIRE,data,resources()::get,ACTIONS,1,false);
        var second=Act1FighterGrab.find(SPIRE,data,resources()::get,ACTIONS,2,false);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals("etc motion:11",first.poses().get(0).action());
        assertEquals("etc motion:12",first.poses().get(1).action());
        assertTrue(second.visuals().get(0).start()>first.visuals().get(0).start());
        assertTrue(second.visuals().get(2).start()>first.visuals().get(2).start());
        assertEquals(first.visuals().get(0).end(),first.visuals().get(2).start());
        assertTrue(first.visuals().get(0).layer()<1000);
        assertTrue(first.visuals().get(1).layer()>2000);
        var rebound=first.poses().get(5);
        assertEquals(40,rebound.motion().maximumHeight(rebound.start(),rebound.end()),0.01);
    }

    private static Map<String,String> resources(){
        var files=new HashMap<String,String>();
        files.put(FIGHTER+"fighter.chr","[jump power] 470.0");
        files.put("skill/fighterskill.lst","9 `Fighter/Suplex.skl` 18 `Fighter/Spire.skl` 62 `Fighter/LegSuplex.skl`");
        files.put("passiveobject/passiveobject.lst","21001 `Character/Fighter/SuplexSub.obj` 21008 `Character/Fighter/JumpSuplexSub.obj` 21016 `Character/Fighter/LegSuplexSub.obj`");
        for(String object:new String[]{"suplexsub","jumpsuplexsub","legsuplexsub"}){
            files.put("passiveobject/character/fighter/"+object+".obj","[basic motion] `Animation/"+object+".ani`");
            files.put("passiveobject/character/fighter/animation/"+object+".ani",ani(300));
        }
        files.put(FIGHTER+"animation/stay.ani",ani(100));
        files.put(FIGHTER+"animation/jump.ani",ani(100,10000,50,50));
        files.put(FIGHTER+"animation/trylegsuplex.ani",ani(50));
        files.put(FIGHTER+"animation/legsuplexthrow.ani",ani(300,80,80,8000));
        files.put(FIGHTER+"animation/trysuplex.ani",ani(300));
        files.put(FIGHTER+"animation/suplex.ani",ani(200,100,100,100,300));
        files.put(FIGHTER+"animation/tryjumpsuplex.ani",ani(360));
        files.put(FIGHTER+"animation/jumpsuplex.ani",ani(200,100,10000,50,50,50,10000,10000));
        for(String effect:new String[]{"legsuplexgrab","legsuplexroll","legsuplexcrash","suplexdust",
                "jumpsuplexrollunder","jumpsuplexrollover","jumpsuplexcrashunder","jumpsuplexcrashover"}){
            files.put(FIGHTER+"effect/animation/"+effect+".ani",ani(80,80));
        }
        files.put("passiveobject/character/fighter/animation/legsuplexsubthrow.ani",ani(150,150));
        return files;
    }

    private static String ani(int... delays){
        var text=new StringBuilder("[FRAME MAX] ").append(delays.length);
        for(int i=0;i<delays.length;i++)text.append(String.format(" [FRAME%03d] [DELAY] %d",i,delays[i]));
        return text.toString();
    }
}
