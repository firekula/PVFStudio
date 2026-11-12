package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1MageSupplementTest {
    private static final String EFFECT = "character/mage/effect/animation/";
    private static final String ANIMATION = "[frame max] 1 [frame000] [image] `sprite.img` 0 [delay] 400";
    private static final Map<String, String> ACTIONS = Map.of(
            "throw motion 1-1", "prepare.ani", "throw motion 1-2", "release.ani",
            "throw motion 3-1", "prepare.ani", "throw motion 3-2", "release.ani",
            "throw motion 4-1", "prepare.ani", "throw motion 4-2", "release.ani",
            "waiting motion", "wait.ani");

    @Test void missileTrailUsesTheProjectilePositionAtEachParticleBirth() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/mageskill.lst", "11 `mage/magicmissile.skl`");
        files.put("passiveobject/passiveobject.lst", "23007 `mage/missile.obj`");
        files.put("passiveobject/mage/missile.obj", "[basic motion] `projectile.ani`");
        files.put("passiveobject/mage/projectile.ani", ANIMATION);
        files.put(EFFECT + "onehandthrow1.ani", ANIMATION);
        files.put(EFFECT + "onehandthrow2.ani", ANIMATION);
        files.put("character/mage/particle/magicmissile.ptl",
                "[x axis] [move type] `[straight line]` [move variable 1] 400 400");
        files.put("passiveobject/character/mage/particle/magicmissileparticle.ptl",
                "[life time] 360 [creation frequency] 20 [object type] `[animation]` [object] `spark.ani` -1");
        var sequence = sequence("magicmissile", "[casting time] 420 [static data] 300 [level info] 3 0 0 520", files);
        var projectile = sequence.visuals().get(sequence.visuals().size() - 1);
        var emitter = sequence.emitters().get(0);
        assertEquals(projectile.motion(), emitter.sourceMotion());
        assertEquals(420, emitter.start());
        assertEquals(520, emitter.emitFor());
        assertEquals(80, emitter.sourceMotion().offsetAt(620), .001);
        assertEquals(1300, sequence.poses().get(sequence.poses().size() - 1).end());
    }

    @Test void contractAndSpiritUseDifferentNativeReleaseModes() throws Exception {
        var files = new HashMap<String, String>();
        files.put("skill/mageskill.lst", "25 `mage/summonhodor.skl` 21 `mage/summonspiritfire.skl`");
        files.put("monster/monster.lst", "900 `hodor/hodor.mob` 60007 `fire/fire.mob`");
        for (String monster : List.of("hodor", "fire")) {
            files.put("monster/" + monster + "/" + monster + ".mob", "[waiting motion] `stay.ani`");
            files.put("monster/" + monster + "/stay.ani", ANIMATION);
        }
        for (int i = 1; i <= 4; i++) files.put(EFFECT + "summonspirit" + i + ".ani", ANIMATION);
        var contract = sequence("summonhodor", "[casting time] 1000 [static data] 2 0 900 2 700 115 1 0", files);
        assertEquals("throw motion 3-1", contract.poses().get(0).action());
        assertEquals(1, contract.visuals().size());
        assertEquals(115, contract.visuals().get(0).x());
        assertEquals(1000, contract.visuals().get(0).start());

        var spirit = sequence("summonspiritfire", "[casting time] 700 [static data] 0 0 60007 3 600 640 1 0", files);
        assertEquals("throw motion 4-1", spirit.poses().get(0).action());
        assertEquals(5, spirit.visuals().size());
        assertEquals(100, spirit.visuals().get(0).x());
        assertEquals(640 / 530.0, spirit.visuals().get(4).scale(), .001);
        assertEquals(1300, spirit.poses().get(1).end());
    }

    private static Act1SkillSequence sequence(String stem, String text, Map<String, String> files) throws Exception {
        return Act1SkillSequence.find("skill/mage/" + stem + ".skl", text, files::get, ACTIONS, 1, "", true);
    }
}
