# ClaimShift 1.1.1 manual test matrix

Use a disposable local server and two non-OP player accounts where possible.

Recommended stack:

- Minecraft 26.2
- Java 25
- Leaf/Paper/Purpur 26.2
- WorldEdit 7.4.x
- WorldGuard 7.0.18+
- ClaimShift 1.1.1

## 1. Startup

Start the server with ClaimShift and WorldGuard installed.

Expected:

- ClaimShift enables without configuration errors.
- `/claimshift info` shows WorldGuard and the configured presence policy.
- WorldGuard accepts the custom `claimshift-dynamic` flag.

## 2. Create and opt in a player-owned test region

Create a normal WorldGuard region with membership-based protection and add player A as owner. Avoid explicit blanket `build: deny` or similar flags for the basic raid-window test.

Run `/claimshift inspect` while standing inside it.

Expected with a fresh default config:

- the region is found;
- dynamic access: No;
- state: STATIC / UNMANAGED.

Opt the region in:

```text
/rg flag <region> claimshift-dynamic allow
```

Run `/claimshift sync` and inspect again. Expected:

- dynamic access: Yes;
- effective owners: at least 1.

## 3. `online-open`

Set:

```yaml
protection:
  presence-policy: online-open
  offline-delay: 1m
```

Reload ClaimShift.

With owner A online:

- `/claimshift inspect` reports OPEN.
- a non-owner attacker can use actions that WorldGuard membership would normally block, subject to unrelated explicit WorldGuard flags.
- the region's runtime passthrough should be ALLOW while OPEN.

Disconnect owner A.

Immediately after quit:

- state is GRACE.
- the raid window remains open.
- remaining time counts down.

After one minute:

- state becomes PROTECTED.
- the original passthrough value is restored.
- attacker actions covered by ClaimShift rules are denied.

Reconnect owner A:

- state returns to OPEN immediately.

## 4. `offline-open`

Change only:

```yaml
protection:
  presence-policy: offline-open
```

Reload ClaimShift.

With owner A online:

- state is PROTECTED.
- normal WorldGuard protection remains active.

Disconnect owner A:

- state becomes GRACE.
- GRACE remains protected in this policy.

After `offline-delay`:

- state becomes OPEN.
- runtime passthrough becomes ALLOW.
- the attacker can raid according to the configured action rules and remaining WorldGuard flags.

Reconnect owner A:

- protection returns immediately.

## 5. Multiple owners

Add player B as a second owner.

For both policies, verify that the offline transition starts only after the last effective owner disconnects. One owner remaining online must keep the claim in the policy's online state.

## 6. Static administrative region

Create or choose a staff-owned spawn/test region. Set:

```text
/rg flag <region> claimshift-dynamic deny
```

Run `/claimshift sync` and `/claimshift inspect` inside it.

Expected:

- inspection still finds the region;
- dynamic access is No;
- state is shown as STATIC / UNMANAGED;
- ClaimShift never changes that region's passthrough when owners join or quit.

Clear the flag, synchronize, and verify that normal config selection takes over again.

Then set:

```text
/rg flag <region> claimshift-dynamic allow
```

Expected for an owned physical region: ClaimShift manages it even when `manage-all-owned-regions: false` or broad selectors would otherwise leave it out.

## 7. Config selector safety

Test:

```yaml
integration:
  worldguard:
    manage-all-owned-regions: false
    included-regions:
      - raid_*
    excluded-regions:
      - raid_admin_*
```

Expected:

- only matching owned regions are dynamically managed;
- excluded patterns win over normal include selection;
- explicit `claimshift-dynamic allow/deny` per-region overrides take precedence over broad selectors.

## 8. Existing passthrough

Create a region that already has `passthrough: allow` before ClaimShift takes control.

With `manage-existing-passthrough-regions: false`, ClaimShift should leave it unmanaged. Enable the setting only when intentionally testing ClaimShift ownership of pre-existing passthrough regions.

## 9. Safe reload

Introduce an invalid duration:

```yaml
protection:
  offline-delay: definitely-not-a-duration
```

Run `/claimshift reload`.

Expected:

- reload fails with a readable error;
- the previous runtime configuration remains active;
- the plugin is not disabled.

Restore a valid value and reload again.

## 10. Rules migration

Start once with a 1.0.x-style rules file containing:

```yaml
config-version: 1
protection:
  activation-delay: 45s
```

Expected after startup/reload:

- rules schema becomes 2;
- `presence-policy: online-open` is added;
- `offline-delay: 45s` preserves the old value;
- `activation-delay` is removed.

## 11. Restart / unknown offline owner

With `protect-unknown-offline-owners: true`, stop the server while an owner is offline and restart before ClaimShift has a current-session quit timestamp.

Expected for both policies: the claim fails closed as protected until ClaimShift observes owner presence again.

## 12. Crash recovery

While a region is OPEN and ClaimShift owns a temporary passthrough override, terminate the process without a normal plugin shutdown. Restart with ClaimShift still installed.

Expected: `runtime-worldguard.yml` recovery restores the captured original WorldGuard passthrough before normal dynamic reconciliation resumes.

## 13. Multiple controlled regions and persistence

Create two owned regions in the same world. Keep one OPEN while the other transitions to protection and run `/claimshift sync`.

Expected: saving/restoring one region never persists another region's temporary OPEN `passthrough: ALLOW` as administrator configuration.

## 14. Boundary automation

With protection active, test pistons, fluid/fire spread, and hopper movement:

- activity entirely inside the same protected claim should remain possible where Minecraft/WorldGuard allows it;
- movement across a protected claim boundary should be blocked when the corresponding action is enabled.

## Bug report bundle

If any step fails, attach:

```text
/claimshift info
/claimshift inspect
```

plus the relevant console lines, `config.yml`, `rules.yml`, and a description of the WorldGuard region/flags. Remove secrets before posting anything publicly.
