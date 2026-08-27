package dev.onelsey.claimshift.config;

import java.util.List;
import java.util.Locale;

/**
 * Central catalogue for bundled ClaimShift locales.
 * Command tokens are deliberately not localized; only comments and messages are.
 */
public final class LocaleCatalog {
    public static final List<String> SUPPORTED_LOCALES = List.of(
            "en_US",
            "ru_RU",
            "de_DE",
            "es_ES",
            "fr_FR",
            "pl_PL",
            "pt_BR",
            "uk_UA",
            "zh_CN"
    );

    private LocaleCatalog() {
    }

    public static String canonicalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        for (String supported : SUPPORTED_LOCALES) {
            if (supported.toLowerCase(Locale.ROOT).equals(normalized)) {
                return supported;
            }
        }

        if (!normalized.contains("_")) {
            String prefix = normalized + "_";
            String match = null;
            for (String supported : SUPPORTED_LOCALES) {
                if (supported.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    if (match != null) {
                        return null;
                    }
                    match = supported;
                }
            }
            return match;
        }

        return null;
    }
}
