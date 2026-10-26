package studio.pvf.core;

/** Container identity; script encoding is independent of the container. */
public enum PvfFormat {
    ACT1("ACT1 / DNF_SCRIPT_PACK"),
    UUID("UUID / classic PVF"),
    LEGACY("Legacy / fixed directory");

    private final String description;
    PvfFormat(String description) { this.description = description; }
    public String description() { return description; }
}
