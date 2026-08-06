package me.alpha432.network.smp.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.menu.ItemBuilder;
import me.alpha432.network.core.menu.MenuItem;
import me.alpha432.network.core.menu.PagedMenu;
import me.alpha432.network.core.profile.PlayerProfile;
import me.alpha432.network.core.text.Placeholders;
import me.alpha432.network.core.util.Sounds;
import me.alpha432.network.smp.SmpPlugin;
import me.alpha432.network.smp.kit.Kit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** {@code /kit [name]} — claim a kit or browse them in a menu. */
public final class KitCommand extends BaseCommand {

    private final SmpPlugin smp;

    public KitCommand(SmpPlugin plugin) {
        super(plugin, "kit");
        this.smp = plugin;
        playerOnly();
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            new KitMenu(smp, player).open(player);
            return;
        }
        smp.kits().get(args[0]).ifPresentOrElse(
                kit -> claim(smp, player, kit),
                () -> smp.messages().send(sender, "kit.unknown", "<name>", args[0]));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length <= 1 ? smp.kits().names() : List.of();
    }

    /** Shared by the command and the menu. */
    static void claim(SmpPlugin smp, Player player, Kit kit) {
        if (kit.permission() != null && !player.hasPermission(kit.permission())) {
            smp.messages().send(player, "kit.no-permission", "<name>", kit.displayName());
            return;
        }
        long remaining = smp.kits().remaining(player, kit);
        if (remaining == Long.MAX_VALUE) {
            smp.messages().send(player, "kit.already-claimed", "<name>", kit.displayName());
            return;
        }
        if (remaining > 0) {
            smp.messages().send(player, "kit.cooldown",
                    "<name>", kit.displayName(),
                    "<time>", Placeholders.formatSeconds(remaining));
            return;
        }
        if (kit.price() > 0) {
            PlayerProfile profile = Core.profiles().get(player);
            if (!Core.economy().withdraw(profile, kit.price())) {
                smp.messages().send(player, "kit.too-expensive",
                        "<price>", Core.economy().format(kit.price()));
                return;
            }
            Core.profiles().save(profile);
        }
        smp.kits().give(player, kit);
        smp.messages().send(player, "kit.claimed", "<name>", kit.displayName());
        Sounds.play(player, smp.getConfig().getString("kit.claim-sound", ""), 1f, 1.2f);
    }

    /** Kit overview with cooldown state per entry. */
    private static final class KitMenu extends PagedMenu {

        private final SmpPlugin smp;
        private final Player viewer;

        private KitMenu(SmpPlugin plugin, Player viewer) {
            super(plugin.messages().get("kit.menu-title"), 5);
            this.smp = plugin;
            this.viewer = viewer;
            build();
        }

        private void build() {
            List<MenuItem> items = new ArrayList<>();
            for (Kit kit : smp.kits().all()) {
                items.add(MenuItem.of(icon(kit), event -> {
                    viewer.closeInventory();
                    claim(smp, viewer, kit);
                }));
            }
            content(items);
        }

        private ItemStack icon(Kit kit) {
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>────────────");
            lore.add(smp.messages().raw("kit.menu-contents")
                    .replace("<count>", String.valueOf(kit.items().size())));
            if (kit.price() > 0) {
                lore.add(smp.messages().raw("kit.menu-price")
                        .replace("<price>", Core.economy().format(kit.price())));
            }
            boolean allowed = kit.permission() == null || viewer.hasPermission(kit.permission());
            long remaining = smp.kits().remaining(viewer, kit);
            if (!allowed) {
                lore.add(smp.messages().raw("kit.menu-locked"));
            } else if (remaining == Long.MAX_VALUE) {
                lore.add(smp.messages().raw("kit.menu-claimed"));
            } else if (remaining > 0) {
                lore.add(smp.messages().raw("kit.menu-cooldown")
                        .replace("<time>", Placeholders.formatSeconds(remaining)));
            } else {
                lore.add(smp.messages().raw("kit.menu-ready"));
            }
            ItemBuilder builder = ItemBuilder.of(kit.icon())
                    .name(kit.displayName())
                    .lore(lore)
                    .hideAttributes();
            if (allowed && remaining == 0) {
                builder.glow();
            }
            return builder.build();
        }
    }
}
