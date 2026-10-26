package studio.pvf.intellij;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MixedNewlinesTest {
 private static String edit(PvfStudioFrame.Newlines lines,String before,int offset,int length,String added,long oldStamp,long newStamp){String after=before.substring(0,offset)+added+before.substring(offset+length);lines.recordChange(before,after,offset,before.substring(offset,offset+length),added,oldStamp,newStamp);return after;}
 @Test void deletingTheFirstRepeatedLinePreservesTheSecondLinesOwnEnding(){
  String original="a\na\nb\n";var lines=new PvfStudioFrame.Newlines("a\r\na\nb\r\n",1);
  String first=edit(lines,original,0,2,"",1,2);assertEquals("a\nb\r\n",lines.restore(first,2));
  assertEquals("a\r\na\nb\r\n",lines.restore(original,1));String second=edit(lines,original,2,2,"",1,3);assertEquals(first,second);assertEquals("a\r\nb\r\n",lines.restore(second,3));
  edit(lines,second,2,0,"a\n",3,1);assertEquals("a\r\na\nb\r\n",lines.restore(original,1));edit(lines,original,2,2,"",1,3);assertEquals("a\r\nb\r\n",lines.restore(second,3));
 }
 @Test void insertAndDeleteKeepUntouchedMixedLineEndingsAtTheirActualSourceLocations(){
  String original="a\nb\nc\nd\n";var lines=new PvfStudioFrame.Newlines("a\r\nb\nc\rd\r\n",1);
  String inserted=edit(lines,original,2,0,"new\n",1,2);assertEquals("a\r\nnew\r\nb\nc\rd\r\n",lines.restore(inserted,2));
  String deleted=edit(lines,original,0,2,"",1,3);assertEquals("b\nc\rd\r\n",lines.restore(deleted,3));assertEquals("a\r\nb\nc\rd\r\n",lines.restore(original,1));
 }
 @Test void mixedBlankLineDeletionRetainsTheRemainingBlankLinesTerminator(){
  String original="[maze info]\n\n\n[size] 1 1\n";var lines=new PvfStudioFrame.Newlines("[maze info]\r\n\r\n\n[size] 1 1\r\n",10);
  String after=edit(lines,original,"[maze info]\n".length(),1,"",10,11);assertEquals("[maze info]\r\n\n[size] 1 1\r\n",lines.restore(after,11));assertEquals("[maze info]\r\n\r\n\n[size] 1 1\r\n",lines.restore(original,10));
 }
}
