package dev.onelsey.claimshift.command;

import dev.onelsey.claimshift.ClaimShiftPlugin;
import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.config.LocaleService;
import dev.onelsey.claimshift.config.PresencePolicy;
import dev.onelsey.claimshift.config.ReloadResult;
import dev.onelsey.claimshift.integration.ClaimProvider;
import dev.onelsey.claimshift.integration.ProviderDiagnostics;
import dev.onelsey.claimshift.integration.ProviderManager;
import dev.onelsey.claimshift.message.MessageService;
import dev.onelsey.claimshift.metrics.MetricsService;
import dev.onelsey.claimshift.model.ClaimState;
import dev.onelsey.claimshift.model.ClaimStatus;
import dev.onelsey.claimshift.protection.PresenceService;
import dev.onelsey.claimshift.protection.ProtectionService;
import dev.onelsey.claimshift.protection.RaidSessionService;
import dev.onelsey.claimshift.util.DurationFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClaimShiftCommand implements CommandExecutor, TabCompleter {
    private final ClaimShiftPlugin plugin;
    private final ConfigurationService configuration;
    private final LocaleService locales;
    private final ProviderManager providers;
    private final ProtectionService protection;
    private final PresenceService presence;
    private final RaidSessionService raids;
    private final MetricsService metrics;
    private final MessageService messages;

    public ClaimShiftCommand(
            ClaimShiftPlugin plugin,
            ConfigurationService configuration,
            LocaleService locales,
            ProviderManager providers,
            ProtectionService protection,
            PresenceService presence,
            RaidSessionService raids,
            MetricsService metrics,
            MessageService messages
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.locales = locales;
        this.providers = providers;
        this.protection = protection;
        this.presence = presence;
        this.raids = raids;
        this.metrics = metrics;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "sync", "reconcile" -> sync(sender);
            case "info", "status", "version" -> info(sender);
            case "inspect", "check" -> inspect(sender);
            case "language", "locale" -> language(sender, args);
            case "dryrun", "dry-run" -> dryRun(sender, args);
            default -> {
                messages.send(sender, "invalid-command", Map.of("command", Component.text(CommandSyntax.HELP)));
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!hasPermission(sender, "claimshift.reload")) {
            return true;
        }
        ReloadResult result = configuration.reload();
        if (!result.success()) {
            messages.send(sender, "reload-failed", Map.of("reason", Component.text(result.error())));
            return true;
        }
        presence.refreshIntegrations(configuration.ruleSettings().presence());
        if (configuration.pluginSettings().diagnostics().dryRun()) raids.endAll();
        providers.reload();
        metrics.reconcile();
        messages.send(sender, "reload-success", Map.of(
                "duration", Component.text(String.valueOf(result.durationMillis()))
        ));
        return true;
    }

    private boolean sync(CommandSender sender) {
        if (!hasPermission(sender, "claimshift.sync")) {
            return true;
        }
        ClaimProvider provider = providers.active();
        if (!provider.available()) {
            messages.send(sender, "provider-missing");
            return true;
        }
        providers.requestReconcile();
        messages.send(sender, "sync-success", Map.of("provider", Component.text(provider.displayName())));
        return true;
    }

    private boolean info(CommandSender sender) {
        if (!hasPermission(sender, "claimshift.info")) {
            return true;
        }
        ClaimProvider provider = providers.active();
        ProviderDiagnostics diagnostics = provider.diagnostics();

        messages.send(sender, "info-header", Map.of(
                "version", Component.text(plugin.getPluginMeta().getVersion())
        ));
        sendInfoLine(sender, "info-label-server", plugin.getServer().getName() + " " + plugin.getServer().getMinecraftVersion());
        sendInfoLine(sender, "info-label-runtime", plugin.platformName());
        sendInfoLine(sender, "info-label-java", System.getProperty("java.version"));
        sendInfoLine(sender, "info-label-provider", provider.available()
                ? provider.displayName() + " " + provider.version()
                : messages.plain("value-none"));
        sendInfoLine(sender, "info-label-provider-mode", localizedProviderMode(diagnostics.mode()));
        sendInfoLine(sender, "info-label-config-locale", configuration.pluginSettings().configLocale());
        sendInfoLine(sender, "info-label-messages-locale", configuration.pluginSettings().messagesLocale());
        sendInfoLine(sender, "info-label-metrics", messages.plain(metrics.enabled() ? "value-enabled" : "value-disabled"));
        sendInfoLine(sender, "info-label-presence-policy", localizedPresencePolicy(configuration.ruleSettings().presencePolicy()));
        sendInfoLine(sender, "info-label-active-delay", DurationFormatter.format(configuration.ruleSettings().activeDelay()));
        sendInfoLine(sender, "info-label-inactive-delay", DurationFormatter.format(configuration.ruleSettings().inactiveDelay()));
        sendInfoLine(sender, "info-label-smart-presence", localizedBoolean(configuration.ruleSettings().presence().smartEnabled()));
        sendInfoLine(sender, "info-label-pattern-detection", localizedBoolean(configuration.ruleSettings().presence().patternDetectionEnabled()));
        sendInfoLine(sender, "info-label-external-afk", presence.externalAfkSources().isEmpty()
                ? messages.plain("value-none")
                : String.join(", ", presence.externalAfkSources()));
        sendInfoLine(sender, "info-label-dry-run", localizedBoolean(configuration.pluginSettings().diagnostics().dryRun()));
        sendInfoLine(sender, "info-label-raids", localizedBoolean(configuration.ruleSettings().raids().enabled()));
        if (configuration.ruleSettings().raids().enabled()) {
            sendInfoLine(sender, "info-label-active-raids", String.valueOf(raids.activeCount()));
        }

        if (provider.available() && provider.id().equals("worldguard")) {
            sendInfoLine(sender, "info-label-managed-regions", String.valueOf(diagnostics.managedRegions()));
            sendInfoLine(sender, "info-label-open-regions", String.valueOf(diagnostics.openRegions()));
            sendInfoLine(sender, "info-label-grace-regions", String.valueOf(diagnostics.graceRegions()));
            sendInfoLine(sender, "info-label-protected-regions", String.valueOf(diagnostics.protectedRegions()));
            String skipped = diagnostics.extra().get("skipped-existing-passthrough");
            if (skipped != null) {
                sendInfoLine(sender, "info-label-skipped-passthrough", skipped);
            }
        }
        return true;
    }

    private boolean inspect(CommandSender sender) {
        if (!hasPermission(sender, "claimshift.inspect")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }

        var optional = protection.inspect(player.getLocation());
        if (optional.isEmpty()) {
            messages.send(sender, "inspect-no-claim");
            return true;
        }

        ClaimStatus status = optional.get();
        ClaimProvider provider = providers.active();
        boolean managed = provider.isDynamicallyManaged(status.claim());
        messages.send(sender, "inspect-header");
        sendInfoLine(sender, "inspect-label-name", status.claim().name());
        sendInfoLine(sender, "inspect-label-id", status.claim().id());
        sendInfoLine(sender, "inspect-label-provider", provider.displayName());
        sendInfoLine(sender, "inspect-label-state", managed ? localizedState(status.state()) : messages.plain("state-static"));
        sendInfoLine(sender, "inspect-label-owners", String.valueOf(status.claim().owners().size()));
        sendInfoLine(sender, "inspect-label-online-owners", String.valueOf(status.onlineOwners().size()));
        sendInfoLine(sender, "inspect-label-effective-owners", String.valueOf(status.effectiveOwners().size()));
        sendInfoLine(sender, "inspect-label-managed", localizedBoolean(managed));
        status.claim().attribute("management-source").ifPresent(source ->
                sendInfoLine(sender, "inspect-label-management-source", localizedManagementSource(source)));
        if (managed) {
            sendInfoLine(sender, "inspect-label-policy", localizedPresencePolicy(protection.effectivePolicy(status.claim())));
            sendInfoLine(sender, "inspect-label-active-delay", DurationFormatter.format(protection.effectiveActiveDelay(status.claim())));
            sendInfoLine(sender, "inspect-label-inactive-delay", DurationFormatter.format(protection.effectiveInactiveDelay(status.claim())));
            sendInfoLine(sender, "inspect-label-raids-enabled", localizedBoolean(protection.raidSessionsEnabled(status.claim())));
        }
        sendInfoLine(sender, "inspect-label-raid-active", localizedBoolean(status.raidActive()));
        if (status.raidActive()) {
            sendInfoLine(sender, "inspect-label-raid-remaining", DurationFormatter.format(status.raidRemaining()));
        }
        if (managed && status.state() == ClaimState.GRACE) {
            sendInfoLine(sender, "inspect-label-remaining", DurationFormatter.format(status.remaining()));
        }
        return true;
    }

    private boolean dryRun(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "claimshift.dryrun")) return true;
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            messages.send(sender, "dry-run-status", Map.of(
                    "state", messages.render(configuration.pluginSettings().diagnostics().dryRun() ? "value-enabled" : "value-disabled", Map.of()),
                    "command", Component.text(CommandSyntax.DRY_RUN_OFF),
                    "inspect-command", Component.text(CommandSyntax.INSPECT)
            ));
            return true;
        }

        boolean enabled;
        if (args[1].equalsIgnoreCase("on")) enabled = true;
        else if (args[1].equalsIgnoreCase("off")) enabled = false;
        else {
            messages.send(sender, "dry-run-usage", Map.of("command", Component.text(CommandSyntax.DRY_RUN)));
            return true;
        }

        ReloadResult result = configuration.setDryRun(enabled);
        if (!result.success()) {
            messages.send(sender, "dry-run-failed", Map.of("reason", Component.text(result.error())));
            return true;
        }
        if (enabled) raids.endAll();
        providers.reload();
        messages.send(sender, enabled ? "dry-run-enabled" : "dry-run-disabled", Map.of(
                "command", Component.text(CommandSyntax.DRY_RUN_OFF)
        ));
        return true;
    }

    private boolean language(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "claimshift.language")) {
            return true;
        }
        if (args.length < 2) {
            messages.send(sender, "language-failed", Map.of(
                    "reason", messages.render("language-usage", commandSyntaxPlaceholders())
            ));
            return true;
        }

        String locale = configuration.canonicalLocale(args[1]);
        if (locale == null) {
            messages.send(sender, "language-failed", Map.of(
                    "reason", messages.render("language-locale-invalid", Map.of(
                            "locales", Component.text(String.join(", ", locales.supportedLocales()))
                    ))
            ));
            return true;
        }
        LocaleService.Scope scope = LocaleService.Scope.BOTH;
        if (args.length >= 3) {
            try {
                scope = LocaleService.Scope.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                messages.send(sender, "language-failed", Map.of(
                        "reason", messages.render("language-scope-invalid", commandSyntaxPlaceholders())
                ));
                return true;
            }
        }

        ReloadResult result = locales.switchLocale(locale, scope);
        if (result.success()) {
            messages.send(sender, "language-success", Map.of(
                    "scope", Component.text(messages.plain(switch (scope) {
                        case CONFIG -> "scope-config";
                        case MESSAGES -> "scope-messages";
                        case BOTH -> "scope-both";
                    })),
                    "locale", Component.text(locale)
            ));
        } else {
            messages.send(sender, "language-failed", Map.of(
                    "reason", Component.text(result.error())
            ));
        }
        return true;
    }


    private void sendHelp(CommandSender sender) {
        messages.send(sender, "help-header");
        if (sender.hasPermission("claimshift.info")) {
            sendHelpLine(sender, CommandSyntax.INFO, "help-info-description");
        }
        if (sender.hasPermission("claimshift.inspect")) {
            sendHelpLine(sender, CommandSyntax.INSPECT, "help-inspect-description");
        }
        if (sender.hasPermission("claimshift.sync")) {
            sendHelpLine(sender, CommandSyntax.SYNC, "help-sync-description");
        }
        if (sender.hasPermission("claimshift.reload")) {
            sendHelpLine(sender, CommandSyntax.RELOAD, "help-reload-description");
        }
        if (sender.hasPermission("claimshift.language")) {
            sendHelpLine(sender, CommandSyntax.LANGUAGE, "help-language-description");
        }
        if (sender.hasPermission("claimshift.dryrun")) {
            sendHelpLine(sender, CommandSyntax.DRY_RUN, "help-dry-run-description");
        }
    }

    private String localizedState(ClaimState state) {
        return messages.plain(switch (state) {
            case OPEN -> "state-open";
            case GRACE -> "state-grace";
            case PROTECTED -> "state-protected";
        });
    }

    private String localizedPresencePolicy(PresencePolicy policy) {
        return messages.plain(policy == PresencePolicy.OFFLINE_OPEN
                ? "presence-policy-offline-open"
                : "presence-policy-online-open");
    }

    private String localizedBoolean(boolean value) {
        return messages.plain(value ? "value-yes" : "value-no");
    }

    private String localizedManagementSource(String source) {
        String key = switch (source) {
            case "manual-allow" -> "management-source-manual-allow";
            case "manual-deny" -> "management-source-manual-deny";
            case "excluded" -> "management-source-excluded";
            case "included" -> "management-source-included";
            case "manage-all" -> "management-source-manage-all";
            case "auto-new" -> "management-source-auto-new";
            case "legacy-static" -> "management-source-legacy-static";
            case "existing-passthrough" -> "management-source-existing-passthrough";
            case "ownerless" -> "management-source-ownerless";
            default -> "management-source-static";
        };
        return messages.plain(key);
    }

    private String localizedProviderMode(String mode) {
        return messages.plain(switch (mode) {
            case "dynamic-passthrough" -> "provider-mode-dynamic-passthrough";
            case "overlay" -> "provider-mode-overlay";
            case "disabled" -> "provider-mode-disabled";
            case "dry-run" -> "provider-mode-dry-run";
            case "starting" -> "provider-mode-starting";
            case "inactive" -> "provider-mode-inactive";
            default -> "provider-mode-unknown";
        });
    }

    private Map<String, Component> commandSyntaxPlaceholders() {
        return Map.of(
                "command", Component.text(CommandSyntax.LANGUAGE),
                "locales", Component.text(String.join(", ", locales.supportedLocales())),
                "scopes", Component.text(String.join(", ", CommandSyntax.SCOPES))
        );
    }

    private void sendHelpLine(CommandSender sender, String command, String descriptionKey) {
        messages.send(sender, "help-line", Map.of(
                "command", Component.text(command),
                "description", messages.render(descriptionKey, Map.of())
        ));
    }

    private void sendInfoLine(CommandSender sender, String labelKey, String value) {
        messages.send(sender, "info-line", Map.of(
                "key", messages.render(labelKey, Map.of()),
                "value", Component.text(value)
        ));
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages.send(sender, "no-permission");
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("help"));
            if (sender.hasPermission("claimshift.info")) values.add("info");
            if (sender.hasPermission("claimshift.inspect")) values.add("inspect");
            if (sender.hasPermission("claimshift.sync")) values.add("sync");
            if (sender.hasPermission("claimshift.reload")) values.add("reload");
            if (sender.hasPermission("claimshift.language")) values.add("language");
            if (sender.hasPermission("claimshift.dryrun")) values.add("dryrun");
            return filter(values, args[0]);
        }
        if (args.length == 2 && isLanguageCommand(args[0])) {
            return filter(locales.supportedLocales(), args[1]);
        }
        if (args.length == 3 && isLanguageCommand(args[0])) {
            return filter(CommandSyntax.SCOPES, args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("dryrun") || args[0].equalsIgnoreCase("dry-run"))) {
            return filter(List.of("on", "off", "status"), args[1]);
        }
        return List.of();
    }

    private boolean isLanguageCommand(String token) {
        return token.equalsIgnoreCase("language") || token.equalsIgnoreCase("locale");
    }

    private List<String> filter(List<String> values, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
