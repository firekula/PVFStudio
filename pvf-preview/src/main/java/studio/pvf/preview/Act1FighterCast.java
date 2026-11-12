package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.AttackArea;
import studio.pvf.preview.Act1SkillSequence.FrameOverride;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;
import studio.pvf.preview.Act1SkillSequence.VisualStyle;

/** ACT1 Fighter states with actor and world effects on separate clocks. */
final class Act1FighterCast {
    private static final String SKILLS="skill/fighter/";
    private static final String CHR="character/fighter/fighter.chr";
    private static final String EFFECT="character/fighter/effect/animation/";
    private static final String COMMON="common/commoneffect/";
    private static final String MONSTERS="monster/monster.lst";
    private static final String MOUNT=SKILLS+"mount.skl", MOUNT_MISS="mount:miss", MOUNT_CRAZY="mount:crazy";
    private static final String CRAZY_MOUNT=SKILLS+"crazymount.skl";
    private static final String THROW_ENEMY=SKILLS+"throwenemy.skl", THROW_MISS="throwenemy:miss";
    private static final String THROW_WALL="throwenemy:wall";
    private static final String CROUCH=SKILLS+"crouch.skl", CROUCH_SHOULDER="crouch:shoulder";
    private static final String ILLUSION_BOMB=SKILLS+"illusionbomb.skl", ILLUSION_NO_CONTACT="illusionbomb:no-contact";
    private static final String POISON_MIST=SKILLS+"poisonmist.skl", POISON_MIST_FIRE="poisonmist:fire";
    private static final String IDLE="waiting motion";
    private static final String BUFF="buff motion", PREPARE="throw motion 3-1", RELEASE="throw motion 3-2";
    private static final int FRONT=3501, FLOOR=300;

