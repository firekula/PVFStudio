package studio.pvf.preview;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class AnimationTimelineTest {
    @Test void unrelatedPeriodsKeepTheirOwnPhaseOnAnUnboundedClock() throws Exception {
        var a = timeline(true, 100, 100); var b = timeline(true, 150, 150);
        assertEquals(0, a.frameAt(200, false)); assertEquals(1, b.frameAt(200, false));
        assertEquals(1, a.frameAt(350, false)); assertEquals(0, b.frameAt(350, false));
        long hoursLater = 600L * 100_000_000 + 250;
        assertEquals(0, a.frameAt(hoursLater, false)); assertEquals(1, b.frameAt(hoursLater, false));
    }
    @Test void nonLoopingAnimationsHoldTheirLastFrameAndCanBeForcedToLoop() throws Exception {
        var timeline = timeline(false, 80, 120, 160);
        assertEquals(1, timeline.frameAt(80, false)); assertEquals(2, timeline.frameAt(360, false));
        assertEquals(2, timeline.frameAt(Long.MAX_VALUE, false)); assertEquals(0, timeline.frameAt(360, true));
    }
    @Test void zeroDelaysDoNotCauseDivideByZeroOrAnInfiniteTick() throws Exception {
        var mixed = timeline(true, 0, 5, 0);
        assertEquals(1, mixed.frameAt(0, false)); assertEquals(1, mixed.frameAt(5, false));
        assertEquals(1, timeline(true, 0, 0).frameAt(100000, false));
        assertEquals(-1, new AnimationTimeline(new ResourceScript.Animation(false, List.of(), List.of())).frameAt(0, false));
    }
    @Test void clockPauseResumeSpeedAndSeekPreserveAbsoluteTime() {
        AtomicLong now = new AtomicLong(); var clock = new AnimationTimeline.Clock(now::get);
        clock.play(); now.set(1_500_000_000L); assertEquals(1500, clock.milliseconds());
        clock.speed(2); now.set(2_000_000_000L); assertEquals(2500, clock.milliseconds());
        clock.pause(); now.set(10_000_000_000L); assertEquals(2500, clock.milliseconds());
        clock.play(); now.set(10_250_000_000L); assertEquals(3000, clock.milliseconds());
        clock.seek(7200000); assertEquals(7200000, clock.milliseconds());
        clock.pause(); clock.seek(-1); assertEquals(0, clock.milliseconds());
    }
    private static AnimationTimeline timeline(boolean loop, int... delays) throws Exception {
        StringBuilder source = new StringBuilder("[LOOP] " + (loop ? 1 : 0));
        for (int i = 0; i < delays.length; i++) source.append(" [FRAME").append(i).append("] [DELAY] ").append(delays[i]);
        return new AnimationTimeline(ResourceScript.animation(source.toString()));
    }
}
