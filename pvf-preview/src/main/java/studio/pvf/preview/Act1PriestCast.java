package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import studio.pvf.preview.Act1SkillSequence.Visual;

/** Priest state 10, with throw groups selected by the skill dispatch in 0x622CB0. */
final class Act1PriestCast {
    private static final String SKILLS="skill/priest/";
    private static final String REVENGE_PATH=SKILLS+"revengeoflight.skl";
    private static final String REVENGE_BUFF="revengeoflight:buff";
    private static final String EFFECT="character/priest/effect/animation/";
    private static final String COMMON="common/commoneffect/animation/";
    private static final int BACK=499, FRONT=3501, FLOOR=300;
    private static final long STATUS_SAMPLE=1800;

    private enum Cast {
        CONVERT("changehptomp",10,4,0,"化魔","预览生命值足以施放时的转换效果"),
        PERSONAL("hpmaxuppersonal",19,3,1,"天籁之音","以施法者为示例目标"),
        EMBLEM("emblemofsafeguard",20,3,1,"守护徽章","以施法者为示例目标，分担伤害需要队友受击"),
        FOUNTAIN("fountainoflife",21,3,1,"生命源泉","演示施法者获得恢复效果，复苏需要目标生命值降为零"),
        REVENGE("revengeoflight",23,3,1,"光之复仇","演示获得增益并在一次魔法受击触发时向攻击者降下反击落雷"),
        PARTY("hpmaxupparty",24,4,1,"天堂谐音","播放施放者周围的范围效果"),
        HEAL_WIND("healwind",26,4,1,"圣愈之风","以施法者为示例目标，地面光效范围取技能配置"),
        GLORIOUS("gloriousbless",45,4,1,"荣誉祝福","以施法者为示例目标，显示一段持续光效"),
        CURE("cure",52,3,1,"净化","净化的目标光效仅在消除异常状态时触发"),
        HAND("grasphandofanger",64,3,0,"恶魔之手","播放恶魔之手与原始对象图层；受击效果需要目标");

        final String path,label,note;
        final int id,group,recoveryColumn;

        Cast(String name,int id,int group,int recoveryColumn,String label,String note){
            this.path=SKILLS+name+".skl";
            this.id=id;
            this.group=group;
            this.recoveryColumn=recoveryColumn;
            this.label=label;
            this.note=note;
        }
    }

    private Act1PriestCast() {}

