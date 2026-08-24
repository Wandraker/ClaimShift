package dev.onelsey.claimshift.integration;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSelectorTest {
    @Test
    void matchesPlainRegionCaseInsensitively() {
        assertTrue(RegionSelector.matches("world", "Spawn", "spawn"));
        assertFalse(RegionSelector.matches("world", "market", "spawn"));
    }

    @Test
    void matchesQualifiedWorldRegion() {
        assertTrue(RegionSelector.matches("world_nether", "fort", "world_nether:fort"));
        assertFalse(RegionSelector.matches("world", "fort", "world_nether:fort"));
    }

    @Test
    void supportsWildcards() {
        assertTrue(RegionSelector.matchesAny("world", "player_onelsey", Set.of("player_*")));
        assertTrue(RegionSelector.matchesAny("world", "plot7", Set.of("world:plot?")));
        assertFalse(RegionSelector.matchesAny("world", "plot77", Set.of("world:plot?")));
    }
}
