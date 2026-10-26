import studio.pvf.core.PvfArchive;
import studio.pvf.workspace.BinaryAniCodec;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.TreeMap;

/** Read-only corpus audit: every supported binary ANI must recompile byte-for-byte. */
public class BinaryAniAudit {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("BinaryAniAudit <archive> <new-report-directory> [charset=Big5]");
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        Path output = Path.of(args[1]); Files.createDirectory(output);
        Charset charset = Charset.forName(args.length == 3 ? args[2] : "Big5");
        var errors = new TreeMap<String, Integer>();
        int supported = 0, other = 0, unsupported = 0, mismatches = 0; long frames = 0;
        Properties report = new Properties();
        try (var archive = PvfArchive.open(Path.of(args[0]))) {
            report.setProperty("archive.sha256", archive.sha256());
            for (var entry : archive.entries()) {
                if (!entry.path().endsWith(".ani")) continue;
                byte[] bytes = archive.readBytes(entry);
                if (!BinaryAniCodec.isBinary(entry.path(), bytes)) { other++; continue; }
                try {
                    String text = BinaryAniCodec.decode(bytes, charset);
                    byte[] compiled = BinaryAniCodec.encode(text, bytes, charset);
                    if (!Arrays.equals(bytes, compiled)) {
                        if (++mismatches <= 20) report.setProperty("mismatch." + mismatches, entry.path());
                    }
                    supported++; frames += (bytes[0] & 255) | ((bytes[1] & 255) << 8);
                } catch (BinaryAniCodec.FormatException unsupportedFormat) {
                    unsupported++; String key = unsupportedFormat.getMessage().replaceAll("偏移 [0-9]+", "偏移...");
                    errors.merge(key, 1, Integer::sum);
                    if (unsupported <= 20) report.setProperty("unsupported." + unsupported, entry.path() + " : " + unsupportedFormat.getMessage());
                }
            }
        }
        report.setProperty("status", mismatches == 0 ? "PASS" : "FAIL");
        report.setProperty("charset", charset.name()); report.setProperty("supported", Integer.toString(supported));
        report.setProperty("frames", Long.toString(frames)); report.setProperty("otherOrEmpty", Integer.toString(other));
        report.setProperty("unsupported", Integer.toString(unsupported)); report.setProperty("mismatches", Integer.toString(mismatches));
        int group = 0;
        for (var error : errors.entrySet()) report.setProperty("errorGroup." + group++, error.getValue() + " : " + error.getKey());
        try (var stream = Files.newOutputStream(output.resolve("audit.properties"))) { report.store(stream, "Real ANI binary roundtrip audit; unsupported content is not rewritten"); }
        System.out.println("supported=" + supported + " frames=" + frames + " other/empty=" + other + " unsupported=" + unsupported + " mismatches=" + mismatches);
        if (mismatches > 0) throw new AssertionError("Supported ANI roundtrip changed original bytes");
    }
}
