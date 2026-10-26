package studio.pvf.intellij;
import studio.pvf.sceneedit.DungeonDocument;
import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.preview.DungeonScript.Position;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;
import static studio.pvf.intellij.VisualDungeonWorkbenchSmoke.named;
/** Reproduces the user's mixed-newline Lorien file with the real Swing/IntelliJ product. */
final class DungeonUsabilitySmoke {
 static void run(Path archive,Path output)throws Exception{
  var frame=edt(() -> {var f=new PvfStudioFrame(output.resolve("usability-workspaces"),false);f.setSize(1264,811);f.setVisible(true);f.openArchive(archive);return f;});
  try{
   await(() -> button(frame,"打开 PVF…").isEnabled());var wf=PvfStudioFrame.class.getDeclaredField("workspace");wf.setAccessible(true);var w=edt(() -> (PvfWorkspace)wf.get(frame));var pf=PvfStudioFrame.class.getDeclaredField("preview");pf.setAccessible(true);var preview=edt(() -> (ResourcePreviewPanel)pf.get(frame));String path="dungeon/act1/lorien.dgn";
   edt(() -> {if(System.getProperty("pvf.preview.npk")!=null)preview.scan(Path.of(System.getProperty("pvf.preview.npk")));frame.openEntry(path);return null;});await(() -> button(frame,"打开 PVF…").isEnabled()&&!components(frame,DungeonEditorPanel.class).isEmpty());
   var tabs=edt(() -> components(frame,EditorTabs.class).getFirst());var code=edt(() -> editor(tabs.getSelectedComponent()));check(!code.isViewer(),"Mixed CRLF/LF Lorien was opened as a viewer; every graphic action is disabled");
   var panel=edt(() -> components(frame,DungeonEditorPanel.class).getFirst());await(() -> named(panel,"dgn-apply-maps",JButton.class).isEnabled());edt(() -> {panel.selectRoom(new Position(2,0));return null;});
   await(() -> preview.renderedScene()!=null&&!named(panel,"dgn-room-boss-maps",JTextField.class).getText().isBlank());
   String baseline=edt(() -> code.getDocument().getText());String original=w.readText(path,Charset.forName("GBK"));int id=edt(() -> Integer.parseInt(named(panel,"dgn-room-boss-maps",JTextField.class).getText().split(",")[0].trim()));
   edt(() -> {named(panel,"dgn-room-boss-maps",JTextField.class).setText(Integer.toString(id));named(panel,"dgn-apply-maps",JButton.class).doClick();return null;});await(() -> !code.getDocument().getText().equals(baseline)&&named(panel,"dgn-apply-maps",JButton.class).isEnabled());
   edt(() -> {var parsed=DungeonDocument.parse(path,code.getDocument().getText());check(parsed.mazes().getFirst().layout().bossMaps().get(new Position(2,0)).equals(java.util.List.of(id)),"Boss candidate was not editable");UndoManager.getGlobalInstance().undo(TextEditorProvider.getInstance().getTextEditor(code));return null;});await(() -> code.getDocument().getText().equals(baseline));
   edt(() -> {UndoManager.getGlobalInstance().redo(TextEditorProvider.getInstance().getTextEditor(code));button(frame,"保存草稿").doClick();return null;});await(() -> button(frame,"打开 PVF…").isEnabled());check(w.dirtyPaths().contains(path),"Edited mixed-newline DGN was not saved");
   String saved=w.readText(path,Charset.forName("GBK"));String expectedRaw=DungeonDocument.parse(path,original).setCandidates(0,new Position(2,0),true,java.util.List.of(id)).applyTo(original);check(saved.equals(expectedRaw),"A candidate edit changed untouched mixed-newline source bytes");check(saved.contains("\r\n")&&saved.replace("\r\n","").contains("\n"),"Saving normalized away mixed source line endings");
   await(() -> !named(panel,"dgn-room-maps",JTextField.class).getText().isBlank()&&preview.renderedScene()!=null&&!preview.renderedScene().draws().isEmpty());
   edt(() -> {var split=(JSplitPane)((BorderLayout)panel.getLayout()).getLayoutComponent(BorderLayout.CENTER);check(split.getLeftComponent()==panel.previewHost(),"Duplicate upper preview region still occupies main column");var canvas=components(preview,ResourcePreviewPanel.Canvas.class).getFirst();check(canvas.getHeight()>350,"Main preview lost height to duplicate/empty panels");var scroll=named(panel,"dgn-properties-scroll",JScrollPane.class);scroll.getVerticalScrollBar().setValue(0);scroll.dispatchEvent(new MouseWheelEvent(scroll,MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,20,200,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,1));check(scroll.getViewport().getViewPosition().y>=40,"One wheel notch barely moves the inspector");scroll.getVerticalScrollBar().setValue(0);screenshot(frame,output.resolve("lorien-editor-1264.png"));frame.setSize(1024,720);return null;});edt(() -> {screenshot(frame,output.resolve("lorien-editor-1024.png"));return null;});
   for(String other:java.util.List.of("dungeon/act1/lorieninside.dgn","dungeon/act1/grakkarak.dgn",path)){edt(() -> {frame.openEntry(other);return null;});await(() -> button(frame,"打开 PVF…").isEnabled()&&named((Container)tabs.getSelectedComponent(),"dgn-apply-maps",JButton.class).isEnabled());}
   w.archive().verifyUnchanged();w.export(output.resolve("usability-export.pvf"));Files.write(output.resolve("expected-dgn.bin"),w.readBytes(path));Files.writeString(output.resolve("changes.json"),"{\""+path+"\":\"expected-dgn.bin\"}");Files.writeString(output.resolve("original-dgn.txt"),original);Files.writeString(output.resolve("saved-dgn.txt"),saved);verifyNewlineProvenance(frame,w);Files.writeString(output.resolve("dungeon-usability-result.txt"),"PASS: mixed-newline Lorien editable; automatic choices visible; candidate apply/undo/redo/save/export; single full-height preview; fast inspector; related mixed-newline tabs writable; original PVF unchanged\n");
  }catch(Exception|AssertionError failure){edt(() -> {screenshot(frame,output.resolve("dungeon-usability-failure.png"));return null;});throw failure;}
  finally{edt(() -> {respond("放弃未保存编辑");frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));return null;});await(() -> !frame.isDisplayable());}
 }
 private static void verifyNewlineProvenance(PvfStudioFrame frame,PvfWorkspace w)throws Exception{
  String path="etc/__newline_provenance.etc",raw="a\r\na\nb\r\n";w.createFile(path,raw.getBytes(Charset.forName("GBK")));edt(() -> {frame.openEntry(path);return null;});await(() -> button(frame,"打开 PVF…").isEnabled());var tabs=edt(() -> components(frame,EditorTabs.class).getFirst());var code=edt(() -> editor(tabs.getSelectedComponent()));
  for(int offset:java.util.List.of(0,2)){
   edt(() -> {com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(code.getProject(),() -> com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> code.getDocument().deleteString(offset,offset+2)),"删除指定的重复行",null);return null;});
   String expected=offset==0?"a\nb\r\n":"a\r\nb\r\n";saveAndCheck(frame,w,path,expected);
   edt(() -> {UndoManager.getGlobalInstance().undo(TextEditorProvider.getInstance().getTextEditor(code));return null;});saveAndCheck(frame,w,path,raw);
   edt(() -> {UndoManager.getGlobalInstance().redo(TextEditorProvider.getInstance().getTextEditor(code));return null;});saveAndCheck(frame,w,path,expected);
   edt(() -> {UndoManager.getGlobalInstance().undo(TextEditorProvider.getInstance().getTextEditor(code));return null;});saveAndCheck(frame,w,path,raw);
  }
  String blankPath="etc/__blank_provenance.etc",blank="[maze info]\r\n\r\n\n[size] 1 1\r\n";w.createFile(blankPath,blank.getBytes(Charset.forName("GBK")));edt(() -> {frame.openEntry(blankPath);return null;});await(() -> button(frame,"打开 PVF…").isEnabled());var blankCode=edt(() -> editor(tabs.getSelectedComponent()));int blankAt="[maze info]\n".length();
  edt(() -> {com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(blankCode.getProject(),() -> com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> blankCode.getDocument().deleteString(blankAt,blankAt+1)),"删除指定空行",null);return null;});saveAndCheck(frame,w,blankPath,"[maze info]\r\n\n[size] 1 1\r\n");
  edt(() -> {UndoManager.getGlobalInstance().undo(TextEditorProvider.getInstance().getTextEditor(blankCode));return null;});saveAndCheck(frame,w,blankPath,blank);
  String mapPath="map/__newline_properties.map",map="[passive object]\r\n1 100\n120 5\r\n[/passive object]\n";w.createFile(mapPath,map.getBytes(Charset.forName("GBK")));edt(() -> {frame.openEntry(mapPath);return null;});await(() -> button(frame,"打开 PVF…").isEnabled()&&named((Container)tabs.getSelectedComponent(),"map-add-selected",JButton.class).isEnabled());
  var mapPanel=edt(() -> (MapEditorPanel)((VisualEditorSurface)tabs.getSelectedComponent()).visual());edt(() -> {mapPanel.selectSourceOffset(map.indexOf("100"));named(mapPanel,"map-prop-x",JTextField.class).setText("110");named(mapPanel,"map-prop-y",JTextField.class).setText("130");named(mapPanel,"map-apply-properties",JButton.class).doClick();return null;});
  String expectedMap=studio.pvf.sceneedit.MapDocument.parse(mapPath,map).move(studio.pvf.sceneedit.MapDocument.parse(mapPath,map).entries().getFirst().id(),110,130,5).applyTo(map);saveAndCheck(frame,w,mapPath,expectedMap);
 }
 private static void saveAndCheck(PvfStudioFrame frame,PvfWorkspace w,String path,String expected)throws Exception{
  edt(() -> {button(frame,"保存草稿").doClick();return null;});await(() -> button(frame,"打开 PVF…").isEnabled());check(w.readText(path,Charset.forName("GBK")).equals(expected),"Newline provenance changed an untouched terminator: "+path);
 }

}
