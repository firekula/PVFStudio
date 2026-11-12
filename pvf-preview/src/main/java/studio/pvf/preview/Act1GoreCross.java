package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** ACT1 Swordman state 31, sub_638640 / sub_643D00; free-flight object 20028. */
final class Act1GoreCross {
    private static final String CHARACTER="character/swordman/swordman.chr";
    private static final String ACTION="etc motion:25";
    private static final String EFFECT="character/swordman/effect/animation/gorecross/";
    private static final String MOVEMENT="character/swordman/particle/gorecross.ptl";

    private Act1GoreCross() {}

    /** The caller gates the ACT1 skill catalog binding (Swordman skill 64). */
    static Act1SkillSequence find(GameScript.Lookup lookup,Map<String,String> actions) throws IOException {
        String reference=actions.get(ACTION);
        if(reference==null||!GameScript.normalize(reference).endsWith("/gorecross.ani"))return null;
        var body=Act1SkillSequence.frames(CHARACTER,reference,lookup);
        if(body.size()<7)return null;
        String first=lookup.read(EFFECT+"slash1.ani"),second=lookup.read(EFFECT+"slash2.ani");
        String movement=lookup.read(MOVEMENT);
        String object=GameEffectRecipes.passiveObject(lookup,20028);
        if(first==null||second==null||movement==null||object==null)return null;
        var slash1=ResourceScript.animation(first);var slash2=ResourceScript.animation(second);
        long firstEnd=new AnimationTimeline(slash1).duration(),secondEnd=new AnimationTimeline(slash2).duration();
        if(firstEnd<=0||secondEnd<=0||slash2.loop())return null;
        // Body frame 6 arms the event; the second attached slash's completion launches exactly one object.
        long launch=Math.max(Act1SkillSequence.duration(body,6,null),secondEnd);
        var fields=GameScript.fields(lookup.read(object));
        String main=GameScript.resolve(object,GameScript.value(fields,"basic motion"),lookup);
        var extra=GameScript.values(fields,"add object effect");
        if(main==null||extra.size()!=3||GameScript.integer(extra.get(0),0)!=1
                ||GameScript.integer(extra.get(2),0)!=-1)return null;
        String under=GameScript.resolve(object,extra.get(1),lookup);
        if(under==null)return null;
        var base=ResourceScript.animation(lookup.read(main));
        long life=new AnimationTimeline(base).duration();
        if(life<=0||life>10000||base.loop()
                ||!GameScript.values(fields,"object destroy condition").contains("[on end of animation]"))return null;
        var particle=ParticleScript.parse(movement);var axis=particle.axes().get(0);
        // This PTL is a trajectory, not a particle emitter. Unsupported edited paths retain the old partial preview.
        if(!axis.type().equals("straight line")||axis.start()<=0||axis.start()>10000||axis.end()!=0
                ||particle.term()<=0||particle.term()>10000||!particle.changing().equals("keep last value")
                ||!particle.axes().get(1).type().equals("stop")||!particle.axes().get(2).type().equals("stop")
                ||particle.gravity()!=0||!particle.angles().equals(new ParticleScript.Vec(0,0,0)))return null;
        var flight=new SkillMotion(launch,Math.round(particle.term()),axis.start(),-axis.start()*1000/particle.term());
        long finish=launch+life;
        var visuals=List.of(
                new Act1SkillSequence.Visual(EFFECT+"slash1.ani","十字斩刀光",3501,0,firstEnd,0,0),
                new Act1SkillSequence.Visual(EFFECT+"slash2.ani","十字斩刀光",3502,0,secondEnd,0,0),
                new Act1SkillSequence.Visual(under,"十字斩 · 飞行血十字底层",3599,launch,finish,85,-82,flight,1,true),
                new Act1SkillSequence.Visual(main,"十字斩 · 飞行血十字",3600,launch,finish,85,-82,flight,1,true));
        long actorEnd=Act1SkillSequence.duration(body,body.size(),null);
        if(actorEnd<=0||actorEnd>30000)return null;
        long end=Math.max(actorEnd,Math.max(finish,firstEnd));
        var poses=new ArrayList<Act1SkillSequence.Pose>();
        poses.add(new Act1SkillSequence.Pose(ACTION,0,actorEnd));
        if(end>actorEnd&&actions.containsKey("waiting motion"))poses.add(new Act1SkillSequence.Pose("waiting motion",actorEnd,end));
        return new Act1SkillSequence(List.copyOf(poses),visuals,List.of(),
                "十字斩保留双刀光，并在第二刀光结束后发出减速血十字；目标受击、出血及伤害不在无目标预览中执行");
    }
}
