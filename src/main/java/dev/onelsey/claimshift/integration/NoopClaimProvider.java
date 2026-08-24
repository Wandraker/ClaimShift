package dev.onelsey.claimshift.integration;

import dev.onelsey.claimshift.model.ClaimSnapshot;
import org.bukkit.Location;

import java.util.List;

public final class NoopClaimProvider implements ClaimProvider {
    @Override public String id() { return "none"; }
    @Override public String displayName() { return "None"; }
    @Override public String version() { return "-"; }
    @Override public boolean available() { return false; }
    @Override public List<ClaimSnapshot> findClaims(Location location) { return List.of(); }
    @Override public ProviderDiagnostics diagnostics() { return ProviderDiagnostics.simple("inactive"); }
}
