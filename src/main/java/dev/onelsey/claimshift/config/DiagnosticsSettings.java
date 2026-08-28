package dev.onelsey.claimshift.config;

public record DiagnosticsSettings(
        boolean dryRun,
        boolean logTransitions,
        boolean operatorNotice
) {
}
