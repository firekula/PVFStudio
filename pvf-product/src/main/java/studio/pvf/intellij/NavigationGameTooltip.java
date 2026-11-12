package studio.pvf.intellij;

import studio.pvf.preview.GameItemInfo;
import studio.pvf.preview.GameScript;
import studio.pvf.preview.NpkResources;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/** Reads the hovered entry on demand. Swing state belongs to the event thread. */
final class NavigationGameTooltip implements AutoCloseable {
    record Row(String path, Rectangle bounds) {}
    record Source(GameScript.Lookup lookup, NpkResources resources) {}
    private record Content(GameItemInfo info, BufferedImage icon) {}

    private final Function<String,Source> sourceFor;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task -> {
        var thread=new Thread(task,"pvf-navigation-tooltip");
        thread.setDaemon(true);
        return thread;
    });
    private final Timer delay=new Timer(400,event -> load());
    private JComponent owner;
    private Row row;
    private JWindow popup;
    private GameTooltipPanel card;
    private Future<?> pending;
    private long generation;
    private boolean closed;

    NavigationGameTooltip(Function<String,Source> sourceFor){
        this.sourceFor=sourceFor;
        delay.setRepeats(false);
    }

    void install(JComponent view,Function<Point,Row> rowAt){
        var mouse=new MouseAdapter(){
            @Override public void mouseMoved(MouseEvent event){
                if(!windowActive(view))return;
                Row target=rowAt.apply(event.getPoint());
                if(target==null||!supported(target.path())){
                    if(owner==view)dismiss();
                    return;
                }
                if(owner==view&&target.equals(row))return;
                dismiss();
                owner=view;
                row=target;
                delay.restart();
            }
            @Override public void mouseExited(MouseEvent event){if(owner==view)dismiss();}
            @Override public void mousePressed(MouseEvent event){if(owner==view)dismiss();}
        };
        view.addMouseMotionListener(mouse);
        view.addMouseListener(mouse);
        view.addPropertyChangeListener("model",event -> {if(owner==view)dismiss();});
        view.addHierarchyListener(event -> {
            if((event.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0&&owner==view&&!view.isShowing())dismiss();
        });
        if(view.getParent() instanceof JViewport viewport){
            Point lastPosition=viewport.getViewPosition();
            viewport.addChangeListener(event -> {
                Point position=viewport.getViewPosition();
                if(position.equals(lastPosition))return;
                lastPosition.setLocation(position);
                if(owner==view)dismiss();
            });
        }
    }

    static boolean supported(String path){
        return path.endsWith(".equ")||path.endsWith(".stk")||path.endsWith(".skl");
    }

    private static boolean windowActive(JComponent view){
        var window=SwingUtilities.getWindowAncestor(view);
        return window!=null&&window.isActive();
    }

    private boolean canShow(){
        return !closed&&owner!=null&&owner.isShowing()&&windowActive(owner);
    }

    private void load(){
        if(!canShow()){dismiss();return;}
        String path=row.path();
        Source source=sourceFor.apply(path);
        if(source==null){dismiss();return;}
        long ticket=generation;
        pending=worker.submit(() -> {
            try {
                var lookup=GameScript.boundedLookup(source.lookup(),128,16L*1024*1024);
                String text=lookup.read(path);
                if(text==null)return;
                var info=GameItemInfo.read(path,text,lookup,1);
                BufferedImage icon=null;
                try {icon=GameTooltipPanel.readIcon(text,source.resources());}
                catch(IOException|IllegalArgumentException ignored) { /* The description can still be shown. */ }
                var content=new Content(info,icon);
                SwingUtilities.invokeLater(() -> {
                    if(ticket==generation&&canShow())show(content);
                });
            }catch(IOException|IllegalArgumentException|IllegalStateException ignored){
                // The entry can disappear while the workspace is replaced or edited.
            }
        });
    }

    private void show(Content content){
        var viewport=SwingUtilities.getAncestorOfClass(JViewport.class,owner);
        var anchor=viewport==null?owner:viewport;
        Point edge=anchor.getLocationOnScreen();
        Point line=new Point(0,row.bounds().y);
        SwingUtilities.convertPointToScreen(line,owner);
        var config=owner.getGraphicsConfiguration();
        var screen=new Rectangle(config.getBounds());
        var insets=Toolkit.getDefaultToolkit().getScreenInsets(config);
        screen.x+=insets.left;
        screen.y+=insets.top;
        screen.width-=insets.left+insets.right;
        screen.height-=insets.top+insets.bottom;

        card=new GameTooltipPanel();
        card.setName("game-navigation-tooltip");
        card.setOpaque(false);
        card.showItem(content.info(),content.icon());
        Dimension size=card.getPreferredSize();
        if(size.height>screen.height)card.setPreferredSize(new Dimension(size.width,screen.height));
        int right=edge.x+anchor.getWidth()+3;
        int left=edge.x-size.width-3;
        int x=right+size.width<=screen.x+screen.width?right:left;
        x=Math.max(screen.x,Math.min(x,screen.x+screen.width-size.width));
        int y=Math.max(screen.y,Math.min(line.y,screen.y+screen.height-Math.min(size.height,screen.height)));
        popup=new JWindow(SwingUtilities.getWindowAncestor(owner));
        popup.setType(Window.Type.POPUP);
        popup.setFocusableWindowState(false);
        popup.setContentPane(card);
        popup.pack();
        popup.setLocation(x,y);
        popup.setVisible(true);
    }

    void refresh(){
        if(owner==null)return;
        clearRequest();
        delay.restart();
    }

    void dismiss(){
        clearRequest();
        owner=null;
        row=null;
    }

    private void clearRequest(){
        generation++;
        delay.stop();
        // Interrupting a read can close the archive's shared channel.
        if(pending!=null){pending.cancel(false);pending=null;}
        if(popup!=null){popup.dispose();popup=null;card=null;}
    }

    @Override public void close(){
        if(closed)return;
        closed=true;
        dismiss();
        worker.shutdown();
    }
}
