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
    private final JPanel popup=new JPanel(new BorderLayout());
    private Runnable closePopup;
    private boolean popupVisible;
    private final JTextField search=new JTextField();
    private final DefaultListModel<T> rows=new DefaultListModel<>();
    private final JList<T> list=new JList<>(rows);
    private Map<T,String> options=Map.of();
    private Map<T,String> existingLabels=Map.of();
    private List<T> selected=List.of();
    private Set<T> selectedSet=Set.of();
    private Consumer<List<T>> changed=values -> {};
    private Consumer<List<T>> committed=values -> {};
    private boolean pending;
    private long revision;

    MultiSelectDropdown() {
        super(new BorderLayout());add(button);button.setHorizontalAlignment(SwingConstants.LEFT);
        setPreferredSize(new Dimension(260,28));setMaximumSize(new Dimension(Integer.MAX_VALUE,28));
        setMinimumSize(new Dimension(100,26));
        search.putClientProperty("JTextField.placeholderText","搜索编号、名称或坐标");
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}});
        list.setFixedCellHeight(26);list.setFixedCellWidth(310);
        JCheckBox cell=new JCheckBox();cell.setOpaque(true);cell.setBorder(BorderFactory.createEmptyBorder(3,4,3,4));
        list.setCellRenderer((owner,value,index,active,focus) -> {
            cell.setText(label(value));cell.setSelected(selectedSet.contains(value));cell.setFont(owner.getFont());
            cell.setBackground(active?owner.getSelectionBackground():owner.getBackground());cell.setForeground(active?owner.getSelectionForeground():owner.getForeground());return cell;
        });
        list.addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){int i=list.locationToIndex(e.getPoint());if(i>=0&&list.getCellBounds(i,i).contains(e.getPoint()))toggle(rows.get(i));}});
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"toggle");
        list.getActionMap().put("toggle",new AbstractAction(){public void actionPerformed(ActionEvent e){if(list.getSelectedValue()!=null)toggle(list.getSelectedValue());}});
        JScrollPane scroll=new JScrollPane(list);scroll.setPreferredSize(new Dimension(340,230));
        scroll.setWheelScrollingEnabled(false);scroll.getVerticalScrollBar().setUnitIncrement(26);scroll.getVerticalScrollBar().setBlockIncrement(208);
        MouseWheelListener wheel=e -> {if(e.isConsumed())return;var bar=scroll.getVerticalScrollBar();double amount=e.getScrollType()==MouseWheelEvent.WHEEL_BLOCK_SCROLL?bar.getBlockIncrement():26.0*Math.max(1,e.getScrollAmount());bar.setValue(bar.getValue()+(int)Math.round(e.getPreciseWheelRotation()*amount));e.consume();};
        list.addMouseWheelListener(wheel);search.addMouseWheelListener(wheel);scroll.addMouseWheelListener(wheel);scroll.getVerticalScrollBar().addMouseWheelListener(wheel);popup.addMouseWheelListener(wheel);
        JPanel body=new JPanel(new BorderLayout(4,4));body.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));body.add(search,BorderLayout.NORTH);body.add(scroll);
        body.addMouseWheelListener(wheel);
        JButton clear=new JButton("清空候选");clear.addActionListener(e -> commit(List.of()));
        JButton done=new JButton("完成");done.addActionListener(e -> hidePopup());JPanel footer=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));footer.add(clear);footer.add(done);body.add(footer,BorderLayout.SOUTH);popup.add(body);popup.setName("multi-select-popup");
        button.addActionListener(e -> {if(popupVisible)hidePopup();else{search.setText("");filter();pending=false;popupVisible=true;try{closePopup=CandidatePopup.show(popup,search,button,this::popupClosed);}catch(RuntimeException failure){popupVisible=false;throw failure;}}});
        updateSummary();
    }
    void options(Map<T,String> values){options(values,Map.of());}
    void options(Map<T,String> values,Map<T,String> retained){options=Collections.unmodifiableMap(new LinkedHashMap<>(values));existingLabels=Map.copyOf(retained);filter();updateSummary();}
    void selected(List<T> values){
        var extras=new HashSet<>(selectedSet);extras.removeAll(options.keySet());
        ++revision;selected=List.copyOf(new LinkedHashSet<>(values));selectedSet=Set.copyOf(selected);
        var nextExtras=new HashSet<>(selectedSet);nextExtras.removeAll(options.keySet());
        if(!extras.equals(nextExtras))filter();else list.repaint();updateSummary();
    }
    List<T> selected(){return selected;}
    void onChange(Consumer<List<T>> listener){changed=listener;}
    void onCommit(Consumer<List<T>> listener){committed=listener;}
    void toggle(T value){if(!isEnabled())return;var next=new ArrayList<>(selected);if(!next.remove(value))next.add(value);commit(next);}
    private void commit(List<T> values){if(!isEnabled()||selected.equals(values))return;selected(values);changed.accept(selected);if(popupVisible)pending=true;else committed.accept(selected);}
    private void hidePopup(){if(closePopup!=null)closePopup.run();}
    private void popupClosed(){popupVisible=false;closePopup=null;if(!pending)return;pending=false;long expected=revision;var values=selected;SwingUtilities.invokeLater(() -> {if(isEnabled()&&revision==expected)committed.accept(values);});}
    @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);if(button!=null)button.setEnabled(enabled);if(!enabled)hidePopup();}
    private String label(T value){return options.getOrDefault(value,existingLabels.containsKey(value)?"已绑定（筛选不符）· "+existingLabels.get(value):value+" · 未登记");}
    private void filter(){
        String needle=search.getText().trim().toLowerCase(Locale.ROOT);T active=list.getSelectedValue();
        var all=new LinkedHashSet<T>(options.keySet());all.addAll(selected);
        var matches=new ArrayList<T>();for(T value:all)if(label(value).toLowerCase(Locale.ROOT).contains(needle))matches.add(value);
        list.getSelectionModel().setValueIsAdjusting(true);
        try{rows.clear();rows.addAll(matches);}finally{list.getSelectionModel().setValueIsAdjusting(false);}
        if(active!=null)list.setSelectedValue(active,false);list.repaint();
    }
    private void updateSummary(){
        String text=selected.isEmpty()?"选择候选…":selected.size()+" 项 · "+String.join(", ",selected.stream().limit(3).map(Object::toString).toList())+(selected.size()>3?"…":"");
        button.setText(text+"  ▾");button.setToolTipText(selected.isEmpty()?"展开后可搜索并勾选多项":String.join("；",selected.stream().limit(40).map(this::label).toList()));
    }
}
