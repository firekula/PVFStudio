package studio.pvf.sceneedit;

import studio.pvf.preview.EntityScript;
import studio.pvf.preview.ScriptDialect;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable editable MAP snapshot; every command returns an exact-source guarded patch. */
public final class MapDocument {
    public record TileEntry(EntryId id, String reference, SourceDocument.Range range) {}
    public record SpecialContent(String kind, List<String> values) {
        public SpecialContent { kind = ScriptDialect.flag(kind); values = List.copyOf(values); }
    }
    private static final List<String> LAYERS = List.of("distantback", "middleback", "closeback", "bottom", "normal", "close");
    private final String path;
    private final SourceDocument source;
    private final MapRecords records;

    private MapDocument(String path, SourceDocument source) { this.path = path; this.source = source; records = new MapRecords(source); }
    public static MapDocument parse(String path, String text) throws IOException {
        EditSupport.reference(path, ".map"); return new MapDocument(path, SourceDocument.parse(text));
    }
    public String path() { return path; }
    public SourceDocument source() { return source; }
    public List<MapEntry> entries() { return List.copyOf(records.entries); }
    public List<TileEntry> tiles(boolean extended) { return List.copyOf(extended ? records.extended : records.basic); }
    public List<String> diagnostics() { return List.copyOf(records.diagnostics); }

    public ScriptEdit move(EntryId id, double x, double y, double z) throws IOException {
        MapEntry entry = entry(id);
        if (entry.type() == MapEntry.Type.BACKGROUND) throw new IOException("平铺背景没有单个放置坐标");
        List<ScriptEdit.Replacement> changes = new ArrayList<>();
        coordinate(changes, entry, "x", x); coordinate(changes, entry, "y", y); coordinate(changes, entry, "z", z);
        return edit("移动对象", changes);
    }
    private void coordinate(List<ScriptEdit.Replacement> changes, MapEntry entry, String key, double value) throws IOException {
        EditSupport.number(value); Integer field = entry.fields().get(key);
        if (field == null) {
            if (!key.equals("z")) throw new IOException("该记录没有 " + key + " 坐标");
            if (value != 0) {
                var previous = entry.tokens().get(entry.fields().get("y")); int at = previous.range().end();
                changes.add(source.replacement(new SourceDocument.Range(at, at), " " + EditSupport.number(value)));
            }
        } else {
            var original = entry.tokens().get(field);
            if (EditSupport.numeric(original.value()) != value) changes.add(source.replacement(original.range(), EditSupport.number(value, original)));
        }
    }

    public ScriptEdit copy(EntryId id) throws IOException {
        structuralSection(id.section());
        var entry = entry(id); var ai = aiForStructuralChange(entry);
        List<ScriptEdit.Replacement> changes = new ArrayList<>(); int at = entry.range().end();
        changes.add(source.replacement(new SourceDocument.Range(at, at), source.newline() + source.slice(entry.range())));
        if (ai != null) {
            var token = ai.values().get(monsterOrdinal(entry)); int position = token.range().end();
            changes.add(source.replacement(new SourceDocument.Range(position, position), " " + token.raw()));
        }
        return edit("复制对象", changes);
    }
    public ScriptEdit remove(EntryId id) throws IOException {
        structuralSection(id.section());
        var entry = entry(id); var ai = aiForStructuralChange(entry);
        List<ScriptEdit.Replacement> changes = new ArrayList<>(); changes.add(source.replacement(entry.range(), ""));
        if (ai != null) changes.add(source.replacement(ai.values().get(monsterOrdinal(entry)).range(), ""));
        return edit("删除对象", changes);
    }
    private List<MapEntry> ordinaryMonsters() {
        return records.entries.stream().filter(e -> e.type() == MapEntry.Type.MONSTER && !e.conditional()).toList();
    }
    private int monsterOrdinal(MapEntry entry) throws IOException {
        int index = ordinaryMonsters().indexOf(entry); if (index < 0) throw new IOException("无法确定怪物辅助记录顺序"); return index;
    }
    private SourceDocument.Section aiForStructuralChange(MapEntry entry) throws IOException {
        if (entry.type() != MapEntry.Type.MONSTER) return null;
        return checkedAi(entry.conditional());
    }
    private SourceDocument.Section checkedAi(boolean conditional) throws IOException {
        var sections = source.sections("monster specific ai");
        if (records.diagnostics.stream().anyMatch(d -> d.startsWith("[monster]"))) throw new IOException("怪物区段尚未完整解析，暂不能改变记录数量");
        if (sections.isEmpty()) return null;
        if (conditional || sections.size() != 1 || sections.get(0).values().size() != ordinaryMonsters().size()
                || sections.get(0).values().stream().anyMatch(t -> t.kind() != SourceDocument.Kind.STRING))
            throw new IOException("怪物 AI 辅助列表无法逐项关联；可修改已识别坐标，暂不能增删或复制怪物");
        return sections.get(0);
    }

