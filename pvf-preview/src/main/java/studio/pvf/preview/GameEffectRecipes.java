package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** Explicit visual dependencies, never inferred from a similar filename or skill number. */
public final class GameEffectRecipes {
    private GameEffectRecipes() {}
    public record Effect(String key,String label,String path,String source,String note,double x,double y) {
        public Effect(String key,String label,String path,String source,String note){this(key,label,path,source,note,120,-70);}
        @Override public String toString(){return label;}
    }
    public record Laser(long fireAt,int range,boolean charged) {
        public int tileCount(){return Math.max(0,(range-100)/50-1);}
        public int endOffset(){return 50*((range-100)/50)-50;}
    }
    public static Laser laser(String path,String text,boolean act1,boolean charged) {
        return laser(path,text,act1,charged,null);
    }
    public static Laser laser(String path,String text,boolean act1,boolean charged,String passiveText) {
        if(!act1||!path.equals("skill/gunner/laserrifle.skl"))return null;
        var values=GameScript.values(GameScript.fields(text),"static data");if(values.size()<2)return null;
        int delay=GameScript.integer(values.get(0),-1),range=GameScript.integer(values.get(1),-1);
        if(delay<0||delay>30000||range<150||range>6000)return null;
        if(charged){
            if(passiveText==null)return null;var fields=GameScript.fields(passiveText);var info=GameScript.values(fields,"level info");var staticData=GameScript.values(fields,"static data");
            if(info.size()<3||staticData.isEmpty()||GameScript.integer(info.get(0),0)!=2)return null;
            int charge=GameScript.integer(info.get(1),-1),bonus=GameScript.integer(staticData.get(0),-1);
            if(charge<0||charge>30000||bonus<0||range+bonus>6000)return null;
            return new Laser(Math.max(delay,charge),range+bonus,true);
        }
        return new Laser(delay,range,false);
    }
    public static List<Effect> effects(String path,String text,GameScript.Lookup lookup) throws IOException {
        return effects(path,text,lookup,false);
    }
    public static List<Effect> effects(String path,String text,GameScript.Lookup lookup,boolean act1) throws IOException {
        var effects=new LinkedHashMap<String,Effect>();
        for(var reference:ResourceScript.references(text))if(!reference.tag().contains("preload")&&(reference.animation()||reference.particle())){
            String target=GameScript.resolve(path,reference.path(),lookup);
            if(target!=null)effects.putIfAbsent(target,new Effect(target,"["+reference.tag()+"] "+reference.path(),target,path,"配置显式引用"));
        }
        Map<Integer,String> passive=null;
        var fields=new ArrayList<>(GameScript.fields(text));boolean mage=act1&&path.equals("skill/mage/launchjol.skl");
        if(mage)fields.add(new GameScript.Field("passive object",List.of("23008","68","0","53")));
        for(var f:fields)if(f.tag().equals("passive object")&&!f.values().isEmpty()) {
            int id=GameScript.integer(f.first(),-1);if(id<0)continue;
            double x=f.values().size()>1?GameScript.integer(f.values().get(1),0):120;
            double y=(f.values().size()>2?GameScript.integer(f.values().get(2),0):0)-(f.values().size()>3?GameScript.integer(f.values().get(3),0):70);
            if(Math.abs(x)>10000||Math.abs(y)>10000)throw new IOException("被动对象预览坐标超限");
            if(passive==null)passive=GameScript.catalog(lookup.read("passiveobject/passiveobject.lst"));
            String ref=passive.get(id);if(ref==null){effects.put("missing:"+id,new Effect("missing:"+id,"被动对象 #"+id+"（目录缺失）","",path,"找不到被动对象目录记录"));continue;}
            String object=GameScript.resolve("passiveobject/catalog.lst",ref,lookup);if(object==null)continue;
            String definition=lookup.read(object);var objectFields=GameScript.fields(definition);String basic=GameScript.value(objectFields,"basic motion");
            if(!basic.isEmpty()){
                String ani=GameScript.resolve(object,basic,lookup);if(ani!=null)effects.put("passive:"+id,new Effect("passive:"+id,(mage?"杰克爆弹":"触发对象 #"+id)+" · 初始动画",ani,object,"仅演示对象初始视觉；目标选择、移动、命中和运行时条件尚未执行",x,y));
            }
            int index=0;for(String motion:GameScript.values(objectFields,"etc motion"))if(motion.endsWith(".ani")){
                String ani=GameScript.resolve(object,motion,lookup);if(ani!=null){String key="passive:"+id+":"+index;effects.put(key,new Effect(key,"对象 #"+id+" · 关联动画 "+motion,ani,object,"关联动画片段；尚未还原触发时序",x,y));}index++;
            }
            if(effects.values().stream().noneMatch(e->e.key().startsWith("passive:"+id)))effects.put("missing:"+id,new Effect("missing:"+id,"被动对象 #"+id+"（未找到初始视觉）","",object,"该对象依赖尚未解析的客户端逻辑"));
        }
        return List.copyOf(effects.values());
    }

    static String motionFor(Map<String,String> actions,String stem) {
        String matched="";
        for(var entry:actions.entrySet()){
            String value=GameScript.normalize(entry.getValue());
            if(!value.substring(value.lastIndexOf('/')+1).equals(stem+".ani"))continue;
            if(!matched.isEmpty())return "";
            matched=entry.getKey();
        }
        return matched;
    }

    static String passiveObject(GameScript.Lookup lookup,int id) throws IOException {
        String reference=GameScript.catalog(lookup.read("passiveobject/passiveobject.lst")).get(id);
        return reference==null?null:GameScript.resolve("passiveobject/catalog.lst",reference,lookup);
    }
}
