package dev.onelsey.claimshift.listener;

import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.config.PresenceSettings;
import dev.onelsey.claimshift.integration.ProviderManager;
import dev.onelsey.claimshift.protection.ActivityType;
import dev.onelsey.claimshift.protection.PresenceService;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;

/**
 * Supplies Smart Presence with coarse activity signals. ClaimShift deliberately
 * does not store chat text, command arguments, coordinates, or inventory data.
 */
@SuppressWarnings("deprecation")
public final class ActivityListener implements Listener {
    private final PresenceService presence;
    private final ProviderManager providers;
    private final ConfigurationService configuration;

    public ActivityListener(PresenceService presence, ProviderManager providers, ConfigurationService configuration) {
        this.presence = presence;
        this.providers = providers;
        this.configuration = configuration;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        PresenceSettings settings = configuration.ruleSettings().presence();
        if (!settings.smartEnabled()) return;
        var to = event.getTo();
        PresenceService.ActivityResult result = presence.recordMovement(
                event.getPlayer().getUniqueId(),
                to.getWorld() == null ? "unknown" : to.getWorld().getName(),
                to.getX(), to.getY(), to.getZ(),
                settings
        );
        if (result.reactivated()) {
            providers.requestReconcile();
            if (configuration.ruleSettings().activeDelay().isPositive()) {
                providers.requestReconcileAfter(configuration.ruleSettings().activeDelay());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        activity(event.getPlayer(), ActivityType.BLOCK_BREAK, "block-break");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        activity(event.getPlayer(), ActivityType.BLOCK_PLACE, "block-place");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        activity(event.getPlayer(), ActivityType.INTERACT, event.getAction().name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            activity(player, ActivityType.INVENTORY, "inventory-click");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String root = "command";
        if (message != null && !message.isBlank()) {
            String token = message.charAt(0) == '/' ? message.substring(1) : message;
            int space = token.indexOf(' ');
            root = (space >= 0 ? token.substring(0, space) : token).toLowerCase(Locale.ROOT);
        }
        activity(event.getPlayer(), ActivityType.COMMAND, root);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        activity(event.getPlayer(), ActivityType.CHAT, "chat");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        Player player = attacker(event);
        if (player != null) {
            activity(player, ActivityType.COMBAT, "combat");
        }
    }

    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private void activity(Player player, ActivityType type, String signature) {
        PresenceSettings settings = configuration.ruleSettings().presence();
        if (!settings.smartEnabled()) return;
        PresenceService.ActivityResult result = presence.recordActivity(player.getUniqueId(), type, signature, settings);
        if (result.reactivated()) {
            providers.requestReconcile();
            if (configuration.ruleSettings().activeDelay().isPositive()) {
                providers.requestReconcileAfter(configuration.ruleSettings().activeDelay());
            }
        }
    }
}
