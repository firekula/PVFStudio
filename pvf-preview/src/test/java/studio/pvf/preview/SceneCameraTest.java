package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.awt.Point;
import static org.junit.jupiter.api.Assertions.*;

class SceneCameraTest {
    @Test void mapLimitsUseNativeWidthAndCompleteExtendedRows() {
        assertEquals(new SceneCamera.Limits(0, 0), SceneCamera.Limits.forMap(1, 0));
        assertEquals(new SceneCamera.Limits(32, 120), SceneCamera.Limits.forMap(3, 5));
        assertEquals(new SceneCamera.Limits(1824, 120), SceneCamera.Limits.forMap(11, 11));
        assertEquals(new SceneCamera.Limits(928, 0), SceneCamera.Limits.forMap(7, 0));
    }
    @Test void edgesDiscardOverscrollSoReversingDirectionMovesImmediately() {
        var camera = new SceneCamera(new SceneCamera.Limits(928, 120));
        camera.move(-10000, -10000); assertEquals(new Point(0, 0), camera.position());
        camera.move(1, 1); assertEquals(new Point(1, 1), camera.position());
        camera.move(10000, 10000); assertEquals(new Point(928, 120), camera.position());
        camera.move(-1, -1); assertEquals(new Point(927, 119), camera.position());
    }
    @Test void fractionalDisplayDragsAccumulateButNativeCoordinatesAreIntegers() {
        var camera = new SceneCamera(new SceneCamera.Limits(10, 10)); camera.set(0, 0);
        for (int i = 0; i < 4; i++) camera.move(.25, .25);
        assertEquals(new Point(1, 1), camera.position());
        assertEquals(29, SceneCamera.layerOffset(50, .58));
        assertEquals(1, SceneCamera.layerOffset(3, .56));
        assertEquals(1003, SceneCamera.layerOffset(912, 1.1));
    }
}
