package dev.onelsey.claimshift.integration;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class RegionSelector {
    private RegionSelector() {
    }

    static boolean matchesAny(String world, String region, Set<String> patterns) {
        for (String pattern : patterns) {
            if (matches(world, region, pattern)) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(String world, String region, String pattern) {
        String normalized = pattern.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        String regionValue = region.toLowerCase(Locale.ROOT);
        String qualified = (world + ":" + region).toLowerCase(Locale.ROOT);
        String candidate = normalized.contains(":") ? qualified : regionValue;
        return wildcard(normalized).matcher(candidate).matches();
    }

    private static Pattern wildcard(String input) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < input.length(); index++) {
            char ch = input.charAt(index);
            if (ch == '*') {
                regex.append(".*");
            } else if (ch == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }
}
