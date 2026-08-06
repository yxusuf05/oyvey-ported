package me.alpha432.network.practice.menu;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.arena.Arena;
import me.alpha432.network.practice.kit.PracticeKit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Map selection for one kit: a specific arena, or the random entry at the top. */
public final class MapMenu extends PagedMenu {

    public MapMenu(PracticePlugin plugin, Player viewer, PracticeKit kit, boolean ranked) {
        super(plugin.messages().get("menu.map-title", "<kit>", kit.displayName()), 5);

        List<MenuItem> items = new ArrayList<>();
        items.add(MenuItem.of(ItemBuilder.of(Material.ENDER_PEARL)
                .name(plugin.messages().raw("menu.map-random-name"))
                .lore(plugin.messages().rawList("menu.map-random-lore"))
                .hideAttributes().glow().build(), event -> {
            viewer.closeInventory();
            plugin.joinQueue(viewer, kit, ranked, null);
        }));

        for (Arena arena : plugin.arenas().supporting(kit.id())) {
            List<String> lore = new ArrayList<>();
            lore.add(plugin.messages().raw(arena.isOccupied()
                    ? "menu.map-occupied" : "menu.map-free"));
            lore.add(plugin.messages().raw("menu.map-click"));
            items.add(MenuItem.of(ItemBuilder.of(arena.isOccupied()
                            ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE)
                    .name("<yellow>" + arena.displayName())
                    .lore(lore)
                    .hideAttributes().build(), event -> {
                viewer.closeInventory();
                plugin.joinQueue(viewer, kit, ranked, arena.name());
            }));
        }

        if (items.size() == 1) {
            plugin.messages().send(viewer, "queue.no-arena", "<kit>", kit.displayName());
        }
        content(items);
    }
}
