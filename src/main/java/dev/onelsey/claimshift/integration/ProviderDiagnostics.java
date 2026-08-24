package dev.onelsey.claimshift.integration;

import java.util.LinkedHashMap;
import java.util.Map;

public record ProviderDiagnostics(
        String mode,
        int managedRegions,
        int openRegions,
        int graceRegions,
        int protectedRegions,
        Map<String, String> extra
) {
    public ProviderDiagnostics {
        extra = Map.copyOf(new LinkedHashMap<>(extra));
    }

    public static ProviderDiagnostics simple(String mode) {
        return new ProviderDiagnostics(mode, 0, 0, 0, 0, Map.of());
    }
}
