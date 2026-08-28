package dev.onelsey.claimshift.listener;

import dev.onelsey.claimshift.command.CommandSyntax;
import dev.onelsey.claimshift.config.ConfigurationService;
import dev.onelsey.claimshift.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.util.Map;

public final class DiagnosticsNoticeListener implements Listener {
    private final ConfigurationService configuration;
    private final MessageService messages;

    public DiagnosticsNoticeListener(ConfigurationService configuration, MessageService messages) {
        this.configuration = configuration;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!configuration.pluginSettings().diagnostics().dryRun()
                || !configuration.pluginSettings().diagnostics().operatorNotice()
                || !player.hasPermission("claimshift.dryrun")) {
            return;
        }

        Component command = Component.text(CommandSyntax.DRY_RUN_OFF);
        Component inspectCommand = Component.text(CommandSyntax.INSPECT);
        player.showTitle(Title.title(
                messages.render("dry-run-title", Map.of()),
                messages.render("dry-run-subtitle", Map.of("command", command, "inspect-command", inspectCommand)),
                Title.Times.times(Duration.ofMillis(350), Duration.ofSeconds(5), Duration.ofMillis(500))
        ));
        messages.send(player, "dry-run-notice", Map.of("command", command, "inspect-command", inspectCommand));
    }
}
