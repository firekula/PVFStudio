package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** ACT1 Gunner firing states. Input-dependent routes use explicit, deterministic preview scripts. */
final class Act1GunnerAdvancedFire {
    static final String MISS="gunner-final:miss";
    private static final String ROOT="character/gunner/",FX=ROOT+"effect/animation/",PO="passiveobject/character/gunner/";
    private static final String DESPERADO_BULLET=FX+"bullet/desperadoeffect.ani";
    private static final Set<String> NAPALM_PARTICLES=Set.of(
            PO+"animation/napalmbombfire1.ani",PO+"animation/napalmbombfire2.ani",
            PO+"animation/napalmbombfirelight1.ani",PO+"animation/napalmbombfirelight2.ani");
    private static final Set<String> NAPALM_TRACKS=Set.of(FX+"napalmbombshoot.ani",
            PO+"animation/napalmbombexplosion.ani",PO+"animation/napalmbombexplosiondodge.ani",
            PO+"animation/napalmbombarea1.ani",PO+"animation/napalmbombarea2.ani",
            PO+"animation/napalmbombareafire1.ani",PO+"animation/napalmbombareafire2.ani");
    private Act1GunnerAdvancedFire(){}
    /** 0x5AB040 propagates the Napalm projectile's pitch to its two tail creators. */
    static ParticleScript particleScript(String path,ParticleScript s){
        boolean trail=path.equals(PO+"particle/napalmbombfire.ptl")||path.equals(PO+"particle/napalmbombfirelight.ptl");
        boolean ground=path.equals(PO+"particle/napalmbombareafirelight.ptl");
        if((!trail&&!ground)||s.passive())return s;
        var angles=trail?new ParticleScript.Vec(s.angles().x(),-30,s.angles().z()):s.angles();
        var warnings=ground?s.warnings().stream().filter(w->!w.equals("瞬时无限粒子按预览上限显示")).toList():s.warnings();
        return new ParticleScript(s.position(),s.axes(),s.term(),s.changing(),s.gravity(),angles,s.life(),s.landing(),s.alphaStart(),s.alphaEnd(),s.frequency(),ground?1:s.maximum(),s.createTime(),s.area(),s.areaAngles(),s.rangeType(),s.horizontalRange(),s.verticalRange(),s.random(),s.inOrder(),s.passive(),s.layer(),s.objects(),warnings);
    }

    /** Display the original fire IMG colors for the stock Napalm tint, retaining alpha and blend. */
    static ResourceScript.Frame napalmFrame(String path,ResourceScript.Frame frame){
        boolean napalm=NAPALM_TRACKS.contains(path)||NAPALM_PARTICLES.contains(path);
        if(!napalm||frame.r()!=0||frame.g()!=100||frame.b()!=200)return frame;
        return frame.withRgb(255,255,255);
    }

