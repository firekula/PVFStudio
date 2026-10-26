package studio.pvf.sceneedit;

import studio.pvf.preview.NativeMonsterRecord;
import studio.pvf.preview.ScriptDialect;
import java.util.List;

/** Static native Boss placements, independent of the MAP's automatic-pool type. */
public enum MapBossContent {
    BOSS, NONE, UNKNOWN;

    public static MapBossContent inspect(String text){
        if(text==null)return UNKNOWN;
        try{
            var source=SourceDocument.parse(text);if(!source.diagnostics().isEmpty())return UNKNOWN;
            boolean boss=false;
            for(var section:source.sections())if(section.tag().equals("monster")||section.tag().equals("monster condition")){
                var values=section.values().stream().map(SourceDocument.Token::value).toList();
                for(int start=0;start<values.size();){
                    var record=NativeMonsterRecord.read(values,start);String kind=ScriptDialect.flag(values.get(start+record.typeIndex()));
                    if(!List.of("normal","boss","champion","super champion").contains(kind))return UNKNOWN;
                    boss|=kind.equals("boss");start+=record.width();
                }
            }
            return boss?BOSS:NONE;
        }catch(Exception incomplete){return UNKNOWN;}
    }
}
