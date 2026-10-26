package studio.pvf.intellij;
import studio.pvf.sceneedit.*;
import studio.pvf.workspace.PvfWorkspace;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import javax.swing.*;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;
import static studio.pvf.intellij.VisualDungeonWorkbenchSmoke.named;

/** Native Document lifecycle and global undo after closing/reopening an affected tab. */
final class VisualSceneEditorSmoke {
 static void run(Path archive,Path output)throws Exception{
  var frame=edt(() -> {var f=new PvfStudioFrame(output.resolve("scene-workspaces"),false);f.setSize(1264,811);f.setVisible(true);f.openArchive(archive);return f;});
  try{
   await(() -> button(frame,"打开 PVF…").isEnabled());
   var wf=PvfStudioFrame.class.getDeclaredField("workspace");wf.setAccessible(true);var workspace=edt(() -> (PvfWorkspace)wf.get(frame));
   String dgn=workspace.paths().contains("dungeon/act9/kohlepowerstation.dgn")?"dungeon/act9/kohlepowerstation.dgn":"dungeon/act3/2ndbackbone.dgn";
   String map=workspace.paths().contains("map/fruz_station/12057(3,2)n.map")?"map/fruz_station/12057(3,2)n.map":"map/lorien/104.map";
   var pf=PvfStudioFrame.class.getDeclaredField("preview");pf.setAccessible(true);var preview=edt(() -> (ResourcePreviewPanel)pf.get(frame));
   edt(() -> {if(System.getProperty("pvf.preview.npk")!=null)preview.scan(Path.of(System.getProperty("pvf.preview.npk")));frame.openEntry(map);return null;});
   await(() -> button(frame,"打开 PVF…").isEnabled()&&!components(frame,MapEditorPanel.class).isEmpty());
   var tabs=edt(() -> components(frame,EditorTabs.class).getFirst());var original=edt(() -> editor(tabs.getSelectedComponent()));
   String etc=map+".etc";
   edt(() -> {frame.renameEntry(map,etc);return null;});await(() -> button(frame,"打开 PVF…").isEnabled()&&tabs.getToolTipTextAt(tabs.getSelectedIndex()).startsWith(etc));
   check(edt(() -> editor(tabs.getSelectedComponent())==original),"Extension rename replaced Document/Editor");
   edt(() -> {frame.renameEntry(etc,map);return null;});await(() -> !components(frame,MapEditorPanel.class).isEmpty()&&named(frame,"map-add-selected",JButton.class).isEnabled());
   check(edt(() -> editor(tabs.getSelectedComponent())==original),"Restoring MAP extension replaced editor");
   edt(() -> {frame.openEntry(dgn);return null;});await(() -> !components(frame,DungeonEditorPanel.class).isEmpty()&&named(frame,"dgn-apply-maps",JButton.class).isEnabled());
   var panel=edt(() -> components(frame,DungeonEditorPanel.class).getFirst());var code=edt(() -> editor(tabs.getSelectedComponent()));String baseline=edt(() -> code.getDocument().getText());
   var doc=DungeonDocument.parse(dgn,baseline);var room=doc.mazes().getFirst().layout().starts().getFirst();var list=SceneCatalog.build(workspace.paths(),p -> workspace.readText(p,Charset.forName("GBK"))).maps();
   var registered=list.entrySet().stream().filter(e -> e.getKey()>0&&workspace.paths().contains(e.getValue().startsWith("map/")?e.getValue():"map/"+e.getValue())).sorted(Map.Entry.comparingByKey()).findFirst().orElseThrow();int id=registered.getKey();String cloneSource=registered.getValue().startsWith("map/")?registered.getValue():"map/"+registered.getValue();
   String clone=cloneSource.substring(0,cloneSource.length()-4)+"_lifecycle.map";byte[] before=workspace.readBytes(dgn);
   edt(() -> {frame.cloneRoomMap(dgn,new DungeonEditorPanel.Preview(0,room,id,false),clone);return null;});await(() -> button(frame,"打开 PVF…").isEnabled()&&workspace.paths().contains(clone));
   String cloned=edt(() -> code.getDocument().getText());
   edt(() -> {close(frame);return null;});edt(() -> {frame.openEntry(dgn);return null;});await(() -> button(frame,"打开 PVF…").isEnabled()&&!components(frame,DungeonEditorPanel.class).isEmpty());
   var reopened=edt(() -> editor(tabs.getSelectedComponent()));check(reopened!=code,"Test did not close/reopen affected Document");
   edt(() -> {var file=TextEditorProvider.getInstance().getTextEditor(reopened);check(UndoManager.getGlobalInstance().isUndoAvailable(file),"Global clone undo is unavailable after reopening affected tab");var timer=new javax.swing.Timer(300,e -> {for(var window:java.awt.Window.getWindows())if(window instanceof JDialog dialog&&dialog.isShowing()){System.err.println("UNDO_DIALOG "+dialog.getTitle()+" "+components(dialog,JLabel.class).stream().map(JLabel::getText).toList());components(dialog,JButton.class).stream().filter(b -> b.getText().equals("OK")||b.getText().equals("确定")).findFirst().ifPresent(JButton::doClick);}});timer.start();UndoManager.getGlobalInstance().undo(file);timer.stop();return null;});
   await(() -> !workspace.paths().contains(clone));check(edt(() -> reopened.getDocument().getText().equals(baseline)),"Reopened document stayed stale after global undo");check(Arrays.equals(before,workspace.readBytes(dgn)),"Workspace DGN failed to undo");
   edt(() -> {UndoManager.getGlobalInstance().redo(TextEditorProvider.getInstance().getTextEditor(reopened));return null;});await(() -> workspace.paths().contains(clone));check(edt(() -> reopened.getDocument().getText().equals(cloned)),"Reopened document failed global redo");
   await(() -> named(frame,"dgn-apply-maps",JButton.class).isEnabled());
   edt(() -> {components(frame,DungeonEditorPanel.class).getFirst().selectRoom(room);return null;});
   edt(() -> {named(frame,"dgn-open-map",JButton.class).doClick();return null;});await(() -> button(frame,"打开 PVF…").isEnabled()&&tabs.getToolTipTextAt(tabs.getSelectedIndex()).startsWith(clone));
   String renamedDgn=dgn.substring(0,dgn.length()-4)+"_lifecycle.dgn";edt(() -> {frame.renameEntry(dgn,renamedDgn);return null;});await(() -> button(frame,"打开 PVF…").isEnabled()&&workspace.paths().contains(renamedDgn));
   edt(() -> {named((JComponent)tabs.getSelectedComponent(),"scene-return-dungeon",JButton.class).doClick();return null;});await(() -> tabs.getToolTipTextAt(tabs.getSelectedIndex()).startsWith(renamedDgn));
   edt(() -> {screenshot(frame,output.resolve("scene-lifecycle.png"));return null;});Files.writeString(output.resolve("visual-scene-result.txt"),"PASS: extension rename preserves Editor/Document; global clone undo/redo after affected tab closes/reopens; workspace and reopened Document agree\n");
  }catch(Exception|AssertionError failure){edt(() -> {screenshot(frame,output.resolve("scene-lifecycle-failure.png"));return null;});throw failure;}
  finally{edt(() -> {respond("放弃未保存编辑");frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));return null;});await(() -> !frame.isDisplayable());}
 }
 private static void close(PvfStudioFrame frame)throws Exception{var method=PvfStudioFrame.class.getDeclaredMethod("closeSelectedTab");method.setAccessible(true);method.invoke(frame);}
}
