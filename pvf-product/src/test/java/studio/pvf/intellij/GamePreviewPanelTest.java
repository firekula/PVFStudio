package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class GamePreviewPanelTest {
    @org.junit.jupiter.api.io.TempDir Path temporary;
    @Test void pausedSeekStepAndFacingKeepTheLatestRequestDuringBackgroundRendering() throws Exception {
        var data=Map.of("character/gunner/gunner.chr","[waiting motion] `body.ani`","character/gunner/body.ani","[FRAME000] [IMAGE] `a.img` 0 [DELAY] 100 [FRAME001] [IMAGE] `b.img` 0 [DELAY] 100");
        var sprite=new studio.pvf.preview.ScenePreview.SpriteLookup(){public studio.pvf.preview.NpkResources.Info info(String p,int i){return new studio.pvf.preview.NpkResources.Info(1,1,0,0,1,1);}public studio.pvf.preview.NpkResources.Sprite sprite(String p,int i){var image=new java.awt.image.BufferedImage(1,1,2);image.setRGB(0,0,-1);return new studio.pvf.preview.NpkResources.Sprite(image,0,0,1,1,Path.of("fixture.npk"),p,i,i);}};
        var model=studio.pvf.preview.GamePreview.load("skill/gunner/fixture.skl","",data::get,data.keySet(),sprite,studio.pvf.preview.GamePreview.Options.defaults().withEffect("actor"));var holder=new GamePreviewPanel[1];SwingUtilities.invokeAndWait(()->holder[0]=new GamePreviewPanel(()->null,()->{}));var p=holder[0];var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try{var modelField=GamePreviewPanel.class.getDeclaredField("model");modelField.setAccessible(true);SwingUtilities.invokeAndWait(()->{try{modelField.set(p,model);}catch(IllegalAccessException e){throw new AssertionError(e);}});var workerField=GamePreviewPanel.class.getDeclaredField("worker");workerField.setAccessible(true);((ExecutorService)workerField.get(p)).submit(()->{entered.countDown();try{release.await(4,TimeUnit.SECONDS);}catch(InterruptedException e){throw new AssertionError(e);}});assertTrue(entered.await(2,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{var slider=find(p,"game-timeline",JSlider.class);slider.setValue(50);slider.setValue(150);find(p,"game-next",JButton.class).doClick();for(Component c:all(p))if(c instanceof JButton b&&b.getText().equals("朝右"))b.doClick();});release.countDown();
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);boolean[] ready={false};do{SwingUtilities.invokeAndWait(()->{try{var canvas=find(p,"game-canvas",JPanel.class);var field=canvas.getClass().getDeclaredField("frame");field.setAccessible(true);var frame=(studio.pvf.preview.GamePreview.Rendered)field.get(canvas);ready[0]=frame!=null&&frame.milliseconds()==166&&frame.draws().getFirst().mirrored();}catch(ReflectiveOperationException e){throw new AssertionError(e);}});if(ready[0])break;Thread.sleep(20);}while(System.nanoTime()<end);assertTrue(ready[0],"Final paused seek/step/facing was lost");
        }finally{release.countDown();SwingUtilities.invokeAndWait(p::close);}
    }
    private static java.util.List<Component> all(Container root){var out=new java.util.ArrayList<Component>();for(Component c:root.getComponents()){out.add(c);if(c instanceof Container child)out.addAll(all(child));}return out;}
    @Test void eachSkillKeepsItsChosenLevelWhenSwitchingFiles() throws Exception {
        var holder=new GamePreviewPanel[1];SwingUtilities.invokeAndWait(()->holder[0]=new GamePreviewPanel(()->null,()->{}));var p=holder[0];
        var a=new ResourcePreviewPanel.Source("skill/gunner/a.skl","[name] `SkillA` [maximum level] 30",path->null);var b=new ResourcePreviewPanel.Source("skill/mage/b.skl","[name] `SkillB` [maximum level] 30",path->null);
        try{SwingUtilities.invokeAndWait(()->p.setSource(a,Set.of(),true));waitInfo(p,"SkillA");SwingUtilities.invokeAndWait(()->find(p,"game-level",JSpinner.class).setValue(14));waitInfo(p,"14 / 30");SwingUtilities.invokeAndWait(()->p.setSource(b,Set.of(),true));waitInfo(p,"SkillB");SwingUtilities.invokeAndWait(()->find(p,"game-level",JSpinner.class).setValue(7));waitInfo(p,"7 / 30");SwingUtilities.invokeAndWait(()->p.setSource(a,Set.of(),true));waitInfo(p,"SkillA");SwingUtilities.invokeAndWait(()->assertEquals(14,find(p,"game-level",JSpinner.class).getValue()));}
        finally{SwingUtilities.invokeAndWait(p::close);}
    }
    @Test void mountingAnotherWorkspaceDoesNotExposeThePreviousResourceIndex() throws Exception {
        Path packs=Files.createDirectory(temporary.resolve("packs")),workspace=Files.createDirectory(temporary.resolve("workspace"));
        var pack=java.nio.ByteBuffer.allocate(316).order(java.nio.ByteOrder.LITTLE_ENDIAN);pack.put("NeoplePack_Bill\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(1).putInt(284).putInt(32).put("sprite/empty.img".getBytes(java.nio.charset.StandardCharsets.US_ASCII));pack.position(284);pack.put("Neople Image File".getBytes(java.nio.charset.StandardCharsets.US_ASCII));pack.putInt(308,1).putInt(312,0);Files.write(packs.resolve("empty.npk"),pack.array());
        var holder=new ResourcePreviewPanel[1];SwingUtilities.invokeAndWait(()->{holder[0]=new ResourcePreviewPanel();holder[0].scan(packs);});
        try{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);boolean[] loaded={false};do{SwingUtilities.invokeAndWait(()->loaded[0]=holder[0].resources()!=null);if(loaded[0])break;Thread.sleep(20);}while(System.nanoTime()<end);assertTrue(loaded[0]);
            SwingUtilities.invokeAndWait(()->{holder[0].mount(workspace,null);assertNull(holder[0].resources(),"New PVF must explicitly bind its own resource directory");});
        }finally{SwingUtilities.invokeAndWait(holder[0]::close);}
    }
    @Test void oversizedEditedConfigurationDoesNotThrowOnTheEventThread() throws Exception {
        var holder=new GamePreviewPanel[1];
        SwingUtilities.invokeAndWait(()->holder[0]=new GamePreviewPanel(()->null,()->{}));
        try{assertDoesNotThrow(()->SwingUtilities.invokeAndWait(()->holder[0].setSource(new ResourcePreviewPanel.Source("equipment/common.equ","x".repeat(8_000_001),p->null),Set.of(),true)));}
        finally{SwingUtilities.invokeAndWait(holder[0]::close);}
    }
    @Test void laterSourceWinsWithoutInterruptingTheOldArchiveReader() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var interrupted=new java.util.concurrent.atomic.AtomicBoolean();
        var held=new GamePreviewPanel[1];SwingUtilities.invokeAndWait(()->held[0]=new GamePreviewPanel(()->null,()->{}));var panel=held[0];
        try{
            SwingUtilities.invokeAndWait(()->panel.setSource(new ResourcePreviewPanel.Source("equipment/one.equ","[name] <0::name>",path->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted.set(true);Thread.currentThread().interrupt();}return path.equals("string.lst")?"0 `a.str`":"name>Old";}),Set.of(),true));
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->panel.setSource(new ResourcePreviewPanel.Source("equipment/two.equ","[name] `Second`",path->null),Set.of(),true));release.countDown();
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);String[] html={""};
            do{SwingUtilities.invokeAndWait(()->html[0]=infoText(panel));if(html[0].contains("Second"))break;Thread.sleep(20);}while(System.nanoTime()<end);
            assertTrue(html[0].contains("Second"));assertFalse(interrupted.get());
        }finally{release.countDown();SwingUtilities.invokeAndWait(panel::close);}
    }
    @Test void previewAndSplitKeepTheSameCodeComponentAndNoResourceChoiceEditsTheSource() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var code=new JTextArea("[name] `Original`");var panel=new GamePreviewPanel(()->null,()->{});
            try{var surface=new VisualEditorSurface(code,panel,()->{});surface.designTitle("效果预览");surface.setMode(VisualEditorSurface.Mode.DESIGN);surface.setMode(VisualEditorSurface.Mode.SPLIT);surface.setMode(VisualEditorSurface.Mode.CODE);assertSame(code,surface.code());assertEquals("[name] `Original`",code.getText());}finally{panel.close();}
        });
    }
    private static <T extends Component>T find(Container root,String name,Class<T> type){for(Component c:root.getComponents()){if(name.equals(c.getName())&&type.isInstance(c))return type.cast(c);if(c instanceof Container nested)try{return find(nested,name,type);}catch(NoSuchElementException ignored){}}throw new NoSuchElementException(name);}
    private static String infoText(GamePreviewPanel panel){
        var item=find(panel,"game-item-info",GameTooltipPanel.class).item();
        return item==null?"":item.tooltip().heading()+item.rows();
    }
    private static void waitInfo(GamePreviewPanel panel,String value)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);boolean[] found={false};do{SwingUtilities.invokeAndWait(()->found[0]=infoText(panel).contains(value));if(found[0])return;Thread.sleep(20);}while(System.nanoTime()<end);fail("Missing info: "+value);}
}
