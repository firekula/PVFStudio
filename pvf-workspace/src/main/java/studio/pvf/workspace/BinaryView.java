package studio.pvf.workspace;

/** Bounded display for binary resources without a script projection. */
public final class BinaryView {
    private static final String PREFIX = "// 二进制只读预览（十六进制）\n";
    private BinaryView() {}
    public static boolean isView(String text) { return text.startsWith(PREFIX); }
    public static String render(byte[] bytes) {
        return render(bytes, "");
    }
    public static String render(byte[] bytes, String reason) {
        int limit = Math.min(bytes.length, 64 * 1024);
        StringBuilder text = new StringBuilder(PREFIX).append("// 共 ").append(bytes.length)
                .append(" 字节；显示前 ").append(limit).append(" 字节。此格式暂不支持文本编辑或资源预览。\n\n");
        if (!reason.isEmpty()) text.append("// ").append(reason.replace('\n', ' ').replace('\r', ' ')).append("\n\n");
        for (int offset = 0; offset < limit; offset += 16) {
            text.append(String.format(java.util.Locale.ROOT, "%08X  ", offset));
            for (int j = offset; j < Math.min(offset + 16, limit); j++)
                text.append(String.format(java.util.Locale.ROOT, "%02X ", bytes[j] & 255));
            text.append('\n');
        }
        return text.toString();
    }
}
