package studio.pvf.intellij;

import studio.pvf.sceneedit.*;
import studio.pvf.preview.DungeonScript.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Edits the selected source snapshot; previews and navigation never write source. */
final class DungeonEditorPanel extends JPanel {
    @FunctionalInterface interface Edit { void apply(long version, ScriptEdit edit) throws Exception; }
    record Preview(int maze, Position position, Integer mapId, boolean hell) {}
    private final Edit edit;
    private final Consumer<String> openMap;
    private final Runnable previewChanged;
    private final Consumer<SourceDocument.Range> reveal;
    private final JComboBox<String> mazes = named(new JComboBox<>(),"dgn-editor-maze");
    private final JPanel previewHost = new JPanel(new BorderLayout());
    private final Grid grid = new Grid();
    private final JScrollPane inspector;
    private final JLabel selection = new JLabel("选择房间");
    private final JLabel status = new JLabel("正在解析…");
    private final JTextArea diagnostics = new JTextArea(3,20);
    private final JTextField maps = named(new JTextField(),"dgn-room-maps"), bossMaps = named(new JTextField(),"dgn-room-boss-maps");
    private final JCheckBox start = named(new JCheckBox("起点候选"),"dgn-start"), boss = named(new JCheckBox("Boss 候选"),"dgn-boss");
    private final EnumMap<Direction,JCheckBox> doors = new EnumMap<>(Direction.class);
    private final JCheckBox hellEnabled = named(new JCheckBox("启用深渊（整个副本）"),"dgn-hell-enabled");
    private final JCheckBox escape = named(new JCheckBox("当前布局排除深渊"),"dgn-escape-hell");
    private final JTextField hellMap = named(new JTextField(),"dgn-hell-map"), hits = named(new JTextField(),"dgn-hell-hits");
    private final JTextField rate = named(new JTextField(),"dgn-hell-rate"), coin = named(new JTextField(),"dgn-hell-coin");
    private final JLabel hellPosition = new JLabel();
    private final JComboBox<String> previewMode = named(new JComboBox<>(new String[]{"普通房间", "深渊替换房"}),"dgn-preview-mode");
    private final JComboBox<Integer> previewMaps = named(new JComboBox<>(),"dgn-preview-map");
    private final List<JComponent> writes = new ArrayList<>();
    private DungeonDocument model;
    private SceneCatalog catalog;
    private long version;
    private Position position = new Position(0,0);
    private int maze;
    private boolean changing, writable;
    private Consumer<Preview> cloneMap = request -> {};

