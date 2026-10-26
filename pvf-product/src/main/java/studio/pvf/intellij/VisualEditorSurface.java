package studio.pvf.intellij;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;

/** Stable tab identity; switching never creates, disposes or edits a code editor. */
final class VisualEditorSurface extends JPanel {
    enum Mode { CODE, SPLIT, DESIGN }
    private final JComponent code;
    private JComponent visual;
    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));
    private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final EnumMap<Mode, JToggleButton> buttons = new EnumMap<>(Mode.class);
    private final Runnable changed;
    private Mode mode = Mode.CODE;
    private double ratio = .5;
    VisualEditorSurface(JComponent code, JComponent visual, Runnable changed) {
        super(new BorderLayout()); this.code = code; this.visual = visual; this.changed = changed;
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2)); bar.setName("scene-view-toolbar");
        ButtonGroup group = new ButtonGroup(); String[] titles = {"代码", "分屏", "设计"};
        for (Mode item : Mode.values()) {
            JToggleButton button = new JToggleButton(titles[item.ordinal()]); button.setName("scene-mode-" + item.name().toLowerCase());
            group.add(button); bar.add(button); buttons.put(item, button); button.addActionListener(e -> setMode(item));
        }
        split.setResizeWeight(.5); split.setBorder(null);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (mode == Mode.SPLIT && split.isShowing() && split.getWidth() > 0) {
                ratio = Math.clamp((double)split.getDividerLocation() / split.getWidth(), .1, .9); changed.run();
            }
        });
        JPanel toolbar=new JPanel(new BorderLayout()); toolbar.add(tools); toolbar.add(bar,BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH); add(body); setMode(Mode.CODE);
    }
    JComponent code() { return code; }
    JComponent visual() { return visual; }
    Mode mode() { return mode; }
    double ratio() { return ratio; }
    void action(String title,String name,Runnable run) {
        for(Component c:tools.getComponents())if(name.equals(c.getName()))tools.remove(c);
        JButton button=new JButton(title); button.setName(name); button.addActionListener(e -> run.run()); tools.add(button); tools.revalidate();
    }
    void setVisual(JComponent component) { visual = component; setMode(mode); }
    void setMode(Mode next) {
        mode = next; split.setLeftComponent(null); split.setRightComponent(null); body.removeAll();
        switch (mode) {
            case CODE -> body.add(code);
            case DESIGN -> body.add(visual);
            case SPLIT -> { split.setLeftComponent(code); split.setRightComponent(visual); body.add(split); split.setDividerLocation(ratio); }
        }
        buttons.get(mode).setSelected(true); revalidate(); repaint(); changed.run();
    }
    void restore(String value, double splitRatio) {
        ratio = Double.isFinite(splitRatio) ? Math.clamp(splitRatio, .1, .9) : .5;
        try { setMode(Mode.valueOf(value)); } catch (IllegalArgumentException | NullPointerException ignored) { setMode(Mode.CODE); }
    }
    void focusContent() { (mode == Mode.CODE ? code : visual).requestFocusInWindow(); }
}
