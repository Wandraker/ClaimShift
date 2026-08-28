# ClaimShift compatibility

## Runtime baseline

- Minecraft: 26.2
- Java: 25+
- Paper: supported
- Purpur: supported
- Leaf: supported
- Folia: supported by ClaimShift's scheduler model; provider/plugin compatibility is still required
- Spigot: unsupported

## Providers

| Provider | Mode | Dynamic raid window | Notes |
| --- | --- | --- | --- |
| WorldGuard 7.x | `dynamic-passthrough` | Yes | Primary integration; supports both presence policies, Smart Presence, per-region policy/delays/raid overrides and crash-safe passthrough restoration. |
| WorldGuard 7.x | `overlay` | No | ClaimShift only adds protection denials and does not change region flags. |
| Lands 7.x | `overlay` | Provider-dependent | ClaimShift does not rewrite Lands roles/permissions. |

ClaimShift compiles against WorldGuard/WorldEdit and Lands APIs as server-provided compile-only dependencies. Exact dependency pins live in `build.gradle.kts` rather than being duplicated throughout user-facing documentation.

## WorldGuard selection

Dynamic management applies only to physical non-global regions with at least one player owner. `manage-all-owned-regions` defaults to `false`, so regions that already existed before ClaimShift first observed the world stay legacy/static. With `auto-manage-new-regions: true`, eligible player-owned regions created afterwards are managed automatically. Explicit `claimshift-dynamic allow/deny` and include/exclude selectors remain available for administrator control.

- Ownerless admin regions are ignored automatically.
- `claimshift-dynamic: deny` forces a region static.
- `claimshift-dynamic: allow` forces an owned region dynamic, including an explicitly opted-in region that already had `passthrough: ALLOW`.
- Without an explicit per-region flag, `manage-all-owned-regions`, `included-regions`, and `excluded-regions` decide selection.
- Existing `passthrough: ALLOW` regions remain unmanaged by default unless `manage-existing-passthrough-regions` is enabled.

The custom flags are registered during plugin load, as required by WorldGuard's flag registry lifecycle.

### Per-region overrides

Managed WorldGuard regions may override global behavior with:

```text
/rg flag <region> claimshift-policy online-open
/rg flag <region> claimshift-policy offline-open
/rg flag <region> claimshift-active-delay 5m
/rg flag <region> claimshift-inactive-delay 1h
/rg flag <region> claimshift-raids allow
/rg flag <region> claimshift-raids deny
```

The region still has to be dynamically managed; a policy override does not implicitly opt an otherwise static region into ClaimShift. The older `claimshift-delay` flag is still read as a compatibility alias for the inactive-owner delay.

## Overlapping regions

ClaimShift mirrors WorldGuard's highest-priority physical-region selection at a location. Administrators should still test complex overlap layouts because unrelated WorldGuard flags can remain restrictive even when ClaimShift opens a region through passthrough.

## Presence policies

`online-open`:

- first active owner appears -> optional `owner-active` GRACE while the claim stays protected
- `owner-active` expires -> OPEN
- last active owner stops counting as present -> optional `owner-inactive` GRACE while the claim stays open
- `owner-inactive` expires -> PROTECTED

`offline-open`:

- active owner present -> PROTECTED
- last active owner stops counting as present -> `owner-inactive` GRACE, still protected
- `owner-inactive` expires -> OPEN
- an active owner returns to an already OPEN claim -> `owner-active` GRACE, still open
- `owner-active` expires -> PROTECTED

Fresh installations use `offline-open` with `owner-inactive: 1h` and `owner-active: 5m`. Either delay accepts `0` or `0s` for an immediate transition. Existing installations preserve their prior policy/delay behavior during migration.

Unknown absence timestamps after restart fail closed by default with `protect-unknown-offline-owners: true`.

## Smart Presence

Smart Presence is built into ClaimShift and enabled by default.

It can treat a connected owner as inactive after idle time and can ignore simple periodic low-frequency keep-alive actions. It is deliberately conservative and is not advertised as a complete bot detector.

### Optional AFK integrations

| Plugin | Integration | Required? | Notes |
| --- | --- | --- | --- |
| CMI | AFK state | No | Loaded through reflection when installed/enabled. |
| EssentialsX | AFK state | No | Loaded through reflection when installed/enabled. |

ClaimShift does not bundle either plugin API. External AFK checks are sampled on the player's entity scheduler and cached so WorldGuard/global reconciliation does not synchronously call player-oriented APIs across Folia regions.

If an optional bridge cannot link, ClaimShift continues with its own idle/pattern presence logic.

## Raid sessions

Raid sessions are disabled by default. When enabled globally or by the WorldGuard `claimshift-raids: allow` flag, an already-OPEN claim can remain open while qualifying raid activity continues.

Raid sessions are runtime-only and intentionally do not survive a server restart.

## Dry-run upgrades

A brand-new installation starts with dry-run enabled so administrators can inspect decisions safely before ClaimShift changes WorldGuard state.

Existing installations upgrading from older config schemas are migrated with dry-run disabled. An update therefore does not unexpectedly stop an already-running ClaimShift deployment from enforcing its configured behavior.

## bStats

bStats is optional and can be disabled in ClaimShift configuration. The global bStats server-owner opt-out is also respected.

CMI, EssentialsX, WorldGuard, Lands and bStats remain independent systems; ClaimShift does not send player names, UUIDs, claim names or server addresses in its custom bStats charts.
