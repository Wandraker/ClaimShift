package dev.onelsey.claimshift.protection;

import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.config.PresencePolicy;
import dev.onelsey.claimshift.config.RuleSettings;
import dev.onelsey.claimshift.integration.ClaimProvider;
import dev.onelsey.claimshift.integration.ProviderManager;
import dev.onelsey.claimshift.model.ClaimSnapshot;
import dev.onelsey.claimshift.model.ClaimStatus;
import dev.onelsey.claimshift.model.ProtectionAction;
import dev.onelsey.claimshift.model.ProtectionDecision;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ProtectionService {
    private final ConfigurationService configuration;
    private final ProviderManager providers;
    private final ClaimStateService states;
    private final RaidSessionService raids;

    public ProtectionService(
            ConfigurationService configuration,
            ProviderManager providers,
            ClaimStateService states,
            RaidSessionService raids
    ) {
        this.configuration = configuration;
        this.providers = providers;
        this.states = states;
        this.raids = raids;
    }

    public ProtectionDecision check(Player actor, Location location, ProtectionAction action) {
        RuleSettings rules = configuration.ruleSettings();
        if (!rules.protects(action)) {
            return ProtectionDecision.allow(action);
        }
        if (actor != null && actor.hasPermission("claimshift.bypass")) {
            return ProtectionDecision.allow(action);
        }

        ClaimProvider provider = providers.active();
        if (!provider.available()) {
            return ProtectionDecision.allow(action);
        }

        boolean dryRun = configuration.pluginSettings().diagnostics().dryRun();
        List<ClaimSnapshot> claims = provider.findClaims(location);
        for (ClaimSnapshot claim : claims) {
            boolean trusted = actor != null && claim.isTrusted(actor.getUniqueId());
            if (trusted && rules.trustedPlayersBypass()) {
                continue;
            }
            ClaimStatus status = states.evaluate(claim);
            if (status.protectedNow()) {
                if (!dryRun) {
                    return ProtectionDecision.deny(claim, action);
                }
                continue;
            }

            if (!dryRun && actor != null && !trusted && provider.isDynamicallyManaged(claim)) {
                RaidSessionService.Update update = raids.recordOpenClaimActivity(claim, action);
                if (update.active() && update.nextExpiry().isPositive()) {
                    providers.requestReconcileAfter(update.nextExpiry());
                }
            }
        }
        return ProtectionDecision.allow(action);
    }

    public ProtectionDecision checkSystem(Location location, ProtectionAction action) {
        return check(null, location, action);
    }

    public Optional<ClaimStatus> inspect(Location location) {
        ClaimProvider provider = providers.active();
        if (!provider.available()) {
            return Optional.empty();
        }
        return provider.findClaimsForInspection(location).stream().map(states::evaluate).findFirst();
    }

    public Set<String> protectedClaimKeys(Location location, ProtectionAction action) {
        RuleSettings rules = configuration.ruleSettings();
        if (!rules.protects(action) || configuration.pluginSettings().diagnostics().dryRun()) {
            return Set.of();
        }
        ClaimProvider provider = providers.active();
        if (!provider.available()) {
            return Set.of();
        }

        Set<String> result = new LinkedHashSet<>();
        for (ClaimSnapshot claim : provider.findClaims(location)) {
            if (states.evaluate(claim).protectedNow()) {
                result.add(claim.key());
            }
        }
        return Set.copyOf(result);
    }

    /**
     * Returns true when a non-player mechanism crosses into or out of a protected claim domain.
     * Movement entirely inside the same protected claim(s) is allowed so internal automation can keep working.
     */
    public boolean crossesProtectedBoundary(Location from, Location to, ProtectionAction action) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return false;
        }
        Set<String> fromClaims = protectedClaimKeys(from, action);
        Set<String> toClaims = protectedClaimKeys(to, action);
        if (fromClaims.isEmpty() && toClaims.isEmpty()) {
            return false;
        }
        return !fromClaims.equals(toClaims);
    }

    public int claimCount(Location location) {
        ClaimProvider provider = providers.active();
        return provider.available() ? provider.findClaims(location).size() : 0;
    }

    public PresencePolicy effectivePolicy(ClaimSnapshot claim) {
        return states.effectivePolicy(claim, configuration.ruleSettings());
    }

    public Duration effectiveActiveDelay(ClaimSnapshot claim) {
        return states.effectiveActiveDelay(claim, configuration.ruleSettings());
    }

    public Duration effectiveInactiveDelay(ClaimSnapshot claim) {
        return states.effectiveInactiveDelay(claim, configuration.ruleSettings());
    }

    /** Compatibility accessor for the old one-way delay name. */
    public Duration effectiveDelay(ClaimSnapshot claim) {
        return effectiveInactiveDelay(claim);
    }

    public boolean raidSessionsEnabled(ClaimSnapshot claim) {
        return raids.enabledFor(claim, configuration.ruleSettings().raids());
    }
}
