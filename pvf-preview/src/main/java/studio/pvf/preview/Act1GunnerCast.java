package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** ACT1 gunner state-to-resource bindings from 0x600690 and 0x5F9CB0. */
final class Act1GunnerCast {
    private static final String GUNNER = "character/gunner/";
    private static final String EFFECT = GUNNER + "effect/animation/";
    static final String ORDINARY_FLAME = GUNNER + "effect/particle/flamethrower.ptl";
    private static final String ORDINARY_JET = EFFECT + "flamethrower1.ani";
    private static final String FOCUSED_FLAME = GUNNER + "effect/particle/flamethrowerboost.ptl";
    private static final String FOCUSED_JET = EFFECT + "flamethrowerboost1.ani";

    record Clip(String path,String label,List<Integer> variations,int layer,long start,long end,double x,double y,
                int holdDelay,double clipX,GaebolgMotion movement,double scale) {
        Clip(String path,String label,List<Integer> variations,int layer,long start,long end,double x,double y,int holdDelay){
            this(path,label,variations,layer,start,end,x,y,holdDelay,Double.NaN,null,1);
        }
        Clip(String path,String label,List<Integer> variations,int layer,long start,long end,double x,double y,
             int holdDelay,double clipX,GaebolgMotion movement){
            this(path,label,variations,layer,start,end,x,y,holdDelay,clipX,movement,1);
        }

        CombatFrame.Box clipAttack(CombatFrame.Box box,double originX,boolean left) {
            if(movement==null||!Double.isFinite(clipX))return box;
            double portalLocal=clipX-(left?-originX:originX);
            double start=Math.max(box.x(),portalLocal);
            return new CombatFrame.Box(start,box.y(),box.z(),box.x()+box.width()-start,box.depth(),box.height());
        }
    }
    // 0x5A4180 uses these distances at gp size rate 1 and clips the fist at the portal plane.
    private static final double FIST_MASK_OFFSET=-10, FIST_VISIBLE_OFFSET=25, FIST_REACH=458;
    private static final int FIST_REVEAL=100;

    record GaebolgMotion(long reveal,long thrust,long retract,int blow,int gather,double scale) {
        double offset(long time){
            if(time<thrust)return lerp(FIST_MASK_OFFSET,FIST_VISIBLE_OFFSET,progress(time-reveal,FIST_REVEAL))*scale;
            if(time<retract){
                double t=progress(time-thrust,blow);
                return lerp(FIST_VISIBLE_OFFSET,FIST_REACH,t*t)*scale;
            }
            double t=progress(time-retract,gather);
            return lerp(FIST_REACH,FIST_MASK_OFFSET,t*(2-t))*scale;
        }
        double maxOffset(){return FIST_REACH*scale;}
        private static double progress(long elapsed,int duration){return Math.max(0,Math.min(1,elapsed/(double)duration));}
        private static double lerp(double from,double to,double t){return from+(to-from)*t;}
    }
    record Cast(String action,String followAction,long changeAt,long end,List<Clip> clips,SkillParticleEmitter emitter,
                String note,int firstFrameDelay,List<Act1SkillSequence.Visual> visuals) {
        Cast(String action,String followAction,long changeAt,long end,List<Clip> clips,SkillParticleEmitter emitter,
             String note,int firstFrameDelay){
            this(action,followAction,changeAt,end,clips,emitter,note,firstFrameDelay,List.of());
        }
    }

    private Act1GunnerCast() {}

