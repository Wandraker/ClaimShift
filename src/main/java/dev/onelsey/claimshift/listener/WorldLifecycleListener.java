package dev.onelsey.claimshift.listener;

import dev.onelsey.claimshift.integration.ProviderManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public final class WorldLifecycleListener implements Listener {
    private final ProviderManager providers;

    public WorldLifecycleListener(ProviderManager providers) {
        this.providers = providers;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        providers.onWorldLoad(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        providers.onWorldUnload(event.getWorld());
    }
}
