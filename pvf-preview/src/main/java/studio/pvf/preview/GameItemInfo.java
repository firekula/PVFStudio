package studio.pvf.preview;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/** Configuration values only: a PVF entry is not an equipped runtime item instance. */
public record GameItemInfo(String name,String english,String type,int rarity,List<Row> rows,String explanation,List<String> warnings,Tooltip tooltip) {
    public record Row(String label,String value) {}
    public enum Ink { NORMAL, BONUS, FLAVOR }
    public record TooltipLine(String left,String right,Ink ink) {}
    public record Tooltip(String heading,boolean skill,boolean setItem,List<TooltipLine> lines) {}
    private static final Map<String,String> LABELS=Map.ofEntries(
        Map.entry("equipment physical attack","物理攻击力"),Map.entry("equipment magical attack","魔法攻击力"),
        Map.entry("equipment physical defense","物理防御力"),Map.entry("equipment magical defense","魔法防御力"),
        Map.entry("physical attack","力量"),Map.entry("magical attack","智力"),Map.entry("physical defense","体力"),Map.entry("magical defense","精神"),
        Map.entry("minimum level","使用等级"),Map.entry("required level","学习等级"),Map.entry("weight","重量（g）"),Map.entry("price","配置价格"),
        Map.entry("HP MAX","HP 最大值"),Map.entry("MP MAX","MP 最大值"),Map.entry("hp max","HP 最大值"),Map.entry("mp max","MP 最大值"),
        Map.entry("attack speed","攻击速度（原始值）"),Map.entry("move speed","移动速度（原始值）"),Map.entry("cast speed","施放速度（原始值）"),
        Map.entry("hit recovery","硬直（原始值）"),Map.entry("jump power","跳跃力"),Map.entry("inventory limit","负重上限（g）"),
        Map.entry("MP regen speed","MP 恢复（原始值）"),Map.entry("mp regen speed","MP 恢复（原始值）"),Map.entry("cool time","冷却（ms）"),
        Map.entry("consume MP","消耗 MP"),Map.entry("consume mp","消耗 MP"),Map.entry("usable job","适用职业"),Map.entry("durability","基础耐久"),
        Map.entry("physical critical hit","物理暴击率"),Map.entry("magical critical hit","魔法暴击率"),Map.entry("elemental property","攻击属性"),
        Map.entry("fire resistance","火属性抗性"),Map.entry("water resistance","冰属性抗性"),
        Map.entry("light resistance","光属性抗性"),Map.entry("dark resistance","暗属性抗性"));
    public static GameItemInfo read(String path,String text,GameScript.Lookup lookup,int level) throws IOException {
        var fields=GameScript.fields(text);var warnings=new ArrayList<String>();var strings=new GameScript.Strings(lookup,warnings);var rows=new ArrayList<Row>();
        String name=strings.resolve(GameScript.value(fields,"name"));if(name.isBlank())name=path.substring(path.lastIndexOf('/')+1);
        String type=GameScript.flag(GameScript.value(fields,"equipment type"));type=switch(type){case "weapon"->"武器";case "coat avatar"->"上衣装扮";case "pants avatar"->"下装装扮";case "skin avatar"->"皮肤装扮";case "hair avatar"->"头发装扮";case "shoes avatar"->"鞋子装扮";case "cap avatar"->"帽子装扮";case "face avatar"->"脸部装扮";case "belt avatar"->"腰部装扮";case "neck avatar"->"胸部装扮";case "title name"->"称号";case ""->path.endsWith(".skl")?"技能":"物品";default->type;};
        int maximum=Math.max(1,GameScript.integer(GameScript.value(fields,"maximum level"),1));int chosen=Math.max(1,Math.min(level,maximum));
        boolean skill=path.endsWith(".skl");
        var values=new LinkedHashMap<String,String>();
        if(skill)rows.add(new Row("预览等级",chosen+" / "+maximum));
        boolean extraStats=false;
        for(var f:fields) {
            if(f.tag().equals("set ability")||f.tag().equals("if")||f.tag().equals("then")){extraStats=true;continue;}
            if(f.tag().equals("/set ability")||f.tag().equals("/if")||f.tag().equals("/then")){extraStats=false;continue;}
            if(extraStats)continue;
            String label=LABELS.get(f.tag());if(label==null||f.values().isEmpty())continue;
            String value=strings.resolve(f.text());
            if(f.tag().equals("usable job"))value=value.replace("[all]","全职业").replace("[swordman]","鬼剑士").replace("[fighter]","格斗家").replace("[gunner]","神枪手").replace("[mage]","魔法师").replace("[priest]","圣职者");
            if(f.values().size()==2&&f.values().stream().allMatch(v->v.matches("[+-]?\\d+(\\.\\d+)?"))) {
                if(path.endsWith(".skl")&&(f.tag().equals("cool time")||f.tag().equals("consume mp"))) {
                    double a=Double.parseDouble(f.values().get(0)),b=Double.parseDouble(f.values().get(1));
                    value=String.format(Locale.ROOT,"%.0f",maximum==1?a:a+(b-a)*(chosen-1)/(maximum-1));
                }else if(!f.values().get(0).equals(f.values().get(1)))value+="（配置范围）";
            }
            rows.add(new Row(label,value));
            values.putIfAbsent(f.tag(),value);
        }
        var descriptions=new LinkedHashMap<String,String>();
        for(String tag:List.of("explain","basic explain","flavor text","command key explain")){
            String raw=String.join("\n",GameScript.values(fields,tag));
            if(!raw.isBlank())descriptions.put(tag,strings.resolve(raw).strip());
        }
        var explanation=new StringJoiner("\n\n");
        new LinkedHashSet<>(descriptions.values()).forEach(explanation::add);
        boolean condition=false;var conditions=new StringJoiner("\n");
        for(var f:fields){if(f.tag().equals("if")||f.tag().equals("then")){condition=true;conditions.add("["+f.tag()+"]");}else if(f.tag().equals("/then")){condition=false;}else if(condition&&!f.tag().startsWith("/"))conditions.add("["+f.tag()+"] "+strings.resolve(f.text()));}
        if(conditions.length()>0)explanation.add("配置触发条件\n"+conditions);
        boolean setItem=!GameScript.value(fields,"set name").isBlank();
        var tooltip=skill?skillTooltip(name,chosen,fields,values,descriptions):itemTooltip(path,name,type,values,descriptions,setItem);
        return new GameItemInfo(name,strings.resolve(GameScript.value(fields,"name2")),type,GameScript.integer(GameScript.value(fields,"rarity"),0),List.copyOf(rows),explanation.toString(),List.copyOf(new LinkedHashSet<>(warnings)),tooltip);
    }

