package studio.pvf.intellij;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Map;

/** Filters catalog metadata only; definitions/pixels are loaded after selection. */
final class SceneAssetPicker {
    private SceneAssetPicker() {}
    private record Item(int id,String path) { public String toString() { return id+" · "+path; } }
    static Integer choose(Component owner,String title,Map<Integer,String> catalog) {
        JTextField query=new JTextField(); query.setToolTipText("搜索编号或路径"); DefaultListModel<Item> items=new DefaultListModel<>(); JList<Item> list=new JList<>(items); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setFixedCellHeight(25); list.setFixedCellWidth(600);
        Runnable filter=() -> { items.clear(); String text=query.getText().toLowerCase(java.util.Locale.ROOT); var matches=new java.util.ArrayList<Item>(); catalog.forEach((id,path) -> { if((id+" "+path).toLowerCase(java.util.Locale.ROOT).contains(text))matches.add(new Item(id,path)); }); items.addAll(matches); if(!items.isEmpty())list.setSelectedIndex(0); };
        query.getDocument().addDocumentListener(new DocumentListener() { public void insertUpdate(DocumentEvent e){filter.run();} public void removeUpdate(DocumentEvent e){filter.run();} public void changedUpdate(DocumentEvent e){filter.run();} }); filter.run();
        JPanel panel=new JPanel(new BorderLayout(4,4)); panel.add(query,BorderLayout.NORTH); panel.add(new JScrollPane(list)); panel.setPreferredSize(new Dimension(540,360));
        return JOptionPane.showConfirmDialog(owner,panel,title,JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION&&list.getSelectedValue()!=null?list.getSelectedValue().id():null;
    }
}
