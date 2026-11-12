package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class Act1GunnerSupplementTest {
    private static final String ROOT="character/gunner/",EFFECT=ROOT+"effect/animation/";
    private static final Map<String,String> ACTIONS=Map.of("etc motion:9","Animation/AerialKnee.ani","etc motion:10","Animation/TryTread.ani","etc motion:11","Animation/TreadShoot.ani","etc motion:21","Animation/SubWeaponUpRapid1.ani","etc motion:22","Animation/SubWeaponUpRapid2.ani","waiting motion","Animation/Stay.ani");
    private static String ani(int... delays){var b=new StringBuilder();for(int i=0;i<delays.length;i++)b.append("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d\n".formatted(i,delays[i]));return b.toString();}
    private static HashMap<String,String> fixture(){
        var f=new HashMap<String,String>();
        f.put("skill/gunnerskill.lst","4 `gunner/aerialknee.skl` 6 `gunner/punisher.skl` 14 `gunner/aerialgatling.skl`");
        f.put("skill/gunner/aerialknee.skl","[level info] 3 100 0 350 106 2 368 111 4 387 117 6 405 122 8 424 [/level info]");
        f.put(ROOT+"gunner.chr","[attack speed] 1000");
        f.put("etc/gunnerdefaultguninfo.etc","[sub type] 0 [integer] 4 4 0 100 100 1000 100 0 0 1000 [/integer]");
        f.put(ROOT+"animation/aerialknee.ani",ani(250,80,300,70,50));f.put(ROOT+"animation/trytread.ani",ani(70,250));
        f.put(ROOT+"animation/treadshoot.ani",ani(100,100,100,100,100,100,100,100,10000,100,80,80,10000,100,100));
        f.put(ROOT+"animation/subweaponuprapid1.ani",ani(100,100,100,50,50));f.put(ROOT+"animation/subweaponuprapid2.ani","[LOOP] 1 "+ani(100,100));
        for(String suffix:new String[]{"a","b"}){f.put(EFFECT+"subweapon/subweaponuprapid1"+suffix+".ani",ani(100,100,100,50,50));f.put(EFFECT+"subweapon/subweaponuprapid2"+suffix+".ani","[LOOP] 1 "+ani(100,100));}
        f.put(EFFECT+"gatlinggunshootup.ani","[LOOP] 1 "+ani(50,50,100));
        f.put(EFFECT+"gatlingshootup1.ani",ani(50,50));f.put(EFFECT+"gatlingshootup2.ani",ani(50,50));
        f.put(EFFECT+"treadshootdust1.ani",ani(100,100,100,100));f.put(EFFECT+"treadshootdust2.ani",ani(100,100,100,100));
        f.put(EFFECT+"shooteffect/treadrevolver.ani",ani(100,80,80));
        f.put(ROOT+"particle/subweapon/gatlingaerial.ptl","[life time] 1000 [creation frequency] -1 [maximum create number] 1 [object type] `[passive object]` [object] `` 22217 [/object]");
        f.put(ROOT+"effect/particle/gatlingshell.ptl","[life time] 2000 [creation frequency] 5 [object type] `[animation]` [object] `../Animation/GatlingShell.ani` -1 [/object]");
        f.put("passiveobject/passiveobject.lst","22217 `character/gunner/bbq.obj`");f.put("passiveobject/character/gunner/bbq.obj","[basic motion] `Animation/Bullet.ani`");f.put("passiveobject/character/gunner/animation/bullet.ani",ani(1000));
        return f;
    }
    private static Act1SkillSequence load(String path,String choice,HashMap<String,String> f)throws Exception{return Act1GunnerSupplement.find(path,"[static data] "+(path.equals(Act1GunnerSupplement.BBQ)?10:5)+" 500 [/static data]",f::get,ACTIONS,1,choice,null);}
    @Test void bbqMissDoesNotRequireOrEmitSuccessAssets()throws Exception{
        var f=fixture();f.remove(ROOT+"particle/subweapon/gatlingaerial.ptl");var s=load(Act1GunnerSupplement.BBQ,Act1GunnerSupplement.BBQ_MISS,f);
        assertNotNull(s);assertEquals(750,s.poses().get(0).end());assertTrue(s.visuals().isEmpty());assertTrue(s.emitters().isEmpty());
    }
    @Test void bbqHitTransitionsAtKneeAttackThenHoldsBeforeDrawingGun()throws Exception{
        var s=load(Act1GunnerSupplement.BBQ,"",fixture());assertNotNull(s);
        assertEquals(250,s.poses().get(0).end());assertEquals(2,s.poses().get(1).firstFrame());assertEquals(450,s.poses().get(1).end());
        assertEquals(850,s.poses().get(2).end());assertEquals(2050,s.poses().get(3).end());
        var bullet=s.emitters().stream().filter(e->e.path().endsWith("gatlingaerial.ptl")).findFirst().orElseThrow();
        assertEquals(850,bullet.start());assertEquals(1000,bullet.emitFor());assertEquals(10,bullet.frequency());assertEquals(148,bullet.x());assertEquals(155,bullet.z());
        assertEquals(10,s.visuals().stream().filter(v->v.label().equals("BBQ · 逐发枪口")).count());
        assertEquals(4,s.visuals().stream().filter(v->v.variations().equals(java.util.List.of(1,0))).count());
    }
    @Test void bbqUsesMinimumLearnedKneeLevelNotItsOwnLevel()throws Exception{
        var f=fixture();var s=Act1GunnerSupplement.find(Act1GunnerSupplement.BBQ,"[pre required skill] 4 5 12 1 [/pre required skill] [static data] 10 500 [/static data]",f::get,ACTIONS,30,"",null);
        assertEquals(232,s.poses().get(0).end());assertTrue(s.note().contains("Lv.5"));
    }
    @Test void punisherMissStopsAtTryAndNeverLoadsGun()throws Exception{
        var f=fixture();f.remove("etc/gunnerdefaultguninfo.etc");var s=load(Act1GunnerSupplement.PUNISHER,Act1GunnerSupplement.PUNISHER_MISS,f);
        assertNotNull(s);assertEquals(320,s.poses().get(0).end());assertEquals(1,s.poses().size());assertTrue(s.visuals().isEmpty());
    }
    @Test void punisherReplaysOnlyShotFramesThenRecoversPastSentinel()throws Exception{
        var s=load(Act1GunnerSupplement.PUNISHER,"",fixture());assertNotNull(s);assertEquals(70,s.poses().get(0).end());assertEquals(870,s.poses().get(1).end());
        assertEquals(5,s.poses().stream().filter(p->p.firstFrame()==9&&p.endFrame()==12).count());
        assertEquals(2170,s.poses().get(6).end());assertEquals(13,s.poses().get(7).firstFrame());assertEquals(2370,s.poses().get(7).end());
        assertEquals(5,s.visuals().stream().filter(v->v.path().endsWith("treadrevolver.ani")).count());assertEquals(2,s.visuals().stream().filter(v->v.path().contains("treadshootdust")).count());
    }
    @Test void weaponDrawSpeedAndOverridesRetimesRepeatedMuzzle()throws Exception{
        var f=fixture();f.put("equipment/test.equ","[equipment type] `[weapon]` 0 [sub type] 0 [attack speed] 1000 [int data] -1 -1 -1 -1 -1 -1 -1 -1 -1 1000 [/int data]");
        var s=Act1GunnerSupplement.find(Act1GunnerSupplement.PUNISHER,"[static data] 5 500 [/static data]",f::get,ACTIONS,1,"","equipment/test.equ");
        assertEquals(35,s.poses().get(0).end());assertEquals(435,s.poses().get(1).end());assertEquals(130,s.visuals().stream().filter(v->v.path().endsWith("treadrevolver.ani")).findFirst().orElseThrow().style().animationDuration());
    }
    @Test void slowDrawSpeedRetimesBodyAndMuzzleTogether() throws Exception {
        var data=fixture();
        StringBuilder motions=new StringBuilder("[attack speed] 1000 [etc motion]");
        for(int i=0;i<12;i++)motions.append(" `").append(ACTIONS.getOrDefault("etc motion:"+i,"missing.ani")).append("`");
        data.put(ROOT+"gunner.chr",motions.toString());
        data.put("etc/gunnerdefaultguninfo.etc","[sub type] 0 [integer] 4 4 0 100 100 1000 100 0 0 500 [/integer]");
        data.put(ROOT+"animation/treadshoot.ani",data.get(ROOT+"animation/treadshoot.ani")
                .replace("[FRAME009] [IMAGE] `fixture.img` 0","[FRAME009] [IMAGE] `fixture.img` 9")
                .replace("[FRAME010] [IMAGE] `fixture.img` 0","[FRAME010] [IMAGE] `fixture.img` 10"));
        var sprites=new ScenePreview.SpriteLookup(){
            public NpkResources.Info info(String path,int index){return new NpkResources.Info(1,1,0,0,1,1);}
            public NpkResources.Sprite sprite(String path,int index){
                var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0,0,0xffffffff);
                return new NpkResources.Sprite(image,0,0,1,1,java.nio.file.Path.of("fixture.npk"),path,index,index);
            }
        };
        var preview=GamePreview.load(Act1GunnerSupplement.PUNISHER,"[static data] 5 500",
                data::get,data.keySet(),sprites,GamePreview.Options.defaults());
        assertEquals(9,bodyFrame(preview,1820));
        assertEquals(10,bodyFrame(preview,1870));
    }

    private static int bodyFrame(GamePreview preview,long time) throws java.io.IOException {
        return preview.render(time,false).draws().stream().filter(draw->draw.label().equals("body"))
                .findFirst().orElseThrow().prepared().source().requestedFrame();
    }
    @Test void foreignBindingsBadRangesAndCrossSkillChoicesFailClosed()throws Exception{
        var f=fixture();f.put("skill/gunnerskill.lst","14 `gunner/notbbq.skl`");assertNull(load(Act1GunnerSupplement.BBQ,"",f));
        assertNull(load(Act1GunnerSupplement.BBQ,Act1GunnerSupplement.PUNISHER_MISS,fixture()));
        f=fixture();f.put(ROOT+"animation/treadshoot.ani",ani(100,100));assertNull(load(Act1GunnerSupplement.PUNISHER,"",f));
        assertNull(Act1GunnerSupplement.find(Act1GunnerSupplement.PUNISHER,"[static data] 999 [/static data]",fixture()::get,ACTIONS,1,"",null));
    }
    @Test void metadataRequiresAct1AndCatalogBinding()throws Exception{
        assertEquals(Act1GunnerSupplement.BBQ_MISS,Act1GunnerSupplement.choices(Act1GunnerSupplement.BBQ,fixture()::get,true).get(0).key());
        assertTrue(Act1GunnerSupplement.choices(Act1GunnerSupplement.PUNISHER,fixture()::get,false).isEmpty());
    }
    @Test void bbqBulletsEndAtNativeTargetContactAndPointAlongUpwardFlight()throws Exception{
        var p=ParticleScript.parse(fixture().get(ROOT+"particle/subweapon/gatlingaerial.ptl"));
        assertEquals(50,Act1GunnerSupplement.particleScript(ROOT+"particle/subweapon/gatlingaerial.ptl",p).life());
        assertSame(p,Act1GunnerSupplement.particleScript(ROOT+"particle/subweapon/gatlingup.ptl",p));
        var unlimited=ParticleScript.parse(fixture().get(ROOT+"particle/subweapon/gatlingaerial.ptl").replace("[life time] 1000","[life time] 0"));
        assertEquals(50,Act1GunnerSupplement.particleScript(ROOT+"particle/subweapon/gatlingaerial.ptl",unlimited).life());
        var a=ResourceScript.animation("[FRAME000] [IMAGE] `Character/Gunner/Effect/Bullet/Gatling.img` 0 [IMAGE ROTATE] 0.785398 [DELAY] 100");
        assertEquals(-.785398,Act1GunnerSupplement.particleVisual("passiveobject/character/gunner/subweaponbulletgatlingaerial.obj","passiveobject/character/gunner/animation/subweaponbulletgatlingup.ani",a).frames().get(0).rotation(),.000001);
        assertSame(a,Act1GunnerSupplement.particleVisual("passiveobject/other.obj","passiveobject/character/gunner/animation/subweaponbulletgatlingup.ani",a));
    }
    @Test void windmillRestartsPairedDustPerTurnAndAddsLastTurnTail()throws Exception{
        var f=fixture();f.put("skill/gunnerskill.lst","3 `gunner/windmill.skl`");
        f.put(ROOT+"animation/windmillroll.ani",ani(100,100,100,100));f.put(ROOT+"animation/windmillstand.ani",ani(100,100));
        for(int n=1;n<=2;n++)f.put(EFFECT+"windmilldust"+n+".ani",ani(100,100,100,100));f.put(EFFECT+"windmilldust3.ani",ani(300,100,100,100,100));
        var actions=new HashMap<>(ACTIONS);actions.put("etc motion:7","Animation/WindmillRoll.ani");actions.put("etc motion:8","Animation/WindmillStand.ani");
        var s=Act1GunnerSupplement.find(Act1GunnerSupplement.WINDMILL,"[level info] 3 166 2 100 [/level info]",f::get,actions,1,"",null);
        assertNotNull(s);assertEquals(3,s.poses().stream().filter(p->!p.action().equals("waiting motion")).count());assertEquals(200,s.poses().get(0).end());assertEquals(400,s.poses().get(1).end());
        assertEquals(5,s.visuals().size());var tail=s.visuals().get(4);assertEquals(200,tail.start());assertEquals(550,tail.end());assertTrue(s.note().contains("移动回旋踢"));
    }
    @Test void eightGunnerBuffsUseNativeIconIdsAndOutfitHeadHeight()throws Exception{
        var f=fixture();String[] names={"flamebullet","freezebullet","silverbullet","stayonjumpshoot","highanglesliding","robotics","revolvercriticaldamageup","subweaponstuckdown"};int[] ids={1,2,30,33,37,46,60,62},icons={13,14,15,17,18,19,35,36};
        var catalog=new StringBuilder();for(int i=0;i<ids.length;i++)catalog.append(ids[i]+" `gunner/"+names[i]+".skl` ");f.put("skill/gunnerskill.lst",catalog.toString());
        var actions=new HashMap<>(ACTIONS);actions.put("buff motion","Animation/Buff.ani");f.put(ROOT+"animation/buff.ani",ani(100,100,100));
        for(String name:new String[]{"buffchargeglow","buffstarticon","buffstarticonglow","buffstartglow"})f.put("common/commoneffect/animation/"+name+".ani",ani(100,100));
        for(String name:new String[]{"buffchargeparticle","buffstartparticle"})f.put("common/commoneffect/particle/"+name+".ptl","[life time] 100 [creation frequency] -1 [maximum create number] 1 [object type] `[animation]` [object] `../Animation/BuffStartGlow.ani` -1 [/object]");
        for(int i=0;i<ids.length;i++){
            var s=Act1GunnerSupplement.find("skill/gunner/"+names[i]+".skl","[type] `[active]` [casting time] 300 500 [maximum level] 30",f::get,actions,30,"",null,job->160);
            assertNotNull(s,names[i]);assertEquals(500,s.poses().get(0).end());var icon=s.visuals().stream().filter(v->v.style().spriteOverride()!=null).findFirst().orElseThrow();
            assertEquals(icons[i],icon.style().spriteOverride().index());assertEquals(-190,icon.y());assertFalse(icon.mirror());assertEquals(500,icon.start());assertTrue(s.emitters().stream().allMatch(e->e.z()==190));
        }
    }
    @Test void flameBulletLoadsBeforeFiringAndUsesTheSameNativeBurstAfterEitherContact() throws Exception {
        var files = fixture();
        var actions = new HashMap<>(ACTIONS);
        actions.put("buff motion", "Animation/Buff.ani");
        actions.put("attack motion:0", "Animation/Attack1.ani");
        actions.put("etc motion:1", "Animation/Attack1ShootDown.ani");
        files.put("skill/gunnerskill.lst", "1 `gunner/flamebullet.skl` 2 `gunner/freezebullet.skl` 30 `gunner/silverbullet.skl`");
        files.put(ROOT + "animation/buff.ani", ani(100, 100, 100));
        files.put(ROOT + "animation/attack1.ani", ani(100, 100, 10000));
        files.put(ROOT + "animation/attack1shootdown.ani", ani(10000, 50, 50, 10000));
        for (String name : new String[]{"buffchargeglow", "buffstarticon", "buffstarticonglow", "buffstartglow"}) {
            files.put("common/commoneffect/animation/" + name + ".ani", ani(100, 100));
        }
        for (String name : new String[]{"buffchargeparticle", "buffstartparticle"}) {
            files.put("common/commoneffect/particle/" + name + ".ptl", "[life time] 100");
        }
        files.put("etc/gunnerdefaultguninfo.etc", "[sub type] 0 [integer] 4 4 80 100 100 1000 100 95 25 1000 [/integer]"
                + " [string] `Character/Gunner/Particle/Shot.ptl` `Character/Gunner/Particle/Shot.ptl` [/string]");
        files.put(ROOT + "particle/shot.ptl", "[creation pos] 80 0 76 [x axis] [move type] `[straight line]`"
                + " [move variable 1] 800 800 [rotation angle] 0 -23 0 [life time] 500 [creation frequency] -1"
                + " [maximum create number] 1 [object type] `[passive object]` [object] `` 22002 [/object]");
        files.put(EFFECT + "bullet/flamepistol.ani", ani(100, 100));
        files.put(EFFECT + "shooteffect/downpistol.ani", ani(100));
        files.put(EFFECT + "hiteffect/downdefault.ani", ani(100));
        files.put(EFFECT + "hiteffect/collisionwall.ani", ani(100));
        files.put("passiveobject/passiveobject.lst", "22213 `Character/Gunner/FlameBulletFireExplosion.obj` 22002 `Character/Gunner/DownBulletRevolver.obj`");
        files.put("passiveobject/character/gunner/flamebulletfireexplosion.obj", "[basic motion] `Root.ani`"
                + " [string data] `Flame1.ptl` `Flame2.ptl` `Flame3.ptl` `Flame4.ptl` [/string data]");
        files.put("passiveobject/character/gunner/root.ani", "[FRAME000] [IMAGE] `` 0 [DELAY] 400");
        files.put("passiveobject/character/gunner/downbulletrevolver.obj", "[basic motion] `bulletpistol.ani`");
        files.put("passiveobject/character/gunner/bulletpistol.ani",
                "[FRAME000] [IMAGE] `base.img` 0 [DELAY] 100 [ATTACK BOX] -12 -16 -36 25 32 40");
        files.put(EFFECT + "bullet/freezepistol.ani", ani(100, 100));
        files.put(EFFECT + "bullet/silverpistol.ani", ani(100, 100));
        for (int i = 1; i <= 4; i++) files.put("passiveobject/character/gunner/flame" + i + ".ptl", "[life time] 500");
        files.put(Act1SwordAsuraSupplement.GOBLIN + "stay.ani", ani(80));
        files.put(Act1SwordAsuraSupplement.GOBLIN + "damage1.ani", ani(80));

        String text = "[casting time] 500 500 [static data] 2 [level info] 2 7 65";
        assertEquals(2, Act1GunnerSupplement.choices(Act1FlameBullet.PATH, files::get, true).size());
        var defaultSequence = Act1GunnerSupplement.find(Act1FlameBullet.PATH, text, files::get, actions, 1, "", null);
        assertTrue(defaultSequence.visuals().stream().noneMatch(v -> v.path().endsWith("flamepistol.ani")));
        for (String choice : new String[]{Act1FlameBullet.HIT, Act1FlameBullet.WALL}) {
            var sequence = Act1GunnerSupplement.find(Act1FlameBullet.PATH, text, files::get, actions, 1, choice, null);
            assertNotNull(sequence);
            assertEquals(500, sequence.poses().get(0).end());
            var bullet = sequence.visuals().stream().filter(v -> v.path().endsWith("flamepistol.ani")).findFirst().orElseThrow();
            assertEquals(960, bullet.start());
            assertEquals(1100, bullet.end());
            var hitbox = sequence.visuals().stream().filter(v -> v.path().endsWith("/bulletpistol.ani")).findFirst().orElseThrow();
            assertEquals(bullet.start(), hitbox.start());
            assertEquals(bullet.end(), hitbox.end());
            var frame = hitbox.style().animation().frames().get(0);
            assertTrue(frame.image().isEmpty());
            assertEquals(25, frame.combat().attack().get(0).width());
            assertEquals(0, bullet.style().sample(70).opacity());
            assertEquals(1, bullet.style().sample(71).opacity());
            assertTrue(bullet.style().animation().frames().get(0).rotation() > 0);
            var burst = sequence.emitters().stream().filter(e -> e.label().contains("爆炸")).toList();
            assertEquals(4, burst.size());
            assertTrue(burst.stream().allMatch(e -> e.start() == bullet.end() && e.emitFor() == 400));
            assertEquals(choice.equals(Act1FlameBullet.HIT), sequence.visuals().stream().anyMatch(v -> v.label().contains("哥布林受击")));
            assertEquals(choice.equals(Act1FlameBullet.WALL), sequence.visuals().stream().anyMatch(v -> v.path().endsWith("collisionwall.ani")));
        }
        for (String ammo : new String[]{"freezebullet", "silverbullet"}) {
            String skill = "skill/gunner/" + ammo + ".skl";
            var choice = Act1GunnerSupplement.choices(skill, files::get, true).get(0);
            var shot = Act1GunnerSupplement.find(skill, text, files::get, actions, 1, choice.key(), null);
            assertNotNull(shot);
            assertTrue(shot.visuals().stream().anyMatch(v -> v.path().endsWith("/bulletpistol.ani")));
            assertTrue(shot.visuals().stream().anyMatch(v -> v.label().contains("哥布林受击")));
            assertTrue(shot.emitters().stream().noneMatch(e -> e.label().contains("爆炸")));
        }
    }
    @Test void grenadeReloadThrowAndNativeFuseRetainTwoExplosionLayers()throws Exception{
        var f=fixture();f.put("skill/gunnerskill.lst","56 `gunner/grenadenone.skl`");f.put(ROOT+"animation/reloadinhand.ani",ani(150,150,150,150,150,150,150,150,150));f.put(ROOT+"animation/throw1.ani",ani(70));f.put(ROOT+"animation/throw2.ani",ani(70,70,70,70));
        f.put(ROOT+"animation/momentarythrow1.ani",ani(20));
        f.put(ROOT+"animation/momentarythrow2.ani",ani(20,20));
        f.put(EFFECT+"grenade/reloadnone.ani",ani(150,150,150,150,150,150,150,150,150));
        f.put(ROOT+"particle/grenadecenter.ptl","[x axis] [move type] `[straight line]` [move variable 1] 350 350 [z axis] [move type] `[straight line]` [move variable 1] 150 150 [gravity] 1000 [landing type] `[stop]`");
        f.put(ROOT+"particle/grenadedown.ptl","[x axis] [move type] `[straight line]` [move variable 1] 200 200 [z axis] [move type] `[straight line]` [move variable 1] -400 -400 [gravity] 1500 [landing type] `[stop]`");
        f.put("passiveobject/passiveobject.lst","22219 `character/gunner/grenadenone.obj`");f.put("passiveobject/character/gunner/grenadenone.obj","[basic motion] `Animation/GrenadeNone.ani` [etc motion] `Animation/GrenadeNoneRolling.ani` `Animation/GrenadeNoneBomb.ani` `Animation/GrenadeNoneBombEffect.ani` [/etc motion]");
        for(String name:new String[]{"grenadenone","grenadenonerolling","grenadenonebomb","grenadenonebombeffect"})f.put("passiveobject/character/gunner/animation/"+name+".ani",ani(100,100,100));
        var actions=new HashMap<>(ACTIONS);actions.put("etc motion:34","Animation/ReloadInHand.ani");actions.put("throw motion 1-1","Animation/Throw1.ani");actions.put("throw motion 1-2","Animation/Throw2.ani");
        String skill="[casting time] 500 500 [static data] 7 50 400 500 3000 3000 22219 [/static data] [level info] 2 264 200 [/level info]";
        actions.put("throw motion 3-1","Animation/MomentaryThrow1.ani");
        actions.put("throw motion 3-2","Animation/MomentaryThrow2.ani");
        var s=Act1GunnerSupplement.find("skill/gunner/grenadenone.skl",skill,f::get,actions,1,"",null);assertNotNull(s);assertEquals(500,s.poses().get(0).end());assertEquals(550,s.poses().get(1).end());assertEquals(950,s.poses().get(2).end());
        assertEquals("throw motion 3-1",s.poses().get(1).action());
        assertEquals("throw motion 3-2",s.poses().get(2).action());
        var flight=s.visuals().stream().filter(v->v.path().endsWith("/grenadenone.ani")).findFirst().orElseThrow();assertEquals(550,flight.start());assertEquals(1050,flight.end());assertEquals(175,flight.motion().offsetAt(1050),.001);assertEquals(24,flight.motion().heightAt(1050),.001);
        var boom=s.visuals().stream().filter(v->v.path().endsWith("grenadenonebomb.ani")).findFirst().orElseThrow();assertEquals(1050,boom.start());assertEquals(230,boom.x(),.001);assertEquals(-24,boom.y(),.001);assertEquals(200/316.0,boom.scale(),.0001);assertEquals(4,s.visuals().size());
        assertNull(Act1GunnerSupplement.find("skill/gunner/grenadenone.skl",skill,f::get,actions,1,Act1GunnerSupplement.GRENADE_DOWN,null));
        assertNull(Act1GunnerSupplement.find("skill/gunner/grenadenone.skl",skill.replace("400 500 3000","400 1000 3000"),f::get,actions,1,"",null));
        f.remove("passiveobject/passiveobject.lst");assertNotNull(Act1GunnerSupplement.find("skill/gunner/grenadenone.skl",skill,f::get,actions,1,Act1GunnerSupplement.GRENADE_RELOAD,null));
    }
}
