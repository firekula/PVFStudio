package studio.pvf.preview;

import studio.pvf.core.PvfArchive;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

/** Audit the rendered overlay, using the track clock to include brief and image-less attack frames. */
public final class Act1CombatPhaseAudit {
    private static final Map<Class<?>,Map<String,Field>> FIELDS=new HashMap<>();

    public static void main(String[] args) throws Exception {
        if(args.length<3||args.length>4)throw new IllegalArgumentException(
                "Usage: Act1CombatPhaseAudit <Script.pvf> <ImagePacks2> <output.tsv> [skill level]");
        Path output=Path.of(args[2]);
        int requestedLevel=args.length>3?Integer.parseInt(args[3]):1;
        try(var archive=PvfArchive.open(Path.of(args[0]));
            var report=new PrintWriter(Files.newBufferedWriter(output))) {
            var resources=NpkResources.scan(Path.of(args[1]));
            var paths=new HashSet<String>();
            var cache=new HashMap<String,String>();
            archive.entries().forEach(e->paths.add(e.path()));
            GameScript.Lookup lookup=p->{
                if(!paths.contains(p))return null;
                if(!cache.containsKey(p))cache.put(p,new String(archive.readBytes(p),Charset.forName("GBK")));
                return cache.get(p);
            };
            report.println("job\tid\tpath\ttype\tlevel\tname\tchoice\tbranch\tpreview\tattack\tfirst_ms\tpeak\tattack_stages\tsources\tattack_resources\tissues\tdescription");
            int skills=0,branches=0,covered=0,errors=0;
            for(String job:CharacterAppearance.JOBS){
                var entries=new TreeMap<>(GameScript.catalog(lookup.read("skill/"+job+"skill.lst")));
                for(var entry:entries.entrySet()){
                    String path="skill/"+entry.getValue(),script=lookup.read(path);
                    var fields=GameScript.fields(script);
                    String type=GameScript.flag(GameScript.value(fields,"type"));
                    int max=GameScript.integer(GameScript.value(fields,"maximum level"),1);
                    int level=Math.max(1,Math.min(requestedLevel,max));
                    var options=new GamePreview.Options(job,"waiting motion",Map.of(),level,true,"");
                    var choices=new LinkedHashMap<String,String>();
                    choices.put("","自动");
                    GamePreview base=null;
                    try{
                        base=GamePreview.load(path,script,lookup,paths,resources,options);
                        for(var choice:base.effects())if(!choice.key().equals("actor"))
                            choices.put(choice.key(),choice.label());
                    }catch(Exception e){System.err.println(path+" load: "+e);}
                    skills++;
                    for(var choice:choices.entrySet()){
                        try{
                            GamePreview model=choice.getKey().isEmpty()&&base!=null?base:
                                    GamePreview.load(path,script,lookup,paths,resources,options.withEffect(choice.getKey()));
                            var row=inspect(model);
                            if(row.peak>0)covered++;
                            report.println(tsv(job,entry.getKey(),path,type,level,model.info().name(),choice.getKey(),choice.getValue(),
                                    model.unavailableReason().isEmpty()?"available":"unavailable",row.peak>0?"shown":"none",
                                    row.first,row.peak,row.stages,String.join(" | ",row.sources),String.join(" | ",row.resources),
                                    String.join(" | ",model.warnings()),model.info().explanation()));
                        }catch(Exception e){
                            errors++;
                            report.println(tsv(job,entry.getKey(),path,type,level,"",choice.getKey(),choice.getValue(),
                                    "error","unknown",-1,0,"","","",e,""));
                        }
                        branches++;
                    }
                }
                report.flush();
                System.out.println(job+" "+skills+" skills, "+branches+" views, "+covered+" with attack, "+errors+" errors");
            }
            archive.verifyUnchanged();
            System.out.println("Archive verified unchanged");
        }
    }

