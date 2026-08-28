# Changelog

## 1.3.1

### Build and tests

- Fixed the WorldGuard region lifecycle regression test loading Bukkit/Paper classes on the unit-test runtime classpath.
- Moved first-observation lifecycle classification into a pure Java helper so the policy can be tested without a running server API.

WorldGuard region lifecycle and first-run diagnostics usability update.

### Region lifecycle

- Added `integration.worldguard.auto-manage-new-regions`, enabled by default.
- WorldGuard regions already present when ClaimShift first observes a loaded world are recorded as legacy/static and keep their normal WorldGuard behavior.
- Eligible player-owned regions first created while ClaimShift is actively running are automatically classified as dynamic, without requiring `claimshift-dynamic allow`.
- Common WorldGuard `/rg` / `/region` mutation commands trigger a near-immediate reconciliation, while periodic reconciliation remains the fallback for regions created through APIs or other plugins.
- Automatic/legacy classification persists across restarts in internal `region-registry.yml`.
- Regions created while a world was unavailable are safely treated as pre-existing/static when that world is loaded again.
- Deleted region classifications are pruned while their world is loaded, so recreating a region later can be treated as a new region.
- `manage-all-owned-regions`, include selectors, exclude selectors, and explicit `claimshift-dynamic allow/deny` remain available as administrator overrides.
- `/claimshift inspect` now reports the management source, making it clear whether a region is automatic, legacy/static, manually enabled/disabled, included, excluded, or controlled by manage-all.
- Inspection now also explains when an otherwise selected region is intentionally left unmanaged because it already had administrator-owned `passthrough: ALLOW`.

### Dry-run usability

- Reworded first-install dry-run notices to explain that WorldGuard continues enforcing its normal protection while ClaimShift is preview-only.
- The operator notice now tells administrators to inspect a player claim before disabling dry-run.
- Executable `/claimshift inspect` syntax remains code-owned and is inserted through a placeholder instead of being embedded in localized message files.

### Configuration and documentation

- Config schema moved to 3.
- Added localized comments for automatic new-region management across every bundled configuration locale.
- Updated README, compatibility notes, architecture documentation, manual testing, and the public EN/RU Wiki for the old-static/new-dynamic region lifecycle.

## 1.3.0

Smart Presence, anti-abuse, raid lifecycle and first-install diagnostics update.

### Smart Presence

- Added active-owner presence tracking so a connected account does not have to count as present forever.
- Smart Presence is enabled by default with a configurable idle timeout.
- Added meaningful movement anchors so tiny position jitter and camera movement do not continuously refresh presence.
- Added conservative periodic keep-alive detection for repeated low-frequency actions with nearly identical timing.
- Periodic detection does not punish or flag players; matching keep-alive activity simply stops refreshing active presence.
- Added coarse activity signals for movement, blocks, interactions, inventory use, root commands, chat activity and combat without storing chat text, command arguments or inventory contents.
- Added optional soft AFK bridges for CMI and EssentialsX. External AFK APIs are sampled on the player's entity scheduler and cached for Folia-safe reconciliation.
- Added optional anti-relog qualification and maximum continuous-presence controls; both remain disabled by default.
- `/claimshift inspect` now separates connected owners from active owners. Russian UI wording uses «Активные владельцы» rather than a literal technical translation of effective owners.

### Raid sessions

- Added optional runtime raid sessions, disabled by default.
- Qualifying hostile activity can lock an already-OPEN claim open until the inactivity timeout or hard maximum duration expires.
- Added configurable trigger action types and optional inactivity-window extension on continued raid activity.
- Added owner notifications for raid-session start/end.
- Running sessions re-check the claim's current raid enable policy instead of remaining stale after a global/per-region policy change.
- Enabling dry-run terminates active raid sessions and prevents new sessions from starting.
- Raid timing validation now remains strict even when the global raid switch is disabled because a WorldGuard region may enable raids explicitly.
- Raid sessions are runtime-only and are not resurrected after a server restart.

### Transition model and WorldGuard overrides

