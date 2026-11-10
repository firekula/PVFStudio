package studio.pvf.preview;

import java.io.IOException;
import java.util.*;

/** Configuration values only: a PVF entry is not an equipped runtime item instance. */
public record GameItemInfo(String name,String english,String type,int rarity,List<Row> rows,String explanation,List<String> warnings) {
    public record Row(String label,String value) {}
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
        Map.entry("physical critical hit","物理暴击率"),Map.entry("magical critical hit","魔法暴击率"),Map.entry("elemental property","攻击属性"));
    public static GameItemInfo read(String path,String text,GameScript.Lookup lookup,int level) throws IOException {
        var fields=GameScript.fields(text);var warnings=new ArrayList<String>();var strings=new GameScript.Strings(lookup,warnings);var rows=new ArrayList<Row>();
        String name=strings.resolve(GameScript.value(fields,"name"));if(name.isBlank())name=path.substring(path.lastIndexOf('/')+1);
        String type=GameScript.flag(GameScript.value(fields,"equipment type"));type=switch(type){case "weapon"->"武器";case "coat avatar"->"上衣装扮";case "pants avatar"->"下装装扮";case "skin avatar"->"皮肤装扮";case "hair avatar"->"头发装扮";case "shoes avatar"->"鞋子装扮";case "cap avatar"->"帽子装扮";case "face avatar"->"脸部装扮";case "belt avatar"->"腰部装扮";case "neck avatar"->"胸部装扮";case "title name"->"称号";case ""->path.endsWith(".skl")?"技能":"物品";default->type;};
        int maximum=Math.max(1,GameScript.integer(GameScript.value(fields,"maximum level"),1));int chosen=Math.max(1,Math.min(level,maximum));
        if(path.endsWith(".skl"))rows.add(new Row("预览等级",chosen+" / "+maximum));
        for(var f:fields) {
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
        }
        var explanation=new StringJoiner("\n\n");
        for(String tag:List.of("explain","basic explain","flavor text","command key explain"))for(var f:fields)if(f.tag().equals(tag)&&!f.text().isBlank())explanation.add(strings.resolve(f.text()));
        boolean condition=false;var conditions=new StringJoiner("\n");
        for(var f:fields){if(f.tag().equals("if")||f.tag().equals("then")){condition=true;conditions.add("["+f.tag()+"]");}else if(f.tag().equals("/then")){condition=false;}else if(condition&&!f.tag().startsWith("/"))conditions.add("["+f.tag()+"] "+strings.resolve(f.text()));}
        if(conditions.length()>0)explanation.add("配置触发条件\n"+conditions);
        return new GameItemInfo(name,strings.resolve(GameScript.value(fields,"name2")),type,GameScript.integer(GameScript.value(fields,"rarity"),0),List.copyOf(rows),explanation.toString(),List.copyOf(new LinkedHashSet<>(warnings)));
    }
}
