package studio.pvf.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/** Verified, independent version backups before publishing an editor export. */
public final class PvfBackupExport {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private PvfBackupExport() {}

    public record Result(Path output, Path backup) {}

    public static Result export(PvfWorkspace workspace, Path output) throws IOException {
        Path requested = output.toAbsolutePath().normalize();
        if (requested.getParent() == null || requested.getFileName() == null) {
            throw new IOException("请选择一个 PVF 导出文件。");
        }
        Path target = requested.getParent().toRealPath().resolve(requested.getFileName());
        Path source = workspace.archive().sourcePath();
        boolean replacing = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (target.equals(source) || replacing && Files.isSameFile(target, source)) {
            throw new IOException("原包保持只读，请选择另一个导出文件；原包会自动备份。");
        }
        if (replacing && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("导出目标必须是普通文件，不能是目录或符号链接。");
        }
        workspace.archive().verifyUnchanged();
        Path previous = replacing ? target : source;
        String previousDigest = replacing ? sha256(previous) : workspace.archive().sha256();
        Path backup = backup(previous, target, previousDigest);
        Path staging = Files.createTempDirectory(target.getParent(), ".pvf-version-export-");
        Path verified = staging.resolve("verified.pvf");
        try {
            workspace.export(verified);
            if (replacing) {
                // Do not discard another program's changes made during a long export.
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                        || !previousDigest.equals(sha256(target))) {
                    throw new IOException("导出期间目标文件已变化，已停止更新；备份保存在：" + backup);
                }
                try {
                    Files.move(verified, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException failure) {
                    throw new IOException("目标文件系统不支持安全替换，旧文件和备份均已保留，请导出到新文件。", failure);
                }
            } else {
                // A destination created after the initial check must never be overwritten.
                Files.move(verified, target);
            }
            return new Result(target, backup);
        } catch (IOException failure) {
            throw new IOException(failure.getMessage() + "\n旧版本备份已保留：" + backup, failure);
        } finally {
            Files.deleteIfExists(verified);
            Files.deleteIfExists(staging);
        }
    }

    private static Path backup(Path previous, Path target, String expectedDigest) throws IOException {
        Path directory = target.getParent().resolve("backups");
        Files.createDirectories(directory);
        String name = target.getFileName().toString();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".pvf")) name = name.substring(0, name.length() - 4);
        Path backup = directory.resolve(name + ".before-export-" + STAMP.format(LocalDateTime.now())
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ".pvf");
        Path temporary = Files.createTempFile(directory, ".pvf-backup-", ".tmp");
        try {
            Files.copy(previous, temporary, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            if (!expectedDigest.equals(sha256(temporary)) || !expectedDigest.equals(sha256(previous))) {
                throw new IOException("旧版本在备份期间发生变化或备份校验失败，导出已停止。");
            }
            Files.move(temporary, backup);
            return backup;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int count;
                while ((count = stream.read(buffer)) >= 0) if (count != 0) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }
}
