package studio.pvf.preview;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Act1SwordBloodRegressionTest {
    private static String ani(int count,int delay){var s=new StringBuilder();for(int i=0;i<count;i++)s.append("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d ".formatted(i,delay));return s.toString();}
    private static HashMap<String,String> files(){var f=new HashMap<String,String>();f.put("skill/swordmanskill.lst","31 `swordman/grabblastblood.skl` 81 `swordman/outragebreak.skl`");f.put("passiveobject/passiveobject.lst","20017 `character/swordman/grabblastblood.obj` 20049 `character/swordman/grabblastbloodbig.obj` 20043 `character/swordman/outragebreakfloor.obj` 20044 `character/swordman/outragebreakblood.obj`");f.put("character/swordman/grab.ani",ani(17,40));f.put("character/swordman/effect/animation/grabblastblood.ani",ani(13,50));for(boolean big:new boolean[]{false,true}){String stem="grabblastblood"+(big?"big":"");var object=new StringBuilder("[basic motion] `animation/"+stem+"1.ani` [etc motion] ");for(int i=2;i<=(big?6:4);i++)object.append("`animation/").append(stem).append(i).append(".ani` ");object.append("[/etc motion]");f.put("passiveobject/character/swordman/"+stem+".obj",object.toString());for(int i=1;i<=(big?6:4);i++)f.put("passiveobject/character/swordman/animation/"+stem+i+".ani",ani(i==1?6:i==2?4:1,i==1?80:100));}return f;}
    @Test void absorptionExpiresBeforeSprayAndCaptureStartsAtFirstAuthoredGrabBox()throws Exception{
        var f=files();var s=Act1SwordRemaining.find("skill/swordman/grabblastblood.skl","[static data] 1200 500",f::get,Map.of("etc motion:10","grab.ani","waiting motion","wait.ani"),1);assertNotNull(s);
        assertEquals(160,s.poses().get(1).start());assertEquals(1360,s.poses().get(1).end());
        var absorb=s.visuals().stream().filter(v->v.label().contains("吸血")).findFirst().orElseThrow();assertEquals(810,absorb.end());
        var blasts=s.visuals().stream().filter(v->v.path().startsWith("passiveobject/")).toList();assertEquals(4,blasts.size());
        for(var v:blasts){assertEquals(910,v.start());assertTrue(absorb.end()<v.start());assertEquals(43,v.x());assertEquals(-89.71,v.y(),.000001);}
        assertEquals(560,blasts.get(0).end()-blasts.get(0).start());
        for (int i = 0; i < 4; i++) assertEquals(100, blasts.get(0).style().frameOverrides().get(i).delay());assertEquals(400,blasts.get(1).end()-blasts.get(1).start());assertEquals(100,blasts.get(2).end()-blasts.get(2).start());
    }
    @Test void outrageOverridesAuthoredParticleCreationPositionAtNativeFloorAnchor()throws Exception{
        var f=files();f.put("character/swordman/ready.ani",ani(1,560));f.put("character/swordman/slash.ani",ani(9,120));
        f.put("passiveobject/character/swordman/outragebreakfloor.obj","[basic motion] `animation/outragebreak/floor.ani` [etc motion] `animation/outragebreak/floor_over.ani` `animation/outragebreak/floor_blood.ani` [/etc motion]");f.put("passiveobject/character/swordman/outragebreakblood.obj","[basic motion] `animation/outragebreak/bloodexp_damage.ani`");
        GameScript.Lookup lookup=p->f.containsKey(p)?f.get(p):p.endsWith(".ani")?ani(6,100):p.endsWith(".ptl")?"[creation pos] 450 100 0 [life time] 600 [creation frequency] 30 [maximum create time] 500":null;
        var s=Act1SwordRemaining.find("skill/swordman/outragebreak.skl","[static data] 130 150 500 5",lookup,Map.of("etc motion:66","ready.ani","etc motion:67","slash.ani","waiting motion","wait.ani"),1);assertNotNull(s);
        var drops=s.emitters().stream().filter(e->e.path().endsWith("outragebreak_drops.ptl")).findFirst().orElseThrow();assertTrue(drops.overridePosition());assertEquals(280,drops.x());assertEquals(0,drops.y());assertEquals(0,drops.z());
        var floor=s.visuals().stream().filter(v->v.path().endsWith("/floor.ani")).findFirst().orElseThrow();
        var lava=s.visuals().stream().filter(v->v.label().contains("岩浆喷发")).findFirst().orElseThrow();
        var sparks=s.emitters().stream().filter(e->e.path().endsWith("outragebreak_light_part.ptl")).findFirst().orElseThrow();
        assertEquals(floor.end(),drops.start());
        assertEquals(floor.start()+400,lava.start());
        assertEquals(lava.start(),sparks.start());
    }
    @Test void bleedingTargetSelectsSixOriginalLayersWithoutReplacingOrdinaryBranch()throws Exception{
        var f=files();var method=Act1SwordRemaining.class.getDeclaredMethod("bloodChoice",String.class,String.class,GameScript.Lookup.class,Map.class,String.class);
        var s=(Act1SkillSequence)method.invoke(null,"skill/swordman/grabblastblood.skl","[static data] 1200 500",(GameScript.Lookup)f::get,Map.of("etc motion:10","grab.ani","waiting motion","wait.ani"),"sword-final:bleeding");assertNotNull(s);
        var blasts=s.visuals().stream().filter(v->v.path().startsWith("passiveobject/")).toList();assertEquals(6,blasts.size());for(var v:blasts){assertTrue(v.path().contains("grabblastbloodbig"));assertEquals(910,v.start());assertEquals(-89.71,v.y(),.000001);}
        assertNull(method.invoke(null,"skill/swordman/outragebreak.skl","",(GameScript.Lookup)f::get,Map.of(),"sword-final:bleeding"));
    }
}
