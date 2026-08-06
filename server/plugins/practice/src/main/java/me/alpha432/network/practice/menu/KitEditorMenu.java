package me.alpha432.network.practice.menu;

import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.Menu;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.core.util.Sounds;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.kit.PracticeKit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Lists the kits a player may re-arrange and opens the layout editor for one of them. */
public final class KitEditorMenu extends PagedMenu {

    private final PracticePlugin plugin;
    private final Player viewer;

    public KitEditorMenu(PracticePlugin plugin, Player viewer) {
        super(plugin.messages().get("menu.editor-title"), 5);
        this.plugin = plugin;
        this.viewer = viewer;
        build();
    }

    private void build() {
        int limit = plugin.kits().customKitLimit(viewer);
        List<MenuItem> items = new ArrayList<>();
        for (PracticeKit kit : plugin.kits().all()) {
            List<String> lore = new ArrayList<>();
            lore.add(plugin.messages().raw("menu.editor-slots")
                    .replace("<used>", String.valueOf(plugin.kits().layoutCount(viewer)))
                    .replace("<limit>", String.valueOf(limit)));
            lore.add(plugin.kits().hasLayout(viewer, kit)
                    ? plugin.messages().raw("menu.editor-custom")
                    : plugin.messages().raw("menu.editor-default"));
            lore.add(plugin.messages().raw("menu.editor-hint"));
            items.add(MenuItem.of(
                    ItemBuilder.of(kit.icon()).name(kit.displayName()).lore(lore)
                            .hideAttributes().build(),
                    event -> {
                        if (event.isRightClick()) {
                            plugin.kits().resetLayout(viewer, kit);
                            plugin.messages().send(viewer, "editor.reset", "<kit>", kit.displayName());
                            build();
                            render();
                            return;
                        }
                        if (limit <= 0) {
                            plugin.messages().send(viewer, "editor.no-slots");
                            Sounds.play(viewer, plugin.getConfig()
                                    .getString("sounds.deny", ""), 1f, 1f);
                            return;
                        }
                        new LayoutEditor(plugin, viewer, kit).open(viewer);
                    }));
        }
        content(items);
    }

    /**
     * The actual editor: a 36 slot inventory the player rearranges freely. Clicks are allowed
     * here, and the layout is stored when the inventory closes.
     */
    private static final class LayoutEditor extends Menu {

        private final PracticePlugin plugin;
        private final Player viewer;
        private final PracticeKit kit;

        private LayoutEditor(PracticePlugin plugin, Player viewer, PracticeKit kit) {
            super(plugin.messages().get("menu.layout-title", "<kit>", kit.displayName()), 4);
            this.plugin = plugin;
            this.viewer = viewer;
            this.kit = kit;
            cancelClicks(false);
            ItemStack[] contents = plugin.kits().contentsFor(viewer, kit);
            for (int slot = 0; slot < size() && slot < contents.length; slot++) {
                getInventory().setItem(slot, contents[slot]);
            }
        }

        @Override
        public void handleClose(InventoryCloseEvent event) {
            ItemStack[] contents = new ItemStack[36];
            for (int slot = 0; slot < size() && slot < contents.length; slot++) {
                contents[slot] = getInventory().getItem(slot);
            }
            if (plugin.kits().saveLayout(viewer, kit, contents)) {
                plugin.messages().send(viewer, "editor.saved", "<kit>", kit.displayName());
            } else {
                plugin.messages().send(viewer, "editor.no-slots");
            }
        }
    }
}
