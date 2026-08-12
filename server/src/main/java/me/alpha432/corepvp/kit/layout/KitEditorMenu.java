package me.alpha432.corepvp.kit.layout;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.menu.Button;
import me.alpha432.corepvp.menu.Menu;
import me.alpha432.corepvp.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Pick a kit to edit, then which of your layouts to edit. */
public final class KitEditorMenu extends Menu {

    private final CorePvPPlugin plugin;
    private final Messages messages;

    public KitEditorMenu(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    @Override
    public Component title() {
        return messages.render("editor.title");
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    protected void build(Player viewer) {
        int slot = 0;
        for (Kit kit : plugin.kits().enabled()) {
            if (!kit.flags().editable()) {
                continue;
            }
            int saved = plugin.layouts().orderedLayouts(viewer.getUniqueId(), kit.id()).size();
            set(slot++, Button.of(ItemBuilder.copyOf(kit.icon())
                    .name(messages.render("editor.kit-name", Messages.of("kit", kit.displayName())))
                    .loreComponents(messages.renderList("editor.kit-lore",
                            Messages.of("saved", saved),
                            Messages.of("max", KitLayoutService.MAX_LAYOUTS)))
                    .build(),
                    click -> new LayoutSelectMenu(plugin, kit).open(click.player())));
        }

        if (slot == 0) {
            set(13, Button.display(ItemBuilder.of(Material.BARRIER)
                    .name(messages.render("editor.no-kits")).build()));
        }
    }
}
