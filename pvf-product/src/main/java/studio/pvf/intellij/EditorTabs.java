package studio.pvf.intellij;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Stable visual order, explicit moves, and a separate most-recently-used switching history. */
final class EditorTabs extends JTabbedPane {
    private final List<Component> recent = new ArrayList<>();
    private List<Component> cycle;
    private int cycleIndex, batch;
    private Consumer<Component> close = ignored -> {};
    private Function<Component, JPopupMenu> menu = ignored -> new JPopupMenu();
    private Point dragStart;
    private Component dragged;
    private final MouseAdapter mouse = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent e) {
            finishCycle();
            Point point = point(e); int index = indexAtLocation(point.x, point.y);
            if (index < 0) return;
            if (e.isPopupTrigger()) showMenu(e, index);
            else if (SwingUtilities.isLeftMouseButton(e)) {
                setSelectedIndex(index); dragStart = point; dragged = getComponentAt(index);
            }
        }
        @Override public void mouseReleased(MouseEvent e) {
            Point point = point(e); int index = indexAtLocation(point.x, point.y);
            if (index >= 0 && e.isPopupTrigger()) showMenu(e, index);
            else if (index >= 0 && SwingUtilities.isMiddleMouseButton(e)) close.accept(getComponentAt(index));
            else if (dragged != null && dragStart != null && index >= 0 && dragStart.distance(point) > 6)
                moveTo(dragged, index);
            dragged = null; dragStart = null;
        }
        @Override public void mouseDragged(MouseEvent e) { /* The drop commits once; selection never reorders tabs. */ }
        private Point point(MouseEvent e) { return SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), EditorTabs.this); }
        private void showMenu(MouseEvent e, int index) {
            Point point = point(e); menuFor(getComponentAt(index)).show(EditorTabs.this, point.x, point.y);
        }
    };

    EditorTabs() {
        super(TOP, SCROLL_TAB_LAYOUT);
        setFocusTraversalKeysEnabled(false);
        addMouseListener(mouse); addMouseMotionListener(mouse);
        addChangeListener(e -> {
            if (cycle == null) remember(getSelectedComponent());
            for (int i = 0; i < getTabCount(); i++) if (getTabComponentAt(i) instanceof Header header)
                header.label.setForeground(i == getSelectedIndex() ? getForegroundAt(i) : UIManager.getColor("Label.foreground"));
        });
    }
    @Override public void updateUI() {
        // Darcula's WrappingLayout recurses when validating scrolling custom headers.
        // The Swing scroll delegate retains the current theme's defaults and supports a real single row.
        setUI(new ScrollUI());
    }
    private static final class ScrollUI extends BasicTabbedPaneUI {
        @Override protected void installDefaults() {
            super.installDefaults(); tabInsets = new Insets(5, 6, 5, 6); selectedTabPadInsets = new Insets(0, 0, 0, 0);
            tabAreaInsets = new Insets(0, 0, 0, 0); contentBorderInsets = new Insets(0, 0, 0, 0);
        }
        private Color color(String key, Color fallback) { Color color = UIManager.getColor(key); return color == null ? fallback : color; }
        @Override protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int width, int height, boolean selected) {
            g.setColor(color(selected ? "EditorPane.background" : "Panel.background", tabPane.getBackground())); g.fillRect(x, y, width, height);
            if (selected) { g.setColor(color("Component.focusColor", new Color(53, 116, 240))); g.fillRect(x, y + height - 2, width, 2); }
        }
        @Override protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int width, int height, boolean selected) {
            g.setColor(color("Separator.foreground", tabPane.getBackground().darker())); g.drawLine(x + width - 1, y + 4, x + width - 1, y + height - 4);
        }
        @Override protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int index, Rectangle icon, Rectangle text, boolean selected) {}
        @Override protected void paintContentBorder(Graphics g, int placement, int selected) {}
        @Override protected JButton createScrollButton(int direction) {
            Color bg = color("Panel.background", Color.DARK_GRAY), fg = color("Label.foreground", Color.LIGHT_GRAY);
            JButton button = new ScrollButton(direction, bg, fg); button.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2)); button.setFocusable(false); return button;
        }
    }
    private static final class ScrollButton extends BasicArrowButton implements javax.swing.plaf.UIResource {
        ScrollButton(int direction, Color background, Color foreground) { super(direction, background, background, foreground, background); }
    }
    void onClose(Consumer<Component> action) { close = action; }
    void onMenu(Function<Component, JPopupMenu> action) { menu = action; }
    JPopupMenu menuFor(Component component) { return menu.apply(component); }
    void decorate(Component component, Icon icon) {
        int index = indexOfComponent(component);
        if (index >= 0) setTabComponentAt(index, new Header(component, getTitleAt(index), icon));
    }
    @Override public void setTitleAt(int index, String title) {
        super.setTitleAt(index, title);
        if (getTabComponentAt(index) instanceof Header header) header.label.setText(title);
    }
    @Override public void removeTabAt(int index) {
        Component removed = getComponentAt(index); recent.remove(removed);
        super.removeTabAt(index);
    }
    @Override public void removeAll() { finishCycle(); recent.clear(); super.removeAll(); }
    private void remember(Component component) {
        if (component == null) return;
        recent.remove(component); recent.addFirst(component);
    }
    void finishCycle() { cycle = null; remember(getSelectedComponent()); }
    void cycleRecent(int direction) {
        if (getTabCount() < 2) return;
        if (cycle == null) {
            cycle = new ArrayList<>(recent.stream().filter(c -> indexOfComponent(c) >= 0).toList());
            for (int i = 0; i < getTabCount(); i++) if (!cycle.contains(getComponentAt(i))) cycle.add(getComponentAt(i));
            cycleIndex = Math.max(0, cycle.indexOf(getSelectedComponent()));
        }
        cycleIndex = Math.floorMod(cycleIndex + direction, cycle.size()); setSelectedComponent(cycle.get(cycleIndex));
    }
    void selectRelative(int direction) {
        finishCycle(); if (getTabCount() > 0) setSelectedIndex(Math.floorMod(getSelectedIndex() + direction, getTabCount()));
    }
    void moveRelative(Component component, int direction) { moveTo(component, indexOfComponent(component) + direction); }
    void moveTo(Component component, int target) {
        finishCycle(); int from = indexOfComponent(component);
        if (from < 0 || target < 0 || target >= getTabCount() || from == target) return;
        String title = getTitleAt(from), tooltip = getToolTipTextAt(from); Icon icon = getIconAt(from);
        Component header = getTabComponentAt(from), selected = getSelectedComponent();
        boolean enabled = isEnabledAt(from); List<Component> history = List.copyOf(recent);
        runBatch(() -> {
            removeTabAt(from); insertTab(title, icon, component, tooltip, target);
            setTabComponentAt(target, header); setEnabledAt(target, enabled); setSelectedComponent(selected);
        });
        recent.clear(); recent.addAll(history);
    }
    Component preferredAfterClosing(Collection<Component> closed) {
        if (!closed.contains(getSelectedComponent())) return getSelectedComponent();
        return recent.stream().filter(c -> !closed.contains(c) && indexOfComponent(c) >= 0).findFirst().orElse(null);
    }
    void runBatch(Runnable action) {
        batch++;
        try { action.run(); } finally { if (--batch == 0) fireStateChanged(); }
    }
    @Override protected void fireStateChanged() { if (batch == 0) super.fireStateChanged(); }

    private final class Header extends JPanel {
        final JLabel label;
        Header(Component component, String title, Icon icon) {
            super(new FlowLayout(FlowLayout.LEFT, 5, 0)); setOpaque(false);
            label = new JLabel(title, icon, SwingConstants.LEFT); add(label);
            label.setToolTipText(getToolTipTextAt(indexOfComponent(component)));
            JButton button = new JButton() {
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    try {
                        g.setColor(getModel().isRollover() ? new Color(240, 100, 100) : label.getForeground());
                        g.setStroke(new BasicStroke(1.5f)); int x = getWidth() / 2, y = getHeight() / 2;
                        g.drawLine(x - 3, y - 3, x + 3, y + 3); g.drawLine(x + 3, y - 3, x - 3, y + 3);
                    } finally { g.dispose(); }
                }
            };
            button.setPreferredSize(new Dimension(18, 18)); button.setFocusable(false); button.setContentAreaFilled(false);
            button.setBorderPainted(false); button.setRolloverEnabled(true); button.setToolTipText("关闭标签页 (Ctrl+W)");
            button.getAccessibleContext().setAccessibleName("关闭 " + title);
            button.addActionListener(e -> close.accept(component)); add(button);
            addMouseListener(mouse); addMouseMotionListener(mouse); label.addMouseListener(mouse); label.addMouseMotionListener(mouse);
        }
    }
}
