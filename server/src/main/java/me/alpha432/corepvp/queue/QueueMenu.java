package me.alpha432.corepvp.queue;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.menu.Button;
import me.alpha432.corepvp.menu.Menu;
import me.alpha432.corepvp.profile.KitStats;
import me.alpha432.corepvp.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Kit picker for the unranked and ranked queues, with live player counts. */
public final class QueueMenu extends Menu {

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final boolean ranked;

    public QueueMenu(CorePvPPlugin plugin, boolean ranked) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.ranked = ranked;
    }

    @Override
    public Component title() {
        return messages.render(ranked ? "queue.menu.ranked-title" : "queue.menu.title");
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    protected void build(Player viewer) {
        List<Kit> kits = ranked ? plugin.kits().ranked() : plugin.kits().enabled();

        int slot = 0;
        for (Kit kit : kits) {
            KitStats stats = plugin.profiles().require(viewer).kit(kit.id());
            int waiting = plugin.queues().size(QueueKey.solo(kit.id(), ranked));
            int playing = plugin.matches().countsByKit().getOrDefault(kit.id(), 0);

            set(slot++, Button.of(ItemBuilder.copyOf(kit.icon())
                    .name(messages.render("queue.menu.entry-name",
                            Messages.of("kit", kit.displayName())))
                    .loreComponents(messages.renderList(
                            ranked ? "queue.menu.ranked-entry-lore" : "queue.menu.entry-lore",
                            Messages.of("queued", waiting),
                            Messages.of("playing", playing * 2),
                            Messages.of("elo", stats.elo()),
                            Messages.of("wins", stats.wins()),
                            Messages.of("losses", stats.losses()),
                            Messages.of("combat", kit.combatMode().name().toLowerCase(java.util.Locale.ROOT))))
                    .build(),
                    click -> {
                        click.player().closeInventory();
                        plugin.queues().join(click.player(), kit, ranked);
                    }));
        }

        if (kits.isEmpty()) {
            set(13, Button.display(ItemBuilder.of(Material.BARRIER)
                    .name(messages.render("queue.menu.empty"))
                    .build()));
        }
    }
}
