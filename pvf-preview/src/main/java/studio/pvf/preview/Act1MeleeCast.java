package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Actor-attached skill layers from the ACT1 state-entry handlers. */
final class Act1MeleeCast {
    private Act1MeleeCast() {}

    static GameEffectRecipes.AttachedCast find(String path,GameScript.Lookup lookup,
                                               Map<String,String> actions,boolean act1) throws IOException {
        if(!act1)return null;
        return switch(path){
            // Swordman state 16 (normal slash) and state 31, 0x638640.
            case "skill/swordman/hardattack.skl" ->
                    attached(path,"swordman",5,1,"hardattack","鬼斩刀光",lookup,actions,"hardattack1","hardattack2");
            case "skill/swordman/gorecross.skl" ->
                    attached(path,"swordman",64,25,"gorecross","十字斩刀光",lookup,actions,"gorecross/slash1","gorecross/slash2");
            // Mage state 19, 0x614D40.
            case "skill/mage/upperswing.skl" ->
                    attached(path,"mage",12,2,"upperswing","天击挥击",lookup,actions,"upperswing");
            // Priest states 29 and 32, 0x627770.
            case "skill/priest/choppinghammer.skl" ->
                    attached(path,"priest",33,20,"choppinghammer","破碎之锤挥击",lookup,actions,"choppinghammer");
            case "skill/priest/bladeofpurewhite.skl" ->
                    attached(path,"priest",43,25,"bladeofpurewhite","纯白之刃挥击",lookup,actions,
                            "bladeofpurewhite/cast1","bladeofpurewhite/cast2");
            default -> null;
        };
    }

    private static GameEffectRecipes.AttachedCast attached(String path,String job,int skillId,int motionIndex,
                                                            String motion,String label,GameScript.Lookup lookup,
                                                            Map<String,String> actions,String... effectPaths) throws IOException {
        String entry=GameScript.catalog(lookup.read("skill/"+job+"skill.lst")).get(skillId);
        if(entry==null||!path.equals("skill/"+entry))return null;
        String action="etc motion:"+motionIndex;
        String reference=actions.get(action);
        if(reference==null)return null;
        String character="character/"+job+"/";
        String body=GameScript.resolve(character+job+".chr",reference,lookup);
        if(body==null||!body.endsWith("/"+motion+".ani"))return null;
        var clips=new ArrayList<GameEffectRecipes.Attachment>();
        for(int i=0;i<effectPaths.length;i++){
            clips.add(new GameEffectRecipes.Attachment(character+"effect/animation/"+effectPaths[i]+".ani",label,i+1));
        }
        return new GameEffectRecipes.AttachedCast(action,List.copyOf(clips),"预览施放动作和客户端挂载特效；后续飞行与目标命中另由游戏处理");
    }
}
