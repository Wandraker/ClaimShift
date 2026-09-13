package dev.onelsey.claimshift.protection;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceAgeResolverTest {
    @Test
    void zeroLastSeenRemainsUnknown() {
        assertTrue(PresenceAgeResolver.fromLastSeen(0L, 10_000L).isEmpty());
    }

    @Test
    void persistedLastSeenSurvivesRestartAsAbsenceAge() {
        assertEquals(
                Duration.ofHours(2),
                PresenceAgeResolver.fromLastSeen(1_000L, 7_201_000L).orElseThrow()
        );
    }

    @Test
    void backwardClockShiftDoesNotCreateNegativeAge() {
        assertEquals(
                Duration.ZERO,
                PresenceAgeResolver.fromLastSeen(10_000L, 9_000L).orElseThrow()
        );
    }
}
