package dev.onelsey.claimshift.config;

public record PluginSettings(
        String configLocale,
        String messagesLocale,
        String provider,
        WorldGuardSettings worldGuard,
        String landsMode,
        boolean debug
) {
}
