package me.alpha432.corepvp.ffa;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.menu.Button;
import me.alpha432.corepvp.menu.Menu;
import me.alpha432.corepvp.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Picker for the open arenas. */
public final class FfaMenu extends Menu {

    private final CorePvPPlugin plugin;
    private final Messages messages;

    public FfaMenu(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    @Override
    public Component title() {
        return messages.render("ffa.menu.title");
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    protected void build(Player viewer) {
        int slot = 0;
        for (FfaArena arena : plugin.ffa().all()) {
            Kit kit = plugin.kits().byId(arena.kitId());
            set(slot++, Button.of(ItemBuilder.copyOf(kit == null
                            ? new org.bukkit.inventory.ItemStack(Material.IRON_SWORD)
                            : kit.icon())
                    .name(messages.render("ffa.menu.entry-name", Messages.of("arena", arena.id())))
                    .loreComponents(messages.renderList("ffa.menu.entry-lore",
                            Messages.of("kit", kit == null ? arena.kitId() : kit.displayName()),
                            Messages.of("players", plugin.ffa().population(arena.id()))))
                    .build(),
                    click -> {
                        click.player().closeInventory();
                        plugin.ffa().join(click.player(), arena);
                    }));
        }

        if (slot == 0) {
            set(13, Button.display(ItemBuilder.of(Material.BARRIER)
                    .name(messages.render("ffa.menu.empty")).build()));
        }
    }
}
