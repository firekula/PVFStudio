package studio.pvf.intellij;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;
class PvfExportFilesTest {
 @Test void saveChooserOffersOnlyPvfAndUsesSourceDirectory()throws Exception{
  SwingUtilities.invokeAndWait(() -> {var chooser=PvfExportFiles.chooser(Path.of(System.getProperty("java.io.tmpdir"),"source.pvf"),true);assertFalse(chooser.isAcceptAllFileFilterUsed());assertEquals(1,chooser.getChoosableFileFilters().length);assertTrue(chooser.getFileFilter().accept(new java.io.File("sample.PVF")));assertFalse(chooser.getFileFilter().accept(new java.io.File("sample.txt")));assertEquals("Script-edited.pvf",chooser.getSelectedFile().getName());});
 }
 @Test void missingSuffixIsAddedButDifferentFileTypesAreRejected()throws Exception{
  assertEquals(Path.of("output.pvf"),PvfExportFiles.target(Path.of("output")));assertEquals(Path.of("output.PVF"),PvfExportFiles.target(Path.of("output.PVF")));assertThrows(IOException.class,() -> PvfExportFiles.target(Path.of("output.txt")));assertThrows(IOException.class,() -> PvfExportFiles.target(Path.of(".pvf")));
 }
}
