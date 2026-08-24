# ClaimShift

ClaimShift adds configurable presence-based raid windows to territories owned by players in existing claim plugins.

**1.1.1 baseline:** Minecraft **26.2**, Java **25+**, Paper/Purpur/Leaf/Folia. Spigot is intentionally unsupported.

## What ClaimShift does

A dynamically managed claim has three runtime states:

- **OPEN** — ClaimShift allows the raid window.
- **GRACE** — the last effective owner has disconnected and `offline-delay` is still counting down.
- **PROTECTED** — ClaimShift protection is active.

The new `protection.presence-policy` setting decides which presence state is raidable:

### `online-open` — anti-offline-raid mode

```text
owner online   -> OPEN
last owner quits -> GRACE (still open)
offline-delay expires -> PROTECTED
```

This is the original ClaimShift behavior: players can be raided while they are present, but their base becomes protected after they leave.

### `offline-open` — offline-raid mode

```text
owner online   -> PROTECTED
last owner quits -> GRACE (still protected)
offline-delay expires -> OPEN
```

This is useful for Rust-like or faction-style servers where a base becomes raidable only after its owners have been offline for a configured period.

With WorldGuard's default `dynamic-passthrough` integration, an OPEN claim is a real raid window: ClaimShift temporarily sets the managed region's `passthrough` flag to `ALLOW`. When protection is active, ClaimShift restores the exact passthrough value that existed before ClaimShift took control.

GRACE follows the safe side of the selected policy: it remains OPEN in `online-open`, and remains PROTECTED in `offline-open`.


## Quick start

1. Install ClaimShift together with WorldGuard/WorldEdit and start the server once.
2. Choose `protection.presence-policy` in `rules.yml`: `online-open` or `offline-open`.
3. Fresh installations are **opt-in by default** for WorldGuard. Enable a player region with:

```text
/rg flag <region> claimshift-dynamic allow
```

4. Run `/claimshift sync`, then stand inside the region and run `/claimshift inspect`.
5. If you want every owned WorldGuard region to participate automatically, set `manage-all-owned-regions: true`. Keep spawn, shops, event areas, staff bases, and other administrative regions static with `claimshift-dynamic deny` or `excluded-regions`.

This opt-in default is intentional: installing ClaimShift on an existing server must not suddenly make an old staff-owned spawn or service region dynamic.

## Supported platforms

- Minecraft 26.2
- Java 25+
- Paper
- Purpur
- Leaf
- Folia

Spigot is not supported or tested.

ClaimShift uses Paper's modern API and Folia-aware scheduling. Provider compatibility still depends on the installed claim plugin supporting the same server platform/version.

## Claim providers

### WorldGuard 7.x

WorldGuard is the primary 1.1.1 integration and supports two integration modes:

- `dynamic-passthrough` — full dynamic raid-window behavior.
- `overlay` — never changes WorldGuard flags; ClaimShift only adds its own protection denials.

ClaimShift 1.1.1 is compiled against WorldGuard 7.0.18 and WorldEdit 7.4.4 APIs. WorldGuard and WorldEdit remain server-provided dependencies and are not bundled into ClaimShift.

WorldGuard priority and inheritance are used for ClaimShift state selection:

- only highest-priority physical regions at the checked location participate in ClaimShift's protection decision;
- UUID owners/members inherited from parent regions are included;
- legacy name-only owners are resolved without blocking web lookups;
- `__global__` and non-physical template regions are never dynamically managed.

A region that already has `passthrough: ALLOW` is ignored by default because ClaimShift assumes it was intentionally configured as non-protecting. Set `manage-existing-passthrough-regions: true` only if you explicitly want ClaimShift to own those regions too.

**Important:** explicit WorldGuard flags such as `build: DENY`, `chest-access: DENY`, `use: DENY`, or other restrictive flags remain WorldGuard policy. ClaimShift changes only the raid-access layer it owns; it does not erase unrelated administrator restrictions.

## Administrative regions, spawn, shops, events

ClaimShift does **not** need to control every WorldGuard region.

Fresh installations use `manage-all-owned-regions: false`. Existing WorldGuard regions therefore remain normal/static until an administrator explicitly opts them in. This is the safe default for established servers.

Ownerless administrative regions are ignored automatically. A staff-owned spawn/shop/event region also remains static unless it is opted in. If you later enable bulk management with `manage-all-owned-regions: true`, keep administrative regions static in either of these ways:

### Per-region WorldGuard override

ClaimShift registers the WorldGuard state flag `claimshift-dynamic`.

```text
/rg flag spawn claimshift-dynamic deny
```

`deny` means: **never dynamically manage this region; leave normal WorldGuard protection alone**.

```text
/rg flag player_base claimshift-dynamic allow
```

`allow` means: **force this owned region into ClaimShift dynamic management**, even when broad include/exclude defaults would not select it or the region already had `passthrough: ALLOW`.

Clear the flag to return to normal `config.yml` selection rules.

### Config selectors

`integration.worldguard.excluded-regions` supports exact names, `world:region`, `*`, and `?` wildcards, for example:

```yaml
excluded-regions:
  - spawn
  - admin_*
  - world:event_*
```

For the default safe opt-in workflow, leave:

```yaml
manage-all-owned-regions: false
```

and opt player regions in with `included-regions` or `claimshift-dynamic allow`. For a server where almost every owned region is a player claim, set it to `true` and use `claimshift-dynamic deny` / exclusions for administrative regions.

Selection precedence for an owned physical WorldGuard region is:

1. `claimshift-dynamic deny` -> static
2. `claimshift-dynamic allow` -> dynamic
3. normal `manage-all-owned-regions` / include / exclude selectors

