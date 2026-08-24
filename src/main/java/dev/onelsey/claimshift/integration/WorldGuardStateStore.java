package dev.onelsey.claimshift.integration;

import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

final class WorldGuardStateStore {
    record RegionKey(String world, String region) {
        String storageKey() {
            String raw = world + "\u0000" + region;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    record OriginalState(StateFlag.State passthrough) {
        String serialized() {
            return passthrough == null ? "UNSET" : passthrough.name();
        }

        static OriginalState parse(String value) {
            if (value == null || value.equalsIgnoreCase("UNSET")) {
                return new OriginalState(null);
            }
            return new OriginalState(StateFlag.State.valueOf(value.toUpperCase()));
        }
    }

    private final File file;
    private final Map<RegionKey, OriginalState> entries = new HashMap<>();
    private boolean dirty;

    WorldGuardStateStore(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "runtime-worldguard.yml");
        load();
    }

    synchronized Map<RegionKey, OriginalState> snapshot() {
        return Map.copyOf(entries);
    }

    synchronized OriginalState get(RegionKey key) {
        return entries.get(key);
    }

    synchronized boolean contains(RegionKey key) {
        return entries.containsKey(key);
    }

    synchronized void put(RegionKey key, OriginalState state) {
        OriginalState previous = entries.put(key, state);
        if (!state.equals(previous)) {
            dirty = true;
        }
    }

    synchronized void remove(RegionKey key) {
        if (entries.remove(key) != null) {
            dirty = true;
        }
    }

    synchronized void flush() {
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
                throw new IllegalStateException("runtime-worldguard.yml was created by a newer ClaimShift version (schema " + schema + ")");
            }
            ConfigurationSection section = yaml.getConfigurationSection("regions");
            if (section == null) {
                return;
            }
            for (String key : section.getKeys(false)) {
                String base = "regions." + key;
                String world = yaml.getString(base + ".world");
                String region = yaml.getString(base + ".region");
                String original = yaml.getString(base + ".original", "UNSET");
                if (world == null || region == null) {
                    throw new IllegalStateException("Malformed WorldGuard recovery entry: " + key);
                }
                entries.put(new RegionKey(world, region), OriginalState.parse(original));
            }
            dirty = false;
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Could not read WorldGuard recovery state. Refusing to ignore it because regions may still have temporary passthrough overrides.",
                    exception
            );
        }
    }

    private void saveNow() {
        try {
            if (entries.isEmpty()) {
                Files.deleteIfExists(file.toPath());
                return;
            }
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                throw new IOException("Could not create plugin data directory");
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("schema-version", 1);
            for (Map.Entry<RegionKey, OriginalState> entry : entries.entrySet()) {
                String base = "regions." + entry.getKey().storageKey();
                yaml.set(base + ".world", entry.getKey().world());
                yaml.set(base + ".region", entry.getKey().region());
                yaml.set(base + ".original", entry.getValue().serialized());
            }
            File temp = new File(file.getParentFile(), file.getName() + ".tmp");
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailure) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not persist WorldGuard runtime recovery state", exception);
        }
    }
}
