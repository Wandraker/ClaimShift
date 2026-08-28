# ClaimShift architecture

ClaimShift intentionally does not implement its own land-claim database.

## Runtime flow

1. A `ClaimProvider` converts the active provider's territory into a provider-neutral `ClaimSnapshot`.
2. `PresenceService` tracks actual connection state and ClaimShift's active/effective presence state.
3. Smart Presence may treat a connected owner as inactive because of idle time, an external AFK state, anti-relog qualification, a configured continuous-presence cap, or ignored periodic keep-alive activity.
4. `ClaimStateService` derives OPEN, GRACE, or PROTECTED using the configured `PresencePolicy` and the active owners.
5. `RaidSessionService` can optionally keep an already-OPEN claim open while qualifying raid activity continues.
6. `ClaimStatus.protectedNow` records the effective protection state, because GRACE is open in `online-open` but protected in `offline-open`.
7. `ProtectionService` applies action rules and trusted-player/bypass policy.
8. Provider-specific dynamic access is reconciled separately from Bukkit/Paper event protection.

This separation keeps claim discovery, presence policy, protection state, raid lifecycle, and event cancellation independent.

## Presence policy

ClaimShift supports two first-class policies:

- `ONLINE_OPEN`: active owners create the raid window; the inactive/offline transition ends in protection.
- `OFFLINE_OPEN`: active owners keep protection; the inactive/offline transition ends in a raid window.

Presence transitions use two independent delays:

- `owner-active` measures time after the presence condition becomes active.
- `owner-inactive` measures time after the last active owner stops counting as present.

GRACE keeps the state that existed before the transition until the corresponding delay expires. In the default `offline-open` policy, `owner-inactive` delays opening the claim and `owner-active` delays restoring protection to a claim that is already OPEN. The reverse delay is never allowed to create a new vulnerability window when the claim is already in its target protected state.

A player can still be connected while no longer counting as active presence.

## Smart Presence

Smart Presence is an anti-abuse presence layer, not an anti-cheat.

The runtime tracks coarse activity timestamps only. It does not retain chat text, command arguments, inventory contents, or a movement history.

Meaningful activity can include:

- movement beyond the configured anchor distance;
- block breaking/placing;
- interactions and inventory activity;
- commands at root-command granularity only;
- chat as a boolean activity signal, without storing message text;
- combat.

### Periodic keep-alive detection

`ActivityPatternDetector` looks for simple low-frequency repetition of the same activity signature with nearly identical timing. When a sequence crosses the configured sample/tolerance threshold, that repeated action stops refreshing the player's meaningful-activity timestamp.

ClaimShift does not ban, flag, punish, or label the player as a bot. Genuine unrelated gameplay activity can still refresh active presence normally.

The detector is intentionally conservative and does not claim to identify sophisticated automation that imitates varied human gameplay.

### CMI / EssentialsX AFK state

CMI and EssentialsX are optional soft integrations loaded through reflection. No hard API dependency is bundled.

External AFK APIs are sampled on the player's entity scheduler and cached briefly. WorldGuard reconciliation reads the cache instead of calling a player-oriented third-party API directly from the global region scheduler. This preserves Folia's scheduler model and limits cross-region assumptions.

If an optional AFK integration cannot be linked or queried, ClaimShift's own idle/pattern logic continues to work.

### Anti-relog and continuous-presence cap

Both controls are optional and disabled by default.

Anti-relog qualification can stop a quick reconnect from instantly becoming active presence again. The continuous-presence cap can stop a single always-online session from counting forever even if it never disconnects.

They are intentionally opt-in because both can materially change ordinary player behavior.

## Raid sessions

Raid sessions are optional and disabled by default.

A session can only start from qualifying activity while a managed claim is already OPEN. Once started, the session temporarily overrides later presence-derived protection and keeps the claim OPEN until its inactivity timeout or hard maximum duration expires.

The session may extend its inactivity deadline when another configured trigger action occurs. Owner notifications are emitted at start/end.

A current WorldGuard `claimshift-raids` flag overrides the global raid-session switch for that region. If a running session becomes disabled by current claim policy, it is ended instead of remaining as a stale lock.

Session state is intentionally runtime-only. A server restart does not resurrect an old raid lock.

## WorldGuard

WorldGuard's provider is both a claim reader and a dynamic-access controller.

When `protectedNow` is false, dynamic mode uses a temporary `passthrough: ALLOW` override. When `protectedNow` is true, ClaimShift restores the value captured before it took control. Original state is stored in `runtime-worldguard.yml` before the first mutation so an interrupted run can be recovered.

Spatial state selection uses the highest-priority physical regions at a location. Parent player owners/members are folded into the provider-neutral snapshot.

### Administrative/static regions

ClaimShift registers the WorldGuard `claimshift-dynamic` StateFlag during plugin load.

