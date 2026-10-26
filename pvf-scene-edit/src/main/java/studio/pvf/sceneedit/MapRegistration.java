package studio.pvf.sceneedit;

import studio.pvf.core.PvfArchive;
import studio.pvf.preview.EntityScript;
import studio.pvf.preview.DungeonScript.Position;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Pure preparation for clone + map.lst registration + DGN binding; publishes nothing. */
public final class MapRegistration {
    private MapRegistration() {}
    public record Request(Set<String> paths, String sourcePath, String destinationPath, byte[] sourceBytes,
                          String sourceText, String mapListText, String dungeonPath, String dungeonText,
                          int maze, Position room, boolean boss, Integer requestedId, int maximumId) {
        public Request { paths = Set.copyOf(paths); sourceBytes = sourceBytes.clone(); }
        @Override public byte[] sourceBytes() { return sourceBytes.clone(); }
    }
    public record Prepared(int mapId, String destinationPath, byte[] mapBytes, String mapText, ScriptEdit listEdit, ScriptEdit dungeonEdit) {
        public Prepared { mapBytes = mapBytes.clone(); }
        @Override public byte[] mapBytes() { return mapBytes.clone(); }
    }
    public static Prepared prepare(Request request) throws IOException {
        String source = PvfArchive.normalizePath(request.sourcePath), destination = PvfArchive.normalizePath(request.destinationPath);
        EditSupport.reference(source,".map"); EditSupport.reference(destination,".map");
        if (!source.startsWith("map/") || !destination.startsWith("map/")) throw new IOException("MAP 必须在 map/ 下");
        if (!parent(source).equals(parent(destination))) throw new IOException("请复制到同目录，以保留 TIL/ANI 的相对引用");
        var paths = request.paths.stream().map(SceneCatalog::canonical).collect(java.util.stream.Collectors.toSet());
        if (!paths.contains(source)) throw new IOException("找不到要复制的源 MAP");
        if (paths.stream().anyMatch(p->p.equals(destination)||p.startsWith(destination+"/")||destination.startsWith(p+"/"))) throw new IOException("目标文件已存在或路径冲突");
        var catalog = EntityScript.catalog(request.mapListText); TreeSet<Integer> used = new TreeSet<>(catalog.keySet());
        int id;
        if (request.requestedId != null) id = request.requestedId;
        else {
            long next = 1;
            for (int candidate : used) { if (candidate < next) continue; if (candidate != next) break; next++; }
            if (next > request.maximumId) throw new IOException("没有可用的 MAP 编号"); id = (int)next;
        }
        if (id <= 0 || id > request.maximumId || used.contains(id)) throw new IOException("MAP 编号无效或已使用："+id);
        var list = SourceDocument.parse(request.mapListText);
        if (!list.diagnostics().isEmpty()) throw new IOException("map.lst 尚未完整解析");
        int at = request.mapListText.length(); String nl = list.newline();
        ScriptEdit listEdit = list.replace("注册 MAP",new SourceDocument.Range(at,at),EditSupport.leadingNewline(request.mapListText,at,nl)
                +id+"\t"+EditSupport.quote(destination.substring("map/".length()))+nl);
        var dungeon = DungeonDocument.parse(request.dungeonPath,request.dungeonText);
        ScriptEdit dungeonEdit = dungeon.setCandidates(request.maze,request.room,request.boss,List.of(id));
        return new Prepared(id,destination,request.sourceBytes,request.sourceText,listEdit,dungeonEdit);
    }
    private static String parent(String path) { return path.substring(0,path.lastIndexOf('/')+1); }
}
