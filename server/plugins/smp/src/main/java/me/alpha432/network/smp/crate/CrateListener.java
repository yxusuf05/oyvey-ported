package me.alpha432.network.smp.crate;

import me.alpha432.network.core.util.Sounds;
import me.alpha432.network.smp.SmpPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Right click a crate to open it, left click to preview. The crate block is protected. */
public final class CrateListener implements Listener {

    private final SmpPlugin plugin;

    public CrateListener(SmpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        Crate crate = plugin.crates().at(event.getClickedBlock().getLocation()).orElse(null);
        if (crate == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            new CratePreviewMenu(plugin, player, crate).open(player);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        int keys = plugin.crates().countKeys(player, crate);
        if (keys <= 0) {
            plugin.messages().send(player, "crate.no-key", "<crate>", crate.displayName());
            Sounds.play(player, plugin.getConfig().getString("crates.deny-sound", ""), 1f, 1f);
            new CratePreviewMenu(plugin, player, crate).open(player);
            return;
        }
        if (crate.rewards().isEmpty()) {
            plugin.messages().send(player, "crate.empty", "<crate>", crate.displayName());
            return;
        }

        CrateReward reward = plugin.crates().roll(crate);
        if (reward == null) {
            plugin.messages().send(player, "crate.empty", "<crate>", crate.displayName());
            return;
        }
        // Take the key before the animation so it cannot be spent twice.
        if (!plugin.crates().consumeKey(player, crate)) {
            plugin.messages().send(player, "crate.no-key", "<crate>", crate.displayName());
            return;
        }
        new CrateOpenMenu(plugin, player, crate, reward).open(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.crates().at(event.getBlock().getLocation()).isPresent()
                && !event.getPlayer().hasPermission("network.smp.admin")) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "crate.protected");
        }
    }
}
