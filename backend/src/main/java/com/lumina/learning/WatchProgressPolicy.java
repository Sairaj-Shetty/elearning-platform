package com.lumina.learning;

import java.time.Duration;
import java.time.Instant;

/**
 * Credits watch time only when the new position is plausible given wall-clock time.
 * A single forged jump to the end does not complete the lesson.
 */
public final class WatchProgressPolicy {
    public record State(
            double lastPositionSeconds,
            double durationSeconds,
            double accumulatedWatchSeconds,
            Instant lastWatchedAt,
            boolean completed
    ) {}

    public record Update(double positionSeconds, double reportedDurationSeconds, Instant now) {}

    public record Result(
            double lastPositionSeconds,
            double durationSeconds,
            double accumulatedWatchSeconds,
            double maxWatchedPercent,
            boolean justCompleted,
            int milestone
    ) {}

    public Result apply(State state, Update update) {
        double duration = state.durationSeconds();
        if (duration <= 0 && update.reportedDurationSeconds() >= 1 && update.reportedDurationSeconds() <= 14_400) {
            duration = update.reportedDurationSeconds();
        }
        double position = Math.max(0, update.positionSeconds());
        if (duration > 0) {
            position = Math.min(position, duration + 1);
        }
        double accumulated = state.accumulatedWatchSeconds();
        if (state.lastWatchedAt() != null && update.now() != null) {
            double elapsed = Duration.between(state.lastWatchedAt(), update.now()).toMillis() / 1000.0;
            if (elapsed < 0) elapsed = 0;
            double advanced = position - state.lastPositionSeconds();
            if (advanced > 0 && advanced <= elapsed + 3) {
                accumulated += advanced;
            } else if (advanced > 0) {
                accumulated += Math.min(elapsed, 15);
            }
        }
        if (duration > 0) {
            accumulated = Math.min(accumulated, duration);
        }
        double percent = duration > 0 ? Math.min(100.0, accumulated / duration * 100.0) : 0;
        boolean justCompleted = !state.completed() && duration > 0 && accumulated + 0.001 >= duration * 0.9;
        int milestone = 0;
        if (percent >= 99.9) milestone = 100;
        else if (percent >= 75) milestone = 75;
        else if (percent >= 50) milestone = 50;
        else if (percent >= 25) milestone = 25;
        return new Result(round(position), round(duration), round(accumulated), round(percent), justCompleted, milestone);
    }

    public boolean canManuallyComplete(double accumulatedWatchSeconds, double durationSeconds) {
        return durationSeconds > 0 && accumulatedWatchSeconds + 0.001 >= durationSeconds * 0.8;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
