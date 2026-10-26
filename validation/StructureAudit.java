import studio.pvf.workspace.PvfWorkspace;
import java.nio.file.*;
import java.util.Arrays;

/** Isolated real-container create/rename/delete/export audit. Never modifies the source archive. */
public class StructureAudit {
    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("StructureAudit <source PVF> <new output directory>");
        Path output=Path.of(args[1]);Files.createDirectory(output);Path drafts=output.resolve("drafts");
        byte[] payload={65,66,33};String renamed,removed;
        try(var workspace=PvfWorkspace.open(Path.of(args[0]),drafts)) {
            var chosen=workspace.archive().entries().stream().filter(e->e.length()<1024*1024).limit(2).toList();
            renamed=chosen.get(0).path();removed=chosen.get(1).path();
            workspace.rename(renamed,"pvfstudio_test/renamed.etc"); workspace.delete(removed);
            workspace.createDirectory("pvfstudio_test/empty"); workspace.createFile("pvfstudio_test/new.bin",payload);
        }
        try(var workspace=PvfWorkspace.open(Path.of(args[0]),drafts)) {
            if(workspace.contains(renamed)||workspace.contains(removed)||!workspace.contains("pvfstudio_test/new.bin")||!workspace.folders().contains("pvfstudio_test/empty")) throw new AssertionError("Manifest did not restore");
            if(!Arrays.equals(workspace.archive().readBytes(renamed),workspace.readBytes("pvfstudio_test/renamed.etc"))) throw new AssertionError("Rename lost content");
            workspace.export(output.resolve("edited.pvf"));
        }
        Files.write(output.resolve("new.bin"),payload);
        Files.writeString(output.resolve("plan.json"),"{\"rename\":{"+quote(renamed)+":\"pvfstudio_test/renamed.etc\"},\"remove\":["+quote(removed)+"],\"add\":{\"pvfstudio_test/new.bin\":\"new.bin\"}}\n");
        System.out.println("PASS: "+args[0]+" -> "+output);
    }
    private static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
}
