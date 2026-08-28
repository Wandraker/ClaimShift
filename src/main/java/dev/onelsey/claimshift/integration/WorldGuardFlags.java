package dev.onelsey.claimshift.integration;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.util.logging.Logger;

/** ClaimShift-owned WorldGuard flags. Registration happens during plugin load. */
public final class WorldGuardFlags {
    private static volatile StateFlag dynamicOverride;
    private static volatile StringFlag presencePolicy;
    private static volatile StringFlag activeDelay;
    private static volatile StringFlag inactiveDelay;
    private static volatile StringFlag legacyDelay;
    private static volatile StateFlag raidSessions;

    private WorldGuardFlags() {
    }

    public static void register(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        dynamicOverride = registerState(registry, logger, "claimshift-dynamic", false);
        presencePolicy = registerString(registry, logger, "claimshift-policy");
        activeDelay = registerString(registry, logger, "claimshift-active-delay");
        inactiveDelay = registerString(registry, logger, "claimshift-inactive-delay");
        // Compatibility with the pre-1.3 per-region override. Its meaning was
        // specifically the delay after the last active owner became inactive.
        legacyDelay = registerString(registry, logger, "claimshift-delay");
        raidSessions = registerState(registry, logger, "claimshift-raids", false);
    }

    private static StateFlag registerState(FlagRegistry registry, Logger logger, String name, boolean defaultValue) {
        StateFlag candidate = new StateFlag(name, defaultValue);
        try {
            registry.register(candidate);
            return candidate;
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag stateFlag) {
                logger.info("Using existing WorldGuard flag '" + name + "'.");
                return stateFlag;
            }
            logger.warning("WorldGuard flag '" + name + "' already exists with an incompatible type; that ClaimShift override is unavailable.");
            return null;
        }
    }

    private static StringFlag registerString(FlagRegistry registry, Logger logger, String name) {
        StringFlag candidate = new StringFlag(name);
        try {
            registry.register(candidate);
            return candidate;
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StringFlag stringFlag) {
                logger.info("Using existing WorldGuard flag '" + name + "'.");
                return stringFlag;
            }
            logger.warning("WorldGuard flag '" + name + "' already exists with an incompatible type; that ClaimShift override is unavailable.");
            return null;
        }
    }

    public static StateFlag dynamicOverride() { return dynamicOverride; }
    public static StringFlag presencePolicy() { return presencePolicy; }
    public static StringFlag activeDelay() { return activeDelay; }
    public static StringFlag inactiveDelay() { return inactiveDelay; }
    public static StringFlag legacyDelay() { return legacyDelay; }
    public static StateFlag raidSessions() { return raidSessions; }
}
