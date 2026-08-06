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

/** The rank shop: every purchasable rank with its price and perks. */
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
            items.add(MenuItem.of(icon(rank, current), event -> buy(rank)));
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
                    .replace("<price>", Core.economy().format(rank.price())));
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

    private void buy(Rank rank) {
        RankService.PurchaseResult result = Core.ranks().buy(viewer, rank.id());
        switch (result) {
            case SUCCESS -> {
                Core.messages().send(viewer, "rank.buy-success",
                        "<rank>", rank.displayName(), "<price>", Core.economy().format(rank.price()));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                Core.tabs().refreshAll();
                build();
                render();
            }
            case ALREADY_OWNED -> deny("rank.buy-already-owned");
            case NOT_ENOUGH_MONEY -> deny("rank.buy-too-expensive",
                    "<price>", Core.economy().format(rank.price()));
            case STAFF_LOCKED -> deny("rank.buy-staff-locked");
            case NOT_PURCHASABLE -> deny("rank.buy-not-purchasable");
            default -> deny("rank.buy-failed");
        }
    }

    private void deny(String key, Object... placeholders) {
        Core.messages().send(viewer, key, placeholders);
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
    }

    /** Shown when the rank has no icon configured. */
    public static Material defaultIcon() {
        return Material.PAPER;
    }
}
