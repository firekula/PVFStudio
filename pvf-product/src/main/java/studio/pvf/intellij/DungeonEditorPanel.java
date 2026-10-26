package studio.pvf.intellij;

import studio.pvf.sceneedit.*;
import studio.pvf.preview.DungeonScript.*;
import studio.pvf.preview.DungeonMinimap;
import studio.pvf.preview.NpkResources;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private DungeonMinimap minimap;
    private String minimapPath="";
    private final JLabel minimapStatus=named(new JLabel("示意布局 · 选择资源目录后使用原生小地图"),"dgn-minimap-status");
    private final JTabbedPane inspector = named(new JTabbedPane(),"dgn-inspector-tabs");
    private final JComboBox<Position> rooms = named(new JComboBox<>(),"dgn-select-room");
    private final JLabel selection = new JLabel("选择房间");
    private final JLabel status = new JLabel("正在解析…");
    private final JTextArea diagnostics = new JTextArea(3,20);
    private final JButton diagnosticDetails=named(new JButton("布局问题…"),"dgn-diagnostics-details");
    private final MultiSelectDropdown<Integer> maps = named(new MultiSelectDropdown<>(),"dgn-room-maps"), bossMaps = named(new MultiSelectDropdown<>(),"dgn-room-boss-maps");
    private final MultiSelectDropdown<Position> startRooms=named(new MultiSelectDropdown<>(),"dgn-start-rooms"),bossRooms=named(new MultiSelectDropdown<>(),"dgn-boss-rooms");
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
    private Map<Integer,DungeonMapChecks.Check> mapChecks;
    private final JComboBox<DungeonMapChecks.Check> mapProblems=new JComboBox<>();
    private DungeonDocument model;
    private SceneCatalog catalog;
    private long version;
    private Position position = new Position(0,0);
    private int maze;
    private boolean changing, writable;
    private boolean mapsAutomatic,bossMapsAutomatic,mapsEdited,bossMapsEdited;
    private final JLabel mapsOrigin=new JLabel(),bossMapsOrigin=new JLabel();
    record HellTemplate(String path,DungeonDocument.HellSettings settings) {public String toString(){return path+" · MAP #"+settings.mapId()+" · 击打 "+settings.hitCounts()+" · 出现率 "+settings.appearRate()+" · 金币 "+settings.coinLimit();}}
    private Consumer<Consumer<List<HellTemplate>>> templates=done -> done.accept(List.of());
    private Function<List<HellTemplate>,HellTemplate> templatePicker=this::pickTemplate;
    void onHellTemplates(Consumer<Consumer<List<HellTemplate>>> loader){templates=loader;}
    void hellTemplatePicker(Function<List<HellTemplate>,HellTemplate> picker){templatePicker=picker;}
    private SceneViewState view;
    private Map<String,String> restoredState;
    private Consumer<Preview> cloneMap = request -> {};

    DungeonEditorPanel(Edit edit, Consumer<String> openMap, Runnable previewChanged, Consumer<SourceDocument.Range> reveal) {
        super(new BorderLayout(4,4)); this.edit=edit; this.openMap=openMap; this.previewChanged=previewChanged; this.reveal=reveal;
        JPanel top = row(); top.add(new JLabel("副本布局")); mazes.setPreferredSize(new Dimension(130,26)); top.add(mazes);
        top.add(action("新建布局","dgn-new-maze",() -> change(() -> model.addMaze(3,3))));
        top.add(action("复制布局","dgn-copy-maze",() -> change(() -> model.copyMaze(maze))));
        top.add(action("调整尺寸…","dgn-resize",this::resize)); add(top,BorderLayout.NORTH);
        JPanel layout=new JPanel(new BorderLayout(0,3));
        JPanel location=row();location.add(new JLabel("房间"));rooms.setPreferredSize(new Dimension(125,26));location.add(rooms);layout.add(location,BorderLayout.NORTH);layout.add(grid);
        JLabel legend=new JLabel("S 起点 · B Boss · H 深渊 · 蓝框为选中房间");legend.setFont(legend.getFont().deriveFont(11f));
        JPanel caption=new JPanel(new GridLayout(2,1));caption.add(legend);minimapStatus.setFont(minimapStatus.getFont().deriveFont(11f));caption.add(minimapStatus);layout.add(caption,BorderLayout.SOUTH);layout.setBorder(BorderFactory.createEmptyBorder(3,6,3,6));
        rooms.addActionListener(e -> {if(!changing&&rooms.getSelectedItem() instanceof Position p)selectRoom(p);});
        JPanel properties=page();properties.add(selection);
        field(properties,"普通 MAP 候选",maps);properties.add(mapsOrigin);field(properties,"Boss MAP 候选",bossMaps);properties.add(bossMapsOrigin);
        maps.onChange(values -> {if(!changing)mapsEdited=true;});bossMaps.onChange(values -> {if(!changing)bossMapsEdited=true;});
        JPanel automatic=row();automatic.add(action("普通恢复自动","dgn-auto-maps",() -> {maps.selected(List.of());mapsEdited=true;mapsOrigin.setText("应用后恢复自动选图");}));automatic.add(action("Boss 恢复自动","dgn-auto-boss-maps",() -> {bossMaps.selected(List.of());bossMapsEdited=true;bossMapsOrigin.setText("应用后恢复自动 Boss 池");}));properties.add(automatic);
        JPanel applying=row();applying.add(action("创建 / 应用候选","dgn-apply-maps",() -> change(() -> {
            List<Integer> ids=!mapsEdited?current().layout().maps().getOrDefault(position,List.of()):maps.selected();
            List<Integer> bossIds=!bossMapsEdited?current().layout().bossMaps().getOrDefault(position,List.of()):bossMaps.selected();
            if(!current().rooms().contains(position))return model.createRoom(maze,position,ids,bossIds);
            var changes=new ArrayList<ScriptEdit>();
            if(mapsEdited)changes.add(model.setCandidates(maze,position,false,ids));
            if(bossMapsEdited)changes.add(model.setCandidates(maze,position,true,bossIds));
            return changes.isEmpty()?new ScriptEdit("设置房间 MAP 候选",model.source().text(),List.of()):ScriptEdit.combine("设置房间 MAP 候选",changes);
        })));applying.add(action("克隆 MAP 并绑定…","dgn-clone-map",() -> cloneMap.accept(preview())));properties.add(applying);
        JPanel nav=row();nav.add(button("打开房间 MAP","dgn-open-map",this::openSelected));nav.add(action("删除房间","dgn-delete-room",() -> change(() -> model.removeRoom(maze,position))));properties.add(nav);
        inspector.addTab("房间",properties);
        JPanel roles=page();field(roles,"候选起点（可多选）",startRooms);field(roles,"候选 Boss 房间（可多选）",bossRooms);writes.add(startRooms);writes.add(bossRooms);
        startRooms.onCommit(values -> {if(!changing)change(() -> model.setStarts(maze,values));});bossRooms.onCommit(values -> {if(!changing)change(() -> model.setBosses(maze,values));});
        JPanel flags=row();flags.add(start);flags.add(boss);roles.add(flags);writes.add(start);writes.add(boss);
        start.addActionListener(e -> {if(!changing)change(() -> model.setStarts(maze,toggle(current().layout().starts(),start.isSelected())));});
        boss.addActionListener(e -> {if(!changing)change(() -> model.setBosses(maze,toggle(current().layout().bosses(),boss.isSelected())));});
        JPanel only=row();only.add(action("仅此房为起点","dgn-only-start",() -> change(() -> model.setStarts(maze,List.of(position)))));only.add(action("仅此房为 Boss","dgn-only-boss",() -> change(() -> model.setBosses(maze,List.of(position)))));roles.add(only);
        JPanel links=row();links.add(new JLabel("通路"));for(Direction d:Direction.values()){
            JCheckBox door=named(new JCheckBox(d.label),"dgn-connect-"+d.name().toLowerCase(Locale.ROOT));doors.put(d,door);links.add(door);writes.add(door);
            door.addActionListener(e -> {if(!changing)change(() -> checkedConnection(d,door.isSelected()));});
        }roles.add(links);roles.add(note("所有候选都会标记在布局中；通路同时修改相邻房间。"));inspector.addTab("起终点",roles);
        JPanel deep=page();deep.add(hellEnabled);deep.add(escape);writes.add(hellEnabled);writes.add(escape);
        field(deep,"封印 MAP 编号",hellMap);deep.add(hellPosition);
        JPanel hellActions=row();hellActions.add(action("设为当前房间","dgn-hell-here",() -> change(() -> hell(position))));hellActions.add(action("清除封印房","dgn-hell-clear",() -> change(() -> newHell(0,null))));deep.add(hellActions);
        hellEnabled.addActionListener(e -> {if(!changing){if(hellEnabled.isSelected()&&(model.hell(maze).hitCounts().isEmpty()||model.hell(maze).mapId()<=0||model.hell(maze).position()==null))enableFromTemplate();else change(() -> hell(model.hell(maze).position()));}});
        deep.add(action("从同包副本选择深渊模板…","dgn-hell-template",this::enableFromTemplate));
        JPanel deepFiles=row();deepFiles.add(button("编辑封印 MAP","dgn-open-hell-map",() -> {if(model!=null&&catalog!=null){var path=catalog.maps().get(model.hell(maze).mapId());if(path!=null)openMap.accept(path);}}));
        deepFiles.add(button("hellparty.etc","dgn-open-hellparty",() -> {if(catalog!=null&&catalog.paths().contains("etc/hellparty.etc"))openMap.accept("etc/hellparty.etc");else status.setText("当前包没有 etc/hellparty.etc");}));deep.add(deepFiles);
        escape.addActionListener(e -> {if(!changing)change(() -> hell(model.hell(maze).position()));});inspector.addTab("深渊",deep);
        JPanel parameters=page();field(parameters,"击打阈值（全局，保留原字段数量）",hits);field(parameters,"出现率（全局原始值）",rate);field(parameters,"金币限制（旧版本全局原始值）",coin);
        parameters.add(action("应用深渊参数","dgn-apply-hell",() -> change(() -> hell(model.hell(maze).position()))));parameters.add(note("封印房与 Boss 位置独立。概率单位未经确认，保留原始值。"));inspector.addTab("参数",parameters);
        JPanel inspect=page();inspect.add(new JLabel("仅预览，不修改配置"));inspect.add(previewMode);inspect.add(previewMaps);
        previewMode.addActionListener(e -> {if(!changing)previewChanged.run();});previewMaps.addActionListener(e -> {if(!changing)previewChanged.run();});
        inspect.add(new JSeparator());inspect.add(mapProblems);inspect.add(button("打开 MAP 检查项源码","dgn-open-map-problem",() -> {var item=(DungeonMapChecks.Check)mapProblems.getSelectedItem();if(item!=null)openMap.accept(item.path());}));
        inspect.add(button("定位布局源码","dgn-reveal",() -> {if(model==null)return;var sections=model.source().sections("maze info");if(maze<sections.size())reveal.accept(sections.get(maze).heading().range());}));inspector.addTab("预览",inspect);
        for(int i=0;i<inspector.getTabCount();i++)for(Component child:((Container)inspector.getComponentAt(i)).getComponents())if(child instanceof JComponent c){c.setAlignmentX(LEFT_ALIGNMENT);if(c instanceof JComboBox||c instanceof JSeparator)c.setMaximumSize(new Dimension(Integer.MAX_VALUE,c instanceof JSeparator?8:28));}
        JPanel settings=new JPanel(new BorderLayout(0,3));settings.add(layout,BorderLayout.NORTH);settings.add(inspector);settings.setMinimumSize(new Dimension(324,160));settings.setPreferredSize(new Dimension(350,400));
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,previewHost,settings); split.setResizeWeight(.72); split.setDividerLocation(540); add(split);
        diagnostics.setEditable(false); diagnostics.setLineWrap(true); diagnostics.setWrapStyleWord(true);
        JPanel footer=new JPanel(new BorderLayout(6,0));footer.add(status);diagnosticDetails.setVisible(false);footer.add(diagnosticDetails,BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        diagnosticDetails.addActionListener(e -> {var text=new JTextArea(diagnostics.getText(),10,50);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setCaretPosition(0);JOptionPane.showMessageDialog(this,new JScrollPane(text),"当前布局问题",JOptionPane.WARNING_MESSAGE);});
        mazes.addActionListener(e -> { if(!changing && model!=null) { maze=Math.max(0,mazes.getSelectedIndex()); update(); } });
        grid.setName("dgn-editor-grid"); setMinimumSize(new Dimension(300,200));
    }
    private static <T extends JComponent> T named(T c,String name) { c.setName(name); return c; }
    private static JPanel row() { var p=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2)); p.setAlignmentX(LEFT_ALIGNMENT); p.setMaximumSize(new Dimension(Integer.MAX_VALUE,36)); return p; }
    private static JPanel page(){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(BorderFactory.createEmptyBorder(6,7,6,7));return p;}
    private static JTextArea note(String text){var c=new JTextArea(text);c.setEditable(false);c.setLineWrap(true);c.setWrapStyleWord(true);c.setOpaque(false);c.setRows(2);c.setMaximumSize(new Dimension(Integer.MAX_VALUE,50));return c;}
    private static void field(JPanel p,String title,JComponent value) { p.add(new JLabel(title)); value.setMaximumSize(new Dimension(Integer.MAX_VALUE,28)); p.add(value); }
    private JButton button(String title,String name,Runnable run) { JButton b=named(new JButton(title),name); b.setAlignmentX(LEFT_ALIGNMENT); b.addActionListener(e -> run.run()); return b; }
    private JButton action(String title,String name,Runnable run) { JButton b=button(title,name,run); writes.add(b); return b; }
    JPanel previewHost() { return previewHost; }
    Position position() { return position; }
    String minimapImage(){if(model==null)return "";return model.source().sections("minimap image").stream().filter(s -> !s.values().isEmpty()).reduce((a,b) -> b).map(s -> s.values().getFirst().value()).orElse("");}
    void setMinimap(String path,DungeonMinimap atlas,String reason){
        if(!NpkResources.normalize(path).equals(NpkResources.normalize(minimapImage())))return;
        minimapPath=path;minimap=atlas;minimapStatus.setText(atlas==null?"示意布局 · "+reason:"原生 NPK 小地图");minimapStatus.setToolTipText(atlas==null?reason:path);grid.repaint();
    }
    boolean activeHellAt(Position p) { if(model==null)return false;var h=model.hell(maze);return h.enabled()&&!h.excluded()&&h.mapId()>0&&p!=null&&p.equals(h.position()); }
    void selectSourceOffset(int offset) {
        if(model==null)return;var sections=model.source().sections("maze info");int target=0;
        for(int i=0;i<sections.size();i++)if(offset>=sections.get(i).heading().range().start())target=i;
        if(target>=model.mazes().size())return;
        int begin=sections.isEmpty()?0:sections.get(target).heading().range().start(),end=target+1<sections.size()?sections.get(target+1).heading().range().start():model.source().text().length();
        for(var section:model.source().sections())if(section.heading().range().start()>=begin&&section.heading().range().start()<end) {
            if(section.tag().equals("greed"))for(var token:section.values())if(offset>=token.range().start()+1&&offset<token.range().end()-1){
                int cell=(offset-token.range().start()-1)/2;var layout=model.mazes().get(target).layout();
                if(cell<layout.width()*layout.height()){maze=target;changing=true;mazes.setSelectedIndex(maze);changing=false;selectRoom(new Position(cell%layout.width(),cell/layout.width()));}return;
            }
        }
    }
    Preview preview() { boolean deep=previewMode.getSelectedIndex()==1&&activeHellAt(model==null?null:model.hell(maze).position());Integer selected=(Integer)previewMaps.getSelectedItem();if(deep&&activeHellAt(position))selected=model.hell(maze).mapId();return new Preview(maze,position,selected,deep); }
    void captureView(SceneCanvas canvas){view=SceneViewState.capture(canvas);}
    void restoreView(SceneCanvas canvas){if(view!=null)view.apply(canvas);}
    Map<String,String> captureState(){var state=new HashMap<String,String>();if(view!=null)view.save(state);state.put("maze",Integer.toString(maze));state.put("roomX",Integer.toString(position.x()));state.put("roomY",Integer.toString(position.y()));return Map.copyOf(state);}
    void restoreState(Map<String,String> state){view=SceneViewState.read(state);if(model==null){restoredState=state;return;}try{maze=Math.clamp(Integer.parseInt(state.get("maze")),0,model.mazes().size()-1);position=new Position(Integer.parseInt(state.get("roomX")),Integer.parseInt(state.get("roomY")));changing=true;mazes.setSelectedIndex(maze);changing=false;update();}catch(RuntimeException invalid){}}
    void onClone(Consumer<Preview> handler) { cloneMap=handler; }
    void setMapChecks(Map<Integer,DungeonMapChecks.Check> checks){mapChecks=Map.copyOf(checks);mapProblems.removeAllItems();checks.values().stream().filter(c -> !c.messages().isEmpty()).sorted(Comparator.comparing(DungeonMapChecks.Check::path)).forEach(mapProblems::addItem);}
    private ScriptEdit checkedConnection(Direction direction,boolean enabled)throws IOException{
        if(enabled&&mapChecks!=null){var layout=current().layout();for(var p:List.of(position,layout.neighbor(position,direction))){
            var ids=new ArrayList<>(layout.maps().getOrDefault(p,List.of()));ids.addAll(layout.bossMaps().getOrDefault(p,List.of()));if(ids.isEmpty())throw new IOException("请先指定两端房间的 MAP，以验证原生门坐标");
            int gate=p.equals(position)?direction.ordinal():direction.opposite().ordinal();for(int id:ids){var check=mapChecks.get(id);if(check==null||!check.hasGate(gate))throw new IOException("MAP #"+id+" 未验证可用的原生门坐标；请打开 MAP 检查项修正");}
        }}return model.connect(maze,position,direction,enabled);
    }
    void setCatalog(SceneCatalog value) {catalog=value;var items=new LinkedHashMap<Integer,String>();value.maps().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> items.put(e.getKey(),e.getKey()+" · "+e.getValue()));maps.options(items);bossMaps.options(items);}
    void resolvedChoices(DungeonPanel.Selection value) {
        if(value==null||model==null||!value.room().position().equals(position)||!value.dungeon().definition().equals(model.definition()))return;
        changing=true;
        try {
            var normal=value.dungeon().candidates(maze,position,false).stream().map(map -> map.id()).toList();
            var bosses=value.dungeon().candidates(maze,position,true).stream().map(map -> map.id()).toList();
            if(mapsAutomatic&&!mapsEdited){maps.selected(normal);mapsOrigin.setText("自动选图池 · "+normal.size()+" 项；应用修改后固定");}
            if(bossMapsAutomatic&&!bossMapsEdited){bossMaps.selected(bosses);bossMapsOrigin.setText("自动 Boss 池 · "+bosses.size()+" 项；应用修改后固定");}
            previewMaps.removeAllItems();value.room().choices().forEach(map -> previewMaps.addItem(map.id()));if(value.map()!=null)previewMaps.setSelectedItem(value.map().id());
        }finally{changing=false;}
    }
    void install(DungeonDocument value,long stamp,boolean canWrite,String error) {
        model=value; version=stamp; writable=canWrite; status.setText(error.isBlank()?"编辑写入当前文档 · Ctrl+S 保存草稿":error); status.setToolTipText(error);
        if(!NpkResources.normalize(minimapPath).equals(NpkResources.normalize(minimapImage()))){minimap=null;minimapPath=minimapImage();minimapStatus.setText("示意布局 · 等待小地图资源");}
        if(model==null) { writes.forEach(c -> c.setEnabled(false)); return; }
        changing=true; int selected=maze; mazes.removeAllItems();
        for(int i=0;i<model.mazes().size();i++)mazes.addItem((i+1)+" · "+model.mazes().get(i).layout());
        maze=Math.min(selected,model.mazes().size()-1); mazes.setSelectedIndex(maze); changing=false; if(restoredState!=null){var state=restoredState;restoredState=null;restoreState(state);}update();
    }
    private DungeonDocument.Maze current() { return model.mazes().get(maze); }
    void selectRoom(Position value) { if(model!=null && value.x()>=0 && value.y()>=0 && value.x()<current().layout().width() && value.y()<current().layout().height()) { position=value; update(); } }
    private void update() {
        changing=true;
        try {
            var room=current(); var layout=room.layout(); if(position.x()>=layout.width()||position.y()>=layout.height())position=new Position(0,0);
            boolean exists=room.rooms().contains(position), editable=writable&&!model.source().sections("maze info").isEmpty();
            writes.forEach(c -> c.setEnabled(editable));maps.setEnabled(editable);bossMaps.setEnabled(editable);hellMap.setEditable(editable);hits.setEditable(editable);rate.setEditable(editable);
            mapsAutomatic=layout.maps().getOrDefault(position,List.of()).isEmpty();bossMapsAutomatic=layout.bossMaps().getOrDefault(position,List.of()).isEmpty();mapsEdited=false;bossMapsEdited=false;mapsOrigin.setText(mapsAutomatic?"自动选图池，正在解析候选…":"本房间固定的普通 MAP 候选");bossMapsOrigin.setText(bossMapsAutomatic?"自动 Boss 池，正在解析候选…":"本房间固定的 Boss MAP 候选");
            selection.setText("房间 "+position+(exists?"":" · 空格，指定 MAP 后创建")); maps.selected(layout.maps().getOrDefault(position,List.of()));bossMaps.selected(layout.bossMaps().getOrDefault(position,List.of()));
            var positions=new LinkedHashMap<Position,String>();rooms.removeAllItems();
            for(int y=0;y<layout.height();y++)for(int x=0;x<layout.width();x++){var p=new Position(x,y);rooms.addItem(p);if(room.rooms().contains(p))positions.put(p,"房间 "+p);}
            rooms.setSelectedItem(position);startRooms.options(positions);bossRooms.options(positions);startRooms.selected(layout.starts());bossRooms.selected(layout.bosses());
            start.setSelected(layout.starts().contains(position)); boss.setSelected(layout.bosses().contains(position)); start.setEnabled(editable&&exists); boss.setEnabled(editable&&exists);
            for(var item:doors.entrySet()) { item.getValue().setSelected(layout.connected(position,item.getKey())); item.getValue().setEnabled(editable&&exists&&room.rooms().contains(layout.neighbor(position,item.getKey()))); }
            var hell=model.hell(maze); hellEnabled.setSelected(hell.enabled()); escape.setSelected(hell.excluded()); escape.setEnabled(editable&&model.supportsEscape());
            escape.setToolTipText(model.supportsEscape()?"只作用于当前布局":"没有客户端支持 escape hell 的证据；可查看原始源码");
            hellMap.setText(hell.mapId()>0?Integer.toString(hell.mapId()):""); hellPosition.setText("封印房坐标："+(hell.position()==null?"未设置":hell.position()));
            previewMode.setEnabled(hell.enabled()&&!hell.excluded()&&hell.position()!=null&&hell.mapId()>0);if(!previewMode.isEnabled())previewMode.setSelectedIndex(0);
            coin.setEditable(writable&&hell.coinLimit()!=null);
            hits.setText(join(hell.hitCounts())); rate.setText(hell.appearRate()==null?"":hell.appearRate().toString()); coin.setText(hell.coinLimit()==null?"":hell.coinLimit().toString());
            previewMaps.removeAllItems(); var choices=layout.bosses().contains(position)&&!layout.bossMaps().getOrDefault(position,List.of()).isEmpty()?layout.bossMaps().get(position):layout.maps().getOrDefault(position,List.of()); choices.forEach(previewMaps::addItem);
            var issues=model.diagnostics().stream().filter(d -> d.maze()==maze).toList();diagnostics.setText(String.join("\n",issues.stream().map(d -> d.severity()+" "+d.message()).toList()));diagnosticDetails.setText("布局问题 ("+issues.size()+")…");diagnosticDetails.setVisible(!issues.isEmpty());
            grid.setPreferredSize(new Dimension(300,108)); grid.revalidate(); grid.repaint();
        } finally { changing=false; }
        previewChanged.run();
    }
    private List<Position> toggle(List<Position> before,boolean enabled) { var values=new ArrayList<>(before); values.remove(position); if(enabled)values.add(position); return values; }
    private static String join(List<Integer> values) { return String.join(", ",values.stream().map(Object::toString).toList()); }
    private static List<Integer> numbers(String text) throws IOException {
        try { return text.isBlank()?List.of():Arrays.stream(text.trim().split("[,，\\s]+")).map(Integer::valueOf).toList(); }
        catch(NumberFormatException invalid) { throw new IOException("请输入整数编号，用逗号分隔"); }
    }
    private static Integer optional(String text) { return text.isBlank()?null:Integer.valueOf(text.trim()); }
    private HellTemplate pickTemplate(List<HellTemplate> values){
        return (HellTemplate)JOptionPane.showInputDialog(this,"选择当前 PVF 的现有配置（下列为应用后的原始参数）","深渊模板",JOptionPane.PLAIN_MESSAGE,null,values.toArray(),values.getFirst());
    }
    private void enableFromTemplate(){
        if(model==null||!writable)return;long expected=version;int target=maze;String baseline=model.source().text();status.setText("读取当前 PVF 的深渊模板…");
        templates.accept(values -> {
            if(version!=expected||maze!=target||!writable||!model.source().text().equals(baseline)){status.setText("文档已变化，未应用深渊模板");return;}
            var before=model.hell(maze);var prepared=values.stream().map(t -> new HellTemplate(t.path(),new DungeonDocument.HellSettings(true,before.excluded(),before.mapId()>0?before.mapId():t.settings().mapId(),before.position()!=null?before.position():position,before.hitCounts().isEmpty()?t.settings().hitCounts():before.hitCounts(),before.appearRate()!=null?before.appearRate():t.settings().appearRate(),before.coinLimit()!=null?before.coinLimit():t.settings().coinLimit()))).toList();
            if(prepared.isEmpty()){update();status.setText("同包没有可验证的深渊模板；请在代码中提供已确认的原生配置");return;}
            var selected=templatePicker.apply(prepared);if(selected==null){update();return;}
            if(version!=expected||maze!=target||!writable||!model.source().text().equals(baseline)){status.setText("文档已变化，未应用深渊模板");return;}
            change(() -> model.setHell(maze,selected.settings()));
        });
    }
    private ScriptEdit hell(Position at) throws IOException { return newHell(hellMap.getText().isBlank()?0:Integer.parseInt(hellMap.getText().trim()),at); }
    private ScriptEdit newHell(int mapId,Position at) throws IOException {
        return model.setHell(maze,new DungeonDocument.HellSettings(hellEnabled.isSelected(),escape.isSelected(),mapId,at,numbers(hits.getText()),optional(rate.getText()),optional(coin.getText())));
    }
    @FunctionalInterface private interface Command { ScriptEdit make() throws Exception; }
    private void change(Command command) {
        if(changing||!writable||model==null)return;
        try { edit.apply(version,command.make()); }
        catch(Exception failure) { if(model!=null)update();status.setText("无法应用："+failure.getMessage()); status.setToolTipText(status.getText()); }
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
        Grid(){setOpaque(true);setBackground(Optional.ofNullable(UIManager.getColor("Panel.background")).orElse(new Color(45,48,53)));setToolTipText("");addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){if(model==null)return;var m=metrics();selectRoom(new Position((int)Math.floor((e.getX()-m[1])/m[0]),(int)Math.floor((e.getY()-m[2])/m[0])));}});}
        private double[] metrics(){var l=current().layout();double w=getWidth()>0?getWidth():l.width()*64,h=getHeight()>0?getHeight():l.height()*64;double cell=Math.min(64,Math.min(w/l.width(),h/l.height()));return new double[]{cell,(w-cell*l.width())/2,(h-cell*l.height())/2};}
        @Override public String getToolTipText(MouseEvent e){if(model==null)return null;var m=metrics();int x=(int)Math.floor((e.getX()-m[1])/m[0]),y=(int)Math.floor((e.getY()-m[2])/m[0]);if(x<0||y<0||x>=current().layout().width()||y>=current().layout().height())return null;var p=new Position(x,y);return "房间 "+p+(current().layout().starts().contains(p)?" · 起点候选":"")+(current().layout().bosses().contains(p)?" · Boss 候选":"")+(activeHellAt(p)?" · 深渊封印":"");}
        protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);graphics.setColor(getBackground());graphics.fillRect(0,0,getWidth(),getHeight());if(model==null)return;Graphics2D g=(Graphics2D)graphics.create();
            try{
                var room=current();var l=room.layout();var m=metrics();g.translate(m[1],m[2]);g.scale(m[0]/64,m[0]/64);g.setStroke(new BasicStroke(2));
                for(int y=0;y<l.height();y++)for(int x=0;x<l.width();x++){
                    Position p=new Position(x,y);int px=x*64,py=y*64;boolean exists=room.rooms().contains(p);
                    var tile=minimap==null||!exists?null:minimap.tile(l.mask(p));
                    if(tile!=null){g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(tile,px,py,64,64,null);}
                    else{
                        g.setColor(exists?new Color(62,78,99):new Color(35,40,47));g.fillRoundRect(px+4,py+4,56,56,8,8);
                        g.setColor(new Color(100,110,124));g.drawRoundRect(px+4,py+4,56,56,8,8);
                        for(Direction d:Direction.values())if(l.connected(p,d))g.drawLine(px+32,py+32,px+32+d.dx*32,py+32+d.dy*32);
                    }
                    if(tile!=null&&l.bosses().contains(p)&&minimap.boss()!=null)g.drawImage(minimap.boss(),px+22,py+25,24,21,null);
                    g.setColor(Color.WHITE);g.drawString(exists?x+","+y:"+",px+17,py+(tile==null?32:57));
                    int badgeX=px+7;if(l.starts().contains(p)){badge(g,"S",badgeX,py+7,new Color(66,190,125));badgeX+=17;}
                    if(l.bosses().contains(p)){badge(g,"B",badgeX,py+7,new Color(245,157,66));badgeX+=17;}
                    if(activeHellAt(p))badge(g,"H",badgeX,py+7,new Color(194,123,235));
                    if(p.equals(position)){g.setColor(new Color(90,185,255));g.setStroke(new BasicStroke(3));g.drawRoundRect(px+2,py+2,60,60,8,8);g.setStroke(new BasicStroke(2));}
                    if(exists&&l.mask(p)==0&&!room.playable(p)){g.setColor(Color.ORANGE);g.drawString("待连通",px+10,py+50);}
                }
            }finally{g.dispose();}
        }
        private void badge(Graphics2D g,String text,int x,int y,Color color){g.setColor(color);g.fillRoundRect(x,y,15,15,4,4);g.setColor(new Color(20,30,35));g.setFont(g.getFont().deriveFont(Font.BOLD,11f));g.drawString(text,x+4,y+12);}
    }
}
