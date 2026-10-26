package studio.pvf.sceneedit;
import studio.pvf.preview.DungeonScript;
import java.util.*;
import java.io.IOException;
/** Advisory association checks never rewrite a shared MAP. Gates are checked before new links. */
public final class DungeonMapChecks {
 public record Check(String path,DungeonScript.MapInfo info,List<String> messages){
  public Check{messages=List.copyOf(messages);}
  public boolean hasGate(int direction){return info!=null&&info.gates().size()==4&&info.gates().get(direction).x()>=0&&info.gates().get(direction).y()>=0;}
  public String toString(){return path+" · "+String.join("；",messages);}
 }
 public static Check inspect(String path,String text,boolean boss,int mask){
  var messages=new ArrayList<String>();DungeonScript.MapInfo info=null;
  try{info=DungeonScript.map(text);if(boss&&!info.type().equals("boss"))messages.add("Boss 候选的 MAP type 不是 boss");if(mask!=0&&!info.supports(mask))messages.add("MAP greed 未声明当前通路形状");if(info.gates().size()!=4)messages.add("缺少四方向原生 pathgate pos");
   if(boss&&MapDocument.parse(path,text).entries().stream().noneMatch(e -> e.type()==MapEntry.Type.MONSTER&&e.properties().getOrDefault("monsterType","").contains("boss")))messages.add("没有识别到 boss 类型怪物，请检查该 MAP");
  }catch(Exception failure){messages.add("MAP 解析失败："+failure.getMessage());}
  return new Check(path,info,messages);
 }
}
