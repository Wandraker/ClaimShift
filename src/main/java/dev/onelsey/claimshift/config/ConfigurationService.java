package dev.onelsey.claimshift.config;

import dev.onelsey.claimshift.ClaimShiftPlugin;
import dev.onelsey.claimshift.message.MessageBundle;
import dev.onelsey.claimshift.message.MessageValidator;
import dev.onelsey.claimshift.model.ProtectionAction;
import dev.onelsey.claimshift.util.DurationParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConfigurationService {
    private final ClaimShiftPlugin plugin;
    private final File configFile;
    private final File rulesFile;
    private final File messagesFile;

    private volatile PluginSettings pluginSettings;
    private volatile RuleSettings ruleSettings;
    private volatile MessageBundle messageBundle;

    public ConfigurationService(ClaimShiftPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.rulesFile = new File(plugin.getDataFolder(), "rules.yml");
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
    }

    public void ensureFiles() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data directory");
        }
        saveIfMissing("config.yml", configFile);
        saveIfMissing("rules.yml", rulesFile);
        saveIfMissing("messages.yml", messagesFile);
    }

    public synchronized ReloadResult reload() {
        long started = System.nanoTime();
        try {
            YamlConfiguration config = loadFileYaml(configFile);
            YamlConfiguration rules = loadFileYaml(rulesFile);
            YamlConfiguration messages = loadFileYaml(messagesFile);
            boolean legacyMessagesFile = !messages.contains("config-version");

            migrateConfigSchema(config);
            migrateRulesSchema(rules);
            mergeMissing(config, loadResourceYaml("config.yml"));
            mergeMissing(rules, loadResourceYaml("rules.yml"));
            mergeMissing(messages, loadResourceYaml("messages.yml"));

            PluginSettings candidatePlugin = parsePluginSettings(config);
            RuleSettings candidateRules = parseRuleSettings(rules);

            validateMessagesSchema(messages);
            if (legacyMessagesFile) {
                migrateLegacyBundledMessages(messages);
            }
            YamlConfiguration localizedMessages = loadResourceYaml(
                    "locales/messages-defaults/" + candidatePlugin.messagesLocale() + ".yml"
            );
            MessageBundle candidateMessages = MessageBundle.load(messages, localizedMessages);
            validateMessages(candidateMessages, localizedMessages);
            MessageValidator.validate(candidateMessages);

            clearComments(config);
            clearComments(rules);
            clearComments(messages);
            applyComments(config, loadResourceYaml("locales/config-comments/" + candidatePlugin.configLocale() + ".yml"));
            applyComments(rules, loadResourceYaml("locales/rules-comments/" + candidatePlugin.configLocale() + ".yml"));
            applyComments(messages, loadResourceYaml("locales/messages-comments/" + candidatePlugin.configLocale() + ".yml"));

            commitYaml(Map.of(
                    configFile, config,
                    rulesFile, rules,
                    messagesFile, messages
            ));

            this.pluginSettings = candidatePlugin;
            this.ruleSettings = candidateRules;
            this.messageBundle = candidateMessages;
            return ReloadResult.success(elapsedMillis(started));
        } catch (Exception exception) {
            return ReloadResult.failure(elapsedMillis(started), rootMessage(exception));
        }
    }

    public synchronized ReloadResult setDryRun(boolean enabled) {
        long started = System.nanoTime();
        byte[] original;
        try {
            original = Files.readAllBytes(configFile.toPath());
            YamlConfiguration config = loadFileYaml(configFile);
            migrateConfigSchema(config);
            mergeMissing(config, loadResourceYaml("config.yml"));
            config.set("diagnostics.dry-run", enabled);
            atomicSave(configFile, config);
            ReloadResult result = reload();
            if (!result.success()) {
                Files.write(configFile.toPath(), original);
                reload();
                return ReloadResult.failure(elapsedMillis(started), result.error());
            }
            return ReloadResult.success(elapsedMillis(started));
        } catch (Exception exception) {
            return ReloadResult.failure(elapsedMillis(started), rootMessage(exception));
        }
    }

    public synchronized ReloadResult changeLocale(String locale, boolean configScope, boolean messagesScope) {
        long started = System.nanoTime();
        String canonical = canonicalLocale(locale);
        if (canonical == null) {
            return ReloadResult.failure(0L, "Unsupported locale: " + locale);
        }
        byte[] original;
        try {
            original = Files.readAllBytes(configFile.toPath());
            YamlConfiguration config = loadFileYaml(configFile);
            mergeMissing(config, loadResourceYaml("config.yml"));
            if (configScope) {
                config.set("language.config", canonical);
            }
            if (messagesScope) {
                config.set("language.messages", canonical);
            }
            atomicSave(configFile, config);
            ReloadResult result = reload();
            if (!result.success()) {
                Files.write(configFile.toPath(), original);
                reload();
                return ReloadResult.failure(elapsedMillis(started), result.error());
            }
            return ReloadResult.success(elapsedMillis(started));
        } catch (Exception exception) {
            return ReloadResult.failure(elapsedMillis(started), rootMessage(exception));
        }
    }

    private PluginSettings parsePluginSettings(YamlConfiguration config) {
        int schema = config.getInt("config-version", 3);
        if (schema > 3) {
            throw new IllegalArgumentException("config.yml was created by a newer ClaimShift version (schema " + schema + ")");
        }

        String configLocale = requireLocale(config.getString("language.config", "en_US"));
        String messagesLocale = requireLocale(config.getString("language.messages", "en_US"));
        String provider = config.getString("integration.provider", "auto").trim().toLowerCase(Locale.ROOT);
        if (!Set.of("auto", "worldguard", "lands").contains(provider)) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }

        String wgModeRaw = config.getString("integration.worldguard.mode", "dynamic-passthrough")
                .trim().toLowerCase(Locale.ROOT);
        WorldGuardSettings.Mode wgMode = switch (wgModeRaw) {
            case "dynamic-passthrough" -> WorldGuardSettings.Mode.DYNAMIC_PASSTHROUGH;
            case "overlay" -> WorldGuardSettings.Mode.OVERLAY;
            default -> throw new IllegalArgumentException("Unsupported WorldGuard mode: " + wgModeRaw);
        };

        Set<String> included = normalizeSelectors(config.getStringList("integration.worldguard.included-regions"));
        Set<String> excluded = normalizeSelectors(config.getStringList("integration.worldguard.excluded-regions"));
        Duration reconcile = DurationParser.parse(config.getString("integration.worldguard.reconcile-interval", "10s"));
        if (reconcile.isZero() || reconcile.isNegative()) {
            throw new IllegalArgumentException("WorldGuard reconcile interval must be greater than zero");
        }

        WorldGuardSettings worldGuard = new WorldGuardSettings(
                wgMode,
                config.getBoolean("integration.worldguard.manage-all-owned-regions", false),
                config.getBoolean("integration.worldguard.auto-manage-new-regions", true),
                config.getBoolean("integration.worldguard.manage-existing-passthrough-regions", false),
                included,
                excluded,
                reconcile
        );

        String landsMode = config.getString("integration.lands.mode", "overlay").trim().toLowerCase(Locale.ROOT);
        if (!landsMode.equals("overlay")) {
            throw new IllegalArgumentException("Unsupported Lands mode: " + landsMode);
        }

        DiagnosticsSettings diagnostics = new DiagnosticsSettings(
                config.getBoolean("diagnostics.dry-run", false),
                config.getBoolean("diagnostics.log-transitions", true),
                config.getBoolean("diagnostics.operator-notice", true)
        );

        return new PluginSettings(
                configLocale,
                messagesLocale,
                provider,
                worldGuard,
                landsMode,
                config.getBoolean("metrics.enabled", true),
                diagnostics,
                config.getBoolean("debug", false)
        );
    }

    private RuleSettings parseRuleSettings(YamlConfiguration rules) {
        int schema = rules.getInt("config-version", 4);
        if (schema > 4) {
            throw new IllegalArgumentException("rules.yml was created by a newer ClaimShift version (schema " + schema + ")");
        }

        PresencePolicy presencePolicy = PresencePolicy.parse(
                rules.getString("protection.presence-policy", "offline-open")
        );
        Duration activeDelay = parseDurationValue(
                rules, "protection.transition-delays.owner-active", "5m"
        );
        Duration inactiveDelay = parseDurationValue(
                rules, "protection.transition-delays.owner-inactive", "1h"
        );
        Duration notificationCooldown = DurationParser.parse(rules.getString("notifications.cooldown", "1500ms"));
        if (activeDelay.isNegative() || inactiveDelay.isNegative()) {
            throw new IllegalArgumentException("Presence transition delays cannot be negative");
        }
        if (notificationCooldown.isNegative()) {
            throw new IllegalArgumentException("Notification cooldown cannot be negative");
        }

        Duration idleTimeout = DurationParser.parse(rules.getString("presence.smart.idle-timeout", "20m"));
        Duration patternMinimumInterval = DurationParser.parse(rules.getString("presence.smart.patterns.minimum-interval", "30s"));
        Duration patternTolerance = DurationParser.parse(rules.getString("presence.smart.patterns.interval-tolerance", "3s"));
        Duration relogWindow = DurationParser.parse(rules.getString("presence.anti-relog.window", "5m"));
        Duration relogQualification = DurationParser.parse(rules.getString("presence.anti-relog.qualification-time", "2m"));
        Duration maxPresence = DurationParser.parse(rules.getString("presence.max-continuous-presence.duration", "8h"));

        if (idleTimeout.isZero() || idleTimeout.isNegative()) {
            throw new IllegalArgumentException("Smart presence idle timeout must be greater than zero");
        }
        if (patternMinimumInterval.isNegative() || patternTolerance.isNegative()) {
            throw new IllegalArgumentException("Pattern timing values cannot be negative");
        }
        if (relogWindow.isNegative() || relogQualification.isNegative()) {
            throw new IllegalArgumentException("Anti-relog timing values cannot be negative");
        }
        if (maxPresence.isNegative()) {
            throw new IllegalArgumentException("Maximum continuous presence cannot be negative");
        }

        double minimumMovementDistance = rules.getDouble("presence.smart.minimum-movement-distance", 3.0);
        if (minimumMovementDistance < 0.0 || minimumMovementDistance > 64.0) {
            throw new IllegalArgumentException("Smart presence minimum-movement-distance must be between 0 and 64 blocks");
        }

        int patternMinimumSamples = rules.getInt("presence.smart.patterns.minimum-samples", 5);
        if (patternMinimumSamples < 3 || patternMinimumSamples > 32) {
            throw new IllegalArgumentException("Pattern minimum-samples must be between 3 and 32");
        }

        PresenceSettings presence = new PresenceSettings(
                rules.getBoolean("presence.smart.enabled", true),
                idleTimeout,
                minimumMovementDistance,
                rules.getBoolean("presence.smart.patterns.enabled", true),
                patternMinimumSamples,
                patternMinimumInterval,
                patternTolerance,
                rules.getBoolean("presence.smart.external-afk.enabled", true),
                rules.getBoolean("presence.smart.external-afk.cmi", true),
                rules.getBoolean("presence.smart.external-afk.essentialsx", true),
                rules.getBoolean("presence.anti-relog.enabled", false),
                relogWindow,
                relogQualification,
                rules.getBoolean("presence.max-continuous-presence.enabled", false),
                maxPresence
        );

        Set<ProtectionAction> raidTriggers = EnumSet.noneOf(ProtectionAction.class);
        for (String key : rules.getStringList("raids.trigger-actions")) {
            ProtectionAction matched = null;
            for (ProtectionAction action : ProtectionAction.values()) {
                if (action.configKey().equalsIgnoreCase(key)) {
                    matched = action;
                    break;
                }
            }
            if (matched == null) {
                throw new IllegalArgumentException("Unknown raid trigger action: " + key);
            }
            raidTriggers.add(matched);
        }
        RaidSettings raids = new RaidSettings(
                rules.getBoolean("raids.enabled", false),
                DurationParser.parse(rules.getString("raids.inactivity-timeout", "10m")),
                DurationParser.parse(rules.getString("raids.maximum-duration", "30m")),
                rules.getBoolean("raids.extend-on-activity", true),
                raidTriggers
        );
        // Per-region claimshift-raids: allow can enable sessions even when the
        // global switch is false, so timing must always be valid.
        if (raids.inactivityTimeout().isZero()) {
            throw new IllegalArgumentException("Raid inactivity-timeout must be greater than zero");
        }
        if (!raids.maximumDuration().isZero()
                && raids.maximumDuration().compareTo(raids.inactivityTimeout()) < 0) {
            throw new IllegalArgumentException("Raid maximum-duration cannot be shorter than inactivity-timeout");
        }

        Map<ProtectionAction, Boolean> actions = new EnumMap<>(ProtectionAction.class);
        for (ProtectionAction action : ProtectionAction.values()) {
            actions.put(action, rules.getBoolean("protection.actions." + action.configKey(), true));
        }

        return new RuleSettings(
                rules.getBoolean("protection.enabled", true),
                presencePolicy,
                activeDelay,
                inactiveDelay,
                rules.getBoolean("protection.protect-unknown-offline-owners", true),
                rules.getBoolean("protection.trusted-players-bypass", true),
                presence,
                raids,
                notificationCooldown,
                actions
        );
    }

    private void migrateConfigSchema(YamlConfiguration config) {
        int schema = config.getInt("config-version", 1);
        if (schema > 3) {
            throw new IllegalArgumentException("config.yml was created by a newer ClaimShift version (schema " + schema + ")");
        }
        if (schema < 2) {
            // Upgrades must never silently enter dry-run. The bundled fresh-install
            // config has dry-run=true, while existing installations migrate to false.
            if (!config.contains("diagnostics.dry-run")) {
                config.set("diagnostics.dry-run", false);
            }
            config.set("config-version", 2);
            schema = 2;
        }
        if (schema < 3) {
            // Existing regions are classified by the persistent WorldGuard registry
            // when this version first starts. Future regions created while
            // ClaimShift is running can then be opted in automatically without
            // changing any pre-existing server regions.
            if (!config.contains("integration.worldguard.auto-manage-new-regions")) {
                config.set("integration.worldguard.auto-manage-new-regions", true);
            }
            config.set("config-version", 3);
        }
    }

    private void migrateRulesSchema(YamlConfiguration rules) {
        int schema = rules.getInt("config-version", 1);
        if (schema > 4) {
            throw new IllegalArgumentException("rules.yml was created by a newer ClaimShift version (schema " + schema + ")");
        }
        if (schema < 2) {
            if (!rules.contains("protection.presence-policy")) {
                rules.set("protection.presence-policy", "online-open");
            }
            if (!rules.contains("protection.offline-delay")) {
                Object legacyDelay = rules.get("protection.activation-delay");
                rules.set("protection.offline-delay", legacyDelay == null ? "10m" : legacyDelay);
            }
            rules.set("protection.activation-delay", null);
            rules.set("config-version", 2);
            schema = 2;
        }
        if (schema < 3) {
            // Smart Presence is a safe anti-abuse improvement and intentionally
            // becomes enabled for upgraded installations. More aggressive controls
            // (anti-relog, max-session and raid locks) stay disabled by default.
            rules.set("config-version", 3);
            schema = 3;
        }
        if (schema < 4) {
            // Preserve the old delay semantics for existing installations. The
            // old offline-delay was specifically the transition after the last
            // active owner became inactive. The new reverse transition did not
            // exist before 1.3, so upgrades keep it immediate instead of silently
            // changing live raid behaviour.
            Object oldInactiveDelay = rules.get("protection.offline-delay");
            if (!rules.contains("protection.transition-delays.owner-inactive")) {
                rules.set("protection.transition-delays.owner-inactive",
                        oldInactiveDelay == null ? "10m" : oldInactiveDelay);
            }
            if (!rules.contains("protection.transition-delays.owner-active")) {
                rules.set("protection.transition-delays.owner-active", "0s");
            }
            rules.set("protection.offline-delay", null);
            rules.set("config-version", 4);
        }
    }

    private void validateMessagesSchema(YamlConfiguration messages) {
        int schema = messages.getInt("config-version", 1);
        if (schema > 1) {
            throw new IllegalArgumentException("messages.yml was created by a newer ClaimShift version (schema " + schema + ")");
        }
        if (schema < 1) {
            messages.set("config-version", 1);
        }
    }

    private void validateMessages(MessageBundle bundle, YamlConfiguration defaults) {
        if (bundle.prefix() == null || bundle.prefix().isBlank()) {
            throw new IllegalArgumentException("messages.yml prefix cannot be empty");
        }
        for (Map.Entry<String, String> entry : bundle.theme().entrySet()) {
            if (!entry.getValue().matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("Invalid HEX color for theme." + entry.getKey() + ": " + entry.getValue());
            }
        }
        ConfigurationSection section = defaults.getConfigurationSection("messages");
        if (section == null) {
            throw new IllegalStateException("Bundled locale is missing messages section");
        }
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key) && bundle.message(key).isBlank()) {
                throw new IllegalArgumentException("Message cannot be empty: " + key);
            }
        }
    }

    private void migrateLegacyBundledMessages(YamlConfiguration messages) {
        ConfigurationSection custom = messages.getConfigurationSection("messages");
        if (custom == null || custom.getKeys(true).isEmpty()) {
            return;
        }

        // 0.x wrote every bundled translation into messages.yml. Since 1.0.0 that
        // file contains overrides only. Remove values that are byte-for-byte equal
        // to any bundled locale for the same key; genuinely customized values stay.
        List<YamlConfiguration> defaults = LocaleCatalog.SUPPORTED_LOCALES.stream()
                .map(locale -> loadResourceYaml("locales/messages-defaults/" + locale + ".yml"))
                .toList();
        int removed = 0;
        for (String key : new ArrayList<>(custom.getKeys(true))) {
            if (custom.isConfigurationSection(key)) {
                continue;
            }
            Object actual = messages.get("messages." + key);
            if (actual == null) {
                continue;
            }
            boolean stockValue = defaults.stream()
                    .map(locale -> locale.get("messages." + key))
                    .filter(Objects::nonNull)
                    .anyMatch(actual::equals);
            if (stockValue) {
                messages.set("messages." + key, null);
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Migrated " + removed + " legacy bundled message value(s); custom overrides were preserved.");
        }
    }

    private static void clearComments(YamlConfiguration target) {
        for (String key : target.getKeys(true)) {
            target.setComments(key, List.of());
            target.setInlineComments(key, List.of());
        }
    }

    private Duration parseDurationValue(YamlConfiguration yaml, String path, String fallback) {
        Object raw = yaml.get(path);
        if (raw == null) {
            return DurationParser.parse(fallback);
        }
        if (raw instanceof Number number) {
            if (number.doubleValue() == 0.0d) {
                return Duration.ZERO;
            }
            throw new IllegalArgumentException("Duration at '" + path + "' must include a unit (or be 0 to disable it)");
        }
        return DurationParser.parse(String.valueOf(raw));
    }

    private Set<String> normalizeSelectors(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private String requireLocale(String locale) {
        String canonical = canonicalLocale(locale);
        if (canonical == null) {
            throw new IllegalArgumentException("Unsupported locale: " + locale);
        }
        return canonical;
    }

    /**
     * Resolves common locale spellings without ever changing the canonical keys
     * stored in config.yml. Examples: ru, ru-ru, RU_ru -> ru_RU.
     */
    public String canonicalLocale(String input) {
        return LocaleCatalog.canonicalize(input);
    }

    public PluginSettings pluginSettings() {
        return Objects.requireNonNull(pluginSettings, "Configuration has not been loaded yet");
    }

    public RuleSettings ruleSettings() {
        return Objects.requireNonNull(ruleSettings, "Rules have not been loaded yet");
    }

    public MessageBundle messageBundle() {
        return Objects.requireNonNull(messageBundle, "Messages have not been loaded yet");
    }

    public File configFile() { return configFile; }
    public File rulesFile() { return rulesFile; }
    public File messagesFile() { return messagesFile; }

    public YamlConfiguration loadFileYaml(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    public YamlConfiguration loadResourceYaml(String path) {
        try (InputStream input = plugin.getResource(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled resource: " + path);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read bundled resource: " + path, exception);
        }
    }

    public void mergeMissing(YamlConfiguration target, YamlConfiguration defaults) {
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                if (!target.isConfigurationSection(key)) {
                    target.createSection(key);
                }
                continue;
            }
            if (!target.contains(key)) {
                target.set(key, defaults.get(key));
            }
        }
    }

    public static void applyComments(YamlConfiguration target, YamlConfiguration comments) {
        for (String key : comments.getKeys(false)) {
            Object value = comments.get(key);
            if (value instanceof List<?> list) {
                target.setComments(key, list.stream().map(String::valueOf).toList());
            } else if (comments.isConfigurationSection(key)) {
                ConfigurationSection section = comments.getConfigurationSection(key);
                if (section != null) {
                    applyCommentsRecursive(target, section, key);
                }
            }
        }
    }

    private static void applyCommentsRecursive(YamlConfiguration target, ConfigurationSection section, String prefix) {
        for (String child : section.getKeys(false)) {
            String path = prefix + "." + child;
            Object value = section.get(child);
            if (value instanceof List<?> list) {
                target.setComments(path, list.stream().map(String::valueOf).toList());
            } else if (value instanceof ConfigurationSection nested) {
                applyCommentsRecursive(target, nested, path);
            }
        }
    }

    private void commitYaml(Map<File, YamlConfiguration> files) throws Exception {
        Map<File, File> temps = new HashMap<>();
        Map<File, byte[]> originals = new HashMap<>();
        try {
            for (Map.Entry<File, YamlConfiguration> entry : files.entrySet()) {
                File target = entry.getKey();
                originals.put(target, Files.exists(target.toPath()) ? Files.readAllBytes(target.toPath()) : null);
                File temp = new File(target.getParentFile(), target.getName() + ".claimshift-tmp");
                entry.getValue().save(temp);
                temps.put(target, temp);
            }
            for (Map.Entry<File, File> entry : temps.entrySet()) {
                moveReplace(entry.getValue(), entry.getKey());
            }
        } catch (Exception failure) {
            for (Map.Entry<File, byte[]> entry : originals.entrySet()) {
                try {
                    if (entry.getValue() == null) {
                        Files.deleteIfExists(entry.getKey().toPath());
                    } else {
                        Files.write(entry.getKey().toPath(), entry.getValue());
                    }
                } catch (IOException ignored) {
                }
            }
            throw failure;
        } finally {
            for (File temp : temps.values()) {
                Files.deleteIfExists(temp.toPath());
            }
        }
    }

    private void atomicSave(File target, YamlConfiguration yaml) throws Exception {
        File temp = new File(target.getParentFile(), target.getName() + ".claimshift-tmp");
        yaml.save(temp);
        moveReplace(temp, target);
    }

    private void moveReplace(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void saveIfMissing(String resource, File file) {
        if (!file.exists()) {
            plugin.saveResource(resource, false);
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
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
