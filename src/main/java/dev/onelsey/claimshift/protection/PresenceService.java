package dev.onelsey.claimshift.protection;

import dev.onelsey.claimshift.ClaimShiftPlugin;
import dev.onelsey.claimshift.config.PresenceSettings;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks actual connection state and ClaimShift's effective presence state.
 * Effective presence may exclude externally-AFK players, idle players,
 * relog-qualified sessions and optional overlong sessions.
 */
public final class PresenceService {
    public record Evaluation(
            boolean online,
            boolean effective,
            Optional<Duration> absenceAge,
            Optional<Duration> effectiveAge,
            String reason
    ) {
    }

    public record ActivityResult(boolean accepted, boolean suspiciousPattern, boolean reactivated) {
    }

    private record ActivitySample(long nanos) {
    }

    private static final class PlayerState {
        volatile long joinedAtNanos;
        volatile long lastQuitNanos;
        volatile long lastMeaningfulActivityNanos;
        volatile long effectiveSinceNanos;
        volatile long externalAfkSinceNanos;
        volatile long relogBlockedUntilNanos;
        volatile String movementWorld;
        volatile double movementX;
        volatile double movementY;
        volatile double movementZ;
        volatile boolean movementAnchorSet;
        final Map<String, Deque<ActivitySample>> samples = new HashMap<>();
    }

    private final ClaimShiftPlugin plugin;
    private final ExternalAfkBridge externalAfk;
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public PresenceService(ClaimShiftPlugin plugin) {
        this.plugin = plugin;
        this.externalAfk = new ExternalAfkBridge(plugin);
    }

    public void initialize(Server server, PresenceSettings settings) {
        online.clear();
        long now = System.nanoTime();
        for (Player player : server.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            PlayerState state = states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
            state.joinedAtNanos = now;
            state.lastMeaningfulActivityNanos = now;
            state.effectiveSinceNanos = now;
            state.relogBlockedUntilNanos = 0L;
        }
        externalAfk.refresh(settings);
    }

    public void refreshIntegrations(PresenceSettings settings) {
        externalAfk.refresh(settings);
    }

    public void markOnline(UUID playerId, PresenceSettings settings) {
        long now = System.nanoTime();
        PlayerState state = states.computeIfAbsent(playerId, ignored -> new PlayerState());
        long previousQuit = state.lastQuitNanos;
        state.joinedAtNanos = now;
        state.lastMeaningfulActivityNanos = now;
        state.effectiveSinceNanos = now;
        state.externalAfkSinceNanos = 0L;
        state.relogBlockedUntilNanos = 0L;
        state.movementAnchorSet = false;
        externalAfk.invalidate(playerId);
        if (settings.antiRelogEnabled() && previousQuit > 0L) {
            long gap = Math.max(0L, now - previousQuit);
            if (gap <= safeNanos(settings.antiRelogWindow())) {
                state.relogBlockedUntilNanos = addSaturated(now, safeNanos(settings.antiRelogQualification()));
                state.effectiveSinceNanos = state.relogBlockedUntilNanos;
            }
        }
        online.add(playerId);
    }

    public void markOffline(UUID playerId) {
        long now = System.nanoTime();
        online.remove(playerId);
        PlayerState state = states.computeIfAbsent(playerId, ignored -> new PlayerState());
        state.lastQuitNanos = now;
        state.effectiveSinceNanos = 0L;
        state.externalAfkSinceNanos = 0L;
        state.relogBlockedUntilNanos = 0L;
        externalAfk.invalidate(playerId);
    }

    public boolean isOnline(UUID playerId) {
        return online.contains(playerId);
    }

    public Set<UUID> onlineOwners(Set<UUID> owners) {
        Set<UUID> result = new HashSet<>();
        for (UUID owner : owners) {
            if (isOnline(owner)) result.add(owner);
        }
        return Set.copyOf(result);
    }

