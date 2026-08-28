# ClaimShift manual test matrix

Use a disposable local server and two non-OP player accounts where possible.

Recommended stack:

- Minecraft 26.2
- Java 25
- Leaf/Paper/Purpur 26.2
- WorldEdit 7.4.x
- WorldGuard 7.0.18+
- current ClaimShift build

## 1. Startup

Start the server with ClaimShift and WorldGuard installed.

Expected on a brand-new data folder:

- ClaimShift enables without configuration errors.
- `/claimshift info` shows WorldGuard, the configured presence policy, Smart Presence and dry-run status.
- WorldGuard accepts the ClaimShift custom flags.
- dry-run is enabled and an operator receives the diagnostics title/chat notice on join.
- no WorldGuard passthrough value is changed while dry-run is active.

Run `/claimshift dryrun status`, inspect the intended regions, then run `/claimshift dryrun off` before the enforcement tests below.

## 2. Create a player-owned test region

After ClaimShift has already started, create a normal WorldGuard region with membership-based protection and add player A as owner. Avoid explicit blanket `build: deny` or similar flags for the basic raid-window test.

After a normal `/rg claim` / `/rg define` workflow, wait about a second for the command-triggered reconciliation (or use `/claimshift sync`), then run `/claimshift inspect` while standing inside it.

Expected with a fresh default config:

- the region is found;
- dynamic access: Yes;
- management source reports that it was automatically classified as a new region;
- active owners: at least 1.

For comparison, an owned region that already existed before ClaimShift first observed the world should remain STATIC / UNMANAGED until explicitly enabled:

```text
/rg flag <old-region> claimshift-dynamic allow
```

## 3. `online-open`

Set:

```yaml
protection:
  presence-policy: online-open
  transition-delays:
    owner-active: 10s
    owner-inactive: 1m
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

- state enters GRACE while protection remains active for `owner-active`;
- after `owner-active`, state becomes OPEN again.

Set `owner-active: 0` and verify the reconnect transition becomes immediate.

## 4. `offline-open`

Set:

```yaml
protection:
  presence-policy: offline-open
  transition-delays:
    owner-active: 5m
    owner-inactive: 1h
```

Reload ClaimShift.

With owner A online:

- state is PROTECTED.
- normal WorldGuard protection remains active.

Disconnect owner A:

- state becomes GRACE.
- GRACE remains protected in this policy.

After `owner-inactive`:

- state becomes OPEN.
- runtime passthrough becomes ALLOW.
- the attacker can raid according to the configured action rules and remaining WorldGuard flags.

Reconnect owner A after the region is already OPEN:

- state becomes GRACE but remains OPEN;
- protection does not return instantly;
- after `owner-active`, the original passthrough is restored and state becomes PROTECTED.

Reconnect owner A before `owner-inactive` expires, while the region never actually became OPEN. Expected: it stays PROTECTED immediately; the reverse delay must not create a new vulnerability window.

Set either transition delay to `0` and `0s` in separate tests and verify that direction becomes immediate.

## 5. Multiple owners

Add player B as a second owner.

For both policies, verify that the offline transition starts only after the last active owner stops counting as present. One owner remaining online must keep the claim in the policy's online state.

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
    auto-manage-new-regions: false
    included-regions:
      - raid_*
    excluded-regions:
      - raid_admin_*
```

Expected:

- only matching owned regions are dynamically managed;
- excluded patterns win over normal include selection;
- explicit `claimshift-dynamic allow/deny` per-region overrides take precedence over broad selectors.

## 8. Existing-region migration and automatic new regions

Start with WorldGuard regions already present before enabling this ClaimShift build. Keep:

```yaml
integration:
  worldguard:
    manage-all-owned-regions: false
    auto-manage-new-regions: true
```

Expected:

- regions already present when ClaimShift first observes the loaded world are reported by `/claimshift inspect` as legacy/static and remain unmanaged unless explicitly included or given `claimshift-dynamic allow`;
- create a new owned physical region while ClaimShift is running (for example with WorldGuard claim/create workflow); it becomes dynamically managed automatically after reconciliation without needing `claimshift-dynamic allow`;
- restart the server and verify that the new automatically managed region remains dynamic;
- set `claimshift-dynamic deny` on that new region and verify it becomes static;
- delete an automatically managed region, synchronize, recreate the same region name while ClaimShift is still running, and verify the recreated region is treated as newly created;
- load a world that was not loaded during ClaimShift startup and verify unknown pre-existing regions in that world are baselined as legacy/static rather than treated as newly created.

