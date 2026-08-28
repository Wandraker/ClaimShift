package dev.onelsey.claimshift.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionLifecycleClassifierTest {
    @Test
    void baselineObservationIsAlwaysLegacyStatic() {
        assertEquals(
                RegionLifecycleClassification.LEGACY_STATIC,
                RegionLifecycleClassifier.classifyFirstObservation(true, true)
        );
        assertEquals(
                RegionLifecycleClassification.LEGACY_STATIC,
                RegionLifecycleClassifier.classifyFirstObservation(true, false)
        );
    }

    @Test
    void runtimeObservationRespectsAutoManageSetting() {
        assertEquals(
                RegionLifecycleClassification.AUTO_DYNAMIC,
                RegionLifecycleClassifier.classifyFirstObservation(false, true)
        );
        assertEquals(
                RegionLifecycleClassification.LEGACY_STATIC,
                RegionLifecycleClassifier.classifyFirstObservation(false, false)
        );
    }
}
