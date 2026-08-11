package me.alpha432.corepvp.match.snapshot;

import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.menu.Button;
import me.alpha432.corepvp.menu.Menu;
import me.alpha432.corepvp.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Shows how one player's inventory looked when the match ended. */
public final class SnapshotMenu extends Menu {

    private final Messages messages;
    private final MatchSnapshot snapshot;
    private final PlayerSnapshot player;

    public SnapshotMenu(Messages messages, MatchSnapshot snapshot, PlayerSnapshot player) {
        this.messages = messages;
        this.snapshot = snapshot;
        this.player = player;
    }

    @Override
    public Component title() {
        return messages.render("snapshot.title", Messages.of("player", player.name()));
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    protected void build(Player viewer) {
        ItemStack[] contents = player.inventoryItems();
        // Rows 0-2 hold the main inventory, row 3 the hotbar - the same layout
        // the player saw in their own inventory screen.
        for (int slot = 9; slot < Math.min(36, contents.length); slot++) {
            put(slot - 9, contents[slot]);
        }
        for (int slot = 0; slot < Math.min(9, contents.length); slot++) {
            put(27 + slot, contents[slot]);
        }

        ItemStack[] armor = player.armorItems();
        for (int i = 0; i < armor.length && i < 4; i++) {
            // Bukkit stores armour boots-first; showing it helmet-first reads
            // the way it looks on the player.
            put(36 + (3 - i), armor[i]);
        }

        set(49, Button.display(ItemBuilder.of(Material.PAPER)
                .name(messages.render("snapshot.summary-name", Messages.of("player", player.name())))
                .loreComponents(messages.renderList("snapshot.summary-lore",
                        Messages.of("kit", snapshot.kitName()),
                        Messages.of("result", messages.raw(player.winner()
                                ? "snapshot.won" : "snapshot.lost")),
                        Messages.of("health", String.format("%.1f", player.health() / 2.0D)),
                        Messages.of("hits", player.hits()),
                        Messages.of("combo", player.longestCombo()),
                        Messages.of("potions", player.potionsThrown()),
                        Messages.of("accuracy", player.potionAccuracy()),
                        Messages.of("crystals", player.crystalsPlaced()),
                        Messages.of("totems", player.totemsPopped())))
                .build()));
    }

    private void put(int slot, ItemStack stack) {
        if (stack != null && stack.getType() != Material.AIR) {
            set(slot, Button.display(stack));
        }
    }
}