    private static Tooltip skillTooltip(String name,int level,List<GameScript.Field> fields,Map<String,String> values,Map<String,String> descriptions) {
        var lines=new ArrayList<TooltipLine>();
        String type=GameScript.flag(GameScript.value(fields,"type"));
        String cast=GameScript.value(fields,"casting time");
        if(cast.isEmpty())cast=GameScript.value(fields,"cast time");
        String castLabel="";
        if(type.equals("passive"))castLabel="被动技能";
        else if(!type.isEmpty()||!cast.isEmpty())castLabel="施放时间："+(cast.isEmpty()||cast.equals("0")?"瞬发":seconds(cast));
        String mp=values.getOrDefault("consume mp","");
        if(!castLabel.isEmpty()||!mp.isEmpty())lines.add(new TooltipLine(castLabel,mp.isEmpty()?"":"MP "+mp,Ink.NORMAL));
        String cooldown=values.getOrDefault("cool time","");
        if(!cooldown.isEmpty())lines.add(new TooltipLine("冷却时间："+seconds(cooldown),"",Ink.NORMAL));
        String command=descriptions.getOrDefault("command key explain","");
        if(!command.isEmpty()){
            if(!command.startsWith("操作指令"))command="操作指令："+command;
            lines.add(new TooltipLine(command,"",Ink.NORMAL));
        }
        section(lines,descriptions.getOrDefault("basic explain",descriptions.getOrDefault("explain","")),Ink.NORMAL);
        section(lines,descriptions.getOrDefault("flavor text",""),Ink.FLAVOR);
        return new Tooltip(name+" Lv"+level,true,false,List.copyOf(lines));
    }

