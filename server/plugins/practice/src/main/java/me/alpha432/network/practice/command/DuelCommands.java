package me.alpha432.network.practice.command;

import me.alpha432.network.core.command.BaseCommand;
import me.alpha432.network.core.command.CommandUtil;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.arena.Arena;
import me.alpha432.network.practice.kit.PracticeKit;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@code /duel <player> [kit]} and {@code /accept [player]} for direct challenges. */
public final class DuelCommands {

    /** target -> (challenger -> kit) */
    private static final Map<UUID, Map<UUID, String>> INVITES = new HashMap<>();

    private DuelCommands() {
    }

    public static void register(PracticePlugin plugin) {
        new DuelCommand(plugin).register();
        new AcceptCommand(plugin).register();
    }

    /** Removes every invite involving the player; called when they quit. */
    public static void forget(UUID uuid) {
        INVITES.remove(uuid);
        INVITES.values().forEach(map -> map.remove(uuid));
    }

    private static final class DuelCommand extends BaseCommand {

        private final PracticePlugin practice;

        private DuelCommand(PracticePlugin plugin) {
            super(plugin, "duel");
            this.practice = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            if (args.length == 0) {
                practice.messages().send(sender, "duel.usage");
                return;
            }
            if (!practice.hub().isPractice(player)) {
                practice.messages().send(sender, "error.not-in-practice");
                return;
            }
            if (practice.matches().isInMatch(player)) {
                practice.messages().send(sender, "duel.busy");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || target.equals(player)) {
                practice.messages().send(sender, "error.player-offline", "<name>", args[0]);
                return;
            }
            if (!practice.hub().isPractice(target) || practice.matches().isInMatch(target)) {
                practice.messages().send(sender, "duel.target-busy", "<player>", target.getName());
                return;
            }
            String kitId = args.length > 1 ? args[1].toLowerCase() : practice.defaultKitId();
            Optional<PracticeKit> kit = practice.kits().get(kitId);
            if (kit.isEmpty()) {
                practice.messages().send(sender, "duel.unknown-kit", "<kit>", kitId);
                return;
            }

            INVITES.computeIfAbsent(target.getUniqueId(), id -> new HashMap<>())
                    .put(player.getUniqueId(), kit.get().id());
            practice.messages().send(sender, "duel.sent",
                    "<player>", target.getName(), "<kit>", kit.get().displayName());
            practice.messages().send(target, "duel.received",
                    "<player>", player.getName(), "<kit>", kit.get().displayName());
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            if (args.length <= 1) {
                return CommandUtil.onlineNames(sender);
            }
            return args.length == 2 ? practice.kits().names() : List.of();
        }
    }

    private static final class AcceptCommand extends BaseCommand {

        private final PracticePlugin practice;

        private AcceptCommand(PracticePlugin plugin) {
            super(plugin, "accept");
            this.practice = plugin;
            playerOnly();
        }

        @Override
        protected void run(CommandSender sender, String[] args) {
            Player player = player(sender);
            Map<UUID, String> invites = INVITES.get(player.getUniqueId());
            if (invites == null || invites.isEmpty()) {
                practice.messages().send(sender, "duel.nothing-pending");
                return;
            }

            UUID challengerId = null;
            if (args.length > 0) {
                Player named = Bukkit.getPlayerExact(args[0]);
                if (named != null && invites.containsKey(named.getUniqueId())) {
                    challengerId = named.getUniqueId();
                }
            } else {
                challengerId = invites.keySet().iterator().next();
            }
            if (challengerId == null) {
                practice.messages().send(sender, "duel.nothing-pending");
                return;
            }

            String kitId = invites.remove(challengerId);
            Player challenger = Bukkit.getPlayer(challengerId);
            if (challenger == null) {
                practice.messages().send(sender, "duel.challenger-offline");
                return;
            }
            if (practice.matches().isInMatch(challenger) || practice.matches().isInMatch(player)) {
                practice.messages().send(sender, "duel.busy");
                return;
            }
            Optional<PracticeKit> kit = practice.kits().get(kitId);
            if (kit.isEmpty()) {
                practice.messages().send(sender, "duel.unknown-kit", "<kit>", kitId);
                return;
            }
            Optional<Arena> arena = practice.arenas().reserve(kitId, null);
            if (arena.isEmpty()) {
                practice.messages().send(sender, "queue.no-free-arena");
                practice.messages().send(challenger, "queue.no-free-arena");
                return;
            }
            practice.queue().leave(player);
            practice.queue().leave(challenger);
            practice.matches().start(challenger, player, kit.get(), arena.get(), false);
        }

        @Override
        protected List<String> complete(CommandSender sender, String[] args) {
            return args.length <= 1 ? CommandUtil.onlineNames(sender) : List.of();
        }
    }
}