    private record Observation(int peak,long first,String stages,Set<String> sources,Set<String> resources){}
    private static Observation inspect(GamePreview model)throws Exception{
        long end=model.duration();
        var samples=new TreeSet<Long>();
        var paths=new TreeSet<String>();
        samples.add(0L);
        samples.add(end-1);
        for(var area:(List<?>)field(model,"attackAreas")){
            long start=(long)field(area,"start"),stop=(long)field(area,"end");
            long growthEnd=(long)field(area,"growthEnd");
            addTime(samples,start-1,end);
            addTime(samples,start,end);
            addTime(samples,growthEnd,end);
            addTime(samples,(long)field(area,"firstPulse"),end);
            addTime(samples,stop-1,end);
            addTime(samples,stop,end);
            paths.add("ACT1 runtime: "+field(area,"label"));
        }
        for(Object track:(List<?>)field(model,"tracks")){
            var clock=(AnimationTimeline)field(track,"timeline");
            if(!clock.hasAttackBoxes())continue;
            long start=(long)field(track,"start"),stop=Math.min(end,(long)field(track,"end"));
            var style=(Act1SkillSequence.VisualStyle)field(track,"visualStyle");
            Object combat=field(track,"combat");
            if(!(boolean)field(combat,"attack"))continue;
            Object actor=field(combat,"actor");
            if(actor!=null&&(boolean)field(actor,"secondary"))continue;
            String path=(String)field(track,"path");
            if(actor==null && path.startsWith("monster/") && !(boolean)field(combat,"alliedAttack"))continue;
            paths.add(path);
            long period=style.loopPeriod()>0?style.loopPeriod():clock.duration();
            boolean looping=(boolean)field(track,"loop")||style.loopPeriod()>0;
            addTime(samples,start-1,end);
            addTime(samples,stop,end);
            addTime(samples,stop-1,end);
            if(period>0)for(long cycle=start;cycle<stop;cycle+=period){
                addTime(samples,cycle,stop);
                for(long boundary:(long[])field(clock,"ends")){
                    if(boundary>period)break;
                    addTime(samples,cycle+boundary,stop);
                    addTime(samples,cycle+boundary-1,stop);
                }
                if(!looping)break;
            }
        }
        for(Object track:(List<?>)field(model,"particles")){
            @SuppressWarnings("unchecked")
            var clocks=(List<AnimationTimeline>)field(track,"animations");
            if(clocks.stream().noneMatch(AnimationTimeline::hasAttackBoxes))continue;
            paths.add((String)field(track,"path"));
            long start=(long)field(track,"start"),stop=Math.min(end,(long)field(track,"end"));
            addTime(samples,start-1,end);
            addTime(samples,stop,end);
            addTime(samples,stop-1,end);
            for(long t=start;t<stop;t+=10)samples.add(t);
            for(var clock:clocks)for(long boundary:(long[])field(clock,"ends")){
                addTime(samples,start+boundary-1,stop);
                addTime(samples,start+boundary,stop);
            }
        }
        int peak=0;
        long first=-1;
        var sources=new TreeSet<String>();
        var active=new TreeMap<String,Long>();
        var spans=new TreeMap<String,List<String>>();
        for(long time:samples){
            var attacks=model.render(time,false,false,true).combatVolumes().stream()
                    .filter(v->v.kind()==CombatDebug.Kind.ATTACK).toList();
            if(!attacks.isEmpty()&&first<0)first=time;
            peak=Math.max(peak,attacks.size());
            var present=new TreeSet<String>();
            for(var attack:attacks)present.add(attack.label());
            sources.addAll(present);
            for(var source:List.copyOf(active.keySet()))if(!present.contains(source)){
                long from=active.remove(source);
                spans.computeIfAbsent(source,k->new ArrayList<>()).add(from+"–"+time);
            }
            for(var source:present)active.putIfAbsent(source,time);
        }
        for(var entry:active.entrySet())spans.computeIfAbsent(entry.getKey(),k->new ArrayList<>()).add(entry.getValue()+"–"+end);
        var stages=new ArrayList<String>();
        spans.forEach((label,intervals)->stages.add(label+": "+String.join(", ",intervals)));
        return new Observation(peak,first,String.join(" | ",stages),sources,paths);
    }
    private static void addTime(Set<Long> samples,long time,long stop){if(time>=0&&time<stop)samples.add(time);}
    private static Object field(Object value,String name)throws Exception{
        var fields=FIELDS.computeIfAbsent(value.getClass(),k->new HashMap<>());
        var field=fields.get(name);
        if(field==null){
            field=value.getClass().getDeclaredField(name);
            field.setAccessible(true);
            fields.put(name,field);
        }
        return field.get(value);
    }
    private static String tsv(Object... columns){
        return String.join("\t",Arrays.stream(columns).map(String::valueOf)
                .map(s->s.replaceAll("[\\t\\r\\n]+"," ").strip()).toList());
    }
}
