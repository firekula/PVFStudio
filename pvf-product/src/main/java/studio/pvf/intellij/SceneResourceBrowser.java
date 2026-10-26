package studio.pvf.intellij;

import studio.pvf.preview.EntityScript;
import studio.pvf.sceneedit.SceneCatalog;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Catalog filtering stays cheap; only the selected asset requests real pixels. */
final class SceneResourceBrowser extends JPanel {
    enum Category {
        TILE("同目录 TIL"), ANIMATION("同目录 ANI"), NPC("全局 NPC"), MONSTER("全局怪物"), PASSIVE("全局被动对象"), EFFECT("特效候选（被动对象）");
        final String label; Category(String label){this.label=label;} public String toString(){return label;}
    }
    record Asset(Category category,Integer id,String path) { public String toString(){return (id==null?"":id+" · ")+path;} }
    record Thumbnail(BufferedImage image,String detail,Boolean extendedTile) {Thumbnail(BufferedImage image,String detail){this(image,detail,null);}}
    @FunctionalInterface interface ThumbnailLoader { void load(Asset asset,Consumer<Thumbnail> result); }
    private String source;
    private final JComboBox<Category> category=new JComboBox<>(Category.values());
    private final JTextField filter=new JTextField();
    private final DefaultListModel<Asset> items=new DefaultListModel<>();
    private final JList<Asset> list=new JList<>(items);
    private final JLabel image=new JLabel("选择资源查看缩略图",SwingConstants.CENTER);
    private final JTextArea detail=new JTextArea(3,16);
    private final Map<String,Thumbnail> cache=new LinkedHashMap<>();
    private SceneCatalog catalog;
    private ThumbnailLoader loader=(asset,result) -> {};
    private long generation;
    private Boolean selectedExtended;
    SceneResourceBrowser(String source) {
        super(new BorderLayout(4,4)); this.source=source;
        category.setName("map-asset-category"); filter.setName("map-asset-filter"); list.setName("map-asset-list");
        JPanel header=new JPanel(new GridLayout(2,1,0,4)); header.add(category); header.add(filter); add(header,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setFixedCellHeight(25); list.setFixedCellWidth(420); add(new JScrollPane(list));
        image.setPreferredSize(new Dimension(120,100)); detail.setEditable(false); detail.setLineWrap(true); detail.setWrapStyleWord(true); detail.setOpaque(false);
        JPanel footer=new JPanel(new BorderLayout()); footer.add(image); footer.add(detail,BorderLayout.SOUTH); add(footer,BorderLayout.SOUTH);
        category.addActionListener(e -> rebuild());
        filter.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){rebuild();}public void removeUpdate(DocumentEvent e){rebuild();}public void changedUpdate(DocumentEvent e){rebuild();}});
        list.addListSelectionListener(e -> { if(!e.getValueIsAdjusting())thumbnail(); });
        setMinimumSize(new Dimension(140,120));
    }
    void setCatalog(SceneCatalog catalog) { this.catalog=catalog; rebuild(); }
    void setSource(String path) { source=path;cache.clear();rebuild(); }
    void setThumbnailLoader(ThumbnailLoader loader) { this.loader=loader; cache.clear(); thumbnail(); }
    boolean tileTypeKnown(){return selectedExtended!=null;}
    boolean selectedExtendedTile(){return selected()!=null&&selected().category()==Category.TILE&&Boolean.TRUE.equals(selectedExtended);}
    Asset selected() { return list.getSelectedValue(); }
    void select(Category type,String path) {
        category.setSelectedItem(type); filter.setText("");
        for(int i=0;i<items.size();i++)if(items.get(i).path().equals(SceneCatalog.canonical(path))) { list.setSelectedIndex(i); list.ensureIndexIsVisible(i); break; }
    }
    private void rebuild() {
        Asset previous=selected(); items.clear(); if(catalog==null)return;
        Category selected=(Category)category.getSelectedItem(); List<Asset> assets=new ArrayList<>();
        if(selected==Category.TILE||selected==Category.ANIMATION) catalog.filesUnder(source,selected==Category.TILE?".til":".ani").forEach(p -> assets.add(new Asset(selected,null,p)));
        else {
            EntityScript.Kind kind=selected==Category.NPC?EntityScript.Kind.NPC:selected==Category.MONSTER?EntityScript.Kind.MONSTER:EntityScript.Kind.PASSIVE;
            catalog.entities(kind).forEach((id,path) -> {
                if(selected!=Category.EFFECT||path.contains("particle")||path.contains("/effect/"))assets.add(new Asset(selected,id,path));
            });
        }
        String query=filter.getText().toLowerCase(Locale.ROOT).trim();
        items.addAll(assets.stream().filter(a -> a.toString().toLowerCase(Locale.ROOT).contains(query)).toList());
        if(previous!=null)for(int i=0;i<items.size();i++)if(items.get(i).equals(previous)){list.setSelectedIndex(i);return;}
        if(!items.isEmpty())list.setSelectedIndex(0);
    }
    private void thumbnail() {
        long request=++generation;selectedExtended=null; Asset asset=selected(); image.setIcon(null);
        if(asset==null){image.setText("没有匹配资源");detail.setText("");return;}
        detail.setText(asset.toString()); detail.setCaretPosition(0);
        Thumbnail cached=cache.get(asset.toString()); if(cached!=null){show(cached);return;}
        image.setText("读取所选资源…");
        loader.load(asset,result -> {
            if(result.image()!=null){if(cache.size()>=32)cache.remove(cache.keySet().iterator().next());cache.put(asset.toString(),result);}
            if(request==generation)show(result);
        });
    }
    private void show(Thumbnail value) { selectedExtended=value.extendedTile(); image.setIcon(value.image()==null?null:new ImageIcon(value.image())); image.setText(value.image()==null?"此帧没有可用图像":""); detail.setText(value.detail()); detail.setCaretPosition(0); }
}
