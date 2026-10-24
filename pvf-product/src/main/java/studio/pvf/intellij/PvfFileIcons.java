package studio.pvf.intellij;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;
import java.util.Locale;

/** Small vector icons for the PVF archive's virtual file types. */
final class PvfFileIcons {
    private PvfFileIcons() {}
    static Icon forEntry(String name, boolean directory, boolean expanded) {
        String type;
        if (directory) type = expanded ? "folder-open" : "folder";
        else {
            int dot = name.lastIndexOf('.');
            String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
            type = switch (extension) {
                case "pvf" -> "archive";
                case "ai", "aic", "nut", "lua", "js", "py" -> "script";
                case "lst" -> "list";
                case "str", "txt", "md" -> "text";
                case "key", "cfg", "ini", "xml", "json", "etc" -> "config";
                case "stk", "cre", "pet" -> "item";
                case "equ" -> "equipment";
                case "skl", "ani", "act" -> "skill";
                case "chr", "npc", "mob" -> "character";
                case "png", "jpg", "jpeg", "bmp", "dds", "img" -> "image";
                case "wav", "ogg", "mp3" -> "sound";
                default -> "file";
            };
        }
        return IconLoader.getIcon("/icons/pvf/" + type + ".svg", PvfFileIcons.class);
    }
}
