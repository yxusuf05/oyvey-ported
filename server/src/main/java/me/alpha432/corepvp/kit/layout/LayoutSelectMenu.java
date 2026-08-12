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

/** The layout slots for one kit: pick one to use, or click to edit it. */
public final class LayoutSelectMenu extends Menu {

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final Kit kit;

    public LayoutSelectMenu(CorePvPPlugin plugin, Kit kit) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.kit = kit;
    }

    @Override
    public Component title() {
        return messages.render("editor.select-title", Messages.of("kit", kit.displayName()));
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    protected void build(Player viewer) {
        int active = plugin.layouts().selectedSlot(viewer.getUniqueId(), kit.id());

        for (int slot = 0; slot < KitLayoutService.MAX_LAYOUTS; slot++) {
            int index = slot;
            KitLayout layout = plugin.layouts().layout(viewer.getUniqueId(), kit.id(), slot);
            boolean saved = layout != null && !layout.isEmpty();
            boolean selected = index == active;

            set(11 + slot, Button.of(ItemBuilder.of(saved ? Material.BOOK : Material.PAPER)
                    .name(messages.render("editor.slot-name",
                            Messages.of("slot", index + 1),
                            Messages.of("state", messages.raw(selected
                                    ? "editor.state-active"
                                    : saved ? "editor.state-saved" : "editor.state-empty"))))
                    .loreComponents(messages.renderList("editor.slot-lore"))
                    .build(),
                    click -> {
                        if (click.isRight()) {
                            plugin.layouts().select(click.player().getUniqueId(), kit.id(), index);
                            messages.send(click.player(), "editor.selected",
                                    Messages.of("kit", kit.displayName()),
                                    Messages.of("slot", index + 1));
                            redraw();
                            return;
                        }
                        new LayoutEditMenu(plugin, kit, index).open(click.player());
                    }));
        }

        set(22, Button.of(ItemBuilder.of(Material.ARROW)
                .name(messages.render("editor.back")).build(),
                click -> new KitEditorMenu(plugin).open(click.player())));
    }
}
