import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.preview.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Real TIL origins and scrolling scenery at loop boundaries; read-only, independent workspace. */
public class MapAlignmentAudit {
    public static void main(String[] args) throws Exception {
        if(args.length!=4) throw new IllegalArgumentException("MapAlignmentAudit <PVF> <NPK directory> <new output directory> <map prefix>");
        Path out=Path.of(args[2]); Files.createDirectory(out); var resources=NpkResources.scan(Path.of(args[1]));
        int maps=0, tiles=0, repeatFrames=0; List<String> report=new ArrayList<>();
        try(var workspace=PvfWorkspace.open(Path.of(args[0]),out.resolve("workspace"))) {
            var paths=workspace.paths(); Map<String,String> cache=new HashMap<>();
            ScenePreview.TextLookup lookup=path->{
                if(!paths.contains(path)) return null;
                if(!cache.containsKey(path)) cache.put(path,workspace.readText(path,Charset.forName("Big5")));
                return cache.get(path);
            };
            for(var path:paths.stream().filter(p->p.startsWith(args[3])&&p.endsWith(".map")).sorted().toList()) {
                var layout=SceneScript.map(lookup.read(path)); var scene=ScenePreview.load(path,lookup.read(path),lookup,resources);
                int offsetTiles=0; for(var ref:layout.tiles()) {
                    var tp=ResourceScript.animationCandidates(path,ref).stream().filter(paths::contains).findFirst().orElseThrow();
                    var tile=SceneScript.tile(lookup.read(tp)); if(tile.imageOffset()!=0) offsetTiles++; tiles++;
                }
                boolean sea=EntityScript.placements(lookup.read(path)).spawns().stream().anyMatch(s->s.kind()==EntityScript.Kind.PASSIVE&&s.id()==723);
                for(long time:new long[]{0,4000,7999,8000,60061}) {
                    var frame=scene.render(time,false);
                    if(sea) {
                        if(frame.draws().stream().noneMatch(d->d.repeat()&&d.repeatPeriod()==556)) throw new AssertionError(path+" missing repeating sea");
                        int width=(int)frame.bounds().getWidth(); var image=new BufferedImage(width,480,BufferedImage.TYPE_INT_ARGB); var g=image.createGraphics();
                        frame.paint(g,new Rectangle(0,0,width,480),0,0,1,0,0,false);g.dispose();
                        for(int x=0;x<width;x++) if(image.getRGB(x,0)>>>24!=255) throw new AssertionError(path+" uncovered sky x="+x+" t="+time);
                        for(int camera:new int[]{0,frame.cameraLimits().maxX()}) {
                            var viewport=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);g=viewport.createGraphics();
                            frame.paintClient(g,new Rectangle(0,0,640,480),1,new Point(camera,0),false,false);g.dispose();
                            for(int x=0;x<640;x++) if((viewport.getRGB(x,0)&0xffffff)==0) throw new AssertionError(path+" black sky in viewport x="+x+" t="+time);
                        }
                        if(path.endsWith("/(10,0)normal.map")&&(time==0||time==4000)) ImageIO.write(image,"PNG",out.resolve("sea-train-"+time+".png").toFile());
                        repeatFrames++;
                    }
                    if(!frame.warnings().isEmpty()) report.add(path+" t="+time+" diagnostics="+frame.warnings());
                }
                report.add(path+" basic="+layout.tiles().size()+" offsetBasic="+offsetTiles+" extended="+layout.extended().size()+" sea="+sea);maps++;
            }
            if(!workspace.dirtyPaths().isEmpty())throw new AssertionError("Drafts changed"); workspace.archive().verifyUnchanged();
        }
        report.add("PASS maps="+maps+" basicTiles="+tiles+" seaFrameChecks="+repeatFrames);
        Files.write(out.resolve("alignment-audit.txt"),report);System.out.println(report.get(report.size()-1));
    }
}
