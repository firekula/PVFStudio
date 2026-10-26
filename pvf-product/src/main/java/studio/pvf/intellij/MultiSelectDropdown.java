package studio.pvf.intellij;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** A fixed-height field; only its searchable choice popup scrolls. Refreshes never emit edits. */
final class MultiSelectDropdown<T> extends JPanel {
    private final JButton button=new JButton();
    private final JPopupMenu popup=new JPopupMenu();
    private final JTextField search=new JTextField();
    private final DefaultListModel<T> rows=new DefaultListModel<>();
    private final JList<T> list=new JList<>(rows);
    private Map<T,String> options=Map.of();
    private List<T> selected=List.of();
    private Consumer<List<T>> changed=values -> {};

    MultiSelectDropdown() {
        super(new BorderLayout());add(button);button.setHorizontalAlignment(SwingConstants.LEFT);
        setPreferredSize(new Dimension(260,28));setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
        setMinimumSize(new Dimension(100,26));
        search.putClientProperty("JTextField.placeholderText","搜索编号、名称或坐标");
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}});
        list.setCellRenderer((owner,value,index,active,focus) -> {
            JCheckBox cell=new JCheckBox(label(value),selected.contains(value));cell.setOpaque(true);
            cell.setBackground(active?owner.getSelectionBackground():owner.getBackground());cell.setForeground(active?owner.getSelectionForeground():owner.getForeground());cell.setBorder(BorderFactory.createEmptyBorder(3,4,3,4));return cell;
        });
        list.addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){int i=list.locationToIndex(e.getPoint());if(i>=0&&list.getCellBounds(i,i).contains(e.getPoint()))toggle(rows.get(i));}});
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"toggle");
        list.getActionMap().put("toggle",new AbstractAction(){public void actionPerformed(ActionEvent e){if(list.getSelectedValue()!=null)toggle(list.getSelectedValue());}});
        JScrollPane scroll=new JScrollPane(list);scroll.setPreferredSize(new Dimension(340,230));
        JPanel body=new JPanel(new BorderLayout(4,4));body.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));body.add(search,BorderLayout.NORTH);body.add(scroll);
        JButton clear=new JButton("清空候选");clear.addActionListener(e -> commit(List.of()));
        JButton done=new JButton("完成");done.addActionListener(e -> popup.setVisible(false));JPanel footer=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));footer.add(clear);footer.add(done);body.add(footer,BorderLayout.SOUTH);popup.add(body);
        button.addActionListener(e -> {if(popup.isVisible())popup.setVisible(false);else{search.setText("");filter();popup.show(button,0,button.getHeight());search.requestFocusInWindow();}});
        updateSummary();
    }
    void options(Map<T,String> values){options=Collections.unmodifiableMap(new LinkedHashMap<>(values));filter();updateSummary();}
    void selected(List<T> values){selected=List.copyOf(new LinkedHashSet<>(values));filter();updateSummary();}
    List<T> selected(){return selected;}
    void onChange(Consumer<List<T>> listener){changed=listener;}
    void toggle(T value){if(!isEnabled())return;var next=new ArrayList<>(selected);if(!next.remove(value))next.add(value);commit(next);}
    private void commit(List<T> values){if(!isEnabled()||selected.equals(values))return;selected(values);changed.accept(selected);}
    @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);if(button!=null)button.setEnabled(enabled);if(!enabled&&popup!=null)popup.setVisible(false);}
    private String label(T value){return options.getOrDefault(value,value+" · 未登记");}
    private void filter(){
        String needle=search.getText().trim().toLowerCase(Locale.ROOT);T active=list.getSelectedValue();rows.clear();
        var all=new LinkedHashSet<T>(options.keySet());all.addAll(selected);
        for(T value:all)if(label(value).toLowerCase(Locale.ROOT).contains(needle))rows.addElement(value);
        if(active!=null)list.setSelectedValue(active,false);list.repaint();
    }
    private void updateSummary(){
        String text=selected.isEmpty()?"选择候选…":selected.size()+" 项 · "+String.join(", ",selected.stream().limit(3).map(Object::toString).toList())+(selected.size()>3?"…":"");
        button.setText(text+"  ▾");button.setToolTipText(selected.isEmpty()?"展开后可搜索并勾选多项":String.join("；",selected.stream().limit(40).map(this::label).toList()));
    }
}
