package me.alpha432.corepvp.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for every GUI.
 *
 * <p>Menus identify themselves through {@link InventoryHolder}, so the single
 * {@link MenuListener} can tell a plugin GUI apart from a chest without
 * tracking open inventories by hand.
 */
public abstract class Menu implements InventoryHolder {

    private final Map<Integer, Button> buttons = new HashMap<>();
    private Inventory inventory;
    private Player viewer;

    public abstract Component title();

    /** Inventory height in rows (1-6). */
    public abstract int rows();

    /** Fills {@link #buttons} for this viewer. Called on every redraw. */
    protected abstract void build(Player viewer);

    protected void set(int slot, Button button) {
        if (slot >= 0 && slot < size()) {
            buttons.put(slot, button);
        }
    }

    protected void set(int row, int column, Button button) {
        set(row * 9 + column, button);
    }

    protected void fillEmpty(Button button) {
        for (int slot = 0; slot < size(); slot++) {
            buttons.putIfAbsent(slot, button);
        }
    }

    public int size() {
        return Math.max(1, Math.min(6, rows())) * 9;
    }

    public Player viewer() {
        return viewer;
    }

    public Button button(int slot) {
        return buttons.get(slot);
    }

    /**
     * Slots the player may freely move items in and out of. Used by the kit
     * editor; everything else stays locked.
     */
    public boolean isEditable(int slot) {
        return false;
    }

    public void open(Player player) {
        this.viewer = player;
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, size(), title());
        }
        redraw();
        player.openInventory(inventory);
    }

    /** Rebuilds and repaints without closing the inventory (no flicker). */
    public void redraw() {
        if (viewer == null) {
            return;
        }
        buttons.clear();
        build(viewer);
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < size(); slot++) {
            Button button = buttons.get(slot);
            if (isEditable(slot) && button == null) {
                continue;
            }
            inventory.setItem(slot, button == null ? null : button.icon());
        }
    }

    /** Called after the inventory closes. */
    public void onClose(Player player) {
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, size(), title());
        }
        return inventory;
    }
}
