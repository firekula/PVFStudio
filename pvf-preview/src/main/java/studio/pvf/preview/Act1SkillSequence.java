package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Casts whose effects begin on different character or passive-object clocks. */
record Act1SkillSequence(List<Pose> poses,List<Visual> visuals,List<SkillParticleEmitter> emitters,String note,
                         List<AttackArea> attackAreas) {
    Act1SkillSequence(List<Pose> poses,List<Visual> visuals,List<SkillParticleEmitter> emitters,String note) {
        this(poses,visuals,emitters,note,List.of());
    }

    record AttackArea(String label,long start,long end,double x,double y,CombatDebug.TargetRange range,
                      int fromRadius,long growthEnd,long firstPulse) {
        AttackArea(String label,long start,long end,double x,double y,CombatDebug.TargetRange range) {
            this(label,start,end,x,y,range,range.radius(),start,start);
        }
        AttackArea(String label,long start,long end,double x,double y,CombatDebug.TargetRange range,
                   int fromRadius,long growthEnd) {
            this(label,start,end,x,y,range,fromRadius,growthEnd,start);
        }

        AttackArea startingPulseAt(long time) {
            return new AttackArea(label,start,end,x,y,range,fromRadius,growthEnd,time);
        }
        boolean activeAt(long time) { return time >= start && time < end; }
        boolean pulseAt(long time) {
            return time>=firstPulse&&(time-firstPulse)%range.interval()<Math.min(60,range.interval());
        }
        CombatDebug.TargetRange rangeAt(long time) {
            if(time>=growthEnd||growthEnd<=start||fromRadius==range.radius())return range;
            int radius=(int)(fromRadius+(range.radius()-fromRadius)*(Math.max(start,time)-start)/(double)(growthEnd-start));
            return new CombatDebug.TargetRange(radius,range.maxZ(),range.interval());
        }
    }

    private static final String FIGHTER="character/fighter/";
    private static final String FIGHTER_EFFECT=FIGHTER+"effect/animation/";

    record Pose(String action,long start,long end,Integer holdDelay,boolean fitToStage,SkillMotion motion,
                int firstFrame,int endFrame,boolean reverseFacing,List<VisualKeyframe> keyframes,boolean secondary) {
        Pose {
            keyframes=List.copyOf(keyframes);
        }
        Pose(String action,long start,long end,Integer holdDelay,boolean fitToStage,SkillMotion motion,
             int firstFrame,int endFrame,boolean reverseFacing){
            this(action,start,end,holdDelay,fitToStage,motion,firstFrame,endFrame,reverseFacing,List.of());
        }
        Pose(String action,long start,long end,Integer holdDelay,boolean fitToStage,SkillMotion motion,
             int firstFrame,int endFrame,boolean reverseFacing,List<VisualKeyframe> keyframes){
            this(action,start,end,holdDelay,fitToStage,motion,firstFrame,endFrame,reverseFacing,keyframes,false);
        }
        Pose(String action,long start,long end,Integer holdDelay,boolean fitToStage,SkillMotion motion,int firstFrame,int endFrame){
            this(action,start,end,holdDelay,fitToStage,motion,firstFrame,endFrame,false);
        }
        Pose(String action,long start,long end){this(action,start,end,null,false,null);}
        Pose(String action,long start,long end,Integer holdDelay,boolean fitToStage,SkillMotion motion){
            this(action,start,end,holdDelay,fitToStage,motion,0,Integer.MAX_VALUE);
        }
        Pose(String action,long start,long end,Integer holdDelay,boolean fitToStage,SkillMotion motion,int firstFrame){
            this(action,start,end,holdDelay,fitToStage,motion,firstFrame,Integer.MAX_VALUE);
        }
    }

    record SpriteOverride(String image,int index) {}
    record VisualKeyframe(long time,double scale,double opacity) {}
    record FrameOverride(double dx,double dy,Integer delay,double scaleX,double scaleY) {
        FrameOverride(double dx,double dy,Integer delay){this(dx,dy,delay,1,1);}
    }
    record VisualStyle(SpriteOverride spriteOverride,long animationDuration,List<VisualKeyframe> keyframes,
                       Map<Integer,FrameOverride> frameOverrides,boolean reverseFacing,long loopPeriod,
                       ResourceScript.Animation animation) {
        static final VisualStyle DEFAULT=new VisualStyle(null,0,List.of());
        private static final VisualKeyframe IDENTITY=new VisualKeyframe(0,1,1);
        VisualStyle(SpriteOverride spriteOverride,long animationDuration,List<VisualKeyframe> keyframes){
            this(spriteOverride,animationDuration,keyframes,Map.of());
        }
        VisualStyle(SpriteOverride spriteOverride,long animationDuration,List<VisualKeyframe> keyframes,
                    Map<Integer,FrameOverride> frameOverrides){
            this(spriteOverride,animationDuration,keyframes,frameOverrides,false);
        }
        VisualStyle(SpriteOverride spriteOverride,long animationDuration,List<VisualKeyframe> keyframes,
                    Map<Integer,FrameOverride> frameOverrides,boolean reverseFacing){
            this(spriteOverride,animationDuration,keyframes,frameOverrides,reverseFacing,0);
        }
        VisualStyle(SpriteOverride spriteOverride,long animationDuration,List<VisualKeyframe> keyframes,
                    Map<Integer,FrameOverride> frameOverrides,boolean reverseFacing,long loopPeriod){
            this(spriteOverride,animationDuration,keyframes,frameOverrides,reverseFacing,loopPeriod,null);
        }
        VisualStyle(ResourceScript.Animation animation){
            this(null,0,List.of(),Map.of(),false,0,animation);
        }
        VisualStyle {
            keyframes=List.copyOf(keyframes);
            frameOverrides=Map.copyOf(frameOverrides);
        }

        VisualKeyframe sample(long time){
            if(keyframes.isEmpty())return IDENTITY;
            var previous=keyframes.get(0);
            for(var next:keyframes){
                if(time<=next.time()){
                    if(next.time()==previous.time())return next;
                    double fraction=(time-previous.time())/(double)(next.time()-previous.time());
                    return new VisualKeyframe(time,previous.scale()+(next.scale()-previous.scale())*fraction,
                            previous.opacity()+(next.opacity()-previous.opacity())*fraction);
                }
                previous=next;
            }
            return previous;
        }
    }

    record Visual(String path,String label,int layer,long start,long end,double x,double y,SkillMotion motion,
                  double scale,boolean mirror,VisualStyle style,List<Integer> variations,boolean decorative,
                  boolean alliedAttack) {
        Visual {variations=List.copyOf(variations);}
        Visual(String path,String label,int layer,long start,long end,double x,double y,SkillMotion motion,double scale,
               boolean mirror,VisualStyle style,List<Integer> variations,boolean decorative){
            this(path,label,layer,start,end,x,y,motion,scale,mirror,style,variations,decorative,false);
        }
        Visual(String path,String label,int layer,long start,long end,double x,double y,SkillMotion motion,double scale,
               boolean mirror,VisualStyle style,List<Integer> variations){
            this(path,label,layer,start,end,x,y,motion,scale,mirror,style,variations,false);
        }
        Visual(String path,String label,int layer,long start,long end,double x,double y,SkillMotion motion,double scale,
               boolean mirror,VisualStyle style){
            this(path,label,layer,start,end,x,y,motion,scale,mirror,style,List.of());
        }
        Visual(String path,String label,int layer,long start,long end,double x,double y){
            this(path,label,layer,start,end,x,y,null,1,true);
        }
        Visual(String path,String label,int layer,long start,long end,double x,double y,SkillMotion motion,double scale,boolean mirror){
            this(path,label,layer,start,end,x,y,motion,scale,mirror,VisualStyle.DEFAULT);
        }
        Visual(String path,String label,int layer,long start,long end,double x,double y,SkillMotion motion,double scale,boolean mirror,
               SpriteOverride spriteOverride){
            this(path,label,layer,start,end,x,y,motion,scale,mirror,new VisualStyle(spriteOverride,0,List.of()));
        }
        Visual withoutAttack(){
            return new Visual(path,label,layer,start,end,x,y,motion,scale,mirror,style,variations,true);
        }

        Visual asAlliedAttack(){
            return new Visual(path,label,layer,start,end,x,y,motion,scale,mirror,style,variations,false,true);
        }
    }

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,
                                  int level,String choice,boolean act1) throws IOException {
        return find(path,text,lookup,actions,level,choice,act1,Act1BuffCast::stockHeight);
    }

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,
                                  int level,String choice,boolean act1,Act1BuffCast.ActorHeight height) throws IOException {
        return find(path,text,lookup,actions,level,choice,act1,height,null);
    }

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,
                                  int level,String choice,boolean act1,Act1BuffCast.ActorHeight height,String weaponPath) throws IOException {
        return find(path,text,lookup,actions,level,choice,act1,height,weaponPath,null);
    }

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,
                                  int level,String choice,boolean act1,Act1BuffCast.ActorHeight height,String weaponPath,
                                  ScenePreview.SpriteLookup sprites) throws IOException {
        boolean miss=choice.equals("grabexplosion:miss")&&path.equals("skill/fighter/grabexplosion.skl");
        boolean dragonMiss=choice.equals(Act1DragonSpear.MISS)&&path.equals(Act1DragonSpear.PATH);
        boolean fighterMiss=Act1FighterGrab.missSelected(path,choice);
        if(!act1)return null;
        if(path.startsWith("skill/fighter/")){
            var fighterHit=Act1FighterHits.find(path,lookup,actions,choice);
            if(fighterHit!=null)return fighterHit;
            var fighter=Act1FighterCast.find(path,text,lookup,actions,level,choice,height);
            if(fighter!=null)return fighter;
        }
        if(path.startsWith("skill/swordman/")){
            var sword=Act1SwordTechniqueCast.find(path,text,lookup,actions,level,choice,weaponPath);
            if(sword!=null)return sword;
            var wave=Act1SwordSupplement.find(path,text,lookup,actions,level,choice);
            if(wave!=null)return wave;
            var melee=Act1SwordMeleeSupplement.find(path,text,lookup,actions,level,weaponPath,choice);
            if(melee!=null)return melee;
            var blood=Act1SwordRemaining.bloodChoice(path,text,lookup,actions,choice);
            if(blood!=null)return blood;
            var remaining=Act1SwordRemaining.find(path,text,lookup,actions,level,choice);
            if(remaining!=null)return remaining;
            var asura=Act1SwordAsuraSupplement.find(path,text,lookup,actions,level,choice);
            if(asura!=null)return asura;
            if(choice.isEmpty()){
                var combo=Act1SwordComboSupplement.find(path,text,lookup,actions,level,weaponPath);
                if(combo!=null)return combo;
                var buff=Act1SwordBuffSupplement.find(path,text,lookup,actions,level,height);
                if(buff!=null)return buff;
            }
        }
        if(path.startsWith("skill/gunner/")){
            var utility=Act1GunnerUtilityCast.find(path,text,lookup,actions,level,choice,weaponPath);
            if(utility!=null)return utility;
            var support=Act1GunnerSupportCast.find(path,text,lookup,actions,level,choice);
            if(support!=null)return support;
            var fire=Act1GunnerAdvancedFire.find(path,text,lookup,actions,level,choice,weaponPath);
            if(fire!=null)return fire;
            var gunner=Act1GunnerSupplement.find(path,text,lookup,actions,level,choice,weaponPath,height);
            if(gunner!=null)return gunner;
            var mechanical=Act1GunnerMechanicalSupplement.find(path,text,lookup,actions,level,choice);
            if(mechanical!=null)return mechanical;
        }
        int precision=precisionSkill(path);
        boolean precisionWall=precision!=0&&Act1GunnerUtilityCast.isWallChoice(path,choice);
        if(precision!=0&&(choice.isEmpty()||choice.equals("precision:down")||precisionWall)&&matches(path,"gunner",precision,lookup)){
            return Act1GunnerPrecisionCast.find(precision,text,lookup,actions,level,choice.equals("precision:down"),weaponPath,precisionWall);
        }
        if(path.startsWith("skill/mage/")){
            if(choice.equals(Act1BuffCast.SHIELD_HIT))return Act1BuffCast.find(path,text,lookup,actions,level,height,true);
            var chaser=Act1MageChaser.find(path,text,lookup,actions,level,choice,height,sprites);
            if(chaser!=null)return chaser;
            var mage=Act1MageSupplement.find(path,text,lookup,actions,level,choice,height,weaponPath,sprites);
            if(mage!=null)return mage;
            var element=Act1MageElementCast.find(path,text,lookup,actions,level,choice);
            if(element!=null)return element;
        }
        if(path.startsWith("skill/priest/")){
            var upper=Act1PriestAntiairUpper.find(path,lookup,actions,weaponPath,choice);
            if(upper!=null)return upper;
            var remaining=Act1PriestRemaining.find(path,text,lookup,actions,level,choice,height,weaponPath);
            if(remaining!=null)return remaining;
            var supplement=Act1PriestSupplement.find(path,text,lookup,actions,level,weaponPath,choice);
            if(supplement!=null)return supplement;
            var priest=Act1PriestCast.find(path,text,lookup,actions,level,height,choice);
            if(priest!=null)return priest;
        }
        if(!choice.isEmpty()&&!miss&&!dragonMiss&&!fighterMiss)return null;
        if(path.startsWith("skill/priest/")){
            var projectile=Act1PriestProjectile.find(path,text,lookup,actions,level);
            if(projectile!=null)return projectile;
        }
        if(path.endsWith("/backstep.skl")){
            String job=CharacterAppearance.inferJob(path,text,"");
            return matches(path,job,169,lookup)?Act1Backstep.find(job,text,lookup,actions):null;
        }
        return switch(path){
            case "skill/fighter/grabexplosion.skl" -> matches(path,"fighter",3,lookup)
                    ?grabExplosion(text,lookup,actions,level,miss):null;
            case "skill/fighter/energyball.skl" -> matches(path,"fighter",12,lookup)?energyBall(text,lookup,actions,level):null;
            case "skill/fighter/kikouken.skl" -> matches(path,"fighter",16,lookup)?spiralNenField(text,lookup,actions,level):null;
            case "skill/fighter/legsuplex.skl", "skill/fighter/suplex.skl", "skill/fighter/spire.skl" ->
                    Act1FighterGrab.find(path,text,lookup,actions,level,fighterMiss);
            case "skill/swordman/normalwave.skl" -> matches(path,"swordman",20,lookup)?Act1SwordWaveCast.find(20,text,lookup,actions,level):null;
            case "skill/swordman/icewave.skl" -> matches(path,"swordman",21,lookup)?Act1SwordWaveCast.find(21,text,lookup,actions,level):null;
            case "skill/swordman/firewave.skl" -> matches(path,"swordman",22,lookup)?Act1SwordWaveCast.find(22,text,lookup,actions,level):null;
            case "skill/swordman/bloodblast.skl" -> matches(path,"swordman",24,lookup)?bloodBlast(text,lookup,actions,level):null;
            case "skill/swordman/gorecross.skl" -> matches(path,"swordman",64,lookup)?Act1GoreCross.find(lookup,actions):null;
            case "skill/mage/blackhole.skl" -> matches(path,"mage",58,lookup)?Act1BlackHole.find(text,lookup,actions,level):null;
            case "skill/mage/chasersexplosion.skl" -> matches(path,"mage",10,lookup)?Act1ChaserPress.find(text,lookup,actions,level):null;
            case Act1DragonSpear.PATH -> matches(path,"mage",69,lookup)
                    ?Act1DragonSpear.find(text,lookup,actions,level,dragonMiss):null;
            case "skill/mage/magicmissile.skl" -> matches(path,"mage",11,lookup)?magicMissile(text,lookup,actions,level):null;
            default -> Act1BuffCast.find(path,text,lookup,actions,level,height);
        };
    }

    static GameEffectRecipes.Effect precisionDownChoice(String path,GameScript.Lookup lookup,boolean act1) throws IOException {
        int skill=precisionSkill(path);
        return act1&&skill!=0&&matches(path,"gunner",skill,lookup)
                ?new GameEffectRecipes.Effect("precision:down","向下射击","",path,"使用客户端的向下射击动作和枪口坐标"):null;
    }

    private static int precisionSkill(String path){
        return switch(path){
            case "skill/gunner/headshot.skl" -> 5;
            case "skill/gunner/turningshoot.skl" -> 20;
            case "skill/gunner/liftshot.skl" -> 47;
            default -> 0;
        };
    }

    static GameEffectRecipes.Effect grabMissChoice(String path,GameScript.Lookup lookup,boolean act1) throws IOException {
        if(!act1||!path.equals("skill/fighter/grabexplosion.skl")||!matches(path,"fighter",3,lookup))return null;
        return new GameEffectRecipes.Effect("grabexplosion:miss","擒月炎 · 未抓到目标","",path,"伸手未抓到时结束施放");
    }

    static GameEffectRecipes.Effect dragonSpearMissChoice(String path,GameScript.Lookup lookup,boolean act1) throws IOException {
        if(!act1||!path.equals(Act1DragonSpear.PATH)||!matches(path,"mage",69,lookup))return null;
        return new GameEffectRecipes.Effect(Act1DragonSpear.MISS,"煌龙偃月 · 未命中","",path,"刺击后刀身直接消散");
    }

    /** Fighter state 23 jumps from frame 8 directly to frame 9 on a successful grab. */
    private static Act1SkillSequence grabExplosion(String text,GameScript.Lookup lookup,Map<String,String> actions,
                                                    int level,boolean miss) throws IOException {
        String action="etc motion:14";
        var frames=frames(FIGHTER+"fighter.chr",actions.get(action),lookup);
        if(frames.size()<14)return null;
        long grab=duration(frames,8,null);
        if(miss){
            var poses=new ArrayList<Pose>();
            poses.add(new Pose(action,0,grab));
            if(actions.containsKey("waiting motion"))poses.add(new Pose("waiting motion",grab,grab+200));
            return new Act1SkillSequence(List.copyOf(poses),List.of(),List.of(),
                    "擒月炎未抓到目标时，在第 8 帧切回待机");
        }
        long burst=duration(frames,13,0);
        long end=duration(frames,frames.size(),0);
        var lunge=new SkillMotion(grab,100,1000,-10000);
        var effects=new ArrayList<>(List.of(
                new Visual(FIGHTER_EFFECT+"grabexplosion1.ani","擒月炎 · 蓄火底层",3501,grab,burst,0,0,lunge,1,true),
                new Visual(FIGHTER_EFFECT+"grabexplosion2.ani","擒月炎 · 蓄火",3502,grab,burst,0,0,lunge,1,true),
                new Visual(FIGHTER_EFFECT+"grabexplosion3.ani","擒月炎 · 火光",3502,grab,burst,0,0,lunge,1,true)));
        String object=GameEffectRecipes.passiveObject(lookup,21014);
        var emitters=new ArrayList<SkillParticleEmitter>();
        long finish=end;
        double scale=levelValue(GameScript.fields(text),level,2,100,1,400)/100.0;
        if(object!=null){
            var objectFields=GameScript.fields(lookup.read(object));
            String root=GameScript.resolve(object,GameScript.value(objectFields,"basic motion"),lookup);
            double explosionX=59+lunge.offsetAt(burst);
            if(root!=null){
                long life=new AnimationTimeline(ResourceScript.animation(lookup.read(root))).duration();
                if(life>0){
                    effects.add(new Visual(root,"擒月炎 · 爆炸判定",3503,burst,burst+life,explosionX,-95,null,scale,true));
                    finish=Math.max(finish,burst+life);
                }
            }
            for(String reference:GameScript.values(objectFields,"string data")){
                if(emitters.size()>=4)break;
                if(!GameScript.normalize(reference).endsWith(".ptl"))continue;
                String target=GameScript.resolve(object,reference,lookup);
                if(target!=null){
                    var particle=ParticleScript.parse(lookup.read(target));
                    long emission=particle.maximum()>1&&particle.frequency()>0
                            ?Math.min(5000,(long)Math.ceil((particle.maximum()-1)*1000/particle.frequency())+1):1;
                    emitters.add(new SkillParticleEmitter(target,"擒月炎 · 爆炸",burst,emission,0,
                            explosionX,-1,94,Double.NaN,scale));
                    finish=Math.max(finish,burst+emission+SkillParticleEmitter.tail(particle));
                }
            }
        }
        var poses=new ArrayList<Pose>();
        poses.add(new Pose(action,0,end,0,false,lunge));
        if(finish>end&&actions.containsKey("waiting motion"))poses.add(new Pose("waiting motion",end,finish,null,false,lunge));
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(effects),List.copyOf(emitters),
                "擒月炎演示抓取成功的短前移、蓄火与爆炸；目标姿势取决于实际敌人");
    }

    /** State 10 spawns #21002 at the discharge attachment's first frame change. */
    private static Act1SkillSequence energyBall(String text,GameScript.Lookup lookup,Map<String,String> actions,
                                                 int level) throws IOException {
        String prepare="throw motion 2-1",release="throw motion 2-2";
        if(!actions.containsKey(prepare)||!actions.containsKey(release))return null;
        String discharge=FIGHTER_EFFECT+"energyballshoot.ani";
        String dischargeText=lookup.read(discharge);
        if(dischargeText==null)return null;
        var frames=ResourceScript.animation(dischargeText).frames();
        if(frames.size()<2)return null;
        var fields=GameScript.fields(text);
        long cast=castingTime(fields,level,250);
        long launch=cast+frames.get(0).delay();
        int life=levelValue(fields,level,0,525,1,10000);
        double size=levelValue(fields,level,4,100,1,400)/100.0;
        var ball=projectile(lookup,21002,FIGHTER+"particle/energyball.ptl","念气波 · 飞行",launch,life,70,63,size);
        if(ball==null)return null;
        long recovered=Math.max(launch+1,cast+staticValue(fields,0,500));
        var visuals=List.of(new Visual(FIGHTER_EFFECT+"energycharge.ani","念气波 · 聚气",3501,0,cast,0,0),
                new Visual(discharge,"念气波 · 出手",3501,cast,recovered,0,0),ball);
        return new Act1SkillSequence(castPoses(actions,prepare,release,cast,recovered,ball.end(),true),visuals,List.of(),
                "念气波预览水平施放；聚气动作按施放时间压缩，飞行时长和大小取当前等级");
    }

    /** Fighter state 10 creates #21006 as it switches from charge to release (0x5E9C90). */
    private static Act1SkillSequence spiralNenField(String text,GameScript.Lookup lookup,Map<String,String> actions,
                                                    int level) throws IOException {
        String prepare="throw motion 2-1",release="throw motion 2-2";
        if(!actions.containsKey(prepare)||!actions.containsKey(release))return null;
        String charge=FIGHTER_EFFECT+"energycharge.ani";
        var motions=objectMotions(lookup,21006);
        if(motions.isEmpty()||lookup.read(charge)==null)return null;
        String field=motions.get(0);
        var animation=ResourceScript.animation(lookup.read(field));
        long duration=new AnimationTimeline(animation).duration();
        if(duration<=0)return null;

        var fields=GameScript.fields(text);
        long cast=castingTime(fields,level,700);
        long recovery=Math.max(1,staticValue(fields,0,1500));
        long end=cast+recovery;
        long fieldEnd=animation.loop()?end:cast+Math.min(recovery,duration);
        double scale=levelValue(fields,level,4,100,1,400)/100.0;
        var visuals=List.of(
                new Visual(charge,"螺旋念气场 · 聚气",3501,0,cast,0,0),
                new Visual(field,"螺旋念气场 · 念气场",3600,cast,fieldEnd,107,1-62,null,scale,true));
        return new Act1SkillSequence(castPoses(actions,prepare,release,cast,end,end,false),visuals,List.of(),
                "念气场在推掌时生成并停留在前方，大小取当前技能等级；感电与受击效果需要目标");
    }

    /** Mage state 10 attaches OneHandThrow1/2 and generates #23007 on release. */
    private static Act1SkillSequence magicMissile(String text,GameScript.Lookup lookup,Map<String,String> actions,
                                                   int level) throws IOException {
        String prepare="throw motion 1-1",release="throw motion 1-2";
        if(!actions.containsKey(prepare)||!actions.containsKey(release))return null;
        var fields=GameScript.fields(text);
        long cast=castingTime(fields,level,420);
        int life=levelValue(fields,level,2,520,1,10000);
        var star=projectile(lookup,23007,"character/mage/particle/magicmissile.ptl","魔法星弹 · 飞行",cast,life,44,60,1);
        if(star==null)return null;
        long recovered=cast+Math.max(1,staticValue(fields,0,300));
        String effect="character/mage/effect/animation/onehandthrow";
        var visuals=List.of(new Visual(effect+"1.ani","魔法星弹 · 施法",3501,0,cast,0,0),
                new Visual(effect+"2.ani","魔法星弹 · 出手",3501,cast,-1,0,0),star);
        return new Act1SkillSequence(castPoses(actions,prepare,release,cast,recovered,star.end(),false),visuals,List.of(),
                "魔法星弹预览短按和水平飞行；蓄力、方向键修正、尾迹与追踪留待对照");
    }

    private static Visual projectile(GameScript.Lookup lookup,int objectId,String movement,String label,long start,
                                     int life,double x,double height,double scale) throws IOException {
        var objects=objectMotions(lookup,objectId);
        String motion=lookup.read(movement);
        if(objects.isEmpty()||motion==null)return null;
        var axis=ParticleScript.parse(motion).axes().get(0);
        if(!axis.type().equals("straight line")||axis.start()<=0)return null;
        return new Visual(objects.get(0),label,3600,start,start+life,x,-height,new SkillMotion(start,life,axis.start(),0),scale,true);
    }

    static List<Pose> castPoses(Map<String,String> actions,String prepare,String release,
                                               long cast,long recovered,long finish,boolean fitPreparation){
        var poses=new ArrayList<Pose>();
        if(cast>0)poses.add(new Pose(prepare,0,cast,null,fitPreparation,null));
        poses.add(new Pose(release,cast,recovered));
        if(finish>recovered&&actions.containsKey("waiting motion"))poses.add(new Pose("waiting motion",recovered,finish));
        return List.copyOf(poses);
    }

    /** Swordman state 10 creates all eruption objects together. Blank ANI frames supply the warning interval. */
    private static Act1SkillSequence bloodBlast(String text,GameScript.Lookup lookup,Map<String,String> actions,
                                                 int level) throws IOException {
        String prepare="throw motion 2-1",release="throw motion 2-2";
        if(!actions.containsKey(prepare)||!actions.containsKey(release))return null;
        var eruption=objectMotions(lookup,20009);
        var forewarning=objectMotions(lookup,20031);
        if(eruption.size()<6||forewarning.size()<2)return null;
        var fields=GameScript.fields(text);
        long cast=levelValue(fields,level,1,400,0,10000);
        long recovery=cast+Math.max(1,staticValue(fields,3,700));
        long end=duration(ResourceScript.animation(lookup.read(eruption.get(0))).frames(),Integer.MAX_VALUE,null);
        var poses=castPoses(actions,prepare,release,cast,recovery,end,false);
        var visuals=new ArrayList<>(List.of(
                new Visual(eruption.get(3),"怒气爆发 · 地面血阵",300,0,-1,0,50,null,1,false),
                new Visual(eruption.get(4),"怒气爆发 · 地面暗纹",301,0,-1,0,50,null,1,false),
                new Visual(eruption.get(5),"怒气爆发 · 地面喷发",302,0,-1,0,50,null,1,false),
                new Visual(forewarning.get(0),"怒气爆发 · 施放后层",3500,0,-1,0,0),
                new Visual(forewarning.get(1),"怒气爆发 · 施放前层",3501,0,-1,0,0,null,1,false),
                new Visual(eruption.get(1),"怒气爆发 · 血柱后层",3600,0,-1,0,50),
                new Visual(eruption.get(0),"怒气爆发 · 血柱",3601,0,-1,0,50),
                new Visual(eruption.get(2),"怒气爆发 · 血柱前层",3602,0,-1,0,50)));
        var collision=objectMotions(lookup,20010);
        if(!collision.isEmpty()){
            // State 10 creates the wider, invisible 20010 at the caster's ground origin.
            // The visible 20009 keeps its separate Y+50 anchor and narrower attack boxes.
            visuals.add(new Visual(collision.get(0),"怒气爆发 · 外围攻击判定",3603,0,-1,0,0));
            String hitObject=lookup.read(collision.get(0));
            long hit=firstAttackTime(hitObject);
            if(hit>=0){
                double targetX=75;
                String folder=collision.get(0).substring(0,collision.get(0).lastIndexOf('/')+1);
                long targetEnd=end;
                for(int i=1;i<=2;i++){
                    String hitPath=folder+"blastbloodhit"+i+".ani";
                    String source=lookup.read(hitPath);
                    if(source==null)continue;
                    long life=new AnimationTimeline(ResourceScript.animation(source)).duration();
                    if(life<=0)continue;
                    targetEnd=Math.max(targetEnd,hit+life);
                    visuals.add(new Visual(hitPath,"怒气爆发 · 目标血花",3603+i,hit,hit+life,targetX,0));
                }
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay","怒气爆发 · 目标",0,hit,targetX,null);
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"down","怒气爆发 · 目标受击",hit,targetEnd,targetX,null);
                poses=castPoses(actions,prepare,release,cast,recovery,targetEnd,false);
            }
        }
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),
                "怒气爆发使用原版地面血阵、血柱和地面目标命中后生成的两层血花");
    }

    private static long firstAttackTime(String source) throws IOException {
        if(source==null)return -1;
        var frames=ResourceScript.animation(source).frames();
        int index=-1;
        for(var section:ResourceScript.sections(source)){
            if(section.tag().matches("frame[0-9]+"))index++;
            if(section.tag().equals("attack box")&&section.values().size()>=6&&index>=0&&index<frames.size())
                return duration(frames,index,null);
        }
        return -1;
    }

    /** #48012 plays its standard ANI at the packet speed, then creates the target object (0x5D2A60 / 0x5D2B20). */
    static long targetWarning(GameScript.Lookup lookup,List<Visual> visuals,String label,long start,double x,int speed)
            throws IOException {
        String object=GameEffectRecipes.passiveObject(lookup,48012);
        if(object==null)return start;
        var motions=GameScript.values(GameScript.fields(lookup.read(object)),"etc motion");
        if(motions.size()<2)return start;
        String path=GameScript.resolve(object,motions.get(1),lookup);
        if(path==null)return start;
        long authored=new AnimationTimeline(ResourceScript.animation(lookup.read(path))).duration();
        if(authored<=0)return start;
        long delay=Math.max(1,authored*100/Math.max(1,speed));
        var style=new VisualStyle(null,delay,List.of());
        visuals.add(new Visual(path,label,3650,start,start+delay,x,0,null,1,false,style));
        return start+delay;
    }

    static List<String> objectMotions(GameScript.Lookup lookup,int id) throws IOException {
        String path=GameEffectRecipes.passiveObject(lookup,id);
        if(path==null)return List.of();
        var fields=GameScript.fields(lookup.read(path));
        var references=new ArrayList<String>();
        references.add(GameScript.value(fields,"basic motion"));
        references.addAll(GameScript.values(fields,"etc motion"));
        var motions=new ArrayList<String>();
        for(String ref:references){
            if(ref.isEmpty())return List.of();
            String motion=GameScript.resolve(path,ref,lookup);
            if(motion==null)return List.of();
            motions.add(motion);
        }
        return List.copyOf(motions);
    }

    static int castingTime(List<GameScript.Field> fields,int level,int fallback){
        var values=GameScript.values(fields,"casting time");
        if(values.isEmpty())return fallback;
        int first=GameScript.integer(values.get(0),fallback);
        int last=values.size()>1?GameScript.integer(values.get(1),first):first;
        int maximum=Math.max(1,GameScript.integer(GameScript.value(fields,"maximum level"),1));
        double fraction=maximum==1?0:(Math.min(level,maximum)-1)/(double)(maximum-1);
        return (int)Math.max(0,Math.min(30000,Math.round(first+(last-(double)first)*fraction)));
    }

    static int staticValue(List<GameScript.Field> fields,int index,int fallback){
        var values=GameScript.values(fields,"static data");
        return index>=values.size()?fallback:Math.max(0,Math.min(30000,GameScript.integer(values.get(index),fallback)));
    }

    static boolean matches(String path,String job,int id,GameScript.Lookup lookup) throws IOException {
        return path.equals("skill/"+GameScript.catalog(lookup.read("skill/"+job+"skill.lst")).get(id));
    }

    static List<ResourceScript.Frame> frames(String owner,String reference,GameScript.Lookup lookup) throws IOException {
        if(reference==null)return List.of();
        String resolved=GameScript.resolve(owner,reference,lookup);
        return resolved==null?List.of():ResourceScript.animation(lookup.read(resolved)).frames();
    }

    static long duration(List<ResourceScript.Frame> frames,int count,Integer holdDelay){
        return frames.stream().limit(count).mapToLong(f->holdDelay!=null&&f.delay()>=10000?holdDelay:f.delay()).sum();
    }

    static int levelValue(List<GameScript.Field> fields,int level,int column,int fallback,int min,int max){
        var values=GameScript.values(fields,"level info");
        if(values.isEmpty())return fallback;
        int width=GameScript.integer(values.get(0),0);
        if(width<=column||width>64)return fallback;
        long index=1L+(level-1L)*width+column;
        if(index>=values.size())return fallback;
        return Math.max(min,Math.min(max,GameScript.integer(values.get((int)index),fallback)));
    }
}
