package studio.pvf.preview;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Gunner states 16/17/18/22: 0x600690 entry, 0x5F56F0 collision, 0x5F9CB0 update. */
final class Act1GunnerSupplement {
    static final String BBQ="skill/gunner/aerialgatling.skl",PUNISHER="skill/gunner/punisher.skl";
    static final String WINDMILL="skill/gunner/windmill.skl";
    static final String GRENADE_UP="grenade:up",GRENADE_DOWN="grenade:down",GRENADE_RELOAD="grenade:reload";
    static final String BBQ_MISS="bbq:miss",PUNISHER_MISS="punisher:miss";
    private static final String ROOT="character/gunner/",EFFECT=ROOT+"effect/animation/";
    private record Weapon(int type,int attackSpeed,List<GameScript.Field> fields){}
    private Act1GunnerSupplement(){}
    /** CNGunnerBullet::update 0x5A8F80 removes a BBQ bullet after its 50ms target-contact event. */
    static ParticleScript particleScript(String path,ParticleScript s){
        if(!path.equals(ROOT+"particle/subweapon/gatlingaerial.ptl")||!s.passive()||s.objects().size()!=1||s.objects().get(0).index()!=22217)return s;
        return new ParticleScript(s.position(),s.axes(),s.term(),s.changing(),s.gravity(),s.angles(),s.life()>0?Math.min(50,s.life()):50,s.landing(),s.alphaStart(),s.alphaEnd(),s.frequency(),s.maximum(),s.createTime(),s.area(),s.areaAngles(),s.rangeType(),s.horizontalRange(),s.verticalRange(),s.random(),s.inOrder(),s.passive(),s.layer(),s.objects(),s.warnings());
    }
    /** This stock projectile's +45-degree game rotation faces up; Java2D's screen angle has the opposite sign. */
    static ResourceScript.Animation particleVisual(String owner,String path,ResourceScript.Animation animation){
        if(!owner.equals("passiveobject/character/gunner/subweaponbulletgatlingaerial.obj")||!path.equals("passiveobject/character/gunner/animation/subweaponbulletgatlingup.ani")||animation.frames().size()!=1)return animation;
        var frame=animation.frames().get(0);
        if(!frame.image().equalsIgnoreCase("Character/Gunner/Effect/Bullet/Gatling.img")||Math.abs(frame.rotation()-Math.PI/4)>.00001)return animation;
        return new ResourceScript.Animation(animation.loop(),List.of(frame.withRotation(-frame.rotation())),animation.warnings());
    }

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,String choice,String weaponPath)throws IOException{
        return find(path,text,lookup,actions,level,choice,weaponPath,Act1BuffCast::stockHeight);
    }
    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,String choice,String weaponPath,Act1BuffCast.ActorHeight height)throws IOException{
        int grenade=grenadeId(path);
        if(grenade!=0)return binding(path,grenade,lookup)?grenade(grenade,text,lookup,actions,level,choice):null;
        int buff=buffId(path);
        if(buff!=0){
            boolean elementalShot=Act1FlameBullet.selected(path,choice);
            if(!choice.isEmpty()&&!elementalShot||!binding(path,buff,lookup))return null;
            var loaded=buff(buff,text,lookup,actions,level,height.read("gunner"));
            return elementalShot?Act1FlameBullet.appendShot(path,loaded,text,lookup,actions,level,weaponPath,choice.equals(Act1FlameBullet.WALL)):loaded;
        }
        int id=path.equals(BBQ)?14:path.equals(PUNISHER)?6:path.equals(WINDMILL)?3:0;
        if(id==0||!binding(path,id,lookup))return null;
        String missKey=id==14?BBQ_MISS:PUNISHER_MISS;
        if(!choice.isEmpty()&&!choice.equals(missKey))return null;
        var weapon=weapon(lookup,weaponPath);if(weapon==null)return null;
        if(id==3)return choice.isEmpty()?windmill(text,lookup,actions,level,weapon):null;
        return id==14?bbq(text,lookup,actions,choice.equals(missKey),weapon):punisher(text,lookup,actions,choice.equals(missKey),weapon);
    }

    private static int grenadeId(String path){return switch(path){case "skill/gunner/grenadenone.skl"->56;case "skill/gunner/grenadelight.skl"->57;case "skill/gunner/grenadewater.skl"->58;default->0;};}
    /** State28 loads the grenade. 0x6003C4 sends zero-based throw group 2 to state10. */
    private static Act1SkillSequence grenade(int id,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,String choice)throws IOException{
        if(!choice.isEmpty()&&!choice.equals(GRENADE_UP)&&!choice.equals(GRENADE_RELOAD))return null;
        String type=id==56?"none":id==57?"light":"water",label=id==56?"G-14 手雷":id==57?"G-35L 感电手雷":"G-18C 冰冻手雷";
        var fields=GameScript.fields(text);long load=Act1SkillSequence.castingTime(fields,level,500);
        var reload=body(actions,"etc motion:34","reloadinhand.ani",lookup);
        String reloadPath=EFFECT+"grenade/reload"+type+".ani";
        if(load<=0||reload.size()!=9||lookup.read(reloadPath)==null)return null;
        var poses=new ArrayList<Act1SkillSequence.Pose>();var visuals=new ArrayList<Act1SkillSequence.Visual>();
        poses.add(new Act1SkillSequence.Pose("etc motion:34",0,load,null,true,null));
        visuals.add(new Act1SkillSequence.Visual(reloadPath,label+" · 装填",2151,0,load,0,0,null,1,true,new Act1SkillSequence.VisualStyle(null,load,List.of())));
        if(choice.equals(GRENADE_RELOAD))return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),label+"首次输入只装填；再次输入才投掷，受击掉雷分支不在本次装填中触发");
        String prepareAction="throw motion 3-1",releaseAction="throw motion 3-2";
        if(body(actions,prepareAction,"momentarythrow1.ani",lookup).isEmpty()
                ||body(actions,releaseAction,"momentarythrow2.ani",lookup).isEmpty())return null;
        int prepare=Act1SkillSequence.staticValue(fields,1,0),recovery=Act1SkillSequence.staticValue(fields,2,0),fuse=Act1SkillSequence.staticValue(fields,3,0),object=Act1SkillSequence.staticValue(fields,6,0);
        if(prepare<1||prepare>5000||recovery<1||recovery>5000||fuse<1||fuse>5000||object!=22219+id-56)return null;
        String ptl=ROOT+"particle/grenade"+(choice.equals(GRENADE_UP)?"up":"center")+".ptl";String script=lookup.read(ptl);if(script==null)return null;
        var trajectory=ParticleScript.parse(script);var x=trajectory.axes().get(0);var y=trajectory.axes().get(1);var z=trajectory.axes().get(2);
        if(!x.type().equals("straight line")||x.start()!=x.end()||!y.type().equals("stop")||!z.type().equals("straight line")||z.start()!=z.end()||trajectory.gravity()<=0||!trajectory.landing().equals("stop"))return null;
        double landing=1000*(z.start()+Math.sqrt(z.start()*z.start()+2*trajectory.gravity()*74))/trajectory.gravity();
        // Longer edited fuses and low throws enter a separate native rolling/bounce state, not this airborne recipe.
        if(fuse>=landing)return null;
        var motions=Act1SkillSequence.objectMotions(lookup,object);if(motions.size()!=4)return null;
        long fire=load+prepare,boom=fire+fuse,actorEnd=fire+recovery;
        var movement=new SkillMotion(fire,fuse,x.start(),0,z.start(),-trajectory.gravity(),74);
        poses.add(new Act1SkillSequence.Pose(prepareAction,load,fire,null,true,null));
        poses.add(new Act1SkillSequence.Pose(releaseAction,fire,actorEnd,null,true,null));
        visuals.add(new Act1SkillSequence.Visual(motions.get(0),label+" · 飞行",3501,fire,boom,55,0,movement,1,true));
        String explosionText=lookup.read(motions.get(2));if(explosionText==null)return null;
        var explosion=ResourceScript.animation(explosionText);long duration=new AnimationTimeline(explosion).duration();if(explosion.loop()||duration<=0||duration>10000)return null;
        double size=Act1SkillSequence.levelValue(fields,level,1,0,1,3000)/316.0;if(size<=0)return null;
        double bx=55+movement.offsetAt(boom),by=-movement.heightAt(boom);
        for(int i=2;i<=3;i++){
            if(lookup.read(motions.get(i))==null)return null;
            visuals.add(new Act1SkillSequence.Visual(motions.get(i),label+(i==2?" · 爆炸主体":" · 爆炸光效"),3501+i,boom,boom+duration,bx,by,null,size,true));
        }
        return finish(poses,visuals,new ArrayList<>(),lookup,actions,actorEnd,label+"演示装填后立即第二次输入"+(choice.equals(GRENADE_UP)?"高抛":"平投")+"一枚手雷；使用原生 PTL 速度与重力，按引信空爆及当前等级直径显示两层爆炸，目标感电／冰冻、低抛弹跳及受击掉雷分支未执行");
    }

    private static int buffId(String path){return switch(path){
        case "skill/gunner/flamebullet.skl"->1;case "skill/gunner/freezebullet.skl"->2;case "skill/gunner/silverbullet.skl"->30;
        case "skill/gunner/stayonjumpshoot.skl"->33;case "skill/gunner/highanglesliding.skl"->37;case "skill/gunner/robotics.skl"->46;
        case "skill/gunner/revolvercriticaldamageup.skl"->60;case "skill/gunner/subweaponstuckdown.skl"->62;default->0;};}
    /** 0x5F7410 selects the common BuffIcon frames; state14 supplies the charge clock. */
    private static Act1SkillSequence buff(int id,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,int height)throws IOException{
        var fields=GameScript.fields(text);String kind=GameScript.flag(GameScript.value(fields,"type"));
        if(!kind.isEmpty()&&!kind.equals("active")||height<1||height>1000||!actions.containsKey("buff motion")||!actions.containsKey("waiting motion"))return null;
        if(Act1SkillSequence.frames(ROOT+"gunner.chr",actions.get("buff motion"),lookup).isEmpty())return null;
        int icon=switch(id){case 1->13;case 2->14;case 30->15;case 33->17;case 37->18;case 46->19;case 60->35;case 62->36;default->throw new AssertionError(id);};
        String label=switch(id){case 1->"爆炎弹";case 2->"冰冻弹";case 30->"银弹";case 33->"空中射击";case 37->"浮空铲";case 46->"机械改良";case 60->"死亡左轮";default->"潜能爆发";};
        String common="common/commoneffect/";long cast=Act1SkillSequence.castingTime(fields,level,500);
        for(String file:List.of("animation/buffstarticon.ani","animation/buffstarticonglow.ani","animation/buffstartglow.ani","particle/buffstartparticle.ptl"))if(lookup.read(common+file)==null)return null;
        var poses=new ArrayList<Act1SkillSequence.Pose>();var visuals=new ArrayList<Act1SkillSequence.Visual>();var emitters=new ArrayList<SkillParticleEmitter>();
        if(cast>0){
            if(lookup.read(common+"animation/buffchargeglow.ani")==null||lookup.read(common+"particle/buffchargeparticle.ptl")==null)return null;
            poses.add(new Act1SkillSequence.Pose("buff motion",0,cast,null,true,null));
            visuals.add(new Act1SkillSequence.Visual(common+"animation/buffchargeglow.ani",label+" · 聚气",3501,0,cast,0,-height-30));
            emitters.add(new SkillParticleEmitter(common+"particle/buffchargeparticle.ptl",label+" · 聚气粒子",0,cast,0,0,height+30));
        }
        long end=Act1BuffCast.activate(label,icon,cast,height,lookup,visuals,emitters);
        poses.add(new Act1SkillSequence.Pose("waiting motion",cast,Math.max(cast+1,end)));
        String note=label+"展示原生增益施放、聚气及生效图标；"+switch(id){
            case 1,2,30->"已装填子弹的发射与目标受击属于后续射击，不在本次增益施放中自动触发";
            case 33->"空中连续射击属于生效后的跳跃攻击，此处不自动跳起开火";
            case 37->"生效后的滑铲浮空效果需要另行滑铲并命中目标";
            case 46->"机械改良作用于已有机器人，不凭空生成机器人";
            case 60->"左轮暴击增伤需要后续攻击触发，此处不自动射击";
            default->"重火器的潜能增益作用于后续技能，此处不自动施放重火器";
        };
        return finish(poses,visuals,emitters,lookup,actions,Math.max(cast+1,end),note);
    }

    private static Act1SkillSequence windmill(String text,GameScript.Lookup lookup,Map<String,String> actions,int level,Weapon weapon)throws IOException{
        var roll=body(actions,"etc motion:7","windmillroll.ani",lookup);
        var stand=body(actions,"etc motion:8","windmillstand.ani",lookup);
        if(roll.size()!=4||stand.size()!=2)return null;
        var fields=GameScript.fields(text);int count=Act1SkillSequence.levelValue(fields,level,1,0,0,32),boost=Act1SkillSequence.levelValue(fields,level,2,0,0,500);
        if(count<1||count>32)return null;int speed=Math.max(1,(int)(weapon.attackSpeed()*(1+boost/100.0)));
        long turn=scaled(duration(roll,0,4),speed),recover=scaled(duration(stand,0,2),speed),end=count*turn+recover;
        if(turn<=0||recover<=0||end>15000)return null;
        var poses=new ArrayList<Act1SkillSequence.Pose>();var visuals=new ArrayList<Act1SkillSequence.Visual>();
        for(int i=0;i<count;i++){
            long start=i*turn;poses.add(new Act1SkillSequence.Pose("etc motion:7",start,start+turn,null,true,null));
            // These are reset on each completed turn, even though their ANI files do not declare LOOP.
            if(!visual(visuals,lookup,EFFECT+"windmilldust1.ani","回旋踢 · 前层旋风",3501,start,start+turn,0,0,speed,List.of())
                    ||!visual(visuals,lookup,EFFECT+"windmilldust2.ani","回旋踢 · 后层旋风",-1,start,start+turn,0,0,speed,List.of()))return null;
        }
        if(!visual(visuals,lookup,EFFECT+"windmilldust3.ani","回旋踢 · 末圈烟尘",3502,(count-1)*turn,Long.MAX_VALUE,0,0,speed,List.of()))return null;
        poses.add(new Act1SkillSequence.Pose("etc motion:8",count*turn,end,null,true,null));
        return finish(poses,visuals,new ArrayList<>(),lookup,actions,end,"回旋踢按当前等级旋转次数和攻速增幅完成旋转、末圈烟尘与起身；未叠加移动回旋踢或起身回旋踢的被动条件");
    }

    static List<GameEffectRecipes.Effect> choices(String path,GameScript.Lookup lookup,boolean act1)throws IOException{
        if(!act1)return List.of();
        int buff=buffId(path);
        if((buff==1||buff==2||buff==30)&&binding(path,buff,lookup))return Act1FlameBullet.choices(path,lookup);
        int grenade=grenadeId(path);
        if(grenade!=0&&binding(path,grenade,lookup))return List.of(
                new GameEffectRecipes.Effect(GRENADE_UP,"手雷 · 装填后高抛","",path,"装填后立即第二次输入，按向上方向键的原生轨迹高抛一枚"),
                new GameEffectRecipes.Effect(GRENADE_RELOAD,"手雷 · 仅装填","",path,"首次输入只装填手雷；不执行后续投掷"));
        if(path.equals(BBQ)&&binding(path,14,lookup))return List.of(new GameEffectRecipes.Effect(BBQ_MISS,"BBQ · 未命中","",path,"膝撞未抓到目标时结束，不进入向上机枪射击"));
        if(path.equals(PUNISHER)&&binding(path,6,lookup))return List.of(new GameEffectRecipes.Effect(PUNISHER_MISS,"踏射 · 未命中","",path,"未抓到目标时仅完成试探踩踏，不执行持续射击"));
        return List.of();
    }

    private static Act1SkillSequence bbq(String text,GameScript.Lookup lookup,Map<String,String> actions,boolean miss,Weapon weapon)throws IOException{
        var knee=body(actions,"etc motion:9","aerialknee.ani",lookup);if(knee.size()!=5)return null;
        if(!binding("skill/gunner/aerialknee.skl",4,lookup))return null;
        String linked=lookup.read("skill/gunner/aerialknee.skl");if(linked==null)return null;
        int linkedLevel=1;var prerequisites=GameScript.values(GameScript.fields(text),"pre required skill");
        for(int i=0;i+1<prerequisites.size();i+=2)if(GameScript.integer(prerequisites.get(i),-1)==4)linkedLevel=GameScript.integer(prerequisites.get(i+1),1);
        if(linkedLevel<1||linkedLevel>30)return null;
        int boost=Act1SkillSequence.levelValue(GameScript.fields(linked),linkedLevel,1,0,0,500);
        int speed=Math.max(1,(int)(weapon.attackSpeed()*(1+boost/100.0)));
        long grab=scaled(duration(knee,0,1),speed),tryEnd=scaled(duration(knee,0,knee.size()),speed);
        if(tryEnd<=0||tryEnd>10000)return null;
        String note="膝撞按前置技能最低已学等级 Lv."+linkedLevel+" 与当前武器攻速演示；";
        if(miss)return new Act1SkillSequence(List.of(new Act1SkillSequence.Pose("etc motion:9",0,tryEnd,null,true,null)),List.of(),List.of(),note+"未命中或目标不可抓取时不进入机枪阶段");
        var ready=body(actions,"etc motion:21","subweaponuprapid1.ani",lookup);
        var firing=body(actions,"etc motion:22","subweaponuprapid2.ani",lookup);
        int count=Act1SkillSequence.staticValue(GameScript.fields(text),0,0);
        if(ready.isEmpty()||firing.isEmpty()||count<1||count>32)return null;
        long draw=grab+200,fire=draw+duration(ready,0,ready.size()),stop=fire+100L*count,end=stop+200;
        if(fire<=draw||end>15000)return null;
        var poses=new ArrayList<Act1SkillSequence.Pose>();var visuals=new ArrayList<Act1SkillSequence.Visual>();var emitters=new ArrayList<SkillParticleEmitter>();
        poses.add(new Act1SkillSequence.Pose("etc motion:9",0,grab,null,true,null,0,1));
        poses.add(new Act1SkillSequence.Pose("etc motion:9",grab,draw,null,false,null,2,3));
        poses.add(new Act1SkillSequence.Pose("etc motion:21",draw,fire));
        poses.add(new Act1SkillSequence.Pose("etc motion:22",fire,end));
        for(String suffix:List.of("a","b")){
            int layer=suffix.equals("a")?2151:651;
            if(!visual(visuals,lookup,EFFECT+"subweapon/subweaponuprapid1"+suffix+".ani","BBQ · 机枪起势",layer,draw,fire,0,0,1000,List.of(1,0))
                    ||!visual(visuals,lookup,EFFECT+"subweapon/subweaponuprapid2"+suffix+".ani","BBQ · 机枪射击",layer,fire,end,0,0,1000,List.of(1,0)))return null;
        }
        if(!visual(visuals,lookup,EFFECT+"gatlinggunshootup.ani","BBQ · 持续枪口",2700,fire,end,0,0,1000,List.of()))return null;
        for(int i=0;i<count;i++)if(!visual(visuals,lookup,EFFECT+"gatlingshootup"+(1+i%2)+".ani","BBQ · 逐发枪口",2701,fire+100L*i,Long.MAX_VALUE,0,0,1000,List.of()))return null;
        String bullet=ROOT+"particle/subweapon/gatlingaerial.ptl",shell=ROOT+"effect/particle/gatlingshell.ptl";
        String ptl=lookup.read(bullet);if(ptl==null||lookup.read(shell)==null)return null;
        var parsed=ParticleScript.parse(ptl);
        if(!parsed.passive()||parsed.frequency()!=-1||parsed.maximum()!=1||parsed.objects().size()!=1||parsed.objects().get(0).index()!=22217
                ||Act1SkillSequence.objectMotions(lookup,22217).isEmpty())return null;
        emitters.add(new SkillParticleEmitter(bullet,"BBQ · 斜向弹道",fire,100L*count,10,148,0,155,Double.NaN,1,true));
        // The shell PTL keeps its native five-per-second cadence until the last shot.
        emitters.add(new SkillParticleEmitter(shell,"BBQ · 弹壳",fire,Math.max(1,100L*(count-1)+1),0,79,0,86,Double.NaN,1,true));
        return finish(poses,visuals,emitters,lookup,actions,end,note+"命中分支假设首个攻击帧抓到可抓取且存活的目标，等待 200ms 后向上射击；枪口随机变体固定交替，随机偏移取中心，目标受击与伤害不在无目标预览中执行");
    }

    private static Act1SkillSequence punisher(String text,GameScript.Lookup lookup,Map<String,String> actions,boolean miss,Weapon weapon)throws IOException{
        var attempt=body(actions,"etc motion:10","trytread.ani",lookup);if(attempt.size()!=2)return null;
        long grab=scaled(duration(attempt,0,1),weapon.attackSpeed()),tryEnd=scaled(duration(attempt,0,2),weapon.attackSpeed());
        if(tryEnd<=0||tryEnd>10000)return null;
        if(miss)return new Act1SkillSequence(List.of(new Act1SkillSequence.Pose("etc motion:10",0,tryEnd,null,true,null)),List.of(),List.of(),"踏射未抓到目标，只完成试探踩踏；速度使用职业基础攻速与当前武器");
        var tread=body(actions,"etc motion:11","treadshoot.ani",lookup);
        int count=Act1SkillSequence.staticValue(GameScript.fields(text),0,0),drawSpeed=drawSpeed(lookup,weapon);
        if(tread.size()!=15||tread.get(8).delay()<10000||tread.get(12).delay()<10000||count<1||count>32||drawSpeed<=0)return null;
        long fire=grab+scaled(duration(tread,0,8),drawSpeed),cycle=scaled(duration(tread,9,12),drawSpeed),recovery=scaled(duration(tread,13,15),drawSpeed);
        if(cycle<=0||recovery<=0||fire+count*cycle+recovery>20000)return null;
        var poses=new ArrayList<Act1SkillSequence.Pose>();var visuals=new ArrayList<Act1SkillSequence.Visual>();var emitters=new ArrayList<SkillParticleEmitter>();
        poses.add(new Act1SkillSequence.Pose("etc motion:10",0,grab,null,true,null,0,1));
        poses.add(new Act1SkillSequence.Pose("etc motion:11",grab,fire,null,true,null,0,8));
        // 0x5F9CB0 spawns #22207 at the frame-3 stomp, 100 units in front at ground level.
        var stomp=Act1SkillSequence.objectMotions(lookup,22207);
        if(!stomp.isEmpty()){
            long impact=grab+scaled(duration(tread,0,3),drawSpeed);
            visual(visuals,lookup,stomp.get(0),"踏射 · 踩踏判定",3501,impact,Long.MAX_VALUE,100,0,1000,List.of());
        }
        for(int n=1;n<=2;n++)if(!visual(visuals,lookup,EFFECT+"treadshootdust"+n+".ani","踏射 · 踩踏烟尘"+n,n==1?-1:1,grab,fire,0,0,drawSpeed,List.of()))return null;
        String type=switch(weapon.type()){case 0->"revolver";case 1->"automatic";case 2->"musket";case 3->"handcannon";default->"bowgun";};
        for(int i=0;i<count;i++){
            long start=fire+i*cycle;
            poses.add(new Act1SkillSequence.Pose("etc motion:11",start,start+cycle,null,true,null,9,12));
            if(!visual(visuals,lookup,EFFECT+"shooteffect/tread"+type+".ani","踏射 · 向下射击",3502,start,Long.MAX_VALUE,0,0,drawSpeed,List.of(0,0)))return null;
            if(weapon.type()==1){
                String shell=ROOT+"effect/particle/automaticshell.ptl";if(lookup.read(shell)==null)return null;
                emitters.add(new SkillParticleEmitter(shell,"踏射 · 自动手枪抛壳",start,1,0,74,0,57,Double.NaN,1,true));
            }else if(weapon.type()==2){
                if(!visual(visuals,lookup,EFFECT+"musketdust/musketdustsmall4.ani","踏射 · 步枪枪口烟尘",3503,start,Long.MAX_VALUE,63,-85,1000,List.of())
                        ||!visual(visuals,lookup,EFFECT+"musketdust/musketdustlarge.ani","踏射 · 步枪膛口烟尘",3504,start,Long.MAX_VALUE,33,-130,1000,List.of()))return null;
            }
        }
        long recover=fire+count*cycle,end=recover+recovery;
        poses.add(new Act1SkillSequence.Pose("etc motion:11",recover,end,null,true,null,13,15));
        return finish(poses,visuals,emitters,lookup,actions,end,"踏射假设首个攻击帧抓到可抓取且持续存活的目标，按当前枪械拔枪速度重复射击 "+count+" 次后收招；保留踩踏烟尘、枪械专属向下枪口和抛壳，目标相关弹道、受击、伤害与技能增益不在无目标预览中执行");
    }

    private static Act1SkillSequence finish(ArrayList<Act1SkillSequence.Pose> poses,ArrayList<Act1SkillSequence.Visual> visuals,ArrayList<SkillParticleEmitter> emitters,GameScript.Lookup lookup,Map<String,String> actions,long end,String note)throws IOException{
        long finish=end;for(var v:visuals)finish=Math.max(finish,v.end());
        for(var e:emitters)finish=Math.max(finish,e.start()+e.emitFor()+SkillParticleEmitter.tail(particleScript(e.path(),ParticleScript.parse(lookup.read(e.path())))));
        if(finish>end&&actions.containsKey("waiting motion"))poses.add(new Act1SkillSequence.Pose("waiting motion",end,finish));
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.copyOf(emitters),note);
    }
    private static boolean visual(List<Act1SkillSequence.Visual> visuals,GameScript.Lookup lookup,String path,String label,int layer,long start,long limit,double x,double y,int speed,List<Integer> variants)throws IOException{
        String text=lookup.read(path);if(text==null)return false;var ani=ResourceScript.animation(text);
        long raw=new AnimationTimeline(ani).duration();if(raw<=0||raw>10000)return false;
        long life=scaled(raw,speed),end=ani.loop()?limit:Math.min(limit,start+life);
        if(end<=start||end==Long.MAX_VALUE)return false;
        var style=new Act1SkillSequence.VisualStyle(null,speed==1000?0:life,List.of());
        visuals.add(new Act1SkillSequence.Visual(path,label,layer,start,end,x,y,null,1,true,style,variants));return true;
    }
    private static List<ResourceScript.Frame> body(Map<String,String> actions,String action,String name,GameScript.Lookup lookup)throws IOException{
        String ref=actions.get(action);return ref!=null&&GameScript.normalize(ref).endsWith("/"+name)?Act1SkillSequence.frames(ROOT+"gunner.chr",ref,lookup):List.of();
    }
    private static long duration(List<ResourceScript.Frame> frames,int first,int end){return frames.subList(first,end).stream().mapToLong(ResourceScript.Frame::delay).sum();}
    private static long scaled(long value,int speed){return (long)Math.ceil(value*1000.0/speed);}
    private static boolean binding(String path,int id,GameScript.Lookup lookup)throws IOException{return path.equals("skill/"+GameScript.catalog(lookup.read("skill/gunnerskill.lst")).get(id));}
    private static Weapon weapon(GameScript.Lookup lookup,String path)throws IOException{
        int type=0,bonus=0;List<GameScript.Field> fields=List.of();
        if(path!=null&&!path.isEmpty()){
            String text=lookup.read(GameScript.normalize(path));if(text==null)return null;fields=GameScript.fields(text);
            if(!GameScript.flag(GameScript.value(fields,"equipment type")).equals("weapon"))return null;
            type=GameScript.integer(GameScript.value(fields,"sub type"),-1);bonus=GameScript.integer(GameScript.value(fields,"attack speed"),0);
        }
        String chr=lookup.read(ROOT+"gunner.chr");if(chr==null||type<0||type>4)return null;
        double speed;try{speed=Double.parseDouble(GameScript.value(GameScript.fields(chr),"attack speed"))+bonus;}catch(NumberFormatException e){return null;}
        return Double.isFinite(speed)&&speed>=100&&speed<=5000?new Weapon(type,(int)speed,fields):null;
    }
    /** 0x5F4BC0 equipment int-data overlays table integer[9]; 0x5F3950 applies attack speed. */
    private static int drawSpeed(GameScript.Lookup lookup,Weapon weapon)throws IOException{
        String text=lookup.read("etc/gunnerdefaultguninfo.etc");if(text==null)return 0;
        int current=-1,draw=0;for(var f:GameScript.fields(text)){
            if(f.tag().equals("sub type"))current=GameScript.integer(f.first(),-1);
            else if(current==weapon.type()&&f.tag().equals("integer")&&f.values().size()>=10)draw=GameScript.integer(f.values().get(9),0);
        }
        var overrides=GameScript.values(weapon.fields(),"int data");if(overrides.size()>9&&!overrides.get(9).equals("-1"))draw=GameScript.integer(overrides.get(9),0);
        return draw>=100&&draw<=5000?Math.max(1,(int)((long)draw*weapon.attackSpeed()/1000)):0;
    }
}
