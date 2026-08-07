package me.alpha432.network.practice.bot;

import me.alpha432.network.practice.PracticePlugin;
import me.alpha432.network.practice.kit.PracticeKit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns the training bots: one per player, ticked centrally. */
public final class BotService {

    private final PracticePlugin plugin;
    private final Map<UUID, PracticeBot> bots = new HashMap<>();
    private BukkitTask task;

    public BotService(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickAll() {
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, PracticeBot> entry : bots.entrySet()) {
            try {
                if (!entry.getValue().tick()) {
                    finished.add(entry.getKey());
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Bot of " + entry.getKey() + " failed: " + e.getMessage());
                finished.add(entry.getKey());
            }
        }
        for (UUID uuid : finished) {
            PracticeBot bot = bots.remove(uuid);
            if (bot != null) {
                bot.remove();
            }
        }
    }

    public PracticeBot of(Player player) {
        return bots.get(player.getUniqueId());
    }

    public boolean has(Player player) {
        return bots.containsKey(player.getUniqueId());
    }

    /** The bot this entity belongs to, or {@code null}. */
    public PracticeBot byEntity(Entity entity) {
        for (PracticeBot bot : bots.values()) {
            if (bot.isEntity(entity)) {
                return bot;
            }
        }
        return null;
    }

    /**
     * Spawns a bot for the player, replacing an existing one.
     *
     * @return the new bot, or {@code null} when the kit is unknown
     */
    public PracticeBot spawn(Player player, BotSettings settings) {
        PracticeKit kit = plugin.kits().get(settings.kitId()).orElse(null);
        if (kit == null) {
            return null;
        }
        remove(player);

        PracticeBot bot = new PracticeBot(player, settings);
        bot.spawn(player.getLocation().add(player.getLocation().getDirection().setY(0).normalize()
                        .multiply(3)),
                kit, plugin.messages().raw("bot.entity-name").replace("<player>", player.getName()));
        bots.put(player.getUniqueId(), bot);

        // Give the player the same kit so the fight is fair.
        plugin.equipForBotFight(player, kit);
        return bot;
    }

    public void remove(Player player) {
        PracticeBot bot = bots.remove(player.getUniqueId());
        if (bot != null) {
            bot.remove();
        }
    }

    public void removeAll() {
        bots.values().forEach(PracticeBot::remove);
        bots.clear();
    }

    public int count() {
        return bots.size();
    }
}
