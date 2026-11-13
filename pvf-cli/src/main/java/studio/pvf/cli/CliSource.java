package studio.pvf.cli;

import studio.pvf.core.PvfArchive;
import studio.pvf.workspace.BinaryAniCodec;
import studio.pvf.workspace.BinaryScriptCodec;
import studio.pvf.workspace.PvfTextEncoding;
import studio.pvf.workspace.PvfWorkspace;
import studio.pvf.workspace.TextCodec;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;

final class CliSource implements AutoCloseable {
    private static final int MAX_TEXT_BYTES = 8 * 1024 * 1024;
    private final PvfArchive archive;
    private final PvfWorkspace workspace;
    private final Charset charset;
    private final LinkedHashMap<String, Integer> entries = new LinkedHashMap<>();
    private BinaryScriptCodec.StringTable stringTable;

    private CliSource(PvfArchive archive, PvfWorkspace workspace, Charset explicitCharset) {
        this.archive = archive;
        this.workspace = workspace;
        this.charset = explicitCharset == null ? PvfTextEncoding.defaultCharset(archive.format()) : explicitCharset;
        archive.entries().forEach(entry -> entries.put(entry.path(), entry.length()));
    }

    static CliSource open(Path archive, Path workspace, String encoding, boolean createWorkspace) throws IOException {
        Charset charset = encoding == null ? null : Charset.forName(encoding);
        if (!Files.isRegularFile(archive)) {
            throw new CliFailure("ARCHIVE_NOT_FOUND", "Archive not found: " + archive, "Pass the filesystem path of Script.pvf.");
        }
        if (workspace == null) return new CliSource(PvfArchive.open(archive), null, charset);
        if (!createWorkspace && !Files.isRegularFile(workspace.resolve("workspace.properties"))) {
            throw new CliFailure("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspace,
                    "Use the same workspace directory as stage-text or replace-text.");
        }
        var drafts = PvfWorkspace.open(archive, workspace);
        return new CliSource(drafts.archive(), drafts, charset);
    }

    PvfArchive archive() { return archive; }
    PvfWorkspace workspace() { return workspace; }
    Charset charset() { return charset; }
    Set<String> paths() { return workspace == null ? entries.keySet() : workspace.paths(); }
    int length(String path) throws IOException { return workspace == null ? entries.get(path) : workspace.length(path); }

    String requireEntry(String path) throws IOException {
        String normalized = PvfArchive.normalizePath(path);
        if (!(workspace == null ? entries.containsKey(normalized) : workspace.contains(normalized))) {
            throw new CliFailure("ENTRY_NOT_FOUND", "Entry not found: " + normalized,
                    "Use list to find the exact virtual path inside the archive.");
        }
        return normalized;
    }

    byte[] bytes(String path) throws IOException {
        return workspace == null ? archive.readBytes(path) : workspace.readBytes(path);
    }

    String read(String path) throws IOException {
        if (workspace == null ? !entries.containsKey(path) : !workspace.contains(path)) return null;
        if (length(path) > MAX_TEXT_BYTES) {
            throw new CliFailure("ENTRY_TOO_LARGE", "Entry exceeds the CLI text size limit: " + path,
                    "Use extract for the complete raw file.");
        }
        try {
            if (workspace != null) return workspace.readText(path, charset);
            byte[] data = archive.readBytes(path);
            if (BinaryAniCodec.isBinary(path, data)) return BinaryAniCodec.decode(data, charset);
            if (BinaryScriptCodec.isScript(data)) {
                if (stringTable == null) {
                    stringTable = new BinaryScriptCodec.StringTable(archive.readBytes("stringtable.bin"), charset);
                }
                return BinaryScriptCodec.decode(data, stringTable);
            }
            return TextCodec.decode(data, charset);
        } catch (CharacterCodingException ex) {
            throw new CliFailure("TEXT_ENCODING", "Cannot read " + path + " as " + charset.name(),
                    "Pass --charset with the archive's encoding, or use extract for raw bytes.");
        }
    }

    boolean textSizeSupported(int bytes) { return bytes <= MAX_TEXT_BYTES; }

    @Override public void close() throws IOException {
        if (workspace == null) archive.close();
        else workspace.close();
    }
}
