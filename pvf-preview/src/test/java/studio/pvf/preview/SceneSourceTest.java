package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SceneSourceTest {
    @Test void duplicateMultipartAndConditionalDrawsRetainTheirOwnSourceRecord() throws Exception {
        String map="[tile] `a.til` [/tile] [animation] `a.ani` `[normal]` 20 30 0 `a.ani` `[normal]` 20 30 0 [/animation]"
                +"[passive object] 1 100 120 0 1 100 120 0 [/passive object] [monster condition] 2 1 0 100 120 0 1 1 `[fixed]` `[normal]` [/monster condition]";
        var scene=ScenePreview.load("map/a.map",map,p -> switch(p) {
            case "map/a.til" -> "[pass type] "+"0 ".repeat(420);
            case "map/a.ani", "passiveobject/a.ani", "monster/a.ani" -> "[FRAME MAX] 1 [FRAME000] [IMAGE] `a.img` 0 [DELAY] 100";
            case "passiveobject/passiveobject.lst" -> "1 `a.obj`";
            case "passiveobject/a.obj" -> "[basic motion] `a.ani` [add object effect] 1 `a.ani` 1 [/add object effect]";
            case "monster/monster.lst" -> "2 `a.mon`";
            case "monster/a.mon" -> "[waiting motion] `a.ani`";
            default -> null;
        },new ScenePreview.SpriteLookup() {
            public NpkResources.Info info(String p,int i){return new NpkResources.Info(10,10,0,0,10,10);}
            public NpkResources.Sprite sprite(String p,int i){var image=new BufferedImage(10,10,BufferedImage.TYPE_INT_ARGB); var g=image.createGraphics(); g.setColor(Color.RED); g.fillRect(0,0,10,10); g.dispose(); return new NpkResources.Sprite(image,0,0,10,10,Path.of("fake"),p,i,i);}
        });
        var ordinary=scene.render(0,false,false);
        assertEquals(Set.of(new SceneSource("animation",0,0),new SceneSource("animation",0,1),new SceneSource("passive object",0,0),new SceneSource("passive object",0,1)),
                new HashSet<>(ordinary.draws().stream().map(ScenePreview.Draw::source).filter(Objects::nonNull).toList()));
        assertTrue(scene.render(0,false,true).draws().stream().anyMatch(d -> new SceneSource("monster condition",0,0).equals(d.source())));
        assertEquals(2,ordinary.draws().stream().filter(d -> new SceneSource("passive object",0,1).equals(d.source())).count());
    }
    @Test void nestedBackgroundsAndRepeatedSectionsRetainTheOriginalSectionAndRecordOrdinals() throws Exception {
        var map=SceneScript.map("[tile] `a.til` [/tile] [background animation] `a.ani` `[normal]` `[below]` "
                +"[ani info] [filename] `b.ani` [layer] `[normal]` [order] `[upper]` [/ani info] [/background animation] "
                +"[background animation] `c.ani` `[close]` `[below]` [/background animation]");
        assertEquals(List.of(new SceneSource("background animation",0,0),new SceneSource("background animation",0,1),new SceneSource("background animation",1,0)),map.animations().stream().map(SceneScript.Placement::source).toList());
        var entities=EntityScript.placements("[special passive object] 5 100 120 0 1 `[hellparty]` 117 1 1 `[/hellparty]` 6 200 120 0 0 [/special passive object]");
        assertEquals(List.of(new SceneSource("special passive object",0,0),new SceneSource("special passive object",0,1)),entities.spawns().stream().map(EntityScript.Spawn::source).toList());
    }
}
