package dev.onelsey.claimshift.integration;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.util.logging.Logger;

/**
 * ClaimShift-owned WorldGuard flags. Registration must happen during plugin load,
 * before WorldGuard locks its flag registry.
 */
public final class WorldGuardFlags {
    private static volatile StateFlag dynamicOverride;

    private WorldGuardFlags() {
    }

    public static void register(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        StateFlag candidate = new StateFlag("claimshift-dynamic", false);
        try {
            registry.register(candidate);
            dynamicOverride = candidate;
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get("claimshift-dynamic");
            if (existing instanceof StateFlag stateFlag) {
                dynamicOverride = stateFlag;
                logger.info("Using existing WorldGuard flag 'claimshift-dynamic'.");
            } else {
                dynamicOverride = null;
                logger.warning("WorldGuard flag 'claimshift-dynamic' already exists with an incompatible type; per-region ClaimShift overrides are unavailable.");
            }
        }
    }

    public static StateFlag dynamicOverride() {
        return dynamicOverride;
    }
}
