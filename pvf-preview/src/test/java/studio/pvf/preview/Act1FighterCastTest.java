package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1FighterCastTest {
    private static final String MOUNT="skill/fighter/mount.skl";
    private static final String CHR="character/fighter/";

    @Test void mountAlternatesHandsUntilTheConfiguredFinisher() throws Exception {
        var fixture=mountFixture();
        var sequence=Act1FighterCast.find(MOUNT,"[static data] 7 500",fixture.files::get,fixture.actions,1,"",job->90);
        assertNotNull(sequence);
        assertEquals(List.of("etc motion:7","etc motion:8","etc motion:9","etc motion:8","etc motion:9",
                        "etc motion:8","etc motion:9","etc motion:34"),
                sequence.poses().stream().limit(8).map(Act1SkillSequence.Pose::action).toList());
        var strike=sequence.visuals().get(0);
        assertEquals(CHR+"effect/animation/mountend.ani",strike.path());
        assertEquals(1500,strike.start());
        assertEquals(0,strike.x());
        var shorter=Act1FighterCast.find(MOUNT,"[static data] 3 500",fixture.files::get,fixture.actions,1,"",job->90);
        assertEquals(700,shorter.visuals().get(0).start());
    }

    @Test void mountWithoutAValidTargetKeepsTheFighterIdle() throws Exception {
        var fixture=mountFixture();
        var choice=Act1FighterCast.alternateChoice(MOUNT,fixture.files::get,true);
        assertNotNull(choice);
        assertNull(Act1FighterCast.alternateChoice(MOUNT,fixture.files::get,false));
        var sequence=Act1FighterCast.find(MOUNT,"[static data] 7 500",fixture.files::get,fixture.actions,1,choice.key(),job->90);
        assertNotNull(sequence);
        assertEquals(List.of("waiting motion"),sequence.poses().stream().map(Act1SkillSequence.Pose::action).toList());
        assertTrue(sequence.visuals().isEmpty());
    }

    @Test void fighterBuffUsesItsNativeActivationIconAtTheCurrentCastTime() throws Exception {
        var f=castFixture();
        f.files.put("common/commoneffect/animation/buffstarticon.ani",ani(500));
        f.files.put("common/commoneffect/animation/buffchargeglow.ani",ani(50));
        var sequence=Act1FighterCast.find("skill/fighter/attackspeedup.skl","[casting time] 600 600",f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        var icon=sequence.visuals().stream().filter(v->v.style().spriteOverride()!=null).findFirst().orElseThrow();
        assertEquals(600,icon.start());
        assertEquals(3,icon.style().spriteOverride().index());
        assertEquals(-120,icon.y());
    }

    @Test void aimVitalPointUsesTheConfiguredDiameterAndKeepsTheGroundAnchor() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","39 `Fighter/AimVitalPoint.skl`");
        f.files.put("common/commoneffect/animation/buffstarticon.ani",ani(400));
        for(int i=1;i<=3;i++)f.files.put(CHR+"effect/animation/aimvitalpoint"+i+".ani",ani(i*70,500));
        var sequence=Act1FighterCast.find("skill/fighter/aimvitalpoint.skl",
                "[casting time] 500 500 [level info] 4 60 10000 530 10",f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        var icon=sequence.visuals().stream().filter(v->v.style().spriteOverride()!=null).findFirst().orElseThrow();
        assertEquals(10,icon.style().spriteOverride().index());
        var rings=sequence.visuals().stream().filter(v->v.path().contains("aimvitalpoint")).toList();
        assertEquals(3,rings.size());
        assertTrue(rings.stream().allMatch(v->v.start()==500&&v.x()==0&&v.y()==0&&v.scale()==1));
        assertEquals(List.of(1070L,1140L,1210L),rings.stream().map(Act1SkillSequence.Visual::end).toList());
    }

    @Test void kensinUsesSkillLevelForCloneCountRadiusAndExpiry() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","8 `Fighter/Kensin.skl`");
        f.files.put("monster/monster.lst","60001 `Fighter/Illusion.mob`");
        String folder="monster/fighter/";
        f.files.put(folder+"illusion.mob","""
                [waiting motion] `Animation/IllusionCreate.ani`
                [etc motion] `Animation/IllusionCreateEffect.ani` `Animation/Illusion.ani` `Animation/IllusionEffect.ani` [/etc motion]
                """);
        for(var name:List.of("illusioncreate","illusioncreateeffect","illusion","illusioneffect","illusiondestroy","illusiondestroyeffect"))
            f.files.put(folder+"animation/"+name+".ani",ani(180));
        var sequence=Act1FighterCast.find("skill/fighter/kensin.skl",
                "[casting time] 200 200 [static data] 500 [level info] 3 1 7000 75 2 8000 80",
                f.files::get,f.actions,2,"",job->90);
        assertNotNull(sequence);
        var clones=sequence.visuals().stream().filter(v->v.label().equals("分身 · 维持")).toList();
        assertEquals(List.of(80d,-80d),clones.stream().map(Act1SkillSequence.Visual::x).toList());
        assertTrue(clones.stream().allMatch(v->v.start()==380&&v.end()==8200));
        assertEquals(2,sequence.visuals().stream().filter(v->v.label().equals("分身 · 消失")&&v.start()==8200).count());
    }

    @Test void closePunchStartsTheShockObjectOnlyInTheContactScenario() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","19 `Fighter/ClosePunch.skl`");
        f.add("etc motion:13","closepunch",100,200,500,100);
        f.files.put(CHR+"effect/animation/closepunchexecute.ani",ani(100,180));
        f.files.put("passiveobject/passiveobject.lst","21009 `Fighter/ClosePunchSub.obj`");
        f.files.put("passiveobject/fighter/closepunchsub.obj","[basic motion] `Sub.ani`");
        f.files.put("passiveobject/fighter/sub.ani",ani(500,180));
        var hit=Act1FighterHits.find("skill/fighter/closepunch.skl",f.files::get,f.actions,"");
        assertNotNull(hit);
        var shock=hit.visuals().stream().filter(v->v.label().equals("寸拳 · 命中冲击波")).findFirst().orElseThrow();
        assertEquals(100,shock.start());
        assertEquals(780,shock.end());
        var miss=Act1FighterHits.find("skill/fighter/closepunch.skl",f.files::get,f.actions,"fighter-hit:miss");
        assertEquals(List.of("寸拳 · 出拳光效"),miss.visuals().stream().map(Act1SkillSequence.Visual::label).toList());
    }

    @Test void stateTenSkillsUseTheirOwnThrowGroupsAndEffectClocks() throws Exception {
        var f=castFixture();
        String effect=CHR+"effect/animation/";
        f.files.put(effect+"roarstun1.ani",ani(220,55));
        f.files.put(effect+"roarstun2.ani",ani(220,55));
        f.files.put("passiveobject/passiveobject.lst","21004 `Character/Fighter/ThrowSand.obj` 21005 `Character/Fighter/RoarStun.obj`");
        f.files.put("passiveobject/character/fighter/roarstun.obj","[basic motion] `Animation/RoarStun.ani`");
        f.files.put("passiveobject/character/fighter/animation/roarstun.ani",ani(55));
        f.files.put("passiveobject/character/fighter/throwsand.obj","[basic motion] `Animation/ThrowSand.ani`");
        f.files.put("passiveobject/character/fighter/particle/throwsandparticle1.ptl","[life time] 500 [creation frequency] -1 [maximum create number] 30");
        f.files.put("passiveobject/character/fighter/particle/throwsandparticle2.ptl","[life time] 900 [creation frequency] -1 [maximum create number] 4");
        var roar=Act1FighterCast.find("skill/fighter/sajahu.skl","[casting time] 700 700 [static data] 800",f.files::get,f.actions,1,"",job->90);
        assertNotNull(roar);
        assertEquals("throw motion 3-1",roar.poses().get(0).action());
        assertEquals("throw motion 3-2",roar.poses().get(1).action());
        assertEquals(List.of(700L,700L,700L),roar.visuals().stream().map(Act1SkillSequence.Visual::start).toList());
        assertEquals(-65,roar.visuals().get(1).y());
        f.add("throw motion 1-1","throw1",60,60);
        f.add("throw motion 1-2","throw2",80,120);
        var sand=Act1FighterCast.find("skill/fighter/throwsand.skl","[static data] 150 500",f.files::get,f.actions,1,"",job->90);
        assertNotNull(sand);
        assertEquals("throw motion 1-2",sand.poses().get(1).action());
        assertEquals(2,sand.emitters().size());
        assertEquals(230,sand.emitters().get(0).start());
        assertEquals(65,sand.emitters().get(0).x());
        assertEquals(50,sand.emitters().get(0).z());
        assertTrue(sand.emitters().get(0).overridePosition());
    }

    @Test void shoulderTackleStartsItsVisualAndMotionAtRelease() throws Exception {
        var f=castFixture();
        f.add("etc motion:15","shouldertackle",100,10000,100,500,100);
        f.files.put(CHR+"effect/animation/shouldertackle.ani",ani(100,100,100,100,100));
        var tackle=Act1FighterCast.find("skill/fighter/shouldertackle.skl","[static data] 500 -500 400 700 -1000",f.files::get,f.actions,1,"",job->90);
        assertNotNull(tackle);
        assertEquals(100,tackle.poses().get(1).start());
        var effect=tackle.visuals().get(0);
        assertEquals(100,effect.start());
        assertEquals(80,effect.motion().offsetAt(500),0.001);
    }

    @Test void spiralNenUsesTheAuthoredOrbitAndVerticalBob() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","40 `Fighter/SpiralNen.skl`");
        f.files.put("passiveobject/passiveobject.lst","21007 `Character/Fighter/SpiralNen.obj`");
        f.files.put("passiveobject/character/fighter/spiralnen.obj","[basic motion] `Animation/SpiralNen.ani`");
        f.files.put("passiveobject/character/fighter/animation/spiralnen.ani",ani(100,100));
        f.files.put(CHR+"particle/spiralnen.ptl","""
                [x axis] [move type] `[sin]` [move variable 1] 60 60 [move variable 2] 1800 1800
                [y axis] [move type] `[cos]` [move variable 1] 60 60 [move variable 2] 1800 1800
                [z axis] [move type] `[sin]` [move variable 1] 10 10 [move variable 2] 1000 1000
                """);
        var sequence=Act1FighterCast.find("skill/fighter/spiralnen.skl","[casting time] 500 500 [static data] 2000 500 [level info] 2 100 12000",f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        var orb=sequence.visuals().get(0);
        assertEquals(500,orb.start());
        assertEquals(12500,orb.end());
        assertEquals(-50,orb.y());
        assertEquals(60,orb.motion().offsetAt(950),0.001);
        assertEquals(60*Math.cos(250*Math.PI/900)*.29-10,orb.motion().offsetYAt(750),0.001);
    }

    @Test void venomMineReleasesOnFrameThreeAndDetonatesAtTheMineLandings() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","76 `Fighter/VenomMine.skl`");
        f.add("etc motion:31","venomminethrow",80,100,80,80,80,80,200);
        f.add("etc motion:32","venomminepound",80,80,400,80);
        String folder=CHR+"effect/animation/venommine/";
        f.files.put(folder+"venommine.ani",ani(70,60000,70,70));
        f.files.put(folder+"venomminedust.ani",ani(350));
        f.files.put(folder+"venomminepounddust.ani",ani(480));
        f.files.put("passiveobject/passiveobject.lst","21028 `Character/Fighter/VenomExplosion.obj`");
        f.files.put("passiveobject/character/fighter/venomexplosion.obj","[basic motion] `Animation/VenomExplosion.ani`");
        f.files.put("passiveobject/character/fighter/animation/venomexplosion.ani",ani(300));
        String text="[static data] 50 80 500 [level info] 2 400 2";
        var sequence=Act1FighterCast.find("skill/fighter/venommine.skl",text,f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        var mines=sequence.visuals().stream().filter(v->v.path().equals(folder+"venommine.ani")).toList();
        assertEquals(260,mines.get(0).start());
        assertEquals(820,mines.get(2).start());
        assertEquals(50,mines.get(2).x());
        var explosions=sequence.visuals().stream().filter(v->v.path().endsWith("/venomexplosion.ani")).toList();
        assertEquals(List.of(860L,1500L),explosions.stream().map(Act1SkillSequence.Visual::start).toList());
        assertEquals(List.of(50d,130d),explosions.stream().map(Act1SkillSequence.Visual::x).toList());
    }

    @Test void crouchStaysDefensiveUntilTheOptionalShoulderInput() throws Exception {
        var f=castFixture();
        String path="skill/fighter/crouch.skl";
        String text="[level info] 3 2000 -1000 135";
        f.files.put("skill/fighterskill.lst","2 `Fighter/Crouch.skl`");
        f.add("etc motion:0","crouch",150);
        f.add("dashattack motion","dashattack",120,80,150);

        var regular=Act1FighterCast.find(path,text,f.files::get,f.actions,1,"",job->90);
        assertNotNull(regular);
        assertEquals("etc motion:0",regular.poses().get(0).action());
        assertEquals(2000,regular.poses().get(0).end());
        assertFalse(regular.poses().stream().anyMatch(p->p.action().equals("dashattack motion")));

        var choice=Act1FighterCast.alternateChoice(path,f.files::get,true);
        assertNotNull(choice);
        var shoulder=Act1FighterCast.find(path,text,f.files::get,f.actions,1,choice.key(),job->90);
        assertEquals(List.of("etc motion:0","dashattack motion","waiting motion"),
                shoulder.poses().stream().map(Act1SkillSequence.Pose::action).toList());
        assertEquals(150,shoulder.poses().get(1).start());
        assertEquals(500,shoulder.poses().get(1).end());
    }

    @Test void poisonMistLandsBeforeNineCloudsSpreadAndFade() throws Exception {
        var f=poisonMistFixture();
        var sequence=Act1FighterCast.find("skill/fighter/poisonmist.skl",POISON_MIST,f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        var flight=sequence.visuals().get(0);
        var pouch=sequence.visuals().get(1);
        var clouds=sequence.visuals().stream().filter(v->v.path().endsWith("poisonmistmist1.ani")).toList();
        assertEquals(60,flight.motion().heightAt(0));
        assertEquals(147,flight.end());
        assertEquals(flight.end(),pouch.start());
        assertEquals(9,clouds.size());
        assertEquals(227,clouds.get(0).start());
        assertEquals(10147,clouds.get(0).end());
        assertEquals(125,clouds.get(5).x()+clouds.get(5).motion().offsetAt(4147)-pouch.x(),0.001);
        assertEquals(37.5,clouds.get(7).y()+clouds.get(7).motion().offsetYAt(4147),0.001);
        assertEquals(.5,clouds.get(0).style().sample(8420).opacity(),0.001);
    }

    @Test void poisonMistChecksTheGrowingAreaFromLandingThroughDissipation() throws Exception {
        var f=poisonMistFixture();
        var sequence=Act1FighterCast.find("skill/fighter/poisonmist.skl",POISON_MIST,f.files::get,f.actions,1,"",job->90);
        assertEquals(1,sequence.attackAreas().size());
        var area=sequence.attackAreas().get(0);
        assertFalse(area.activeAt(146));
        assertTrue(area.activeAt(147));
        assertEquals(75,area.rangeAt(147).radius());
        assertEquals(162,area.rangeAt(2147).radius());
        assertEquals(250,area.rangeAt(4147).radius());
        assertEquals(300,area.range().maxZ());
        assertEquals(100,area.range().interval());
        assertTrue(area.activeAt(10146));
        assertFalse(area.activeAt(10147));
    }

    @Test void fireHitOnMaturePoisonMistStartsASeparateMineBlastAndShortensTheField() throws Exception {
        var f=poisonMistFixture();
        var choice=Act1FighterCast.alternateChoice("skill/fighter/poisonmist.skl",f.files::get,true);
        assertNotNull(choice);
        var sequence=Act1FighterCast.find("skill/fighter/poisonmist.skl",POISON_MIST,f.files::get,f.actions,1,choice.key(),job->90);
        assertNotNull(sequence);
        var blasts=sequence.visuals().stream().filter(v->v.path().endsWith("/fireexplosion.ani")).toList();
        assertTrue(blasts.size()>2);
        long first=blasts.get(0).start();
        assertTrue(first>=4247&&first<=4347);
        assertTrue(blasts.get(blasts.size()-1).start()>first+200);
        assertTrue(blasts.stream().allMatch(v->v.end()-v.start()==400&&v.scale()==1.5));
        assertEquals(blasts.size(),blasts.stream().map(v->List.of(v.x(),v.y())).distinct().count());
        assertEquals(5147,sequence.attackAreas().get(0).end());
        assertEquals(5147,sequence.visuals().stream().filter(v->v.path().endsWith("/poisonmistmist1.ani"))
                .findFirst().orElseThrow().end());

        f.files.put("passiveobject/common/animation/fireexplosion.ani",ani(400)+" [ATTACK BOX] 0 0 0 1 1 1");
        var isolated=Act1FighterCast.find("skill/fighter/poisonmist.skl",POISON_MIST,f.files::get,f.actions,1,choice.key(),job->90);
        assertEquals(1,isolated.visuals().stream().filter(v->v.path().endsWith("/fireexplosion.ani")).count());
    }

    private static final String POISON_MIST="[static data] 500 300 4000 3000 140 150 [level info] 2 49 10000";

    private static Fixture poisonMistFixture(){
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","64 `Fighter/PoisonMist.skl`");
        f.add("etc motion:23","airrolling",100,100,100);
        f.files.put(CHR+"particle/poisonmistarea.ptl","""
                [x axis] [move type] `[straight line]` [move variable 1] 50 50
                [z axis] [move type] `[straight line]` [move variable 1] -300 -300
                [gravity] 1500 [landing type] `[stop]`
                """);
        String folder="passiveobject/character/fighter/";
        f.files.put("passiveobject/passiveobject.lst","""
                21018 `Character/Fighter/PoisonMistArea.obj` 21019 `Character/Fighter/PoisonMistMine.obj`
                40002 `Common/FireExplosion.obj`
                """);
        f.files.put(folder+"poisonmistarea.obj","""
                [basic motion] `Animation/PoisonMistPouch1.ani`
                [etc motion] `Animation/PoisonMistPouch2.ani` `Animation/PoisonMistSmoke.ani` [/etc motion]
                """);
        f.files.put(folder+"animation/poisonmistpouch1.ani",ani(80,80));
        f.files.put(folder+"animation/poisonmistpouch2.ani",ani(80,80,80,80,80,100000,1500));
        f.files.put(folder+"animation/poisonmistsmoke.ani",ani(320,150,150,150,150,150,150,150,150,150,150,150,150,150,150,150000,1500));
        f.files.put(folder+"animation/poisonmistmist1.ani",ani(150,150,150,150,150,150));
        f.files.put(folder+"poisonmistmine.obj","[basic motion] `Animation/PoisonMistMine.ani`");
        f.files.put(folder+"animation/poisonmistmine.ani",ani(80)+" [DAMAGE BOX] -50 -30 -50 100 60 100");
        f.files.put("passiveobject/common/fireexplosion.obj","[basic motion] `Animation/FireExplosion.ani`");
        f.files.put("passiveobject/common/animation/fireexplosion.ani",ani(400)+" [ATTACK BOX] -65 -40 -50 130 80 100");
        return f;
    }

    @Test void nenFlowerLoadsTheSpawnFromItsMonsterCatalogAtTheCastPoint() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","67 `Fighter/NenFlower.skl`");
        f.files.put("monster/monster.lst","60012 `Fighter/NenFlower.mob`");
        f.files.put("monster/fighter/nenflower.mob","""
                [waiting motion] `Animation/NenFlowerAppearFlower.ani`
                [etc motion] `Animation/NenFlowerAppearFloor.ani` [/etc motion]
                """);
        f.files.put("monster/fighter/animation/nenflowerappearflower.ani",ani(140,420));
        f.files.put("monster/fighter/animation/nenflowerappearfloor.ani",ani(70,70,70,70,70,0));
        String text="[casting time] 800 800 [static data] 800 5000";
        var sequence=Act1FighterCast.find("skill/fighter/nenflower.skl",text,f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        assertEquals(List.of(70d,70d),sequence.visuals().stream().map(Act1SkillSequence.Visual::x).toList());
        assertEquals(800,sequence.visuals().get(0).start());
        assertEquals(1360,sequence.visuals().get(1).end());
        assertTrue(sequence.visuals().get(0).layer()<sequence.visuals().get(1).layer());
    }

    @Test void nenGuardRepeatsOnlyExpandedFramesThenDissolves() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","42 `Fighter/NenGuard.skl`");
        f.files.put("passiveobject/passiveobject.lst","21010 `Fighter/NenGuard.obj`");
        f.files.put("passiveobject/fighter/nenguard.obj","[basic motion] `Guard.ani` [etc motion] `Floor.ani` [/etc motion]");
        f.files.put("passiveobject/fighter/guard.ani",ani(100,100,100,100,100,100,100,100,100,10000,100,100,100,100));
        f.files.put("passiveobject/fighter/floor.ani",ani(100,100,100,100,100000,100,100,100,100,100));
        var sequence=Act1FighterCast.find("skill/fighter/nenguard.skl","[casting time] 700 700 [level info] 3 10000 200 450",
                f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        var cycle=sequence.visuals().stream().filter(v->v.label().contains("循环罩体")).findFirst().orElseThrow();
        assertEquals(1600,cycle.start());
        assertEquals(10300,cycle.end());
        assertEquals(500,cycle.style().loopPeriod());
        assertEquals(List.of(0,1,2,3),cycle.style().frameOverrides().keySet().stream().sorted().toList());
        assertEquals(10700,sequence.visuals().get(sequence.visuals().size()-1).end());
    }

    @Test void nenFlowerUsesConfiguredGrowthTimeBeforeItsBlast() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","67 `Fighter/NenFlower.skl`");
        f.files.put("monster/monster.lst","60012 `Fighter/NenFlower.mob`");
        f.files.put("monster/fighter/nenflower.mob","""
                [waiting motion] `Appear.ani`
                [etc motion] `Floor.ani` `Light.ani` `Bloom.ani` `BloomFloor.ani` `BloomLight.ani` [/etc motion]
                """);
        for(String name:List.of("appear","floor","light","bloom","bloomfloor","bloomlight"))
            f.files.put("monster/fighter/"+name+".ani",ani(70,70));
        f.files.put("passiveobject/passiveobject.lst","21021 `Fighter/NenFlowerBlast.obj`");
        f.files.put("passiveobject/fighter/nenflowerblast.obj","[basic motion] `Blast.ani` [etc motion] `Tile.ani` [/etc motion]");
        f.files.put("passiveobject/fighter/blast.ani",ani(300,100));
        f.files.put("passiveobject/fighter/tile.ani",ani(300,100));
        var sequence=Act1FighterCast.find("skill/fighter/nenflower.skl","[casting time] 1000 1000 [static data] 800 5000",
                f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        var bloom=sequence.visuals().stream().filter(v->v.path().endsWith("/bloom.ani")).findFirst().orElseThrow();
        assertEquals(1140,bloom.start());
        assertEquals(5000,bloom.style().animationDuration());
        assertEquals(6140,bloom.end());
        assertEquals(2,sequence.visuals().stream().filter(v->v.path().startsWith("passiveobject/")&&v.start()==6140).count());
    }

    @Test void junkSpinKeepsRotatingThroughTheScriptedThrows() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","77 `Fighter/JunkSpin.skl`");
        f.add("etc motion:33","junkspin",100,100,100,100);
        f.files.put(CHR+"particle/junkspinthrowobject.ptl","""
                [x axis] [move type] `[straight line]` [move variable 1] 120 320 [gravity] 1200 [life time] 10000
                [y axis] [move type] `[straight line]` [move variable 1] 80 240
                [z axis] [move type] `[straight line]` [move variable 1] 80 240 [landing type] `[vanish]`
                """);
        f.files.put("passiveobject/passiveobject.lst","21029 `Fighter/JunkSpinObject.obj`");
        var names=List.of("wagon","pipe","box","brick","ladder","nipper","barrel","barrel2");
        f.files.put("passiveobject/fighter/junkspinobject.obj","[basic motion] `barrel.ani` [etc motion] "+
                String.join(" ",names.stream().map(n->"`"+n+".ani`").toList())+" [/etc motion]");
        for(String name:names)f.files.put("passiveobject/fighter/"+name+".ani",ani(100));
        f.files.put(CHR+"effect/animation/junkspin/front-normal.ani",ani(100,100,100,100));
        var sequence=Act1FighterCast.find("skill/fighter/junkspin.skl","[static data] 7 150 350 160 40 100 6 10000 2 500",
                f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        var throwsAt=sequence.visuals().stream().filter(v->v.label().endsWith("投掷物")).toList();
        assertEquals(List.of(350L,700L,1050L,1400L,1750L,2100L,2450L),
                throwsAt.stream().map(Act1SkillSequence.Visual::start).toList());
        assertTrue(throwsAt.stream().anyMatch(v->v.motion().velocity()>0));
        assertTrue(throwsAt.stream().anyMatch(v->v.motion().velocity()<0));
        assertEquals(2450,sequence.visuals().get(0).end());
        assertEquals(400,sequence.visuals().get(0).style().loopPeriod());
    }

    @Test void typhoonSwitchesItsLayersIndividuallyAndFinishesOnTheQuakeFrame() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","63 `Fighter/Typhoon.skl`");
        f.add("etc motion:11","tryjumpsuplex",100,200);
        f.add("etc motion:21","typhoonspin",80,80);
        f.add("etc motion:22","typhoonquake",40,40);
        String effect=CHR+"effect/animation/typhoon";
        for(int i=1;i<=3;i++){
            f.files.put(effect+"appear"+i+".ani",ani(80*(i-1),80,80,80,80));
            f.files.put(effect+"spin"+i+".ani",ani(80,80,80,80,80,80,80,80));
        }
        f.files.put(effect+"dust1.ani",ani(80,80,80,80));
        f.files.put(effect+"dust2.ani",ani(80,80,80,80));
        for(int i=1;i<=9;i++)f.files.put(effect+"disappear"+i+".ani",ani(80,80,80,80,80));
        String text="[static data] 500 500 -1 1000 150 [level info] 4 900 594 450 5000";
        var sequence=Act1FighterCast.find("skill/fighter/typhoon.skl",text,f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        assertEquals(List.of("etc motion:11","etc motion:21","etc motion:22","waiting motion"),
                sequence.poses().stream().map(Act1SkillSequence.Pose::action).toList());
        var spin=sequence.visuals().stream().filter(v->v.path().matches(".*spin[1-3]\\.ani")).toList();
        assertEquals(List.of(620L,700L,780L),spin.stream().map(Act1SkillSequence.Visual::start).toList());
        assertEquals(1.5,spin.get(0).scale(),0.001);
        var dispersal=sequence.visuals().stream().filter(v->v.path().contains("disappear")).toList();
        assertEquals(9,dispersal.size());
        assertEquals(5340,dispersal.get(0).start());
    }

    @Test void throwEnemyShowsASeparateCatchAndTimesOutWithoutATarget() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","49 `Fighter/ThrowEnemy.skl`");
        f.add("etc motion:4","trysuplex",300);
        String path="skill/fighter/throwenemy.skl";
        String text="[static data] 500 700 200 200 500 100000 5 330 500";
        var caught=Act1FighterCast.find(path,text,f.files::get,f.actions,1,"",job->90);
        assertNotNull(caught);
        assertEquals(List.of(300L,600L),caught.poses().stream().limit(2).map(Act1SkillSequence.Pose::end).toList());
        var miss=Act1FighterCast.alternateChoice(path,f.files::get,true);
        assertNotNull(miss);
        var failed=Act1FighterCast.find(path,text,f.files::get,f.actions,1,miss.key(),job->90);
        assertEquals(330,failed.poses().get(0).end());
        assertEquals("waiting motion",failed.poses().get(1).action());
        assertTrue(failed.visuals().isEmpty());
    }

    @Test void throwEnemyUsesWallArtAtContactAndReboundsTowardTheCaster() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","49 `Fighter/ThrowEnemy.skl`");
        f.add("etc motion:4","trysuplex",300);
        for (int i=0;i<3;i++) {
            f.files.put(CHR+"effect/animation/throwenemyfloor"+i+".ani",ani(350));
            f.files.put(CHR+"effect/animation/throwenemywall"+i+".ani",ani(350));
        }
        for (String motion:List.of("stay","damage1","overturn","down")) {
            f.files.put(Act1SwordAsuraSupplement.GOBLIN+motion+".ani",ani(100));
        }
        String path="skill/fighter/throwenemy.skl";
        String text="[static data] 500 700 200 200 500 100000 5 330 500";
        var open=Act1FighterCast.find(path,text,f.files::get,f.actions,1,"",job->90);
        assertNotNull(open);
        assertTrue(open.visuals().stream().anyMatch(v->v.path().endsWith("throwenemyfloor0.ani")));
        var wall=Act1FighterCast.find(path,text,f.files::get,f.actions,1,"throwenemy:wall",job->90);
        assertNotNull(wall);
        var flight=wall.visuals().stream().filter(v->v.label().equals("抛投 · 抛出目标")).findFirst().orElseThrow();
        var rebound=wall.visuals().stream().filter(v->v.label().equals("抛投 · 撞墙反弹")).findFirst().orElseThrow();
        var impacts=wall.visuals().stream().filter(v->v.path().contains("/throwenemywall")).toList();
        assertEquals(3,impacts.size());
        assertEquals(flight.end(),rebound.start());
        assertTrue(impacts.stream().allMatch(v->v.start()==rebound.start()&&v.x()==rebound.x()&&v.y()<-30));
        assertEquals(flight.x()+flight.motion().offsetAt(flight.end()),rebound.x(),0.001);
        assertTrue(rebound.motion().offsetAt(rebound.end())<0);
        assertTrue(wall.visuals().stream().noneMatch(v->v.path().contains("/throwenemyfloor")));
    }

    @Test void lightningDanceWithoutATargetPlaysOnlyTheOpeningKick() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","68 `Fighter/LightningDance.skl`");
        f.add("attack motion:3","attack4",100,100,100);
        var sequence=Act1FighterCast.find("skill/fighter/lightningdance.skl","",f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        assertEquals("attack motion:3",sequence.poses().get(0).action());
        assertEquals(300,sequence.poses().get(0).end());
        assertEquals("waiting motion",sequence.poses().get(1).action());
        assertTrue(sequence.visuals().isEmpty());
    }

    @Test void illusionBombUsesTheRequiredCloneLevelForTheSpawnCircle() throws Exception {
        var f=castFixture();
        f.files.put("skill/fighterskill.lst","8 `Fighter/Kensin.skl` 69 `Fighter/IllusionBomb.skl`");
        f.files.put("skill/fighter/kensin.skl","[level info] 3 1 7000 75 4 7000 100");
        f.files.put("monster/monster.lst","60001 `Fighter/FighterIllusion.mob`");
        f.files.put("monster/fighter/fighterillusion.mob","""
                [waiting motion] `Animation/IllusionCreate.ani`
                [etc motion] `Animation/IllusionCreateEffect.ani` [/etc motion]
                """);
        f.files.put("monster/fighter/animation/illusioncreate.ani",ani(100,100,100,100,100));
        f.files.put("monster/fighter/animation/illusioncreateeffect.ani",ani(100,100,100,100,100));
        String text="[casting time] 600 600 [pre required skill] 8 2 [static data] 120 700";
        var sequence=Act1FighterCast.find("skill/fighter/illusionbomb.skl",text,f.files::get,f.actions,1,"",job->90);
        assertNotNull(sequence);
        assertEquals(8,sequence.visuals().size());
        var bodies=sequence.visuals().stream().filter(v->v.path().endsWith("/illusioncreate.ani")).toList();
        assertEquals(List.of(100d,0d,-100d,0d),bodies.stream().map(Act1SkillSequence.Visual::x).toList());
        assertEquals(List.of(0d,29d,0d,-29d),bodies.stream().map(Act1SkillSequence.Visual::y).toList());
        assertEquals(600,bodies.get(0).start());
    }

    private static Fixture mountFixture(){
        var fixture=new Fixture(new HashMap<>(),new HashMap<>());
        fixture.files.put("skill/fighterskill.lst","14 `Fighter/Mount.skl`");
        fixture.add("waiting motion","stay",90);
        fixture.add("etc motion:7","mount",300);
        fixture.add("etc motion:8","mountleft",150,50);
        fixture.add("etc motion:9","mountright",150,50);
        fixture.add("etc motion:34","mountend",250,250,100,100,100);
        fixture.files.put(CHR+"effect/animation/mountend.ani",ani(250,250,100,100,100));
        return fixture;
    }

    private static Fixture castFixture(){
        var f=new Fixture(new HashMap<>(),new HashMap<>());
        f.files.put("skill/fighterskill.lst","31 `Fighter/AttackSpeedUp.skl` 15 `Fighter/Sajahu.skl` 13 `Fighter/ThrowSand.skl` 54 `Fighter/ShoulderTackle.skl`");
        f.add("waiting motion","stay",80);
        f.add("buff motion","buff",100,100);
        f.add("throw motion 3-1","charge",80,80,80);
        f.add("throw motion 3-2","charge",80,80,80);
        return f;
    }

    private record Fixture(Map<String,String> files,Map<String,String> actions){
        void add(String action,String stem,int... delays){
            actions.put(action,"animation/"+stem+".ani");
            files.put(CHR+"animation/"+stem+".ani",ani(delays));
        }
    }

    private static String ani(int... delays){
        var text=new StringBuilder("[FRAME MAX] ").append(delays.length);
        for(int i=0;i<delays.length;i++)text.append(String.format(" [FRAME%03d] [DELAY] %d",i,delays[i]));
        return text.toString();
    }
}
