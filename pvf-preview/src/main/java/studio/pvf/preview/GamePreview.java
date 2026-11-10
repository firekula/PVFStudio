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
    public record Draw(PreviewRenderer.Prepared prepared,double x,double y,boolean mirrored,String label) {}
    public record Rendered(List<Draw> draws,Rectangle2D bounds,long milliseconds,List<String> warnings) {}
    private record Track(String path,AnimationTimeline timeline,int layer,String label,long start,long end,double x,double y,boolean loop) {}
    private final List<Track> tracks=new ArrayList<>();
    private final List<String> warnings=new ArrayList<>();
    private final LinkedHashMap<ResourceScript.Frame,PreviewRenderer.Prepared> pixels=new LinkedHashMap<>(32,.75f,true);
    private final ScenePreview.SpriteLookup sprites;
    private final CharacterAppearance.Model appearance;
    private final GameItemInfo info;
    private final List<GameEffectRecipes.Effect> effects;
    private Rectangle2D bounds;
    private Rectangle2D actorBounds;
    private long cacheBytes,duration;
    private boolean wholeSequence;
    private static final long CACHE_LIMIT=32L*1024*1024;
    private GamePreview(ScenePreview.SpriteLookup sprites,CharacterAppearance.Model appearance,GameItemInfo info,List<GameEffectRecipes.Effect> effects){this.sprites=sprites;this.appearance=appearance;this.info=info;this.effects=effects;warnings.addAll(appearance.warnings());warnings.addAll(info.warnings());}
    public static GamePreview load(String path,String text,GameScript.Lookup lookup,Set<String> paths,NpkResources resources,Options options) throws IOException {
        return load(path,text,lookup,paths,new ScenePreview.SpriteLookup(){public NpkResources.Info info(String p,int f)throws IOException{return resources.info(p,f);}public NpkResources.Sprite sprite(String p,int f)throws IOException{return resources.sprite(p,f);}},options);
    }
    public static GamePreview load(String path,String text,GameScript.Lookup raw,Set<String> paths,ScenePreview.SpriteLookup sprites,Options options) throws IOException {
        GameScript.Lookup bounded=GameScript.boundedLookup(raw,4096,32L*1024*1024);GameScript.Lookup lookup=p->p.equals(path)?text:bounded.read(p);
        String passive=path.equals("skill/gunner/laserrifle.skl")?lookup.read("skill/gunner/laserriflecharge.skl"):null;
        var laser=options.effect().equals("actor")?null:GameEffectRecipes.laser(path,text,options.act1(),options.effect().equals("laser:charged"),passive);
        var available=CharacterAppearance.motions(Optional.ofNullable(lookup.read("character/gunner/gunner.chr")).orElse(""));
        String ready=available.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase("Animation/SubWeaponFrontRapid1.ani")).map(Map.Entry::getKey).findFirst().orElse("waiting motion");
        var appearance=CharacterAppearance.load(path,text,lookup,paths,options.job(),laser==null?options.action():ready,options.outfit());
        var choices=new ArrayList<>(GameEffectRecipes.effects(path,text,lookup,options.act1()));
        if(GameEffectRecipes.laser(path,text,options.act1(),false)!=null){choices.add(new GameEffectRecipes.Effect("actor","仅角色动作（不施放）","",path,"动作检查"));choices.add(new GameEffectRecipes.Effect("laser:normal","激光炮 · 普通施放组合","",path,"原生资源组合"));if(GameEffectRecipes.laser(path,text,options.act1(),true,passive)!=null)choices.add(new GameEffectRecipes.Effect("laser:charged","激光炮 · 满蓄力（被动 Lv.1）","",path,"蓄力参数来自同包被动技能 Lv.1"));}
        var model=new GamePreview(sprites,appearance,GameItemInfo.read(path,text,lookup,options.level()),List.copyOf(choices));
        model.wholeSequence=laser!=null;
        for(var part:appearance.parts())model.add(part.animation(),lookup,part.variation(),part.layer(),part.slot(),0,laser==null||part.slot().equals("title name")?Long.MAX_VALUE:laser.fireAt(),0,0,false);
        model.actorBounds=model.bounds==null?new Rectangle2D.Double(-40,-140,80,150):(Rectangle2D)model.bounds.clone();
        if(laser!=null)model.addLaser(path,text,lookup,paths,options,laser,available);
        if(appearance.parts().stream().noneMatch(p->p.owner().equals(path))&&path.endsWith(".equ"))model.warn(options.outfit().containsKey(CharacterAppearance.slot(text))?"当前部位已隐藏或由搭配替换":"配置未定义独立穿戴外观；当前显示参考角色");
        if(path.endsWith(".skl")&&laser==null&&!options.effect().equals("actor"))model.warn("尚无已验证的完整施放配方；可检查关联资源与角色动作");
        String selectedEffect=options.effect().isEmpty()&&options.act1()&&path.equals("skill/mage/launchjol.skl")?"passive:23008":options.effect();
        for(var effect:model.effects)if(effect.key().equals(selectedEffect)) {
            model.warn(effect.note());
            if(effect.path().endsWith(".ani"))model.add(effect.path(),lookup,List.of(0,0),5000,effect.label(),0,Long.MAX_VALUE,effect.x(),effect.y(),false);
            else if(!effect.path().isEmpty())model.warn("粒子资源可在代码模式的资源预览中播放："+effect.path());
        }
        model.tracks.sort(Comparator.comparingInt(Track::layer));
        model.duration=Math.max(1,model.duration);
        if(model.bounds==null)model.bounds=new Rectangle2D.Double(-36,-160,72,176);
        return model;
    }
    private void addLaser(String path,String text,GameScript.Lookup lookup,Set<String> paths,Options options,GameEffectRecipes.Laser laser,Map<String,String> actions) throws IOException {
        String firing=actions.entrySet().stream().filter(e->e.getValue().equalsIgnoreCase("Animation/SubWeaponFrontRapid2.ani")).map(Map.Entry::getKey).findFirst().orElse("waiting motion");
        var fire=CharacterAppearance.load(path,text,lookup,paths,"gunner",firing,options.outfit());
        for(var part:fire.parts())if(!part.slot().equals("title name"))add(part.animation(),lookup,part.variation(),part.layer(),part.slot(),laser.fireAt(),Long.MAX_VALUE,0,0,false);
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
        try {
            if(tracks.size()>=256)throw new IOException("组合轨道超过 256 个");String text=lookup.read(path);if(text==null)throw new IOException("找不到 ANI："+path);
            var parsed=ResourceScript.animation(text);var frames=new ArrayList<ResourceScript.Frame>();
            for(var frame:parsed.frames())frames.add(frame.image().isEmpty()?frame:frame.withImage(NpkResources.substitute(frame.image(),variants.stream().mapToInt(Integer::intValue).toArray())));
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
            tracks.add(new Track(path,timeline,layer,label,start,end<0?start+timeline.duration():end,x,y,loop));duration=Math.max(duration,Math.min(60000,start+timeline.duration()));
            for(String warning:parsed.warnings())warn(path+"："+warning);
        }catch(IOException|IllegalArgumentException failure){warn(label+"："+failure.getMessage());}
    }
    private static Rectangle2D visibleBounds(java.awt.image.BufferedImage image) {
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        int[] row=new int[image.getWidth()];for(int y=0;y<image.getHeight();y++){image.getRGB(0,y,image.getWidth(),1,row,0,image.getWidth());for(int x=0;x<row.length;x++)if((row[x]>>>24)!=0){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=y;}}
        return right<left?null:new Rectangle2D.Double(left,top,right-left+1,bottom-top+1);
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
                var prepared=pixels.get(frame);if(prepared==null){prepared=PreviewRenderer.prepare(frame,sprites.sprite(frame.image(),frame.index()));long bytes=(long)prepared.image().getWidth()*prepared.image().getHeight()*4;
                    while(!pixels.isEmpty()&&(cacheBytes+bytes>CACHE_LIMIT||pixels.size()>=128)){var removed=pixels.remove(pixels.keySet().iterator().next());cacheBytes-=(long)removed.image().getWidth()*removed.image().getHeight()*4;}
                    if(bytes<=CACHE_LIMIT){pixels.put(frame,prepared);cacheBytes+=bytes;}
                }
                boolean flip=mirrored&&!track.label().equals("title name");
                draws.add(new Draw(prepared,flip?-track.x():track.x(),track.y(),flip,track.label()));
            }catch(IOException|IllegalArgumentException failure){warn(track.path()+"："+failure.getMessage());}
        }
        Rectangle2D view=mirrored?new Rectangle2D.Double(-bounds.getMaxX(),bounds.getY(),bounds.getWidth(),bounds.getHeight()):bounds;
        return new Rendered(List.copyOf(draws),(Rectangle2D)view.clone(),time,warnings());
    }
    private void warn(String warning){if(warnings.size()<256&&!warnings.contains(warning))warnings.add(warning);}
    public long duration(){return duration;}
    public boolean usingSequence(){return wholeSequence;}
    public Rectangle2D actorBounds(){return (Rectangle2D)actorBounds.clone();}
    public GameItemInfo info(){return info;}
    public CharacterAppearance.Model appearance(){return appearance;}
    public List<GameEffectRecipes.Effect> effects(){return effects;}
    public List<String> warnings(){return List.copyOf(warnings);}
}
