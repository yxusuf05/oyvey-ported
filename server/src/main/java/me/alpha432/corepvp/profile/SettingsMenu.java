package me.alpha432.corepvp.profile;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.menu.Button;
import me.alpha432.corepvp.menu.Menu;
import me.alpha432.corepvp.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

/** The player's own toggles. */
public final class SettingsMenu extends Menu {

    private final CorePvPPlugin plugin;
    private final Messages messages;

    public SettingsMenu(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    @Override
    public Component title() {
        return messages.render("settings.title");
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    protected void build(Player viewer) {
        Profile profile = plugin.profiles().require(viewer);

        int slot = 10;
        for (ProfileSettings.Flag flag : ProfileSettings.Flag.values()) {
            boolean enabled = profile.settings().get(flag);
            String key = flag.name().toLowerCase(Locale.ROOT).replace('_', '-');

            set(slot++, Button.of(ItemBuilder.of(enabled ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(messages.render("settings.entry-name",
                            Messages.of("setting", messages.raw("settings.names." + key)),
                            Messages.of("state", messages.raw(enabled
                                    ? "settings.on" : "settings.off"))))
                    .loreComponents(messages.renderList("settings.entry-lore"))
                    .build(),
                    click -> {
                        boolean now = profile.settings().toggle(flag);
                        profile.markDirty();
                        applySideEffects(click.player(), flag, now);
                        redraw();
                    }));
        }
    }

    /** Some toggles change the world immediately, not just on the next login. */
    private void applySideEffects(Player player, ProfileSettings.Flag flag, boolean enabled) {
        if (flag == ProfileSettings.Flag.PLAYER_VISIBILITY) {
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (other.equals(player)) {
                    continue;
                }
                if (enabled) {
                    player.showPlayer(plugin, other);
                } else {
                    player.hidePlayer(plugin, other);
                }
            }
        }
        if (flag == ProfileSettings.Flag.SCOREBOARD && plugin.boards().board(player) != null) {
            plugin.boards().board(player).visible(enabled);
        }
    }
}
