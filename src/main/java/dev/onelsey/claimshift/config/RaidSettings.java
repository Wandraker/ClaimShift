package dev.onelsey.claimshift.config;

import dev.onelsey.claimshift.model.ProtectionAction;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

public record RaidSettings(
        boolean enabled,
        Duration inactivityTimeout,
        Duration maximumDuration,
        boolean extendOnActivity,
        Set<ProtectionAction> triggerActions
) {
    public RaidSettings {
        inactivityTimeout = nonNegative(inactivityTimeout);
        maximumDuration = nonNegative(maximumDuration);
        triggerActions = triggerActions == null || triggerActions.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(triggerActions));
    }

    private static Duration nonNegative(Duration value) {
        return value == null || value.isNegative() ? Duration.ZERO : value;
    }
}
