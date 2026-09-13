package dev.onelsey.claimshift.protection;

import java.time.Duration;
import java.util.Optional;

/**
 * Converts a persisted wall-clock last-seen timestamp into an absence age.
 * Runtime presence timing still uses System.nanoTime; wall time is only a
 * restart fallback because monotonic timestamps cannot survive a JVM restart.
 */
final class PresenceAgeResolver {
    private PresenceAgeResolver() {
    }

    static Optional<Duration> fromLastSeen(long lastSeenEpochMillis, long nowEpochMillis) {
        if (lastSeenEpochMillis <= 0L) {
            return Optional.empty();
        }
        if (nowEpochMillis <= lastSeenEpochMillis) {
            return Optional.of(Duration.ZERO);
        }
        return Optional.of(Duration.ofMillis(nowEpochMillis - lastSeenEpochMillis));
    }
}
