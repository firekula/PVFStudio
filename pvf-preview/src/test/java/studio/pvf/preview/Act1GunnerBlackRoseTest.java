package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1GunnerBlackRoseTest {
    @Test void secondCommandStartsContinuousFireOnFrameEightAndDismissesTheSquad() throws Exception {
        String gunner = "passiveobject/character/gunner/";
        var files = new HashMap<String, String>();
        files.put("skill/gunnerskill.lst", "70 `gunner/blackrose.skl`");
        files.put("data/skill/gunner.sd", "[int] `br appear delay` 520 `br appear term` 200 `br appear time` 1400 `br disappear time` 940");
        var catalog = new StringBuilder("22229 `character/gunner/blackrosemanager.obj` 22236 `character/gunner/blackrosebulletgrenade.obj`");
        files.put(gunner + "blackrosemanager.obj", "[name] `manager`");
        files.put(gunner + "blackrosebulletgrenade.obj", "[basic motion] `grenade.ani` [etc motion] `bomb1.ani` `bomb2.ani`");
        var soldiers = List.of("blackroseshotgun", "blackroserifle1", "blackroserifle2", "blackrosegrenade");
        for (int i = 0; i < soldiers.size(); i++) {
            String stem = soldiers.get(i);
            catalog.append(' ').append(22230 + i).append(" `character/gunner/").append(stem).append(".obj`");
            files.put(gunner + stem + ".obj", "[etc motion] `appear.ani` `stay.ani` `" + stem + "attack.ani` `move.ani` "
                    + "`disappear.ani` `appear1.ani` `appear2.ani` `fade1.ani` `fade2.ani` "
                    + "[string data] `muzzle1.ani` `muzzle2.ani` `muzzle3.ani` `muzzle4.ani` `" + stem + ".ptl`");
            files.put(gunner + stem + "attack.ani", i == 0 || i == 3 ? ani(350, 50, 600, 50, 200) : ani(30, 10, 90, 50, 30));
            files.put(gunner + stem + ".ptl", "[x axis] [move type] `[straight line]` [move variable 1] 700 700 "
                    + "[gravity] 1000 [rotation angle] 0 15 0");
        }
        files.put("passiveobject/passiveobject.lst", catalog.toString());
        files.put("character/gunner/animation/blackrose.ani", ani(20, 20, 200, 50, 50, 50, 50, 300, 50, 80, 80, 80, 2710));
        GameScript.Lookup lookup = path -> files.containsKey(path) ? files.get(path) : path.endsWith(".ani") ? ani(100) : null;
        var actions = Map.of("waiting motion", "Animation/Stay.ani", "throw motion 2-1", "Animation/Command1.ani",
                "throw motion 2-2", "Animation/Command2.ani", "etc motion:42", "Animation/BlackRose.ani");
        String text = "[static data] 700 800 1 1000 100 10 700 100 5 500 100 1 1500 100 100 50 3000 [level info] 1 30000";

        var normal = Act1GunnerSupportCast.find(Act1GunnerBlackRose.PATH, text, lookup, actions, 1, "");
        assertNotNull(normal);
        assertEquals(30700, normal.visuals().stream().filter(v -> v.label().endsWith("退场")).findFirst().orElseThrow().start());
        var hit = normal.visuals().stream().filter(v -> v.label().endsWith("哥布林受击")).findFirst().orElseThrow();
        var rifle = normal.emitters().stream().filter(p -> p.path().endsWith("blackroserifle2.ptl")).findFirst().orElseThrow();
        assertEquals(11.6, rifle.y(), .001);
        assertEquals(2130, rifle.start());
        assertEquals(2270, hit.start());

        var choice = Act1GunnerSupportCast.choices(Act1GunnerBlackRose.PATH, lookup, true).get(0);
        var focused = Act1GunnerSupportCast.find(Act1GunnerBlackRose.PATH, text, lookup, actions, 1, choice.key());
        assertNotNull(focused);
        var firing = focused.visuals().stream().filter(v -> v.label().endsWith("步枪二集中射击")).findFirst().orElseThrow();
        assertEquals(2840, firing.start());
        assertEquals(5840, firing.end());
        assertEquals(210, firing.style().loopPeriod());
        assertTrue(focused.emitters().stream().anyMatch(e -> e.path().endsWith("blackroserifle2.ptl") && e.start() == 2870));
        assertTrue(focused.visuals().stream().anyMatch(v -> v.label().endsWith("退场") && v.start() == 5840));
    }

    private static String ani(int... delays) {
        var result = new StringBuilder();
        for (int i = 0; i < delays.length; i++) result.append(String.format(" [FRAME%03d] [DELAY] %d", i, delays[i]));
        return result.toString();
    }
}
