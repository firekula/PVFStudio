package studio.pvf.sceneedit;

import studio.pvf.preview.EntityScript;
import studio.pvf.preview.ResourceScript;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Lightweight current-workspace catalog. No entity definitions or images are read eagerly. */
public final class SceneCatalog {
    @FunctionalInterface public interface TextLookup { String read(String path) throws IOException; }
    private final Set<String> paths;
    private final Map<EntityScript.Kind,Map<Integer,String>> entities = new EnumMap<>(EntityScript.Kind.class);
    private final Map<Integer,String> maps = new TreeMap<>();
    private final List<String> diagnostics = new ArrayList<>();

    private SceneCatalog(Set<String> paths) {
        this.paths = paths.stream().map(SceneCatalog::canonical).collect(Collectors.toUnmodifiableSet());
    }
    public static SceneCatalog build(Set<String> paths, TextLookup lookup) {
        var catalog = new SceneCatalog(paths);
        for (var kind : EntityScript.Kind.values()) catalog.entities.put(kind, catalog.readList(kind.list(), lookup));
        catalog.maps.putAll(catalog.readList("map/map.lst", lookup));
        return catalog;
    }
    private Map<Integer,String> readList(String path, TextLookup lookup) {
        if (!paths.contains(path)) return Map.of();
        try {
            String text = lookup.read(path);
            if (text == null) throw new IOException("索引文件不存在");
            Map<Integer,String> result = new TreeMap<>();
            for (var item : EntityScript.catalog(text).entrySet()) result.put(item.getKey(), resolve(path, item.getValue()));
            return Collections.unmodifiableMap(new LinkedHashMap<>(result));
        } catch (IOException failure) { diagnostics.add(path + "：" + failure.getMessage()); return Map.of(); }
    }
    public Map<Integer,String> entities(EntityScript.Kind kind) { return entities.getOrDefault(kind, Map.of()); }
    public Map<Integer,String> maps() { return Collections.unmodifiableMap(maps); }
    public List<String> diagnostics() { return List.copyOf(diagnostics); }
    public Set<String> paths() { return paths; }
    public List<String> filesUnder(String source, String extension) {
        String parent = parent(canonical(source)), suffix = extension.toLowerCase(Locale.ROOT);
        return paths.stream().filter(path -> path.startsWith(parent) && path.endsWith(suffix)).sorted().toList();
    }
    public String resolve(String source, String reference) {
        var candidates = ResourceScript.animationCandidates(canonical(source), reference);
        return candidates.stream().filter(paths::contains).findFirst().orElse(candidates.isEmpty() ? canonical(reference) : candidates.get(0));
    }
    public String relative(String source, String target) throws IOException {
        String folder = parent(canonical(source)), normalized = canonical(target);
        if (normalized.startsWith(folder)) return normalized.substring(folder.length());
        String[] from = folder.isEmpty() ? new String[0] : folder.substring(0, folder.length() - 1).split("/");
        String[] to = normalized.split("/"); int common = 0;
        while (common < from.length && common < to.length && from[common].equals(to[common])) common++;
        return "../".repeat(from.length - common) + String.join("/", List.of(to).subList(common, to.length));
    }
    public static String canonical(String path) { return path.replace('\\', '/').toLowerCase(Locale.ROOT); }
    private static String parent(String path) { return path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : ""; }
}
