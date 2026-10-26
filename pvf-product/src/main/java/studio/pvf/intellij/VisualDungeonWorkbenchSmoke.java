package studio.pvf.intellij;

import studio.pvf.sceneedit.DungeonDocument;
import studio.pvf.workspace.PvfWorkspace;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import javax.swing.*;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.util.*;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Actual design surface, native room pixels, source writes and one global cross-file undo. */
final class VisualDungeonWorkbenchSmoke {
    static void run(Path archive,Path output) throws Exception {
        var frame=edt(() -> { var f=new PvfStudioFrame(output.resolve("visual-workspaces"),false); f.setSize(1264,811); f.setVisible(true); return f; });
        try {
            await(() -> button(frame,"打开最近的文件").isEnabled());
            edt(() -> { frame.openArchive(archive); return null; }); await(() -> ready(frame));
            var wf=PvfStudioFrame.class.getDeclaredField("workspace"); wf.setAccessible(true);
            var pf=PvfStudioFrame.class.getDeclaredField("preview"); pf.setAccessible(true);
            var workspace=edt(() -> (PvfWorkspace)wf.get(frame)); var preview=edt(() -> (ResourcePreviewPanel)pf.get(frame));
            String path=workspace.paths().contains("dungeon/act9/kohlepowerstation.dgn")?"dungeon/act9/kohlepowerstation.dgn":"dungeon/act3/2ndbackbone.dgn";
            edt(() -> { if(System.getProperty("pvf.preview.npk")!=null)preview.scan(Path.of(System.getProperty("pvf.preview.npk"))); frame.openEntry(path); return null; });
            await(() -> ready(frame)&&!components(frame,DungeonEditorPanel.class).isEmpty());
            var panel=edt(() -> components(frame,DungeonEditorPanel.class).getFirst());
            await(() -> named(panel,"dgn-apply-maps",JButton.class).isEnabled());
            var tabs=edt(() -> components(frame,EditorTabs.class).getFirst()); var code=edt(() -> editor(tabs.getSelectedComponent()));
            var before=edt(() -> DungeonDocument.parse(path,code.getDocument().getText()));
            var first=before.mazes().getFirst().layout().starts().getFirst();
            var hell=before.hell(0);
            if(hell.enabled()&&!hell.excluded()&&hell.position()!=null) {
                String hellReference=studio.pvf.preview.EntityScript.catalog(workspace.readText("map/map.lst",java.nio.charset.Charset.forName("GBK"))).get(hell.mapId());
                String hellPath=studio.pvf.core.PvfArchive.normalizePath(hellReference.startsWith("map/")?hellReference:"map/"+hellReference);
                String unchanged=edt(() -> code.getDocument().getText());
                edt(() -> { panel.selectRoom(hell.position()); named(panel,"dgn-preview-mode",JComboBox.class).setSelectedIndex(1); return null; });
                await(() -> preview.renderedScene()!=null&&preview.previewDetails().contains(hellPath));
                check(edt(() -> unchanged.equals(code.getDocument().getText())),"Hell preview changed source");
                edt(() -> { screenshot(frame,output.resolve("dgn-hell-preview.png")); named(panel,"dgn-preview-mode",JComboBox.class).setSelectedIndex(0); return null; });
            }
            edt(() -> { panel.selectRoom(first); return null; });
            await(() -> preview.renderedScene()!=null&&!preview.renderedScene().draws().isEmpty()&&panel.preview().mapId()!=null);
            edt(() -> {
                check(components(preview,DungeonPanel.class).stream().noneMatch(JComponent::isVisible),"Resource scan replaced the design room preview with old layout controls");
                check(components(preview,ResourcePreviewPanel.Canvas.class).getFirst().getHeight()>=60,"Design preview pixels are clipped by controls"); return null;
            });
            edt(() -> { screenshot(frame,output.resolve("dgn-design-1264.png")); frame.setSize(1024,720); return null; });
            edt(() -> { screenshot(frame,output.resolve("dgn-design-1024.png")); return null; });
            String baseline=edt(() -> code.getDocument().getText());
            edt(() -> { named(panel,"dgn-boss",JCheckBox.class).doClick(); return null; });
            await(() -> !code.getDocument().getText().equals(baseline));
            edt(() -> {
                var changed=DungeonDocument.parse(path,code.getDocument().getText());
                check(changed.mazes().getFirst().layout().bosses().contains(first),"Boss click did not update source");
                check(changed.hell(0).equals(before.hell(0)),"Boss click changed hell parameters");
                UndoManager.getGlobalInstance().undo(TextEditorProvider.getInstance().getTextEditor(code)); return null;
            });
            await(() -> code.getDocument().getText().equals(baseline)&&named(panel,"dgn-apply-maps",JButton.class).isEnabled()&&panel.preview().mapId()!=null);
            edt(() -> { named(panel,"dgn-open-map",JButton.class).doClick(); return null; });
            await(() -> ready(frame)&&tabs.getTabCount()==2);
            edt(() -> { named((JComponent)tabs.getSelectedComponent(),"scene-return-dungeon",JButton.class).doClick(); return null; });
            await(() -> tabs.getSelectedComponent() instanceof VisualEditorSurface surface && surface.visual()==panel);
            var selected=edt(panel::preview); String sourcePath=workspace.paths().contains("map/map.lst")?studio.pvf.preview.EntityScript.catalog(workspace.readText("map/map.lst",java.nio.charset.Charset.forName("GBK"))).get(selected.mapId()):null;
            check(sourcePath!=null,"Resolved room has no catalog source");
            String source=studio.pvf.core.PvfArchive.normalizePath(sourcePath.startsWith("map/")?sourcePath:"map/"+sourcePath);
            String destination=source.substring(0,source.length()-4)+"_visual_smoke.map";
            byte[] listBefore=workspace.readBytes("map/map.lst"), dungeonBefore=workspace.readBytes(path);
            edt(() -> { frame.cloneRoomMap(path,selected,destination); return null; });
            await(() -> ready(frame)&&workspace.paths().contains(destination));
            edt(() -> { check(!code.getDocument().getText().equals(baseline),"Clone did not bind DGN document");
                respond("撤销"); UndoManager.getGlobalInstance().undo(TextEditorProvider.getInstance().getTextEditor(code)); return null; });
            await(() -> ready(frame)&&!workspace.paths().contains(destination));
            check(Arrays.equals(listBefore,workspace.readBytes("map/map.lst"))&&Arrays.equals(dungeonBefore,workspace.readBytes(path)),"Global undo did not restore workspace");
            check(edt(() -> code.getDocument().getText().equals(baseline)),"Global undo did not restore the single DGN Document");
            edt(() -> { respond("重做"); UndoManager.getGlobalInstance().redo(TextEditorProvider.getInstance().getTextEditor(code)); return null; });
            await(() -> ready(frame)&&workspace.paths().contains(destination));
            edt(() -> { screenshot(frame,output.resolve("dgn-clone-redo.png")); return null; });
            Files.writeString(output.resolve("visual-dungeon-result.txt"),"PASS: native room pixels; 1264/1024 layouts; Boss/source/hell independence; map navigation/return; atomic clone + global undo/redo of workspace and Document\n");
        } catch(Exception|AssertionError failure) {
            edt(() -> {
                screenshot(frame,output.resolve("visual-dungeon-failure.png"));
                System.err.println("VISUAL_DUNGEON_LABELS "+components(frame,JLabel.class).stream().map(JLabel::getText).toList());
                for(var window:java.awt.Window.getWindows())if(window instanceof JDialog dialog&&dialog.isShowing())
                    System.err.println("VISUAL_DUNGEON_DIALOG "+dialog.getTitle()+" "+components(dialog,JOptionPane.class).stream().map(JOptionPane::getMessage).toList());
                return null;
            }); throw failure;
        } finally {
            edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING)); return null; }); await(() -> !frame.isDisplayable());
        }
    }
    private static boolean ready(PvfStudioFrame f) { return f.isEnabled()&&button(f,"打开 PVF…").isEnabled(); }
    static <T> T named(java.awt.Container root,String name,Class<T> type) { return components(root,java.awt.Component.class).stream().filter(c -> name.equals(c.getName())&&type.isInstance(c)).map(type::cast).findFirst().orElseThrow(); }
}
