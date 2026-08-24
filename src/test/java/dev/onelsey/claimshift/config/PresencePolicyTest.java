package dev.onelsey.claimshift.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PresencePolicyTest {
    @Test
    void parsesSupportedPoliciesCaseInsensitively() {
        assertEquals(PresencePolicy.ONLINE_OPEN, PresencePolicy.parse("online-open"));
        assertEquals(PresencePolicy.OFFLINE_OPEN, PresencePolicy.parse("OFFLINE-OPEN"));
    }

    @Test
    void rejectsUnknownPolicy() {
        assertThrows(IllegalArgumentException.class, () -> PresencePolicy.parse("sometimes-open"));
    }
}