    public ScriptEdit addEntity(EntityScript.Kind kind, int id, double x, double y, double z) throws IOException {
        if (id < 0) throw new IOException("对象编号不能为负数");
        if (x != Math.rint(x) || y != Math.rint(y) || z != Math.rint(z)) throw new IOException("新增实体的原生坐标需要整数");
        String xyz = EditSupport.number(x) + " " + EditSupport.number(y) + " " + EditSupport.number(z);
        String tag, record;
        SourceDocument.Section ai = null;
        switch (kind) {
            case NPC -> { tag = "NPC"; record = id + " `[right]` " + xyz; }
            case PASSIVE -> { tag = "passive object"; record = id + " " + xyz; }
            case MONSTER -> { tag = "monster"; record = id + " 1 0 " + xyz + " 1 1 `[fixed]` `[normal]`"; ai = checkedAi(false); }
            default -> throw new IOException("不支持的对象类型");
        }
        structuralSection(ScriptDialect.tag(tag));
        List<ScriptEdit.Replacement> changes = new ArrayList<>(); changes.add(EditSupport.appendRecord(source, tag, record,kind==EntityScript.Kind.MONSTER));
        if (ai != null) {
            var old = ai.values(); int at = old.isEmpty() ? ai.heading().range().end() : old.get(old.size() - 1).range().end();
            changes.add(source.replacement(new SourceDocument.Range(at, at), " `[normal]`"));
        }
        return edit("添加" + kind.label(), changes);
    }
    public ScriptEdit addAnimation(String reference, String layer, boolean background, boolean upper, double x, double y, double z) throws IOException {
        structuralSection(background?"background animation":"animation");
        EditSupport.reference(reference, ".ani"); layer = ScriptDialect.flag(layer); checkLayer(layer);
        String record = EditSupport.quote(reference) + " " + EditSupport.flag(layer) + " "
                + (background ? EditSupport.flag(upper ? "upper" : "below") : EditSupport.number(x) + " " + EditSupport.number(y) + " " + EditSupport.number(z));
        return edit("添加动画", List.of(EditSupport.appendRecord(source, background ? "background animation" : "animation", record)));
    }
    private void structuralSection(String tag) throws IOException {
        if(records.diagnostics.stream().anyMatch(d -> d.startsWith("["+tag+"]")||tag.equals("background animation")&&d.startsWith("[ani info]")))
            throw new IOException("["+tag+"] 尚未完整解析，请先修正源码再增删记录；已识别坐标仍可调整");
    }

    public ScriptEdit setField(EntryId id, String field, String value) throws IOException {
        var entry = entry(id);
        if (List.of("x", "y", "z").contains(field)) {
            double number = EditSupport.numeric(value);
            return move(id, field.equals("x") ? number : entry.x(), field.equals("y") ? number : entry.y(), field.equals("z") ? number : entry.z());
        }
        Integer position = entry.fields().get(field);
        if (position == null || field.equals("contentCount") || field.equals("traitCount")) throw new IOException("该原生记录不支持单独修改 " + field);
        var token = entry.tokens().get(position); String replacement;
        List<ScriptEdit.Replacement> changes = new ArrayList<>();
        if (field.equals("reference")) { EditSupport.reference(value, ".ani"); replacement = EditSupport.quote(value); }
        else if (List.of("layer", "facing", "copyType", "monsterType", "order").contains(field)) {
            value = ScriptDialect.flag(value);
            switch (field) {
                case "layer" -> checkLayer(value);
                case "facing" -> checkChoice(value, List.of("left", "right"));
                case "copyType" -> checkChoice(value, List.of("fixed", "half", "ratio"));
                case "order" -> checkChoice(value, List.of("below", "upper"));
                case "monsterType" -> {
                    checkChoice(value, List.of("normal", "boss", "champion", "super champion"));
                    boolean champion = value.equals("champion") || value.equals("super champion");
                    Integer count = entry.fields().get("traitCount");
                    if (!champion && count != null) for (int i = count; i < entry.tokens().size(); i++) changes.add(source.replacement(entry.tokens().get(i).range(), ""));
                    if (champion && count == null) changes.add(source.replacement(new SourceDocument.Range(token.range().end(), token.range().end()), " 0"));
                }
                default -> { }
            }
            replacement = EditSupport.flag(value);
        } else {
            int number = EditSupport.integer(value);
            if (number < 0 && !field.equals("level")) throw new IOException("该数值不能为负数");
            replacement = Integer.toString(number);
        }
        if (!token.raw().equals(replacement)) changes.add(source.replacement(token.range(), replacement));
        return edit("修改对象属性", changes);
    }
    private static void checkLayer(String layer) throws IOException { checkChoice(layer, LAYERS); }
    private static void checkChoice(String value, List<String> choices) throws IOException {
        if (!choices.contains(value)) throw new IOException("不支持的属性值：" + value);
    }

