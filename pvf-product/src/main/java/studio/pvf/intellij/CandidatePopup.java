package studio.pvf.intellij;

import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import javax.swing.*;

/** Register the searchable list with the platform's focus and wheel event dispatcher. */
final class CandidatePopup {
    private CandidatePopup(){}
    static Runnable show(JComponent content,JComponent focus,JComponent anchor,Runnable closed){
        var popup=JBPopupFactory.getInstance().createComponentPopupBuilder(content,focus)
                .setFocusable(true).setRequestFocus(true).setCancelOnClickOutside(true).setCancelKeyEnabled(true)
                .addListener(new JBPopupListener(){@Override public void onClosed(LightweightWindowEvent event){closed.run();}}).createPopup();
        popup.showUnderneathOf(anchor);return popup::cancel;
    }
}
