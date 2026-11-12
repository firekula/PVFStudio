package studio.pvf.preview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects the same weapon for the pose, effects and wardrobe of a skill preview. */
final class Act1PreviewWeapon {
    private static final String PREFIX="weapon:";
    private static final Map<String,List<Kind>> KINDS=Map.of(
            "swordman",List.of(new Kind(0,"ssword","短剑"),new Kind(1,"katana","太刀"),
                    new Kind(2,"club","钝器"),new Kind(3,"hsword","巨剑"),new Kind(5,"beamsword","光剑")),
            "gunner",List.of(new Kind(0,"revolver","左轮枪"),new Kind(1,"automatic","自动手枪"),
                    new Kind(2,"musket","步枪"),new Kind(3,"hcannon","手炮"),new Kind(4,"bowgun","手弩")),
            "fighter",List.of(new Kind(0,"knuckle","手套")),
            "mage",List.of(new Kind(0,"spear","矛")),
            "priest",List.of(new Kind(0,"cross","十字架"),new Kind(1,"rosary","念珠"),
                    new Kind(2,"totem","图腾"),new Kind(3,"scythe","镰刀"),new Kind(4,"axe","战斧")));
    private static final Map<String,Set<String>> VARIANTS=Map.of(
            "swordman",Set.of("weaponcombo","momentaryslash","jumpattackmulti","backstepcutter","rapidmoveslash",
                    "illusionslash","upperslash","chargecrash","hopsmash","vaneslash"),
            "gunner",Set.of("punisher","headshot","turningshoot","liftshot","walkshoot","walkshootturning","fastshoot",
                    "randomshoot","napalmbomb","desperado"),
            "priest",Set.of("willdriver","homerun"));

    private record Kind(int subtype,String folder,String label) {}
    private record Candidate(String path,int level,int rarity,int grade) {}
    private static final Comparator<Candidate> ORDER=Comparator.comparingInt(Candidate::level)
            .thenComparingInt(Candidate::rarity).thenComparingInt(Candidate::grade).thenComparing(Candidate::path);

    private Act1PreviewWeapon() {}

    static List<GameEffectRecipes.Effect> choices(String path,String job,Set<String> paths,boolean act1){
        if(!act1||!varies(path,job))return List.of();
        var result=new ArrayList<GameEffectRecipes.Effect>();
        for(var kind:KINDS.get(job)){
            String prefix=folder(job,kind);
            if(paths.stream().anyMatch(p->p.startsWith(prefix)&&p.endsWith(".equ"))){
                result.add(new GameEffectRecipes.Effect(PREFIX+kind.subtype(),"武器 · "+kind.label(),"",path,
                        "试用当前资源包内可装备等级最低的"+kind.label()));
            }
        }
        return List.copyOf(result);
    }

    static String recipeChoice(String path,String job,String choice,boolean act1){
        return act1&&choice.startsWith(PREFIX)&&forcedType(path,job,choice)!=null?"":choice;
    }

    static Map<String,String> outfit(String path,String job,String choice,boolean act1,
                                     Set<String> paths,GameScript.Lookup lookup,Map<String,String> outfit) throws IOException {
        if(!path.endsWith(".skl")||!KINDS.containsKey(job))return outfit;
        Integer forced=act1?forcedType(path,job,choice):null;
        if(forced==null&&outfit.containsKey("weapon"))return outfit;
        int subtype=forced!=null?forced:path.equals("skill/swordman/jumpattackmulti.skl")?1:0;
        String weapon=lowest(job,subtype,paths,lookup);
        if(weapon==null&&forced==null)return outfit;
        var selected=new HashMap<>(outfit);
        selected.put("weapon",weapon==null?"":weapon);
        return Map.copyOf(selected);
    }

    static Integer forcedType(String path,String job,String choice){
        if(job.equals("gunner")&&(Act1GunnerUtilityCast.isWallChoice(path,choice)||Act1FlameBullet.selected(path,choice)))return 0;
        if (job.equals("swordman")) {
            Integer subtype = Act1SwordMeleeSupplement.masteryWeapon(path, choice);
            if (subtype != null) return subtype;
        }
        if(path.equals(Act1SwordTechniqueCast.MOMENTARY_PATH)){
            if(choice.equals(Act1SwordTechniqueCast.KATANA))return 1;
            if(choice.equals(Act1SwordTechniqueCast.LIGHTSABER))return 5;
        }
        Integer type=varies(path,job)?typeChoice(choice):null;
        return type!=null&&KINDS.get(job).stream().anyMatch(k->k.subtype()==type)?type:null;
    }

    private static Integer typeChoice(String choice){
        if(!choice.startsWith(PREFIX)||choice.length()!=PREFIX.length()+1)return null;
        int subtype=choice.charAt(PREFIX.length())-'0';
        return subtype>=0&&subtype<=5?subtype:null;
    }

    private static boolean varies(String path,String job){
        String prefix="skill/"+job+"/";
        if(!path.startsWith(prefix)||!path.endsWith(".skl"))return false;
        return VARIANTS.getOrDefault(job,Set.of()).contains(path.substring(prefix.length(),path.length()-4));
    }

    private static String lowest(String job,int subtype,Set<String> paths,GameScript.Lookup lookup) throws IOException {
        var kind=KINDS.get(job).stream().filter(k->k.subtype()==subtype).findFirst().orElse(null);
        if(kind==null)return null;
        String prefix=folder(job,kind);
        Candidate best=null;
        for(String path:paths.stream().filter(p->p.startsWith(prefix)&&p.endsWith(".equ")).sorted().limit(512).toList()){
            String text=lookup.read(path);
            if(text==null)continue;
            var fields=GameScript.fields(text);
            if(!"weapon".equals(GameScript.flag(GameScript.value(fields,"equipment type")))
                    ||GameScript.integer(GameScript.value(fields,"sub type"),-1)!=subtype)continue;
            var jobs=GameScript.values(fields,"usable job");
            if(!jobs.isEmpty()&&jobs.stream().noneMatch(value->job.equals(GameScript.flag(value))))continue;
            int level=GameScript.integer(GameScript.value(fields,"minimum level"),1);
            if(level<0)continue;
            var candidate=new Candidate(path,level,number(fields,"rarity"),number(fields,"grade"));
            if(best==null||ORDER.compare(candidate,best)<0)best=candidate;
        }
        return best==null?null:best.path();
    }

    private static int number(List<GameScript.Field> fields,String tag){
        return GameScript.integer(GameScript.value(fields,tag),Integer.MAX_VALUE);
    }

    private static String folder(String job,Kind kind){
        return "equipment/character/"+job+"/weapon/"+kind.folder()+"/";
    }
}
