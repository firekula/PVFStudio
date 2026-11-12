package studio.pvf.preview;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Act1GunnerMechanicalSupplementTest {
    static final Map<String, String> ACTIONS = Map.of("etc motion:32", "Animation/SetRobot.ani",
            "waiting motion", "Animation/Stay.ani");
    private static final Map<String, String> CONFIG = Map.of(
            "robotrx78", "[level info] 3 5 250 7000 [/level info]",
            "robotez8", "[level info] 4 18 500 3000 100 [/level info]",
            "robotexs", "[static data] 200 5 1000 [/static data] [level info] 4 30 50 400 30000 [/level info]",
            "robottempester", "[static data] 150 5 200 2 400 10000 [/static data] [level info] 6 35 219 1314 657 1533 30000 [/level info]");

    static HashMap<String, String> bodyFiles() {
        var files = new HashMap<String, String>();
        files.put("skill/gunnerskill.lst", "27 `gunner/robotrx78.skl` 28 `gunner/robotez8.skl` 29 `gunner/robotexs.skl` 63 `gunner/robottempester.skl`");
        files.put("monster/monster.lst", "60002 `gunner/robot/rx78.mob` 60003 `gunner/robot/ez8.mob` 60004 `gunner/robot/exs.mob` 60018 `gunner/robot/tempester.mob`");
        files.put("character/gunner/animation/setrobot.ani", ani(150));
        files.put("character/gunner/animation/stay.ani", ani(100));
        var bodies = Map.of("rx78", "rx78_ani/stay", "ez8", "ez8_ani/stay1",
                "exs", "exs_ani/body", "tempester", "tempesterani/bodystand");
        bodies.forEach((type, body) -> {
            String root = "monster/gunner/robot/";
            files.put(root + type + ".mob", "[waiting motion] `" + body + ".ani`"
                    + (type.equals("tempester") ? " [floating height] 150" : ""));
            files.put(root + body + ".ani", ani(100));
        });
        files.put("monster/gunner/robot/exs_ani/cannonstand.ani", ani(1000));
        files.put("monster/gunner/robot/tempesterani/effectbooststay.ani", ani(100));
        return files;
    }

    private static String ani(int delay) {
        return "[LOOP] 1 [FRAME000] [IMAGE] `fixture.img` 0 [DELAY] " + delay;
    }

    private static Act1SkillSequence load(String name, int cast, GameScript.Lookup lookup) throws Exception {
        return Act1GunnerMechanicalSupplement.find("skill/gunner/" + name + ".skl",
                "[type] `[active]` [casting time] " + cast + " " + cast + " " + CONFIG.get(name), lookup, ACTIONS, 1, "");
    }

    @Test void allRobotsAppearAfterTheirCastAtTheNativeFacingOffset() throws Exception {
        String[] names = {"robotrx78", "robotez8", "robotexs", "robottempester"};
        int[] times = {300, 400, 500, 600};
        for (int i = 0; i < names.length; i++) {
            var s = load(names[i], times[i], Act1RobotAttackTest.fixture());
            assertNotNull(s);
            assertEquals("etc motion:32", s.poses().get(0).action());
            assertTrue(s.poses().get(0).fitToStage());
            assertEquals(times[i], s.poses().get(0).end());
            var body = s.visuals().get(0);
            assertEquals(times[i], body.start());
            assertEquals(30, body.x());
            assertTrue(body.mirror());
        }
    }

    @Test void exsCannonAndTempesterBoostUseTheirNativeBackLayers() throws Exception {
        var exs = load("robotexs", 500, Act1RobotAttackTest.fixture());
        var gun = exs.visuals().stream().filter(v -> v.path().endsWith("cannonstand.ani")).findFirst().orElseThrow();
        assertEquals(-60, gun.y());
        assertTrue(gun.layer() < exs.visuals().get(0).layer());
        var tempester = load("robottempester", 600, Act1RobotAttackTest.fixture());
        var initial = tempester.visuals().stream().filter(v -> v.start() == 600).toList();
        assertEquals(2, initial.size());
        assertTrue(initial.stream().allMatch(v -> v.y() == -150));
        assertTrue(initial.get(1).layer() < initial.get(0).layer());
    }

    @Test void missingPartsAndCatalogRebindingRejectWholeRecipe() throws Exception {
        var source = Act1RobotAttackTest.fixture();
        assertNull(load("robotexs", 500, path -> path.endsWith("/cannonstand.ani") ? null : source.read(path)));
        assertNull(load("robotrx78", 300, path -> path.equals("monster/monster.lst")
                ? "60002 `gunner/robot/foreign.mob`" : source.read(path)));
        assertNull(load("robotrx78", 300, path -> path.equals("skill/gunnerskill.lst")
                ? "27 `gunner/foreign.skl`" : source.read(path)));
    }

    @Test void foreignChoiceMotionAndNonActiveSkillFailClosed() throws Exception {
        var f = Act1RobotAttackTest.fixture();
        String path = "skill/gunner/robotrx78.skl";
        assertNull(Act1GunnerMechanicalSupplement.find(path, "[casting time] 300 300", f, ACTIONS, 1, "bbq:miss"));
        assertNull(Act1GunnerMechanicalSupplement.find(path, "[type] `[passive]`", f, ACTIONS, 1, ""));
        assertNull(Act1GunnerMechanicalSupplement.find(path, "", f, Map.of("etc motion:32", "Animation/Fake.ani"), 1, ""));
    }
}
