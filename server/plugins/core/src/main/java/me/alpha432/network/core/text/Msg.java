package me.alpha432.network.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Thin MiniMessage wrapper. All user facing text in the network goes through here. */
public final class Msg {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Msg() {
    }

    /**
     * Parses MiniMessage. Placeholders are passed as alternating key/value pairs and replaced
     * literally before parsing, e.g. {@code Msg.mm("Hi <name>", "<name>", player.getName())}.
     */
    public static Component mm(String raw, Object... placeholders) {
        return MINI.deserialize(replace(raw, placeholders));
    }

    /** Like {@link #mm} but without the italic default that item names and lore inherit. */
    public static Component item(String raw, Object... placeholders) {
        return mm(raw, placeholders).decoration(TextDecoration.ITALIC, false);
    }

    public static String replace(String raw, Object... placeholders) {
        if (raw == null) {
            return "";
        }
        String result = raw;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace(String.valueOf(placeholders[i]), String.valueOf(placeholders[i + 1]));
        }
        return result;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Escapes MiniMessage tags in player supplied text so nobody can inject formatting. */
    public static String escape(String raw) {
        return raw == null ? "" : MINI.escapeTags(raw);
    }

    public static MiniMessage mini() {
        return MINI;
    }
}
