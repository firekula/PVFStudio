package studio.pvf.sceneedit;

import java.util.List;
import java.util.Map;

/** One native placement, including all parts of a special object's payload. */
public record MapEntry(EntryId id, Type type, int definitionId, String reference, String layer,
                       double x, double y, double z, Map<String,String> properties,
                       SourceDocument.Range range, List<SourceDocument.Token> tokens,
                       Map<String,Integer> fields) {
    public enum Type { NPC, MONSTER, PASSIVE, SPECIAL, ANIMATION, BACKGROUND }
    public MapEntry { properties = Map.copyOf(properties); tokens = List.copyOf(tokens); fields = Map.copyOf(fields); }
    public boolean conditional() { return id.section().equals("monster condition"); }
    public boolean mirrored() { return properties.getOrDefault("facing", "right").equals("left"); }
}
