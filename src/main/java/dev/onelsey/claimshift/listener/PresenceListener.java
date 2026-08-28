package dev.onelsey.claimshift.listener;

import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.config.PresenceSettings;
import dev.onelsey.claimshift.integration.ProviderManager;
import dev.onelsey.claimshift.protection.PresenceService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;

public final class PresenceListener implements Listener {
    private final PresenceService presence;
    private final ProviderManager providers;
    private final ConfigurationService configuration;

    public PresenceListener(PresenceService presence, ProviderManager providers, ConfigurationService configuration) {
        this.presence = presence;
        this.providers = providers;
        this.configuration = configuration;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PresenceSettings settings = configuration.ruleSettings().presence();
        presence.markOnline(event.getPlayer().getUniqueId(), settings);
        providers.requestReconcile();
        Duration activeDelay = configuration.ruleSettings().activeDelay();
        if (activeDelay.isPositive()) {
            providers.requestReconcileAfter(activeDelay);
        }
        if (settings.antiRelogEnabled() && settings.antiRelogQualification().isPositive()) {
            providers.requestReconcileAfter(settings.antiRelogQualification());
        }
        if (settings.maxContinuousPresenceEnabled() && settings.maxContinuousPresence().isPositive()) {
            providers.requestReconcileAfter(settings.maxContinuousPresence());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        presence.markOffline(event.getPlayer().getUniqueId());
        providers.requestReconcile();
        Duration delay = configuration.ruleSettings().inactiveDelay();
        if (delay.isPositive()) {
            providers.requestReconcileAfter(delay);
        }
    }
}
