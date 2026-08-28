package dev.onelsey.claimshift.protection;

import dev.onelsey.claimshift.config.PresencePolicy;
import dev.onelsey.claimshift.model.ClaimState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimStateCalculatorTest {
    private static final UUID OWNER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void noOwnersIsOpen() {
        var result = ClaimStateCalculator.calculate(
                Set.of(), Set.of(), Map.of(),
                PresencePolicy.ONLINE_OPEN, Duration.ofMinutes(1), true
        );
        assertEquals(ClaimState.OPEN, result.state());
        assertFalse(result.protectedNow());
    }

    @Test
    void onlineOpenMakesAnyOnlineOwnerRaidable() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A, OWNER_B),
                Set.of(OWNER_B),
                Map.of(OWNER_A, Duration.ofHours(2)),
                PresencePolicy.ONLINE_OPEN,
                Duration.ofMinutes(1),
                true
        );
        assertEquals(ClaimState.OPEN, result.state());
        assertFalse(result.protectedNow());
    }

    @Test
    void onlineOpenUsesMostRecentlyOfflineOwnerForGrace() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A, OWNER_B),
                Set.of(),
                Map.of(
                        OWNER_A, Duration.ofSeconds(50),
                        OWNER_B, Duration.ofSeconds(20)
                ),
                PresencePolicy.ONLINE_OPEN,
                Duration.ofMinutes(1),
                true
        );
        assertEquals(ClaimState.GRACE, result.state());
        assertEquals(Duration.ofSeconds(40), result.remaining());
        assertFalse(result.protectedNow());
    }

    @Test
    void onlineOpenProtectsAfterOfflineDelay() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A, OWNER_B),
                Set.of(),
                Map.of(
                        OWNER_A, Duration.ofMinutes(5),
                        OWNER_B, Duration.ofMinutes(2)
                ),
                PresencePolicy.ONLINE_OPEN,
                Duration.ofMinutes(1),
                true
        );
        assertEquals(ClaimState.PROTECTED, result.state());
        assertEquals(Duration.ZERO, result.remaining());
        assertTrue(result.protectedNow());
    }

    @Test
    void offlineOpenProtectsWhileOwnerIsOnline() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(OWNER_A),
                Map.of(),
                PresencePolicy.OFFLINE_OPEN,
                Duration.ofMinutes(1),
                true
        );
        assertEquals(ClaimState.PROTECTED, result.state());
        assertTrue(result.protectedNow());
    }

    @Test
    void offlineOpenGraceRemainsProtectedBeforeRaidWindow() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(),
                Map.of(OWNER_A, Duration.ofSeconds(20)),
                PresencePolicy.OFFLINE_OPEN,
                Duration.ofMinutes(1),
                true
        );
        assertEquals(ClaimState.GRACE, result.state());
        assertEquals(Duration.ofSeconds(40), result.remaining());
        assertTrue(result.protectedNow());
    }

    @Test
    void offlineOpenBecomesRaidableAfterOfflineDelay() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(),
                Map.of(OWNER_A, Duration.ofMinutes(2)),
                PresencePolicy.OFFLINE_OPEN,
                Duration.ofMinutes(1),
                true
        );
        assertEquals(ClaimState.OPEN, result.state());
        assertFalse(result.protectedNow());
    }


    @Test
    void knownGraceTakesPriorityOverUnknownFailOpenOwner() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A, OWNER_B),
                Set.of(),
                Map.of(OWNER_A, Duration.ofSeconds(20)),
                PresencePolicy.OFFLINE_OPEN,
                Duration.ofMinutes(1),
                false
        );
        assertEquals(ClaimState.GRACE, result.state());
        assertEquals(Duration.ofSeconds(40), result.remaining());
        assertTrue(result.protectedNow());
    }

    @Test
    void unknownFailOpenOwnerWinsAfterKnownGraceHasExpired() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A, OWNER_B),
                Set.of(),
                Map.of(OWNER_A, Duration.ofMinutes(2)),
                PresencePolicy.ONLINE_OPEN,
                Duration.ofMinutes(1),
                false
        );
        assertEquals(ClaimState.OPEN, result.state());
        assertEquals(Duration.ZERO, result.remaining());
        assertFalse(result.protectedNow());
    }

    @Test
    void unknownOwnerCanFailClosedAfterRestartForEitherPolicy() {
        for (PresencePolicy policy : PresencePolicy.values()) {
            var result = ClaimStateCalculator.calculate(
                    Set.of(OWNER_A), Set.of(), Map.of(),
                    policy, Duration.ofMinutes(1), true
            );
            assertEquals(ClaimState.PROTECTED, result.state());
            assertTrue(result.protectedNow());
        }
    }

    @Test
    void unknownOwnerCanBeConfiguredFailOpenForEitherPolicy() {
        for (PresencePolicy policy : PresencePolicy.values()) {
            var result = ClaimStateCalculator.calculate(
                    Set.of(OWNER_A), Set.of(), Map.of(),
                    policy, Duration.ofMinutes(1), false
            );
            assertEquals(ClaimState.OPEN, result.state());
            assertFalse(result.protectedNow());
        }
    }
    @Test
    void offlineOpenUsesOneHourInactiveDelayBeforeOpening() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(),
                Map.of(),
                Map.of(OWNER_A, Duration.ofMinutes(35)),
                PresencePolicy.OFFLINE_OPEN,
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                true,
                false
        );
        assertEquals(ClaimState.GRACE, result.state());
        assertEquals(Duration.ofMinutes(25), result.remaining());
        assertTrue(result.protectedNow());
    }

    @Test
    void offlineOpenBecomesOpenAfterInactiveDelay() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(),
                Map.of(),
                Map.of(OWNER_A, Duration.ofHours(2)),
                PresencePolicy.OFFLINE_OPEN,
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                true,
                false
        );
        assertEquals(ClaimState.OPEN, result.state());
        assertFalse(result.protectedNow());
    }

    @Test
    void returningOwnerDoesNotInstantlyCloseAnAlreadyOpenClaim() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(OWNER_A),
                Map.of(OWNER_A, Duration.ofMinutes(2)),
                Map.of(),
                PresencePolicy.OFFLINE_OPEN,
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                true,
                true
        );
        assertEquals(ClaimState.GRACE, result.state());
        assertEquals(Duration.ofMinutes(3), result.remaining());
        assertFalse(result.protectedNow());
    }

    @Test
    void protectionReturnsAfterActiveDelayExpires() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(OWNER_A),
                Map.of(OWNER_A, Duration.ofMinutes(6)),
                Map.of(),
                PresencePolicy.OFFLINE_OPEN,
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                true,
                true
        );
        assertEquals(ClaimState.PROTECTED, result.state());
        assertTrue(result.protectedNow());
    }

    @Test
    void reverseDelayDoesNotCreateAWindowWhenClaimNeverOpened() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(OWNER_A),
                Map.of(OWNER_A, Duration.ZERO),
                Map.of(),
                PresencePolicy.OFFLINE_OPEN,
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                true,
                false
        );
        assertEquals(ClaimState.PROTECTED, result.state());
        assertEquals(Duration.ZERO, result.remaining());
        assertTrue(result.protectedNow());
    }

    @Test
    void zeroTransitionDelaysAreImmediate() {
        var inactive = ClaimStateCalculator.calculate(
                Set.of(OWNER_A), Set.of(), Map.of(), Map.of(OWNER_A, Duration.ZERO),
                PresencePolicy.OFFLINE_OPEN, Duration.ZERO, Duration.ZERO, true, false
        );
        assertEquals(ClaimState.OPEN, inactive.state());
        assertFalse(inactive.protectedNow());

        var active = ClaimStateCalculator.calculate(
                Set.of(OWNER_A), Set.of(OWNER_A), Map.of(OWNER_A, Duration.ZERO), Map.of(),
                PresencePolicy.OFFLINE_OPEN, Duration.ZERO, Duration.ZERO, true, true
        );
        assertEquals(ClaimState.PROTECTED, active.state());
        assertTrue(active.protectedNow());
    }

    @Test
    void onlineOpenCanAlsoUseTheReverseTransitionDelay() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(OWNER_A),
                Map.of(OWNER_A, Duration.ofMinutes(2)),
                Map.of(),
                PresencePolicy.ONLINE_OPEN,
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                true,
                false
        );
        assertEquals(ClaimState.GRACE, result.state());
        assertEquals(Duration.ofMinutes(3), result.remaining());
        assertTrue(result.protectedNow());
    }

    @Test
    void secondActiveOwnerDoesNotResetAnExistingActiveTransition() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A, OWNER_B),
                Set.of(OWNER_A, OWNER_B),
                Map.of(
                        OWNER_A, Duration.ofMinutes(4),
                        OWNER_B, Duration.ofMinutes(1)
                ),
                Map.of(),
                PresencePolicy.ONLINE_OPEN,
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                true,
                false
        );
        assertEquals(ClaimState.GRACE, result.state());
        assertEquals(Duration.ofMinutes(1), result.remaining());
        assertTrue(result.protectedNow());
    }

    @Test
    void inactiveDelayDoesNotOpenAClaimThatIsAlreadyProtected() {
        var result = ClaimStateCalculator.calculate(
                Set.of(OWNER_A),
                Set.of(),
                Map.of(),
                Map.of(OWNER_A, Duration.ofSeconds(20)),
                PresencePolicy.ONLINE_OPEN,
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                true,
                false
        );
        assertEquals(ClaimState.PROTECTED, result.state());
        assertEquals(Duration.ZERO, result.remaining());
        assertTrue(result.protectedNow());
    }

}
