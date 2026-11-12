package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** Explicit visual dependencies, never inferred from a similar filename or skill number. */
public final class GameEffectRecipes {
    private GameEffectRecipes() {}
    static final String LASER_SKILL="skill/gunner/laserrifle.skl", LASER_CHARGE="skill/gunner/laserriflecharge.skl";
    public record Effect(String key,String label,String path,String source,String note,double x,double y) {
        public Effect(String key,String label,String path,String source,String note){this(key,label,path,source,note,120,-70);}
        @Override public String toString(){return label;}
    }
    public record Laser(long fireAt,int range,boolean charged,int chargeThreshold) {
        public Laser(long fireAt,int range,boolean charged){this(fireAt,range,charged,0);}
        public int tileCount(){return Math.max(0,(range-100)/50-1);}
        public int endOffset(){return 50*((range-100)/50)-50;}
        boolean hasChargePassive(){return chargeThreshold>0;}
        int recoilSpeed(long elapsed){
            // State 26 interpolates 100..400 from the capped hold time carried by object 22017.
            if(charged||chargeThreshold==0)return 400;
            return 100+(int)(300*elapsed/chargeThreshold);
        }
    }
    record AttachedCast(String action,List<Attachment> clips,String note) {}
    record Attachment(String path,String label,int layer,long start,long end,double x,double y) {
        Attachment(String path,String label,int layer){this(path,label,layer,0,-1,0,0);}

        // 0x472400 draws negative priorities before the actor and the rest after it.
        int renderLayer(){return layer<0?-1000+layer:3500+layer;}
    }

    private static final String FIGHTER_EFFECT="character/fighter/effect/animation/";

    /** ACT1 CNFighter state entry, 0x5E9C90. The action index is the CHR slot read by the native branch. */
    static AttachedCast fighterCast(String path,GameScript.Lookup lookup,Map<String,String> actions,boolean act1) throws IOException {
        if(!act1||!path.startsWith("skill/fighter/"))return null;
        int id=switch(path){
            case "skill/fighter/throwweb.skl" -> 52;
            case "skill/fighter/whirlwindkick.skl" -> 58;
            case "skill/fighter/blockbuster.skl" -> 60;
            case "skill/fighter/strongestlowkick.skl" -> 71;
            case "skill/fighter/hiddensting.skl" -> 75;
            case "skill/fighter/junkspin.skl" -> 77;
            default -> -1;
        };
        if(id<0||!path.equals("skill/"+GameScript.catalog(lookup.read("skill/fighterskill.lst")).get(id)))return null;
        var cast=switch(id){
            case 52 -> new AttachedCast("etc motion:29",List.of(
                    fighterAttachment("throwweblineardodge.ani","天罗地网 · 网光",1),
                    fighterAttachment("throwwebnormal.ani","天罗地网 · 网身",2)),
                    "天罗地网按近身施放动作和网层预览");
            case 58 -> new AttachedCast("etc motion:16",List.of(
                    fighterAttachment("whirlwindkick.ani","旋风腿 · 踢击轨迹",1)),
                    "旋风腿预览一次转身，游戏中的位移和连续转身由施放状态控制");
            case 60 -> new AttachedCast("etc motion:17",List.of(
                    fighterAttachment("blockbusterblock2.ani","砖袭 · 砖块后层",-1),
                    fighterAttachment("blockbusterblock1.ani","砖袭 · 砖块前层",1),
                    fighterAttachment("blockbusterglow.ani","砖袭 · 挥击光效",2)),
                    "砖袭按单人施放预览，碎片和扬尘由命中触发");
            case 71 -> new AttachedCast("etc motion:24",List.of(
                    new Attachment(FIGHTER_EFFECT+"strongestlowkick/ready.ani","武神强踢 · 蓄势",1,0,-1,-22,-45)),
                    "武神强踢按单人施放预览，命中表现取决于战斗目标");
            case 75 -> new AttachedCast("etc motion:30",List.of(
                    fighterAttachment("hiddenstingpin.ani","毒影针 · 针体",1)),
                    "毒影针按单人施放预览，命中时客户端切换针体效果");
            case 77 -> new AttachedCast("etc motion:33",List.of(
                    fighterAttachment("junkspin/backback.ani","街头风暴 · 后层旋风",-3),
                    fighterAttachment("junkspin/back-dodge.ani","街头风暴 · 后层光效",-2),
                    fighterAttachment("junkspin/back-normal.ani","街头风暴 · 后层轨迹",-1),
                    fighterAttachment("junkspin/front-normal.ani","街头风暴 · 前层轨迹",1),
                    fighterAttachment("junkspin/front-dodge.ani","街头风暴 · 前层光效",2)),
                    "街头风暴预览一轮旋转，抛掷物由游戏的连发状态和随机选择生成");
            default -> throw new IllegalStateException("Unhandled Fighter skill " + id);
        };
        String motion=actions.get(cast.action());
        if(motion==null)return null;
        String bodyPath=GameScript.resolve("character/fighter/fighter.chr",motion,lookup);
        if(bodyPath==null)return cast;
        // The state-exit branches remove these attachments, including Brick's longer glow ANI.
        long end;
        try {
            end=new AnimationTimeline(ResourceScript.animation(lookup.read(bodyPath))).duration();
        }catch(IllegalArgumentException invalidBody){return cast;}
        if(end<=0)return cast;
        var bounded=cast.clips().stream().map(clip->new Attachment(clip.path(),clip.label(),clip.layer(),clip.start(),end,clip.x(),clip.y())).toList();
        return new AttachedCast(cast.action(),bounded,cast.note());
    }

    private static Attachment fighterAttachment(String relative,String label,int layer){
        return new Attachment(FIGHTER_EFFECT+relative,label,layer);
    }

    public static Laser laser(String path,String text,boolean act1,boolean charged) {
        return laser(path,text,act1,charged,null);
    }
    public static Laser laser(String path,String text,boolean act1,boolean charged,String passiveText) {
        return laser(path,text,act1,charged,passiveText,1);
    }
    static Laser laser(String path,String text,boolean act1,boolean charged,String passiveText,int chargeLevel) {
        if(!act1||!path.equals(LASER_SKILL)||text==null)return null;
        var values=GameScript.values(GameScript.fields(text),"static data");if(values.size()<2)return null;
        int delay=GameScript.integer(values.get(0),-1),range=GameScript.integer(values.get(1),-1);
        if(delay<0||delay>30000||range<150||range>6000)return null;
        var fields=GameScript.fields(passiveText);
        var info=GameScript.values(fields,"level info");
        var staticData=GameScript.values(fields,"static data");
        if(info.size()>=3&&!staticData.isEmpty()&&GameScript.integer(info.get(0),0)==2){
            int row=Math.max(1,Math.min(chargeLevel,(info.size()-1)/2));
            int charge=GameScript.integer(info.get(2*row-1),-1),bonus=GameScript.integer(staticData.get(0),-1);
            if(charge>0&&charge<=30000&&bonus>=0&&bonus<=6000-range){
                if(charged)return new Laser(Math.max(delay,charge),range+bonus,true,charge);
                if(delay<charge)return new Laser(delay,range,false,charge);
            }
        }
        return charged?null:new Laser(delay,range,false);
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