    static Cast find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,boolean act1,
                     ScenePreview.SpriteLookup sprites) throws IOException {
        if(!act1||!path.startsWith("skill/gunner/"))return null;
        int id=switch(path){
            case "skill/gunner/gatling.skl" -> 12;
            case "skill/gunner/flamethrower.skl" -> 13;
            case "skill/gunner/flamethrowerboost.skl" -> 36;
            case "skill/gunner/crossshoot.skl" -> 48;
            case "skill/gunner/gaebolgpunch.skl" -> 67;
            default -> -1;
        };
        if(id<0)return null;
        String entry=GameScript.catalog(lookup.read("skill/gunnerskill.lst")).get(id);
        if(entry==null||!path.equals("skill/"+entry))return null;
        if(id==67)return gaebolg(lookup,actions,sprites);
        var fields=GameScript.fields(text);
        if(id==48)return crossShot(fields,actions);
        return rapid(id,fields,lookup,actions,level);
    }

    /** State 27 fires from (77,0,112) after the configured draw delay. */
    private static Cast crossShot(List<GameScript.Field> fields,Map<String,String> actions){
        String action=GameEffectRecipes.motionFor(actions,"crossshoot");
        var values=GameScript.values(fields,"static data");
        if(action.isEmpty()||values.size()<2)return null;
        int fire=number(values.get(0),0,30000),recovery=number(values.get(1),0,30000);
        if(fire<0||recovery<0)return null;
        long end=Math.max(1,(long)fire+recovery);
        return new Cast(action,"",end,end,List.of(
                clip(EFFECT+"shooteffect/crossshoot.ani","交叉射击枪口",2700,fire,-1,77,-112),
                clip(EFFECT+"bullet/crossshoot.ani","交叉射击弹道",3000,fire,-1,77,-112)
        ),null,"交叉射击按配置的开火延迟和收招时间预览。游戏内的拔枪、装弹速度另行计算",fire);
    }

    private static Cast rapid(int id,List<GameScript.Field> fields,GameScript.Lookup lookup,Map<String,String> actions,int level) throws IOException {
        String ready=GameEffectRecipes.motionFor(actions,"subweaponfrontrapid1");
        String fire=GameEffectRecipes.motionFor(actions,"subweaponfrontrapid2");
        if(ready.isEmpty()||fire.isEmpty())return null;
        boolean gatling=id==12;
        boolean boost=id==36;
        var config=GameScript.values(fields,"static data");
        if(config.size()<2)return null;
        int startup=gatling?(int)motionTime(lookup,actions.get(ready),Integer.MAX_VALUE):number(config.get(1),0,30000);
        int fireFor=levelValue(fields,level,gatling?2:1);
        int rate=gatling?number(config.get(0),1,60):0;
        int recovery=number(config.get(gatling?1:0),0,30000);
        if(startup<0||fireFor<1||rate<0||recovery<0)return null;
        if(gatling)recovery=(int)Math.min(10000,(long)fireFor*recovery/1000);
        long stop=(long)startup+fireFor;
        long end=Math.min(60000,stop+recovery);
        int variant=gatling?1:0;
        var clips=new ArrayList<Clip>();
        for(String suffix:List.of("a","b")){
            int layer=suffix.equals("a")?2151:651;
            clips.add(new Clip(EFFECT+"subweapon/subweaponfrontrapid1"+suffix+".ani","重火器枪体",List.of(variant,0),layer,0,startup,0,0,0));
            clips.add(new Clip(EFFECT+"subweapon/subweaponfrontrapid2"+suffix+".ani","重火器枪体",List.of(variant,0),layer,startup,end,0,0,0));
        }
        if(gatling){
            clips.add(clip(EFFECT+"gatlinggunshootfront.ani","格林机枪枪口",2700,startup,stop,0,0));
            clips.add(clip(EFFECT+"gatlinggunoverheat.ani","格林机枪过热",2700,stop,end,0,0));
        }
        String emitter=gatling?GUNNER+"particle/subweapon/gatlingfront.ptl":
                boost?FOCUSED_FLAME:ORDINARY_FLAME;
        String label=gatling?"格林机枪弹道":boost?"烈焰强击火焰":"火焰喷射器火焰";
        return new Cast(ready,fire,startup,end,List.copyOf(clips),
                // State 22 updates the particle origin during fire. The flame textures extend behind that origin.
                new SkillParticleEmitter(emitter,label,startup,fireFor,rate,gatling?175:232,gatling?57:88,gatling?Double.NaN:141),
                "施放按当前等级最大持续时间预览；枪口粒子使用客户端 PTL，未执行命中和手动终止",-1,
                gatling?List.of():flameCollision(lookup,boost,startup,stop));
    }

    private static List<Act1SkillSequence.Visual> flameCollision(GameScript.Lookup lookup,boolean focused,
                                                                long start,long stop) throws IOException {
        // 0x600690 state 22 creates a separate invisible damage object at the muzzle.
        // 0x5A8F80 / 0x5A9870 keep its final ANI frame until the owner stops firing.
        String object=GameEffectRecipes.passiveObject(lookup,focused?22204:22201);
        if(object==null)return List.of();
        String motion=GameScript.value(GameScript.fields(lookup.read(object)),"basic motion");
        if(motion.isEmpty())return List.of();
        String path=GameScript.resolve(object,motion,lookup);
        if(path==null)return List.of();
        return List.of(new Act1SkillSequence.Visual(path,focused?"聚焦喷火器 · 攻击判定":"喷火器 · 攻击判定",
                3000,start,stop,141,-88));
    }

    /** Keep the thin jet around the muzzle axis. PTL still controls each particle's trajectory. */
    static ResourceScript.Animation particleVisual(String emitter,String path,ResourceScript.Animation animation) {
        boolean ordinary=emitter.equals(ORDINARY_FLAME)&&path.equals(ORDINARY_JET);
        boolean focused=emitter.equals(FOCUSED_FLAME)&&path.equals(FOCUSED_JET);
        if((!ordinary&&!focused)||animation.frames().size()!=2)return animation;
        var first=animation.frames().get(0);
        var last=animation.frames().get(1);
        if(!first.interpolation())return animation;
        double turn=2*Math.PI;
        double change=last.rotation()-first.rotation();
        double rotation;
        if(ordinary&&Math.abs(change-1.5)<.01)rotation=first.rotation();
        else if(focused&&Math.abs(Math.abs(change)-turn)<=.01)rotation=last.rotation()-Math.copySign(turn,change);
        else return animation;
        var aligned=last.withRotation(rotation);
        return new ResourceScript.Animation(animation.loop(),List.of(first,aligned),animation.warnings());
    }

    private static Cast gaebolg(GameScript.Lookup lookup,Map<String,String> actions,
                               ScenePreview.SpriteLookup sprites) throws IOException {
        String action=GameEffectRecipes.motionFor(actions,"gaebolgpunch");
        if(action.isEmpty())return null;
        long click=motionTime(lookup,actions.get(action),4);
        if(click<0)return null;
        String object=GameEffectRecipes.passiveObject(lookup,22228);
        if(object==null)return null;
        var motions=GameScript.values(GameScript.fields(lookup.read(object)),"etc motion");
        if(motions.size()<6)return null;
        var paths=new ArrayList<String>();
        for(int i=0;i<6;i++){
            String target=GameScript.resolve(object,motions.get(i),lookup);
            if(target==null)return null;
            paths.add(target);
        }
        var gate=ResourceScript.animation(lookup.read(paths.get(0))).frames();
        var glow=ResourceScript.animation(lookup.read(paths.get(1))).frames();
        // State 2 reveals the fist; state 3 starts the thrust and installs the hit ANI;
        // state 4 starts retraction after the configured stop. 0x5A3530 removes collision before state 4.
        var settings=GameScript.fields(lookup.read("data/skill/gunner.sd"));
        var integers=GameScript.values(settings,"int");
        int show=gpTime(integers,"gp punch show time",1100), windup=gpTime(integers,"gp punch time",2100);
        int blow=gpTime(integers,"gp blow time",400), stop=gpTime(integers,"gp stop time",1200);
        int hitTime=gpTime(integers,"gp hit time",500), gather=gpTime(integers,"gp gather time",600);
        double scale=gpValue(GameScript.values(settings,"float"),"gp size rate",1.5);
        if(scale<.1||scale>5)scale=1.5;
        long thrust=click+windup, reveal=click+Math.min(show,windup);
        long retract=thrust+blow+stop, collisionEnd=Math.min(retract,thrust+hitTime);
        long gateClose=retract+Math.max(gather,closingTime(glow));
        long end=gateClose+closingTime(gate);
        double gateX=440, maskX=gateX+FIST_MASK_OFFSET*scale;
        var movement=new GaebolgMotion(reveal,thrust,retract,blow,gather,scale);
        var visuals=new ArrayList<Act1SkillSequence.Visual>();
        String hit=GameScript.resolve(object,"Animation/GaebolgPunchHitEffect.ani",lookup);
        if(hit!=null){
            // 0x5A4180 advances the attack box with the visible front; 0x5A4680 attaches the hit ANI to the target.
            double targetX=gateX+420*scale;
            long contact=thrust+(long)Math.ceil(blow*Math.sqrt((420-FIST_VISIBLE_OFFSET)
                    /(FIST_REACH-FIST_VISIBLE_OFFSET)));
            int height=Act1SwordAsuraSupplement.targetHeight(lookup,sprites);
            long hitEnd=contact+new AnimationTimeline(ResourceScript.animation(lookup.read(hit))).duration();
            if(contact<collisionEnd&&hitEnd>contact){
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay","盖波加拳 · 演示目标",0,contact,targetX,null);
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"damage1","盖波加拳 · 目标受击",contact,hitEnd,targetX,null);
                Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay","盖波加拳 · 演示目标",hitEnd,end,targetX,null);
                var style=new Act1SkillSequence.VisualStyle(null,0,List.of(),Map.of(),true);
                visuals.add(new Act1SkillSequence.Visual(hit,"盖波加拳 · 独立命中光",3552,contact,hitEnd,
                        targetX,-height/2.0,null,Math.max(.8,height*.01),true,style));
            }
        }
        return new Cast(action,"",Long.MAX_VALUE,end,List.of(
                clip(EFFECT+"gaebolgpunch.ani","召唤装置",2151,0,-1,0,0),
                clip(EFFECT+"gaebolgpunchclick.ani","召唤开关",2700,click,-1,0,0),
                new Clip(paths.get(0),"异次元裂口",List.of(),2900,click,end,gateX,0,holdUntil(gate,gateClose-click),Double.NaN,null,scale),
                new Clip(paths.get(1),"裂口光效",List.of(),2950,click,gateClose,gateX,0,holdUntil(glow,retract-click),Double.NaN,null,scale),
                new Clip(paths.get(2),"盖波加拳击",List.of(),3100,reveal,retract+gather,gateX,0,0,maskX,movement,scale),
                new Clip(paths.get(3),"盖波加拳 · 攻击判定",List.of(),3101,thrust,collisionEnd,gateX,0,0,maskX,movement,scale),
                new Clip(paths.get(4),"裂口电光",List.of(),3150,reveal+1,retract+gather,gateX,0,0,maskX,null,scale),
                new Clip(paths.get(5),"拳击光效",List.of(),3200,thrust,-1,gateX,0,0,maskX,null,scale)
        ),null,"拳头和攻击判定使用 ACT1 的时长、缩放与裂口裁剪；预览用固定裂口和哥布林位置。",-1,List.copyOf(visuals));
    }

    private static double gpValue(List<String> values,String key,double fallback) {
        for(int i=0;i+1<values.size();i+=2)if(values.get(i).equals(key)){
            try {
                double value=Double.parseDouble(values.get(i+1));
                return Double.isFinite(value)?value:fallback;
            } catch(NumberFormatException ignored){return fallback;}
        }
        return fallback;
    }

    private static int gpTime(List<String> values,String key,int fallback) {
        return (int)Math.max(1,Math.min(20000,gpValue(values,key,fallback)));
    }

    /** Replace the sentinel hold frame with the time the native state would leave that frame. */
    private static int holdUntil(List<ResourceScript.Frame> frames,long elapsed){
        for(var frame:frames){
            if(frame.delay()>=10000)return (int)Math.max(1,elapsed);
            elapsed-=frame.delay();
        }
        return 0;
    }

    private static long closingTime(List<ResourceScript.Frame> frames){
        long total=0;
        boolean closing=false;
        for(var frame:frames){
            if(closing)total+=frame.delay();
            else if(frame.delay()>=10000)closing=true;
        }
        return Math.max(1,Math.min(total,10000));
    }

    private static long frameTime(List<ResourceScript.Frame> frames,int count){
        return frames.stream().limit(count).mapToLong(ResourceScript.Frame::delay).sum();
    }

    /** The stock ACT1 skin ANI selects the cross-shot pose on Bullet Guard's first frame. */
    static ResourceScript.Frame bodyFrame(String path,int ordinal,ResourceScript.Frame frame){
        if(ordinal==0&&frame.index()==180&&path.equals(GUNNER+"animation/bulletguard.ani")
                &&frame.image().equalsIgnoreCase("Character/Gunner/Equipment/Avatar/skin/gn_body%04d.img")){
            return frame.withIndex(185);
        }
        return frame;
    }

    private static Clip clip(String path,String label,int layer,long start,long end,double x,double y){
        return new Clip(path,label,List.of(),layer,start,end,x,y,0);
    }

    private static long motionTime(GameScript.Lookup lookup,String path,int count) throws IOException {
        String ani=GameScript.resolve(GUNNER+"gunner.chr",path,lookup);
        if(ani==null)return -1;
        var frames=ResourceScript.animation(lookup.read(ani)).frames();
        if(frames.isEmpty())return -1;
        return Math.min(30000,frames.stream().limit(count).mapToLong(ResourceScript.Frame::delay).sum());
    }

    private static int levelValue(List<GameScript.Field> fields,int level,int column){
        var values=GameScript.values(fields,"level info");
        if(values.isEmpty())return -1;
        int width=number(values.get(0),column+1,64);
        if(width<0)return -1;
        int index=1+(level-1)*width+column;
        return index<values.size()?number(values.get(index),1,20000):-1;
    }

    private static int number(String value,int min,int max){
        int parsed=GameScript.integer(value,-1);
        return parsed>=min&&parsed<=max?parsed:-1;
    }
}
