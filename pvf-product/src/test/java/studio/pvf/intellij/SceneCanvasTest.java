package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import studio.pvf.preview.*;
import studio.pvf.sceneedit.*;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneCanvasTest {
    static final String MAP="[tile] `a.til` `a.til` `a.til` `a.til` [/tile]\n[passive object] 1 100 120 5 1 100 120 5 [/passive object]";
    @Test void projectionIsInvertibleAcrossOverviewPanParallaxAndClientCamera() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            SceneCanvas canvas=canvas(); canvas.panX=65; canvas.panY=-17; canvas.freeZoom=1.3;
            for(boolean viewport:List.of(false,true)) {
                canvas.clientViewport=viewport; canvas.camera.set(100,20);
                for(double scroll:List.of(0.,.4,1.,1.7)) {
                    var point=canvas.worldToView(300,235,scroll); var world=canvas.viewToWorld(point,scroll);
                    assertEquals(300,world.getX(),1e-8); assertEquals(235,world.getY(),1e-8);
                }
            }
        } catch(Exception e){throw new RuntimeException(e);} });
    }
    @Test void duplicateAnchorCycleDragPreservesZAndEscapeOrStaleVersionNeverWrites() throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            SceneCanvas canvas=canvas(); var model=MapDocument.parse("map/a.map",MAP); long[] version={7}; List<EntryId> selected=new ArrayList<>(); List<String> result=new ArrayList<>();
            var control=new MapCanvasController(canvas,() -> version[0],selected::add,(stamp,id,x,y,z) -> result.add(model.move(id,x,y,z).applyTo(MAP)));
            control.install(model,7,true); var point=canvas.worldToView(100,235,1);
            control.press(point,false); assertEquals(model.entries().getLast().id(),selected.getLast()); control.cancel();
            control.press(point,true); assertEquals(model.entries().getFirst().id(),selected.getLast());
            var end=canvas.worldToView(110,255,1); control.drag(end); control.release(end);
            var moved=MapDocument.parse("map/a.map",result.getFirst()).entries().getFirst();
            assertEquals(110,moved.x()); assertEquals(140,moved.y()); assertEquals(5,moved.z());
            control.press(point,false); control.drag(end); control.cancel(); assertEquals(1,result.size());
            control.press(point,false); version[0]++; control.release(end); assertEquals(1,result.size());
        } catch(Exception e){throw new RuntimeException(e);} });
    }
    static SceneCanvas canvas() throws Exception {
        var rendered=ScenePreview.load("map/a.map",MAP,path -> path.endsWith(".til")?"[pass type] "+"0 ".repeat(420):null,new ScenePreview.SpriteLookup() {
            public NpkResources.Info info(String p,int i){throw new AssertionError();}
            public NpkResources.Sprite sprite(String p,int i){throw new AssertionError();}
        }).render(0,false);
        var canvas=new SceneCanvas(); canvas.scene=rendered; canvas.world=rendered.bounds(); canvas.camera=new SceneCamera(rendered.cameraLimits()); canvas.setSize(900,650); return canvas;
    }
}
