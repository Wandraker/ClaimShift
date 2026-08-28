package dev.onelsey.claimshift.protection;

import java.time.Duration;
import java.util.List;

/** Pure helper for recognising simple low-frequency periodic keep-alive patterns. */
public final class ActivityPatternDetector {
    private ActivityPatternDetector() {
    }

    public static boolean isPeriodic(
            List<Long> timestampsNanos,
            int minimumSamples,
            Duration minimumInterval,
            Duration tolerance
    ) {
        if (timestampsNanos == null || timestampsNanos.size() < minimumSamples || minimumSamples < 3) {
            return false;
        }
        int start = timestampsNanos.size() - minimumSamples;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (int i = start + 1; i < timestampsNanos.size(); i++) {
            long interval = Math.max(0L, timestampsNanos.get(i) - timestampsNanos.get(i - 1));
            min = Math.min(min, interval);
            max = Math.max(max, interval);
        }
        long required = safeNanos(minimumInterval);
        long allowedSpread = safeNanos(tolerance);
        return min >= required && max - min <= allowedSpread;
    }

    private static long safeNanos(Duration duration) {
        try {
            return Math.max(0L, duration.toNanos());
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
