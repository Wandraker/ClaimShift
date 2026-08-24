# Changelog

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
