package studio.pvf.intellij;

import studio.pvf.sceneedit.*;
import studio.pvf.preview.*;
import studio.pvf.workspace.PvfWorkspace;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;
import static studio.pvf.intellij.VisualDungeonWorkbenchSmoke.named;

/** Real asset browser, mouse gestures, one Document, native placements and persisted MAP drafts. */
final class VisualMapWorkbenchSmoke {
    static void run(Path archive,Path output) throws Exception {
        var frame=edt(() -> { var f=new PvfStudioFrame(output.resolve("map-workspaces"),false);f.setSize(1264,811);f.setVisible(true);return f;});
        try {
            await(() -> button(frame,"打开最近的文件").isEnabled());
            edt(() -> {frame.openArchive(archive);return null;});await(() -> ready(frame));
            var wf=PvfStudioFrame.class.getDeclaredField("workspace");wf.setAccessible(true);var pf=PvfStudioFrame.class.getDeclaredField("preview");pf.setAccessible(true);
            var workspace=edt(() -> (PvfWorkspace)wf.get(frame));var preview=edt(() -> (ResourcePreviewPanel)pf.get(frame));
            String path=workspace.paths().contains("map/fruz_station/12057(3,2)n.map")?"map/fruz_station/12057(3,2)n.map":"map/lorien/104.map";
            Charset charset=Charset.forName(workspace.archive().format()==studio.pvf.core.PvfFormat.ACT1?"GBK":"Big5");
            var catalog=SceneCatalog.build(workspace.paths(),p -> workspace.readText(p,charset));
            edt(() -> {preview.scan(Path.of(System.getProperty("pvf.preview.npk")));frame.openEntry(path);return null;});
            await(() -> ready(frame)&&!components(frame,MapEditorPanel.class).isEmpty()&&preview.renderedScene()!=null);
            var panel=edt(() -> components(frame,MapEditorPanel.class).getFirst());var tabs=edt(() -> components(frame,EditorTabs.class).getFirst());
            var code=edt(() -> editor(tabs.getSelectedComponent()));var surface=edt(() -> (VisualEditorSurface)tabs.getSelectedComponent());
            await(() -> named(panel,"map-add-selected",JButton.class).isEnabled()&&named(panel,"map-asset-list",JList.class).getModel().getSize()>0);
            String baseline=edt(() -> code.getDocument().getText());var model=MapDocument.parse(path,baseline);
            var canvas=edt(() -> components(preview,ResourcePreviewPanel.Canvas.class).getFirst());
            edt(() -> {
                check(canvas.getHeight()>=100,"MAP design canvas is clipped");screenshot(frame,output.resolve("map-design-1264.png"));frame.setSize(1024,720);return null;
            });
            edt(() -> {screenshot(frame,output.resolve("map-design-1024.png"));frame.setSize(1264,811);canvas.clientViewport=false;canvas.freeZoom=1;return null;});
            var movable=edt(() -> model.entries().stream().filter(e -> e.type()!=MapEntry.Type.BACKGROUND).filter(e -> {
                var hits=new SceneHitIndex(model,canvas.scene).hits(canvas,canvas.worldToView(e.x(),SceneCoordinates.screenY(e.y(),e.z()),1));return !hits.isEmpty()&&hits.getFirst().equals(e.id());
            }).findFirst().orElseThrow());
            System.err.println("VISUAL_MAP_STEP drag "+movable.id());
            edt(() -> {
                var point=canvas.worldToView(movable.x(),SceneCoordinates.screenY(movable.y(),movable.z()),1);
                canvas.panX+=canvas.getWidth()/2.-point.getX();canvas.panY+=canvas.getHeight()/2.-point.getY();
                point=canvas.worldToView(movable.x(),SceneCoordinates.screenY(movable.y(),movable.z()),1);
                int x=(int)Math.round(point.getX()),y=(int)Math.round(point.getY());
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,0,x,y,1,false,MouseEvent.BUTTON1));
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,0,MouseEvent.BUTTON1_DOWN_MASK,x+20,y+10,0,false,MouseEvent.NOBUTTON));
                canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,0,0,x+20,y+10,1,false,MouseEvent.BUTTON1));return null;
            });
            await(() -> !code.getDocument().getText().equals(baseline));
            System.err.println("VISUAL_MAP_STEP undo");
            edt(() -> {
                var moved=MapDocument.parse(path,code.getDocument().getText()).entries().stream().filter(e -> e.id().equals(movable.id())).findFirst().orElseThrow();
                check(moved.x()==movable.x()+20&&moved.y()==movable.y()+10&&moved.z()==movable.z(),"Mouse drag changed the wrong placement or Z");
                UndoManager.getGlobalInstance().undo(TextEditorProvider.getInstance().getTextEditor(code));return null;
            });
            await(() -> code.getDocument().getText().equals(baseline)&&named(panel,"map-add-selected",JButton.class).isEnabled());
            int count=model.entries().size();
            System.err.println("VISUAL_MAP_STEP entity-add");
            for(var kind:List.of(EntityScript.Kind.MONSTER,EntityScript.Kind.PASSIVE)) {
                int id=model.entries().stream().filter(e -> kind==EntityScript.Kind.MONSTER?e.type()==MapEntry.Type.MONSTER:e.type()==MapEntry.Type.PASSIVE).mapToInt(MapEntry::definitionId).filter(n -> catalog.entities(kind).containsKey(n)).findFirst().orElseGet(() -> catalog.entities(kind).keySet().iterator().next());
                String asset=catalog.entities(kind).get(id);var category=kind==EntityScript.Kind.MONSTER?SceneResourceBrowser.Category.MONSTER:SceneResourceBrowser.Category.PASSIVE;
                int expected=++count;
                edt(() -> {panel.browser().select(category,asset);panel.addSelectedAt(300,220);return null;});
                await(() -> entryCount(code,path)==expected&&named(panel,"map-add-selected",JButton.class).isEnabled());
            }
            String ani=catalog.filesUnder(path,".ani").stream().findFirst().orElseThrow();int withAni=++count;
            System.err.println("VISUAL_MAP_STEP animation-add");
            edt(() -> {panel.browser().select(SceneResourceBrowser.Category.ANIMATION,ani);panel.addSelectedAt(340,220);return null;});
            await(() -> entryCount(code,path)==withAni&&named(panel,"map-add-selected",JButton.class).isEnabled());
            String factory=null;
            for(var asset:catalog.entities(EntityScript.Kind.PASSIVE).entrySet())if(asset.getValue().contains("particlefactory")) {
                try {if(EntityScript.particleFactory(workspace.readText(asset.getValue(),charset),asset.getKey())!=null){factory=asset.getValue();break;}}catch(Exception ignored){}
            }
            check(factory!=null,"Fixture has no recognized particle factory");String selectedFactory=factory;int withFactory=++count;
            System.err.println("VISUAL_MAP_STEP factory-add");
            edt(() -> {panel.browser().select(SceneResourceBrowser.Category.PASSIVE,selectedFactory);panel.addSelectedAt(380,220);return null;});
            await(() -> entryCount(code,path)==withFactory&&named(panel,"map-add-selected",JButton.class).isEnabled());
            String tile=catalog.filesUnder(path,".til").stream().findFirst().orElseThrow();int tileCount=model.tiles(false).size()+1;
            System.err.println("VISUAL_MAP_STEP tile-add");
            edt(() -> {panel.tileColumnPicker(defaults -> defaults);panel.browser().select(SceneResourceBrowser.Category.TILE,tile);return null;});
            await(() -> panel.browser().tileTypeKnown());edt(() -> {panel.addSelectedAt(400,220);return null;});
            await(() -> {try{return MapDocument.parse(path,code.getDocument().getText()).tiles(false).size()==tileCount&&named(panel,"map-add-selected",JButton.class).isEnabled();}catch(Exception e){return false;}});
            edt(() -> {
                String text=code.getDocument().getText();long stamp=code.getDocument().getModificationStamp();
                for(var mode:VisualEditorSurface.Mode.values())surface.setMode(mode);
                check(code.getDocument().getText().equals(text)&&code.getDocument().getModificationStamp()==stamp,"View switch changed MAP source");
                button(frame,"保存草稿").doClick();return null;
            });await(() -> ready(frame));
            check(workspace.dirtyPaths().contains(path),"Visual edits were not saved as a MAP draft");
            await(() -> preview.renderedScene()!=null&&preview.renderedScene().emitterCount()>0);
            edt(() -> {screenshot(frame,output.resolve("map-placed-native.png"));return null;});
            Files.writeString(output.resolve("visual-map-result.txt"),"PASS: real resource catalog and native canvas; 1264/1024 layouts; actual mouse XY drag retains Z and undo; add monster/passive/ANI/TIL/recognized particle factory; mode identity; saved MAP draft\n");
        } catch(Exception|AssertionError failure) {
            System.err.println("VISUAL_MAP_FAILURE "+failure);
            edt(() -> {screenshot(frame,output.resolve("visual-map-failure.png"));System.err.println("VISUAL_MAP_LABELS "+components(frame,JLabel.class).stream().map(JLabel::getText).toList());return null;});throw failure;
        } finally {edt(() -> {respond("放弃未保存编辑");frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));return null;});await(() -> !frame.isDisplayable());}
    }
    private static int entryCount(com.intellij.openapi.editor.Editor editor,String path){try{return MapDocument.parse(path,editor.getDocument().getText()).entries().size();}catch(Exception e){return -1;}}
    private static boolean ready(PvfStudioFrame frame){return frame.isEnabled()&&button(frame,"打开 PVF…").isEnabled();}
}
