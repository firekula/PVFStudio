package studio.pvf.preview;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** ACT1 map/tile layout; pass cells are 16 by 12 world pixels, below the 120-pixel image header. */
public final class SceneScript {
    public static final int CELL = 16, COLUMNS = 14, TILE_WIDTH = CELL * COLUMNS;
    public static final int FLOOR_STAGE = 7;
    private SceneScript() {}

    public static Tile tile(String text) throws IOException {
        List<ResourceScript.Frame> images = new ArrayList<>(); List<Integer> passes = new ArrayList<>();
        List<String> warnings = new ArrayList<>(); boolean extended = false, passSeen = false; int imageOffset = 0;
        for (var section : ResourceScript.sections(text)) {
            switch (section.tag()) {
                case "image" -> {
                    String path = value(section, 0); int index = integer(section, 1);
                    if (!path.isEmpty() && index < 0) throw error(section, "IMG 帧号不能为负数");
                    images.add(ResourceScript.Frame.still(path, index));
                }
                case "tile type" -> {
                    String type = flag(value(section, 0));
                    if (!type.equals("basic") && !type.equals("extended")) throw error(section, "未知地块类型：" + type);
                    extended = type.equals("extended");
                }
                case "img pos" -> {
                    imageOffset = integer(section, 0);
                    if (Math.abs((long) imageOffset) > 1_000_000) throw error(section, "地块图片偏移超出范围");
                }
                case "pass type" -> {
                    if (passSeen) throw error(section, "重复的 pass type");
                    passSeen = true;
                    for (int i = 0; i < section.values().size(); i++) {
                        int type = integer(section, i);
                        if (type < 0 || type > 2) throw error(section, "通行类型须为 0、1 或 2");
                        passes.add(type);
                    }
                }
                default -> { }
            }
        }
        if (images.size() > 256) throw new IOException("TIL 图片超过 256 个");
        if (passes.size() % COLUMNS != 0 || passes.size() > COLUMNS * 30) throw new IOException("pass type 须按 14 列排列，最多 30 行");
        if (passes.isEmpty()) warnings.add("没有配置 pass type，不推断通行状态");
        else if (extended && passes.size() > COLUMNS * 10) warnings.add("extended 通行格超过 10 行，多余行未用于通行格");
        return new Tile(List.copyOf(images), List.copyOf(passes), extended, List.copyOf(warnings), imageOffset);
    }

    public static MapLayout map(String text) throws IOException {
        List<String> tiles = new ArrayList<>(), extended = new ArrayList<>();
        List<Placement> animations = new ArrayList<>(); List<Rectangle2D.Double> movable = new ArrayList<>();
        double far = 1, middle = 1, near = 1; boolean town = false;
        for (var section : mapSections(text)) {
            switch (section.tag()) {
                case "tile", "extended tile" -> {
                    List<String> target = section.tag().equals("tile") ? tiles : extended;
                    for (String path : section.values()) {
                        if (!path.toLowerCase(Locale.ROOT).endsWith(".til")) throw error(section, "需要 .til 路径：" + path);
                        target.add(path);
                    }
                }
                case "animation", "background animation" -> {
                    boolean repeat = section.tag().equals("background animation"); int stride = repeat ? 3 : 5;
                    if (section.values().size() % stride != 0) throw error(section, "动画参数不完整");
                    for (int i = 0; i < section.values().size(); i += stride) {
                        String path = value(section, i), layer = flag(value(section, i + 1));
                        if (!path.toLowerCase(Locale.ROOT).endsWith(".ani")) throw error(section, "需要 .ani 路径：" + path);
                        if (!List.of("distantback", "middleback", "closeback", "bottom", "normal", "close").contains(layer))
                            throw error(section, "未知动画层：" + layer);
                        String side = repeat ? flag(value(section, i + 2)) : "";
                        if (repeat && !side.equals("below") && !side.equals("upper")) throw error(section, "背景顺序须为 below 或 upper");
                        animations.add(new Placement(path, layer, repeat, side.equals("upper"),
                                repeat ? 0 : number(section, i + 2), repeat ? 0 : number(section, i + 3),
                                repeat ? 0 : number(section, i + 4)));
                    }
                }
                case "virtual movable area" -> {
                    town |= !section.values().isEmpty();
                    if (section.values().size() % 4 != 0) throw error(section, "移动区域需要 x、y、宽、高");
                    for (int i = 0; i < section.values().size(); i += 4) {
                        double width = number(section, i + 2), height = number(section, i + 3);
                        if (width < 0 || height < 0) throw error(section, "移动区域尺寸不能为负数");
                        movable.add(new Rectangle2D.Double(number(section, i), number(section, i + 1), width, height));
                    }
                }
                case "town movable area" -> town |= !section.values().isEmpty();
                case "far sight scroll" -> far = number(section, 0) / 100;
                case "middle sight scroll" -> middle = number(section, 0) / 100;
                case "near sight scroll" -> near = number(section, 0) / 100;
                default -> { }
            }
        }
        if (tiles.size() + extended.size() > 4096 || animations.size() > 4096 || movable.size() > 4096)
            throw new IOException("地图地块、动画或区域数量超出预览上限 4096");
        if (tiles.isEmpty()) throw new IOException("MAP 没有基础地块，无法确定扩展地块的行宽");
        if (far < 0 || middle < 0 || near < 0 || far > 10 || middle > 10 || near > 10)
            throw new IOException("地图滚动比例超出预览范围 0–1000%");
        return new MapLayout(List.copyOf(tiles), List.copyOf(extended), List.copyOf(animations), List.copyOf(movable), far, middle, near, town);
    }

