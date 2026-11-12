package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class Act1GoreCrossTest {
    private static final String ROOT="character/swordman/";
    private static final Map<String,String> ACTIONS=Map.of("etc motion:25","Animation/GoreCross.ani","waiting motion","Animation/Stay.ani");
    private static String animation(int... delays){
        var text=new StringBuilder();
        for(int i=0;i<delays.length;i++)text.append("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d\n".formatted(i,delays[i]));
        return text.toString();
    }
    private static HashMap<String,String> fixture(){
        var files=new HashMap<String,String>();
        files.put(ROOT+"animation/gorecross.ani",animation(50,50,50,50,50,50,50,50,50,280));
        files.put(ROOT+"effect/animation/gorecross/slash1.ani",animation(550));
        files.put(ROOT+"effect/animation/gorecross/slash2.ani",animation(550,40));
        files.put(ROOT+"particle/gorecross.ptl","[x axis] [move type] `[straight line]` [move variable 1] 400 0 [changing term] 280 [changing type] `[keep last value]`");
        files.put("passiveobject/passiveobject.lst","20028 `character/swordman/gorecross.obj`");
        files.put("passiveobject/character/swordman/gorecross.obj","[basic motion] `Animation/GoreCross1.ani` [add object effect] 1 `Animation/GoreCross2.ani` -1 [/add object effect] [object destroy condition] `[destroy condition]` `[on end of animation]`");
        files.put("passiveobject/character/swordman/animation/gorecross1.ani",animation(140,70,70));
        files.put("passiveobject/character/swordman/animation/gorecross2.ani",animation(140,70,70));
        return files;
    }
    @Test void waitsForSecondSlashCompletionAndKeepsBothAttachedSlashes() throws Exception {
        var files=fixture();var cast=Act1GoreCross.find(files::get,ACTIONS);
        assertNotNull(cast);assertEquals(4,cast.visuals().size());
        assertEquals(0,cast.visuals().get(0).start());assertEquals(0,cast.visuals().get(1).start());
        assertEquals(590,cast.visuals().get(2).start());assertEquals(870,cast.visuals().get(2).end());
        assertEquals("waiting motion",cast.poses().get(1).action());assertEquals(870,cast.poses().get(1).end());
        assertTrue(cast.emitters().isEmpty(),"The PTL describes movement, not repeated projectile emission");
        files.put(ROOT+"effect/animation/gorecross/slash2.ani",animation(690));
        assertEquals(690,Act1GoreCross.find(files::get,ACTIONS).visuals().get(2).start());
        files.put(ROOT+"animation/gorecross.ani",animation(150,150,150,150,150,150,50,50,50,280));
        assertEquals(900,Act1GoreCross.find(files::get,ACTIONS).visuals().get(2).start());
    }
    @Test void projectileLayersShareOneDeceleratingClockAndEndWithObject() throws Exception {
        var cast=Act1GoreCross.find(fixture()::get,ACTIONS);
        var under=cast.visuals().get(2);var front=cast.visuals().get(3);
        assertTrue(under.path().endsWith("gorecross2.ani"));assertTrue(front.path().endsWith("gorecross1.ani"));
        assertTrue(under.layer()<front.layer());assertEquals(under.motion(),front.motion());
        assertEquals(85,front.x());assertEquals(-82,front.y());
        assertEquals(0,front.motion().offsetAt(590),.0001);
        assertEquals(42,front.motion().offsetAt(730),.0001);
        assertEquals(56,front.motion().offsetAt(870),.0001);
        assertEquals(56,front.motion().offsetAt(1200),.0001);
    }
    @Test void movementEditsAndObjectLifetimeAreReadFromCurrentScripts() throws Exception {
        var files=fixture();files.put(ROOT+"particle/gorecross.ptl","[x axis] [move type] `[straight line]` [move variable 1] 600 0 [changing term] 400 [changing type] `[keep last value]`");
        files.put("passiveobject/character/swordman/animation/gorecross1.ani",animation(600));
        var front=Act1GoreCross.find(files::get,ACTIONS).visuals().get(3);
        assertEquals(1190,front.end());assertEquals(120,front.motion().offsetAt(1190),.0001);
    }
    @Test void rejectsMissingDependenciesAndUnsupportedMotionRatherThanInventingTrajectory() throws Exception {
        var files=fixture();files.remove("passiveobject/character/swordman/animation/gorecross2.ani");
        assertNull(Act1GoreCross.find(files::get,ACTIONS));
        files=fixture();files.put(ROOT+"particle/gorecross.ptl","[x axis] [move type] `[sin]` [move variable 1] 400 0");
        assertNull(Act1GoreCross.find(files::get,ACTIONS));
        assertNull(Act1GoreCross.find(fixture()::get,Map.of("etc motion:25","Animation/Other.ani")));
    }
}
