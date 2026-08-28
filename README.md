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
active owner present       -> OPEN
last active owner is lost  -> GRACE
delay expires              -> PROTECTED
```

Useful for servers that want to prevent offline/AFK raiding while still allowing players to defend their bases while they are actively present.

### `offline-open`

```text
active owner present       -> PROTECTED
last active owner is lost  -> GRACE
delay expires              -> OPEN
```

Useful for servers where claims should become raidable after their owners stop being actively present for a configured period.

Both transition directions have independent delays. Fresh installations default to `offline-open`: a claim stays protected for one hour after the last active owner disappears, and protection takes five minutes to return after an active owner comes back to a claim that is already OPEN. Either delay can be disabled with `0` or `0s`.

With Smart Presence enabled, an owner may stop counting as active because of AFK/idle detection even while the account remains connected.

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

Regions that already exist when ClaimShift first observes a loaded world are recorded as legacy/static and keep their normal WorldGuard behavior. Eligible player-owned regions first created while ClaimShift is running are automatically managed by default. Older regions can still be enabled explicitly with `claimshift-dynamic allow`, and any region can be forced static with `claimshift-dynamic deny`.

## Region selection

WorldGuard regions can also be selected through `config.yml`.

Example:

```yaml
integration:
  provider: auto

  worldguard:
    mode: dynamic-passthrough
    manage-all-owned-regions: false
    auto-manage-new-regions: true
    manage-existing-passthrough-regions: false
    included-regions: []
    excluded-regions:
      - __global__
```

`auto-manage-new-regions` lets ClaimShift distinguish migration safety from normal day-to-day use: old regions stay static, while newly created eligible player claims become dynamic automatically. If most existing owned regions should also become dynamic, `manage-all-owned-regions` can be enabled and administrative regions can be excluded individually or by pattern.

Per-region `claimshift-dynamic allow` / `deny` flags take priority over automatic lifecycle classification and general selection rules.

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
  presence-policy: offline-open
  transition-delays:
    owner-active: 5m
    owner-inactive: 1h
  protect-unknown-offline-owners: true
  trusted-players-bypass: true
```

Each action can be enabled or disabled independently.

For automation such as pistons, fluids, fire and hoppers, ClaimShift tries to preserve normal behavior inside the same protected claim while blocking movement across protected boundaries.

## Multiple owners

Claims with multiple owners are supported.

The offline transition starts only after the last **active** owner stops counting as present. A connected owner can become inactive through Smart Presence without actually disconnecting. If at least one active owner remains, the claim stays in the policy's online state.

WorldGuard ownership inheritance is taken into account where applicable.

## Smart Presence and AFK abuse

A connected account does not have to count as active forever. Smart Presence is enabled by default and can stop simple AFK machines or periodic keep-alive binds from holding a claim in its online state indefinitely.

It uses several coarse signals:

- idle time since meaningful player activity
- meaningful movement distance instead of tiny position jitter or camera movement
- repeated low-frequency activity patterns, such as the same action every few minutes
- optional AFK state from CMI and EssentialsX when either plugin is installed

Suspicious periodic activity is not treated as cheating and ClaimShift does not punish the player. That activity simply stops refreshing the active-presence timer. Normal gameplay activity can immediately make the owner active again.

Optional stronger controls are available but disabled by default:

- anti-relog qualification after quick reconnects
- maximum continuous presence time

ClaimShift is not a full bot detector. A sufficiently sophisticated automation system may still resemble real gameplay, so server anti-cheat and server rules remain separate layers.

## Raid sessions

Raid sessions are optional and disabled by default. When enabled, qualifying activity against a claim that is already OPEN can create a temporary raid lock. The lock keeps the claim open while the raid is active so protection cannot suddenly return in the middle of an ongoing attack.

A raid session has:

- an inactivity timeout
- an optional hard maximum duration
- optional extension when qualifying activity continues
- configurable trigger action types
- owner notifications when the session starts or ends

WorldGuard regions can override the global raid setting individually.

```text
/rg flag <region> claimshift-raids allow
/rg flag <region> claimshift-raids deny
```

Presence policy and both transition delays can also be overridden per WorldGuard region:

```text
/rg flag <region> claimshift-policy offline-open
/rg flag <region> claimshift-active-delay 5m
/rg flag <region> claimshift-inactive-delay 1h
```

The older `claimshift-delay` flag remains accepted as a compatibility alias for the inactive-owner delay.

## First-install dry-run

A brand-new ClaimShift installation starts in **dry-run mode**. This is a safety preview: ClaimShift calculates which regions would be OPEN, GRACE or PROTECTED, but it does not change WorldGuard state and does not deny player/system actions.

Operators with the diagnostics permission receive a title/chat reminder while dry-run is enabled. When the configuration looks correct, disable it with:

```text
/claimshift dryrun off
```

Upgrading an existing ClaimShift installation does **not** automatically enable dry-run. If an administrator enables it later, the reminder returns until it is disabled again.

Dry-run transitions can be logged to the console without exposing player names or region contents.

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

Bundled locales:

- `en_US` — English
- `ru_RU` — Russian
- `de_DE` — German
- `es_ES` — Spanish
- `fr_FR` — French
- `pl_PL` — Polish
- `pt_BR` — Brazilian Portuguese
- `uk_UA` — Ukrainian
- `zh_CN` — Simplified Chinese

`messages.yml` stores custom overrides, while untouched stock messages come from the selected bundled locale.

Executable command names, subcommands and scope tokens are intentionally **not translated**. Localization only changes human-readable descriptions, labels, comments and messages, so documented commands remain stable in every locale.

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
/claimshift dryrun <on|off|status>
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
- `/claimshift dryrun` — checks or changes the safe diagnostics preview mode

## Permissions

- `claimshift.admin`
- `claimshift.reload`
- `claimshift.sync`
- `claimshift.info`
- `claimshift.inspect`
- `claimshift.language`
- `claimshift.dryrun`
- `claimshift.bypass`

`claimshift.admin` intentionally does **not** grant `claimshift.bypass`, allowing administrators to test ClaimShift protection without silently bypassing it.


## Anonymous metrics

ClaimShift can send anonymous usage statistics through [bStats](https://bstats.org/).

The integration reports the normal bStats platform/plugin statistics plus a small set of ClaimShift configuration charts:

- presence policy
- active claim provider
- WorldGuard integration mode
- selected message locale
- Smart Presence state
- periodic-pattern detection state
- global raid-session state

ClaimShift does not add usernames, player UUIDs, region names or server addresses to its custom charts.

Plugin-side metrics can be disabled in `config.yml`:

```yaml
metrics:
  enabled: false
```

The server-wide bStats opt-out in `plugins/bStats/config.yml` is also always respected.

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
    -> PresenceService / Smart Presence
    -> ClaimStateService
    -> optional RaidSessionService
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
