package dev.onelsey.claimshift.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)(ms|s|m|h|d)", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration cannot be empty");
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (normalized.equals("0")) {
            return Duration.ZERO;
        }
        Matcher matcher = TOKEN.matcher(normalized);
        long millis = 0L;
        int end = 0;
        boolean matched = false;

        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new IllegalArgumentException("Invalid duration: " + input);
            }
            matched = true;
            long amount = Long.parseLong(matcher.group(1));
            millis = Math.addExact(millis, toMillis(amount, matcher.group(2)));
            end = matcher.end();
        }

        if (!matched || end != normalized.length()) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }

        return Duration.ofMillis(millis);
    }

    private static long toMillis(long amount, String unit) {
        return switch (unit) {
            case "ms" -> amount;
            case "s" -> Math.multiplyExact(amount, 1_000L);
            case "m" -> Math.multiplyExact(amount, 60_000L);
            case "h" -> Math.multiplyExact(amount, 3_600_000L);
            case "d" -> Math.multiplyExact(amount, 86_400_000L);
            default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
        };
    }
}
