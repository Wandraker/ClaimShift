package dev.onelsey.claimshift.config;

import java.util.List;

public final class LocaleService {
    public enum Scope {
        CONFIG,
        MESSAGES,
        BOTH
    }

    private final ConfigurationService configuration;

    public LocaleService(ConfigurationService configuration) {
        this.configuration = configuration;
    }

    public List<String> supportedLocales() {
        return LocaleCatalog.SUPPORTED_LOCALES;
    }

    public ReloadResult switchLocale(String locale, Scope scope) {
        return configuration.changeLocale(
                locale,
                scope == Scope.CONFIG || scope == Scope.BOTH,
                scope == Scope.MESSAGES || scope == Scope.BOTH
        );
    }
}
