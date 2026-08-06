package me.alpha432.network.practice.match;

import me.alpha432.network.core.util.Sounds;
import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.arena.Arena;
import me.alpha432.network.practice.kit.PracticeKit;
import me.alpha432.network.practice.stats.Elo;
import me.alpha432.network.practice.stats.KitStats;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs duels: countdown, the fight itself, the result and the cleanup afterwards. */
public final class MatchService {

    private final PracticePlugin plugin;
    private final Map<UUID, Match> byPlayer = new HashMap<>();
    private final List<Match> running = new ArrayList<>();

    public MatchService(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public Match matchOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public boolean isInMatch(Player player) {
        return byPlayer.containsKey(player.getUniqueId());
    }

    public List<Match> running() {
        return running;
    }

    public int fightingPlayers() {
        return byPlayer.size();
    }

    /** Sets up the arena, equips both players and starts the countdown. */
    public Match start(Player first, Player second, PracticeKit kit, Arena arena, boolean ranked) {
        Match match = new Match(kit, arena, ranked, first.getUniqueId(), second.getUniqueId());
        byPlayer.put(first.getUniqueId(), match);
        byPlayer.put(second.getUniqueId(), match);
        running.add(match);

        prepare(first, kit, arena.firstSpawn(), second);
        prepare(second, kit, arena.secondSpawn(), first);

        plugin.messages().send(first, "match.found", "<opponent>", second.getName(),
                "<kit>", kit.displayName(), "<arena>", arena.displayName());
        plugin.messages().send(second, "match.found", "<opponent>", first.getName(),
                "<kit>", kit.displayName(), "<arena>", arena.displayName());

        countdown(match);
        return match;
    }

    private void prepare(Player player, PracticeKit kit, org.bukkit.Location spawn, Player opponent) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setHealth(maxHealth(player));
        player.setLevel(0);
        player.setExp(0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setMaximumNoDamageTicks(kit.damageTicks());

        player.teleport(spawn);
        player.getInventory().setStorageContents(plugin.kits().contentsFor(player, kit));
        if (kit.helmet() != null) {
            player.getInventory().setHelmet(kit.helmet().clone());
        }
        if (kit.chestplate() != null) {
            player.getInventory().setChestplate(kit.chestplate().clone());
        }
        if (kit.leggings() != null) {
            player.getInventory().setLeggings(kit.leggings().clone());
        }
        if (kit.boots() != null) {
            player.getInventory().setBoots(kit.boots().clone());
        }
        kit.effects().forEach(player::addPotionEffect);
        // Look at each other so the first second is not spent turning around.
        player.setRotation(yawTowards(player, opponent), 0f);
    }

    private void countdown(Match match) {
        int seconds = plugin.getConfig().getInt("match.countdown-seconds", 5);
        int[] remaining = {seconds};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running.contains(match)) {
                holder[0].cancel();
                return;
            }
            if (remaining[0] > 0) {
                forEachViewer(match, player -> {
                    plugin.messages().actionBar(player, "match.countdown",
                            "<seconds>", String.valueOf(remaining[0]));
                    Sounds.play(player, plugin.getConfig().getString("match.countdown-sound", ""), 1f, 1f);
                });
                remaining[0]--;
                return;
            }
            holder[0].cancel();
            match.state(Match.State.FIGHTING);
            forEachViewer(match, player -> {
                plugin.messages().actionBar(player, "match.go");
                Sounds.play(player, plugin.getConfig().getString("match.start-sound", ""), 1f, 1.5f);
            });
        }, 0L, 20L);
    }

    /** Ends the match with a winner. Pass {@code null} when nobody won (both left). */
    public void end(Match match, UUID winnerId, String reasonKey) {
        if (!running.remove(match)) {
            return;
        }
        match.state(Match.State.ENDING);
        match.winner(winnerId);

        Player winner = winnerId == null ? null : Bukkit.getPlayer(winnerId);
        UUID loserId = winnerId == null ? null : match.opponentOf(winnerId);
        Player loser = loserId == null ? null : Bukkit.getPlayer(loserId);

        int eloChange = 0;
        if (match.isRanked() && winnerId != null && loserId != null) {
            eloChange = applyRankedResult(match, winnerId, loserId);
        } else if (winnerId != null && loserId != null) {
            plugin.stats().recordCasual(winnerId, loserId, match.kit().id());
        }

        announce(match, winner, loser, eloChange, reasonKey);

        byPlayer.remove(match.first());
        byPlayer.remove(match.second());
        match.spectators().forEach(byPlayer::remove);

        int restored = match.blocks().restore();
        if (restored > 0) {
            plugin.getLogger().fine("Rolled back " + restored + " blocks in arena "
                    + match.arena().name());
        }
        plugin.arenas().release(match.arena());

        List<UUID> toHub = new ArrayList<>();
        toHub.add(match.first());
        toHub.add(match.second());
        toHub.addAll(match.spectators());

        long delay = plugin.getConfig().getLong("match.end-delay-ticks", 60L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID uuid : toHub) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && !isInMatch(player)) {
                    plugin.hub().send(player);
                }
            }
        }, delay);
    }

    private int applyRankedResult(Match match, UUID winnerId, UUID loserId) {
        KitStats winnerStats = plugin.stats().get(winnerId, match.kit().id());
        KitStats loserStats = plugin.stats().get(loserId, match.kit().id());
        int kFactor = plugin.getConfig().getInt("ranked.k-factor", 32);
        int gain = Elo.gain(winnerStats.elo(), loserStats.elo(), kFactor);
        int loss = Elo.loss(winnerStats.elo(), loserStats.elo(), kFactor);
        winnerStats.won(gain);
        loserStats.lost(loss);
        plugin.stats().save(winnerStats);
        plugin.stats().save(loserStats);
        return gain;
    }

    private void announce(Match match, Player winner, Player loser, int eloChange, String reasonKey) {
        String winnerName = winner != null ? winner.getName() : "-";
        String loserName = loser != null ? loser.getName() : "-";
        forEachViewer(match, player -> {
            plugin.messages().send(player, "match.result-header");
            plugin.messages().send(player, "match.result-line",
                    "<winner>", winnerName,
                    "<loser>", loserName,
                    "<duration>", match.durationText(),
                    "<reason>", plugin.messages().raw(reasonKey));
            if (match.isRanked() && eloChange > 0) {
                plugin.messages().send(player, "match.result-elo",
                        "<winner>", winnerName, "<elo>", String.valueOf(eloChange));
            }
            plugin.messages().send(player, "match.result-footer");
        });
    }

    /** Both fighters and every spectator that is still online. */
    public void forEachViewer(Match match, java.util.function.Consumer<Player> action) {
        Player first = Bukkit.getPlayer(match.first());
        Player second = Bukkit.getPlayer(match.second());
        if (first != null) {
            action.accept(first);
        }
        if (second != null) {
            action.accept(second);
        }
        for (UUID uuid : match.spectators()) {
            Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) {
                action.accept(spectator);
            }
        }
    }

    public void addSpectator(Player spectator, Match match) {
        match.spectators().add(spectator.getUniqueId());
        byPlayer.put(spectator.getUniqueId(), match);
        spectator.setGameMode(GameMode.SPECTATOR);
        Player target = Bukkit.getPlayer(match.first());
        spectator.teleport(target != null ? target.getLocation() : match.arena().firstSpawn());
        forEachViewer(match, player -> plugin.messages().send(player, "spectate.joined",
                "<player>", spectator.getName()));
    }

    public void removeSpectator(Player spectator) {
        Match match = byPlayer.remove(spectator.getUniqueId());
        if (match != null) {
            match.spectators().remove(spectator.getUniqueId());
        }
        plugin.hub().send(spectator);
    }

    public boolean isSpectator(Player player) {
        Match match = byPlayer.get(player.getUniqueId());
        return match != null && match.spectators().contains(player.getUniqueId());
    }

    /** Ends every running match, e.g. on shutdown. */
    public void endAll() {
        for (Match match : new ArrayList<>(running)) {
            end(match, null, "match.reason-cancelled");
        }
    }

    private static double maxHealth(Player player) {
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attribute == null ? 20.0D : attribute.getValue();
    }

    private static float yawTowards(Player from, Player to) {
        double dx = to.getLocation().getX() - from.getLocation().getX();
        double dz = to.getLocation().getZ() - from.getLocation().getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