    public Set<UUID> effectiveOwners(Set<UUID> owners, PresenceSettings settings) {
        Set<UUID> result = new HashSet<>();
        for (UUID owner : owners) {
            if (evaluate(owner, settings).effective()) result.add(owner);
        }
        return Set.copyOf(result);
    }

    public Set<String> externalAfkSources() {
        return Set.copyOf(externalAfk.sources());
    }

    public Evaluation evaluate(UUID playerId, PresenceSettings settings) {
        boolean connected = online.contains(playerId);
        PlayerState state = states.get(playerId);
        long now = System.nanoTime();

        if (!connected) {
            if (state == null || state.lastQuitNanos <= 0L) {
                return inactive(false, Optional.empty(), "offline-unknown");
            }
            long absentSince = state.lastQuitNanos;
            if (settings.smartEnabled() && state.lastMeaningfulActivityNanos > 0L) {
                long idleSince = addSaturated(state.lastMeaningfulActivityNanos, safeNanos(settings.idleTimeout()));
                if (idleSince < absentSince) absentSince = idleSince;
            }
            return inactive(false, Optional.of(durationSince(absentSince, now)), "offline");
        }

        if (!settings.smartEnabled()) {
            long since = state == null || state.joinedAtNanos <= 0L ? now : state.joinedAtNanos;
            return active(Optional.of(durationSince(since, now)), "online");
        }
        if (state == null) {
            return active(Optional.of(Duration.ZERO), "online");
        }

        Optional<String> afkSource = settings.externalAfkEnabled() ? externalAfk.afkSource(playerId) : Optional.empty();
        if (afkSource.isPresent()) {
            if (state.externalAfkSinceNanos <= 0L) state.externalAfkSinceNanos = now;
            return inactive(true,
                    Optional.of(durationSince(state.externalAfkSinceNanos, now)),
                    "external-afk:" + afkSource.get());
        }
        if (state.externalAfkSinceNanos > 0L) {
            state.externalAfkSinceNanos = 0L;
            state.effectiveSinceNanos = now;
        }

        if (settings.antiRelogEnabled() && state.relogBlockedUntilNanos > now) {
            long blockedSince = Math.max(state.joinedAtNanos,
                    state.relogBlockedUntilNanos - safeNanos(settings.antiRelogQualification()));
            return inactive(true, Optional.of(durationSince(blockedSince, now)), "relog-qualification");
        }
        if (state.relogBlockedUntilNanos > 0L && now >= state.relogBlockedUntilNanos
                && state.effectiveSinceNanos < state.relogBlockedUntilNanos) {
            state.effectiveSinceNanos = state.relogBlockedUntilNanos;
        }

        if (settings.maxContinuousPresenceEnabled()
                && settings.maxContinuousPresence().isPositive()
                && state.joinedAtNanos > 0L) {
            long maxAt = addSaturated(state.joinedAtNanos, safeNanos(settings.maxContinuousPresence()));
            if (now >= maxAt) {
                return inactive(true, Optional.of(durationSince(maxAt, now)), "max-continuous-presence");
            }
        }

        long lastActivity = state.lastMeaningfulActivityNanos > 0L
                ? state.lastMeaningfulActivityNanos
                : state.joinedAtNanos;
        long idleAt = addSaturated(lastActivity, safeNanos(settings.idleTimeout()));
        if (now >= idleAt) {
            return inactive(true, Optional.of(durationSince(idleAt, now)), "idle");
        }

        long effectiveSince = state.effectiveSinceNanos > 0L ? state.effectiveSinceNanos : state.joinedAtNanos;
        if (effectiveSince <= 0L || effectiveSince > now) effectiveSince = now;
        return active(Optional.of(durationSince(effectiveSince, now)), "active");
    }

