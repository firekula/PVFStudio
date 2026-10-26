package studio.pvf.intellij;

import studio.pvf.sceneedit.MapRegistration;
import studio.pvf.workspace.PvfWorkspace;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/** Prepared cross-file operation; the editor bridge owns freeze/version checks and global undo. */
final class MapCloneCommand {
    private MapCloneCommand() {}
    record Prepared(MapRegistration.Prepared registration, List<PvfWorkspace.TextChange> changes, Map<String,String> documentTexts) {
        Prepared { changes=List.copyOf(changes); documentTexts=Map.copyOf(documentTexts); }
        PvfWorkspace.BatchSnapshot publish(PvfWorkspace workspace) throws IOException {
            return workspace.applyTextBatch(Map.of(registration.destinationPath(),registration.mapBytes()),changes);
        }
    }
    static Prepared prepare(MapRegistration.Request request, Charset charset, byte[] expectedList, byte[] expectedDungeon) throws IOException {
        if(expectedList==null||expectedDungeon==null)throw new IOException("缺少现有索引/副本文档的工作区快照");
        var registration=MapRegistration.prepare(request);
        String list=registration.listEdit().applyTo(request.mapListText());
        String dungeon=registration.dungeonEdit().applyTo(request.dungeonText());
        var changes=List.of(new PvfWorkspace.TextChange("map/map.lst",list,charset,expectedList),
                new PvfWorkspace.TextChange(request.dungeonPath(),dungeon,charset,expectedDungeon),
                new PvfWorkspace.TextChange(registration.destinationPath(),registration.mapText(),charset,null));
        return new Prepared(registration,changes,Map.of("map/map.lst",list,request.dungeonPath(),dungeon,registration.destinationPath(),registration.mapText()));
    }
}
