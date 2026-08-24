package dev.onelsey.claimshift.util;

import java.time.Duration;

public final class DurationFormatter {
    private DurationFormatter() {
    }

    public static String format(Duration duration) {
        long millis = duration.toMillis();
        if (millis == 0L) {
            return "0s";
        }

        StringBuilder result = new StringBuilder();
        long days = millis / 86_400_000L;
        millis %= 86_400_000L;
        long hours = millis / 3_600_000L;
        millis %= 3_600_000L;
        long minutes = millis / 60_000L;
        millis %= 60_000L;
        long seconds = millis / 1_000L;
        millis %= 1_000L;

        append(result, days, "d");
        append(result, hours, "h");
        append(result, minutes, "m");
        append(result, seconds, "s");
        append(result, millis, "ms");
        return result.toString();
    }

    private static void append(StringBuilder builder, long value, String suffix) {
        if (value > 0L) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(value).append(suffix);
        }
    }
}
