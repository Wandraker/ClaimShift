package dev.onelsey.claimshift.message;

import dev.onelsey.claimshift.config.ConfigurationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class MessageService {
    private final ConfigurationService configuration;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public MessageService(ConfigurationService configuration) {
        this.configuration = configuration;
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(render(key, Map.of()));
    }

    public void send(CommandSender sender, String key, Map<String, Component> placeholders) {
        sender.sendMessage(render(key, placeholders));
    }

    public Component render(String key, Map<String, Component> placeholders) {
        MessageBundle bundle = configuration.messageBundle();
        try {
            Component prefix = miniMessage.deserialize(bundle.prefix(), themeResolver(bundle));
            TagResolver.Builder resolver = TagResolver.builder()
                    .resolver(themeResolver(bundle))
                    .resolver(TagResolver.resolver("prefix", Tag.selfClosingInserting(prefix)));
            placeholders.forEach((name, component) -> resolver.resolver(Placeholder.component(name, component)));
            return miniMessage.deserialize(bundle.message(key), resolver.build());
        } catch (RuntimeException exception) {
            return Component.text("[ClaimShift] Message rendering failed for '" + key + "': " + exception.getMessage());
        }
    }

    public String plain(String key) {
        return plain.serialize(render(key, Map.of()));
    }

    private TagResolver themeResolver(MessageBundle bundle) {
        TagResolver.Builder builder = TagResolver.builder();
        bundle.theme().forEach((name, hex) -> {
            TextColor color = TextColor.fromHexString(hex);
            if (color != null && isValidTagName(name)) {
                builder.resolver(TagResolver.resolver(name, Tag.styling(color)));
            }
        });
        return builder.build();
    }

    private boolean isValidTagName(String name) {
        return name.matches("[a-z0-9_-]+");
    }
}
