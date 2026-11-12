package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1PriestRemainingTest {
    private static final String ROOT="character/priest/", EFFECT=ROOT+"effect/animation/";
    private static final Map<String,String> ACTIONS=Map.of(
            "waiting motion","animation/stay.ani","down motion","animation/down.ani",
            "buff motion","animation/buff.ani","etc motion:13","animation/luckystraight.ani",
            "etc motion:22","animation/homerungrab.ani","etc motion:23","animation/homeruncharge.ani",
            "etc motion:24","animation/homerunsmash.ani");

    @Test void homeRunMissDoesNotPlayChargeAndTheGrabbedRouteReachesImpact() throws Exception {
        var data=new HashMap<String,String>();
        data.put("skill/priestskill.lst","41 `priest/homerun.skl`");
        data.put(ROOT+"animation/homerungrab.ani",animation(70,70,200,60,60,60,60,150));
        data.put(ROOT+"animation/homeruncharge.ani",animation(300,90,90,2500));
        data.put(ROOT+"animation/homerunsmash.ani",animation(60,60,60,150));
        for(String name:new String[]{"grab1","grab2","dust1","dust2","dust3","smash","hit1","hit2"}){
            data.put(EFFECT+"homerun/"+name+".ani",animation(500));
        }
        for(String name:new String[]{"stay","damage1","overturn"}){
            data.put(Act1SwordAsuraSupplement.GOBLIN+name+".ani",animation(500));
        }
        String path="skill/priest/homerun.skl";
        var hit=Act1PriestRemaining.find(path,"[static data] 20 500",data::get,ACTIONS,1,"",job->111,null);
        var miss=Act1PriestRemaining.find(path,"[static data] 20 500",data::get,ACTIONS,1,"homerun:miss",job->111,null);
        assertEquals(730,hit.poses().get(1).start());
        assertEquals(1230,hit.poses().get(2).start());
        assertEquals(1350,hit.visuals().stream().filter(v->v.path().endsWith("hit2.ani")).findFirst().orElseThrow().start());
        var thrown=hit.visuals().stream().filter(v->v.path().endsWith("/overturn.ani")&&v.start()==730).findFirst().orElseThrow();
        assertEquals(1300,thrown.motion().heightAt(1040),.001);
        assertEquals(55,thrown.motion().heightAt(1350),.001);
        assertEquals(88,thrown.x()+thrown.motion().offsetAt(1350),.001);
        assertEquals(730,miss.poses().get(0).end());
        assertEquals(1,miss.poses().size());
        assertTrue(miss.visuals().isEmpty());
    }

    @Test void sacrificeUsesTheSecondaryActorsPositionAndHeight() throws Exception {
        var data=Map.of("skill/priestskill.lst","25 `priest/sacrificeofsoul.skl`",
                EFFECT+"sacrificeofsoulcast.ani",animation(500),EFFECT+"sacrificeofsoulbuff.ani",animation(600));
        var sequence=Act1PriestRemaining.find("skill/priest/sacrificeofsoul.skl","[static data] 1600",
                data::get,ACTIONS,1,"",job->111,null);
        assertEquals("down motion",sequence.poses().get(0).action());
        var ally=sequence.poses().get(1);
        assertTrue(ally.secondary());
        var buff=sequence.visuals().get(1);
        assertEquals(ally.motion().offsetAt(0),buff.x());
        assertEquals(-111,buff.y());
    }

    @Test void shadowReplaysTheSameTravelAtTheConfiguredDelay() throws Exception {
        var data=Map.of("skill/priestskill.lst","35 `priest/shadowboxer.skl` 61 `priest/luckystraight.skl`",
                "data/skill/priest.sd","[int] `sb shadow delay` 150 [/int]",
                "skill/priest/luckystraight.skl","[static data] 500 -1200",
                ROOT+"animation/luckystraight.ani",animation(70,70,70,70,70,70,70),
                EFFECT+"luckystraightwave.ani",animation(300));
        var sequence=Act1PriestRemaining.find("skill/priest/shadowboxer.skl",
                "[casting time] 500 [static data] 1 [level info] 2 12000 216",data::get,ACTIONS,1,"",job->111,null);
        var echo=sequence.poses().stream().filter(Act1SkillSequence.Pose::secondary).findFirst().orElseThrow();
        var actor=sequence.poses().stream().filter(p->!p.secondary()&&p.action().equals(echo.action())).findFirst().orElseThrow();
        assertEquals(150,echo.start()-actor.start());
        assertEquals(actor.motion().offsetAt(actor.start()+200),echo.motion().offsetAt(echo.start()+200));
        assertEquals(150.0/255,echo.keyframes().get(0).opacity());
    }

    private static String animation(int... delays){
        var script=new StringBuilder();
        for(int i=0;i<delays.length;i++)script.append("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d\n".formatted(i,delays[i]));
        return script.toString();
    }
}
