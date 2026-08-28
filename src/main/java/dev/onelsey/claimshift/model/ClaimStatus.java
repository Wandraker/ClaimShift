package dev.onelsey.claimshift.model;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

public record ClaimStatus(
        ClaimSnapshot claim,
        ClaimState state,
        Duration remaining,
        Set<UUID> onlineOwners,
        Set<UUID> effectiveOwners,
        boolean protectedNow,
        boolean raidActive,
        Duration raidRemaining
) {
    public ClaimStatus {
        remaining = safe(remaining);
        raidRemaining = safe(raidRemaining);
        onlineOwners = Set.copyOf(onlineOwners);
        effectiveOwners = Set.copyOf(effectiveOwners);
    }

    private static Duration safe(Duration value) {
        return value == null || value.isNegative() ? Duration.ZERO : value;
    }
}
