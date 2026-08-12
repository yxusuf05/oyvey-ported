package me.alpha432.corepvp.elo;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.menu.Button;
import me.alpha432.corepvp.menu.Menu;
import me.alpha432.corepvp.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Top players for one kit, with a row of kits to switch between. */
public final class LeaderboardMenu extends Menu {

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private String kitId;

    public LeaderboardMenu(CorePvPPlugin plugin, String kitId) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.kitId = kitId;
    }

    @Override
    public Component title() {
        return messages.render("leaderboard.title", Messages.of("kit", kitId));
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    protected void build(Player viewer) {
        List<LeaderboardService.Row> rows = plugin.leaderboards().top(kitId);

        if (rows.isEmpty()) {
            set(22, Button.display(ItemBuilder.of(Material.BARRIER)
                    .name(messages.render("leaderboard.empty"))
                    .build()));
        }

        for (int i = 0; i < rows.size() && i < 36; i++) {
            LeaderboardService.Row row = rows.get(i);
            Material icon = switch (row.position()) {
                case 1 -> Material.GOLD_INGOT;
                case 2 -> Material.IRON_INGOT;
                case 3 -> Material.COPPER_INGOT;
                default -> Material.PAPER;
            };
            set(i, Button.display(ItemBuilder.of(icon)
                    .name(messages.render("leaderboard.entry-name",
                            Messages.of("position", row.position()),
                            Messages.of("player", row.name())))
                    .loreComponents(messages.renderList("leaderboard.entry-lore",
                            Messages.of("elo", row.elo()),
                            Messages.of("wins", row.wins()),
                            Messages.of("losses", row.losses())))
                    .build()));
        }

        // Bottom row: one icon per kit to switch the board.
        List<Kit> kits = new ArrayList<>(plugin.kits().enabled());
        for (int i = 0; i < kits.size() && i < 9; i++) {
            Kit kit = kits.get(i);
            set(45 + i, Button.of(ItemBuilder.copyOf(kit.icon())
                    .name(messages.render("leaderboard.switch",
                            Messages.of("kit", kit.displayName())))
                    .build(),
                    click -> {
                        kitId = kit.id();
                        plugin.leaderboards().refresh(kitId);
                        redraw();
                    }));
        }
    }
}
