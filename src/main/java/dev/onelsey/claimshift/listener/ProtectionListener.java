
package dev.onelsey.claimshift.listener;

import dev.onelsey.claimshift.config.ConfigurationService;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import dev.onelsey.claimshift.message.MessageService;
import dev.onelsey.claimshift.model.ProtectionAction;
import dev.onelsey.claimshift.model.ProtectionDecision;
import dev.onelsey.claimshift.protection.ProtectionService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectionListener implements Listener {
    private final ConfigurationService configuration;
    private final ProtectionService protection;
    private final MessageService messages;
    private final ConcurrentHashMap<UUID, Long> lastNoticeNanos = new ConcurrentHashMap<>();

    public ProtectionListener(ConfigurationService configuration, ProtectionService protection, MessageService messages) {
        this.configuration = configuration;
        this.protection = protection;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        denyPlayer(event.getPlayer(), event.getBlock(), ProtectionAction.BLOCK_BREAK, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        denyPlayer(event.getPlayer(), event.getBlockPlaced(), ProtectionAction.BLOCK_PLACE, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        denyPlayer(event.getPlayer(), event.getHarvestedBlock(), ProtectionAction.INTERACTIONS, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShearBlock(PlayerShearBlockEvent event) {
        denyPlayer(event.getPlayer(), event.getBlock(), ProtectionAction.INTERACTIONS, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFertilize(BlockFertilizeEvent event) {
        Player actor = event.getPlayer();
        if (actor != null) {
            denyPlayer(actor, event.getBlock(), ProtectionAction.INTERACTIONS, () -> event.setCancelled(true));
            return;
        }
        if (protection.checkSystem(event.getBlock().getLocation(), ProtectionAction.INTERACTIONS).denied()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSignChange(SignChangeEvent event) {
        denyPlayer(event.getPlayer(), event.getBlock(), ProtectionAction.INTERACTIONS, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        ProtectionAction action = block.getState(false) instanceof InventoryHolder
                ? ProtectionAction.CONTAINERS
                : (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.PHYSICAL
                ? ProtectionAction.INTERACTIONS
                : null);
        if (action != null) {
            denyPlayer(event.getPlayer(), block, action, () -> event.setCancelled(true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        denyEntityInteraction(event.getPlayer(), event.getRightClicked(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        denyEntityInteraction(event.getPlayer(), event.getRightClicked(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShear(PlayerShearEntityEvent event) {
        denyEntityInteraction(event.getPlayer(), event.getEntity(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLeash(PlayerLeashEntityEvent event) {
        denyEntityInteraction(event.getPlayer(), event.getEntity(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        denyEntityInteraction(event.getPlayer(), event.getEntity(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player actor = event.getPlayer();
        if (actor == null) {
            return;
        }
        denyEntityInteraction(actor, event.getEntity(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player actor = resolvePlayer(event.getDamager());
        if (actor != null) {
            ProtectionDecision decision = protection.check(actor, event.getEntity().getLocation(), ProtectionAction.ENTITY_DAMAGE);
            if (decision.denied()) {
                event.setCancelled(true);
                notifyDenied(actor, decision);
            }
            return;
        }

        // Do not make players invulnerable to normal mob/environment combat. The
        // system path only protects non-player entities that belong to an offline claim.
        if (!(event.getEntity() instanceof Player)
                && protection.checkSystem(event.getEntity().getLocation(), ProtectionAction.ENTITY_DAMAGE).denied()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player player) {
            ProtectionDecision decision = protection.check(player, event.getBlock().getLocation(), ProtectionAction.ENTITY_GRIEF);
            if (decision.denied()) {
                event.setCancelled(true);
                notifyDenied(player, decision);
            }
            return;
        }
        if (protection.checkSystem(event.getBlock().getLocation(), ProtectionAction.ENTITY_GRIEF).denied()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHangingBreak(HangingBreakEvent event) {
        Player actor = event instanceof HangingBreakByEntityEvent byEntity
                ? resolvePlayer(byEntity.getRemover())
                : null;
        if (actor != null) {
            ProtectionDecision decision = protection.check(actor, event.getEntity().getLocation(), ProtectionAction.HANGING);
            if (decision.denied()) {
                event.setCancelled(true);
                notifyDenied(actor, decision);
            }
            return;
        }
        if (protection.checkSystem(event.getEntity().getLocation(), ProtectionAction.HANGING).denied()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player actor = event.getPlayer();
        if (actor == null) {
            return;
        }
        ProtectionDecision decision = protection.check(actor, event.getEntity().getLocation(), ProtectionAction.HANGING);
        if (decision.denied()) {
            event.setCancelled(true);
            notifyDenied(actor, decision);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        denyPlayer(event.getPlayer(), event.getBlock(), ProtectionAction.BUCKETS, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketFill(PlayerBucketFillEvent event) {
        denyPlayer(event.getPlayer(), event.getBlock(), ProtectionAction.BUCKETS, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        ProtectionDecision decision = protection.check(
                event.getPlayer(),
                event.getEntity().getLocation(),
                ProtectionAction.BUCKETS
        );
        if (decision.denied()) {
            event.setCancelled(true);
            notifyDenied(event.getPlayer(), decision);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        ProtectionDecision decision = protection.check(
                event.getPlayer(),
                event.getItemFrame().getLocation(),
                ProtectionAction.HANGING
        );
        if (decision.denied()) {
            event.setCancelled(true);
            notifyDenied(event.getPlayer(), decision);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Location location = inventoryLocation(event.getInventory());
        if (location == null) {
            return;
        }
        ProtectionDecision decision = protection.check(player, location, ProtectionAction.CONTAINERS);
        if (decision.denied()) {
            event.setCancelled(true);
            notifyDenied(player, decision);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        denyOpenInventoryMutation(player, event.getView().getTopInventory(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        denyOpenInventoryMutation(player, event.getView().getTopInventory(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Location source = inventoryLocation(event.getSource());
        Location destination = inventoryLocation(event.getDestination());
        if (protection.crossesProtectedBoundary(source, destination, ProtectionAction.CONTAINER_AUTOMATION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        Location source = event.getItem().getLocation();
        Location destination = inventoryLocation(event.getInventory());
        if (protection.crossesProtectedBoundary(source, destination, ProtectionAction.CONTAINER_AUTOMATION)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onIgnite(BlockIgniteEvent event) {
        Player actor = event.getPlayer();
        ProtectionDecision decision = protection.check(actor, event.getBlock().getLocation(), ProtectionAction.FIRE);
        if (decision.denied()) {
            event.setCancelled(true);
            if (actor != null) {
                notifyDenied(actor, decision);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBurn(BlockBurnEvent event) {
        if (protection.checkSystem(event.getBlock().getLocation(), ProtectionAction.FIRE).denied()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSpread(BlockSpreadEvent event) {
        Material source = event.getSource().getType();
        if (source != Material.FIRE && source != Material.SOUL_FIRE) {
            return;
        }
        if (protection.crossesProtectedBoundary(
                event.getSource().getLocation(),
                event.getBlock().getLocation(),
                ProtectionAction.FIRE
        )) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFluid(BlockFromToEvent event) {
        Material source = event.getBlock().getType();
        if (source != Material.WATER && source != Material.LAVA) {
            return;
        }
        if (protection.crossesProtectedBoundary(
                event.getBlock().getLocation(),
                event.getToBlock().getLocation(),
                ProtectionAction.FLUIDS
        )) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        // Even an empty piston movement places a piston head one block forward.
        // Check that boundary too, otherwise a piston outside a protected claim
        // could extend its head into the claim without moving any block.
        Block head = event.getBlock().getRelative(event.getDirection());
        if (protection.crossesProtectedBoundary(
                event.getBlock().getLocation(),
                head.getLocation(),
                ProtectionAction.PISTONS
        )) {
            event.setCancelled(true);
            return;
        }

        for (Block block : event.getBlocks()) {
            Block destination = block.getRelative(event.getDirection());
            if (protection.crossesProtectedBoundary(
                    block.getLocation(),
                    destination.getLocation(),
                    ProtectionAction.PISTONS
            )) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Block destination = block.getRelative(event.getDirection().getOppositeFace());
            if (protection.crossesProtectedBoundary(
                    block.getLocation(),
                    destination.getLocation(),
                    ProtectionAction.PISTONS
            )) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> protection.checkSystem(block.getLocation(), ProtectionAction.EXPLOSIONS).denied());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> protection.checkSystem(block.getLocation(), ProtectionAction.EXPLOSIONS).denied());
    }

    private void denyPlayer(Player player, Block block, ProtectionAction action, Runnable cancel) {
        ProtectionDecision decision = protection.check(player, block.getLocation(), action);
        if (decision.denied()) {
            cancel.run();
            notifyDenied(player, decision);
        }
    }

    private void denyEntityInteraction(Player player, Entity entity, Runnable cancel) {
        ProtectionDecision decision = protection.check(player, entity.getLocation(), ProtectionAction.ENTITY_INTERACT);
        if (decision.denied()) {
            cancel.run();
            notifyDenied(player, decision);
        }
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private void denyOpenInventoryMutation(Player player, Inventory inventory, Runnable cancel) {
        Location location = inventoryLocation(inventory);
        if (location == null) {
            return;
        }
        ProtectionDecision decision = protection.check(player, location, ProtectionAction.CONTAINERS);
        if (decision.denied()) {
            cancel.run();
            notifyDenied(player, decision);
        }
    }

    private Location inventoryLocation(Inventory inventory) {
        try {
            // Personal/crafting/ender inventories can report the player's current
            // location. They are not territory containers and must never become
            // unusable just because the player is standing inside a protected claim.
            if (inventory.getHolder() instanceof Player) {
                return null;
            }
            return inventory.getLocation();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void notifyDenied(Player player, ProtectionDecision decision) {
        long now = System.nanoTime();
        long cooldownNanos = configuration.ruleSettings().notificationCooldown().toNanos();
        Long previous = lastNoticeNanos.put(player.getUniqueId(), now);
        if (previous != null && now - previous < cooldownNanos) {
            return;
        }
        messages.send(player, "protected", Map.of(
                "claim", Component.text(decision.claim().name()),
                "action", Component.text(decision.action().configKey())
        ));
    }
}
