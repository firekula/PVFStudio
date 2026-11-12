package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1PriestCastTest {
    private static final String EFFECT="character/priest/effect/animation/";

    @Test void cureUsesDeliverAndReplacesTheChargeWithTheReleaseEffect() throws Exception {
        var files=motions();
        files.put("skill/priestskill.lst","52 `Priest/Cure.skl`");
        files.put(EFFECT+"curecast1.ani",animation("charge",400));
        files.put(EFFECT+"curecast2.ani",animation("release",320));
        var preview=load("cure","[casting time] 500 500 [static data] 900 400",files);

        assertEquals("throw motion 3-1",preview.appearance().action());
        assertEquals("deliver1.img",draw(preview,"body",499).prepared().source().img());
        assertEquals("charge.img",draw(preview,"净化 · 施法",499).prepared().source().img());
        assertEquals("deliver2.img",draw(preview,"body",500).prepared().source().img());
        assertEquals("release.img",draw(preview,"净化 · 施放",500).prepared().source().img());
        assertFalse(preview.render(500,false).draws().stream().anyMatch(d->d.label().equals("净化 · 施法")));
        assertEquals(900,preview.duration());
    }

    @Test void partyHealthUsesBlastAndStartsTheScaledFloorOnRelease() throws Exception {
        var files=motions();
        files.put("skill/priestskill.lst","24 `Priest/HpMaxUpParty.skl`");
        files.put(EFFECT+"hpmaxup/partyblast.ani",animation("blast",560));
        files.put(EFFECT+"hpmaxup/partyfloor.ani",animation("floor",960));
        var preview=load("hpmaxupparty","[casting time] 800 800 [static data] 900 500",files);

        assertEquals("throw motion 4-1",preview.appearance().action());
        assertEquals("blast1.img",draw(preview,"body",799).prepared().source().img());
        assertFalse(preview.render(799,false).draws().stream().anyMatch(d->d.label().contains("地面光效")));
        assertEquals("blast2.img",draw(preview,"body",800).prepared().source().img());
        var floor=draw(preview,"天堂谐音 · 地面光效",800);
        assertEquals(900.0/256,floor.prepared().transform().getScaleX(),.001);
        assertEquals("stay.img",draw(preview,"body",1500).prepared().source().img());
        assertEquals(1760,preview.duration());
    }

    @Test void revengeOfLightUsesTheCounterObjectOnTheAttackerAndPreservesBuffOnlyChoice() throws Exception {
        var files=motions();
        files.put("skill/priestskill.lst","23 `Priest/RevengeOfLight.skl`");
        files.put(EFFECT+"revengeoflight/cast.ani",animation("charge",400));
        files.put(EFFECT+"revengeoflight/buffstart1.ani",animation("start",320));
        files.put(EFFECT+"revengeoflight/buffloop.ani",animation("buff",320));
        files.put("passiveobject/passiveobject.lst","24012 `priest/counter.obj`");
        files.put("passiveobject/priest/counter.obj","[basic motion] `thunderbolt.ani`");
        files.put("passiveobject/priest/thunderbolt.ani",animation("lightning",540));
        files.put(Act1SwordAsuraSupplement.GOBLIN+"stay.ani",animation("goblin",80));
        files.put(Act1SwordAsuraSupplement.GOBLIN+"damage1.ani",animation("goblin-hit",80));
        String path="skill/priest/revengeoflight.skl";
        String text="[casting time] 500 [static data] 900 500 800 150 100 400 200";
        var actions=Map.of("throw motion 3-1","Deliver1.ani","throw motion 3-2","Deliver2.ani","waiting motion","Stay.ani");
        var hit=Act1SkillSequence.find(path,text,files::get,actions,1,"",true);
        var buff=Act1SkillSequence.find(path,text,files::get,actions,1,"revengeoflight:buff",true);
        var bolt=hit.visuals().stream().filter(v->v.path().endsWith("/thunderbolt.ani")).findFirst().orElseThrow();
        assertTrue(bolt.start()>1000);
        assertTrue(bolt.x()>0);
        assertTrue(hit.visuals().stream().anyMatch(v->v.path().endsWith("/damage1.ani")&&v.x()==bolt.x()));
        assertTrue(buff.visuals().stream().noneMatch(v->v.path().endsWith("/thunderbolt.ani")));
    }

    private static Map<String,String> motions(){
        var files=new HashMap<String,String>();
        files.put("character/priest/priest.chr","""
                [waiting motion] `Stay.ani`
                [throw motion 2-1] `Pray1.ani` [throw motion 2-2] `Pray2.ani`
                [throw motion 3-1] `Deliver1.ani` [throw motion 3-2] `Deliver2.ani`
                [throw motion 4-1] `Blast1.ani` [throw motion 4-2] `Blast2.ani`
                """);
        for(String image:java.util.List.of("stay","pray1","pray2","deliver1","deliver2","blast1","blast2")){
            files.put("character/priest/"+image+".ani",animation(image,100));
        }
        return files;
    }

    private static String animation(String image,int duration){
        return "[FRAME000] [IMAGE] `"+image+".img` 0 [DELAY] "+duration;
    }

    private static GamePreview load(String skill,String text,Map<String,String> files) throws Exception {
        var pixels=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0,0,0xffffffff);
        var sprites=new ScenePreview.SpriteLookup(){
            public NpkResources.Info info(String image,int index){return new NpkResources.Info(1,1,0,0,1,1);}
            public NpkResources.Sprite sprite(String image,int index){
                return new NpkResources.Sprite(pixels,0,0,1,1,Path.of("fixture.npk"),image,index,index);
            }
        };
        return GamePreview.load("skill/priest/"+skill+".skl",text,files::get,files.keySet(),sprites,GamePreview.Options.defaults());
    }

    private static GamePreview.Draw draw(GamePreview preview,String label,long time) throws Exception {
        return preview.render(time,false).draws().stream().filter(d->d.label().equals(label)).findFirst().orElseThrow();
    }
}
