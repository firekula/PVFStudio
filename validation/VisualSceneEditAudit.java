import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.sceneedit.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.*;

/** Real native scene changes, persistent drafts and exported archive. Never writes the input. */
public class VisualSceneEditAudit {
 static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
 static String hash(Path p)throws Exception{var d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){byte[] b=new byte[1048576];int n;while((n=in.read(b))>=0)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException("VisualSceneEditAudit <PVF> <new output directory> <charset>");
  Path input=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectory(out);Charset charset=Charset.forName(args[2]);String sha=hash(input);
  var edits=new LinkedHashMap<String,String>();var notes=new ArrayList<String>();boolean hellEdited=false;
  try(var w=PvfWorkspace.open(input,out.resolve("drafts"))){
   for(String path:w.paths().stream().filter(p -> p.startsWith("map/")&&p.endsWith(".map")).sorted().toList())try{
    String text=w.readText(path,charset);var map=MapDocument.parse(path,text);ScriptEdit change=null;
    var entry=map.entries().stream().filter(e -> e.fields().containsKey("x")).findFirst();
    if(entry.isPresent()){var e=entry.get();change=map.move(e.id(),e.x()+1,e.y(),e.z());}
    else if(!map.tiles(false).isEmpty()){
     var basic=new ArrayList<>(map.tiles(false).stream().map(MapDocument.TileEntry::reference).toList());int width=basic.size();var ext=map.tiles(true);if(ext.size()%width!=0)continue;
     var expanded=new ArrayList<String>();for(int row=0;row<ext.size()/width;row++){for(int col=0;col<width;col++)expanded.add(ext.get(row*width+col).reference());expanded.add(ext.get(row*width+width-1).reference());}basic.add(basic.get(0));change=map.replaceTileGrid(basic,expanded);
    }
    if(change==null)continue;String after=change.applyTo(text);if(after.equals(text))continue;w.stageText(path,after,charset);edits.put(path,after);break;
   }catch(Exception failure){notes.add("MAP "+path+": "+failure.getMessage());}
   for(String path:w.paths().stream().filter(p -> p.startsWith("dungeon/")&&p.endsWith(".dgn")).sorted().toList())try{
    String text=w.readText(path,charset);var d=DungeonDocument.parse(path,text);if(d.source().sections("maze info").isEmpty())continue;
    var m=d.mazes().get(0);var candidates=new ArrayList<>(m.layout().bosses());var newBoss=m.rooms().stream().filter(p -> !candidates.contains(p)).sorted(Comparator.comparingInt(studio.pvf.preview.DungeonScript.Position::y).thenComparingInt(studio.pvf.preview.DungeonScript.Position::x)).findFirst();if(newBoss.isEmpty())continue;
    candidates.add(newBoss.get());String after=d.setBosses(0,candidates).applyTo(text);w.stageText(path,after,charset);edits.put(path,after);break;
   }catch(Exception failure){notes.add("DGN "+path+": "+failure.getMessage());}
   for(String path:w.paths().stream().filter(p -> p.endsWith(".dgn")).sorted().toList())try{
    String text=w.readText(path,charset);var d=DungeonDocument.parse(path,text);if(d.source().sections("maze info").isEmpty())continue;
    for(int i=0;i<d.mazes().size();i++){var h=d.hell(i);if(!h.enabled()||h.excluded()||h.position()==null||h.mapId()<=0||h.hitCounts().isEmpty())continue;
     var hits=new ArrayList<>(h.hitCounts());hits.set(0,hits.get(0)+1);String after=d.setHell(i,new DungeonDocument.HellSettings(h.enabled(),h.excluded(),h.mapId(),h.position(),hits,h.appearRate(),h.coinLimit())).applyTo(text);w.stageText(path,after,charset);edits.put(path,after);hellEdited=true;break;
    }if(hellEdited)break;
   }catch(Exception failure){notes.add("HELL "+path+": "+failure.getMessage());}
   if(edits.keySet().stream().noneMatch(p -> p.endsWith(".map")))throw new AssertionError("No editable native MAP found");
   notes.add("hellEdited="+hellEdited);notes.add("dungeonEdited="+edits.keySet().stream().anyMatch(p -> p.endsWith(".dgn")));
  }
  var changes=new ArrayList<String>();
  try(var w=PvfWorkspace.open(input,out.resolve("drafts"))){
   for(var edit:edits.entrySet()){
    String actual=w.readText(edit.getKey(),charset);
    if(!SourceDocument.parse(actual).tokens().stream().map(SourceDocument.Token::raw).toList().equals(SourceDocument.parse(edit.getValue()).tokens().stream().map(SourceDocument.Token::raw).toList()))throw new AssertionError("Reopen changed source tokens: "+edit.getKey());
    if(edit.getKey().endsWith(".map"))MapDocument.parse(edit.getKey(),actual);else DungeonDocument.parse(edit.getKey(),actual);
   }
   int i=0;for(String path:w.dirtyPaths().stream().sorted().toList()){
    String payload="payload-"+i+".bin",source="source-"+i+".txt";Files.write(out.resolve(payload),w.readBytes(path));
    if(edits.containsKey(path))Files.writeString(out.resolve(source),edits.get(path));
    changes.add("{\"path\":"+quote(path)+",\"payload\":"+quote(payload)+",\"source\":"+(edits.containsKey(path)?quote(source):"null")+"}");i++;
   }
   w.export(out.resolve("edited.pvf"));
  }
  try(var w=PvfWorkspace.open(out.resolve("edited.pvf"),out.resolve("export-readback"))){for(String path:edits.keySet()){String text=w.readText(path,charset);if(path.endsWith(".map"))MapDocument.parse(path,text);else DungeonDocument.parse(path,text);}}
  if(!sha.equals(hash(input)))throw new AssertionError("Original archive changed");
  Files.writeString(out.resolve("manifest.json"),"{\"inputSha256\":"+quote(sha)+",\"charset\":"+quote(charset.name())+",\"hellEdited\":"+hellEdited+",\"changes\":["+String.join(",",changes)+"]}");Files.write(out.resolve("notes.txt"),notes);
  System.out.println("VISUAL_SCENE_EDIT_AUDIT_OK edits="+edits.keySet()+" hell="+hellEdited+" output="+out);
 }
}
