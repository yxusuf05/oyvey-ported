package me.alpha432.network.practice.bot;

import me.alpha432.network.practice.PracticePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;

/** Wires player actions to the training bot and keeps the bot out of everything else. */
public final class BotListener implements Listener {

    private final PracticePlugin plugin;

    public BotListener(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    /** Counting swings is what makes the accuracy number meaningful. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        PracticeBot bot = plugin.bots().of(event.getPlayer());
        if (bot != null) {
            bot.stats().swing();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        PracticeBot bot = plugin.bots().byEntity(event.getEntity());
        if (bot == null || !(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!attacker.equals(bot.owner())) {
            return;
        }
        if (bot.settings().behavior() == BotBehavior.BOXING
                || bot.settings().behavior() == BotBehavior.SUMO) {
            // Only the hit count matters in these modes.
            event.setDamage(0.0D);
        }
        bot.onHitByOwner();
        plugin.showBotStats(attacker, bot);
    }

    /** Nobody except the owner may hurt a training bot, and the bot hurts nobody else. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        PracticeBot bot = plugin.bots().byEntity(event.getEntity());
        if (bot != null) {
            if (!(event instanceof EntityDamageByEntityEvent byEntity)
                    || !bot.owner().equals(byEntity.getDamager())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        PracticeBot attacker = plugin.bots().byEntity(byEntity.getDamager());
        if (attacker != null && !attacker.owner().equals(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        PracticeBot bot = plugin.bots().byEntity(event.getEntity());
        if (bot == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.messages().send(bot.owner(), "bot.defeated",
                "<hits>", String.valueOf(bot.stats().hits()),
                "<time>", bot.stats().durationText());
        plugin.respawnBot(bot);
    }

    /** With AI switched off the bot should never pick a target on its own anyway. */
    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (plugin.bots().byEntity(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.bots().remove(event.getPlayer());
    }
}
