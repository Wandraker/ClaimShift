package dev.onelsey.claimshift.integration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistent classification of WorldGuard regions observed by ClaimShift.
 *
 * <p>The registry exists so a server can safely install ClaimShift without
 * converting its pre-existing regions into dynamic claims. Regions already
 * present when ClaimShift first observes a loaded world are classified as
 * LEGACY_STATIC. Regions first discovered later while ClaimShift is actively
 * running can be classified AUTO_DYNAMIC. Both classifications survive restarts,
 * so an automatically managed player claim remains dynamic after a reboot.</p>
 *
 * <p>This file is internal plugin state, not administrator configuration.
 * Explicit WorldGuard flags and config selectors always take precedence.</p>
 */
final class WorldGuardRegionRegistry {
    private final File file;
    private final Map<WorldGuardStateStore.RegionKey, RegionLifecycleClassification> entries = new HashMap<>();
    private boolean dirty;
    private boolean sessionBootstrapped;

    WorldGuardRegionRegistry(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "region-registry.yml");
        load();
    }

    synchronized boolean beginSessionBootstrap() {
        if (sessionBootstrapped) {
            return false;
        }
        sessionBootstrapped = true;
        return true;
    }

    synchronized RegionLifecycleClassification classification(WorldGuardStateStore.RegionKey key) {
        return entries.get(key);
    }

    synchronized RegionLifecycleClassification classifyRuntime(
            WorldGuardStateStore.RegionKey key,
            boolean autoManageNewRegions
    ) {
        RegionLifecycleClassification existing = entries.get(key);
        if (existing != null) {
            return existing;
        }
        RegionLifecycleClassification created = RegionLifecycleClassifier.classifyFirstObservation(false, autoManageNewRegions);
        entries.put(key, created);
        dirty = true;
        flush();
        return created;
    }

    /**
     * Marks regions which already exist when a world first becomes observable in
     * the current server session. Unknown regions are deliberately static because
     * ClaimShift could not have witnessed their creation while that world was
     * unavailable.
     */
    synchronized void bootstrapWorld(String world, Set<WorldGuardStateStore.RegionKey> currentRegions) {
        pruneWorldInternal(world, currentRegions);
        for (WorldGuardStateStore.RegionKey key : currentRegions) {
            if (!entries.containsKey(key)) {
                entries.put(key, RegionLifecycleClassifier.classifyFirstObservation(true, false));
                dirty = true;
            }
        }
        flush();
    }

    /** Removes stale classifications for regions deleted while the world is loaded. */
    synchronized void pruneWorld(String world, Set<WorldGuardStateStore.RegionKey> currentRegions) {
        pruneWorldInternal(world, currentRegions);
        flush();
    }

    synchronized Map<WorldGuardStateStore.RegionKey, RegionLifecycleClassification> snapshot() {
        return Map.copyOf(entries);
    }

    private void pruneWorldInternal(String world, Set<WorldGuardStateStore.RegionKey> currentRegions) {
        Set<WorldGuardStateStore.RegionKey> stale = new HashSet<>();
        for (WorldGuardStateStore.RegionKey key : entries.keySet()) {
            if (key.world().equals(world) && !currentRegions.contains(key)) {
                stale.add(key);
            }
        }
        if (!stale.isEmpty()) {
            stale.forEach(entries::remove);
            dirty = true;
        }
    }

    private void flush() {
        if (!dirty) {
            return;
        }
        saveNow();
        dirty = false;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            int schema = yaml.getInt("schema-version", 1);
            if (schema > 1) {
                throw new IllegalStateException("region-registry.yml was created by a newer ClaimShift version (schema " + schema + ")");
            }
            ConfigurationSection section = yaml.getConfigurationSection("regions");
            if (section == null) {
                return;
            }
            for (String storageKey : section.getKeys(false)) {
                String base = "regions." + storageKey;
                String world = yaml.getString(base + ".world");
                String region = yaml.getString(base + ".region");
                String classification = yaml.getString(base + ".classification");
                if (world == null || region == null || classification == null) {
                    throw new IllegalStateException("Malformed WorldGuard region-registry entry: " + storageKey);
                }
                entries.put(
                        new WorldGuardStateStore.RegionKey(world, region),
                        RegionLifecycleClassification.valueOf(classification.toUpperCase())
                );
            }
            dirty = false;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not read ClaimShift WorldGuard region registry", exception);
        }
    }

    private void saveNow() {
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                throw new IOException("Could not create plugin data directory");
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("schema-version", 1);
            for (Map.Entry<WorldGuardStateStore.RegionKey, RegionLifecycleClassification> entry : entries.entrySet()) {
                String base = "regions." + entry.getKey().storageKey();
                yaml.set(base + ".world", entry.getKey().world());
                yaml.set(base + ".region", entry.getKey().region());
                yaml.set(base + ".classification", entry.getValue().name());
            }
            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not persist ClaimShift WorldGuard region registry", exception);
        }
    }
}
