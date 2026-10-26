package studio.pvf.intellij;

import studio.pvf.preview.*;
import studio.pvf.workspace.PvfWorkspace;
import javax.swing.*;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Actual 100-level explicit MAPs, shape diagnostics and source gate coordinates through the workbench. */
final class Dungeon100WorkbenchSmoke {
    static void run(Path archive, Path output) throws Exception {
        var frame = edt(() -> { var f = new PvfStudioFrame(output.resolve("workspaces"), false); f.setSize(1550, 1150); f.setVisible(true); return f; });
        List<String> report = new ArrayList<>();
        try {
            await(() -> button(frame,"打开最近的文件").isEnabled());
            edt(() -> { frame.openArchive(archive); return null; }); await(() -> ready(frame));
            var previewField=PvfStudioFrame.class.getDeclaredField("preview"); previewField.setAccessible(true);
            var workspaceField=PvfStudioFrame.class.getDeclaredField("workspace"); workspaceField.setAccessible(true);
            var panel=edt(() -> (ResourcePreviewPanel) previewField.get(frame));
            var workspace=edt(() -> (PvfWorkspace) workspaceField.get(frame));
            var controls=edt(() -> components(frame,DungeonPanel.class).getFirst());
            for(String name:List.of("fruz_station","kohlepowerstation","grandine")) {
                String path="dungeon/act9/"+name+".dgn";
                edt(() -> { frame.openEntry(path); return null; });
                await(() -> ready(frame)&&controls.isVisible()&&panel.renderedScene()!=null&&panel.previewDetails().contains(path));
                var dungeon=edt(() -> controls.selection().dungeon());
                for(int index=0;index<dungeon.definition().mazes().size();index++) {
                    var maze=dungeon.definition().mazes().get(index);
                    var layout=dungeon.arrange(index,maze.starts().isEmpty()?null:maze.starts().get(0),maze.bosses().isEmpty()?null:maze.bosses().get(0),0);
                    check(layout.rooms().stream().allMatch(r->r.map()!=null),"Configured room remained unbound: "+name+" #"+index+" "+layout.warnings());
                    report.add(name+" #"+index+" rooms="+layout.rooms().size()+" missing=0 diagnostics="+layout.warnings());
                }
            }
            edt(() -> { components(frame,JComboBox.class).stream().filter(c->"dungeon-maze".equals(c.getName())).findFirst().orElseThrow().setSelectedIndex(4); return null; });
            await(() -> panel.renderedScene()!=null&&controls.selection().layout().maze().width()==6);
            edt(() -> { controls.selectRoom(new DungeonScript.Position(4,1)); return null; });
            await(() -> panel.renderedScene()!=null&&controls.selection().map()!=null&&controls.selection().map().id()==15415);
            edt(() -> {
                var selection=controls.selection();
                check(selection.map().path().equals("map/grandine/15413(2,11)normal.map"),"Explicit ID was replaced by a guessed filename");
                check(selection.layout().warnings().stream().anyMatch(w->w.contains("DGN 明确指定")&&w.contains("15415")),"MAP shape discrepancy was hidden");
                check(components(frame,JToggleButton.class).stream().filter(b->"preview-details".equals(b.getName())).findFirst().orElseThrow().getText().matches(".*\\([1-9][0-9]*\\).*"),"Folded details hid the warning count");
                var gates=selection.dungeon().gates(selection.layout(),selection.room(),selection.map(),true,true);
                check(gates.stream().anyMatch(g->g.direction()==DungeonScript.Direction.LEFT&&g.kind()==DungeonPreview.GateKind.NORMAL&&g.position().equals(new DungeonScript.Position(46,195))),"Left gate coordinate changed");
                check(gates.stream().anyMatch(g->g.direction()==DungeonScript.Direction.RIGHT&&g.kind()==DungeonPreview.GateKind.BOSS&&g.position().equals(new DungeonScript.Position(847,195))),"Boss gate coordinate changed");
                screenshot(frame,output.resolve("hundred-grandine-explicit-room-4-1.png")); return null;
            });
            edt(() -> { controls.selectRoom(new DungeonScript.Position(1,2)); return null; });
            await(() -> panel.renderedScene()!=null&&controls.selection().map()!=null&&controls.selection().map().id()==15416);
            edt(() -> {
                var selection=controls.selection();
                var gates=selection.dungeon().gates(selection.layout(),selection.room(),selection.map(),true,true);
                var up=gates.stream().filter(g->g.direction()==DungeonScript.Direction.UP).findFirst().orElseThrow();
                check(up.open()&&up.position().equals(new DungeonScript.Position(779,107)),"Source UP gate position or topology changed");
                check(gates.stream().noneMatch(g->g.open()&&g.direction()!=DungeonScript.Direction.UP),"Invented connection in one-exit room");
                check(panel.previewDetails().contains("上 (779,107) → 房间 (1,1)"),"Gate coordinates are absent from UI details");
                var actor=panel.renderedScene().entities().stream().filter(e->e.info().spawn().id()==5819).findFirst().orElseThrow();
                check(actor.info().spawn().x()==779&&actor.info().spawn().y()==107&&!actor.info().animations().isEmpty(),"Gate did not render at its source anchor");
                var viewport=components(frame,JCheckBox.class).stream().filter(c->c.getText().equals("客户端视口")).findFirst().orElseThrow();
                if(!viewport.isSelected()) viewport.doClick();
                var canvas=components(frame,ResourcePreviewPanel.Canvas.class).getFirst(); canvas.camera.set(256,18);
                screenshot(frame,output.resolve("hundred-grandine-room-1-2.png"));
                components(frame,JCheckBox.class).stream().filter(c->c.getText().equals("房间已清理")).findFirst().orElseThrow().doClick(); return null;
            });
            await(() -> panel.renderedScene()!=null&&!controls.selection().cleared());
            edt(() -> { screenshot(frame,output.resolve("hundred-grandine-room-closed.png")); return null; });
            // Later basic images have an extra 80-pixel header; extensions join at 480, not 400.
            edt(() -> {
                var rendered=panel.renderedScene();
                var floor=rendered.draws().stream().filter(d->d.stage()==SceneScript.FLOOR_STAGE).toList();
                check(floor.getFirst().y()==-80,"New basic tile img pos was ignored");
                check(floor.stream().anyMatch(d->d.y()==480),"Extended floor strip was displaced with the basic image");
                return null;
            });
            edt(() -> { frame.openEntry("dungeon/act8/pirateonthetrain.dgn"); return null; });
            await(() -> ready(frame)&&panel.renderedScene()!=null&&panel.previewDetails().contains("dungeon/act8/pirateonthetrain.dgn"));
            edt(() -> { controls.selectRoom(new DungeonScript.Position(1,0)); return null; });
            await(() -> panel.renderedScene()!=null&&controls.selection().map().path().equals("map/pirateonthetrain/(1,0)normal.map"));
            edt(() -> {
                var rendered=panel.renderedScene();
                check(rendered.draws().stream().anyMatch(d->d.repeat()&&d.repeatPeriod()==556),"Sea-train sky did not use its explicit scroll period");
                check(rendered.draws().stream().filter(d->d.stage()==SceneScript.FLOOR_STAGE).allMatch(d->d.y()==-80),"Sea-train basic image origin is wrong");
                var viewport=components(frame,JCheckBox.class).stream().filter(c->c.getText().equals("客户端视口")).findFirst().orElseThrow();
                if(viewport.isSelected()) viewport.doClick();
                screenshot(frame,output.resolve("hundred-sea-train-overview.png"));
                viewport.doClick(); var canvas=components(frame,ResourcePreviewPanel.Canvas.class).getFirst();
                canvas.camera.set(rendered.cameraLimits().maxX(),0);
                screenshot(frame,output.resolve("hundred-sea-train-right-viewport.png")); return null;
            });
            edt(() -> { frame.openEntry("dungeon/act9/kohlepowerstation.dgn"); return null; });
            await(() -> ready(frame)&&panel.renderedScene()!=null&&panel.previewDetails().contains("dungeon/act9/kohlepowerstation.dgn"));
            edt(() -> { controls.selectRoom(new DungeonScript.Position(4,1)); return null; });
            await(() -> panel.renderedScene()!=null&&controls.selection().map().path().equals("map/kohlepowerstation/18008(4,1)b.map"));
            edt(() -> {
                var draws=panel.renderedScene().draws(); int lastRail=-1,stair=-1;
                for(int i=0;i<draws.size();i++) {
                    var sprite=draws.get(i).prepared().source();
                    if(draws.get(i).stage()==18&&sprite.img().endsWith("/rail.img")) lastRail=i;
                    if(sprite.img().endsWith("/down_stair01.img")&&sprite.requestedFrame()==0) stair=i;
                }
                check(lastRail>=0&&stair>lastRail,"Close-layer rail overpainted the later stair landing"); return null;
            });
            for(var size:List.of(new java.awt.Dimension(1264,811),new java.awt.Dimension(1024,720))) {
                edt(() -> {
                    components(frame,JSplitPane.class).forEach(p->p.setResizeWeight(0));
                    frame.setSize(size); frame.validate();
                    var panes=components(frame,JSplitPane.class); panes.getFirst().setDividerLocation(280);
                    var editorPane=panes.stream().filter(p->p.getRightComponent()==panel).findFirst().orElseThrow();
                    editorPane.setDividerLocation(Math.min(540,editorPane.getMaximumDividerLocation()));
                    frame.validate(); return null;
                });
                await(() -> components(frame,ResourcePreviewPanel.Canvas.class).getFirst().getHeight()>=260);
                edt(() -> {
                    var canvas=components(frame,ResourcePreviewPanel.Canvas.class).getFirst();
                    var options=components(frame,JToggleButton.class).stream().filter(b->"preview-options".equals(b.getName())).findFirst().orElseThrow();
                    var details=components(frame,JToggleButton.class).stream().filter(b->"preview-details".equals(b.getName())).findFirst().orElseThrow();
                    check(!options.isSelected()&&!details.isSelected(),"Compact preview should start with optional controls folded");
                    check(components(frame,JComponent.class).stream().filter(c->"dungeon-rooms".equals(c.getName())).findFirst().orElseThrow().isShowing(),"Room grid disappeared in compact mode");
                    int height=canvas.getHeight(); var camera=canvas.camera.position();
                    check(canvas.getWidth()>=(size.width==1264?370:280)&&canvas.getWidth()<=(size.width==1264?440:330),"Compact check did not use the reported narrow preview pane");
                    options.doClick(); frame.validate();
                    check(components(frame,JComboBox.class).stream().filter(c->"dungeon-maze".equals(c.getName())).findFirst().orElseThrow().isShowing(),"Layout settings cannot be expanded");
                    check(canvas.getHeight()<height,"Expanding options did not expose controls");
                    options.doClick(); details.doClick(); frame.validate();
                    check(components(frame,JTextArea.class).stream().anyMatch(c->c.isShowing()&&c.getText().contains("通路门坐标")),"Room diagnostics cannot be expanded");
                    details.doClick(); frame.validate();
                    check(canvas.getHeight()==height&&canvas.camera.position().equals(camera),"Folding settings changed the camera or failed to restore canvas space");
                    report.add("compact "+size.width+"x"+size.height+" canvas="+canvas.getWidth()+"x"+canvas.getHeight());
                    screenshot(frame,output.resolve("hundred-kohle-compact-"+size.width+".png")); return null;
                });
            }
            check(workspace.dirtyPaths().isEmpty(),"Dungeon inspection modified drafts");
            Files.write(output.resolve("hundred-room-audit.txt"),report);
            Files.writeString(output.resolve("dungeon-100-result.txt"),"PASS: 100-level three DGNs/all eight layouts have explicit MAPs; mismatched greed diagnosed; original UP(779,107)/LEFT(46,195)/RIGHT(847,195) anchors; ordinary/Boss connections and close state; basic img pos 80 with unchanged extension join; sea-train repeating sky 556 in overview/right viewport; close-layer rails before stairs; compact narrow preview at 1264x811/1024x720, options/details/diagnostics and camera preservation; no draft changes\n");
        } finally {
            edt(() -> { respond("放弃未保存编辑"); frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING)); return null; });
            await(() -> !frame.isDisplayable());
        }
    }
    private static boolean ready(PvfStudioFrame frame) { return frame.isEnabled()&&button(frame,"打开 PVF…").isEnabled(); }
}
