import studio.pvf.core.*;
import studio.pvf.preview.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.List;

/** Actual archive/image corpus check. No source or user workspace writes. Output must be new. */
public final class GamePreviewAudit {
 private record Sample(String path,String job,String action,String effect){}
 public static void main(String[] args)throws Exception {
  Path out=Path.of(args[2]);Files.createDirectory(out);var resources=NpkResources.scan(Path.of(args[1]));var report=new ArrayList<String>();
  try(var archive=PvfArchive.open(Path.of(args[0]))){
   var paths=new HashSet<String>();archive.entries().forEach(e->paths.add(e.path()));var texts=new HashMap<String,String>();
   GameScript.Lookup lookup=p->{if(!paths.contains(p))return null;String value=texts.get(p);if(value==null){value=new String(archive.readBytes(p),Charset.forName("GBK"));texts.put(p,value);}return value;};
   report.add("archive_sha256="+archive.sha256());report.add("resource_images="+resources.imageCount());
   var samples=new LinkedHashMap<String,Sample>();for(String job:CharacterAppearance.JOBS)for(String action:List.of("waiting motion","move motion","attack motion:0","jump motion"))samples.put(job+"-"+action.split(" ")[0],new Sample("skill/"+job+"/preview.skl",job,action,"actor"));
   samples.put("uniform",new Sample("equipment/character/gunner/avatar/coat/auniform_wtsummer.equ","gunner","waiting motion",""));
   for(String effect:List.of("","passive:23008","passive:48030"))samples.put("suneater"+effect.replace(':','-'),new Sample("equipment/character/gunner/weapon/hcannon/n_cann_suneater.equ","gunner","waiting motion",effect));
   samples.put("armor",new Sample("equipment/character/common/jacket/harmor/amr_ha_northmyre1.equ","gunner","waiting motion",""));
   samples.put("joytron",new Sample("equipment/character/common/title/joytron_handlingfeeling.equ","gunner","waiting motion",""));
   for(String effect:List.of("laser:normal","laser:charged"))samples.put(effect.replace(':','-'),new Sample("skill/gunner/laserrifle.skl","gunner","waiting motion",effect));
   samples.put("mage-jol",new Sample("skill/mage/launchjol.skl","mage","waiting motion",""));
   for(var sample:samples.entrySet()){
    var selected=sample.getValue();String path=selected.path(),text=lookup.read(path);if(text==null)text="";
    var options=new GamePreview.Options(selected.job(),selected.action(),Map.of(),14,true,selected.effect());
    long loadStart=System.nanoTime();var preview=GamePreview.load(path,text,lookup,paths,resources,options);long loadMs=(System.nanoTime()-loadStart)/1_000_000;
    for(long time:new long[]{0,150,500,1050,1200}){
     var frame=preview.render(time,false);var image=new BufferedImage(520,420,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(new Color(41,44,51));g.fillRect(0,0,520,420);
     double scale=Math.min(2,Math.min(460/Math.max(1,frame.bounds().getWidth()),330/Math.max(1,frame.bounds().getHeight())));double x=260-frame.bounds().getCenterX()*scale,y=48-frame.bounds().getMinY()*scale;
     for(var draw:frame.draws())draw.prepared().paint(g,x+draw.x()*scale,y+draw.y()*scale,scale,draw.mirrored());g.setFont(new Font("Microsoft YaHei",Font.PLAIN,15));g.setColor(Color.WHITE);g.drawString(preview.info().name()+" @ "+time+"ms",12,24);g.dispose();
     ImageIO.write(image,"png",out.resolve(sample.getKey()+"-"+time+".png").toFile());
     if(frame.draws().isEmpty())throw new AssertionError("No draws: "+sample);
    }
    long[] renderNanos=new long[120];for(int i=0;i<renderNanos.length;i++){long tick=System.nanoTime();preview.render(i*16L,false,true);renderNanos[i]=System.nanoTime()-tick;}Arrays.sort(renderNanos);
    report.add(sample.getKey()+" load_ms="+loadMs+" render_p95_us="+renderNanos[113]/1000+" parts="+preview.appearance().parts().size()+" effects="+preview.effects().size()+" warnings="+preview.warnings());
    for(var effect:preview.effects())report.add("  effect="+effect);
   }
   long peakHeap=java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream().filter(p->p.getType()==java.lang.management.MemoryType.HEAP).mapToLong(p->p.getPeakUsage().getUsed()).sum();report.add("heap_pool_peak_sum_bytes="+peakHeap);
   archive.verifyUnchanged();Files.write(out.resolve("report.txt"),report);report.forEach(System.out::println);
  }
 }
}
