package studio.pvf.intellij;

import studio.pvf.core.PvfArchive;
import studio.pvf.workspace.PvfWorkspace;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.List;
import static studio.pvf.intellij.TabWorkbenchSmoke.*;

/** Actual chooser/save/backup flow on a private source copy, including source metadata replacement. */
final class ExportSaveSmoke {
    static void run(Path original,Path output)throws Exception{
        Path source=output.resolve("source-copy.pvf");Files.copy(original,source);Files.writeString(output.resolve("hidden-by-filter.txt"),"not a PVF");
        var frame=edt(() -> {var f=new PvfStudioFrame(output.resolve("export-workspaces"),false);f.setSize(1100,760);f.setVisible(true);f.openArchive(source);return f;});
        var driver=new DialogDriver(output);edt(() -> {driver.timer.start();return null;});
        try{
            await(() -> button(frame,"打开 PVF…").isEnabled());var field=PvfStudioFrame.class.getDeclaredField("workspace");field.setAccessible(true);var workspace=edt(() -> (PvfWorkspace)field.get(frame));
            String path="etc/__export_save_probe.etc";workspace.createFile(path,"first".getBytes(StandardCharsets.US_ASCII));edt(() -> {frame.openEntry(path);return null;});await(() -> button(frame,"打开 PVF…").isEnabled());
            var tabs=edt(() -> components(frame,EditorTabs.class).getFirst());var code=edt(() -> editor(tabs.getSelectedComponent()));
            Files.setLastModifiedTime(source,FileTime.fromMillis(Files.getLastModifiedTime(source).toMillis()+60000));
            Path target=output.resolve("edited.pvf");export(frame,driver,output.resolve("edited"),false);check(Files.isRegularFile(target),"Missing suffix was not completed to .pvf");
            try(var result=PvfArchive.open(target)){check(new String(result.readBytes(path),StandardCharsets.US_ASCII).equals("first"),"New export lost draft content");}
            byte[] first=Files.readAllBytes(target);Path replacement=output.resolve("same-bytes.pvf");Files.copy(original,replacement);Files.move(replacement,source,StandardCopyOption.REPLACE_EXISTING);
            edt(() -> {com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(code.getProject(),() -> com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> code.getDocument().setText("second")),"修改待导出内容",null);return null;});
            export(frame,driver,target,false);try(var result=PvfArchive.open(target)){check(new String(result.readBytes(path),StandardCharsets.US_ASCII).equals("second"),"Existing export was not updated");}
            var backups=Files.list(output.resolve("backups"));List<Path> saved;try(backups){saved=backups.filter(p -> p.toString().endsWith(".pvf")).toList();}check(saved.size()==2,"Both exports must preserve a prior version");check(saved.stream().anyMatch(p -> {try{return Arrays.equals(first,Files.readAllBytes(p));}catch(Exception e){throw new RuntimeException(e);}}),"Existing destination backup was not byte exact");
            Path noBackup=output.resolve("without-backup.pvf");edt(() -> {((JCheckBoxMenuItem)button(frame,"导出前自动备份")).setSelected(false);return null;});export(frame,driver,noBackup,false);check(Files.exists(noBackup),"New-file export without backup failed after same-byte source replacement");
            byte[] changed=Files.readAllBytes(source);changed[changed.length-1]^=1;Files.write(source,changed);
            edt(() -> {com.intellij.openapi.command.CommandProcessor.getInstance().executeCommand(code.getProject(),() -> com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> code.getDocument().setText("preserved after source error")),"保留源变化后的草稿",null);((JCheckBoxMenuItem)button(frame,"导出前自动备份")).setSelected(true);return null;});
            Path blocked=output.resolve("blocked.pvf");export(frame,driver,blocked,true);check(!Files.exists(blocked),"A true source change published an output");check(workspace.readText(path,StandardCharsets.US_ASCII).equals("preserved after source error"),"Source error discarded the staged draft");
            Files.writeString(output.resolve("export-save-result.txt"),"PASS: PVF-only chooser; suffix completion; metadata touch; same-byte source replacement; new and existing destination export; byte-exact backup; no-backup export; actual content change blocks publication and retains drafts\n");
        }catch(Exception|AssertionError failure){edt(() -> {screenshot(frame,output.resolve("export-save-failure.png"));return null;});throw failure;}
        finally{edt(() -> {driver.timer.stop();respond("放弃未保存编辑");frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));return null;});await(() -> !frame.isDisplayable());}
    }
    private static void export(PvfStudioFrame frame,DialogDriver driver,Path target,boolean failure)throws Exception{
        int success=driver.success,errors=driver.errors.size();driver.pending=target;
        edt(() -> {button(frame,"导出新 PVF…").doClick();return null;});await(() -> driver.success>success||driver.errors.size()>errors);
        check(failure?driver.errors.size()>errors:driver.success>success,"Unexpected export result: "+driver.errors);await(() -> button(frame,"打开 PVF…").isEnabled());
    }
    private static final class DialogDriver {
        private final Path output;private final javax.swing.Timer timer;
        private volatile Path pending;private volatile int success;private int choices;
        private final List<String> errors=new java.util.concurrent.CopyOnWriteArrayList<>();
        DialogDriver(Path output){this.output=output;timer=new javax.swing.Timer(30,e -> poll());}
        private void poll(){
            for(Window w:Window.getWindows())if(w instanceof JDialog dialog&&dialog.isShowing()){
                var choosers=components(dialog,JFileChooser.class);
                if(!choosers.isEmpty()&&pending!=null){var chooser=choosers.getFirst();Path selected=pending;pending=null;
                    try{check(!chooser.isAcceptAllFileFilterUsed()&&chooser.getChoosableFileFilters().length==1,"Save dialog still offers all file types");check(!chooser.getFileFilter().accept(new java.io.File("wrong.txt")),"PVF filter accepts .txt");screenshot(dialog,output.resolve("export-chooser-"+(++choices)+".png"));chooser.setSelectedFile(selected.toFile());chooser.approveSelection();}
                    catch(Exception|AssertionError failure){errors.add(failure.toString());chooser.cancelSelection();}return;
                }
                var panes=components(dialog,JOptionPane.class);if(panes.isEmpty())continue;
                if(dialog.getTitle().equals("导出完成")){success++;panes.getFirst().setValue(JOptionPane.OK_OPTION);}
                else if(dialog.getTitle().equals("备份并更新导出文件"))panes.getFirst().setValue(JOptionPane.OK_OPTION);
                else if(dialog.getTitle().equals("PVF Studio")||dialog.getTitle().equals("导出文件名")){errors.add(String.valueOf(panes.getFirst().getMessage()));panes.getFirst().setValue(JOptionPane.OK_OPTION);}
            }
        }
    }
}
