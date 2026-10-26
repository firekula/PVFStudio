package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DungeonMinimapTest {
    static class Lookup implements ScenePreview.SpriteLookup {
        final List<Integer> loaded=new ArrayList<>();int bad=-1;
        public NpkResources.Info info(String path,int frame)throws IOException{if(frame==bad)throw new IOException("missing frame");return new NpkResources.Info(18,18,0,0,18,18);}
        public NpkResources.Sprite sprite(String path,int frame)throws IOException{
            if(!path.equals("map/minimap/act1.img"))throw new IOException("optional icon absent");loaded.add(frame);var image=new BufferedImage(18,18,2);image.setRGB(0,0,0xff000000|frame);return new NpkResources.Sprite(image,0,0,18,18,Path.of("test.npk"),path,frame,frame);
        }
    }
    @Test void verifiedMaskOrderLoadsOnlyLitTilesAndNeverInventsZeroMask()throws Exception{
        var lookup=new Lookup();var atlas=DungeonMinimap.load("map/minimap/act1.img",lookup);
        assertNull(atlas.tile(0));assertNull(atlas.tile(16));
        for(int mask=1;mask<=15;mask++)assertEquals(0xff000000|((mask-1)*8+4),atlas.tile(mask).getRGB(0,0));
        assertEquals(15,lookup.loaded.size());assertNull(atlas.boss());
    }
    @Test void incompleteOrDifferentLayoutsMustFallBackInsteadOfPaintingWrongTiles(){
        var missing=new Lookup();missing.bad=119;assertThrows(IOException.class,() -> DungeonMinimap.load("map/minimap/act1.img",missing));
        var different=new Lookup(){@Override public NpkResources.Info info(String path,int frame){return new NpkResources.Info(32,32,0,0,32,32);}};
        assertThrows(IOException.class,() -> DungeonMinimap.load("map/minimap/act1.img",different));
    }
}
