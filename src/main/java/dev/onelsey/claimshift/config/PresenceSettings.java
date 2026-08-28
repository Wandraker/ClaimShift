package dev.onelsey.claimshift.config;

import java.time.Duration;

public record PresenceSettings(
        boolean smartEnabled,
        Duration idleTimeout,
        double minimumMovementDistance,
        boolean patternDetectionEnabled,
        int patternMinimumSamples,
        Duration patternMinimumInterval,
        Duration patternIntervalTolerance,
        boolean externalAfkEnabled,
        boolean useCmiAfk,
        boolean useEssentialsAfk,
        boolean antiRelogEnabled,
        Duration antiRelogWindow,
        Duration antiRelogQualification,
        boolean maxContinuousPresenceEnabled,
        Duration maxContinuousPresence
) {
    public PresenceSettings {
        idleTimeout = nonNegative(idleTimeout);
        patternMinimumInterval = nonNegative(patternMinimumInterval);
        patternIntervalTolerance = nonNegative(patternIntervalTolerance);
        antiRelogWindow = nonNegative(antiRelogWindow);
        antiRelogQualification = nonNegative(antiRelogQualification);
        maxContinuousPresence = nonNegative(maxContinuousPresence);
        minimumMovementDistance = Math.max(0.0, minimumMovementDistance);
        patternMinimumSamples = Math.max(3, patternMinimumSamples);
    }

    private static Duration nonNegative(Duration value) {
        return value == null || value.isNegative() ? Duration.ZERO : value;
    }
}