- `DENY` is a per-region hard static override.
- `ALLOW` is a per-region hard dynamic override for an owned physical region.
- unset falls back to config selectors and the persistent region lifecycle registry.

Inspection can see a top-priority WorldGuard region even when it is intentionally unmanaged; protection checks continue to use only dynamically selected claims.

ClaimShift keeps a persistent `region-registry.yml` classification for WorldGuard regions. Regions already present when a loaded world is first observed in a server session are recorded as `LEGACY_STATIC`; eligible regions first discovered later while ClaimShift is actively running can be recorded as `AUTO_DYNAMIC` when `auto-manage-new-regions` is enabled. This lets a server install ClaimShift without converting historical regions while making newly created player claims dynamic automatically. Explicit `claimshift-dynamic` flags and broad config selectors remain higher-level administrator controls.

### Per-region policy overrides

Additional WorldGuard flags can tune a managed region without creating a separate global configuration:

- `claimshift-policy` — presence policy override (`online-open` / `offline-open`)
- `claimshift-active-delay` — delay after an active owner appears
- `claimshift-inactive-delay` — delay after the last active owner becomes inactive
- `claimshift-delay` — compatibility alias for the inactive-owner delay
- `claimshift-raids` — raid-session enable/disable override

Invalid string overrides fall back to the validated global rule instead of breaking protection evaluation.

## Dry-run diagnostics

A fresh installation ships with `diagnostics.dry-run: true`.

Dry-run is a safety preview. ClaimShift still discovers claims and calculates states, but it:

- does not persist temporary WorldGuard passthrough changes;
- restores any previously owned runtime override before previewing;
- does not deny protected actions;
- does not start raid sessions;
- can log state/passthrough transitions without writing player/region content;
- can notify operators that the safety mode is active.

Config migration explicitly disables dry-run for existing pre-1.3 installations so an upgrade never silently turns an already-running protection setup into preview-only behavior.

## Folia

ClaimShift never assumes one universal server main thread.

WorldGuard reconciliation and provider reloads are dispatched through Paper's `GlobalRegionScheduler`. Event handlers execute in the context supplied by the server. Player-facing notifications and optional external AFK API sampling use the player's entity scheduler. In-memory presence and raid state use concurrent collections with small synchronized per-player sections where necessary.

## WorldGuard persistence discipline

`RegionManager#saveChanges()` persists a whole world's region manager. When ClaimShift has multiple runtime passthrough overrides in the same manager, saving one restored region must not accidentally serialize another region's temporary OPEN value. Before a manager save, ClaimShift stages every recorded region in that manager back to its captured original passthrough, saves once, then reapplies only runtime overrides that are still active. Recovery metadata is removed only after the save succeeds.

`runtime-worldguard.yml` is treated as safety-critical recovery state. A malformed recovery file is not silently discarded because that could strand a temporary passthrough override after an interrupted run.

## Time model

Presence transitions, Smart Presence idle time, anti-relog windows, raid sessions, and notification cooldowns use monotonic elapsed time (`System.nanoTime`) rather than wall-clock time.

Restarted-server ownership with no current-session absence timestamp follows `protect-unknown-offline-owners`; the default is fail-closed/protected for both presence policies.

## Configuration migration

Rules schema 2 replaced the old `protection.activation-delay` key with `protection.offline-delay` and added `protection.presence-policy`.

Rules schema 3 adds Smart Presence, anti-relog, continuous-presence, and raid-session settings. Smart Presence is a safe default for upgrades; aggressive anti-relog/max-session/raid behavior stays disabled unless an administrator enables it.

Rules schema 4 replaces the one-way `protection.offline-delay` model with `protection.transition-delays.owner-active` and `owner-inactive`. Existing installations preserve their previous policy and map the old delay to `owner-inactive`; the newly introduced reverse transition migrates to `0s` so an update does not silently add a new raid window.

Config schema 2 adds diagnostics. The bundled fresh-install config enables dry-run, while migration from the older schema explicitly writes `diagnostics.dry-run: false` before defaults are merged.

## Localization model

Bundled locale files are immutable stock text. `messages.yml` stores theme/prefix plus explicit overrides only. Locale changes therefore replace untouched stock text immediately while preserving custom overrides. Legacy full-message files are migrated once using the configuration schema marker.

## Command localization boundary

Executable command names, subcommands and argument scope tokens are defined in code and remain locale-independent. Locale bundles only provide human-readable prose. Usage messages receive command syntax through placeholders so translating a locale cannot accidentally change executable command tokens.

## Metrics

Optional bStats integration is isolated in `MetricsService`. It can be reconciled after a safe configuration reload and shut down cleanly during plugin disable.

Custom charts expose only coarse ClaimShift configuration choices such as presence policy, provider, WorldGuard mode, locale, Smart Presence/pattern settings and the global raid-session switch. They never include claim names, player identifiers, server addresses or content.
