package studio.pvf.sceneedit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** A command against one exact document snapshot; it never regenerates unrelated source. */
public record ScriptEdit(String label, String baseline, List<Replacement> replacements) {
    public record Replacement(int start, int end, String expected, String value) {
        public Replacement { Objects.requireNonNull(expected); Objects.requireNonNull(value); }
    }

    public ScriptEdit {
        Objects.requireNonNull(label); Objects.requireNonNull(baseline);
        replacements = List.copyOf(replacements);
    }

    public static ScriptEdit combine(String label,List<ScriptEdit> edits) throws IOException {
        if(edits.isEmpty())throw new IOException("没有可合并的编辑");String baseline=edits.get(0).baseline();var changes=new ArrayList<Replacement>();var inserts=new java.util.LinkedHashMap<Integer,Replacement>();
        for(var edit:edits){if(!baseline.equals(edit.baseline()))throw new IOException("编辑不是来自同一源码快照");for(var change:edit.replacements()){
            if(change.start()==change.end())inserts.merge(change.start(),change,(a,b) -> new Replacement(a.start(),a.end(),"",a.value()+b.value()));else changes.add(change);
        }}changes.addAll(inserts.values());return new ScriptEdit(label,baseline,changes);
    }

    public String applyTo(String current) throws IOException {
        if (!baseline.equals(current)) throw new IOException("代码已变化，请重新选择后再编辑");
        var ordered = new ArrayList<>(replacements);
        ordered.sort(Comparator.comparingInt(Replacement::start).thenComparingInt(Replacement::end));
        Replacement previous = null;
        for (var edit : ordered) {
            if (edit.start < 0 || edit.end < edit.start || edit.end > current.length()
                    || !current.substring(edit.start, edit.end).equals(edit.expected))
                throw new IOException("编辑片段与当前代码不匹配");
            if (previous != null && (edit.start < previous.end || edit.start == previous.start))
                throw new IOException("编辑片段互相重叠");
            previous = edit;
        }
        StringBuilder result = new StringBuilder(current);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            var edit = ordered.get(i); result.replace(edit.start, edit.end, edit.value);
        }
        return result.toString();
    }
}
