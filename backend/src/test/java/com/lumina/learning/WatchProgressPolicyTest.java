package com.lumina.learning;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchProgressPolicyTest {
    private final WatchProgressPolicy policy = new WatchProgressPolicy();

    @Test
    void aSingleJumpToTheEndDoesNotCompleteTheLesson() {
        var result = policy.apply(
                new WatchProgressPolicy.State(0, 0, 0, null, false),
                new WatchProgressPolicy.Update(600, 600, Instant.parse("2026-10-11T10:00:00Z"))
        );
        assertFalse(result.justCompleted());
        assertTrue(result.accumulatedWatchSeconds() < 1);
    }

    @Test
    void steadyPlaybackCompletesAtNinetyPercent() {
        Instant start = Instant.parse("2026-10-11T10:00:00Z");
        var first = policy.apply(
                new WatchProgressPolicy.State(0, 0, 0, null, false),
                new WatchProgressPolicy.Update(0, 100, start)
        );
        var second = policy.apply(
                new WatchProgressPolicy.State(first.lastPositionSeconds(), first.durationSeconds(), first.accumulatedWatchSeconds(), start, false),
                new WatchProgressPolicy.Update(95, 100, start.plusSeconds(95))
        );
        assertTrue(second.justCompleted());
        assertTrue(policy.canManuallyComplete(80, 100));
        assertFalse(policy.canManuallyComplete(70, 100));
    }
}
