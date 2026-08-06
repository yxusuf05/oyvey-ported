package me.alpha432.network.smp;

import me.alpha432.network.core.Core;
import me.alpha432.network.core.text.Messages;
import me.alpha432.network.core.text.Placeholders;
import me.alpha432.network.core.util.Cooldowns;
import me.alpha432.network.smp.command.EconomyCommands;
import me.alpha432.network.smp.command.HomeCommands;
import me.alpha432.network.smp.command.KitCommand;
import me.alpha432.network.smp.command.ShopCommands;
import me.alpha432.network.smp.command.SpawnCommands;
import me.alpha432.network.smp.command.TeleportCommands;
import me.alpha432.network.smp.command.WarpCommands;
import me.alpha432.network.smp.home.HomeService;
import me.alpha432.network.smp.kit.KitService;
import me.alpha432.network.smp.listener.SmpListener;
import me.alpha432.network.smp.shop.ShopService;
import me.alpha432.network.smp.teleport.TeleportRequestService;
import me.alpha432.network.smp.warp.WarpService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** The survival area: spawn, homes, warps, teleport requests, kits, economy and shop. */
public final class SmpPlugin extends JavaPlugin {

    private Messages messages;
    private SpawnService spawn;
    private HomeService homes;
    private WarpService warps;
    private KitService kits;
    private ShopService shop;
    private TeleportRequestService requests;
    private Cooldowns teleportCooldowns;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        Core.database().applySchema(this, "schema.sql");

        spawn = new SpawnService(this);
        homes = new HomeService(this, Core.database(), getConfig().getInt("homes.default-limit", 1));
        warps = new WarpService(this);
        kits = new KitService(this, Core.database());
        shop = new ShopService(this);
        requests = new TeleportRequestService(getConfig().getLong("teleport.request-timeout-seconds", 60L));
        teleportCooldowns = new Cooldowns();

        Bukkit.getPluginManager().registerEvents(homes, this);
        Bukkit.getPluginManager().registerEvents(requests, this);
        Bukkit.getPluginManager().registerEvents(new SmpListener(this), this);

        SpawnCommands.register(this);
        HomeCommands.register(this);
        WarpCommands.register(this);
        TeleportCommands.register(this);
        EconomyCommands.register(this);
        ShopCommands.register(this);
        new KitCommand(this).register();

        getLogger().info("NetworkSMP enabled with " + kits.all().size() + " kits and "
                + warps.all().size() + " warps.");
    }

    /**
     * Checks and starts the teleport cooldown.
     *
     * @return false when the player still has to wait; a message was already sent
     */
    public boolean startTeleport(Player player, String type) {
        if (player.hasPermission("network.smp.tp.bypass-cooldown")) {
            return true;
        }
        if (teleportCooldowns.isActive(player.getUniqueId())) {
            messages.send(player, "teleport.cooldown",
                    "<time>", Placeholders.formatSeconds(
                            teleportCooldowns.remainingSeconds(player.getUniqueId())),
                    "<type>", type);
            return false;
        }
        teleportCooldowns.setSeconds(player.getUniqueId(),
                getConfig().getLong("teleport.cooldown-seconds", 10L));
        return true;
    }

    /** Warmup in seconds; the {@code network.teleport.fast} perk halves it. */
    public int warmupFor(Player player) {
        int warmup = getConfig().getInt("teleport.warmup-seconds", 3);
        if (player.hasPermission("network.teleport.fast")) {
            warmup = Math.max(1, warmup / 2);
        }
        return warmup;
    }

    public void reloadEverything() {
        reloadConfig();
        messages.reload();
        spawn.load();
        warps.load();
        kits.reload();
        shop.reload();
    }

    public Messages messages() {
        return messages;
    }

    public SpawnService spawn() {
        return spawn;
    }

    public HomeService homes() {
        return homes;
    }

    public WarpService warps() {
        return warps;
    }

    public KitService kits() {
        return kits;
    }

    public ShopService shop() {
        return shop;
    }

    public TeleportRequestService requests() {
        return requests;
    }
}
