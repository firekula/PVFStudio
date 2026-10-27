package studio.pvf.intellij;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** PVF-only export selection. Never silently replaces a differently typed file. */
final class PvfExportFiles {
    private PvfExportFiles(){}
    static JFileChooser chooser(Path source,boolean backup){
        JFileChooser chooser=new JFileChooser(){
            @Override public void approveSelection(){
                if(getDialogType()==SAVE_DIALOG)try{setSelectedFile(target(getSelectedFile()==null?null:getSelectedFile().toPath()).toFile());}
                catch(IOException invalid){JOptionPane.showMessageDialog(this,invalid.getMessage(),"导出文件名",JOptionPane.ERROR_MESSAGE);return;}
                super.approveSelection();
            }
        };
        chooser.setDialogTitle(backup?"导出 PVF（自动备份旧版本）":"导出到新 PVF 文件（不覆盖既有文件）");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);chooser.setFileFilter(new FileNameExtensionFilter("PVF 配置包 (*.pvf)","pvf"));chooser.setAcceptAllFileFilterUsed(false);
        if(source!=null&&source.getParent()!=null)chooser.setCurrentDirectory(source.getParent().toFile());
        chooser.setSelectedFile(new java.io.File(chooser.getCurrentDirectory(),"Script-edited.pvf"));return chooser;
    }
    static Path target(Path requested)throws IOException{
        if(requested==null||requested.getFileName()==null||requested.getFileName().toString().isBlank())throw new IOException("请选择一个 PVF 导出文件。");
        String name=requested.getFileName().toString();
        if(name.toLowerCase(Locale.ROOT).endsWith(".pvf")&&name.length()>4)return requested;
        if(name.indexOf('.')<0)return requested.resolveSibling(name+".pvf");
        throw new IOException("导出文件必须使用 .pvf 扩展名；输入不带扩展名的文件名时会自动补全。");
    }
}
