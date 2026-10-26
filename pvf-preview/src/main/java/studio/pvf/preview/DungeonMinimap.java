package studio.pvf.preview;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/** The verified ACT1 minimap layout: R/U/L/D mask order, eight variants per shape. */
public final class DungeonMinimap {
    private final List<BufferedImage> tiles;
    private final BufferedImage boss;
    private DungeonMinimap(List<BufferedImage> tiles,BufferedImage boss){this.tiles=List.copyOf(tiles);this.boss=boss;}
    public static String image(String text){return ResourceScript.sections(text).stream().filter(s -> s.tag().equals("minimap image")&&!s.values().isEmpty()).reduce((a,b) -> b).map(s -> s.values().get(0)).orElse("");}
    public static DungeonMinimap load(String path,ScenePreview.SpriteLookup lookup)throws IOException{
        // Check the full shape table before displaying any native tile from a different format.
        for(int frame=0;frame<120;frame++){
            var info=lookup.info(path,frame);
            if(info.width()!=18||info.height()!=18||info.x()!=0||info.y()!=0||info.canvasWidth()!=18||info.canvasHeight()!=18)
                throw new IOException("小地图图块格式不匹配（需 18×18 的 15 组方向图块）："+path);
        }
        var tiles=new java.util.ArrayList<BufferedImage>();
        for(int mask=1;mask<=15;mask++)tiles.add(lookup.sprite(path,(mask-1)*8+4).image());
        BufferedImage boss=null;
        try{var icon=lookup.sprite("Interface/MiniMap.img",37);if(icon.image().getWidth()==16&&icon.image().getHeight()==14)boss=icon.image();}catch(IOException missing){/* Text role badges remain available. */}
        return new DungeonMinimap(tiles,boss);
    }
    public BufferedImage tile(int mask){return mask<1||mask>15?null:tiles.get(mask-1);}
    public BufferedImage boss(){return boss;}
}
