package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ActionScriptTest {
    @Test void readsInitialBaseAndSubLayersWithOffsetsAndIgnoresBehaviors() throws Exception {
        var motion = ActionScript.motion("[MOTION] [BASE ANI] `../animation/body.ani` [SUB ANI] `back.ani` 0 -10 `front.ani` 0 100 [/SUB ANI] "
                + "[SUB ANI WITH XY] `glow.ani` 0 101 3 4 [/SUB ANI WITH XY] [SUB ANI WITH XYZ] `smoke.ani` 2 102 5 6 7 [/SUB ANI WITH XYZ] [/MOTION] "
                + "[BEHAVIOR] [BASE ANI] `never.ani` [SET ACTION] [CUSTOM] 99 [/BEHAVIOR]");
        assertEquals("../animation/body.ani",motion.base()); assertEquals(4,motion.parts().size()); assertTrue(motion.warnings().isEmpty());
        assertEquals(-10,motion.parts().get(0).layer()); assertEquals(3,motion.parts().get(2).x());
        assertEquals(7,motion.parts().get(3).z()); assertEquals(2,motion.parts().get(3).startFrame());
    }
    @Test void malformedSubGroupKeepsBaseButUnscopedOrUnterminatedMotionIsRejected() throws Exception {
        var motion = ActionScript.motion("[MOTION] [BASE ANI] `body.ani` [SUB ANI] `partial.ani` 0 [/SUB ANI] [/MOTION]");
        assertEquals("body.ani",motion.base()); assertTrue(motion.parts().isEmpty()); assertEquals(1,motion.warnings().size());
        assertThrows(IOException.class,()->ActionScript.motion("[BASE ANI] `outside.ani`"));
        assertThrows(IOException.class,()->ActionScript.motion("[MOTION] [BASE ANI] `body.ani`"));
        assertThrows(IOException.class,()->ActionScript.motion("[MOTION] [BASE ANI] `body.act` [/MOTION]"));
    }
}