    public ScriptEdit setSpecialContents(EntryId id, List<SpecialContent> contents) throws IOException {
        var entry = entry(id);
        if (entry.type() != MapEntry.Type.SPECIAL) throw new IOException("请选择特殊被动对象");
        if (contents.size() > 4096) throw new IOException("特殊对象内容超过 4096 项");
        List<String> payloads = new ArrayList<>();
        for (var content : contents) {
            if (content.kind().equals("hellparty")) {
                if (content.values().isEmpty() || content.values().size() % 3 != 0) throw new IOException("hellparty 需要组编号、相对权重、组号三元组");
                for (String value : content.values()) if (EditSupport.integer(value) < 0) throw new IOException("hellparty 参数不能为负数");
                payloads.add("`[hellparty]` " + String.join(" ", content.values()) + " `[/hellparty]`");
            } else {
                checkChoice(content.kind(), List.of("item", "monster", "trap", "quest"));
                if (content.values().size() != 5) throw new IOException("特殊对象内容需要 5 个原生参数");
                for (String value : content.values()) EditSupport.integer(value);
                payloads.add(EditSupport.flag(content.kind()) + " " + String.join(" ", content.values()));
            }
        }
        int countIndex = entry.fields().get("contentCount"); var count = entry.tokens().get(countIndex);
        List<ScriptEdit.Replacement> changes = new ArrayList<>(); changes.add(source.replacement(count.range(), Integer.toString(contents.size())));
        String payload = String.join(source.newline(), payloads);
        if (countIndex + 1 < entry.tokens().size()) {
            var range = new SourceDocument.Range(entry.tokens().get(countIndex + 1).range().start(), entry.range().end());
            changes.add(source.replacement(range, payload));
        } else if (!payload.isEmpty()) {
            int at = count.range().end(); changes.add(source.replacement(new SourceDocument.Range(at, at), source.newline() + payload));
        }
        return edit("修改特殊对象内容", changes);
    }

    public ScriptEdit replaceTiles(boolean extended, List<String> references) throws IOException {
        var basic = extended ? tiles(false).stream().map(TileEntry::reference).toList() : references;
        var extra = extended ? references : tiles(true).stream().map(TileEntry::reference).toList();
        if (!extended && !extra.isEmpty() && basic.size() != tiles(false).size())
            throw new IOException("改变基础列数时，请同时提供保留列归属的扩展地块");
        return replaceTileGrid(basic, extra);
    }
    public ScriptEdit replaceTileGrid(List<String> basic, List<String> extended) throws IOException {
        structuralSection("tile");structuralSection("extended tile");
        if (basic.isEmpty() || basic.size() + extended.size() > 4096) throw new IOException("基础地块不能为空，地块总数最多 4096");
        if (extended.size() % basic.size() != 0) throw new IOException("新增扩展地块必须组成完整行");
        List<ScriptEdit.Replacement> changes = new ArrayList<>();
        replaceTileValues(changes, "tile", basic); replaceTileValues(changes, "extended tile", extended);
        return edit("调整地块序列", changes);
    }
    public ScriptEdit moveTileColumn(int from, int to) throws IOException {
        var basic = new ArrayList<>(tiles(false).stream().map(TileEntry::reference).toList());
        var extra = tiles(true).stream().map(TileEntry::reference).toList(); int width = basic.size();
        if (from < 0 || to < 0 || from >= width || to >= width || extra.size() % width != 0) throw new IOException("地块列或扩展行无效");
        String moved = basic.remove(from); basic.add(to, moved); List<String> updated = new ArrayList<>();
        for (int start = 0; start < extra.size(); start += width) {
            var row = new ArrayList<>(extra.subList(start, start + width)); String tile = row.remove(from); row.add(to, tile); updated.addAll(row);
        }
        return replaceTileGrid(basic, updated);
    }
    private void replaceTileValues(List<ScriptEdit.Replacement> changes, String tag, List<String> references) throws IOException {
        List<String> raw = new ArrayList<>();
        for (String reference : references) { EditSupport.reference(reference, ".til"); raw.add(EditSupport.quote(reference)); }
        var sections = source.sections(tag);
        if (sections.isEmpty()) { if (!raw.isEmpty()) changes.add(EditSupport.appendRecord(source, tag, String.join(source.newline(), raw))); }
        else {
            changes.addAll(EditSupport.values(source, sections.get(0), raw));
            for (int i = 1; i < sections.size(); i++) changes.addAll(EditSupport.values(source, sections.get(i), List.of()));
        }
    }

    private MapEntry entry(EntryId id) throws IOException {
        var entry = records.entries.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow(() -> new IOException("源记录已不存在，请重新选择"));
        if (!source.safe(entry.range())) throw new IOException("该记录尚未完整解析"); return entry;
    }
    private ScriptEdit edit(String label, List<ScriptEdit.Replacement> changes) throws IOException {
        Map<Integer,ScriptEdit.Replacement> inserts = new LinkedHashMap<>(); List<ScriptEdit.Replacement> result = new ArrayList<>();
        for (var change : changes) {
            if (!source.safe(new SourceDocument.Range(change.start(), change.end()))) throw new IOException("该片段尚未完整解析");
            if (change.start() == change.end()) inserts.merge(change.start(), change,
                    (left, right) -> new ScriptEdit.Replacement(left.start(), left.end(), "", left.value() + right.value()));
            else result.add(change);
        }
        result.addAll(inserts.values()); return new ScriptEdit(label, source.text(), result);
    }
}
