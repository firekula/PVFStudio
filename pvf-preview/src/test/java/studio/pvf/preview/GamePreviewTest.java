package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GamePreviewTest {
    @Test void clothingFollowsTheBodyFrameDelays() throws Exception {
        var files=data();
        files.put("character/gunner/body.ani","""
                [FRAME000] [IMAGE] `body.img` 0 [DELAY] 125
                [FRAME001] [IMAGE] `body.img` 1 [DELAY] 100
                """);
        files.put("equipment/coat.ani","""
                [FRAME000] [IMAGE] `coat.img` 0 [DELAY] 250
                [FRAME001] [IMAGE] `coat.img` 1 [DELAY] 100
                """);
        var preview=GamePreview.load("equipment/coat.equ","[equipment type] `[coat avatar]` [waiting motion] `coat.ani`",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        for(long time:new long[]{150,375}){
            var frames=preview.render(time,false,true).draws().stream()
                    .map(d->d.prepared().source().requestedFrame()).toList();
            assertEquals(List.of(1,1),frames,"Clothing and body diverged at "+time+" ms");
        }
    }

    @Test void crossShotTransitionsIntoTheFiringPoseAndEffectsAtItsConfiguredDelay() throws Exception {
        var files=data();
        files.put("skill/gunnerskill.lst","48 `Gunner/CrossShoot.skl`");
        files.put("character/gunner/gunner.chr","[etc motion] `CrossShoot.ani`");
        files.put("character/gunner/crossshoot.ani","""
                [FRAME000] [IMAGE] `body.img` 0 [DELAY] 10000
                [FRAME001] [IMAGE] `body.img` 1 [DELAY] 100
                """);
        files.put("character/gunner/effect/animation/shooteffect/crossshoot.ani","[FRAME000] [IMAGE] `flash.img` 0 [DELAY] 100");
        files.put("character/gunner/effect/animation/bullet/crossshoot.ani","[FRAME000] [IMAGE] `shot.img` 0 [DELAY] 100");
        var preview=GamePreview.load("skill/gunner/crossshoot.skl","[static data] 220 600 3",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertEquals(List.of("body.img"),preview.render(219,false).draws().stream().map(d->d.prepared().source().img()).toList());
        var shot=preview.render(220,false).draws();
        assertEquals(List.of("body.img","flash.img","shot.img"),shot.stream().map(d->d.prepared().source().img()).toList());
        assertEquals(1,shot.get(0).prepared().source().requestedFrame());
        assertEquals(820,preview.duration());
    }

    @Test void ordinaryFlameUsesForwardStreaksAndVariesTheParticlePatternPerCast() throws Exception {
        var files=data();
        String particle="character/gunner/effect/particle/flamethrower.ptl";
        files.put(particle,"""
                [x axis] [move type] `[straight line]` [move variable 1] 400 0
                [life time] 600
                [creation frequency] 50
                [rotation angle range type] `[rectangle]`
                [rotation angle variable] 30 30
                [object type] `[animation]`
                [object] `../Animation/FlameThrower1.ani` -1
                [/object]
                """);
        files.put("character/gunner/effect/animation/flamethrower1.ani","""
                [FRAME000] [IMAGE] `jet.img` 0 [IMAGE RATE] 1 0.05 [IMAGE ROTATE] 0 [INTERPOLATION] 1 [DELAY] 500
                [FRAME001] [IMAGE] `jet.img` 0 [IMAGE RATE] 1 1 [IMAGE ROTATE] 1.5 [DELAY] 0
                """);
        var preview=GamePreview.load("skill/gunner/test.skl","[particle] `"+particle+"`",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        var first=preview.render(250,false,true).draws();
        assertTrue(first.size()>3);
        assertTrue(first.stream().allMatch(d->Math.abs(d.prepared().transform().getShearY())<.01));
        var positions=first.stream().map(d->List.of(d.x(),d.y())).toList();
        assertTrue(positions.stream().map(pos->pos.get(1)).distinct().count()>3);
        long next=preview.duration()+250;
        var nextPositions=preview.render(next,false,true).draws().stream().map(d->List.of(d.x(),d.y())).toList();
        assertNotEquals(positions,nextPositions);
        assertEquals(nextPositions,preview.render(next,false,true).draws().stream().map(d->List.of(d.x(),d.y())).toList());
    }

    @Test void focusedFlameKeepsItsJetPointingOutOfTheMuzzle() throws Exception {
        var files=data();
        String particle="character/gunner/effect/particle/flamethrowerboost.ptl";
        String jet="character/gunner/effect/animation/flamethrowerboost1.ani";
        files.put(particle,"""
                [life time] 600
                [creation frequency] -1
                [maximum create number] 2
                [how to create object] `[in order]`
                [object type] `[animation]`
                [object]
                `../Animation/FlameThrowerBoost1.ani` -1
                `../Animation/FlameThrowerBoost2.ani` -1
                [/object]
                """);
        files.put(jet,"""
                [FRAME000] [IMAGE] `jet.img` 0 [IMAGE RATE] 1 0.01 [IMAGE ROTATE] 0 [INTERPOLATION] 1 [DELAY] 500
                [FRAME001] [IMAGE] `jet.img` 0 [IMAGE RATE] 2 0.3 [IMAGE ROTATE] 6.28 [DELAY] 0
                """);
        files.put("character/gunner/effect/animation/flamethrowerboost2.ani","""
                [FRAME000] [IMAGE] `plume.img` 0 [IMAGE ROTATE] 0 [INTERPOLATION] 1 [DELAY] 500
                [FRAME001] [IMAGE] `plume.img` 0 [IMAGE ROTATE] 1.5 [DELAY] 100
                """);
        var preview=GamePreview.load("skill/gunner/test.skl","[particle] `"+particle+"`",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        for(long time:new long[]{125,250,375}){
            var draw=preview.render(time,false).draws().stream()
                    .filter(d->d.prepared().source().img().equals("jet.img")).findFirst().orElseThrow();
            var transform=draw.prepared().transform();
            assertTrue(transform.getScaleX()>0);
            assertEquals(0,transform.getShearY(),.01,"Jet tilted at "+time+" ms");
        }
        var plume=preview.render(250,false).draws().stream()
                .filter(d->d.prepared().source().img().equals("plume.img")).findFirst().orElseThrow();
        assertEquals(Math.sin(.75),plume.prepared().transform().getShearY(),.001);
        var original=new AnimationTimeline(ResourceScript.animation(files.get(jet)));
        assertEquals(3.14,original.sample(250,false).rotation(),.001);
    }

    @Test void skillDefaultsToItsExactCharacterMotionAndLeavesUnresolvedSkillsBlank() throws Exception {
        var data=data();
        data.put("character/gunner/gunner.chr","[waiting motion] `body.ani` [etc motion] `FastKnee.ani`");
        data.put("character/gunner/fastknee.ani","[FRAME000] [IMAGE] `blue.img` 0 [DELAY] 150");
        var fastKnee=GamePreview.load("skill/gunner/fastknee.skl","[type] `[active]`",data::get,data.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertEquals("etc motion",fastKnee.appearance().action());
        assertEquals("blue.img",fastKnee.render(0,false).draws().get(0).prepared().source().img());
        var unknown=GamePreview.load("skill/gunner/unlinked.skl","[type] `[active]`",data::get,data.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertTrue(unknown.render(0,false).draws().isEmpty());
        assertFalse(unknown.unavailableReason().isBlank());
    }

    @Test void explicitSkillAnimationPlaysWithoutSelectingTheResourceManually() throws Exception {
        var data=data();
        data.put("skill/gunner/cast.ani","[FRAME000] [IMAGE] `blue.img` 0 [DELAY] 100");
        var skill=GamePreview.load("skill/gunner/example.skl","[animation] `cast.ani`",data::get,data.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertEquals(java.util.List.of("blue.img"),skill.render(0,false).draws().stream().map(draw->draw.prepared().source().img()).toList());
    }

    @Test void explicitSkillParticleCanBeSoughtAndMirrored() throws Exception {
        var data=data();
        data.put("skill/gunner/cast.ptl","""
                [x axis]
                [move type] `[straight line]`
                [move variable 1] 400 400
                [life time] 600
                [creation frequency] -1
                [maximum create number] 1
                [object type] `[animation]`
                [object] `cast.ani` -1
                [/object]
                """);
        data.put("skill/gunner/cast.ani","[FRAME000] [IMAGE] `blue.img` 0 [DELAY] 100");
        var skill=GamePreview.load("skill/gunner/example.skl","[particle] `cast.ptl`",data::get,data.keySet(),new Pixels(),GamePreview.Options.defaults());
        var start=skill.render(0,false).draws().get(0);
        var next=skill.render(150,false).draws().get(0);
        assertTrue(next.x()>start.x());
        assertEquals(-next.x(),skill.render(150,true).draws().get(0).x(),.01);
        assertEquals("blue.img",next.prepared().source().img());
    }
    private static Map<String,String> data(){var d=new HashMap<String,String>();d.put("character/gunner/gunner.chr","[waiting motion] `body.ani`");d.put("character/gunner/body.ani","[LOOP] 1 [FRAME000] [IMAGE] `red.img` 0 [DELAY] 100 [FRAME001] [IMAGE] `green.img` 0 [DELAY] 100");return d;}
    @Test void loopRepeatsNonLoopingTracksOnTheirOwnClocks() throws Exception {
        var d=data();d.put("character/gunner/body.ani",d.get("character/gunner/body.ani").replace("[LOOP] 1", "[LOOP] 0"));d.put("equipment/title.ani","[FRAME000] [IMAGE] `blue.img` 0 [DELAY] 150 [FRAME001] [IMAGE] `green.img` 0 [DELAY] 150");
        var p=GamePreview.load("equipment/title.equ","[equipment type] `[title name]` [custom animation] `title.ani`",d::get,d.keySet(),new Pixels(),GamePreview.Options.defaults());
        var repeated=p.render(250,false,true);assertTrue(repeated.draws().stream().anyMatch(draw->draw.label().equals("body")&&draw.prepared().source().img().equals("red.img")));assertTrue(repeated.draws().stream().anyMatch(draw->draw.label().equals("title name")&&draw.prepared().source().img().equals("green.img")));
        assertTrue(p.render(250,false,false).draws().stream().anyMatch(draw->draw.label().equals("body")&&draw.prepared().source().img().equals("green.img")));
    }
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
