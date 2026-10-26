package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import studio.pvf.preview.*;
import studio.pvf.sceneedit.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SceneHitIndexTest {
    @Test void parsedChampionOwnershipHitsAndDragsTheFollowingMonsterOnly()throws Exception{
        String text="[monster] 51 1 0 100 120 0 1 1 `[fixed]` `[champion]` 1 7 52 1 0 200 120 0 1 1 `[fixed]` `[normal]` 53 1 0 300 120 0 1 1 `[fixed]` `[normal]` [/monster]";
        var model=MapDocument.parse("map/a.map",text);var pixels=new BufferedImage(40,10,BufferedImage.TYPE_INT_ARGB);var g=pixels.createGraphics();g.setColor(Color.RED);g.fillRect(0,0,40,10);g.dispose();var prepared=PreviewRenderer.prepare(ResourceScript.Frame.still("a.img",0),new NpkResources.Sprite(pixels,0,0,40,10,Path.of("fake"),"a.img",0,0));
        var draws=EntityScript.placements(text).spawns().stream().map(spawn -> new ScenePreview.Draw(prepared,spawn.x(),SceneCoordinates.screenY(spawn.y(),spawn.z()),1,false,1,1,false,1,15,120,spawn.id(),0,spawn.source())).toList();
        var rendered=new ScenePreview.Rendered(draws,List.of(),List.of(),new Rectangle2D.Double(0,0,448,480),0,Map.of(),List.of(),1,3,List.of(),0,List.of(),0,0,null,false);
        var canvas=new SceneCanvas();canvas.setSize(900,650);canvas.scene=rendered;canvas.world=rendered.bounds();canvas.freeZoom=1;canvas.sceneSourcePath="map/a.map";canvas.sceneSourceText=text;var edits=new java.util.ArrayList<String>();
        var controller=new MapCanvasController(canvas,() -> 1L,id -> {},(stamp,id,x,y,z) -> edits.add(model.move(id,x,y,z).applyTo(text)));controller.install(model,1,true);controller.press(canvas.worldToView(230,242,1),false);assertEquals(model.entries().get(1).id(),controller.selected());controller.release(canvas.worldToView(240,242,1));var after=MapDocument.parse("map/a.map",edits.getFirst());assertEquals(210,after.entries().get(1).x());assertEquals(300,after.entries().get(2).x());
    }
    @Test void hitsOpaqueSpritePixelsAndDeduplicatesMultipartOwnersWhileLogicAnchorsRemainSelectable() throws Exception {
        var model=MapDocument.parse("map/a.map","[tile] `a.til` [/tile] [animation] `a.ani` `[normal]` 100 120 0 `a.ani` `[normal]` 100 120 0 [/animation] [passive object] 1 300 120 0 [/passive object]");
        var image=new BufferedImage(40,10,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(Color.RED);g.fillRect(20,0,20,10);g.dispose();
        var sprite=new NpkResources.Sprite(image,0,0,40,10,Path.of("fake"),"a.img",0,0);
        var prepared=PreviewRenderer.prepare(ResourceScript.Frame.still("a.img",0),sprite);
        var a=new SceneSource("animation",0,0);var b=new SceneSource("animation",0,1);
        var draws=List.of(new ScenePreview.Draw(prepared,100,240,1,false,1,1,false,1,15,120,0,0,a),new ScenePreview.Draw(prepared,100,240,1,false,1,1,false,1,15,120,1,0,b),new ScenePreview.Draw(prepared,170,240,1,false,1,1,false,1,15,120,2,0,b));
        var rendered=new ScenePreview.Rendered(draws,List.of(),List.of(),new Rectangle2D.Double(0,0,448,480),0,Map.of(),List.of(),1,3,List.of(),0,List.of(),0,0,null,false);
        var canvas=new SceneCanvas();canvas.setSize(900,650);canvas.scene=rendered;canvas.world=rendered.bounds();canvas.freeZoom=1;
        var index=new SceneHitIndex(model,rendered);
        assertEquals(List.of(model.entries().get(1).id(),model.entries().get(0).id()),index.hits(canvas,canvas.worldToView(130,242,1)));
        assertTrue(index.hits(canvas,canvas.worldToView(110,242,1)).isEmpty(),"Transparent bounding-box pixels must not steal selection");
        assertEquals(List.of(model.entries().get(1).id()),index.hits(canvas,canvas.worldToView(200,242,1)));
        assertEquals(List.of(model.entries().get(2).id()),index.hits(canvas,canvas.worldToView(300,240,1)));
    }
}
