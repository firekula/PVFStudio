package studio.pvf.intellij;

import studio.pvf.preview.DungeonPreview;
import studio.pvf.preview.DungeonScript;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/** Bounded, in-memory layout controls; all script and pixel reads stay on the preview worker. */
final class DungeonPanel extends JPanel {
    record Selection(DungeonPreview dungeon, DungeonPreview.Layout layout, DungeonPreview.Room room,
                     DungeonPreview.MapChoice map, boolean cleared, boolean bossCondition, long seed) {
        String description() {
            String doors = String.join(" · ", Arrays.stream(DungeonScript.Direction.values()).map(d -> d.label + "："
                    + (!layout.maze().connected(room.position(), d) ? "无通路"
                    : (room.boss() || layout.maze().neighbor(room.position(), d).equals(layout.boss()) ? "Boss 门" : "普通门")
                    + (cleared && (bossCondition || !(room.boss() || layout.maze().neighbor(room.position(), d).equals(layout.boss()))) ? "开启" : "关闭"))).toList());
            return "副本布局 " + layout.maze() + " · 方案 " + seed + " · 房间 " + room.position()
                    + (room.start() ? " · 起点" : "") + (room.boss() ? " · Boss" : "") + "\n" + doors + "\n"
                    + (layout.warnings().isEmpty() ? "" : String.join("\n", layout.warnings().stream().limit(4).toList()) + "\n");
        }
    }
    private final JComboBox<String> mazes = new JComboBox<>();
    private final JComboBox<Object> starts = new JComboBox<>(), bosses = new JComboBox<>();
    private final JComboBox<DungeonPreview.MapChoice> maps = new JComboBox<>();
    private final JCheckBox cleared = new JCheckBox("房间已清理", true), condition = new JCheckBox("Boss 条件已满足", true);
    private final JButton regenerate = new JButton("换一组房间");
    private final JLabel current = new JLabel();
    private final RoomGrid grid = new RoomGrid();
    private final Consumer<Selection> changed;
    private final Map<DungeonScript.Position, Boolean> clearStates = new HashMap<>();
    private final Map<DungeonScript.Position, Integer> mapSelections = new HashMap<>();
    private DungeonPreview dungeon; private DungeonPreview.Layout layout;
    private DungeonScript.Position position; private boolean changing; private long seed;
    DungeonPanel(Consumer<Selection> changed) {
        this.changed = changed; setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        JPanel first = row(); mazes.setName("dungeon-maze"); starts.setName("dungeon-start"); bosses.setName("dungeon-boss"); maps.setName("dungeon-map");
        first.add(new JLabel("副本布局")); first.add(mazes); first.add(regenerate); add(first);
        JPanel positions = row(); positions.add(new JLabel("起点")); positions.add(starts); positions.add(new JLabel("Boss")); positions.add(bosses); add(positions);
        grid.setPreferredSize(new Dimension(300, 105)); grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105)); grid.setName("dungeon-rooms"); add(grid);
        JPanel status = row(); status.add(current); status.add(cleared); status.add(condition); add(status);
        maps.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26)); maps.setToolTipText("当前房间的有效 MAP 候选；可逐个检查不同画面"); add(maps);
        JLabel hint = new JLabel("点击房间切换预览 · 绿：起点 · 橙：Boss · 蓝：当前"); add(hint);
        regenerate.setToolTipText("更换可复现的 MAP 候选组合；起点、Boss 和布局保持所选值");
        mazes.addActionListener(e -> { if (!changing && dungeon != null) { clearStates.clear(); mapSelections.clear(); fillPositions(); rebuild(); } });
        starts.addActionListener(e -> { if (!changing) { mapSelections.clear(); rebuild(); } });
        bosses.addActionListener(e -> { if (!changing) { mapSelections.clear(); rebuild(); } });
        regenerate.addActionListener(e -> { seed++; mapSelections.clear(); rebuild(); }); maps.addActionListener(e -> {
            if (!changing) { if (maps.getSelectedItem() instanceof DungeonPreview.MapChoice map && position != null) mapSelections.put(position, map.id()); emit(); }
        });
        cleared.addActionListener(e -> { if (position != null) clearStates.put(position, cleared.isSelected()); emit(); });
        condition.addActionListener(e -> emit()); setVisible(false);
    }
    private static JPanel row() { var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1)); row.setAlignmentX(LEFT_ALIGNMENT); return row; }
    void install(DungeonPreview value) {
        int previous = Math.max(0, mazes.getSelectedIndex()); dungeon = value; changing = true; mazes.removeAllItems();
        for (int i = 0; i < dungeon.definition().mazes().size(); i++) mazes.addItem((i + 1) + " · " + dungeon.definition().mazes().get(i));
        mazes.setSelectedIndex(Math.min(previous, mazes.getItemCount() - 1)); changing = false;
        clearStates.clear(); mapSelections.clear(); condition.setVisible(value.definition().bossCondition()); condition.setSelected(true); fillPositions(); setVisible(true); rebuild();
    }
    private void fillPositions() {
        var maze = dungeon.definition().mazes().get(mazes.getSelectedIndex()); Object start = starts.getSelectedItem(), boss = bosses.getSelectedItem();
        changing = true; starts.removeAllItems(); bosses.removeAllItems();
        if (maze.starts().isEmpty()) starts.addItem("未指定"); else maze.starts().stream().distinct().forEach(starts::addItem);
        if (maze.bosses().isEmpty()) bosses.addItem("无"); else maze.bosses().stream().distinct().forEach(bosses::addItem);
        selectExisting(starts, start); selectExisting(bosses, boss); changing = false;
    }
    private static void selectExisting(JComboBox<Object> box, Object value) {
        for (int i = 0; i < box.getItemCount(); i++) if (box.getItemAt(i).equals(value)) { box.setSelectedIndex(i); return; }
    }
    private static DungeonScript.Position coordinate(Object value) { return value instanceof DungeonScript.Position p ? p : null; }
    private void rebuild() {
        if (changing || dungeon == null) return;
        layout = dungeon.arrange(mazes.getSelectedIndex(), coordinate(starts.getSelectedItem()), coordinate(bosses.getSelectedItem()), seed);
        if (position == null || layout.room(position) == null) position = layout.start();
        if (position == null || layout.room(position) == null) position = layout.rooms().isEmpty() ? null : layout.rooms().get(0).position();
        selectRoom(position);
    }
    void selectRoom(DungeonScript.Position value) {
        if (layout == null || value == null || layout.room(value) == null) return;
        position = value; var room = layout.room(position); changing = true; maps.removeAllItems(); room.choices().forEach(maps::addItem);
        if (!room.choices().isEmpty()) {
            maps.setSelectedIndex(room.selected());
            room.choices().stream().filter(m -> Objects.equals(mapSelections.get(position), m.id())).findFirst().ifPresent(maps::setSelectedItem);
        }
        changing = false;
        current.setText("房间 " + position); cleared.setSelected(clearStates.getOrDefault(position, true)); grid.repaint(); emit();
    }
    Selection selection() {
        var room = layout == null ? null : layout.room(position);
        return room == null ? null : new Selection(dungeon, layout, room, (DungeonPreview.MapChoice) maps.getSelectedItem(), cleared.isSelected(), condition.isSelected(), seed);
    }
    private void emit() { Selection selection = selection(); if (selection != null && !changing) changed.accept(selection); }
    private final class RoomGrid extends JComponent {
        RoomGrid() {
            setToolTipText(""); addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) {
                if (layout != null) selectRoom(at(e.getPoint()));
            }});
        }
        private int cell() { return layout == null ? 1 : Math.max(1, Math.min(32, Math.min(getWidth() / layout.maze().width(), getHeight() / layout.maze().height()))); }
        private int left(int cell) { return (getWidth() - cell * layout.maze().width()) / 2; }
        private DungeonScript.Position at(Point p) {
            int cell = cell(); int x = p.x - left(cell);
            return new DungeonScript.Position(x < 0 ? -1 : x / cell, p.y < 0 ? -1 : p.y / cell);
        }
        @Override public String getToolTipText(MouseEvent e) {
            if (layout == null) return null; var room = layout.room(at(e.getPoint()));
            return room == null ? null : "房间 " + room.position() + " · " + room.choices().size() + " 个 MAP 候选";
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); if (layout == null) return; Graphics2D g = (Graphics2D) graphics.create();
            try {
                int cell = cell(), ox = left(cell); g.setStroke(new BasicStroke(2));
                for (var room : layout.rooms()) {
                    int x = ox + room.position().x() * cell, y = room.position().y() * cell;
                    g.setColor(room.position().equals(position) ? new Color(70, 150, 220) : room.boss() ? new Color(190, 110, 40)
                            : room.start() ? new Color(60, 150, 95) : new Color(80, 85, 95));
                    g.fillRect(x + 3, y + 3, Math.max(1, cell - 6), Math.max(1, cell - 6));
                    g.setColor(room.choices().isEmpty() ? Color.RED : new Color(180, 195, 210));
                    g.drawRect(x + 3, y + 3, Math.max(1, cell - 6), Math.max(1, cell - 6));
                    for (var d : DungeonScript.Direction.values()) if (layout.maze().connected(room.position(), d))
                        g.drawLine(x + cell / 2, y + cell / 2, x + cell / 2 + d.dx * cell / 2, y + cell / 2 + d.dy * cell / 2);
                    if (cell >= 24 && (room.start() || room.boss())) { g.setColor(Color.WHITE); g.drawString(room.boss() ? "B" : "S", x + 8, y + cell - 8); }
                }
            } finally { g.dispose(); }
        }
    }
}
