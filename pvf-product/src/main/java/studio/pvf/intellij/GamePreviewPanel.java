package studio.pvf.intellij;

import studio.pvf.preview.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** One active player preview. Archive/IMG work never runs on EDT and cancellation never interrupts IO. */
final class GamePreviewPanel extends JPanel implements AutoCloseable {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"pvf-game-preview");t.setDaemon(true);return t;});
    private final Supplier<NpkResources> resources;
    private final Runnable chooseResources;
    private final JComboBox<Choice> job=new JComboBox<>(),action=new JComboBox<>();
    private final JComboBox<Object> effect=new JComboBox<>();
    private final JSpinner level=new JSpinner(new SpinnerNumberModel(1,1,1000,1));
    private final JLabel levelLabel=new JLabel("技能等级"),message=new JLabel("选择装备、装扮或技能");
    private final JButton play=new JButton("播放"),facing=new JButton("朝右"),choose=new JButton("选择资源目录");
    private final JComboBox<String> speed=new JComboBox<>(new String[]{"0.25×","0.5×","1×","2×","4×"});
    private final JComboBox<String> zoom=new JComboBox<>(new String[]{"角色视角","全效果","100%","200%","400%"});
    private final JCheckBox loop=new JCheckBox("循环",true);
    private final JCheckBox hitBoxes=new JCheckBox("判定框");
    private final JCheckBox boxCoordinates=new JCheckBox("框坐标");
    private final JSlider timeline=new JSlider(0,1000,0);
    private final JLabel time=new JLabel("0 ms");
    private final GameTooltipPanel info=new GameTooltipPanel();
    private final JEditorPane details=new JEditorPane("text/html","");
    private final JLabel itemIcon=new JLabel();
    private final DefaultListModel<Wear> wearing=new DefaultListModel<>();
    private final JList<Wear> outfit=new JList<>(wearing);
    private final Canvas canvas=new Canvas();
    private final JTabbedPane inspector=new JTabbedPane();
    private final JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final javax.swing.Timer timer=new javax.swing.Timer(16,e->requestFrame());
    private final AnimationTimeline.Clock clock=new AnimationTimeline.Clock();
    private final Map<String,Map<String,String>> outfits=new HashMap<>();
    private final Map<String,String> rememberedEffects=new HashMap<>();
    private final Map<String,Integer> levels=new HashMap<>();
    private ResourcePreviewPanel.Source source;
    private Set<String> paths=Set.of();
    private GamePreview model;
    private volatile long generation;
    private boolean changing,closed,act1,mirrored,renderPending,renderAgain,playing,autoPlayPending;
    private String selectedJob="gunner",selectedAction="waiting motion",selectedEffect="";
    private Path settings;
    private long settingsGeneration,choiceRevision;
    private boolean resetSelectedSlot;
    private boolean dividerPlaced;
    private record Choice(String key,String label){@Override public String toString(){return label;}}
    private record Wear(String slot,String path){@Override public String toString(){return slot+" · "+path.substring(path.lastIndexOf('/')+1);}}
    private record Loaded(GameItemInfo info,GamePreview preview,GamePreview.Rendered frame,String job,String slot,int maximum,int level,java.awt.image.BufferedImage icon,String iconWarning) {}

    GamePreviewPanel(Supplier<NpkResources> resources,Runnable chooseResources) {
        super(new BorderLayout(0,4));this.resources=resources;this.chooseResources=chooseResources;setName("game-preview");setMinimumSize(new Dimension(260,260));
        JPanel header=new JPanel();header.setLayout(new BoxLayout(header,BoxLayout.Y_AXIS));
        JPanel controls=new JPanel(new Wrap(5,3));
        for(String key:CharacterAppearance.JOBS)job.addItem(new Choice(key,jobLabel(key)));
        job.setName("game-job");action.setName("game-action");level.setName("game-level");effect.setName("game-effect");choose.setName("game-choose-resources");
        controls.add(new JLabel("职业"));controls.add(job);controls.add(new JLabel("动作"));controls.add(action);controls.add(facing);controls.add(levelLabel);controls.add(level);controls.add(choose);header.add(controls);
        effect.setMaximumRowCount(12);effect.setToolTipText("配置明确引用的动画、对象初始视觉或已验证的技能配方");header.add(effect);effect.setVisible(false);
        message.setBorder(BorderFactory.createEmptyBorder(2,8,3,8));message.setPreferredSize(new Dimension(250,22));header.add(message);add(header,BorderLayout.NORTH);
        canvas.setMinimumSize(new Dimension(180,180));
        details.setName("game-resource-details");details.setEditable(false);
        JPanel information=new JPanel(new BorderLayout());itemIcon.setName("game-item-icon");itemIcon.setBorder(BorderFactory.createEmptyBorder(6,10,2,10));information.add(itemIcon,BorderLayout.NORTH);information.add(scroll(info));inspector.addTab("游戏说明",information);
        JPanel outfitPage=new JPanel(new BorderLayout(0,4));outfit.setName("game-outfit");outfitPage.add(new JScrollPane(outfit));
        JPanel outfitTools=new JPanel(new Wrap(3,3));JButton add=new JButton("选择穿戴…"),remove=new JButton("隐藏部位"),reset=new JButton("重置");
        add.setName("game-outfit-add");remove.setName("game-outfit-hide");reset.setName("game-outfit-reset");outfitTools.add(add);outfitTools.add(remove);outfitTools.add(reset);outfitPage.add(outfitTools,BorderLayout.SOUTH);
        inspector.addTab("穿戴搭配",outfitPage);inspector.addTab("资源",scroll(details));inspector.setMinimumSize(new Dimension(210,150));
        split.setLeftComponent(canvas);split.setRightComponent(inspector);split.setResizeWeight(.7);split.setBorder(null);add(split);
        JPanel footer=new JPanel();footer.setLayout(new BoxLayout(footer,BoxLayout.Y_AXIS));
        JPanel transport=new JPanel(new Wrap(4,2));JButton previous=new JButton("◀"),next=new JButton("▶");previous.setName("game-previous");next.setName("game-next");play.setName("game-play");timeline.setName("game-timeline");
        hitBoxes.setName("game-hit-boxes");boxCoordinates.setName("game-hit-box-coordinates");boxCoordinates.setVisible(false);
        hitBoxes.setToolTipText("显示三维判定框。单击框查看坐标与尺寸，单击空白取消。");
        boxCoordinates.setToolTipText("同时显示所有判定框的坐标与尺寸。");
        transport.add(previous);transport.add(play);transport.add(next);transport.add(speed);transport.add(loop);transport.add(zoom);
        transport.add(hitBoxes);transport.add(boxCoordinates);transport.add(time);
        speed.setSelectedIndex(2);footer.add(transport);footer.add(timeline);add(footer,BorderLayout.SOUTH);
        job.addActionListener(e->{if(!changing){selectedJob=((Choice)job.getSelectedItem()).key();selectedAction="waiting motion";reload();saveSettings();}});
        action.addActionListener(e->{if(!changing&&action.getSelectedItem() instanceof Choice choice){selectedAction=choice.key();if(source!=null&&source.path().endsWith(".skl"))selectedEffect="actor";reload();saveSettings();}});
        effect.addActionListener(e->{
            if(changing)return;
            selectedEffect=effect.getSelectedItem() instanceof GameEffectRecipes.Effect item?item.key():"";
            if(source!=null){
                rememberedEffects.put(source.path(),selectedEffect);
                autoPlayPending=source.path().endsWith(".skl")&&!selectedEffect.equals("actor");
            }
            reload();
            saveSettings();
        });
        level.addChangeListener(e->{if(!changing){if(source!=null)levels.put(source.path(),((Number)level.getValue()).intValue());reload();saveSettings();}});choose.addActionListener(e->this.chooseResources.run());
        facing.addActionListener(e->{canvas.clearSelection();mirrored=!mirrored;facing.setText(mirrored?"朝左":"朝右");requestFrame();});
        play.addActionListener(e->{if(playing)pause();else startPlayback();});
        previous.addActionListener(e->seek(Math.max(0,clock.milliseconds()-16)));next.addActionListener(e->seek(loop.isSelected()?clock.milliseconds()+16:Math.min(model==null?60000:model.duration(),clock.milliseconds()+16)));
        speed.addActionListener(e->clock.speed(switch(speed.getSelectedIndex()){case 0->.25;case 1->.5;case 3->2;case 4->4;default->1;}));
        timeline.addChangeListener(e->{if(!changing)seek(timeline.getValue());});
        zoom.addActionListener(e->{canvas.freeZoom=0;canvas.panX=0;canvas.panY=0;canvas.repaint();});
        hitBoxes.addActionListener(e->{
            boolean enabled=hitBoxes.isSelected();
            boxCoordinates.setVisible(enabled);
            if(!enabled)canvas.clearSelection();
            transport.revalidate();
            requestFrame();
            canvas.repaint();
        });
        boxCoordinates.addActionListener(e->canvas.repaint());
        add.addActionListener(e->chooseOutfit());remove.addActionListener(e->{if(outfit.getSelectedValue() instanceof Wear selected){outfits.computeIfAbsent(selectedJob,k->new HashMap<>()).put(selected.slot(),"");reload();saveSettings();}});
        reset.addActionListener(e->{outfits.remove(selectedJob);reload();saveSettings();});
        addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent e){responsive();}});
        addHierarchyListener(e->{
            if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)==0)return;
            if(!isShowing()){
                if(model!=null&&model.usingSequence())autoPlayPending=true;
                pause();
            }else{
                long ticket=generation;
                SwingUtilities.invokeLater(()->{if(!closed&&ticket==generation)startPendingPlayback();});
            }
        });
        select(job,selectedJob);level.setVisible(false);levelLabel.setVisible(false);timer.setRepeats(true);
    }
    private static JScrollPane scroll(JComponent pane){var result=new JScrollPane(pane);result.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);result.setBorder(null);return result;}
    private void responsive(){int orientation=getWidth()<610?JSplitPane.VERTICAL_SPLIT:JSplitPane.HORIZONTAL_SPLIT;if(split.getOrientation()!=orientation){split.setOrientation(orientation);split.setResizeWeight(.7);dividerPlaced=false;}if(!dividerPlaced&&getWidth()>0&&getHeight()>0){dividerPlaced=true;SwingUtilities.invokeLater(()->{if(!closed)split.setDividerLocation(.7);});}revalidate();}
    void mount(Path directory) {
        ++generation;pause();settings=directory.resolve("game-preview.properties");outfits.clear();rememberedEffects.clear();levels.clear();
        Path config=settings;long ticket=++settingsGeneration,revision=choiceRevision;
        worker.execute(()->{var p=new Properties();try{if(Files.isRegularFile(config))try(var stream=Files.newInputStream(config)){p.load(stream);}}catch(IOException ignored){}
            SwingUtilities.invokeLater(()->{if(closed||ticket!=settingsGeneration||revision!=choiceRevision)return;selectedJob=p.getProperty("job","gunner");selectedAction=p.getProperty("action","waiting motion");for(String key:p.stringPropertyNames())if(key.startsWith("outfit.")){String[] bits=key.split("\\.",3);if(bits.length==3)outfits.computeIfAbsent(bits[1],k->new HashMap<>()).put(bits[2],p.getProperty(key));}else if(key.startsWith("effect."))rememberedEffects.put(key.substring(7),p.getProperty(key));else if(key.startsWith("level.")){try{levels.put(key.substring(6),Math.max(1,Math.min(1000,Integer.parseInt(p.getProperty(key)))));}catch(NumberFormatException ignored){}}changing=true;select(job,selectedJob);changing=false;if(source!=null)selectedEffect=rememberedEffects.getOrDefault(source.path(),"");reload();});});
    }
    private void saveSettings(){++choiceRevision;if(settings==null)return;Path config=settings;var p=new Properties();p.setProperty("job",selectedJob);p.setProperty("action",selectedAction);outfits.forEach((j,slots)->slots.forEach((s,path)->p.setProperty("outfit."+j+"."+s,path)));levels.forEach((path,value)->p.setProperty("level."+path,value.toString()));rememberedEffects.forEach((path,value)->p.setProperty("effect."+path,value));worker.execute(()->{try{Path tmp=Files.createTempFile(config.getParent(),"game-preview-",".tmp");try{try(var stream=Files.newOutputStream(tmp)){p.store(stream,"PVF Studio preview choices; does not modify scripts");}Files.move(tmp,config,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}}catch(IOException ignored){}});}
    void setSource(ResourcePreviewPanel.Source value,Set<String> available,boolean legacy){
        boolean changed=source==null||value==null||!source.path().equals(value.path());source=value;paths=Set.copyOf(available);act1=legacy;
        if(changed){
            selectedEffect=value==null?"":rememberedEffects.getOrDefault(value.path(),"");
            canvas.panX=canvas.panY=canvas.freeZoom=0;
            resetSelectedSlot=true;
            autoPlayPending=value!=null&&value.path().endsWith(".skl")&&!selectedEffect.equals("actor");
        }
        reload();
    }
    void resourcesChanged(){reload();}
    private void reload() {
        if(closed)return;
        long ticket=++generation;
        if(playing&&model!=null&&model.usingSequence())autoPlayPending=true;
        pause();canvas.clearSelection();renderPending=false;renderAgain=false;model=null;
        if(source==null){canvas.frame=null;canvas.error="选择装备、装扮或技能";canvas.repaint();return;}
        ResourcePreviewPanel.Source snapshot=source;Set<String> files=paths;NpkResources indexed=resources.get();
        String requested=selectedJob,motion=selectedAction,selectedVisual=selectedEffect;int requestedLevel=levels.getOrDefault(snapshot.path(),1);boolean legacy=act1,reset=resetSelectedSlot,mirror=mirrored,debug=hitBoxes.isSelected();
        Map<String,Map<String,String>> wardrobe=new HashMap<>();outfits.forEach((j,slots)->wardrobe.put(j,Map.copyOf(slots)));
        facing.setEnabled(false);
        message.setText("正在组合角色与资源…");canvas.error="正在载入…";canvas.repaint();
        worker.execute(()->{try{
            GameScript.Lookup lookup=p->{if(closed||ticket!=generation)throw new IOException("预览已过期");return p.equals(snapshot.path())?snapshot.text():snapshot.lookup().read(p);};
            String selected=CharacterAppearance.inferJob(snapshot.path(),snapshot.text(),requested);var fields=GameScript.fields(snapshot.text());String slot=snapshot.path().endsWith(".equ")?CharacterAppearance.slot(snapshot.text()):"";
            int maximum=Math.max(1,Math.min(1000,GameScript.integer(GameScript.value(fields,"maximum level"),1))),chosen=Math.min(maximum,requestedLevel);
            var clothes=new HashMap<>(wardrobe.getOrDefault(selected,Map.of()));if(reset&&!slot.isEmpty())clothes.remove(slot);
            var options=new GamePreview.Options(selected,motion,clothes,chosen,legacy,selectedVisual);
            GamePreview preview=indexed==null?null:GamePreview.load(snapshot.path(),snapshot.text(),lookup,files,indexed,options);
            var metadata=preview==null?GameItemInfo.read(snapshot.path(),snapshot.text(),lookup,options.level()):preview.info();
            java.awt.image.BufferedImage icon=null;String iconWarning="";
            try{icon=GameTooltipPanel.readIcon(snapshot.text(),indexed);}catch(IOException failure){iconWarning="图标："+failure.getMessage();}
            var loaded=new Loaded(metadata,preview,preview==null?null:preview.render(0,mirror,false,debug),selected,slot,maximum,chosen,icon,iconWarning);
            SwingUtilities.invokeLater(()->{if(closed||ticket!=generation)return;accept(loaded);});
        }catch(Exception failure){SwingUtilities.invokeLater(()->{if(closed||ticket!=generation)return;canvas.frame=null;canvas.error="预览失败："+failure.getMessage();message.setText("配置无法预览 · 查看代码或重新选择资源");info.showStatus(snapshot.path()+"\n"+canvas.error);facing.setEnabled(true);canvas.repaint();});}});
    }
    private void accept(Loaded loaded) {
        model=loaded.preview();
        boolean skill=source.path().endsWith(".skl");
        boolean playable=model!=null&&model.unavailableReason().isEmpty();
        canvas.frame=playable?loaded.frame():null;
        canvas.error=model==null?"请选择配套的资源目录":model.unavailableReason();
        info.showItem(loaded.info());
        if(info.getParent() instanceof JViewport viewport)viewport.setViewPosition(new Point());
        itemIcon.setIcon(loaded.icon()==null?null:new ImageIcon(loaded.icon()));itemIcon.setVisible(loaded.icon()!=null);itemIcon.setToolTipText(loaded.iconWarning().isEmpty()?"当前配置图标":loaded.iconWarning());
        changing=true;selectedJob=loaded.job();select(job,selectedJob);job.setEnabled(!source.path().contains("/character/"+selectedJob+"/")&&!source.path().startsWith("skill/"+selectedJob+"/"));
        if(resetSelectedSlot){var slots=outfits.get(selectedJob);if(slots!=null)slots.remove(loaded.slot());resetSelectedSlot=false;}
        level.setModel(new SpinnerNumberModel(loaded.level(),1,loaded.maximum(),1));if(source.path().endsWith(".skl"))levels.put(source.path(),loaded.level());level.setVisible(source.path().endsWith(".skl"));levelLabel.setVisible(level.isVisible());
        action.removeAllItems();effect.removeAllItems();effect.addItem(skill?"自动技能动画":"默认效果 / 穿戴外观");wearing.clear();
        if(model!=null){
            if(!playable)action.addItem(new Choice("none","未关联技能动作"));
            model.appearance().actions().forEach((key,value)->{
                String label=model.usingSequence()&&key.equals(model.appearance().action())?"技能预览":CharacterAppearance.actionLabel(key);
                action.addItem(new Choice(key,label.equals(key)&&key.startsWith("etc motion")?"扩展动作 · "+value.substring(value.lastIndexOf('/')+1):label));
            });
            select(action,!playable?"none":model.usingSequence()?model.appearance().action():selectedAction);
            action.setEnabled(playable&&!model.usingSequence());
            if(playable&&!model.usingSequence()&&action.getSelectedItem() instanceof Choice selected)selectedAction=selected.key();
            for(var item:model.effects()){effect.addItem(item);if(item.key().equals(selectedEffect))effect.setSelectedItem(item);}
            if(skill&&model.effects().stream().noneMatch(item->item.key().equals("actor"))){
                var manual=new GameEffectRecipes.Effect("actor","仅角色动作","","","手动查看职业动作");
                effect.addItem(manual);
                if(selectedEffect.equals("actor"))effect.setSelectedItem(manual);
            }
            model.appearance().wearing().forEach((slot,path)->wearing.addElement(new Wear(slot,path.isEmpty()?"（隐藏）":path)));
            canvas.actorView=model.actorBounds();
            timeline.setMaximum((int)Math.min(60000,model.duration()));timeline.setValue(0);
            details.setText(resourceHtml(model).replace("</body>",loaded.iconWarning().isEmpty()?"</body>":"<p>"+html(loaded.iconWarning())+"</p></body>"));
            message.setText(playable?summary(model.warnings()):model.unavailableReason());
        }else{details.setText("请选择当前 PVF 对应的客户端或 ImagePacks2 目录。");message.setText("属性说明已加载 · 请选择资源目录查看动画");}
        effect.setVisible(effect.getItemCount()>1);
        changing=false;
        clock.seek(0);
        time.setText("0 ms");
        play.setEnabled(playable);
        timeline.setEnabled(playable);
        facing.setEnabled(playable);
        startPendingPlayback();
        if(playable&&hitBoxes.isSelected())requestFrame();
        canvas.repaint();
        responsive();
    }
    private void requestFrame() {
        if(model==null||closed||canvas.selectedVolume>=0||!model.unavailableReason().isEmpty())return;
        if(renderPending){renderAgain=true;return;}
        if(playing&&!loop.isSelected()&&clock.milliseconds()>=model.duration()){pause();clock.seek(model.duration());}
        GamePreview snapshot=model;long ticket=generation,time=clock.milliseconds();boolean mirror=mirrored,repeat=loop.isSelected(),debug=hitBoxes.isSelected();renderPending=true;
        worker.execute(()->{try{var frame=snapshot.render(time,mirror,repeat,debug);SwingUtilities.invokeLater(()->{
            if(closed||ticket!=generation)return;
            renderPending=false;boolean again=renderAgain;renderAgain=false;
            // A click pins the frame the user actually saw, even if a later one was being rendered.
            if(canvas.selectedVolume>=0)return;
            canvas.frame=frame;canvas.error="";
            if(!again)showFrameTime(frame);
            canvas.repaint();
            if(again)requestFrame();
        });}catch(Exception failure){SwingUtilities.invokeLater(()->{if(closed||ticket!=generation)return;renderPending=false;renderAgain=false;if(canvas.selectedVolume>=0)return;pause();message.setText("渲染失败："+failure.getMessage());});}});
    }
    private void showFrameTime(GamePreview.Rendered frame){
        long displayed=frame.milliseconds(),duration=model.duration();
        changing=true;
        timeline.setValue((int)(displayed>duration?displayed%duration:displayed));
        changing=false;
        time.setText(displayed+" ms");
        message.setText(summary(frame.warnings()));
    }
    private void seek(long time){pause();canvas.clearSelection();clock.seek(time);requestFrame();}
    private void pause(){timer.stop();clock.pause();playing=false;play.setText("播放");}
    private void startPendingPlayback(){
        if(model==null||!isShowing())return;
        boolean restart=autoPlayPending&&model.usingSequence();
        autoPlayPending=false;
        if(restart){
            zoom.setSelectedIndex(1);
            clock.seek(0);
            startPlayback();
        }
    }
    private void startPlayback(){
        if(model==null||!model.unavailableReason().isEmpty())return;
        canvas.clearSelection();
        if(!loop.isSelected()&&clock.milliseconds()>=model.duration())clock.seek(0);
        clock.play();
        playing=true;
        play.setText("暂停");
        timer.start();
        requestFrame();
    }
    void deactivate(){
        if(model!=null&&model.usingSequence())autoPlayPending=true;
        ++generation;
        pause();canvas.clearSelection();
        renderPending=false;
        renderAgain=false;
    }
    @Override public void close(){if(closed)return;closed=true;++generation;pause();worker.shutdown();}
    private static void select(JComboBox<Choice> box,String key){for(int i=0;i<box.getItemCount();i++)if(box.getItemAt(i).key().equals(key)){box.setSelectedIndex(i);return;}if(box.getItemCount()>0)box.setSelectedIndex(0);}
    private static String jobLabel(String key){return switch(key){case "swordman"->"鬼剑士";case "fighter"->"格斗家";case "mage"->"魔法师";case "priest"->"圣职者";default->"神枪手";};}
    private static String html(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static String summary(List<String> warnings){return warnings.isEmpty()?"角色与动作已组合 · 配置预览":warnings.size()+" 条预览提示 · 在“资源”页查看支持范围";}
    private static String resourceHtml(GamePreview preview){
        var b=new StringBuilder("<html><body><b>组合资源</b>");
        for(var p:preview.appearance().parts()){
            b.append("<p>").append(html(p.slot())).append(" · layer ").append(p.layer()).append("<br>")
                    .append(html(p.animation()).replace("/","/&#8203;")).append("<br>variation ").append(p.variation()).append("</p>");
        }
        b.append("<hr><p>属性显示 PVF 配置值。强化、随机品级和角色加成由游戏计算。</p>");
        String explanation=preview.info().explanation();
        int descriptionStart=preview.info().tooltip().skill()?0:explanation.indexOf("配置触发条件\n");
        if(descriptionStart>=0&&!explanation.isBlank()){
            b.append("<p><b>详细说明</b><br>").append(html(explanation.substring(descriptionStart)).replace("\n","<br>")).append("</p>");
        }
        for(String warning:preview.warnings())b.append("<p>").append(html(warning)).append("</p>");
        return b.append("</body></html>").toString();
    }
    private void chooseOutfit() {
        if(source==null)return;String j=selectedJob;List<String> candidates=paths.stream().filter(p->p.endsWith(".equ")&&(p.startsWith("equipment/character/"+j+"/")||p.startsWith("equipment/character/common/title/"))).sorted().toList();
        var list=new JList<String>();var filter=new JTextField();Runnable apply=()->{String term=filter.getText().toLowerCase(Locale.ROOT);list.setListData(candidates.stream().filter(p->p.contains(term)).toArray(String[]::new));};
        filter.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){apply.run();}public void removeUpdate(DocumentEvent e){apply.run();}public void changedUpdate(DocumentEvent e){apply.run();}});apply.run();
        JPanel content=new JPanel(new BorderLayout(4,4));content.add(filter,BorderLayout.NORTH);content.add(new JScrollPane(list));content.setPreferredSize(new Dimension(620,340));
        if(JOptionPane.showConfirmDialog(this,content,"选择同包穿戴配置（可输入路径筛选）",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION||list.getSelectedValue()==null)return;
        String path=list.getSelectedValue();var snapshot=source;long ticket=generation;
        worker.execute(()->{try{String text=snapshot.lookup().read(path);String slot=text==null?"":CharacterAppearance.slot(text);SwingUtilities.invokeLater(()->{if(closed||ticket!=generation)return;if(slot.isEmpty()){message.setText("配置没有可识别的穿戴部位");return;}outfits.computeIfAbsent(j,k->new HashMap<>()).put(slot,path);reload();saveSettings();});}catch(IOException failure){SwingUtilities.invokeLater(()->{if(!closed&&ticket==generation)message.setText(failure.getMessage());});}});
    }
    /** FlowLayout otherwise reports one-row height even when controls actually wrap. */
    private static final class Wrap extends FlowLayout {
        Wrap(int h,int v){super(FlowLayout.LEFT,h,v);}
        @Override public Dimension minimumLayoutSize(Container target){Dimension d=preferredLayoutSize(target);int width=0;for(Component child:target.getComponents())if(child.isVisible())width=Math.max(width,child.getMinimumSize().width);d.width=width+2*getHgap()+target.getInsets().left+target.getInsets().right;return d;}
        @Override public Dimension preferredLayoutSize(Container target){
            synchronized(target.getTreeLock()){
                int width=target.getParent()!=null&&target.getParent().getWidth()>0?target.getParent().getWidth():target.getWidth();if(width<=0)width=Integer.MAX_VALUE/4;
                Insets in=target.getInsets();int available=Math.max(1,width-in.left-in.right-2*getHgap()),rowWidth=0,rowHeight=0,totalHeight=0,maxWidth=0;
                for(Component child:target.getComponents())if(child.isVisible()){Dimension d=child.getPreferredSize();if(rowWidth>0&&rowWidth+getHgap()+d.width>available){maxWidth=Math.max(maxWidth,rowWidth);totalHeight+=rowHeight+getVgap();rowWidth=0;rowHeight=0;}if(rowWidth>0)rowWidth+=getHgap();rowWidth+=d.width;rowHeight=Math.max(rowHeight,d.height);}
                return new Dimension(Math.max(maxWidth,rowWidth)+in.left+in.right+2*getHgap(),totalHeight+rowHeight+in.top+in.bottom+2*getVgap());
            }
        }
    }
    private final class Canvas extends JPanel {
        private final GamePreviewPainter painter = new GamePreviewPainter();
        private record View(double x,double y,double scale) {}
        GamePreview.Rendered frame;Rectangle2D actorView=new Rectangle2D.Double(-40,-140,80,150);String error="选择装备、装扮或技能";double panX,panY,freeZoom;Point dragging;
        private int selectedVolume=-1;
        private boolean panned;
        Canvas(){
            setName("game-canvas");
            var mouse=new MouseAdapter(){
                @Override public void mousePressed(MouseEvent e){dragging=e.getPoint();panned=false;}
                @Override public void mouseDragged(MouseEvent e){
                    if(dragging==null||!panned&&dragging.distanceSq(e.getPoint())<16)return;
                    panned=true;panX+=e.getX()-dragging.x;panY+=e.getY()-dragging.y;dragging=e.getPoint();repaint();
                }
                @Override public void mouseReleased(MouseEvent e){
                    boolean click=dragging!=null&&!panned&&dragging.distanceSq(e.getPoint())<16&&SwingUtilities.isLeftMouseButton(e);
                    dragging=null;
                    if(click)pickVolume(e);
                }
                @Override public void mouseWheelMoved(MouseWheelEvent e){freeZoom=Math.clamp((freeZoom>0?freeZoom:scale())*Math.pow(1.12,-e.getPreciseWheelRotation()),.1,8);repaint();}
            };
            addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);
        }
        double scale(){if(frame==null)return 1;if(freeZoom>0)return freeZoom;return switch(zoom.getSelectedIndex()){case 0->Math.min(2,Math.max(.25,(getHeight()-48)/Math.max(1,actorView.getHeight())));case 2->1;case 3->2;case 4->4;default->Math.max(.1,Math.min(4,Math.min((getWidth()-36)/Math.max(1,frame.bounds().getWidth()),(getHeight()-48)/Math.max(1,frame.bounds().getHeight()))));};}
        private View view(){
            boolean actor=zoom.getSelectedIndex()==0;
            var bounds=actor?actorView:frame.bounds();
            if(actor&&mirrored)bounds=new Rectangle2D.Double(-bounds.getMaxX(),bounds.getY(),bounds.getWidth(),bounds.getHeight());
            double scale=scale();
            return new View(getWidth()/2.0-bounds.getCenterX()*scale+panX,
                    getHeight()/2.0-bounds.getCenterY()*scale+panY,scale);
        }
        private void clearSelection(){
            if(selectedVolume<0)return;
            selectedVolume=-1;
            repaint();
        }
        private void pickVolume(MouseEvent event){
            if(frame==null||model==null||!error.isEmpty()||!hitBoxes.isSelected())return;
            var view=view();
            int picked=CombatDebug.pick(frame.combatVolumes(),view.x(),view.y(),view.scale(),event.getX(),event.getY(),selectedVolume);
            if(picked<0){clearSelection();return;}
            selectedVolume=picked;
            pause();
            clock.seek(frame.milliseconds());
            showFrameTime(frame);
            repaint();
        }
        @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);var g=(Graphics2D)graphics.create();try{
            if(frame!=null){var view=view();double x=view.x(),y=view.y();
                painter.paint(g,frame,getWidth(),getHeight(),x,y,view.scale(),getBackground());
                g.setColor(UIManager.getColor("Label.disabledForeground"));g.drawLine((int)x-7,(int)y,(int)x+7,(int)y);g.drawLine((int)x,(int)y-4,(int)x,(int)y+4);
                if(hitBoxes.isSelected())CombatDebug.paint(g,frame.combatVolumes(),x,y,view.scale(),boxCoordinates.isSelected(),selectedVolume);
            }else painter.paint(g,null,getWidth(),getHeight(),0,0,1,getBackground());
            if(!error.isEmpty()){g.setColor(getForeground());FontMetrics fm=g.getFontMetrics();String label=error.length()>70?error.substring(0,70)+"…":error;g.drawString(label,Math.max(8,(getWidth()-fm.stringWidth(label))/2),frame==null?getHeight()/2:22);}
        }finally{g.dispose();}}
    }
}
