package studio.pvf.sceneedit;

import studio.pvf.preview.DungeonScript;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static studio.pvf.preview.DungeonScript.*;

/** Native graph commands with maze-local source scopes and explicit global hell parameters. */
public final class DungeonDocument {
    public record Maze(int index, DungeonScript.Maze layout, Set<Position> rooms) {
        public Maze { rooms = Set.copyOf(rooms); }
        public boolean playable(Position p) {
            return rooms.contains(p) && (layout.mask(p) != 0 || rooms.size() == 1 && layout.starts().contains(p));
        }
    }
    public record HellSettings(boolean enabled, boolean excluded, int mapId, Position position,
                               List<Integer> hitCounts, Integer appearRate, Integer coinLimit) {
        public HellSettings { hitCounts = List.copyOf(hitCounts); }
    }
    public enum Severity { WARNING, ERROR }
    public record Problem(Severity severity, int maze, Position position, String message) {}
    private record Scope(Maze maze, int start, int end, List<SourceDocument.Section> sections, boolean editable) {}
    private final String path;
    private final SourceDocument source;
    private final Definition definition;
    private final List<Scope> scopes;
    private final boolean supportsEscape;

    private DungeonDocument(String path, String text, boolean escapeCapability) throws IOException {
        this.path = path; source = SourceDocument.parse(text);
        if (!source.diagnostics().isEmpty()) throw new IOException(source.diagnostics().get(0).message());
        definition = DungeonScript.parse(text);
        supportsEscape = escapeCapability || !source.sections("escape hell").isEmpty()
                || globalValues("hit count").size() >= 5;
        var markers = source.sections("maze info"); List<Scope> result = new ArrayList<>();
        for (int i = 0; i < definition.mazes().size(); i++) {
            var layout = definition.mazes().get(i); Set<Position> members = new LinkedHashSet<>();
            for (int y = 0; y < layout.height(); y++) for (int x = 0; x < layout.width(); x++)
                if (layout.mask(new Position(x,y)) != 0) members.add(new Position(x,y));
            members.addAll(layout.starts()); members.addAll(layout.bosses()); members.addAll(layout.maps().keySet()); members.addAll(layout.bossMaps().keySet());
            int start = markers.isEmpty() ? 0 : markers.get(i).heading().range().start();
            int end = i + 1 < markers.size() ? markers.get(i + 1).heading().range().start() : text.length();
            result.add(new Scope(new Maze(i, layout, members), start, end, source.sections().stream()
                    .filter(s -> s.heading().range().start() >= start && s.heading().range().start() < end).toList(), !markers.isEmpty()));
        }
        scopes = List.copyOf(result);
    }
    public static DungeonDocument parse(String path, String text) throws IOException { return parse(path,text,false); }
    /** The workspace adapter can supply verified format capabilities; source-only parsing stays conservative. */
    public static DungeonDocument parse(String path, String text, boolean supportsEscape) throws IOException {
        EditSupport.reference(path,".dgn"); return new DungeonDocument(path,text,supportsEscape);
    }
    public String path() { return path; }
    public SourceDocument source() { return source; }
    public Definition definition() { return definition; }
    public List<Maze> mazes() { return scopes.stream().map(Scope::maze).toList(); }
    public boolean supportsEscape() { return supportsEscape; }
    private Scope scope(int index) throws IOException {
        if (index < 0 || index >= scopes.size()) throw new IOException("布局已不存在");
        var result = scopes.get(index);
        if (!result.editable) throw new IOException("固定 warroom 布局请通过代码编辑，不能自动转换成 maze");
        return result;
    }
    private static void inside(DungeonScript.Maze maze, Position position) throws IOException {
        if (position == null || position.x() < 0 || position.y() < 0 || position.x() >= maze.width() || position.y() >= maze.height()) throw new IOException("房间坐标越界");
    }
    private static void member(Scope scope, Position position) throws IOException {
        inside(scope.maze.layout,position); if (!scope.maze.rooms.contains(position)) throw new IOException("请先创建该房间");
    }

