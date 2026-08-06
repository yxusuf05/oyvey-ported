package me.alpha432.network.smp.shop;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.core.util.Sounds;
import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Items of one shop category. Left click buys, right click sells; holding shift multiplies the
 * amount by a full stack.
 */
public final class CategoryMenu extends PagedMenu {

    private final SmpPlugin plugin;
    private final Player viewer;
    private final ShopService.Category category;

    public CategoryMenu(SmpPlugin plugin, Player viewer, ShopService.Category category) {
        super(plugin.messages().get("shop.category-title", "<category>", category.displayName()), 6);
        this.plugin = plugin;
        this.viewer = viewer;
        this.category = category;
        build();
    }

    private void build() {
        List<MenuItem> items = new ArrayList<>();
        for (ShopService.Entry entry : category.entries()) {
            items.add(MenuItem.of(icon(entry), event -> {
                boolean bulk = event.getClick() == ClickType.SHIFT_LEFT
                        || event.getClick() == ClickType.SHIFT_RIGHT;
                if (event.isRightClick()) {
                    sell(entry, bulk);
                } else {
                    buy(entry, bulk);
                }
            }));
        }
        content(items);
    }

    private ItemStack icon(ShopService.Entry entry) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>────────────");
        if (entry.canBuy()) {
            lore.add(plugin.messages().raw("shop.lore-buy")
                    .replace("<price>", Core.economy().format(entry.buyPrice()))
                    .replace("<amount>", String.valueOf(entry.amount())));
        }
        if (entry.canSell()) {
            lore.add(plugin.messages().raw("shop.lore-sell")
                    .replace("<price>", Core.economy().format(plugin.shop().sellPrice(viewer, entry)))
                    .replace("<amount>", String.valueOf(entry.amount())));
        }
        lore.add("<dark_gray>────────────");
        lore.add(plugin.messages().raw("shop.lore-hint"));
        return ItemBuilder.of(entry.display().clone())
                .amount(entry.amount())
                .lore(lore)
                .hideAttributes()
                .build();
    }

    private void buy(ShopService.Entry entry, boolean bulk) {
        if (!entry.canBuy()) {
            deny("shop.not-buyable");
            return;
        }
        int amount = bulk ? entry.material().getMaxStackSize() : entry.amount();
        double price = entry.buyPrice() / entry.amount() * amount;
        PlayerProfile profile = Core.profiles().get(viewer);
        if (!Core.economy().withdraw(profile, price)) {
            deny("shop.too-expensive", "<price>", Core.economy().format(price));
            return;
        }
        ItemStack stack = entry.display().clone();
        stack.setAmount(amount);
        viewer.getInventory().addItem(stack).values()
                .forEach(rest -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), rest));
        Core.profiles().save(profile);
        plugin.messages().send(viewer, "shop.bought",
                "<amount>", String.valueOf(amount),
                "<item>", entry.material().name(),
                "<price>", Core.economy().format(price));
        Sounds.play(viewer, plugin.getConfig().getString("shop.buy-sound", ""), 1f, 1.2f);
        refreshPrices();
    }

    private void sell(ShopService.Entry entry, boolean bulk) {
        if (!entry.canSell()) {
            deny("shop.not-sellable");
            return;
        }
        int wanted = bulk ? Integer.MAX_VALUE : entry.amount();
        int removed = removeItems(entry, wanted);
        if (removed == 0) {
            deny("shop.nothing-to-sell", "<item>", entry.material().name());
            return;
        }
        double payout = plugin.shop().sellPrice(viewer, entry) / entry.amount() * removed;
        PlayerProfile profile = Core.profiles().get(viewer);
        Core.economy().deposit(profile, payout);
        Core.profiles().save(profile);
        plugin.messages().send(viewer, "shop.sold",
                "<amount>", String.valueOf(removed),
                "<item>", entry.material().name(),
                "<price>", Core.economy().format(payout));
        Sounds.play(viewer, plugin.getConfig().getString("shop.sell-sound", ""), 1f, 1f);
        refreshPrices();
    }

    /** Removes up to {@code wanted} matching items and reports how many actually went. */
    private int removeItems(ShopService.Entry entry, int wanted) {
        int removed = 0;
        ItemStack[] contents = viewer.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && removed < wanted; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != entry.material()) {
                continue;
            }
            int take = Math.min(stack.getAmount(), wanted - removed);
            removed += take;
            if (take >= stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        if (removed > 0) {
            viewer.getInventory().setStorageContents(contents);
        }
        return removed;
    }

    private void refreshPrices() {
        build();
        render();
    }

    private void deny(String key, Object... placeholders) {
        plugin.messages().send(viewer, key, placeholders);
        Sounds.play(viewer, plugin.getConfig().getString("shop.deny-sound", ""), 1f, 1f);
    }
}