    private Act1FighterCast() {}

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,
                                  int level,String choice,Act1BuffCast.ActorHeight height) throws IOException {
        if(!path.startsWith(SKILLS)||!choice.isEmpty()&&!alternateSelected(path,choice)&&!Act1FighterHits.miss(path,choice))return null;
        int id=switch(path){
            case SKILLS+"shouldercharge.skl" -> 1;
            case CROUCH -> 2;
            case SKILLS+"kensin.skl" -> 8;
            case SKILLS+"throwsand.skl" -> 13;
            case MOUNT -> 14;
            case SKILLS+"sajahu.skl" -> 15;
            case SKILLS+"superarmor.skl" -> 20;
            case SKILLS+"lightenchantweapon.skl" -> 30;
            case SKILLS+"attackspeedup.skl" -> 31;
            case SKILLS+"lighttoleranceup.skl" -> 36;
            case SKILLS+"enchantpoison.skl" -> 38;
            case SKILLS+"aimvitalpoint.skl" -> 39;
            case SKILLS+"spiralnen.skl" -> 40;
            case SKILLS+"nenguard.skl" -> 42;
            case SKILLS+"doublethrow.skl" -> 43;
            case SKILLS+"provocation.skl" -> 44;
            case SKILLS+"singlekick.skl" -> 46;
            case THROW_ENEMY -> 49;
            case SKILLS+"definitegrab.skl" -> 50;
            case SKILLS+"shouldertackle.skl" -> 54;
            case SKILLS+"typhoon.skl" -> 63;
            case POISON_MIST -> 64;
            case SKILLS+"nenflower.skl" -> 67;
            case SKILLS+"lightningdance.skl" -> 68;
            case ILLUSION_BOMB -> 69;
            case SKILLS+"venommine.skl" -> 76;
            case SKILLS+"junkspin.skl" -> 77;
            default -> -1;
        };
        if(id<0||!matches(path,id,lookup))return null;
        if(choice.equals(MOUNT_MISS))return mountMiss(actions);
        var fields=GameScript.fields(text);
        return switch(id){
            case 1 -> shoulderCharge(lookup,actions);
            case 2 -> crouch(fields,lookup,actions,level,choice.equals(CROUCH_SHOULDER));
            case 8 -> kensin(fields,lookup,actions,level);
            case 13 -> sand(fields,lookup,actions);
            case 14 -> choice.equals(MOUNT_CRAZY)?crazyMount(lookup,actions):mount(fields,lookup,actions);
            case 15 -> roar(fields,lookup,actions,level);
            case 40 -> spiralNen(fields,lookup,actions,level);
            case 42 -> nenGuard(fields,lookup,actions,level);
            case 46 -> singleKick(lookup,actions);
            case 49 -> throwEnemy(fields,lookup,actions,choice.equals(THROW_MISS),choice.equals(THROW_WALL));
            case 54 -> tackle(fields,lookup,actions,Act1FighterHits.miss(path,choice));
            case 63 -> typhoon(fields,lookup,actions,level);
            case 64 -> poisonMist(fields,lookup,actions,level,choice.equals(POISON_MIST_FIRE));
            case 67 -> nenFlower(fields,lookup,actions,level);
            case 68 -> lightningDance(lookup,actions,Act1FighterHits.miss(path,choice));
            case 69 -> illusionBomb(fields,lookup,actions,level,choice.equals(ILLUSION_NO_CONTACT));
            case 76 -> venomMine(fields,lookup,actions,level);
            case 77 -> junkSpin(fields,lookup,actions,level);
            default -> buff(id,fields,lookup,actions,level,height.read("fighter"));
        };
    }

    static GameEffectRecipes.Effect alternateChoice(String path,GameScript.Lookup lookup,boolean act1) throws IOException {
        if(!act1)return null;
        if(path.equals(CROUCH)&&matches(path,2,lookup)){
            return new GameEffectRecipes.Effect(CROUCH_SHOULDER,"蹲伏 · 追加肩撞","",path,"蹲伏后按攻击键转入肩撞");
        }
        if(path.equals(MOUNT)&&matches(path,14,lookup)){
            return new GameEffectRecipes.Effect(MOUNT_MISS,"伏虎霸王拳 · 无可攻击目标","",path,"没有可攻击的倒地目标时不会进入骑乘状态");
        }
        if(path.equals(THROW_ENEMY)&&matches(path,49,lookup)){
            return new GameEffectRecipes.Effect(THROW_MISS,"抛投 · 未抓到目标","",path,"超过技能的抓取时限后回到待机");
        }
        if(path.equals(ILLUSION_BOMB)&&matches(path,69,lookup)){
            return new GameEffectRecipes.Effect(ILLUSION_NO_CONTACT,"幻影爆碎 · 无接触","",path,"分身持续至原版存续时间结束");
        }
        if(path.equals(POISON_MIST)&&matches(path,64,lookup)
                &&GameEffectRecipes.passiveObject(lookup,21019)!=null&&GameEffectRecipes.passiveObject(lookup,40002)!=null){
            return new GameEffectRecipes.Effect(POISON_MIST_FIRE,"死亡毒雾 · 火属性连锁引爆","",path,
                    "完全扩散后点燃一枚地雷，爆炸攻击盒碰到的其他地雷各自延时引爆");
        }
        return null;
    }

    static GameEffectRecipes.Effect crazyMountChoice(String path,GameScript.Lookup lookup,boolean act1) throws IOException {
        if(!act1||!path.equals(MOUNT)||!matches(path,14,lookup)||!matches(CRAZY_MOUNT,74,lookup))return null;
        return new GameEffectRecipes.Effect(MOUNT_CRAZY,"狂 · 霸王拳 · 异常状态目标","",path,
                "已学习狂 · 霸王拳，且命中处于异常状态的敌人");
    }

    static GameEffectRecipes.Effect wallThrowChoice(String path,GameScript.Lookup lookup,boolean act1) throws IOException {
        if(!act1||!path.equals(THROW_ENEMY)||!matches(path,49,lookup)||!Act1WallImpact.available(lookup))return null;
        return new GameEffectRecipes.Effect(THROW_WALL,"抛投 · 撞墙","",path,
                "演示哥布林撞墙时的三层冲击与反弹");
    }

    private static boolean alternateSelected(String path,String choice){
        return path.equals(MOUNT)&&(choice.equals(MOUNT_MISS)||choice.equals(MOUNT_CRAZY))
                ||path.equals(THROW_ENEMY)&&(choice.equals(THROW_MISS)||choice.equals(THROW_WALL))
                ||path.equals(CROUCH)&&choice.equals(CROUCH_SHOULDER)
                ||path.equals(ILLUSION_BOMB)&&choice.equals(ILLUSION_NO_CONTACT)
                ||path.equals(POISON_MIST)&&choice.equals(POISON_MIST_FIRE);
    }

    private static boolean matches(String path,int id,GameScript.Lookup lookup) throws IOException {
        return path.equals("skill/"+GameScript.catalog(lookup.read("skill/fighterskill.lst")).get(id));
    }

    /** Fighter state 12 uses DashAttack. Its ID 1 branch changes attack info and plays G_SHOULDER on frame one. */
    private static Act1SkillSequence shoulderCharge(GameScript.Lookup lookup,Map<String,String> actions) throws IOException {
        String action="dashattack motion";
        long end=motionTime(lookup,actions,action);
        if(end<=0)return null;
        return new Act1SkillSequence(List.of(new Pose(action,0,end)),List.of(),List.of(),
                "铁山靠使用原版冲刺攻击动作。进招速度与近身命中加成由实战决定。");
    }

    /** 0x5E39C0 accepts attack input in state 15 and enters the ordinary dash attack with subitem 2. */
    private static Act1SkillSequence crouch(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                           Map<String,String> actions,int level,boolean shoulder) throws IOException {
        String stance="etc motion:0", dash="dashattack motion";
        long motion=motionTime(lookup,actions,stance);
        if(motion<=0)return null;
        long limit=Act1SkillSequence.levelValue(fields,level,0,2000,1,30000);
        long dashTime=shoulder?motionTime(lookup,actions,dash):0;
        if(shoulder&&(dashTime<=0||limit<=1))return null;
        long release=shoulder?Math.min(motion,limit-1):limit;
        long end=release+dashTime;
        var poses=new ArrayList<Pose>();
        poses.add(new Pose(stance,0,release));
        if(shoulder)poses.add(new Pose(dash,release,end));
        if(actions.containsKey(IDLE))poses.add(new Pose(IDLE,end,end+150));
        return new Act1SkillSequence(List.copyOf(poses),List.of(),List.of(),
                shoulder?"蹲伏动作播完时取样攻击输入，播放原版肩撞及其攻击盒":"从静止进入蹲伏，按当前等级维持防御姿势");
    }

    /** 0x5E39C0 only enters state 19 when an eligible nearby target is already on the ground. */
    private static Act1SkillSequence mountMiss(Map<String,String> actions){
        if(!actions.containsKey(IDLE))return null;
        return new Act1SkillSequence(List.of(new Pose(IDLE,0,300)),List.of(),List.of(),
                "伏虎霸王拳需要可攻击的倒地目标；无目标时角色维持待机");
    }

    /** 0x5E9C90 uses the alternating hand motions until the final configured strike. */
    private static Act1SkillSequence mount(List<GameScript.Field> fields,GameScript.Lookup lookup,Map<String,String> actions) throws IOException {
        String mount="etc motion:7",left="etc motion:8",right="etc motion:9",finisher="etc motion:34";
        long readyTime=motionTime(lookup,actions,mount),leftTime=motionTime(lookup,actions,left),
                rightTime=motionTime(lookup,actions,right),finishTime=motionTime(lookup,actions,finisher);
        String blast=EFFECT+"mountend.ani";
        long blastTime=clipTime(lookup,blast);
        int strikes=Math.min(16,Act1SkillSequence.staticValue(fields,0,7));
        if(readyTime<=0||leftTime<=0||rightTime<=0||finishTime<=0||blastTime<=0||strikes==0)return null;
        var poses=new ArrayList<Pose>();
        poses.add(new Pose(mount,0,readyTime));
        long time=readyTime;
        for(int punch=0;punch<strikes-1;punch++){
            boolean useLeft=punch%2==0;
            long end=time+(useLeft?leftTime:rightTime);
            poses.add(new Pose(useLeft?left:right,time,end));
            time=end;
        }
        long end=time+finishTime;
        poses.add(new Pose(finisher,time,end));
        if(actions.containsKey(IDLE))poses.add(new Pose(IDLE,end,Math.max(end+150,time+blastTime)));
        var visuals=new ArrayList<Visual>();
        visuals.add(new Visual(blast,"伏虎霸王拳 · 终结冲击波",3600,time,-1,0,0));
        var finishFrames=Act1SkillSequence.frames(CHR,actions.get(finisher),lookup);
        long impact=time+Act1SkillSequence.duration(finishFrames,3,null);
        for(var clip:Act1SkillSequence.objectMotions(lookup,21024))
            addRenderOrAttack(lookup,visuals,clip,"伏虎霸王拳 · 地面冲击",FLOOR,impact,-1,60,0);
        Act1SwordAsuraSupplement.addTarget(visuals,lookup,"down","伏虎霸王拳 · 倒地目标",0,end,53,null);
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),
                "伏虎霸王拳按当前脚本的攻击次数演示骑乘、左右拳和终结冲击波");
    }

    /** The abnormal-target branch uses state 19 with the Crazy Mount flag and objects #21025–#21027. */
    private static Act1SkillSequence crazyMount(GameScript.Lookup lookup,Map<String,String> actions) throws IOException {
        String text=lookup.read(CRAZY_MOUNT);
        if(text==null||!matches(CRAZY_MOUNT,74,lookup))return null;
        String ready="etc motion:25", left="etc motion:26", right="etc motion:27", finish="etc motion:28";
        var readyFrames=Act1SkillSequence.frames(CHR,actions.get(ready),lookup);
        var leftFrames=Act1SkillSequence.frames(CHR,actions.get(left),lookup);
        var rightFrames=Act1SkillSequence.frames(CHR,actions.get(right),lookup);
        var finishFrames=Act1SkillSequence.frames(CHR,actions.get(finish),lookup);
        if(readyFrames.size()<7||leftFrames.isEmpty()||rightFrames.isEmpty()||finishFrames.size()<5)return null;
        var fields=GameScript.fields(text);
        int count=Math.min(16,Act1SkillSequence.staticValue(fields,0,8));
        if(count<2)return null;
        var visuals=new ArrayList<Visual>();
        var poses=new ArrayList<Pose>();
        long readyEnd=Act1SkillSequence.duration(readyFrames,Integer.MAX_VALUE,null);
        poses.add(new Pose(ready,0,readyEnd));
        addVisual(lookup,visuals,EFFECT+"crazymountstart.ani","狂 · 霸王拳 · 起手",FRONT,0,readyEnd,0,0,1,false);
        crazyMountStrike(lookup,visuals,21026,Act1SkillSequence.duration(readyFrames,6,null));
        long time=readyEnd;
        for(int strike=1;strike<count-1;strike++){
            boolean useLeft=strike%2==0;
            long next=time+Act1SkillSequence.duration(useLeft?leftFrames:rightFrames,Integer.MAX_VALUE,null);
            poses.add(new Pose(useLeft?left:right,time,next));
            crazyMountStrike(lookup,visuals,useLeft?21026:21027,time);
            time=next;
        }
        long end=time+Act1SkillSequence.duration(finishFrames,Integer.MAX_VALUE,null);
        poses.add(new Pose(finish,time,end));
        addVisual(lookup,visuals,EFFECT+"crazymountend.ani","狂 · 霸王拳 · 终结",FRONT,time,end,0,0,1,false);
        crazyMountStrike(lookup,visuals,21025,time+Act1SkillSequence.duration(finishFrames,4,null));
        Act1SwordAsuraSupplement.addTarget(visuals,lookup,"down","狂 · 霸王拳 · 倒地目标",0,end,53,null);
        if(actions.containsKey(IDLE))poses.add(new Pose(IDLE,end,end+150));
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),
                "演示狂 · 霸王拳击中异常状态目标时的连续范围冲击");
    }

    private static void crazyMountStrike(GameScript.Lookup lookup,List<Visual> visuals,int object,long start) throws IOException {
        var clips=Act1SkillSequence.objectMotions(lookup,object);
        for(int i=0;i<clips.size();i++){
            String label=i==0?"狂 · 霸王拳 · 地面扩散":"狂 · 霸王拳 · 拳击光效";
            addRenderOrAttack(lookup,visuals,clips.get(i),label,i==0?FLOOR:FRONT,start,-1,60,0);
        }
    }

    private static void addRenderOrAttack(GameScript.Lookup lookup,List<Visual> visuals,String path,String label,
                                   int layer,long start,long end,double x,double y) throws IOException {
        String source=lookup.read(path);
        if(source!=null&&ResourceScript.animation(source).frames().stream().anyMatch(f->!f.image().isEmpty()||!f.combat().attack().isEmpty()))
            addVisual(lookup,visuals,path,label,layer,start,end,x,y,1,false);
    }

    private static long motionTime(GameScript.Lookup lookup,Map<String,String> actions,String action) throws IOException {
        return Act1SkillSequence.duration(Act1SkillSequence.frames(CHR,actions.get(action),lookup),Integer.MAX_VALUE,null);
    }

    private static long clipTime(GameScript.Lookup lookup,String path) throws IOException {
        String text=lookup.read(path);
        return text==null?0:new AnimationTimeline(ResourceScript.animation(text)).duration();
    }

    /** 0x5E2260 and #48015 (0x5C0450) select the recipient's activation effect. */
    private static Act1SkillSequence buff(int id,List<GameScript.Field> fields,GameScript.Lookup lookup,
                                          Map<String,String> actions,int level,int height) throws IOException {
        if(!actions.containsKey(BUFF)||!actions.containsKey(IDLE))return null;
        String label=switch(id){
            case 20 -> "霸体护甲";
            case 30 -> "光之兵刃";
            case 31 -> "风之气息";
            case 36 -> "烈日光辉";
            case 38 -> "涂毒";
            case 39 -> "弱点感知";
            case 43 -> "双重投掷";
            case 44 -> "挑衅";
            case 50 -> "暴力抓取";
            default -> throw new IllegalArgumentException("Fighter buff " + id);
        };
        int icon=switch(id){
            case 30 -> 2;
            case 31 -> 3;
            case 36 -> 11;
            case 38 -> 8;
            case 39 -> 10;
            case 43 -> 22;
            case 50 -> 25;
            default -> -1;
        };
        int z=height+30;
        long cast=Act1SkillSequence.castingTime(fields,level,500);
        var armor=id==20?Act1SuperArmor.fighterBuff(fields,level):null;
        var visuals=new ArrayList<Visual>();
        var particles=new ArrayList<SkillParticleEmitter>();
        String glow=COMMON+"animation/buffchargeglow.ani";
        long end=(armor==null?cast:armor.end())+350;
        if(cast>0){
            if(lookup.read(glow)!=null)visuals.add(new Visual(glow,label+" · 聚气",FRONT,0,cast,0,-z));
            end=Math.max(end,emit(lookup,particles,COMMON+"particle/buffchargeparticle.ptl",label+" · 聚气粒子",0,cast,0,z,false));
        }
        if(icon>=0)end=Math.max(end,Act1BuffCast.activate(label,icon,cast,height,lookup,visuals,particles));
        if(id==39||id==44){
            // 0x5E2260 scales the three staggered ground ANIs by the configured diameter / 530.
            int diameterColumn=id==39?2:1;
            double scale=Act1SkillSequence.levelValue(fields,level,diameterColumn,id==39?500:400,1,3000)/530.0;
            String animation=id==39?"aimvitalpoint":"provocation";
            for(int i=1;i<=3;i++)end=Math.max(end,addVisual(lookup,visuals,EFFECT+animation+i+".ani",label+" · 范围",
                    FLOOR+i,cast,-1,0,0,scale,false));
            if(id==44){
                // #48015 applies CNProvocationed on contact. 0x53FB90 attaches the ANI above the target.
                double targetX=Math.min(95,scale*150);
                int targetZ=Act1SwordAsuraSupplement.goblinHeight(lookup)+30;
                long marked=addVisual(lookup,visuals,EFFECT+"provocationed.ani","挑衅 · 目标标记",
                        FRONT+100,cast,-1,targetX,-targetZ,1,false);
                if(marked>cast){
                    end=Math.max(end,marked);
                    Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay","挑衅 · 目标",0,end,targetX,null);
                }
            }
        }
        if(armor==null&&visuals.isEmpty()&&particles.isEmpty())return null;
        var poses=new ArrayList<Pose>();
        if(cast>0)poses.add(new Pose(BUFF,0,cast,null,true,null));
        poses.add(new Pose(IDLE,cast,end));
        String note=switch(id){
            case 20 -> "聚气完成后进入霸体，持续时间由当前技能等级决定";
            case 38 -> "涂毒展示聚气与生效提示；施毒需要武器命中目标";
            case 39 -> "弱点感知展示原版生效图标和三层范围，持续状态调整自身暴击和敌人回避";
            case 43 -> "双重投掷展示聚气与生效提示；双投发生在后续道具投掷中";
            case 44 -> "挑衅按等级范围播放三层扩散光效，并显示范围内哥布林的原版头顶标记";
            case 50 -> "暴力抓取展示聚气与生效提示；增强判定作用于后续抓取";
            default -> label+"以施法者为示例目标，展示施法和生效提示";
        };
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.copyOf(particles),note);
    }

    /** State 10 creates #21004 on throw body frame 1, which starts two instant particle groups. */
    private static Act1SkillSequence sand(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                          Map<String,String> actions) throws IOException {
        String prepare="throw motion 1-1",throwAction="throw motion 1-2";
        if(!actions.containsKey(prepare)||!actions.containsKey(throwAction))return null;
        var release=Act1SkillSequence.frames(CHR,actions.get(throwAction),lookup);
        if(release.size()<2)return null;
        String object=GameEffectRecipes.passiveObject(lookup,21004);
        if(object==null)return null;
        long cast=Act1SkillSequence.staticValue(fields,0,150);
        long launch=cast+release.get(0).delay();
        long recovered=Math.max(launch+1,cast+Act1SkillSequence.staticValue(fields,1,500));
        var emitters=new ArrayList<SkillParticleEmitter>();
        long end=recovered;
        for(int i=1;i<=2;i++){
            String ptl=GameScript.resolve(object,"Particle/ThrowSandParticle"+i+".ptl",lookup);
            if(ptl!=null)end=Math.max(end,emit(lookup,emitters,ptl,"抛沙 · 沙尘",launch,1,65,50,true));
        }
        var visuals=new ArrayList<Visual>();
        var motions=Act1SkillSequence.objectMotions(lookup,21004);
        if(!motions.isEmpty()){
            addRenderOrAttack(lookup,visuals,motions.get(0),"抛沙 · 攻击判定",FRONT,launch,-1,65,-50);
            end=Math.max(end,launch+clipTime(lookup,motions.get(0)));
        }
        if(emitters.isEmpty()&&visuals.isEmpty())return null;
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,prepare,throwAction,cast,recovered,end,false),
                List.copyOf(visuals),List.copyOf(emitters),"抛沙在出手第 2 帧从前方 65、离地 50 的位置散出沙尘，并播放原版攻击判定");
    }

    /** 0x5E9C90 attaches two unmirrored roar layers at Y=-65 and starts #21005 at the feet. */
    private static Act1SkillSequence roar(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                          Map<String,String> actions,int level) throws IOException {
        if(!actions.containsKey(PREPARE)||!actions.containsKey(RELEASE))return null;
        var motions=Act1SkillSequence.objectMotions(lookup,21005);
        if(motions.isEmpty())return null;
        long cast=Act1SkillSequence.castingTime(fields,level,700);
        long recovered=cast+Math.max(1,Act1SkillSequence.staticValue(fields,0,800));
        var visuals=new ArrayList<Visual>();
        addVisual(lookup,visuals,motions.get(0),"狮子吼 · 地面冲击",FLOOR,cast,recovered,0,0,1,false);
        for(int i=1;i<=2;i++)addVisual(lookup,visuals,EFFECT+"roarstun"+i+".ani","狮子吼 · 扩散声波",FRONT,cast,recovered,0,-65,1,false);
        if(visuals.isEmpty())return null;
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,PREPARE,RELEASE,cast,recovered,recovered,false),
                List.copyOf(visuals),List.of(),"狮子吼按施放时钟展开地面冲击和声波；眩晕需要实际目标");
    }

    /** #21010 holds its expanded shield until 400 ms before its configured lifetime ends (0x59B2E0). */
    private static Act1SkillSequence nenGuard(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                              Map<String,String> actions,int level) throws IOException {
        if(!actions.containsKey(PREPARE)||!actions.containsKey(RELEASE))return null;
        var clips=Act1SkillSequence.objectMotions(lookup,21010);
        if(clips.size()<2)return null;
        long cast=Act1SkillSequence.castingTime(fields,level,700);
        long recovered=cast+Math.max(1,Act1SkillSequence.staticValue(fields,0,700));
        int life=Act1SkillSequence.levelValue(fields,level,0,10000,1,40000);
        long end=cast+life;
        var shell=guardClip(lookup,clips.get(0),"念气罩 · 罩体",3600,cast,life,9);
        var floor=guardClip(lookup,clips.get(1),"念气罩 · 地面光圈",FLOOR,cast,life,4);
        if(shell==null||floor==null)return null;
        var frames=ResourceScript.animation(lookup.read(clips.get(0))).frames();
        long opened=cast+Act1SkillSequence.duration(frames,9,null);
        long fade=Math.max(opened,end-400);
        long period=Act1SkillSequence.duration(frames,9,null)-Act1SkillSequence.duration(frames,4,null);
        if(opened<fade&&period>0&&frames.size()>10){
            var skipped=new HashMap<Integer,FrameOverride>();
            for(int i=0;i<4;i++)skipped.put(i,new FrameOverride(0,0,0));
            var cycle=new VisualStyle(null,0,List.of(),skipped,false,period);
            var visuals=new ArrayList<Visual>();
            visuals.add(floor);
            visuals.add(new Visual(shell.path(),"念气罩 · 展开",3600,cast,opened,0,0));
            visuals.add(new Visual(shell.path(),"念气罩 · 循环罩体",3600,opened,fade,0,0,null,1,false,cycle));
            var dissolve=new HashMap<Integer,FrameOverride>();
            for(int i=0;i<10;i++)dissolve.put(i,new FrameOverride(0,0,0));
            visuals.add(new Visual(shell.path(),"念气罩 · 消散",3600,fade,end,0,0,
                    null,1,false,new VisualStyle(null,0,List.of(),dissolve)));
            return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,PREPARE,RELEASE,cast,recovered,Math.max(end,recovered),false),
                    List.copyOf(visuals),List.of(),"念气罩展开后循环播放罩体，到期前 400 毫秒开始消散");
        }
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,PREPARE,RELEASE,cast,recovered,Math.max(end,recovered),false),
                List.of(floor,shell),List.of(),"念气罩在施放点展开，按当前等级维持");
    }

    private static Visual guardClip(GameScript.Lookup lookup,String path,String label,int layer,long start,int life,int holdFrame) throws IOException {
        String text=lookup.read(path);
        if(text==null)return null;
        var frames=ResourceScript.animation(text).frames();
        if(frames.size()<=holdFrame)return null;
        long intro=Act1SkillSequence.duration(frames,holdFrame,null);
        int hold=(int)Math.max(1,life-400-intro);
        var style=new VisualStyle(null,0,List.of(),Map.of(holdFrame,new FrameOverride(0,0,hold)));
        return new Visual(path,label,layer,start,start+life,0,0,null,1,false,style);
    }

    /** State 10 gives #21007 the authored SpiralNen PTL at a base height of 50 px. */
    private static Act1SkillSequence spiralNen(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                               Map<String,String> actions,int level) throws IOException {
        if(!actions.containsKey(PREPARE)||!actions.containsKey(RELEASE))return null;
        var motions=Act1SkillSequence.objectMotions(lookup,21007);
        String motion=lookup.read("character/fighter/particle/spiralnen.ptl");
        if(motions.isEmpty()||motion==null)return null;
        var ptl=ParticleScript.parse(motion);
        var axes=ptl.axes();
        long cast=Act1SkillSequence.castingTime(fields,level,500);
        long recovered=cast+Math.max(1,Act1SkillSequence.staticValue(fields,0,2000));
        int lifetime=Act1SkillSequence.levelValue(fields,level,1,20000,1,40000);
        var orbit=new SkillMotion.Orbit(axes.get(0),axes.get(1),ptl.term(),ptl.changing(),0,axes.get(2));
        var visual=new Visual(motions.get(0),"念气环绕 · 念气珠",3600,cast,cast+lifetime,0,-50,
                SkillMotion.orbit(cast,lifetime,orbit),1,true);
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,PREPARE,RELEASE,cast,recovered,Math.max(recovered,cast+lifetime),false),
                List.of(visual),List.of(),"一次施放生成一枚念气珠，按 PTL 轨迹绕角色转动；叠加与碰撞消耗需要实战条件");
    }

    /** State 24 forwards attack index 3 into the common state-8 attack motion loader. */
    private static Act1SkillSequence singleKick(GameScript.Lookup lookup,Map<String,String> actions) throws IOException {
        String action="attack motion:3";
        long end=motionTime(lookup,actions,action);
        if(end<=0)return null;
        return new Act1SkillSequence(List.of(new Pose(action,0,end)),List.of(),List.of(),
                "前踢使用第四段攻击姿势；击退表现需要实际目标");
    }

    /** State 17 catches the victim, then state 10 releases it with #48028 tracking terrain contact. */
    private static Act1SkillSequence throwEnemy(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                                Map<String,String> actions,boolean miss,boolean wall) throws IOException {
        String grab="etc motion:4";
        long motion=motionTime(lookup,actions,grab);
        if(motion<=0)return null;
        long timeout=Math.max(1,Act1SkillSequence.staticValue(fields,7,330));
        var poses=new ArrayList<Pose>();
        var visuals=new ArrayList<Visual>();
        long end=miss?timeout:Math.min(timeout,motion);
        poses.add(new Pose(grab,0,end));
        boolean bounced=false;
        if(!miss){
            String floor=EFFECT+"throwenemyfloor";
            boolean hasFloor=lookup.read(floor+"0.ani")!=null;
            boolean hasImpact=hasFloor||wall&&Act1WallImpact.available(lookup);
            long release=end+(hasImpact?Math.max(1,Act1SkillSequence.staticValue(fields,0,500)):motion);
            poses.add(new Pose(grab,end,release));
            end=release;
            if(hasImpact){
                double targetX=38;
                double speed=Math.max(0,Math.min(3000,Act1SkillSequence.staticValue(fields,1,700)));
                double launchZ=Math.max(0,Math.min(1500,Act1SkillSequence.staticValue(fields,2,200)));
                double height=20;
                long airborne=Act1WallImpact.airborne(height,launchZ);
                long landing=release+airborne;
                var flight=new SkillMotion(release,airborne,speed,0,launchZ,-Act1WallImpact.GRAVITY,height);
                double origin=targetX+40;
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay","抛投 · 目标",0,poses.get(0).end(),targetX,null);
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"damage1","抛投 · 抓取目标",poses.get(0).end(),release,
                        targetX,new SkillMotion(0,0,0,0,0,0,height+10));
                bounced=wall&&speed>0&&Act1WallImpact.available(lookup);
                long impact=bounced?Act1WallImpact.contactTime(flight):landing;
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"overturn","抛投 · 抛出目标",release,impact,origin,flight);
                if(bounced){
                    end=Act1WallImpact.addRebound(visuals,lookup,fields,"抛投",origin,flight,impact);
                }else{
                    // 0x5BF7C0 attaches the ring and sets the remaining floor layers to the ground plane.
                    double landingX=origin+flight.offsetAt(landing);
                    double scale=Act1SwordAsuraSupplement.goblinHeight(lookup)/160.0;
                    end=landing+180;
                    for(int i=0;i<3;i++)end=Math.max(end,addVisual(lookup,visuals,floor+i+".ani","抛投 · 落地冲击",
                            i==0?FRONT+100:FLOOR+i,landing,-1,landingX,0,scale,true));
                    Act1SwordAsuraSupplement.addTarget(visuals,lookup,"down","抛投 · 落地目标",landing,end,landingX,null);
                }
            }
        }
        long actorEnd=poses.get(poses.size()-1).end();
        if(actions.containsKey(IDLE))poses.add(new Pose(IDLE,actorEnd,Math.max(end,actorEnd+180)));
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),miss
                ?"抛投未抓到目标时，在技能设定的抓取时限结束后回到待机"
                :bounced?"抛投演示哥布林撞墙、反弹和倒地。墙面位置为固定演示取样"
                :"抛投以开阔地面上的哥布林为例，播放抓取、飞行和落地冲击");
    }

    /** State 32 lands the first kick via 0x5E0FD0, then searches for a different eligible target. */
    private static Act1SkillSequence lightningDance(GameScript.Lookup lookup,Map<String,String> actions,boolean miss) throws IOException {
        String kick="attack motion:3";
        long duration=motionTime(lookup,actions,kick);
        if(duration<=0)return null;
        var poses=new ArrayList<Pose>();
        var visuals=new ArrayList<Visual>();
        poses.add(new Pose(kick,0,duration));
        String motion=GameScript.resolve(CHR,actions.get(kick),lookup);
        long contact=miss||motion==null?-1:Act1FighterHits.firstForwardAttack(lookup.read(motion),64);
        long end=duration+180;
        if(contact>=0){
            end=Math.max(end,addVisual(lookup,visuals,EFFECT+"lightningdancehit.ani","闪电之舞 · 命中电光",
                    FRONT+100,contact,-1,64,5*.29-65,1,true));
            Act1FighterHits.addTarget(lookup,visuals,"闪电之舞",64,contact,end,"stay");
        }
        if(actions.containsKey(IDLE))poses.add(new Pose(IDLE,duration,end));
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),
                contact>=0?"闪电之舞演示首踢命中一名目标；附近无其他敌人时结束":"闪电之舞演示未命中的起手");
    }

    /** State 25 releases the shoulder effect after the charge hold and applies X motion with deceleration. */
    private static Act1SkillSequence tackle(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                            Map<String,String> actions,boolean miss) throws IOException {
        String action="etc motion:15";
        var body=Act1SkillSequence.frames(CHR,actions.get(action),lookup);
        if(body.size()<3)return null;
        String path=EFFECT+"shouldertackle.ani";
        long visualTime=clipTime(lookup,path), release=body.get(0).delay();
        if(visualTime<=0)return null;
        long recovery=Act1SkillSequence.duration(body.subList(2,body.size()),Integer.MAX_VALUE,null);
        var data=GameScript.values(fields,"static data");
        int speed=Act1SkillSequence.staticValue(fields,2,400);
        int acceleration=data.size()>4?Math.min(-1,GameScript.integer(data.get(4),-1000)):-1000;
        long travel=Math.min(recovery,(long)Math.ceil(-1000.0*speed/acceleration));
        var motion=new SkillMotion(release,travel,speed,acceleration);
        var poses=List.of(new Pose(action,0,release,null,false,null,0,1),
                new Pose(action,release,release+recovery,null,false,motion,2));
        var visual=new Visual(path,"野蛮冲撞 · 冲撞光效",FRONT,release,release+Math.min(visualTime,recovery),0,0,motion,1,true);
        var visuals=new ArrayList<Visual>();
        visuals.add(visual);
        String hit=EFFECT+"shouldertacklehit.ani";
        if(!miss&&lookup.read(hit)!=null){
            double targetX=82;
            long contact=release;
            while(contact<release+travel&&motion.offsetAt(contact)+48<targetX)contact++;
            if(motion.offsetAt(contact)+48>=targetX){
                long end=addVisual(lookup,visuals,hit,"野蛮冲撞 · 肩撞命中",FRONT+100,contact,-1,targetX,-41,1,true);
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay","野蛮冲撞 · 目标",0,contact,targetX,null);
                var flight=new SkillMotion(contact,300,220,-350,220,-1500);
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"overturn","野蛮冲撞 · 目标击飞",contact,Math.max(end,contact+300),
                        targetX,flight);
            }
        }
        return new Act1SkillSequence(poses,List.copyOf(visuals),List.of(),"野蛮冲撞演示短按出手、冲撞特效和小怪被击飞");
    }

    /** In state 30 each appearance layer becomes its own spin loop; quake frame 1 starts all nine finish layers. */
    private static Act1SkillSequence typhoon(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                             Map<String,String> actions,int level) throws IOException {
        String attempt="etc motion:11", spin="etc motion:21", quake="etc motion:22";
        long attemptTime=motionTime(lookup,actions,attempt), spinCycle=motionTime(lookup,actions,spin);
        var quakeFrames=Act1SkillSequence.frames(CHR,actions.get(quake),lookup);
        if(attemptTime<=0||spinCycle<=0||quakeFrames.size()<2)return null;
        long caught=Math.min(attemptTime,Math.max(1,Act1SkillSequence.staticValue(fields,0,500)));
        long quakeStart=caught+Act1SkillSequence.levelValue(fields,level,3,5000,1,20000);
        long disperse=quakeStart+quakeFrames.get(0).delay();
        long end=quakeStart+Act1SkillSequence.duration(quakeFrames,Integer.MAX_VALUE,null);
        double scale=Act1SkillSequence.staticValue(fields,4,150)/100.0;
        var visuals=new ArrayList<Visual>();
        String prefix=EFFECT+"typhoon";
        for(int i=1;i<=3;i++){
            long next=addVisual(lookup,visuals,prefix+"appear"+i+".ani","末日风暴 · 风暴形成",FRONT+i,caught,disperse,0,0,scale,true);
            String loop=prefix+"spin"+i+".ani";
            if(next>caught&&next<disperse&&lookup.read(loop)!=null){
                visuals.add(new Visual(loop,"末日风暴 · 旋转风暴",FRONT+i,next,disperse,0,0,null,scale,true));
            }
        }
        long dustStart=addVisual(lookup,visuals,prefix+"dust1.ani","末日风暴 · 扬尘",3500,caught,disperse,0,0,scale,true);
        String dustLoop=prefix+"dust2.ani";
        if(dustStart>caught&&dustStart<disperse&&lookup.read(dustLoop)!=null){
            visuals.add(new Visual(dustLoop,"末日风暴 · 旋转扬尘",FRONT+20,dustStart,disperse,0,0,null,scale,true));
        }
        for(int i=1;i<=9;i++){
            end=Math.max(end,addVisual(lookup,visuals,prefix+"disappear"+i+".ani","末日风暴 · 终结散开",FRONT+i,disperse,-1,0,0,scale,true));
        }
        if(visuals.isEmpty())return null;
        var poses=new ArrayList<Pose>();
        poses.add(new Pose(attempt,0,caught));
        poses.add(new Pose(spin,caught,quakeStart));
        long recovered=quakeStart+Act1SkillSequence.duration(quakeFrames,Integer.MAX_VALUE,null);
        poses.add(new Pose(quake,quakeStart,recovered));
        if(actions.containsKey(IDLE)&&end>recovered)poses.add(new Pose(IDLE,recovered,end));
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),
                "末日风暴演示原地抓取成功后的风暴形成、持续旋转和终结；实际移动与目标受击由实战提供");
    }

    /** State 31 throws #21018. 0x59CC80 grows the poison radius and 0x59CA60 scans actors every 100 ms. */
    private static Act1SkillSequence poisonMist(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                                Map<String,String> actions,int level,boolean fire) throws IOException {
        String roll="etc motion:23";
        long rollTime=motionTime(lookup,actions,roll);
        String object=GameEffectRecipes.passiveObject(lookup,21018);
        var clips=Act1SkillSequence.objectMotions(lookup,21018);
        if(rollTime<=0||object==null||clips.size()<3)return null;
        String cloud=GameScript.resolve(object,"Animation/PoisonMistMist1.ani",lookup);
        String trajectory=lookup.read("character/fighter/particle/poisonmistarea.ptl");
        if(cloud==null||trajectory==null)return null;
        var pouchFrames=ResourceScript.animation(lookup.read(clips.get(1))).frames();
        var smokeFrames=ResourceScript.animation(lookup.read(clips.get(2))).frames();
        if(pouchFrames.size()<7||smokeFrames.size()<17)return null;

        int castHeight=50,pouchHeight=castHeight+10;
        var ptl=ParticleScript.parse(trajectory);
        double velocityZ=ptl.axes().get(2).start(),gravity=ptl.gravity(),velocityX=ptl.axes().get(0).start();
        if(gravity<=0)return null;
        long landing=(long)Math.ceil(1000*(velocityZ+Math.sqrt(velocityZ*velocityZ+2.0*gravity*pouchHeight))/gravity);
        double groundX=10+velocityX*landing/1000.0;
        long life=Act1SkillSequence.levelValue(fields,level,1,10000,1,40000);
        long growth=Math.min(life,Math.max(1,Act1SkillSequence.staticValue(fields,2,4000)));
        long fade=Math.min(life,Math.max(1,Act1SkillSequence.staticValue(fields,3,3000)));
        int radius=Act1SkillSequence.staticValue(fields,0,500)/2;
        int height=Act1SkillSequence.staticValue(fields,1,300);
        long fadeStart=life-fade;
        long fieldEnd=landing+life;
        var visuals=new ArrayList<Visual>();
        var emitters=new ArrayList<SkillParticleEmitter>();
        long blastEnd=0;
        if(fire&&growth<life){
            int spacing=Act1SkillSequence.staticValue(fields,4,140);
            double scale=Act1SkillSequence.staticValue(fields,5,150)/100.0;
            long ignition=landing+growth;
            var blasts=Act1PoisonChain.schedule(lookup,radius,height,spacing,scale,Math.min(life-growth,Act1PoisonChain.FADE));
            if(!blasts.isEmpty()){
                // 0x59C580 starts the fade on the first fire contact, before the first fuse expires.
                fieldEnd=Math.min(fieldEnd,ignition+Act1PoisonChain.FADE);
                fadeStart=Math.min(fadeStart,growth);
            }
            for(int i=0;i<blasts.size();i++){
                var blast=blasts.get(i);
                var mine=blast.mine();
                blastEnd=Math.max(blastEnd,addExplosion(lookup,visuals,emitters,40002,"死亡毒雾 · 地雷 "+(i+1)+" 爆炸",
                        ignition+blast.time(),groundX+mine.x(),mine.y(),mine.z(),scale));
            }
        }
        var flight=new SkillMotion(0,landing,velocityX,0,velocityZ,-gravity,pouchHeight);
        visuals.add(new Visual(clips.get(0),"死亡毒雾 · 抛出毒囊",3600,0,landing,10,0,flight,1,true));
        visuals.add(new Visual(clips.get(1),"死亡毒雾 · 毒囊落地",3500,landing,fieldEnd,groundX,0,null,1,true,
                poisonMistHold(pouchFrames,5,fadeStart,fade)));
        visuals.add(new Visual(clips.get(2),"死亡毒雾 · 囊口烟雾",3501,landing,fieldEnd,groundX,0,null,1,true,
                poisonMistHold(smokeFrames,15,fadeStart,fade)));

        long onset=pouchFrames.get(0).delay();
        if(landing+onset<fieldEnd)
            addPoisonClouds(visuals,cloud,groundX,landing,onset,fieldEnd-landing,growth,fadeStart,fade,radius);
        var area=new AttackArea("死亡毒雾 · 中毒范围",landing,fieldEnd,groundX,0,
                new CombatDebug.TargetRange(radius,height,100),75,landing+growth);
        long end=Math.max(fieldEnd,blastEnd);
        var poses=new ArrayList<Pose>();
        poses.add(new Pose(roll,0,rollTime,null,false,new SkillMotion(0,rollTime,0,0,0,0,castHeight)));
        if(actions.containsKey(IDLE))poses.add(new Pose(IDLE,rollTime,Math.max(rollTime+150,end)));
        String note=blastEnd>0
                ?"死亡毒雾在完全扩散后点燃首枚地雷。地雷位置和引信使用固定随机样本，后续爆炸按攻击盒与地雷受击盒相交触发"
                :"死亡毒雾以离地 50 像素为施放起点，毒囊着地后每 100 毫秒检查范围内的可中毒目标";
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.copyOf(emitters),note,List.of(area));
    }

    private static VisualStyle poisonMistHold(List<ResourceScript.Frame> frames,int frame,long fadeStart,long fade){
        long intro=Act1SkillSequence.duration(frames,frame,null);
        int delay=(int)Math.max(1,fadeStart-intro);
        return new VisualStyle(null,0,List.of(new VisualKeyframe(0,1,1),new VisualKeyframe(fadeStart,1,1),
                new VisualKeyframe(fadeStart+fade,1,0)),Map.of(frame,new FrameOverride(0,0,delay)));
    }

    /** 0x59BBB0 offsets the middle clouds by the growth radius and the diagonal ones by 70% of it. */
    private static void addPoisonClouds(List<Visual> visuals,String path,double groundX,long landing,long onset,
                                        long life,long growth,long fadeStart,long fade,double radius){
        double progress=Math.min(1,onset/(double)growth);
        double scale=.3+.7*progress;
        double initialRadius=75+(radius-75)*progress-125*scale;
        double finalRadius=radius-125;
        long travel=Math.max(1,growth-onset);
        long start=landing+onset;
        long duration=life-onset;
        long localFadeStart=fadeStart-onset;
        long growthEnd=Math.min(travel,duration);
        var keyTimes=List.of(0L,Math.max(0,localFadeStart),growthEnd,duration).stream().distinct().sorted().toList();
        var keyframes=keyTimes.stream().map(time->new VisualKeyframe(time,
                scale+(1-scale)*Math.min(1,time/(double)travel),
                Math.max(0,Math.min(1,1-(time-localFadeStart)/(double)fade)))).toList();
        var style=new VisualStyle(null,0,keyframes);
        for(int row=-1;row<=1;row++){
            for(int column=-1;column<=1;column++){
                double diagonal=row!=0&&column!=0?.7:1;
                double x=column*diagonal,y=row*diagonal*.3;
                double initialY=y*initialRadius,finalY=y*finalRadius,baseY=Math.max(initialY,finalY);
                double vx=x*(finalRadius-initialRadius)*1000/travel;
                double vz=(initialY-finalY)*1000/travel;
                var spread=new SkillMotion(start,travel,vx,0,vz,0,baseY-initialY);
                visuals.add(new Visual(path,"死亡毒雾 · 扩散毒雾",3590+row,start,landing+life,
                        groundX+x*initialRadius,baseY,spread,1,false,style));
            }
        }
    }

    /** #60012 state 16 opens, state 17 retimes its growth and state 18 spawns #21021. */
    private static Act1SkillSequence nenFlower(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                               Map<String,String> actions,int level) throws IOException {
        if(!actions.containsKey(PREPARE)||!actions.containsKey(RELEASE))return null;
        String monster=monster(lookup,60012);
        if(monster==null)return null;
        var mob=GameScript.fields(lookup.read(monster));
        String flower=GameScript.resolve(monster,GameScript.value(mob,"waiting motion"),lookup);
        var motions=GameScript.values(mob,"etc motion");
        if(flower==null||motions.isEmpty())return null;
        String floor=GameScript.resolve(monster,motions.get(0),lookup);
        if(floor==null)return null;
        long cast=Act1SkillSequence.castingTime(fields,level,800);
        long recovered=cast+Math.max(1,Act1SkillSequence.staticValue(fields,0,800));
        long growing=cast+clipTime(lookup,flower);
        long bloomTime=Math.max(1,Act1SkillSequence.staticValue(fields,1,5000));
        long blastTime=growing+bloomTime;
        var visuals=new ArrayList<Visual>();
        addVisual(lookup,visuals,floor,"千莲怒放 · 地面花阵",FLOOR,cast,-1,70,0,1,false);
        addVisual(lookup,visuals,flower,"千莲怒放 · 召唤花苞",3600,cast,-1,70,0,1,false);
        if(visuals.size()!=2)return null;
        if(motions.size()<5)
            return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,PREPARE,RELEASE,cast,recovered,recovered,false),
                    List.copyOf(visuals),List.of(),"千莲怒放 · 召唤");
        String appearanceLight=GameScript.resolve(monster,motions.get(1),lookup);
        String bloom=GameScript.resolve(monster,motions.get(2),lookup);
        String bloomFloor=GameScript.resolve(monster,motions.get(3),lookup);
        String bloomLight=GameScript.resolve(monster,motions.get(4),lookup);
        if(appearanceLight!=null)addVisual(lookup,visuals,appearanceLight,"千莲怒放 · 花苞光效",FRONT,cast,growing,70,0,1,false);
        if(bloom!=null){
            var frames=ResourceScript.animation(lookup.read(bloom)).frames();
            long authored=Act1SkillSequence.duration(frames,Integer.MAX_VALUE,null);
            if(authored>0){
                var style=new VisualStyle(null,bloomTime,List.of());
                visuals.add(new Visual(bloom,"千莲怒放 · 莲花生长",3600,growing,blastTime,70,0,null,1,false,style));
                for(int i=0;i<frames.size();i++){
                    long start=growing+bloomTime*Act1SkillSequence.duration(frames,i,null)/authored;
                    long stop=growing+bloomTime*Act1SkillSequence.duration(frames,i+1,null)/authored;
                    if(bloomFloor!=null)addVisual(lookup,visuals,bloomFloor,"千莲怒放 · 开花地阵",FLOOR,start,stop,70,0,1,false);
                    if(bloomLight!=null)addVisual(lookup,visuals,bloomLight,"千莲怒放 · 开花光效",FRONT,start,stop,70,0,1,false);
                }
            }
        }
        var blasts=Act1SkillSequence.objectMotions(lookup,21021);
        long last=blastTime;
        for(int i=0;i<Math.min(2,blasts.size());i++){
            String clip=blasts.get(i);
            addVisual(lookup,visuals,clip,i==0?"千莲怒放 · 爆炸":"千莲怒放 · 爆炸地面光环",
                    i==0?3600:FLOOR,blastTime,-1,70,0,1,false);
            last=Math.max(last,blastTime+clipTime(lookup,clip));
        }
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,PREPARE,RELEASE,cast,recovered,last,false),
                List.copyOf(visuals),List.of(),"千莲怒放在前方召唤莲花，按脚本时长生长、开花并引爆");
    }

    /** Fighter state 36 emits #21029 at the scripted interval with direction and velocity selected at each throw. */
    private static Act1SkillSequence junkSpin(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                               Map<String,String> actions,int level) throws IOException {
        String action="etc motion:33";
        if(motionTime(lookup,actions,action)<=0)return null;
        var objects=Act1SkillSequence.objectMotions(lookup,21029);
        String particle=lookup.read("character/fighter/particle/junkspinthrowobject.ptl");
        if(objects.size()<9||particle==null)return null;
        var ptl=ParticleScript.parse(particle);
        var axes=ptl.axes();
        int count=Math.max(1,Math.min(32,Act1SkillSequence.staticValue(fields,0,7)));
        int interval=Math.max(1,Act1SkillSequence.staticValue(fields,2,350));
        int xRange=Act1SkillSequence.staticValue(fields,3,160);
        int yRange=Act1SkillSequence.staticValue(fields,4,40);
        int zRange=Act1SkillSequence.staticValue(fields,5,100);
        int commonCount=Math.min(objects.size()-1,Math.max(1,Act1SkillSequence.staticValue(fields,6,6)));
        int specialCount=Math.min(objects.size()-1-commonCount,Act1SkillSequence.staticValue(fields,8,2));
        int chance=Act1SkillSequence.levelValue(fields,level,9,20,0,100);
        long spinEnd=(long)count*interval;
        var visuals=new ArrayList<Visual>();
        var tracks=List.of("backback","back-dodge","back-normal","front-normal","front-dodge");
        var names=List.of("后层旋风","后层光效","后层轨迹","前层轨迹","前层光效");
        for(int i=0;i<tracks.size();i++){
            String clip=EFFECT+"junkspin/"+tracks.get(i)+".ani";
            long cycle=clipTime(lookup,clip);
            if(cycle>0){
                var style=new VisualStyle(null,0,List.of(),Map.of(),false,cycle);
                visuals.add(new Visual(clip,"街头风暴 · "+names.get(i),i<3?-1003+i:FRONT+i-3,
                        0,spinEnd,0,0,null,1,true,style));
            }
        }
        var rng=new SplittableRandom(77);
        long last=spinEnd;
        for(int i=0;i<count;i++){
            long start=(long)(i+1)*interval;
            int dir=rng.nextBoolean()?1:-1;
            int variant=specialCount>0&&rng.nextInt(100)<chance?rng.nextInt(specialCount):specialCount+rng.nextInt(commonCount);
            double vx=junkSpeed(axes.get(0),rng,xRange);
            double vy=junkSpeed(axes.get(1),rng,yRange)*(rng.nextBoolean()?1:-1);
            double vz=junkSpeed(axes.get(2),rng,zRange);
            double gravity=Math.max(1,Math.abs(ptl.gravity()));
            long flight=Math.max(1,Math.min(ptl.life(),Math.round(1000*(vz+Math.sqrt(vz*vz+2*gravity*111))/gravity)));
            var motion=new SkillMotion(start,flight,dir*vx,0,vz-vy*.29,-gravity,111);
            String clip=objects.get(variant+1);
            var style=new VisualStyle(null,0,List.of(),Map.of(),dir<0);
            visuals.add(new Visual(clip,"街头风暴 · 投掷物",vy<0?490:3600,start,start+flight,
                    dir*45,0,motion,1,true,style));
            last=Math.max(last,start+flight);
            for(var ground:List.of("ground","ground-effect"))
                addVisual(lookup,visuals,EFFECT+"junkspin/"+ground+".ani","街头风暴 · 脚下冲击",FLOOR,
                        start,-1,0,0,1,false);
        }
        var poses=new ArrayList<Pose>();
        poses.add(new Pose(action,0,spinEnd));
        if(actions.containsKey(IDLE))poses.add(new Pose(IDLE,spinEnd,Math.max(spinEnd+150,last)));
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),
                "街头风暴按脚本间隔投出常见和特殊物品，预览固定随机种子以便逐帧检查");
    }

    private static double junkSpeed(ParticleScript.Axis axis,SplittableRandom rng,int extra){
        return axis.start()+rng.nextDouble()*Math.max(0,axis.end()-axis.start()+extra);
    }

    /** 0x5E39C0 enters state 10 with motion group 2; 0x5E9C90 spawns #60001 without the Illusion Bomb flag. */
    private static Act1SkillSequence kensin(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                            Map<String,String> actions,int level) throws IOException {
        if(!actions.containsKey(PREPARE)||!actions.containsKey(RELEASE))return null;
        String monster=monster(lookup,60001);
        if(monster==null)return null;
        var mob=GameScript.fields(lookup.read(monster));
        var motions=GameScript.values(mob,"etc motion");
        if(motions.size()<3)return null;
        String body=GameScript.resolve(monster,GameScript.value(mob,"waiting motion"),lookup);
        String createLight=GameScript.resolve(monster,motions.get(0),lookup);
        String stayBody=GameScript.resolve(monster,motions.get(1),lookup);
        String stayLight=GameScript.resolve(monster,motions.get(2),lookup);
        String destroy=GameScript.resolve(monster,"Animation/IllusionDestroy.ani",lookup);
        String destroyLight=GameScript.resolve(monster,"Animation/IllusionDestroyEffect.ani",lookup);
        if(body==null||createLight==null||stayBody==null||stayLight==null)return null;
        long cast=Act1SkillSequence.castingTime(fields,level,200);
        long recovered=cast+Math.max(1,Act1SkillSequence.staticValue(fields,0,500));
        int count=Act1SkillSequence.levelValue(fields,level,0,1,1,32);
        int radius=Act1SkillSequence.levelValue(fields,level,2,75,0,3000);
        long life=Act1SkillSequence.levelValue(fields,level,1,7000,1,40000);
        long disappear=cast+life;
        long opened=Math.min(disappear,cast+clipTime(lookup,body));
        var visuals=new ArrayList<Visual>();
        long end=Math.max(recovered,disappear);
        for(int i=0;i<count;i++){
            var point=clonePoint(i,count,radius);
            int layer=3600+point.y()*2;
            addVisual(lookup,visuals,body,"分身 · 出现",layer,cast,opened,point.x(),point.y(),1,true);
            addVisual(lookup,visuals,createLight,"分身 · 出现光效",layer+1,cast,opened,point.x(),point.y(),1,true);
            if(disappear>opened){
                visuals.add(new Visual(stayBody,"分身 · 维持",layer,opened,disappear,point.x(),point.y()));
                visuals.add(new Visual(stayLight,"分身 · 维持光效",layer+1,opened,disappear,point.x(),point.y()));
            }
            if(destroy!=null)end=Math.max(end,addVisual(lookup,visuals,destroy,"分身 · 消失",layer,disappear,-1,point.x(),point.y(),1,true));
            if(destroyLight!=null)end=Math.max(end,addVisual(lookup,visuals,destroyLight,"分身 · 消失光效",layer+1,disappear,-1,point.x(),point.y(),1,true));
        }
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,PREPARE,RELEASE,cast,recovered,end,false),
                List.copyOf(visuals),List.of(),"分身按当前等级的数量、生成半径和存在时间，播放原版出现、维持和消失");
    }

    private record ClonePoint(int x,int y) {}

    private static ClonePoint clonePoint(int index,int count,int radius){
        double angle=Math.toRadians(360*index/count);
        return new ClonePoint((int)(radius*Math.cos(angle)),(int)(radius*Math.sin(angle))*29/100);
    }

    /** State 10 summons #60001. 0x673120 uses 0x672E70 to create #46004, then ends the clone. */
    private static Act1SkillSequence illusionBomb(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                                  Map<String,String> actions,int level,boolean noContact) throws IOException {
        if(!actions.containsKey(PREPARE)||!actions.containsKey(RELEASE))return null;
        String clone="skill/fighter/kensin.skl";
        String cloneText=lookup.read(clone),monster=monster(lookup,60001);
        if(cloneText==null||monster==null||!matches(clone,8,lookup))return null;
        var prerequisites=GameScript.values(fields,"pre required skill");
        int cloneLevel=9;
        for(int i=0;i+1<prerequisites.size();i+=2){
            if(GameScript.integer(prerequisites.get(i),-1)==8){
                cloneLevel=Math.max(1,GameScript.integer(prerequisites.get(i+1),9));
                break;
            }
        }
        var cloneFields=GameScript.fields(cloneText);
        int count=Act1SkillSequence.levelValue(cloneFields,cloneLevel,0,9,1,32);
        int radius=Act1SkillSequence.levelValue(cloneFields,cloneLevel,2,115,0,3000);
        long life=Act1SkillSequence.levelValue(cloneFields,cloneLevel,1,7000,1,40000);
        var mob=GameScript.fields(lookup.read(monster));
        var motions=GameScript.values(mob,"etc motion");
        String body=GameScript.resolve(monster,GameScript.value(mob,"waiting motion"),lookup);
        String light=GameScript.resolve(monster,GameScript.value(mob,"etc motion"),lookup);
        if(body==null||light==null)return null;
        long bodyTime=clipTime(lookup,body),lightTime=clipTime(lookup,light);
        if(bodyTime<=0||lightTime<=0)return null;
        long cast=Act1SkillSequence.castingTime(fields,level,600);
        long recovered=cast+Math.max(1,Act1SkillSequence.staticValue(fields,1,700));
        var visuals=new ArrayList<Visual>();
        var emitters=new ArrayList<SkillParticleEmitter>();
        String stay=motions.size()<3?null:GameScript.resolve(monster,motions.get(1),lookup);
        String stayLight=motions.size()<3?null:GameScript.resolve(monster,motions.get(2),lookup);
        String destroy=GameScript.resolve(monster,"Animation/IllusionDestroy.ani",lookup);
        String destroyLight=GameScript.resolve(monster,"Animation/IllusionDestroyEffect.ani",lookup);
        long expiry=cast+life;
        long active=cast+bodyTime;
        double targetX=radius+24;
        long contact=noContact||stay==null?-1:firstCloneContact(lookup,stay,active,expiry,targetX-radius);
        long end=Math.max(recovered,cast+Math.max(bodyTime,lightTime));
        for(int i=0;i<count;i++){
            var point=clonePoint(i,count,radius);
            int layer=3600+point.y()*2;
            long stop=i==0&&contact>=0?contact:expiry;
            visuals.add(new Visual(body,"幻影爆碎 · 分身出现",layer,cast,Math.min(active,stop),point.x(),point.y()));
            visuals.add(new Visual(light,"幻影爆碎 · 分身光效",layer+1,cast,Math.min(active,stop),point.x(),point.y()));
            if(stay!=null&&stayLight!=null&&stop>active){
                visuals.add(new Visual(stay,"幻影爆碎 · 分身维持",layer,active,stop,point.x(),point.y()).withoutAttack());
                visuals.add(new Visual(stayLight,"幻影爆碎 · 分身维持光效",layer+1,active,stop,point.x(),point.y()));
            }
            if(stay!=null){
                end=Math.max(end,stop);
                if(destroy!=null)end=Math.max(end,addVisual(lookup,visuals,destroy,
                        "幻影爆碎 · 分身消失",layer,stop,-1,point.x(),point.y(),1,true));
                if(destroyLight!=null)end=Math.max(end,addVisual(lookup,visuals,destroyLight,
                        "幻影爆碎 · 分身消失光效",layer+1,stop,-1,point.x(),point.y(),1,true));
            }
        }
        if(contact>=0){
            double scale=Act1SkillSequence.staticValue(fields,0,120)/100.0;
            end=Math.max(end,addExplosion(lookup,visuals,emitters,46004,"幻影爆碎 · 光爆",contact,radius,0,50,scale));
            Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay","幻影爆碎 · 目标",0,contact,targetX,null);
            Act1SwordAsuraSupplement.addTarget(visuals,lookup,"damage1","幻影爆碎 · 目标受击",contact,contact+250,targetX,null);
            Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay","幻影爆碎 · 目标",contact+250,end,targetX,null);
        }
        String note=contact>=0?"幻影爆碎按分身最低前置等级召唤分身，用前方哥布林演示接触引爆与原版光爆判定"
                :"幻影爆碎按分身最低前置等级演示分身的出现、维持和消失";
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,PREPARE,RELEASE,cast,recovered,end,false),
                List.copyOf(visuals),List.copyOf(emitters),note);
    }

    private static long firstCloneContact(GameScript.Lookup lookup,String stay,long active,long expiry,double targetOffset)
            throws IOException {
        String target=lookup.read(Act1SwordAsuraSupplement.GOBLIN+"stay.ani");
        if(target==null)return -1;
        var targetFrames=ResourceScript.animation(target).frames();
        if(targetFrames.isEmpty())return -1;
        var hurtBoxes=targetFrames.get(0).combat().hurt();
        long time=active;
        for(var frame:ResourceScript.animation(lookup.read(stay)).frames()){
            if(time>=expiry)break;
            if(frame.delay()<=0)continue;
            for(var attack:frame.combat().attack()){
                for(var targetBox:hurtBoxes){
                    var hurt=targetBox.mirrored();
                    if(attack.x()<targetOffset+hurt.x()+hurt.width()&&attack.x()+attack.width()>targetOffset+hurt.x()
                            &&attack.y()<hurt.y()+hurt.depth()&&attack.y()+attack.depth()>hurt.y()
                            &&attack.z()<hurt.z()+hurt.height()&&attack.z()+attack.height()>hurt.z())return time;
                }
            }
            time+=frame.delay();
        }
        return -1;
    }

    private static long addExplosion(GameScript.Lookup lookup,List<Visual> visuals,List<SkillParticleEmitter> emitters,
                                     int id,String label,long start,double x,double y,double z,double scale) throws IOException {
        String object=GameEffectRecipes.passiveObject(lookup,id);
        if(object==null)return start;
        var fields=GameScript.fields(lookup.read(object));
        String root=GameScript.resolve(object,GameScript.value(fields,"basic motion"),lookup);
        if(root==null)return start;
        long life=clipTime(lookup,root);
        if(life<=0)return start;
        long end=start+life;
        visuals.add(new Visual(root,label+"判定",FRONT,start,end,x,y-z,null,scale,true));
        for(String reference:GameScript.values(fields,"string data")){
            String path=GameScript.resolve(object,reference,lookup);
            if(path==null||!path.endsWith(".ptl"))continue;
            var particle=ParticleScript.parse(lookup.read(path));
            emitters.add(new SkillParticleEmitter(path,label,start,life,0,x,y,z,Double.NaN,scale,false));
            end=Math.max(end,start+life+SkillParticleEmitter.tail(particle));
        }
        return end;
    }

    private static String monster(GameScript.Lookup lookup,int id) throws IOException {
        String entry=GameScript.catalog(lookup.read(MONSTERS)).get(id);
        return entry==null?null:GameScript.resolve(MONSTERS,entry,lookup);
    }

    /** State 35 throws mines along X, then punches once per mine and creates #21028 at each landing point. */
    private static Act1SkillSequence venomMine(List<GameScript.Field> fields,GameScript.Lookup lookup,
                                              Map<String,String> actions,int level) throws IOException {
        String throwing="etc motion:31", pounding="etc motion:32";
        var toss=Act1SkillSequence.frames(CHR,actions.get(throwing),lookup);
        var punch=Act1SkillSequence.frames(CHR,actions.get(pounding),lookup);
        if(toss.size()<4||punch.size()<3)return null;
        var explosion=Act1SkillSequence.objectMotions(lookup,21028);
        String folder=EFFECT+"venommine/", mine=folder+"venommine.ani";
        if(explosion.isEmpty()||clipTime(lookup,mine)<=0)return null;
        long release=Act1SkillSequence.duration(toss,3,null);
        long punchStart=Act1SkillSequence.duration(toss,toss.size(),null);
        long punchTime=Act1SkillSequence.duration(punch,punch.size(),null);
        if(punchTime<=0||punchStart<=0)return null;
        long hitFrame=Act1SkillSequence.duration(punch,2,null);
        int count=Act1SkillSequence.levelValue(fields,level,1,5,1,8);
        int first=Act1SkillSequence.staticValue(fields,0,50),spacing=Act1SkillSequence.staticValue(fields,1,80);
        var poses=new ArrayList<Pose>();
        var visuals=new ArrayList<Visual>();
        poses.add(new Pose(throwing,0,punchStart));
        long land=release+560;
        var mineStyle=new VisualStyle(null,0,List.of(),Map.of(0,new FrameOverride(0,0,0)));
        long end=0;
        for(int index=0;index<count;index++){
            double x=first+(double)index*spacing;
            double speed=(x-29)/.56;
            double apexX=29+speed*.24;
            // 0x5F1DF0/0x5F1EF0: start Z=67, add 32 at 240 ms, reach the ground after another 320 ms.
            var rise=new SkillMotion(release,240,speed,0,2*32/.24,-2*32/(.24*.24),67);
            var drop=new SkillMotion(release+240,320,speed,0,0,-2*99/(.32*.32),99);
            visuals.add(new Visual(mine,"毒雷引爆 · 毒雷飞行",3600,release,release+240,29,0,rise,1,true,mineStyle));
            visuals.add(new Visual(mine,"毒雷引爆 · 毒雷下落",3600,release+240,land,apexX,0,drop,1,true,mineStyle));
            long start=punchStart+index*punchTime;
            long trigger=start+hitFrame;
            poses.add(new Pose(pounding,start,start+punchTime));
            if(trigger>land)visuals.add(new Visual(mine,"毒雷引爆 · 地面毒雷",3600,land,trigger,x,0,null,1,true,mineStyle));
            end=Math.max(end,addVisual(lookup,visuals,folder+"venomminedust.ani","毒雷引爆 · 毒雷落地",FLOOR,land,-1,x,0,1,true));
            end=Math.max(end,addVisual(lookup,visuals,folder+"venomminepounddust.ani","毒雷引爆 · 捶地扬尘",FLOOR,trigger,-1,56,0,1,true));
            for(int layer=0;layer<explosion.size();layer++){
                end=Math.max(end,addVisual(lookup,visuals,explosion.get(layer),"毒雷引爆 · 爆炸",3600+layer,trigger,-1,x,0,1,true));
            }
        }
        long recovered=punchStart+count*punchTime;
        if(actions.containsKey(IDLE))poses.add(new Pose(IDLE,recovered,Math.max(end,recovered+150)));
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),
                "毒雷数量取当前等级，落点取 SKL 的首个距离与间隔；按捶地动作逐枚引爆");
    }

    private static long addVisual(GameScript.Lookup lookup,List<Visual> visuals,String path,String label,int layer,
                                  long start,long cap,double x,double y,double scale,boolean mirror) throws IOException {
        long end=start+clipTime(lookup,path);
        if(cap>=0)end=Math.min(end,cap);
        if(end>start)visuals.add(new Visual(path,label,layer,start,end,x,y,null,scale,mirror));
        return end;
    }

    private static long emit(GameScript.Lookup lookup,List<SkillParticleEmitter> emitters,String path,String label,
                             long start,long emission,double x,double z,boolean override) throws IOException {
        String text=lookup.read(path);
        if(text==null)return start;
        emitters.add(new SkillParticleEmitter(path,label,start,emission,0,x,0,z,Double.NaN,1,override));
        return start+emission+SkillParticleEmitter.tail(ParticleScript.parse(text));
    }
}
