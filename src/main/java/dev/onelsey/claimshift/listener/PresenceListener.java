package dev.onelsey.claimshift.listener;

import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.integration.ProviderManager;
import dev.onelsey.claimshift.protection.PresenceService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        presence.markOnline(event.getPlayer().getUniqueId());
        providers.requestReconcile();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        presence.markOffline(event.getPlayer().getUniqueId());
        providers.requestReconcile();
        providers.requestReconcileAfter(configuration.ruleSettings().offlineDelay());
    }
}
