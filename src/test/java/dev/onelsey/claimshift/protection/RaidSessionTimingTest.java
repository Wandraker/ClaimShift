package dev.onelsey.claimshift.protection;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidSessionTimingTest {
    @Test
    void inactivityDeadlineEndsSession() {
        long s = Duration.ofSeconds(1).toNanos();
        assertFalse(RaidSessionTiming.expired(0, 10 * s, Duration.ofSeconds(30), Duration.ofMinutes(5), 39 * s));
        assertTrue(RaidSessionTiming.expired(0, 10 * s, Duration.ofSeconds(30), Duration.ofMinutes(5), 40 * s));
    }

    @Test
    void continuedActivityCannotPassHardMaximum() {
        long m = Duration.ofMinutes(1).toNanos();
        assertEquals(
                Duration.ofMinutes(1),
                RaidSessionTiming.remaining(0, 4 * m, Duration.ofMinutes(10), Duration.ofMinutes(5), 4 * m)
        );
        assertTrue(RaidSessionTiming.expired(0, 4 * m, Duration.ofMinutes(10), Duration.ofMinutes(5), 5 * m));
    }

    @Test
    void zeroMaximumMeansNoHardCap() {
        long m = Duration.ofMinutes(1).toNanos();
        assertEquals(
                Duration.ofMinutes(10),
                RaidSessionTiming.remaining(0, 20 * m, Duration.ofMinutes(10), Duration.ZERO, 20 * m)
        );
    }
}
