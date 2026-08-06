package me.alpha432.network.practice.menu;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.ffa.FfaService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Map selection for the free-for-all arenas. */
public final class FfaMenu extends PagedMenu {

    public FfaMenu(PracticePlugin plugin, Player viewer) {
        super(plugin.messages().get("menu.ffa-title"), 4);

        List<MenuItem> items = new ArrayList<>();
        for (FfaService.FfaArena arena : plugin.ffa().all()) {
            List<String> lore = new ArrayList<>();
            lore.add(plugin.messages().raw("menu.ffa-kit").replace("<kit>", arena.kit()));
            lore.add(plugin.messages().raw("menu.ffa-players")
                    .replace("<count>", String.valueOf(plugin.ffa().playerCount(arena.id()))));
            if (arena.spawn() == null) {
                lore.add(plugin.messages().raw("menu.ffa-unset"));
            } else {
                lore.add(plugin.messages().raw("menu.ffa-click"));
            }
            items.add(MenuItem.of(ItemBuilder.of(arena.icon())
                    .name(arena.displayName())
                    .lore(lore)
                    .hideAttributes().build(), event -> {
                viewer.closeInventory();
                plugin.joinFfa(viewer, arena);
            }));
        }
        content(items);
    }
}
