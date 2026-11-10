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
            do{SwingUtilities.invokeAndWait(()->html[0]=find(panel,"game-item-info",JEditorPane.class).getText());if(html[0].contains("Second"))break;Thread.sleep(20);}while(System.nanoTime()<end);
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
    private static void waitInfo(GamePreviewPanel panel,String value)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);boolean[] found={false};do{SwingUtilities.invokeAndWait(()->found[0]=find(panel,"game-item-info",JEditorPane.class).getText().contains(value));if(found[0])return;Thread.sleep(20);}while(System.nanoTime()<end);fail("Missing info: "+value);}
}
