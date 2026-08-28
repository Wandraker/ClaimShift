package dev.onelsey.claimshift.protection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityPatternDetectorTest {
    @Test
    void detectsSimpleFiveMinuteKeepAlivePattern() {
        long m = Duration.ofMinutes(1).toNanos();
        assertTrue(ActivityPatternDetector.isPeriodic(
                List.of(0L, 5*m, 10*m, 15*m, 20*m),
                5,
                Duration.ofSeconds(30),
                Duration.ofSeconds(3)
        ));
    }

    @Test
    void smallHumanTimingVariationOutsideToleranceIsNotPeriodic() {
        long s = Duration.ofSeconds(1).toNanos();
        assertFalse(ActivityPatternDetector.isPeriodic(
                List.of(0L, 300*s, 617*s, 895*s, 1235*s),
                5,
                Duration.ofSeconds(30),
                Duration.ofSeconds(3)
        ));
    }

    @Test
    void fastNormalGameplayIsNotClassifiedAsKeepAlivePattern() {
        long s = Duration.ofSeconds(1).toNanos();
        assertFalse(ActivityPatternDetector.isPeriodic(
                List.of(0L, 2*s, 4*s, 6*s, 8*s),
                5,
                Duration.ofSeconds(30),
                Duration.ofSeconds(3)
        ));
    }
    @Test
    void timingVariationExactlyAtToleranceIsPeriodic() {
        long s = Duration.ofSeconds(1).toNanos();
        assertTrue(ActivityPatternDetector.isPeriodic(
                List.of(0L, 300*s, 603*s, 903*s, 1206*s),
                5,
                Duration.ofSeconds(30),
                Duration.ofSeconds(3)
        ));
    }

    @Test
    void intervalJustBelowMinimumIsNotPeriodic() {
        long s = Duration.ofSeconds(1).toNanos();
        assertFalse(ActivityPatternDetector.isPeriodic(
                List.of(0L, 29*s, 58*s, 87*s, 116*s),
                5,
                Duration.ofSeconds(30),
                Duration.ofSeconds(3)
        ));
    }

    @Test
    void usesOnlyMostRecentConfiguredSampleWindow() {
        long s = Duration.ofSeconds(1).toNanos();
        assertTrue(ActivityPatternDetector.isPeriodic(
                List.of(0L, 41*s, 500*s, 800*s, 1100*s, 1400*s, 1700*s),
                5,
                Duration.ofSeconds(30),
                Duration.ofSeconds(3)
        ));
    }

}
