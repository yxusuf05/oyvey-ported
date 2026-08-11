package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.duel.DuelService;
import me.alpha432.corepvp.kit.Kit;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** {@code /duel <player> [kit]} and {@code /duel accept <player>} */
public final class DuelCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;
    private final DuelService duels;

    public DuelCommand(CorePvPPlugin plugin, DuelService duels) {
        super(plugin.messages(), null, true);
        this.plugin = plugin;
        this.duels = duels;
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            messages.send(player, "duel.usage");
            return;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            if (args.length < 2) {
                messages.send(player, "duel.accept-usage");
                return;
            }
            Player from = Bukkit.getPlayerExact(args[1]);
            if (from == null) {
                messages.send(player, "general.player-not-found", Messages.of("input", args[1]));
                return;
            }
            duels.accept(player, from);
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(player, "general.player-not-found", Messages.of("input", args[0]));
            return;
        }

        Kit kit = args.length >= 2
                ? plugin.kits().byId(args[1])
                : firstEnabledKit();
        if (kit == null) {
            messages.send(player, "kit.not-found",
                    Messages.of("input", args.length >= 2 ? args[1] : "-"));
            return;
        }
        duels.send(player, target, kit);
    }

    private Kit firstEnabledKit() {
        List<Kit> enabled = plugin.kits().enabled();
        return enabled.isEmpty() ? null : enabled.get(0);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length <= 1) {
            List<String> options = new ArrayList<>();
            options.add("accept");
            Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accept") && sender instanceof Player player) {
            return duels.sendersFor(player);
        }
        if (args.length == 2) {
            return plugin.kits().ids();
        }
        return List.of();
    }
}
