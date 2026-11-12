package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Common state 6, Backstep branch. CHR uses a held airborne frame immediately before landing. */
final class Act1Backstep {
    private static final double GRAVITY=1500, RETREAT_SPEED=286;
    private static final String DUST="common/commoneffect/particle/dustdownsmall.ptl";

    private Act1Backstep() {}

    static Act1SkillSequence find(String job,String text,GameScript.Lookup lookup,Map<String,String> actions) throws IOException {
        String character="character/"+job+"/"+job+".chr";
        String jump=actions.get("jump motion");
        if(jump==null)return null;
        String path=GameScript.resolve(character,jump,lookup);
        if(path==null)return null;
        var frames=ResourceScript.animation(lookup.read(path)).frames();
        int airborne=-1;
        for(int i=0;i+1<frames.size();i++){
            if(frames.get(i).delay()>=10000)airborne=i;
        }
        if(airborne<0)return null;
        int power=GameScript.integer(GameScript.value(GameScript.fields(text),"static data"),0);
        if(power<=0||power>2000)return null;
        double jumpAbility=jumpAbility(GameScript.value(GameScript.fields(lookup.read(character)),"jump power"));
        double verticalSpeed=4000*power/(2100+(.0121*(Math.floor(jumpAbility)-10000)));
        int landing=(int)Math.ceil(2000*verticalSpeed/GRAVITY);
        // End the quadratic at the first millisecond on the ground.
        var motion=new SkillMotion(0,landing,-RETREAT_SPEED,0,verticalSpeed,-GRAVITY);
        long recovery=frames.subList(airborne+1,frames.size()).stream().mapToLong(ResourceScript.Frame::delay).sum();
        var poses=new ArrayList<Act1SkillSequence.Pose>();
        long landed=landing+recovery;
        poses.add(new Act1SkillSequence.Pose("jump motion",0,landed,landing,false,motion,airborne));
        String dust=lookup.read(DUST);
        var particles=dust==null?List.<SkillParticleEmitter>of()
                :List.of(new SkillParticleEmitter(DUST,"后跳 · 落地扬尘",landing,1,0,motion.offsetAt(landing),0,0,
                        Double.NaN,1,true));
        long end=landed+220;
        if(dust!=null)end=Math.max(end,landing+1+SkillParticleEmitter.tail(ParticleScript.parse(dust)));
        if(actions.containsKey("waiting motion")){
            poses.add(new Act1SkillSequence.Pose("waiting motion",landed,end,null,false,motion));
        }
        return new Act1SkillSequence(List.copyOf(poses),List.of(),particles,
                "后跳以默认跳跃能力和普通地面演示；角色朝向保留，落地扬尘取普通地面资源");
    }

    private static double jumpAbility(String value){
        try{
            double parsed=Double.parseDouble(value);
            if(Double.isFinite(parsed)&&parsed>=0&&parsed<=2000)return parsed;
        }catch(NumberFormatException ignored){}
        return 100;
    }
}
