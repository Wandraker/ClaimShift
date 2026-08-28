package dev.onelsey.claimshift.protection;

import java.time.Duration;

/** Pure monotonic raid-session deadline helper used by runtime code and tests. */
final class RaidSessionTiming {
    private RaidSessionTiming() {
    }

    static boolean expired(
            long startedNanos,
            long lastActivityNanos,
            Duration inactivityTimeout,
            Duration maximumDuration,
            long nowNanos
    ) {
        return nowNanos >= deadline(startedNanos, lastActivityNanos, inactivityTimeout, maximumDuration);
    }

    static Duration remaining(
            long startedNanos,
            long lastActivityNanos,
            Duration inactivityTimeout,
            Duration maximumDuration,
            long nowNanos
    ) {
        long end = deadline(startedNanos, lastActivityNanos, inactivityTimeout, maximumDuration);
        return Duration.ofNanos(Math.max(0L, end - nowNanos));
    }

    static long deadline(
            long startedNanos,
            long lastActivityNanos,
            Duration inactivityTimeout,
            Duration maximumDuration
    ) {
        long inactivityEnd = addSaturated(lastActivityNanos, safeNanos(inactivityTimeout));
        if (maximumDuration == null || maximumDuration.isZero() || maximumDuration.isNegative()) {
            return inactivityEnd;
        }
        return Math.min(inactivityEnd, addSaturated(startedNanos, safeNanos(maximumDuration)));
    }

    private static long safeNanos(Duration duration) {
        if (duration == null || duration.isNegative()) return 0L;
        try {
            return duration.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long addSaturated(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }
}