- Replaced the one-way offline transition timer with independent active-owner and inactive-owner transition delays.
- Fresh installations now default to `offline-open`, with `owner-inactive: 1h` before a claim becomes OPEN and `owner-active: 5m` before protection returns to an already OPEN claim.
- Either transition delay accepts `0` or `0s` for an immediate transition.
- The active-owner recovery delay is only applied when the claim is actually in the opposite dynamic state, so joining after a restart or returning before the claim ever opened does not create a new vulnerability window.
- Existing installations preserve their selected presence policy and previous offline delay. The old delay migrates to `owner-inactive`, while the newly introduced reverse transition migrates as immediate (`0s`) to avoid silently changing live behavior.
- Added `claimshift-policy` per-region presence-policy override.
- Added `claimshift-active-delay` and `claimshift-inactive-delay` per-region transition overrides.
- Kept the older `claimshift-delay` WorldGuard flag as a compatibility alias for the inactive-owner delay.
- Runtime OPEN/PROTECTED memory is now tracked independently from crash-recovery metadata, so reverse transition delays also work correctly for regions whose original WorldGuard `passthrough` was already `ALLOW`.
- Added `claimshift-raids` per-region raid-session allow/deny override.
- Existing `claimshift-dynamic` opt-in/static override behavior is unchanged.

### First-install diagnostics

- Added `diagnostics.dry-run`, `diagnostics.log-transitions`, and `diagnostics.operator-notice`.
- Brand-new installations start in dry-run so administrators can preview ClaimShift decisions without changing WorldGuard state or denying actions.
- Existing installations migrate with dry-run explicitly disabled, so upgrading never silently changes an enforced server into preview-only mode.
- Added `/claimshift dryrun <on|off|status>` and the `claimshift.dryrun` permission.
- Operators receive a title/chat reminder while dry-run is enabled, including the stable command used to disable it.
- Dry-run can log changed state/passthrough previews while suppressing identical repeated reconciliation output.

### Configuration, localization and diagnostics

- Rules schema moved to 4; config schema remains 2.
- Expanded localized comments for all Smart Presence, anti-relog, maximum-presence and raid settings across every bundled locale.
- Added Smart Presence, pattern detection, external AFK source, dry-run and raid-session information to `/claimshift info` / `/claimshift inspect`.
- Expanded bStats configuration charts with coarse Smart Presence, periodic-pattern and global raid-session switches; no player/claim identifiers are submitted by ClaimShift custom charts.
- Updated architecture, compatibility and manual testing documentation for Smart Presence, CMI/EssentialsX, Folia-safe AFK sampling, dry-run, per-region overrides and raid sessions.

## 1.2.0

Public usability and observability update.

- Added optional anonymous bStats integration using plugin ID `33671`.
- Added custom bStats charts for presence policy, active claim provider, WorldGuard mode, and message locale.
- Added `metrics.enabled` with live enable/disable reconciliation through `/claimshift reload`; the global bStats opt-out remains respected.
- Bundled German, Spanish, French, Polish, Brazilian Portuguese, Ukrainian, and Simplified Chinese localization in addition to English and Russian.
- Centralized locale canonicalization and added common short-code handling such as `de`, `pt`, and `zh-cn`.
- Fixed localization leaking into executable command syntax: command names, subcommands, and `config/messages/both` scope tokens are now code-owned and stable across every locale.
- Usage/error messages now receive command syntax and allowed scope tokens through safe placeholders instead of embedding translatable command literals.
- Added metrics status to `/claimshift info`.
- Removed release-number wording from runtime/provider configuration comments.
- Added regression tests for locale canonicalization and stable command syntax.
- Build output now shades and relocates bStats into the ClaimShift JAR to avoid classpath conflicts.

## 1.1.1

State-calculation regression fix.

### Protection state

