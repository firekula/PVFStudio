package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1PriestAntiairUpperTest {
    @Test void chargeReleasesTheBladeAndWeaponSpecificQuakeOnTheirNativeFrames() throws Exception {
        String path = "skill/priest/antiairupper.skl";
        String object = "passiveobject/character/priest/";
        var files = new HashMap<String, String>();
        files.put("skill/priestskill.lst", "1 `priest/antiairupper.skl` 66 `priest/chargeweapon.skl`");
        files.put("skill/priest/chargeweapon.skl", "[static data] 200 10 200 30 300 20 50 50 250");
        files.put("passiveobject/passiveobject.lst", "24017 `character/priest/antiairupperblade.obj` "
                + "24018 `character/priest/antiairupperquake.obj`");
        files.put(object + "antiairupperblade.obj", "[basic motion] `blade.ani`");
        files.put(object + "antiairupperquake.obj", "[basic motion] `quake.ani`");
        files.put(object + "blade.ani", ani(100, 100, 100, 100, 100));
        files.put(object + "quake.ani", ani(120, 120, 120, 120, 120, 120));
        files.put("character/priest/animation/antiairupper.ani", ani(70, 180, 60, 60, 60, 70, 70, 70, 140));
        files.put("character/priest/effect/animation/chargeweapon.ani", ani(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1));
        files.put("rosary.equ", "[sub type] 1");
        for (String name : List.of("stay", "overturn")) files.put(Act1SwordAsuraSupplement.GOBLIN + name + ".ani", ani(80));
        var actions = Map.of("etc motion:1", "Animation/AntiairUpper.ani", "waiting motion", "Animation/Stay.ani");

        var choice = Act1PriestAntiairUpper.choices(path, files::get, true).get(0);
        assertNull(Act1PriestAntiairUpper.find(path, files::get, actions, null, ""));
        var charged = Act1PriestAntiairUpper.find(path, files::get, actions, "rosary.equ", choice.key());
        assertNotNull(charged);
        assertEquals(1, charged.poses().get(1).firstFrame());
        assertEquals(250, charged.poses().get(1).start());
        assertEquals(550, charged.poses().get(1).end());
        assertEquals(610, charged.visuals().get(1).start());
        var quake = charged.visuals().get(2);
        assertEquals(730, quake.start());
        assertEquals(-114, quake.x());
        assertEquals(250 / 298.0, quake.scale(), .0001);
        assertTrue(quake.layer() < 500);
        assertTrue(charged.visuals().stream().anyMatch(v -> v.path().endsWith("overturn.ani") && v.start() == quake.start()));
    }

    private static String ani(int... delays) {
        var result = new StringBuilder();
        for (int i = 0; i < delays.length; i++) result.append(String.format(" [FRAME%03d] [DELAY] %d", i, delays[i]));
        return result.toString();
    }
}
