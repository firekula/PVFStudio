package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1MageImpactTest {
    @Test void shieldReplacesItsLoopWithTwoHitLayersDuringTheGoblinStrike() throws Exception {
        var assets = goblin();
        assets.put("skill/mageskill.lst", "37 `Mage/AuraShield.skl`");
        assets.put("character/mage/effect/animation/aurashieldstart.ani", animation(160));
        assets.put("character/mage/effect/animation/aurashieldloop.ani", animation(400));
        assets.put("character/mage/effect/animation/aurashieldhit1.ani", animation(430));
        assets.put("character/mage/effect/animation/aurashieldhit2.ani", animation(520));
        var sequence = Act1SkillSequence.find("skill/mage/aurashield.skl", "[casting time] 800", assets::get,
                Map.of("buff motion", "buff.ani", "waiting motion", "stay.ani"), 1, "mage-aurashield:hit", true);
        assertNotNull(sequence);
        var first = sequence.visuals().stream().filter(v -> v.path().endsWith("aurashieldhit1.ani")).findFirst().orElseThrow();
        var second = sequence.visuals().stream().filter(v -> v.path().endsWith("aurashieldhit2.ani")).findFirst().orElseThrow();
        assertEquals(first.start(), second.start());
        assertEquals(first.end(), second.end());
        assertEquals(430, first.end() - first.start());
        assertTrue(sequence.visuals().stream().anyMatch(v -> v.path().endsWith("aurashieldloop.ani") && v.start() == first.end()));
        assertTrue(sequence.visuals().stream().anyMatch(v -> v.path().endsWith("animation_goblin/attack1.ani")
                && v.start() < first.start() && v.end() > first.start()));
    }

    @Test void palmAttachesItsKnockbackVolumeWhenTheSlideReachesTheGoblin() throws Exception {
        var assets = goblin();
        assets.put("skill/mageskill.lst", "14 `Mage/HandStrike.skl`");
        String mage = "character/mage/";
        String frames = animation(70, 30, 30, 1, 1, 1, 1, 10000, 1, 40, 70, 70, 70, 160);
        assets.put(mage + "hand.ani", frames);
        assets.put(mage + "effect/animation/handstrike.ani", frames);
        assets.put("passiveobject/passiveobject.lst", "23029 `mage/palm.obj`");
        assets.put("passiveobject/mage/palm.obj", "[basic motion] `volume.ani`");
        assets.put("passiveobject/mage/volume.ani", "[FRAME000] [IMAGE] `` 0 [DELAY] 300");
        assets.put(mage + "effect/particle/handhit1.ptl", "[life time] 280 [creation frequency] 10 [maximum create number] 2");
        assets.put(mage + "effect/particle/handhit2.ptl", "[life time] 200 [creation frequency] 100 [maximum create number] 10");
        var sequence = Act1SkillSequence.find("skill/mage/handstrike.skl", "", assets::get,
                Map.of("etc motion:4", "hand.ani", "waiting motion", "stay.ani"), 1, "mage-handstrike:hit", true);
        assertNotNull(sequence);
        var volume = sequence.visuals().stream().filter(v -> v.path().endsWith("volume.ani")).findFirst().orElseThrow();
        var target = sequence.visuals().stream().filter(v -> v.path().endsWith("overturn.ani")).findFirst().orElseThrow();
        assertEquals(176, volume.start());
        assertEquals(300, volume.end() - volume.start());
        assertEquals(volume.start(), target.start());
        assertEquals(volume.x(), target.x());
        assertTrue(target.motion().offsetAt(target.end()) > 0);
        assertEquals(2, sequence.emitters().size());
        assertTrue(sequence.emitters().stream().allMatch(emitter -> emitter.start() == volume.start()));
    }

    @Test void chargedPalmHoldsTheCastAndPlaysTheSharedImpactOnlyAtTheWall() throws Exception {
        var assets = goblin();
        assets.put("skill/mageskill.lst", "14 `Mage/HandStrike.skl`");
        String mage = "character/mage/";
        String frames = animation(70, 30, 30, 1, 1, 1, 1, 10000, 1, 40, 70, 70, 70, 160);
        assets.put(mage + "hand.ani", frames);
        assets.put(mage + "effect/animation/handstrike.ani", frames);
        assets.put("passiveobject/passiveobject.lst", "23029 `mage/palm.obj`");
        assets.put("passiveobject/mage/palm.obj", "[basic motion] `volume.ani`");
        assets.put("passiveobject/mage/volume.ani", "[FRAME000] [IMAGE] `` 0 [DELAY] 300");
        for (int i = 0; i < 3; i++) {
            assets.put("character/fighter/effect/animation/throwenemywall" + i + ".ani", animation(350));
        }
        for (int i = 1; i <= 2; i++) {
            assets.put(mage + "effect/animation/magiconcharge4" + i + ".ani", animation(40, 40));
            assets.put(mage + "effect/animation/magicfullcharge4" + i + ".ani", animation(80));
        }
        String text = "[static data] 500 -800 500 200 500 200 [level info] 8 150 250 400 200 0 75 125 75";
        var actions = Map.of("etc motion:4", "hand.ani", "waiting motion", "stay.ani");
        var shortHit = Act1SkillSequence.find("skill/mage/handstrike.skl", text, assets::get,
                actions, 1, "mage-handstrike:hit", true);
        assertNotNull(shortHit);
        assertTrue(shortHit.visuals().stream().noneMatch(v -> v.path().contains("/throwenemywall")));
        var charged = Act1SkillSequence.find("skill/mage/handstrike.skl", text, assets::get,
                actions, 1, "mage-handstrike:wall", true);
        assertNotNull(charged);
        var charging = charged.visuals().stream().filter(v -> v.path().contains("magiconcharge4")).toList();
        var chargedFlash = charged.visuals().stream().filter(v -> v.path().contains("magicfullcharge4")).toList();
        assertEquals(2, charging.size());
        assertEquals(2, chargedFlash.size());
        assertTrue(charging.stream().allMatch(v -> v.start() == 134 && v.end() == 634 && v.y() == -106));
        assertTrue(chargedFlash.stream().allMatch(v -> v.start() == 634 && v.motion().start() == 634));
        var contact = charged.visuals().stream().filter(v -> v.path().endsWith("/volume.ani")).findFirst().orElseThrow();
        assertEquals(675, contact.start());
        var impacts = charged.visuals().stream().filter(v -> v.path().contains("/throwenemywall")).toList();
        assertEquals(3, impacts.size());
        assertTrue(impacts.stream().allMatch(v -> v.start() == contact.end() && v.y() < -30));
        var caster = charged.poses().get(0).motion();
        assertEquals(impacts.get(0).x(), caster.offsetAt(charged.poses().get(0).end()), 1);
        var rebound = charged.visuals().stream().filter(v -> v.label().equals("落花掌 · 撞墙反弹")).findFirst().orElseThrow();
        assertEquals(contact.end(), rebound.start());
        assertTrue(rebound.motion().offsetAt(rebound.end()) < 0);
        assertTrue(Act1MageSupplement.choices("skill/mage/handstrike.skl", assets::get, true).stream()
                .anyMatch(choice -> choice.key().equals("mage-handstrike:wall")));
    }

    private static HashMap<String, String> goblin() {
        var result = new HashMap<String, String>();
        for (String stem : new String[]{"stay", "down", "overturn"}) {
            result.put(Act1SwordAsuraSupplement.GOBLIN + stem + ".ani", animation(100));
        }
        result.put(Act1SwordAsuraSupplement.GOBLIN + "attack1.ani", animation(80, 80, 80, 80, 200));
        return result;
    }

    private static String animation(int... delays) {
        var source = new StringBuilder();
        for (int i = 0; i < delays.length; i++) {
            source.append(String.format("[FRAME%03d] [IMAGE] `fixture.img` 0 [DELAY] %d%n", i, delays[i]));
        }
        return source.toString();
    }
}
