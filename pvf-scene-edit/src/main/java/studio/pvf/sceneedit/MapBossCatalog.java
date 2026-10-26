package studio.pvf.sceneedit;

import java.io.IOException;
import java.util.*;

/** Worker-owned cache; open/drafted paths must be supplied as volatile on every snapshot. */
public final class MapBossCatalog {
    private final Map<String,MapBossContent> originals=new HashMap<>();
    public Map<Integer,MapBossContent> classify(SceneCatalog catalog,SceneCatalog.TextLookup lookup,Set<String> volatilePaths){
        var result=new HashMap<Integer,MapBossContent>();var snapshot=new HashMap<String,MapBossContent>();
        for(var entry:catalog.maps().entrySet()){
            String path=entry.getValue();var value=snapshot.get(path);
            if(value==null){
                value=volatilePaths.contains(path)?null:originals.get(path);
                if(value==null){try{value=MapBossContent.inspect(lookup.read(path));}catch(IOException unreadable){value=MapBossContent.UNKNOWN;}if(!volatilePaths.contains(path))originals.put(path,value);}
                snapshot.put(path,value);
            }
            result.put(entry.getKey(),value);
        }
        return Map.copyOf(result);
    }
}
