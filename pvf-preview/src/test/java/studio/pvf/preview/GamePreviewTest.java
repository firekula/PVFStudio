package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GamePreviewTest {
    private static Map<String,String> data(){var d=new HashMap<String,String>();d.put("character/gunner/gunner.chr","[waiting motion] `body.ani`");d.put("character/gunner/body.ani","[LOOP] 1 [FRAME000] [IMAGE] `red.img` 0 [DELAY] 100 [FRAME001] [IMAGE] `green.img` 0 [DELAY] 100");return d;}
    @Test void composesIndependentTimingAndMirrorsAroundFeet() throws Exception {
        var d=data();String path="equipment/part.equ";String text="[equipment type] `[weapon]` [LAYER] 1800 [waiting motion] `part.ani`";
        d.put(path,text);d.put("equipment/part.ani","[LOOP] 1 [FRAME000] [IMAGE] `blue.img` 0 [IMAGE POS] 4 0 [DELAY] 300");
        var p=GamePreview.load(path,text,d::get,d.keySet(),new Pixels(),GamePreview.Options.defaults());
        var image=paint(p.render(150,false));assertEquals(Color.GREEN.getRGB(),image.getRGB(10,10));assertEquals(Color.BLUE.getRGB(),image.getRGB(14,10));
        assertEquals(7,p.render(150,false).bounds().getWidth());
        assertEquals(Color.BLUE.getRGB(),paint(p.render(150,true)).getRGB(3,10));
        assertEquals(image.getRGB(10,10),paint(p.render(150,false)).getRGB(10,10));
        assertEquals(Color.RED.getRGB(),paint(p.render(250,false)).getRGB(10,10));
    }
    @Test void missingPartDoesNotSuppressValidBody() throws Exception {
        var d=data();String path="equipment/part.equ",text="[equipment type] `[weapon]` [waiting motion] `missing.ani`";
        var p=GamePreview.load(path,text,d::get,d.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertEquals(Color.RED.getRGB(),paint(p.render(0,false)).getRGB(10,10));assertFalse(p.warnings().isEmpty());
    }
    @Test void explicitPassiveObjectUsesCurrentCatalogAndDoesNotGuessBattleMotion() throws Exception {
        var d=data();d.put("passiveobject/passiveobject.lst","23008 `common/projectile.obj`");d.put("passiveobject/common/projectile.obj","[basic motion] `orb.ani` [etc motion] `impact.ani`");d.put("passiveobject/common/orb.ani","[FRAME000] [IMAGE] `blue.img` 0 [DELAY] 150");
        String path="equipment/weapon.equ",text="[equipment type] `[weapon]` [if] [attack success] 1 [/if] [then] [passive object] 23008 120 0 90 40 0 `Straight.ptl` [/passive object] [/then]";
        var p=GamePreview.load(path,text,d::get,d.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertTrue(p.effects().stream().anyMatch(e->e.key().equals("passive:23008")));
        var triggered=GamePreview.load(path,text,d::get,d.keySet(),new Pixels(),GamePreview.Options.defaults().withEffect("passive:23008"));
        assertTrue(triggered.render(0,false).draws().stream().anyMatch(draw->draw.prepared().source().img().equals("blue.img")));
        assertTrue(triggered.warnings().stream().anyMatch(w->w.contains("触发")||w.contains("初始")));
    }
    @Test void headTitleStaysReadableWhenActorFacesLeft() throws Exception {
        var d=data();d.put("equipment/title.ani","[FRAME000] [IMAGE] `title.img` 0 [DELAY] 100");String path="equipment/title.equ",text="[equipment type] `[title name]` [custom animation] `title.ani`";
        var p=GamePreview.load(path,text,d::get,d.keySet(),new Pixels(),GamePreview.Options.defaults());
        var right=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);var left=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(boolean mirror:new boolean[]{false,true}){var target=mirror?left:right;var g=target.createGraphics();for(var draw:p.render(0,mirror).draws())draw.prepared().paint(g,30+draw.x(),30+draw.y(),1,draw.mirrored());g.dispose();}
        for(int y=0;y<28;y++)for(int x=0;x<64;x++)assertEquals(right.getRGB(x,y),left.getRGB(x,y),"Title text must not mirror");
    }
    @Test void mageJolUsesVerifiedAct1ObjectBindingAndOtherVersionsKeepItDisabled() throws Exception {
        var d=data();d.put("character/mage/mage.chr","[waiting motion] `body.ani`");d.put("character/mage/body.ani",d.get("character/gunner/body.ani"));d.put("passiveobject/passiveobject.lst","23008 `character/mage/launchjol.obj`");d.put("passiveobject/character/mage/launchjol.obj","[basic motion] `orb.ani`");d.put("passiveobject/character/mage/orb.ani","[FRAME000] [IMAGE] `blue.img` 0 [DELAY] 100");
        String path="skill/mage/launchjol.skl",text="[name] `JOL`";
        var preview=GamePreview.load(path,text,d::get,d.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertTrue(preview.effects().stream().anyMatch(e->e.key().equals("passive:23008")));
        assertTrue(preview.render(0,false).draws().stream().anyMatch(e->e.prepared().source().img().equals("blue.img")));
        var other=GamePreview.load(path,text,d::get,d.keySet(),new Pixels(),new GamePreview.Options("mage","waiting motion",Map.of(),1,false,""));assertTrue(other.effects().isEmpty());
    }
    @Test void repeatingSkillDoesNotRestartTheIndependentTitleClock() throws Exception {
        var d=data();d.put("equipment/title.equ","[equipment type] `[title name]` [custom animation] `title.ani`");d.put("equipment/title.ani","[LOOP] 1 [FRAME000] [IMAGE] `red.img` 0 [DELAY] 100 [FRAME001] [IMAGE] `blue.img` 0 [DELAY] 200");
        var p=GamePreview.load("skill/gunner/laserrifle.skl","[static data] 200 150",d::get,d.keySet(),new Pixels(),new GamePreview.Options("gunner","waiting motion",Map.of("title name","equipment/title.equ"),1,true,""));
        assertEquals(400,p.duration());var frame=p.render(450,false,true);
        assertTrue(frame.draws().stream().anyMatch(draw->draw.label().equals("body")&&draw.prepared().source().img().equals("red.img")));
        assertTrue(frame.draws().stream().anyMatch(draw->draw.label().equals("title name")&&draw.prepared().source().img().equals("blue.img")));
    }
    private static BufferedImage paint(GamePreview.Rendered frame){var img=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);var g=img.createGraphics();for(var d:frame.draws())d.prepared().paint(g,10+d.x(),10+d.y(),1,d.mirrored());g.dispose();return img;}
    private static class Pixels implements ScenePreview.SpriteLookup {
        public NpkResources.Info info(String path,int frame){return new NpkResources.Info(3,3,0,0,3,3);}
        public NpkResources.Sprite sprite(String path,int frame){var image=new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(path.equals("red.img")?Color.RED:path.equals("green.img")?Color.GREEN:Color.BLUE);g.fillRect(0,0,3,3);if(path.equals("title.img")){g.setColor(Color.RED);g.fillRect(0,0,1,3);}g.dispose();return new NpkResources.Sprite(image,0,0,3,3,Path.of("fixture.npk"),path,frame,frame);}
    }
}
