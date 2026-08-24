package dev.onelsey.claimshift.config;

import dev.onelsey.claimshift.model.ProtectionAction;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

public record RuleSettings(
        boolean enabled,
        PresencePolicy presencePolicy,
        Duration offlineDelay,
        boolean protectUnknownOfflineOwners,
        boolean trustedPlayersBypass,
        Duration notificationCooldown,
        Map<ProtectionAction, Boolean> actions
) {
    public RuleSettings {
        actions = Map.copyOf(new EnumMap<>(actions));
    }

    public boolean protects(ProtectionAction action) {
        return enabled && actions.getOrDefault(action, false);
    }
}
