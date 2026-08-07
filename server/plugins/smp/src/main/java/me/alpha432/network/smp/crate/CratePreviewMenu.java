package me.alpha432.network.smp.crate;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Shows every reward of a crate together with its chance. */
public final class CratePreviewMenu extends PagedMenu {

    public CratePreviewMenu(SmpPlugin plugin, Player viewer, Crate crate) {
        super(plugin.messages().get("crate.preview-title", "<crate>", crate.displayName()), 5);

        double total = crate.totalWeight();
        List<MenuItem> items = new ArrayList<>();
        List<CrateReward> sorted = new ArrayList<>(crate.rewards());
        sorted.sort((a, b) -> Double.compare(a.weight(), b.weight()));

        for (CrateReward reward : sorted) {
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>────────────");
            lore.add(plugin.messages().raw("crate.preview-chance")
                    .replace("<chance>", String.format("%.2f", reward.chance(total))));
            if (!reward.items().isEmpty()) {
                lore.add(plugin.messages().raw("crate.preview-items")
                        .replace("<count>", String.valueOf(reward.items().size())));
            }
            if (reward.broadcast()) {
                lore.add(plugin.messages().raw("crate.preview-rare"));
            }
            items.add(MenuItem.display(ItemBuilder.of(reward.display().clone())
                    .name(reward.displayName())
                    .lore(lore)
                    .hideAttributes()
                    .build()));
        }
        content(items);
    }
}
