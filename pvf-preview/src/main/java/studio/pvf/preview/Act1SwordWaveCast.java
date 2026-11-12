package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Swordman state 20 (0x638640 / 0x643D00), passive objects 20001..20007 and 20024/20025. */
final class Act1SwordWaveCast {
    private static final String ROOT="character/swordman/",ACTION="etc motion:9";
    private static final String PARTICLES="passiveobject/character/swordman/particle/";
    // Stock Lv30 has 13 columns; leave a bounded margin for edited skills (three bursts per column).
    private static final int MAX_COLUMNS=16;

    private Act1SwordWaveCast() {}

    /** Caller verifies the ACT1 catalog binding for Swordman skill 20, 21 or 22. No held direction input. */
    static Act1SkillSequence find(int skill,String text,GameScript.Lookup lookup,Map<String,String> actions,int level) throws IOException {
        if(skill<20||skill>22)return null;
        String reference=actions.get(ACTION);
        if(reference==null||!GameScript.normalize(reference).endsWith("/wave.ani"))return null;
        var body=Act1SkillSequence.frames(ROOT+"swordman.chr",reference,lookup);
        if(body.size()<5||body.get(0).delay()<10000)return null;
        var fields=GameScript.fields(text);
        long cast=Act1SkillSequence.castingTime(fields,level,skill==20?200:skill==21?300:400);
        long windup=body.subList(1,4).stream().mapToLong(ResourceScript.Frame::delay).sum();
        int hold=(int)Math.max(0,cast-windup);
        long launch=Act1SkillSequence.duration(body,skill==20?3:4,hold);
        long recovered=cast+Act1SkillSequence.staticValue(fields,skill==21?1:0,500);
        if(launch>=recovered||recovered>30000)return null;
        String attachment=ROOT+"effect/animation/"+switch(skill){case 20->"normalwaveslash.ani";case 21->"icewave.ani";default->"firewavebig-sword-effect.ani";};
        long attachedLife=life(attachment,lookup);
        if(attachedLife<=0)return null;
        // 0x489400 / 0x4890B0 select static[1] with no direction held; static[2] is the input speed, not acceleration.
        SkillMotion actor=skill==20?new SkillMotion(0,recovered,Act1SkillSequence.staticValue(fields,1,100),0):null;
        var visuals=new ArrayList<Act1SkillSequence.Visual>();
        var emitters=new ArrayList<SkillParticleEmitter>();
        visuals.add(new Act1SkillSequence.Visual(attachment,"波动剑 · 施放刀光",3501,hold,hold+attachedLife,skill==20?0:59,0,actor,1,true));
        if(skill==20){
            var motions=Act1SkillSequence.objectMotions(lookup,20001);
            if(motions.size()!=1)return null;
            long life=life(motions.get(0),lookup);if(life<=0)return null;
            double scale=Act1SkillSequence.levelValue(fields,level,1,100,1,400)/100.0;
            // Its ANI already contains the wave's forward motion. The object remains at its launch location.
            visuals.add(new Act1SkillSequence.Visual(motions.get(0),"地裂·波动剑 · 地面波动",3600,launch,launch+life,actor.offsetAt(launch),0,null,scale,true));
        }else if(skill==21){
            int count=Act1SkillSequence.levelValue(fields,level,1,5,0,1000);
            int interval=Act1SkillSequence.staticValue(fields,0,100);
            int linger=Act1SkillSequence.levelValue(fields,level,0,200,0,10000);
            if(count<1||count>MAX_COLUMNS||interval<1||interval>1000)return null;
            var paths=new ArrayList<String>();
            for(int id=20002;id<=20007;id++){
                var motions=Act1SkillSequence.objectMotions(lookup,id);
                if(motions.size()!=1||life(motions.get(0),lookup)<=0)return null;
                paths.add(motions.get(0));
            }
            for(String particle:List.of("icewaveparticlesmoke.ptl","icewaveparticle.ptl","icewaveparticlestar.ptl")){
                if(lookup.read(PARTICLES+particle)==null)return null;
            }
            double x=100,y=-4;
            for(int n=0;n<count;n++){
                String path=paths.get(n%6);
                var frames=ResourceScript.animation(lookup.read(path)).frames();
                if(frames.size()<4||linger<frames.get(0).delay())return null;
                int delay=linger-frames.get(0).delay();
                long born=launch+(long)n*interval;
                long shatter=born+linger+frames.get(2).delay();
                long end=born+Act1SkillSequence.duration(frames,frames.size(),null)-frames.get(1).delay()+delay;
                double scale=1.5+n*.075;
                var style=new Act1SkillSequence.VisualStyle(null,0,List.of(),Map.of(1,new Act1SkillSequence.FrameOverride(0,0,delay)));
                visuals.add(new Act1SkillSequence.Visual(path,"冰刃·波动剑 · 冰柱 "+(n+1),3600+n,born,end,x,y,null,scale,true,style));
                emitters.add(burst("icewaveparticlesmoke.ptl","冰刃 · 起柱烟雾",born,x,y,0,scale));
                double height=20-(int)(n*-1.5);
                emitters.add(burst("icewaveparticle.ptl","冰刃 · 碎冰",shatter,x,y,height,scale));
                // The native star burst is not passed through the ice-column image scaling loop.
                emitters.add(burst("icewaveparticlestar.ptl","冰刃 · 碎裂星光",shatter,x,y,height,1));
                x+=21-(int)(n*-1.2);
                y+=n%2==0?8:-8;
            }
        }else{
            var fire=Act1SkillSequence.objectMotions(lookup,20024);
            var blast=Act1SkillSequence.objectMotions(lookup,20025);
            if(fire.size()!=2||blast.size()!=2)return null;
            long fireLife=life(fire.get(0),lookup),blastLife=life(blast.get(0),lookup);
            if(fireLife<=0||blastLife<=0||life(fire.get(1),lookup)<=0||life(blast.get(1),lookup)<=0)return null;
            var backFrames=ResourceScript.animation(lookup.read(blast.get(1))).frames();
            if(backFrames.size()<5)return null;
            long explosion=launch+fireLife;
            visuals.add(new Act1SkillSequence.Visual(fire.get(1),"爆炎·波动剑 · 火浪后层",3599,launch,explosion,0,0));
            visuals.add(new Act1SkillSequence.Visual(fire.get(0),"爆炎·波动剑 · 火浪",3600,launch,explosion,0,0));
            visuals.add(new Act1SkillSequence.Visual(blast.get(1),"爆炎·波动剑 · 爆炸后层",3599,explosion,explosion+Math.min(blastLife,Act1SkillSequence.duration(backFrames,4,null)),360,0));
            visuals.add(new Act1SkillSequence.Visual(blast.get(0),"爆炎·波动剑 · 末端爆炸",3600,explosion,explosion+blastLife,360,0));
        }
        long finish=recovered;
        for(var visual:visuals)finish=Math.max(finish,visual.end());
        for(var emitter:emitters)finish=Math.max(finish,emitter.start()+emitter.emitFor()+SkillParticleEmitter.tail(ParticleScript.parse(lookup.read(emitter.path()))));
        var poses=new ArrayList<Act1SkillSequence.Pose>();
        long bodyEnd = skill == 20 ? Math.min(recovered, Act1SkillSequence.duration(body, body.size(), hold)) : recovered;
        poses.add(new Act1SkillSequence.Pose(ACTION, 0, bodyEnd, hold, false, actor));
        // State 20 times out separately from the ANI. The last release pose keeps sliding until then.
        if (skill == 20 && recovered > bodyEnd) {
            poses.add(new Act1SkillSequence.Pose(ACTION, bodyEnd, recovered, null, false, actor, body.size() - 1, body.size()));
        }
        if(finish>recovered&&actions.containsKey("waiting motion"))poses.add(new Act1SkillSequence.Pose("waiting motion",recovered,finish,null,false,actor));
        String note=switch(skill){
            case 20->"地裂·波动剑演示未按方向键的施放位移；地面波动按当前等级缩放，命中伤害不在无目标预览中执行";
            case 21->"冰刃·波动剑演示普通平地上的连续冰柱、起柱烟雾和碎冰；冰柱数量与停留时间取当前等级，目标冻结不在无目标预览中执行";
            default->"爆炎·波动剑演示火浪结束后前方的末端爆炸，刀光使用阿修罗默认大小；目标受击不在无目标预览中执行";
        };
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.copyOf(emitters),note);
    }

    private static SkillParticleEmitter burst(String file,String label,long start,double x,double y,double z,double scale){
        return new SkillParticleEmitter(PARTICLES+file,label,start,1,0,x,y,z,Double.NaN,scale,true,1);
    }

    private static long life(String path,GameScript.Lookup lookup) throws IOException {
        String text=lookup.read(path);if(text==null)return -1;
        var ani=ResourceScript.animation(text);long duration=new AnimationTimeline(ani).duration();
        return ani.loop()||duration<=0||duration>10000?-1:duration;
    }
}
