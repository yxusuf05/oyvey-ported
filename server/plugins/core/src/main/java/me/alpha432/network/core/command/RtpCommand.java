package me.alpha432.network.core.command;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.CorePlugin;
import me.alpha432.network.core.teleport.RandomTeleportService;
import me.alpha432.network.core.util.Cooldowns;
import me.alpha432.network.core.util.Locations;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /rtp} — one command for every area. Which destination a player gets is decided by the
 * provider registered for the world they stand in.
 */
public final class RtpCommand extends BaseCommand {

    private final CorePlugin core;
    private final Cooldowns cooldowns = new Cooldowns();

    public RtpCommand(CorePlugin plugin) {
        super(plugin, "rtp");
        this.core = plugin;
        permission("network.command.rtp");
        playerOnly();
    }

    @Override
    protected void run(CommandSender sender, String[] args) {
        Player player = player(sender);
        RandomTeleportService.Provider provider = Core.randomTeleports().providerFor(player.getWorld());
        if (provider == null) {
            Core.messages().send(sender, "rtp.wrong-world");
            return;
        }
        if (!player.hasPermission("network.command.rtp.bypass-cooldown")
                && cooldowns.isActive(player.getUniqueId())) {
            Core.messages().send(sender, "rtp.cooldown",
                    "<seconds>", String.valueOf(cooldowns.remainingSeconds(player.getUniqueId())));
            return;
        }
        cooldowns.setSeconds(player.getUniqueId(),
                core.getConfig().getLong("teleport.rtp-cooldown-seconds", 30L));

        Core.messages().send(sender, provider.searchingKey());
        provider.find(player).thenAccept(target -> {
            if (target == null) {
                Core.messages().send(sender, "rtp.failed");
                cooldowns.clear(player.getUniqueId());
                return;
            }
            Core.teleports().teleport(player, target);
            Core.messages().send(sender, "rtp.done", "<location>", Locations.pretty(target));
        });
    }
}
