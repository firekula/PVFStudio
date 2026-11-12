package studio.pvf.preview;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;

/** Immutable actor tracks plus bounded worker-confined pixel preparation. Time is absolute. */
public final class GamePreview {
    static final int WORLD_BEHIND_ACTOR = 450, WORLD_IN_FRONT_OF_ACTOR = 3600;
    public record Options(String job,String action,Map<String,String> outfit,int level,boolean act1,String effect) {
        public Options {outfit=Map.copyOf(outfit);level=Math.max(1,Math.min(1000,level));}
        public static Options defaults(){return new Options("gunner","waiting motion",Map.of(),1,true,"");}
        public Options withEffect(String selected){return new Options(job,action,outfit,level,act1,selected);}
    }
    public record Draw(PreviewRenderer.Prepared prepared,double x,double y,boolean mirrored,String label,double opacity,double clipX,
                       boolean armorOutline) {
        public Draw(PreviewRenderer.Prepared prepared,double x,double y,boolean mirrored,String label,double opacity,double clipX) {
            this(prepared,x,y,mirrored,label,opacity,clipX,false);
        }
        public void paint(Graphics2D graphics,double originX,double originY,double zoom){
            if(armorOutline){
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                    if(dx!=0||dy!=0)prepared.paint(graphics,originX+(x+dx)*zoom,originY+(y+dy)*zoom,zoom,mirrored,opacity);
                }
                return;
            }
            if(Double.isNaN(clipX)){
                prepared.paint(graphics,originX+x*zoom,originY+y*zoom,zoom,mirrored,opacity);
                return;
            }
            var box=prepared.bounds(mirrored);
            double left=x+box.getMinX(),right=x+box.getMaxX();
            if(mirrored)right=Math.min(right,clipX);
            else left=Math.max(left,clipX);
            if(right<=left)return;
            Graphics2D clipped=(Graphics2D)graphics.create();
            try {
                clipped.clip(new Rectangle2D.Double(originX+left*zoom,originY+(y+box.getMinY())*zoom,
                        (right-left)*zoom,box.getHeight()*zoom));
                prepared.paint(clipped,originX+x*zoom,originY+y*zoom,zoom,mirrored,opacity);
            }finally{clipped.dispose();}
        }
    }
    public record Rendered(List<Draw> draws,Rectangle2D bounds,long milliseconds,List<String> warnings,List<CombatDebug.Volume> combatVolumes) {
        public Rendered(List<Draw> draws,Rectangle2D bounds,long milliseconds,List<String> warnings) {
            this(draws,bounds,milliseconds,warnings,List.of());
        }
    }
    private static final class ActorPose {
        final boolean secondary;
        AnimationTimeline body;
        ActorPose(boolean secondary) { this.secondary=secondary; }
    }
    private record CombatSource(ActorPose actor, boolean body, boolean attack, int range, boolean alliedAttack) {
        CombatSource(ActorPose actor,boolean body,boolean attack,int range){
            this(actor,body,attack,range,false);
        }
        static final CombatSource ALLY=new CombatSource(null,false,true,0,true);
        static final CombatSource AUTHORED=new CombatSource(null,false,true,0);
        static final CombatSource DECORATION=new CombatSource(null,false,false,0);
    }
    private record Track(String path,AnimationTimeline timeline,int layer,String label,long start,long end,double x,double y,
                         boolean loop,Act1GunnerCast.Clip castClip,SkillMotion motion,boolean mirror,
                         Act1SkillSequence.VisualStyle visualStyle,CombatSource combat,double combatScale) {}
    private record TrackStyle(int holdDelay,List<Integer> poseDelays,Act1GunnerCast.Clip castClip,
                              SkillMotion motion,double scale,boolean mirror,int firstFrame,int endFrame,Act1SkillSequence.VisualStyle visualStyle) {}
    private record ParticleTrack(String path,ParticleTimeline timeline,List<AnimationTimeline> animations,
                                 String label,long start,long end,double x,double y,double z,double clipX,boolean reverseFacing,
                                 SkillMotion sourceMotion) {}
    private record ParticleImage(String path,int index) {}
    private record ParticleImageBounds(int width,int height,int trimX,int trimY,Rectangle2D visible) {}
    private record PaintedSprite(String path, int index, int rgba, String effect) {}
    private record CachedSprite(ResourceScript.Frame frame, PreviewRenderer.Prepared prepared) {}
    private record OutlineGroup(int drawIndex, List<Draw> outlines) {}
    private final List<Track> tracks=new ArrayList<>();
    private final List<ParticleTrack> particles=new ArrayList<>();
    private List<Act1SkillSequence.AttackArea> attackAreas=List.of();
    private Rectangle2D attackAreaBounds;
    private int particleTrackLimit=48;
    private final List<String> warnings=new ArrayList<>();
    private final LinkedHashMap<PaintedSprite,CachedSprite> pixels=new LinkedHashMap<>(32,.75f,true);
    private final ScenePreview.SpriteLookup sprites;
    private final CharacterAppearance.Model appearance;
    private final GameItemInfo info;
    private final List<GameEffectRecipes.Effect> effects;
    private final boolean act1;
    private List<Act1SkillSequence.Pose> sequencePoses=List.of();
    private Rectangle2D bounds;
    private Rectangle2D actorBounds;
    private long cacheBytes,duration;
    private boolean wholeSequence;
    private boolean sortByGroundDepth;
    private boolean armorPossible;
    private Act1SuperArmor.Span runtimeArmor;
    private String unavailableReason="";
    private static final long CACHE_LIMIT=32L*1024*1024;
    private GamePreview(ScenePreview.SpriteLookup sprites,CharacterAppearance.Model appearance,GameItemInfo info,
                        List<GameEffectRecipes.Effect> effects,boolean act1){
        this.sprites=sprites;
        this.appearance=appearance;
        this.info=info;
        this.effects=effects;
        this.act1=act1;
        warnings.addAll(appearance.warnings());
        warnings.addAll(info.warnings());
    }
    public static GamePreview load(String path,String text,GameScript.Lookup lookup,Set<String> paths,NpkResources resources,Options options) throws IOException {
        return load(path,text,lookup,paths,new ScenePreview.SpriteLookup(){public NpkResources.Info info(String p,int f)throws IOException{return resources.info(p,f);}public NpkResources.Sprite sprite(String p,int f)throws IOException{return resources.sprite(p,f);}},options);
    }
    public static GamePreview load(String path,String text,GameScript.Lookup raw,Set<String> paths,ScenePreview.SpriteLookup sprites,Options requested) throws IOException {
        GameScript.Lookup bounded=GameScript.boundedLookup(raw,4096,32L*1024*1024);
        GameScript.Lookup lookup=p->p.equals(path)?text:bounded.read(p);
        boolean skill=path.endsWith(".skl");
        String job=CharacterAppearance.inferJob(path,text,requested.job());
        var weaponLookup=GameScript.boundedLookup(raw,512,12L*1024*1024);
        var equipped=Act1PreviewWeapon.outfit(path,job,requested.effect(),requested.act1(),paths,weaponLookup,requested.outfit());
        var options=new Options(job,requested.action(),equipped,requested.level(),requested.act1(),requested.effect());
        String recipeChoice=Act1PreviewWeapon.recipeChoice(path,job,options.effect(),options.act1());
        boolean actorOnly=options.effect().equals("actor");
        boolean chargeSkill=options.act1()&&path.equals(GameEffectRecipes.LASER_CHARGE)
                &&"gunner/laserriflecharge.skl".equals(GameScript.catalog(lookup.read("skill/gunnerskill.lst")).get(41));
        String laserPath=chargeSkill?GameEffectRecipes.LASER_SKILL:path;
        String laserText=chargeSkill?lookup.read(laserPath):text;
        String passive=chargeSkill?text:path.equals(GameEffectRecipes.LASER_SKILL)?lookup.read(GameEffectRecipes.LASER_CHARGE):null;
        int chargeLevel=chargeSkill?options.level():1;
        var normalLaser=GameEffectRecipes.laser(laserPath,laserText,options.act1(),false);
        var shortLaser=GameEffectRecipes.laser(laserPath,laserText,options.act1(),false,passive,chargeLevel);
        var chargedLaser=GameEffectRecipes.laser(laserPath,laserText,options.act1(),true,passive,chargeLevel);
        var laser=switch(options.effect()){
            case "actor" -> null;
            case "laser:normal" -> shortLaser;
            case "laser:charged" -> chargedLaser;
            case "" -> chargeSkill?chargedLaser:normalLaser;
            default -> normalLaser;
        };
        String character="character/"+job+"/"+job+".chr";
        var available=CharacterAppearance.motions(Optional.ofNullable(lookup.read(character)).orElse(""));
        var cast=recipeChoice.isEmpty()?Act1GunnerCast.find(path,text,lookup,available,options.level(),options.act1(),sprites):null;
        var sequence=Act1SkillSequence.find(path,text,lookup,available,options.level(),recipeChoice,options.act1(),
                buffJob->Act1BuffCast.standingHeight(CharacterAppearance.load(path,text,lookup,paths,buffJob,"waiting motion",options.outfit()),lookup,sprites),
                options.outfit().get("weapon"),sprites);
        var attached=sequence==null&&recipeChoice.isEmpty()?attachedCast(path,lookup,available,options.act1()):null;
        String matched="";
        if(skill&&!actorOnly&&options.act1()){
            String stem=path.substring(path.lastIndexOf('/')+1,path.length()-4);
            matched=GameEffectRecipes.motionFor(available,stem);
            if(!matched.isEmpty()&&GameScript.resolve(character,available.get(matched),lookup)==null)matched="";
        }
        String action=options.action();
        if(laser!=null)action=laserAction(available,17,"subweaponfrontrapid1");
        else if(cast!=null)action=cast.action();
        else if(attached!=null)action=attached.action();
        else if(sequence!=null)action=sequence.poses().get(0).action();
        else if(!matched.isEmpty())action=matched;
        var appearance=CharacterAppearance.load(path,text,lookup,paths,job,action,options.outfit());
        var choices=new ArrayList<>(GameEffectRecipes.effects(path,text,lookup,options.act1()));
        var grabMiss=Act1SkillSequence.grabMissChoice(path,lookup,options.act1());
        if(grabMiss!=null)choices.add(grabMiss);
        var spearMiss=Act1SkillSequence.dragonSpearMissChoice(path,lookup,options.act1());
        if(spearMiss!=null)choices.add(spearMiss);
        var fighterMiss=Act1FighterGrab.missChoice(path,lookup,options.act1());
        if(fighterMiss!=null)choices.add(fighterMiss);
        var smasherChoice=Act1PriestSupplement.smasherChoice(path,lookup,options.act1());
        if(smasherChoice!=null)choices.add(smasherChoice);
        var fighterAlternate=Act1FighterCast.alternateChoice(path,lookup,options.act1());
        if(fighterAlternate!=null)choices.add(fighterAlternate);
        var wallThrow=Act1FighterCast.wallThrowChoice(path,lookup,options.act1());
        if(wallThrow!=null)choices.add(wallThrow);
        choices.addAll(Act1FighterHits.choices(path,lookup,options.act1()));
        var crazyMount=Act1FighterCast.crazyMountChoice(path,lookup,options.act1());
        if(crazyMount!=null)choices.add(crazyMount);
        choices.addAll(Act1SwordTechniqueCast.choices(path,lookup,options.act1()));
        choices.addAll(Act1SwordSupplement.choices(path,lookup,options.act1()));
        choices.addAll(Act1SwordMeleeSupplement.choices(path,lookup,options.act1()));
        choices.addAll(Act1SwordRemaining.bloodChoices(path,lookup,options.act1()));
        choices.addAll(Act1SwordRemaining.targetChoices(path,lookup,options.act1()));
        choices.addAll(Act1SwordAsuraSupplement.choices(path,text,lookup,options.act1()));
        choices.addAll(Act1PreviewWeapon.choices(path,job,paths,options.act1()));
        choices.addAll(Act1GunnerSupplement.choices(path,lookup,options.act1()));
        choices.addAll(Act1GunnerSupportCast.choices(path,lookup,options.act1()));
        choices.addAll(Act1GunnerMechanicalSupplement.choices(path,lookup,options.act1()));
        choices.addAll(Act1GunnerUtilityCast.choices(path,lookup,options.act1()));
        choices.addAll(Act1GunnerAdvancedFire.choices(path,lookup,options.act1()));
        choices.addAll(Act1PriestRemaining.choices(path,lookup,options.act1()));
        choices.addAll(Act1PriestSupplement.thunderChoices(path,lookup,options.act1()));
        choices.addAll(Act1PriestSummon.choices(path,lookup,options.act1()));
        choices.addAll(Act1PriestCast.choices(path,lookup,options.act1()));
        choices.addAll(Act1PriestAntiairUpper.choices(path,lookup,options.act1()));
        choices.addAll(Act1MageSupplement.choices(path,lookup,options.act1()));
        var shieldHit=Act1BuffCast.shieldHitChoice(path,lookup,options.act1());
        if(shieldHit!=null)choices.add(shieldHit);
        var elementCharge=Act1MageElementCast.chargeChoice(path,lookup,options.act1());
        if(elementCharge!=null)choices.add(elementCharge);
        var frostHit=Act1MageElementCast.frostHitChoice(path,lookup,options.act1());
        if(frostHit!=null)choices.add(frostHit);
        var frostChargedHit=Act1MageElementCast.frostChargedHitChoice(path,lookup,options.act1());
        if(frostChargedHit!=null)choices.add(frostChargedHit);
        var precisionDown=Act1SkillSequence.precisionDownChoice(path,lookup,options.act1());
        if(precisionDown!=null)choices.add(precisionDown);
        if(normalLaser!=null){
            if(choices.stream().noneMatch(e->e.key().equals("actor")))choices.add(new GameEffectRecipes.Effect("actor","仅角色动作（不施放）","",path,"动作检查"));
            choices.add(new GameEffectRecipes.Effect("laser:unlearned","激光炮 · 未习得蓄电","",path,"ACT1 普通施放"));
            if(shortLaser!=null&&shortLaser.hasChargePassive())
                choices.add(new GameEffectRecipes.Effect("laser:normal","激光炮 · 习得蓄电后短按","",path,"ACT1 短按施放"));
            if(chargedLaser!=null)
                choices.add(new GameEffectRecipes.Effect("laser:charged","激光炮 · 满蓄力（被动 Lv."+chargeLevel+"）","",path,"蓄力参数来自同包被动技能"));
        }
        var model=new GamePreview(sprites,appearance,GameItemInfo.read(path,text,lookup,options.level()),List.copyOf(choices),options.act1());
        if(options.act1()&&sequence!=null&&path.equals("skill/gunner/mechadrop.skl"))
            model.particleTrackLimit=160; // At most 32 scripted drops, each with five time-limited sources.
        if(options.act1()&&sequence!=null)
            model.runtimeArmor=Act1SuperArmor.runtimeSpan(path,text,lookup,options.level(),sequence);
        if(options.act1()&&Act1PreviewWeapon.forcedType(path,job,options.effect())!=null
                &&equipped.getOrDefault("weapon","").isEmpty())model.warn("当前 PVF 缺少此分支的试用武器");
        boolean showActor=!skill||actorOnly||laser!=null||cast!=null||attached!=null||!matched.isEmpty();
        long transition=cast!=null?cast.changeAt():Long.MAX_VALUE;
        boolean motionOnly=skill&&!matched.isEmpty()&&laser==null&&cast==null&&attached==null&&sequence==null;
        if(sequence!=null)model.addSequenceActors(sequence,path,text,lookup,paths,options,job);
        else if(laser!=null)model.addLaser(path,text,lookup,paths,options,laser,available,chargeLevel,normalLaser.fireAt());
        else if(showActor)model.addActor(appearance,lookup,0,transition,true,motionOnly?300:null,cast==null?-1:cast.firstFrameDelay());
        if(laser==null)model.actorBounds=model.bounds==null?null:(Rectangle2D)model.bounds.clone();
        if(cast!=null)model.addCast(path,text,lookup,paths,options,cast);
        if(sequence!=null){
            for(var visual:sequence.visuals())model.addVisual(visual,lookup);
            for(var emitter:sequence.emitters())model.addParticles(emitter,lookup);
            model.addAttackAreas(sequence.attackAreas());
            model.warn(sequence.note());
        }
        if(attached!=null){
            for(var clip:attached.clips())model.add(clip.path(),lookup,List.of(),clip.renderLayer(),clip.label(),
                    clip.start(),clip.end(),clip.x(),clip.y(),false);
            model.warn(attached.note());
        }
        if(appearance.parts().stream().noneMatch(p->p.owner().equals(path))&&path.endsWith(".equ"))model.warn(options.outfit().containsKey(CharacterAppearance.slot(text))?"当前部位已隐藏或由搭配替换":"配置未定义独立穿戴外观；当前显示参考角色");
        String selectedEffect=options.effect();
        if(skill&&selectedEffect.isEmpty()&&laser==null&&cast==null&&attached==null&&sequence==null){
            selectedEffect=choices.stream().filter(e->!e.path().isEmpty()).map(GameEffectRecipes.Effect::key).findFirst().orElse("");
        }
        for(var effect:model.effects)if(effect.key().equals(selectedEffect)) {
            model.warn(effect.note());
            if(effect.path().endsWith(".ani"))model.add(effect.path(),lookup,List.of(0,0),5000,effect.label(),0,Long.MAX_VALUE,effect.x(),effect.y(),false);
            else if(effect.path().endsWith(".ptl"))model.addParticles(
                    new SkillParticleEmitter(effect.path(),effect.label(),0,0,0,effect.x(),-effect.y()),lookup);
        }
        model.wholeSequence=skill&&!actorOnly&&(!model.tracks.isEmpty()||!model.particles.isEmpty());
        if(motionOnly)model.warn("当前播放 CHR 中唯一同名动作，尚未关联技能特效时序");
        if(skill&&!actorOnly&&model.tracks.isEmpty()&&model.particles.isEmpty()){
            String type=GameScript.flag(GameScript.value(GameScript.fields(text),"type"));
            model.unavailableReason=type.equals("passive")?"此被动技能没有可关联的独立动画":"未找到可播放的技能动画";
            model.warn(model.unavailableReason+"；可在效果下拉框选择“仅角色动作”");
        }
        model.tracks.sort(Comparator.comparingInt(Track::layer));
        model.sortByGroundDepth=model.tracks.stream().anyMatch(GamePreview::usesGroundDepth);
        model.armorPossible=model.act1&&(model.runtimeArmor!=null||model.tracks.stream().anyMatch(
                track->track.combat().body()&&track.timeline().hasAuthoredArmor()));
        model.duration=Math.max(1,model.duration);
        if(model.bounds==null)model.bounds=new Rectangle2D.Double(-36,-160,72,176);
        if(model.actorBounds==null)model.actorBounds=(Rectangle2D)model.bounds.clone();
        return model;
    }

    static GameEffectRecipes.AttachedCast attachedCast(String path,GameScript.Lookup lookup,Map<String,String> actions,
                                                       boolean act1) throws IOException {
        var fighter=GameEffectRecipes.fighterCast(path,lookup,actions,act1);
        return fighter!=null?fighter:Act1MeleeCast.find(path,lookup,actions,act1);
    }

    private void addSequenceActors(Act1SkillSequence sequence,String path,String text,GameScript.Lookup lookup,
                                   Set<String> paths,Options options,String job) throws IOException {
        sequencePoses=sequence.poses();
        boolean first=true;
        for(var pose:sequencePoses){
            var actor=first?appearance:CharacterAppearance.load(path,text,lookup,paths,job,pose.action(),options.outfit());
            addActor(actor,lookup,pose.start(),pose.end(),first,pose.holdDelay(),-1,pose.motion(),pose.fitToStage()?pose.end()-pose.start():0,
                    pose.firstFrame(),pose.endFrame(),pose.reverseFacing(),pose.keyframes(),pose.secondary());
            first=false;
        }
    }

    private void addActor(CharacterAppearance.Model actor,GameScript.Lookup lookup,long start,long end,boolean title,Integer holdDelay,int firstFrameDelay){
        addActor(actor,lookup,start,end,title,holdDelay,firstFrameDelay,null,0,0,Integer.MAX_VALUE,false,List.of(),false);
    }

    private void addActor(CharacterAppearance.Model actor,GameScript.Lookup lookup,long start,long end,boolean title,
                          Integer holdDelay,int firstFrameDelay,SkillMotion motion,long fitDuration,int firstFrame,int endFrame,
                          boolean reverseFacing,List<Act1SkillSequence.VisualKeyframe> keyframes,boolean secondary){
        List<Integer> poseDelays=act1?actorDelays(actor.bodyAnimation(),lookup,holdDelay,firstFrameDelay,fitDuration,
                firstFrame,endFrame):List.of();
        addActor(actor,lookup,start,end,title,holdDelay,motion,firstFrame,endFrame,reverseFacing,keyframes,secondary,poseDelays);
    }

    private void addActor(CharacterAppearance.Model actor,GameScript.Lookup lookup,long start,long end,boolean title,
                          Integer holdDelay,SkillMotion motion,int firstFrame,int endFrame,boolean reverseFacing,
                          List<Act1SkillSequence.VisualKeyframe> keyframes,boolean secondary,List<Integer> poseDelays){
        var visualStyle=new Act1SkillSequence.VisualStyle(null,0,keyframes,Map.of(),reverseFacing);
        var style=new TrackStyle(holdDelay==null?0:holdDelay,poseDelays,null,motion,1,true,firstFrame,endFrame,visualStyle);
        var pose=new ActorPose(secondary);
        double top=Double.POSITIVE_INFINITY;
        for(var part:actor.parts()){
            if(part.slot().equals("title name"))continue;
            // Keep the actor's signed EQU order between the existing rear and front skill tracks.
            int layer=1000+Math.max(-499,Math.min(2399,part.layer()));
            if(secondary)layer=-100+Math.max(-100,Math.min(100,part.layer()/10));
            String label=secondary?"附加角色 · "+part.slot():part.slot();
            var partBounds=add(part.animation(),lookup,part.variation(),layer,label,start,end,0,0,false,style,
                    new CombatSource(pose,part.slot().equals("body"),true,0));
            if(partBounds!=null)top=Math.min(top,partBounds.getMinY());
        }
        if(title){
            var titleStyle=new TrackStyle(0,List.of(),null,null,1,true,0,Integer.MAX_VALUE,
                    Act1SkillSequence.VisualStyle.DEFAULT);
            for(var part:actor.parts()){
                if(part.slot().equals("title name"))add(part.animation(),lookup,part.variation(),part.layer(),part.slot(),
                        start,Long.MAX_VALUE,0,Double.isFinite(top)?top:-140,false,titleStyle);
            }
        }
    }

    /** ACT1 copies body frame delays into the overlapping frames of equipped layers (0x493B70). */
    private List<Integer> actorDelays(String body,GameScript.Lookup lookup,Integer holdDelay,int firstFrameDelay,long fitDuration,int firstFrame,int endFrame){
        if(body==null)return List.of();
        try {
            String text=lookup.read(body);
            if(text==null)return List.of();
            var frames=ResourceScript.animation(text).frames();
            var delays=new ArrayList<Integer>(frames.size());
            for(int i=0;i<frames.size();i++){
                int delay=frames.get(i).delay();
                if(i==firstFrame&&firstFrameDelay>=0)delay=firstFrameDelay;
                else if(holdDelay!=null&&delay>=10000)delay=holdDelay;
                delays.add(delay);
            }
            if(fitDuration>0&&firstFrame<delays.size()){
                var slice=delays.subList(firstFrame,Math.min(endFrame,delays.size()));
                long total=slice.stream().mapToLong(Integer::longValue).sum();
                if(total>0&&total!=fitDuration)retime(slice,total,fitDuration);
            }
            return delays;
        }catch(IOException|IllegalArgumentException failure){
            warn("身体动作时钟："+failure.getMessage());
            return List.of();
        }
    }

    private void addCast(String path,String text,GameScript.Lookup lookup,Set<String> paths,Options options,Act1GunnerCast.Cast cast) throws IOException {
        if(!cast.followAction().isEmpty()){
            var fire=CharacterAppearance.load(path,text,lookup,paths,"gunner",cast.followAction(),options.outfit());
            addActor(fire,lookup,cast.changeAt(),cast.end(),false,null,-1);
        }
        for(var clip:cast.clips())add(clip.path(),lookup,clip.variations(),clip.layer(),clip.label(),
                clip.start(),clip.end(),clip.x(),clip.y(),false,clip.holdDelay(),List.of(),clip);
        for(var visual:cast.visuals())addVisual(visual,lookup);
        if(cast.emitter()!=null)addParticles(cast.emitter(),lookup);
        warn(cast.note());
    }

    private void addVisual(Act1SkillSequence.Visual visual,GameScript.Lookup lookup){
        add(visual.path(),lookup,visual.variations(),visual.layer(),visual.label(),
                visual.start(),visual.end(),visual.x(),visual.y(),false,
                new TrackStyle(0,List.of(),null,visual.motion(),visual.scale(),visual.mirror(),0,Integer.MAX_VALUE,visual.style()),
                visual.alliedAttack()?CombatSource.ALLY:visual.decorative()?CombatSource.DECORATION:CombatSource.AUTHORED);
    }
    private static final String LASER_GUN="character/gunner/effect/animation/subweapon/";
    private static final String LASER_EFFECT=LASER_GUN+"laserrifleeffect/";

    private static String laserAction(Map<String,String> actions,int index,String stockName){
        String slot="etc motion:"+index;
        if(actions.containsKey(slot))return slot;
        String action=GameEffectRecipes.motionFor(actions,stockName);
        return action.isEmpty()?"waiting motion":action;
    }

    private static List<Integer> laserAnimationClock(String path,GameScript.Lookup lookup,int rate) throws IOException {
        if(path==null)return List.of();
        String text=lookup.read(path);
        if(text==null)return List.of();
        return laserClockAtRate(ResourceScript.animation(text).frames().stream().map(ResourceScript.Frame::delay).toList(),rate);
    }

    private static List<Integer> laserClockAtRate(List<Integer> clock,int rate){
        if(rate==1000)return clock;
        // 0x4896C0 stores the speed in percent and 0x82EC70 truncates each frame duration.
        float percent=rate*0.1f;
        return clock.stream().map(delay->(int)(delay*100.0/percent)).toList();
    }

    private static long laserClockDuration(List<Integer> clock){
        return clock.stream().mapToLong(Integer::longValue).sum();
    }

    private void addLaser(String path,String text,GameScript.Lookup lookup,Set<String> paths,Options options,
                          GameEffectRecipes.Laser laser,Map<String,String> actions,int chargeLevel,long drawDuration) throws IOException {
        String firing=laserAction(actions,18,"subweaponfrontrapid2");
        var fire=CharacterAppearance.load(path,text,lookup,paths,"gunner",firing,options.outfit());
        var authoredDrawClock=laserAnimationClock(appearance.bodyAnimation(),lookup,1000);
        long authoredDraw=laserClockDuration(authoredDrawClock);
        long rate=drawDuration>0?1000L*authoredDraw/drawDuration:1000;
        int drawRate=(int)Math.max(1,Math.min(Integer.MAX_VALUE,rate));
        var drawClock=laserClockAtRate(authoredDrawClock,drawRate);
        long drawEnd=drawDuration==0||drawClock.isEmpty()?drawDuration:laserClockDuration(drawClock);
        String flash2=LASER_EFFECT+"laserriflefullcharge2.ani";
        var flashClock=laser.charged()?laserAnimationClock(flash2,lookup,drawRate):List.<Integer>of();
        long flashEnd=laser.chargeThreshold()+laserClockDuration(flashClock);
        // Demonstrate a short tap or release after the native full-charge confirmation flash.
        long fireAt=laser.charged()?Math.max(drawEnd,flashEnd):drawEnd;
        addActor(appearance,lookup,0,fireAt,true,null,null,0,Integer.MAX_VALUE,false,List.of(),false,drawClock);
        addLaserGun(lookup,"subweaponfrontrapid1",0,fireAt,drawClock,null);

        String object=GameEffectRecipes.passiveObject(lookup,22017);
        var motions=object==null?List.<String>of():GameScript.values(GameScript.fields(lookup.read(object)),"etc motion");
        int first=laser.charged()?3:0;
        String beam=motions.size()>first?GameScript.resolve(object,motions.get(first),lookup):null;
        long beamDuration=laserClockDuration(laserAnimationClock(beam,lookup,1000));
        long beamEnd=beamDuration>0?fireAt+beamDuration:Long.MAX_VALUE;
        addActor(fire,lookup,fireAt,beamEnd,false,null,-1);
        var fireClock=actorDelays(fire.bodyAnimation(),lookup,null,-1,0,0,Integer.MAX_VALUE);
        addLaserGun(lookup,"subweaponfrontrapid2",fireAt,beamEnd,fireClock,null);

        // Body children use native priorities 651, 1000 and 2151. Object attachments draw outside the body tree.
        if(laser.hasChargePassive()&&drawClock.size()>3){
            long chargeAt=drawClock.subList(0,3).stream().mapToLong(Integer::longValue).sum();
            long chargingEnd=laser.charged()?flashEnd:fireAt;
            var chargingClock=laserAnimationClock(LASER_EFFECT+"chargingeffect.ani",lookup,drawRate);
            add(LASER_EFFECT+"chargingeffect.ani",lookup,List.of(),2000,"蓄力光效",chargeAt,chargingEnd,0,0,false,0,chargingClock,null);
            if(fireAt>chargingEnd){
                var hold=new TrackStyle(0,List.of(),null,null,1,true,8,9,Act1SkillSequence.VisualStyle.DEFAULT);
                add(LASER_EFFECT+"chargingeffect.ani",lookup,List.of(),2000,"蓄力光效",chargingEnd,fireAt,0,0,false,hold);
                var fullClock=laserAnimationClock(LASER_EFFECT+"chargefulleffect.ani",lookup,drawRate);
                add(LASER_EFFECT+"chargefulleffect.ani",lookup,List.of(),2001,"满蓄力枪光",chargingEnd,fireAt,0,0,false,0,fullClock,null);
            }
        }
        if(laser.charged()){
            String flash1=LASER_EFFECT+"laserriflefullcharge1.ani";
            add(flash2,lookup,List.of(),3501,"满蓄力光效",laser.chargeThreshold(),flashEnd,0,0,false,0,flashClock,null);
            add(flash1,lookup,List.of(),3502,"满蓄力光效",laser.chargeThreshold(),flashEnd,0,0,false,0,laserAnimationClock(flash1,lookup,drawRate),null);
        }else if(laser.hasChargePassive()){
            // State 22 resets FireOnceEffect as well, and attaches only FireOnceSpark.
            add(LASER_EFFECT+"fireoncespark.ani",lookup,List.of(),3501,"普通激光火花",fireAt,beamEnd,0,0,false);
        }
        add(LASER_EFFECT+"firering.ani",lookup,List.of(),3503,"激光炮环",fireAt,beamEnd,0,0,false);

        if(beamDuration>0){
            addLaserRecovery(path,text,lookup,paths,options,actions,laser.recoilSpeed(fireAt),beamEnd);
            if(bounds!=null)actorBounds=(Rectangle2D)bounds.clone();
            add(beam,lookup,List.of(),4000,"激光起始",fireAt,beamEnd,150,-85,false,
                    new CombatSource(null,false,true,laser.range()));
            if(motions.size()>first+2){
                String tile=GameScript.resolve(object,motions.get(first+1),lookup);
                String end=GameScript.resolve(object,motions.get(first+2),lookup);
                if(tile!=null)for(int i=0;i<laser.tileCount();i++)
                    add(tile,lookup,List.of(),4000,"激光光束",fireAt,beamEnd,150+50*i,-85,false,CombatSource.DECORATION);
                if(end!=null)add(end,lookup,List.of(),4000,"激光末端",fireAt,beamEnd,150+laser.endOffset(),-85,false,
                        CombatSource.DECORATION);
                if(tile==null||end==null)warn("激光炮对象 22017 缺少光束部件");
            }else warn("激光炮对象 22017 缺少光束部件");
        }else warn("未找到激光炮对象 22017 的起始动画");
        warn("激光炮按基础配置演示；未模拟角色攻速修正、伤害和命中");
        if(laser.charged())warn("满蓄力按被动技能 Lv."+chargeLevel+" 演示，确认闪光结束时松键");
        else if(laser.hasChargePassive())warn("普通激光炮按习得蓄力被动后的短按分支演示");
    }

    private void addLaserGun(GameScript.Lookup lookup,String stem,long start,long end,List<Integer> clock,SkillMotion motion){
        var style=new TrackStyle(0,clock,null,motion,1,true,0,Integer.MAX_VALUE,Act1SkillSequence.VisualStyle.DEFAULT);
        add(LASER_GUN+stem+"b.ani",lookup,List.of(2,0),1651,"激光炮枪体",start,end,0,0,false,style);
        add(LASER_GUN+stem+"a.ani",lookup,List.of(2,0),3151,"激光炮枪体",start,end,0,0,false,style);
    }

    private void addLaserRecovery(String path,String text,GameScript.Lookup lookup,Set<String> paths,Options options,
                                  Map<String,String> actions,int speed,long start) throws IOException {
        // Object 22017 ends state 22. State 26 plays Once2 with reverse motion and the two FireCharge tracks.
        long travelTime=2L*(speed-5);
        long end=start+travelTime;
        var motion=new SkillMotion(start,travelTime,-speed,500);
        String action=laserAction(actions,16,"subweaponfrontonce2");
        var actor=CharacterAppearance.load(path,text,lookup,paths,"gunner",action,options.outfit());
        addActor(actor,lookup,start,end,false,null,-1,motion,0,0,Integer.MAX_VALUE,false,List.of(),false);
        var clock=actorDelays(actor.bodyAnimation(),lookup,null,-1,0,0,Integer.MAX_VALUE);
        addLaserGun(lookup,"subweaponfrontonce2",start,end,clock,motion);
        var style=new TrackStyle(0,List.of(),null,motion,1,true,0,Integer.MAX_VALUE,Act1SkillSequence.VisualStyle.DEFAULT);
        add(LASER_EFFECT+"firechargeeffect.ani",lookup,List.of(),-1001,"收枪枪光",start,end,0,0,false,style);
        add(LASER_EFFECT+"firechargespark.ani",lookup,List.of(),3501,"收枪火花",start,end,0,0,false,style);
        if(speed>150){
            var reverse=new Act1SkillSequence.VisualStyle(null,0,List.of(),Map.of(),true);
            var dustStyle=new TrackStyle(0,List.of(),null,null,1,true,0,Integer.MAX_VALUE,reverse);
            String dust="character/gunner/effect/animation/turningshoot.ani";
            add(dust,lookup,List.of(),4000,"后坐扬尘",start,-1,130,0,false,dustStyle);
            long dustEnd=start+laserClockDuration(laserAnimationClock(dust,lookup,1000));
            if(dustEnd>end){
                var idle=CharacterAppearance.load(path,text,lookup,paths,"gunner","waiting motion",options.outfit());
                addActor(idle,lookup,end,dustEnd,false,null,-1,motion,0,0,Integer.MAX_VALUE,false,List.of(),false);
            }
        }
    }
    private void add(String path,GameScript.Lookup lookup,List<Integer> variants,int layer,String label,long start,long end,double x,double y,boolean loop) {
        add(path,lookup,variants,layer,label,start,end,x,y,loop,0);
    }

    private void add(String path,GameScript.Lookup lookup,List<Integer> variants,int layer,String label,long start,long end,double x,double y,boolean loop,int holdDelay) {
        add(path,lookup,variants,layer,label,start,end,x,y,loop,holdDelay,List.of(),null);
    }

    private void add(String path,GameScript.Lookup lookup,List<Integer> variants,int layer,String label,long start,long end,double x,double y,boolean loop,int holdDelay,List<Integer> poseDelays,Act1GunnerCast.Clip castClip) {
        add(path,lookup,variants,layer,label,start,end,x,y,loop,new TrackStyle(holdDelay,poseDelays,castClip,null,castClip==null?1:castClip.scale(),true,0,Integer.MAX_VALUE,Act1SkillSequence.VisualStyle.DEFAULT));
    }

    private Rectangle2D add(String path,GameScript.Lookup lookup,List<Integer> variants,int layer,String label,long start,long end,double x,double y,boolean loop,TrackStyle style) {
        return add(path,lookup,variants,layer,label,start,end,x,y,loop,style,CombatSource.AUTHORED);
    }

    private void add(String path,GameScript.Lookup lookup,List<Integer> variants,int layer,String label,long start,long end,
                     double x,double y,boolean loop,CombatSource combat) {
        add(path,lookup,variants,layer,label,start,end,x,y,loop,
                new TrackStyle(0,List.of(),null,null,1,true,0,Integer.MAX_VALUE,Act1SkillSequence.VisualStyle.DEFAULT),combat);
    }

    private Rectangle2D add(String path,GameScript.Lookup lookup,List<Integer> variants,int layer,String label,long start,
                            long end,double x,double y,boolean loop,TrackStyle style,CombatSource combat) {
        try {
            if(end>=0&&end<=start)return null;
            if(tracks.size()>=256)throw new IOException("组合轨道超过 256 个");
            var visualStyle=style.visualStyle();
            var parsed=visualStyle.animation();
            if(parsed==null){
                String text=lookup.read(path);
                if(text==null)throw new IOException("找不到 ANI："+path);
                parsed=ResourceScript.animation(text);
            }
            var frames=new ArrayList<ResourceScript.Frame>();
            int[] parameters=variants.stream().mapToInt(Integer::intValue).toArray();
            for(int index=style.firstFrame();index<Math.min(style.endFrame(),parsed.frames().size());index++){
                var frame=parsed.frames().get(index);
                if(act1&&label.equals("body"))frame=Act1GunnerCast.bodyFrame(path,index,frame);
                if(act1)frame=Act1GunnerAdvancedFire.napalmFrame(path,frame);
                var override=visualStyle.spriteOverride();
                if(override!=null&&frame.image().isEmpty())frame=frame.withImage(override.image()).withIndex(override.index());
                if(!frame.image().isEmpty())frame=frame.withImage(NpkResources.substitute(frame.image(),parameters));
                if(style.scale()!=1)frame=frame.scaled(style.scale());
                if(index<style.poseDelays().size())frame=frame.withDelay(style.poseDelays().get(index));
                else if(style.holdDelay()>0&&frame.delay()>=10000)frame=frame.withDelay(style.holdDelay());
                var frameOverride=visualStyle.frameOverrides().get(index);
                if(frameOverride!=null){
                    frame=frame.translated(frameOverride.dx(),frameOverride.dy()).scaled(frameOverride.scaleX(),frameOverride.scaleY());
                    if(frameOverride.delay()!=null)frame=frame.withDelay(frameOverride.delay());
                }
                frames.add(frame);
            }
            if(visualStyle.animationDuration()>0){
                var delays=new ArrayList<>(frames.stream().map(ResourceScript.Frame::delay).toList());
                long authored=delays.stream().mapToLong(Integer::longValue).sum();
                if(authored>0){
                    retime(delays,authored,visualStyle.animationDuration());
                    for(int i=0;i<frames.size();i++)frames.set(i,frames.get(i).withDelay(delays.get(i)));
                }
            }
            var animation=new ResourceScript.Animation(parsed.loop(),List.copyOf(frames),parsed.warnings());var timeline=new AnimationTimeline(animation);
            if(frames.isEmpty())throw new IOException("空动画 "+path);
            Rectangle2D partBounds=null;
            for(var frame:frames)if(!frame.image().isEmpty()){
                var sprite=sprites.sprite(frame.image(),frame.index());var visible=visibleBounds(sprite.image());if(visible==null)continue;
                var transform=PreviewRenderer.transform(frame,sprite.canvasWidth(),sprite.canvasHeight(),sprite.x(),sprite.y());
                var b=transform.createTransformedShape(visible).getBounds2D();partBounds=partBounds==null?b:partBounds.createUnion(b);
            }
            boolean title=label.equals("title name");
            if(title&&partBounds!=null){x=-partBounds.getCenterX();y-=8+partBounds.getMaxY();warn("称号按角色可见边界上方对齐；锚点尚待同版本游戏对照");}
            var localBounds=partBounds;
            long stop=end<0?start+timeline.duration():end;
            if(partBounds!=null){
                var baseBounds=partBounds;
                for(var key:visualStyle.keyframes()){
                    double scale=key.scale();
                    var scaled=new Rectangle2D.Double(baseBounds.getX()*scale,baseBounds.getY()*scale,
                            baseBounds.getWidth()*scale,baseBounds.getHeight()*scale);
                    partBounds=partBounds.createUnion(scaled);
                }
                var motion=style.motion();
                double startX=motion==null?0:motion.offsetAt(start),endX=motion==null?0:motion.offsetAt(stop);
                double orbitRadius=motion==null?0:motion.orbitRadius();
                double left=partBounds.getMinX(),right=partBounds.getMaxX();
                if(!style.mirror()){
                    double extent=Math.max(Math.abs(left),Math.abs(right));
                    left=-extent;
                    right=extent;
                }else if(visualStyle.reverseFacing()){
                    double oldLeft=left;
                    left=-right;
                    right=-oldLeft;
                }
                left+=x+(orbitRadius>0?-orbitRadius:Math.min(startX,endX));
                right+=x+(orbitRadius>0?orbitRadius:Math.max(startX,endX));
                var castClip=style.castClip();
                if(castClip!=null){
                    if(castClip.movement()!=null)right+=castClip.movement().maxOffset();
                    if(!Double.isNaN(castClip.clipX()))left=Math.max(left,castClip.clipX());
                }
                double rise=motion==null?0:motion.maximumHeight(start,stop);
                if(title){
                    double minOffset=0,maxOffset=0;
                    for(var pose:sequencePoses){
                        if(pose.secondary())continue;
                        var poseMotion=pose.motion();
                        if(poseMotion==null)continue;
                        double from=poseMotion.offsetAt(pose.start()),to=poseMotion.offsetAt(pose.end());
                        minOffset=Math.min(minOffset,Math.min(from,to));
                        maxOffset=Math.max(maxOffset,Math.max(from,to));
                        rise=Math.max(rise,poseMotion.maximumHeight(pose.start(),pose.end()));
                    }
                    left+=minOffset;
                    right+=maxOffset;
                }
                double dip=motion==null?0:motion.orbitDepth();
                if(right>left)extendBounds(new Rectangle2D.Double(left,partBounds.getMinY()+y-rise,right-left,partBounds.getHeight()+rise+dip));
            }
            if(combat.body())combat.actor().body=timeline;
            tracks.add(new Track(path,timeline,layer,label,start,stop,x,y,loop,style.castClip(),style.motion(),style.mirror(),
                    visualStyle,combat,style.scale()));
            duration=Math.max(duration,Math.min(60000,stop==Long.MAX_VALUE?start+timeline.duration():stop));
            for(String warning:parsed.warnings())warn(path+"："+warning);
            return localBounds;
        }catch(IOException|IllegalArgumentException failure){warn(label+"："+failure.getMessage());}
        return null;
    }
    private static void retime(List<Integer> delays,long authored,long target){
        long elapsed=0,previous=0;
        for(int i=0;i<delays.size();i++){
            elapsed+=delays.get(i);
            long fitted=Math.round(elapsed*(double)target/authored);
            delays.set(i,(int)(fitted-previous));
            previous=fitted;
        }
    }

    private void extendBounds(Rectangle2D box){bounds=bounds==null?box:bounds.createUnion(box);}

    private static Rectangle2D visibleBounds(java.awt.image.BufferedImage image) {
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        int[] row=new int[image.getWidth()];for(int y=0;y<image.getHeight();y++){image.getRGB(0,y,image.getWidth(),1,row,0,image.getWidth());for(int x=0;x<row.length;x++)if((row[x]>>>24)!=0){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=y;}}
        return right<left?null:new Rectangle2D.Double(left,top,right-left+1,bottom-top+1);
    }

    private void addParticles(SkillParticleEmitter emitter,GameScript.Lookup lookup) {
        try {
            if(particles.size()>=particleTrackLimit)throw new IOException("技能粒子源超过 "+particleTrackLimit+" 个");
            String text=lookup.read(emitter.path());
            if(text==null)throw new IOException("找不到 PTL："+emitter.path());
            var script=ParticleScript.parse(text);
            if(act1){
                script=Act1GunnerSupplement.particleScript(emitter.path(),script);
                script=Act1GunnerAdvancedFire.particleScript(emitter.path(),script);
                script=Act1FlameBullet.particleScript(emitter.path(),script);
            }
            if(script.objects().size()>32)throw new IOException("技能粒子对象超过 32 个");
            long emitFor=emitter.emitFor()>0?emitter.emitFor():script.createTime()>0?Math.min(5000,script.createTime()):1000;
            long tail=SkillParticleEmitter.tail(script);
            script=script.previewEmission(emitFor,emitter.frequency()).scaleMovement(emitter.movementScale());
            var animations=new ArrayList<AnimationTimeline>();
            var visualBounds=new HashMap<ParticleImage,ParticleImageBounds>();
            ResourceScript.Animation override=null;
            if(!emitter.animationOverride().isEmpty()){
                String animationText=lookup.read(emitter.animationOverride());
                if(animationText==null)throw new IOException("找不到 ANI："+emitter.animationOverride());
                override=ResourceScript.animation(animationText);
            }
            for(var ref:script.objects()){
                try {
                    var animation=override==null?particleAnimation(emitter.path(),ref,script.passive(),lookup):override;
                    if(emitter.decorative()){
                        var frames=animation.frames().stream().map(ResourceScript.Frame::withoutAttack).toList();
                        animation=new ResourceScript.Animation(animation.loop(),frames,animation.warnings());
                    }
                    if(emitter.scale()!=1){
                        var scaled=animation.frames().stream().map(frame->frame.scaled(emitter.scale())).toList();
                        animation=new ResourceScript.Animation(animation.loop(),scaled,animation.warnings());
                    }
                    if(animation.frames().size()>256)throw new IOException("粒子动画超过 256 帧");
                    for(var frame:animation.frames()){
                        if(frame.image().isEmpty()||frame.a()==0&&!frame.interpolation())continue;
                        var key=new ParticleImage(frame.image(),frame.index());
                        if(visualBounds.containsKey(key))continue;
                        var sprite=sprites.sprite(frame.image(),frame.index());
                        var visible=visibleBounds(sprite.image());
                        if(visible!=null)visualBounds.put(key,new ParticleImageBounds(sprite.canvasWidth(),sprite.canvasHeight(),
                                sprite.x(),sprite.y(),visible));
                    }
                    animations.add(new AnimationTimeline(animation));
                }catch(IOException|IllegalArgumentException failure){
                    warn(emitter.label()+"："+failure.getMessage());
                    animations.add(new AnimationTimeline(new ResourceScript.Animation(false,List.of(),List.of())));
                }
            }
            if(visualBounds.isEmpty()&&animations.stream().noneMatch(AnimationTimeline::hasAttackBoxes))
                throw new IOException("PTL 没有可绘制的对象");
            var pos=emitter.overridePosition()?new ParticleScript.Vec(0,0,0):script.position();
            var simulation=new ParticleTimeline(script,emitter.path().hashCode(),0);
            long active=Math.min(emitFor+tail,emitter.maxDuration());
            boolean reverse=emitter.reverseFacing();
            double x=emitter.x()+(reverse?-pos.x():pos.x()),y=emitter.y()+pos.y(),z=emitter.z()+pos.z();
            particles.add(new ParticleTrack(emitter.path(),simulation,List.copyOf(animations),emitter.label(),
                    emitter.start(),emitter.start()+active,x,y,z,emitter.clipX(),reverse,emitter.sourceMotion()));
            duration=Math.max(duration,Math.min(60000,emitter.start()+active));
            for(int i=0;i<=20;i++){
                long local=active*i/20;
                var sample=simulation.sample(local,x,y,z,reverse);
                for(var particle:sample.particles()){
                    var frame=animations.get(particle.object()).sample(particle.age(),false);
                    if(!visibleParticle(frame)||particle.alpha()<=0)continue;
                    var image=visualBounds.get(new ParticleImage(frame.image(),frame.index()));
                    if(image==null)continue;
                    var transform=PreviewRenderer.transform(frame,image.width(),image.height(),image.trimX(),image.trimY());
                    var measured=transform.createTransformedShape(image.visible()).getBounds2D();
                    var source=emitter.sourceMotion();
                    long birth=emitter.start()+local-particle.age();
                    double particleX=particle.x()+(source==null?0:source.offsetAt(birth));
                    double particleY=particle.y()-particle.z()+(source==null?0:source.offsetYAt(birth));
                    double left=(reverse?-measured.getMaxX():measured.getMinX())+particleX;
                    double right=(reverse?-measured.getMinX():measured.getMaxX())+particleX;
                    if(!Double.isNaN(emitter.clipX()))left=Math.max(left,emitter.clipX());
                    if(right>left)extendBounds(new Rectangle2D.Double(left,measured.getY()+particleY,
                            right-left,measured.getHeight()));
                }
            }
            for(String warning:script.warnings())warn(emitter.path()+"："+warning);
        }catch(IOException|IllegalArgumentException failure){warn(emitter.label()+"："+failure.getMessage());}
    }

    private static boolean visibleParticle(ResourceScript.Frame frame){
        return frame!=null&&!frame.image().isEmpty()&&frame.a()>0&&frame.scaleX()!=0&&frame.scaleY()!=0;
    }

    private ResourceScript.Animation particleAnimation(String owner,ParticleScript.ObjectRef ref,boolean passive,GameScript.Lookup lookup) throws IOException {
        String reference=ref.path();
        if(passive){
            String object=GameEffectRecipes.passiveObject(lookup,ref.index());
            if(object==null)throw new IOException("找不到粒子对象 #"+ref.index());
            owner=object;
            reference=GameScript.value(GameScript.fields(lookup.read(object)),"basic motion");
        }
        if(reference.toLowerCase(Locale.ROOT).endsWith(".img")&&ref.index()>=0)
            return new ResourceScript.Animation(false,List.of(ResourceScript.Frame.still(reference,ref.index())),List.of());
        String path=GameScript.resolve(owner,reference,lookup);
        if(path==null||!path.endsWith(".ani"))throw new IOException("找不到粒子动画："+reference);
        var animation=ResourceScript.animation(lookup.read(path));
        return act1?Act1GunnerAdvancedFire.particleVisual(path,
                Act1GunnerSupplement.particleVisual(owner,path,Act1GunnerCast.particleVisual(owner,path,animation))):animation;
    }

    private PreviewRenderer.Prepared prepare(ResourceScript.Frame frame) throws IOException {
        int rgba = (frame.r() << 24) | (frame.g() << 16) | (frame.b() << 8) | frame.a();
        var key = new PaintedSprite(frame.image(), frame.index(), rgba, frame.effect());
        return prepare(frame,key,false);
    }

    private PreviewRenderer.Prepared prepareOutline(ResourceScript.Frame frame, int color) throws IOException {
        int alpha=(color>>>24)*frame.a()/255;
        return prepare(frame,new PaintedSprite(frame.image(),frame.index(),(alpha<<24)|(color&0x00ffffff),"armor outline"),true);
    }

    private PreviewRenderer.Prepared prepare(ResourceScript.Frame frame, PaintedSprite key, boolean outline) throws IOException {
        var cached = pixels.get(key);
        if (cached != null) {
            // Several particles can share tinted pixels while each interpolates its own transform.
            return cached.frame().equals(frame) ? cached.prepared() : cached.prepared().positioned(frame);
        }
        var sprite=sprites.sprite(frame.image(),frame.index());
        var prepared=outline?PreviewRenderer.armorOutline(frame,sprite,key.rgba()):PreviewRenderer.prepare(frame,sprite);
        long bytes=(long)prepared.image().getWidth()*prepared.image().getHeight()*4;
        while(!pixels.isEmpty()&&(cacheBytes+bytes>CACHE_LIMIT||pixels.size()>=256)){
            var removed=pixels.remove(pixels.keySet().iterator().next()).prepared();
            cacheBytes-=(long)removed.image().getWidth()*removed.image().getHeight()*4;
        }
        if(bytes<=CACHE_LIMIT){pixels.put(key,new CachedSprite(frame,prepared));cacheBytes+=bytes;}
        return prepared;
    }
    public Rendered render(long milliseconds,boolean mirrored) throws IOException {
        return render(milliseconds,mirrored,false);
    }
    private Act1SkillSequence.Pose primaryPoseAt(long time){
        for(int i=sequencePoses.size()-1;i>=0;i--){
            var pose=sequencePoses.get(i);
            if(!pose.secondary()&&time>=pose.start())return pose;
        }
        return null;
    }
    public Rendered render(long milliseconds,boolean mirrored,boolean repeatSequence) throws IOException {
        return render(milliseconds,mirrored,repeatSequence,false);
    }

    public Rendered render(long milliseconds,boolean mirrored,boolean repeatSequence,boolean debug) throws IOException {
        long time=Math.max(0,milliseconds);var draws=new ArrayList<Draw>();
        var volumes=debug?new ArrayList<CombatDebug.Volume>():null;
        long sequenceTime=repeatSequence&&wholeSequence?time%duration:time;
        if(volumes!=null)for(var area:attackAreas)if(area.activeAt(sequenceTime)&&volumes.size()<256){
            var range=area.rangeAt(sequenceTime);
            volumes.add(new CombatDebug.Volume(CombatDebug.Kind.ATTACK,range.envelope(),
                    mirrored?-area.x():area.x(),area.y(),mirrored,area.label(),range,area.pulseAt(sequenceTime)));
        }
        var outlines=armorPossible?new IdentityHashMap<ActorPose,OutlineGroup>():null;
        var authoredArmor=armorPossible?new IdentityHashMap<ActorPose,Long>():null;
        boolean runtimeActive=runtimeArmor!=null&&runtimeArmor.contains(sequenceTime);
        for(var track:orderedTracks(sequenceTime)) {
            boolean title=track.label().equals("title name");
            long trackTime=title?time:sequenceTime;
            if(trackTime<track.start()||trackTime>=track.end())continue;
            long local=trackTime-track.start(),period=track.visualStyle().loopPeriod();
            long frameTime=period>0?local%period:local;
            boolean loopTrack=track.loop()||repeatSequence&&(!wholeSequence||title);
            var frame=track.timeline().sample(frameTime,loopTrack);
            if(frame==null||frame.image().isEmpty()&&!debug)continue;
            try {
                boolean faceLeft=mirrored&&!title;
                double x=track.x(),clipX=Double.NaN;
                var castClip=track.castClip();
                if(castClip!=null){
                    if(castClip.movement()!=null)x+=castClip.movement().offset(trackTime);
                    clipX=castClip.clipX();
                }
                var actorPose=title?primaryPoseAt(sequenceTime):null;
                var movement=actorPose==null?track.motion():actorPose.motion();
                double motion=movement==null?0:movement.offsetAt(sequenceTime);
                double drawX=(faceLeft?-x:x)+(mirrored?-motion:motion);
                double drawY=track.y()+(movement==null?0:movement.offsetYAt(sequenceTime));
                var style=track.visualStyle().sample(local);
                boolean left=faceLeft!=track.visualStyle().reverseFacing();
                if(volumes!=null)collectBoxes(volumes,track,frame,frameTime,loopTrack,drawX,drawY,left,style.scale());
                if(frame.image().isEmpty())continue;
                double opacity=style.opacity();
                if(actorPose!=null&&!actorPose.keyframes().isEmpty()){
                    var actorStyle=new Act1SkillSequence.VisualStyle(null,0,actorPose.keyframes());
                    opacity*=actorStyle.sample(sequenceTime-actorPose.start()).opacity();
                }
                if(opacity<=0||style.scale()<=0)continue;
                var prepared=prepare(frame);
                if(style.scale()!=1)prepared=prepared.positioned(frame.scaled(style.scale()));
                boolean flip=left&&track.mirror();
                var actor=track.combat().actor();
                if(outlines!=null&&actor!=null&&!actor.secondary&&actor.body!=null){
                    long elapsed=authoredArmor.computeIfAbsent(actor,pose->pose.body.authoredArmorElapsed(local,loopTrack));
                    if(runtimeActive||elapsed>=0){
                        double expansion=runtimeActive?runtimeArmor.expansionAt(sequenceTime):0;
                        expansion=Math.max(expansion,Act1SuperArmor.outlineExpansion(elapsed));
                        var outlineFrame=track.combat().body()?frame.expanded(expansion):frame;
                        if(style.scale()!=1)outlineFrame=outlineFrame.scaled(style.scale());
                        var outline=prepareOutline(outlineFrame,Act1SuperArmor.outlineColor(time));
                        var group=outlines.get(actor);
                        if(group==null){
                            group=new OutlineGroup(draws.size(),new ArrayList<>());
                            outlines.put(actor,group);
                        }
                        group.outlines().add(new Draw(outline,drawX,drawY,flip,"霸体轮廓 · "+track.label(),opacity,Double.NaN,true));
                    }
                }
                draws.add(new Draw(prepared,drawX,drawY,flip,track.label(),opacity,faceLeft?-clipX:clipX));
            }catch(IOException|IllegalArgumentException failure){warn(track.path()+"："+failure.getMessage());}
        }
        for(var track:particles){
            long local=sequenceTime-track.start();
            long length=track.end()-track.start();
            if(local<0)continue;
            long cycle=repeatSequence&&wholeSequence?time/duration:0;
            if(repeatSequence&&!wholeSequence){
                cycle=local/length;
                local%=length;
            }
            if(local>=length)continue;
            long variation=act1&&track.path().equals(Act1GunnerCast.ORDINARY_FLAME)?cycle:0;
            boolean faceLeft=mirrored!=track.reverseFacing();
            var sample=track.timeline().sample(local,mirrored?-track.x():track.x(),track.y(),track.z(),faceLeft,variation);
            if(sample.limited())warn(track.path()+"：活动粒子超出预览上限");
            for(var particle:sample.particles()){
                if(draws.size()>=512){warn("当前帧的粒子绘制超过 512 层");break;}
                if(particle.alpha()<=0&&volumes==null)continue;
                var frame=track.animations().get(particle.object()).sample(particle.age(),false);
                boolean spriteVisible=particle.alpha()>0&&visibleParticle(frame);
                if(!spriteVisible&&(volumes==null||frame==null||frame.combat().attack().isEmpty()))continue;
                try {
                    var source=track.sourceMotion();
                    long birth=track.start()+local-particle.age();
                    double x=particle.x()+(source==null?0:(mirrored?-1:1)*source.offsetAt(birth));
                    double y=particle.y()-particle.z()+(source==null?0:source.offsetYAt(birth));
                    if(volumes!=null)for(var box:frame.combat().attack())
                        addVolume(volumes,CombatDebug.Kind.ATTACK,box,x,y,faceLeft,track.label());
                    if(!spriteVisible)continue;
                    draws.add(new Draw(prepare(frame),x,y,faceLeft,track.label(),particle.alpha(),
                            mirrored?-track.clipX():track.clipX()));
                }catch(IOException|IllegalArgumentException failure){warn(track.path()+"："+failure.getMessage());}
            }
        }
        if(outlines!=null&&!outlines.isEmpty()){
            var groups=new ArrayList<>(outlines.values());
            groups.sort(Comparator.comparingInt(OutlineGroup::drawIndex).reversed());
            for(var group:groups)draws.addAll(group.drawIndex(),group.outlines());
        }
        Rectangle2D viewport=debug&&attackAreaBounds!=null?bounds.createUnion(attackAreaBounds):bounds;
        Rectangle2D view=mirrored?new Rectangle2D.Double(-viewport.getMaxX(),viewport.getY(),viewport.getWidth(),viewport.getHeight()):viewport;
        return new Rendered(List.copyOf(draws),(Rectangle2D)view.clone(),time,warnings(),
                volumes==null?List.of():List.copyOf(volumes));
    }

    private void addAttackAreas(List<Act1SkillSequence.AttackArea> areas) {
        attackAreas=List.copyOf(areas);
        for(var area:attackAreas){
            var b=area.range().envelope();
            var projected=new Rectangle2D.Double(area.x()+b.x(),area.y()+b.y()-b.height(),b.width(),b.depth()+b.height());
            attackAreaBounds=attackAreaBounds==null?projected:attackAreaBounds.createUnion(projected);
            duration=Math.max(duration,area.end());
        }
    }

    private static void collectBoxes(List<CombatDebug.Volume> volumes, Track track, ResourceScript.Frame frame,
                                     long local, boolean loop, double x, double y, boolean left, double keyframeScale) {
        var source=track.combat();
        if(source.actor()!=null&&source.actor().secondary)return;
        boolean enemy=source.actor()==null&&!source.alliedAttack()&&track.path().startsWith("monster/");
        double scale=track.combatScale()*keyframeScale;
        if(source.body()||enemy){
            var kind=enemy?CombatDebug.Kind.ENEMY_HURT:CombatDebug.Kind.PLAYER_HURT;
            for(var box:frame.combat().hurt())addVolume(volumes,kind,box.scaled(scale),x,y,left,track.label());
        }
        if(enemy||!source.attack())return;
        var boxes=frame.combat().attack();
        for(int i=0;i<boxes.size();i++){
            var box=boxes.get(i);
            // The laser's object adjusts the first box of each beam frame from index 2 in 0x5A8770.
            if(i==0&&source.range()>0&&track.timeline().frameAt(local,loop)>=2)
                box=new CombatFrame.Box(box.x(),box.y(),box.z(),source.range()-box.x(),box.depth(),box.height());
            box=box.scaled(scale);
            if(track.castClip()!=null)box=track.castClip().clipAttack(box,x,left);
            addVolume(volumes,CombatDebug.Kind.ATTACK,box,x,y,left,track.label());
        }
    }

    private static void addVolume(List<CombatDebug.Volume> volumes, CombatDebug.Kind kind, CombatFrame.Box box,
                                  double x, double y, boolean left, String label) {
        if(volumes.size()<256&&box.width()>0&&box.depth()>0&&box.height()>0)
            volumes.add(new CombatDebug.Volume(kind,box,x,y,left,label));
    }

    private static boolean usesGroundDepth(Track track) {
        return track.motion()!=null&&track.motion().sortsByGroundDepth();
    }

    private List<Track> orderedTracks(long time) {
        if (!sortByGroundDepth) return tracks;
        var depths = new IdentityHashMap<SkillMotion, Double>();
        for (var track : tracks) {
            if (time >= track.start() && time < track.end() && usesGroundDepth(track)) {
                depths.computeIfAbsent(track.motion(), motion -> motion.groundDepthAt(time));
            }
        }
        if (depths.isEmpty()) return tracks;
        var ordered = new ArrayList<>(tracks);
        ordered.sort(Comparator.comparingDouble((Track track) -> {
            var depth = depths.get(track.motion());
            if (depth == null) return track.layer();
            int plane = depth < 0 ? WORLD_BEHIND_ACTOR : WORLD_IN_FRONT_OF_ACTOR;
            return plane + Math.max(-.4, Math.min(.4, depth / 10_000));
        }).thenComparingInt(Track::layer));
        return ordered;
    }
    private void warn(String warning){if(warnings.size()<256&&!warnings.contains(warning))warnings.add(warning);}
    public long duration(){return duration;}
    public boolean usingSequence(){return wholeSequence;}
    public String unavailableReason(){return unavailableReason;}
    public Rectangle2D actorBounds(){return (Rectangle2D)actorBounds.clone();}
    public GameItemInfo info(){return info;}
    public CharacterAppearance.Model appearance(){return appearance;}
    public List<GameEffectRecipes.Effect> effects(){return effects;}
    public List<String> warnings(){return List.copyOf(warnings);}
}
