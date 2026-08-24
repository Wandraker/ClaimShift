package dev.onelsey.claimshift.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationParserTest {
    @Test
    void parsesSingleUnits() {
        assertEquals(Duration.ofMillis(1500), DurationParser.parse("1500ms"));
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("30s"));
        assertEquals(Duration.ofMinutes(1), DurationParser.parse("1m"));
        assertEquals(Duration.ofHours(2), DurationParser.parse("2h"));
        assertEquals(Duration.ofDays(3), DurationParser.parse("3d"));
    }

    @Test
    void parsesCompoundAndWhitespace() {
        assertEquals(Duration.ofMinutes(61).plusSeconds(2), DurationParser.parse("1h 1m 2s"));
        assertEquals(Duration.ofMillis(62_250), DurationParser.parse("1m2s250ms"));
    }

    @Test
    void rejectsMalformedDurations() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("1 minute"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("1m-nope"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("-1m"));
    }
}
