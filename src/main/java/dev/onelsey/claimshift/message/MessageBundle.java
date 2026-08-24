package dev.onelsey.claimshift.message;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public record MessageBundle(Map<String, String> theme, String prefix, Map<String, String> messages) {
    public MessageBundle {
        theme = Map.copyOf(theme);
        messages = Map.copyOf(messages);
    }

    public static MessageBundle load(YamlConfiguration custom, YamlConfiguration localizedDefaults) {
        Map<String, String> theme = readStringSection(custom, "theme");
        Map<String, String> messages = readStringSection(localizedDefaults, "messages");
        messages.putAll(readStringSection(custom, "messages"));
        String prefix = custom.getString("prefix", "<bold>ClaimShift</bold>");
        return new MessageBundle(theme, prefix, messages);
    }

    private static Map<String, String> readStringSection(YamlConfiguration yaml, String path) {
        Map<String, String> result = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                Object value = section.get(key);
                if (value != null) {
                    result.put(key, String.valueOf(value));
                }
            }
        }
        return result;
    }

    public String message(String key) {
        return messages.getOrDefault(key, "<red>Missing message: " + key + "</red>");
    }
}
