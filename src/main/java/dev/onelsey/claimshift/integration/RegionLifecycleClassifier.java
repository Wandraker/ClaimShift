package dev.onelsey.claimshift.integration;

/**
 * Pure classification helper for deciding how a region should be treated when
 * ClaimShift observes it for the first time.
 *
 * <p>This class deliberately has no Bukkit or WorldGuard dependencies so the
 * lifecycle policy can be regression-tested without a server API on the test
 * runtime classpath.</p>
 */
final class RegionLifecycleClassifier {
    private RegionLifecycleClassifier() {
    }

    static RegionLifecycleClassification classifyFirstObservation(
            boolean baselineObservation,
            boolean autoManageNewRegions
    ) {
        if (baselineObservation) {
            return RegionLifecycleClassification.LEGACY_STATIC;
        }
        return autoManageNewRegions
                ? RegionLifecycleClassification.AUTO_DYNAMIC
                : RegionLifecycleClassification.LEGACY_STATIC;
    }
}
