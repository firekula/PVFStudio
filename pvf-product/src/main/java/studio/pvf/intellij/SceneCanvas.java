package studio.pvf.intellij;

import studio.pvf.preview.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

class SceneCanvas extends JPanel {
    interface Interaction {
        void press(Point2D point,boolean cycle); void drag(Point2D point); void release(Point2D point); void cancel();
        void paint(Graphics2D g);
    }
    Interaction interaction;
    Runnable viewChanged=() -> {};
    private boolean spacePan,editingGesture;
    PreviewRenderer.Prepared prepared; ScenePreview.Rendered scene;
    SceneCamera camera;
    Rectangle2D world; String error = ""; int zoom;
    boolean overlay, markers, clientViewport; double panX, panY, freeZoom; Point drag;
    SceneCanvas() {
        setFocusable(true);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("pressed SPACE"),"pan-start");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("released SPACE"),"pan-stop");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ESCAPE"),"cancel-gesture");
        getActionMap().put("pan-start",new AbstractAction(){public void actionPerformed(ActionEvent e){spacePan=true;}});
        getActionMap().put("pan-stop",new AbstractAction(){public void actionPerformed(ActionEvent e){spacePan=false;}});
        getActionMap().put("cancel-gesture",new AbstractAction(){public void actionPerformed(ActionEvent e){if(interaction!=null)interaction.cancel();editingGesture=false;repaint();}});
        setPreferredSize(new Dimension(400, 360)); setToolTipText("拖动平移，滚轮缩放");
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                if(usingClient()&&!clientBounds(view().scale()).contains(event.getPoint()))return;
                if(interaction!=null&&!spacePan&&SwingUtilities.isLeftMouseButton(event)) {
                    editingGesture=true; interaction.press(event.getPoint(),event.isAltDown()); return;
                }
                if (!SwingUtilities.isLeftMouseButton(event)&&!SwingUtilities.isMiddleMouseButton(event))return;
                drag = event.getPoint(); setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override public void mouseReleased(MouseEvent event) {
                if(editingGesture&&interaction!=null)interaction.release(event.getPoint()); editingGesture=false;
                drag = null; setCursor(Cursor.getDefaultCursor()); viewChanged.run();
            }
            @Override public void mouseDragged(MouseEvent event) {
                if(editingGesture&&interaction!=null){interaction.drag(event.getPoint());return;}
                if (drag == null) return;
                if (usingClient()) camera.move((drag.x - event.getX()) / view().scale(), (drag.y - event.getY()) / view().scale());
                else { panX += event.getX() - drag.x; panY += event.getY() - drag.y; }
                drag = event.getPoint(); repaint();
            }
            @Override public void mouseMoved(MouseEvent event) {
                View view = view();
                if (usingClient() && !clientBounds(view.scale()).contains(event.getPoint())) { setToolTipText("客户端视口外；在边框内拖动移动相机"); return; }
                double x = (event.getX() - view.x() - (usingClient() ? 0 : panX)) / view.scale(),
                        y = (event.getY() - view.y() - (usingClient() ? 0 : panY)) / view.scale();
                String entity = scene == null ? "" : scene.entityAt(x, y);
                String grid = scene != null && overlay ? scene.collisionAt(x, y) : "";
                String tooltip = entity + (!entity.isEmpty() && !grid.isEmpty() ? "；" : "") + grid;
                setToolTipText(tooltip.isEmpty() ? usingClient() ? "拖动相机，到达地图边界后停止；滚轮缩放视口" : "拖动平移，滚轮缩放" : tooltip);
            }
            @Override public void mouseWheelMoved(MouseWheelEvent event) {
                View old = view();
                if (usingClient()) {
                    if (!clientBounds(old.scale()).contains(event.getPoint())) return;
                    freeZoom = Math.max(.02, Math.min(8, old.scale() * Math.pow(1.2, -event.getPreciseWheelRotation()))); repaint(); viewChanged.run(); return;
                }
                double wx = (event.getX() - old.x() - panX) / old.scale(), wy = (event.getY() - old.y() - panY) / old.scale();
                freeZoom = Math.max(.02, Math.min(8, old.scale() * Math.pow(1.2, -event.getPreciseWheelRotation())));
                View current = view(); panX = event.getX() - current.x() - wx * current.scale();
                panY = event.getY() - current.y() - wy * current.scale(); repaint();viewChanged.run();
            }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
    }
    void resetView() { panX = 0; panY = 0; freeZoom = 0; drag = null; if (camera != null) camera.reset(); }
    Point2D worldToView(double x,double y,double scroll) {
        View v=view(); double originX;
        if(usingClient())originX=clientBounds(v.scale()).x-SceneCamera.layerOffset(camera.position().x,scroll)*v.scale();
        else originX=v.x()+panX*scroll-(scene==null||scene.cameraLimits()==null?0:scene.cameraLimits().maxX()/2.0)*(scroll-1)*v.scale();
        return new Point2D.Double(originX+x*v.scale(),v.y()+(usingClient()?0:panY)+y*v.scale());
    }
    Point2D viewToWorld(Point2D point,double scroll) {
        Point2D origin=worldToView(0,0,scroll); double scale=view().scale();
        return new Point2D.Double((point.getX()-origin.getX())/scale,(point.getY()-origin.getY())/scale);
    }
    boolean usingClient() { return clientViewport && scene != null && scene.cameraLimits() != null && camera != null; }
    Rectangle clientBounds(double scale) {
        int width = Math.max(1, (int) Math.round(SceneCamera.WIDTH * scale)), height = Math.max(1, (int) Math.round(SceneCamera.HEIGHT * scale));
        return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
    }
    private View view() {
        double scale = freeZoom > 0 ? freeZoom : new double[]{1, .25, .5, 1, 2, 4}[zoom];
        double ox = getWidth() / 2.0, oy = getHeight() * .72;
        if (world != null && (scene != null || zoom == 0 || freeZoom > 0)) {
            if (zoom == 0 && freeZoom == 0) scale = Math.min(4,
                    Math.min(Math.max(1, getWidth() - 40) / (usingClient() ? SceneCamera.WIDTH : Math.max(1, world.getWidth())),
                            Math.max(1, getHeight() - 40) / (usingClient() ? SceneCamera.HEIGHT : Math.max(1, world.getHeight()))));
            ox = getWidth() / 2.0 - world.getCenterX() * scale;
            oy = getHeight() / 2.0 - world.getCenterY() * scale;
        }
        if (usingClient()) {
            Rectangle viewport = clientBounds(scale); Point position = camera.position();
            ox = viewport.x - position.x * scale; oy = viewport.y - position.y * scale;
        }
        return new View(ox, oy, scale);
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create();
        try {
            for (int y = 0; y < getHeight(); y += 16) for (int x = 0; x < getWidth(); x += 16) {
                g.setColor(((x / 16 + y / 16) & 1) == 0 ? new Color(54, 57, 62) : new Color(63, 66, 72));
                g.fillRect(x, y, 16, 16);
            }
            View view = view();
            if (scene != null) {
                if (usingClient()) {
                    Rectangle viewport = clientBounds(view.scale()); Point position = camera.position();
                    g.setColor(new Color(0, 0, 0, 100)); g.fillRect(0, 0, getWidth(), getHeight());
                    scene.paintClient(g, viewport, view.scale(), position, overlay, markers);
                    // Draw after the scene using the canvas clip, so scene clipping cannot hide the frame.
                    g.setStroke(new BasicStroke(1)); g.setColor(new Color(15, 20, 27));
                    g.drawRect(viewport.x - 2, viewport.y - 2, viewport.width + 3, viewport.height + 3);
                    g.setColor(new Color(125, 195, 250)); g.drawRect(viewport.x - 1, viewport.y - 1, viewport.width + 1, viewport.height + 1);
                    String label = "客户端视口 640×480 · 相机 " + position.x + "/" + camera.limits().maxX() + ", " + position.y + "/" + camera.limits().maxY();
                    g.drawString(label, Math.max(6, viewport.x), Math.max(14, viewport.y - 6));
                } else scene.paint(g, new Rectangle(0, 0, getWidth(), getHeight()), view.x(), view.y(), view.scale(), panX, panY, overlay, markers);
            }
            else {
                g.setColor(new Color(140, 147, 158, 120));
                g.drawLine(0, (int) (view.y() + panY), getWidth(), (int) (view.y() + panY));
                g.drawLine((int) (view.x() + panX), 0, (int) (view.x() + panX), getHeight());
                if (prepared != null) prepared.paint(g, view.x() + panX, view.y() + panY, view.scale());
            }
            if(interaction!=null)interaction.paint(g);
            if (!error.isEmpty()) { g.setColor(Color.LIGHT_GRAY); g.drawString(error, 16, 28); }
        } finally { g.dispose(); }
    }
    private record View(double x, double y, double scale) {}
}
