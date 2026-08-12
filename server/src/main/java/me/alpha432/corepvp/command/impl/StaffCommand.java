package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.staff.StaffService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** {@code /staff}, {@code /vanish} and {@code /freeze}. */
public final class StaffCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;
    private final StaffService staff;

    public StaffCommand(CorePvPPlugin plugin) {
        super(plugin.messages(), "corepvp.staff", true);
        this.plugin = plugin;
        this.staff = plugin.staff();
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        switch (label.toLowerCase(Locale.ROOT)) {
            case "vanish" -> {
                boolean vanish = !staff.isVanished(player);
                staff.setVanished(player, vanish);
                messages.send(player, vanish ? "staff.vanish-on" : "staff.vanish-off");
            }
            case "freeze" -> {
                if (args.length == 0) {
                    messages.send(player, "staff.freeze-usage");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    messages.send(player, "general.player-not-found", Messages.of("input", args[0]));
                    return;
                }
                staff.toggleFreeze(player, target);
            }
            default -> staff.toggleStaffMode(player);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("freeze") && args.length <= 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
