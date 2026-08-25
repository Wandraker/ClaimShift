# ClaimShift

ClaimShift adds presence-based dynamic protection to existing Minecraft claim systems.

It does not create its own claims. Instead, it works on top of supported region plugins and changes whether selected player-owned regions are protected or raidable depending on owner presence.

## How it works

A managed claim can be in one of three states:

- **OPEN** — the raid window is active.
- **GRACE** — an owner-presence transition is waiting for the configured delay.
- **PROTECTED** — ClaimShift protection is active.

ClaimShift supports two presence policies:

### `online-open`

```text
owner online        -> OPEN
last owner leaves   -> GRACE
delay expires       -> PROTECTED
```

Useful for servers that want to prevent offline raiding while still allowing players to defend their bases while they are online.

### `offline-open`

```text
owner online        -> PROTECTED
last owner leaves   -> GRACE
delay expires       -> OPEN
```

Useful for servers where claims should become raidable after their owners have been offline for a configured period.

The delay is configurable.

## WorldGuard

WorldGuard is the primary claim integration.

In `dynamic-passthrough` mode, ClaimShift can temporarily open a managed WorldGuard region by changing the raid-access layer it owns, then restore the exact original state when the region becomes protected again.

ClaimShift does not need to manage every region on the server.

A region can be explicitly enabled:

```text
/rg flag <region> claimshift-dynamic allow
```

Or explicitly kept static:

```text
/rg flag <region> claimshift-dynamic deny
```

This is useful for spawn, shops, event areas, staff regions and other administrative zones that should always keep their normal WorldGuard behavior.

Fresh installations use an opt-in approach by default, so existing regions are not automatically converted into dynamic claims.

## Region selection

WorldGuard regions can also be selected through `config.yml`.

Example:

```yaml
integration:
  provider: auto

  worldguard:
    mode: dynamic-passthrough
    manage-all-owned-regions: false
    manage-existing-passthrough-regions: false
    included-regions: []
    excluded-regions:
      - __global__
```

If most owned regions on a server are player claims, `manage-all-owned-regions` can be enabled and administrative regions can be excluded individually or by pattern.

Per-region `claimshift-dynamic allow` / `deny` flags take priority over general selection rules.

Ownerless administrative regions are not dynamically managed.

## Protection rules

Protection behavior is configured in `rules.yml`.

Available action groups include:

- block breaking
- block placing
- containers
- container automation
- interactions
- entity damage
- entity interaction
- entity grief
- hanging entities
- buckets
- explosions
- pistons
- fluids
- fire and fire spread

Example:

```yaml
protection:
  enabled: true
  presence-policy: online-open
  offline-delay: 10m
  protect-unknown-offline-owners: true
  trusted-players-bypass: true
```

Each action can be enabled or disabled independently.

For automation such as pistons, fluids, fire and hoppers, ClaimShift tries to preserve normal behavior inside the same protected claim while blocking movement across protected boundaries.

## Multiple owners

Claims with multiple effective owners are supported.

The offline transition starts only after the last effective owner leaves. If at least one effective owner remains online, the claim stays in the policy's online state.

WorldGuard ownership inheritance is taken into account where applicable.

## Runtime safety

Dynamic WorldGuard state is treated as temporary runtime state.

Before ClaimShift changes a managed region, it records the original value it needs to restore. Recovery metadata is stored in:

```text
plugins/ClaimShift/runtime-worldguard.yml
```

On normal shutdown, provider reload, or startup after an interrupted run, ClaimShift attempts to restore the original WorldGuard state safely.

Do not edit `runtime-worldguard.yml` while the server is running.

If the server is interrupted while a ClaimShift-managed region is temporarily open, start the server with ClaimShift installed again before removing the plugin or manually rewriting those managed WorldGuard regions.

## Configuration

ClaimShift uses three main configuration files:

- `config.yml` — integrations, region selection, language and diagnostics
- `rules.yml` — presence policy, delays and protection behavior
- `messages.yml` — message theme, prefix and custom message overrides

Configuration keys remain stable and are not translated.

### Localization

ClaimShift includes built-in localization support.

Configuration comments and player/admin messages can use different locales. Changing the selected language does not reset unrelated configured values.

`messages.yml` stores custom overrides, while untouched stock messages come from the selected bundled locale.

### MiniMessage

User-facing messages use Adventure MiniMessage.

The default theme exposes tags such as:

