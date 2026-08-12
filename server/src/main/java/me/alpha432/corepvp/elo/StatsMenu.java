package me.alpha432.corepvp.elo;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.menu.Button;
import me.alpha432.corepvp.menu.Menu;
import me.alpha432.corepvp.profile.KitStats;
import me.alpha432.corepvp.profile.Profile;
import me.alpha432.corepvp.util.ItemBuilder;
import me.alpha432.corepvp.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** One player's record, per kit. */
public final class StatsMenu extends Menu {

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final Profile profile;

    public StatsMenu(CorePvPPlugin plugin, Profile profile) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.profile = profile;
    }

    @Override
    public Component title() {
        return messages.render("stats.title", Messages.of("player", profile.name()));
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    protected void build(Player viewer) {
        int slot = 0;
        for (Kit kit : plugin.kits().enabled()) {
            KitStats stats = profile.kit(kit.id());
            set(slot++, Button.display(ItemBuilder.copyOf(kit.icon())
                    .name(messages.render("stats.kit-name", Messages.of("kit", kit.displayName())))
                    .loreComponents(messages.renderList("stats.kit-lore",
                            Messages.of("elo", stats.elo()),
                            Messages.of("wins", stats.wins()),
                            Messages.of("losses", stats.losses()),
                            Messages.of("winrate", Math.round(stats.winRate() * 100.0D)),
                            Messages.of("kills", stats.kills()),
                            Messages.of("deaths", stats.deaths())))
                    .build()));
        }

        set(31, Button.display(ItemBuilder.of(Material.PLAYER_HEAD)
                .name(messages.render("stats.summary-name", Messages.of("player", profile.name())))
                .loreComponents(messages.renderList("stats.summary-lore",
                        Messages.of("kills", profile.globalKills()),
                        Messages.of("deaths", profile.globalDeaths()),
                        Messages.of("rank", plugin.ranks().byId(profile.rankId()).display()),
                        Messages.of("first_seen", TimeUtil.compact(
                                System.currentTimeMillis() - profile.firstSeen()))))
                .build()));
    }
}
