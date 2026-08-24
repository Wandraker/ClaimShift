package dev.onelsey.claimshift.protection;

import dev.onelsey.claimshift.config.PresencePolicy;
import dev.onelsey.claimshift.model.ClaimState;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure state calculator used by the runtime service and unit tests.
 */
public final class ClaimStateCalculator {
    public record Result(ClaimState state, Duration remaining, boolean protectedNow) {
        public Result {
            remaining = remaining == null || remaining.isNegative() ? Duration.ZERO : remaining;
        }
    }

    private ClaimStateCalculator() {
    }

    public static Result calculate(
            Set<UUID> owners,
            Set<UUID> onlineOwners,
            Map<UUID, Duration> knownOfflineAges,
            PresencePolicy presencePolicy,
            Duration offlineDelay,
            boolean protectUnknownOfflineOwners
    ) {
        PresencePolicy policy = presencePolicy == null ? PresencePolicy.ONLINE_OPEN : presencePolicy;
        if (owners == null || owners.isEmpty()) {
            return new Result(ClaimState.OPEN, Duration.ZERO, false);
        }
        if (onlineOwners != null && !onlineOwners.isEmpty()) {
            boolean protectedNow = policy == PresencePolicy.OFFLINE_OPEN;
            return new Result(protectedNow ? ClaimState.PROTECTED : ClaimState.OPEN, Duration.ZERO, protectedNow);
        }

        Duration longestRemaining = Duration.ZERO;
        boolean hasUnknownOfflineOwner = false;
        for (UUID owner : owners) {
            Duration age = knownOfflineAges.get(owner);
            if (age == null) {
                hasUnknownOfflineOwner = true;
                if (!protectUnknownOfflineOwners) {
                    continue;
                }
                // Unknown owners do not extend a known grace period. They only
                // decide the safe restart fallback when no timed transition exists.
                continue;
            }

            Duration safeAge = age.isNegative() ? Duration.ZERO : age;
            Duration remaining = offlineDelay.minus(safeAge);
            if (remaining.isPositive() && remaining.compareTo(longestRemaining) > 0) {
                longestRemaining = remaining;
            }
        }

        if (longestRemaining.isPositive()) {
            boolean protectedDuringGrace = policy == PresencePolicy.OFFLINE_OPEN;
            return new Result(ClaimState.GRACE, longestRemaining, protectedDuringGrace);
        }

        if (hasUnknownOfflineOwner) {
            if (protectUnknownOfflineOwners) {
                return new Result(ClaimState.PROTECTED, Duration.ZERO, true);
            }
            return new Result(ClaimState.OPEN, Duration.ZERO, false);
        }

        boolean protectedNow = policy == PresencePolicy.ONLINE_OPEN;
        return new Result(protectedNow ? ClaimState.PROTECTED : ClaimState.OPEN, Duration.ZERO, protectedNow);
    }
}
