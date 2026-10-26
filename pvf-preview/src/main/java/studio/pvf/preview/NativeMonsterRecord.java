package studio.pvf.preview;
import java.io.IOException;
import java.util.List;
/** Shared native boundary contract for preview ownership and editable source records. */
public record NativeMonsterRecord(int width,int typeIndex,int traitCountIndex) {
 public static NativeMonsterRecord read(List<String> values,int start)throws IOException{
  need(values,start,10);int type=ScriptDialect.flag(values.get(start+9)).equals("npc")?11:9;need(values,start,type+1);int width=type+1,traits=-1;String kind=ScriptDialect.flag(values.get(start+type));
  if(kind.equals("champion")||kind.equals("super champion")){need(values,start,width+1);int count;try{count=Integer.parseInt(values.get(start+width));}catch(NumberFormatException invalid){throw new IOException("精英特性数量无效");}if(count<0||count>64)throw new IOException("精英特性数量超出范围");traits=width;width+=count+1;}
  need(values,start,width);return new NativeMonsterRecord(width,type,traits);
 }
 private static void need(List<?> values,int start,int count)throws IOException{if(start<0||count<0||start+count>values.size())throw new IOException("放置参数尚未完整");}
}