    public ActivityResult recordMovement(
            UUID playerId,
            String world,
            double x,
            double y,
            double z,
            PresenceSettings settings
    ) {
        if (!online.contains(playerId)) return new ActivityResult(false, false, false);
        PlayerState state = states.computeIfAbsent(playerId, ignored -> new PlayerState());
        double required = settings.minimumMovementDistance();
        synchronized (state) {
            if (!state.movementAnchorSet || state.movementWorld == null || !state.movementWorld.equals(world)) {
                state.movementWorld = world;
                state.movementX = x;
                state.movementY = y;
                state.movementZ = z;
                state.movementAnchorSet = true;
                return recordActivity(playerId, ActivityType.MOVEMENT, "move", settings);
            }
            double dx = x - state.movementX;
            double dy = y - state.movementY;
            double dz = z - state.movementZ;
            if (dx * dx + dy * dy + dz * dz < required * required) {
                return new ActivityResult(false, false, false);
            }
            state.movementX = x;
            state.movementY = y;
            state.movementZ = z;
        }
        return recordActivity(playerId, ActivityType.MOVEMENT, "move", settings);
    }

    public ActivityResult recordActivity(UUID playerId, ActivityType type, String signature, PresenceSettings settings) {
        if (!online.contains(playerId)) return new ActivityResult(false, false, false);
        PlayerState state = states.computeIfAbsent(playerId, ignored -> new PlayerState());
        long now = System.nanoTime();
        String safeSignature = type.name() + ":" + (signature == null || signature.isBlank() ? "default" : signature);

        boolean suspicious = settings.smartEnabled()
                && settings.patternDetectionEnabled()
                && isPeriodicPattern(state, safeSignature, now, settings);
        if (suspicious) {
            return new ActivityResult(false, true, false);
        }
        long previous = state.lastMeaningfulActivityNanos;
        boolean reactivated = settings.smartEnabled()
                && previous > 0L
                && now - previous >= safeNanos(settings.idleTimeout());
        state.lastMeaningfulActivityNanos = now;
        if (reactivated) {
            state.effectiveSinceNanos = now;
        } else if (state.effectiveSinceNanos <= 0L) {
            state.effectiveSinceNanos = state.joinedAtNanos > 0L ? state.joinedAtNanos : now;
        }
        return new ActivityResult(true, false, reactivated);
    }

    public Optional<Duration> absenceAge(UUID playerId, PresenceSettings settings) {
        return evaluate(playerId, settings).absenceAge();
    }

    public Optional<Duration> effectiveAge(UUID playerId, PresenceSettings settings) {
        return evaluate(playerId, settings).effectiveAge();
    }

    private boolean isPeriodicPattern(PlayerState state, String signature, long now, PresenceSettings settings) {
        synchronized (state) {
            Deque<ActivitySample> samples = state.samples.computeIfAbsent(signature, ignored -> new ArrayDeque<>());
            samples.addLast(new ActivitySample(now));
            int keep = Math.max(settings.patternMinimumSamples() + 2, 8);
            while (samples.size() > keep) samples.removeFirst();
            if (samples.size() < settings.patternMinimumSamples()) return false;

            return ActivityPatternDetector.isPeriodic(
                    samples.stream().map(ActivitySample::nanos).toList(),
                    settings.patternMinimumSamples(),
                    settings.patternMinimumInterval(),
                    settings.patternIntervalTolerance()
            );
        }
    }

    private static Evaluation active(Optional<Duration> effectiveAge, String reason) {
        return new Evaluation(true, true, Optional.empty(), effectiveAge, reason);
    }

    private static Evaluation inactive(boolean online, Optional<Duration> absenceAge, String reason) {
        return new Evaluation(online, false, absenceAge, Optional.empty(), reason);
    }

    private static Duration durationSince(long since, long now) {
        return Duration.ofNanos(Math.max(0L, now - since));
    }

    private static long safeNanos(Duration duration) {
        try {
            return Math.max(0L, duration.toNanos());
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long addSaturated(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }
}
