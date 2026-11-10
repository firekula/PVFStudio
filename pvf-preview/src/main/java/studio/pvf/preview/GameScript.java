package studio.pvf.preview;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Read-only PVF token events. Quoted tags stay values; include is an event of its own. */
public final class GameScript {
    private GameScript() {}
    @FunctionalInterface public interface Lookup { String read(String path) throws IOException; }
    private static final Pattern TOKENS=Pattern.compile("//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|`[^`]*`|\"[^\"]*\"|\\[[^\\]\\r\\n]+\\]|<include>|[^\\s\\[\"`]+",Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK=Pattern.compile("<(\\d+)::([^>]+)>");
    public record Field(String tag,List<String> values) {
        public String first(){return values.isEmpty()?"":values.get(0);}
        public String text(){return String.join(" ",values);}
    }
    public static List<Field> fields(String text) {
        if(text==null)return List.of();if(text.length()>8_000_000)throw new IllegalArgumentException("预览脚本超过 8 MB 字符上限");
        var result=new ArrayList<Field>();var values=new ArrayList<String>();String tag="";var matcher=TOKENS.matcher(text);int count=0;
        while(matcher.find()) {
            if(++count>250_000)throw new IllegalArgumentException("预览脚本 token 超限");String token=matcher.group();
            if(token.startsWith("//")||token.startsWith("/*"))continue;
            if(token.startsWith("[")||token.equalsIgnoreCase("<include>")) {
                if(!tag.isEmpty())result.add(new Field(tag,List.copyOf(values)));values.clear();
                tag=token.startsWith("[")?ScriptDialect.tag(token.substring(1,token.length()-1)):"include";
            } else values.add(token.startsWith("`")||token.startsWith("\"")?token.substring(1,token.length()-1):token);
        }
        if(!tag.isEmpty())result.add(new Field(tag,List.copyOf(values)));return List.copyOf(result);
    }
    public static String value(List<Field> fields,String tag){return fields.stream().filter(f->f.tag().equals(tag)).map(Field::first).findFirst().orElse("");}
    public static List<String> values(List<Field> fields,String tag){return fields.stream().filter(f->f.tag().equals(tag)).flatMap(f->f.values().stream()).toList();}
    public static int integer(String text,int fallback){try{return Integer.parseInt(text.replace("+",""));}catch(NumberFormatException e){return fallback;}}
    public static String flag(String value){return value.replace("[","").replace("]","").trim().toLowerCase(Locale.ROOT);}
    public static String normalize(String path){return path.replace('\\','/').toLowerCase(Locale.ROOT);}
    public static String resolve(String owner,String reference,Lookup lookup) throws IOException {
        if(reference.contains(":")||reference.indexOf('\0')>=0)return null;
        for(String path:ResourceScript.animationCandidates(owner,reference))if(lookup.read(path)!=null)return path;
        return null;
    }
    public static Map<Integer,String> catalog(String text) {
        if(text==null)return Map.of();var tokens=ResourceScript.listTokens(text);var result=new LinkedHashMap<Integer,String>();
        for(int i=0;i+1<tokens.size();i++){int id=integer(tokens.get(i),-1);if(id>=0)result.put(id,normalize(tokens.get(++i)));}
        return Collections.unmodifiableMap(result);
    }
    public static final class Strings {
        private final Lookup lookup;private final List<String> warnings;private final Map<Integer,Map<String,String>> cache=new HashMap<>();private Map<Integer,String> catalogs;
        public Strings(Lookup lookup,List<String> warnings){this.lookup=lookup;this.warnings=warnings;}
        public String resolve(String input) throws IOException {return resolve(input,new HashSet<>(),0).replace("\\n","\n").replace("\\r","").replace("%%","%");}
        private String resolve(String input,Set<String> active,int depth) throws IOException {
            if(depth>8){warnings.add("本地化引用嵌套超限");return input;}
            var matcher=LINK.matcher(input);var result=new StringBuffer();
            while(matcher.find()) {
                String key=matcher.group(),replacement=key;int id=integer(matcher.group(1),-1);
                if(active.add(key)) {
                    var table=table(id);String raw=table.get(matcher.group(2));
                    if(raw!=null)replacement=resolve(raw,active,depth+1);else warnings.add("找不到本地化字符串 "+key);
                    active.remove(key);
                }else warnings.add("本地化引用循环 "+key);
                matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(result);return result.toString();
        }
        private Map<String,String> table(int id) throws IOException {
            if(cache.containsKey(id))return cache.get(id);if(catalogs==null)catalogs=catalog(lookup.read("string.lst"));
            var result=new HashMap<String,String>();String path=catalogs.get(id),text=path==null?null:lookup.read(path);
            if(text!=null){if(text.length()>8_000_000)throw new IOException("本地化文件超限");for(String line:text.split("\\R")){int split=line.indexOf('>');if(split>0&&!line.stripLeading().startsWith("//"))result.putIfAbsent(line.substring(0,split).trim(),line.substring(split+1));}}
            cache.put(id,result);return result;
        }
    }
}
