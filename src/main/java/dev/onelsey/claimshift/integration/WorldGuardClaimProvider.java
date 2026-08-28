package dev.onelsey.claimshift.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.onelsey.claimshift.ClaimShiftPlugin;
import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.config.WorldGuardSettings;
import dev.onelsey.claimshift.model.ClaimSnapshot;
import dev.onelsey.claimshift.model.ClaimStatus;
import dev.onelsey.claimshift.protection.ClaimStateService;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class WorldGuardClaimProvider implements ClaimProvider {
    private record Mutation(
            RegionManager manager,
            WorldGuardStateStore.RegionKey key,
            ProtectedRegion region,
            StateFlag.State original,
            StateFlag.State desired,
            boolean restoring
    ) {
    }

    private final ClaimShiftPlugin plugin;
    private final ConfigurationService configuration;
    private final ClaimStateService states;
    private final String version;
    private final WorldGuardStateStore stateStore;
    private final WorldGuardRegionRegistry regionRegistry;
    private final AtomicBoolean reconcileQueued = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<WorldGuardStateStore.RegionKey, String> dryRunPreview = new HashMap<>();
    /**
     * Logical runtime OPEN state from the previous successful reconciliation.
     * This is intentionally separate from stateStore: a region whose original
     * passthrough is already ALLOW can still be logically OPEN/PROTECTED through
     * ClaimShift event protection even though no temporary flag override needs to
     * be persisted for crash recovery.
     */
    private final Set<WorldGuardStateStore.RegionKey> runtimeProjectedOpen = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<WorldGuardStateStore.RegionKey> dryRunProjectedOpen = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile ScheduledTask reconcileTask;
    private volatile ScheduledTask transitionWakeTask;
    private volatile ProviderDiagnostics diagnostics = ProviderDiagnostics.simple("starting");

    public WorldGuardClaimProvider(
            ClaimShiftPlugin plugin,
            ConfigurationService configuration,
            ClaimStateService states,
            WorldGuardRegionRegistry regionRegistry
    ) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.states = states;
        this.regionRegistry = regionRegistry;
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) {
            throw new IllegalStateException("WorldGuard is not enabled");
        }
        this.version = worldGuard.getPluginMeta().getVersion();
        this.stateStore = new WorldGuardStateStore(plugin);
        recoverStaleOverrides();
        if (regionRegistry.beginSessionBootstrap()) {
            bootstrapLoadedWorlds();
        }
        if (dynamicModeEnabled()) {
            // ProviderManager constructs us on the global-region scheduler, so the
            // first reconciliation can run immediately. This makes startup state
            // deterministic before /claimshift info or the first raid interaction.
            reconcileSafely();
        }
        startReconciler();
    }

    @Override public String id() { return "worldguard"; }
    @Override public String displayName() { return "WorldGuard"; }
    @Override public String version() { return version; }

    @Override
    public boolean available() {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        return worldGuard != null && worldGuard.isEnabled();
    }

    @Override
    public List<ClaimSnapshot> findClaims(Location location) {
        return findTopClaims(location, true);
    }

    @Override
    public List<ClaimSnapshot> findClaimsForInspection(Location location) {
        return findTopClaims(location, false);
    }

    private List<ClaimSnapshot> findTopClaims(Location location, boolean managedOnly) {
        World world = location.getWorld();
        if (world == null) {
            return List.of();
        }

        WorldGuardSettings settings = configuration.pluginSettings().worldGuard();
        ApplicableRegionSet applicable = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(location));

        // WorldGuard membership/build semantics only consider the highest-priority
        // physical regions at a point. Apply the same rule so ClaimShift never lets
        // a lower-priority claim decide protection where WorldGuard would ignore it.
        List<ProtectedRegion> physical = applicable.getRegions().stream()
                .filter(ProtectedRegion::isPhysicalArea)
                .filter(region -> !region.getId().equalsIgnoreCase("__global__"))
                .toList();
        if (physical.isEmpty()) {
            return List.of();
        }
        int highestPriority = physical.stream()
                .mapToInt(ProtectedRegion::getPriority)
                .max()
                .orElse(Integer.MIN_VALUE);

        return physical.stream()
                .filter(region -> region.getPriority() == highestPriority)
                .filter(region -> !managedOnly || isManageable(world, region, settings))
                .sorted(Comparator.comparingInt(ProtectedRegion::volume)
                        .thenComparing(ProtectedRegion::getId))
                .map(region -> snapshot(world, region))
                .toList();
    }

    @Override
    public boolean isDynamicallyManaged(ClaimSnapshot claim) {
        if (!configuration.ruleSettings().enabled()) {
            return false;
        }
        WorldGuardSettings settings = configuration.pluginSettings().worldGuard();
        if (settings.mode() != WorldGuardSettings.Mode.DYNAMIC_PASSTHROUGH) {
            return false;
        }
        World world = plugin.getServer().getWorld(claim.world());
        if (world == null) {
            return false;
        }
        RegionManager manager = regionManager(world);
        if (manager == null) {
            return false;
        }
        ProtectedRegion region = manager.getRegion(claim.name());
        return region != null && isManageable(world, region, settings);
    }

    @Override
    public ProviderDiagnostics diagnostics() {
        return diagnostics;
    }

    @Override
    public void requestReconcile() {
        if (closed.get() || (!dynamicModeEnabled() && stateStore.snapshot().isEmpty())) {
            return;
        }
        if (!reconcileQueued.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
            reconcileQueued.set(false);
            if (!closed.get()) {
                reconcileSafely();
            }
        });
    }

    @Override
    public void requestReconcileAfter(Duration delay) {
        if (closed.get() || !dynamicModeEnabled()) {
            return;
        }
        long millis = Math.max(1L, delay.toMillis());
        plugin.getServer().getAsyncScheduler().runDelayed(
                plugin,
                ignored -> requestReconcile(),
                millis,
                TimeUnit.MILLISECONDS
        );
    }


    @Override
    public void onWorldLoad(World world) {
        // A world loaded after startup may contain regions created while ClaimShift
        // could not observe that world. Unknown regions are therefore baselined as
        // legacy/static before normal reconciliation begins.
        bootstrapWorldRegistry(world);
        // If recovery metadata from an interrupted run exists, reconciliation treats
        // it as the captured original state and either restores it or resumes dynamic
        // control safely.
        requestReconcile();
    }

    @Override
    public void onWorldUnload(World world) {
        if (closed.get()) {
            return;
        }
        try {
            restoreWorldOverrides(world);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not restore WorldGuard state before unloading world '"
                    + world.getName() + "': " + rootMessage(exception));
        } finally {
            runtimeProjectedOpen.removeIf(key -> key.world().equals(world.getName()));
            dryRunProjectedOpen.removeIf(key -> key.world().equals(world.getName()));
        }
    }

    @Override
    public void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ScheduledTask task = reconcileTask;
        if (task != null) {
            task.cancel();
            reconcileTask = null;
        }
        cancelTransitionWake();
        try {
            restoreAllOverrides(true);
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not restore WorldGuard passthrough state: " + rootMessage(exception));
        } finally {
            runtimeProjectedOpen.clear();
            dryRunProjectedOpen.clear();
        }
    }

    private boolean dynamicModeEnabled() {
        return configuration.ruleSettings().enabled()
                && configuration.pluginSettings().worldGuard().mode() == WorldGuardSettings.Mode.DYNAMIC_PASSTHROUGH;
    }

    private void startReconciler() {
        if (!dynamicModeEnabled()) {
            diagnostics = ProviderDiagnostics.simple(configuration.ruleSettings().enabled() ? "overlay" : "disabled");
            return;
        }
        long periodMillis = Math.max(1000L, configuration.pluginSettings().worldGuard().reconcileInterval().toMillis());
        reconcileTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                ignored -> requestReconcile(),
                1L,
                periodMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private void reconcileSafely() {
        try {
            reconcile();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("WorldGuard reconciliation failed: " + rootMessage(throwable));
            if (configuration.pluginSettings().debug()) {
                plugin.getLogger().log(Level.WARNING, "WorldGuard reconciliation failure details", throwable);
            }
        }
    }

    private synchronized void reconcile() {
        if (!dynamicModeEnabled()) {
            restoreAllOverrides(true);
            dryRunPreview.clear();
            runtimeProjectedOpen.clear();
            dryRunProjectedOpen.clear();
            cancelTransitionWake();
            diagnostics = ProviderDiagnostics.simple(configuration.ruleSettings().enabled() ? "overlay" : "disabled");
            return;
        }
        if (configuration.pluginSettings().diagnostics().dryRun()) {
            restoreAllOverrides(true);
            runtimeProjectedOpen.clear();
            reconcileDryRun();
            return;
        }
        dryRunPreview.clear();
        dryRunProjectedOpen.clear();

        WorldGuardSettings settings = configuration.pluginSettings().worldGuard();
        int managed = 0;
        int open = 0;
        int grace = 0;
        int protectedCount = 0;
        int skippedPassthrough = 0;
        Duration nextTransition = null;
        Set<WorldGuardStateStore.RegionKey> discovered = new HashSet<>();
        List<Mutation> mutations = new ArrayList<>();

        for (World world : plugin.getServer().getWorlds()) {
            RegionManager manager = regionManager(world);
            if (manager == null) {
                continue;
            }
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (!selected(world, region, settings)) {
                    continue;
                }
                WorldGuardStateStore.RegionKey key = new WorldGuardStateStore.RegionKey(world.getName(), region.getId());
                if (!settings.manageExistingPassthroughRegions()
                        && dynamicOverrideState(region) != StateFlag.State.ALLOW
                        && !stateStore.contains(key)
                        && region.getFlag(Flags.PASSTHROUGH) == StateFlag.State.ALLOW) {
                    skippedPassthrough++;
                    continue;
                }

                managed++;
                discovered.add(key);
                ClaimStatus status = states.evaluate(snapshot(world, region));
                if (status.protectedNow()) runtimeProjectedOpen.remove(key);
                else runtimeProjectedOpen.add(key);
                switch (status.state()) {
                    case OPEN -> open++;
                    case GRACE -> {
                        grace++;
                        nextTransition = earlier(nextTransition, status.remaining());
                    }
                    case PROTECTED -> protectedCount++;
                }

                WorldGuardStateStore.OriginalState stored = stateStore.get(key);
                StateFlag.State original = stored == null ? region.getFlag(Flags.PASSTHROUGH) : stored.passthrough();
                StateFlag.State desired = desiredPassthrough(status.protectedNow(), original);
                boolean restoring = stored != null && sameState(desired, original);
                StateFlag.State current = region.getFlag(Flags.PASSTHROUGH);

                if (!sameState(current, desired) || restoring) {
                    mutations.add(new Mutation(manager, key, region, original, desired, restoring));
                }
            }
            pruneWorldRegistry(world, manager);
        }

        // Write recovery metadata before any temporary WorldGuard mutation. If the
        // process dies after this point, the next ClaimShift startup can restore it.
        for (Mutation mutation : mutations) {
            if (!mutation.restoring()
                    && !sameState(mutation.desired(), mutation.original())
                    && !stateStore.contains(mutation.key())) {
                stateStore.put(mutation.key(), new WorldGuardStateStore.OriginalState(mutation.original()));
            }
        }
        stateStore.flush();

        Map<RegionManager, Set<WorldGuardStateStore.RegionKey>> keysToRemoveAfterSave = new HashMap<>();
        for (Mutation mutation : mutations) {
            StateFlag.State current = mutation.region().getFlag(Flags.PASSTHROUGH);
            if (!sameState(current, mutation.desired())) {
                mutation.region().setFlag(Flags.PASSTHROUGH, mutation.desired());
                if (configuration.pluginSettings().debug()) {
                    plugin.getLogger().info("WorldGuard region " + mutation.key().world() + ":" + mutation.key().region()
                            + " passthrough -> " + stateName(mutation.desired()));
                }
            }
            if (mutation.restoring()) {
                keysToRemoveAfterSave
                        .computeIfAbsent(mutation.manager(), ignored -> new HashSet<>())
                        .add(mutation.key());
            }
        }

        // Anything no longer selected/owned/available must be put back exactly as
        // ClaimShift found it. Keep its recovery metadata until the original values
        // are known to have been written safely.
        for (WorldGuardStateStore.RegionKey key : stateStore.snapshot().keySet()) {
            if (discovered.contains(key)) {
                continue;
            }
            World world = plugin.getServer().getWorld(key.world());
            if (world == null) {
                continue;
            }
            RegionManager manager = regionManager(world);
            if (manager == null) {
                continue;
            }
            ProtectedRegion region = manager.getRegion(key.region());
            WorldGuardStateStore.OriginalState original = stateStore.get(key);
            if (region == null) {
                stateStore.remove(key);
                continue;
            }
            if (original != null && !sameState(region.getFlag(Flags.PASSTHROUGH), original.passthrough())) {
                region.setFlag(Flags.PASSTHROUGH, original.passthrough());
            }
            keysToRemoveAfterSave.computeIfAbsent(manager, ignored -> new HashSet<>()).add(key);
        }

        // RegionManager#saveChanges writes the entire manager, not a single region.
        // Before saving one restored region, temporarily put *every* ClaimShift-
        // controlled region in that manager back to its original passthrough value.
        // This prevents another currently OPEN region's temporary ALLOW from being
        // persisted as if it were administrator configuration. After a successful
        // save, active runtime overrides are applied again in memory.
        for (Map.Entry<RegionManager, Set<WorldGuardStateStore.RegionKey>> entry : keysToRemoveAfterSave.entrySet()) {
            try {
                saveOriginalStatesSafely(entry.getKey(), entry.getValue());
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not save restored WorldGuard region state: " + rootMessage(exception));
            }
        }
        stateStore.flush();
        runtimeProjectedOpen.retainAll(discovered);
        scheduleTransitionWake(nextTransition);

        Map<String, String> extra = new HashMap<>();
        if (skippedPassthrough > 0) {
            extra.put("skipped-existing-passthrough", String.valueOf(skippedPassthrough));
        }
        diagnostics = new ProviderDiagnostics(
                "dynamic-passthrough",
                managed,
                open,
                grace,
                protectedCount,
                extra
        );
    }

    private void reconcileDryRun() {
        WorldGuardSettings settings = configuration.pluginSettings().worldGuard();
        int managed = 0;
        int open = 0;
        int grace = 0;
        int protectedCount = 0;
        int skippedPassthrough = 0;
        Duration nextTransition = null;
        Set<WorldGuardStateStore.RegionKey> discovered = new HashSet<>();

        for (World world : plugin.getServer().getWorlds()) {
            RegionManager manager = regionManager(world);
            if (manager == null) continue;
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (!selected(world, region, settings)) continue;
                WorldGuardStateStore.RegionKey key = new WorldGuardStateStore.RegionKey(world.getName(), region.getId());
                if (!settings.manageExistingPassthroughRegions()
                        && dynamicOverrideState(region) != StateFlag.State.ALLOW
                        && region.getFlag(Flags.PASSTHROUGH) == StateFlag.State.ALLOW) {
                    skippedPassthrough++;
                    continue;
                }

                discovered.add(key);
                managed++;
                ClaimSnapshot claim = snapshot(world, region);
                ClaimStatus status = states.evaluate(claim);
                switch (status.state()) {
                    case OPEN -> open++;
                    case GRACE -> {
                        grace++;
                        nextTransition = earlier(nextTransition, status.remaining());
                    }
                    case PROTECTED -> protectedCount++;
                }
                if (status.protectedNow()) dryRunProjectedOpen.remove(key);
                else dryRunProjectedOpen.add(key);

                StateFlag.State original = region.getFlag(Flags.PASSTHROUGH);
                StateFlag.State desired = desiredPassthrough(status.protectedNow(), original);
                String policy = states.effectivePolicy(claim, configuration.ruleSettings()).configValue();
                String activeDelay = states.effectiveActiveDelay(claim, configuration.ruleSettings()).toString();
                String inactiveDelay = states.effectiveInactiveDelay(claim, configuration.ruleSettings()).toString();
                String preview = status.state().name() + ":" + stateName(desired)
                        + ":active=" + status.effectiveOwners().size()
                        + ":online=" + status.onlineOwners().size()
                        + ":policy=" + policy
                        + ":active-delay=" + activeDelay
                        + ":inactive-delay=" + inactiveDelay
                        + (status.raidActive() ? ":RAID" : "");
                String previous = dryRunPreview.put(key, preview);
                if (configuration.pluginSettings().diagnostics().logTransitions() && !preview.equals(previous)) {
                    plugin.getLogger().info("[DRY RUN] WorldGuard region " + key.world() + ":" + key.region()
                            + " would be " + status.state().name()
                            + " (passthrough " + stateName(original) + " -> " + stateName(desired)
                            + ", active owners " + status.effectiveOwners().size() + "/" + status.claim().owners().size()
                            + ", connected owners " + status.onlineOwners().size()
                            + ", policy " + policy
                            + ", active delay " + activeDelay
                            + ", inactive delay " + inactiveDelay + ")");
                }
            }
            pruneWorldRegistry(world, manager);
        }
        dryRunPreview.keySet().retainAll(discovered);
        dryRunProjectedOpen.retainAll(discovered);
        scheduleTransitionWake(nextTransition);

        Map<String, String> extra = new HashMap<>();
        extra.put("dry-run", "true");
        if (skippedPassthrough > 0) extra.put("skipped-existing-passthrough", String.valueOf(skippedPassthrough));
        diagnostics = new ProviderDiagnostics("dry-run", managed, open, grace, protectedCount, extra);
    }

    private Duration earlier(Duration current, Duration candidate) {
        if (candidate == null || candidate.isZero() || candidate.isNegative()) return current;
        if (current == null || candidate.compareTo(current) < 0) return candidate;
        return current;
    }

    private void scheduleTransitionWake(Duration delay) {
        cancelTransitionWake();
        if (delay == null || delay.isZero() || delay.isNegative() || closed.get()) return;
        long millis = Math.max(1L, delay.toMillis());
        transitionWakeTask = plugin.getServer().getAsyncScheduler().runDelayed(
                plugin,
                ignored -> {
                    transitionWakeTask = null;
                    requestReconcile();
                },
                millis,
                TimeUnit.MILLISECONDS
        );
    }

    private void cancelTransitionWake() {
        ScheduledTask task = transitionWakeTask;
        if (task != null) {
            task.cancel();
            transitionWakeTask = null;
        }
    }

    private StateFlag.State desiredPassthrough(boolean protectedNow, StateFlag.State original) {
        return protectedNow ? original : StateFlag.State.ALLOW;
    }

    private void saveOriginalStatesSafely(
            RegionManager manager,
            Set<WorldGuardStateStore.RegionKey> removeAfterSave
    ) throws Exception {
        record StagedState(
                WorldGuardStateStore.RegionKey key,
                ProtectedRegion region,
                StateFlag.State current,
                StateFlag.State original
        ) {
        }

        List<StagedState> staged = new ArrayList<>();
        Map<WorldGuardStateStore.RegionKey, WorldGuardStateStore.OriginalState> stored = stateStore.snapshot();

        // First collect everything without mutating WorldGuard. If lookup fails, no
        // region has been touched yet.
        for (Map.Entry<WorldGuardStateStore.RegionKey, WorldGuardStateStore.OriginalState> entry : stored.entrySet()) {
            WorldGuardStateStore.RegionKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.world());
            if (world == null || regionManager(world) != manager) {
                continue;
            }
            ProtectedRegion region = manager.getRegion(key.region());
            if (region == null) {
                continue;
            }
            staged.add(new StagedState(
                    key,
                    region,
                    region.getFlag(Flags.PASSTHROUGH),
                    entry.getValue().passthrough()
            ));
        }

        boolean saved = false;
        try {
            for (StagedState state : staged) {
                if (!sameState(state.current(), state.original())) {
                    state.region().setFlag(Flags.PASSTHROUGH, state.original());
                }
            }
            manager.saveChanges();
            saved = true;
        } finally {
            // Regions that remain under ClaimShift control must immediately return
            // to their runtime OPEN/GRACE override after the disk snapshot is safe.
            for (StagedState state : staged) {
                if (!removeAfterSave.contains(state.key())
                        && !sameState(state.current(), state.original())) {
                    state.region().setFlag(Flags.PASSTHROUGH, state.current());
                }
            }
        }

        if (saved) {
            removeAfterSave.forEach(stateStore::remove);
        }
    }

    private synchronized void recoverStaleOverrides() {
        Map<WorldGuardStateStore.RegionKey, WorldGuardStateStore.OriginalState> stale = stateStore.snapshot();
        if (stale.isEmpty()) {
            return;
        }

        Map<RegionManager, Set<WorldGuardStateStore.RegionKey>> byManager = new HashMap<>();
        for (Map.Entry<WorldGuardStateStore.RegionKey, WorldGuardStateStore.OriginalState> entry : stale.entrySet()) {
            WorldGuardStateStore.RegionKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.world());
            if (world == null) {
                continue;
            }
            RegionManager manager = regionManager(world);
            if (manager == null) {
                continue;
            }
            ProtectedRegion region = manager.getRegion(key.region());
            if (region == null) {
                stateStore.remove(key);
                continue;
            }
            region.setFlag(Flags.PASSTHROUGH, entry.getValue().passthrough());
            byManager.computeIfAbsent(manager, ignored -> new HashSet<>()).add(key);
        }

        int restored = 0;
        for (Map.Entry<RegionManager, Set<WorldGuardStateStore.RegionKey>> entry : byManager.entrySet()) {
            try {
                entry.getKey().saveChanges();
                for (WorldGuardStateStore.RegionKey key : entry.getValue()) {
                    stateStore.remove(key);
                    restored++;
                }
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not save recovered WorldGuard state: " + rootMessage(exception));
            }
        }
        stateStore.flush();
        if (restored > 0) {
            plugin.getLogger().warning("Recovered " + restored + " WorldGuard region state(s) from an interrupted previous run.");
        }
    }


    private synchronized void restoreWorldOverrides(World world) throws Exception {
        RegionManager manager = regionManager(world);
        if (manager == null) {
            return;
        }

        Set<WorldGuardStateStore.RegionKey> keys = new HashSet<>();
        for (WorldGuardStateStore.RegionKey key : stateStore.snapshot().keySet()) {
            if (key.world().equals(world.getName())) {
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            return;
        }

        // The same manager-wide persistence discipline used during normal
        // reconciliation is required here: never serialize another temporary
        // OPEN value while restoring the world that is about to unload.
        saveOriginalStatesSafely(manager, keys);
        stateStore.flush();
    }

    private synchronized void restoreAllOverrides(boolean saveManagers) {
        Map<WorldGuardStateStore.RegionKey, WorldGuardStateStore.OriginalState> snapshot = stateStore.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }

        Map<RegionManager, Set<WorldGuardStateStore.RegionKey>> byManager = new HashMap<>();
        for (Map.Entry<WorldGuardStateStore.RegionKey, WorldGuardStateStore.OriginalState> entry : snapshot.entrySet()) {
            WorldGuardStateStore.RegionKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.world());
            if (world == null) {
                continue;
            }
            RegionManager manager = regionManager(world);
            if (manager == null) {
                continue;
            }
            ProtectedRegion region = manager.getRegion(key.region());
            if (region == null) {
                stateStore.remove(key);
                continue;
            }
            region.setFlag(Flags.PASSTHROUGH, entry.getValue().passthrough());
            byManager.computeIfAbsent(manager, ignored -> new HashSet<>()).add(key);
        }

        if (!saveManagers) {
            stateStore.flush();
            return;
        }

        for (Map.Entry<RegionManager, Set<WorldGuardStateStore.RegionKey>> entry : byManager.entrySet()) {
            try {
                entry.getKey().saveChanges();
                entry.getValue().forEach(stateStore::remove);
            } catch (Exception exception) {
                plugin.getLogger().warning("Could not save restored WorldGuard state: " + rootMessage(exception));
            }
        }
        stateStore.flush();
    }

    private boolean isManageable(World world, ProtectedRegion region, WorldGuardSettings settings) {
        if (!selected(world, region, settings)) {
            return false;
        }
        if (dynamicOverrideState(region) == StateFlag.State.ALLOW) {
            return true;
        }
        if (settings.mode() != WorldGuardSettings.Mode.DYNAMIC_PASSTHROUGH || settings.manageExistingPassthroughRegions()) {
            return true;
        }
        WorldGuardStateStore.RegionKey key = new WorldGuardStateStore.RegionKey(world.getName(), region.getId());
        return stateStore.contains(key) || region.getFlag(Flags.PASSTHROUGH) != StateFlag.State.ALLOW;
    }

    private boolean selected(World world, ProtectedRegion region, WorldGuardSettings settings) {
        if (!region.isPhysicalArea() || region.getId().equalsIgnoreCase("__global__") || effectiveOwners(region).isEmpty()) {
            return false;
        }

        WorldGuardStateStore.RegionKey key = new WorldGuardStateStore.RegionKey(world.getName(), region.getId());
        RegionLifecycleClassification classification = regionRegistry.classification(key);
        if (classification == null) {
            classification = regionRegistry.classifyRuntime(key, settings.autoManageNewRegions());
        }

        StateFlag.State override = dynamicOverrideState(region);
        if (override == StateFlag.State.DENY) {
            return false;
        }
        if (override == StateFlag.State.ALLOW) {
            return true;
        }
        if (RegionSelector.matchesAny(world.getName(), region.getId(), settings.excludedRegions())) {
            return false;
        }
        if (settings.manageAllOwnedRegions()) {
            return true;
        }
        if (RegionSelector.matchesAny(world.getName(), region.getId(), settings.includedRegions())) {
            return true;
        }
        return classification == RegionLifecycleClassification.AUTO_DYNAMIC;
    }

    private void bootstrapLoadedWorlds() {
        for (World world : plugin.getServer().getWorlds()) {
            bootstrapWorldRegistry(world);
        }
    }

    private void bootstrapWorldRegistry(World world) {
        RegionManager manager = regionManager(world);
        if (manager == null) {
            return;
        }
        Set<WorldGuardStateStore.RegionKey> current = new HashSet<>();
        for (ProtectedRegion region : manager.getRegions().values()) {
            if (region.isPhysicalArea() && !region.getId().equalsIgnoreCase("__global__")) {
                current.add(new WorldGuardStateStore.RegionKey(world.getName(), region.getId()));
            }
        }
        regionRegistry.bootstrapWorld(world.getName(), current);
    }

    private void pruneWorldRegistry(World world, RegionManager manager) {
        Set<WorldGuardStateStore.RegionKey> current = new HashSet<>();
        for (ProtectedRegion region : manager.getRegions().values()) {
            if (region.isPhysicalArea() && !region.getId().equalsIgnoreCase("__global__")) {
                current.add(new WorldGuardStateStore.RegionKey(world.getName(), region.getId()));
            }
        }
        regionRegistry.pruneWorld(world.getName(), current);
    }

    private String managementSource(World world, ProtectedRegion region, WorldGuardSettings settings) {
        if (!region.isPhysicalArea() || region.getId().equalsIgnoreCase("__global__")) {
            return "static";
        }
        if (effectiveOwners(region).isEmpty()) {
            return "ownerless";
        }
        WorldGuardStateStore.RegionKey key = new WorldGuardStateStore.RegionKey(world.getName(), region.getId());
        RegionLifecycleClassification classification = regionRegistry.classification(key);
        if (classification == null) {
            classification = regionRegistry.classifyRuntime(key, settings.autoManageNewRegions());
        }

        StateFlag.State override = dynamicOverrideState(region);
        if (override == StateFlag.State.DENY) {
            return "manual-deny";
        }
        if (override == StateFlag.State.ALLOW) {
            return "manual-allow";
        }
        if (RegionSelector.matchesAny(world.getName(), region.getId(), settings.excludedRegions())) {
            return "excluded";
        }
        if (settings.manageAllOwnedRegions()) {
            return "manage-all";
        }
        if (RegionSelector.matchesAny(world.getName(), region.getId(), settings.includedRegions())) {
            return "included";
        }
        return classification == RegionLifecycleClassification.AUTO_DYNAMIC ? "auto-new" : "legacy-static";
    }


    private String effectiveManagementSource(World world, ProtectedRegion region, WorldGuardSettings settings) {
        String source = managementSource(world, region, settings);
        if (source.equals("manual-allow")
                || !Set.of("manage-all", "included", "auto-new").contains(source)
                || settings.mode() != WorldGuardSettings.Mode.DYNAMIC_PASSTHROUGH
                || settings.manageExistingPassthroughRegions()) {
            return source;
        }
        WorldGuardStateStore.RegionKey key = new WorldGuardStateStore.RegionKey(world.getName(), region.getId());
        if (!stateStore.contains(key) && region.getFlag(Flags.PASSTHROUGH) == StateFlag.State.ALLOW) {
            return "existing-passthrough";
        }
        return source;
    }

    private StateFlag.State dynamicOverrideState(ProtectedRegion region) {
        StateFlag overrideFlag = WorldGuardFlags.dynamicOverride();
        return overrideFlag == null ? null : region.getFlag(overrideFlag);
    }

    private ClaimSnapshot snapshot(World world, ProtectedRegion region) {
        Set<UUID> owners = effectiveOwners(region);
        Set<UUID> trusted = effectiveTrusted(region);
        Map<String, String> attributes = new HashMap<>();
        if (WorldGuardFlags.presencePolicy() != null) {
            String value = region.getFlag(WorldGuardFlags.presencePolicy());
            if (value != null && !value.isBlank()) attributes.put("presence-policy", value.trim());
        }
        if (WorldGuardFlags.activeDelay() != null) {
            String value = region.getFlag(WorldGuardFlags.activeDelay());
            if (value != null && !value.isBlank()) attributes.put("active-delay", value.trim());
        }
        String inactiveDelay = null;
        if (WorldGuardFlags.inactiveDelay() != null) {
            inactiveDelay = region.getFlag(WorldGuardFlags.inactiveDelay());
        }
        if ((inactiveDelay == null || inactiveDelay.isBlank()) && WorldGuardFlags.legacyDelay() != null) {
            inactiveDelay = region.getFlag(WorldGuardFlags.legacyDelay());
        }
        if (inactiveDelay != null && !inactiveDelay.isBlank()) {
            attributes.put("inactive-delay", inactiveDelay.trim());
        }
        attributes.put("management-source", effectiveManagementSource(world, region, configuration.pluginSettings().worldGuard()));
        WorldGuardStateStore.RegionKey runtimeKey = new WorldGuardStateStore.RegionKey(world.getName(), region.getId());
        boolean runtimeOpen = configuration.pluginSettings().diagnostics().dryRun()
                ? dryRunProjectedOpen.contains(runtimeKey)
                : runtimeProjectedOpen.contains(runtimeKey);
        attributes.put("runtime-dynamic-open", Boolean.toString(runtimeOpen));
        if (WorldGuardFlags.raidSessions() != null) {
            StateFlag.State value = region.getFlag(WorldGuardFlags.raidSessions());
            if (value != null) attributes.put("raid-sessions", value == StateFlag.State.ALLOW ? "true" : "false");
        }
        return new ClaimSnapshot(
                id(),
                world.getName() + ":" + region.getId(),
                region.getId(),
                world.getName(),
                owners,
                trusted,
                attributes
        );
    }

    private Set<UUID> effectiveOwners(ProtectedRegion region) {
        Set<UUID> result = new HashSet<>();
        for (ProtectedRegion current = region; current != null; current = current.getParent()) {
            addDomainPlayers(current.getOwners(), result);
        }
        return Set.copyOf(result);
    }

    private Set<UUID> effectiveTrusted(ProtectedRegion region) {
        Set<UUID> result = new HashSet<>();
        for (ProtectedRegion current = region; current != null; current = current.getParent()) {
            addDomainPlayers(current.getOwners(), result);
            addDomainPlayers(current.getMembers(), result);
        }
        return Set.copyOf(result);
    }

    @SuppressWarnings("deprecation")
    private void addDomainPlayers(DefaultDomain domain, Set<UUID> result) {
        result.addAll(domain.getUniqueIds());

        // Modern WorldGuard stores UUIDs, but legacy/offline-mode regions can still
        // contain player names. Resolve without performing a blocking web lookup.
        for (String name : domain.getPlayers()) {
            UUID resolved = resolveLegacyPlayerName(name);
            if (resolved != null) {
                result.add(resolved);
            }
        }
    }

    private UUID resolveLegacyPlayerName(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(name);
        if (cached != null) {
            return cached.getUniqueId();
        }
        if (!plugin.getServer().getOnlineMode()) {
            return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        }

        // A legacy name-only owner can be offline and absent from Bukkit's cache on
        // an online-mode server. Represent that unresolved identity with a stable
        // ClaimShift-only UUID instead of dropping the owner entirely. The moment
        // the real player is online/cached, the real UUID above replaces it. With
        // protect-unknown-offline-owners=true this fails closed rather than turning
        // an old name-based region into an accidentally unmanaged raid target.
        return UUID.nameUUIDFromBytes(
                ("ClaimShift:LegacyOwner:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)
        );
    }

    private RegionManager regionManager(World world) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }

    private boolean sameState(StateFlag.State a, StateFlag.State b) {
        return a == b;
    }

    private String stateName(StateFlag.State state) {
        return state == null ? "UNSET" : state.name();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
