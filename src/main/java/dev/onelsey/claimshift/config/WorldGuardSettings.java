package dev.onelsey.claimshift.config;

import java.time.Duration;
import java.util.Set;

public record WorldGuardSettings(
        Mode mode,
        boolean manageAllOwnedRegions,
        boolean manageExistingPassthroughRegions,
        Set<String> includedRegions,
        Set<String> excludedRegions,
        Duration reconcileInterval
) {
    public enum Mode {
        DYNAMIC_PASSTHROUGH,
        OVERLAY
    }

    public WorldGuardSettings {
        includedRegions = Set.copyOf(includedRegions);
        excludedRegions = Set.copyOf(excludedRegions);
    }
}
