package dev.onelsey.claimshift.model;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record ClaimSnapshot(
        String provider,
        String id,
        String name,
        String world,
        Set<UUID> owners,
        Set<UUID> trustedPlayers,
        Map<String, String> attributes
) {
    public ClaimSnapshot {
        owners = Set.copyOf(owners);
        trustedPlayers = Set.copyOf(trustedPlayers);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public ClaimSnapshot(
            String provider,
            String id,
            String name,
            String world,
            Set<UUID> owners,
            Set<UUID> trustedPlayers
    ) {
        this(provider, id, name, world, owners, trustedPlayers, Map.of());
    }

    public boolean isTrusted(UUID playerId) {
        return trustedPlayers.contains(playerId) || owners.contains(playerId);
    }

    public String key() {
        return provider + ":" + id;
    }

    public Optional<String> attribute(String key) {
        String value = attributes.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
