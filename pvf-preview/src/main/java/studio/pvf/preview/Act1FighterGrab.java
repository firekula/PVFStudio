package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Pose;
import studio.pvf.preview.Act1SkillSequence.Visual;

/** ACT1 Fighter grab transitions in 0x5E9C90, 0x5E0FD0 and 0x5E39C0. */
final class Act1FighterGrab {
    private static final String CHARACTER="character/fighter/fighter.chr";
    private static final String EFFECT="character/fighter/effect/animation/";
    private static final String LEG_ATTEMPT="etc motion:18", LEG_THROW="etc motion:19";
    private static final String JUMP="jump motion", IDLE="waiting motion";
    private static final double REFERENCE_AIR_HEIGHT=50;
    private static final double JUMP_GRAVITY=1500, LEG_DROP_GRAVITY=2500;
    private static final int FRONT=3501, REAR=-1001;

    private Act1FighterGrab() {}

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,
                                  int level,boolean miss) throws IOException {
        Grab skill=Grab.fromPath(path);
        if(skill==null||!skill.matches(lookup))return null;
        var fields=GameScript.fields(text);
        int timeout=Act1SkillSequence.staticValue(fields,0,skill.defaultTimeout);
        if(timeout==0)return null;
        return switch(skill){
            case LEG -> airGrab(lookup,actions,timeout,miss);
            case SUPLEX -> groundGrab(lookup,actions,timeout,miss);
            case SPIRE -> spire(lookup,actions,timeout,fields,level,miss);
        };
    }

    static boolean missSelected(String path,String choice) {
        Grab skill=Grab.fromPath(path);
        return skill!=null&&skill.missKey().equals(choice);
    }

    static GameEffectRecipes.Effect missChoice(String path,GameScript.Lookup lookup,boolean act1) throws IOException {
        Grab skill=Grab.fromPath(path);
        if(!act1||skill==null||!skill.matches(lookup))return null;
        String note=skill==Grab.LEG?"抓取失败后按空中状态继续下落":"超过抓取时限后回到待机";
        return new GameEffectRecipes.Effect(skill.missKey(),skill.label+" · 未抓到目标","",path,note);
    }

    private static Act1SkillSequence airGrab(GameScript.Lookup lookup,Map<String,String> actions,int timeout,
                                             boolean miss) throws IOException {
        var attempt=frames(lookup,actions,LEG_ATTEMPT);
        if(attempt.isEmpty())return null;
        var jump=frames(lookup,actions,JUMP);
        int airborne=lastAirborneFrame(jump);
        if(miss){
            long landing=flightTime(REFERENCE_AIR_HEIGHT,0,JUMP_GRAVITY);
            long attemptEnd=Math.min(timeout,landing);
            var fall=new SkillMotion(0,landing,0,0,0,-JUMP_GRAVITY,REFERENCE_AIR_HEIGHT);
            var poses=new ArrayList<Pose>();
            poses.add(new Pose(LEG_ATTEMPT,0,attemptEnd,null,false,fall));
            if(airborne>=0){
                if(attemptEnd<landing)poses.add(new Pose(JUMP,attemptEnd,landing,null,false,fall,airborne,airborne+1));
                landing=addJumpLanding(poses,jump,airborne,landing);
            }
            addIdle(poses,actions,landing,landing+180);
            return sequence(poses,List.of(),"空绞锤从 50 像素参考高度试抓，落空后继续下落；在触地与技能抓取时限中先到的一刻切换动作");
        }

        var body=frames(lookup,actions,LEG_THROW);
        if(body.size()<4)return null;
        String grab=EFFECT+"legsuplexgrab.ani", roll=EFFECT+"legsuplexroll.ani", crash=EFFECT+"legsuplexcrash.ani";
        String object=GameEffectRecipes.passiveObject(lookup,21016);
        if(object==null)return null;
        String shock=GameScript.resolve(object,"Animation/LegSuplexSubThrow.ani",lookup);
        if(shock==null)return null;

        // State 29 keeps Z steady until throw frame 1 and then sets vertical acceleration to -2500.
        long caught=Math.min(timeout,Math.max(1,Act1SkillSequence.duration(attempt,attempt.size(),null)));
        long drop=caught+body.get(0).delay();
        long dropTime=flightTime(REFERENCE_AIR_HEIGHT,0,LEG_DROP_GRAVITY);
        long landing=drop+dropTime;
        var initialHeight=new SkillMotion(0,0,0,0,0,0,REFERENCE_AIR_HEIGHT);
        var fall=new SkillMotion(drop,dropTime,0,0,0,-LEG_DROP_GRAVITY,REFERENCE_AIR_HEIGHT);
        long rebound=landing+50;
        long groundDuration=Math.max(clipDuration(crash,lookup),clipDuration(shock,lookup));
        var poses=new ArrayList<Pose>();
        poses.add(new Pose(LEG_ATTEMPT,0,caught,null,false,initialHeight));
        poses.add(new Pose(LEG_THROW,caught,rebound,null,false,fall));
        long recovery=rebound;
        if(airborne>=0){
            double speed=reboundSpeed(lookup);
            long reboundTime=flightTime(0,speed,JUMP_GRAVITY);
            var bounce=new SkillMotion(rebound,reboundTime,0,0,speed,-JUMP_GRAVITY);
            poses.add(new Pose(JUMP,rebound,rebound+reboundTime,null,false,bounce,airborne,airborne+1));
            recovery=addJumpLanding(poses,jump,airborne,rebound+reboundTime);
        }
        addIdle(poses,actions,recovery,Math.max(landing+groundDuration,recovery+120));
        var visuals=List.of(
                new Visual(grab,"空绞锤 · 抓取闪光",FRONT,caught,rebound,0,0,fall,1,true),
                new Visual(roll,"空绞锤 · 空中翻转",FRONT,caught,rebound,0,0,fall,1,true),
                new Visual(crash,"空绞锤 · 落地光效",3600,landing,-1,0,0),
                new Visual(shock,"空绞锤 · 地面冲击",300,landing,-1,-28,0));
        return sequence(poses,visuals,"空绞锤演示抓取普通目标后的空中翻转、落地冲击和反弹；抓取使用 50 像素参考高度");
    }

    private static Act1SkillSequence groundGrab(GameScript.Lookup lookup,Map<String,String> actions,int timeout,
                                                boolean miss) throws IOException {
        String attempt="etc motion:4", slam="etc motion:5";
        if(frames(lookup,actions,attempt).isEmpty())return null;
        if(miss)return groundMiss(attempt,timeout,actions,"背摔");
        var body=frames(lookup,actions,slam);
        if(body.size()<5||GameEffectRecipes.passiveObject(lookup,21001)==null)return null;
        String dust=EFFECT+"suplexdust.ani";
        long caught=Math.min(timeout,100);
        long impact=caught+Act1SkillSequence.duration(body,4,null);
        long end=caught+Act1SkillSequence.duration(body,body.size(),null);
        var poses=new ArrayList<>(List.of(new Pose(attempt,0,caught),new Pose(slam,caught,end)));
        addIdle(poses,actions,end,Math.max(end+120,impact+clipDuration(dust,lookup)));
        return sequence(poses,List.of(new Visual(dust,"背摔 · 落地扬尘",3600,impact,-1,-47,0)),
                "背摔按 100 毫秒内抓到普通目标演示；第 5 帧触发落地扬尘");
    }

    private static Act1SkillSequence spire(GameScript.Lookup lookup,Map<String,String> actions,int timeout,
                                          List<GameScript.Field> fields,int level,boolean miss) throws IOException {
        String attempt="etc motion:11", slam="etc motion:12";
        if(frames(lookup,actions,attempt).isEmpty())return null;
        if(miss)return groundMiss(attempt,timeout,actions,"螺旋彗星落");
        var body=frames(lookup,actions,slam);
        if(body.size()<8||GameEffectRecipes.passiveObject(lookup,21008)==null)return null;
        int power=Act1SkillSequence.levelValue(fields,level,2,1100,1,2000);
        long caught=Math.min(timeout,100);
        long launch=caught+Act1SkillSequence.duration(body,2,null);
        long airTime=flightTime(0,power,JUMP_GRAVITY);
        long spin=launch+(airTime+1)/2;
        long landing=launch+airTime;
        long rebound=landing+100;
        // 0x4CA2F0 derives velocity from a bounce height; the state-18 call supplies 40 px.
        double reboundSpeed=Math.sqrt(2*JUMP_GRAVITY*40);
        long reboundTime=flightTime(0,reboundSpeed,JUMP_GRAVITY);
        long end=rebound+reboundTime;
        var flight=new SkillMotion(launch,airTime,0,0,power,-JUMP_GRAVITY);
        var bounce=new SkillMotion(rebound,reboundTime,0,0,reboundSpeed,-JUMP_GRAVITY);
        var poses=new ArrayList<>(List.of(
                new Pose(attempt,0,caught),new Pose(slam,caught,launch,null,false,null,0,2),
                new Pose(slam,launch,spin,null,false,flight,2,3),
                new Pose(slam,spin,landing,null,false,flight,3,7),
                new Pose(slam,landing,rebound,null,false,null,6,7),
                new Pose(slam,rebound,end,null,false,bounce,7,8)));
        String under=EFFECT+"jumpsuplexcrashunder.ani", over=EFFECT+"jumpsuplexcrashover.ani";
        addIdle(poses,actions,end,Math.max(end+120,landing+Math.max(clipDuration(under,lookup),clipDuration(over,lookup))));
        var visuals=List.of(
                new Visual(EFFECT+"jumpsuplexrollunder.ani","螺旋彗星落 · 回旋后层",REAR,spin,landing,0,0,flight,1,true),
                new Visual(EFFECT+"jumpsuplexrollover.ani","螺旋彗星落 · 回旋前层",FRONT,spin,landing,0,0,flight,1,true),
                new Visual(under,"螺旋彗星落 · 冲击后层",REAR,landing,-1,0,10),
                new Visual(over,"螺旋彗星落 · 冲击前层",3600,landing,-1,0,-10));
        return sequence(poses,visuals,"螺旋彗星落按当前等级的跳跃力和参考重力演示原地起跳、回旋与落地冲击；横向转向取决于施放输入");
    }

    private static Act1SkillSequence groundMiss(String attempt,int timeout,Map<String,String> actions,String label){
        var poses=new ArrayList<>(List.of(new Pose(attempt,0,timeout)));
        addIdle(poses,actions,timeout,timeout+180);
        return sequence(poses,List.of(),label+"在抓取时限结束后返回待机");
    }

    private static List<ResourceScript.Frame> frames(GameScript.Lookup lookup,Map<String,String> actions,
                                                       String action) throws IOException {
        return Act1SkillSequence.frames(CHARACTER,actions.get(action),lookup);
    }

    private static long clipDuration(String path,GameScript.Lookup lookup) throws IOException {
        String text=lookup.read(path);
        return text==null?0:new AnimationTimeline(ResourceScript.animation(text)).duration();
    }

    private static long flightTime(double height,double speed,double gravity){
        return (long)Math.ceil(1000*(speed+Math.sqrt(speed*speed+2*gravity*height))/gravity);
    }

    private static double reboundSpeed(GameScript.Lookup lookup) throws IOException {
        String configured=GameScript.value(GameScript.fields(lookup.read(CHARACTER)),"jump power");
        double power=470;
        try {
            double parsed=Double.parseDouble(configured);
            if(Double.isFinite(parsed)&&parsed>=0&&parsed<=2000)power=parsed;
        }catch(NumberFormatException invalidPower){/* Use stock Fighter attributes. */}
        // State 6 passes 250 through 0x488FE0 with the stock gravity-rate multiplier.
        return 4000*250/(2100+.0121*(Math.floor(power)-10000));
    }

    private static int lastAirborneFrame(List<ResourceScript.Frame> frames){
        for(int i=frames.size()-2;i>=0;i--)if(frames.get(i).delay()>=10000)return i;
        return -1;
    }

    private static long addJumpLanding(List<Pose> poses,List<ResourceScript.Frame> jump,int airborne,long landing){
        int start=airborne+1;
        long end=landing+Act1SkillSequence.duration(jump.subList(start,jump.size()),Integer.MAX_VALUE,null);
        if(end>landing)poses.add(new Pose(JUMP,landing,end,null,false,null,start));
        return end;
    }

    private static void addIdle(List<Pose> poses,Map<String,String> actions,long start,long end){
        if(actions.containsKey(IDLE))poses.add(new Pose(IDLE,start,end));
    }

    private static Act1SkillSequence sequence(List<Pose> poses,List<Visual> visuals,String note){
        return new Act1SkillSequence(List.copyOf(poses),visuals,List.of(),note);
    }

    private enum Grab {
        LEG("legsuplex",62,500,"空绞锤"),
        SUPLEX("suplex",9,300,"背摔"),
        SPIRE("spire",18,360,"螺旋彗星落");

        final String name,label,path;
        final int id,defaultTimeout;

        Grab(String name,int id,int defaultTimeout,String label){
            this.name=name;
            this.id=id;
            this.defaultTimeout=defaultTimeout;
            this.label=label;
            this.path="skill/fighter/"+name+".skl";
        }

        static Grab fromPath(String path){
            for(Grab grab:values())if(grab.path.equals(path))return grab;
            return null;
        }

        boolean matches(GameScript.Lookup lookup) throws IOException {
            return path.equals("skill/"+GameScript.catalog(lookup.read("skill/fighterskill.lst")).get(id));
        }

        String missKey(){return name+":miss";}
    }
}
