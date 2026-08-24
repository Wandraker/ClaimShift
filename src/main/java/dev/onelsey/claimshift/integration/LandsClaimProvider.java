package dev.onelsey.claimshift.integration;

import dev.onelsey.claimshift.ClaimShiftPlugin;
import dev.onelsey.claimshift.model.ClaimSnapshot;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Area;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LandsClaimProvider implements ClaimProvider {
    private final ClaimShiftPlugin plugin;
    private final LandsIntegration lands;
    private final String version;

    public LandsClaimProvider(ClaimShiftPlugin plugin) {
        this.plugin = plugin;
        Plugin landsPlugin = plugin.getServer().getPluginManager().getPlugin("Lands");
        if (landsPlugin == null || !landsPlugin.isEnabled()) {
            throw new IllegalStateException("Lands is not enabled");
        }
        this.version = landsPlugin.getPluginMeta().getVersion();
        this.lands = LandsIntegration.of(plugin);
    }

    @Override public String id() { return "lands"; }
    @Override public String displayName() { return "Lands"; }
    @Override public String version() { return version; }

    @Override
    public boolean available() {
        Plugin landsPlugin = plugin.getServer().getPluginManager().getPlugin("Lands");
        return landsPlugin != null && landsPlugin.isEnabled();
    }

    @Override
    public List<ClaimSnapshot> findClaims(Location location) {
        Area area = lands.getArea(location);
        if (area == null) {
            return List.of();
        }

        UUID owner = area.getOwnerUID();
        if (owner == null) {
            return List.of();
        }
        Set<UUID> trusted = new HashSet<>(area.getTrustedPlayers());
        trusted.add(owner);
        String landName = area.getLand().getName();
        String id = area.getLand().getULID().toString() + ":" + area.getULID();
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();

        return List.of(new ClaimSnapshot(id(), id, landName, world, Set.of(owner), trusted));
    }

    @Override
    public ProviderDiagnostics diagnostics() {
        return ProviderDiagnostics.simple("overlay");
    }
}
