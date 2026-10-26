package studio.pvf.intellij;
import java.util.*;
import java.io.IOException;
/** Preflight all currently held affected paths, including documents opened after publication. */
final class SceneDocumentReconciliation {
 static Map<String,String> targets(Map<String,String> before,Map<String,String> after,Map<String,String> current,boolean toAfter)throws IOException{
  var result=new LinkedHashMap<String,String>();var expected=toAfter?before:after;var target=toAfter?after:before;
  for(String path:before.keySet())if(current.containsKey(path)){
   if(!Objects.equals(current.get(path),expected.get(path)))throw new IOException("关联文档已有后续编辑，请先撤销这些编辑："+path);
   result.put(path,target.get(path));
  }return Map.copyOf(result);
 }
}
