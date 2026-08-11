package me.alpha432.corepvp.command.impl;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.command.SimpleCommand;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.match.snapshot.MatchSnapshot;
import me.alpha432.corepvp.match.snapshot.PlayerSnapshot;
import me.alpha432.corepvp.match.snapshot.SnapshotMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /inv <id> <player>} - opens a post-match inventory.
 *
 * <p>Reached by clicking the link in the end-of-match message, which runs this
 * command. Adventure's callback click events are not available in the bundled
 * version, so the link points at a real command.
 */
public final class InvCommand extends SimpleCommand {

    private final CorePvPPlugin plugin;

    public InvCommand(CorePvPPlugin plugin) {
        super(plugin.messages(), null, true);
        this.plugin = plugin;
    }

    @Override
    protected void run(CommandSender sender, String label, String[] args) {
        Player player = (Player) sender;
        if (args.length < 2) {
            messages.send(player, "snapshot.usage");
            return;
        }

        MatchSnapshot snapshot = plugin.snapshots().get(args[0]);
        if (snapshot == null) {
            messages.send(player, "snapshot.expired");
            return;
        }
        PlayerSnapshot target = snapshot.byName(args[1]);
        if (target == null) {
            messages.send(player, "snapshot.no-player",
                    Messages.of("input", args[1]),
                    Messages.of("players", String.join(", ", snapshot.names())));
            return;
        }
        new SnapshotMenu(messages, snapshot, target).open(player);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 2) {
            MatchSnapshot snapshot = plugin.snapshots().get(args[0]);
            return snapshot == null ? List.of() : snapshot.names();
        }
        return List.of();
    }
}
