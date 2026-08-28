package dev.onelsey.claimshift.protection;

import dev.onelsey.claimshift.ClaimShiftPlugin;
import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.config.RaidSettings;
import dev.onelsey.claimshift.message.MessageService;
import dev.onelsey.claimshift.model.ClaimSnapshot;
import dev.onelsey.claimshift.model.ProtectionAction;
import dev.onelsey.claimshift.util.DurationFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional raid lock. A session can only start while the claim is already open;
 * once started it keeps that claim open until inactivity/max-duration expires.
 */
public final class RaidSessionService {
    public record Update(boolean active, boolean started, Duration nextExpiry) {
    }

    private static final class Session {
        final long startedNanos;
        volatile long lastActivityNanos;
        final Duration inactivityTimeout;
        final Duration maximumDuration;
        final String claimName;
        final Set<UUID> owners;

        Session(long now, RaidSettings settings, ClaimSnapshot claim) {
            this.startedNanos = now;
            this.lastActivityNanos = now;
            this.inactivityTimeout = settings.inactivityTimeout();
            this.maximumDuration = settings.maximumDuration();
            this.claimName = claim.name();
            this.owners = claim.owners();
        }
    }

    private final ClaimShiftPlugin plugin;
    private final ConfigurationService configuration;
    private final MessageService messages;
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public RaidSessionService(ClaimShiftPlugin plugin, ConfigurationService configuration, MessageService messages) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.messages = messages;
    }

    public Update recordOpenClaimActivity(ClaimSnapshot claim, ProtectionAction action) {
        RaidSettings settings = configuration.ruleSettings().raids();
        if (!enabledFor(claim, settings) || !settings.triggerActions().contains(action)) {
            return new Update(false, false, Duration.ZERO);
        }

        long now = System.nanoTime();
        AtomicBoolean started = new AtomicBoolean(false);
        Session session = sessions.compute(claim.key(), (key, current) -> {
            if (current == null || expired(current, now)) {
                if (current != null) notifyEnded(current);
                started.set(true);
                return new Session(now, settings, claim);
            }
            if (settings.extendOnActivity()) current.lastActivityNanos = now;
            return current;
        });
        if (started.get()) notifyStarted(session);
        return new Update(true, started.get(), remaining(session, now));
    }

    public boolean isActive(String claimKey) {
        Session session = sessions.get(claimKey);
        if (session == null) return false;
        long now = System.nanoTime();
        if (expired(session, now)) {
            if (sessions.remove(claimKey, session)) notifyEnded(session);
            return false;
        }
        return true;
    }

    /**
     * Checks an active session against the claim's current override as well as
     * the current global raid setting. This makes a config reload or a
     * claimshift-raids deny flag able to stop an existing lock safely.
     */
    public boolean isActiveFor(ClaimSnapshot claim) {
        if (!enabledFor(claim, configuration.ruleSettings().raids())) {
            end(claim.key(), true);
            return false;
        }
        return isActive(claim.key());
    }

    public Optional<Duration> remaining(String claimKey) {
        Session session = sessions.get(claimKey);
        if (session == null) return Optional.empty();
        long now = System.nanoTime();
        if (expired(session, now)) {
            if (sessions.remove(claimKey, session)) notifyEnded(session);
            return Optional.empty();
        }
        return Optional.of(remaining(session, now));
    }

    public int activeCount() {
        int count = 0;
        for (String key : sessions.keySet()) if (isActive(key)) count++;
        return count;
    }

    public void clear() {
        sessions.clear();
    }

    public void endAll() {
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (sessions.remove(entry.getKey(), entry.getValue())) {
                notifyEnded(entry.getValue());
            }
        }
    }

    private void end(String claimKey, boolean notify) {
        Session removed = sessions.remove(claimKey);
        if (notify && removed != null) notifyEnded(removed);
    }

    public boolean enabledFor(ClaimSnapshot claim, RaidSettings settings) {
        return claim.attribute("raid-sessions")
                .map(value -> value.equalsIgnoreCase("true") || value.equalsIgnoreCase("allow") || value.equalsIgnoreCase("yes"))
                .orElse(settings.enabled());
    }

    private boolean expired(Session session, long now) {
        return RaidSessionTiming.expired(
                session.startedNanos,
                session.lastActivityNanos,
                session.inactivityTimeout,
                session.maximumDuration,
                now
        );
    }

    private Duration remaining(Session session, long now) {
        return RaidSessionTiming.remaining(
                session.startedNanos,
                session.lastActivityNanos,
                session.inactivityTimeout,
                session.maximumDuration,
                now
        );
    }

    private void notifyStarted(Session session) {
        notifyOwners(session, "raid-started-owner", Map.of(
                "claim", Component.text(session.claimName),
                "remaining", Component.text(DurationFormatter.format(session.inactivityTimeout))
        ));
    }

    private void notifyEnded(Session session) {
        notifyOwners(session, "raid-ended-owner", Map.of("claim", Component.text(session.claimName)));
    }

    private void notifyOwners(Session session, String key, Map<String, Component> placeholders) {
        for (UUID owner : session.owners) {
            Player player = plugin.getServer().getPlayer(owner);
            if (player == null || !player.isOnline()) continue;
            player.getScheduler().run(plugin, ignored -> messages.send(player, key, placeholders), null);
        }
    }

}