The persistent classification lives in internal `region-registry.yml`; it is not intended as administrator configuration.

## 9. Existing passthrough

Create a region that already has `passthrough: allow` before ClaimShift takes control.

With `manage-existing-passthrough-regions: false`, ClaimShift should leave it unmanaged. Enable the setting only when intentionally testing ClaimShift ownership of pre-existing passthrough regions.

When intentionally managed under `offline-open`, first let the region become OPEN, then reconnect/reactivate the owner with a non-zero `owner-active` delay. Expected: the region remains logically OPEN for the configured recovery GRACE even though its administrator-owned passthrough was already ALLOW, then ClaimShift event protection returns after the delay. This verifies that logical runtime state is not confused with crash-recovery metadata.

## 10. Safe reload

Introduce an invalid duration:

```yaml
protection:
  transition-delays:
    owner-inactive: definitely-not-a-duration
```

Run `/claimshift reload`.

Expected:

- reload fails with a readable error;
- the previous runtime configuration remains active;
- the plugin is not disabled.

Restore a valid value and reload again.

## 11. Rules migration

Start once with a 1.0.x-style rules file containing:

```yaml
config-version: 1
protection:
  activation-delay: 45s
```

Expected after startup/reload:

- rules schema becomes 4;
- `presence-policy: online-open` is preserved for the upgraded installation;
- Smart Presence defaults are merged while anti-relog, max-session and raid sessions remain disabled by default;
- the old `45s` delay is preserved as `transition-delays.owner-inactive: 45s`;
- the newly introduced reverse transition is `transition-delays.owner-active: 0s` so upgrading does not silently add a new delay;
- `activation-delay` and the intermediate `offline-delay` key are removed.

## 12. Restart / unknown offline owner

With `protect-unknown-offline-owners: true`, stop the server while an owner is offline and restart before ClaimShift has a current-session quit timestamp.

Expected for both policies: the claim fails closed as protected until ClaimShift observes owner presence again.

## 13. Crash recovery

While a region is OPEN and ClaimShift owns a temporary passthrough override, terminate the process without a normal plugin shutdown. Restart with ClaimShift still installed.

Expected: `runtime-worldguard.yml` recovery restores the captured original WorldGuard passthrough before normal dynamic reconciliation resumes.

## 14. Multiple controlled regions and persistence

Create two owned regions in the same world. Keep one OPEN while the other transitions to protection and run `/claimshift sync`.

Expected: saving/restoring one region never persists another region's temporary OPEN `passthrough: ALLOW` as administrator configuration.

## 15. Boundary automation

With protection active, test pistons, fluid/fire spread, and hopper movement:

- activity entirely inside the same protected claim should remain possible where Minecraft/WorldGuard allows it;
- movement across a protected claim boundary should be blocked when the corresponding action is enabled.

## 16. Localization and command stability

Switch through several bundled locales with `/claimshift language`.

Expected:

- messages and configuration comments change language;
- command literals remain `/claimshift help`, `info`, `inspect`, `sync`, `reload`, `language`;
- scope tokens remain `config`, `messages`, `both`;
- tab completion returns stable command/scope tokens rather than translated equivalents.

## 17. bStats lifecycle

With `metrics.enabled: true`, start the server and verify ClaimShift enables normally with bStats available. Toggle it to `false`, run `/claimshift reload`, and verify `/claimshift info` shows metrics disabled without restarting the plugin. Re-enable it and reload again.

If the server-wide bStats configuration opts out, ClaimShift must respect that setting.


## 18. Fresh-install dry-run safety

Delete the ClaimShift data folder and start with a clean plugin installation.

Expected:

- generated `config.yml` contains `diagnostics.dry-run: true`;
- dynamic claims are discovered and `/claimshift inspect` reports calculated states;
- WorldGuard passthrough remains at its administrator-owned value;
- protected actions are not denied by ClaimShift;
- raid sessions do not start;
- an operator with `claimshift.dryrun` sees the title/chat reminder;
- `/claimshift dryrun off` persists `false`, reloads provider state and stops future notices.

Re-enable it with `/claimshift dryrun on` and verify the notice/preview behavior returns.

## 19. Upgrade does not force dry-run

Start from a pre-diagnostics `config.yml` using the older config schema and an otherwise working ClaimShift setup, then update.

Expected after migration:

- config schema is upgraded;
- `diagnostics.dry-run` is written as `false`;
- new diagnostics keys are merged;
- previously enforced dynamic behavior remains enforced instead of silently switching to preview-only mode.

