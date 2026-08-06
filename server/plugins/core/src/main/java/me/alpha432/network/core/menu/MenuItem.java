package me.alpha432.network.core.menu;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** An icon in a {@link Menu} plus what happens when it is clicked. */
public record MenuItem(ItemStack stack, Consumer<InventoryClickEvent> handler) {

    public static MenuItem of(ItemStack stack, Consumer<InventoryClickEvent> handler) {
        return new MenuItem(stack, handler);
    }

    /** An icon that does nothing when clicked. */
    public static MenuItem display(ItemStack stack) {
        return new MenuItem(stack, null);
    }

    public void click(InventoryClickEvent event) {
        if (handler != null) {
            handler.accept(event);
        }
    }
}
