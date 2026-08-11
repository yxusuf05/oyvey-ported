package me.alpha432.corepvp.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves message keys from messages.yml into Adventure components.
 *
 * <p>Values supplied by players go through {@link #of(String, String)}, which
 * inserts them literally - a player named {@code <red>x} cannot inject
 * formatting into a message about them.
 */
public final class Messages {

    private final ConfigManager configs;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private TagResolver prefixResolver = TagResolver.empty();

    public Messages(ConfigManager configs) {
        this.configs = configs;
        reload();
    }

    public void reload() {
        String prefix = configs.get("messages.yml").getString("prefix", "");
        prefixResolver = Placeholder.component("prefix", miniMessage.deserialize(prefix));
    }

    /** Builds a literal placeholder: {@code of("player", name)} fills {@code <player>}. */
    public static TagResolver of(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    public static TagResolver of(String key, Number value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /** Inserts an already built component, e.g. another rendered message. */
    public static TagResolver of(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public String raw(String key) {
        String value = configs.get("messages.yml").getString(key);
        return value == null ? key : value;
    }

    public Component render(String key, TagResolver... placeholders) {
        return deserialize(raw(key), placeholders);
    }

    public List<Component> renderList(String key, TagResolver... placeholders) {
        List<String> lines = configs.get("messages.yml").getStringList(key);
        List<Component> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(deserialize(line, placeholders));
        }
        return rendered;
    }

    public void send(CommandSender target, String key, TagResolver... placeholders) {
        Component message = render(key, placeholders);
        if (!Component.empty().equals(message)) {
            target.sendMessage(message);
        }
    }

    /** Parses arbitrary MiniMessage input that did not come from a player. */
    public Component parse(String miniMessageInput, TagResolver... placeholders) {
        return deserialize(miniMessageInput, placeholders);
    }

    private Component deserialize(String input, TagResolver... placeholders) {
        TagResolver resolver = placeholders.length == 0
                ? prefixResolver
                : TagResolver.resolver(prefixResolver, TagResolver.resolver(placeholders));
        return miniMessage.deserialize(input, resolver);
    }
}