    static ResourceScript.Animation particleVisual(String path,ResourceScript.Animation animation){
        if(!NAPALM_PARTICLES.contains(path))return animation;
        return new ResourceScript.Animation(animation.loop(),animation.frames().stream().map(frame->napalmFrame(path,frame)).toList(),
                animation.warnings());
    }
    static List<GameEffectRecipes.Effect> choices(String path,GameScript.Lookup lookup,boolean act1)throws IOException{
        return act1&&path.equals("skill/gunner/steyrheavyrifle.skl")&&bound(path,40,lookup)?List.of(new GameEffectRecipes.Effect(MISS,"狙击枪 · 未命中","",path,"没有目标时只演示原始飞行弹，不生成嵌入和延迟爆炸")):List.of();
    }
    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,String choice,String weaponPath)throws IOException{
        int id=switch(path){case "skill/gunner/randomshoot.skl"->9;case "skill/gunner/walkshoot.skl"->15;case "skill/gunner/steyrheavyrifle.skl"->40;case "skill/gunner/napalmbomb.skl"->53;case "skill/gunner/desperado.skl"->64;default->0;};
        boolean wall=id==15&&Act1GunnerUtilityCast.isWallChoice(path,choice);
        if(id==0||!bound(path,id,lookup)||!choice.isEmpty()&&!wall&&!(id==40&&choice.equals(MISS)))return null;
        var fields=GameScript.fields(text);String kind=GameScript.flag(GameScript.value(fields,"type"));if(!kind.isEmpty()&&!kind.equals("active"))return null;
        var b=new Build(lookup,actions);switch(id){
            case 40->steyr(b,fields,level,choice.equals(MISS));
            case 53->napalm(b,text,fields,level,weaponPath);
            case 9->random(b,fields,level,weaponPath);
            case 15->walking(b,fields,level,weaponPath,wall);
            case 64->desperado(b,fields,level);
            default->throw new AssertionError(id);
        }
        return b.finish();
    }
    private static boolean bound(String path,int id,GameScript.Lookup lookup)throws IOException{return path.equals("skill/"+GameScript.catalog(lookup.read("skill/gunnerskill.lst")).get(id));}
    private static void steyr(Build b,List<GameScript.Field> f,int level,boolean miss)throws IOException{
        if(!"character/gunner/subweaponbulletsteyr.obj".equals(GameScript.catalog(b.lookup.read("passiveobject/passiveobject.lst")).get(22203))){b.valid=false;return;}
        // 0x600690 state22 phase1 spawns 22203 at (150,0,85); state26 applies recoil and SubWeaponFrontOnce2.
        long ready=b.pose("etc motion:15",0,null,null);var recoil=new SkillMotion(ready,500,-250,500);long recover=b.pose("etc motion:16",ready,null,recoil);
        for(int phase=1;phase<=2;phase++)for(int layer=0;layer<2;layer++)b.visual(FX+"subweapon/subweaponfrontonce"+phase+(layer==0?"a":"b")+".ani","狙击枪 · 枪体",layer==0?2151:349,phase==1?0:ready,phase==1?ready:recover,0,0,phase==1?null:recoil,1,Act1SkillSequence.VisualStyle.DEFAULT,List.of(4,0));
        b.visual(FX+"shooteffect/steyrshoot.ani","狙击枪 · 枪口",3500,ready,ready+150,0,0,recoil,1);
        String ptl=ROOT+"particle/subweapon/steyr.ptl",raw=b.lookup.read(ptl);if(raw==null){b.valid=false;return;}var p=ParticleScript.parse(raw);
        if(p.axes().get(0).start()!=800||p.life()!=1000){b.valid=false;return;}
        if(miss){b.emitter(ptl,"狙击枪 · 未命中弹",ready,1,0,150,0,85,1,null);b.note="狙击枪按原生重火器动作和 (150,0,85) 枪口发射；无目标，不显示目标嵌入、命中光或延迟爆炸";return;}
        long fuse=Act1SkillSequence.levelValue(f,level,2,0,1,2000);if(fuse<1){b.valid=false;return;}
        long contact=ready+300,boom=contact+fuse;
        double targetX=390,targetHeight=40;
        var aimedShot=new SkillMotion(ready,300,800,0,(targetHeight-85)/.3,0,85);
        b.visual(PO+"animation/subweaponbulletsteyr.ani","狙击枪 · 飞行弹",3501,ready,contact,150,0,aimedShot,1);
        Act1SwordAsuraSupplement.addTarget(b.visuals,b.lookup,"stay","狙击枪 · 演示目标",0,contact,targetX,null);
        Act1SwordAsuraSupplement.addTarget(b.visuals,b.lookup,"damage1","狙击枪 · 命中目标",contact,boom,targetX,null);
        b.visual(PO+"animation/subweaponbulletsteyrhit.ani","狙击枪 · 嵌入弹",3551,contact,boom,targetX,-targetHeight,null,1);
        b.visual(FX+"hiteffect/steyrhit.ani","狙击枪 · 命中火花",3552,contact,contact+300,targetX,-targetHeight,null,1);
        // Native22218 shares 0x556A70 / 0x5C1FF0 with other generic explosions: PTLs run until root ANI ends.
        if(!"character/gunner/steyrfireexplosion.obj".equals(GameScript.catalog(b.lookup.read("passiveobject/passiveobject.lst")).get(22218))){b.valid=false;return;}
        String object=PO+"steyrfireexplosion.obj",objectText=b.lookup.read(object);if(objectText==null){b.valid=false;return;}
        String root=GameScript.resolve(object,GameScript.value(GameScript.fields(objectText),"basic motion"),b.lookup);
        if(!"passiveobject/common/animation/fireexplosion.ani".equals(root)){b.valid=false;return;}
        String rootText=b.lookup.read(root);if(rootText==null||ResourceScript.animation(rootText).frames().isEmpty()){b.valid=false;return;}
        long emission=new AnimationTimeline(ResourceScript.animation(rootText)).duration();if(emission<1||emission>3000){b.valid=false;return;}
        b.visual(root,"狙击枪 · 延迟爆炸判定",3500,boom,boom+emission,targetX,-targetHeight,null,1);
        for(int i=1;i<=4;i++)b.emitter("passiveobject/common/particle/fireexplosionparticle"+i+".ptl","狙击枪 · 延迟爆炸 "+i,boom,emission,0,targetX,0,targetHeight,1,null);
        Act1SwordAsuraSupplement.addTarget(b.visuals,b.lookup,"overturn","狙击枪 · 爆炸击倒",boom,b.end,targetX,null);
        b.note="狙击枪以枪口前方 240 像素的哥布林为固定目标，按等级引信在命中处延迟爆炸。可切换未命中；移动粘附和目标搜索未模拟。";
    }
    private static void napalm(Build b,String skill,List<GameScript.Field> f,int level,String weapon)throws IOException{
        if(!"character/gunner/napalmbomb.obj".equals(GameScript.catalog(b.lookup.read("passiveobject/passiveobject.lst")).get(22209))){b.valid=false;return;}
        // 0x5F9CB0 uses the downward second-shot clock and substitutes NapalmBombShoot and object 22209.
        var shot=Act1GunnerPrecisionCast.find(53,skill,b.lookup,b.actions,level,true,weapon);
        if(shot==null){b.valid=false;return;}
        b.poses.addAll(shot.poses());
        var muzzle=shot.visuals().get(0);
        long fire=muzzle.start();
        String ptl=ROOT+"particle/napalmbomb.ptl",raw=b.lookup.read(ptl);if(raw==null){b.valid=false;return;}var p=ParticleScript.parse(raw);
        double speed=p.axes().get(0).start(),angle=Math.toRadians(p.angles().y());double vz=speed*Math.sin(angle),vx=speed*Math.cos(angle);
        double x=muzzle.x(),height=-muzzle.y();if(vz>=0||height<=0){b.valid=false;return;}long flight=(long)Math.ceil(height/-vz*1000),impact=fire+flight;
        long life=Act1SkillSequence.levelValue(f,level,6,0,1000,20000);double explosion=Act1SkillSequence.levelValue(f,level,1,0,1,1000)/300.0,diameter=Act1SkillSequence.levelValue(f,level,2,0,1,1500);
        if(life<1000||explosion<=0||diameter<=0){b.valid=false;return;}long end=impact+life;var motion=new SkillMotion(fire,flight,vx,0,vz,0,height);double ground=x+motion.offsetAt(impact);
        b.visuals.add(muzzle);
        b.emitter(PO+"particle/napalmbombfire.ptl","地狱烈炎 · 火球尾焰",fire,flight,20,x,0,0,explosion,motion);
        b.emitter(PO+"particle/napalmbombfirelight.ptl","地狱烈炎 · 火球光",fire,flight,20,x,0,0,explosion,motion);
        for(String suffix:List.of("","dodge"))b.visual(PO+"animation/napalmbombexplosion"+suffix+".ani","地狱烈炎 · 落地爆炸"+suffix,3501,impact,impact+400,ground,0,null,explosion);
        var fade=new Act1SkillSequence.VisualStyle(null,0,List.of(new Act1SkillSequence.VisualKeyframe(0,1,0),new Act1SkillSequence.VisualKeyframe(1000,1,1),new Act1SkillSequence.VisualKeyframe(life-1000,1,1),new Act1SkillSequence.VisualKeyframe(life,1,0)));
        for(int i=1;i<=2;i++)b.visual(PO+"animation/napalmbombarea"+i+".ani","地狱烈炎 · 燃烧地面 "+i,i-1,impact,end,ground,0,null,diameter/450,fade,List.of());
        // Native ground fire locations are random. A fixed symmetric sample keeps inspection and scrubbing reproducible.
        for(int i=0;i<9;i++){
            double dx=(i%3-1)*diameter*.25,dy=(i/3-1)*diameter*.06;long delay=Math.round(2*Math.hypot(dx,dy/.29));
            // 0x5AAC40 creates paired AreaFire animations once, delayed by twice the radial distance.
            for(int layer=1;layer<=2;layer++)b.visual(PO+"animation/napalmbombareafire"+layer+".ani","地狱烈炎 · 扩散火焰 "+i+"/"+layer,3502+layer,impact+delay,impact+delay+640,ground+dx,dy,null,.85);
            b.emitter(PO+"particle/napalmbombareafirelight.ptl","地狱烈炎 · 地面火星 "+i,impact,life-1500,2,ground+dx,dy,0,1,null);
        }
        // 0x5AA450 halves the configured ground diameter. 0x5AA390 admits grounded targets only;
        // 0x5AA8D0 applies the next ground hit after the configured delay.
        int radius=(int)diameter/2,interval=Math.max(1,Act1SkillSequence.staticValue(f,0,1000));
        if(radius>0)b.areas.add(new Act1SkillSequence.AttackArea("地狱烈炎 · 地面持续伤害",impact,end,
                ground,0,new CombatDebug.TargetRange(radius,0,interval)).startingPulseAt(impact+interval));
        b.note="地狱烈炎使用原生向下射击、落地爆炸和地面持续判定。地火视觉采用固定九点取样；持续伤害按客户端的落地目标范围显示。";
    }
    private static void random(Build b,List<GameScript.Field> f,int level,String weapon)throws IOException{
        int count=Act1SkillSequence.levelValue(f,level,1,0,1,40),cadence=Act1SkillSequence.staticValue(f,0,0),type=weaponType(b.lookup,weapon);if(count<1||cadence<50||type<0){b.valid=false;return;}
        var frames=b.frames("etc motion:13");if(frames.size()!=19){b.valid=false;return;}int[] starts={0,4,9,14},ends={4,9,14,19};long end=(long)count*cadence;
        // Native random pose selection is represented by a stable four-way cycle; each pose retains its sentinel hold.
        for(int block=0;block<4;block++){
            long begin=end*block/4,stop=end*(block+1)/4;int first=starts[block],last=ends[block];long motion=(last-first-1)*150L;
            int hold=(int)Math.max(1,stop-begin-motion);b.poses.add(new Act1SkillSequence.Pose("etc motion:13",begin,stop,hold,true,null,first,last));
            var overrides=new HashMap<Integer,Act1SkillSequence.FrameOverride>();for(int i=0;i<19;i++)overrides.put(i,new Act1SkillSequence.FrameOverride(0,0,i<first||i>=last?0:i==last-1?hold:150));
            var style=new Act1SkillSequence.VisualStyle(null,stop-begin,List.of(),overrides);
            for(int layer=1;layer<=2;layer++)b.visual(FX+"shooteffect/randomshoot"+layer+".ani","乱射 · 分层枪光",layer==1?3501:499,begin,stop,0,0,null,1,style,List.of(type==1?0:type,type==4?4:0));
        }
        // 0x5F9CB0 state 20 rolls x=[-100,99], y=[-50,49], z=[0,99] per shot.
        String shot=FX+"bullet/randomshoot.ani";
        String source=b.lookup.read(shot);
        if(source!=null){
            var timeline=new AnimationTimeline(ResourceScript.animation(source));
            if(timeline.hasAttackBoxes()){
                var rng=new Random(9);
                for(int i=0;i<count;i++){
                    long start=(long)i*cadence;
                    int x=rng.nextInt(200)-100,y=rng.nextInt(100)-50,z=rng.nextInt(100);
                    b.visual(shot,"乱射 · 散射判定",3501,start,start+timeline.duration(),x,y-z,null,1);
                }
            }
        }
        b.note="乱射按等级最少发数和基础射击间隔演示；判定使用原版没有图像的弹体和三轴随机范围，位置固定取样便于逐帧检查。未模拟连打加速和目标受击";
    }
    private static void walking(Build b,List<GameScript.Field> f,int level,String weapon,boolean wall)throws IOException{
        var base=Act1GunnerPrecisionCast.find(47,"[static data] 250 [/static data]",b.lookup,b.actions,1,false,weapon);if(base==null){b.valid=false;return;}
        int shots=wall?1:3;
        long end=shots*800L;int boost=Act1SkillSequence.levelValue(f,level,1,0,0,1000);var move=new SkillMotion(0,end,60*(1+boost/1000.0),0);
        // Controlled input: walk forward for 2.4s and press attack three times. Native state23 uses pose +3/+6 for 100..300ms after each shot.
        for(int i=0;i<shots;i++){long t=i*800L;String side="etc motion:"+(i%2==0?27:30);b.pose("etc motion:24",t,t+100,move);b.pose(side,t+100,t+300,move);b.pose("etc motion:24",t+300,t+800,move);
            long fire=t;var muzzle=base.visuals().stream().filter(v->v.label().equals("枪口火光")).findFirst().orElseThrow();b.visual(muzzle.path(),"移动射击 · 枪口 "+i,3502,fire,fire+200,47,-119,move,1,muzzle.style(),muzzle.variations());
            if(wall)Act1GunnerUtilityCast.walkingWallBullet(b.visuals,b.emitters,b.lookup,weapon,fire,47,.29,120);
            else for(var e:base.emitters())if(e.label().contains("子弹"))b.emitter(e.path(),"移动射击 · 子弹 "+i,fire,1,0,47,i%2==0?1:-2,i%2==0?120:117,1,move);
        }
        b.note=wall?"移动射击演示前移时的第一发左轮弹撞墙，墙面位置为固定演示取样。"
                :"移动射击演示固定 2.4 秒前移、三次攻击输入；动作使用原生前移动及双手射击分支，等级移动倍率参与取样，60 像素／秒基础预览速度与每 800 毫秒输入是演示条件，未模拟自动瞄准、MP消耗或全套装备移动速度";
    }
    private static void desperado(Build b,List<GameScript.Field> f,int level)throws IOException{
        int ground=Act1SkillSequence.levelValue(f,level,2,0,1,40),air=Act1SkillSequence.levelValue(f,level,6,0,1,30),cadence=Act1SkillSequence.staticValue(f,2,0);if(ground<1||air<1||cadence<50){b.valid=false;return;}
        long ready=b.pose("etc motion:35",0,null,null);var rf=b.frames("etc motion:35");if(rf.size()<7){b.valid=false;return;}long impact=rf.subList(0,6).stream().mapToLong(ResourceScript.Frame::delay).sum();b.visual(FX+"desperado/ready1crash.ani","疯狂屠戮 · 起手冲击",3501,impact,impact+360,63,0,null,1);
        long second=ready+(long)(ground/2)*cadence;
        shootGround(b,ready,second,cadence);
        long secondEnd=b.pose("etc motion:36",second,null,null);b.visual(FX+"desperado/ready2crash1.ani","疯狂屠戮 · 旋击前层",3501,second+800,second+1200,0,0,null,1);b.visual(FX+"desperado/ready2crash2.ani","疯狂屠戮 · 旋击后层",499,second+800,second+1200,0,0,null,1);
        long jump=secondEnd+(long)(ground-ground/2)*cadence;shootGround(b,secondEnd,jump,cadence);
        var jf=b.frames("etc motion:37");if(jf.size()!=14){b.valid=false;return;}long jumpStart=jump+jf.subList(0,10).stream().mapToLong(ResourceScript.Frame::delay).sum(),jumpEnd=jump+jf.stream().mapToLong(ResourceScript.Frame::delay).sum();
        b.poses.add(new Act1SkillSequence.Pose("etc motion:37",jump,jumpStart,null,false,null,0,10));
        // State29 phase2 interpolates frames10..13 to z200 and unobstructed forward distance100.
        long ascent=jf.subList(10,13).stream().mapToLong(ResourceScript.Frame::delay).sum();
        var lift=new SkillMotion(jumpStart,ascent,100000.0/ascent,0,200000.0/ascent,0,0);b.poses.add(new Act1SkillSequence.Pose("etc motion:37",jumpStart,jumpEnd,null,false,lift,10,14));b.visual(FX+"desperado/ready3crash.ani","疯狂屠戮 · 腾空旋击",3501,jump+600,jump+1000,0,0,null,1);
        long airEnd=jumpEnd+(long)air*cadence;var elevated=new SkillMotion(jumpEnd,airEnd-jumpEnd,0,0,0,0,200,null,100);b.pose("etc motion:38",jumpEnd,airEnd,elevated);
        for(int i=0;i<air;i++){
            long t=jumpEnd+(long)i*cadence;
            for(int side=0;side<2;side++){var style=new Act1SkillSequence.VisualStyle(null,0,List.of(),Map.of(),side==1);b.visual(FX+"shooteffect/desperadoaerial.ani","疯狂屠戮 · 空中枪光",3501,t,t+210,0,0,elevated,1,style,List.of());}
            desperadoBullets(b,t,0,0,elevated);
        }
        long fall=(long)Math.ceil(Math.sqrt(400.0/1500)*1000),land=airEnd+fall;var falling=new SkillMotion(airEnd,fall,0,0,0,-1500,200,null,100);var landed=new SkillMotion(land,0,0,0,100);
        long finish=land+700;
        b.poses.add(new Act1SkillSequence.Pose("etc motion:40",airEnd,land,(int)Math.max(1,fall-240),false,falling,0,4));b.poses.add(new Act1SkillSequence.Pose("etc motion:40",land,finish,null,false,landed,4,5));
        long end=b.pose("etc motion:39",finish,null,landed);
        var finishFrames=b.frames("etc motion:39");
        if(finishFrames.isEmpty())return;
        // 0x5FD28E calls the per-target burst from frame index 4 of the finish animation.
        long burst=finish+finishFrames.stream().limit(Math.min(4,finishFrames.size()-1)).mapToLong(ResourceScript.Frame::delay).sum();
        double targetX=106;
        Act1SwordAsuraSupplement.addTarget(b.visuals,b.lookup,"stay","疯狂屠戮 · 演示目标",0,impact,targetX,null);
        Act1SwordAsuraSupplement.addTarget(b.visuals,b.lookup,"damage1","疯狂屠戮 · 射击受击",impact,burst,targetX,null);
        Act1SwordAsuraSupplement.addTarget(b.visuals,b.lookup,"overturn","疯狂屠戮 · 爆裂受击",burst,end,targetX,null);
        if(b.visuals.stream().anyMatch(v->v.label().equals("疯狂屠戮 · 爆裂受击"))){
            b.visual(FX+"desperado/burst.ani","疯狂屠戮 · 目标爆裂",3551,burst,Math.min(end,burst+320),targetX,-36,null,1);
        }
        b.note="疯狂屠戮演示起手、两段地面射击、腾空和落地收招；每发生成原始双层弹光，收招在演示哥布林躯干处播放独立爆裂。弹光散布及目标位置固定取样，连打加速、装备攻速和多目标聚拢未模拟。";
    }
    private static void shootGround(Build b,long start,long end,int cadence)throws IOException{
        if(start>=end)return;
        b.pose("etc motion:26",start,end,null);
        for(long t=start;t<end;t+=cadence){
            b.visual(FX+"shooteffect/desperado.ani","疯狂屠戮 · 地面枪光",3501,t,t+210,47,-119,null,1);
            desperadoBullets(b,t,47,-119,null);
        }
    }

    /** 0x5A6E80 spawns two visual objects, with distinct offsets and first-frame delays of 0 and 30 ms. */
    private static void desperadoBullets(Build b,long start,double x,double y,SkillMotion motion)throws IOException{
        for(int i=0;i<2;i++){
            boolean air=motion!=null;
            int dx=air?(i==0?-46:46):(i==0?10:39);
            int dy=air?(i==0?36:-11):(i==0?11:42);
            int delay=i*30;
            var style=new Act1SkillSequence.VisualStyle(null,0,List.of(),
                    Map.of(0,new Act1SkillSequence.FrameOverride(0,0,delay)),air&&i==0);
            b.visual(DESPERADO_BULLET,"疯狂屠戮 · 弹光",3502,start,start+350+delay,x+dx,y+dy,motion,1,style,List.of());
        }
    }
    private static int weaponType(GameScript.Lookup lookup,String path)throws IOException{if(path==null||path.isEmpty())return 0;String text=lookup.read(GameScript.normalize(path));if(text==null)return -1;int type=GameScript.integer(GameScript.value(GameScript.fields(text),"sub type"),-1);return type>=0&&type<=4?type:-1;}
    private static final class Build{
        final GameScript.Lookup lookup;final Map<String,String> actions;final List<Act1SkillSequence.Pose> poses=new ArrayList<>();final List<Act1SkillSequence.Visual> visuals=new ArrayList<>();final List<SkillParticleEmitter> emitters=new ArrayList<>();final List<Act1SkillSequence.AttackArea> areas=new ArrayList<>();boolean valid=true;long end;String note="";
        Build(GameScript.Lookup l,Map<String,String>a){lookup=l;actions=a;}
        List<ResourceScript.Frame> frames(String key)throws IOException{return Act1SkillSequence.frames(ROOT+"gunner.chr",actions.get(key),lookup);}
        long pose(String key,long start,Long stop,SkillMotion motion)throws IOException{var frames=frames(key);if(frames.isEmpty())valid=false;long finish=stop==null?start+frames.stream().mapToLong(ResourceScript.Frame::delay).sum():stop;poses.add(new Act1SkillSequence.Pose(key,start,finish,null,false,motion));end=Math.max(end,finish);return finish;}
        void visual(String path,String label,int layer,long start,long stop,double x,double y,SkillMotion motion,double scale)throws IOException{visual(path,label,layer,start,stop,x,y,motion,scale,Act1SkillSequence.VisualStyle.DEFAULT,List.of());}
        void visual(String path,String label,int layer,long start,long stop,double x,double y,SkillMotion motion,double scale,Act1SkillSequence.VisualStyle style,List<Integer> variations)throws IOException{String text=lookup.read(path);if(text==null||ResourceScript.animation(text).frames().isEmpty())valid=false;visuals.add(new Act1SkillSequence.Visual(path,label,layer,start,stop,x,y,motion,scale,true,style,variations));end=Math.max(end,stop);}
        void emitter(String path,String label,long start,long duration,double frequency,double x,double y,double z,double scale,SkillMotion motion)throws IOException{String text=lookup.read(path);if(text==null){valid=false;return;}var p=ParticleScript.parse(text);emitters.add(new SkillParticleEmitter(path,label,start,duration,frequency,x,y,z,Double.NaN,scale,true,1,false,motion));end=Math.max(end,start+duration+SkillParticleEmitter.tail(p));}
        Act1SkillSequence finish(){if(!valid||poses.isEmpty()||end>60000)return null;long actorEnd=poses.get(poses.size()-1).end();if(actorEnd<end){SkillMotion m=poses.get(poses.size()-1).motion();if(m!=null)m=new SkillMotion(end,0,0,0,m.offsetAt(actorEnd));poses.add(new Act1SkillSequence.Pose("waiting motion",actorEnd,end,null,false,m));}return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.copyOf(emitters),note,List.copyOf(areas));}
    }
}
