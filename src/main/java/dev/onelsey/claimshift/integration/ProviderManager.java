package dev.onelsey.claimshift.integration;

import dev.onelsey.claimshift.ClaimShiftPlugin;
import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.protection.ClaimStateService;

import org.bukkit.World;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class ProviderManager {
    private static final List<String> AUTO_PRIORITY = List.of("worldguard");

    private final ClaimShiftPlugin plugin;
    private final ConfigurationService configuration;
    private final ClaimStateService states;
    private final AtomicBoolean reloadQueued = new AtomicBoolean();
    private volatile ClaimProvider active = new NoopClaimProvider();

    public ProviderManager(ClaimShiftPlugin plugin, ConfigurationService configuration, ClaimStateService states) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.states = states;
    }

    public void start() {
        reload();
    }

    public void reload() {
        if (!reloadQueued.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            reloadQueued.set(false);
            reloadNow();
        });
    }

    public void shutdown() {
        ClaimProvider previous = active;
        active = new NoopClaimProvider();
        previous.shutdown();
    }

    public ClaimProvider active() {
        return active;
    }

    public void requestReconcile() {
        active.requestReconcile();
    }

    public void requestReconcileAfter(Duration delay) {
        active.requestReconcileAfter(delay);
    }

    public void onWorldLoad(World world) {
        active.onWorldLoad(world);
    }

    public void onWorldUnload(World world) {
        active.onWorldUnload(world);
    }

    private void reloadNow() {
        ClaimProvider previous = active;
        try {
            previous.shutdown();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Provider shutdown failed: " + rootMessage(throwable));
        }

        String requested = configuration.pluginSettings().provider();
        ClaimProvider selected = requested.equals("auto") ? autoDetect() : createIfAvailable(requested);
        active = selected;
        plugin.getLogger().info("Claim provider: " + selected.displayName() + " " + selected.version()
                + " [" + selected.diagnostics().mode() + "]");

        if (!selected.available()) {
            plugin.getLogger().warning("No supported claim provider is active. Protection checks are idle.");
        } else {
            if (selected.id().equals("lands")) {
                plugin.getLogger().warning("Lands is running in overlay-only mode. ClaimShift does not rewrite Lands roles to create an OPEN raid window; select Lands explicitly only when your Lands permissions already provide the raid-access behavior expected by the selected presence policy.");
            }
            selected.requestReconcile();
        }
    }

    private ClaimProvider autoDetect() {
        for (String id : AUTO_PRIORITY) {
            ClaimProvider provider = createIfAvailable(id);
            if (provider.available()) {
                return provider;
            }
        }
        return new NoopClaimProvider();
    }

    private ClaimProvider createIfAvailable(String id) {
        String pluginName = switch (id) {
            case "lands" -> "Lands";
            case "worldguard" -> "WorldGuard";
            default -> null;
        };
        if (pluginName == null || !isPluginEnabled(pluginName)) {
            if (!configuration.pluginSettings().provider().equals("auto") && pluginName != null) {
                plugin.getLogger().warning(pluginName + " was selected as provider but is not installed/enabled.");
            }
            return new NoopClaimProvider();
        }

        try {
            if (id.equals("worldguard")) {
                return new WorldGuardClaimProvider(plugin, configuration, states);
            }
            if (id.equals("lands")) {
                return new LandsClaimProvider(plugin);
            }
            return new NoopClaimProvider();
        } catch (LinkageError | RuntimeException exception) {
            plugin.getLogger().warning("Could not enable " + pluginName + " integration: " + rootMessage(exception));
            if (configuration.pluginSettings().debug()) {
                plugin.getLogger().log(Level.WARNING, pluginName + " integration failure details", exception);
            }
            return new NoopClaimProvider();
        }
    }

    private boolean isPluginEnabled(String name) {
        return plugin.getServer().getPluginManager().isPluginEnabled(name);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
