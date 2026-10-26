package studio.pvf.intellij;

import studio.pvf.preview.*;
import studio.pvf.sceneedit.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/** Native draw ownership survives duplicate positions, multiple parts and invisible frames. */
final class SceneHitIndex {
    private final MapDocument model;
    private final ScenePreview.Rendered rendered;
    private final Map<EntryId,MapEntry> entries=new LinkedHashMap<>();
    private final Map<EntryId,Double> scrolls=new HashMap<>();
    SceneHitIndex(MapDocument model,ScenePreview.Rendered rendered) {
        this.model=model;this.rendered=rendered;model.entries().forEach(e -> entries.put(e.id(),e));
        if(rendered!=null)for(var draw:rendered.draws())if(draw.source()!=null)scrolls.putIfAbsent(id(draw.source()),draw.scroll());
    }
    static EntryId id(SceneSource source) { return source==null?null:new EntryId(source.section(),source.sectionOccurrence(),source.recordOrdinal()); }
    double scroll(EntryId id) {
        return scrolls.getOrDefault(id,1.);
    }
    List<EntryId> hits(SceneCanvas canvas,Point2D point) {
        LinkedHashSet<EntryId> result=new LinkedHashSet<>();
        if(rendered!=null)for(int i=rendered.draws().size()-1;i>=0;i--) {
            var draw=rendered.draws().get(i); EntryId id=id(draw.source()); if(id==null||!entries.containsKey(id)||draw.opacity()<=0)continue;
            Point2D world=canvas.viewToWorld(point,draw.scroll()); double origin=draw.x();
            if(draw.repeat()) {
                var bounds=draw.prepared().bounds(draw.mirrored()); double period=draw.repeatPeriod()>0?draw.repeatPeriod():Math.max(1,bounds.getWidth());
                origin+=Math.floor((world.getX()-origin-bounds.getX())/period)*period;
            }
            try {
                var transform=draw.mirrored()?draw.prepared().mirroredTransform():draw.prepared().transform();
                var pixel=transform.inverseTransform(new Point2D.Double(world.getX()-origin,world.getY()-draw.y()),null);
                int x=(int)Math.floor(pixel.getX()),y=(int)Math.floor(pixel.getY()); var image=draw.prepared().image();
                if(x>=0&&y>=0&&x<image.getWidth()&&y<image.getHeight()&&(image.getRGB(x,y)>>>24)>0)result.add(id);
            } catch(NoninvertibleTransformException ignored) { /* Zero-scaled frames remain selectable at their anchor. */ }
        }
        var all=model.entries();
        for(int i=all.size()-1;i>=0;i--) {
            var entry=all.get(i); if(entry.type()==MapEntry.Type.BACKGROUND)continue;
            var anchor=canvas.worldToView(entry.x(),SceneCoordinates.screenY(entry.y(),entry.z()),scroll(entry.id()));
            if(anchor.distance(point)<=8)result.add(entry.id());
        }
        return List.copyOf(result);
    }
}
