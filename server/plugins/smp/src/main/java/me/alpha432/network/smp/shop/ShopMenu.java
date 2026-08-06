package me.alpha432.network.smp.shop;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.Menu;
import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.entity.Player;

/** Category overview of the shop. */
public final class ShopMenu extends Menu {

    public ShopMenu(SmpPlugin plugin, Player viewer) {
        super(plugin.messages().get("shop.title"), plugin.getConfig().getInt("shop.rows", 3));
        for (ShopService.Category category : plugin.shop().categories()) {
            set(category.slot(),
                    ItemBuilder.of(category.icon())
                            .name(category.displayName())
                            .lore(plugin.messages().rawList("shop.category-lore").stream()
                                    .map(line -> line.replace("<count>", String.valueOf(category.entries().size())))
                                    .toList())
                            .hideAttributes()
                            .build(),
                    event -> new CategoryMenu(plugin, viewer, category).open(viewer));
        }
        fill(filler());
    }
}