## 20. Smart Presence idle transition

Use a short test configuration:

```yaml
presence:
  smart:
    enabled: true
    idle-timeout: 1m
    minimum-movement-distance: 3.0
    patterns:
      enabled: true
```

With an owner connected but inactive:

- initially `/claimshift inspect` shows one online owner and one active owner;
- after the idle timeout, online owners remains 1 but active owners becomes 0;
- the claim enters the same GRACE/offline path that a genuinely absent owner would use;
- meaningful activity makes the owner active again and reconciliation returns the claim to its online-policy state.

Turning only the camera or moving less than the configured anchor distance must not continuously refresh presence.

## 21. Periodic keep-alive pattern

Use a test-only short pattern interval/tolerance and repeatedly perform the exact same action at nearly identical intervals.

Expected:

- the first samples count as normal activity;
- once `minimum-samples` is reached and interval spread stays within tolerance, subsequent matching repetitions stop refreshing meaningful activity;
- the player is not kicked, banned, warned or otherwise punished;
- after the idle timeout, the owner becomes inactive despite the repeated keep-alive action;
- unrelated genuine activity can reactivate the owner.

Then vary the timing outside the configured tolerance and verify it is not classified as the same simple periodic pattern.

## 22. CMI / EssentialsX AFK bridge

Test separately with CMI and EssentialsX when available.

Expected:

- `/claimshift info` lists the linked external AFK source;
- marking the owner AFK eventually removes them from active owners while they remain connected;
- clearing AFK eventually restores active presence when normal activity requirements are satisfied;
- removing/not installing either plugin does not prevent ClaimShift startup;
- on Folia, no cross-region/thread-access exceptions appear from AFK integration.

## 23. Optional anti-relog

Enable anti-relog with short test values. Disconnect and reconnect inside the configured window.

Expected:

- the player is online but does not count as active during the qualification period;
- the claim does not instantly flip to the policy's online state just because of the quick reconnect;
- after qualification expires, the owner counts as active again;
- with anti-relog disabled, reconnect behavior returns to normal.

## 24. Optional maximum continuous presence

Enable the cap with a short test duration.

Expected:

- the owner initially counts as active;
- when the cap expires, the still-connected owner stops counting as active;
- normal activity does not bypass the hard cap for that continuous session;
- reconnecting starts a new session subject to any anti-relog rules;
- with the cap disabled, a genuinely active player has no hard session limit.

## 25. Per-region WorldGuard policy overrides

For an opted-in region test:

```text
/rg flag <region> claimshift-policy offline-open
/rg flag <region> claimshift-active-delay 15s
/rg flag <region> claimshift-inactive-delay 30s
/rg flag <region> claimshift-raids allow
```

Expected:

- `/claimshift inspect` reports the effective per-region policy and both delays;
- the global policy remains unchanged for other regions;
- clearing a flag returns that setting to the validated global value;
- clearing `claimshift-inactive-delay` and setting the older `claimshift-delay 45s` still overrides the inactive-owner delay for compatibility;
- `claimshift-raids deny` disables raid sessions for that region even if globally enabled;
- `claimshift-raids allow` can enable sessions for that region even if globally disabled.

## 26. Raid-session lifecycle

Enable raid sessions with short test values and configure a player-driven trigger action such as `block-break`.

Expected:

- no session exists merely because a claim is OPEN;
- a non-trusted player's qualifying action while OPEN starts the session;
- owners receive the start notification;
- changing owner presence while the session is active does not close the claim;
- qualifying activity extends the inactivity deadline when configured;
- inactivity ends the session and allows the normal presence-derived state to take over;
- the hard maximum duration ends the session even with continued activity;
- owners receive the end notification;
- changing current raid policy to disabled causes a running session to end on reconciliation;
- enabling dry-run ends active sessions and prevents new ones from starting;
- restarting the server does not resurrect an old raid session.

## 27. Dry-run transition logging

Enable:

```yaml
diagnostics:
  dry-run: true
  log-transitions: true
```

Expected:

- console previews appear when a selected region's calculated state/passthrough decision changes;
- identical repeated reconciliation does not spam the same preview continuously;
- the preview does not modify WorldGuard or expose player chat/command content.


## Bug report bundle

If any step fails, attach:

```text
/claimshift info
/claimshift inspect
```

plus the relevant console lines, `config.yml`, `rules.yml`, and a description of the WorldGuard region/flags. Remove secrets before posting anything publicly.
