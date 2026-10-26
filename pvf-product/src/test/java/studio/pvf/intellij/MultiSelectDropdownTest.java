package studio.pvf.intellij;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MultiSelectDropdownTest {
    @Test void largeCatalogChangesPublishOneBatchAndKnownChoiceDoesNotRebuildRows()throws Exception{
        SwingUtilities.invokeAndWait(() -> {try{
            var field=new MultiSelectDropdown<Integer>();var listField=MultiSelectDropdown.class.getDeclaredField("list");listField.setAccessible(true);var list=(JList<Integer>)listField.get(field);int[] added={0},removed={0};
            list.getModel().addListDataListener(new javax.swing.event.ListDataListener(){public void intervalAdded(javax.swing.event.ListDataEvent e){added[0]++;}public void intervalRemoved(javax.swing.event.ListDataEvent e){removed[0]++;}public void contentsChanged(javax.swing.event.ListDataEvent e){}});
            var catalog=new LinkedHashMap<Integer,String>();for(int id=1;id<=10000;id++)catalog.put(id,id+" · map/room"+id+".map");field.options(catalog);
            assertEquals(1,added[0],"Installing a catalog must publish one model event, not one event per MAP");assertEquals(10000,list.getModel().getSize());
            assertTrue(list.getFixedCellHeight()>0&&list.getFixedCellWidth()>0,"Layout must not measure every cell after a selection");
            var renderer=list.getCellRenderer();assertSame(renderer.getListCellRendererComponent(list,1,0,false,false),renderer.getListCellRendererComponent(list,2,1,false,false),"Renderer components must be reused");
            added[0]=0;removed[0]=0;list.setSelectedIndex(0);field.toggle(1);field.toggle(5000);field.toggle(1);
            assertEquals(List.of(5000),field.selected());assertEquals(0,added[0]);assertEquals(0,removed[0],"Checking a known MAP only repaints; it cannot rebuild the whole catalog");
            field.selected(List.of(5000,10001));assertEquals(10001,list.getModel().getSize());field.toggle(10001);assertEquals(10000,list.getModel().getSize(),"Unregistered selected IDs are still retained and removable");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test void multipleChoicesPreserveOrderUnknownIdsAndSilentRefresh() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var field=new MultiSelectDropdown<Integer>();var events=new ArrayList<List<Integer>>();field.onChange(events::add);
            field.options(Map.of(10,"10 · a.map",20,"20 · b.map"));field.selected(List.of(99,10));
            assertEquals(List.of(99,10),field.selected());assertTrue(events.isEmpty());
            field.toggle(20);assertEquals(List.of(99,10,20),field.selected());assertEquals(List.of(List.of(99,10,20)),events);
            field.options(Map.of(20,"20 · new.map"));assertEquals(List.of(99,10,20),field.selected());
            field.toggle(10);assertEquals(List.of(99,20),field.selected());
            field.setEnabled(false);field.toggle(20);assertEquals(List.of(99,20),field.selected());
        });
    }
}
