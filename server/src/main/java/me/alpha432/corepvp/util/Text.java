package me.alpha432.corepvp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mini(String input) {
        return MINI.deserialize(input);
    }

    /**
     * Item names and lore render italic by default, which looks wrong on every
     * GUI icon. This turns that off without touching other decorations.
     */
    public static Component item(String input) {
        return mini(input).decoration(TextDecoration.ITALIC, false);
    }

    public static Component item(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
