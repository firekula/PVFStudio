package studio.pvf.intellij;

import studio.pvf.workspace.*;
import studio.pvf.core.PvfArchive;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Actual 86 background/viewport switching and persisted tree mutations, isolated from user workspaces. */
final class MapFilesWorkbenchSmoke {
    static void run(Path archive, Path output) throws Exception {
        String map="map/247_blackvolcano/58461(0,1)s.map", far="map/247_blackvolcano/animation/far.ani";
        Path workspaces=output.resolve("workspaces");
        PvfStudioFrame frame=edt(()->{var f=new PvfStudioFrame(workspaces,false);f.setSize(1450,950);f.setVisible(true);return f;});
        try {
            await(()->button(frame,"打开最近的文件").isEnabled()); edt(()->{frame.openArchive(archive);return null;}); await(()->ready(frame));
            var field=PvfStudioFrame.class.getDeclaredField("workspace");field.setAccessible(true);
            var previewField=PvfStudioFrame.class.getDeclaredField("preview");previewField.setAccessible(true);
            PvfWorkspace workspace=edt(()->(PvfWorkspace)field.get(frame));ResourcePreviewPanel panel=edt(()->(ResourcePreviewPanel)previewField.get(frame));
            edt(()->{frame.openEntry(map);return null;}); await(()->ready(frame)&&panel.renderedScene()!=null&&panel.previewDetails().contains(map));
            var canvas=edt(()->components(panel,ResourcePreviewPanel.Canvas.class).getFirst());
            var viewport=edt(()->components(panel,JCheckBox.class).stream().filter(c->c.getText().equals("客户端视口")).findFirst().orElseThrow());
            edt(()->{
                check(panel.renderedScene().activeFrames().keySet().stream().anyMatch(p->p.endsWith(":"+far)),"Nested background ANI missing from tracks");
                check(panel.renderedScene().draws().stream().anyMatch(d->d.prepared().source().img().endsWith("/far.img")),"Far IMG did not decode");
                check(canvas.world.getMinY()<panel.renderedScene().bounds().getMinY(),"Overview still clips scenery to floor bounds");
                screenshot(frame,output.resolve("volcano-overview.png"));return null;
            });
            BufferedImage before=edt(()->capture(canvas));
            edt(()->{viewport.doClick();check(canvas.usingClient(),"Client mode not activated");screenshot(frame,output.resolve("volcano-client.png"));frame.openEntry(far);return null;});
            await(()->ready(frame)&&panel.hasPreparedFrame());
            // Disabling while an ANI is active used to leave the canvas' cached mode behind.
            edt(()->{viewport.setSelected(false);frame.openEntry(map);return null;});
            await(()->ready(frame)&&panel.renderedScene()!=null&&panel.previewDetails().contains(map));
            edt(()->{check(!canvas.usingClient(),"Client clipping survived a source switch");check(same(before,capture(canvas)),"Overview changed after client/ANI/off/MAP cycle");return null;});
            String other=workspace.paths().stream().filter(p->p.startsWith("map/247_blackvolcano/")&&p.endsWith(".map")&&!p.equals(map)).findFirst().orElseThrow();
            edt(()->{viewport.doClick();frame.openEntry(other);return null;});await(()->ready(frame)&&panel.renderedScene()!=null&&panel.previewDetails().contains(other));
            edt(()->{viewport.doClick();frame.openEntry(map);return null;});await(()->ready(frame)&&panel.renderedScene()!=null&&panel.previewDetails().contains(map));
            edt(()->{check(!viewport.isSelected()&&!canvas.usingClient(),"Client clipping survived map switching");check(same(before,capture(canvas)),"Overview did not restore after MAP/MAP cycle");screenshot(frame,output.resolve("volcano-restored.png"));return null;});
            // The real context menu opens the standard create dialog.
            edt(()->{JTree tree=components(frame,JTree.class).getFirst();tree.setSelectionRow(0);Rectangle b=tree.getRowBounds(0);
                tree.dispatchEvent(new MouseEvent(tree,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),InputEvent.BUTTON3_DOWN_MASK,b.x+6,b.y+6,1,true,MouseEvent.BUTTON3));
                var menu=Arrays.stream(MenuSelectionManager.defaultManager().getSelectedPath()).filter(e->e instanceof JPopupMenu).map(e->(JPopupMenu)e).findFirst().orElse(null);
                if(menu==null)menu=Arrays.stream(Window.getWindows()).flatMap(w->components(w,JPopupMenu.class).stream()).filter(Component::isShowing).findFirst().orElseThrow();
                screenshot(menu,output.resolve("tree-context-menu.png"));
                for(String title:new String[]{"新建文件…","新建文件夹…","重命名…","删除…"}) check(button(menu,title)!=null,"Missing tree action "+title);
                MenuSelectionManager.defaultManager().clearSelectedPath();frame.createEntry("pvfstudio_test/empty",true);return null;});await(()->ready(frame));
            edt(()->{frame.createEntry("pvfstudio_test/new.ani",false);return null;});await(()->ready(frame)&&workspaceContains(workspace,"pvfstudio_test/new.ani"));
            await(()->tabs(frame).getSelectedComponent()!=null&&tabs(frame).getTitleAt(tabs(frame).getSelectedIndex()).equals("new.ani"));
            String text=workspace.readText(far,java.nio.charset.Charset.forName("Big5"));
            edt(()->{var editor=editor(tabs(frame).getSelectedComponent());
                com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(editor.getProject(),()->com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(()->editor.getDocument().setText(text)),"New binary ANI",null);
                frame.renameEntry("pvfstudio_test","renamed_test");return null;});await(()->ready(frame)&&workspaceContains(workspace,"renamed_test/new.ani"));
            edt(()->{check(tabs(frame).getToolTipTextAt(tabs(frame).getSelectedIndex()).startsWith("renamed_test/new.ani"),"Rename left stale editor path");button(frame,"保存草稿").doClick();return null;});await(()->ready(frame));
            check(workspace.readText("renamed_test/new.ani",java.nio.charset.Charset.forName("Big5")).contains("Far.img"),"Renamed unsaved ANI did not compile");
            edt(()->{frame.renameEntry(far,"renamed_test/original_far.ani");return null;});await(()->ready(frame)&&workspaceContains(workspace,"renamed_test/original_far.ani"));
            edt(()->{frame.deleteEntry(map);return null;});await(()->ready(frame)&&!workspaceContains(workspace,map));
            Path exported=output.resolve("structure-edited.pvf");workspace.export(exported);
            Files.write(output.resolve("new.ani"),workspace.readBytes("renamed_test/new.ani"));
            try(var result=PvfArchive.open(exported)) {
                check(result.fileCount()==workspace.archive().fileCount(),"Unexpected structural entry count");
                check(Arrays.equals(workspace.archive().readBytes(far),result.readBytes("renamed_test/original_far.ani")),"Renamed original payload changed");
                check(Arrays.equals(workspace.readBytes("renamed_test/new.ani"),result.readBytes("renamed_test/new.ani")),"New ANI missing from export");
                check(result.entries().stream().noneMatch(e->e.path().equals(map)||e.path().equals(far)),"Deleted paths remained in directory");
            }
            // Reopen the same original source and restore directory changes and renamed tabs.
            edt(()->{frame.openArchive(archive);return null;});await(()->ready(frame));
            PvfWorkspace restored=edt(()->(PvfWorkspace)field.get(frame));
            check(restored.contains("renamed_test/new.ani")&&!restored.contains(map)&&restored.folders().contains("renamed_test/empty"),"Structural manifest did not restore");
            Files.writeString(output.resolve("map-files-result.txt"),"PASS: real 86 nested Far IMG; identical full overview after client/ANI/MAP and client/MAP/MAP cycles; tree context actions; new binary ANI, directory rename preserving unsaved editor, save, original rename, deletion, persistent reopen and export\n");
        } finally {edt(()->{respond("放弃未保存编辑");frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));return null;});await(()->!frame.isDisplayable());}
    }
    private static boolean workspaceContains(PvfWorkspace workspace,String path){try{return workspace.contains(path);}catch(Exception e){return false;}}
    private static boolean ready(PvfStudioFrame frame){return frame.isEnabled()&&button(frame,"打开 PVF…").isEnabled();}
    private static EditorTabs tabs(PvfStudioFrame frame){return components(frame,EditorTabs.class).getFirst();}
    private static BufferedImage capture(Component c){BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();c.paint(g);g.dispose();return image;}
    private static boolean same(BufferedImage a,BufferedImage b){return a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight()&&Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));}
}
