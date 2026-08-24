package dev.onelsey.claimshift.config;

import java.util.Locale;

/**
 * Defines which owner-presence state is raidable.
 */
public enum PresencePolicy {
    ONLINE_OPEN("online-open"),
    OFFLINE_OPEN("offline-open");

    private final String configValue;

    PresencePolicy(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static PresencePolicy parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "online-open" -> ONLINE_OPEN;
            case "offline-open" -> OFFLINE_OPEN;
            default -> throw new IllegalArgumentException(
                    "Unsupported presence policy: " + value + " (expected online-open or offline-open)"
            );
        };
    }
}
