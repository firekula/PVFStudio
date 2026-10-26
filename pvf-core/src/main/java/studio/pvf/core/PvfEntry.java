package studio.pvf.core;

/** Immutable entry; checksum seed and physical block layout depend on its container. */
public final class PvfEntry {
    private final Object owner;
    private final int index;
    private final long fileNumber;
    private final long offset;
    private final int length;
    private final int key;
    private final String path;
    private final byte[] rawPath;
    private final int treePosition;
    private final boolean legacy;

    PvfEntry(Object owner, int index, long fileNumber, long offset, int length, int key,
             String path, byte[] rawPath, int treePosition) {
        this(owner, index, fileNumber, offset, length, key, path, rawPath, treePosition, false);
    }

    PvfEntry(Object owner, int index, long fileNumber, long offset, int length, int key,
             String path, byte[] rawPath, int treePosition, boolean legacy) {
        this.owner = owner;
        this.index = index;
        this.fileNumber = fileNumber;
        this.offset = offset;
        this.length = length;
        this.key = key;
        this.path = path;
        this.rawPath = rawPath.clone();
        this.treePosition = treePosition;
        this.legacy = legacy;
    }

    public int index() { return index; }
    public long fileNumber() { return fileNumber; }
    public long offset() { return offset; }
    public int length() { return length; }
    public int key() { return key; }
    public String path() { return path; }
    public long paddedLength() { return legacy ? length : PvfIo.align4(length); }
    long storedLength() { return paddedLength() + (legacy ? 24 : 0); }

    boolean belongsTo(Object archiveOwner) { return owner == archiveOwner; }
    byte[] rawPath() { return rawPath.clone(); }
    int treePosition() { return treePosition; }

    @Override public String toString() { return path + " (" + length + " bytes)"; }
}
