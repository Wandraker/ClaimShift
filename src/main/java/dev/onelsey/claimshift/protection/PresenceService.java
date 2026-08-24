package dev.onelsey.claimshift.protection;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PresenceService {
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> lastQuitNanos = new ConcurrentHashMap<>();

    public void initialize(Server server) {
        online.clear();
        for (Player player : server.getOnlinePlayers()) {
            online.add(player.getUniqueId());
        }
    }

    public void markOnline(UUID playerId) {
        online.add(playerId);
        lastQuitNanos.remove(playerId);
    }

    public void markOffline(UUID playerId) {
        online.remove(playerId);
        lastQuitNanos.put(playerId, System.nanoTime());
    }

    public boolean isOnline(UUID playerId) {
        return online.contains(playerId);
    }

    public Set<UUID> onlineOwners(Set<UUID> owners) {
        Set<UUID> result = new HashSet<>();
        for (UUID owner : owners) {
            if (isOnline(owner)) {
                result.add(owner);
            }
        }
        return result;
    }

    public Optional<Duration> offlineAge(UUID playerId) {
        if (isOnline(playerId)) {
            return Optional.of(Duration.ZERO);
        }
        Long quitAt = lastQuitNanos.get(playerId);
        if (quitAt == null) {
            return Optional.empty();
        }
        long elapsed = Math.max(0L, System.nanoTime() - quitAt);
        return Optional.of(Duration.ofNanos(elapsed));
    }
}
