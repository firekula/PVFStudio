package studio.pvf.sceneedit;

import studio.pvf.preview.ScriptDialect;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native record boundaries, including binary projections with no physical record newlines. */
final class MapRecords {
    final List<MapEntry> entries = new ArrayList<>();
    final List<MapDocument.TileEntry> basic = new ArrayList<>(), extended = new ArrayList<>();
    final List<String> diagnostics = new ArrayList<>();
    private final SourceDocument source;

    MapRecords(SourceDocument source) {
        this.source = source; source.diagnostics().forEach(d -> diagnostics.add(d.message()));
        SourceDocument.Section background = null; int nestedOrdinal = 0;
        var sections = source.sections();
        for (int index = 0; index < sections.size(); index++) {
            var section = sections.get(index);
            try {
                switch (section.tag()) {
                    case "tile", "extended tile" -> {
                        var target = section.tag().equals("tile") ? basic : extended;
                        for (int i = 0; i < section.values().size(); i++) {
                            var token = section.values().get(i); EditSupport.reference(token.value(), ".til");
                            target.add(new MapDocument.TileEntry(new EntryId(section.tag(), section.occurrence(), i), token.value(), token.range()));
                        }
                    }
                    case "npc", "monster", "monster condition", "passive object", "special passive object", "animation" -> placements(section);
                    case "background animation" -> { placements(section); background = section; nestedOrdinal = section.values().size() / 3; }
                    case "/background animation" -> background = null;
                    case "ani info" -> {
                        if (background == null) continue;
                        int end = index + 1;
                        while (end < sections.size() && !sections.get(end).tag().equals("/ani info")
                                && !sections.get(end).tag().equals("/background animation")) end++;
                        if (end == sections.size() || !sections.get(end).tag().equals("/ani info")) throw new IOException("ani info 尚未结束");
                        SourceDocument.Token filename = null, layer = null, order = null;
                        for (int j = index + 1; j < end; j++) {
                            var field = sections.get(j);
                            if (field.values().isEmpty()) continue;
                            if (field.tag().equals("filename")) filename = field.values().get(0);
                            if (field.tag().equals("layer")) layer = field.values().get(0);
                            if (field.tag().equals("order")) order = field.values().get(0);
                        }
                        if (filename == null || layer == null || order == null) throw new IOException("ani info 缺少 filename/layer/order");
                        var fields = fields("reference", "layer", "order");
                        entries.add(make(new EntryId("background animation", background.occurrence(), nestedOrdinal++), MapEntry.Type.BACKGROUND,
                                List.of(filename, layer, order), fields, new SourceDocument.Range(section.heading().range().start(), sections.get(end).heading().range().end())));
                        index = end;
                    }
                    default -> { }
                }
            } catch (IOException failure) { diagnostics.add("[" + section.tag() + "]：" + failure.getMessage()); }
        }
    }

