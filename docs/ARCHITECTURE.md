# ClaimShift architecture

ClaimShift intentionally does not implement its own land-claim database.

## Runtime flow

1. A `ClaimProvider` converts the active provider's territory into a provider-neutral `ClaimSnapshot`.
2. `PresenceService` tracks online owners and current-run quit timestamps.
3. `ClaimStateService` derives OPEN, GRACE, or PROTECTED using the configured `PresencePolicy`.
4. `ClaimStatus.protectedNow` records the effective protection state, because GRACE is open in `online-open` but protected in `offline-open`.
5. `ProtectionService` applies action rules and trusted-player/bypass policy.
6. Provider-specific dynamic access is reconciled separately from Bukkit/Paper event protection.

This separation keeps claim discovery, presence policy, protection state, and event cancellation independent.

## Presence policy

ClaimShift supports two first-class policies:

- `ONLINE_OPEN`: online owners create the raid window; the offline transition ends in protection.
- `OFFLINE_OPEN`: online owners keep protection; the offline transition ends in a raid window.

`offline-delay` always measures time after the last effective owner disconnects. GRACE keeps the pre-transition safety state until that delay expires.

## WorldGuard

WorldGuard's provider is both a claim reader and a dynamic-access controller.

When `protectedNow` is false, dynamic mode uses a temporary `passthrough: ALLOW` override. When `protectedNow` is true, ClaimShift restores the value captured before it took control. Original state is stored in `runtime-worldguard.yml` before the first mutation so an interrupted run can be recovered.

Spatial state selection uses the highest-priority physical regions at a location. Parent player owners/members are folded into the effective snapshot.

### Administrative/static regions

ClaimShift registers the WorldGuard `claimshift-dynamic` StateFlag during plugin load.

- `DENY` is a per-region hard static override.
- `ALLOW` is a per-region hard dynamic override for an owned physical region.
- unset falls back to config selectors.

Inspection can see a top-priority WorldGuard region even when it is intentionally unmanaged; protection checks continue to use only dynamically selected claims.

## Folia

ClaimShift never assumes one universal server main thread. WorldGuard reconciliation and provider reloads are dispatched through Paper's `GlobalRegionScheduler`; event handlers execute in the context supplied by the server. In-memory presence data uses concurrent collections.

## WorldGuard persistence discipline

`RegionManager#saveChanges()` persists a whole world's region manager. When ClaimShift has multiple runtime passthrough overrides in the same manager, saving one restored region must not accidentally serialize another region's temporary OPEN value. Before a manager save, ClaimShift stages every recorded region in that manager back to its captured original passthrough, saves once, then reapplies only runtime overrides that are still active. Recovery metadata is removed only after the save succeeds.

`runtime-worldguard.yml` is treated as safety-critical recovery state. A malformed recovery file is not silently discarded because that could strand a temporary passthrough override after an interrupted run.

## Time model

Offline transitions and notification cooldowns use monotonic elapsed time (`System.nanoTime`) rather than wall-clock time. Restarted-server ownership with no current-session quit timestamp follows `protect-unknown-offline-owners`; the default is fail-closed/protected for both presence policies.

## Configuration migration

Rules schema 2 replaces the old `protection.activation-delay` key with `protection.offline-delay` and adds `protection.presence-policy`. ClaimShift migrates schema-1 rules atomically, preserving the old duration and defaulting the original behavior to `online-open`.

## Localization model

Bundled locale files are immutable stock text. `messages.yml` stores theme/prefix plus explicit overrides only. Locale changes therefore replace untouched stock text immediately while preserving custom overrides. Legacy full-message files are migrated once using the configuration schema marker.

## Command localization boundary

Executable command names, subcommands and argument scope tokens are defined in code and remain locale-independent. Locale bundles only provide human-readable prose. Usage messages receive command syntax through placeholders so translating a locale cannot accidentally change executable command tokens.

## Metrics

Optional bStats integration is isolated in `MetricsService`. It can be reconciled after a safe configuration reload and shut down cleanly during plugin disable. Custom charts expose only coarse ClaimShift configuration choices and never claim/player identifiers.
