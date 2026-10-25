package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResourceScriptTest {
    @Test void act1FramesRespectParametersAndDoNotParseQuotedOrCommentedTags() throws Exception {
        var animation = ResourceScript.animation("""
                // [FRAME999] [IMAGE] `bad.img` 10
                [LOOP] 1
                [FRAME MAX] 2
                [FRAME000]
                [IMAGE] `Monster\\Ghoul\\SkullBombParticle.img` 0
                [IMAGE POS] -93 -86
                [IMAGE RATE] 2.0 -1.0
                [IMAGE ROTATE] 3.14
                [RGBA] 128 64 32 255
                [DELAY] 80
                [DAMAGE TYPE] `[NORMAL]`
                /* [FRAME999] [DELAY] 20 */
                [FRAME001]
                [IMAGE] `Monster/Ghoul/SkullBombParticle.img` 1
                [DELAY] 120
                """);
        assertTrue(animation.loop()); assertEquals(2, animation.frames().size());
        var frame = animation.frames().get(0);
        assertEquals(-93, frame.x()); assertEquals(-86, frame.y()); assertEquals(2, frame.scaleX());
        assertEquals(-1, frame.scaleY()); assertEquals(3.14, frame.rotation()); assertEquals(64, frame.g());
        assertEquals(80, frame.delay()); assertEquals(4, frame.line());
        assertEquals(120, animation.frames().get(1).delay());
    }

    @Test void referencesKeepTagContextAndIgnoreComments() throws Exception {
        var refs = ResourceScript.references("""
                [icon] `Item/IconMaterial2.img` 5
                [field image] `Item/FieldImage.img` 28
                // [fake] `no.img` 4
                [attack motion]
                `Animation/Attack.ani`
                `Animation/Stay.ani`
                [/attack motion]
                [name] `[fake]`
                """);
        assertEquals(4, refs.size()); assertEquals("icon", refs.get(0).tag()); assertEquals(5, refs.get(0).index());
        assertEquals("field image", refs.get(1).tag()); assertTrue(refs.get(2).animation());
        assertEquals("attack motion", refs.get(3).tag());
    }

    @Test void relativeAnimationPathsResolveInsidePvf() {
        assertEquals(List.of("monster/goblin/animation/stay.ani", "animation/stay.ani"),
                ResourceScript.animationCandidates("monster/goblin/goblin.mob", "Animation\\Stay.ani"));
        assertEquals(List.of("monster/shared/stay.ani"),
                ResourceScript.animationCandidates("monster/goblin/goblin.mob", "../Shared/Stay.ani"));
        assertEquals(List.of("monster/stay.ani"), ResourceScript.animationCandidates("a/b.mob", "/Monster/Stay.ani"));
    }

    @Test void partialEditsAndInvalidValuesHaveActionableErrors() {
        for (String bad : List.of("[FRAME000] [IMAGE POS] -1", "[FRAME000] [IMAGE] `a.img`",
                "[FRAME000] [IMAGE RATE] NaN 1",
                "[FRAME000] [RGBA] 255 255 255 256", "[FRAME000] [DELAY] -1"))
            assertThrows(IOException.class, () -> ResourceScript.animation(bad), bad);
    }

    @Test void realAct1LongDelaysRepeatedLabelsAndEmptyAnimationsRemainInspectable() throws Exception {
        var animation = ResourceScript.animation("[FRAME MAX] 1 [FRAME000] [DELAY] 60000000 [FRAME000] [DELAY] 1");
        assertEquals(2, animation.frames().size()); assertEquals(60000000, animation.frames().get(0).delay());
        assertEquals(2, animation.warnings().size());
        assertTrue(ResourceScript.animation("[FRAME MAX] 0").frames().isEmpty());
    }

    @Test void effectsAreReportedAndBlankFramesDoNotReusePreviousImages() throws Exception {
        var animation = ResourceScript.animation("[FRAME000] [IMAGE] `a.img` 0 [INTERPOLATION] 1 "
                + "[GRAPHIC EFFECT] `UNSUPPORTED` [FRAME001] [DELAY] 10");
        assertEquals(1, animation.warnings().size()); assertTrue(animation.frames().get(0).interpolation());
        assertTrue(animation.frames().get(1).image().isEmpty());
    }
}