    static List<GameEffectRecipes.Effect> choices(String path,GameScript.Lookup lookup,boolean act1) throws IOException {
        if(!act1||!path.equals(REVENGE_PATH)
                ||!path.equals("skill/"+GameScript.catalog(lookup.read("skill/priestskill.lst")).get(23)))return List.of();
        return List.of(new GameEffectRecipes.Effect(REVENGE_BUFF,"光之复仇 · 仅增益","",path,"展示增益施放及持续光效"));
    }

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,
                                  Act1BuffCast.ActorHeight height,String choice) throws IOException {
        if(!path.startsWith(SKILLS))return null;
        if(!choice.isEmpty()&&!(path.equals(REVENGE_PATH)&&choice.equals(REVENGE_BUFF)))return null;
        Cast cast=null;
        for(var candidate:Cast.values())if(path.equals(candidate.path)){cast=candidate;break;}
        if(cast==null||!path.equals("skill/"+GameScript.catalog(lookup.read("skill/priestskill.lst")).get(cast.id)))return null;
        String prepare="throw motion "+cast.group+"-1",release="throw motion "+cast.group+"-2";
        if(!actions.containsKey(prepare)||!actions.containsKey(release))return null;
        var fields=GameScript.fields(text);
        long charge=Act1SkillSequence.castingTime(fields,level,500);
        long recovered=charge+Math.max(1,Act1SkillSequence.staticValue(fields,cast.recoveryColumn,500));
        var clips=new Clips(lookup,cast.label,charge,recovered);
        String note=cast.note;
        switch(cast){
            case CONVERT -> {
                String folder="changehptomp/";
                clips.prepare(folder+"cast1.ani",FRONT+1,0);
                clips.prepare(folder+"cast2.ani",FRONT,0);
                clips.prepare(folder+"cast3.ani",FRONT,0);
                clips.release(folder+"blast1.ani",FRONT);
                clips.release(folder+"blast2.ani",BACK);
                clips.release(folder+"blast3.ani",BACK+1);
                clips.release(folder+"blast4.ani",FRONT+1);
            }
            case PERSONAL -> {
                clips.prepare("hpmaxup/personalcast.ani",FRONT,0);
                clips.release("hpmaxup/personalblast.ani",FRONT);
            }
            case EMBLEM -> {
                clips.prepare("emblemofsafeguard/cast1.ani",FRONT,0);
                clips.prepare("emblemofsafeguard/cast2.ani",FRONT+1,0);
                clips.release("emblemofsafeguard/blast.ani",FRONT);
            }
            case FOUNTAIN -> {
                int actorHeight=height.read("priest");
                clips.prepare("fountainoflife/cast.ani",FRONT,-actorHeight);
                clips.activate(40,actorHeight);
                clips.add(COMMON+"priestslowhealb.ani","持续恢复后层",BACK,charge,charge+STATUS_SAMPLE,0,-actorHeight/2,1,true);
                clips.add(COMMON+"priestslowhealf.ani","持续恢复前层",FRONT,charge,charge+STATUS_SAMPLE,0,-actorHeight/2,1,true);
                clips.add(EFFECT+"fountainoflife/target.ani","目标光效",FRONT+1,charge,charge+STATUS_SAMPLE,0,-actorHeight-30,1,true);
            }
            case REVENGE -> {
                String folder="revengeoflight/";
                clips.prepare(folder+"cast.ani",FRONT,0);
                clips.activate(39,height.read("priest"));
                long loopStart=clips.add(EFFECT+folder+"buffstart1.ani","生效后层",BACK,charge,-1,0,0,1,true);
                clips.add(EFFECT+folder+"buffstart2.ani","生效前层",FRONT,charge,loopStart,0,0,1,true);
                clips.add(EFFECT+folder+"buffloop.ani","持续光效",FRONT,loopStart,loopStart+STATUS_SAMPLE,0,0,1,true);
                if(choice.equals(REVENGE_BUFF))note="光之复仇演示施法者获得增益与持续光效";
                else {
                    // 0x54AC10 calls #24012 with the attacker as the origin on the magic-damage trigger.
                    var bolt=Act1SkillSequence.objectMotions(lookup,24012);
                    if(bolt.isEmpty())return null;
                    long triggered=Math.max(recovered,loopStart)+350;
                    double attackerX=130;
                    int warningSpeed=Act1SkillSequence.staticValue(fields,4,100);
                    long strike=Act1SkillSequence.targetWarning(lookup,clips.visuals,"光之复仇 · 反击预警",triggered,attackerX,warningSpeed);
                    long boltEnd=clips.add(bolt.get(0),"反击落雷",3600,strike,-1,attackerX,0,1,true);
                    clips.end=Math.max(clips.end,thunderParticles(lookup,clips.particles,bolt.get(0),"光之复仇 · 落雷余光",strike,attackerX));
                    Act1SwordAsuraSupplement.addTarget(clips.visuals,lookup,"stay","光之复仇 · 哥布林攻击者",0,strike,attackerX,null);
                    Act1SwordAsuraSupplement.addTarget(clips.visuals,lookup,"damage1","光之复仇 · 反击命中",strike,boltEnd,attackerX,null);
                    Act1SwordAsuraSupplement.addTarget(clips.visuals,lookup,"stay","光之复仇 · 哥布林攻击者",boltEnd,clips.end,attackerX,null);
                }
            }
            case PARTY -> {
                clips.release("hpmaxup/partyblast.ani",FRONT);
                double scale=Math.max(1,Act1SkillSequence.staticValue(fields,0,900))/256.0;
                clips.add(EFFECT+"hpmaxup/partyfloor.ani","地面光效",FLOOR,charge,-1,0,0,scale,true);
            }
            case HEAL_WIND -> {
                String folder="healwind/";
                clips.prepare(folder+"cast.ani",FRONT,0);
                clips.release(folder+"buff1.ani",BACK);
                clips.release(folder+"buff2.ani",FRONT);
                clips.release(folder+"buff3.ani",FRONT+1);
                String floor=EFFECT+folder+"floor.ani";
                var frames=clips.frames(floor);
                if(!frames.isEmpty()){
                    // 0x627770 normalizes the last frame's X scale, then applies the configured range.
                    double authoredScale=frames.get(frames.size()-1).scaleX();
                    double scale=authoredScale>0?Act1SkillSequence.staticValue(fields,0,900)*.005/authoredScale:1;
                    clips.add(floor,"地面光效",FLOOR,charge,-1,0,0,scale,true);
                }
            }
            case GLORIOUS -> {
                String folder="gloriousbless/";
                clips.prepare(folder+"cast1.ani",FRONT,0);
                clips.prepare(folder+"cast2.ani",FRONT+1,0);
                clips.release(folder+"blast1.ani",FRONT);
                clips.release(folder+"blast2.ani",FRONT+1);
                clips.add(EFFECT+folder+"buff.ani","持续光效",FRONT+2,charge,charge+STATUS_SAMPLE,0,-height.read("priest"),1,true);
            }
            case CURE -> {
                clips.prepare("curecast1.ani",FRONT,0);
                clips.release("curecast2.ani",FRONT);
            }
            case HAND -> {
                String folder="grasphandofanger/";
                clips.prepare(folder+"cast1.ani",FRONT+1,0);
                clips.prepare(folder+"cast2.ani",FRONT,0);
                clips.release(folder+"blast1.ani",FRONT+1);
                clips.release(folder+"blast2.ani",FRONT);
                clips.hand();
            }
        }
        if(clips.visuals.isEmpty())return null;
        return new Act1SkillSequence(Act1SkillSequence.castPoses(actions,prepare,release,charge,recovered,clips.end,false),
                List.copyOf(clips.visuals),List.copyOf(clips.particles),note);
    }

    /** CNThunderBolt adds the shared stock particle burst on animation frame 1 (0x5C7B30). */
    static long thunderParticles(GameScript.Lookup lookup,List<SkillParticleEmitter> emitters,
                                 String bolt,String label,long strike,double x) throws IOException {
        String path="passiveobject/common/particle/thunderboltparticle.ptl";
        String text=lookup.read(path);
        if(text==null)return strike;
        var frames=ResourceScript.animation(lookup.read(bolt)).frames();
        if(frames.size()<2)return strike;
        long burst=strike+frames.get(0).delay();
        emitters.add(new SkillParticleEmitter(path,label,burst,1,0,x,0));
        return burst+1+SkillParticleEmitter.tail(ParticleScript.parse(text));
    }

    private static final class Clips {
        private final GameScript.Lookup lookup;
        private final String label;
        private final long charge;
        private final List<Visual> visuals=new ArrayList<>();
        private final List<SkillParticleEmitter> particles=new ArrayList<>();
        private long end;

        Clips(GameScript.Lookup lookup,String label,long charge,long end){
            this.lookup=lookup;
            this.label=label;
            this.charge=charge;
            this.end=end;
        }

        void prepare(String path,int layer,double y) throws IOException {
            add(EFFECT+path,"施法",layer,0,charge,0,y,1,true);
        }

        void release(String path,int layer) throws IOException {
            add(EFFECT+path,"施放",layer,charge,-1,0,0,1,true);
        }

        void activate(int icon,int height) throws IOException {
            end=Math.max(end,Act1BuffCast.activate(label,icon,charge,height,lookup,visuals,particles));
        }

        List<ResourceScript.Frame> frames(String path) throws IOException {
            String text=lookup.read(path);
            return text==null?List.of():ResourceScript.animation(text).frames();
        }

        long add(String path,String phase,int layer,long start,long stop,double x,double y,double scale,boolean mirror) throws IOException {
            if(path==null||stop==start)return start;
            var frames=frames(path);
            if(frames.isEmpty())return start;
            long finish=stop<0?start+Act1SkillSequence.duration(frames,frames.size(),null):stop;
            if(finish<=start)return start;
            visuals.add(new Visual(path,label+" · "+phase,layer,start,finish,x,y,null,scale,mirror));
            end=Math.max(end,finish);
            return finish;
        }

        void hand() throws IOException {
            String object=GameEffectRecipes.passiveObject(lookup,24003);
            if(object==null)return;
            var fields=GameScript.fields(lookup.read(object));
            String main=GameScript.resolve(object,GameScript.value(fields,"basic motion"),lookup);
            long until=add(main,"恶魔之手",3600,charge,-1,60,-60,1,true);
            if(until==charge)return;
            var layers=GameScript.values(fields,"add object effect");
            for(int i=1;i+1<layers.size()&&i<=5;i+=2){
                String clip=GameScript.resolve(object,layers.get(i),lookup);
                int layer=GameScript.integer(layers.get(i+1),1);
                add(clip,"手部光效",3600+layer,charge,until,60,-60,1,true);
            }
        }
    }
}