    private static Tooltip itemTooltip(String path,String name,String type,Map<String,String> values,Map<String,String> descriptions,boolean setItem) {
        var lines=new ArrayList<TooltipLine>();
        String weight=values.getOrDefault("weight","");
        String price=values.getOrDefault("price","");
        if(!weight.isEmpty()||!price.isEmpty()){
            lines.add(new TooltipLine(weight.isEmpty()?"":scaled(weight,3,"kg"),price.isEmpty()?"":gold(price),Ink.NORMAL));
        }
        String subtype=itemSubtype(path);
        String slot=switch(type){case "coat" -> "上衣";case "pants" -> "下装";case "shoulder" -> "护肩";case "belt" -> "腰带";case "shoes" -> "鞋";default -> type;};
        lines.add(new TooltipLine(subtype,slot,Ink.NORMAL));
        String durability=values.getOrDefault("durability","");
        if(!durability.isEmpty())lines.add(new TooltipLine("耐久度 "+durability,"",Ink.NORMAL));
        String required=values.getOrDefault("minimum level","");
        if(!required.isEmpty()&&!required.equals("1"))lines.add(new TooltipLine("Lv"+required+"以上可以使用","",Ink.NORMAL));
        String jobs=values.getOrDefault("usable job","");
        if(!jobs.isEmpty()&&!jobs.equals("全职业"))lines.add(new TooltipLine(jobs+"可以使用","",Ink.NORMAL));

        var stats=new ArrayList<TooltipLine>();
        var bonuses=new ArrayList<TooltipLine>();
        for(var entry:values.entrySet()){
            String tag=entry.getKey(),value=entry.getValue();
            switch(tag){
                case "weight","price","durability","minimum level","required level","usable job","cool time","consume mp" -> { }
                case "attack speed","move speed","cast speed" -> bonuses.add(new TooltipLine(
                        LABELS.get(tag).replace("（原始值）","")+" "+signed(scaled(value,1,"%")),"",Ink.BONUS));
                case "hit recovery","jump power","fire resistance","water resistance","light resistance","dark resistance" -> bonuses.add(new TooltipLine(
                        LABELS.get(tag).replace("（原始值）","")+" "+signed(value),"",Ink.BONUS));
                case "mp regen speed" -> bonuses.add(new TooltipLine(regeneration(value),"",Ink.BONUS));
                default -> stats.add(new TooltipLine(LABELS.get(tag)+" "+value,"",Ink.NORMAL));
            }
        }
        group(lines,stats);
        group(lines,bonuses);
        String effect=descriptions.getOrDefault("basic explain","");
        String description=descriptions.getOrDefault("explain","");
        if(type.equals("称号")&&effect.isEmpty()){
            effect=description;
            description="";
        }
        section(lines,effect,Ink.BONUS);
        if(!description.equals(effect))section(lines,description,Ink.FLAVOR);
        String flavor=descriptions.getOrDefault("flavor text","");
        if(!flavor.equals(description)&&!flavor.equals(effect))section(lines,flavor,Ink.FLAVOR);
        return new Tooltip(name,false,setItem,List.copyOf(lines));
    }

    private static String itemSubtype(String path) {
        if(path.contains("/hcannon/"))return "手炮";
        if(path.contains("/revolver/"))return "左轮枪";
        if(path.contains("/automatic/"))return "自动手枪";
        if(path.contains("/musket/"))return "步枪";
        if(path.contains("/bowgun/"))return "手弩";
        if(path.contains("/harmor/"))return "重甲";
        if(path.contains("/larmor/"))return "轻甲";
        if(path.contains("/plate/"))return "板甲";
        if(path.contains("/leather/"))return "皮甲";
        if(path.contains("/cloth/"))return "布甲";
        return "";
    }

    private static void group(List<TooltipLine> lines,List<TooltipLine> group) {
        if(group.isEmpty())return;
        if(!lines.isEmpty())lines.add(new TooltipLine("","",Ink.NORMAL));
        lines.addAll(group);
    }
    private static void section(List<TooltipLine> lines,String text,Ink ink) {
        if(!text.isBlank())group(lines,List.of(new TooltipLine(text,"",ink)));
    }
    private static String seconds(String raw){return scaled(raw,3,"秒");}
    private static String scaled(String raw,int decimals,String suffix){
        if(!raw.matches("[+-]?\\d{1,12}(\\.\\d{1,4})?"))return raw;
        try{return new BigDecimal(raw).movePointLeft(decimals).stripTrailingZeros().toPlainString()+suffix;}
        catch(NumberFormatException invalid){return raw;}
    }
    private static String signed(String value){return value.matches("[0-9].*")?"+"+value:value;}
    private static String gold(String value){
        try{return String.format(Locale.ROOT,"%,d金币",Long.parseLong(value));}
        catch(NumberFormatException invalid){return value;}
    }
    private static String regeneration(String value) {
        try{return "每分钟恢复"+Math.multiplyExact(Long.parseLong(value),3)+"MP";}
        catch(ArithmeticException|NumberFormatException invalid){return "MP 恢复 "+value;}
    }
}
