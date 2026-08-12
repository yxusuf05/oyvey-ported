package me.alpha432.corepvp.crystal;

import me.alpha432.corepvp.CorePvPPlugin;
import me.alpha432.corepvp.config.Messages;
import me.alpha432.corepvp.match.Match;
import me.alpha432.corepvp.util.Tasks;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Crystal PvP bookkeeping: who placed what, and who to blame for the blast.
 *
 * <p>Explosions in Bukkit carry no attacker, so without this every crystal kill
 * would be attributed to nobody - no kill message, no stat, no ELO. Resolved
 * attackers are fed into the match engine's existing combat tagging, so death
 * handling stays in one place.
 */
public final class CrystalService {

    /** How far from a block explosion a detonation still counts as its cause. */
    private static final double ANCHOR_RADIUS = 10.0D;

    private final CorePvPPlugin plugin;
    private final Messages messages;
    private final AttributionTracker tracker;
    private BukkitTask sweepTask;

    public CrystalService(CorePvPPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.tracker = new AttributionTracker(
                plugin.configs().main().getLong("crystal.attribution-seconds", 30L) * 1000L,
                plugin.configs().main().getLong("crystal.anchor-attribution-seconds", 3L) * 1000L);
    }

    public void start() {
        stop();
        // Cheap, but it stops the maps growing on a busy FFA arena.
        sweepTask = Tasks.timer(tracker::sweep, 20L * 30L, 20L * 30L);
    }

    public void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        tracker.clear();
    }

    public void rememberCrystal(UUID crystalId, Player placer) {
        tracker.rememberEntity(crystalId, placer.getUniqueId());

        Match match = plugin.matches().matchOf(placer);
        if (match != null) {
            match.stats(placer.getUniqueId()).crystalPlaced();
        }
    }

    public void forgetCrystal(UUID crystalId) {
        tracker.forgetEntity(crystalId);
    }

    public void rememberAnchor(Location location, Player player) {
        tracker.rememberPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                player.getUniqueId());
    }

    public Player placerOf(UUID crystalId) {
        return online(tracker.ownerOfEntity(crystalId));
    }

    public Player anchorOwnerNear(Location location) {
        return online(tracker.ownerNear(location.getBlockX(), location.getBlockY(),
                location.getBlockZ(), ANCHOR_RADIUS));
    }

    /**
     * Credits an explosion to a player: tags them as the last attacker so the
     * kill lands on them, and records the hit for match statistics.
     */
    public void attribute(Player victim, Player attacker, double damage) {
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        plugin.matches().tagAttacker(victim, attacker);

        Match match = plugin.matches().matchOf(victim);
        if (match != null && match.contains(attacker.getUniqueId())) {
            match.stats(attacker.getUniqueId()).hit();
            match.stats(attacker.getUniqueId()).damage(damage);
            match.stats(victim.getUniqueId()).tookHit();
        }
    }

    /** A totem saved someone: count it and tell the people watching. */
    public void totemPopped(Player victim) {
        Match match = plugin.matches().matchOf(victim);
        if (match != null) {
            match.stats(victim.getUniqueId()).totemPopped();
            match.broadcast(messages.render("crystal.totem-popped",
                    Messages.of("player", victim.getName()),
                    Messages.of("count", match.stats(victim.getUniqueId()).totemsPopped())));
            return;
        }
        if (plugin.configs().main().getBoolean("crystal.announce-totems-outside-matches", true)) {
            victim.sendMessage(messages.render("crystal.totem-popped-self"));
        }
    }

    private Player online(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Player player = plugin.getServer().getPlayer(uuid);
        return player != null && player.isOnline() ? player : null;
    }
}