`/claimshift inspect` also sees top-priority WorldGuard regions that are intentionally static and reports them as `STATIC / UNMANAGED`, so administrators can verify that spawn or another protected area is not under ClaimShift control.

### Lands 7.x

Lands is supported as an **overlay** provider in 1.1.1. ClaimShift can add protection denials to Lands areas, but 1.1.1 does not rewrite Lands roles/permissions to force a land into a public raid window.

## Protection coverage

Each action can be enabled independently in `rules.yml`:

- block break
- block place
- containers
- hopper/container automation across protected boundaries
- block interactions
- entity damage
- entity interaction / armor stands / placed entities / shear / leash / unleash
- entity grief
- hanging entities
- buckets
- explosions
- pistons crossing protected boundaries
- fluids crossing protected boundaries
- fire / fire spread across protected boundaries

For non-player automation, ClaimShift tries to preserve machinery that stays entirely inside the same protected claim and blocks movement across a protected boundary.

## WorldGuard runtime safety

Dynamic passthrough is temporary state. ClaimShift stores the original passthrough value in `plugins/ClaimShift/runtime-worldguard.yml` **before** applying a temporary OPEN override.

On normal shutdown, provider reload, or the next startup after an interrupted run, ClaimShift restores the original value and removes recovery metadata after WorldGuard saves it successfully.

Do not edit `runtime-worldguard.yml` while the server is running.

**Crash-recovery rule:** if the server/process is interrupted while a region is temporarily OPEN, start the server with ClaimShift installed at least once before removing ClaimShift or manually changing the managed WorldGuard regions. That recovery boot lets ClaimShift restore any recorded original passthrough values safely.

While a WorldGuard region is dynamically managed, avoid editing that region's `passthrough` flag by hand. Exclude the region, set `claimshift-dynamic deny`, or switch to `overlay`, synchronize/reload, then make administrator passthrough changes.

## Configuration

ClaimShift uses:

- `config.yml` — language, provider selection, WorldGuard integration, diagnostics
- `rules.yml` — presence policy, timing, and protection behavior
- `messages.yml` — HEX theme, MiniMessage prefix, optional custom message overrides

Example policy configuration:

```yaml
protection:
  enabled: true
  presence-policy: online-open
  offline-delay: 10m
```

Switch only `presence-policy` to `offline-open` to invert the raid model.

Existing 1.0.x `activation-delay` configurations are migrated automatically to `offline-delay` while preserving the configured duration.

### Languages

English (`en_US`) is the default. `ru_RU` is bundled.

`language.config` controls comments in all ClaimShift YAML configuration files. `language.messages` controls built-in player/admin text.

The keys themselves are never translated, so changing language cannot invalidate the configuration structure.

`messages.yml` stores **overrides only**. If a message key is absent, ClaimShift reads it from the selected built-in locale. Changing language therefore changes untouched stock messages immediately while preserving genuinely customized messages.

ClaimShift also migrates stock messages written by older development builds out of `messages.yml` so they no longer pin the old language.

## MiniMessage and HEX

All user-facing messages use Adventure MiniMessage. The default theme exposes:

- `<primary>`
- `<secondary>`
- `<success>`
- `<warning>`
- `<error>`
- `<muted>`

Every HEX value, prefix, and message override can be customized.

## Commands

- `/claimshift help`
- `/claimshift info`
- `/claimshift inspect`
- `/claimshift sync`
- `/claimshift reload`
- `/claimshift language <en_US|ru_RU> [config|messages|both]`

Alias: `/cshift`

`/claimshift info` shows the active presence policy and offline transition delay.

`/claimshift inspect` shows the top claim, effective/online owner count, state, transition time, and whether ClaimShift is dynamically managing it.

## Permissions

- `claimshift.admin` — admin commands, **not** protection bypass
- `claimshift.reload`
- `claimshift.sync`
- `claimshift.info`
- `claimshift.inspect`
- `claimshift.language`
- `claimshift.bypass` — bypasses ClaimShift's own active protection checks

`claimshift.admin` deliberately does not grant `claimshift.bypass`, so operators can test ClaimShift's own checks without silently bypassing them. For a meaningful WorldGuard raid-window test, the attacker account should also be non-OP and must not have WorldGuard bypass/build permissions.

## Safe reload

`/claimshift reload` parses and validates candidate config, rules, MiniMessage, durations, provider settings, and locales before replacing the in-memory configuration. If validation fails, the running configuration remains active and the command reports the error.

## Building

ClaimShift uses Gradle Kotlin DSL and targets Java 25 bytecode.

Requirements:

- JDK 25
- network access to Maven repositories on the first dependency resolution

In IntelliJ IDEA, use **Gradle -> Tasks -> build -> build**. On Windows you can also run:

```bat
gradlew.bat clean build
```

On Linux/macOS:

```bash
./gradlew clean build
```

The JAR is written to `build/libs/ClaimShift-1.1.1.jar`.

The repository also contains a GitHub Actions build that compiles and runs tests on Temurin Java 25 with Gradle 9.2.1.

The source archive intentionally does not bundle WorldGuard, WorldEdit, Lands, Guava, or Gson binaries.

## Testing

See [`docs/TESTING.md`](docs/TESTING.md) for the two-account test matrix, including both presence policies and static administrative regions.

## Bug reports

Use GitHub Issues and select the bug report template. Include `/claimshift info`, `/claimshift inspect` when relevant, provider/version information, steps to reproduce, and console output.

Never publish passwords, tokens, private keys, database credentials, or other secrets.

## License

ClaimShift is source-available, not OSI open source. Server use, configuration, translations, and contribution-focused forks are permitted under the ClaimShift Software License. Unauthorized redistribution, rebranding, resale, and publication of modified builds or derivative source are prohibited.

See [`LICENSE`](LICENSE).
