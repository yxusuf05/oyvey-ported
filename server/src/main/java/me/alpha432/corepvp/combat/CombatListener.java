package me.alpha432.corepvp.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Removes the parts of modern combat that legacy kits do not want. */
public final class CombatListener implements Listener {

    private final CombatModeService combat;

    public CombatListener(CombatModeService combat) {
        this.combat = combat;
    }

    /**
     * A raised attack speed attribute alone still leaves sweep attacks in play,
     * which would let one swing hit everyone standing nearby - not something
     * 1.8-style kits ever had.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSweep(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        if (event.getDamager() instanceof Player attacker && combat.modeOf(attacker).legacy()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        combat.forget(event.getPlayer().getUniqueId());
    }
}
