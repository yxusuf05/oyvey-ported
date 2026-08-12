package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.state.PlayerState;
import me.alpha432.corepvp.survival.SurvivalService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything survival: {@code /survival}, {@code /home}, {@code /sethome},
 * {@code /delhome}, {@code /tpa} and {@code /tpaccept}.
 *
 * <p>One class registered under several command names, so each stays short to
 * type while the logic lives in one place.
 */
public final class SurvivalCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;
    private final SurvivalService survival;

    public SurvivalCommand(CorePvPPlugin plugin) {
        super(plugin.messages(), null, true);
        this.plugin = plugin;
        this.survival = plugin.survival();
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        switch (label.toLowerCase(Locale.ROOT)) {
            case "sethome" -> setHome(player, args);
            case "delhome" -> deleteHome(player, args);
            case "home" -> home(player, args);
            case "homes" -> listHomes(player);
            case "tpa" -> tpa(player, args);
            case "tpaccept" -> survival.acceptTeleport(player);
            default -> survival.enter(player);
        }
    }

    private void requireSurvival(Player player, Runnable action) {
        if (!plugin.states().is(player, PlayerState.SURVIVAL)) {
            messages.send(player, "survival.not-in");
            return;
        }
        action.run();
    }

    private void setHome(Player player, String[] args) {
        requireSurvival(player, () -> {
            String name = args.length > 0 ? args[0] : "home";
            if (survival.setHome(player, name)) {
                messages.send(player, "survival.home-set", Messages.of("name", name));
            }
        });
    }

    private void deleteHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0] : "home";
        if (survival.deleteHome(player, name)) {
            messages.send(player, "survival.home-deleted", Messages.of("name", name));
        } else {
            messages.send(player, "survival.home-missing", Messages.of("name", name));
        }
    }

    private void home(Player player, String[] args) {
        requireSurvival(player, () -> {
            String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
            Location location = survival.homes(player).get(name);
            if (location == null) {
                messages.send(player, "survival.home-missing", Messages.of("name", name));
                return;
            }
            player.teleport(location);
            messages.send(player, "survival.home-teleported", Messages.of("name", name));
        });
    }

    private void listHomes(Player player) {
        Map<String, Location> homes = survival.homes(player);
        if (homes.isEmpty()) {
            messages.send(player, "survival.no-homes");
            return;
        }
        messages.send(player, "survival.home-list",
                Messages.of("homes", String.join(", ", homes.keySet())),
                Messages.of("used", homes.size()),
                Messages.of("limit", survival.homeLimit(player)));
    }

    private void tpa(Player player, String[] args) {
        requireSurvival(player, () -> {
            if (args.length == 0) {
                messages.send(player, "survival.tpa-usage");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(player, "general.player-not-found", Messages.of("input", args[0]));
                return;
            }
            survival.requestTeleport(player, target);
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length > 1) {
            return List.of();
        }
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "home", "delhome" -> List.copyOf(survival.homes(player).keySet());
            case "tpa" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            default -> List.of();
        };
    }
}
