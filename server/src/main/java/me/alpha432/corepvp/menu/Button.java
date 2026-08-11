package me.alpha432.corepvp.menu;

import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** One clickable icon in a {@link Menu}. */
public final class Button {

    private final ItemStack icon;
    private final Consumer<MenuClick> action;

    private Button(ItemStack icon, Consumer<MenuClick> action) {
        this.icon = icon;
        this.action = action;
    }

    /** An icon that does nothing when clicked. */
    public static Button display(ItemStack icon) {
        return new Button(icon, null);
    }

    public static Button of(ItemStack icon, Consumer<MenuClick> action) {
        return new Button(icon, action);
    }

    public ItemStack icon() {
        return icon;
    }

    public void click(MenuClick click) {
        if (action != null) {
            action.accept(click);
        }
    }
}
