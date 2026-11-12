package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Swordman states 19/25/28 (0x638640, 0x643D00); CNVaneSlash 0x598420/0x5984E0. */
final class Act1SwordTechniqueCast {
    static final String VANE_PATH="skill/swordman/vaneslash.skl",MOMENTARY_PATH="skill/swordman/momentaryslash.skl";
    static final String THRUST_PATH="skill/swordman/dashattackmultihit.skl";
    static final String VANE_MISS="vaneslash:miss",KATANA="momentary:katana",LIGHTSABER="momentary:lightsaber";
    private static final String ROOT="character/swordman/",EFFECT=ROOT+"effect/animation/";
    private Act1SwordTechniqueCast(){}

    static List<GameEffectRecipes.Effect> choices(String path,GameScript.Lookup lookup,boolean act1)throws IOException{
        if(!act1)return List.of();
        if(path.equals(VANE_PATH)&&binding(path,58,lookup))return List.of(new GameEffectRecipes.Effect(VANE_MISS,"裂波斩 · 未命中","",path,"未抓到目标时只完成上挑，不进入下斩和裂波"));
        if(path.equals(MOMENTARY_PATH)&&binding(path,9,lookup))return List.of(
                new GameEffectRecipes.Effect(KATANA,"拔刀斩 · 太刀追击","",path,"试用最低级太刀，演示达到精通要求后追加输入的红色追击"),
                new GameEffectRecipes.Effect(LIGHTSABER,"拔刀斩 · 光剑追击","",path,"试用最低级光剑，演示达到精通要求后追加输入的白色追击"));
        return List.of();
    }

    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,String choice)throws IOException{
        return find(path,text,lookup,actions,level,choice,null);
    }
    static Act1SkillSequence find(String path,String text,GameScript.Lookup lookup,Map<String,String> actions,int level,String choice,String weaponPath)throws IOException{
        int id=path.equals(VANE_PATH)?58:path.equals(MOMENTARY_PATH)?9:path.equals(THRUST_PATH)?11:0;
        if(id==0||!binding(path,id,lookup))return null;
        if(id==11)return choice.isEmpty()?thrust(text,lookup,actions):null;
        if(id==58){
            int type=weaponType(weaponPath,lookup);if(type<0||type>5)return null;
            return choice.isEmpty()||choice.equals(VANE_MISS)?vane(lookup,actions,choice.equals(VANE_MISS),type):null;
        }
        return choice.isEmpty()||choice.equals(KATANA)||choice.equals(LIGHTSABER)?momentary(lookup,actions,choice):null;
    }

    private static Act1SkillSequence momentary(GameScript.Lookup lookup,Map<String,String> actions,String choice)throws IOException{
        var body=body(actions,"etc motion:8","momentaryslash.ani",lookup);
        if(body.size()<12)return null;
        long strike=duration(body,2),follow=duration(body,8),end=duration(body,body.size());
        if(strike<=0||end>10000)return null;
        var poses=new ArrayList<Act1SkillSequence.Pose>();var visuals=new ArrayList<Act1SkillSequence.Visual>();
        boolean twice=!choice.isEmpty();
        poses.add(new Act1SkillSequence.Pose("etc motion:8",0,twice?follow:end));
        if(!slash(visuals,lookup,"blue_ldodge",strike,twice?follow:end))return null;
        if(twice){
            long secondEnd=follow+end-body.get(0).delay();
            poses.add(new Act1SkillSequence.Pose("etc motion:8",follow,secondEnd,null,false,null,1));
            if(!slash(visuals,lookup,choice.equals(KATANA)?"red_ldodge":"white_ldodge",follow+body.get(1).delay(),secondEnd))return null;
        }
        String note=twice?"拔刀斩演示已学武器奥义、精通达到追击条件并装备"+(choice.equals(KATANA)?"太刀":"光剑")+"时，在首击第 8 帧追加输入的追击；目标出血或感电由实战提供":"拔刀斩演示单次施放的前后双层刀光；太刀／光剑精通追击需额外输入";
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),note);
    }

    /** State 25: no direction held, static[0]/[1]; attachment event at body frame 1. */
    private static Act1SkillSequence thrust(String text,GameScript.Lookup lookup,Map<String,String> actions)throws IOException{
        var body=body(actions,"etc motion:11","dashattackmultihit.ani",lookup);if(body.size()!=8)return null;
        long end=duration(body,body.size()),event=duration(body,1);
        var data=GameScript.values(GameScript.fields(text),"static data");if(data.size()<2)return null;
        int speed=GameScript.integer(data.get(0),-1),acceleration=GameScript.integer(data.get(1),0);
        if(speed<0||speed>2000||acceleration>=0||acceleration < -10000||end<=event||end>10000)return null;
        long travel=Math.min(end,Math.round(-1000.0*speed/acceleration));
        var motion=new SkillMotion(0,travel,speed,acceleration);var visuals=new ArrayList<Act1SkillSequence.Visual>();
        for(int n=1;n<=2;n++){
            String path=EFFECT+"dashattackmultihit"+n+".ani";long life=life(path,lookup);if(life<=0)return null;
            visuals.add(new Act1SkillSequence.Visual(path,"连突刺 · "+(n==1?"后层刀光":"前层刀光"),n==1?3499:3501,event,Math.min(end,event+life),0,0,motion,1,true));
        }
        return new Act1SkillSequence(List.of(new Act1SkillSequence.Pose("etc motion:11",0,end,null,false,motion)),List.copyOf(visuals),List.of(),"连突刺演示前冲攻击后的追加突刺与未按方向键的减速滑行；光剑精通触发的额外光剑气尚未接入");
    }

    private static boolean slash(List<Act1SkillSequence.Visual> visuals,GameScript.Lookup lookup,String color,long start,long actorEnd)throws IOException{
        String[] variants={"none_under",color+"_under","none_upper",color+"_upper"};
        int[] layers={3498,3499,3501,3502};
        long lifetime=life(EFFECT+"momentaryslash/momentaryslash_none_under.ani",lookup);
        if(lifetime<=0)return false;
        for(int i=0;i<variants.length;i++){
            String path=EFFECT+"momentaryslash/momentaryslash_"+variants[i]+".ani";
            if(life(path,lookup)<=0)return false;
            // Native removes all eight attachments when the none-under layer finishes or state exits.
            visuals.add(new Act1SkillSequence.Visual(path,"拔刀斩 · "+(color.startsWith("red")?"太刀追击":color.startsWith("white")?"光剑追击":"首击")+(i<2?"后层":"前层"),layers[i],start,Math.min(actorEnd,start+lifetime),0,0));
        }
        return true;
    }

    private static Act1SkillSequence vane(GameScript.Lookup lookup,Map<String,String> actions,boolean miss,int weaponType)throws IOException{
        var attempt=body(actions,"etc motion:14","vaneslashtry.ani",lookup);
        if(attempt.size()!=5)return null;
        long release=duration(attempt,attempt.size());if(release<=0||release>5000)return null;
        var poses=new ArrayList<Act1SkillSequence.Pose>();var visuals=new ArrayList<Act1SkillSequence.Visual>();
        poses.add(new Act1SkillSequence.Pose("etc motion:14",0,release));
        double upScale=weaponType==3?1:weaponType==0?.8:.9,downScale=weaponType==3?1.4:weaponType==0?1.1:1.3;
        for(String name:List.of("upper","dust")){
            String path=EFFECT+"vaneslash/"+name+".ani";long duration=life(path,lookup);if(duration<=0)return null;
            visuals.add(new Act1SkillSequence.Visual(path,"裂波斩 · "+(name.equals("upper")?"上挑刀光":"上挑扬尘"),name.equals("upper")?3501:3502,0,miss?Math.min(duration,release):duration,0,0,null,name.equals("upper")?upScale:1,true));
        }
        if(miss)return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),"裂波斩演示未抓到目标：上挑结束即回到待机，不生成下斩和裂波");
        var smash=body(actions,"etc motion:15","vaneslash.ani",lookup);
        if(smash.size()!=7)return null;
        long duration=duration(smash,smash.size()),end=release+duration;
        var motions=Act1SkillSequence.objectMotions(lookup,20022);
        if(motions.size()!=2)return null;
        var wave=frames(motions.get(0),lookup);if(wave.size()!=19||wave.get(12).delay()<10000)return null;
        long tail=duration(wave,wave.size())-duration(wave,13),exit=duration-tail;
        long firstLoop=duration(wave,12),period=duration(wave,12)-duration(wave,9);
        if(exit<firstLoop||period<=0||duration>10000)return null;
        var settings=GameScript.values(GameScript.fields(lookup.read("data/skill/swordman.sd")),"int");
        int x=setting(settings,"vs hit x offset"),z=setting(settings,"vs hit z offset");
        if(x==Integer.MIN_VALUE||z==Integer.MIN_VALUE||Math.abs(x)>2000||z<0||z>2000)return null;
        poses.add(new Act1SkillSequence.Pose("etc motion:15",release,end));
        String smashPath=EFFECT+"vaneslash/smash.ani";long smashLife=life(smashPath,lookup);if(smashLife<=0)return null;
        var smashOverrides=new HashMap<Integer,Act1SkillSequence.FrameOverride>();
        var slashFrames=frames(smashPath,lookup);
        for(int i=0;i<slashFrames.size();i++)smashOverrides.put(i,new Act1SkillSequence.FrameOverride(0,0,null,i<3?upScale:downScale,i<3?upScale:downScale*.8));
        var smashStyle=new Act1SkillSequence.VisualStyle(null,0,List.of(),smashOverrides);
        visuals.add(new Act1SkillSequence.Visual(smashPath,"裂波斩 · 下斩刀光",3501,release,Math.min(end,release+smashLife),0,0,null,1,true,smashStyle));
        long normalLife=life(motions.get(1),lookup);if(normalLife<=0)return null;
        visuals.add(new Act1SkillSequence.Visual(motions.get(1),"裂波斩 · 裂波底层",3599,release,Math.min(end,release+normalLife),x,1-z));
        visuals.add(new Act1SkillSequence.Visual(motions.get(0),"裂波斩 · 裂波起势",3600,release,release+firstLoop,x,1-z));
        if(exit>firstLoop)visuals.add(new Act1SkillSequence.Visual(motions.get(0),"裂波斩 · 裂波旋转",3600,release+firstLoop,release+exit,x,1-z,null,1,true,slice(wave,9,12,period)));
        visuals.add(new Act1SkillSequence.Visual(motions.get(0),"裂波斩 · 裂波消散",3600,release+exit,end,x,1-z,null,1,true,slice(wave,13,wave.size(),0)));
        vaneTarget(visuals, lookup, duration(attempt,2), release+duration(smash,3), end, x, z);
        return new Act1SkillSequence(List.copyOf(poses),List.copyOf(visuals),List.of(),"裂波斩以哥布林演示上挑抓取、随手升空、下斩命中和裂波");
    }

    private static void vaneTarget(List<Act1SkillSequence.Visual> visuals, GameScript.Lookup lookup,
                                   long grab, long impact, long end, int x, int z) throws IOException {
        // 0x539650 interpolates the grabbed enemy to the skill offset, with an arc in Z.
        double startX = 64;
        double holdZ = Math.max(10, z - Act1SwordAsuraSupplement.goblinHeight(lookup) / 2);
        double apex = holdZ + 70;
        long half = (impact - grab) / 2;
        double ascent = half / 1000.0;
        double descent = (impact - grab - half) / 1000.0;
        double vx = (x - startX) * 1000 / (impact - grab);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "stay", "裂波斩 · 哥布林", 0, grab, startX, null);
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "overturn", "裂波斩 · 上挑擒抱", grab, grab + half,
                startX, new SkillMotion(grab, half, vx, 0, 2 * apex / ascent, -2 * apex / (ascent * ascent), 0));
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "overturn", "裂波斩 · 空中束缚", grab + half, impact,
                startX + vx * ascent, new SkillMotion(grab + half, impact - grab - half, vx, 0,
                        0, -2 * (apex - holdZ) / (descent * descent), apex));
        // The stock hit uses push 100 and lift 50. The example returns the target to the ground.
        double gravity = 850;
        double airtime = (50 + Math.sqrt(2500 + 2 * gravity * holdZ)) / gravity;
        long landing = Math.min(end, impact + Math.round(airtime * 1000));
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "overturn", "裂波斩 · 裂波受击", impact, landing,
                x, new SkillMotion(impact, landing - impact, 100, 0, 50, -gravity, holdZ));
        Act1SwordAsuraSupplement.addTarget(visuals, lookup, "down", "裂波斩 · 落地", landing, end,
                x + 100 * (landing - impact) / 1000.0, null);
    }

    private static Act1SkillSequence.VisualStyle slice(List<ResourceScript.Frame> frames,int first,int last,long period){
        var overrides=new HashMap<Integer,Act1SkillSequence.FrameOverride>();
        for(int i=0;i<frames.size();i++)if(i<first||i>=last)overrides.put(i,new Act1SkillSequence.FrameOverride(0,0,0));
        return new Act1SkillSequence.VisualStyle(null,0,List.of(),overrides,false,period);
    }
    private static boolean binding(String path,int id,GameScript.Lookup lookup)throws IOException{
        String ref=GameScript.catalog(lookup.read("skill/swordmanskill.lst")).get(id);
        return ref!=null&&path.equals(GameScript.resolve("skill/swordmanskill.lst",ref,p->p.equals(path)?"":null));
    }
    private static List<ResourceScript.Frame> body(Map<String,String> actions,String action,String filename,GameScript.Lookup lookup)throws IOException{
        String ref=actions.get(action);return ref!=null&&GameScript.normalize(ref).endsWith("/"+filename)?Act1SkillSequence.frames(ROOT+"swordman.chr",ref,lookup):List.of();
    }
    private static List<ResourceScript.Frame> frames(String path,GameScript.Lookup lookup)throws IOException{
        String text=lookup.read(path);return text==null?List.of():ResourceScript.animation(text).frames();
    }
    private static long duration(List<ResourceScript.Frame> frames,int count){return Act1SkillSequence.duration(frames,count,null);}
    private static long life(String path,GameScript.Lookup lookup)throws IOException{
        String text=lookup.read(path);if(text==null)return -1;var ani=ResourceScript.animation(text);long d=new AnimationTimeline(ani).duration();return ani.loop()||d<=0||d>10000?-1:d;
    }
    private static int setting(List<String> values,String name){for(int i=0;i+1<values.size();i+=2)if(values.get(i).equals(name))return GameScript.integer(values.get(i+1),Integer.MIN_VALUE);return Integer.MIN_VALUE;}
    private static int weaponType(String path,GameScript.Lookup lookup)throws IOException{
        if(path==null||path.isEmpty())return 0;
        String text=lookup.read(GameScript.normalize(path));if(text==null)return -1;
        var fields=GameScript.fields(text);
        return GameScript.flag(GameScript.value(fields,"equipment type")).equals("weapon")?GameScript.integer(GameScript.value(fields,"sub type"),-1):-1;
    }
}
