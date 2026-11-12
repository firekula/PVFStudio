package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class Act1GunnerPrecisionCastTest {
    private static final String ROOT="character/gunner/";
    private static final Map<String,String> ACTIONS=Map.of("attack motion:1","Animation/Attack2.ani","etc motion:2","Animation/Attack2ShootUp.ani","etc motion:3","Animation/Attack2ShootDown.ani","etc motion:4","Animation/Reload.ani","waiting motion","Animation/Stay.ani");
    private static final String HEAD="[level info] 4 400 0 100 10 421 50 107 12 [/level info]";
    private static String ani(int... delays){var out=new StringBuilder();for(int i=0;i<delays.length;i++)out.append("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d\n".formatted(i,delays[i]));return out.toString();}
    private static HashMap<String,String> fixture(){
        var f=new HashMap<String,String>();f.put(ROOT+"gunner.chr","[attack speed] 1000.0");
        f.put(ROOT+"animation/attack2.ani",ani(100,200,50,50,50,50,10000));
        for(String direction:new String[]{"up","down"}){
            f.put(ROOT+"animation/attack2shoot"+direction+".ani",ani(10000,50,50,10000));
            f.put(ROOT+"effect/animation/headshot"+direction+".ani",ani(80,70,70,70));
            for(String gun:new String[]{"revolver","bowgun"})f.put(ROOT+"particle/"+direction+"attack"+gun+".ptl","[creation pos] 86 0 120 [x axis] [move type] `[straight line]` [move variable 1] 800 800 [life time] 500 [creation frequency] -1 [maximum create number] 1 [object type] `[passive object]` [object] `` 22001 [/object]");
            for(String gun:new String[]{"pistol","bowgun"})f.put(ROOT+"effect/animation/shooteffect/"+direction+gun+".ani",ani(70,70));
        }
        f.put(ROOT+"animation/reload.ani",ani(75,225));f.put(ROOT+"effect/animation/turningshoot.ani",ani(80,80,80,80,80,80,80,80));
        f.put("etc/gunnerdefaultguninfo.etc","[sub type] 0 [integer] 4 4 80 80 100 1000 100 95 25 1000 [/integer] [string] `Character/Gunner/Particle/UpAttackRevolver.ptl` `Character/Gunner/Particle/DownAttackRevolver.ptl` [/string] [sub type] 4 [integer] 7 7 10 40 60 500 110 60 15 2000 [/integer] [string] `Character/Gunner/Particle/UpAttackBowGun.ptl` `Character/Gunner/Particle/DownAttackBowGun.ptl` [/string]");
        f.put("passiveobject/passiveobject.lst","22001 `character/gunner/bullet.obj`");f.put("passiveobject/character/gunner/bullet.obj","[basic motion] `bullet.ani`");f.put("passiveobject/character/gunner/bullet.ani",ani(50));
        f.put("skill/gunnerskill.lst","5 `gunner/headshot.skl`");f.put("skill/gunner/headshot.skl",HEAD);
        f.put("equipment/bowgun.equ","[equipment type] `[weapon]` 17 [sub type] 4");return f;
    }
    @Test void headshotUsesNativeWeaponReactionRecoveryAndReloadClocks() throws Exception {
        var s=Act1GunnerPrecisionCast.find(5,HEAD,fixture()::get,ACTIONS,1,false,null);
        assertNotNull(s);assertEquals(200,s.poses().get(0).end());assertEquals(2,s.poses().get(0).firstFrame());assertEquals(6,s.poses().get(0).endFrame());
        assertEquals(80,s.poses().get(1).holdDelay());assertEquals(480,s.poses().get(1).end());assertEquals(780,s.poses().get(2).end());
        assertEquals(280,s.emitters().get(0).start());assertEquals(85,s.emitters().get(0).x());assertEquals(112,s.emitters().get(0).z());assertTrue(s.emitters().get(0).overridePosition());
        assertEquals(2,s.visuals().size());assertTrue(s.visuals().get(0).path().endsWith("headshotup.ani"));assertEquals(124,s.visuals().get(0).x());assertEquals(-113,s.visuals().get(0).y());
    }
    @Test void headshotDecodesTheDefaultMuzzleImageVariant() throws Exception {
        var files=fixture();
        files.put(ROOT+"gunner.chr","""
                [attack speed] 1000.0
                [waiting motion] `Animation/Stay.ani`
                [attack motion] `Animation/Attack1.ani` `Animation/Attack2.ani`
                [etc motion] `Animation/Other.ani` `Animation/Other.ani` `Animation/Attack2ShootUp.ani` `Animation/Attack2ShootDown.ani` `Animation/Reload.ani`
                """);
        files.put(ROOT+"animation/stay.ani",ani(100));
        files.put(ROOT+"effect/animation/shooteffect/uppistol.ani","[FRAME000] [IMAGE] `muzzle%d%d.img` 0 [DELAY] 140");
        var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xffffffff);
        var sprites=new ScenePreview.SpriteLookup(){
            public NpkResources.Info info(String path,int frame){return new NpkResources.Info(1,1,0,0,1,1);}
            public NpkResources.Sprite sprite(String path,int frame){
                return new NpkResources.Sprite(image,0,0,1,1,Path.of("fixture.npk"),path,frame,frame);
            }
        };
        var preview=GamePreview.load("skill/gunner/headshot.skl",HEAD,files::get,files.keySet(),sprites,GamePreview.Options.defaults());
        assertTrue(preview.render(300,false).draws().stream().anyMatch(d->d.label().equals("枪口火光")
                &&d.prepared().source().img().equals("muzzle00.img")));
    }
    @Test void downChoiceSelectsAlternativeFlashPoseAndMuzzleOffsets() throws Exception {
        var s=Act1GunnerPrecisionCast.find(5,HEAD,fixture()::get,ACTIONS,1,true,null);
        assertEquals("etc motion:3",s.poses().get(1).action());assertEquals(78,s.emitters().get(0).x());assertEquals(85,s.emitters().get(0).z());
        assertTrue(s.visuals().get(0).path().endsWith("headshotdown.ani"));assertEquals(120,s.visuals().get(0).x());assertEquals(-115,s.visuals().get(0).y());
        assertEquals(1,s.visuals().stream().filter(v->v.path().contains("headshot")).count());
    }
    @Test void turningShotFlipsImmediatelyAndUsesLinkedHeadshotRatherThanTurningDamageColumn() throws Exception {
        var s=Act1GunnerPrecisionCast.find(20,"[level info] 1 120 [/level info]",fixture()::get,ACTIONS,1,false,null);
        assertNotNull(s);assertEquals(200,s.poses().get(0).end());assertTrue(s.poses().stream().allMatch(Act1SkillSequence.Pose::reverseFacing));
        assertTrue(s.visuals().stream().allMatch(v->v.style().reverseFacing()));assertEquals(-85,s.emitters().get(0).x());assertTrue(s.emitters().get(0).reverseFacing());
        var turn=s.visuals().stream().filter(v->v.path().endsWith("turningshoot.ani")).findFirst().orElseThrow();assertEquals(0,turn.start());assertEquals(640,turn.end());assertTrue(s.note().contains("Lv.1"));
    }
    @Test void selectedWeaponAndHeadshotLevelChangeTimingAndBulletBinding() throws Exception {
        var s=Act1GunnerPrecisionCast.find(5,HEAD,fixture()::get,ACTIONS,2,false,"equipment/bowgun.equ");
        assertEquals(67,s.poses().get(0).end());assertEquals(107,s.emitters().get(0).start());assertTrue(s.emitters().get(0).path().endsWith("upattackbowgun.ptl"));
        assertEquals(967,s.poses().get(2).end());assertTrue(s.visuals().get(1).path().endsWith("upbowgun.ani"));
    }
    @Test void liftShotUsesSameSingleBulletButOwnRecoveryAndNoHeadshotOrReload() throws Exception {
        var s=Act1GunnerPrecisionCast.find(47,"[static data] 250 [/static data]",fixture()::get,ACTIONS,1,false,null);
        assertNotNull(s);assertEquals(630,s.poses().get(1).end());assertEquals(1,s.visuals().size());assertFalse(s.poses().stream().anyMatch(p->p.action().equals("etc motion:4")));assertEquals(1,s.emitters().size());
    }
    @Test void napalmUsesTheDownwardSecondAttackAndItsOwnCastThenReload() throws Exception {
        var files=fixture();
        files.put(ROOT+"effect/animation/napalmbombshoot.ani",ani(250));
        files.remove(ROOT+"effect/animation/shooteffect/downpistol.ani");
        files.remove("passiveobject/passiveobject.lst");
        var sequence=Act1GunnerPrecisionCast.find(53,"[casting time] 400 400",files::get,ACTIONS,1,false,null);
        assertNotNull(sequence);
        assertEquals(400,sequence.poses().get(0).end());
        assertEquals("etc motion:3",sequence.poses().get(1).action());
        assertEquals("etc motion:4",sequence.poses().get(2).action());
        var flash=sequence.visuals().get(0);
        assertTrue(flash.path().endsWith("napalmbombshoot.ani"));
        assertEquals(480,flash.start());
        assertEquals(120,flash.x());
        assertEquals(-115,flash.y());
        assertTrue(sequence.emitters().isEmpty());
    }
    @Test void equippedIntegerOverridesAndAttackSpeedApplyBeforeStageClocks() throws Exception {
        var f=fixture();f.put("equipment/bowgun.equ","[equipment type] `[weapon]` 17 [sub type] 4 [attack speed] 1000 [int data] -1 -1 -1 200 -1 -1 -1 -1 -1 -1 [/int data]");
        var s=Act1GunnerPrecisionCast.find(5,HEAD,f::get,ACTIONS,1,false,"equipment/bowgun.equ");
        assertEquals(50,s.poses().get(0).end());assertEquals(150,s.emitters().get(0).start());
    }
    @Test void rejectsMissingNativeInputsAndUnresolvedEquippedWeapon() throws Exception {
        var f=fixture();f.remove("etc/gunnerdefaultguninfo.etc");assertNull(Act1GunnerPrecisionCast.find(5,HEAD,f::get,ACTIONS,1,false,null));
        f=fixture();f.remove(ROOT+"effect/animation/headshotup.ani");assertNull(Act1GunnerPrecisionCast.find(5,HEAD,f::get,ACTIONS,1,false,null));
        assertNull(Act1GunnerPrecisionCast.find(5,HEAD,fixture()::get,ACTIONS,1,false,"equipment/missing.equ"));
        f=fixture();f.put("skill/gunnerskill.lst","5 `gunner/other.skl`");assertNull(Act1GunnerPrecisionCast.find(20,"",f::get,ACTIONS,1,false,null));
    }
    @Test void invisibleBulletWithAttackVolumeKeepsParticleTrack() throws Exception {
        var f=fixture();f.put("passiveobject/character/gunner/bullet.ani","[FRAME000] [IMAGE] `` 0 [DELAY] 80 [ATTACK BOX] 0 0 0 50 40 60");
        var s=Act1GunnerPrecisionCast.find(5,HEAD,f::get,ACTIONS,1,false,null);
        assertNotNull(s);assertEquals(1,s.emitters().size());assertEquals(2,s.visuals().size());
    }
}
