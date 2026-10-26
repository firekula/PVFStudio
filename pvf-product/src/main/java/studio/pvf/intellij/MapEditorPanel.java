package studio.pvf.intellij;

import studio.pvf.preview.*;
import studio.pvf.sceneedit.*;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.*;

/** Inspector, assets and tile sequences all edit the same guarded MAP source. */
final class MapEditorPanel extends JPanel {
    private String path;
    private final DungeonEditorPanel.Edit edit;
    private final LongSupplier currentVersion;
    private final Consumer<String> openDefinition;
    private final Consumer<SourceDocument.Range> reveal;
    private final Runnable previewChanged;
    private final SceneResourceBrowser browser;
    private final JPanel previewHost=new JPanel(new BorderLayout()),properties=new JPanel();
    private final DefaultListModel<MapEntry> objects=new DefaultListModel<>();
    private final JList<MapEntry> objectList=new JList<>(objects);
    private final JLabel status=new JLabel("正在解析…"),selectedLabel=new JLabel("选择对象或资源");
    private final Map<String,JTextField> fields=new LinkedHashMap<>();
    private final List<JComponent> writes=new ArrayList<>();
    private final JTable tiles=new JTable();
    private final JSplitPane assetSplit,inspectorSplit;
    private final JCheckBox background=new JCheckBox("作为循环背景"),upper=new JCheckBox("上层背景");
    private final JComboBox<String> layer=new JComboBox<>(new String[]{"normal","bottom","distantback","middleback","closeback","close"});
    private final JTextArea payload=new JTextArea(5,18);
    private MapDocument model;
    private SceneCatalog catalog;
    private MapCanvasController control;
    private SceneCanvas canvas;
    private long version;
    private boolean writable,changing;
    private EntryId selected;
    private record View(double panX,double panY,double zoom,int zoomIndex,Point camera) {}
    private View view;
    private record PaneLayout(int width,int assets,int inspector) {}
    private PaneLayout paneLayout;
    private Function<List<String>,List<String>> tileColumnPicker=this::pickExtendedColumn;
    MapEditorPanel(String path,DungeonEditorPanel.Edit edit,LongSupplier currentVersion,Consumer<String> openDefinition,
                   Consumer<SourceDocument.Range> reveal,Runnable previewChanged) {
        super(new BorderLayout(4,4)); this.path=path; this.edit=edit; this.currentVersion=currentVersion; this.openDefinition=openDefinition; this.reveal=reveal; this.previewChanged=previewChanged;
        browser=new SceneResourceBrowser(path);
        JPanel bar=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2));
        bar.add(action("添加到中心","map-add-selected",() -> addSelectedAt(visibleCenter().getX(),visibleCenter().getY())));
        bar.add(action("点击画布添加","map-place-tool",() -> { if(control!=null)control.placeAt(point -> addSelectedAt(point.getX(),point.getY()-SceneCoordinates.ORIGIN_Y)); }));
        bar.add(new JLabel("ANI 图层")); bar.add(layer); bar.add(background); bar.add(upper);
        JButton sequence=button("地块序列…","map-tile-sequence",this::editTiles); bar.add(sequence); writes.add(sequence);
        add(bar,BorderLayout.NORTH);
        JTabbedPane left=new JTabbedPane(); left.addTab("资源",browser); left.addTab("对象",new JScrollPane(objectList)); left.setMinimumSize(new Dimension(150,120));
        objectList.setName("map-object-list"); objectList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); objectList.setFixedCellHeight(25); objectList.setFixedCellWidth(420);
        objectList.setCellRenderer(new DefaultListCellRenderer(){ public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean isSelected,boolean focus) {
            var label=(JLabel)super.getListCellRendererComponent(list,value,index,isSelected,focus); var entry=(MapEntry)value;
            label.setText((index+1)+" · "+entry.type()+" "+(entry.definitionId()<0?entry.reference():"#"+entry.definitionId())); return label;
        }});
        objectList.addListSelectionListener(e -> { if(!changing&&!e.getValueIsAdjusting())selectEntry(objectList.getSelectedValue()==null?null:objectList.getSelectedValue().id()); });
        properties.setLayout(new BoxLayout(properties,BoxLayout.Y_AXIS)); properties.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        JScrollPane inspector=new JScrollPane(properties); inspector.setMinimumSize(new Dimension(180,120)); inspector.setPreferredSize(new Dimension(250,400));
        inspectorSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,previewHost,inspector); inspectorSplit.setResizeWeight(.72); inspectorSplit.setDividerLocation(500);
        assetSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,inspectorSplit); assetSplit.setResizeWeight(.2); assetSplit.setDividerLocation(190); add(assetSplit);
        status.setName("map-editor-status"); status.setToolTipText("选择对象后拖动；重复点击或 Alt 点击轮换重叠对象；中键/空格拖动平移；Esc 取消。坐标拖动保留 Z。"); add(status,BorderLayout.SOUTH);
        fillProperties();
    }
    private static JButton button(String title,String name,Runnable run) { JButton b=new JButton(title); b.setName(name); b.setAlignmentX(LEFT_ALIGNMENT); b.addActionListener(e -> run.run()); return b; }
    private JButton action(String title,String name,Runnable run) { var b=button(title,name,run); writes.add(b); return b; }
    SceneResourceBrowser browser() { return browser; }
    JPanel previewHost() { return previewHost; }
    void tileColumnPicker(Function<List<String>,List<String>> picker) { tileColumnPicker=picker; }
    void setCatalog(SceneCatalog value) { catalog=value; browser.setCatalog(value); }
    void attach(SceneCanvas value) {
        if(canvas!=value) {
            canvas=value; control=new MapCanvasController(canvas,currentVersion,this::selectEntry,(stamp,id,x,y,z) -> edit.apply(stamp,model.move(id,x,y,z)));
        }
        canvas.interaction=control;
        if(model!=null) {
            control.install(model,version,writable);
            if(canvas.world==null)canvas.world=new Rectangle2D.Double(0,0,Math.max(224,model.tiles(false).size()*224.),480);
            restoreView(canvas);
        }
    }
    void captureView(SceneCanvas value) { view=new View(value.panX,value.panY,value.freeZoom,value.zoom,value.camera==null?null:value.camera.position()); }
    void captureLayout() { if(getWidth()>0)paneLayout=new PaneLayout(getWidth(),assetSplit.getDividerLocation(),inspectorSplit.getDividerLocation()); }
    void restoreView(SceneCanvas value) {
        if(paneLayout!=null&&value.scene!=null) {
            double scale=(double)getWidth()/paneLayout.width();
            assetSplit.setDividerLocation((int)Math.round(paneLayout.assets()*scale));assetSplit.doLayout();
            // Settle the parent's resize before restoring the inner divider, or resizeWeight applies its delta twice.
            inspectorSplit.doLayout();inspectorSplit.setDividerLocation((int)Math.round(paneLayout.inspector()*scale));inspectorSplit.doLayout();
        }
        if(view==null)return; value.panX=view.panX;value.panY=view.panY;value.freeZoom=view.zoom;value.zoom=view.zoomIndex;
        if(value.camera!=null&&view.camera!=null)value.camera.set(view.camera.x,view.camera.y);
    }
    void install(MapDocument value,long stamp,boolean canWrite,String error) {
        if(value!=null&&!path.equals(value.path())){path=value.path();browser.setSource(path);}
        model=value; version=stamp; writable=canWrite; status.setText(error.isBlank()?"选择/拖动对象 · 中键或空格平移 · Esc 取消 · Ctrl+S 保存草稿":error); status.setToolTipText(error.isBlank()?status.getToolTipText():error);
        if(model==null){writes.forEach(c -> c.setEnabled(false));return;}
        changing=true; objects.clear(); objects.addAll(model.entries()); changing=false;
        if(selected!=null&&model.entries().stream().noneMatch(e -> e.id().equals(selected)))selected=null;
        if(control!=null)control.install(model,stamp,canWrite);
        fillProperties(); writes.forEach(c -> c.setEnabled(canWrite));
        if(!model.diagnostics().isEmpty()) { status.setText("解析提示："+String.join("；",model.diagnostics().stream().limit(3).toList())); status.setToolTipText(String.join("\n",model.diagnostics())); }
        previewChanged.run();
    }
    void selectEntry(EntryId id) {
        selected=id;
        if(control!=null&&!Objects.equals(control.selected(),id))control.select(id);
        changing=true; try { if(id==null)objectList.clearSelection(); else for(int i=0;i<objects.size();i++)if(objects.get(i).id().equals(id)){objectList.setSelectedIndex(i);break;} } finally { changing=false; }
        fillProperties();
    }
    private MapEntry entry() { return model==null||selected==null?null:model.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null); }
    private Point2D visibleCenter() {
        if(canvas==null)return new Point2D.Double(112,120);
        var point=canvas.viewToWorld(new Point2D.Double(canvas.getWidth()/2.,canvas.getHeight()/2.),1);
        return new Point2D.Double(Math.rint(point.getX()),Math.rint(point.getY()-SceneCoordinates.ORIGIN_Y));
    }
    void addSelectedAt(double x,double y) {
        if(model==null||catalog==null||!writable)return; var asset=browser.selected(); if(asset==null)return;
        change(() -> {
            if(asset.category()==SceneResourceBrowser.Category.TILE) {
                var basic=new ArrayList<>(model.tiles(false).stream().map(MapDocument.TileEntry::reference).toList()); int width=basic.size();
                var extended=model.tiles(true).stream().map(MapDocument.TileEntry::reference).toList(); var after=new ArrayList<String>();
                if(!extended.isEmpty()) {
                    if(width==0||extended.size()%width!=0)throw new java.io.IOException("请先在地块序列中补齐现有扩展行");
                    List<String> defaults=new ArrayList<>();for(int row=0;row<extended.size()/width;row++)defaults.add(extended.get(row*width+width-1));
                    var selected=tileColumnPicker.apply(List.copyOf(defaults)); if(selected==null)return null;
                    if(selected.size()!=defaults.size())throw new java.io.IOException("每个扩展行须选择一个新列地块");
                    for(int row=0;row<selected.size();row++){after.addAll(extended.subList(row*width,(row+1)*width));after.add(selected.get(row));}
                }
                basic.add(catalog.relative(path,asset.path())); return model.replaceTileGrid(basic,after);
            }
            if(asset.category()==SceneResourceBrowser.Category.ANIMATION)return model.addAnimation(catalog.relative(path,asset.path()),(String)layer.getSelectedItem(),background.isSelected(),upper.isSelected(),Math.rint(x),Math.rint(y),0);
            EntityScript.Kind kind=asset.category()==SceneResourceBrowser.Category.MONSTER?EntityScript.Kind.MONSTER:asset.category()==SceneResourceBrowser.Category.NPC?EntityScript.Kind.NPC:EntityScript.Kind.PASSIVE;
            return model.addEntity(kind,asset.id(),Math.rint(x),Math.rint(y),0);
        });
    }
    private List<String> pickExtendedColumn(List<String> defaults) {
        var choices=catalog.filesUnder(path,".til").stream().map(p -> {try{return catalog.relative(path,p);}catch(Exception e){return p;}}).toArray(String[]::new);
        JPanel form=new JPanel(new GridLayout(0,2,4,4)); List<JComboBox<String>> cells=new ArrayList<>();
        for(int row=0;row<defaults.size();row++){form.add(new JLabel("扩展行 "+(row+1)+" 的新列"));var cell=new JComboBox<>(choices);cell.setSelectedItem(defaults.get(row));cells.add(cell);form.add(cell);}
        if(JOptionPane.showConfirmDialog(this,form,"新增基础列：选择每个扩展行的地块",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;
        return cells.stream().map(c -> (String)c.getSelectedItem()).toList();
    }
    private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("id","全局编号"),Map.entry("x","X"),Map.entry("y","Y"),Map.entry("z","Z"),Map.entry("reference","ANI 路径"),Map.entry("facing","朝向（left/right）"),Map.entry("autoLevel","自动等级"),Map.entry("level","等级/偏移"),Map.entry("randomDrops","随机掉落"),Map.entry("fixedDrops","固定掉落"),Map.entry("copyType","复制方式（fixed/half/ratio）"),Map.entry("monsterType","类型（normal/boss/champion）"),Map.entry("npcId","NPC 编号"),Map.entry("layer","图层"),Map.entry("order","背景顺序（below/upper）"));
    private void fillProperties() {
        // Keep toolbar actions; discard old inspector action references when rebuilding.
        writes.removeIf(c -> SwingUtilities.isDescendingFrom(c,properties)); properties.removeAll(); fields.clear(); var entry=entry();
        selectedLabel.setText(entry==null?"选择对象查看原生属性":entry.type()+" · "+(entry.definitionId()<0?entry.reference():"#"+entry.definitionId())); properties.add(selectedLabel);
        if(entry!=null) {
            for(String key:List.of("id","reference","x","y","z","facing","autoLevel","level","randomDrops","fixedDrops","copyType","monsterType","npcId","layer","order")) {
                if(!entry.fields().containsKey(key))continue;
                properties.add(new JLabel(LABELS.getOrDefault(key,key))); JTextField field=new JTextField(entry.properties().getOrDefault(key,"")); field.setName("map-prop-"+key); field.setMaximumSize(new Dimension(Integer.MAX_VALUE,28)); field.setEditable(writable); fields.put(key,field); properties.add(field);
            }
            if(entry.fields().containsKey("traitCount")) {
                properties.add(new JLabel("精英特性编号（逗号分隔）"));
                JTextField traits=new JTextField(String.join(", ",entry.tokens().subList(entry.fields().get("traitCount")+1,entry.tokens().size()).stream().map(SourceDocument.Token::value).toList()));
                traits.setName("map-prop-traits"); traits.setMaximumSize(new Dimension(Integer.MAX_VALUE,28)); traits.setEditable(writable); fields.put("traits",traits); properties.add(traits);
            }
            properties.add(action("应用属性","map-apply-properties",() -> change(this::propertyEdit)));
            JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,3,2)); actions.add(action("复制","map-clone-entry",() -> change(() -> model.copy(selected)))); actions.add(action("删除","map-delete-entry",() -> change(() -> model.remove(selected)))); actions.setMaximumSize(new Dimension(Integer.MAX_VALUE,38)); properties.add(actions);
            properties.add(button("定位源码","map-reveal-source",() -> reveal.accept(entry.range())));
            properties.add(button("打开全局定义","map-open-definition",this::definition));
            if(entry.type()!=MapEntry.Type.ANIMATION&&entry.type()!=MapEntry.Type.BACKGROUND) {
                JTextArea note=new JTextArea("图层、碰撞、待机 ANI 和粒子工厂参数在全局定义中设置。修改全局定义会影响所有使用此编号的地图。"); note.setEditable(false); note.setLineWrap(true); note.setWrapStyleWord(true); note.setOpaque(false); note.setRows(4); properties.add(note);
            }
            if(entry.type()==MapEntry.Type.SPECIAL) {
                properties.add(new JLabel("容器内容（类型 + 原生整数参数）")); payload.setText(contents(entry)); payload.setName("map-special-contents"); properties.add(new JScrollPane(payload));
                properties.add(action("应用容器 / hellparty 内容","map-apply-contents",() -> change(() -> model.setSpecialContents(selected,parseContents(payload.getText())))));
            }
        }
        for(Component c:properties.getComponents())if(c instanceof JComponent component)component.setAlignmentX(LEFT_ALIGNMENT);
        writes.forEach(c -> c.setEnabled(writable)); properties.revalidate(); properties.repaint();
    }
    private ScriptEdit propertyEdit() throws Exception {
        String text=model.source().text();
        for(var field:fields.entrySet()) {
            if(field.getKey().equals("traits"))continue;
            if(field.getValue().getText().equals(entry().properties().get(field.getKey())))continue;
            var current=MapDocument.parse(path,text); var entry=current.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElseThrow();
            if(!field.getValue().getText().equals(entry.properties().get(field.getKey())))text=current.setField(selected,field.getKey(),field.getValue().getText()).applyTo(text);
        }
        if(fields.containsKey("traits")) {
            var current=MapDocument.parse(path,text); var entry=current.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElseThrow();
            if(entry.fields().containsKey("traitCount")) {
                String raw=fields.get("traits").getText().trim(); var traits=raw.isBlank()?List.<Integer>of():Arrays.stream(raw.split("[,，\\s]+")).map(Integer::valueOf).toList();
                if(traits.size()>64||traits.stream().anyMatch(n -> n<0))throw new java.io.IOException("特性编号须非负，最多 64 项");
                var count=entry.tokens().get(entry.fields().get("traitCount"));
                var previous=entry.tokens().subList(entry.fields().get("traitCount")+1,entry.tokens().size()).stream().map(t -> Integer.valueOf(t.value())).toList();
                if(!previous.equals(traits))text=current.source().replace("设置精英特性",new SourceDocument.Range(count.range().start(),entry.range().end()),traits.size()+(traits.isEmpty()?"":" "+String.join(" ",traits.stream().map(Object::toString).toList()))).applyTo(text);
            }
        }
        return model.source().replace("修改对象属性",new SourceDocument.Range(0,model.source().text().length()),text);
    }
    private void definition() {
        var entry=entry(); if(entry==null||catalog==null)return;
        String target;
        if(!entry.reference().isBlank())target=catalog.resolve(path,entry.reference());
        else { var kind=entry.type()==MapEntry.Type.NPC?EntityScript.Kind.NPC:entry.type()==MapEntry.Type.MONSTER?EntityScript.Kind.MONSTER:EntityScript.Kind.PASSIVE; target=catalog.entities(kind).get(entry.definitionId()); }
        if(target!=null)openDefinition.accept(target);
    }
    private static String contents(MapEntry entry) {
        var tokens=entry.tokens(); int i=entry.fields().get("contentCount")+1; List<String> lines=new ArrayList<>();
        while(i<tokens.size()) {
            String kind=ScriptDialect.flag(tokens.get(i++).value()); List<String> values=new ArrayList<>();
            if(kind.equals("hellparty")){while(i<tokens.size()&&!ScriptDialect.flag(tokens.get(i).value()).equals("/hellparty"))values.add(tokens.get(i++).value());i++;}
            else for(int n=0;n<5&&i<tokens.size();n++)values.add(tokens.get(i++).value());
            lines.add(kind+" "+String.join(" ",values));
        }
        return String.join("\n",lines);
    }
    private static List<MapDocument.SpecialContent> parseContents(String text) {
        List<MapDocument.SpecialContent> values=new ArrayList<>();
        for(String line:text.lines().filter(l -> !l.isBlank()).toList()){var fields=line.trim().split("\\s+"); values.add(new MapDocument.SpecialContent(fields[0],Arrays.asList(fields).subList(1,fields.length)));}
        return values;
    }
    private void editTiles() {
        if(model==null)return;
        JTextArea basic=new JTextArea(String.join("\n",model.tiles(false).stream().map(MapDocument.TileEntry::reference).toList()),5,22);
        JTextArea extended=new JTextArea(String.join("\n",model.tiles(true).stream().map(MapDocument.TileEntry::reference).toList()),5,22);
        JPanel form=new JPanel(new GridLayout(1,2,6,0)); JPanel a=new JPanel(new BorderLayout()),b=new JPanel(new BorderLayout());
        a.add(new JLabel("基础 TIL：每行一项"),BorderLayout.NORTH);a.add(new JScrollPane(basic)); b.add(new JLabel("扩展 TIL：逐行排列，列数须一致"),BorderLayout.NORTH);b.add(new JScrollPane(extended));form.add(a);form.add(b);
        var tableModel=new AbstractTableModel(){public int getColumnCount(){return model.tiles(false).size();}public int getRowCount(){return 1+(int)Math.ceil((double)model.tiles(true).size()/Math.max(1,getColumnCount()));}public Object getValueAt(int row,int col){var list=model.tiles(row>0);int index=row==0?col:(row-1)*getColumnCount()+col;return index<list.size()?list.get(index).reference():"";}};
        tiles.setModel(tableModel);tiles.setCellSelectionEnabled(true);tiles.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);tiles.setRowHeight(36);
        for(int i=0;i<tiles.getColumnCount();i++)tiles.getColumnModel().getColumn(i).setPreferredWidth(160);
        JPanel dialog=new JPanel(new BorderLayout(4,4));dialog.add(new JLabel("首行为基础地块，后续为扩展行；移动列会同步移动所有扩展行。"),BorderLayout.NORTH);dialog.add(new JScrollPane(tiles));dialog.add(form,BorderLayout.SOUTH);dialog.setPreferredSize(new Dimension(660,380));
        Object[] choices={"应用序列","当前列左移","当前列右移","取消"};int choice=JOptionPane.showOptionDialog(this,dialog,"MAP 地块序列",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,choices,choices[0]);
        if(choice==0)change(() -> model.replaceTileGrid(basic.getText().lines().filter(l -> !l.isBlank()).map(String::trim).toList(),extended.getText().lines().filter(l -> !l.isBlank()).map(String::trim).toList()));
        if((choice==1||choice==2)&&tiles.getSelectedColumn()>=0){int from=tiles.getSelectedColumn(),to=from+(choice==1?-1:1);change(() -> model.moveTileColumn(from,to));}
    }
    @FunctionalInterface private interface Command { ScriptEdit make() throws Exception; }
    private void change(Command command) {
        if(!writable||model==null)return;
        try{var prepared=command.make();if(prepared!=null)edit.apply(version,prepared);}
        catch(Exception failure){status.setText("无法应用："+failure.getMessage());status.setToolTipText(status.getText());}
    }
}
