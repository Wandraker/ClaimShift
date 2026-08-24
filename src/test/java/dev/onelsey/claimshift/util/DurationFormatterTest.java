package dev.onelsey.claimshift.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationFormatterTest {
    @Test
    void formatsReadableCompoundDurations() {
        assertEquals("0s", DurationFormatter.format(Duration.ZERO));
        assertEquals("1m 30s", DurationFormatter.format(Duration.ofSeconds(90)));
        assertEquals("1s 500ms", DurationFormatter.format(Duration.ofMillis(1500)));
    }
}
