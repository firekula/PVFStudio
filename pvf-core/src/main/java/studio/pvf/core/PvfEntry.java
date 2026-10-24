package studio.pvf.core;

/** An immutable entry; key is CRC32 of padded plaintext seeded by its relative data offset. */
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

    PvfEntry(Object owner, int index, long fileNumber, long offset, int length, int key,
             String path, byte[] rawPath, int treePosition) {
        this.owner = owner;
        this.index = index;
        this.fileNumber = fileNumber;
        this.offset = offset;
        this.length = length;
        this.key = key;
        this.path = path;
        this.rawPath = rawPath.clone();
        this.treePosition = treePosition;
    }

    public int index() { return index; }
    public long fileNumber() { return fileNumber; }
    public long offset() { return offset; }
    public int length() { return length; }
    public int key() { return key; }
    public String path() { return path; }
    public long paddedLength() { return PvfIo.align4(length); }

    boolean belongsTo(Object archiveOwner) { return owner == archiveOwner; }
    byte[] rawPath() { return rawPath.clone(); }
    int treePosition() { return treePosition; }

    @Override public String toString() { return path + " (" + length + " bytes)"; }
}
