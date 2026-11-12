package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GamePreviewTest {
    @Test void actorBoxUsesCurrentFrameAndIncludesOnlyMountedAttackLayers() throws Exception {
        var files=data();
        files.put("character/gunner/body.ani","""
                [FRAME000] [IMAGE] `body.img` 0 [DELAY] 50
                [DAMAGE BOX] -10 -5 0 25 10 100
                [FRAME001] [IMAGE] `body.img` 1 [DELAY] 300
                [DAMAGE BOX] -10 -5 0 25 10 100
                """);
        files.put("equipment/coat.ani","""
                [FRAME000] [IMAGE] `coat.img` 0 [DELAY] 150
                [FRAME001] [IMAGE] `` 0 [DELAY] 200
                [DAMAGE BOX] 0 -2 0 4 4 10
                [ATTACK BOX] 6 -10 35 39 20 38
                """);
        var preview=GamePreview.load("equipment/coat.equ","[equipment type] `[coat avatar]` [waiting motion] `coat.ani`",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertTrue(preview.render(50,false).combatVolumes().isEmpty());
        assertEquals(1,preview.render(49,false,false,true).combatVolumes().size());
        var boxes=preview.render(50,true,false,true).combatVolumes();
        assertEquals(2,boxes.size());
        var attack=boxes.stream().filter(b->b.kind()==CombatDebug.Kind.ATTACK).findFirst().orElseThrow();
        assertEquals(new CombatFrame.Box(-45,-10,35,39,20,38),attack.orientedBox());
    }

    @Test void armorContourUsesTheBodyStateAndPrecedesTheWornLayers() throws Exception {
        var files=data();
        files.put("character/gunner/body.ani","""
                [FRAME000] [IMAGE] `body.img` 0 [DELAY] 100 [DAMAGE TYPE] `SUPERARMOR`
                [FRAME001] [IMAGE] `body.img` 1 [DELAY] 400 [DAMAGE TYPE] `UNBREAKABLE`
                """);
        files.put("equipment/coat.ani","""
                [FRAME000] [IMAGE] `coat.img` 0 [DELAY] 100
                [FRAME001] [IMAGE] `coat.img` 1 [DELAY] 400
                """);
        var preview=GamePreview.load("equipment/coat.equ","[equipment type] `[coat avatar]` [waiting motion] `coat.ani`",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        var first=preview.render(20,true).draws();
        assertEquals(List.of(true,true,false,false),first.stream().map(GamePreview.Draw::armorOutline).toList());
        assertTrue(first.stream().allMatch(GamePreview.Draw::mirrored));
        assertEquals("LINEARDODGE",first.get(0).prepared().effect());
        assertTrue(preview.render(150,false).draws().stream().anyMatch(GamePreview.Draw::armorOutline));
        assertFalse(preview.render(200,false).draws().stream().anyMatch(GamePreview.Draw::armorOutline));
    }

    @Test void camouflageHidesTheEquippedTitleUntilTheActorReappears() throws Exception {
        var files=Act1GunnerSupportCastTest.fixture();
        files.put("character/gunner/gunner.chr","[waiting motion] `Animation/Stay.ani` "
                +"[throw motion 2-1] `Animation/Command1.ani` [throw motion 2-2] `Animation/Command2.ani`");
        files.put("equipment/title.equ","[equipment type] `[title name]` [custom animation] `title.ani`");
        files.put("equipment/title.ani",Act1GunnerSupportCastTest.ani());
        var options=new GamePreview.Options("gunner","waiting motion",Map.of("title name","equipment/title.equ"),1,true,"");
        var preview=GamePreview.load("skill/gunner/camouflage.skl",
                "[casting time] 700 [static data] 500 [level info] 1 6000",files::get,files.keySet(),new Pixels(),options);
        assertTrue(preview.render(500,false).draws().stream().anyMatch(d->d.label().equals("title name")));
        assertFalse(preview.render(900,false).draws().stream().anyMatch(d->d.label().equals("title name")));
        assertTrue(preview.render(6750,false).draws().stream().anyMatch(d->d.label().equals("title name")));
    }

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

    @Test void bulletGuardUsesTheBodyPoseThatMatchesItsClothes() throws Exception {
        var files=data();
        files.put("character/gunner/gunner.chr","[etc motion] `Animation/BulletGuard.ani`");
        files.put("character/gunner/animation/bulletguard.ani","""
                [FRAME000] [IMAGE] `Character/Gunner/Equipment/Avatar/skin/gn_body%04d.img` 180 [DELAY] 10
                [FRAME001] [IMAGE] `Character/Gunner/Equipment/Avatar/skin/gn_body%04d.img` 186 [DELAY] 190
                """);
        var preview=GamePreview.load("skill/gunner/bulletguard.skl","",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertEquals(185,preview.render(0,false).draws().get(0).prepared().source().requestedFrame());
        assertEquals(186,preview.render(10,false).draws().get(0).prepared().source().requestedFrame());
    }

    @Test void groundHeavyWeaponParticlesUseTheFiringOrigin() throws Exception {
        var files=data();
        files.put("skill/gunnerskill.lst","12 `Gunner/Gatling.skl` 13 `Gunner/FlameThrower.skl`");
        files.put("character/gunner/gunner.chr","[etc motion] `SubWeaponFrontRapid1.ani` `SubWeaponFrontRapid2.ani`");
        String pose="[FRAME000] [IMAGE] `body.img` 0 [DELAY] 400";
        files.put("character/gunner/subweaponfrontrapid1.ani",pose);
        files.put("character/gunner/subweaponfrontrapid2.ani",pose);
        String particle="""
                [life time] 600
                [creation frequency] 10
                [object type] `[animation]`
                [object] `muzzle.img` 0 [/object]
                """;
        files.put("character/gunner/particle/subweapon/gatlingfront.ptl",particle);
        files.put("character/gunner/effect/particle/flamethrower.ptl",particle);
        for(String skill:List.of("gatling","flamethrower")){
            boolean gatling=skill.equals("gatling");
            var preview=GamePreview.load("skill/gunner/"+skill+".skl",
                    "[static data] "+(gatling?"7 400":"400 400")+" [level info] 3 1 3000 3000",
                    files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
            var shot=preview.render(400,false).draws().stream()
                    .filter(d->d.prepared().source().img().equals("muzzle.img")).findFirst().orElseThrow();
            assertEquals(gatling?175:232,shot.x());
            assertEquals(gatling?-57:-88,shot.y());
            assertEquals(gatling?Double.NaN:141,shot.clipX());
            var left=preview.render(400,true).draws().stream()
                    .filter(d->d.prepared().source().img().equals("muzzle.img")).findFirst().orElseThrow();
            assertEquals(-shot.x(),left.x());
            assertEquals(-shot.clipX(),left.clipX());
        }
    }

    @Test void sayaShowsItsWorldRangeThroughTheWholeArrayLifetime() throws Exception {
        var files=swordAreaFiles();
        files.put("skill/swordmanskill.lst","36 `swordman/saya.skl`");
        files.put("passiveobject/passiveobject.lst","20012 `sword/saya.obj`");
        files.put("passiveobject/sword/saya.obj","[etc motion] `effect.ani` `effect.ani` `effect.ani` `effect.ani` `effect.ani` `effect.ani`");
        files.put("passiveobject/sword/effect.ani",files.get("character/swordman/body.ani"));
        var model=GamePreview.load("skill/swordman/saya.skl","[casting time] 500 [static data] 600 250 70 1000 [level info] 5 100 3000 0 0 0",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertTrue(attacksAt(model,499,false).isEmpty());
        assertTrue(model.render(500,false).combatVolumes().isEmpty());
        var area=attacksAt(model,500,false).get(0);
        assertEquals(122,area.x());
        assertEquals(500,area.box().width());
        assertEquals(145,area.box().depth());
        assertEquals(70,area.box().height());
        assertEquals(-122,attacksAt(model,1500,true).get(0).x());
        assertFalse(attacksAt(model,3499,false).isEmpty());
        assertTrue(attacksAt(model,3500,false).isEmpty());
    }

    @Test void killingIntentUsesTheSelectedLevelDiameterAndGroundHeight() throws Exception {
        var files=swordAreaFiles();
        files.put("skill/swordmanskill.lst","52 `swordman/dotarea.skl`");
        files.put("character/swordman/effect/particle/dotareaquake.ptl","[life time] 100 [max particle] 1");
        var model=GamePreview.load("skill/swordman/dotarea.skl","[casting time] 500 [static data] 50 50 [level info] 4 756 300 10 10 846 306 15 15",
                files::get,files.keySet(),new Pixels(),new GamePreview.Options("swordman","waiting motion",Map.of(),2,true,""));
        assertTrue(attacksAt(model,499,false).isEmpty());
        var area=attacksAt(model,500,false).get(0);
        assertEquals(0,area.x());
        assertEquals(306,area.box().width());
        assertEquals(306*.29,area.box().depth(),.001);
        assertEquals(50,area.box().height());
        assertFalse(attacksAt(model,2299,false).isEmpty());
        assertTrue(attacksAt(model,2300,false).isEmpty());
    }

    private static Map<String,String> swordAreaFiles(){
        var files=new HashMap<String,String>();
        files.put("character/swordman/swordman.chr","[waiting motion] `body.ani` [buff motion] `body.ani` [throw motion 1-1] `body.ani` [throw motion 1-2] `body.ani`");
        files.put("character/swordman/body.ani","[FRAME000] [IMAGE] `red.img` 0 [DELAY] 100");
        return files;
    }

    private static List<CombatDebug.Volume> attacksAt(GamePreview model,long time,boolean mirrored)throws IOException {
        return model.render(time,mirrored,false,true).combatVolumes().stream()
                .filter(v->v.kind()==CombatDebug.Kind.ATTACK).toList();
    }

    @Test void gaebolgShowsAndMovesTheFistThroughTheGate() throws Exception {
        var files=data();
        files.put("skill/gunnerskill.lst","67 `Gunner/GaebolgPunch.skl`");
        files.put("character/gunner/gunner.chr","[etc motion] `GaebolgPunch.ani`");
        files.put("character/gunner/gaebolgpunch.ani","[FRAME000] [IMAGE] `body.img` 0 [DELAY] 80");
        files.put("passiveobject/passiveobject.lst","22228 `gunner/punch.obj`");
        files.put("passiveobject/gunner/punch.obj","[etc motion] `gate.ani` `dodge.ani` `fist.ani` `hit.ani` `electric.ani` `shoot.ani`");
        for(String name:List.of("gate","dodge","fist","hit","electric","shoot")){
            files.put("passiveobject/gunner/"+name+".ani",
                    "[FRAME000] [IMAGE] `"+name+".img` 0 [IMAGE POS] -457 -150 [DELAY] 800");
        }
        files.put("passiveobject/gunner/hit.ani",
                "[FRAME000] [IMAGE] `` 0 [DELAY] 70 [ATTACK BOX] -457 -35 8 457 70 123");
        files.put("data/skill/gunner.sd","[int] `gp punch show time` 600 `gp punch time` 800 "
                + "`gp blow time` 160 `gp stop time` 600 `gp hit time` 260 `gp gather time` 240 [float] `gp size rate` 1.5");
        var preview=GamePreview.load("skill/gunner/gaebolgpunch.skl","",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertTrue(preview.render(639,false).draws().stream().noneMatch(d->d.label().equals("盖波加拳击")));
        assertFalse(preview.render(879,false,false,true).combatVolumes().stream().anyMatch(v->v.kind()==CombatDebug.Kind.ATTACK));
        var attack=preview.render(900,false,false,true).combatVolumes().stream()
                .filter(v->v.kind()==CombatDebug.Kind.ATTACK).findFirst().orElseThrow();
        var reflected=preview.render(900,true,false,true).combatVolumes().stream()
                .filter(v->v.kind()==CombatDebug.Kind.ATTACK).findFirst().orElseThrow();
        assertEquals(425,attack.x()+attack.orientedBox().x(),.01);
        assertEquals(-425,reflected.x()+reflected.orientedBox().x()+reflected.box().width(),.01);
        assertFalse(attacksAt(preview,1139,false).isEmpty());
        assertTrue(attacksAt(preview,1140,false).isEmpty());
        double first=fist(preview,900,false).x();
        var extended=fist(preview,1060,false);
        var mirrored=fist(preview,1060,true);
        assertTrue(extended.x()>first+100);
        assertEquals(-extended.x(),mirrored.x());
        assertEquals(425,extended.clipX());
        assertEquals(-extended.clipX(),mirrored.clipX());
        assertTrue(fist(preview,1800,false).x()<extended.x());
        assertTrue(preview.render(1920,false).draws().stream().noneMatch(d->d.label().equals("盖波加拳击")));
    }

    @Test void forwardMaskClipsOnlyTheHiddenPixelsInBothDirections() {
        var sprite=new Pixels().sprite("blue.img",0);
        var prepared=PreviewRenderer.prepare(ResourceScript.Frame.still("blue.img",0),sprite);
        var target=new BufferedImage(20,8,BufferedImage.TYPE_INT_ARGB);
        var graphics=target.createGraphics();
        try {
            new GamePreview.Draw(prepared,0,0,false,"fist",1,1).paint(graphics,10,0,1);
            new GamePreview.Draw(prepared,0,4,true,"fist",1,-1).paint(graphics,10,0,1);
        }finally{graphics.dispose();}
        assertEquals(0,target.getRGB(10,0));
        assertEquals(Color.BLUE.getRGB(),target.getRGB(11,0));
        assertEquals(Color.BLUE.getRGB(),target.getRGB(8,4));
        assertEquals(0,target.getRGB(9,4));
    }

    private static GamePreview.Draw fist(GamePreview preview,long time,boolean mirrored) throws Exception {
        return preview.render(time,mirrored).draws().stream()
                .filter(d->d.prepared().source().img().equals("fist.img")).findFirst().orElseThrow();
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

    @Test void mageUpperSwingPlaysItsActorAttachedEffectOnTheNativeClock() throws Exception {
        var files=data();
        String path="skill/mage/upperswing.skl";
        files.put("skill/mageskill.lst","12 `Mage/UpperSwing.skl`");
        files.put("character/mage/mage.chr","[etc motion] `First.ani` `Second.ani` `Animation/UpperSwing.ani`");
        files.put("character/mage/animation/upperswing.ani","[FRAME000] [IMAGE] `body.img` 0 [DELAY] 680");
        files.put("character/mage/effect/animation/upperswing.ani","""
                [FRAME000] [DELAY] 320
                [FRAME001] [IMAGE] `upper.img` 0 [IMAGE POS] 50 -60 [DELAY] 360
                """);
        var options=GamePreview.Options.defaults();
        var preview=GamePreview.load(path,"[type] `[active]`",files::get,files.keySet(),new Pixels(),options);
        assertEquals(List.of("body.img"),preview.render(319,false).draws().stream().map(d->d.prepared().source().img()).toList());
        var strike=preview.render(320,true).draws();
        assertEquals(List.of("body.img","upper.img"),strike.stream().map(d->d.prepared().source().img()).toList());
        assertEquals(0,strike.get(1).x(),.001);
        assertEquals(0,strike.get(1).y());
        assertTrue(strike.get(1).mirrored());
        var other=GamePreview.load(path,"[type] `[active]`",files::get,files.keySet(),new Pixels(),
                new GamePreview.Options("mage","etc motion:2",Map.of(),1,false,"actor"));
        assertEquals(List.of("body.img"),other.render(320,false).draws().stream().map(d->d.prepared().source().img()).toList());
    }

    @Test void grabExplosionShowsChargeOnlyAfterTheGrabFrame() throws Exception {
        var files=data();
        String path="skill/fighter/grabexplosion.skl";
        files.put("skill/fighterskill.lst","3 `Fighter/GrabExplosion.skl`");
        String motions="`Unused.ani` ".repeat(14)+"`Animation/GrabExplosion.ani`";
        files.put("character/fighter/fighter.chr","[etc motion] "+motions);
        var body=new StringBuilder();
        for(int i=0;i<14;i++){
            body.append(String.format(Locale.ROOT,"[FRAME%03d] [IMAGE] `body.img` %d [DELAY] %d%n",i,i,i==8?10000:10));
        }
        files.put("character/fighter/animation/grabexplosion.ani",body.toString());
        for(int i=1;i<=3;i++){
            files.put("character/fighter/effect/animation/grabexplosion"+i+".ani",
                    "[FRAME000] [IMAGE] `charge.img` 0 [DELAY] 100");
        }
        files.put("passiveobject/passiveobject.lst","21014 `character/fighter/grabexplosion.obj`");
        files.put("passiveobject/character/fighter/grabexplosion.obj","[string data] `Flame.ptl` [/string data]");
        files.put("passiveobject/character/fighter/flame.ptl","""
                [life time] 300 [creation frequency] -1 [maximum create number] 1
                [object type] `[animation]` [object] `blast.img` 0 [/object]
                """);
        var preview=GamePreview.load(path,"[type] `[active]`",files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertTrue(preview.render(79,false).draws().stream().noneMatch(d->d.prepared().source().img().equals("charge.img")));
        assertTrue(preview.render(80,false).draws().stream().anyMatch(d->d.prepared().source().img().equals("charge.img")));
        assertTrue(preview.duration()<10000,"The runtime wait frame must have a finite preview duration");
        var impact=preview.render(120,false).draws();
        assertTrue(impact.stream().noneMatch(d->d.prepared().source().img().equals("charge.img")));
        var flame=impact.stream().filter(d->d.prepared().source().img().equals("blast.img")).findFirst().orElseThrow();
        assertEquals(91,flame.x(),.001);
        var casting=preview.render(120,true).draws().stream().filter(d->d.label().equals("body")).findFirst().orElseThrow();
        assertEquals(-32,casting.x(),.001);
        assertEquals(-95,flame.y());
        var miss=GamePreview.load(path,"[type] `[active]`",files::get,files.keySet(),new Pixels(),
                GamePreview.Options.defaults().withEffect("grabexplosion:miss"));
        assertEquals(80,miss.duration());
        assertTrue(miss.render(79,false).draws().stream().allMatch(d->d.label().equals("body")));
    }

    @Test void nenShotUsesItsLaunchFrameAndMovesUntilTheSelectedLevelLifetime() throws Exception {
        var files=data();
        String path="skill/fighter/energyball.skl";
        files.put("skill/fighterskill.lst","12 `Fighter/EnergyBall.skl`");
        files.put("character/fighter/fighter.chr","""
                [waiting motion] `Stay.ani`
                [throw motion 2-1] `Throw1.ani`
                [throw motion 2-2] `Shoot.ani`
                """);
        files.put("character/fighter/stay.ani","[FRAME000] [IMAGE] `stay.img` 0 [DELAY] 100");
        files.put("character/fighter/throw1.ani","[FRAME000] [IMAGE] `cast.img` 0 [DELAY] 350");
        files.put("character/fighter/shoot.ani","[FRAME000] [IMAGE] `release.img` 0 [DELAY] 80");
        files.put("character/fighter/effect/animation/energycharge.ani","[LOOP] 1 [FRAME000] [IMAGE] `charge.img` 0 [DELAY] 70");
        files.put("character/fighter/effect/animation/energyballshoot.ani","""
                [FRAME000] [DELAY] 80
                [FRAME001] [IMAGE] `shoot.img` 0 [DELAY] 80
                """);
        files.put("character/fighter/particle/energyball.ptl","[x axis] [move type] `[straight line]` [move variable 1] 400 400");
        files.put("passiveobject/passiveobject.lst","21002 `character/fighter/energyball.obj`");
        files.put("passiveobject/character/fighter/energyball.obj","[basic motion] `EnergyBall.ani`");
        files.put("passiveobject/character/fighter/energyball.ani","[LOOP] 1 [FRAME000] [IMAGE] `ball.img` 0 [DELAY] 500");
        var preview=GamePreview.load(path,"[casting time] 250 250 [static data] 500 [level info] 5 525 0 0 0 125",
                files::get,files.keySet(),new Pixels(),GamePreview.Options.defaults());
        assertTrue(preview.render(249,false).draws().stream().anyMatch(d->d.prepared().source().img().equals("cast.img")));
        assertTrue(preview.render(250,false).draws().stream().anyMatch(d->d.prepared().source().img().equals("release.img")));
        assertFalse(preview.render(329,false).draws().stream().anyMatch(d->d.prepared().source().img().equals("ball.img")));
        var launched=preview.render(330,false).draws().stream()
                .filter(d->d.prepared().source().img().equals("ball.img")).findFirst().orElseThrow();
        assertEquals(70,launched.x());
        assertEquals(-63,launched.y());
        assertEquals(1.25,launched.prepared().transform().getScaleX(),.001);
        var moving=preview.render(530,true).draws().stream()
                .filter(d->d.prepared().source().img().equals("ball.img")).findFirst().orElseThrow();
        assertEquals(-150,moving.x());
        assertFalse(preview.render(855,false).draws().stream().anyMatch(d->d.prepared().source().img().equals("ball.img")));
    }

    @Test void spiralNenFieldStartsWithThePushAndStaysAtItsSpawnPoint() throws Exception {
        var files=data();
        String path="skill/fighter/kikouken.skl";
        files.put("skill/fighterskill.lst","16 `Fighter/Kikouken.skl`");
        files.put("character/fighter/fighter.chr","""
                [waiting motion] `Stay.ani`
                [throw motion 2-1] `Throw1.ani`
                [throw motion 2-2] `Shoot.ani`
                """);
        files.put("character/fighter/stay.ani","[FRAME000] [IMAGE] `stay.img` 0 [DELAY] 100");
        files.put("character/fighter/throw1.ani","[FRAME000] [IMAGE] `cast.img` 0 [DELAY] 350");
        files.put("character/fighter/shoot.ani","[FRAME000] [IMAGE] `push.img` 0 [DELAY] 700");
        files.put("character/fighter/effect/animation/energycharge.ani","[LOOP] 1 [FRAME000] [IMAGE] `charge.img` 0 [DELAY] 70");
        files.put("passiveobject/passiveobject.lst","21006 `Character/Fighter/EnergyField.obj`");
        files.put("passiveobject/character/fighter/energyfield.obj","[basic motion] `Animation/EnergyField.ani`");
        files.put("passiveobject/character/fighter/animation/energyfield.ani","[FRAME000] [IMAGE] `field.img` 0 [DELAY] 1040");
        var model=GamePreview.load(path,"""
                [casting time] 700 700
                [static data] 1500
                [maximum level] 2
                [level info] 7
                85 150 40 7 100 7000 83
                89 158 42 7 150 7000 92
                [/level info]
                """,files::get,files.keySet(),new Pixels(),new GamePreview.Options("fighter","waiting motion",Map.of(),2,true,""));

        var charging=model.render(699,false).draws().stream().map(d->d.prepared().source().img()).toList();
        assertTrue(charging.containsAll(List.of("cast.img","charge.img")));
        assertFalse(charging.contains("field.img"));
        var pushed=model.render(700,false).draws();
        assertTrue(pushed.stream().anyMatch(d->d.prepared().source().img().equals("push.img")));
        assertFalse(pushed.stream().anyMatch(d->d.prepared().source().img().equals("charge.img")));
        var field=pushed.stream().filter(d->d.prepared().source().img().equals("field.img")).findFirst().orElseThrow();
        assertEquals(107,field.x());
        assertEquals(-61,field.y());
        assertEquals(1.5,field.prepared().transform().getScaleX(),.001);
        var mirrored=model.render(1739,true).draws().stream()
                .filter(d->d.prepared().source().img().equals("field.img")).findFirst().orElseThrow();
        assertEquals(-107,mirrored.x());
        assertEquals(-61,mirrored.y());
        assertTrue(mirrored.mirrored());
        assertFalse(model.render(1740,false).draws().stream().anyMatch(d->d.prepared().source().img().equals("field.img")));
        assertEquals(2200,model.duration());
    }

    @Test void backstepUsesTheAirborneAndLandingFramesAndMovesOppositeFacing() throws Exception {
        var files=data();
        String path="skill/mage/backstep.skl";
        files.put("skill/mageskill.lst","169 `Mage/BackStep.skl`");
        files.put("character/mage/mage.chr","""
                [jump power] 350.0
                [jump motion] `Animation/Jump.ani`
                [waiting motion] `Stay.ani`
                """);
        files.put("character/mage/stay.ani","[FRAME000] [IMAGE] `body.img` 4 [DELAY] 100");
        files.put("character/mage/animation/jump.ani","""
                [FRAME000] [IMAGE] `body.img` 1 [DELAY] 80
                [FRAME001] [IMAGE] `body.img` 2 [DELAY] 10000
                [FRAME002] [IMAGE] `body.img` 3 [DELAY] 90
                """);
        files.put("common/commoneffect/particle/dustdownsmall.ptl","""
                [creation pos] 320 270 50 [life time] 700
                [creation frequency] -1 [maximum create number] 1
                [object] `dust.img` 0 [/object]
                """);
        var model=GamePreview.load(path,"[static data] 200",files::get,files.keySet(),new Pixels(),
                GamePreview.Options.defaults());
        var airborne=model.render(200,false).draws().get(0);
        assertEquals(2,airborne.prepared().source().requestedFrame());
        assertEquals(-57.2,airborne.x(),.001);
        assertTrue(airborne.y()<-45);
        var other=model.render(200,true).draws().get(0);
        assertEquals(-airborne.x(),other.x());
        assertEquals(airborne.y(),other.y());
        var landing=model.render(550,false).draws().get(0);
        assertEquals(3,landing.prepared().source().requestedFrame());
        assertEquals(0,landing.y(),.001);
        assertTrue(landing.x()<-140);
        var dust=model.render(550,false).draws().stream().filter(d->d.label().equals("后跳 · 落地扬尘")).findFirst().orElseThrow();
        assertEquals(landing.x(),dust.x(),.001);
        assertEquals(0,dust.y(),.001);
        assertTrue(model.render(1200,false).draws().stream().anyMatch(d->d.label().equals("body")));
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
    @Test void shortLaserWithThePassiveUsesItsReleaseLayersAndRecoil() throws Exception {
        var preview=laserFixture("laser:normal");
        var ready=laserImages(preview,225);
        assertTrue(ready.indexOf("rapid1-b.img")<ready.indexOf("chargingeffect.img"));
        assertTrue(ready.indexOf("chargingeffect.img")<ready.indexOf("rapid1-a.img"));
        assertTrue(laserImages(preview,298).contains("body1.img"));
        var release=laserImages(preview,299);
        assertTrue(release.containsAll(List.of("body2.img","rapid2-b.img","rapid2-a.img","fireoncespark.img","firering.img","laserfire.img")));
        assertFalse(release.contains("fireonceeffect.img"));
        assertFalse(release.contains("firechargeeffect.img"));
        assertTrue(release.indexOf("body2.img")<release.indexOf("rapid2-b.img"));
        assertTrue(release.indexOf("rapid2-a.img")<release.indexOf("fireoncespark.img"));
        assertTrue(release.indexOf("fireoncespark.img")<release.indexOf("firering.img"));
        assertTrue(laserImages(preview,798).contains("laserfire.img"));
        var recovery=laserImages(preview,799);
        assertFalse(recovery.contains("laserfire.img"));
        assertFalse(recovery.contains("rapid2-a.img"));
        assertTrue(recovery.containsAll(List.of("body-recovery.img","once2-a.img","once2-b.img","firechargeeffect.img","firechargespark.img")));
        assertTrue(recovery.indexOf("firechargeeffect.img")<recovery.indexOf("body-recovery.img"));
        assertTrue(recovery.indexOf("once2-a.img")<recovery.indexOf("firechargespark.img"));
        var recoiling=preview.render(899,false).draws();
        assertEquals(-16.4,recoiling.stream().filter(d->d.label().equals("body")).findFirst().orElseThrow().x(),1e-9);
        assertEquals(130,recoiling.stream().filter(d->d.prepared().source().img().equals("turningshoot.img")).findFirst().orElseThrow().x());
    }

    @Test void chargedLaserShowsTheThresholdFlashBeforeReleasingThenUsesTheSameRecovery() throws Exception {
        var preview=laserFixture("laser:charged");
        assertFalse(preview.render(224,false).draws().stream().anyMatch(d->d.label().equals("蓄力光效")));
        assertTrue(preview.render(225,false).draws().stream().anyMatch(d->d.label().equals("蓄力光效")));
        var held=preview.render(999,false).draws();
        assertTrue(held.stream().anyMatch(d->d.prepared().source().img().equals("rapid1-a.img")&&d.prepared().source().requestedFrame()==4));
        assertFalse(laserImages(preview,999).contains("laserriflefullcharge1.img"));
        assertTrue(laserImages(preview,1000).containsAll(List.of("laserriflefullcharge1.img","laserriflefullcharge2.img","chargingeffect.img")));
        assertTrue(laserImages(preview,1149).contains("laserriflefullcharge2.img"));
        var release=laserImages(preview,1150);
        assertTrue(release.containsAll(List.of("rapid2-a.img","rapid2-b.img","chargedlaserfire.img","firering.img")));
        assertFalse(release.contains("chargingeffect.img"));
        assertFalse(release.contains("fireoncespark.img"));
        assertFalse(release.contains("firechargeeffect.img"));
        assertTrue(laserImages(preview,1849).contains("chargedlaserfire.img"));
        assertTrue(laserImages(preview,1850).containsAll(List.of("once2-a.img","firechargeeffect.img","firechargespark.img")));
        assertFalse(laserImages(preview,1850).contains("chargedlaserfire.img"));
    }

    @Test void normalLaserWithoutThePassiveSkipsTheChargingAndShortTapAttachments() throws Exception {
        var preview=laserFixture("");
        assertFalse(laserImages(preview,225).contains("chargingeffect.img"));
        var release=laserImages(preview,299);
        assertTrue(release.containsAll(List.of("rapid2-a.img","laserfire.img","firering.img")));
        assertFalse(release.contains("fireoncespark.img"));
        assertEquals(-37.5,preview.render(899,false).draws().stream()
                .filter(draw->draw.label().equals("body")).findFirst().orElseThrow().x(),1e-9);
        assertTrue(preview.effects().stream().anyMatch(effect->effect.key().equals("laser:normal")));
        var missingPassive=laserFixture("laser:unlearned",false);
        assertFalse(missingPassive.effects().stream().anyMatch(effect->effect.key().equals("laser:charged")||effect.key().equals("laser:normal")));
    }

    private static List<String> laserImages(GamePreview preview,long time) throws IOException {
        return preview.render(time,false).draws().stream().map(d->d.prepared().source().img()).toList();
    }

    private static GamePreview laserFixture(String choice) throws IOException {
        return laserFixture(choice,true);
    }

    private static GamePreview laserFixture(String choice,boolean hasPassive) throws IOException {
        var d=data();
        String root="character/gunner/",gun=root+"effect/animation/subweapon/",effects=gun+"laserrifleeffect/";
        d.put(root+"gunner.chr","[waiting motion] `body.ani` [etc motion] "+"`body.ani` ".repeat(16)
                +"`Animation/SubWeaponFrontOnce2.ani` `Animation/SubWeaponFrontRapid1.ani` `Animation/SubWeaponFrontRapid2.ani` [/etc motion]");
        d.put(root+"animation/subweaponfrontrapid1.ani",laserAni("body1",100,100,100,50,50));
        d.put(root+"animation/subweaponfrontrapid2.ani","[LOOP] 1 "+laserAni("body2",100,100));
        d.put(root+"animation/subweaponfrontonce2.ani",laserAni("body-recovery",100,50,250));
        for(String side:List.of("a","b")){
            d.put(gun+"subweaponfrontrapid1"+side+".ani",laserAni("rapid1-"+side,100,100,100,50,50));
            d.put(gun+"subweaponfrontrapid2"+side+".ani","[LOOP] 1 "+laserAni("rapid2-"+side,100,100));
            d.put(gun+"subweaponfrontonce2"+side+".ani",laserAni("once2-"+side,100,50,250));
        }
        for(String fx:List.of("fireonceeffect","fireoncespark","firechargeeffect","firechargespark","firering","chargingeffect")){
            d.put(effects+fx+".ani",laserAni(fx,100,100,100,100,100));
        }
        for(int side=1;side<=2;side++){
            String fx="laserriflefullcharge"+side;
            d.put(effects+fx+".ani",laserAni(fx,40,40,40,40,40));
        }
        String obj="passiveobject/character/gunner/";
        d.put("passiveobject/passiveobject.lst","22017 `character/gunner/subweaponbulletlaser.obj`");
        d.put(obj+"subweaponbulletlaser.obj","[etc motion] `Animation/LaserRifle/LaserFire.ani` `Animation/LaserRifle/LaserTile.ani` `Animation/LaserRifle/LaserEnd.ani` "
                +"`Animation/LaserRifle/ChargedLaserFire.ani` `Animation/LaserRifle/ChargedLaserTile.ani` `Animation/LaserRifle/ChargedLaserEnd.ani` [/etc motion]");
        for(String fx:List.of("laserfire","lasertile","laserend","chargedlaserfire","chargedlasertile","chargedlaserend")){
            int length=fx.startsWith("charged")?700:500;
            d.put(obj+"animation/laserrifle/"+fx+".ani",laserAni(fx,length));
        }
        d.put(root+"effect/animation/turningshoot.ani",laserAni("turningshoot",80,80));
        if(hasPassive)d.put("skill/gunner/laserriflecharge.skl","[static data] 50 [/static data] [level info] 2 1000 40 [/level info]");
        return GamePreview.load("skill/gunner/laserrifle.skl","[static data] 300 150",d::get,d.keySet(),new Pixels(),GamePreview.Options.defaults().withEffect(choice));
    }

    private static String laserAni(String image,int... delays) {
        var text=new StringBuilder();
        for(int frame=0;frame<delays.length;frame++){
            text.append("[FRAME%03d] [IMAGE] `%s.img` %d [DELAY] %d ".formatted(frame,image,frame,delays[frame]));
        }
        return text.toString();
    }

    private static BufferedImage paint(GamePreview.Rendered frame){var img=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);var g=img.createGraphics();for(var d:frame.draws())d.paint(g,10,10,1);g.dispose();return img;}
    private static class Pixels implements ScenePreview.SpriteLookup {
        public NpkResources.Info info(String path,int frame){return new NpkResources.Info(3,3,0,0,3,3);}
        public NpkResources.Sprite sprite(String path,int frame){var image=new BufferedImage(3,3,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(path.equals("red.img")?Color.RED:path.equals("green.img")?Color.GREEN:Color.BLUE);g.fillRect(0,0,3,3);if(path.equals("title.img")){g.setColor(Color.RED);g.fillRect(0,0,1,3);}g.dispose();return new NpkResources.Sprite(image,0,0,3,3,Path.of("fixture.npk"),path,frame,frame);}
    }
}
