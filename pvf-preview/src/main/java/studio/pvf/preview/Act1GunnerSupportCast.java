package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** ACT1 Gunner state10 commands and their separately clocked passive objects. */
final class Act1GunnerSupportCast {
    private static final String ROOT="character/gunner/",PO="passiveobject/character/gunner/",ANI=PO+"animation/";
    static final String EMPTY="gunner-final:empty";
    private static final String EZ8 = "gunner-final:ez8";
    private Act1GunnerSupportCast(){}
    static List<GameEffectRecipes.Effect> choices(String path,GameScript.Lookup lookup,boolean act1)throws IOException{
        if (act1 && blackRose(path, lookup)) {
            return List.of(new GameEffectRecipes.Effect(Act1GunnerBlackRose.FOCUSED, "黑玫瑰 · 集中火力", "", path,
                    "召唤后再次施放，演示连续开火和提前退场"));
        }
        if (act1 && path.equals(Act1MechaDropRare.PATH) && Act1SkillSequence.matches(path, "gunner", 59, lookup))
            return Act1MechaDropRare.choices();
        if (!act1 || !path.equals("skill/gunner/robotsexplosion.skl")
                || !Act1SkillSequence.matches(path, "gunner", 50, lookup)) return List.of();
        return List.of(
                new GameEffectRecipes.Effect(EMPTY, "机械引爆 · 空场", "", path, "场内没有己方机器人时只显示原始指令波"),
                new GameEffectRecipes.Effect(EZ8, "机械引爆 · Ez-8", "", path, "提前引爆场内已有的 Lv.1 Ez-8"));
    }
    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,String choice)throws IOException{
        if ((choice.isEmpty() || choice.equals(Act1GunnerBlackRose.FOCUSED)) && blackRose(path, lookup)) {
            return Act1GunnerBlackRose.find(text, lookup, actions, level, choice.equals(Act1GunnerBlackRose.FOCUSED));
        }
        int id=switch(path){case "skill/gunner/camouflage.skl"->43;case "skill/gunner/robotsexplosion.skl"->50;case "skill/gunner/uraniumbomb.skl"->45;case "skill/gunner/satellitelaser.skl"->54;case Act1MechaDropRare.PATH->59;default->0;};
        var rareVariant = id == 59 ? Act1MechaDropRare.variant(choice) : null;
        if (id == 0 || !choice.isEmpty() && rareVariant == null && !(id == 50 && (choice.equals(EMPTY) || choice.equals(EZ8)))
                || !Act1SkillSequence.matches(path, "gunner", id, lookup)) return null;
        var f=GameScript.fields(text);String kind=GameScript.flag(GameScript.value(f,"type"));if(!kind.isEmpty()&&!kind.equals("active"))return null;
        for(String key:List.of("throw motion 2-1","throw motion 2-2","waiting motion"))if(!actions.containsKey(key)||Act1SkillSequence.frames(ROOT+"gunner.chr",actions.get(key),lookup).isEmpty())return null;
        // 0x600690 / 0x5F9CB0: the command vector contains prepare/recovery/mode1.
        // The unresolved mode-to-body-group selector is disclosed below, not claimed as a recovered mapping.
        long cast=id==45?Act1SkillSequence.staticValue(f,2,0):id==54?Act1SkillSequence.staticValue(f,0,0):Act1SkillSequence.castingTime(f,level,0);
        long recover=Act1SkillSequence.staticValue(f,id==54?1:id==59?10:0,0);
        if(cast<1||cast>10000||recover<1||recover>10000)return null;
        var b=new Build(lookup,cast,recover);
        switch(id){
            case 43->{
                long duration=Act1SkillSequence.levelValue(f,level,0,0,1,45000);if(duration<1)return null;
                b.visual("common/commoneffect/animation/camouflage.ani","伪装 · 消失",3500,cast,cast+700,0,0,null,1);
                b.visual("common/commoneffect/animation/decamouflage.ani","伪装 · 显形",3500,cast+duration,cast+duration+700,0,0,null,1);
                b.poses.clear();b.poses.add(new Act1SkillSequence.Pose("throw motion 2-1",0,cast,null,true,null));
                b.poses.add(new Act1SkillSequence.Pose("waiting motion",cast,cast+duration,null,false,null,0,Integer.MAX_VALUE,false,List.of(new Act1SkillSequence.VisualKeyframe(0,1,0))));
                b.poses.add(new Act1SkillSequence.Pose("waiting motion",cast+duration,cast+duration+700));
                b.note="伪装按当前等级持续时间演示敌方不可见视角，使用原始消失／显形动画；不把敌方不可见与自身半透明视角混为一谈，未模拟攻击提前解除或范围队友";
            }
            case 50->{
                int range=Act1SkillSequence.levelValue(f,level,0,0,1,3000);if(range<1)return null;
                // 0x600690: three normal objects, scale range * (1/530). Target callback 0x5F9610 filters owned RX/Ez.
                for(int i=1;i<=3;i++)b.visual(ROOT+"effect/animation/robotsexplosion"+i+".ani","机械引爆 · 指令波 "+i,3500+i,cast,cast+500+70L*(i-1),0,0,null,range/530.0);
                if(choice.equals(EMPTY))b.note="机械引爆显示随等级范围缩放的三层指令波；本预览为空场，没有可引爆的己方机器人";
                else {
                    boolean ez8 = choice.equals(EZ8);
                    var robot = ez8 ? Act1RobotAttack.Robot.EZ8 : Act1RobotAttack.Robot.RX78;
                    String mob = Act1RobotAttack.ROBOT + (ez8 ? "ez8.mob" : "rx78.mob");
                    if (!mob.equals("monster/" + GameScript.catalog(lookup.read("monster/monster.lst")).get(ez8 ? 60003 : 60002)))
                        return null;
                    var monster = GameScript.fields(lookup.read(mob));
                    String body = Act1GunnerMechanicalSupplement.motion(lookup, mob, monster, ez8 ? "etc motion" : "waiting motion",
                            0, ez8 ? "ez8_ani/stay2.ani" : "rx78_ani/stay.ani");
                    var blast = Act1RobotAttack.blast(lookup, robot);
                    if (body == null || blast == null) return null;
                    double scale = ez8 ? Act1GunnerMechanicalSupplement.ez8Scale(
                            GameScript.fields(lookup.read("skill/gunner/robotez8.skl")), 1) : 1;
                    if (scale <= 0) return null;
                    // 0x5F9610 dispatches to 0x66F4C0 for RX-78 and 0x66DB60 for Ez-8.
                    String name = ez8 ? "Ez-8" : "RX-78";
                    double x = Math.min(120, range);
                    b.visual(new Act1SkillSequence.Visual(body, "机械引爆 · 已有己方 " + name, 1, 0, cast, x, 0).withoutAttack());
                    b.end = Math.max(b.end, blast.add(b.visuals, b.emitters, "机械引爆 · " + name, cast, x, 0, 0, 30, scale));
                    b.note = "机械引爆演示指令范围内已有的一台己方 " + name + (ez8 ? "（Lv.1）" : "")
                            + " 在指令完成时立即引爆，三层指令波随等级范围缩放。可切换场内机器人或空场";
                }
            }
            case 45->{
                if(!binding(lookup,22206,"dropbomblight.obj")||!binding(lookup,22208,"lightexplosion.obj"))return null;
                int height=Act1SkillSequence.staticValue(f,1,0);double size=Act1SkillSequence.levelValue(f,level,5,0,1,1000)/100.0;
                var ints=GameScript.values(GameScript.fields(lookup.read(PO+"dropbomblight.obj")),"int data");
                if(height<1||height>1000||size<=0||ints.size()<2)return null;
                double vz=GameScript.integer(ints.get(0),0),az=GameScript.integer(ints.get(1),0);if(vz>=0||az>=0)return null;
                long flight=(long)Math.ceil(1000*(-vz-Math.sqrt(vz*vz-2*az*height))/az),impact=cast+flight;
                var motion=new SkillMotion(cast,flight,0,0,vz,az,height);
                b.visual(ANI+"dropbomblight.ani","量子爆弹 · 坠落",3500,cast,impact,0,0,motion,1);
                b.emitter(PO+"particle/dropbombtail.ptl","量子爆弹 · 尾烟",cast,flight,0,0,70,1,motion);
                b.emitter(PO+"particle/dropbomblightdestroy.ptl","量子爆弹 · 弹体碎片",impact,1,0,0,40,1,null);
                String lightObject=lookup.read(PO+"lightexplosion.obj");if(lightObject==null)return null;
                String lightRoot=GameScript.resolve(PO+"lightexplosion.obj",GameScript.value(GameScript.fields(lightObject),"basic motion"),lookup);
                if(!"passiveobject/common/animation/lightexplosion.ani".equals(lightRoot))return null;
                String lightAni=lookup.read(lightRoot);if(lightAni==null||ResourceScript.animation(lightAni).frames().isEmpty())return null;
                long lightLife=new AnimationTimeline(ResourceScript.animation(lightAni)).duration();if(lightLife<1||lightLife>3000)return null;
                // Same native generic explosion as RX (0x556A70 / 0x5C1FF0): emit until root ANI finishes.
                b.visual(lightRoot,"量子爆弹 · 光爆判定",3500,impact,impact+lightLife,0,-20,null,size);
                for(int i=1;i<=4;i++)b.stockEmitter("passiveobject/common/particle/lightexplosionparticle"+i+".ptl","量子爆弹 · 光爆 "+i,impact,lightLife,0,20,size);
                b.note="量子爆弹以角色原点作为固定准星，不输入移动瞄准；使用原始下坠速度／加速度、尾烟、地面触发碎片及等级爆炸倍率，未模拟提前撞击空中目标和感电状态";
            }
            case 54->{
                if(!binding(lookup,22211,"satelasermanager.obj"))return null;
                var managerMotions=Act1SkillSequence.objectMotions(lookup,22211);
                if(managerMotions.size()<3)return null;
                int count=Act1SkillSequence.staticValue(f,3,0),life=Act1SkillSequence.levelValue(f,level,2,0,1000,20000);
                String pt=lookup.read(PO+"particle/satelaser.ptl");if(count<1||count>8||life<1000||pt==null)return null;
                var p=ParticleScript.parse(pt);long crash=cast+500,orbit=crash+300,end=crash+life;
                b.visual(ROOT+"effect/animation/satelaser/cast1.ani","卫星射线 · 呼叫",3500,0,cast,0,0,null,1);
                b.visual(ROOT+"effect/animation/satelaser/cast2.ani","卫星射线 · 呼叫完成",3500,cast,cast+recover,0,0,null,1);
                b.visual(managerMotions.get(0),"卫星射线 · 地面预警",0,cast,crash,0,0,null,1);
                b.visual(ANI+"satelaserwarning.ani","卫星射线 · 预警",3500,cast,crash,1,.29,null,1);
                b.visual(managerMotions.get(1),"卫星射线 · 冲击地面",0,crash,orbit,0,0,null,1);
                b.visual(ANI+"satelasercrash.ani","卫星射线 · 冲击",3500,crash,orbit,0,0,null,1);
                b.visual(ANI+"satelasercrashdust.ani","卫星射线 · 冲击扬尘",3501,crash,crash+400,0,0,null,1);
                // 0x5ACA80 state 4 switches the manager to SateLaserMove.ani.
                // Its attack boxes stay at the manager origin while the beam children orbit and fade.
                b.visual(managerMotions.get(2),"卫星射线 · 持续攻击",0,orbit,end,0,0,null,1);
                for(int i=0;i<count;i++){
                    var movement=SkillMotion.orbit(orbit,end-orbit,new SkillMotion.Orbit(p.axes().get(0),p.axes().get(1),p.term(),p.changing(),360.0*i/count));
                    var fade=List.of(new Act1SkillSequence.VisualKeyframe(0,1,1),new Act1SkillSequence.VisualKeyframe(end-orbit-500,1,1),new Act1SkillSequence.VisualKeyframe(end-orbit,1,0));
                    // The first 300ms is an entrance frame, then the six 100ms frames repeat (0x5AC930).
                    var style=new Act1SkillSequence.VisualStyle(null,0,fade,Map.of(0,new Act1SkillSequence.FrameOverride(0,0,0)),false,600);
                    b.visual(ANI+"satelaser.ani","卫星射线 · 光柱进入 "+i,3500+i,crash,orbit,0,.29,null,1);
                    b.visual(ANI+"satelaser.ani","卫星射线 · 光柱 "+i,3500+i,orbit,end,0,.29,movement,1,style);
                    // Native tile draw repeats to viewport top (0x5ABEF0). Preview uses a fixed 400px column extent.
                    // Native step is scale*41, minus one at scale1 (0x5ABEF0 / 0x9F5578).
                    for(int tile=1;tile<=10;tile++){
                        b.visual(ANI+"satelasertile.ani","卫星射线 · 柱进入 "+i+"/"+tile,3500+i,crash,orbit,0,.29-tile*40,null,1);
                        b.visual(ANI+"satelasertile.ani","卫星射线 · 柱延展 "+i+"/"+tile,3500+i,orbit,end,0,.29-tile*40,movement,1,style);
                    }
                    var floorStyle=new Act1SkillSequence.VisualStyle(null,0,List.of(new Act1SkillSequence.VisualKeyframe(0,1,1),new Act1SkillSequence.VisualKeyframe(end-crash-500,1,1),new Act1SkillSequence.VisualKeyframe(end-crash,1,0)));
                    b.visual(ANI+"satelaserfloor.ani","卫星射线 · 焦点 "+i,1,crash,end,0,.29,movement,1,floorStyle);
                    b.visual(ANI+"satelaserspark.ani","卫星射线 · 电光 "+i,3510+i,crash,end,0,.29,movement,1,floorStyle);
                }
                b.note="卫星射线按原始预警、冲击、五束环绕轨迹和等级持续时间播放；固定准星，不模拟追踪敌人，柱顶以预览高度 400 像素截取；光束随机闪烁与地面灼痕随机散布未模拟";
            }
            case 59->{
                if(!binding(lookup,22223,"mechadrop.obj"))return null;
                int count=Act1SkillSequence.levelValue(f,level,2,0,2,32),width=Act1SkillSequence.levelValue(f,level,0,0,1,1000),depth=Act1SkillSequence.levelValue(f,level,1,0,1,500);
                var statics=GameScript.values(f,"static data");
                int cadence=Act1SkillSequence.staticValue(f,8,0),rows=Act1SkillSequence.staticValue(f,9,0),vx=Act1SkillSequence.staticValue(f,6,0),vz=statics.size()>7?GameScript.integer(statics.get(7),0):0;
                if(count<2||width<1||depth<1||cadence<50||cadence>1000||rows<2||rows>8||vx<0||vx>1000||vz>=0)return null;
                long move=cast+500,travel=cadence*(count-1)/rows,depart=move+travel,flight=(long)Math.ceil(400000.0/-vz);
                var plane=new SkillMotion(move,travel,width*1000.0/travel,0);
                b.visual(ANI+"mechadropappear.ani","空投支援 · 区域出现",0,cast,move,0,0,null,1);
                b.visual(ANI+"mechadropmove.ani","空投支援 · 投放区域",0,move,depart,0,0,plane,1);
                b.visual(ANI+"mechadropdisappear.ani","空投支援 · 区域消失",0,depart,depart+600,width,0,null,1);
                String robot=Act1RobotAttack.ROBOT+"rx78_ani/";
                if(!"gunner/robot/droprx78.mob".equals(GameScript.catalog(lookup.read("monster/monster.lst")).get(60013)))return null;
                var blast = Act1RobotAttack.blast(lookup, Act1RobotAttack.Robot.MECHADROP);
                if (blast == null) return null;
                double scale = Act1SkillSequence.levelValue(f, level, 6, 100, 1, 1000) / 100.0;
                int rareCount = rareVariant == null ? 0 : Act1SkillSequence.levelValue(f, level, 3, 0, 0, count);
                var rare = rareCount == 0 ? null : Act1MechaDropRare.load(lookup, f, level, rareVariant, blast, scale);
                if (rareVariant != null && rare == null) return null;
                for(int i=0;i<count;i++){
                    long spawn=move+500L+i*cadence,land=spawn+flight;
                    double x=width*i/(count-1)-100, rowDepth=depth*(i%rows)/(rows-1)-depth/2;
                    var falling=new SkillMotion(spawn,flight,vx,0,vz,0,400);
                    // 0x5A9E90 chooses distinct replacement slots. Use the first slots for the fixed preview roll.
                    if (i < rareCount) {
                        b.end = Math.max(b.end, rare.add(lookup, b.visuals, b.emitters, i, spawn, land, x, rowDepth, falling));
                        continue;
                    }
                    b.visual(new Act1SkillSequence.Visual(robot+"drop.ani","空投支援 · 下落机器人 "+i,3500,spawn,land,x,rowDepth*.29,falling,1,true).withoutAttack());
                    b.visual(new Act1SkillSequence.Visual(robot+"dropeffect.ani","空投支援 · 下落尾焰 "+i,3501,spawn,land,x,rowDepth*.29,falling,1,true).withoutAttack());
                    // 0x670020 state 16: variant 1 detonates immediately once it reaches Z=0; 0x66F4C0 spawns 22227.
                    b.end=Math.max(b.end,blast.add(b.visuals,b.emitters,"空投支援 · 普通 RX-78 "+(i+1),land,
                            x+falling.offsetAt(land),rowDepth,0,30,scale));
                }
                b.note = rare == null
                        ? "空投支援按原始投放间隔、三排范围、400 像素高度及下落速度展示普通 RX-78 落地即自爆。随机坐标抖动取中值，可切换稀有机器人场景"
                        : rare.note(rareCount);
            }
            default->throw new AssertionError(id);
        }
        return b.finish();
    }
    private static boolean blackRose(String path, GameScript.Lookup lookup) throws IOException {
        return path.equals(Act1GunnerBlackRose.PATH)
                && path.equals("skill/" + GameScript.catalog(lookup.read("skill/gunnerskill.lst")).get(70));
    }

    private static boolean binding(GameScript.Lookup l,int id,String name)throws IOException{return ("character/gunner/"+name).equals(GameScript.catalog(l.read("passiveobject/passiveobject.lst")).get(id));}
    private static final class Build {
        final GameScript.Lookup lookup;final List<Act1SkillSequence.Pose> poses=new ArrayList<>();final List<Act1SkillSequence.Visual> visuals=new ArrayList<>();final List<SkillParticleEmitter> emitters=new ArrayList<>();long end,actorEnd;boolean valid=true;String note="";
        Build(GameScript.Lookup lookup,long cast,long recover){this.lookup=lookup;actorEnd=end=cast+recover;poses.add(new Act1SkillSequence.Pose("throw motion 2-1",0,cast,null,true,null));poses.add(new Act1SkillSequence.Pose("throw motion 2-2",cast,actorEnd,null,true,null));}
        void visual(String path,String label,int layer,long start,long stop,double x,double y,SkillMotion motion,double scale)throws IOException{visual(path,label,layer,start,stop,x,y,motion,scale,Act1SkillSequence.VisualStyle.DEFAULT);}
        void visual(String path,String label,int layer,long start,long stop,double x,double y,SkillMotion motion,double scale,Act1SkillSequence.VisualStyle style)throws IOException{
            visual(new Act1SkillSequence.Visual(path,label,layer,start,stop,x,y,motion,scale,true,style));
        }
        void visual(Act1SkillSequence.Visual visual)throws IOException{
            String text=lookup.read(visual.path());
            if(text==null||ResourceScript.animation(text).frames().isEmpty())valid=false;
            visuals.add(visual);
            end=Math.max(end,visual.end());
        }
        void emitter(String path,String label,long start,long duration,double x,double y,double z,double scale,SkillMotion motion)throws IOException{String text=lookup.read(path);if(text==null){valid=false;return;}var p=ParticleScript.parse(text);emitters.add(new SkillParticleEmitter(path,label,start,duration,p.frequency(),x,y,z,Double.NaN,scale,true,scale,false,motion));end=Math.max(end,start+duration+SkillParticleEmitter.tail(p));}
        void stockEmitter(String path,String label,long start,long duration,double x,double z)throws IOException{stockEmitter(path,label,start,duration,x,z,1);}
        void stockEmitter(String path,String label,long start,long duration,double x,double z,double scale)throws IOException{String text=lookup.read(path);if(text==null){valid=false;return;}var p=ParticleScript.parse(text);emitters.add(new SkillParticleEmitter(path,label,start,duration,0,x,0,z,Double.NaN,scale,true));end=Math.max(end,start+duration+SkillParticleEmitter.tail(p));}
        Act1SkillSequence finish(){if(!valid||end>60000)return null;if(poses.get(poses.size()-1).end()<end)poses.add(new Act1SkillSequence.Pose("waiting motion",poses.get(poses.size()-1).end(),end));return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.copyOf(emitters),note+"；角色指令动作暂用同职业施放动作近似，尚未独立确认原版动作组");}
    }
}
