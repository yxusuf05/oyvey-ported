package me.alpha432.network.practice.menu;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.stats.KitStats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Per kit record of one player. Reads from the database so it also works for offline players. */
public final class StatsMenu extends PagedMenu {

    public StatsMenu(PracticePlugin plugin, Player viewer, UUID target, String targetName) {
        super(plugin.messages().get("menu.stats-title", "<player>", targetName), 5);

        plugin.stats().statsOf(target).thenAccept(stats -> Bukkit.getScheduler().runTask(plugin, () -> {
            List<MenuItem> items = new ArrayList<>();
            stats.sort((a, b) -> Integer.compare(b.elo(), a.elo()));
            for (KitStats entry : stats) {
                Material icon = plugin.kits().get(entry.kit())
                        .map(kit -> kit.icon()).orElse(Material.PAPER);
                String kitName = plugin.kits().get(entry.kit())
                        .map(kit -> kit.displayName()).orElse(entry.kit());
                List<String> lore = new ArrayList<>();
                lore.add("<dark_gray>────────────");
                lore.add(plugin.messages().raw("stats.elo")
                        .replace("<elo>", String.valueOf(entry.elo()))
                        .replace("<tier>", plugin.tierName(entry.tier())));
                lore.add(plugin.messages().raw("stats.record")
                        .replace("<wins>", String.valueOf(entry.wins()))
                        .replace("<losses>", String.valueOf(entry.losses())));
                lore.add(plugin.messages().raw("stats.winrate")
                        .replace("<rate>", String.format("%.0f", entry.winRate() * 100)));
                lore.add(plugin.messages().raw("stats.streak")
                        .replace("<streak>", String.valueOf(entry.streak()))
                        .replace("<best>", String.valueOf(entry.bestStreak())));
                items.add(MenuItem.display(ItemBuilder.of(icon).name(kitName).lore(lore)
                        .hideAttributes().build()));
            }
            if (items.isEmpty()) {
                items.add(MenuItem.display(ItemBuilder.of(Material.BARRIER)
                        .name(plugin.messages().raw("stats.empty"))
                        .build()));
            }
            content(items);
            render();
        }));
    }
}