    private void placements(SourceDocument.Section section) throws IOException {
        var values = section.values(); int ordinal = 0;
        for (int start = 0; start < values.size();) {
            MapEntry.Type type; Map<String,Integer> fields; int count;
            switch (section.tag()) {
                case "npc" -> {
                    type = MapEntry.Type.NPC;
                    int left = values.size() - start;
                    boolean nextAtFour = left >= 6 && List.of("left", "right").contains(flag(values.get(start + 5)));
                    count = left == 4 || nextAtFour ? 4 : 5;
                    fields = count == 4 ? fields("id", "facing", "x", "y") : fields("id", "facing", "x", "y", "z");
                }
                case "monster", "monster condition" -> {
                    type = MapEntry.Type.MONSTER; need(values, start, 10);
                    fields = fields("id", "autoLevel", "level", "x", "y", "z", "randomDrops", "fixedDrops", "copyType", "monsterType");
                    int typeIndex = 9;
                    if (flag(values.get(start + typeIndex)).equals("npc")) {
                        need(values, start, 12); fields.put("npcId", 10); fields.put("monsterType", 11); typeIndex = 11;
                    }
                    count = typeIndex + 1; String monsterType = flag(values.get(start + typeIndex));
                    if (monsterType.equals("champion") || monsterType.equals("super champion")) {
                        need(values, start, count + 1); int traits = EditSupport.integer(values.get(start + count).value());
                        if (traits < 0 || traits > 64) throw new IOException("精英特性数量超出范围");
                        fields.put("traitCount", count); count += traits + 1;
                    }
                }
                case "passive object" -> { type = MapEntry.Type.PASSIVE; count = 4; fields = fields("id", "x", "y", "z"); }
                case "special passive object" -> {
                    type = MapEntry.Type.SPECIAL; need(values, start, 4);
                    boolean noZ = sameLine(values.get(start), values.get(start + 2)) && !sameLine(values.get(start + 2), values.get(start + 3))
                            && (start + 4 == values.size() || !sameLine(values.get(start + 3), values.get(start + 4)));
                    int header = noZ ? 4 : 5; need(values, start, header);
                    fields = noZ ? fields("id", "x", "y", "contentCount") : fields("id", "x", "y", "z", "contentCount");
                    int contents = EditSupport.integer(values.get(start + header - 1).value());
                    if (contents < 0 || contents > 4096) throw new IOException("特殊对象内容数量超出范围");
                    int end = start + header;
                    for (int item = 0; item < contents; item++) {
                        need(values, end, 1); String kind = flag(values.get(end));
                        if (kind.equals("hellparty")) {
                            int begin = ++end;
                            while (end < values.size() && !flag(values.get(end)).equals("/hellparty")) end++;
                            if (end == values.size() || (end - begin) % 3 != 0) throw new IOException("hellparty 内容尚未完整");
                            end++;
                        } else if (List.of("item", "monster", "trap", "quest").contains(kind)) { need(values, end, 6); end += 6; }
                        else throw new IOException("特殊对象内容类型无法识别：" + kind);
                    }
                    count = end - start;
                }
                case "animation" -> { type = MapEntry.Type.ANIMATION; count = 5; fields = fields("reference", "layer", "x", "y", "z"); }
                case "background animation" -> { type = MapEntry.Type.BACKGROUND; count = 3; fields = fields("reference", "layer", "order"); }
                default -> throw new IOException("未知放置区段");
            }
            need(values, start, count); var record = values.subList(start, start + count);
            entries.add(make(new EntryId(section.tag(), section.occurrence(), ordinal++), type, record, fields,
                    new SourceDocument.Range(record.get(0).range().start(), record.get(record.size() - 1).range().end())));
            if (entries.size() > 8192) throw new IOException("编辑对象超过 8192 个");
            start += count;
        }
    }

    private MapEntry make(EntryId id, MapEntry.Type type, List<SourceDocument.Token> tokens, Map<String,Integer> fields, SourceDocument.Range range) throws IOException {
        Map<String,String> properties = new LinkedHashMap<>();
        for (var field : fields.entrySet()) {
            String value = tokens.get(field.getValue()).value();
            if (List.of("facing", "layer", "order", "copyType", "monsterType").contains(field.getKey())) value = ScriptDialect.flag(value);
            properties.put(field.getKey(), value);
        }
        String reference = properties.getOrDefault("reference", "");
        if (!reference.isEmpty()) EditSupport.reference(reference, ".ani");
        return new MapEntry(id, type, properties.containsKey("id") ? EditSupport.integer(properties.get("id")) : -1,
                reference, properties.getOrDefault("layer", "normal"), coordinate(properties, "x"), coordinate(properties, "y"), coordinate(properties, "z"), properties, range, tokens, fields);
    }
    private static double coordinate(Map<String,String> properties, String key) throws IOException {
        return properties.containsKey(key) ? EditSupport.numeric(properties.get(key)) : 0;
    }
    private boolean sameLine(SourceDocument.Token first, SourceDocument.Token second) {
        String between = source.text().substring(first.range().end(), second.range().start());
        return between.indexOf('\n') < 0 && between.indexOf('\r') < 0;
    }
    private static String flag(SourceDocument.Token token) { return ScriptDialect.flag(token.value()); }
    private static Map<String,Integer> fields(String... names) {
        Map<String,Integer> result = new LinkedHashMap<>(); for (int i = 0; i < names.length; i++) result.put(names[i], i); return result;
    }
    private static void need(List<?> values, int start, int count) throws IOException {
        if (start < 0 || count < 0 || start + count > values.size()) throw new IOException("放置参数尚未完整");
    }
}