    public ScriptEdit createRoom(int maze, Position position, List<Integer> maps) throws IOException {
        return createRoom(maze,position,maps,List.of());
    }
    public ScriptEdit createRoom(int maze, Position position, List<Integer> maps, List<Integer> bossMaps) throws IOException {
        var s = scope(maze); inside(s.maze.layout,position);
        if (s.maze.rooms.contains(position)) throw new IOException("该房间已经存在");
        if (maps.isEmpty()) throw new IOException("新房间需要先指定一个 MAP");
        return ScriptEdit.combine("创建房间",List.of(candidates(s,position,false,maps),candidates(s,position,true,bossMaps)));
    }
    public ScriptEdit setCandidates(int maze, Position position, boolean boss, List<Integer> maps) throws IOException {
        var s = scope(maze); member(s,position); return candidates(s,position,boss,maps);
    }
    private ScriptEdit candidates(Scope s, Position position, boolean boss, List<Integer> maps) throws IOException {
        if (maps.stream().anyMatch(id -> id <= 0)) throw new IOException("MAP 编号必须为正整数");
        String tag = boss ? "boss map specification" : "map specification";
        var matching = s.sections.stream().filter(section -> section.tag().equals(tag) && point(section).equals(position)).toList();
        List<ScriptEdit.Replacement> changes = new ArrayList<>();
        if (maps.isEmpty()) matching.forEach(section -> changes.add(drop(section)));
        else {
            List<String> raw = new ArrayList<>(List.of(Integer.toString(position.x()),Integer.toString(position.y())));
            maps.forEach(id -> raw.add(Integer.toString(id)));
            if (matching.isEmpty()) changes.add(append(s,tag,raw,true));
            else changes.addAll(EditSupport.values(source,matching.get(matching.size()-1),raw));
        }
        return edit("设置房间 MAP",changes);
    }
    public ScriptEdit connect(int maze, Position position, Direction direction, boolean connected) throws IOException {
        var s = scope(maze); member(s,position);
        var other = s.maze.layout.neighbor(position,direction); member(s,other);
        List<Integer> masks = new ArrayList<>(s.maze.layout.masks());
        changeBit(masks,s.maze.layout.width(),position,direction.bit,connected);
        changeBit(masks,s.maze.layout.width(),other,direction.opposite().bit,connected);
        List<ScriptEdit.Replacement> changes = new ArrayList<>(); masks(s,s.maze.layout.width(),masks,changes);
        return edit("调整房间连接",changes);
    }
    private static void changeBit(List<Integer> masks, int width, Position p, int bit, boolean enabled) {
        int offset = p.x()+p.y()*width; masks.set(offset,enabled ? masks.get(offset)|bit : masks.get(offset)&~bit);
    }
    public ScriptEdit removeRoom(int maze, Position position) throws IOException {
        var s = scope(maze); member(s,position); var layout = s.maze.layout;
        List<Integer> masks = new ArrayList<>(layout.masks()); masks.set(position.x()+position.y()*layout.width(),0);
        for (Direction d : Direction.values()) {
            Position other = layout.neighbor(position,d);
            if (bounded(layout,other)) changeBit(masks,layout.width(),other,d.opposite().bit,false);
        }
        List<ScriptEdit.Replacement> changes = new ArrayList<>(); masks(s,layout.width(),masks,changes);
        removeReferences(s,Set.of(position),changes); return edit("删除房间",changes);
    }
    public ScriptEdit setStarts(int maze, List<Position> positions) throws IOException { return positions(scope(maze),"start map",positions); }
    public ScriptEdit setBosses(int maze, List<Position> positions) throws IOException { return positions(scope(maze),"boss map",positions); }
    private ScriptEdit positions(Scope s, String tag, List<Position> positions) throws IOException {
        for (Position p : positions) member(s,p);
        List<ScriptEdit.Replacement> changes = new ArrayList<>(); setPositions(s,tag,positions,changes); return edit("设置房间候选",changes);
    }
    private void setPositions(Scope s, String tag, List<Position> positions, List<ScriptEdit.Replacement> changes) throws IOException {
        List<String> raw = new ArrayList<>();
        for (Position p : positions) { raw.add(Integer.toString(p.x())); raw.add(Integer.toString(p.y())); }
        if (positions.isEmpty() && tag.equals("boss map")) raw.addAll(List.of("-1","-1"));
        values(s,tag,raw,true,changes);
    }

