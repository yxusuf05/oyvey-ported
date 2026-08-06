package me.alpha432.network.practice.menu;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.Menu;
import me.alpha432.network.practice.PracticePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** The practice hub menu: pick unranked, ranked, FFA, the kit editor or the statistics. */
public final class PracticeMenu extends Menu {

    public PracticeMenu(PracticePlugin plugin, Player viewer) {
        super(plugin.messages().get("menu.practice-title"), 3);

        set(10, ItemBuilder.of(Material.IRON_SWORD)
                        .name(plugin.messages().raw("menu.unranked-name"))
                        .lore(plugin.messages().rawList("menu.unranked-lore"))
                        .hideAttributes().build(),
                event -> new QueueMenu(plugin, viewer, false).open(viewer));

        set(12, ItemBuilder.of(Material.DIAMOND_SWORD)
                        .name(plugin.messages().raw("menu.ranked-name"))
                        .lore(plugin.messages().rawList("menu.ranked-lore"))
                        .hideAttributes().glow().build(),
                event -> new QueueMenu(plugin, viewer, true).open(viewer));

        set(14, ItemBuilder.of(Material.NETHERITE_AXE)
                        .name(plugin.messages().raw("menu.ffa-name"))
                        .lore(plugin.messages().rawList("menu.ffa-lore"))
                        .hideAttributes().build(),
                event -> new FfaMenu(plugin, viewer).open(viewer));

        set(16, ItemBuilder.of(Material.ANVIL)
                        .name(plugin.messages().raw("menu.editor-name"))
                        .lore(plugin.messages().rawList("menu.editor-lore"))
                        .hideAttributes().build(),
                event -> new KitEditorMenu(plugin, viewer).open(viewer));

        set(21, ItemBuilder.of(Material.BOOK)
                        .name(plugin.messages().raw("menu.stats-name"))
                        .lore(plugin.messages().rawList("menu.stats-lore"))
                        .hideAttributes().build(),
                event -> new StatsMenu(plugin, viewer, viewer.getUniqueId(), viewer.getName())
                        .open(viewer));

        set(23, ItemBuilder.of(Material.GOLD_INGOT)
                        .name(plugin.messages().raw("menu.leaderboard-name"))
                        .lore(plugin.messages().rawList("menu.leaderboard-lore"))
                        .hideAttributes().build(),
                event -> new LeaderboardMenu(plugin, viewer).open(viewer));

        fill(filler());
    }
}
