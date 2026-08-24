package dev.onelsey.claimshift.model;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

public record ClaimStatus(
        ClaimSnapshot claim,
        ClaimState state,
        Duration remaining,
        Set<UUID> onlineOwners,
        boolean protectedNow
) {
    public ClaimStatus {
        remaining = remaining == null || remaining.isNegative() ? Duration.ZERO : remaining;
        onlineOwners = Set.copyOf(onlineOwners);
    }
}
