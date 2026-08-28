package dev.onelsey.claimshift.config;

import dev.onelsey.claimshift.model.ProtectionAction;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

public record RuleSettings(
        boolean enabled,
        PresencePolicy presencePolicy,
        Duration activeDelay,
        Duration inactiveDelay,
        boolean protectUnknownOfflineOwners,
        boolean trustedPlayersBypass,
        PresenceSettings presence,
        RaidSettings raids,
        Duration notificationCooldown,
        Map<ProtectionAction, Boolean> actions
) {
    public RuleSettings {
        activeDelay = nonNegative(activeDelay);
        inactiveDelay = nonNegative(inactiveDelay);
        actions = Map.copyOf(new EnumMap<>(actions));
    }

    /**
     * Compatibility accessor for code written against the pre-1.3 configuration
     * model. The old offline-delay was the delay after the last active owner
     * became inactive, which is now named inactiveDelay.
     */
    public Duration offlineDelay() {
        return inactiveDelay;
    }

    public boolean protects(ProtectionAction action) {
        return enabled && actions.getOrDefault(action, false);
    }

    private static Duration nonNegative(Duration value) {
        return value == null || value.isNegative() ? Duration.ZERO : value;
    }
}