    public static int stage(Placement placement) {
        int base = switch (placement.layer()) {
            case "distantback" -> 0;
            case "middleback" -> 3;
            case "closeback" -> 6;
            case "bottom" -> 11;
            case "normal" -> 14;
            case "close" -> 17;
            default -> throw new IllegalArgumentException(placement.layer());
        };
        // 0x463940 draws the floor in closeback/below, before closeback objects and upper backgrounds.
        if (placement.layer().equals("closeback"))
            return base + (placement.repeat() ? (placement.upper() ? 3 : 0) : 2);
        return placement.repeat() ? base + (placement.upper() ? 2 : 0) : base + 1;
    }
    /** 0x4C53D0 calls the stable Y sort (0x4C5230) only for native layer 1, normal. */
    public static double sortDepth(int stage, double y) { return stage == 15 ? y : 0; }
    /** Newer maps wrap a background in ani info rather than storing three flat values. */
    private static List<ResourceScript.Section> mapSections(String text) throws IOException {
        List<ResourceScript.Section> result = new ArrayList<>();
        boolean background = false, info = false;
        String filename = null, layer = null, order = null;
        int line = 0;
        for (var section : ResourceScript.sections(text)) {
            if (section.tag().equals("background animation")) {
                background = true; result.add(section); continue;
            }
            if (section.tag().equals("/background animation")) {
                if (info) throw error(section, "ani info 未结束");
                background = false; result.add(section); continue;
            }
            if (background && section.tag().equals("ani info")) {
                if (info) throw error(section, "重复的 ani info");
                info = true; filename = layer = order = null; line = section.line(); continue;
            }
            if (info) {
                switch (section.tag()) {
                    case "filename" -> filename = value(section, 0);
                    case "layer" -> layer = value(section, 0);
                    case "order" -> order = value(section, 0);
                    case "/ani info" -> {
                        if (filename == null || layer == null || order == null) throw error(section, "ani info 缺少 filename、layer 或 order");
                        result.add(new ResourceScript.Section("background animation", List.of(filename, layer, order), line, List.of(line, line, line)));
                        info = false;
                    }
                    default -> { }
                }
            } else result.add(section);
        }
        if (info) throw new IOException("MAP 的 ani info 未结束");
        return result;
    }
    public static String flag(String value) { return ScriptDialect.flag(value); }
    private static String value(ResourceScript.Section section, int index) throws IOException {
        if (index >= section.values().size()) throw error(section, "参数不完整");
        return section.values().get(index);
    }
    private static int integer(ResourceScript.Section section, int index) throws IOException {
        try { return Integer.parseInt(value(section, index)); }
        catch (NumberFormatException failure) { throw error(section, "需要整数"); }
    }
    private static double number(ResourceScript.Section section, int index) throws IOException {
        try {
            double value = Double.parseDouble(value(section, index));
            if (!Double.isFinite(value) || Math.abs(value) > 1_000_000) throw error(section, "坐标或比例超出范围");
            return value;
        } catch (NumberFormatException failure) { throw error(section, "需要数值"); }
    }
    private static IOException error(ResourceScript.Section section, String message) {
        return new IOException("第 " + section.line() + " 行 [" + section.tag() + "]：" + message);
    }
    public record Tile(List<ResourceScript.Frame> images, List<Integer> passes, boolean extended, List<String> warnings, int imageOffset) {
        // Newer basic IMG canvases include img pos extra pixels above the ACT1 floor origin.
        // Extended images are still 120-pixel strips; their origin is the previous row's bottom.
        public int imageY() { return extended ? 0 : -imageOffset; }
        public int height() { return worldHeight() + (extended ? 0 : SceneCoordinates.ORIGIN_Y); }
        public int worldHeight() { return (passes.isEmpty() ? (extended ? 10 : 30) : rows()) * SceneCoordinates.CELL_HEIGHT; }
        public int rows() { return Math.min(extended ? 10 : 30, passes.size() / COLUMNS); }
        public int pass(int column, int row) { return passes.get(row * COLUMNS + column); }
    }
    public record Placement(String path, String layer, boolean repeat, boolean upper, double x, double y, double z) {}
    public record MapLayout(List<String> tiles, List<String> extended, List<Placement> animations,
                            List<Rectangle2D.Double> movable, double farScroll, double middleScroll, double nearScroll, boolean town) {}
}