- Fixed `protect-unknown-offline-owners: false` not actually failing open under the `online-open` presence policy after a restart when an owner's current-session offline timestamp is unknown.
- Unknown owners now explicitly resolve to OPEN when fail-open mode is selected and no known grace transition is still active.
- A known, still-running grace period continues to take priority over the unknown-owner fallback, preserving the configured transition window.
- Added regression coverage for both pure unknown-owner and mixed known/unknown-owner cases.
- Runtime provider behavior, region selection, WorldGuard state restoration, and configuration schema are otherwise unchanged from 1.1.0.

## 1.1.0

Presence-policy and administrator-region usability update.

### Dynamic policy

- Added `protection.presence-policy` with `online-open` and `offline-open` modes.
- `online-open` preserves the original ClaimShift model: online owners create an OPEN raid window; after the last owner disconnects, GRACE remains open until the offline delay expires and the claim becomes protected.
- `offline-open` inverts the model: online owners remain protected; after the last owner disconnects, GRACE remains protected until the offline delay expires and the claim becomes OPEN.
- GRACE now carries an explicit effective protection state instead of assuming that every grace period is raidable.
- Unknown offline-owner timestamps remain fail-closed/protected by default for both policies.

### Configuration migration

- Rules schema moved to version 2.
- Replaced `protection.activation-delay` with the clearer `protection.offline-delay`.
- Existing 1.0.x rules are migrated atomically: the old delay value is preserved and policy defaults to `online-open`.
- `/claimshift info` now reports the active presence policy and offline transition delay.

### WorldGuard administration

- Fresh installations now default to safe opt-in WorldGuard management with `manage-all-owned-regions: false`; existing regions are left static until selected explicitly. Existing 1.0.x configs keep their configured value during upgrade.
- Added the custom WorldGuard state flag `claimshift-dynamic`.
- `claimshift-dynamic: deny` forces a region to remain static and leaves normal WorldGuard protection untouched.
- `claimshift-dynamic: allow` forces an owned physical region into dynamic ClaimShift management, including explicitly opted-in regions with pre-existing `passthrough: ALLOW`.
- Per-region flag overrides take precedence over broad include/exclude selection.
- Ownerless administrative regions remain ignored automatically.
- `/claimshift inspect` can now see intentionally unmanaged top-priority WorldGuard regions and reports them as STATIC / UNMANAGED instead of pretending no region exists.
- Added documentation for safe spawn/shop/event handling and an opt-in migration model using `manage-all-owned-regions: false`.

### Messaging / docs

- Protection-denial text is now policy-neutral instead of assuming every protected claim has an offline owner.
- GRACE/inspection timing labels are now transition-neutral and work for both policy directions.
- Expanded compatibility, architecture, README, and manual test coverage for both policies and static administrative regions.

## 1.0.2

Runtime startup and MiniMessage validation fix.

### Messaging / startup

- Fixed startup failure on valid bundled messages such as `language-success` when placeholders were nested inside formatting tags.
- Message validation now uses self-closing component placeholders, matching the resolver semantics used by runtime message rendering.
- The `<prefix>` insertion is now explicitly self-closing in both validation and runtime rendering, so following message content cannot become a child of the prefix tag.
- Kept strict MiniMessage validation enabled; malformed custom messages still fail safe reload/startup instead of being silently accepted.
- Runtime claim/protection behavior is unchanged from 1.0.1.

## 1.0.1

Build/test reliability update. Runtime ClaimShift behavior is unchanged from 1.0.0.

### Build and tests

- Added the JUnit Platform launcher explicitly to the test runtime classpath, fixing Gradle 9.x test startup failures such as `Failed to load JUnit Platform`.
- Kept JUnit artifacts aligned through the existing JUnit 6.0.1 BOM.
- Updated release/build metadata and artifact naming to 1.0.1.
- Corrected the documented GitHub Actions Gradle version to 9.2.1.

## 1.0.0

First stable ClaimShift architecture for Minecraft 26.2 / Java 25.

### Dynamic protection

- Added explicit `OPEN`, `GRACE`, and `PROTECTED` claim states.
- A claim stays OPEN while at least one effective owner is online.
- Added configurable offline activation delay after the last owner leaves.
- Added safe startup behavior for owners whose quit timestamp is unknown after restart.
- Added multiple-owner handling.

