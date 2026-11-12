package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1SpiritAttacksTest {
    private static final String WATER = "monster/spirit/magewaterspirit.mob";
    private static final String WISP = "monster/spirit/magelightspirit.mob";
    private static final String KING = "monster/spikeking/spikeking.mob";
    private static final String ICE = "passiveobject/monster/spirit/animation/icewave1.ani";
    private static final String EMPTY = "[frame000] [delay] 100 [image] `test.img` 0";
    private static final String HIT = EMPTY + " [attack box] 4 -10 0 60 20 50";

    @Test
    void icePillarSpawnsOnTargetWhenTheSecondCastingFrameStarts() throws Exception {
        var assets = new HashMap<String, String>();
        assets.put(WATER, "[attack motion] `wateranimation/attack1.ani` `wateranimation/casting.ani`");
        assets.put("monster/spirit/wateranimation/casting.ani", EMPTY + " [frame001] [delay] 200");
        assets.put("passiveobject/passiveobject.lst", "30380 `monster/spirit/summonmonstericewaveup.obj`");
        assets.put("passiveobject/monster/spirit/summonmonstericewaveup.obj",
                "[basic motion] `animation/icewave1.ani` [attack info] `ice.atk`");
        assets.put("passiveobject/monster/spirit/ice.atk", "[attack enemy] 1");
        assets.put(ICE, EMPTY + " [frame001] [delay] 300 [attack box] -12 -10 0 24 20 80");
        var visuals = new ArrayList<Act1SkillSequence.Visual>();
        var outcome = Act1SpiritAttacks.append(60006, WATER, "mage-spirit:ice-pillar", assets::get, 900, 100,
                visuals);
        assertNotNull(outcome);
        assertEquals(1200, outcome.bodyEnd());
        assertEquals(1400, outcome.end());
        var ice = visuals.stream().filter(visual -> visual.path().equals(ICE)).findFirst().orElseThrow();
        assertEquals(1000, ice.start());
        assertTrue(ice.alliedAttack());
        assertTrue(ice.x() > 100);
        assertTrue(visuals.stream().filter(visual -> !visual.path().equals(ICE)).allMatch(visual -> !visual.alliedAttack()));
    }

    @Test
    void lightningOverlayAndGlowNeverDuplicateTheWispBodyAttack() throws Exception {
        var assets = Map.of(
                WISP, "[attack motion] `normal.ani` `attack2.ani` [attack info] `normal.atk` `light.atk` "
                        + "[etc motion] `decoy.ani` `cast1.ani` `cast2.ani` `attack2effect.ani`",
                "monster/spirit/attack2.ani", HIT,
                "monster/spirit/light.atk", "[attack enemy] 1",
                "monster/spirit/attack2effect.ani", HIT + " [frame001] [delay] 80",
                "equipment/monster/spirit/light1glow.equ", "[layer] 1000 [attack motion] `normal.ani` `attack2.ani`",
                "equipment/monster/spirit/attack2.ani", HIT);
        var visuals = new ArrayList<Act1SkillSequence.Visual>();
        var outcome = Act1SpiritAttacks.append(60009, WISP, "mage-spirit:lightning", assets::get, 900, 100,
                visuals);
        assertNotNull(outcome);
        assertEquals(1000, outcome.bodyEnd());
        assertEquals(1080, outcome.end());
        assertEquals(List.of("monster/spirit/attack2.ani"),
                visuals.stream().filter(Act1SkillSequence.Visual::alliedAttack).map(Act1SkillSequence.Visual::path).toList());
        assertEquals(List.of(900L, 900L, 900L), visuals.stream().map(Act1SkillSequence.Visual::start).toList());
    }

    @Test
    void kingArmEffectStartsWithBodyAndDoesNotOwnItsCollision() throws Exception {
        var assets = Map.of(KING, "[attack motion] `attack.ani` [attack info] `attack.atk` [etc motion] `effect.ani`",
                "monster/spikeking/attack.ani", HIT, "monster/spikeking/attack.atk", "[attack enemy] 1",
                "monster/spikeking/effect.ani", HIT);
        var visuals = new ArrayList<Act1SkillSequence.Visual>();
        var outcome = Act1SpiritAttacks.append(904, KING, "", assets::get, 1900, 100,
                visuals);
        assertNotNull(outcome);
        assertEquals(List.of("monster/spikeking/attack.ani"),
                visuals.stream().filter(Act1SkillSequence.Visual::alliedAttack).map(Act1SkillSequence.Visual::path).toList());
        assertEquals(1900, visuals.stream().filter(visual -> visual.path().endsWith("effect.ani")).findFirst().orElseThrow().start());
    }

    @Test
    void consumedAllyRemainsVisibleUnderAnIncreasingWhiteMaskUntilRemoval() throws Exception {
        var assets = Map.of(KING, "[attack motion] `attack.ani` `cast.ani` `cast.ani` "
                        + "[etc motion] `arm.ani` `self.ani` `other.ani`",
                "monster/spikeking/cast.ani", EMPTY + " [frame001] [delay] 1900",
                "monster/spikeking/self.ani", EMPTY,
                "monster/spikeking/other.ani", EMPTY,
                "monster/spirit/fireanimation/stay.ani", EMPTY);
        var visuals = new ArrayList<Act1SkillSequence.Visual>();
        var result = Act1SpiritAttacks.append(904, KING, "mage-spirit:king-consume", assets::get, 900, 100,
                visuals);
        assertNotNull(result);
        var ally = visuals.stream().filter(visual -> visual.path().endsWith("fireanimation/stay.ani")).toList();
        assertEquals(2, ally.size());
        assertEquals(1, ally.get(0).style().sample(0).opacity());
        assertEquals(0, ally.get(1).style().sample(0).opacity());
        assertEquals(.5, ally.get(1).style().sample(750).opacity());
        assertEquals(List.of(2400L, 2400L), ally.stream().map(Act1SkillSequence.Visual::end).toList());

        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80501234);
        var sprite = new NpkResources.Sprite(image, 0, 0, 1, 1, null, "test.img", 0, 0);
        var frame = ally.get(1).style().animation().frames().get(0);
        assertEquals(0x80ffffff, PreviewRenderer.prepare(frame, sprite).image().getRGB(0, 0));
    }

    @Test
    void unavailableIceObjectRemovesTheChoiceWithoutAddingACast() throws Exception {
        var assets = Map.of(WATER, "[attack motion] `normal.ani` `casting.ani`",
                "monster/spirit/casting.ani", EMPTY + " [frame001] [delay] 200");
        var visuals = new ArrayList<Act1SkillSequence.Visual>();
        assertTrue(Act1SpiritAttacks.choices("skill/mage/summonspiritwater.skl", 60006, WATER, assets::get).isEmpty());
        assertNull(Act1SpiritAttacks.append(60006, WATER, "mage-spirit:ice-pillar", assets::get, 900, 100,
                visuals));
        assertTrue(visuals.isEmpty());
    }
}
