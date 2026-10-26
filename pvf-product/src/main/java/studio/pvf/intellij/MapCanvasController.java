package studio.pvf.intellij;

import studio.pvf.preview.SceneCoordinates;
import studio.pvf.sceneedit.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.*;

/** A drag is one guarded source command at release. Cancel and stale source produce no write. */
final class MapCanvasController implements SceneCanvas.Interaction {
    @FunctionalInterface interface Move { void move(long version,EntryId id,double x,double y,double z) throws Exception; }
    private final SceneCanvas canvas;
    private final LongSupplier currentVersion;
    private final Consumer<EntryId> selection;
    private final Move move;
    private MapDocument model;
    private long version;
    private boolean writable;
    private EntryId selected;
    private Point2D initial,lastPoint,lastClick;
    private List<EntryId> lastHits=List.of();
    private int cycle;
    private double scroll=1;
    private String error="";
    private Consumer<Point2D> placement;
    MapCanvasController(SceneCanvas canvas,LongSupplier currentVersion,Consumer<EntryId> selection,Move move) {
        this.canvas=canvas; this.currentVersion=currentVersion; this.selection=selection; this.move=move;
    }
    void install(MapDocument model,long version,boolean writable) {
        if(this.version!=version||!writable)cancel(); this.model=model; this.version=version; this.writable=writable;
        if(selected!=null&&model.entries().stream().noneMatch(e -> e.id().equals(selected)))selected=null;
        canvas.repaint();
    }
    EntryId selected() { return selected; }
    void select(EntryId id) { selected=id; selection.accept(id); canvas.repaint(); }
    String error() { return error; }
    void placeAt(Consumer<Point2D> action) { cancel(); placement=action; canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); canvas.requestFocusInWindow(); canvas.repaint(); }
    public void press(Point2D point,boolean next) {
        if(model==null||!canvas.matchesSource(model)){cancel();return;}
        if(placement!=null) { var action=placement; placement=null; canvas.setCursor(Cursor.getDefaultCursor()); action.accept(canvas.viewToWorld(point,1)); return; }
        if(model==null)return; var index=new SceneHitIndex(model,canvas.scene); var hits=index.hits(canvas,point);
        cycle=lastClick!=null&&lastClick.distance(point)<4&&lastHits.equals(hits)&&!hits.isEmpty()?(cycle+1)%hits.size():0;
        lastClick=point; lastHits=hits;
        select(hits.isEmpty()?null:hits.get(cycle)); initial=null;
        if(selected!=null&&writable) { initial=indexPoint(point,index.scroll(selected)); lastPoint=initial; scroll=index.scroll(selected); }
    }
    private Point2D indexPoint(Point2D p,double scroll) { return canvas.viewToWorld(p,scroll); }
    public void drag(Point2D point) { if(initial!=null) { lastPoint=indexPoint(point,scroll); canvas.repaint(); } }
    public void release(Point2D point) {
        if(initial==null)return; drag(point);
        if(writable&&version==currentVersion.getAsLong()&&selected!=null&&canvas.matchesSource(model)) {
            var entry=model.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null);
            double dx=Math.rint(lastPoint.getX()-initial.getX()),dy=Math.rint(lastPoint.getY()-initial.getY());
            if(entry!=null&&entry.type()!=MapEntry.Type.BACKGROUND&&(dx!=0||dy!=0)) {
                try { move.move(version,selected,entry.x()+dx,entry.y()+dy,entry.z()); error=""; }
                catch(Exception failure){error=failure.getMessage();}
            }
        }
        cancel();
    }
    public void cancel() { initial=null; lastPoint=null; placement=null; canvas.setCursor(Cursor.getDefaultCursor()); canvas.repaint(); }
    public void paint(Graphics2D g) {
        if(model==null)return;
        if(placement!=null){g.setColor(Color.YELLOW);g.drawString("点击放置 · Esc 取消",16,48);}
        var index=new SceneHitIndex(model,canvas.scene);
        for(var entry:model.entries()) {
            if(entry.type()==MapEntry.Type.BACKGROUND)continue;
            Point2D point=canvas.worldToView(entry.x(),SceneCoordinates.screenY(entry.y(),entry.z()),index.scroll(entry.id()));
            g.setColor(entry.id().equals(selected)?new Color(255,210,80):new Color(125,205,230,140));
            int x=(int)Math.round(point.getX()),y=(int)Math.round(point.getY()); g.drawLine(x-5,y,x+5,y); g.drawLine(x,y-5,x,y+5);
            if(entry.id().equals(selected)) {
                g.drawOval(x-9,y-9,18,18);
                if(initial!=null&&lastPoint!=null) {
                    var ghost=canvas.worldToView(entry.x()+lastPoint.getX()-initial.getX(),SceneCoordinates.screenY(entry.y()+lastPoint.getY()-initial.getY(),entry.z()),scroll);
                    int gx=(int)ghost.getX(),gy=(int)ghost.getY(); g.drawLine(x,y,gx,gy); g.drawRect(gx-10,gy-10,20,20);
                }
            }
        }
    }
}