### WorldGuard

- Added full `dynamic-passthrough` mode so online/grace regions can actually be raided instead of remaining blocked by WorldGuard's normal membership protection.
- Captures the original `passthrough` value before changing a region and restores it in PROTECTED state.
- Added persistent `runtime-worldguard.yml` recovery metadata for interrupted runs.
- Restores temporary WorldGuard state on provider reload and normal plugin shutdown.
- Mirrors WorldGuard highest-priority membership/build semantics for overlapping physical regions.
- Includes inherited player owners/members; supports UUID ownership plus cached/online legacy names and deterministic offline-mode UUIDs.
- Ignores non-physical/template regions and `__global__` for dynamic management.
- Added include/exclude selectors with `world:region`, `*`, and `?` support.
- Existing passthrough-ALLOW regions are left untouched by default.
- Added configurable safety reconciliation interval plus immediate join/quit reconciliation.
- Added monotonic real-time grace/notification timing so wall-clock changes do not distort protection transitions.
- WorldGuard manager saves now stage every ClaimShift-controlled region back to its captured original value before saving, preventing another OPEN region's temporary `passthrough: ALLOW` from being persisted accidentally.
- Recovery metadata loading is strict: a malformed `runtime-worldguard.yml` fails the integration loudly instead of silently ignoring potentially required restoration data.

### Protection coverage

- Block breaking and placing.
- Containers and selected block interactions.
- Hopper/container automation across protected boundaries.
- Entity damage, entity interaction, armor stands, entity placement, shear/leash/unleash, entity grief, and hanging entities.
- Buckets and explosions.
- Boundary-aware pistons, fluid flow, and fire spread.
- Piston boundary checks include the extending piston-head destination, not only moved blocks.
- Uses Paper 26.2's current entity-interaction event path instead of the obsolete generic interaction event.
- Trusted owners/members can bypass ClaimShift protection when enabled.
- `claimshift.admin` deliberately does not imply `claimshift.bypass`.

### Localization and configuration

- English is the default configuration/message locale; Russian is bundled.
- Configuration keys remain language-independent.
- Config/rules/messages comments are regenerated in `language.config` without resetting configured values.
- `messages.yml` now stores custom overrides only; missing messages come from the active built-in locale.
- Added migration of stock 0.x message values so old defaults no longer pin the previous language.
- Message migration is schema-gated and runs only for legacy files, so a deliberate custom override equal to a stock string is not deleted on later reloads.
- Custom MiniMessage/HEX values survive locale switches.
- Reload validates configuration, durations, provider settings, theme colors, and MiniMessage before replacing runtime state.

### Administration

- Added `/claimshift help`.
- Added `/claimshift info` with provider diagnostics and state counts.
- Added `/claimshift inspect` with effective/online owners, state, grace time, and dynamic-management status.
- Added `/claimshift sync` for immediate provider reconciliation.
- Added `/claimshift reload` safe reload.
- Added `/claimshift language <locale> [config|messages|both]`.

### Platform/build

- Minecraft 26.2 baseline.
- Java 25 bytecode/toolchain.
- Paper, Purpur, Leaf, and Folia targets.
- Spigot intentionally unsupported.
- Paper API pinned to `26.2.build.116-stable`.
- WorldGuard 7.0.18 / WorldEdit 7.4.4 compile-only non-transitive APIs to avoid Paper/EngineHub Guava/Gson constraint conflicts.
- LandsAPI 7.25.4 overlay integration.
- Reproducible JAR ordering/timestamps and embedded ClaimShift license.
- Added GitHub Actions Java 25 / Gradle 9.2.1 build-and-test workflow.

## 0.2.2-dev

- Resolved Paper 26.2 / EngineHub Gson dependency metadata conflict by making EngineHub API edges non-transitive.

## 0.2.1-dev

- Resolved Paper 26.2 / WorldGuard Guava dependency metadata conflict.

## 0.2.0-dev

- Moved development baseline to Minecraft 26.2 + Java 25.

## 0.1.0-dev

- Initial development foundation.
