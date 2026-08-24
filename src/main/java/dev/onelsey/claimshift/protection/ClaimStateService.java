package dev.onelsey.claimshift.protection;

import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.config.RuleSettings;
import dev.onelsey.claimshift.model.ClaimSnapshot;
import dev.onelsey.claimshift.model.ClaimStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClaimStateService {
    private final ConfigurationService configuration;
    private final PresenceService presence;

    public ClaimStateService(ConfigurationService configuration, PresenceService presence) {
        this.configuration = configuration;
        this.presence = presence;
    }

    public ClaimStatus evaluate(ClaimSnapshot claim) {
        RuleSettings rules = configuration.ruleSettings();
        Set<UUID> onlineOwners = presence.onlineOwners(claim.owners());

        Map<UUID, Duration> knownOfflineAges = new HashMap<>();
        for (UUID owner : claim.owners()) {
            if (onlineOwners.contains(owner)) {
                continue;
            }
            presence.offlineAge(owner).ifPresent(age -> knownOfflineAges.put(owner, age));
        }

        ClaimStateCalculator.Result result = ClaimStateCalculator.calculate(
                claim.owners(),
                onlineOwners,
                knownOfflineAges,
                rules.presencePolicy(),
                rules.offlineDelay(),
                rules.protectUnknownOfflineOwners()
        );
        return new ClaimStatus(claim, result.state(), result.remaining(), onlineOwners, result.protectedNow());
    }
}
