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
import org.bukkit.inventory.ItemStack;

/**
 * The editor itself: the top row is the hotbar the player is arranging, the
 * rows below hold everything the kit contains so items can be dragged in.
 */
public final class LayoutEditMenu extends Menu {

    private static final int HOTBAR_ROW = 0;
    private static final int SAVE_SLOT = 49;
    private static final int RESET_SLOT = 45;

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final Kit kit;
    private final int layoutSlot;

    public LayoutEditMenu(CorePvPPlugin plugin, Kit kit, int layoutSlot) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.kit = kit;
        this.layoutSlot = layoutSlot;
    }

    @Override
    public Component title() {
        return messages.render("editor.edit-title",
                Messages.of("kit", kit.displayName()),
                Messages.of("slot", layoutSlot + 1));
    }

    @Override
    public int rows() {
        return 6;
    }

    /** Only the top row can be rearranged; the rest is a palette. */
    @Override
    public boolean isEditable(int slot) {
        return slot >= 0 && slot < 9;
    }

    @Override
    protected void build(Player viewer) {
        KitLayout existing = plugin.layouts().layout(viewer.getUniqueId(), kit.id(), layoutSlot);
        ItemStack[] hotbar = existing != null && existing.matches(kit)
                ? existing.hotbar()
                : java.util.Arrays.copyOf(kit.contents(), 9);

        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = hotbar[slot];
            if (item != null && !item.getType().isAir()) {
                set(HOTBAR_ROW * 9 + slot, Button.display(item.clone()));
            }
        }

        set(18, Button.display(ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(messages.render("editor.palette-header")).build()));

        // Palette: one of each distinct item the kit hands out.
        int paletteSlot = 27;
        for (ItemStack item : kit.contents()) {
            if (item == null || item.getType().isAir() || paletteSlot >= 45) {
                continue;
            }
            boolean alreadyShown = false;
            for (int check = 27; check < paletteSlot; check++) {
                Button button = button(check);
                if (button != null && button.icon().isSimilar(item)) {
                    alreadyShown = true;
                    break;
                }
            }
            if (alreadyShown) {
                continue;
            }
            ItemStack copy = item.clone();
            set(paletteSlot++, Button.of(copy, click -> {
                // Click a palette item to drop it into the first free hotbar slot.
                for (int target = 0; target < 9; target++) {
                    if (getInventory().getItem(target) == null) {
                        getInventory().setItem(target, copy.clone());
                        return;
                    }
                }
            }));
        }

        set(RESET_SLOT, Button.of(ItemBuilder.of(Material.BARRIER)
                .name(messages.render("editor.reset-name"))
                .loreComponents(messages.renderList("editor.reset-lore"))
                .build(),
                click -> {
                    plugin.layouts().reset(click.player().getUniqueId(), kit.id(), layoutSlot);
                    messages.send(click.player(), "editor.reset", Messages.of("kit", kit.displayName()));
                    click.player().closeInventory();
                }));

        set(SAVE_SLOT, Button.of(ItemBuilder.of(Material.LIME_DYE)
                .name(messages.render("editor.save-name"))
                .loreComponents(messages.renderList("editor.save-lore"))
                .build(),
                click -> {
                    save(click.player());
                    click.player().closeInventory();
                }));
    }

    private void save(Player player) {
        ItemStack[] hotbar = new ItemStack[KitLayout.SIZE];
        for (int slot = 0; slot < KitLayout.SIZE; slot++) {
            ItemStack item = getInventory().getItem(slot);
            hotbar[slot] = item == null ? null : item.clone();
        }
        KitLayout layout = new KitLayout(layoutSlot, "Layout " + (layoutSlot + 1), hotbar);
        plugin.layouts().save(player.getUniqueId(), kit.id(), layout);
        plugin.layouts().select(player.getUniqueId(), kit.id(), layoutSlot);
        messages.send(player, "editor.saved",
                Messages.of("kit", kit.displayName()), Messages.of("slot", layoutSlot + 1));
    }

    /** Closing without pressing save keeps the arrangement anyway - less to lose. */
    @Override
    public void onClose(Player player) {
        save(player);
    }
}
