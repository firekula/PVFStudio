package studio.pvf.preview;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;

/** Immutable actor tracks plus bounded worker-confined pixel preparation. Time is absolute. */
public final class GamePreview {
    public record Options(String job,String action,Map<String,String> outfit,int level,boolean act1,String effect) {
        public Options {outfit=Map.copyOf(outfit);level=Math.max(1,Math.min(1000,level));}
        public static Options defaults(){return new Options("gunner","waiting motion",Map.of(),1,true,"");}
        public Options withEffect(String selected){return new Options(job,action,outfit,level,act1,selected);}
    }
    public record Draw(PreviewRenderer.Prepared prepared,double x,double y,boolean mirrored,String label,double opacity) {}
    public record Rendered(List<Draw> draws,Rectangle2D bounds,long milliseconds,List<String> warnings) {}
    private record Track(String path,AnimationTimeline timeline,int layer,String label,long start,long end,double x,double y,boolean loop) {}
    private record ParticleTrack(String path,ParticleTimeline timeline,List<AnimationTimeline> animations,
                                 String label,long start,long end,double x,double y,double z) {}
    private final List<Track> tracks=new ArrayList<>();
    private final List<ParticleTrack> particles=new ArrayList<>();
    private final List<String> warnings=new ArrayList<>();
    private final LinkedHashMap<ResourceScript.Frame,PreviewRenderer.Prepared> pixels=new LinkedHashMap<>(32,.75f,true);
    private final ScenePreview.SpriteLookup sprites;
    private final CharacterAppearance.Model appearance;
    private final GameItemInfo info;
    private final List<GameEffectRecipes.Effect> effects;
    private final boolean act1;
    private Rectangle2D bounds;
    private Rectangle2D actorBounds;
    private long cacheBytes,duration;
    private boolean wholeSequence;
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
    public static GamePreview load(String path,String text,GameScript.Lookup raw,Set<String> paths,ScenePreview.SpriteLookup sprites,Options options) throws IOException {
        GameScript.Lookup bounded=GameScript.boundedLookup(raw,4096,32L*1024*1024);
        GameScript.Lookup lookup=p->p.equals(path)?text:bounded.read(p);
        boolean skill=path.endsWith(".skl");
        boolean actorOnly=options.effect().equals("actor");
        String passive=path.equals("skill/gunner/laserrifle.skl")?lookup.read("skill/gunner/laserriflecharge.skl"):null;
        var laser=actorOnly?null:GameEffectRecipes.laser(path,text,options.act1(),options.effect().equals("laser:charged"),passive);
        String job=CharacterAppearance.inferJob(path,text,options.job());
        String character="character/"+job+"/"+job+".chr";
        var available=CharacterAppearance.motions(Optional.ofNullable(lookup.read(character)).orElse(""));
        var cast=options.effect().isEmpty()?Act1GunnerCast.find(path,text,lookup,available,options.level(),options.act1()):null;
        String matched="";
        if(skill&&!actorOnly&&options.act1()){
            String stem=path.substring(path.lastIndexOf('/')+1,path.length()-4);
            matched=GameEffectRecipes.motionFor(available,stem);
            if(!matched.isEmpty()&&GameScript.resolve(character,available.get(matched),lookup)==null)matched="";
        }
        String action=options.action();
        if(laser!=null)action=available.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase("Animation/SubWeaponFrontRapid1.ani")).map(Map.Entry::getKey).findFirst().orElse("waiting motion");
        else if(cast!=null)action=cast.action();
        else if(!matched.isEmpty())action=matched;
        var appearance=CharacterAppearance.load(path,text,lookup,paths,job,action,options.outfit());
        var choices=new ArrayList<>(GameEffectRecipes.effects(path,text,lookup,options.act1()));
        if(GameEffectRecipes.laser(path,text,options.act1(),false)!=null){choices.add(new GameEffectRecipes.Effect("actor","仅角色动作（不施放）","",path,"动作检查"));choices.add(new GameEffectRecipes.Effect("laser:normal","激光炮 · 普通施放组合","",path,"原生资源组合"));if(GameEffectRecipes.laser(path,text,options.act1(),true,passive)!=null)choices.add(new GameEffectRecipes.Effect("laser:charged","激光炮 · 满蓄力（被动 Lv.1）","",path,"蓄力参数来自同包被动技能 Lv.1"));}
        var model=new GamePreview(sprites,appearance,GameItemInfo.read(path,text,lookup,options.level()),List.copyOf(choices),options.act1());
        boolean showActor=!skill||actorOnly||laser!=null||cast!=null||!matched.isEmpty();
        long transition=laser!=null?laser.fireAt():cast!=null?cast.changeAt():Long.MAX_VALUE;
        boolean motionOnly=skill&&!matched.isEmpty()&&laser==null&&cast==null;
        if(showActor)model.addActor(appearance,lookup,0,transition,true,motionOnly?300:0,cast==null?-1:cast.firstFrameDelay());
        model.actorBounds=model.bounds==null?null:(Rectangle2D)model.bounds.clone();
        if(laser!=null)model.addLaser(path,text,lookup,paths,options,laser,available);
        if(cast!=null)model.addCast(path,text,lookup,paths,options,cast);
        if(appearance.parts().stream().noneMatch(p->p.owner().equals(path))&&path.endsWith(".equ"))model.warn(options.outfit().containsKey(CharacterAppearance.slot(text))?"当前部位已隐藏或由搭配替换":"配置未定义独立穿戴外观；当前显示参考角色");
        String selectedEffect=options.effect();
        if(skill&&selectedEffect.isEmpty()&&laser==null&&cast==null){
            selectedEffect=choices.stream().filter(e->!e.path().isEmpty()).map(GameEffectRecipes.Effect::key).findFirst().orElse("");
        }
        for(var effect:model.effects)if(effect.key().equals(selectedEffect)) {
            model.warn(effect.note());
            if(effect.path().endsWith(".ani"))model.add(effect.path(),lookup,List.of(0,0),5000,effect.label(),0,Long.MAX_VALUE,effect.x(),effect.y(),false);
            else if(effect.path().endsWith(".ptl"))model.addParticles(
                    new Act1GunnerCast.Emitter(effect.path(),effect.label(),0,0,0,effect.x(),-effect.y()),lookup);
        }
        model.wholeSequence=skill&&!actorOnly&&(!model.tracks.isEmpty()||!model.particles.isEmpty());
        if(motionOnly)model.warn("当前播放 CHR 中唯一同名动作，尚未关联技能特效时序");
        if(skill&&!actorOnly&&model.tracks.isEmpty()&&model.particles.isEmpty()){
            String type=GameScript.flag(GameScript.value(GameScript.fields(text),"type"));
            model.unavailableReason=type.equals("passive")?"此被动技能没有可关联的独立动画":"未找到可播放的技能动画";
            model.warn(model.unavailableReason+"；可在效果下拉框选择“仅角色动作”");
        }
        model.tracks.sort(Comparator.comparingInt(Track::layer));
        model.duration=Math.max(1,model.duration);
        if(model.bounds==null)model.bounds=new Rectangle2D.Double(-36,-160,72,176);
        if(model.actorBounds==null)model.actorBounds=(Rectangle2D)model.bounds.clone();
        return model;
    }

    private void addActor(CharacterAppearance.Model actor,GameScript.Lookup lookup,long start,long end,boolean title,int holdDelay,int firstFrameDelay){
        List<Integer> poseDelays=act1?actorDelays(actor.bodyAnimation(),lookup,holdDelay,firstFrameDelay):List.of();
        for(var part:actor.parts()){
            boolean isTitle=part.slot().equals("title name");
            if(isTitle&&!title)continue;
            add(part.animation(),lookup,part.variation(),part.layer(),part.slot(),start,isTitle?Long.MAX_VALUE:end,
                    0,0,false,isTitle?0:holdDelay,isTitle?List.of():poseDelays);
        }
    }

    /** ACT1 copies body frame delays into the overlapping frames of equipped layers (0x493B70). */
    private List<Integer> actorDelays(String body,GameScript.Lookup lookup,int holdDelay,int firstFrameDelay){
        if(body==null)return List.of();
        try {
            String text=lookup.read(body);
            if(text==null)return List.of();
            var frames=ResourceScript.animation(text).frames();
            var delays=new ArrayList<Integer>(frames.size());
            for(int i=0;i<frames.size();i++){
                int delay=frames.get(i).delay();
                if(i==0&&firstFrameDelay>=0)delay=firstFrameDelay;
                else if(holdDelay>0&&delay>=10000)delay=holdDelay;
                delays.add(delay);
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
            addActor(fire,lookup,cast.changeAt(),cast.end(),false,0,-1);
        }
        for(var clip:cast.clips())add(clip.path(),lookup,clip.variations(),clip.layer(),clip.label(),
                clip.start(),clip.end(),clip.x(),clip.y(),false,clip.holdDelay());
        if(cast.emitter()!=null)addParticles(cast.emitter(),lookup);
        warn(cast.note());
    }
    private void addLaser(String path,String text,GameScript.Lookup lookup,Set<String> paths,Options options,GameEffectRecipes.Laser laser,Map<String,String> actions) throws IOException {
        String firing=actions.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase("Animation/SubWeaponFrontRapid2.ani")).map(Map.Entry::getKey).findFirst().orElse("waiting motion");
        var fire=CharacterAppearance.load(path,text,lookup,paths,"gunner",firing,options.outfit());
        addActor(fire,lookup,laser.fireAt(),Long.MAX_VALUE,false,0,-1);
        String local="character/gunner/effect/animation/subweapon/laserrifleeffect/";
        String sub="character/gunner/effect/animation/subweapon/";
        for(String suffix:List.of("a","b")) {
            int layer=suffix.equals("a")?2151:651;
            add(sub+"subweaponfrontrapid1"+suffix+".ani",lookup,List.of(2,0),layer,"激光炮枪体",0,laser.fireAt(),0,0,false);
            add(sub+"subweaponfrontrapid2"+suffix+".ani",lookup,List.of(2,0),layer,"激光炮枪体",laser.fireAt(),-1,0,0,false);
        }
        if(bounds!=null)actorBounds=(Rectangle2D)bounds.clone();
        String obj="passiveobject/character/gunner/animation/laserrifle/";
        String prefix=laser.charged()?"charged":"";
        if(laser.charged())add(local+"chargingeffect.ani",lookup,List.of(),2500,"蓄力光效",0,laser.fireAt(),0,0,false);
        for(String name:laser.charged()?List.of("firechargeeffect.ani","firechargespark.ani","firering.ani"):List.of("fireonceeffect.ani","fireoncespark.ani","firering.ani"))add(local+name,lookup,List.of(),2700,name,laser.fireAt(),-1,0,0,false);
        add(obj+prefix+"laserfire.ani",lookup,List.of(),3000,"激光起始",laser.fireAt(),-1,150,-85,false);
        for(int tile=0;tile<laser.tileCount();tile++)add(obj+prefix+"lasertile.ani",lookup,List.of(),3000,"激光光束",laser.fireAt(),-1,150+tile*50,-85,false);
        add(obj+prefix+"laserend.ani",lookup,List.of(),3000,"激光末端",laser.fireAt(),-1,150+laser.endOffset(),-85,false);
        warn("激光炮按基础配置合成施放；未模拟角色攻速修正、伤害和命中");
        if(laser.charged())warn("满蓄力按被动技能 Lv.1 演示；不模拟手动按键释放");
    }
    private void add(String path,GameScript.Lookup lookup,List<Integer> variants,int layer,String label,long start,long end,double x,double y,boolean loop) {
        add(path,lookup,variants,layer,label,start,end,x,y,loop,0);
    }

    private void add(String path,GameScript.Lookup lookup,List<Integer> variants,int layer,String label,long start,long end,double x,double y,boolean loop,int holdDelay) {
        add(path,lookup,variants,layer,label,start,end,x,y,loop,holdDelay,List.of());
    }

    private void add(String path,GameScript.Lookup lookup,List<Integer> variants,int layer,String label,long start,long end,double x,double y,boolean loop,int holdDelay,List<Integer> poseDelays) {
        try {
            if(end>=0&&end<=start)return;
            if(tracks.size()>=256)throw new IOException("组合轨道超过 256 个");String text=lookup.read(path);if(text==null)throw new IOException("找不到 ANI："+path);
            var parsed=ResourceScript.animation(text);var frames=new ArrayList<ResourceScript.Frame>();
            int[] parameters=variants.stream().mapToInt(Integer::intValue).toArray();
            for(var frame:parsed.frames()){
                if(!frame.image().isEmpty())frame=frame.withImage(NpkResources.substitute(frame.image(),parameters));
                if(frames.size()<poseDelays.size())frame=frame.withDelay(poseDelays.get(frames.size()));
                else if(holdDelay>0&&frame.delay()>=10000)frame=frame.withDelay(holdDelay);
                frames.add(frame);
            }
            var animation=new ResourceScript.Animation(parsed.loop(),List.copyOf(frames),parsed.warnings());var timeline=new AnimationTimeline(animation);
            if(frames.isEmpty())throw new IOException("空动画 "+path);
            Rectangle2D partBounds=null;
            for(var frame:frames)if(!frame.image().isEmpty()){
                var sprite=sprites.sprite(frame.image(),frame.index());var visible=visibleBounds(sprite.image());if(visible==null)continue;
                var transform=PreviewRenderer.transform(frame,sprite.canvasWidth(),sprite.canvasHeight(),sprite.x(),sprite.y());
                var b=transform.createTransformedShape(visible).getBounds2D();partBounds=partBounds==null?b:partBounds.createUnion(b);
            }
            if(label.equals("title name")&&partBounds!=null){x=-partBounds.getCenterX();y=(bounds==null?-140:bounds.getMinY())-8-partBounds.getMaxY();warn("称号按角色可见边界上方对齐；锚点尚待同版本游戏对照");}
            if(partBounds!=null){var moved=new Rectangle2D.Double(partBounds.getX()+x,partBounds.getY()+y,partBounds.getWidth(),partBounds.getHeight());bounds=bounds==null?moved:bounds.createUnion(moved);}
            long stop=end<0?start+timeline.duration():end;
            tracks.add(new Track(path,timeline,layer,label,start,stop,x,y,loop));
            duration=Math.max(duration,Math.min(60000,stop==Long.MAX_VALUE?start+timeline.duration():stop));
            for(String warning:parsed.warnings())warn(path+"："+warning);
        }catch(IOException|IllegalArgumentException failure){warn(label+"："+failure.getMessage());}
    }
    private static Rectangle2D visibleBounds(java.awt.image.BufferedImage image) {
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        int[] row=new int[image.getWidth()];for(int y=0;y<image.getHeight();y++){image.getRGB(0,y,image.getWidth(),1,row,0,image.getWidth());for(int x=0;x<row.length;x++)if((row[x]>>>24)!=0){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=y;}}
        return right<left?null:new Rectangle2D.Double(left,top,right-left+1,bottom-top+1);
    }

    private void addParticles(Act1GunnerCast.Emitter emitter,GameScript.Lookup lookup) {
        try {
            if(particles.size()>=8)throw new IOException("技能粒子源超过 8 个");
            String text=lookup.read(emitter.path());
            if(text==null)throw new IOException("找不到 PTL："+emitter.path());
            var script=ParticleScript.parse(text);
            if(script.objects().size()>32)throw new IOException("技能粒子对象超过 32 个");
            long emitFor=emitter.emitFor()>0?emitter.emitFor():script.createTime()>0?Math.min(5000,script.createTime()):1000;
            long tail=script.life()==0?800:Math.min(7000,(long)Math.ceil(script.life()*(1+script.random()/200)));
            script=script.previewEmission(emitFor,emitter.frequency());
            var animations=new ArrayList<AnimationTimeline>();
            var visualBounds=new ArrayList<Rectangle2D>();
            for(var ref:script.objects()){
                try {
                    var animation=particleAnimation(emitter.path(),ref,script.passive(),lookup);
                    if(animation.frames().size()>256)throw new IOException("粒子动画超过 256 帧");
                    Rectangle2D measured=null;
                    for(var frame:animation.frames()){
                        if(frame.image().isEmpty()||frame.a()==0)continue;
                        var sprite=sprites.sprite(frame.image(),frame.index());
                        var visible=visibleBounds(sprite.image());
                        if(visible==null)continue;
                        var transform=PreviewRenderer.transform(frame,sprite.canvasWidth(),sprite.canvasHeight(),sprite.x(),sprite.y());
                        var box=transform.createTransformedShape(visible).getBounds2D();
                        measured=measured==null?box:measured.createUnion(box);
                    }
                    animations.add(new AnimationTimeline(animation));
                    visualBounds.add(measured);
                }catch(IOException|IllegalArgumentException failure){
                    warn(emitter.label()+"："+failure.getMessage());
                    animations.add(new AnimationTimeline(new ResourceScript.Animation(false,List.of(),List.of())));
                    visualBounds.add(null);
                }
            }
            if(visualBounds.stream().allMatch(Objects::isNull))throw new IOException("PTL 没有可绘制的对象");
            var pos=script.position();
            var simulation=new ParticleTimeline(script,emitter.path().hashCode(),0);
            long active=emitFor+tail;
            double x=emitter.x()+pos.x(),y=pos.y(),z=emitter.z()+pos.z();
            particles.add(new ParticleTrack(emitter.path(),simulation,List.copyOf(animations),emitter.label(),
                    emitter.start(),emitter.start()+active,x,y,z));
            duration=Math.max(duration,Math.min(60000,emitter.start()+active));
            for(int i=0;i<=20;i++){
                var sample=simulation.sample(active*i/20,x,y,z,false);
                for(var particle:sample.particles()){
                    var measured=visualBounds.get(particle.object());
                    if(measured==null||particle.alpha()<=0)continue;
                    var box=new Rectangle2D.Double(measured.getX()+particle.x(),measured.getY()+particle.y()-particle.z(),
                            measured.getWidth(),measured.getHeight());
                    bounds=bounds==null?box:bounds.createUnion(box);
                }
            }
            for(String warning:script.warnings())warn(emitter.path()+"："+warning);
        }catch(IOException|IllegalArgumentException failure){warn(emitter.label()+"："+failure.getMessage());}
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
        return act1?Act1GunnerCast.particleVisual(owner,path,animation):animation;
    }

    private PreviewRenderer.Prepared prepare(ResourceScript.Frame frame) throws IOException {
        var prepared=pixels.get(frame);
        if(prepared!=null)return prepared;
        prepared=PreviewRenderer.prepare(frame,sprites.sprite(frame.image(),frame.index()));
        long bytes=(long)prepared.image().getWidth()*prepared.image().getHeight()*4;
        while(!pixels.isEmpty()&&(cacheBytes+bytes>CACHE_LIMIT||pixels.size()>=128)){
            var removed=pixels.remove(pixels.keySet().iterator().next());
            cacheBytes-=(long)removed.image().getWidth()*removed.image().getHeight()*4;
        }
        if(bytes<=CACHE_LIMIT){pixels.put(frame,prepared);cacheBytes+=bytes;}
        return prepared;
    }
    public Rendered render(long milliseconds,boolean mirrored) throws IOException {
        return render(milliseconds,mirrored,false);
    }
    public Rendered render(long milliseconds,boolean mirrored,boolean repeatSequence) throws IOException {
        long time=Math.max(0,milliseconds);var draws=new ArrayList<Draw>();
        for(var track:tracks) {
            long trackTime=repeatSequence&&wholeSequence&&!track.label().equals("title name")?time%duration:time;
            if(trackTime<track.start()||trackTime>=track.end())continue;
            long local=trackTime-track.start();var frame=track.timeline().sample(local,track.loop()||repeatSequence&&(!wholeSequence||track.label().equals("title name")));if(frame==null||frame.image().isEmpty())continue;
            try {
                boolean flip=mirrored&&!track.label().equals("title name");
                draws.add(new Draw(prepare(frame),flip?-track.x():track.x(),track.y(),flip,track.label(),1));
            }catch(IOException|IllegalArgumentException failure){warn(track.path()+"："+failure.getMessage());}
        }
        long particleTime=repeatSequence&&wholeSequence?time%duration:time;
        for(var track:particles){
            long local=particleTime-track.start();
            long length=track.end()-track.start();
            if(local<0)continue;
            long cycle=repeatSequence&&wholeSequence?time/duration:0;
            if(repeatSequence&&!wholeSequence){
                cycle=local/length;
                local%=length;
            }
            if(local>=length)continue;
            long variation=act1&&track.path().equals(Act1GunnerCast.ORDINARY_FLAME)?cycle:0;
            var sample=track.timeline().sample(local,mirrored?-track.x():track.x(),track.y(),track.z(),mirrored,variation);
            if(sample.limited())warn(track.path()+"：活动粒子超出预览上限");
            for(var particle:sample.particles()){
                if(draws.size()>=512){warn("当前帧的粒子绘制超过 512 层");break;}
                if(particle.alpha()<=0)continue;
                var frame=track.animations().get(particle.object()).sample(particle.age(),false);
                if(frame==null||frame.image().isEmpty())continue;
                try {
                    draws.add(new Draw(prepare(frame),particle.x(),particle.y()-particle.z(),mirrored,track.label(),particle.alpha()));
                }catch(IOException|IllegalArgumentException failure){warn(track.path()+"："+failure.getMessage());}
            }
        }
        Rectangle2D view=mirrored?new Rectangle2D.Double(-bounds.getMaxX(),bounds.getY(),bounds.getWidth(),bounds.getHeight()):bounds;
        return new Rendered(List.copyOf(draws),(Rectangle2D)view.clone(),time,warnings());
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
