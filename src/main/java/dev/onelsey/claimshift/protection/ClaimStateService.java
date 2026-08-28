package dev.onelsey.claimshift.protection;

import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.config.PresencePolicy;
import dev.onelsey.claimshift.config.PresenceSettings;
import dev.onelsey.claimshift.config.RuleSettings;
import dev.onelsey.claimshift.model.ClaimSnapshot;
import dev.onelsey.claimshift.model.ClaimState;
import dev.onelsey.claimshift.model.ClaimStatus;
import dev.onelsey.claimshift.util.DurationParser;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClaimStateService {
    private final ConfigurationService configuration;
    private final PresenceService presence;
    private final RaidSessionService raids;

    public ClaimStateService(ConfigurationService configuration, PresenceService presence, RaidSessionService raids) {
        this.configuration = configuration;
        this.presence = presence;
        this.raids = raids;
    }

    public ClaimStatus evaluate(ClaimSnapshot claim) {
        RuleSettings rules = configuration.ruleSettings();
        PresenceSettings presenceSettings = rules.presence();
        Set<UUID> onlineOwners = presence.onlineOwners(claim.owners());
        Set<UUID> activeOwners = new HashSet<>();
        Map<UUID, Duration> activeAges = new HashMap<>();
        Map<UUID, Duration> knownInactiveAges = new HashMap<>();

        for (UUID owner : claim.owners()) {
            PresenceService.Evaluation evaluation = presence.evaluate(owner, presenceSettings);
            if (evaluation.effective()) {
                activeOwners.add(owner);
                evaluation.effectiveAge().ifPresent(age -> activeAges.put(owner, age));
            } else {
                evaluation.absenceAge().ifPresent(age -> knownInactiveAges.put(owner, age));
            }
        }

        PresencePolicy effectivePolicy = effectivePolicy(claim, rules);
        Duration activeDelay = effectiveActiveDelay(claim, rules);
        Duration inactiveDelay = effectiveInactiveDelay(claim, rules);
        Boolean currentlyDynamicOpen = claim.attribute("runtime-dynamic-open")
                .map(Boolean::parseBoolean)
                .orElse(null);

        ClaimStateCalculator.Result result = ClaimStateCalculator.calculate(
                claim.owners(),
                activeOwners,
                activeAges,
                knownInactiveAges,
                effectivePolicy,
                activeDelay,
                inactiveDelay,
                rules.protectUnknownOfflineOwners(),
                currentlyDynamicOpen
        );

        boolean raidActive = raids.isActiveFor(claim);
        Duration raidRemaining = raids.remaining(claim.key()).orElse(Duration.ZERO);
        if (raidActive) {
            return new ClaimStatus(
                    claim,
                    ClaimState.OPEN,
                    Duration.ZERO,
                    onlineOwners,
                    activeOwners,
                    false,
                    true,
                    raidRemaining
            );
        }

        return new ClaimStatus(
                claim,
                result.state(),
                result.remaining(),
                onlineOwners,
                activeOwners,
                result.protectedNow(),
                false,
                Duration.ZERO
        );
    }

    public PresencePolicy effectivePolicy(ClaimSnapshot claim, RuleSettings rules) {
        return claim.attribute("presence-policy").map(value -> {
            try {
                return PresencePolicy.parse(value);
            } catch (IllegalArgumentException ignored) {
                return rules.presencePolicy();
            }
        }).orElse(rules.presencePolicy());
    }

    public Duration effectiveActiveDelay(ClaimSnapshot claim, RuleSettings rules) {
        return durationOverride(claim, "active-delay", rules.activeDelay());
    }

    public Duration effectiveInactiveDelay(ClaimSnapshot claim, RuleSettings rules) {
        return durationOverride(claim, "inactive-delay", rules.inactiveDelay());
    }

    /** Compatibility name retained for older callers: this is the inactive delay. */
    public Duration effectiveDelay(ClaimSnapshot claim, RuleSettings rules) {
        return effectiveInactiveDelay(claim, rules);
    }

    private Duration durationOverride(ClaimSnapshot claim, String key, Duration fallback) {
        return claim.attribute(key).map(value -> {
            try {
                Duration parsed = DurationParser.parse(value);
                return parsed.isNegative() ? fallback : parsed;
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }).orElse(fallback);
    }
}
