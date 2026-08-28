package dev.onelsey.claimshift.protection;

import dev.onelsey.claimshift.ClaimShiftPlugin;
import dev.onelsey.claimshift.config.PresenceSettings;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional AFK bridge that uses runtime reflection so ClaimShift does not need
 * to bundle or hard-depend on CMI/EssentialsX APIs.
 *
 * <p>External plugin APIs are sampled on the player's entity scheduler and the
 * result is cached. This keeps ClaimShift's WorldGuard/global reconciliation
 * from making cross-region player API calls on Folia.</p>
 */
public final class ExternalAfkBridge {
    private interface Detector {
        String name();
        Optional<Boolean> isAfk(UUID playerId) throws Exception;
    }

    private record CacheEntry(Optional<String> source, long checkedNanos) {
    }

    private static final long CACHE_TTL_NANOS = Duration.ofSeconds(5).toNanos();

    private final ClaimShiftPlugin plugin;
    private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Set<UUID> refreshing = ConcurrentHashMap.newKeySet();
    private volatile List<Detector> detectors = List.of();

    public ExternalAfkBridge(ClaimShiftPlugin plugin) {
        this.plugin = plugin;
    }

    public void refresh(PresenceSettings settings) {
        cache.clear();
        refreshing.clear();
        if (!settings.externalAfkEnabled()) {
            detectors = List.of();
            return;
        }
        List<Detector> found = new ArrayList<>();
        if (settings.useCmiAfk()) {
            Detector cmi = createCmiDetector();
            if (cmi != null) found.add(cmi);
        }
        if (settings.useEssentialsAfk()) {
            Detector essentials = createEssentialsDetector();
            if (essentials != null) found.add(essentials);
        }
        detectors = List.copyOf(found);
        if (!found.isEmpty()) {
            plugin.getLogger().info("Smart Presence external AFK bridge(s): "
                    + String.join(", ", found.stream().map(Detector::name).toList()));
        }
    }

    public List<String> sources() {
        return detectors.stream().map(Detector::name).toList();
    }

    /**
     * Returns the last scheduler-safe AFK sample and requests a refresh when it
     * is missing or stale. The first sample is intentionally fail-open: Smart
     * Presence's own idle detector still protects against indefinite AFK use.
     */
    public Optional<String> afkSource(UUID playerId) {
        if (detectors.isEmpty()) return Optional.empty();

        long now = System.nanoTime();
        CacheEntry current = cache.get(playerId);
        if (current == null || now - current.checkedNanos() >= CACHE_TTL_NANOS) {
            scheduleRefresh(playerId);
        }
        return current == null ? Optional.empty() : current.source();
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
        refreshing.remove(playerId);
    }

    private void scheduleRefresh(UUID playerId) {
        if (!refreshing.add(playerId)) return;
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            refreshing.remove(playerId);
            cache.remove(playerId);
            return;
        }

        player.getScheduler().run(
                plugin,
                ignored -> {
                    try {
                        cache.put(playerId, new CacheEntry(queryDetectors(playerId), System.nanoTime()));
                    } finally {
                        refreshing.remove(playerId);
                    }
                },
                () -> refreshing.remove(playerId)
        );
    }

    private Optional<String> queryDetectors(UUID playerId) {
        for (Detector detector : detectors) {
            try {
                Optional<Boolean> result = detector.isAfk(playerId);
                if (result.orElse(false)) {
                    return Optional.of(detector.name());
                }
            } catch (Throwable ignored) {
                // Optional integration failures must never affect claim protection.
            }
        }
        return Optional.empty();
    }

    private Detector createEssentialsDetector() {
        Plugin essentials = plugin.getServer().getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled()) {
            return null;
        }
        try {
            Method getUser = essentials.getClass().getMethod("getUser", UUID.class);
            return new Detector() {
                @Override public String name() { return "EssentialsX"; }

                @Override
                public Optional<Boolean> isAfk(UUID playerId) throws Exception {
                    Object user = getUser.invoke(essentials, playerId);
                    if (user == null) return Optional.empty();
                    Method isAfk = user.getClass().getMethod("isAfk");
                    return Optional.of(Boolean.TRUE.equals(isAfk.invoke(user)));
                }
            };
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("EssentialsX detected, but its AFK API could not be linked: " + exception.getMessage());
            return null;
        }
    }

    private Detector createCmiDetector() {
        Plugin cmiPlugin = plugin.getServer().getPluginManager().getPlugin("CMI");
        if (cmiPlugin == null || !cmiPlugin.isEnabled()) {
            return null;
        }
        try {
            Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI", false, cmiPlugin.getClass().getClassLoader());
            Method getInstance = cmiClass.getMethod("getInstance");
            Object cmi = getInstance.invoke(null);
            Method getPlayerManager = cmiClass.getMethod("getPlayerManager");
            Object playerManager = getPlayerManager.invoke(cmi);
            Method getUser = findMethod(playerManager.getClass(), "getUser", UUID.class);
            if (getUser == null) {
                plugin.getLogger().warning("CMI detected, but ClaimShift could not find getUser(UUID) for AFK integration.");
                return null;
            }
            return new Detector() {
                @Override public String name() { return "CMI"; }

                @Override
                public Optional<Boolean> isAfk(UUID playerId) throws Exception {
                    Object user = getUser.invoke(playerManager, playerId);
                    if (user == null) return Optional.empty();
                    Method isAfk = findMethod(user.getClass(), "isAfk");
                    if (isAfk == null) isAfk = findMethod(user.getClass(), "isAFK");
                    if (isAfk == null) return Optional.empty();
                    return Optional.of(Boolean.TRUE.equals(isAfk.invoke(user)));
                }
            };
        } catch (Throwable exception) {
            plugin.getLogger().warning("CMI detected, but its AFK API could not be linked: " + exception.getMessage());
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
