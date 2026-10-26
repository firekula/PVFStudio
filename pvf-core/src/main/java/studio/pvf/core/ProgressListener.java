package studio.pvf.core;

/** Stage-local progress. A negative total means discovery/indeterminate work. Called on the I/O thread. */
@FunctionalInterface
public interface ProgressListener {
    ProgressListener NONE = (stage, completed, total, detail) -> {};
    void update(String stage, long completed, long total, String detail);
}
