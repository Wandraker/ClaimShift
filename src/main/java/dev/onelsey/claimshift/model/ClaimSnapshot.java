
package dev.onelsey.claimshift.model;

import java.util.Set;
import java.util.UUID;

public record ClaimSnapshot(
        String provider,
        String id,
        String name,
        String world,
        Set<UUID> owners,
        Set<UUID> trustedPlayers
) {
    public ClaimSnapshot {
        owners = Set.copyOf(owners);
        trustedPlayers = Set.copyOf(trustedPlayers);
    }

    public boolean isTrusted(UUID playerId) {
        return trustedPlayers.contains(playerId) || owners.contains(playerId);
    }

    public String key() {
        return provider + ":" + id;
    }
}
