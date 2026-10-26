package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import studio.pvf.sceneedit.SceneCatalog;
import javax.swing.*;
import javax.swing.event.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static studio.pvf.intellij.DungeonEditorPanelTest.find;

class SceneResourceBrowserTest {
    @Test void largeCatalogPublishesABoundedNumberOfListUpdatesAndLoadsOnlySelectedThumbnail() throws Exception {
        Set<String> paths=new HashSet<>(Set.of("passiveobject/passiveobject.lst"));StringBuilder raw=new StringBuilder();
        for(int i=1;i<=3000;i++){paths.add("passiveobject/a"+i+".obj");raw.append(i).append(" `a").append(i).append(".obj`\n");}
        var catalog=SceneCatalog.build(paths,path -> {assertEquals("passiveobject/passiveobject.lst",path);return raw.toString();});
        SwingUtilities.invokeAndWait(() -> {
            var browser=new SceneResourceBrowser("map/a.map");find(browser,"map-asset-category",JComboBox.class).setSelectedItem(SceneResourceBrowser.Category.PASSIVE);
            var list=find(browser,"map-asset-list",JList.class);int[] events={0},loads={0};
            list.getModel().addListDataListener(new ListDataListener(){public void intervalAdded(ListDataEvent e){events[0]++;}public void intervalRemoved(ListDataEvent e){}public void contentsChanged(ListDataEvent e){events[0]++;}});
            browser.setThumbnailLoader((asset,result) -> loads[0]++);browser.setCatalog(catalog);
            assertEquals(3000,list.getModel().getSize());assertTrue(events[0]<=2,"Per-item events make the native list repeatedly measure the whole catalog");assertEquals(1,loads[0]);
        });
    }
}
