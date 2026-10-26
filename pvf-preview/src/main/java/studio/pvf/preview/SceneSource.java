package studio.pvf.preview;

/** Source placement identity within one exact parsed MAP, independent of sprite parts or visibility. */
public record SceneSource(String section, int sectionOccurrence, int recordOrdinal) {}
