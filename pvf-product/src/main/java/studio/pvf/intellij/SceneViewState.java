package studio.pvf.intellij;

import java.util.*;
import java.awt.Point;

/** Optional metadata only; malformed future values cannot prevent source recovery. */
record SceneViewState(double panX,double panY,double zoom,int zoomIndex,Point camera) {
    static SceneViewState capture(SceneCanvas c) {return new SceneViewState(c.panX,c.panY,c.freeZoom,c.zoom,c.camera==null?null:c.camera.position());}
    void apply(SceneCanvas c) {c.panX=panX;c.panY=panY;c.freeZoom=zoom;c.zoom=zoomIndex;if(c.camera!=null&&camera!=null)c.camera.set(camera.x,camera.y);}
    void save(Map<String,String> m) {m.put("panX",Double.toString(panX));m.put("panY",Double.toString(panY));m.put("zoom",Double.toString(zoom));m.put("zoomIndex",Integer.toString(zoomIndex));if(camera!=null){m.put("cameraX",Integer.toString(camera.x));m.put("cameraY",Integer.toString(camera.y));}}
    static SceneViewState read(Map<String,String> m) {
        try {double x=Double.parseDouble(m.get("panX")),y=Double.parseDouble(m.get("panY")),z=Double.parseDouble(m.get("zoom"));int i=Integer.parseInt(m.get("zoomIndex"));
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||z<0||z>8||i<0||i>5)return null;
            Point camera=m.containsKey("cameraX")?new Point(Integer.parseInt(m.get("cameraX")),Integer.parseInt(m.get("cameraY"))):null;return new SceneViewState(x,y,z,i,camera);
        }catch(RuntimeException invalid){return null;}
    }
}
