package dev.onelsey.claimshift;

import dev.onelsey.claimshift.command.ClaimShiftCommand;
import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.config.LocaleService;
import dev.onelsey.claimshift.config.ReloadResult;
import dev.onelsey.claimshift.integration.ProviderManager;
import dev.onelsey.claimshift.integration.WorldGuardFlags;
import dev.onelsey.claimshift.listener.ActivityListener;
import dev.onelsey.claimshift.listener.DiagnosticsNoticeListener;
import dev.onelsey.claimshift.listener.PresenceListener;
import dev.onelsey.claimshift.listener.ProtectionListener;
import dev.onelsey.claimshift.listener.WorldLifecycleListener;
import dev.onelsey.claimshift.listener.WorldGuardMutationListener;
import dev.onelsey.claimshift.message.MessageService;
import dev.onelsey.claimshift.metrics.MetricsService;
import dev.onelsey.claimshift.protection.ClaimStateService;
import dev.onelsey.claimshift.protection.PresenceService;
import dev.onelsey.claimshift.protection.RaidSessionService;
import dev.onelsey.claimshift.protection.ProtectionService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class ClaimShiftPlugin extends JavaPlugin {
    private ConfigurationService configuration;
    private ProviderManager providers;
    private MetricsService metrics;

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        try {
            WorldGuardFlags.register(getLogger());
        } catch (LinkageError | RuntimeException exception) {
            getLogger().warning("Could not register ClaimShift WorldGuard flags: " + rootMessage(exception));
        }
    }

    @Override
    public void onEnable() {
        try {
            configuration = new ConfigurationService(this);
            configuration.ensureFiles();
            ReloadResult initialLoad = configuration.reload();
            if (!initialLoad.success()) {
                getLogger().severe("Configuration load failed: " + initialLoad.error());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            LocaleService locales = new LocaleService(configuration);
            MessageService messages = new MessageService(configuration);
            PresenceService presence = new PresenceService(this);
            presence.initialize(getServer(), configuration.ruleSettings().presence());
            RaidSessionService raids = new RaidSessionService(this, configuration, messages);
            ClaimStateService states = new ClaimStateService(configuration, presence, raids);
            providers = new ProviderManager(this, configuration, states);
            metrics = new MetricsService(this, configuration, providers);
            metrics.reconcile();
            ProtectionService protection = new ProtectionService(configuration, providers, states, raids);

            getServer().getPluginManager().registerEvents(new PresenceListener(presence, providers, configuration), this);
            getServer().getPluginManager().registerEvents(new ActivityListener(presence, providers, configuration), this);
            getServer().getPluginManager().registerEvents(new DiagnosticsNoticeListener(configuration, messages), this);
            getServer().getPluginManager().registerEvents(new ProtectionListener(configuration, protection, messages), this);
            getServer().getPluginManager().registerEvents(new WorldLifecycleListener(providers), this);
            getServer().getPluginManager().registerEvents(new WorldGuardMutationListener(providers), this);

            ClaimShiftCommand commandHandler = new ClaimShiftCommand(
                    this,
                    configuration,
                    locales,
                    providers,
                    protection,
                    presence,
                    raids,
                    metrics,
                    messages
            );
            PluginCommand command = getCommand("claimshift");
            if (command == null) {
                throw new IllegalStateException("claimshift command is missing from plugin.yml");
            }
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);

            providers.start();
            if (configuration.pluginSettings().diagnostics().dryRun()) {
                getLogger().warning("DRY RUN is enabled: ClaimShift will preview decisions without changing WorldGuard state or denying actions. Use /claimshift dryrun off when ready.");
            }
            getLogger().info("ClaimShift " + getPluginMeta().getVersion() + " enabled on " + platformName() + ".");
            getLogger().info("Minecraft " + getServer().getMinecraftVersion() + " / Java " + System.getProperty("java.version"));
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "ClaimShift could not start: " + rootMessage(exception), exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        if (providers != null) {
            try {
                providers.shutdown();
            } catch (Throwable throwable) {
                getLogger().warning("Provider shutdown failed: " + rootMessage(throwable));
            }
        }
        getServer().getGlobalRegionScheduler().cancelTasks(this);
        getServer().getAsyncScheduler().cancelTasks(this);
    }

    public String platformName() {
        String name = getServer().getName();
        String version = getServer().getVersion().toLowerCase();
        if (version.contains("folia") || name.equalsIgnoreCase("Folia")) return "Folia";
        if (version.contains("leaf") || name.equalsIgnoreCase("Leaf")) return "Leaf";
        if (version.contains("purpur") || name.equalsIgnoreCase("Purpur")) return "Purpur";
        return "Paper";
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