    DungeonEditorPanel(Edit edit, Consumer<String> openMap, Runnable previewChanged, Consumer<SourceDocument.Range> reveal) {
        super(new BorderLayout(4,4)); this.edit=edit; this.openMap=openMap; this.previewChanged=previewChanged; this.reveal=reveal;
        JPanel top = row(); top.add(new JLabel("副本布局")); mazes.setPreferredSize(new Dimension(130,26)); top.add(mazes);
        top.add(action("新建布局","dgn-new-maze",() -> change(() -> model.addMaze(3,3))));
        top.add(action("复制布局","dgn-copy-maze",() -> change(() -> model.copyMaze(maze))));
        top.add(action("调整尺寸…","dgn-resize",this::resize)); add(top,BorderLayout.NORTH);
        JPanel scene = new JPanel(new BorderLayout(4,4));
        JScrollPane gridScroll = new JScrollPane(grid); gridScroll.setPreferredSize(new Dimension(420,190));
        JPanel layout = new JPanel(new BorderLayout()); layout.add(new JLabel("点击空格创建房间 · S 起点 · B Boss · H 深渊封印"),BorderLayout.NORTH); layout.add(gridScroll);
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT,layout,previewHost); vertical.setResizeWeight(.32); vertical.setDividerLocation(210); scene.add(vertical);
        JPanel properties = new JPanel(); properties.setLayout(new BoxLayout(properties,BoxLayout.Y_AXIS));
        selection.setAlignmentX(LEFT_ALIGNMENT); properties.add(selection);
        field(properties,"普通 MAP 候选（编号，逗号分隔）",maps); field(properties,"Boss MAP 候选",bossMaps);
        JPanel choose = row(); choose.add(action("选择 MAP…","dgn-pick-map",() -> chooseMap(maps)));
        choose.add(action("选择 Boss MAP…","dgn-pick-boss-map",() -> chooseMap(bossMaps))); properties.add(choose);
        properties.add(action("创建房间 / 应用候选","dgn-apply-maps",() -> change(() -> {
            List<Integer> ids = numbers(maps.getText());
            if (!current().rooms().contains(position)) return model.createRoom(maze,position,ids);
            ScriptEdit first = model.setCandidates(maze,position,false,ids);
            String intermediate = first.applyTo(model.source().text());
            String after = DungeonDocument.parse(model.path(),intermediate,model.supportsEscape()).setCandidates(maze,position,true,numbers(bossMaps.getText())).applyTo(intermediate);
            return model.source().replace("设置房间 MAP 候选",new SourceDocument.Range(0,model.source().text().length()),after);
        })));
        JPanel flags=row(); flags.add(start); flags.add(boss); properties.add(flags); writes.add(start); writes.add(boss);
        start.addActionListener(e -> { if(!changing) change(() -> model.setStarts(maze,toggle(current().layout().starts(),start.isSelected()))); });
        boss.addActionListener(e -> { if(!changing) change(() -> model.setBosses(maze,toggle(current().layout().bosses(),boss.isSelected()))); });
        JPanel links = row(); links.add(new JLabel("通路"));
        for (Direction d:Direction.values()) {
            JCheckBox door=named(new JCheckBox(d.label),"dgn-connect-"+d.name().toLowerCase(Locale.ROOT)); doors.put(d,door); links.add(door); writes.add(door);
            door.addActionListener(e -> { if(!changing) change(() -> model.connect(maze,position,d,door.isSelected())); });
        }
        properties.add(links);
        JPanel nav=row(); nav.add(button("打开房间 MAP","dgn-open-map",this::openSelected)); nav.add(action("删除房间","dgn-delete-room",() -> change(() -> model.removeRoom(maze,position)))); properties.add(nav);
        properties.add(action("克隆 MAP 并绑定…","dgn-clone-map",() -> cloneMap.accept(preview())));
        properties.add(new JSeparator()); properties.add(new JLabel("深渊封印房")); properties.add(hellEnabled); properties.add(escape); writes.add(hellEnabled); writes.add(escape);
        field(properties,"封印 MAP 编号",hellMap); properties.add(hellPosition);
        JPanel hellActions=row(); hellActions.add(action("设为当前房间","dgn-hell-here",() -> change(() -> hell(position))));
        hellActions.add(action("清除封印房","dgn-hell-clear",() -> change(() -> newHell(0,null)))); properties.add(hellActions);
        hellEnabled.addActionListener(e -> { if(!changing) change(() -> hell(model.hell(maze).position())); });
        escape.addActionListener(e -> { if(!changing) change(() -> hell(model.hell(maze).position())); });
        field(properties,"击打阈值（全局，保留原字段数量）",hits); field(properties,"出现率（全局原始值）",rate); field(properties,"金币限制（旧版本全局原始值）",coin);
        properties.add(action("应用深渊参数","dgn-apply-hell",() -> change(() -> hell(model.hell(maze).position()))));
        JTextArea explanation=new JTextArea("深渊启用后，在指定坐标以封印 MAP 替换普通 MAP；与 Boss 位置独立。封印对象的 monster / hellparty 内容在 MAP 中编辑。概率单位未经确认，保留原始值。");
        explanation.setEditable(false); explanation.setLineWrap(true); explanation.setWrapStyleWord(true); explanation.setOpaque(false); explanation.setRows(5); properties.add(explanation);
        properties.add(new JSeparator()); properties.add(new JLabel("仅预览，不修改配置")); properties.add(previewMode); properties.add(previewMaps);
        previewMode.addActionListener(e -> { if(!changing) previewChanged.run(); }); previewMaps.addActionListener(e -> { if(!changing) previewChanged.run(); });
        properties.add(button("定位布局源码","dgn-reveal",() -> {
            if(model==null)return; var sections=model.source().sections("maze info"); if(maze<sections.size())reveal.accept(sections.get(maze).heading().range());
        }));
        properties.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        for(Component child:properties.getComponents())if(child instanceof JComponent c)c.setAlignmentX(LEFT_ALIGNMENT);
        inspector=new JScrollPane(properties); inspector.setMinimumSize(new Dimension(180,100)); inspector.setPreferredSize(new Dimension(300,400));
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,scene,inspector); split.setResizeWeight(.72); split.setDividerLocation(540); add(split);
        diagnostics.setEditable(false); diagnostics.setLineWrap(true); diagnostics.setWrapStyleWord(true);
        JPanel footer=new JPanel(new BorderLayout()); footer.add(status,BorderLayout.NORTH); footer.add(new JScrollPane(diagnostics)); add(footer,BorderLayout.SOUTH);
        mazes.addActionListener(e -> { if(!changing && model!=null) { maze=Math.max(0,mazes.getSelectedIndex()); update(); } });
        grid.setName("dgn-editor-grid"); setMinimumSize(new Dimension(300,200));
    }
    private static <T extends JComponent> T named(T c,String name) { c.setName(name); return c; }
    private static JPanel row() { var p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2)); p.setAlignmentX(LEFT_ALIGNMENT); p.setMaximumSize(new Dimension(Integer.MAX_VALUE,36)); return p; }
    private static void field(JPanel p,String title,JTextField value) { p.add(new JLabel(title)); value.setMaximumSize(new Dimension(Integer.MAX_VALUE,28)); p.add(value); }
    private JButton button(String title,String name,Runnable run) { JButton b=named(new JButton(title),name); b.setAlignmentX(LEFT_ALIGNMENT); b.addActionListener(e -> run.run()); return b; }
    private JButton action(String title,String name,Runnable run) { JButton b=button(title,name,run); writes.add(b); return b; }
    JPanel previewHost() { return previewHost; }
    Position position() { return position; }
    Preview preview() { return new Preview(maze,position,(Integer)previewMaps.getSelectedItem(),previewMode.getSelectedIndex()==1); }
    void onClone(Consumer<Preview> handler) { cloneMap=handler; }
    void setCatalog(SceneCatalog value) { catalog=value; }
    void resolvedChoices(DungeonPanel.Selection value) {
        if(value==null||!value.room().position().equals(position)||value.room().hell())return;
        changing=true;
        try { previewMaps.removeAllItems(); value.room().choices().forEach(map -> previewMaps.addItem(map.id())); if(value.map()!=null)previewMaps.setSelectedItem(value.map().id()); }
        finally { changing=false; }
    }
    void install(DungeonDocument value,long stamp,boolean canWrite,String error) {
        model=value; version=stamp; writable=canWrite; status.setText(error.isBlank()?"编辑写入当前文档 · Ctrl+S 保存草稿":error); status.setToolTipText(error);
        if(model==null) { writes.forEach(c -> c.setEnabled(false)); return; }
        changing=true; int selected=maze; mazes.removeAllItems();
        for(int i=0;i<model.mazes().size();i++)mazes.addItem((i+1)+" · "+model.mazes().get(i).layout());
        maze=Math.min(selected,model.mazes().size()-1); mazes.setSelectedIndex(maze); changing=false; update();
    }
    private DungeonDocument.Maze current() { return model.mazes().get(maze); }
    void selectRoom(Position value) { if(model!=null && value.x()>=0 && value.y()>=0 && value.x()<current().layout().width() && value.y()<current().layout().height()) { position=value; update(); } }
    private void update() {
        Point scroll=inspector.getViewport().getViewPosition();
        changing=true;
        try {
            var room=current(); var layout=room.layout(); if(position.x()>=layout.width()||position.y()>=layout.height())position=new Position(0,0);
            boolean exists=room.rooms().contains(position), editable=writable&&!model.source().sections("maze info").isEmpty();
            writes.forEach(c -> c.setEnabled(editable));
            selection.setText("房间 "+position+(exists?"":" · 空格，指定 MAP 后创建")); maps.setText(join(layout.maps().getOrDefault(position,List.of()))); bossMaps.setText(join(layout.bossMaps().getOrDefault(position,List.of())));
            start.setSelected(layout.starts().contains(position)); boss.setSelected(layout.bosses().contains(position)); start.setEnabled(editable&&exists); boss.setEnabled(editable&&exists);
            for(var item:doors.entrySet()) { item.getValue().setSelected(layout.connected(position,item.getKey())); item.getValue().setEnabled(editable&&exists&&room.rooms().contains(layout.neighbor(position,item.getKey()))); }
            var hell=model.hell(maze); hellEnabled.setSelected(hell.enabled()); escape.setSelected(hell.excluded()); escape.setEnabled(editable&&model.supportsEscape());
            escape.setToolTipText(model.supportsEscape()?"只作用于当前布局":"没有客户端支持 escape hell 的证据；可查看原始源码");
            hellMap.setText(hell.mapId()>0?Integer.toString(hell.mapId()):""); hellPosition.setText("封印房坐标："+(hell.position()==null?"未设置":hell.position()));
            hits.setText(join(hell.hitCounts())); rate.setText(hell.appearRate()==null?"":hell.appearRate().toString()); coin.setText(hell.coinLimit()==null?"":hell.coinLimit().toString());
            previewMaps.removeAllItems(); var choices=layout.bosses().contains(position)&&!layout.bossMaps().getOrDefault(position,List.of()).isEmpty()?layout.bossMaps().get(position):layout.maps().getOrDefault(position,List.of()); choices.forEach(previewMaps::addItem);
            diagnostics.setText(String.join("\n",model.diagnostics().stream().filter(d -> d.maze()==maze).map(d -> d.severity()+" "+d.message()).limit(20).toList())); diagnostics.setCaretPosition(0);
            grid.setPreferredSize(new Dimension(layout.width()*64,layout.height()*64)); grid.revalidate(); grid.repaint();
        } finally { changing=false; }
        SwingUtilities.invokeLater(() -> inspector.getViewport().setViewPosition(scroll));
        previewChanged.run();
    }
    private List<Position> toggle(List<Position> before,boolean enabled) { var values=new ArrayList<>(before); values.remove(position); if(enabled)values.add(position); return values; }
    private static String join(List<Integer> values) { return String.join(", ",values.stream().map(Object::toString).toList()); }
    private static List<Integer> numbers(String text) throws IOException {
        try { return text.isBlank()?List.of():Arrays.stream(text.trim().split("[,，\\s]+")).map(Integer::valueOf).toList(); }
        catch(NumberFormatException invalid) { throw new IOException("请输入整数编号，用逗号分隔"); }
    }
    private static Integer optional(String text) { return text.isBlank()?null:Integer.valueOf(text.trim()); }
    private ScriptEdit hell(Position at) throws IOException { return newHell(hellMap.getText().isBlank()?0:Integer.parseInt(hellMap.getText().trim()),at); }
    private ScriptEdit newHell(int mapId,Position at) throws IOException {
        return model.setHell(maze,new DungeonDocument.HellSettings(hellEnabled.isSelected(),escape.isSelected(),mapId,at,numbers(hits.getText()),optional(rate.getText()),optional(coin.getText())));
    }
    @FunctionalInterface private interface Command { ScriptEdit make() throws Exception; }
    private void change(Command command) {
        if(changing||!writable||model==null)return;
        try { edit.apply(version,command.make()); }
        catch(Exception failure) { status.setText("无法应用："+failure.getMessage()); status.setToolTipText(status.getText()); }
    }
    private void chooseMap(JTextField target) {
        if(catalog==null)return;
        Integer selected=SceneAssetPicker.choose(this,"选择房间 MAP",catalog.maps());
        if(selected!=null)target.setText(target.getText().isBlank()?selected.toString():target.getText()+", "+selected);
    }
    private void openSelected() {
        if(catalog==null)return; Integer id=(Integer)previewMaps.getSelectedItem();
        if(previewMode.getSelectedIndex()==1 && model!=null)id=model.hell(maze).mapId();
        if(id!=null&&catalog.maps().containsKey(id))openMap.accept(catalog.maps().get(id)); else status.setText("没有可打开的显式 MAP；先从候选列表选择 MAP");
    }
    String cropSummary(int width,int height) {
        if(model==null)return "0 个房间";
        long count=current().rooms().stream().filter(p -> p.x()>=width||p.y()>=height).count();
        return count+" 个房间将被裁掉；它们的通路、起点、Boss 和封印坐标引用一并移除。MAP 文件保留。";
    }
    private void resize() {
        if(model==null)return; var layout=current().layout(); JSpinner width=new JSpinner(new SpinnerNumberModel(layout.width(),1,50,1)), height=new JSpinner(new SpinnerNumberModel(layout.height(),1,50,1));
        JPanel form=row(); form.add(new JLabel("宽")); form.add(width); form.add(new JLabel("高")); form.add(height);
        if(JOptionPane.showConfirmDialog(this,form,"布局尺寸",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        int w=(Integer)width.getValue(),h=(Integer)height.getValue(); boolean crop=current().rooms().stream().anyMatch(p -> p.x()>=w||p.y()>=h);
        if(crop&&JOptionPane.showConfirmDialog(this,cropSummary(w,h),"确认裁剪引用",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        change(() -> model.resize(maze,w,h,crop));
    }
    private final class Grid extends JComponent {
        Grid() { setOpaque(true); addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { selectRoom(new Position(e.getX()/64,e.getY()/64)); } }); }
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics); if(model==null)return; Graphics2D g=(Graphics2D)graphics.create();
            try {
                var room=current(); var layout=room.layout(); var hell=model.hell(maze); g.setStroke(new BasicStroke(3));
                for(int y=0;y<layout.height();y++)for(int x=0;x<layout.width();x++) {
                    Position p=new Position(x,y); int px=x*64,py=y*64; boolean exists=room.rooms().contains(p);
                    g.setColor(exists?new Color(62,78,99):new Color(35,40,47)); g.fillRoundRect(px+5,py+5,54,54,8,8);
                    g.setColor(p.equals(position)?new Color(90,185,255):new Color(100,110,124)); g.drawRoundRect(px+5,py+5,54,54,8,8);
                    for(Direction d:Direction.values())if(layout.connected(p,d))g.drawLine(px+32,py+32,px+32+d.dx*32,py+32+d.dy*32);
                    String flags=(layout.starts().contains(p)?"S":"")+(layout.bosses().contains(p)?"B":"")+(p.equals(hell.position())?"H":"");
                    g.setColor(Color.WHITE); g.drawString(exists?flags.isEmpty()?x+","+y:flags:"+",px+16,py+32);
                    if(exists&&layout.mask(p)==0&&!room.playable(p)) { g.setColor(Color.ORANGE); g.drawString("待连通",px+10,py+50); }
                }
            } finally { g.dispose(); }
        }
    }
}
