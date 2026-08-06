package me.alpha432.network.core.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * A chest GUI. Clicks are cancelled and routed to the {@link MenuItem} in the clicked slot,
 * so subclasses only describe what the menu looks like.
 */
public class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, MenuItem> items = new HashMap<>();
    private final int size;
    private boolean cancelClicks = true;

    public Menu(Component title, int rows) {
        this.size = Math.max(1, Math.min(6, rows)) * 9;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public Menu set(int slot, MenuItem item) {
        if (slot < 0 || slot >= size) {
            return this;
        }
        items.put(slot, item);
        inventory.setItem(slot, item == null ? null : item.stack());
        return this;
    }

    public Menu set(int slot, ItemStack stack, java.util.function.Consumer<InventoryClickEvent> handler) {
        return set(slot, MenuItem.of(stack, handler));
    }

    public Menu display(int slot, ItemStack stack) {
        return set(slot, MenuItem.display(stack));
    }

    /** Puts the item into the first empty slot. */
    public Menu add(MenuItem item) {
        for (int slot = 0; slot < size; slot++) {
            if (!items.containsKey(slot)) {
                return set(slot, item);
            }
        }
        return this;
    }

    /** Fills every still empty slot, typically with a glass pane. */
    public Menu fill(ItemStack filler) {
        for (int slot = 0; slot < size; slot++) {
            if (!items.containsKey(slot)) {
                set(slot, MenuItem.display(filler));
            }
        }
        return this;
    }

    public Menu fillBorder(ItemStack filler) {
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            boolean edge = row == 0 || row == rows - 1 || column == 0 || column == 8;
            if (edge && !items.containsKey(slot)) {
                set(slot, MenuItem.display(filler));
            }
        }
        return this;
    }

    public Menu clear() {
        items.clear();
        inventory.clear();
        return this;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public int size() {
        return size;
    }

    public int rows() {
        return size / 9;
    }

    /** Set to false when the menu intentionally lets players move items (kit editors). */
    public Menu cancelClicks(boolean cancelClicks) {
        this.cancelClicks = cancelClicks;
        return this;
    }

    public boolean cancelsClicks() {
        return cancelClicks;
    }

    /** Called by the shared listener; override for menus that need raw click access. */
    public void handleClick(InventoryClickEvent event) {
        MenuItem item = items.get(event.getRawSlot());
        if (item != null) {
            item.click(event);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Default filler used by most menus in the network. */
    public static ItemStack filler() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name("<gray>").build();
    }
}