    public ScriptEdit resize(int maze, int width, int height, boolean crop) throws IOException {
        dimensions(width,height); var s = scope(maze); var old = s.maze.layout;
        Set<Position> removed = new HashSet<>();
        s.maze.rooms.stream().filter(p -> p.x() >= width || p.y() >= height).forEach(removed::add);
        Position hellPosition = old.hell().position();
        if (hellPosition != null && (hellPosition.x() >= width || hellPosition.y() >= height)) removed.add(hellPosition);
        if (!crop && !removed.isEmpty()) throw new IOException("缩小布局会裁剪房间，请先确认受影响内容");
        List<Integer> masks = new ArrayList<>();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            Position p = new Position(x,y); int mask = old.mask(p);
            for (Direction d : Direction.values()) {
                Position next = old.neighbor(p,d);
                if (bounded(old,next) && (next.x() >= width || next.y() >= height)) mask &= ~d.bit;
            }
            masks.add(mask);
        }
        List<ScriptEdit.Replacement> changes = new ArrayList<>();
        values(s,"size",List.of(Integer.toString(width),Integer.toString(height)),false,changes);
        masks(s,width,masks,changes); removeReferences(s,removed,changes); return edit("调整布局大小",changes);
    }
    private void removeReferences(Scope s, Set<Position> removed, List<ScriptEdit.Replacement> changes) throws IOException {
        if (removed.isEmpty()) return;
        for (var section : s.sections) if ((section.tag().equals("map specification") || section.tag().equals("boss map specification")) && removed.contains(point(section))) changes.add(drop(section));
        if (s.maze.layout.starts().stream().anyMatch(removed::contains)) setPositions(s,"start map",s.maze.layout.starts().stream().filter(p -> !removed.contains(p)).toList(),changes);
        if (s.maze.layout.bosses().stream().anyMatch(removed::contains)) setPositions(s,"boss map",s.maze.layout.bosses().stream().filter(p -> !removed.contains(p)).toList(),changes);
        if (s.maze.layout.hell().position() != null && removed.contains(s.maze.layout.hell().position())) {
            localSections(s,"seal door map index").forEach(section -> changes.add(drop(section)));
            localSections(s,"seal door pos").forEach(section -> changes.add(drop(section)));
        }
    }
    public ScriptEdit copyMaze(int maze) throws IOException {
        var s = scope(maze); if (scopes.size() >= 256) throw new IOException("布局最多 256 套");
        String copy = source.text().substring(s.start,s.end); int at = s.end;
        for(String tag:List.of("seal door map index","seal door pos","escape hell")){var inherited=localSections(s,tag).stream().filter(section -> section.heading().range().start()<s.start).reduce((a,b) -> b);if(inherited.isPresent())copy+=source.newline()+source.slice(inherited.get().range());}
        return edit("复制布局",List.of(source.replacement(new SourceDocument.Range(at,at),source.newline()+copy+source.newline())));
    }
    public ScriptEdit addMaze(int width, int height) throws IOException {
        dimensions(width,height); if (scopes.size() >= 256) throw new IOException("布局最多 256 套");
        if (scopes.stream().noneMatch(Scope::editable)) throw new IOException("固定 warroom 布局不能自动转换成 maze");
        String nl = source.newline(); int at = source.text().length();
        String text = nl+"[maze info]"+nl+"[size] "+width+" "+height+nl+"[greed] `"+"AA".repeat(width*height)+"`"+nl
                +"[start map] 0 0 [/start map]"+nl+"[boss map] -1 -1 [/boss map]"+nl;
        return edit("新增布局",List.of(source.replacement(new SourceDocument.Range(at,at),text)));
    }
    private static void dimensions(int width, int height) throws IOException {
        if (width < 1 || width > 50 || height < 1 || height > 50) throw new IOException("布局宽高须为 1～50");
    }

    public HellSettings hell(int maze) {
        var room = scopes.get(maze).maze.layout.hell();
        return new HellSettings(definition.hellEnabled(),room.excluded(),room.mapId(),room.position(),globalValues("hit count"),globalInteger("seal door appear rate"),globalInteger("hell coin limit"));
    }
    public ScriptEdit setHell(int maze, HellSettings settings) throws IOException {
        var s = scope(maze); var before = hell(maze);
        if (settings.mapId < 0) throw new IOException("深渊 MAP 编号不能为负数");
        if (settings.enabled && !settings.excluded) {
            if (settings.mapId == 0 || settings.position == null) throw new IOException("请指定深渊 MAP 和房间坐标");
            member(s,settings.position);
        }
        if (settings.excluded && !supportsEscape) throw new IOException("当前版本没有可验证的 escape hell 支持");
        if (!before.hitCounts.isEmpty() && settings.hitCounts.size() != before.hitCounts.size()) throw new IOException("请保留原有难度参数项数");
        if (settings.hitCounts.stream().anyMatch(n -> n < 0)) throw new IOException("封印击打参数不能为负数");
        List<ScriptEdit.Replacement> changes = new ArrayList<>();
        if (settings.enabled != before.enabled) global("hell dungeon",List.of(settings.enabled ? "1":"0"),changes);
        if (settings.mapId != before.mapId) {
            if (settings.mapId == 0) localSections(s,"seal door map index").forEach(section -> changes.add(drop(section)));
            else values(s,"seal door map index",List.of(Integer.toString(settings.mapId)),false,changes);
        }
        if (!java.util.Objects.equals(settings.position,before.position)) {
            if (settings.position == null) localSections(s,"seal door pos").forEach(section -> changes.add(drop(section)));
            else { inside(s.maze.layout,settings.position); values(s,"seal door pos",List.of(Integer.toString(settings.position.x()),Integer.toString(settings.position.y())),false,changes); }
        }
        if (settings.excluded != before.excluded) values(s,"escape hell",List.of(settings.excluded ? "1":"0"),false,changes);
        if (!settings.hitCounts.equals(before.hitCounts)) global("hit count",settings.hitCounts.stream().map(String::valueOf).toList(),changes);
        if (!java.util.Objects.equals(settings.appearRate,before.appearRate)) globalNullable("seal door appear rate",settings.appearRate,changes);
        if (!java.util.Objects.equals(settings.coinLimit,before.coinLimit)) globalNullable("hell coin limit",settings.coinLimit,changes);
        return edit("设置深渊房",changes);
    }
    private List<Integer> globalValues(String tag) {
        var sections = source.sections(tag); if (sections.isEmpty()) return List.of();
        try { List<Integer> result = new ArrayList<>(); for (var token : sections.get(sections.size()-1).values()) result.add(EditSupport.integer(token.value())); return List.copyOf(result); }
        catch (IOException ignored) { return List.of(); }
    }
    private Integer globalInteger(String tag) { var values = globalValues(tag); return values.isEmpty() ? null : values.get(0); }
    private void globalNullable(String tag, Integer value, List<ScriptEdit.Replacement> changes) throws IOException {
        if (value == null) source.sections(tag).forEach(section -> changes.add(drop(section)));
        else global(tag,List.of(Integer.toString(value)),changes);
    }
    private void global(String tag, List<String> raw, List<ScriptEdit.Replacement> changes) throws IOException {
        var sections = source.sections(tag);
        if (sections.isEmpty()) changes.add(append(scopes.get(scopes.size()-1),tag,raw,false));
        else changes.addAll(EditSupport.values(source,sections.get(sections.size()-1),raw));
    }

    private void masks(Scope s, int width, List<Integer> masks, List<ScriptEdit.Replacement> changes) throws IOException {
        var original = s.sections.stream().filter(section -> section.tag().equals("greed")).reduce((a,b)->b);
        String raw = original.isPresent() && !original.get().values().isEmpty() ? original.get().values().get(0).raw() : "";
        boolean legacy = raw.chars().anyMatch(c -> LEGACY_CODES.indexOf(c)>=0);
        String separator = raw.contains("\\r\\n") ? "\\r\\n" : raw.contains("\\n") ? "\\n" : source.newline();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < masks.size(); i++) {
            if (i > 0 && i % width == 0) code.append(separator);
            int mask = masks.get(i); if (legacy) code.append(LEGACY_CODES.charAt(mask)); else code.append(String.valueOf((char)('A'+mask)).repeat(2));
        }
        char delimiter = raw.startsWith("\"") ? '"':'`'; values(s,"greed",List.of(delimiter+code.toString()+delimiter),false,changes);
    }
    private List<SourceDocument.Section> localSections(Scope s, String tag) {
        var local = s.sections.stream().filter(section -> section.tag().equals(tag)).toList();
        if (local.isEmpty() && s.maze.index == 0 && (tag.equals("seal door map index") || tag.equals("seal door pos") || tag.equals("escape hell")))
            return source.sections(tag).stream().filter(section -> section.heading().range().start() < s.start).toList();
        return local;
    }
    private void values(Scope s, String tag, List<String> raw, boolean close, List<ScriptEdit.Replacement> changes) throws IOException {
        var sections = localSections(s,tag);
        if (sections.isEmpty()) changes.add(append(s,tag,raw,close));
        else changes.addAll(EditSupport.values(source,sections.get(sections.size()-1),raw));
    }
    private ScriptEdit.Replacement append(Scope scope, String tag, List<String> raw, boolean close) {
        String nl = source.newline(); int at = scope.end;
        return source.replacement(new SourceDocument.Range(at,at),EditSupport.leadingNewline(source.text(),at,nl)+"["+tag+"]"+nl
                +String.join(" ",raw)+nl+(close ? "[/"+tag+"]"+nl:""));
    }
    private ScriptEdit.Replacement drop(SourceDocument.Section section) {
        int end = section.end(); int index = source.sections().indexOf(section);
        if (index + 1 < source.sections().size() && source.sections().get(index+1).tag().equals("/"+section.tag())) end = source.sections().get(index+1).heading().range().end();
        return source.replacement(new SourceDocument.Range(section.heading().range().start(),end),"");
    }
    private static Position point(SourceDocument.Section section) {
        try { return new Position(EditSupport.integer(section.values().get(0).value()),EditSupport.integer(section.values().get(1).value())); }
        catch (IOException | IndexOutOfBoundsException ignored) { return new Position(-1,-1); }
    }
    private ScriptEdit edit(String label, List<ScriptEdit.Replacement> changes) throws IOException {
        Map<Integer,ScriptEdit.Replacement> inserts = new LinkedHashMap<>(); List<ScriptEdit.Replacement> result = new ArrayList<>();
        for (var change : changes) {
            if (!source.safe(new SourceDocument.Range(change.start(),change.end()))) throw new IOException("该源片段尚未完整解析");
            if (change.start()==change.end()) inserts.merge(change.start(),change,(a,b)->new ScriptEdit.Replacement(a.start(),a.end(),"",a.value()+b.value()));
            else result.add(change);
        }
        result.addAll(inserts.values()); return new ScriptEdit(label,source.text(),result);
    }

    /** Existing imperfect layouts remain editable; new rooms must be reachable before export. */
    public List<Problem> newRoomErrors(DungeonDocument baseline) {
        return diagnostics().stream().filter(p -> p.severity()==Severity.ERROR && (baseline==null||p.maze()>=baseline.mazes().size()||p.position()!=null&&!baseline.mazes().get(p.maze()).rooms().contains(p.position()))).toList();
    }
    public List<Problem> diagnostics() {
        List<Problem> problems = new ArrayList<>();
        for (Scope scope : scopes) {
            Maze maze = scope.maze; var layout = maze.layout;
            for (Position p : maze.rooms) {
                if (!maze.playable(p)) problems.add(new Problem(Severity.ERROR,maze.index,p,"房间 "+p+" 待完成连接"));
                for (Direction direction : Direction.values()) if ((layout.mask(p)&direction.bit)!=0 && !layout.connected(p,direction))
                    problems.add(new Problem(Severity.ERROR,maze.index,p,"房间 "+p+" 的"+direction.label+"连接未与相邻房间对应"));
            }
            if (layout.starts().isEmpty()) problems.add(new Problem(Severity.ERROR,maze.index,null,"布局没有起点"));
            for (Position start : layout.starts()) {
                Set<Position> reached = reachable(maze,start);
                for (Position p : maze.rooms) if (!reached.contains(p)) problems.add(new Problem(Severity.ERROR,maze.index,p,"起点 "+start+" 到房间 "+p+" 不可达"));
            }
            var hell = layout.hell();
            if (definition.hellEnabled() && !hell.excluded()) {
                if (hell.mapId()<=0 || hell.position()==null) problems.add(new Problem(Severity.WARNING,maze.index,null,"当前布局尚未配置深渊房"));
                else if (!maze.rooms.contains(hell.position())) problems.add(new Problem(Severity.ERROR,maze.index,hell.position(),"深渊坐标没有对应房间"));
            }
        }
        return List.copyOf(problems);
    }
    private static boolean bounded(DungeonScript.Maze layout, Position p) { return p.x()>=0 && p.y()>=0 && p.x()<layout.width() && p.y()<layout.height(); }
    private static Set<Position> reachable(Maze maze, Position start) {
        Set<Position> visited = new HashSet<>(); ArrayDeque<Position> queue = new ArrayDeque<>(); visited.add(start); queue.add(start);
        while (!queue.isEmpty()) { Position position = queue.remove(); for (Direction d : Direction.values()) if (maze.layout.connected(position,d)) { Position next = maze.layout.neighbor(position,d); if (visited.add(next)) queue.add(next); } }
        return visited;
    }
}
