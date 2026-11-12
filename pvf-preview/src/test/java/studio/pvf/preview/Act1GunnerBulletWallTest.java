package studio.pvf.preview;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Act1GunnerBulletWallTest {
    private static final String ROOT = "character/gunner/";
    private static final String FX = ROOT + "effect/animation/";
    private static final String FAST = "skill/gunner/fastshoot.skl";
    private static final String TURN = "skill/gunner/walkshootturning.skl";
    private static final String WALL = "gunner-bullet:wall";

    @Test void ordinaryBulletStopsAtItsWallContactAndPlaysTheTerrainClipInBothDirections() throws Exception {
        var files = fixture();
        for (String skill : List.of(FAST, TURN)) {
            var preview = GamePreview.load(skill, "[type] `[active]`", files::get, files.keySet(), sprites(),
                    GamePreview.Options.defaults().withEffect(WALL));
            assertTrue(preview.usingSequence());
            assertTrue(preview.effects().stream().anyMatch(effect -> effect.key().equals(WALL)));
            long fire = skill.equals(TURN) ? 800 : 262;
            long contact = fire + 90;
            var before = preview.render(contact - 1, false).draws();
            assertTrue(before.stream().anyMatch(d -> d.label().endsWith("弹道")));
            assertTrue(before.stream().noneMatch(d -> d.label().endsWith("子弹撞墙")));
            var hit = preview.render(contact, false).draws();
            assertTrue(hit.stream().noneMatch(d -> d.label().endsWith("弹道") || d.label().contains("目标")));
            var impact = hit.stream().filter(d -> d.label().endsWith("子弹撞墙")).findFirst().orElseThrow();
            var mirror = preview.render(contact, true).draws().stream().filter(d -> d.label().endsWith("子弹撞墙"))
                    .findFirst().orElseThrow();
            assertEquals("wall.img", impact.prepared().source().img());
            assertEquals(skill.equals(TURN) ? -95 : 119, impact.x(), .01);
            assertEquals(-119, impact.y(), 3);
            assertEquals(-impact.x(), mirror.x(), .01);
            assertEquals(impact.y(), mirror.y(), .01);
            assertNotEquals(impact.mirrored(), mirror.mirrored());
        }
    }

    private static HashMap<String, String> fixture() {
        var f = new HashMap<String, String>();
        f.put("skill/gunnerskill.lst", "11 `Gunner/FastShoot.skl` 32 `Gunner/WalkShootTurning.skl`");
        f.put(ROOT + "gunner.chr", """
                [attack speed] 1000.0
                [waiting motion] `Animation/Stay.ani`
                [attack motion] `Animation/Ready.ani` `Animation/Ready.ani`
                [etc motion] `Animation/Fire.ani` `Animation/Fire.ani` `Animation/Fire.ani` `Animation/Fire.ani`
                """ + " `Animation/Stay.ani`".repeat(19) + " `Animation/Stay.ani`".repeat(4));
        f.put(ROOT + "animation/stay.ani", animation("body.img", 150));
        f.put(ROOT + "animation/ready.ani", animation("body.img", 100, 100, 10000));
        f.put(ROOT + "animation/fire.ani", animation("body.img", 10000, 100));
        f.put(FX + "fastshootup.ani", animation("flash.img", 140));
        f.put(FX + "shooteffect/uppistol.ani", animation("muzzle.img", 140));
        f.put(FX + "hiteffect/collisionwall.ani", animation("wall.img", 250));
        f.put("etc/gunnerdefaultguninfo.etc", """
                [sub type] 0 [integer] 4 4 80 80 100 1000 100 95 25 1000 [/integer]
                [string] `Character/Gunner/Particle/Bullet.ptl` `Character/Gunner/Particle/Bullet.ptl` [/string]
                """);
        f.put(ROOT + "particle/bullet.ptl", """
                [creation pos] 86 0 120 [x axis] [move type] `[straight line]` [move variable 1] 800 800
                [life time] 500 [creation frequency] -1 [maximum create number] 1 [object type] `[passive object]`
                [object] `` 22001 [/object]
                """);
        f.put("passiveobject/passiveobject.lst", "22001 `Character/Gunner/Bullet.obj`");
        f.put("passiveobject/character/gunner/bullet.obj", "[basic motion] `Bullet.ani`");
        f.put("passiveobject/character/gunner/bullet.ani", animation("bullet.img", 500));
        return f;
    }

    private static String animation(String image, int... delays) {
        var frames = new StringBuilder();
        for (int i = 0; i < delays.length; i++) {
            frames.append("[FRAME%03d] [IMAGE] `%s` 0 [DELAY] %d%n".formatted(i, image, delays[i]));
        }
        return frames.toString();
    }

    private static ScenePreview.SpriteLookup sprites() {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffffffff);
        return new ScenePreview.SpriteLookup() {
            public NpkResources.Info info(String path, int frame) { return new NpkResources.Info(1, 1, 0, 0, 1, 1); }
            public NpkResources.Sprite sprite(String path, int frame) {
                return new NpkResources.Sprite(image, 0, 0, 1, 1, Path.of("fixture.npk"), path, frame, frame);
            }
        };
    }
}
