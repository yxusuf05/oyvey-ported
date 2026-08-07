package me.alpha432.network.core.rank;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The rank shop: every rank that can be bought in the web shop, with its price and perks. */
public final class RankMenu extends PagedMenu {

    private final Player viewer;

    public RankMenu(Player viewer) {
        super(Core.messages().get("rank.menu-title"), 6);
        this.viewer = viewer;
        build();
    }

    private void build() {
        Rank current = Core.ranks().of(viewer);
        List<MenuItem> items = new ArrayList<>();
        for (Rank rank : Core.ranks().purchasable()) {
            items.add(MenuItem.of(icon(rank, current), event -> showShop(rank)));
        }
        content(items);
    }

    private ItemStack icon(Rank rank, Rank current) {
        boolean owned = current.weight() >= rank.weight();
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>────────────────");
        lore.addAll(rank.perks());
        lore.add("<dark_gray>────────────────");
        if (owned) {
            lore.add(Core.messages().raw("rank.menu-owned"));
        } else if (current.staff()) {
            lore.add(Core.messages().raw("rank.menu-staff-locked"));
        } else {
            lore.add(Core.messages().raw("rank.menu-price")
                    .replace("<price>", Core.plugin().formatRealMoney(rank.price())));
            lore.add(Core.messages().raw("rank.menu-click"));
        }
        ItemBuilder builder = ItemBuilder.of(rank.icon())
                .name(rank.prefix() + rank.displayName())
                .lore(lore)
                .hideAttributes();
        if (owned) {
            builder.glow();
        }
        return builder.build();
    }

    /**
     * Ranks are sold for real money, so a click cannot hand one out. It shows the shop link
     * instead; the shop grants the rank by running {@code /rank set} through the console.
     */
    private void showShop(Rank rank) {
        viewer.closeInventory();
        Core.messages().send(viewer, "rank.shop-hint",
                "<rank>", rank.displayName(),
                "<price>", Core.plugin().formatRealMoney(rank.price()),
                "<url>", Core.plugin().shopUrl());
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    /** Shown when the rank has no icon configured. */
    public static Material defaultIcon() {
        return Material.PAPER;
    }
}
