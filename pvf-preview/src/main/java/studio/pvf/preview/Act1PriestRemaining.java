package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;
import studio.pvf.preview.Act1SkillSequence.VisualKeyframe;

/** Priest state 31 and the two conditional status previews. */
final class Act1PriestRemaining {
    private static final String PRIEST="character/priest/", EFFECT=PRIEST+"effect/animation/";
    private static final String HOME_RUN="skill/priest/homerun.skl";
    private static final String SACRIFICE="skill/priest/sacrificeofsoul.skl";
    private static final String SHADOW="skill/priest/shadowboxer.skl";
    private static final String HOME_MISS="homerun:miss";
    private static final String WAIT="waiting motion";

    private Act1PriestRemaining() {}

    static List<GameEffectRecipes.Effect> choices(String path,GameScript.Lookup lookup,boolean act1) throws IOException {
        if(!act1||!path.equals(HOME_RUN)||!matches(path,41,lookup))return List.of();
        return List.of(new GameEffectRecipes.Effect(HOME_MISS,"星落打 · 未抓到目标","",path,"试抓结束后收招"));
    }

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,
                                 int level,String choice,Act1BuffCast.ActorHeight height,String weaponPath) throws IOException {
        if(path.equals(HOME_RUN)&&(choice.isEmpty()||choice.equals(HOME_MISS))&&matches(path,41,lookup)){
            return homeRun(text,lookup,actions,choice.equals(HOME_MISS),weaponPath);
        }
        if(!choice.isEmpty())return null;
        if(path.equals(SACRIFICE)&&matches(path,25,lookup))return sacrifice(text,lookup,actions,height.read("priest"));
        if(path.equals(SHADOW)&&matches(path,35,lookup))return shadow(text,lookup,actions,level,height.read("priest"));
        return null;
    }

    /** 0x622CB0 cues grab at frame 4 and impact at frame 2; 0x54CFF0 carries the target through the airborne arc. */
    private static Act1SkillSequence homeRun(String text,GameScript.Lookup lookup,Map<String,String> actions,
                                             boolean miss,String weaponPath) throws IOException {
        String grab="etc motion:22", charge="etc motion:23", swing="etc motion:24";
        var grabFrames=body(lookup,actions,grab);
        if(grabFrames.size()<5)return null;
        long windup=duration(grabFrames);
        if(miss)return new Act1SkillSequence(List.of(new Pose(grab,0,windup)),List.of(),List.of(),
                "星落打未抓到目标，播放完整试抓动作");
        var chargeFrames=body(lookup,actions,charge);
        var swingFrames=body(lookup,actions,swing);
        if(chargeFrames.size()<4||swingFrames.size()<3)return null;
        int type=weaponType(weaponPath,lookup);
        if(type<0)return null;
        int grabX=new int[]{133,125,170,186,161}[type];
        int hitX=new int[]{88,68,104,124,102}[type];
        int hitZ=new int[]{55,50,29,52,43}[type];
        double swingScale=new double[]{1,.88,1.12,1.23,1.21}[type];
        long chargeTime=Math.max(before(chargeFrames,3)+1,Math.min(5000,
                Act1SkillSequence.staticValue(GameScript.fields(text),1,500)));
        long smash=windup+chargeTime, recover=smash+duration(swingFrames);
        long hit=smash+before(swingFrames,2);
        var visuals=new ArrayList<Visual>();
        String folder=EFFECT+"homerun/";
        add(visuals,lookup,folder+"grab1.ani","星落打 · 抓取",3501,before(grabFrames,4),windup,grabX,0,1);
        add(visuals,lookup,folder+"grab2.ani","星落打 · 蓄力",3501,windup,smash,grabX,0,1);
        add(visuals,lookup,folder+"dust1.ani","星落打 · 蓄力扬尘",300,windup,smash,0,0,1);
        add(visuals,lookup,folder+"dust2.ani","星落打 · 蓄力烟尘",3502,windup,smash,0,0,1);
        add(visuals,lookup,folder+"dust3.ani","星落打 · 挥击扬尘",3502,smash,recover,0,0,1);
        add(visuals,lookup,folder+"smash.ani","星落打 · 挥击",3503,smash,recover,0,0,swingScale);
        add(visuals,lookup,folder+"hit1.ani","星落打 · 命中冲击",3600,hit,Long.MAX_VALUE,hitX,-hitZ,1);
        add(visuals,lookup,folder+"hit2.ani","星落打 · 满蓄力冲击",3601,hit,Long.MAX_VALUE,hitX,-hitZ,1);
        if(visuals.isEmpty())return null;
        long end=Math.max(recover,visuals.stream().mapToLong(Visual::end).max().orElse(0));
        long capture=before(grabFrames,4);
        Act1SwordAsuraSupplement.addTarget(visuals,lookup,"stay","星落打 · 哥布林",0,capture,grabX,null);
        Act1SwordAsuraSupplement.addTarget(visuals,lookup,"damage1","星落打 · 抓取目标",capture,windup,grabX,null);
        double apex=Act1PriestSupplement.setting(Act1PriestSupplement.settings(lookup),"hr jump height",1300);
        double seconds=(hit-windup)/1000.0;
        var throwUp=new SkillMotion(windup,hit-windup,(hitX-grabX)/seconds,0,
                (4*apex-hitZ)/seconds,(4*hitZ-8*apex)/(seconds*seconds));
        Act1SwordAsuraSupplement.addTarget(visuals,lookup,"overturn","星落打 · 抛起目标",windup,hit,grabX,throwUp);
        var knockback=new SkillMotion(hit,500,400,-500,80,-800,hitZ);
        Act1SwordAsuraSupplement.addTarget(visuals,lookup,"overturn","星落打 · 击飞目标",hit,end,hitX,knockback);
        var poses=new ArrayList<>(List.of(new Pose(grab,0,windup),new Pose(charge,windup,smash),new Pose(swing,smash,recover)));
        if(end>recover&&actions.containsKey(WAIT))poses.add(new Pose(WAIT,recover,end));
        return new Act1SkillSequence(poses,visuals,List.of(),
                "星落打演示抓起一名普通哥布林，抛至原版配置高度后接满蓄挥击；冲击位置按武器类别选取");
    }

    /** State 5 triggers skill 25, then 0x6209A0 adds the two native effects at the caster and living allies. */
    private static Act1SkillSequence sacrifice(String text,GameScript.Lookup lookup,Map<String,String> actions,int height) throws IOException {
        if(!actions.containsKey("down motion")||!actions.containsKey(WAIT))return null;
        int diameter=Act1SkillSequence.staticValue(GameScript.fields(text),0,1600);
        if(diameter<2)return null;
        int allyX=Math.min(150,diameter/2);
        var visuals=new ArrayList<Visual>();
        add(visuals,lookup,EFFECT+"sacrificeofsoulcast.ani","灵魂牺牲 · 倒地触发",3501,0,Long.MAX_VALUE,0,0,1);
        add(visuals,lookup,EFFECT+"sacrificeofsoulbuff.ani","灵魂牺牲 · 队友恢复",3501,0,Long.MAX_VALUE,allyX,-height,1);
        if(visuals.size()!=2)return null;
        long end=visuals.stream().mapToLong(Visual::end).max().orElseThrow();
        var ally=new Pose(WAIT,0,end,null,false,new SkillMotion(0,0,0,0,allyX),
                0,Integer.MAX_VALUE,false,List.of(),true);
        return new Act1SkillSequence(List.of(new Pose("down motion",0,end),ally),visuals,List.of(),
                "灵魂牺牲在死亡时触发；演示一名范围内队友接收恢复光效，队友沿用参考角色外观");
    }

    /** CNShadowBoxer draws stored pose snapshots delayed by the Priest SD setting (0x54BC20). */
    private static Act1SkillSequence shadow(String text,GameScript.Lookup lookup,Map<String,String> actions,
                                            int level,int height) throws IOException {
        String straight="skill/priest/luckystraight.skl";
        String strikeText=lookup.read(straight);
        if(strikeText==null||!actions.containsKey("buff motion"))return null;
        var strike=Act1PriestSupplement.find(straight,strikeText,lookup,actions,1);
        if(strike==null||!strike.emitters().isEmpty())return null;
        var fields=GameScript.fields(text);
        long activation=Math.max(1,Act1SkillSequence.castingTime(fields,level,500));
        long active=Act1SkillSequence.levelValue(fields,level,0,12000,1,60000);
        int delay=Act1PriestSupplement.setting(Act1PriestSupplement.settings(lookup),"sb shadow delay",150);
        int count=Act1SkillSequence.staticValue(fields,0,1);
        if(delay<1||delay>2000||count<1||count>3)return null;
        var visuals=new ArrayList<Visual>();
        var emitters=new ArrayList<SkillParticleEmitter>();
        var poses=new ArrayList<Pose>();
        poses.add(new Pose("buff motion",0,activation,null,true,null));
        long end=Act1BuffCast.activate("幻影化身",45,activation,height,lookup,visuals,emitters);
        long start=activation+260;
        if(start>=activation+active)return null;
        if(actions.containsKey(WAIT))poses.add(new Pose(WAIT,activation,start));
        for(var pose:strike.poses()){
            poses.add(shift(pose,start,false));
            if(pose.action().equals(WAIT))continue;
            for(int i=1;i<=count;i++){
                long offset=start+(long)i*delay;
                if(offset+pose.start()<activation+active)poses.add(shift(pose,offset,true));
            }
        }
        for(var visual:strike.visuals()){
            visuals.add(new Visual(visual.path(),visual.label(),visual.layer(),start+visual.start(),
                    visual.end()<0?visual.end():start+visual.end(),visual.x(),visual.y(),shift(visual.motion(),start),
                    visual.scale(),visual.mirror(),visual.style(),visual.variations()));
        }
        long posesEnd=poses.stream().mapToLong(Pose::end).max().orElseThrow();
        end=Math.max(end,posesEnd);
        var last=poses.stream().filter(p->!p.secondary()).reduce((a,b)->b).orElseThrow();
        if(actions.containsKey(WAIT)&&end>last.end()){
            poses.add(new Pose(WAIT,last.end(),end,null,false,last.motion()));
        }
        return new Act1SkillSequence(poses,visuals,emitters,
                "幻影化身演示激活后打一记直拳冲击；影子按原版配置延迟复制角色姿势，后续命中和双重幻影由实战提供");
    }

    private static Pose shift(Pose pose,long offset,boolean echo){
        return new Pose(pose.action(),pose.start()+offset,pose.end()+offset,pose.holdDelay(),pose.fitToStage(),
                shift(pose.motion(),offset),pose.firstFrame(),pose.endFrame(),pose.reverseFacing(),
                echo?List.of(new VisualKeyframe(0,1,150.0/255)):pose.keyframes(),echo);
    }

    private static SkillMotion shift(SkillMotion motion,long offset){
        return motion==null?null:new SkillMotion(motion.start()+offset,motion.travelTime(),motion.velocity(),
                motion.acceleration(),motion.verticalVelocity(),motion.verticalAcceleration(),motion.height(),
                motion.orbit(),motion.initialX());
    }

    private static int weaponType(String path,GameScript.Lookup lookup) throws IOException {
        if(path==null||path.isEmpty())return 0;
        var fields=GameScript.fields(lookup.read(GameScript.normalize(path)));
        if(!"weapon".equals(GameScript.flag(GameScript.value(fields,"equipment type"))))return -1;
        int type=GameScript.integer(GameScript.value(fields,"sub type"),-1);
        return type>=0&&type<=4?type:-1;
    }

    private static List<ResourceScript.Frame> body(GameScript.Lookup lookup,Map<String,String> actions,String action) throws IOException {
        String path=actions.get(action);
        return path==null?List.of():Act1SkillSequence.frames(PRIEST+"priest.chr",path,lookup);
    }

    private static long duration(List<ResourceScript.Frame> frames){return before(frames,frames.size());}

    private static long before(List<ResourceScript.Frame> frames,int index){
        return frames.subList(0,index).stream().mapToLong(ResourceScript.Frame::delay).sum();
    }

    private static void add(List<Visual> visuals,GameScript.Lookup lookup,String path,String label,int layer,
                            long start,long limit,int x,int y,double scale) throws IOException {
        String text=lookup.read(path);
        if(text==null)return;
        long end=Math.min(limit,start+new AnimationTimeline(ResourceScript.animation(text)).duration());
        if(end>start)visuals.add(new Visual(path,label,layer,start,end,x,y,null,scale,true));
    }

    private static boolean matches(String path,int id,GameScript.Lookup lookup) throws IOException {
        return path.equals("skill/"+GameScript.catalog(lookup.read("skill/priestskill.lst")).get(id));
    }
}
