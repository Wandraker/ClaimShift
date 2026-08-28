package dev.onelsey.claimshift.listener;

import dev.onelsey.claimshift.integration.ProviderManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * WorldGuard does not expose general region create/remove/change events. Trigger a
 * near-immediate reconciliation after common region mutation commands while the
 * periodic reconciler remains the authoritative fallback for API-created regions.
 */
public final class WorldGuardMutationListener implements Listener {
    private static final Set<String> MUTATIONS = Set.of(
            "claim", "define", "create", "def", "d",
            "redefine", "update", "move",
            "remove", "rem", "delete", "del",
            "addowner", "removeowner", "addmember", "removemember",
            "setparent", "setpriority", "flag", "load", "reload"
    );

    private final ProviderManager providers;

    public WorldGuardMutationListener(ProviderManager providers) {
        this.providers = providers;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        reconcileIfRegionMutation(event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        reconcileIfRegionMutation(event.getCommand());
    }

    private void reconcileIfRegionMutation(String raw) {
        String command = raw == null ? "" : raw.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return;
        }
        String root = parts[0].toLowerCase(Locale.ROOT);
        int namespace = root.indexOf(':');
        if (namespace >= 0) {
            root = root.substring(namespace + 1);
        }
        if (!root.equals("rg") && !root.equals("region")) {
            return;
        }
        String subcommand = parts[1].toLowerCase(Locale.ROOT);
        if (MUTATIONS.contains(subcommand)) {
            providers.requestReconcileAfter(Duration.ofSeconds(1));
        }
    }
}
