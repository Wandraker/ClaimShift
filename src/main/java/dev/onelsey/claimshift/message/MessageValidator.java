package dev.onelsey.claimshift.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Set;

public final class MessageValidator {
    private static final MiniMessage STRICT = MiniMessage.builder().strict(true).build();
    private static final Set<String> PLACEHOLDERS = Set.of(
            "claim", "action", "remaining", "duration", "reason", "scope", "locale",
            "version", "command", "description", "key", "value", "provider", "locales", "scopes"
    );

    private MessageValidator() {
    }

    public static void validate(MessageBundle bundle) {
        if (bundle.prefix() == null || bundle.prefix().isBlank()) {
            throw new IllegalArgumentException("messages.yml prefix cannot be empty");
        }

        TagResolver theme = themeResolver(bundle);
        deserialize("prefix", bundle.prefix(), theme);

        TagResolver.Builder all = TagResolver.builder().resolver(theme)
                .resolver(TagResolver.resolver("prefix", Tag.selfClosingInserting(Component.empty())));
        for (String placeholder : PLACEHOLDERS) {
            all.resolver(Placeholder.component(placeholder, Component.empty()));
        }
        TagResolver resolver = all.build();
        for (var entry : bundle.messages().entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalArgumentException("Message cannot be empty: " + entry.getKey());
            }
            deserialize(entry.getKey(), entry.getValue(), resolver);
        }
    }

    private static TagResolver themeResolver(MessageBundle bundle) {
        TagResolver.Builder builder = TagResolver.builder();
        bundle.theme().forEach((name, hex) -> {
            if (!name.matches("[a-z0-9_-]+")) {
                throw new IllegalArgumentException("Invalid theme tag name: " + name);
            }
            TextColor color = TextColor.fromHexString(hex);
            if (color == null) {
                throw new IllegalArgumentException("Invalid HEX color for theme." + name + ": " + hex);
            }
            builder.resolver(TagResolver.resolver(name, Tag.styling(color)));
        });
        return builder.build();
    }

    private static void deserialize(String key, String value, TagResolver resolver) {
        try {
            STRICT.deserialize(value, resolver);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid MiniMessage in '" + key + "': " + rootMessage(exception));
        }
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
