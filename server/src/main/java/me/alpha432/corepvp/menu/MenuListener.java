package me.alpha432.corepvp.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** The one listener that drives every {@link Menu}. */
public final class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Menu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean topInventory = event.getRawSlot() < event.getInventory().getSize();
        int slot = event.getRawSlot();

        if (topInventory && menu.isEditable(slot)) {
            return;
        }

        // Shift-clicking from the player's own inventory can land in a locked
        // slot, so it is blocked too unless the menu is editable throughout.
        if (!topInventory && !event.isShiftClick()) {
            return;
        }

        event.setCancelled(true);
        if (!topInventory) {
            return;
        }

        Button button = menu.button(slot);
        if (button != null) {
            button.click(new MenuClick(player, menu, slot, event.getClick()));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Menu menu)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize && !menu.isEditable(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Menu menu && event.getPlayer() instanceof Player player) {
            menu.onClose(player);
        }
    }
}
