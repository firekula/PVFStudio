package studio.pvf.intellij;

import studio.pvf.preview.*;
import studio.pvf.workspace.PvfWorkspace;
import javax.swing.*;
import java.awt.event.WindowEvent;
import java.nio.file.*;
import java.util.*;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;
import static studio.pvf.intellij.VisualDungeonWorkbenchSmoke.named;

/** Real archive/resources and initialized IntelliJ workbench; no user workspace writes. */
final class GamePreviewWorkbenchSmoke {
    static void run(Path archive,Path output)throws Exception {
        var frame=edt(()->{var f=new PvfStudioFrame(output.resolve("game-workspaces"),false);f.setSize(1264,811);f.setVisible(true);return f;});
        try{
            await(()->button(frame,"打开最近的文件").isEnabled());edt(()->{frame.openArchive(archive);return null;});await(()->ready(frame));
            var workspace=(PvfWorkspace)field(frame,"workspace");var preview=(ResourcePreviewPanel)field(frame,"preview");var game=(GamePreviewPanel)field(frame,"gamePreview");
            String avatar="equipment/character/gunner/avatar/coat/auniform_wtsummer.equ",weapon="equipment/character/gunner/weapon/hcannon/n_cann_suneater.equ",title="equipment/character/common/title/joytron_handlingfeeling.equ";
            edt(()->{preview.scan(Path.of(System.getProperty("pvf.preview.npk")));frame.openEntry(avatar);return null;});
            await(()->ready(frame)&&loaded(game,"白色校服衬衫"));
            var tabs=edt(()->components(frame,EditorTabs.class).getFirst());var surface=edt(()->(VisualEditorSurface)tabs.getSelectedComponent());var code=edt(()->editor(tabs.getSelectedComponent()));String baseline=edt(()->code.getDocument().getText());
            edt(()->{check(surface.mode()==VisualEditorSurface.Mode.DESIGN,"Game file did not default to preview");check(named(game,"game-canvas",JPanel.class).getWidth()>300,"Preview canvas is too narrow");screenshot(frame,output.resolve("game-avatar-1264.png"));named(game,"game-timeline",JSlider.class).setValue(150);return null;});
            await(()->renderedTime(game)==150);
            edt(()->{button(game,"播放").doClick();((AnimationTimeline.Clock)field(game,"clock")).seek(current(game).duration()+300);return null;});
            await(()->renderedTime(game)>current(game).duration());edt(()->{button(game,"暂停").doClick();return null;});
            edt(()->{button(game,"朝右").doClick();surface.setMode(VisualEditorSurface.Mode.SPLIT);return null;});
            await(()->loaded(game,"白色校服衬衫"));edt(()->{check(editor(tabs.getSelectedComponent())==code,"Mode switch replaced editor");check(code.getDocument().getText().equals(baseline),"Preview changed source");screenshot(frame,output.resolve("game-split.png"));surface.setMode(VisualEditorSurface.Mode.CODE);return null;});
            edt(()->{com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(code.getProject(),()->com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(()->code.getDocument().setText(baseline.replace("白色校服衬衫","预览实时修改"))),"Game preview live edit",null);surface.setMode(VisualEditorSurface.Mode.DESIGN);return null;});
            await(()->loaded(game,"预览实时修改"));
            edt(()->{com.intellij.openapi.command.undo.UndoManager.getGlobalInstance().undo(com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(code));return null;});await(()->loaded(game,"白色校服衬衫"));
            edt(()->{frame.openEntry("equipment/character/gunner.lay");return null;});await(()->ready(frame)&&tabs.getToolTipTextAt(tabs.getSelectedIndex()).startsWith("equipment/character/gunner.lay"));
            var dependency=edt(()->editor(tabs.getSelectedComponent()));String lay=edt(()->dependency.getDocument().getText());
            edt(()->{com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(dependency.getProject(),()->com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(()->dependency.getDocument().setText(lay.replace("%s/Stay.ani","%s/Move.ani"))),"Game dependency edit",null);tabs.setSelectedComponent(surface);return null;});
            await(()->loaded(game,"白色校服衬衫")&&current(game).appearance().parts().stream().anyMatch(p->p.slot().equals("coat avatar")&&p.animation().endsWith("move.ani")));
            edt(()->{com.intellij.openapi.command.undo.UndoManager.getGlobalInstance().undo(com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance().getTextEditor(dependency));return null;});
            await(()->loaded(game,"白色校服衬衫")&&current(game).appearance().parts().stream().filter(p->p.slot().equals("coat avatar")).allMatch(p->p.animation().endsWith("stay.ani")));
            edt(()->{frame.openEntry(weapon);return null;});await(()->ready(frame)&&loaded(game,"吞日者"));
            edt(()->{var effects=named(game,"game-effect",JComboBox.class);check(effects.getItemCount()>=3,"Weapon trigger references missing");effects.setSelectedIndex(1);return null;});await(()->loaded(game,"吞日者")&&rendered(game)!=null&&rendered(game).draws().size()>10);
            edt(()->{screenshot(frame,output.resolve("game-weapon-trigger.png"));frame.openEntry(title);return null;});await(()->ready(frame)&&loaded(game,"操作快感"));
            edt(()->{screenshot(frame,output.resolve("game-title.png"));frame.setSize(1024,720);return null;});
            edt(()->{frame.validate();return null;});await(()->game.getWidth()<800);
            edt(()->{screenshot(frame,output.resolve("game-title-1024.png"));check(named(game,"game-play",JButton.class).isShowing(),"Playback disappeared in small window");check(SwingUtilities.convertRectangle(game.getParent(),game.getBounds(),frame).getMaxX()<=frame.getWidth(),"Game preview extends outside resized window");frame.setSize(1264,811);frame.openEntry("skill/gunner/laserrifle.skl");return null;});await(()->ready(frame)&&loaded(game,"激光炮"));
            edt(()->{named(game,"game-level",JSpinner.class).setValue(14);return null;});await(()->current(game)!=null&&current(game).info().rows().stream().anyMatch(r->r.label().equals("预览等级")&&r.value().startsWith("14")));
            edt(()->{named(game,"game-timeline",JSlider.class).setValue(450);return null;});await(()->renderedTime(game)==450);
            edt(()->{screenshot(frame,output.resolve("game-skill.png"));frame.openEntry("skill/mage/launchjol.skl");return null;});await(()->ready(frame)&&current(game)!=null&&current(game).appearance().job().equals("mage"));
            edt(()->{check(current(game).effects().stream().anyMatch(e->e.key().equals("passive:23008")),"Mage native object binding missing");screenshot(frame,output.resolve("game-mage-jol.png"));return null;});
            String stackable=workspace.paths().stream().filter(p->p.startsWith("stackable/")&&p.endsWith(".stk")).sorted().findFirst().orElseThrow();
            edt(()->{frame.openEntry(stackable);return null;});await(()->ready(frame)&&current(game)!=null&&current(game).info().type().equals("物品"));
            edt(()->{check(((VisualEditorSurface)tabs.getSelectedComponent()).mode()==VisualEditorSurface.Mode.DESIGN,"STK did not default to preview");frame.openEntry(title);frame.openEntry(weapon);frame.openEntry(avatar);return null;});await(()->loaded(game,"白色校服衬衫"));
            var oldResources=edt(preview::resources);edt(()->{preview.scan(Path.of(System.getProperty("pvf.preview.npk")));return null;});await(()->preview.resources()!=oldResources&&loaded(game,"白色校服衬衫"));
            edt(()->{check(workspace.dirtyPaths().isEmpty(),"Pure preview created script drafts");return null;});
            workspace.archive().verifyUnchanged();Files.writeString(output.resolve("game-preview-result.txt"),"PASS: real EQU/SKL/STK, preview-first/split/code identity, seek/loop/facing, live top-level and LAY edit/undo, fast tab switches, resource reindex, Sun Eater refs, title, skill levels, 1264/1024 layouts, no preview draft changes\n");
        }catch(Exception|AssertionError failure){edt(()->{screenshot(frame,output.resolve("game-preview-failure.png"));return null;});throw failure;}
        finally{edt(()->{respond("放弃未保存编辑");frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));return null;});await(()->!frame.isDisplayable());}
    }
    private static Object field(Object owner,String name){try{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private static GamePreview current(GamePreviewPanel panel){return (GamePreview)field(panel,"model");}
    private static boolean loaded(GamePreviewPanel panel,String name){var m=current(panel);return m!=null&&m.info().name().contains(name);}
    private static GamePreview.Rendered rendered(GamePreviewPanel panel){var canvas=components(panel,JPanel.class).stream().filter(p->"game-canvas".equals(p.getName())).findFirst().orElseThrow();return (GamePreview.Rendered)field(canvas,"frame");}
    private static long renderedTime(GamePreviewPanel panel){var frame=rendered(panel);return frame==null?-1:frame.milliseconds();}
    private static boolean ready(PvfStudioFrame frame){return frame.isEnabled()&&button(frame,"打开 PVF…").isEnabled();}
}
