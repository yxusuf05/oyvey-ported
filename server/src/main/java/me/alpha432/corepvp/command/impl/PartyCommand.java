package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.kit.Kit;
import me.alpha432.corepvp.party.Party;
import me.alpha432.corepvp.party.PartyService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** {@code /party create|invite|join|leave|kick|list|chat|split|ffa|disband} */
public final class PartyCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;
    private final PartyService parties;

    public PartyCommand(CorePvPPlugin plugin) {
        super(plugin.messages(), null, true);
        this.plugin = plugin;
        this.parties = plugin.parties();
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            list(player);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> parties.create(player);
            case "leave" -> parties.leave(player);
            case "disband" -> parties.disband(player);
            case "list", "info" -> list(player);
            case "invite" -> withTarget(player, args, parties::invite);
            case "join" -> withTarget(player, args, (self, target) -> parties.join(self, target));
            case "kick" -> withTarget(player, args, parties::kick);
            case "chat", "c" -> chat(player, args);
            case "split" -> parties.split(player, kitFrom(player, args));
            case "ffa" -> parties.freeForAll(player, kitFrom(player, args));
            default -> messages.send(player, "party.usage");
        }
    }

    private void withTarget(Player player, String[] args, java.util.function.BiConsumer<Player, Player> action) {
        if (args.length < 2) {
            messages.send(player, "party.target-usage", Messages.of("action", args[0]));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "general.player-not-found", Messages.of("input", args[1]));
            return;
        }
        action.accept(player, target);
    }

    private void chat(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "party.chat-usage");
            return;
        }
        parties.chat(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
    }

    private Kit kitFrom(Player player, String[] args) {
        if (args.length >= 2) {
            Kit kit = plugin.kits().byId(args[1]);
            if (kit != null) {
                return kit;
            }
            messages.send(player, "kit.not-found", Messages.of("input", args[1]));
            return null;
        }
        List<Kit> kits = plugin.kits().enabled();
        return kits.isEmpty() ? null : kits.get(0);
    }

    private void list(Player player) {
        Party party = parties.partyOf(player);
        if (party == null) {
            messages.send(player, "party.not-in");
            return;
        }
        Player leader = Bukkit.getPlayer(party.leader());
        List<String> names = new ArrayList<>();
        for (UUID member : party.members()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) {
                names.add(online.getName());
            }
        }
        player.sendMessage(messages.render("party.list-header",
                Messages.of("leader", leader == null ? "?" : leader.getName()),
                Messages.of("size", party.size())));
        player.sendMessage(messages.render("party.list-members",
                Messages.of("members", String.join(", ", names))));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length <= 1) {
            return List.of("create", "invite", "join", "leave", "kick", "list", "chat", "split", "ffa", "disband");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("split") || sub.equals("ffa")) {
                return plugin.kits().ids();
            }
            if (sub.equals("invite") || sub.equals("join") || sub.equals("kick")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            }
        }
        return List.of();
    }
}
