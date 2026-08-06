package me.alpha432.network.practice.menu;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.kit.PracticeKit;
import me.alpha432.network.practice.stats.KitStats;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit selection for the queue. A left click joins with a random map, a right click opens the
 * map list first.
 */
public final class QueueMenu extends PagedMenu {

    private final PracticePlugin plugin;
    private final Player viewer;
    private final boolean ranked;

    public QueueMenu(PracticePlugin plugin, Player viewer, boolean ranked) {
        super(plugin.messages().get(ranked ? "menu.queue-ranked-title" : "menu.queue-unranked-title"), 5);
        this.plugin = plugin;
        this.viewer = viewer;
        this.ranked = ranked;
        build();
    }

    private void build() {
        List<MenuItem> items = new ArrayList<>();
        for (PracticeKit kit : plugin.kits().all()) {
            if (ranked && !kit.ranked()) {
                continue;
            }
            items.add(MenuItem.of(icon(kit), event -> {
                if (event.isRightClick()) {
                    new MapMenu(plugin, viewer, kit, ranked).open(viewer);
                } else {
                    viewer.closeInventory();
                    plugin.joinQueue(viewer, kit, ranked, null);
                }
            }));
        }
        content(items);
    }

    private ItemStack icon(PracticeKit kit) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>────────────");
        lore.add(plugin.messages().raw("menu.queue-waiting")
                .replace("<count>", String.valueOf(plugin.queue().size(kit.id(), ranked))));
        lore.add(plugin.messages().raw("menu.queue-maps")
                .replace("<count>", String.valueOf(plugin.arenas().supporting(kit.id()).size())));
        if (ranked) {
            KitStats stats = plugin.stats().get(viewer.getUniqueId(), kit.id());
            lore.add(plugin.messages().raw("menu.queue-elo")
                    .replace("<elo>", String.valueOf(stats.elo()))
                    .replace("<tier>", plugin.tierName(stats.tier())));
        }
        if (plugin.kits().hasLayout(viewer, kit)) {
            lore.add(plugin.messages().raw("menu.queue-custom"));
        }
        lore.add("<dark_gray>────────────");
        lore.add(plugin.messages().raw("menu.queue-hint"));
        return ItemBuilder.of(kit.icon())
                .name(kit.displayName())
                .lore(lore)
                .hideAttributes()
                .build();
    }
}
