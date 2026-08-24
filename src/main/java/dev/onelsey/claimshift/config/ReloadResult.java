package dev.onelsey.claimshift.config;

public record ReloadResult(boolean success, long durationMillis, String error) {
    public static ReloadResult success(long durationMillis) {
        return new ReloadResult(true, durationMillis, null);
    }

    public static ReloadResult failure(long durationMillis, String error) {
        return new ReloadResult(false, durationMillis, error);
    }
}
