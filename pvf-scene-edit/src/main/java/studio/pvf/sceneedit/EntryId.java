package studio.pvf.sceneedit;

/** Identity inside a particular source snapshot, independent of line layout and sprite count. */
public record EntryId(String section, int sectionOccurrence, int recordOrdinal) {
    public EntryId {
        if (section == null || section.isBlank() || sectionOccurrence < 0 || recordOrdinal < 0)
            throw new IllegalArgumentException("无效的源记录编号");
    }
}
