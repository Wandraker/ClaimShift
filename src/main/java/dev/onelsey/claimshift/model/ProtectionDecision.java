package dev.onelsey.claimshift.model;

public record ProtectionDecision(boolean denied, ClaimSnapshot claim, ProtectionAction action) {
    public static ProtectionDecision allow(ProtectionAction action) {
        return new ProtectionDecision(false, null, action);
    }

    public static ProtectionDecision deny(ClaimSnapshot claim, ProtectionAction action) {
        return new ProtectionDecision(true, claim, action);
    }
}
