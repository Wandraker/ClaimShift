package dev.onelsey.claimshift.metrics;

import dev.onelsey.claimshift.ClaimShiftPlugin;
import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.integration.ClaimProvider;
import dev.onelsey.claimshift.integration.ProviderManager;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

/**
 * Optional anonymous bStats integration. bStats itself also honours the global
 * server-owner opt-out in plugins/bStats/config.yml.
 */
public final class MetricsService {
    public static final int PLUGIN_ID = 33671;

    private final ClaimShiftPlugin plugin;
    private final ConfigurationService configuration;
    private final ProviderManager providers;
    private Metrics metrics;

    public MetricsService(
            ClaimShiftPlugin plugin,
            ConfigurationService configuration,
            ProviderManager providers
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.providers = providers;
    }

    public synchronized void reconcile() {
        boolean shouldRun = configuration.pluginSettings().metricsEnabled();
        if (shouldRun && metrics == null) {
            start();
        } else if (!shouldRun && metrics != null) {
            shutdown();
        }
    }

    public synchronized boolean enabled() {
        return metrics != null;
    }

    public synchronized void shutdown() {
        if (metrics == null) {
            return;
        }
        try {
            metrics.shutdown();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not stop bStats metrics: " + rootMessage(exception));
        } finally {
            metrics = null;
        }
    }

    private void start() {
        try {
            Metrics created = new Metrics(plugin, PLUGIN_ID);
            created.addCustomChart(new SimplePie(
                    "presence_policy",
                    () -> configuration.ruleSettings().presencePolicy().configValue()
            ));
            created.addCustomChart(new SimplePie(
                    "claim_provider",
                    this::activeProviderId
            ));
            created.addCustomChart(new SimplePie(
                    "worldguard_mode",
                    this::worldGuardMode
            ));
            created.addCustomChart(new SimplePie(
                    "messages_locale",
                    () -> configuration.pluginSettings().messagesLocale()
            ));
            metrics = created;
        } catch (LinkageError | RuntimeException exception) {
            metrics = null;
            plugin.getLogger().warning("bStats metrics could not start: " + rootMessage(exception));
        }
    }

    private String worldGuardMode() {
        ClaimProvider provider = providers.active();
        if (provider == null || !provider.available() || !provider.id().equals("worldguard")) {
            return "not-active";
        }
        return switch (configuration.pluginSettings().worldGuard().mode()) {
            case DYNAMIC_PASSTHROUGH -> "dynamic-passthrough";
            case OVERLAY -> "overlay";
        };
    }

    private String activeProviderId() {
        ClaimProvider provider = providers.active();
        if (provider == null || !provider.available()) {
            return "none";
        }
        return provider.id();
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