- `<primary>`
- `<secondary>`
- `<success>`
- `<warning>`
- `<error>`
- `<muted>`

HEX colors, prefix formatting and message overrides can be customized.

## Commands

```text
/claimshift help
/claimshift info
/claimshift inspect
/claimshift sync
/claimshift reload
/claimshift language <locale> [config|messages|both]
```

Alias:

```text
/cshift
```

Useful commands for testing and administration:

- `/claimshift info` — shows current runtime/provider information
- `/claimshift inspect` — inspects the ClaimShift state at your current location
- `/claimshift sync` — requests immediate provider reconciliation
- `/claimshift reload` — safely validates and reloads configuration

## Permissions

- `claimshift.admin`
- `claimshift.reload`
- `claimshift.sync`
- `claimshift.info`
- `claimshift.inspect`
- `claimshift.language`
- `claimshift.bypass`

`claimshift.admin` intentionally does **not** grant `claimshift.bypass`, allowing administrators to test ClaimShift protection without silently bypassing it.

## Safe reload

`/claimshift reload` validates candidate configuration before replacing the active runtime configuration.

If a duration, provider setting, locale, MiniMessage string or another validated value is invalid, the reload is rejected and the previous working configuration remains active.

## Supported server software

ClaimShift targets the modern Paper ecosystem:

- Paper
- Purpur
- Leaf
- Folia

Spigot is intentionally unsupported.

Provider compatibility also depends on the installed claim plugin supporting the same server environment.

## Building from source

ClaimShift uses Gradle Kotlin DSL.

### Requirements

- a compatible JDK
- network access for dependency resolution on the first build

The Java toolchain and dependency versions used by the project are defined in `build.gradle.kts`.

### Windows

```bat
gradlew.bat clean build
```

### Linux / macOS

```bash
./gradlew clean build
```

The compiled plugin JAR is written to:

```text
build/libs/
```

In IntelliJ IDEA, the same build can be run through:

```text
Gradle -> Tasks -> build -> build
```

The repository also contains a GitHub Actions workflow that builds the project and runs tests automatically.

Server-provided dependencies such as supported claim APIs are not bundled into the ClaimShift JAR.

## Project structure

```text
src/main/java/          Plugin source code
src/main/resources/     plugin.yml, configuration and bundled locales
src/test/               Unit and regression tests
docs/                   Architecture, compatibility and manual testing notes
.github/                 CI workflow and issue templates
build.gradle.kts        Build configuration and dependency definitions
CONTRIBUTING.md          Contribution guidelines
LICENSE                  ClaimShift Software License
```

### Main architecture

ClaimShift keeps provider-specific claim discovery separate from protection policy.

The general runtime flow is:

```text
Claim provider
    -> ClaimSnapshot
    -> PresenceService
    -> ClaimStateService
    -> ProtectionService
    -> provider reconciliation / event protection
```

This allows claim-provider integration, owner presence, state calculation and protection rules to evolve independently.

More implementation details are available in:

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/COMPATIBILITY.md`](docs/COMPATIBILITY.md)
- [`docs/TESTING.md`](docs/TESTING.md)

## Development guidelines

When changing ClaimShift:

- preserve the separation between claim providers and protection logic
- keep WorldGuard recovery behavior safe
- do not assume one universal main thread; keep Folia compatibility in mind
- keep configuration keys stable
- do not overwrite genuinely customized messages during localization changes
- add or update tests for state-machine and migration behavior
- update manual testing notes when behavior changes
- avoid NMS unless there is a strong reason
- do not add Spigot-specific compatibility code
- do not commit IDE files, build output, credentials, tokens or private server data

Before submitting a behavior change, run the automated test suite and follow the relevant cases from [`docs/TESTING.md`](docs/TESTING.md).

## Contributing

Bug reports, documentation fixes, translations, compatibility fixes and focused code contributions are welcome.

See [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a pull request.

When reporting a bug, include:

```text
/claimshift info
/claimshift inspect
```

when relevant, together with the claim provider, reproduction steps, relevant configuration and console output.

Remove passwords, tokens, private keys, database credentials and other secrets before publishing logs or configuration files.

## License

ClaimShift is **source-available**, not OSI open source.

The license allows official server use, configuration, translations, source inspection and contribution-focused development while restricting unauthorized redistribution, rebranding, resale and publication of modified or derivative builds.

See [`LICENSE`](LICENSE) for the complete terms.
