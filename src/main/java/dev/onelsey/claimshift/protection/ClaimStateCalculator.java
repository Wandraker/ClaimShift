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

    /**
     * Compatibility overload for the pre-1.3 one-way delay model. Online/effective
     * transitions are immediate and the old offline delay becomes inactiveDelay.
     */
    public static Result calculate(
            Set<UUID> owners,
            Set<UUID> effectiveOwners,
            Map<UUID, Duration> knownInactiveAges,
            PresencePolicy presencePolicy,
            Duration offlineDelay,
            boolean protectUnknownOfflineOwners
    ) {
        return calculate(
                owners,
                effectiveOwners,
                Map.of(),
                knownInactiveAges,
                presencePolicy,
                Duration.ZERO,
                offlineDelay,
                protectUnknownOfflineOwners,
                null
        );
    }

    /**
     * Calculates the desired claim state using two independent transition delays.
     *
     * @param effectiveAges age of each currently effective owner state
     * @param knownInactiveAges age of each known inactive owner state
     * @param activeDelay delay after the presence condition becomes active
     * @param inactiveDelay delay after the last effective owner becomes inactive
     * @param currentlyDynamicOpen null when the provider cannot report current
     *                             dynamic state; otherwise true when ClaimShift is
     *                             already holding the claim open. This prevents a
     *                             reverse delay from creating a new vulnerability
     *                             when the claim is already in its target state.
     */
    public static Result calculate(
            Set<UUID> owners,
            Set<UUID> effectiveOwners,
            Map<UUID, Duration> effectiveAges,
            Map<UUID, Duration> knownInactiveAges,
            PresencePolicy presencePolicy,
            Duration activeDelay,
            Duration inactiveDelay,
            boolean protectUnknownOfflineOwners,
            Boolean currentlyDynamicOpen
    ) {
        PresencePolicy policy = presencePolicy == null ? PresencePolicy.OFFLINE_OPEN : presencePolicy;
        Duration safeActiveDelay = safe(activeDelay);
        Duration safeInactiveDelay = safe(inactiveDelay);

        if (owners == null || owners.isEmpty()) {
            return new Result(ClaimState.OPEN, Duration.ZERO, false);
        }

        Set<UUID> active = effectiveOwners == null ? Set.of() : effectiveOwners;
        Map<UUID, Duration> activeAges = effectiveAges == null ? Map.of() : effectiveAges;
        Map<UUID, Duration> inactiveAges = knownInactiveAges == null ? Map.of() : knownInactiveAges;

        if (!active.isEmpty()) {
            boolean targetProtected = policy == PresencePolicy.OFFLINE_OPEN;
            if (alreadyAtTarget(currentlyDynamicOpen, targetProtected)) {
                return target(targetProtected);
            }

            Duration conditionAge = longestAge(active, activeAges);
            Duration remaining = remaining(safeActiveDelay, conditionAge);
            if (remaining.isPositive()) {
                // During the transition keep the state that applied before an
                // effective owner appeared.
                return new Result(ClaimState.GRACE, remaining, !targetProtected);
            }
            return target(targetProtected);
        }

        boolean targetProtected = policy == PresencePolicy.ONLINE_OPEN;

        Duration longestRemaining = Duration.ZERO;
        boolean hasUnknownInactiveOwner = false;
        for (UUID owner : owners) {
            Duration age = inactiveAges.get(owner);
            if (age == null) {
                hasUnknownInactiveOwner = true;
                continue;
            }
            Duration remaining = remaining(safeInactiveDelay, safe(age));
            if (remaining.isPositive() && remaining.compareTo(longestRemaining) > 0) {
                longestRemaining = remaining;
            }
        }

        if (!hasUnknownInactiveOwner && alreadyAtTarget(currentlyDynamicOpen, targetProtected)) {
            return target(targetProtected);
        }

        if (longestRemaining.isPositive()) {
            return new Result(ClaimState.GRACE, longestRemaining, !targetProtected);
        }

        if (hasUnknownInactiveOwner) {
            // Unknown timestamps are a restart/integration edge case. Keep the
            // explicit fail-closed/fail-open contract instead of guessing an age.
            return protectUnknownOfflineOwners
                    ? new Result(ClaimState.PROTECTED, Duration.ZERO, true)
                    : new Result(ClaimState.OPEN, Duration.ZERO, false);
        }

        return target(targetProtected);
    }

    private static Result target(boolean protectedNow) {
        return new Result(
                protectedNow ? ClaimState.PROTECTED : ClaimState.OPEN,
                Duration.ZERO,
                protectedNow
        );
    }

    private static boolean alreadyAtTarget(Boolean currentlyDynamicOpen, boolean targetProtected) {
        if (currentlyDynamicOpen == null) return false;
        boolean currentlyProtected = !currentlyDynamicOpen;
        return currentlyProtected == targetProtected;
    }

    /**
     * The presence condition is active as long as at least one effective owner is
     * active. Therefore its age is the longest current effective-owner age.
     */
    private static Duration longestAge(Set<UUID> activeOwners, Map<UUID, Duration> ages) {
        Duration longest = Duration.ZERO;
        for (UUID owner : activeOwners) {
            Duration age = safe(ages.get(owner));
            if (age.compareTo(longest) > 0) longest = age;
        }
        return longest;
    }

    private static Duration remaining(Duration delay, Duration age) {
        if (delay.isZero()) return Duration.ZERO;
        Duration value = delay.minus(age);
        return value.isNegative() ? Duration.ZERO : value;
    }

    private static Duration safe(Duration value) {
        return value == null || value.isNegative() ? Duration.ZERO : value;
    }
}
