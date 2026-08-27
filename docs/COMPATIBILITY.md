# ClaimShift compatibility

## Runtime baseline

- Minecraft: 26.2
- Java: 25+
- Paper: supported
- Purpur: supported
- Leaf: supported
- Folia: supported by ClaimShift's scheduler model; provider compatibility is still required
- Spigot: unsupported

## Providers

| Provider | Mode | Dynamic raid window | Notes |
| --- | --- | --- | --- |
| WorldGuard 7.x | `dynamic-passthrough` | Yes | Primary integration; supports both `online-open` and `offline-open` presence policies. |
| WorldGuard 7.x | `overlay` | No | ClaimShift only adds protection denials and does not change region flags. |
| Lands 7.x | `overlay` | Provider-dependent | ClaimShift does not rewrite Lands roles/permissions. |

ClaimShift compiles against WorldGuard 7.0.18, WorldEdit 7.4.4, and LandsAPI 7.25.4. These APIs are compile-only and remain server-provided.

## WorldGuard selection

Dynamic management applies only to physical non-global regions with at least one effective player owner. Fresh installations are opt-in: `manage-all-owned-regions` defaults to `false`, so existing regions stay static until selected explicitly.

- Ownerless admin regions are ignored automatically.
- `claimshift-dynamic: deny` forces a region static.
- `claimshift-dynamic: allow` forces an owned region dynamic, including an explicitly opted-in region that already had `passthrough: ALLOW`.
- Without an explicit per-region flag, `manage-all-owned-regions`, `included-regions`, and `excluded-regions` decide selection. Set `manage-all-owned-regions: true` only when bulk management is desired.
- Existing `passthrough: ALLOW` regions remain unmanaged by default unless `manage-existing-passthrough-regions` is enabled.

The custom `claimshift-dynamic` flag is registered during plugin load, as required by WorldGuard's flag registry lifecycle.

## Overlapping regions

ClaimShift mirrors WorldGuard's highest-priority physical-region selection at a location. Administrators should still test complex overlap layouts because unrelated WorldGuard flags can remain restrictive even when ClaimShift opens a region through passthrough.

## Presence policies

`online-open`:

- any effective owner online -> OPEN
- last owner disconnects -> GRACE, still open
- `offline-delay` expires -> PROTECTED

`offline-open`:

- any effective owner online -> PROTECTED
- last owner disconnects -> GRACE, still protected
- `offline-delay` expires -> OPEN

Unknown offline timestamps after restart fail closed by default with `protect-unknown-offline-owners: true`.
