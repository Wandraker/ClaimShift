package dev.onelsey.claimshift.integration;

import dev.onelsey.claimshift.model.ClaimSnapshot;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface ClaimProvider {
    String id();

    String displayName();

    String version();

    boolean available();

    List<ClaimSnapshot> findClaims(Location location);

    default Optional<ClaimSnapshot> findClaim(Location location) {
        List<ClaimSnapshot> claims = findClaims(location);
        return claims.isEmpty() ? Optional.empty() : Optional.of(claims.getFirst());
    }

    /**
     * Returns claims for diagnostics/inspection, including claims intentionally
     * excluded from dynamic management when the provider can expose them safely.
     */
    default List<ClaimSnapshot> findClaimsForInspection(Location location) {
        return findClaims(location);
    }

    default boolean isDynamicallyManaged(ClaimSnapshot claim) {
        return false;
    }

    default ProviderDiagnostics diagnostics() {
        return ProviderDiagnostics.simple("overlay");
    }

    default void requestReconcile() {
    }

    default void requestReconcileAfter(Duration delay) {
    }

    default void onWorldLoad(World world) {
        requestReconcile();
    }

    default void onWorldUnload(World world) {
    }

    default void shutdown() {
    }
}
