package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** Player equipment expansion with per-owner include/variation context, independent of the desktop. */
public final class CharacterAppearance {
    private CharacterAppearance() {}
    public static final List<String> JOBS=List.of("swordman","fighter","gunner","mage","priest");
    public record Part(String animation,List<Integer> variation,int layer,String slot,String owner) {}
    public record Model(String job,List<Part> parts,Map<String,String> actions,Map<String,String> wearing,List<String> warnings,String action,String bodyAnimation) {}
    private static final Set<String> MOTIONS=Set.of("waiting motion","move motion","sit motion","damage motion 1","damage motion 2","down motion","overturn motion","jump motion","jumpattack motion","rest motion","dash motion","dashattack motion","buff motion","getitem motion","attack motion","etc motion","throw motion 1-1","throw motion 1-2","throw motion 2-1","throw motion 2-2","throw motion 3-1","throw motion 3-2","throw motion 4-1","throw motion 4-2");
    public static Map<String,String> motions(String text) {
        var actions=new LinkedHashMap<String,String>();
        for(var f:GameScript.fields(text))if(MOTIONS.contains(f.tag()))for(int i=0;i<f.values().size();i++){
            String path=f.values().get(i);if(!path.toLowerCase(Locale.ROOT).endsWith(".ani"))continue;
            actions.put(f.tag()+(f.values().size()>1?":"+i:""),path);
        }
        return Collections.unmodifiableMap(actions);
    }
    public static String inferJob(String path,String text,String requested) {
        for(String job:JOBS)if(path.contains("/"+job+"/"))return job;
        var usable=GameScript.values(GameScript.fields(text),"usable job").stream().map(GameScript::flag).filter(JOBS::contains).toList();
        if(usable.contains(requested))return requested;if(!usable.isEmpty())return usable.get(0);
        return JOBS.contains(requested)?requested:"gunner";
    }
    public static String slot(String text){return GameScript.flag(GameScript.value(GameScript.fields(text),"equipment type"));}
    public static Model load(String path,String text,GameScript.Lookup rawLookup,Set<String> paths,String requested,String action,Map<String,String> outfit) throws IOException {
        GameScript.Lookup bounded=GameScript.boundedLookup(rawLookup,1024,16L*1024*1024);GameScript.Lookup lookup=p->p.equals(path)?text:bounded.read(p);String job=inferJob(path,text,requested);
        String chr="character/"+job+"/"+job+".chr",body=lookup.read(chr);var warnings=new ArrayList<String>();
        if(body==null)return new Model(job,List.of(),Map.of(),Map.of(),List.of("找不到职业配置 "+chr),action,null);
        Map<String,String> actions=motions(body);String chosen=actions.containsKey(action)?action:"waiting motion";
        if(!actions.containsKey(chosen)&&!actions.isEmpty())chosen=actions.keySet().iterator().next();
        String reference=actions.get(chosen),bodyAnimation=reference==null?null:resolvedPath(chr,reference,lookup);
        var wearing=new LinkedHashMap<String,String>();
        for(String candidate:paths.stream().filter(p->p.startsWith("equipment/character/"+job+"/avatar/")&&p.endsWith("_default.equ")).sorted().toList()){
            try{String value=lookup.read(candidate);if(value!=null)wearing.putIfAbsent(slot(value),candidate);}catch(IOException failure){warnings.add(failure.getMessage());break;}
        }
        String ownSlot=path.endsWith(".equ")?slot(text):"";if(!ownSlot.isEmpty())wearing.put(ownSlot,path);wearing.putAll(outfit);
        var parts=new ArrayList<Part>();var hidden=new HashMap<Integer,Set<String>>();int skin=0;var budget=new ExpansionBudget();
        for(var entry:wearing.entrySet()) {
            String owner=entry.getValue();if(owner.isEmpty())continue;
            try{String item=lookup.read(owner);
            if(item==null){if(warnings.size()<256)warnings.add("找不到穿戴配置 "+owner);continue;}
            var fields=GameScript.fields(item);var variation=GameScript.values(fields,"variation");
            if(entry.getKey().equals("skin avatar")&&variation.size()>1)skin=GameScript.integer(variation.get(1),0);
            for(String value:GameScript.values(fields,"hide layer"))hidden.computeIfAbsent(GameScript.integer(value,Integer.MIN_VALUE),n->new HashSet<>()).add(owner);
            expand(owner,owner,item,lookup,chosen,entry.getKey(),variation(fields),0,"",new HashSet<>(),parts,warnings,budget,0);}catch(IOException|IllegalArgumentException failure){if(warnings.size()<256)warnings.add(owner+"："+failure.getMessage());if(budget.exhausted||failure instanceof GameScript.LimitExceeded)break;}
        }
        parts.removeIf(p->hidden.containsKey(p.layer())&&hidden.get(p.layer()).stream().anyMatch(owner->!owner.equals(p.owner())));
        // The client attaches EQU layers at their signed priority (0x47E290, 0x82F260).
        // Negative children draw before the body. Zero and positive children draw after it (0x82E3A0).
        if(bodyAnimation!=null)parts.add(0,new Part(bodyAnimation,List.of(skin),0,"body",chr));
        parts.sort(Comparator.comparingInt(Part::layer));
        return new Model(job,List.copyOf(parts),actions,Collections.unmodifiableMap(wearing),List.copyOf(warnings),chosen,bodyAnimation);
    }
    private static List<Integer> variation(List<GameScript.Field> fields) throws IOException {
        var raw=GameScript.values(fields,"variation");if(raw.isEmpty())return List.of(0,0);if(raw.size()>2)throw new IOException("装备 variation 只能有一或两个参数");var result=new ArrayList<Integer>();
        for(String token:raw){int v=GameScript.integer(token,-1);if(v<0||v>9999)throw new IOException("variation 超出 0–9999");result.add(v);}
        return List.copyOf(result);
    }
    private static String resolvedPath(String owner,String ref,GameScript.Lookup lookup) throws IOException {
        String path=GameScript.resolve(owner,ref,lookup);return path!=null?path:ResourceScript.animationCandidates(owner,ref).stream().findFirst().orElse(ref);
    }
    private static final class ExpansionBudget {int nodes;long characters;boolean exhausted;void enter(String text)throws IOException{if(++nodes>256||(characters+=text.length())>16L*1024*1024){exhausted=true;throw new IOException("穿戴引用展开总量超限");}}}
    private static void expand(String owner,String current,String text,GameScript.Lookup lookup,String action,String slot,List<Integer> variants,int baseLayer,String substitution,Set<String> active,List<Part> parts,List<String> warnings,ExpansionBudget budget,int depth) throws IOException {
        budget.enter(text);if(depth>8||parts.size()>=255)throw new IOException("穿戴引用深度或部件数超限");
        if(!active.add(current))throw new IOException("include 引用循环："+current);
        int layer=baseLayer,layerVariation=baseLayer;
        String part=substitution;
        var motionPaths=new LinkedHashMap<Integer,String>();
        boolean definesMotion=false;
        try {
            for(var field:GameScript.fields(text)) {
                if(field.tag().equals("layer variation")){
                    if(field.values().size()<2)throw new IOException("layer variation 参数不完整");
                    layerVariation=GameScript.integer(field.first(),Integer.MIN_VALUE);part=field.values().get(1);
                    if(Math.abs((long)layerVariation)>1_000_000||part.contains("%")||part.contains(":"))throw new IOException("layer variation 参数无效");
                    layer=layerVariation;
                }else if(field.tag().equals("layer"))layer=layerVariation==0?GameScript.integer(field.first(),0):layerVariation;
                else if(field.tag().equals("include")) {
                    try{String include=GameScript.resolve(current,field.first(),lookup);if(include==null)throw new IOException("找不到 include "+field.first());
                        expand(owner,include,lookup.read(include),lookup,action,slot,variants,layer,part,active,parts,warnings,budget,depth+1);
                    }catch(IOException|IllegalArgumentException failure){if(budget.exhausted||failure instanceof GameScript.LimitExceeded)throw failure;if(warnings.size()<256)warnings.add(owner+"："+failure.getMessage());}
                }else if(field.tag().equals("custom animation")) {
                    for(String ref:field.values())if(ref.toLowerCase(Locale.ROOT).endsWith(".ani")){if(parts.size()>=255)throw new IOException("穿戴部件数超限");parts.add(new Part(resolvedPath(owner,ref,lookup),variants,4000,slot,owner));}
                }else if(MOTIONS.contains(field.tag())){
                    for(int index=0;index<field.values().size();index++){
                        String ref=field.values().get(index);
                        if(!ref.toLowerCase(Locale.ROOT).endsWith(".ani"))continue;
                        definesMotion=true;
                        String key=field.tag()+(field.values().size()>1?":"+index:"");
                        if(!key.equals(action))continue;
                        if(ref.contains("%s")){
                            if(part.isEmpty())throw new IOException("动作缺少 layer variation 参数");
                            ref=ref.replace("%s",part);
                        }
                        motionPaths.put(layer,ref);
                    }
                }
            }
            for(var motion:motionPaths.entrySet()){
                if(parts.size()>=255)throw new IOException("穿戴部件数超限");
                parts.add(new Part(resolvedPath(owner,motion.getValue(),lookup),variants,motion.getKey(),slot,owner));
            }
            if(motionPaths.isEmpty()&&definesMotion&&warnings.size()<256)warnings.add(owner+"：部件未定义动作 "+actionLabel(action));
        }finally{active.remove(current);}
    }
    public static String actionLabel(String key) {
        return switch(key){case "waiting motion"->"待机";case "move motion"->"移动";case "jump motion"->"跳跃";case "dash motion"->"奔跑";case "sit motion"->"蹲下";case "rest motion"->"休息";case "buff motion"->"施放增益";case "throw motion 1-1"->"投掷一 · 准备";case "throw motion 1-2"->"投掷一 · 出手";case "throw motion 2-1"->"投掷二 · 准备";case "throw motion 2-2"->"投掷二 · 出手";case "throw motion 3-1"->"投掷三 · 准备";case "throw motion 3-2"->"投掷三 · 出手";case "throw motion 4-1"->"投掷四 · 准备";case "throw motion 4-2"->"投掷四 · 出手";case "attack motion:0","attack motion"->"普通攻击 1";case "attack motion:1"->"普通攻击 2";default->key;};
    }
}
