package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** ACT1 gunner state-to-resource bindings from 0x600690 and 0x5F9CB0. */
final class Act1GunnerCast {
    private static final String GUNNER = "character/gunner/";
    private static final String EFFECT = GUNNER + "effect/animation/";
    private static final String FOCUSED_FLAME = GUNNER + "effect/particle/flamethrowerboost.ptl";
    private static final String FOCUSED_JET = EFFECT + "flamethrowerboost1.ani";

    record Clip(String path,String label,List<Integer> variations,int layer,long start,long end,double x,double y,int holdDelay) {}
    record Emitter(String path,String label,long start,long emitFor,double frequency,double x,double z) {}
    record Cast(String action,String followAction,long changeAt,long end,List<Clip> clips,Emitter emitter,String note) {}

    private Act1GunnerCast() {}

    static Cast find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,boolean act1) throws IOException {
        if(!act1||!path.startsWith("skill/gunner/"))return null;
        int id=switch(path){
            case "skill/gunner/gatling.skl" -> 12;
            case "skill/gunner/flamethrower.skl" -> 13;
            case "skill/gunner/flamethrowerboost.skl" -> 36;
            case "skill/gunner/gaebolgpunch.skl" -> 67;
            default -> -1;
        };
        if(id<0)return null;
        String entry=GameScript.catalog(lookup.read("skill/gunnerskill.lst")).get(id);
        if(entry==null||!path.equals("skill/"+entry))return null;
        if(id==67)return gaebolg(lookup,actions);
        return rapid(id,GameScript.fields(text),lookup,actions,level);
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
                boost?FOCUSED_FLAME:GUNNER+"effect/particle/flamethrower.ptl";
        String label=gatling?"格林机枪弹道":boost?"烈焰强击火焰":"火焰喷射器火焰";
        return new Cast(ready,fire,startup,end,List.copyOf(clips),
                new Emitter(emitter,label,startup,fireFor,rate,gatling?150:141,88),
                "施放按当前等级最大持续时间预览；枪口粒子使用客户端 PTL，未执行命中和手动终止");
    }

    /** Keep the focused jet aimed at the muzzle through the source's nearly equivalent 0/6.28 angles. */
    static ResourceScript.Animation particleVisual(String emitter,String path,ResourceScript.Animation animation) {
        if(!emitter.equals(FOCUSED_FLAME)||!path.equals(FOCUSED_JET)||animation.frames().size()!=2)return animation;
        var first=animation.frames().get(0);
        var last=animation.frames().get(1);
        double turn=2*Math.PI;
        double change=last.rotation()-first.rotation();
        if(!first.interpolation()||Math.abs(Math.abs(change)-turn)>.01)return animation;
        var aligned=last.withRotation(last.rotation()-Math.copySign(turn,change));
        return new ResourceScript.Animation(animation.loop(),List.of(first,aligned),animation.warnings());
    }

    private static Cast gaebolg(GameScript.Lookup lookup,Map<String,String> actions) throws IOException {
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
        var gate=ResourceScript.animation(lookup.read(paths.get(0)));
        long appear=gate.frames().stream().limit(7).mapToLong(ResourceScript.Frame::delay).sum();
        long punch=click+appear;
        long end=punch+680;
        double gateX=440;
        return new Cast(action,"",Long.MAX_VALUE,end,List.of(
                clip(EFFECT+"gaebolgpunch.ani","召唤装置",2151,0,-1,0,0),
                clip(EFFECT+"gaebolgpunchclick.ani","召唤开关",2700,click,-1,0,0),
                new Clip(paths.get(0),"异次元裂口",List.of(),2900,click,end,gateX,0,360),
                new Clip(paths.get(1),"裂口光效",List.of(),2950,click,end,gateX,0,360),
                clip(paths.get(4),"裂口电光",3000,click,punch,gateX,0),
                clip(paths.get(2),"盖波加拳击",3100,punch,punch+360,gateX,0),
                clip(paths.get(5),"拳击光效",3200,punch,-1,gateX,0)
        ),null,"使用技能状态 30、角色动作及对象 22228；裂口相对位置、停顿和拳击阶段为离线预览");
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
