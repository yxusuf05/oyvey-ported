package me.alpha432.network.practice.menu;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.kit.PracticeKit;
import me.alpha432.network.practice.stats.KitStats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Kit picker, then the top ratings of that kit. */
public final class LeaderboardMenu extends PagedMenu {

    public LeaderboardMenu(PracticePlugin plugin, Player viewer) {
        super(plugin.messages().get("menu.leaderboard-title"), 5);

        List<MenuItem> items = new ArrayList<>();
        for (PracticeKit kit : plugin.kits().ranked()) {
            items.add(MenuItem.of(ItemBuilder.of(kit.icon())
                            .name(kit.displayName())
                            .lore(plugin.messages().rawList("menu.leaderboard-kit-lore"))
                            .hideAttributes().build(),
                    event -> new KitLeaderboard(plugin, viewer, kit).open(viewer)));
        }
        content(items);
    }

    /** Top players of a single kit. */
    private static final class KitLeaderboard extends PagedMenu {

        private KitLeaderboard(PracticePlugin plugin, Player viewer, PracticeKit kit) {
            super(plugin.messages().get("menu.leaderboard-kit-title", "<kit>", kit.displayName()), 5);

            int limit = plugin.getConfig().getInt("leaderboard-size", 25);
            plugin.stats().leaderboard(kit.id(), limit).thenAccept(entries ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        List<MenuItem> items = new ArrayList<>();
                        int place = 1;
                        for (KitStats stats : entries) {
                            String name = Bukkit.getOfflinePlayer(stats.uuid()).getName();
                            List<String> lore = new ArrayList<>();
                            lore.add(plugin.messages().raw("stats.elo")
                                    .replace("<elo>", String.valueOf(stats.elo()))
                                    .replace("<tier>", plugin.tierName(stats.tier())));
                            lore.add(plugin.messages().raw("stats.record")
                                    .replace("<wins>", String.valueOf(stats.wins()))
                                    .replace("<losses>", String.valueOf(stats.losses())));
                            items.add(MenuItem.display(ItemBuilder.of(Material.PLAYER_HEAD)
                                    .skull(Bukkit.getOfflinePlayer(stats.uuid()))
                                    .name(plugin.messages().raw("menu.leaderboard-entry")
                                            .replace("<place>", String.valueOf(place++))
                                            .replace("<player>", name == null ? "?" : name))
                                    .lore(lore)
                                    .build()));
                        }
                        if (items.isEmpty()) {
                            items.add(MenuItem.display(ItemBuilder.of(Material.BARRIER)
                                    .name(plugin.messages().raw("stats.empty")).build()));
                        }
                        content(items);
                        render();
                    }));
        }
    }
}
